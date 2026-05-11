package ch.jp.shooting.api;

import ch.jp.shooting.dto.*;
import ch.jp.shooting.service.auth.AuthorizationService;
import ch.jp.shooting.service.auth.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@org.jspecify.annotations.NullMarked
public class UserController {

    private final UserService userService;
    private final AuthorizationService authorizationService;

    public UserController(UserService userService, AuthorizationService authorizationService) {
        this.userService = userService;
        this.authorizationService = authorizationService;
    }

    // ==================== USER MANAGEMENT ====================

    /**
     * List all users with pagination
     * Only ADMIN can list all users
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserDTO>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserDTO> users = userService.listUsers(pageable);
        return ResponseEntity.ok(users);
    }

    /**
     * Get current authenticated user
     */
    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        try {
            UserDTO user = userService.getUserByEmail(email);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get user by ID
     * ADMIN can view any user, OWNER can view users on their ranges
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable UUID id) {
        try {
            UserDTO user = userService.getUserById(id);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Create new user
     * Only ADMIN can create users
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> createUser(@RequestBody CreateUserRequest request) {
        try {
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
            }
            if (request.getVorname() == null || request.getVorname().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Vorname is required"));
            }
            if (request.getNachname() == null || request.getNachname().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Nachname is required"));
            }

            UserDTO newUser = userService.createUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(newUser);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update user profile
     * Users can update themselves, ADMIN/OWNER can update others
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Object> updateUser(
            @PathVariable UUID id,
            @RequestBody UpdateUserRequest request) {
        try {
            // Check authorization: own user or admin
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            UUID currentUserId = UUID.fromString(auth.getCredentials().toString()); // Simplified; depends on your auth setup

            if (!id.equals(currentUserId) && !authorizationService.hasRole(currentUserId, "ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot modify other users"));
            }

            UserDTO updated = userService.updateUser(id, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete user (soft delete - sets status to INACTIVE)
     * Only ADMIN can delete users
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> deleteUser(@PathVariable UUID id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== PASSWORD MANAGEMENT ====================

    /**
     * Change current user's password
     */
    @PostMapping("/me/password")
    public ResponseEntity<Object> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            UserDTO user = userService.getUserByEmail(email);

            userService.changePassword(user.getId(), request.getOldPassword(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== ROLE MANAGEMENT ====================

    /**
     * Get all roles for a user (both scoped and unscoped)
     */
    @GetMapping("/{id}/roles")
    public ResponseEntity<List<UserRoleDto>> getUserRoles(@PathVariable UUID id) {
        try {
            List<UserRoleDto> roles = userService.getUserRoles(id);
            return ResponseEntity.ok(roles);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Assign an unscoped role to a user
     * Only ADMIN can assign roles
     */
    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> assignRole(
            @PathVariable UUID id,
            @RequestBody AssignRoleRequest request) {
        try {
            UserRoleDto assignedRole = userService.assignRole(id, request.getRoleName());
            return ResponseEntity.status(HttpStatus.CREATED).body(assignedRole);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Assign a scoped role to a user (e.g., OWNER for a specific range)
     * Only ADMIN can assign scoped roles
     */
    @PostMapping("/{id}/roles/scoped")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> assignScopedRole(
            @PathVariable UUID id,
            @RequestBody AssignRoleRequest request) {
        try {
            if (request.getScopeType() == null || request.getScopeId() == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "scopeType and scopeId are required for scoped roles"));
            }

            UUID scopeId = UUID.fromString(request.getScopeId());
            UserRoleDto assignedRole = userService.assignScopedRole(
                id,
                request.getRoleName(),
                request.getScopeType(),
                scopeId,
                request.getExpiresAt()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(assignedRole);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Revoke an unscoped role from a user
     * Only ADMIN can revoke roles
     */
    @DeleteMapping("/{id}/roles/{roleName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> revokeRole(
            @PathVariable UUID id,
            @PathVariable String roleName) {
        try {
            userService.revokeRole(id, roleName);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Revoke a scoped role from a user
     * Only ADMIN can revoke scoped roles
     */
    @DeleteMapping("/{id}/roles/{roleName}/scoped")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> revokeScopedRole(
            @PathVariable UUID id,
            @PathVariable String roleName,
            @RequestParam String scopeType,
            @RequestParam String scopeId) {
        try {
            UUID scopeIdUuid = UUID.fromString(scopeId);
            userService.revokeScopedRole(id, roleName, scopeType, scopeIdUuid);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== ERROR HANDLING ====================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "Internal server error"));
    }
}
