package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Wiederverwendbare Session-Konfiguration für Wettkampf oder Training.
 * Enthält Programme, Bereichs-Segment-Zuordnung und optionale Vorgabenspieler.
 */
@Entity
@Table(name = "session_templates")
@NullMarked
public class SessionTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionType type; // COMPETITION oder TRAINING

    /**
     * Programm-IDs als JSON-Array (UUID[]).
     * Wird beim Session-Start als Snapshots geladen.
     */
    @Column(name = "program_ids", columnDefinition = "TEXT")
    @Nullable
    private String programIds;

    /**
     * Bereichs-Segment-Zuordnung als JSON.
     * Format: RangeSegmentEntry[] ({rangeId, segmentIds[]})
     */
    @Column(name = "range_segment_map", columnDefinition = "TEXT")
    @Nullable
    private String rangeSegmentMap;

    /**
     * Optionale Vorgaben-Spieler als JSON.
     * Format: SessionPlayer[] ({type, displayName, userId?})
     */
    @Column(name = "default_players", columnDefinition = "TEXT")
    @Nullable
    private String defaultPlayers;

    /**
     * Wettkampf-spezifische Felder (optional, null für TRAINING)
     */
    @Column(name = "max_groups")
    @Nullable
    private Integer maxGroups;

    /**
     * Bracket-Typ: ROUND_ROBIN, SINGLE_ELIMINATION, DOUBLE_ELIMINATION
     */
    @Column(name = "bracket_type")
    @Nullable
    private String bracketType;

    /**
     * Standard-Tiebreaker-Kriterium (z.B. "TOTAL_SCORE", "AVG_SCORE", "WINS")
     */
    @Column(name = "default_tiebreaker")
    @Nullable
    private String defaultTiebreaker;

    /**
     * Ob Ergebnisse nach Abschluss veröffentlicht werden
     */
    @Column(name = "publish_results", nullable = false)
    private boolean publishResults = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    @Nullable
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // ── Constructors ──
    public SessionTemplate() {
    }

    public SessionTemplate(String name, SessionType type) {
        this.name = name;
        this.type = type;
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

    public SessionType getType() {
        return type;
    }

    public void setType(SessionType type) {
        this.type = type;
    }

    @Nullable
    public String getProgramIds() {
        return programIds;
    }

    public void setProgramIds(@Nullable String programIds) {
        this.programIds = programIds;
    }

    @Nullable
    public String getRangeSegmentMap() {
        return rangeSegmentMap;
    }

    public void setRangeSegmentMap(@Nullable String rangeSegmentMap) {
        this.rangeSegmentMap = rangeSegmentMap;
    }

    @Nullable
    public String getDefaultPlayers() {
        return defaultPlayers;
    }

    public void setDefaultPlayers(@Nullable String defaultPlayers) {
        this.defaultPlayers = defaultPlayers;
    }

    @Nullable
    public Integer getMaxGroups() {
        return maxGroups;
    }

    public void setMaxGroups(@Nullable Integer maxGroups) {
        this.maxGroups = maxGroups;
    }

    @Nullable
    public String getBracketType() {
        return bracketType;
    }

    public void setBracketType(@Nullable String bracketType) {
        this.bracketType = bracketType;
    }

    @Nullable
    public String getDefaultTiebreaker() {
        return defaultTiebreaker;
    }

    public void setDefaultTiebreaker(@Nullable String defaultTiebreaker) {
        this.defaultTiebreaker = defaultTiebreaker;
    }

    public boolean isPublishResults() {
        return publishResults;
    }

    public void setPublishResults(boolean publishResults) {
        this.publishResults = publishResults;
    }

    @Nullable
    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(@Nullable User createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
