package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Akkumulierte Ergebnisse für einen Spieler in einer Session.
 * Speichert Programme, Segmente und Schritte als JSON für Flexibilität.
 * Format: ProgramResult[] ({programId, segmentResults[]})
 */
@Entity
@Table(name = "player_results")
@NullMarked
public class PlayerResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private LiveSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private SessionPlayer player;

    /**
     * Ergebnisse pro Programm als JSON.
     * Format: ProgramResult[] ({
     *   programId,
     *   segmentResults: [{
     *     segmentId,
     *     groupId,
     *     stepResults: [{stepId, state, noBirds, pointsEarned, corrections[]}],
     *     score,
     *     maxScore
     *   }]
     * })
     */
    @Column(name = "program_results", columnDefinition = "TEXT")
    @Nullable
    private String programResults;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // ── Constructors ──
    public PlayerResult() {
    }

    public PlayerResult(LiveSession session, SessionPlayer player) {
        this.session = session;
        this.player = player;
    }

    // ── Accessors ──
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LiveSession getSession() {
        return session;
    }

    public void setSession(LiveSession session) {
        this.session = session;
    }

    public SessionPlayer getPlayer() {
        return player;
    }

    public void setPlayer(SessionPlayer player) {
        this.player = player;
    }

    @Nullable
    public String getProgramResults() {
        return programResults;
    }

    public void setProgramResults(@Nullable String programResults) {
        this.programResults = programResults;
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
