package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Response mit Spieler-Ergebnissen.
 */
@NullMarked
public class PlayerResultResponse {
    @JsonProperty("id")
    public UUID id;

    @JsonProperty("playerId")
    public UUID playerId;

    @JsonProperty("displayName")
    public String displayName;

    @JsonProperty("programResults")
    @Nullable
    public String programResults; // JSON: ProgramResult[]

    @JsonProperty("totalScore")
    public int totalScore; // Computed: summe aller Punkte

    @JsonProperty("maxScore")
    public int maxScore; // Computed: summe aller verfügbaren Punkte

    @JsonProperty("completionPct")
    public int completionPct; // Computed: Prozentsatz abgeschlossener Schritte

    @JsonProperty("createdAt")
    public Instant createdAt;

    @JsonProperty("updatedAt")
    public Instant updatedAt;

    public PlayerResultResponse() {
    }
}
