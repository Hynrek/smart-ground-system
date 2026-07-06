package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Projektion: ein abgeschlossenes Serie-Ergebnis eines registrierten Users.
 * Atom der Score-Verfolgung — Passe-/Wettkampf-Totale werden per Aggregation
 * berechnet, nie gespeichert. Quelle bleibt PlayInstance.stateJson bzw.
 * CompetitionSerieResult; diese Tabelle ist daraus wiederherstellbar.
 */
@Entity
@Table(
    name = "user_serie_scores",
    uniqueConstraints = @UniqueConstraint(columnNames = {"source_id", "user_id"}),
    indexes = {
        @Index(name = "idx_uss_user_completed", columnList = "user_id, completed_at DESC"),
        @Index(name = "idx_uss_serie_points", columnList = "serie_id, total_points DESC")
    })
@NullMarked
public class UserSerieScore {

    public UserSerieScore() {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 'TRAINING' | 'COMPETITION' */
    @Column(nullable = false, length = 12)
    private String context;

    @Column(name = "total_points", nullable = false)
    private int totalPoints;

    @Column(name = "max_points", nullable = false)
    private int maxPoints;

    /** Eigenständige Kopie der StepStates des Spielers (StepStateRecord[] als JSON). */
    @Column(name = "step_states_json", columnDefinition = "TEXT")
    @Nullable
    private String stepStatesJson;

    @Column(name = "serie_id", nullable = false)
    private UUID serieId;

    @Column(name = "serie_alias", nullable = false)
    private String serieAlias;

    /** Training: blockId; Wettkampf: CompetitionSerieResult-Id. Idempotenz-Schlüssel. */
    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    /** Gruppiert Trainings-Zeilen zu einer Passe. */
    @Column(name = "play_instance_id")
    @Nullable
    private UUID playInstanceId;

    @Column(name = "session_id")
    @Nullable
    private UUID sessionId;

    @Column(name = "group_id")
    @Nullable
    private UUID groupId;

    @Column(name = "passe_index")
    @Nullable
    private Integer passeIndex;

    /** Anzeige-Label des Parents: Training = templateName, Wettkampf = Session-Name. */
    @Column(name = "parent_name")
    @Nullable
    private String parentName;

    @Column(name = "range_id")
    @Nullable
    private UUID rangeId;

    @Column(name = "range_name")
    @Nullable
    private String rangeName;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }
    public int getMaxPoints() { return maxPoints; }
    public void setMaxPoints(int maxPoints) { this.maxPoints = maxPoints; }
    public @Nullable String getStepStatesJson() { return stepStatesJson; }
    public void setStepStatesJson(@Nullable String stepStatesJson) { this.stepStatesJson = stepStatesJson; }
    public UUID getSerieId() { return serieId; }
    public void setSerieId(UUID serieId) { this.serieId = serieId; }
    public String getSerieAlias() { return serieAlias; }
    public void setSerieAlias(String serieAlias) { this.serieAlias = serieAlias; }
    public UUID getSourceId() { return sourceId; }
    public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }
    public @Nullable UUID getPlayInstanceId() { return playInstanceId; }
    public void setPlayInstanceId(@Nullable UUID playInstanceId) { this.playInstanceId = playInstanceId; }
    public @Nullable UUID getSessionId() { return sessionId; }
    public void setSessionId(@Nullable UUID sessionId) { this.sessionId = sessionId; }
    public @Nullable UUID getGroupId() { return groupId; }
    public void setGroupId(@Nullable UUID groupId) { this.groupId = groupId; }
    public @Nullable Integer getPasseIndex() { return passeIndex; }
    public void setPasseIndex(@Nullable Integer passeIndex) { this.passeIndex = passeIndex; }
    public @Nullable String getParentName() { return parentName; }
    public void setParentName(@Nullable String parentName) { this.parentName = parentName; }
    public @Nullable UUID getRangeId() { return rangeId; }
    public void setRangeId(@Nullable UUID rangeId) { this.rangeId = rangeId; }
    public @Nullable String getRangeName() { return rangeName; }
    public void setRangeName(@Nullable String rangeName) { this.rangeName = rangeName; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
