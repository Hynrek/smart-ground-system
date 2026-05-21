package ch.jp.shooting.model;

import ch.jp.shooting.model.auth.User;
import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "play_instances")
@NullMarked
public class PlayInstance {

    public PlayInstance() {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID instanceId;

    /**
     * 'programm' | 'training'
     */
    @Column(nullable = false, length = 10)
    private String type;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    /**
     * 'active' | 'completed' | 'cancelled'
     */
    @Column(nullable = false, length = 12)
    private String status = "active";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /**
     * Spieler als JSON (PlayerRef[]).
     */
    @Column(name = "players_json", columnDefinition = "TEXT", nullable = false)
    private String playersJson = "[]";

    /**
     * Für type='programm': Blöcke als JSON (PlayBlock[]).
     * Für type='training': Phasen als JSON (PlayPhase[]).
     */
    @Column(name = "state_json", columnDefinition = "TEXT", nullable = false)
    private String stateJson = "[]";

    /**
     * Nur für Training: Index der aktuell aktiven Phase
     */
    @Column(name = "current_phase_index")
    @Nullable
    private Integer currentPhaseIndex;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    @Nullable
    private Instant completedAt;

    public UUID getInstanceId() { return instanceId; }
    public void setInstanceId(UUID instanceId) { this.instanceId = instanceId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public UUID getTemplateId() { return templateId; }
    public void setTemplateId(UUID templateId) { this.templateId = templateId; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public String getPlayersJson() { return playersJson; }
    public void setPlayersJson(String playersJson) { this.playersJson = playersJson; }
    public String getStateJson() { return stateJson; }
    public void setStateJson(String stateJson) { this.stateJson = stateJson; }
    public @Nullable Integer getCurrentPhaseIndex() { return currentPhaseIndex; }
    public void setCurrentPhaseIndex(@Nullable Integer currentPhaseIndex) { this.currentPhaseIndex = currentPhaseIndex; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public @Nullable Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(@Nullable Instant completedAt) { this.completedAt = completedAt; }
}
