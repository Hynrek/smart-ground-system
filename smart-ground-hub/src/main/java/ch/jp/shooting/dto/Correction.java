package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit-Trail für Korrektur eines Schrittergebnisses.
 */
@NullMarked
public class Correction {
    @JsonProperty("id")
    public UUID id;

    @JsonProperty("type")
    public String type; // 'hit-miss' (einziger Admin-bearbeitbarer Typ)

    @JsonProperty("oldState")
    public String oldState;

    @JsonProperty("newState")
    public String newState;

    @JsonProperty("correctedBy")
    public UUID correctedBy; // userId des Admins

    @JsonProperty("correctedAt")
    public Instant correctedAt;

    public Correction() {
    }

    public Correction(UUID id, String type, String oldState, String newState, UUID correctedBy) {
        this.id = id;
        this.type = type;
        this.oldState = oldState;
        this.newState = newState;
        this.correctedBy = correctedBy;
        this.correctedAt = Instant.now();
    }
}
