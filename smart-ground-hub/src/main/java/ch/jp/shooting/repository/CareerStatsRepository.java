package ch.jp.shooting.repository;

import ch.jp.shooting.model.CareerStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository für Spielerkarriere-Statistiken.
 */
@Repository
public interface CareerStatsRepository extends JpaRepository<CareerStats, UUID> {

    /**
     * Findet die Karriere-Statistiken für einen bestimmten Benutzer.
     */
    Optional<CareerStats> findByUserId(UUID userId);

    /**
     * Gibt ein Leaderboard mit Top-Spielern nach Gesamtpunkten (absteigend).
     */
    @Query("SELECT c FROM CareerStats c ORDER BY c.totalScore DESC")
    Page<CareerStats> findLeaderboard(Pageable pageable);

    /**
     * Gibt ein Leaderboard mit Top-Spielern nach Siegen (absteigend).
     */
    @Query("SELECT c FROM CareerStats c ORDER BY c.totalWins DESC")
    Page<CareerStats> findLeaderboardByWins(Pageable pageable);
}
