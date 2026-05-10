package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ergebnisse für ein Segment innerhalb eines Programms.
 */
@NullMarked
public class SegmentResult {
    @JsonProperty("segmentId")
    public UUID segmentId;

    @JsonProperty("groupId")
    public UUID groupId; // Welche Gruppe der Spieler für dieses Segment hatte

    @JsonProperty("stepResults")
    public List<StepResult> stepResults = new ArrayList<>();

    @JsonProperty("score")
    public int score; // Akkumulierte Punkte

    @JsonProperty("maxScore")
    public int maxScore; // Maximale verfügbare Punkte

    public SegmentResult() {
    }

    public SegmentResult(UUID segmentId, UUID groupId) {
        this.segmentId = segmentId;
        this.groupId = groupId;
    }

    public List<StepResult> getStepResults() {
        return stepResults;
    }
}
