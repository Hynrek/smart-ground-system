package ch.jp.shooting.service;

import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Reine Tie-Break-Logik (kein Spring/JPA-State): Erkennt Punktgleichheit und ordnet
 * gleichstehende Blöcke anhand der Stechen-Runden. Die Reihenfolge innerhalb eines
 * Blocks wird durch die erste Runde bestimmt, die die Spieler trennt.
 */
@Component
@NullMarked
public class TieResolver {

    public record PlayerStanding(UUID playerId, String displayName, int totalScore, int maxScore) {}

    public record TiebreakerRound(UUID tieGroupId, int roundNumber,
                                  List<UUID> participantIds, Map<UUID, Integer> scoresByPlayer) {}

    public record ResolvedStanding(UUID playerId, String displayName, int totalScore, int maxScore,
                                   int rank, boolean tied, boolean tieResolvedByStechen) {}

    public List<ResolvedStanding> resolve(List<PlayerStanding> standings, List<TiebreakerRound> rounds) {
        // 1) Nach Hauptpunkten absteigend sortieren.
        List<PlayerStanding> sorted = new ArrayList<>(standings);
        sorted.sort(Comparator.comparingInt(PlayerStanding::totalScore).reversed());

        // 2) Stechen-Runden chronologisch ordnen.
        List<TiebreakerRound> orderedRounds = new ArrayList<>(rounds);
        orderedRounds.sort(Comparator.comparingInt(TiebreakerRound::roundNumber));

        List<ResolvedStanding> result = new ArrayList<>();
        int i = 0;
        int rank = 1;
        while (i < sorted.size()) {
            // Block gleicher Hauptpunkte finden.
            int j = i;
            while (j + 1 < sorted.size() && sorted.get(j + 1).totalScore() == sorted.get(i).totalScore()) {
                j++;
            }
            List<PlayerStanding> block = sorted.subList(i, j + 1);

            if (block.size() == 1) {
                PlayerStanding p = block.get(0);
                result.add(new ResolvedStanding(p.playerId(), p.displayName(), p.totalScore(),
                        p.maxScore(), rank, false, false));
            } else {
                resolveBlock(block, orderedRounds, rank, result);
            }
            rank += block.size(); // Standard-Wettkampf-Ranking: nächster Rang springt um Blockgröße.
            i = j + 1;
        }
        return result;
    }

    /** Ordnet einen punktgleichen Block anhand der Stechen-Runden. */
    private void resolveBlock(List<PlayerStanding> block, List<TiebreakerRound> rounds,
                              int baseRank, List<ResolvedStanding> out) {
        Set<UUID> blockIds = new HashSet<>();
        for (PlayerStanding p : block) blockIds.add(p.playerId());

        // Nur Runden, die (auch) Spieler dieses Blocks betreffen.
        List<TiebreakerRound> relevant = rounds.stream()
                .filter(r -> r.participantIds().stream().anyMatch(blockIds::contains))
                .toList();
        boolean hasStechen = !relevant.isEmpty();

        // Vergleichsschlüssel je Spieler: Stechen-Scores in Rundenreihenfolge (höher = besser).
        // Fehlt ein Score in einer Runde, zählt diese Runde nicht weiter — ein früherer
        // trennender Score hat dann bereits entschieden.
        Comparator<PlayerStanding> byStechen = (x, y) -> {
            for (TiebreakerRound r : relevant) {
                Integer sx = r.scoresByPlayer().get(x.playerId());
                Integer sy = r.scoresByPlayer().get(y.playerId());
                if (sx == null || sy == null) continue;
                int cmp = Integer.compare(sy, sx); // absteigend
                if (cmp != 0) return cmp;
            }
            return 0; // weiterhin gleich
        };

        List<PlayerStanding> ordered = new ArrayList<>(block);
        ordered.sort(byStechen);

        // Ränge innerhalb des Blocks; identischer Stechen-Schlüssel => geteilter Rang, bleibt tied.
        int idx = 0;
        while (idx < ordered.size()) {
            int k = idx;
            while (k + 1 < ordered.size() && byStechen.compare(ordered.get(idx), ordered.get(k + 1)) == 0) {
                k++;
            }
            boolean sharedRank = (k > idx);
            int rankHere = baseRank + idx;
            for (int m = idx; m <= k; m++) {
                PlayerStanding p = ordered.get(m);
                out.add(new ResolvedStanding(p.playerId(), p.displayName(), p.totalScore(),
                        p.maxScore(), rankHere, sharedRank, hasStechen));
            }
            idx = k + 1;
        }
    }
}
