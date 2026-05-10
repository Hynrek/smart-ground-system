package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * Request zum Korrigieren eines Schrittergebnisses (nur Admin).
 */
@NullMarked
public class CorrectionRequest {
    @JsonProperty("playerId")
    public UUID playerId;

    @JsonProperty("programId")
    public UUID programId;

    @JsonProperty("segmentId")
    public UUID segmentId;

    @JsonProperty("stepId")
    public String stepId;

    @JsonProperty("oldState")
    public String oldState;

    @JsonProperty("newState")
    public String newState;

    public CorrectionRequest() {
    }
}
