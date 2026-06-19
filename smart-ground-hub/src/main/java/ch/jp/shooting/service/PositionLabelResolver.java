package ch.jp.shooting.service;

import ch.jp.shooting.model.RangePosition;
import ch.jp.shooting.repository.RangePositionRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

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
}
