package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Detailliertes Serie-Ergebnis einer Rotte inkl. der rohen Play-Ergebnisse
 * (Spieler-Punkte und Schritt-Zustände), wie sie beim Abschluss persistiert wurden.
 * Dient der Schritt-für-Schritt-Auswertung abgeschlossener Wettkämpfe.
 */
@NullMarked
public class CompetitionSerieResultDetailResponse {
    @JsonProperty("groupId")        public UUID groupId;
    @JsonProperty("passeIndex")     public int passeIndex;
    @JsonProperty("serieId")        public UUID serieId;
    @JsonProperty("playInstanceId") @Nullable public UUID playInstanceId;
    @JsonProperty("completedAt")    public Instant completedAt;

    /** Rohe Play-Ergebnisse als geparstes JSON (Array von Spielern mit stepStates). */
    @JsonProperty("results")        @Nullable public JsonNode results;

    /** Eingefrorene, aufgelöste Serie-Definition zum Abschlusszeitpunkt (Name, Platz, Step-Buchstaben). */
    @JsonProperty("serieSnapshot")  @Nullable public JsonNode serieSnapshot;

    public CompetitionSerieResultDetailResponse() {}
}
