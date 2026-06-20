package ch.jp.shooting.service;

import ch.jp.shooting.dto.play.StepRecord;
import ch.jp.shooting.model.RangePosition;
import ch.jp.shooting.repository.RangePositionRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Löst Positions-IDs (UUID-Strings aus Step.posId) auf die aktuellen
 * RangePosition-Entitäten auf. Quelle der Wahrheit für Buchstabe (label)
 * und Geräte-Alias zur Anzeige-Zeit.
 */
@Service
@NullMarked
public class PositionLabelResolver {

    private final RangePositionRepository positionRepository;

    public PositionLabelResolver(RangePositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    /**
     * Lädt die Positionen für die gegebenen posId-Strings (UUIDs) als Map
     * posIdString → RangePosition. Null/leer/ungültige IDs werden ignoriert;
     * unbekannte IDs fehlen schlicht in der Map (Anzeige fällt auf Platzhalter zurück).
     */
    public Map<String, RangePosition> byPosIds(Collection<String> posIds) {
        var ids = posIds.stream()
            .filter(s -> s != null && !s.isBlank())
            .map(PositionLabelResolver::parseOrNull)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (ids.isEmpty()) return Map.of();
        return positionRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(p -> p.getId().toString(), p -> p));
    }

    private static @Nullable UUID parseOrNull(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Anzeige-Alias einer Position: Geräte-Alias, sonst der Positions-Buchstabe (label).
     * Einzige Quelle der Wahrheit für die Alias-Auflösung (von SerieService und PasseService genutzt).
     */
    public static String aliasOf(RangePosition position) {
        var device = position.getDevice();
        return device != null && device.getAlias() != null && !device.getAlias().isBlank()
            ? device.getAlias()
            : position.getLabel();
    }

    /**
     * Löst die Positions-Labels einer Step-Liste in EINEM Lookup auf und gibt neue
     * StepRecords mit aktualisierten letter/alias-Werten zurück (posId/type bleiben erhalten).
     */
    public List<StepRecord> resolveSteps(List<StepRecord> steps) {
        var posIds = steps.stream()
            .flatMap(s -> Stream.of(s.posId(), s.posId1(), s.posId2()))
            .filter(Objects::nonNull)
            .toList();
        var positions = byPosIds(posIds);
        return steps.stream().map(s -> resolveStep(s, positions)).toList();
    }

    /** Erzeugt einen Step mit live aufgelösten letter/alias-Werten aus der gegebenen Positions-Map. */
    public static StepRecord resolveStep(StepRecord s, Map<String, RangePosition> positions) {
        var p  = s.posId()  != null ? positions.get(s.posId())  : null;
        var p1 = s.posId1() != null ? positions.get(s.posId1()) : null;
        var p2 = s.posId2() != null ? positions.get(s.posId2()) : null;
        return new StepRecord(
            s.id(),
            s.type(),
            s.posId(),
            p  != null ? aliasOf(p)  : null,
            s.posId1(),
            s.posId2(),
            p1 != null ? aliasOf(p1) : null,
            p2 != null ? aliasOf(p2) : null,
            p  != null ? p.getLabel()  : null,
            p1 != null ? p1.getLabel() : null,
            p2 != null ? p2.getLabel() : null
        );
    }
}
