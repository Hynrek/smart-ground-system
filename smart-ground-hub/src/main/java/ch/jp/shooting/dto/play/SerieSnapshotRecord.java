package ch.jp.shooting.dto.play;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Eingefrorene Serie-Definition zum Abschlusszeitpunkt eines Wettkampf-Serie-Ergebnisses.
 * Hält die zur Anzeige aufgelösten Werte (Serie-Name, Platz-Name, Step-Buchstaben),
 * damit ein späteres Umbenennen einer Position eine abgeschlossene Wertung nicht verändert.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SerieSnapshotRecord(
    String serieName,
    @Nullable String rangeName,
    List<StepRecord> steps
) {}
