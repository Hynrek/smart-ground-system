package ch.jp.shooting.service;

import ch.jp.shooting.model.BracketMatch;
import ch.jp.shooting.repository.BracketMatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service für Bracket-Progression: Gewinner vorrücken, nächste Matches vorbereiten.
 * Verwaltet den Bracket-Status während das Turnier läuft.
 */
@Service
@NullMarked
public class BracketProgressionService {
    private final BracketMatchRepository bracketMatchRepository;

    public BracketProgressionService(BracketMatchRepository bracketMatchRepository) {
        this.bracketMatchRepository = bracketMatchRepository;
    }

    /**
     * Recorddet den Gewinner eines Matches und bereitet den nächsten Match vor.
     * Automatically propagiert den Gewinner zum nächsten Round-Match.
     */
    @Transactional
    public void recordMatchWinner(UUID sessionId, int matchNumber, UUID winnerId,
                                  Integer score1, Integer score2) throws Exception {
        // Finde das Match
        BracketMatch match = bracketMatchRepository.findBySessionIdAndMatchNumber(sessionId, matchNumber)
            .orElseThrow(() -> new Exception("Match not found: " + matchNumber));

        // Validiere Winner
        if (!winnerId.equals(match.getContestant1Id()) && !winnerId.equals(match.getContestant2Id())) {
            throw new Exception("Winner must be one of the contestants");
        }

        // Setze Ergebnis
        match.setWinnerId(winnerId);
        match.setScore1(score1);
        match.setScore2(score2);
        match.setUpdatedAt(Instant.now());

        bracketMatchRepository.save(match);

        // Advance Winner zum nächsten Match
        advanceWinnerToNextRound(sessionId, match);
    }

    /**
     * Rückt einen Gewinner zum nächsten Match vor.
     * Logik:
     * - Finde Match in nächster Runde mit diesem Gewinner als Gegner
     * - Setze Contestant1 oder Contestant2
     * - Wenn beide Gegner bereit: Match kann gespielt werden
     */
    @Transactional
    public void advanceWinnerToNextRound(UUID sessionId, BracketMatch currentMatch) throws Exception {
        UUID winner = currentMatch.getWinnerId();
        if (winner == null) {
            return; // Kein Gewinner yet
        }

        int nextRound = currentMatch.getRoundNumber() + 1;
        List<BracketMatch> nextRoundMatches = bracketMatchRepository
            .findBySessionIdAndRoundNumberOrderByMatchNumber(sessionId, nextRound);

        if (nextRoundMatches.isEmpty()) {
            return; // Finale erreicht
        }

        // Finde den Match in der nächsten Runde, der auf diesen Gewinner wartet
        // Einfache Logik: Der Match-Index in der nächsten Runde basiert auf Position in dieser Runde
        int positionInCurrentRound = currentMatch.getMatchNumber() / 2;
        if (currentMatch.getMatchNumber() % 2 == 1) {
            // Ungerade Nummer → slot 1 des nächsten Matches
            if (positionInCurrentRound < nextRoundMatches.size()) {
                BracketMatch nextMatch = nextRoundMatches.get(positionInCurrentRound);
                nextMatch.setContestant1Id(winner);
                nextMatch.setUpdatedAt(Instant.now());
                bracketMatchRepository.save(nextMatch);
            }
        } else {
            // Gerade Nummer → slot 2 des nächsten Matches
            if (positionInCurrentRound < nextRoundMatches.size()) {
                BracketMatch nextMatch = nextRoundMatches.get(positionInCurrentRound);
                nextMatch.setContestant2Id(winner);
                nextMatch.setUpdatedAt(Instant.now());
                bracketMatchRepository.save(nextMatch);
            }
        }
    }

    /**
     * Prüft, ob eine Runde komplett gespielt wurde.
     */
    public boolean isRoundComplete(UUID sessionId, int roundNumber) {
        List<BracketMatch> roundMatches = bracketMatchRepository
            .findBySessionIdAndRoundNumberOrderByMatchNumber(sessionId, roundNumber);

        if (roundMatches.isEmpty()) {
            return true;
        }

        long unplayedCount = roundMatches.stream()
            .filter(m -> !m.isBye() && m.getWinnerId() == null)
            .count();

        return unplayedCount == 0;
    }

    /**
     * Findet das erste noch nicht gespielte Match.
     */
    public @Nullable BracketMatch findNextUnplayedMatch(UUID sessionId) {
        return bracketMatchRepository.findFirstUnplayedMatch(sessionId).orElse(null);
    }

    /**
     * Holt alle Matches einer bestimmten Runde.
     */
    public List<BracketMatch> getMatchesForRound(UUID sessionId, int roundNumber) {
        return bracketMatchRepository.findBySessionIdAndRoundNumberOrderByMatchNumber(sessionId, roundNumber);
    }

    /**
     * Zählt, wie viele Matches einer Runde bereits gespielt wurden.
     */
    public long getPlayedMatchesInRound(UUID sessionId, int roundNumber) {
        return bracketMatchRepository.countBySessionIdAndRoundNumberAndWinnerIdIsNotNull(sessionId, roundNumber);
    }

    /**
     * Bestimmt die nächste Runde, die gespielt werden kann.
     */
    public int getNextPlayableRound(UUID sessionId) {
        int roundNum = 1;
        while (isRoundComplete(sessionId, roundNum)) {
            roundNum++;
        }
        return roundNum;
    }
}
