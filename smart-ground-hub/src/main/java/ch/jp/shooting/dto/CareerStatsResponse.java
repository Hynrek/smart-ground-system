package ch.jp.shooting.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO für Karriere-Statistiken eines Spielers.
 */
public record CareerStatsResponse(
    UUID userId,
    int totalWins,
    int participations,
    int totalScore,
    double avgScore,
    Instant lastCompeted
) {
}
