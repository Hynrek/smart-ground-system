package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Eine Gruppe von Spielern, die zusammen ein Programm durchführen.
 * Progress enthält aktive Bereichs- und Segment-IDs sowie abgeschlossene Segmente.
 */
@Entity
@Table(name = "shooter_groups")
@NullMarked
public class ShooterGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private LiveSession session;

    @Column(nullable = false)
    private String name; // "Gruppe 1", "Gruppe A", etc.

    @OneToMany(mappedBy = "group", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SessionPlayer> members = new ArrayList<>();

    /**
     * Progress-Tracking pro Programm als JSON-Snapshot.
     * Format: GroupProgress[] ({programId, completedSegmentIds[], activeRangeId?, activeSegmentId?})
     */
    @Column(name = "progress", columnDefinition = "TEXT")
    @Nullable
    private String progress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // ── Constructors ──
    public ShooterGroup() {
    }

    public ShooterGroup(LiveSession session, String name) {
        this.session = session;
        this.name = name;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<SessionPlayer> getMembers() {
        return members;
    }

    public void setMembers(List<SessionPlayer> members) {
        this.members = members;
    }

    @Nullable
    public String getProgress() {
        return progress;
    }

    public void setProgress(@Nullable String progress) {
        this.progress = progress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
