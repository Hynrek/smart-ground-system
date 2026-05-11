package ch.jp.shooting.model;

import ch.jp.shooting.model.auth.User;
import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Ein Spieler in einer Session (User oder Gast).
 * Typ bestimmt, ob userId gesetzt ist (USER) oder nicht (GUEST).
 */
@Entity
@Table(name = "session_players")
@NullMarked
public class SessionPlayer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ShooterGroup group;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PlayerType type; // USER, GUEST

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @Nullable
    private User user; // null für Gäste

    @Column(nullable = false)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // ── Constructors ──
    public SessionPlayer() {
    }

    public SessionPlayer(ShooterGroup group, PlayerType type, String displayName) {
        this.group = group;
        this.type = type;
        this.displayName = displayName;
    }

    public SessionPlayer(ShooterGroup group, User user, String displayName) {
        this.group = group;
        this.type = PlayerType.USER;
        this.user = user;
        this.displayName = displayName;
    }

    // ── Accessors ──
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ShooterGroup getGroup() {
        return group;
    }

    public void setGroup(ShooterGroup group) {
        this.group = group;
    }

    public PlayerType getType() {
        return type;
    }

    public void setType(PlayerType type) {
        this.type = type;
    }

    @Nullable
    public User getUser() {
        return user;
    }

    public void setUser(@Nullable User user) {
        this.user = user;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
