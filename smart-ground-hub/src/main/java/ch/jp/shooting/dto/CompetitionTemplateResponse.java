package ch.jp.shooting.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Response für eine Wettkampf-Vorlage.
 */
@NullMarked
public record CompetitionTemplateResponse(
    UUID id,
    String name,
    String type,
    @Nullable String programIds,
    @Nullable String rangeSegmentMap,
    @Nullable String defaultPlayers,
    @Nullable Integer maxGroups,
    @Nullable String bracketType,
    @Nullable String defaultTiebreaker,
    boolean publishResults,
    @Nullable UUID createdById,
    Instant createdAt
) {
}
