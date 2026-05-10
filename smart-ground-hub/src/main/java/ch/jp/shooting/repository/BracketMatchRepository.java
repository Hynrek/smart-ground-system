package ch.jp.shooting.repository;

import ch.jp.shooting.model.BracketMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository für Bracket-Matches in Eliminierungsturnieren.
 */
@Repository
public interface BracketMatchRepository extends JpaRepository<BracketMatch, UUID> {

    /**
     * Findet alle Matches einer Sitzung.
     */
    List<BracketMatch> findBySessionIdOrderByMatchNumber(UUID sessionId);

    /**
     * Findet alle Matches einer bestimmten Runde.
     */
    List<BracketMatch> findBySessionIdAndRoundNumberOrderByMatchNumber(UUID sessionId, int roundNumber);

    /**
     * Findet ein spezifisches Match nach Session und Match-Nummer.
     */
    Optional<BracketMatch> findBySessionIdAndMatchNumber(UUID sessionId, int matchNumber);

    /**
     * Findet das erste noch nicht gespielte Match einer Session (für Operator-Dashboard).
     */
    @Query("SELECT m FROM BracketMatch m WHERE m.sessionId = ?1 AND m.winnerId IS NULL " +
           "AND m.contestant1Id IS NOT NULL AND m.contestant2Id IS NOT NULL " +
           "ORDER BY m.roundNumber ASC, m.matchNumber ASC LIMIT 1")
    Optional<BracketMatch> findFirstUnplayedMatch(UUID sessionId);

    /**
     * Zählt die gespielten Matches einer Runde.
     */
    long countBySessionIdAndRoundNumberAndWinnerIdIsNotNull(UUID sessionId, int roundNumber);

    /**
     * Findet alle Matches mit einem bestimmten Spieler als Gegner.
     */
    List<BracketMatch> findBySessionIdAndContestant1IdOrSessionIdAndContestant2Id(
        UUID sessionId1, UUID contestant1Id,
        UUID sessionId2, UUID contestant2Id
    );
}
