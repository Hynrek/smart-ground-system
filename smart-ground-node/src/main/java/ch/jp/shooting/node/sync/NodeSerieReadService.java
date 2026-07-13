package ch.jp.shooting.node.sync;

import ch.jp.shooting.node.outbox.OutboxEntry;
import ch.jp.shooting.node.outbox.OutboxEntryRepository;
import ch.jp.smartground.model.SerieOutboxItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Read-your-writes (Teilprojekt #3): vereinigt die gesyncten Serien (#2) mit noch nicht
 * bestätigten lokalen Outbox-Einträgen, damit ein Schütze seine offline erstellte Serie
 * sofort sieht, bevor der Hub sie je bestätigt hat. Kein HTTP-Endpoint hier — die
 * node-api-Fassade (#5) exponiert das erst.
 */
@Service
public class NodeSerieReadService {

    private final SyncedSerieRepository syncedSerieRepository;
    private final OutboxEntryRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public NodeSerieReadService(SyncedSerieRepository syncedSerieRepository,
                                 OutboxEntryRepository outboxRepository,
                                 @Qualifier("outboxObjectMapper") ObjectMapper objectMapper) {
        this.syncedSerieRepository = syncedSerieRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public List<VisibleSerie> findAllVisible() {
        var byId = new LinkedHashMap<UUID, VisibleSerie>();

        for (SyncedSerie synced : syncedSerieRepository.findAll()) {
            if (!synced.isDeleted()) {
                byId.put(synced.getId(), new VisibleSerie(
                        synced.getId(), synced.getName(), synced.getOwnership(), synced.getRangeId(),
                        synced.getOwnerId(), synced.getStepsJson(), synced.isPublished(), "synced"));
            }
        }

        // Outbox-Einträge überschreiben absichtlich eine SyncedSerie mit derselben id (z. B. eine
        // Offline-Bearbeitung einer bereits vom Hub synchronisierten Serie): der lokale
        // pending/failed-Zustand soll gegenüber dem letzten bekannten Hub-Stand gewinnen, bis der
        // Outbox-Eintrag bestätigt (SENT) und die nächste Abwärts-Synchronisierung ihn ersetzt hat.
        for (OutboxEntry entry : outboxRepository.findByEntityTypeOrderBySequenceAsc("SERIE")) {
            if ("SENT".equals(entry.getStatus())) {
                continue; // bereits (oder bald) über SyncedSerie sichtbar
            }
            SerieOutboxItem item = readJson(entry.getPayloadJson());
            String provenance = "FAILED".equals(entry.getStatus()) ? "failed" : "pending";
            byId.put(item.getId(), new VisibleSerie(
                    item.getId(), item.getName(), item.getOwnership(), unwrapRangeId(item),
                    item.getOwnerId(), item.getStepsJson(), Boolean.TRUE.equals(item.getPublished()), provenance));
        }

        return List.copyOf(byId.values());
    }

    private SerieOutboxItem readJson(String json) {
        try {
            return objectMapper.readValue(json, SerieOutboxItem.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("corrupt outbox payload", e);
        }
    }

    private static UUID unwrapRangeId(SerieOutboxItem item) {
        JsonNullable<UUID> rangeId = item.getRangeId();
        return (rangeId != null && rangeId.isPresent()) ? rangeId.get() : null;
    }
}
