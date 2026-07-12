package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * Score-Eintrag für ein Segment eines Spielers.
 */
@NullMarked
public class SegmentScoreEntry {
    @JsonProperty("segmentId")
    public UUID segmentId;

    @JsonProperty("score")
    public int score;

    @JsonProperty("maxScore")
    public int maxScore;

    @JsonProperty("completionPct")
    public int completionPct;

    public SegmentScoreEntry() {
    }

    public SegmentScoreEntry(UUID segmentId, int score, int maxScore, int completionPct) {
        this.segmentId = segmentId;
        this.score = score;
        this.maxScore = maxScore;
        this.completionPct = completionPct;
    }
}
