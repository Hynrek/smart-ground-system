package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Eine Stechen-Runde für einen punktgleichen Block in einem Wettkampf.
 * Ergebnisse werden bewusst NICHT in PlayerResult geschrieben — sie ordnen nur den
 * gleichstehenden Block. Mehrere Runden teilen sich dieselbe tieGroupId.
 */
@Entity
@Table(name = "competition_tiebreakers")
@NullMarked
public class CompetitionTiebreaker {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private LiveSession session;

    /** Verbindet alle Runden desselben Stechens (Runde 1 + Wiederholungen). */
    @Column(name = "tie_group_id", nullable = false)
    private UUID tieGroupId;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    /** Position im Klassement, für die das Stechen läuft (1 = erster Platz). */
    @Column(name = "tie_position", nullable = false)
    private int tiePosition;

    /** SessionPlayer-IDs, die zu Beginn dieser Runde noch gleichstanden. JSON: ["uuid", ...]. */
    @Column(name = "participants_json", columnDefinition = "TEXT", nullable = false)
    private String participantsJson = "[]";

    @Column(name = "template_type", nullable = false)
    private String templateType = "passe"; // passe | serie

    @Column(name = "template_id")
    @Nullable
    private UUID templateId;

    @Column(name = "template_name")
    @Nullable
    private String templateName;

    /** Eingefrorener Snapshot des gewählten Ablaufs (unveränderlich, wie programSnapshots). */
    @Column(name = "program_snapshot", columnDefinition = "TEXT")
    @Nullable
    private String programSnapshot;

    @Column(name = "play_instance_id")
    @Nullable
    private UUID playInstanceId;

    /** Stechen-Ergebnisse. JSON: [{"playerId":"uuid","totalPoints":8,"maxPoints":10}]. */
    @Column(name = "results_json", columnDefinition = "TEXT")
    @Nullable
    private String resultsJson;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TiebreakerStatus status = TiebreakerStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    @Nullable
    private Instant completedAt;

    public CompetitionTiebreaker() {}

    public CompetitionTiebreaker(LiveSession session, UUID tieGroupId, int roundNumber, int tiePosition) {
        this.session = session;
        this.tieGroupId = tieGroupId;
        this.roundNumber = roundNumber;
        this.tiePosition = tiePosition;
    }

    public UUID getId() { return id; }
    public LiveSession getSession() { return session; }
    public void setSession(LiveSession session) { this.session = session; }
    public UUID getTieGroupId() { return tieGroupId; }
    public void setTieGroupId(UUID tieGroupId) { this.tieGroupId = tieGroupId; }
    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }
    public int getTiePosition() { return tiePosition; }
    public void setTiePosition(int tiePosition) { this.tiePosition = tiePosition; }
    public String getParticipantsJson() { return participantsJson; }
    public void setParticipantsJson(String participantsJson) { this.participantsJson = participantsJson; }
    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }
    @Nullable public UUID getTemplateId() { return templateId; }
    public void setTemplateId(@Nullable UUID templateId) { this.templateId = templateId; }
    @Nullable public String getTemplateName() { return templateName; }
    public void setTemplateName(@Nullable String templateName) { this.templateName = templateName; }
    @Nullable public String getProgramSnapshot() { return programSnapshot; }
    public void setProgramSnapshot(@Nullable String programSnapshot) { this.programSnapshot = programSnapshot; }
    @Nullable public UUID getPlayInstanceId() { return playInstanceId; }
    public void setPlayInstanceId(@Nullable UUID playInstanceId) { this.playInstanceId = playInstanceId; }
    @Nullable public String getResultsJson() { return resultsJson; }
    public void setResultsJson(@Nullable String resultsJson) { this.resultsJson = resultsJson; }
    public TiebreakerStatus getStatus() { return status; }
    public void setStatus(TiebreakerStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    @Nullable public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(@Nullable Instant completedAt) { this.completedAt = completedAt; }
}
