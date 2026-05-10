package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Request zum Einreichen von Ergebnissen nach Spielerdurchlauf.
 */
@NullMarked
public class SubmitResultRequest {
    @JsonProperty("groupId")
    public UUID groupId;

    @JsonProperty("playerId")
    public UUID playerId;

    @JsonProperty("programId")
    public UUID programId;

    @JsonProperty("segmentId")
    public UUID segmentId;

    @JsonProperty("stepResults")
    public List<StepResult> stepResults = new ArrayList<>();

    public SubmitResultRequest() {
    }
}
