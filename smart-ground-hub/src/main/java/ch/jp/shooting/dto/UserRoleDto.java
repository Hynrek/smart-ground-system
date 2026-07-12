package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@org.jspecify.annotations.NullMarked
public class UserRoleDto {
    private UUID roleId;
    private String roleName;
    private String description;

    @Nullable
    private String scopeType; // RANGE, COMPETITION, FACILITY

    @Nullable
    private UUID scopeId;

    private Instant assignedAt;

    @Nullable
    private Instant expiresAt;

    @Nullable
    private Boolean isExpired;

    // ==================== CONSTRUCTORS ====================
    public UserRoleDto() {}

    public UserRoleDto(UUID roleId, String roleName, String description, Instant assignedAt) {
        this.roleId = roleId;
        this.roleName = roleName;
        this.description = description;
        this.assignedAt = assignedAt;
    }

    // ==================== GETTERS & SETTERS ====================
    public UUID getRoleId() { return roleId; }
    public void setRoleId(UUID roleId) { this.roleId = roleId; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Nullable
    public String getScopeType() { return scopeType; }
    public void setScopeType(@Nullable String scopeType) { this.scopeType = scopeType; }

    @Nullable
    public UUID getScopeId() { return scopeId; }
    public void setScopeId(@Nullable UUID scopeId) { this.scopeId = scopeId; }

    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }

    @Nullable
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(@Nullable Instant expiresAt) { this.expiresAt = expiresAt; }

    @Nullable
    public Boolean getIsExpired() { return isExpired; }
    public void setIsExpired(@Nullable Boolean isExpired) { this.isExpired = isExpired; }
}
