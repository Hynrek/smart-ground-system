package ch.jp.shooting.service.auth;

import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.auth.ScopedAccessRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@org.jspecify.annotations.NullMarked
public class AuthorizationService {

    private final UserRepository userRepository;
    private final ScopedAccessRepository scopedAccessRepository;

    public AuthorizationService(UserRepository userRepository,
                                ScopedAccessRepository scopedAccessRepository) {
        this.userRepository = userRepository;
        this.scopedAccessRepository = scopedAccessRepository;
    }

    /**
     * Check if user has a specific role (unscoped).
     * Used for system-level permissions (ADMIN, SHOOTER).
     */
    public boolean hasRole(UUID userId, String roleName) {
        return userRepository.findRolesByUserId(userId).stream()
            .anyMatch(role -> role.getName().equals(roleName));
    }

    /**
     * Check if user has a role scoped to a specific range.
     * Used for OWNER, RANGE_OPERATOR, COMPETITION_RANGE_MASTER.
     */
    public boolean hasRoleForRange(UUID userId, String roleName, UUID rangeId) {
        // First check if user has the unscoped role
        if (hasRole(userId, roleName)) {
            Set<String> unrestricted = Set.of("SHOOTER", "COMPETITION_MASTER");
            if (unrestricted.contains(roleName)) {
                return true;
            }
        }

        // Check scoped_access table
        return scopedAccessRepository.findByUserIdAndRoleNameAndScopeId(
            userId, roleName, "RANGE", rangeId).stream()
            .anyMatch(access -> !access.isExpired());
    }

    /**
     * Get all ranges a user can access with a specific role.
     */
    public Set<UUID> getAccessibleRanges(UUID userId, String roleName) {
        Set<UUID> ranges = new HashSet<>();

        // If SHOOTER or unrestricted, return empty set (= unrestricted)
        if (hasRole(userId, roleName)) {
            if (Set.of("SHOOTER", "COMPETITION_MASTER").contains(roleName)) {
                return Collections.emptySet();
            }
        }

        // Otherwise, get scoped ranges
        scopedAccessRepository.findByUserIdAndRoleName(userId, roleName).forEach(access -> {
            if (!access.isExpired() && "RANGE".equals(access.getScopeType())) {
                ranges.add(access.getScopeId());
            }
        });

        return ranges;
    }

    /**
     * Check if user can control/write to a device on a specific range.
     * Used for Remote module writes.
     */
    public boolean canControlDeviceOnRange(UUID userId, UUID rangeId) {
        // ADMIN, SHOOTER → unrestricted
        if (hasRole(userId, "ADMIN") || hasRole(userId, "SHOOTER")) {
            return true;
        }

        // OWNER → if assigned to this range
        if (hasRoleForRange(userId, "OWNER", rangeId)) {
            return true;
        }

        // RANGE_OPERATOR → if assigned to THIS specific range only
        if (hasRoleForRange(userId, "RANGE_OPERATOR", rangeId)) {
            return true;
        }

        // COMPETITION_MASTER → can control comp-related devices
        if (hasRole(userId, "COMPETITION_MASTER")) {
            return true;
        }

        // COMPETITION_RANGE_MASTER → if assigned to this range
        if (hasRoleForRange(userId, "COMPETITION_RANGE_MASTER", rangeId)) {
            return true;
        }

        return false;
    }

    /**
     * Check if user can view SmartBox/device information.
     */
    public boolean canViewSmartBox(UUID userId) {
        return hasRole(userId, "ADMIN")
            || hasRole(userId, "OWNER")
            || hasRole(userId, "SHOOTER")
            || hasRole(userId, "COMPETITION_MASTER");
    }

    /**
     * Check if user can manage firmware and device types (system admin only).
     */
    public boolean canManageFirmware(UUID userId) {
        return hasRole(userId, "ADMIN");
    }

    /**
     * Check if user can manage users.
     */
    public boolean canManageUsers(UUID userId, @Nullable UUID targetRangeId) {
        // ADMIN can manage all users
        if (hasRole(userId, "ADMIN")) {
            return true;
        }

        // OWNER can manage users on their assigned ranges
        if (targetRangeId != null && hasRoleForRange(userId, "OWNER", targetRangeId)) {
            return true;
        }

        return false;
    }

    /**
     * Check if user can approve segment completion on a range.
     */
    public boolean canApproveSegment(UUID userId, UUID rangeId) {
        return hasRoleForRange(userId, "COMPETITION_RANGE_MASTER", rangeId);
    }

    /**
     * Check if user can track/input competition scores.
     */
    public boolean canTrackScores(UUID userId) {
        return hasRole(userId, "SHOOTER")
            || hasRole(userId, "COMPETITION_MASTER")
            || userRepository.findRolesByUserId(userId).stream()
                .anyMatch(r -> r.getName().equals("COMPETITION_RANGE_MASTER"));
    }

    /**
     * Check if user can run competition programs on a specific range.
     */
    public boolean canRunProgram(UUID userId, UUID rangeId) {
        // SHOOTER → any range
        if (hasRole(userId, "SHOOTER")) {
            return true;
        }

        // COMPETITION_RANGE_MASTER → scoped to that range only
        if (hasRoleForRange(userId, "COMPETITION_RANGE_MASTER", rangeId)) {
            return true;
        }

        return false;
    }
}
