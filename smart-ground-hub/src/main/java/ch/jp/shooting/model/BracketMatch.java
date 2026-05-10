package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Einzelnes Match in einem Eliminierungs-Turnier (Single/Double-Elimination).
 * Verfolgt Gegner, Gewinner und Scores für jedes Match.
 */
@Entity
@Table(name = "bracket_matches", indexes = {
    @Index(name = "idx_bracket_matches_session", columnList = "session_id"),
    @Index(name = "idx_bracket_matches_round", columnList = "session_id,round_number")
})
@NullMarked
public class BracketMatch {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /**
     * Eindeutige Match-Nummer pro Session (z.B. 1, 2, 3, ... oder nach Runde: 1-1, 1-2, 2-1)
     */
    @Column(name = "match_number", nullable = false)
    private int matchNumber;

    /**
     * Runde im Bracket (1 = Viertelfinale, 2 = Halbfinale, 3 = Finale)
     */
    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    /**
     * Erster Gegner (null = Freilos)
     */
    @Column(name = "contestant1_id")
    private @Nullable UUID contestant1Id;

    /**
     * Zweiter Gegner (null = Freilos oder wartet auf Aufsteiger)
     */
    @Column(name = "contestant2_id")
    private @Nullable UUID contestant2Id;

    /**
     * Gewinner des Matches (null = noch nicht gespielt)
     */
    @Column(name = "winner_id")
    private @Nullable UUID winnerId;

    /**
     * Score des ersten Gegners
     */
    @Column(name = "score_1")
    private @Nullable Integer score1;

    /**
     * Score des zweiten Gegners
     */
    @Column(name = "score_2")
    private @Nullable Integer score2;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // ── Constructors ──
    public BracketMatch() {
    }

    public BracketMatch(UUID sessionId, int matchNumber, int roundNumber,
                        @Nullable UUID contestant1Id, @Nullable UUID contestant2Id) {
        this.sessionId = sessionId;
        this.matchNumber = matchNumber;
        this.roundNumber = roundNumber;
        this.contestant1Id = contestant1Id;
        this.contestant2Id = contestant2Id;
    }

    // ── Accessors ──
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public int getMatchNumber() {
        return matchNumber;
    }

    public void setMatchNumber(int matchNumber) {
        this.matchNumber = matchNumber;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public @Nullable UUID getContestant1Id() {
        return contestant1Id;
    }

    public void setContestant1Id(@Nullable UUID contestant1Id) {
        this.contestant1Id = contestant1Id;
    }

    public @Nullable UUID getContestant2Id() {
        return contestant2Id;
    }

    public void setContestant2Id(@Nullable UUID contestant2Id) {
        this.contestant2Id = contestant2Id;
    }

    public @Nullable UUID getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(@Nullable UUID winnerId) {
        this.winnerId = winnerId;
    }

    public @Nullable Integer getScore1() {
        return score1;
    }

    public void setScore1(@Nullable Integer score1) {
        this.score1 = score1;
    }

    public @Nullable Integer getScore2() {
        return score2;
    }

    public void setScore2(@Nullable Integer score2) {
        this.score2 = score2;
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

    // ── Helper Methods ──

    /**
     * Prüft, ob das Match ein Freilos ist (ein Gegner fehlt).
     */
    public boolean isBye() {
        return (contestant1Id == null && contestant2Id != null) ||
               (contestant1Id != null && contestant2Id == null);
    }

    /**
     * Setzt das Match als Freilos (ein Gegner wird auf null gesetzt).
     */
    public void setBye(boolean bye) {
        if (bye && !isBye()) {
            if (contestant1Id != null) {
                contestant2Id = null;
            } else if (contestant2Id != null) {
                contestant1Id = null;
            }
        }
    }

    /**
     * Prüft, ob das Match gespielt wurde.
     */
    public boolean isPlayed() {
        return winnerId != null;
    }

    /**
     * Prüft, ob das Match noch ausstehend ist.
     */
    public boolean isPending() {
        return !isPlayed() && contestant1Id != null && contestant2Id != null;
    }
}
