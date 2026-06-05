package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Abgeschlossene Serie für eine Rotte in einem Wettkampf.
 * Eine Zeile pro (session, group, passeIndex, serieId).
 */
@Entity
@Table(
    name = "competition_serie_results",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"session_id", "group_id", "passe_index", "serie_id"}
    )
)
@NullMarked
public class CompetitionSerieResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private LiveSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ShooterGroup group;

    @Column(name = "passe_index", nullable = false)
    private int passeIndex;

    @Column(name = "serie_id", nullable = false)
    private UUID serieId;

    @Column(name = "play_instance_id")
    @Nullable
    private UUID playInstanceId;

    /**
     * Rohergebnisse vom Play als JSON.
     * Format: { "players": [{ "playerId": "uuid", "totalPoints": 8, "maxPoints": 10 }] }
     */
    @Column(name = "results", columnDefinition = "TEXT")
    @Nullable
    private String results;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt = Instant.now();

    public CompetitionSerieResult() {}

    public CompetitionSerieResult(LiveSession session, ShooterGroup group, int passeIndex, UUID serieId) {
        this.session = session;
        this.group = group;
        this.passeIndex = passeIndex;
        this.serieId = serieId;
    }

    public UUID getId() { return id; }
    public LiveSession getSession() { return session; }
    public void setSession(LiveSession session) { this.session = session; }
    public ShooterGroup getGroup() { return group; }
    public void setGroup(ShooterGroup group) { this.group = group; }
    public int getPasseIndex() { return passeIndex; }
    public void setPasseIndex(int passeIndex) { this.passeIndex = passeIndex; }
    public UUID getSerieId() { return serieId; }
    public void setSerieId(UUID serieId) { this.serieId = serieId; }
    @Nullable public UUID getPlayInstanceId() { return playInstanceId; }
    public void setPlayInstanceId(@Nullable UUID playInstanceId) { this.playInstanceId = playInstanceId; }
    @Nullable public String getResults() { return results; }
    public void setResults(@Nullable String results) { this.results = results; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
