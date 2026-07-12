package ch.jp.shooting.model.auth;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scoped_access")
@org.jspecify.annotations.NullMarked
public class ScopedAccess {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(nullable = false)
    private String scopeType; // RANGE, COMPETITION, FACILITY

    @Column(nullable = false)
    private UUID scopeId; // range_id, competition_id, or facility_id

    @Column(nullable = false)
    private Instant assignedAt = Instant.now();

    @Nullable
    private Instant expiresAt;

    // ==================== CONSTRUCTORS ====================
    public ScopedAccess() {}

    public ScopedAccess(User user, Role role, String scopeType, UUID scopeId) {
        this.user = user;
        this.role = role;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
    }

    // ==================== GETTERS & SETTERS ====================
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public UUID getScopeId() { return scopeId; }
    public void setScopeId(UUID scopeId) { this.scopeId = scopeId; }

    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }

    @Nullable
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(@Nullable Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
