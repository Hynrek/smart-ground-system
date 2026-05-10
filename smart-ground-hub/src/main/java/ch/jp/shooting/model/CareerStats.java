package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Spielerkarriere-Statistiken über alle Wettbewerbe hinweg.
 * Wird einmal beim Abschluss einer Sitzung aktualisiert.
 */
@Entity
@Table(name = "career_stats", indexes = {
    @Index(name = "idx_career_stats_user_id", columnList = "user_id"),
    @Index(name = "idx_career_stats_total_score", columnList = "total_score DESC")
})
@NullMarked
public class CareerStats {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Wettbewerbsergebnisse
    @Column(name = "total_wins", nullable = false)
    private int totalWins = 0;

    @Column(name = "participations", nullable = false)
    private int participations = 0;

    @Column(name = "total_score", nullable = false)
    private int totalScore = 0;

    @Column(name = "avg_score", nullable = false)
    private double avgScore = 0.0;

    @Column(name = "last_competed")
    private @Nullable Instant lastCompeted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // Getter & Setter
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public int getTotalWins() {
        return totalWins;
    }

    public void setTotalWins(int totalWins) {
        this.totalWins = totalWins;
    }

    public int getParticipations() {
        return participations;
    }

    public void setParticipations(int participations) {
        this.participations = participations;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }

    public double getAvgScore() {
        return avgScore;
    }

    public void setAvgScore(double avgScore) {
        this.avgScore = avgScore;
    }

    public @Nullable Instant getLastCompeted() {
        return lastCompeted;
    }

    public void setLastCompeted(@Nullable Instant lastCompeted) {
        this.lastCompeted = lastCompeted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
