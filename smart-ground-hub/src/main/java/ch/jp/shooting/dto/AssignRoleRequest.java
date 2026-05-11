package ch.jp.shooting.dto;

import org.jspecify.annotations.Nullable;
import java.time.Instant;

@org.jspecify.annotations.NullMarked
public class AssignRoleRequest {
    private String roleName; // ADMIN, OWNER, SHOOTER, etc.

    @Nullable
    private String scopeType; // RANGE, COMPETITION, FACILITY (for scoped roles)

    @Nullable
    private String scopeId; // UUID as string (range_id, competition_id, etc.)

    @Nullable
    private Instant expiresAt; // Optional expiration for time-limited access

    // ==================== CONSTRUCTORS ====================
    public AssignRoleRequest() {}

    public AssignRoleRequest(String roleName) {
        this.roleName = roleName;
    }

    public AssignRoleRequest(String roleName, String scopeType, String scopeId) {
        this.roleName = roleName;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
    }

    // ==================== GETTERS & SETTERS ====================
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    @Nullable
    public String getScopeType() { return scopeType; }
    public void setScopeType(@Nullable String scopeType) { this.scopeType = scopeType; }

    @Nullable
    public String getScopeId() { return scopeId; }
    public void setScopeId(@Nullable String scopeId) { this.scopeId = scopeId; }

    @Nullable
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(@Nullable Instant expiresAt) { this.expiresAt = expiresAt; }
}
