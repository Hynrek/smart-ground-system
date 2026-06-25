package ch.jp.shooting.api;

import ch.jp.shooting.dto.UserDTO;
import ch.jp.shooting.exception.ForbiddenException;
import ch.jp.shooting.service.auth.AuthorizationService;
import ch.jp.shooting.service.auth.UserService;
import ch.jp.smartground.api.UserApi;
import ch.jp.smartground.model.AssignRoleRequest;
import ch.jp.smartground.model.AssignScopedRoleRequest;
import ch.jp.smartground.model.ChangePasswordRequest;
import ch.jp.smartground.model.PageMeta;
import ch.jp.smartground.model.UserPageResponse;
import ch.jp.smartground.model.UserResponse;
import ch.jp.smartground.model.UserRoleResponse;
import jakarta.validation.Valid;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@NullMarked
public class UserController implements UserApi {

    private final UserService userService;
    private final AuthorizationService authorizationService;

    public UserController(UserService userService, AuthorizationService authorizationService) {
        this.userService = userService;
        this.authorizationService = authorizationService;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserPageResponse> listUsers(Integer page, Integer size) {
        Page<UserDTO> result = userService.listUsers(PageRequest.of(
                page != null ? page : 0,
                size != null ? size : 20));
        UserPageResponse response = new UserPageResponse()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .meta(new PageMeta()
                        .page(result.getNumber())
                        .size(result.getSize())
                        .totalElements((int) result.getTotalElements())
                        .totalPages(result.getTotalPages()));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UserResponse> getUser(UUID id) {
        return ResponseEntity.ok(toResponse(userService.getUserById(id)));
    }

    @Override
    public ResponseEntity<UserResponse> createUser(@Valid ch.jp.smartground.model.CreateUserRequest request) {
        ch.jp.shooting.dto.CreateUserRequest dto = new ch.jp.shooting.dto.CreateUserRequest();
        dto.setEmail(request.getEmail());
        dto.setUsername(request.getUsername());
        dto.setPassword(request.getPassword());
        dto.setVorname(request.getVorname());
        dto.setNachname(request.getNachname());
        dto.setGeburtsdatum(jsonNullableOrNull(request.getGeburtsdatum()));
        dto.setGeschlecht(jsonNullableEnumToString(request.getGeschlecht()));
        dto.setTelefonnummer(jsonNullableOrNull(request.getTelefonnummer()));
        dto.setStrasse(jsonNullableOrNull(request.getStrasse()));
        dto.setHausnummer(jsonNullableOrNull(request.getHausnummer()));
        dto.setPlz(jsonNullableOrNull(request.getPlz()));
        dto.setStadt(jsonNullableOrNull(request.getStadt()));
        dto.setLand(jsonNullableOrNull(request.getLand()));
        dto.setMitgliedsnummer(jsonNullableOrNull(request.getMitgliedsnummer()));
        dto.setSprache(jsonNullableEnumToString(request.getSprache()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(userService.createUser(dto)));
    }

    @Override
    public ResponseEntity<UserResponse> updateUser(UUID id, @Valid ch.jp.smartground.model.UpdateUserRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDTO currentUser = userService.getUserByEmail(auth.getName());
        if (!id.equals(currentUser.getId()) && !authorizationService.hasRole(currentUser.getId(), "ADMIN")) {
            throw new ForbiddenException("Cannot modify other users");
        }
        ch.jp.shooting.dto.UpdateUserRequest dto = new ch.jp.shooting.dto.UpdateUserRequest();
        dto.setEmail(jsonNullableOrNull(request.getEmail()));
        dto.setUsername(jsonNullableOrNull(request.getUsername()));
        dto.setVorname(jsonNullableOrNull(request.getVorname()));
        dto.setNachname(jsonNullableOrNull(request.getNachname()));
        dto.setGeburtsdatum(jsonNullableOrNull(request.getGeburtsdatum()));
        dto.setGeschlecht(jsonNullableEnumToString(request.getGeschlecht()));
        dto.setTelefonnummer(jsonNullableOrNull(request.getTelefonnummer()));
        dto.setTelefonBestaetigt(jsonNullableOrNull(request.getTelefonBestaetigt()));
        dto.setStrasse(jsonNullableOrNull(request.getStrasse()));
        dto.setHausnummer(jsonNullableOrNull(request.getHausnummer()));
        dto.setPlz(jsonNullableOrNull(request.getPlz()));
        dto.setStadt(jsonNullableOrNull(request.getStadt()));
        dto.setLand(jsonNullableOrNull(request.getLand()));
        dto.setProfilbildUrl(jsonNullableOrNull(request.getProfilbildUrl()));
        dto.setBiographie(jsonNullableOrNull(request.getBiographie()));
        dto.setSprache(jsonNullableEnumToString(request.getSprache()));
        dto.setMitgliedsnummer(jsonNullableOrNull(request.getMitgliedsnummer()));
        dto.setSchiessLizenz(jsonNullableOrNull(request.getSchiessLizenz()));
        dto.setSchiessLizenzVerfallsdatum(jsonNullableOrNull(request.getSchiessLizenzVerfallsdatum()));
        dto.setStatus(jsonNullableEnumToString(request.getStatus()));
        return ResponseEntity.ok(toResponse(userService.updateUser(id, dto)));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> changePassword(@Valid ChangePasswordRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDTO user = userService.getUserByEmail(auth.getName());
        userService.changePassword(user.getId(), request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<UserRoleResponse>> getUserRoles(UUID id) {
        return ResponseEntity.ok(
                userService.getUserRoles(id).stream().map(this::toRoleResponse).toList());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserRoleResponse> assignRole(UUID id, @Valid AssignRoleRequest assignRoleRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toRoleResponse(userService.assignRole(id, assignRoleRequest.getRoleName())));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserRoleResponse> assignScopedRole(UUID id, @Valid AssignScopedRoleRequest assignScopedRoleRequest) {
        java.time.Instant expiresAt = assignScopedRoleRequest.getExpiresAt().isPresent()
                ? assignScopedRoleRequest.getExpiresAt().get().toInstant()
                : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(
                toRoleResponse(userService.assignScopedRole(
                        id,
                        assignScopedRoleRequest.getRoleName(),
                        assignScopedRoleRequest.getScopeType().getValue(),
                        assignScopedRoleRequest.getScopeId(),
                        expiresAt)));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revokeRole(UUID id, String roleName) {
        userService.revokeRole(id, roleName);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revokeScopedRole(UUID id, String roleName, String scopeType, UUID scopeId) {
        userService.revokeScopedRole(id, roleName, scopeType, scopeId);
        return ResponseEntity.noContent().build();
    }

    // ── Mappers ────────────────────────────────────────────────────────────────

    private UserResponse toResponse(UserDTO dto) {
        UserResponse r = new UserResponse()
                .id(dto.getId())
                .email(dto.getEmail())
                .username(dto.getUsername())
                .vorname(dto.getVorname())
                .nachname(dto.getNachname())
                .fullName(dto.getFullName())
                .geburtsdatum(dto.getGeburtsdatum())
                .telefonnummer(dto.getTelefonnummer())
                .strasse(dto.getStrasse())
                .hausnummer(dto.getHausnummer())
                .plz(dto.getPlz())
                .stadt(dto.getStadt())
                .land(dto.getLand())
                .profilbildUrl(dto.getProfilbildUrl())
                .biographie(dto.getBiographie())
                .sprache(dto.getSprache())
                .mitgliedsnummer(dto.getMitgliedsnummer())
                .schiessLizenz(dto.getSchiessLizenz())
                .schiessLizenzVerfallsdatum(dto.getSchiessLizenzVerfallsdatum())
                .erstelltAm(dto.getErstelltAm() != null
                        ? dto.getErstelltAm().atOffset(ZoneOffset.UTC) : null)
                .aktualisiertAm(dto.getAktualisiertAm() != null
                        ? dto.getAktualisiertAm().atOffset(ZoneOffset.UTC) : null)
                .assignedRangeId(dto.getAssignedRangeId());
        // Konvertierung String → generierte Enums für geschlecht und status
        if (dto.getGeschlecht() != null) {
            r.geschlecht(UserResponse.GeschlechtEnum.fromValue(dto.getGeschlecht()));
        }
        if (dto.getStatus() != null) {
            r.status(UserResponse.StatusEnum.fromValue(dto.getStatus()));
        }
        return r;
    }

    private UserRoleResponse toRoleResponse(ch.jp.shooting.dto.UserRoleDto dto) {
        UserRoleResponse r = new UserRoleResponse()
                .roleId(dto.getRoleId())
                .roleName(dto.getRoleName())
                .description(dto.getDescription())
                .scopeId(dto.getScopeId())
                .isExpired(dto.getIsExpired());
        if (dto.getAssignedAt() != null) {
            r.assignedAt(dto.getAssignedAt().atOffset(ZoneOffset.UTC));
        }
        if (dto.getExpiresAt() != null) {
            r.expiresAt(dto.getExpiresAt().atOffset(ZoneOffset.UTC));
        }
        if (dto.getScopeType() != null) {
            r.scopeType(UserRoleResponse.ScopeTypeEnum.fromValue(dto.getScopeType()));
        }
        return r;
    }

    // ── JsonNullable helpers ───────────────────────────────────────────────────

    /**
     * Extracts the value from a JsonNullable, returning null if undefined or null.
     */
    private static <T> @Nullable T jsonNullableOrNull(JsonNullable<T> nullable) {
        return nullable != null && nullable.isPresent() ? nullable.get() : null;
    }

    /**
     * Extracts the string value from a JsonNullable-wrapped generated enum (has getValue()).
     * Returns null if undefined, null, or the contained value is null.
     */
    private static <E extends Enum<E>> @Nullable String jsonNullableEnumToString(JsonNullable<E> nullable) {
        if (nullable == null || !nullable.isPresent()) return null;
        E value = nullable.get();
        if (value == null) return null;
        try {
            return (String) value.getClass().getMethod("getValue").invoke(value);
        } catch (Exception e) {
            return value.name();
        }
    }
}
