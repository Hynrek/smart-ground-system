package ch.jp.shooting.model.auth;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permissions")
@NullMarked
public class PermissionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String action; // z.B. "manage_users", "view_remote"

    @Nullable
    private String description;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // ==================== KONSTRUKTOREN ====================
    public PermissionEntity() {}

    public PermissionEntity(String action, @Nullable String description) {
        this.action = action;
        this.description = description;
    }

    // ==================== GETTER & SETTER ====================
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    @Nullable
    public String getDescription() { return description; }
    public void setDescription(@Nullable String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Permission toEnum() {
        return Permission.valueOf(action.toUpperCase());
    }
}
