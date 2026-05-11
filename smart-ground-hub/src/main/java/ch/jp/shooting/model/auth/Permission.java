package ch.jp.shooting.model.auth;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permissions")
@org.jspecify.annotations.NullMarked
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String name; // "devices:read", "firmware:admin", etc.

    @Nullable
    private String description;

    @Column(nullable = false)
    private String module; // REMOTE, WETTKAMPF, KARRIERE, SYSTEM

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // ==================== CONSTRUCTORS ====================
    public Permission() {}

    public Permission(String name, String module, String description) {
        this.name = name;
        this.module = module;
        this.description = description;
    }

    // ==================== GETTERS & SETTERS ====================
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @Nullable
    public String getDescription() { return description; }
    public void setDescription(@Nullable String description) { this.description = description; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
