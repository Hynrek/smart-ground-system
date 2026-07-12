package ch.jp.shooting.service;

import ch.jp.shooting.model.CareerStats;
import ch.jp.shooting.repository.CareerStatsRepository;
import org.springframework.stereotype.Service;
import org.jspecify.annotations.NullMarked;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hilfservice für Tiebreaker-Auflösung bei Spieler-Rankings.
 * Unterstützt verschiedene Kriterien zur Sortierung von Spielern.
 */
@Service
@NullMarked
public class TiebreakerResolver {
    private final CareerStatsRepository careerStatsRepository;

    public TiebreakerResolver(CareerStatsRepository careerStatsRepository) {
        this.careerStatsRepository = careerStatsRepository;
    }

    /**
     * Kriterien für Tiebreaker-Auflösung.
     */
    public enum TiebreakerCriteria {
        TOTAL_SCORE,    // Gesamtpunkte aus Karriere-Statistiken
        WIN_PERCENT,    // Gewinn-Prozentsatz (Siege / Teilnahmen)
        HEAD_TO_HEAD,   // Direkter Vergleich (TODO: erfordert Match-Historie)
        AVG_SCORE,      // Durchschnittliche Punkte pro Turnier
        WINS            // Absolute Siegesanzahl
    }

    /**
     * Sortiert eine Liste von Spielern nach konfigurierten Tiebreaker-Kriterien.
     * Wendet Kriterien in Reihenfolge an, bis alle Spieler eindeutig sortiert sind.
     *
     * @param playerIds Liste von Spieler-UUIDs
     * @param criteria Priorisierte Liste von Tiebreaker-Kriterien
     * @return Sortierte Liste (höchstwertig zuerst)
     */
    public List<UUID> resolveTiebreaker(List<UUID> playerIds, List<TiebreakerCriteria> criteria) {
        if (playerIds.isEmpty()) {
            return playerIds;
        }

        List<TiebreakerCriteria> finalCriteria = criteria;
        if (finalCriteria == null || finalCriteria.isEmpty()) {
            // Fallback: Nach TOTAL_SCORE sortieren
            finalCriteria = Arrays.asList(TiebreakerCriteria.TOTAL_SCORE);
        }

        // Lade Karriere-Statistiken für alle Spieler
        Map<UUID, CareerStats> statsMap = new HashMap<>();
        for (UUID playerId : playerIds) {
            careerStatsRepository.findByUserId(playerId).ifPresent(stats -> {
                statsMap.put(playerId, stats);
            });
        }

        // Sortiere nach Kriterien (in Reihenfolge)
        List<UUID> sorted = new ArrayList<>(playerIds);
        final List<TiebreakerCriteria> sortCriteria = finalCriteria;
        sorted.sort((id1, id2) -> {
            for (TiebreakerCriteria criterion : sortCriteria) {
                int comparison = compareByCriteria(id1, id2, criterion, statsMap);
                if (comparison != 0) {
                    return comparison; // Aufsteigend: beste zuerst
                }
            }
            return 0; // Gleich
        });

        return sorted;
    }

    /**
     * Vergleicht zwei Spieler nach einem einzelnen Kriterium.
     * Negative Werte: id1 besser, Positive: id2 besser, 0: gleich
     */
    private int compareByCriteria(UUID id1, UUID id2, TiebreakerCriteria criterion,
                                    Map<UUID, CareerStats> statsMap) {
        CareerStats stats1 = statsMap.getOrDefault(id1, new CareerStats());
        CareerStats stats2 = statsMap.getOrDefault(id2, new CareerStats());

        switch (criterion) {
            case TOTAL_SCORE:
                return Integer.compare(stats2.getTotalScore(), stats1.getTotalScore());
            case WINS:
                return Integer.compare(stats2.getTotalWins(), stats1.getTotalWins());
            case AVG_SCORE:
                return Double.compare(stats2.getAvgScore(), stats1.getAvgScore());
            case WIN_PERCENT:
                double winPct1 = stats1.getParticipations() > 0
                    ? (double) stats1.getTotalWins() / stats1.getParticipations() : 0;
                double winPct2 = stats2.getParticipations() > 0
                    ? (double) stats2.getTotalWins() / stats2.getParticipations() : 0;
                return Double.compare(winPct2, winPct1);
            case HEAD_TO_HEAD:
                // TODO: Implementieren sobald Match-Historie verfügbar
                return 0;
            default:
                return 0;
        }
    }
}
