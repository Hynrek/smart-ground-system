package ch.jp.shooting.dto.play;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * Ergebnis eines einzelnen Schritts für einen Spieler innerhalb eines abgeschlossenen Blocks.
 * type/letter/letter1/letter2 sind Best-Effort-Anreicherung aus der Serie-Definition zum
 * Aufzeichnungszeitpunkt (siehe UserScoreService) — bei älteren, bereits persistierten Zeilen
 * bleiben sie null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StepStateRecord(
    String playerId,
    int serieIndex,
    int stepIndex,
    String state,
    int pointValue,
    int noBirds,
    int pointsEarned,
    @Nullable String type,
    @Nullable String letter,
    @Nullable String letter1,
    @Nullable String letter2
) {}
