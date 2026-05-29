package ch.jp.shooting.model.auth;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_roles")
@NullMarked
public class UserRoleEntity {

    @EmbeddedId
    private UserRoleId id = new UserRoleId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    private Role role;

    @Nullable
    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt = Instant.now();

    // ==================== KONSTRUKTOREN ====================
    public UserRoleEntity() {}

    public UserRoleEntity(User user, Role role, @Nullable UUID grantedBy) {
        this.user = user;
        this.role = role;
        this.grantedBy = grantedBy;
        this.id = new UserRoleId(user.getId(), role.getId());
    }

    // ==================== GETTER ====================
    public UserRoleId getId() { return id; }
    public User getUser() { return user; }
    public Role getRole() { return role; }

    @Nullable
    public UUID getGrantedBy() { return grantedBy; }
    public void setGrantedBy(@Nullable UUID grantedBy) { this.grantedBy = grantedBy; }

    public Instant getGrantedAt() { return grantedAt; }

    // ==================== ZUSAMMENGESETZTER SCHLÜSSEL ====================
    @Embeddable
    public static class UserRoleId implements Serializable {
        @Column(name = "user_id")
        private UUID userId;

        @Column(name = "role_id")
        private UUID roleId;

        public UserRoleId() {}

        public UserRoleId(UUID userId, UUID roleId) {
            this.userId = userId;
            this.roleId = roleId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UserRoleId that)) return false;
            return Objects.equals(userId, that.userId) && Objects.equals(roleId, that.roleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, roleId);
        }
    }
}
