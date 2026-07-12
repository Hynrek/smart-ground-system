package ch.jp.shooting.node.sync;

import ch.jp.shooting.node.hub.HubClient;
import ch.jp.smartground.model.SerieSyncItem;
import ch.jp.smartground.model.SerieSyncPage;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Sync-Client (hub-api, abwärts). Drainiert alle Seiten ab dem persistenten Cursor, upsertet jede
 * Serie (inkl. Grabsteine) idempotent nach der Hub-id und schiebt den Cursor pro Seite nach.
 * Kein umschliessendes @Transactional: jedes save() committet einzeln, damit ein Absturz mitten im
 * Drain den Cursor höchstens zu WENIG vorschiebt — der nächste Lauf holt die Seite erneut, was dank
 * idempotentem Upsert folgenlos ist (überlebt einen Neustart mit korrektem Cursor).
 */
@NullMarked
@Service
public class SerieSyncService {

    static final String ENTITY = "serie";
    static final int PAGE_LIMIT = 500;

    private final HubClient hubClient;
    private final SyncedSerieRepository syncedSerieRepository;
    private final SyncStateRepository syncStateRepository;

    public SerieSyncService(HubClient hubClient,
                            SyncedSerieRepository syncedSerieRepository,
                            SyncStateRepository syncStateRepository) {
        this.hubClient = hubClient;
        this.syncedSerieRepository = syncedSerieRepository;
        this.syncStateRepository = syncStateRepository;
    }

    public int sync() {
        Instant cursor = loadCursor();
        int applied = 0;
        boolean hasMore = true;

        while (hasMore) {
            SerieSyncPage page = hubClient.fetchSerieSyncPage(cursor, PAGE_LIMIT);
            List<SerieSyncItem> items = page.getItems();
            if (items != null) {
                for (SerieSyncItem item : items) {
                    if (upsert(item)) {
                        applied++;
                    }
                }
            }
            if (page.getNextCursor() != null) {
                cursor = page.getNextCursor().toInstant();
            }
            saveCursor(cursor);
            hasMore = Boolean.TRUE.equals(page.getHasMore());
        }
        return applied;
    }

    private Instant loadCursor() {
        return syncStateRepository.findById(ENTITY)
                .map(SyncState::getCursor)
                .orElse(Instant.EPOCH);
    }

    private void saveCursor(Instant cursor) {
        SyncState state = syncStateRepository.findById(ENTITY).orElseGet(() -> {
            SyncState fresh = new SyncState();
            fresh.setEntity(ENTITY);
            return fresh;
        });
        state.setCursor(cursor);
        syncStateRepository.save(state);
    }

    /**
     * Upsertet eine Zeile idempotent und meldet zurueck, ob es eine ECHTE Aenderung war.
     * Grund: die Hub-Query verwendet eine inklusive untere Cursor-Schranke (updated_at >= cursor),
     * daher liefert der Hub die Grenzzeile bei JEDEM folgenden Tick erneut aus. Ohne diesen Vergleich
     * wuerde SerieSyncScheduler das als "1 Zeile angewandt" protokollieren — auf Dauer ein Dauer-
     * Heartbeat-Log statt eines echten Signals. Der Upsert selbst bleibt unveraendert (idempotent,
     * harmlos), nur die applied-Zaehlung wird verfeinert. Cursor-Fortschritt ist davon unberuehrt.
     */
    private boolean upsert(SerieSyncItem item) {
        var existing = syncedSerieRepository.findById(item.getId());
        Instant incomingUpdatedAt = item.getUpdatedAt().toInstant();
        boolean changed = existing.isEmpty() || !existing.get().getUpdatedAt().equals(incomingUpdatedAt);

        SyncedSerie row = existing.orElseGet(SyncedSerie::new);
        row.setId(item.getId());
        row.setName(item.getName());
        row.setOwnership(item.getOwnership());
        row.setOwnerId(item.getOwnerId());
        row.setRangeId(item.getRangeId().isPresent() ? item.getRangeId().get() : null);
        row.setStepsJson(item.getStepsJson());
        row.setPublished(Boolean.TRUE.equals(item.getPublished()));
        row.setCreatedAt(item.getCreatedAt().toInstant());
        row.setUpdatedAt(incomingUpdatedAt);
        row.setDeleted(Boolean.TRUE.equals(item.getDeleted()));
        syncedSerieRepository.save(row);
        return changed;
    }
}
