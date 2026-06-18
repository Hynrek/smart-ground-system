package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Eine aktive oder abgeschlossene Spielsession.
 * Enthält alle Spieler, Gruppen, und Ergebnisse für ein Spiel.
 * Programmkonfiguration und Bereichszuordnungen sind als JSON-Snapshots immutable.
 */
@Entity
@Table(name = "live_sessions")
@NullMarked
public class LiveSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    @Nullable
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionType type; // COMPETITION, TRAINING, FREE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    @Nullable
    private SessionTemplate template;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionStatus status; // SETUP, OPEN, ACTIVE, PRE_COMPLETE, COMPLETED, ABANDONED

    /** Höchster (0-basierter) Passe-Index, der zum Spielen freigegeben ist (Admin-Gate). */
    @Column(name = "released_passe_index", nullable = false)
    private int releasedPasseIndex = 0;

    /**
     * Programmkonfiguration als JSON-Snapshot beim Start (immutable während Spiel).
     * Format: Program[] (komplette Struktur: {id, name, segments[{id, alias, steps[...]}]})
     */
    @Column(name = "program_snapshots", columnDefinition = "TEXT")
    @Nullable
    private String programSnapshots;

    /**
     * Bereichs-Segment-Zuordnung als JSON-Snapshot.
     * Format: RangeSegmentEntry[] ({rangeId, segmentIds[]})
     */
    @Column(name = "range_segment_map", columnDefinition = "TEXT")
    @Nullable
    private String rangeSegmentMap;

    @OneToMany(mappedBy = "session", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ShooterGroup> groups = new ArrayList<>();

    @OneToMany(mappedBy = "session", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlayerResult> playerResults = new ArrayList<>();

    /**
     * Bracket-Phase für Eliminierungsturniere (nur bei bracketType != ROUND_ROBIN).
     * SETUP → SEEDING → IN_PROGRESS → FINALS → COMPLETED
     */
    @Column(name = "bracket_phase")
    @Enumerated(EnumType.STRING)
    @Nullable
    private BracketPhase bracketPhase;

    /**
     * Bracket-State als JSON (Rounds, Matches, Seeding Info).
     * Format: { rounds: [...], seededPlayers: [{playerId, seed}], ...}
     */
    @Column(name = "bracket_state", columnDefinition = "TEXT")
    @Nullable
    private String bracketStateJson;

    @Column(name = "started_at")
    @Nullable
    private Instant startedAt;

    @Column(name = "completed_at")
    @Nullable
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // ── Constructors ──
    public LiveSession() {
    }

    public LiveSession(SessionType type, SessionStatus status) {
        this.type = type;
        this.status = status;
    }

    // ── Accessors ──
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    public SessionType getType() {
        return type;
    }

    public void setType(SessionType type) {
        this.type = type;
    }

    @Nullable
    public SessionTemplate getTemplate() {
        return template;
    }

    public void setTemplate(@Nullable SessionTemplate template) {
        this.template = template;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    @Nullable
    public String getProgramSnapshots() {
        return programSnapshots;
    }

    public void setProgramSnapshots(@Nullable String programSnapshots) {
        this.programSnapshots = programSnapshots;
    }

    public int getReleasedPasseIndex() {
        return releasedPasseIndex;
    }

    public void setReleasedPasseIndex(int releasedPasseIndex) {
        this.releasedPasseIndex = releasedPasseIndex;
    }

    @Nullable
    public String getRangeSegmentMap() {
        return rangeSegmentMap;
    }

    public void setRangeSegmentMap(@Nullable String rangeSegmentMap) {
        this.rangeSegmentMap = rangeSegmentMap;
    }

    public List<ShooterGroup> getGroups() {
        return groups;
    }

    public void setGroups(List<ShooterGroup> groups) {
        this.groups = groups;
    }

    public List<PlayerResult> getPlayerResults() {
        return playerResults;
    }

    public void setPlayerResults(List<PlayerResult> playerResults) {
        this.playerResults = playerResults;
    }

    @Nullable
    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(@Nullable Instant startedAt) {
        this.startedAt = startedAt;
    }

    @Nullable
    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(@Nullable Instant completedAt) {
        this.completedAt = completedAt;
    }

    @Nullable
    public BracketPhase getBracketPhase() {
        return bracketPhase;
    }

    public void setBracketPhase(@Nullable BracketPhase bracketPhase) {
        this.bracketPhase = bracketPhase;
    }

    @Nullable
    public String getBracketStateJson() {
        return bracketStateJson;
    }

    public void setBracketStateJson(@Nullable String bracketStateJson) {
        this.bracketStateJson = bracketStateJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

