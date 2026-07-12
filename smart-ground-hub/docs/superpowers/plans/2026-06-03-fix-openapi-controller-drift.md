# Fix OpenAPI/Controller Drift Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `openapi.yaml` the single source of truth by (a) fixing all missing/wrong spec entries and (b) migrating every controller to implement a generated interface with no stray Spring mapping annotations.

**Architecture:** All changes flow spec-first: edit `openapi.yaml` → `./mvnw generate-sources` → implement. Controllers that previously bypassed the generated interface are refactored to implement it; where multiple controllers shared one `DeviceTypeApi` tag they are merged into one controller. The `GET /api/events` stub is removed from the spec (real-time is handled by STOMP/WebSocket). `POST /api/device-types/groups` (undocumented) is dropped from the controller.

**Tech Stack:** Java 25, Spring Boot 4, openapi-generator 7.9.0, Maven, JUnit 5 / Mockito, H2 (tests)

---

## File Map

| Action | File |
|--------|------|
| Modify | `src/main/resources/static/openapi.yaml` |
| Generate | `target/generated-sources/openapi/ch/jp/smartground/api/UserApi.java` (re-gen) |
| Generate | `target/generated-sources/openapi/ch/jp/smartground/api/RoleApi.java` (new) |
| Generate | `target/generated-sources/openapi/ch/jp/smartground/model/CreateUserRequest.java` (re-gen) |
| Generate | `target/generated-sources/openapi/ch/jp/smartground/model/UserResponse.java` (new) |
| Generate | `target/generated-sources/openapi/ch/jp/smartground/model/UserPageResponse.java` (new) |
| Generate | `target/generated-sources/openapi/ch/jp/smartground/model/UpdateUserRequest.java` (new) |
| Generate | `target/generated-sources/openapi/ch/jp/smartground/model/ChangePasswordRequest.java` (new) |
| Generate | `target/generated-sources/openapi/ch/jp/smartground/model/RoleResponse.java` (new) |
| Generate | `target/generated-sources/openapi/ch/jp/smartground/model/UserRoleResponse.java` (new) |
| Generate | `target/generated-sources/openapi/ch/jp/smartground/model/AssignRoleRequest.java` (new) |
| Generate | `target/generated-sources/openapi/ch/jp/smartground/model/AssignScopedRoleRequest.java` (new) |
| Modify | `src/main/java/ch/jp/shooting/api/UserController.java` |
| Modify | `src/main/java/ch/jp/shooting/api/RoleController.java` |
| Modify | `src/main/java/ch/jp/shooting/api/DeviceTypeController.java` |
| Delete | `src/main/java/ch/jp/shooting/api/DeviceTypeGroupController.java` |
| Delete | `src/main/java/ch/jp/shooting/api/FirmwareConfigController.java` |
| Modify | `src/main/java/ch/jp/shooting/api/RangeController.java` |

---

## Task 1: Fix `openapi.yaml` — User and Role schemas + endpoints

**Files:**
- Modify: `src/main/resources/static/openapi.yaml`

The spec currently has a stale `CreateUserRequest` (username/password/role) and is missing all user management and role endpoints. This task fixes all of that in one spec edit.

- [ ] **Step 1: Replace the `CreateUserRequest` schema** (around line 3100 in `openapi.yaml`)

Find the existing `CreateUserRequest` schema block:

```yaml
    CreateUserRequest:
      type: object
      required: [username, password, role]
      properties:
        username:
          type: string
          description: Username for the new user
        password:
          type: string
          description: Password for the new user
        role:
          type: string
          description: User role (ADMIN or SHOOTER)
```

Replace it with:

```yaml
    CreateUserRequest:
      type: object
      required: [email, password, vorname, nachname]
      properties:
        email:
          type: string
          format: email
        password:
          type: string
        vorname:
          type: string
        nachname:
          type: string
        geburtsdatum:
          type: string
          format: date
          nullable: true
        geschlecht:
          type: string
          nullable: true
          enum: [MAENNLICH, WEIBLICH, DIVERS, UNBEKANNT]
        telefonnummer:
          type: string
          nullable: true
        strasse:
          type: string
          nullable: true
        hausnummer:
          type: string
          nullable: true
        plz:
          type: string
          nullable: true
        stadt:
          type: string
          nullable: true
        land:
          type: string
          nullable: true
        mitgliedsnummer:
          type: string
          nullable: true
        sprache:
          type: string
          nullable: true
          enum: [DE, EN, FR, IT]
```

- [ ] **Step 2: Add new User/Role schemas** at the end of the `components.schemas` section (before `securitySchemes`):

```yaml
    UpdateUserRequest:
      type: object
      properties:
        email:
          type: string
          format: email
          nullable: true
        vorname:
          type: string
          nullable: true
        nachname:
          type: string
          nullable: true
        geburtsdatum:
          type: string
          format: date
          nullable: true
        geschlecht:
          type: string
          nullable: true
          enum: [MAENNLICH, WEIBLICH, DIVERS, UNBEKANNT]
        telefonnummer:
          type: string
          nullable: true
        telefonBestaetigt:
          type: boolean
          nullable: true
        strasse:
          type: string
          nullable: true
        hausnummer:
          type: string
          nullable: true
        plz:
          type: string
          nullable: true
        stadt:
          type: string
          nullable: true
        land:
          type: string
          nullable: true
        profilbildUrl:
          type: string
          nullable: true
        biographie:
          type: string
          nullable: true
        sprache:
          type: string
          nullable: true
          enum: [DE, EN, FR, IT]
        mitgliedsnummer:
          type: string
          nullable: true
        schiessLizenz:
          type: string
          nullable: true
        schiessLizenzVerfallsdatum:
          type: string
          format: date
          nullable: true
        status:
          type: string
          nullable: true
          enum: [ACTIVE, INACTIVE, SUSPENDED, PENDING_APPROVAL]

    ChangePasswordRequest:
      type: object
      required: [oldPassword, newPassword]
      properties:
        oldPassword:
          type: string
        newPassword:
          type: string

    UserResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
        email:
          type: string
        vorname:
          type: string
        nachname:
          type: string
        fullName:
          type: string
          nullable: true
        geburtsdatum:
          type: string
          format: date
          nullable: true
        geschlecht:
          type: string
          nullable: true
        telefonnummer:
          type: string
          nullable: true
        strasse:
          type: string
          nullable: true
        hausnummer:
          type: string
          nullable: true
        plz:
          type: string
          nullable: true
        stadt:
          type: string
          nullable: true
        land:
          type: string
          nullable: true
        profilbildUrl:
          type: string
          nullable: true
        biographie:
          type: string
          nullable: true
        sprache:
          type: string
          nullable: true
        mitgliedsnummer:
          type: string
          nullable: true
        schiessLizenz:
          type: string
          nullable: true
        schiessLizenzVerfallsdatum:
          type: string
          format: date
          nullable: true
        status:
          type: string
          enum: [ACTIVE, INACTIVE, SUSPENDED, PENDING_APPROVAL]
        erstelltAm:
          type: string
          format: date-time
        aktualisiertAm:
          type: string
          format: date-time
          nullable: true
        assignedRangeId:
          type: string
          format: uuid
          nullable: true

    UserPageResponse:
      type: object
      properties:
        content:
          type: array
          items:
            $ref: '#/components/schemas/UserResponse'
        meta:
          $ref: '#/components/schemas/PageMeta'

    RoleResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
        name:
          type: string
        description:
          type: string
          nullable: true

    UserRoleResponse:
      type: object
      properties:
        roleId:
          type: string
          format: uuid
        roleName:
          type: string
        description:
          type: string
          nullable: true
        scopeType:
          type: string
          nullable: true
          enum: [RANGE, COMPETITION, FACILITY]
        scopeId:
          type: string
          format: uuid
          nullable: true
        assignedAt:
          type: string
          format: date-time
        expiresAt:
          type: string
          format: date-time
          nullable: true
        isExpired:
          type: boolean
          nullable: true

    AssignRoleRequest:
      type: object
      required: [roleName]
      properties:
        roleName:
          type: string
        expiresAt:
          type: string
          format: date-time
          nullable: true

    AssignScopedRoleRequest:
      type: object
      required: [roleName, scopeType, scopeId]
      properties:
        roleName:
          type: string
        scopeType:
          type: string
          enum: [RANGE, COMPETITION, FACILITY]
        scopeId:
          type: string
          format: uuid
        expiresAt:
          type: string
          format: date-time
          nullable: true
```

- [ ] **Step 3: Update `POST /api/users` to include a response body and fix all user paths**

Find the existing `/api/users` path block:

```yaml
  /api/users:
    post:
      summary: Create a new user
      operationId: createUser
      tags: [User]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateUserRequest'
      responses:
        '201':
          description: User created successfully
        '409':
          description: User already exists
```

Replace with:

```yaml
  /api/users:
    get:
      summary: List all users (admin only)
      operationId: listUsers
      tags: [User]
      parameters:
        - name: page
          in: query
          required: false
          schema:
            type: integer
            minimum: 0
            default: 0
        - name: size
          in: query
          required: false
          schema:
            type: integer
            minimum: 1
            maximum: 200
            default: 20
      responses:
        '200':
          description: Paginated list of users
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserPageResponse'
        '403':
          description: Not an admin
    post:
      summary: Create a new user
      operationId: createUser
      tags: [User]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateUserRequest'
      responses:
        '201':
          description: User created successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserResponse'
        '409':
          description: Email or membership number already registered

  /api/users/me/password:
    post:
      summary: Change the calling user's password
      operationId: changePassword
      tags: [User]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ChangePasswordRequest'
      responses:
        '204':
          description: Password changed
        '400':
          description: Old password incorrect

  /api/users/{id}:
    get:
      summary: Get user by ID
      operationId: getUser
      tags: [User]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: User found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserResponse'
        '404':
          description: User not found
    patch:
      summary: Partially update a user profile
      operationId: updateUser
      tags: [User]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UpdateUserRequest'
      responses:
        '200':
          description: User updated
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserResponse'
        '403':
          description: Cannot modify another user's profile
        '404':
          description: User not found
    delete:
      summary: Soft-delete a user (admin only)
      operationId: deleteUser
      tags: [User]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: User deleted
        '403':
          description: Not an admin
        '404':
          description: User not found

  /api/users/{id}/roles:
    get:
      summary: List all roles assigned to a user
      operationId: getUserRoles
      tags: [User]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: List of role assignments
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/UserRoleResponse'
        '404':
          description: User not found
    post:
      summary: Assign a global role to a user (admin only)
      operationId: assignRole
      tags: [User]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/AssignRoleRequest'
      responses:
        '201':
          description: Role assigned
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserRoleResponse'
        '404':
          description: User or role not found

  /api/users/{id}/roles/scoped:
    post:
      summary: Assign a scoped role to a user (admin only)
      operationId: assignScopedRole
      tags: [User]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/AssignScopedRoleRequest'
      responses:
        '201':
          description: Scoped role assigned
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserRoleResponse'
        '400':
          description: scopeType and scopeId are required
        '404':
          description: User or role not found

  /api/users/{id}/roles/{roleName}:
    delete:
      summary: Revoke a global role from a user (admin only)
      operationId: revokeRole
      tags: [User]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
        - name: roleName
          in: path
          required: true
          schema:
            type: string
      responses:
        '204':
          description: Role revoked
        '404':
          description: User or role assignment not found

  /api/users/{id}/roles/{roleName}/scoped:
    delete:
      summary: Revoke a scoped role from a user (admin only)
      operationId: revokeScopedRole
      tags: [User]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
        - name: roleName
          in: path
          required: true
          schema:
            type: string
        - name: scopeType
          in: query
          required: true
          schema:
            type: string
        - name: scopeId
          in: query
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: Scoped role revoked
        '404':
          description: User or scoped access not found
```

- [ ] **Step 4: Add `GET /api/roles`**

Add this new path block in the `paths` section, after the user paths and before the DeviceType section:

```yaml
  # ─────────────────────────────────────────────────────────────────────────────
  # Roles
  # ─────────────────────────────────────────────────────────────────────────────
  /api/roles:
    get:
      summary: List all roles (admin only)
      operationId: listRoles
      tags: [Role]
      responses:
        '200':
          description: List of roles
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/RoleResponse'
        '403':
          description: Not an admin
```

- [ ] **Step 5: Remove `GET /api/events`**

Delete the entire `Events` section from `paths` (around line 886–915):

```yaml
  # ─────────────────────────────────────────────────────────────────────────────
  # Events (SSE)
  # ─────────────────────────────────────────────────────────────────────────────
  /api/events:
    get:
      ...
```

Also delete the three SSE event schemas from `components.schemas`:
- `SmartBoxStatusEvent`
- `SmartBoxSyncedEvent`  
- `DeviceHealthEvent`
- `StreamEvents200Response` (generated wrapper — will disappear after re-gen)

---

## Task 2: Regenerate sources

**Files:**
- Generate: all files under `target/generated-sources/openapi/`

- [ ] **Step 1: Run code generation**

```bash
./mvnw generate-sources
```

Expected: `BUILD SUCCESS`. The following new interfaces/models will appear under `target/generated-sources/openapi/src/main/java/ch/jp/smartground/`:
- `api/UserApi.java` — now has `listUsers`, `createUser`, `getUser`, `updateUser`, `deleteUser`, `changePassword`, `getUserRoles`, `assignRole`, `assignScopedRole`, `revokeRole`, `revokeScopedRole`
- `api/RoleApi.java` — new, has `listRoles`
- `model/UpdateUserRequest.java`, `model/UserResponse.java`, `model/UserPageResponse.java`
- `model/ChangePasswordRequest.java`, `model/UserRoleResponse.java`
- `model/AssignRoleRequest.java`, `model/AssignScopedRoleRequest.java`
- `model/RoleResponse.java`
- `api/EventsApi.java` — should now be **absent** (or empty) after removing the events path

- [ ] **Step 2: Verify compilation**

```bash
./mvnw compile
```

Expected: `BUILD SUCCESS`. There will be compiler errors if any controller references the old generated types (e.g. `ch.jp.smartground.model.CreateUserRequest` with the old `username` field). Fix those before continuing.

If `UserController` still has `@RequestMapping` and `@GetMapping` — that is fine for now; we fix it in Task 3.

---

## Task 3: Migrate `UserController` to implement `UserApi`

**Files:**
- Modify: `src/main/java/ch/jp/shooting/api/UserController.java`

`UserApi` (newly generated) maps generated model types to the controller. The controller must implement the interface and remove all Spring routing annotations — routing is now owned by the interface.

- [ ] **Step 1: Rewrite `UserController.java`**

```java
package ch.jp.shooting.api;

import ch.jp.shooting.dto.*;
import ch.jp.shooting.exception.ForbiddenException;
import ch.jp.shooting.service.auth.AuthorizationService;
import ch.jp.shooting.service.auth.UserService;
import ch.jp.smartground.api.UserApi;
import ch.jp.smartground.model.*;
import jakarta.validation.Valid;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
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

    // ── User CRUD ──────────────────────────────────────────────────────────────

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
        dto.setPassword(request.getPassword());
        dto.setVorname(request.getVorname());
        dto.setNachname(request.getNachname());
        dto.setGeburtsdatum(request.getGeburtsdatum());
        dto.setGeschlecht(request.getGeschlecht() != null ? request.getGeschlecht().getValue() : null);
        dto.setTelefonnummer(request.getTelefonnummer());
        dto.setStrasse(request.getStrasse());
        dto.setHausnummer(request.getHausnummer());
        dto.setPlz(request.getPlz());
        dto.setStadt(request.getStadt());
        dto.setLand(request.getLand());
        dto.setMitgliedsnummer(request.getMitgliedsnummer());
        dto.setSprache(request.getSprache() != null ? request.getSprache().getValue() : null);
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
        dto.setEmail(request.getEmail());
        dto.setVorname(request.getVorname());
        dto.setNachname(request.getNachname());
        dto.setGeburtsdatum(request.getGeburtsdatum());
        dto.setGeschlecht(request.getGeschlecht() != null ? request.getGeschlecht().getValue() : null);
        dto.setTelefonnummer(request.getTelefonnummer());
        dto.setTelefonBestaetigt(request.getTelefonBestaetigt());
        dto.setStrasse(request.getStrasse());
        dto.setHausnummer(request.getHausnummer());
        dto.setPlz(request.getPlz());
        dto.setStadt(request.getStadt());
        dto.setLand(request.getLand());
        dto.setProfilbildUrl(request.getProfilbildUrl());
        dto.setBiographie(request.getBiographie());
        dto.setSprache(request.getSprache() != null ? request.getSprache().getValue() : null);
        dto.setMitgliedsnummer(request.getMitgliedsnummer());
        dto.setSchiessLizenz(request.getSchiessLizenz());
        dto.setSchiessLizenzVerfallsdatum(request.getSchiessLizenzVerfallsdatum());
        dto.setStatus(request.getStatus() != null ? request.getStatus().getValue() : null);
        return ResponseEntity.ok(toResponse(userService.updateUser(id, dto)));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> changePassword(@Valid ch.jp.smartground.model.ChangePasswordRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDTO user = userService.getUserByEmail(auth.getName());
        userService.changePassword(user.getId(), request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    // ── Role management ────────────────────────────────────────────────────────

    @Override
    public ResponseEntity<List<UserRoleResponse>> getUserRoles(UUID id) {
        return ResponseEntity.ok(userService.getUserRoles(id).stream().map(this::toRoleResponse).toList());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserRoleResponse> assignRole(UUID id, @Valid ch.jp.smartground.model.AssignRoleRequest request) {
        ch.jp.shooting.dto.AssignRoleRequest dto = new ch.jp.shooting.dto.AssignRoleRequest(request.getRoleName());
        if (request.getExpiresAt() != null) {
            dto.setExpiresAt(request.getExpiresAt().toInstant());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toRoleResponse(userService.assignRole(id, dto.getRoleName())));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserRoleResponse> assignScopedRole(UUID id, @Valid ch.jp.smartground.model.AssignScopedRoleRequest request) {
        java.time.Instant expiresAt = request.getExpiresAt() != null ? request.getExpiresAt().toInstant() : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(
                toRoleResponse(userService.assignScopedRole(
                        id, request.getRoleName(),
                        request.getScopeType().getValue(),
                        request.getScopeId(),
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
        return new UserResponse()
                .id(dto.getId())
                .email(dto.getEmail())
                .vorname(dto.getVorname())
                .nachname(dto.getNachname())
                .fullName(dto.getFullName())
                .geburtsdatum(dto.getGeburtsdatum())
                .geschlecht(dto.getGeschlecht())
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
                .status(dto.getStatus())
                .erstelltAm(dto.getErstelltAm() != null ? dto.getErstelltAm().atOffset(ZoneOffset.UTC) : null)
                .aktualisiertAm(dto.getAktualisiertAm() != null ? dto.getAktualisiertAm().atOffset(ZoneOffset.UTC) : null)
                .assignedRangeId(dto.getAssignedRangeId());
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
            r.scopeType(dto.getScopeType());
        }
        return r;
    }
}
```

> **Note on enum mapping:** After `./mvnw generate-sources` the exact enum type names for `GeschlechtEnum`, `SpracheEnum`, `StatusEnum` in the generated `UpdateUserRequest`/`CreateUserRequest` may differ slightly. Check `target/generated-sources/openapi/src/main/java/ch/jp/smartground/model/UpdateUserRequest.java` and adjust `.getValue()` calls to match. If the generated class uses `String` directly (no enum), remove the `.getValue()` call.

- [ ] **Step 2: Compile**

```bash
./mvnw compile
```

Expected: `BUILD SUCCESS`. Fix any remaining import or type-mismatch errors — they will all be enum accessor mismatches.

- [ ] **Step 3: Run all tests**

```bash
./mvnw test
```

Expected: `BUILD SUCCESS`. If `UserControllerTest` exists and fails, check whether it was testing the old URL mapping; update the test to use the spec-driven route.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/openapi.yaml src/main/java/ch/jp/shooting/api/UserController.java
git commit -m "[backend] Migrate UserController to generated UserApi; fix CreateUserRequest schema"
```

---

## Task 4: Migrate `RoleController` to implement `RoleApi`

**Files:**
- Modify: `src/main/java/ch/jp/shooting/api/RoleController.java`

After Task 2, `target/generated-sources/openapi/src/main/java/ch/jp/smartground/api/RoleApi.java` exists with a single `listRoles()` method.

- [ ] **Step 1: Rewrite `RoleController.java`**

```java
package ch.jp.shooting.api;

import ch.jp.shooting.repository.auth.RoleRepository;
import ch.jp.smartground.api.RoleApi;
import ch.jp.smartground.model.RoleResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@NullMarked
public class RoleController implements RoleApi {

    private final RoleRepository roleRepository;

    public RoleController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RoleResponse>> listRoles() {
        List<RoleResponse> roles = roleRepository.findAll()
                .stream()
                .map(r -> new RoleResponse().id(r.getId()).name(r.getName()).description(r.getDescription()))
                .toList();
        return ResponseEntity.ok(roles);
    }
}
```

- [ ] **Step 2: Compile and test**

```bash
./mvnw test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ch/jp/shooting/api/RoleController.java
git commit -m "[backend] Migrate RoleController to generated RoleApi"
```

---

## Task 5: Consolidate DeviceType controllers into one

**Files:**
- Modify: `src/main/java/ch/jp/shooting/api/DeviceTypeController.java`
- Delete: `src/main/java/ch/jp/shooting/api/DeviceTypeGroupController.java`
- Delete: `src/main/java/ch/jp/shooting/api/FirmwareConfigController.java`

`DeviceTypeApi` (generated) already contains methods for groups and firmware configs. Currently `DeviceTypeController` only overrides `listDeviceTypes`, `getDeviceType`, and `updateDeviceType`. The group and firmware methods live in separate controllers that bypass the interface. The fix: merge everything into `DeviceTypeController`.

- [ ] **Step 1: Add group and firmware methods to `DeviceTypeController.java`**

The full rewritten class (replaces entire file):

```java
package ch.jp.shooting.api;

import ch.jp.shooting.dto.FirmwareConfigManifest;
import ch.jp.shooting.exception.DeviceTemplateNotFoundException;
import ch.jp.shooting.model.DeviceType;
import ch.jp.shooting.model.DeviceTypeGroup;
import ch.jp.shooting.model.FirmwareConfig;
import ch.jp.shooting.repository.DeviceTypeGroupRepository;
import ch.jp.shooting.repository.DeviceTypeRepository;
import ch.jp.shooting.repository.FirmwareConfigRepository;
import ch.jp.shooting.service.FirmwareConfigService;
import ch.jp.smartground.api.DeviceTypeApi;
import ch.jp.smartground.model.*;
import jakarta.validation.Valid;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@NullMarked
public class DeviceTypeController implements DeviceTypeApi {

    private final DeviceTypeRepository deviceTypeRepository;
    private final DeviceTypeGroupRepository groupRepository;
    private final FirmwareConfigService firmwareConfigService;
    private final FirmwareConfigRepository firmwareConfigRepository;

    public DeviceTypeController(DeviceTypeRepository deviceTypeRepository,
                                DeviceTypeGroupRepository groupRepository,
                                FirmwareConfigService firmwareConfigService,
                                FirmwareConfigRepository firmwareConfigRepository) {
        this.deviceTypeRepository = deviceTypeRepository;
        this.groupRepository = groupRepository;
        this.firmwareConfigService = firmwareConfigService;
        this.firmwareConfigRepository = firmwareConfigRepository;
    }

    // ── DeviceType ─────────────────────────────────────────────────────────────

    @Override
    public ResponseEntity<List<DeviceTypeResponse>> listDeviceTypes(UUID firmwareConfigId) {
        return ResponseEntity.ok(deviceTypeRepository.findAll().stream()
                .filter(dt -> firmwareConfigId == null
                        || dt.getSignalType().getFirmwareConfig().getId().equals(firmwareConfigId))
                .map(this::toDeviceTypeResponse)
                .toList());
    }

    @Override
    public ResponseEntity<DeviceTypeResponse> getDeviceType(UUID id) {
        return deviceTypeRepository.findById(id)
                .map(dt -> ResponseEntity.ok(toDeviceTypeResponse(dt)))
                .orElseThrow(() -> new DeviceTemplateNotFoundException(id));
    }

    @Override
    public ResponseEntity<DeviceTypeResponse> updateDeviceType(UUID id, @Valid UpdateDeviceTypeRequest request) {
        DeviceType deviceType = deviceTypeRepository.findById(id)
                .orElseThrow(() -> new DeviceTemplateNotFoundException(id));

        if (request.getName() != null) {
            deviceType.setName(request.getName());
        }
        var signalDurationMs = request.getSignalDurationMs();
        if (signalDurationMs != null && signalDurationMs.isPresent()) {
            deviceType.setSignalDurationMs(signalDurationMs.get());
        }
        var delaySignalDurationMs = request.getDelaySignalDurationMs();
        if (delaySignalDurationMs != null && delaySignalDurationMs.isPresent()) {
            deviceType.setDelaySignalDurationMs(delaySignalDurationMs.get());
        }
        return ResponseEntity.ok(toDeviceTypeResponse(deviceTypeRepository.save(deviceType)));
    }

    // ── DeviceTypeGroup ────────────────────────────────────────────────────────

    @Override
    public ResponseEntity<List<DeviceTypeGroupResponse>> listDeviceTypeGroups() {
        return ResponseEntity.ok(groupRepository.findAll().stream()
                .map(this::toGroupResponse)
                .toList());
    }

    @Override
    public ResponseEntity<DeviceTypeGroupResponse> getDeviceTypeGroup(UUID id) {
        return groupRepository.findById(id)
                .map(g -> ResponseEntity.ok(toGroupResponse(g)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── FirmwareConfig ─────────────────────────────────────────────────────────

    @Override
    public ResponseEntity<List<FirmwareConfigResponse>> listFirmwareConfigs() {
        return ResponseEntity.ok(firmwareConfigRepository.findAll().stream()
                .map(this::toFirmwareConfigResponse)
                .toList());
    }

    @Override
    public ResponseEntity<FirmwareConfigResponse> getFirmwareConfig(UUID id) {
        return firmwareConfigRepository.findById(id)
                .map(fc -> ResponseEntity.ok(toFirmwareConfigResponse(fc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FirmwareConfigResponse> registerFirmwareConfig(
            @Valid RegisterFirmwareConfigRequest request) {
        // Map generated model → internal DTO
        FirmwareConfigManifest manifest = new FirmwareConfigManifest(
                request.getVersion(),
                request.getBoxType(),
                request.getSignalTypes().stream()
                        .map(e -> new FirmwareConfigManifest.SignalTypeEntry(
                                ch.jp.shooting.model.CommunicationDirection.valueOf(e.getDirection().toUpperCase()),
                                ch.jp.shooting.model.DeviceKind.valueOf(e.getDevice().toUpperCase()),
                                e.getCommand(),
                                e.getGroupName(),
                                e.getName(),
                                e.getSignalDurationMs(),
                                e.getDelaySignalDurationMs()
                        ))
                        .toList()
        );
        FirmwareConfig fc = firmwareConfigService.register(manifest);
        return ResponseEntity.status(HttpStatus.CREATED).body(toFirmwareConfigResponse(fc));
    }

    // ── Mappers ────────────────────────────────────────────────────────────────

    private DeviceTypeResponse toDeviceTypeResponse(DeviceType t) {
        var signalType = t.getSignalType();
        var group = t.getGroup();
        return new DeviceTypeResponse()
                .id(t.getId())
                .name(t.getName())
                .signalTypeId(signalType.getId())
                .groupId(group.getId())
                .groupName(group.getName())
                .signalDurationMs(t.getSignalDurationMs())
                .delaySignalDurationMs(t.getDelaySignalDurationMs())
                .command(signalType.getCommand())
                .device(signalType.getDevice().name())
                .direction(signalType.getCommunicationDirection().name());
    }

    private DeviceTypeGroupResponse toGroupResponse(DeviceTypeGroup g) {
        return new DeviceTypeGroupResponse().id(g.getId()).name(g.getName());
    }

    private FirmwareConfigResponse toFirmwareConfigResponse(FirmwareConfig fc) {
        return new FirmwareConfigResponse()
                .id(fc.getId())
                .version(fc.getVersion())
                .boxType(fc.getBoxType())
                .signalTypes(fc.getSignalTypes().stream()
                        .map(st -> new FirmwareConfigSignalTypeResponse()
                                .id(st.getId())
                                .communicationDirection(st.getCommunicationDirection().name())
                                .device(st.getDevice().name())
                                .command(st.getCommand()))
                        .toList());
    }
}
```

> **Note:** `RegisterFirmwareConfigSignalTypeEntry` in the generated model has `getDirection()`, `getDevice()`, `getCommand()`, `getGroupName()`, `getName()`, `getSignalDurationMs()`, `getDelaySignalDurationMs()`. Verify these by reading `target/generated-sources/openapi/src/main/java/ch/jp/smartground/model/RegisterFirmwareConfigSignalTypeEntry.java` if compilation fails.

- [ ] **Step 2: Delete the two old controllers**

```bash
rm src/main/java/ch/jp/shooting/api/DeviceTypeGroupController.java
rm src/main/java/ch/jp/shooting/api/FirmwareConfigController.java
```

- [ ] **Step 3: Compile and test**

```bash
./mvnw test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ch/jp/shooting/api/DeviceTypeController.java
git rm src/main/java/ch/jp/shooting/api/DeviceTypeGroupController.java
git rm src/main/java/ch/jp/shooting/api/FirmwareConfigController.java
git commit -m "[backend] Consolidate DeviceType/Group/FirmwareConfig into one controller; implement DeviceTypeApi"
```

---

## Task 6: Fix `RangeController.sendPositionCommand`

**Files:**
- Modify: `src/main/java/ch/jp/shooting/api/RangeController.java:189–201`

The method has a `TODO` comment and uses `@PostMapping` / `@PathVariable` directly instead of `@Override`. The generated `RangeApi` already has `sendPositionCommand(UUID id, UUID positionId)`. Remove the workaround.

- [ ] **Step 1: Replace the workaround with a proper override**

Find this block in `RangeController.java`:

```java
    // TODO: Sobald mvnw generate-sources gelaufen ist und RangeApi.sendPositionCommand generiert wurde,
    //       @Override wiederherstellen und @PostMapping / @PathVariable entfernen.
    @PostMapping(value = "/api/ranges/{id}/positions/{positionId}/command", produces = "application/json")
    public ResponseEntity<CommandResponse> sendPositionCommand(
            @PathVariable("id") UUID id,
            @PathVariable("positionId") UUID positionId) {
```

Replace with:

```java
    @Override
    public ResponseEntity<CommandResponse> sendPositionCommand(UUID id, UUID positionId) {
```

The method body stays the same.

- [ ] **Step 2: Compile and test**

```bash
./mvnw test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ch/jp/shooting/api/RangeController.java
git commit -m "[backend] Restore @Override on RangeController.sendPositionCommand"
```

---

## Task 7: Final verification

- [ ] **Step 1: Full clean build with tests**

```bash
./mvnw clean test
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 2: Verify the checklist**

Run through the code review checklist for every modified controller:

```
[ ] openapi.yaml updated — every HTTP method has a spec entry
[ ] Controller class implements the generated interface (implements FooApi)
[ ] No @RequestMapping on the class
[ ] No @GetMapping / @PostMapping / @PatchMapping / @DeleteMapping on methods
[ ] Auth: @PreAuthorize where needed
[ ] @NullMarked on all modified classes
```

Apply any fixes found.

- [ ] **Step 3: Final commit if anything was adjusted**

```bash
git add -p   # stage only the specific fixes
git commit -m "[backend] Code review checklist fixes after controller migration"
```

---

## Self-Review

**Spec coverage:**

| Requirement | Covered by |
|-------------|------------|
| Fix `CreateUserRequest` schema | Task 1 Step 1 |
| Add `UserResponse` / `UserPageResponse` schemas | Task 1 Step 2 |
| Add `UpdateUserRequest`, `ChangePasswordRequest` schemas | Task 1 Step 2 |
| Add role schemas (`RoleResponse`, `UserRoleResponse`, etc.) | Task 1 Step 2 |
| Add `GET /api/users`, `PATCH /api/users/{id}`, `DELETE /api/users/{id}` | Task 1 Step 3 |
| Add `POST /api/users/me/password` | Task 1 Step 3 |
| Add role sub-endpoints | Task 1 Step 3 |
| Add `GET /api/roles` | Task 1 Step 4 |
| Remove `GET /api/events` | Task 1 Step 5 |
| Re-generate interfaces | Task 2 |
| Migrate `UserController` | Task 3 |
| Migrate `RoleController` | Task 4 |
| Consolidate `DeviceTypeGroupController` + `FirmwareConfigController` | Task 5 |
| Fix `sendPositionCommand` workaround | Task 6 |

**Potential gaps:**
- `POST /api/device-types/groups` in old `DeviceTypeGroupController` is intentionally dropped (not in spec; no business need identified).
- The `GET /api/events` SSE spec entry is removed; the real-time channel is STOMP/WebSocket.
- The `UserResponse` mapper does not map `geschlecht` or `sprache` as enums — they are passed as plain strings to match `UserDTO`. This is consistent with how `MeResponse` handles the same fields in `AuthController`.
