# Username + Email-or-Username Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a required, unique, case-insensitive `username` to users and let people log in with either their email or username (and let shooters change their own username).

**Architecture:** Add `username` + `username_lower` (shadow column for portable case-insensitive uniqueness) to the `User` entity. Login resolves either identifier to the canonical user; the JWT subject stays the email so `getMe` is unaffected. The frontend relabels the login field, adds the field to the admin create/edit modal and user list, and gives shooters a small self-service edit modal.

**Tech Stack:** Java 25 / Spring Boot 4 / JPA-Hibernate / H2 (tests) / OpenAPI generator; Vue 3 + Pinia + Vitest.

**Spec:** `docs/superpowers/specs/2026-06-26-username-login-design.md`

**Conventions reminder:** `@NullMarked` on new classes; German inline comments for domain logic; controllers implement generated interfaces (no Spring mapping annotations); JSON/text columns only; commit messages `[backend|ui] short description`.

---

## Backend

### Task 1: Add `username` + `username_lower` to the `User` entity

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/model/auth/User.java`
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/model/auth/UserTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `smart-ground-backend/src/test/java/ch/jp/shooting/model/auth/UserTest.java`:

```java
package ch.jp.shooting.model.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserTest {

    @Test
    void setUsername_trimsAndComputesLowercaseShadow() {
        User user = new User("a@b.ch", "Jonas", "Studer");
        user.setUsername("  JonasS  ");

        assertEquals("JonasS", user.getUsername());
        assertEquals("jonass", user.getUsernameLower());
    }

    @Test
    void toUsernameLower_isTrimmedLowercase() {
        assertEquals("jonass", User.toUsernameLower("  JonasS "));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-ground-backend && ./mvnw test -Dtest=UserTest`
Expected: FAIL — compilation error, `setUsername`/`getUsernameLower`/`toUsernameLower` do not exist.

- [ ] **Step 3: Add the fields, accessors, and normalization helper**

In `User.java`, in the `// ==================== AUTHENTICATION ====================` block, right after the `email` column (around line 20), add:

```java
    @Column(nullable = false)
    private String username; // Anzeige-Schreibweise (wie eingegeben)

    @Column(name = "username_lower", nullable = false, unique = true)
    private String usernameLower; // Kleinschreibung – für Login-Suche & Eindeutigkeit
```

Add accessors near the `email` getters/setters (around line 139):

```java
    public String getUsername() { return username; }

    /** Setzt den Benutzernamen und berechnet automatisch die Kleinschreibungs-Variante. */
    public void setUsername(String username) {
        this.username = username.trim();
        this.usernameLower = toUsernameLower(username);
    }

    public String getUsernameLower() { return usernameLower; }

    /** Normalisiert einen Benutzernamen für Eindeutigkeits-/Login-Vergleiche. */
    public static String toUsernameLower(String username) {
        return username.trim().toLowerCase(java.util.Locale.ROOT);
    }
```

(`usernameLower` has no public setter — it is derived only.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-ground-backend && ./mvnw test -Dtest=UserTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/model/auth/User.java smart-ground-backend/src/test/java/ch/jp/shooting/model/auth/UserTest.java
git commit -m "[backend] add username + username_lower to User entity"
```

---

### Task 2: Set `username` everywhere a `User` is persisted (seed + tests)

A `NOT NULL` column breaks any code that saves a `User` without a username. Find and fix all of them.

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/config/DataInitializer.java`
- Modify: any other `main`/`test` source that does `new User(...)` then persists.

- [ ] **Step 1: List every `new User(` site**

Run: `cd smart-ground-backend && grep -rn "new User(" src/`
Expected: at least `DataInitializer.java` lines ~169 and ~183. Note every hit that is later passed to `userRepository.save(...)` / persisted via a relation.

- [ ] **Step 2: Set usernames on the seed users**

In `DataInitializer.java`, after `User admin = new User("admin@smartground.local", "Admin", "User");` (line ~169) add:

```java
        admin.setUsername("admin");
```

After `User shooter = new User("user@smartground.local", "Test", "Schütze");` (line ~183) add:

```java
        shooter.setUsername("user");
```

- [ ] **Step 3: Set a username on every other persisted `new User(...)` found in Step 1**

For each remaining hit (including in test fixtures), add `<var>.setUsername("<unique-handle>")` before it is saved. Use distinct handles per user in a given test to avoid `username_lower` unique collisions.

- [ ] **Step 4: Run the build to surface anything missed**

Run: `cd smart-ground-backend && ./mvnw test`
Expected: any remaining unset-username persistence fails fast with a not-null / unique constraint violation; fix those sites the same way. Re-run until green (existing tests pass).

- [ ] **Step 5: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/config/DataInitializer.java
# plus any test files you edited
git commit -m "[backend] seed usernames and set username on persisted users"
```

---

### Task 3: Repository — find by email-or-username, and by username_lower

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/repository/auth/UserRepository.java`
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/repository/auth/UserRepositoryTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `smart-ground-backend/src/test/java/ch/jp/shooting/repository/auth/UserRepositoryTest.java`:

```java
package ch.jp.shooting.repository.auth;

import ch.jp.shooting.model.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class UserRepositoryTest {

    @Autowired UserRepository userRepository;

    @BeforeEach
    void seed() {
        User u = new User("jonas@example.com", "Jonas", "Studer");
        u.setUsername("JonasS");
        userRepository.save(u);
    }

    @Test
    void findByEmailOrUsername_matchesEmailCaseInsensitive() {
        Optional<User> found = userRepository.findByEmailOrUsernameWithRoles("JONAS@example.com");
        assertTrue(found.isPresent());
        assertEquals("JonasS", found.get().getUsername());
    }

    @Test
    void findByEmailOrUsername_matchesUsernameCaseInsensitive() {
        Optional<User> found = userRepository.findByEmailOrUsernameWithRoles("jonass");
        assertTrue(found.isPresent());
        assertEquals("jonas@example.com", found.get().getEmail());
    }

    @Test
    void findByUsernameLower_returnsUser() {
        assertTrue(userRepository.findByUsernameLower("jonass").isPresent());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-ground-backend && ./mvnw test -Dtest=UserRepositoryTest`
Expected: FAIL — methods `findByEmailOrUsernameWithRoles` / `findByUsernameLower` do not exist.

- [ ] **Step 3: Add the queries**

In `UserRepository.java`, add inside the interface (after `findByEmailWithRoles`):

```java
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role r "
         + "LEFT JOIN FETCH r.permissions "
         + "WHERE LOWER(u.email) = LOWER(:login) OR u.usernameLower = LOWER(:login)")
    Optional<User> findByEmailOrUsernameWithRoles(@Param("login") String login);

    Optional<User> findByUsernameLower(String usernameLower);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-ground-backend && ./mvnw test -Dtest=UserRepositoryTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/repository/auth/UserRepository.java smart-ground-backend/src/test/java/ch/jp/shooting/repository/auth/UserRepositoryTest.java
git commit -m "[backend] add email-or-username and username_lower repository queries"
```

---

### Task 4: Login resolution — `CustomUserDetailsService`

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/CustomUserDetailsService.java`
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/service/CustomUserDetailsServiceTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `smart-ground-backend/src/test/java/ch/jp/shooting/service/CustomUserDetailsServiceTest.java`:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks CustomUserDetailsService service;

    @Test
    void loadUserByUsername_resolvesByUsername_butPrincipalIsEmail() {
        User user = new User("jonas@example.com", "Jonas", "Studer");
        user.setUsername("JonasS");
        user.setPasswordHash("hash");
        when(userRepository.findByEmailOrUsernameWithRoles("jonass"))
                .thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("jonass");

        // JWT-Subject bleibt die kanonische E-Mail
        assertEquals("jonas@example.com", details.getUsername());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-ground-backend && ./mvnw test -Dtest=CustomUserDetailsServiceTest`
Expected: FAIL — service still calls `findByEmailWithRoles`, so the stubbed `findByEmailOrUsernameWithRoles` is never matched (returns null → `UsernameNotFoundException`).

- [ ] **Step 3: Switch the lookup**

In `CustomUserDetailsService.loadUserByUsername`, change the repository call from:

```java
        User user = userRepository.findByEmailWithRoles(emailOrUsername)
```

to:

```java
        User user = userRepository.findByEmailOrUsernameWithRoles(emailOrUsername)
```

Leave the rest unchanged — the principal is still built with `.username(user.getEmail())`, so the JWT subject remains the canonical email.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-ground-backend && ./mvnw test -Dtest=CustomUserDetailsServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/service/CustomUserDetailsService.java smart-ground-backend/src/test/java/ch/jp/shooting/service/CustomUserDetailsServiceTest.java
git commit -m "[backend] resolve login by email or username"
```

---

### Task 5: Internal DTOs + mapper gain `username`

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/dto/UserDTO.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/dto/CreateUserRequest.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/dto/UpdateUserRequest.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/mapper/UserMapper.java`
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/mapper/UserMapperTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `smart-ground-backend/src/test/java/ch/jp/shooting/mapper/UserMapperTest.java`:

```java
package ch.jp.shooting.mapper;

import ch.jp.shooting.dto.UserDTO;
import ch.jp.shooting.model.auth.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    void toDto_mapsUsername() {
        User user = new User("jonas@example.com", "Jonas", "Studer");
        user.setUsername("JonasS");

        UserDTO dto = mapper.toDto(user);

        assertEquals("JonasS", dto.getUsername());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-ground-backend && ./mvnw test -Dtest=UserMapperTest`
Expected: FAIL — `UserDTO.getUsername` does not exist.

- [ ] **Step 3: Add `username` to the three DTOs and the mapper**

In `UserDTO.java`, add a field after `email` (around line 13) and accessors:

```java
    private String username;
```
```java
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
```

In `CreateUserRequest.java`, add after the `email` field (around line 13). It is required, so annotate `@NotBlank`:

```java
    @NotBlank
    private String username;
```
```java
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
```

In `UpdateUserRequest.java`, add after the `email` field (it is optional → `@Nullable`):

```java
    @Nullable
    private String username;
```
```java
    @Nullable
    public String getUsername() { return username; }
    public void setUsername(@Nullable String username) { this.username = username; }
```

In `UserMapper.toDto`, after `dto.setEmail(user.getEmail());` add:

```java
        dto.setUsername(user.getUsername());
```

In `UserMapper.toEntity`, after `user.setEmail(dto.getEmail());` add:

```java
        if (dto.getUsername() != null) {
            user.setUsername(dto.getUsername());
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-ground-backend && ./mvnw test -Dtest=UserMapperTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/dto/UserDTO.java smart-ground-backend/src/main/java/ch/jp/shooting/dto/CreateUserRequest.java smart-ground-backend/src/main/java/ch/jp/shooting/dto/UpdateUserRequest.java smart-ground-backend/src/main/java/ch/jp/shooting/mapper/UserMapper.java smart-ground-backend/src/test/java/ch/jp/shooting/mapper/UserMapperTest.java
git commit -m "[backend] add username to internal user DTOs and mapper"
```

---

### Task 6: `UserService` — validate + enforce unique username on create/update

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/auth/UserService.java`
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/service/UserServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Append to `UserServiceTest.java` (add the imports shown):

```java
// add to imports:
// import ch.jp.shooting.dto.CreateUserRequest;
// import ch.jp.shooting.dto.UpdateUserRequest;
// import ch.jp.shooting.dto.UserDTO;
// import ch.jp.shooting.exception.ConflictException;
// import ch.jp.shooting.model.auth.User;
// import static org.mockito.Mockito.lenient;

    @Test
    void createUser_duplicateUsername_throwsConflict() {
        CreateUserRequest req = new CreateUserRequest("a@b.ch", "pw", "Jonas", "Studer");
        req.setUsername("JonasS");

        lenient().when(userRepository.findByEmail("a@b.ch")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameLower("jonass"))
                .thenReturn(Optional.of(new User()));

        assertThrows(ConflictException.class, () -> userService.createUser(req));
    }

    @Test
    void createUser_invalidUsername_throwsIllegalArgument() {
        CreateUserRequest req = new CreateUserRequest("a@b.ch", "pw", "Jonas", "Studer");
        req.setUsername("ab"); // zu kurz

        lenient().when(userRepository.findByEmail("a@b.ch")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.createUser(req));
    }

    @Test
    void updateUser_usernameTakenByAnotherUser_throwsConflict() {
        UUID targetId = UUID.randomUUID();
        User target = new User("a@b.ch", "Jonas", "Studer");
        target.setUsername("JonasS");

        User other = new User("c@d.ch", "Other", "Person");
        other.setUsername("taken");

        UpdateUserRequest req = new UpdateUserRequest();
        req.setUsername("taken");

        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.findByUsernameLower("taken")).thenReturn(Optional.of(other));

        assertThrows(ConflictException.class, () -> userService.updateUser(targetId, req));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd smart-ground-backend && ./mvnw test -Dtest=UserServiceTest`
Expected: FAIL — service does not yet validate/check usernames (no `findByUsernameLower` usage; create does not set username).

- [ ] **Step 3: Implement validation + uniqueness in the service**

In `UserService.java`, add a pattern constant and helper near the top of the class (after the field declarations):

```java
    // 3–30 Zeichen, beginnt alphanumerisch, danach Buchstaben/Ziffern/._-
    private static final java.util.regex.Pattern USERNAME_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{2,29}$");

    private void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username.trim()).matches()) {
            throw new IllegalArgumentException(
                "Invalid username: 3-30 chars, start with a letter or digit, only letters/digits/._-");
        }
    }
```

In `createUser`, after the existing email-conflict check and before building the entity, add:

```java
        validateUsername(request.getUsername());
        if (userRepository.findByUsernameLower(User.toUsernameLower(request.getUsername())).isPresent()) {
            throw new ConflictException("Username already taken");
        }
```

Then, after `User user = new User(...)` and before `userRepository.save(user)`, add:

```java
        user.setUsername(request.getUsername());
```

In `updateUser`, add a block alongside the other `if (request.getXxx() != null)` blocks:

```java
        if (request.getUsername() != null) {
            validateUsername(request.getUsername());
            String lower = User.toUsernameLower(request.getUsername());
            userRepository.findByUsernameLower(lower)
                .filter(other -> !other.getId().equals(userId))
                .ifPresent(o -> { throw new ConflictException("Username already taken"); });
            user.setUsername(request.getUsername());
        }
```

(Imports `ConflictException` and `User` are already present in this file.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd smart-ground-backend && ./mvnw test -Dtest=UserServiceTest`
Expected: PASS (all tests, including the two pre-existing ones).

- [ ] **Step 5: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/service/auth/UserService.java smart-ground-backend/src/test/java/ch/jp/shooting/service/UserServiceTest.java
git commit -m "[backend] validate and enforce unique username on create/update"
```

---

### Task 7: OpenAPI contract — add `username`, regenerate

**Files:**
- Modify: `smart-ground-backend/src/main/resources/static/openapi.yaml`

- [ ] **Step 1: Add `username` to `CreateUserRequest`**

In `openapi.yaml`, `CreateUserRequest` (around line 3630): add `username` to the `required` list and to `properties`:

```yaml
    CreateUserRequest:
      type: object
      required: [email, username, password, vorname, nachname]
      properties:
        email:
          type: string
          format: email
        username:
          type: string
          minLength: 3
          maxLength: 30
          pattern: '^[A-Za-z0-9][A-Za-z0-9._-]{2,29}$'
        password:
          type: string
```

(Keep the remaining existing properties untouched.)

- [ ] **Step 2: Add `username` to `UpdateUserRequest`**

In `UpdateUserRequest` (around line 4038), add to `properties` (do NOT add to required — it's optional):

```yaml
        username:
          type: string
          minLength: 3
          maxLength: 30
          pattern: '^[A-Za-z0-9][A-Za-z0-9._-]{2,29}$'
          nullable: true
```

- [ ] **Step 3: Add `username` to `UserResponse`**

In `UserResponse` (around line 4114), add under `properties` (right after `email`):

```yaml
        username:
          type: string
```

- [ ] **Step 4: Add `username` to `MeResponse`**

In `MeResponse` (around line 3542), add `username` to the `required` list and as a property after `email`:

```yaml
    MeResponse:
      type: object
      required: [id, email, username, vorname, nachname, status, erstelltAm, permissions]
      properties:
        id:
          type: string
          format: uuid
        email:
          type: string
        username:
          type: string
```

- [ ] **Step 5: Regenerate sources**

Run: `cd smart-ground-backend && ./mvnw generate-sources`
Expected: BUILD SUCCESS. Verify the generated models now expose username:
Run: `grep -rl "getUsername" target/generated-sources/openapi/ch/jp/smartground/model/CreateUserRequest.java target/generated-sources/openapi/ch/jp/smartground/model/MeResponse.java`
Expected: both files listed.

- [ ] **Step 6: Commit**

```bash
git add smart-ground-backend/src/main/resources/static/openapi.yaml
git commit -m "[backend] add username to user/auth OpenAPI schemas"
```

---

### Task 8: Wire `username` through `UserController`

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/api/UserController.java`

- [ ] **Step 1: Map username on create**

In `createUser` (around line 67), after `dto.setEmail(request.getEmail());` add:

```java
        dto.setUsername(request.getUsername());
```

- [ ] **Step 2: Map username on update**

In `updateUser` (around line 93), after `dto.setEmail(jsonNullableOrNull(request.getEmail()));` add:

```java
        dto.setUsername(jsonNullableOrNull(request.getUsername()));
```

- [ ] **Step 3: Map username on response**

In `toResponse` (around line 174), after `.email(dto.getEmail())` add:

```java
                .username(dto.getUsername())
```

- [ ] **Step 4: Compile**

Run: `cd smart-ground-backend && ./mvnw -q compile`
Expected: BUILD SUCCESS (generated `UserResponse.username(...)`, `CreateUserRequest.getUsername()`, and `UpdateUserRequest.getUsername()` now exist).

- [ ] **Step 5: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/api/UserController.java
git commit -m "[backend] map username through UserController create/update/response"
```

---

### Task 9: `AuthController` — canonical JWT subject + `username` in `getMe`

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/api/AuthController.java`

- [ ] **Step 1: Use the resolved email as the JWT subject**

In `login` (around line 60), change:

```java
        String token = jwtService.generateToken(request.getUsername(), role);
        return ResponseEntity.ok(new LoginResponse().token(token));
```

to:

```java
        // Subject ist immer die kanonische E-Mail – egal ob per E-Mail oder Benutzername eingeloggt
        String token = jwtService.generateToken(authentication.getName(), role);
        return ResponseEntity.ok(new LoginResponse().token(token));
```

- [ ] **Step 2: Add username to the `MeResponse`**

In `getMe`, in the `MeResponse response = new MeResponse()` chain (around line 87), after `.email(user.getEmail())` add:

```java
                .username(user.getUsername())
```

- [ ] **Step 3: Compile**

Run: `cd smart-ground-backend && ./mvnw -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/api/AuthController.java
git commit -m "[backend] keep email as JWT subject and return username in getMe"
```

---

### Task 10: Update `UserControllerTest` for username, full backend green

**Files:**
- Modify: `smart-ground-backend/src/test/java/ch/jp/shooting/api/UserControllerTest.java`

- [ ] **Step 1: Inspect and fix the existing controller test**

Run: `cd smart-ground-backend && grep -n "CreateUserRequest\|createUser\|username" src/test/java/ch/jp/shooting/api/UserControllerTest.java`
Wherever a `CreateUserRequest` (generated `ch.jp.smartground.model.CreateUserRequest`) is built, set a valid username, e.g. `.username("validhandle")` (or `req.setUsername("validhandle")`), so requests satisfy the new required field. Use distinct handles per user.

- [ ] **Step 2: Run the full backend test suite**

Run: `cd smart-ground-backend && ./mvnw test`
Expected: PASS — all tests green. Fix any remaining persistence/validation failures introduced by the required username.

- [ ] **Step 3: Package check (no warnings per Definition of Done)**

Run: `cd smart-ground-backend && ./mvnw clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add smart-ground-backend/src/test/java/ch/jp/shooting/api/UserControllerTest.java
git commit -m "[backend] update UserControllerTest for required username"
```

---

## Frontend (`smart-ground-ui`)

### Task 11: Remove dead `authApi.createUser` / `authStore.createUser`

**Files:**
- Modify: `smart-ground-ui/src/services/authApi.js`
- Modify: `smart-ground-ui/src/stores/authStore.js`
- Modify: `smart-ground-ui/src/stores/__tests__/authStore.test.js` (only if it references createUser)

- [ ] **Step 1: Confirm no callers**

Run: `cd smart-ground-ui && grep -rn "createUser" src/ | grep -vi "userApi\|userStore\|UserFormModal"`
Expected: only definitions/exports in `authApi.js` and `authStore.js` (and possibly a test mock). If a real view/component caller appears, STOP and instead fix that caller to use `userStore.createUser` — do not delete.

- [ ] **Step 2: Remove the dead exports**

In `authApi.js`, delete the `createUser` function (lines ~10-15).
In `authStore.js`, remove `createUser` from the import (line 3 → `import { login as loginApi, getMe } from ...`), delete the `createUser` action (lines ~67-78), and remove `createUser` from the returned object (line ~101).
If `authStore.test.js` mocks/asserts `createUser`, remove those references.

- [ ] **Step 3: Run tests**

Run: `cd smart-ground-ui && npm run test -- src/stores/__tests__/authStore.test.js`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add smart-ground-ui/src/services/authApi.js smart-ground-ui/src/stores/authStore.js smart-ground-ui/src/stores/__tests__/authStore.test.js
git commit -m "[ui] remove dead authApi/authStore createUser"
```

---

### Task 12: Relabel the login field to "E-Mail oder Benutzername"

**Files:**
- Modify: `smart-ground-ui/src/views/LoginView.vue`

- [ ] **Step 1: Update label and placeholder**

In `LoginView.vue`, change the label (line ~38) from `Username` to `E-Mail oder Benutzername`, and the input placeholder (line ~43) from `Enter your username` to `E-Mail oder Benutzername eingeben`. Leave the `username` ref and `authStore.login(username, password)` call unchanged — the value is still posted as `{ username }`.

- [ ] **Step 2: Verify the app builds**

Run: `cd smart-ground-ui && npm run lint:check && npm run build`
Expected: no lint errors; build succeeds.

- [ ] **Step 3: Commit**

```bash
git add smart-ground-ui/src/views/LoginView.vue
git commit -m "[ui] relabel login field to email-or-username"
```

---

### Task 13: Admin create/edit modal — username field

**Files:**
- Modify: `smart-ground-ui/src/components/UserFormModal.vue`
- Test: `smart-ground-ui/src/components/__tests__/UserFormModal.test.js`

- [ ] **Step 1: Write the failing test**

In `UserFormModal.test.js`, add a test (mirror the existing create-submit test's setup):

```javascript
  it('requires username on create and includes it in the payload', async () => {
    const wrapper = mount(UserFormModal, { props: { mode: 'create' } })

    await wrapper.find('[data-testid="input-vorname"]').setValue('Hans')
    await wrapper.find('[data-testid="input-nachname"]').setValue('Müller')
    await wrapper.find('[data-testid="input-email"]').setValue('h@t.ch')
    await wrapper.find('[data-testid="input-password"]').setValue('secret')
    // username missing → submit should not call createUser
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    expect(mockCreate).not.toHaveBeenCalled()

    await wrapper.find('[data-testid="input-username"]').setValue('hansm')
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')

    expect(mockCreate).toHaveBeenCalled()
    const payload = mockCreate.mock.calls.at(-1)[0]
    expect(payload.username).toBe('hansm')
  })
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-ground-ui && npm run test -- src/components/__tests__/UserFormModal.test.js`
Expected: FAIL — no `[data-testid="input-username"]` element.

- [ ] **Step 3: Add the field, state, validation, and edit prefill**

In `UserFormModal.vue` template, add a username group on the Basisdaten tab (after the email group, around line 66):

```vue
        <div class="form-group">
          <label for="input-username">Benutzername *</label>
          <input
            id="input-username"
            v-model="form.username"
            data-testid="input-username"
            type="text"
            :class="{ 'input-error': errors.username }"
            :disabled="userStore.isLoading"
          />
          <span v-if="errors.username" data-testid="error-username" class="error-msg">{{ errors.username }}</span>
        </div>
```

In `<script setup>`: add `username: ''` to the `form` reactive (after `email: '',`). Add a username pattern + validation. Near the top of `<script setup>` add:

```javascript
const USERNAME_RE = /^[A-Za-z0-9][A-Za-z0-9._-]{2,29}$/
```

In `validate()`, after the email check, add:

```javascript
  if (!form.username.trim()) errors.username = 'Pflichtfeld'
  else if (!USERNAME_RE.test(form.username.trim())) errors.username = '3–30 Zeichen, Buchstaben/Ziffern/._-'
```

In the edit `watch` (the `Object.assign(form, {...})` block), add:

```javascript
        username: user.username ?? '',
```

(`username` already flows into the payload via `cleanPayload(raw)` for both create and edit — no submit change needed.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-ground-ui && npm run test -- src/components/__tests__/UserFormModal.test.js`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add smart-ground-ui/src/components/UserFormModal.vue smart-ground-ui/src/components/__tests__/UserFormModal.test.js
git commit -m "[ui] add username field to user create/edit modal"
```

---

### Task 14: Show username in the admin user list

**Files:**
- Modify: `smart-ground-ui/src/views/admin/UsersView.vue`
- Test: `smart-ground-ui/src/views/__tests__/UsersView.test.js`

- [ ] **Step 1: Inspect the current table and test**

Run: `cd smart-ground-ui && grep -n "email\|<th\|<td\|vorname" src/views/admin/UsersView.vue`
Identify the table header/row structure (column order) so the username column slots in cleanly (place it right after the name/email column).

- [ ] **Step 2: Write the failing test**

In `UsersView.test.js`, ensure the mocked users include `username` and add an assertion that it renders. Mirror the existing render test; with a mocked user `{ ..., username: 'jonass' }`:

```javascript
  it('renders the username for each user', async () => {
    // (reuse this file's existing store/mock setup that loads users)
    expect(wrapper.text()).toContain('jonass')
  })
```

(Adjust to the file's existing mounting/setup helper.)

- [ ] **Step 3: Run test to verify it fails**

Run: `cd smart-ground-ui && npm run test -- src/views/__tests__/UsersView.test.js`
Expected: FAIL — username not rendered.

- [ ] **Step 4: Add the column**

In `UsersView.vue`, add a `Benutzername` header `<th>` and a `<td>{{ user.username }}</td>` cell in the matching position. Keep styling consistent with adjacent columns.

- [ ] **Step 5: Run test to verify it passes**

Run: `cd smart-ground-ui && npm run test -- src/views/__tests__/UsersView.test.js`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add smart-ground-ui/src/views/admin/UsersView.vue smart-ground-ui/src/views/__tests__/UsersView.test.js
git commit -m "[ui] show username column in admin user list"
```

---

### Task 15: Shooter self-service username edit

**Files:**
- Modify: `smart-ground-ui/src/stores/authStore.js`
- Create: `smart-ground-ui/src/components/UsernameEditModal.vue`
- Modify: `smart-ground-ui/src/views/shooter/ShooterHomeView.vue`
- Test: `smart-ground-ui/src/stores/__tests__/authStore.test.js`
- Test: `smart-ground-ui/src/components/__tests__/UsernameEditModal.test.js` (create)

- [ ] **Step 1: Write the failing store test**

In `authStore.test.js`, add (the file already mocks `../../services/authApi`; also mock `userApi`):

```javascript
// at top with other mocks:
// import * as userApi from '../../services/userApi'
// vi.mock('../../services/userApi')

  it('updateOwnUsername updates current user and reloads profile', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ token: 'test.jwt.token' })
    vi.mocked(authApi.getMe)
      .mockResolvedValueOnce({ id: 'u1', vorname: 'A', nachname: 'B', username: 'old', permissions: [] })
      .mockResolvedValueOnce({ id: 'u1', vorname: 'A', nachname: 'B', username: 'new', permissions: [] })
    vi.mocked(userApi.updateUser).mockResolvedValue({})

    const store = useAuthStore()
    await store.login('a@b.ch', 'pw')
    await store.updateOwnUsername('new')

    expect(userApi.updateUser).toHaveBeenCalledWith('u1', { username: 'new' })
    expect(store.profile.username).toBe('new')
  })
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-ground-ui && npm run test -- src/stores/__tests__/authStore.test.js`
Expected: FAIL — `updateOwnUsername` is not defined.

- [ ] **Step 3: Add the `updateOwnUsername` action**

In `authStore.js`, import the user API at the top:

```javascript
import { updateUser as updateUserApi } from '../services/userApi.js';
```

Add the action (after `login`):

```javascript
  const updateOwnUsername = async (username) => {
    isLoading.value = true;
    error.value = null;
    try {
      await updateUserApi(profile.value.id, { username });
      await _loadProfile();
    } catch (err) {
      error.value = err.message;
      throw err;
    } finally {
      isLoading.value = false;
    }
  };
```

Add `updateOwnUsername` to the returned object.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-ground-ui && npm run test -- src/stores/__tests__/authStore.test.js`
Expected: PASS.

- [ ] **Step 5: Create the modal component**

Create `smart-ground-ui/src/components/UsernameEditModal.vue`:

```vue
<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal-box">
      <h2>Benutzername ändern</h2>
      <div class="form-group">
        <label for="edit-username">Benutzername</label>
        <input
          id="edit-username"
          v-model="username"
          data-testid="edit-username"
          type="text"
          :disabled="authStore.isLoading"
          @keydown.enter="save"
        />
        <span v-if="localError" data-testid="username-error" class="error">{{ localError }}</span>
        <span v-else-if="authStore.error" data-testid="username-error" class="error">{{ authStore.error }}</span>
      </div>
      <div class="actions">
        <button data-testid="cancel" :disabled="authStore.isLoading" @click="$emit('close')">Abbrechen</button>
        <button data-testid="save" :disabled="authStore.isLoading" @click="save">Speichern</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'

const emit = defineEmits(['close', 'saved'])
const authStore = useAuthStore()
const USERNAME_RE = /^[A-Za-z0-9][A-Za-z0-9._-]{2,29}$/

const username = ref(authStore.profile?.username ?? '')
const localError = ref('')

async function save() {
  localError.value = ''
  if (!USERNAME_RE.test(username.value.trim())) {
    localError.value = '3–30 Zeichen, Buchstaben/Ziffern/._-'
    return
  }
  try {
    await authStore.updateOwnUsername(username.value.trim())
    emit('saved')
    emit('close')
  } catch {
    // Fehler wird über authStore.error angezeigt
  }
}
</script>

<style scoped>
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.45); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-box { background: var(--sg-bg-card, #fff); border-radius: 8px; padding: 24px; width: 360px; max-width: 92vw; }
.form-group { display: flex; flex-direction: column; gap: 6px; margin: 12px 0; }
.error { color: var(--sg-color-danger, #ef4444); font-size: .8rem; }
.actions { display: flex; justify-content: flex-end; gap: 8px; }
</style>
```

- [ ] **Step 6: Write the failing component test**

Create `smart-ground-ui/src/components/__tests__/UsernameEditModal.test.js`:

```javascript
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import UsernameEditModal from '../UsernameEditModal.vue'
import { useAuthStore } from '@/stores/authStore'

describe('UsernameEditModal', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('blocks an invalid handle client-side', async () => {
    const store = useAuthStore()
    store.profile = { id: 'u1', username: 'old' }
    store.updateOwnUsername = vi.fn()

    const wrapper = mount(UsernameEditModal)
    await wrapper.find('[data-testid="edit-username"]').setValue('ab')
    await wrapper.find('[data-testid="save"]').trigger('click')

    expect(store.updateOwnUsername).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="username-error"]').exists()).toBe(true)
  })

  it('saves a valid handle and emits saved', async () => {
    const store = useAuthStore()
    store.profile = { id: 'u1', username: 'old' }
    store.updateOwnUsername = vi.fn().mockResolvedValue()

    const wrapper = mount(UsernameEditModal)
    await wrapper.find('[data-testid="edit-username"]').setValue('newhandle')
    await wrapper.find('[data-testid="save"]').trigger('click')

    expect(store.updateOwnUsername).toHaveBeenCalledWith('newhandle')
    expect(wrapper.emitted('saved')).toBeTruthy()
  })
})
```

- [ ] **Step 7: Run the component test**

Run: `cd smart-ground-ui && npm run test -- src/components/__tests__/UsernameEditModal.test.js`
Expected: PASS.

- [ ] **Step 8: Wire the trigger into `ShooterHomeView`**

In `ShooterHomeView.vue`, import and render the modal, toggled by a small button near the displayed name. In `<script setup>` add:

```javascript
import { ref } from 'vue'
import UsernameEditModal from '@/components/UsernameEditModal.vue'
const showAccount = ref(false)
```

Next to the `<span class="user-name">{{ displayName }}</span>` (line ~7) add a button:

```vue
        <button class="account-edit-btn" data-testid="open-account" @click="showAccount = true">Konto</button>
```

At the end of the template add:

```vue
    <UsernameEditModal v-if="showAccount" @close="showAccount = false" />
```

(`ref` may already be imported — merge, don't duplicate the import.)

- [ ] **Step 9: Lint, build, and run the full UI suite**

Run: `cd smart-ground-ui && npm run lint:check && npm run test && npm run build`
Expected: no lint errors; all tests pass; build succeeds.

- [ ] **Step 10: Commit**

```bash
git add smart-ground-ui/src/stores/authStore.js smart-ground-ui/src/stores/__tests__/authStore.test.js smart-ground-ui/src/components/UsernameEditModal.vue smart-ground-ui/src/components/__tests__/UsernameEditModal.test.js smart-ground-ui/src/views/shooter/ShooterHomeView.vue
git commit -m "[ui] let shooters change their own username"
```

---

### Task 16: Update CLAUDE.md docs with the decisions

**Files:**
- Modify: `smart-ground-backend/CLAUDE.md`

- [ ] **Step 1: Record the decisions**

Update the backend `CLAUDE.md`:
- In the **User model** section, add `username` (unique, required, case-insensitive) and `username_lower` to the field list.
- In the **Auth / User tables** schema block, add `username, username_lower` to the `users` table line.
- Add a short note under **Authentication → JWT Flow**: login accepts email *or* username; the JWT subject is always the canonical email; email/username never collide because the username pattern forbids `@`.

- [ ] **Step 2: Commit**

```bash
git add smart-ground-backend/CLAUDE.md
git commit -m "[backend] document username + email-or-username login"
```

---

## Self-Review notes (coverage check)

- Entity + shadow column + normalization → Task 1.
- Required-column migration impact (seed/tests) → Task 2 + Task 10.
- Repository email-or-username + username_lower queries → Task 3.
- Login resolution, canonical JWT subject → Task 4 + Task 9.
- Internal DTOs + mapper → Task 5; OpenAPI schemas → Task 7; controller wiring → Task 8.
- Service validation + case-insensitive uniqueness (create + update, self-exclusion) → Task 6.
- Login relabel → Task 12; admin modal → Task 13; user list → Task 14.
- Shooter self-service edit (backend already authorizes self-edit) → Task 15.
- Dead-code cleanup → Task 11; docs → Task 16.

**Method/name consistency:** `User.toUsernameLower` (entity helper), `findByEmailOrUsernameWithRoles` / `findByUsernameLower` (repo), `validateUsername` / `USERNAME_PATTERN` (service), `updateOwnUsername` (authStore), `USERNAME_RE` (UI regex), `data-testid="input-username"` / `"edit-username"` — used consistently across tasks.
```
