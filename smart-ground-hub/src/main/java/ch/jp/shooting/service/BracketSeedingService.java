package ch.jp.shooting.service;

import ch.jp.shooting.model.BracketMatch;
import ch.jp.shooting.model.SessionPlayer;
import ch.jp.shooting.repository.BracketMatchRepository;
import org.springframework.stereotype.Service;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service für Bracket-Generierung und Spieler-Seeding.
 * Unterstützt Single-Elimination und Double-Elimination Bracket-Typen.
 */
@Service
@NullMarked
public class BracketSeedingService {
    private final TiebreakerResolver tiebreakerResolver;
    private final BracketMatchRepository bracketMatchRepository;

    public BracketSeedingService(TiebreakerResolver tiebreakerResolver,
                                BracketMatchRepository bracketMatchRepository) {
        this.tiebreakerResolver = tiebreakerResolver;
        this.bracketMatchRepository = bracketMatchRepository;
    }

    /**
     * Seeding-Strategien für Bracket-Generierung.
     */
    public enum SeedingStrategy {
        BY_CAREER_STATS,  // Nach Karriere-Statistiken sortieren
        MANUAL,           // Manuelle Seeding-Reihenfolge (bereits sortierte Liste)
        BALANCED          // Alternating (Seed 1 vs HighBye, Seed 2 vs 2ndHighBye, etc.)
    }

    /**
     * Ergebnis einer Bracket-Generierung.
     */
    public static class SeededBracketResult {
        public List<BracketRound> rounds;      // Runden mit Matches
        public List<SeededPlayer> seededPlayers;     // Sortierte Spieler mit Seeding
        public int totalByes;                         // Anzahl der Frelose in Runde 1
        public Map<String, Object> metadata;         // Zusätzliche Informationen

        public SeededBracketResult() {
            this.rounds = new ArrayList<>();
            this.seededPlayers = new ArrayList<>();
            this.metadata = new HashMap<>();
        }
    }

    /**
     * Repräsentation einer Runde im Bracket.
     */
    public static class BracketRound {
        public int roundNumber;
        public List<BracketMatchSlot> matches;

        public BracketRound(int roundNumber) {
            this.roundNumber = roundNumber;
            this.matches = new ArrayList<>();
        }
    }

    /**
     * Ein Match-Slot im Bracket.
     */
    public static class BracketMatchSlot {
        public int matchNumber;
        public @Nullable UUID contestant1;  // null = Freilos
        public @Nullable UUID contestant2;  // null = wartet auf Aufsteiger
        public boolean isBye;               // true wenn Freilos

        public BracketMatchSlot(int matchNumber, @Nullable UUID contestant1, @Nullable UUID contestant2) {
            this.matchNumber = matchNumber;
            this.contestant1 = contestant1;
            this.contestant2 = contestant2;
            this.isBye = (contestant1 == null && contestant2 != null) ||
                         (contestant1 != null && contestant2 == null);
        }
    }

    /**
     * Ein gesäter Spieler mit Position.
     */
    public static class SeededPlayer {
        public UUID playerId;
        public int seed;          // 1, 2, 3, ... (1 = beste)
        public String displayName;

        public SeededPlayer(UUID playerId, int seed, String displayName) {
            this.playerId = playerId;
            this.seed = seed;
            this.displayName = displayName;
        }
    }

    /**
     * Generiert ein Single-Elimination-Bracket.
     */
    public SeededBracketResult generateSingleElimination(
            List<SessionPlayer> players,
            SeedingStrategy strategy,
            @Nullable List<TiebreakerResolver.TiebreakerCriteria> tiebreakers) {

        // Schritt 1: Spieler nach Strategie sortieren
        List<SessionPlayer> seeded = seedPlayers(players, strategy, tiebreakers);

        // Schritt 2: Bye-Positionen berechnen
        int playerCount = seeded.size();
        int nextPowerOf2 = (int) Math.pow(2, Math.ceil(Math.log(playerCount) / Math.log(2)));
        int byeCount = nextPowerOf2 - playerCount;

        // Schritt 3: Bracket aufbauen
        SeededBracketResult result = new SeededBracketResult();
        result.seededPlayers = seeded.stream()
            .mapToInt(p -> seeded.indexOf(p) + 1)
            .mapToObj(i -> new SeededPlayer(seeded.get(i - 1).getId(), i, seeded.get(i - 1).getDisplayName()))
            .collect(Collectors.toList());

        result.totalByes = byeCount;

        // Schritt 4: Erste Runde mit Freylosen
        BracketRound round1 = new BracketRound(1);
        int matchNum = 1;
        List<UUID> round1Winners = new ArrayList<>();

        for (int i = 0; i < nextPowerOf2; i += 2) {
            UUID player1 = i < seeded.size() ? seeded.get(i).getId() : null;
            UUID player2 = (i + 1) < seeded.size() ? seeded.get(i + 1).getId() : null;

            BracketMatchSlot match = new BracketMatchSlot(matchNum++, player1, player2);
            round1.matches.add(match);

            // Für Freilose: auto-advance
            if (match.isBye) {
                round1Winners.add(player1 != null ? player1 : player2);
            }
        }
        result.rounds.add(round1);

        // Schritt 5: Weitere Runden generieren
        List<UUID> currentRoundWinners = round1Winners;
        int currentRound = 2;

        while (currentRoundWinners.size() > 1) {
            BracketRound round = new BracketRound(currentRound);
            matchNum = (currentRound > 2) ? 1 : round1.matches.size() + 1; // Vereinfacht

            for (int i = 0; i < currentRoundWinners.size(); i += 2) {
                UUID player1 = currentRoundWinners.get(i);
                UUID player2 = (i + 1) < currentRoundWinners.size() ? currentRoundWinners.get(i + 1) : null;

                BracketMatchSlot match = new BracketMatchSlot(matchNum++, player1, player2);
                round.matches.add(match);
            }
            result.rounds.add(round);
            currentRound++;
            // TODO: Aus Match-Ergebnissen berechnen - hier nur Skelett
        }

        return result;
    }

    /**
     * Generiert ein Double-Elimination-Bracket (komplexer - Basis-Implementierung).
     */
    public SeededBracketResult generateDoubleElimination(
            List<SessionPlayer> players,
            SeedingStrategy strategy,
            @Nullable List<TiebreakerResolver.TiebreakerCriteria> tiebreakers) {

        // Basis: Starte mit Single-Elimination für Winners Bracket
        SeededBracketResult winnersResult = generateSingleElimination(players, strategy, tiebreakers);

        // TODO: Losers Bracket generieren
        // - Spieler, die in Winners Bracket verlieren, gehen in Losers
        // - Losers Bracket hat Doppel-Ellimination Struktur
        // - Grand Final: Winner vs Winner

        return winnersResult;  // Placeholder
    }

    // ── Private Hilfsmetoden ──

    /**
     * Sortiert Spieler nach angegebener Strategie.
     */
    private List<SessionPlayer> seedPlayers(
            List<SessionPlayer> players,
            SeedingStrategy strategy,
            @Nullable List<TiebreakerResolver.TiebreakerCriteria> tiebreakers) {

        if (strategy == SeedingStrategy.MANUAL) {
            // Liste ist bereits sortiert
            return players;
        }

        if (strategy == SeedingStrategy.BY_CAREER_STATS) {
            // Nach Karriere-Statistiken sortieren
            List<UUID> playerIds = players.stream().map(SessionPlayer::getId).collect(Collectors.toList());
            List<TiebreakerResolver.TiebreakerCriteria> criteria =
                tiebreakers != null ? tiebreakers : Arrays.asList(TiebreakerResolver.TiebreakerCriteria.TOTAL_SCORE);

            List<UUID> sortedIds = tiebreakerResolver.resolveTiebreaker(playerIds, criteria);

            return sortedIds.stream()
                .map(id -> players.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }

        if (strategy == SeedingStrategy.BALANCED) {
            // Alternating high/low seeds (snake/alternating seeding)
            // Pattern: Seed 1, Last, Seed 2, Second-Last, Seed 3, Third-Last, ...
            // This prevents top seeds from facing each other early

            List<SessionPlayer> sorted = new ArrayList<>(players);

            // Zuerst nach Karriere-Statistiken sortieren (wie BY_CAREER_STATS)
            List<UUID> playerIds = sorted.stream().map(SessionPlayer::getId).collect(Collectors.toList());
            List<TiebreakerResolver.TiebreakerCriteria> criteria =
                tiebreakers != null ? tiebreakers : java.util.Arrays.asList(TiebreakerResolver.TiebreakerCriteria.TOTAL_SCORE);

            List<UUID> sortedIds = tiebreakerResolver.resolveTiebreaker(playerIds, criteria);

            // Erstelle eine temporäre Liste für die Umsortierung
            List<SessionPlayer> seedSorted = sortedIds.stream()
                .map(id -> sorted.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            // Wende Balanced-Seeding an
            List<SessionPlayer> balanced = new ArrayList<>();
            int left = 0;
            int right = seedSorted.size() - 1;
            boolean pickFromLeft = true;

            while (left <= right) {
                if (pickFromLeft) {
                    balanced.add(seedSorted.get(left));
                    left++;
                } else {
                    balanced.add(seedSorted.get(right));
                    right--;
                }
                pickFromLeft = !pickFromLeft;
            }

            return balanced;
        }

        return players;
    }
}
