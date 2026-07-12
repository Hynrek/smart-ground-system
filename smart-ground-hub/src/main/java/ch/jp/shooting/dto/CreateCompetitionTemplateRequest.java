package ch.jp.shooting.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Request für die Erstellung einer Wettkampf-Vorlage.
 */
@NullMarked
public record CreateCompetitionTemplateRequest(
    String name,
    String type,  // "COMPETITION" oder "TRAINING"
    @Nullable String programIds,  // JSON-Array von UUIDs
    @Nullable String rangeSegmentMap,  // JSON RangeSegmentEntry[]
    @Nullable String defaultPlayers,  // JSON SessionPlayer[]
    @Nullable Integer maxGroups,
    @Nullable String bracketType,  // "ROUND_ROBIN", "SINGLE_ELIMINATION", etc.
    @Nullable String defaultTiebreaker,  // "TOTAL_SCORE", "AVG_SCORE", "WINS"
    boolean publishResults
) {
}
