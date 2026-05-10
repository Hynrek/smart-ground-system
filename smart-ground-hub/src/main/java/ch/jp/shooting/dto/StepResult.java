package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ergebnis für einen einzelnen Schritt.
 * State: 'pending' | 'done' | 'failed-both' | 'failed-a' | 'failed-b'
 */
@NullMarked
public class StepResult {
    @JsonProperty("stepId")
    public String stepId;

    @JsonProperty("state")
    public String state; // pending, done, failed-both, failed-a, failed-b

    @JsonProperty("noBirds")
    public int noBirds; // count of machine failures before the actual throw

    @JsonProperty("pointsEarned")
    public int pointsEarned;

    @JsonProperty("corrections")
    @Nullable
    public List<Correction> corrections = new ArrayList<>();

    public StepResult() {
    }

    public StepResult(String stepId, String state, int noBirds, int pointsEarned) {
        this.stepId = stepId;
        this.state = state;
        this.noBirds = noBirds;
        this.pointsEarned = pointsEarned;
    }

    public int getPointsEarned() {
        return pointsEarned;
    }

    // Assuming maximum 100 points per step (standard shooting range convention)
    public int getPointValue() {
        return 100;
    }
}
