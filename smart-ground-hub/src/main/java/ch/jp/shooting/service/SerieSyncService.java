package ch.jp.shooting.service;

import ch.jp.shooting.model.Serie;
import ch.jp.shooting.repository.SerieRepository;
import ch.jp.smartground.model.SerieSyncItem;
import ch.jp.smartground.model.SerieSyncPage;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Baut eine Sync-Seite (hub-api, abwärts) aus Serien inkl. Grabsteinen. */
@Service
@NullMarked
public class SerieSyncService {

    private static final int DEFAULT_LIMIT = 500;
    private static final int MAX_LIMIT = 2000;

    private final SerieRepository serieRepository;

    public SerieSyncService(SerieRepository serieRepository) {
        this.serieRepository = serieRepository;
    }

    public SerieSyncPage syncSerien(@Nullable OffsetDateTime updatedAfter, @Nullable Integer limit) {
        Instant cursor = updatedAfter != null ? updatedAfter.toInstant() : Instant.EPOCH;
        int pageSize = clampLimit(limit);

        List<Serie> rows = serieRepository.findForSyncFrom(cursor, pageSize);
        List<SerieSyncItem> items = rows.stream().map(SerieSyncService::toItem).toList();

        // nextCursor = grösstes updated_at der Seite; bei leerer Seite den Anfrage-Cursor spiegeln.
        Instant nextCursor = rows.isEmpty()
                ? cursor
                : rows.get(rows.size() - 1).getUpdatedAt();
        boolean hasMore = rows.size() == pageSize;

        return new SerieSyncPage()
                .items(items)
                .nextCursor(OffsetDateTime.ofInstant(nextCursor, ZoneOffset.UTC))
                .hasMore(hasMore);
    }

    private int clampLimit(@Nullable Integer limit) {
        if (limit == null || limit < 1) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    private static SerieSyncItem toItem(Serie s) {
        return new SerieSyncItem()
                .id(s.getId())
                .name(s.getName())
                .ownership(s.getOwnership())
                .rangeId(s.getRange() != null ? s.getRange().getId() : null)
                .ownerId(s.getOwner().getId())
                .stepsJson(s.getStepsJson())
                .published(s.isPublished())
                .createdAt(OffsetDateTime.ofInstant(s.getCreatedAt(), ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.ofInstant(s.getUpdatedAt(), ZoneOffset.UTC))
                .deleted(s.isDeleted());
    }
}
