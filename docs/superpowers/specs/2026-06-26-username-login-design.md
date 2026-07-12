# Username + Email-or-Username Login — Design Spec

**Date:** 2026-06-26
**Scope:** `smart-ground-backend` + `smart-ground-ui`
**Status:** Approved design, pending implementation plan

## Goal

Add a `username` to user management and allow users to authenticate with **either**
their email address **or** their username (one login field, one password).

## Decisions (locked)

| Decision | Choice |
|---|---|
| Username presence | **Required & unique** for every user |
| Uniqueness / login matching | **Case-insensitive** (stored as entered, matched lowercased) |
| Mutability | **Editable** later via the update-user endpoint; **shooters can change their own** |
| Format | **Standard handle**: `^[A-Za-z0-9][A-Za-z0-9._-]{2,29}$` (3–30 chars, starts alphanumeric; no `@`, no whitespace) |
| Frontend | In scope (login label, admin create/edit modal, user list, shooter self-service edit) |

### Authorization finding (no change needed)

`UserController.updateUser` already permits self-edit: the guard is
`if (!id.equals(currentUser.getId()) && !hasRole(ADMIN)) throw ForbiddenException`. A shooter
can therefore PATCH **their own** user record (including `username`) with no `MANAGE_USERS`
permission and **no new endpoint**. Enabling shooter username changes is purely a frontend
addition plus wiring `username` into the controller's field mapping.

Rationale: there is **no production release** (per both `CLAUDE.md` files), so the schema
can be changed freely and existing dev data may be reset. Email and username never collide
because emails contain `@` and the username pattern forbids `@`, so a single login lookup can
match either without a mode flag.

---

## Backend

### Data model (`model/auth/User.java`)

Add two columns to the `users` table:

- `username` — `VARCHAR`, `NOT NULL`. Stores the handle **as entered** (display casing).
- `username_lower` — `VARCHAR`, `NOT NULL`, **unique**. Lowercased shadow of `username`,
  used for both uniqueness enforcement and login matching.

A `LOWER()` functional unique index is **not** used: it is not portable across H2
(`ddl-auto=create-drop`) and PostgreSQL (`ddl-auto=update`) under Hibernate. The shadow
column gives DB-level case-insensitive uniqueness on both.

Implementation detail: `setUsername(String)` normalizes and recomputes `username_lower`
(`username.trim()`, `username_lower = username.toLowerCase(Locale.ROOT)`) so the two columns
can never drift. `username_lower` has no public setter.

### Repository (`repository/auth/UserRepository.java`)

Add:

```java
@Query("SELECT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role r "
     + "LEFT JOIN FETCH r.permissions "
     + "WHERE LOWER(u.email) = LOWER(:login) OR u.usernameLower = LOWER(:login)")
Optional<User> findByEmailOrUsernameWithRoles(@Param("login") String login);

Optional<User> findByUsernameLower(String usernameLower); // uniqueness checks
```

### Login resolution

- `CustomUserDetailsService.loadUserByUsername(login)` switches from `findByEmailWithRoles`
  to `findByEmailOrUsernameWithRoles`. The returned Spring `UserDetails` principal **stays
  `user.getEmail()`** (unchanged), so the JWT subject remains the canonical email regardless
  of which identifier was typed.
- `AuthController.login` changes its token subject from `request.getUsername()` (the raw typed
  value) to **`authentication.getName()`** (the resolved canonical email). This keeps
  `getMe()` working — it looks the user up by the JWT subject (email) — and fixes the current
  behavior where the subject was whatever the user typed.
- `LoginRequest.username` keeps its name; it semantically means "login identifier". No API
  field rename.

### Service (`service/auth/UserService.java`)

- **Create** (`createUser`): require + validate `username` (format regex + non-blank);
  reject duplicates via `findByUsernameLower` → throw `ConflictException("Username already taken")`.
- **Update** (`updateUser`): if `username` provided, validate format and re-check uniqueness
  **excluding the current user** (a user keeping their own handle must not conflict with
  themselves) → `ConflictException` on collision.
- Validation lives in a small private helper (`validateUsername(String)`) reused by create and
  update; throws `IllegalArgumentException` on a malformed handle (maps to 400 via existing
  handler).

### API contract (`openapi.yaml` — source of truth, regenerate with `./mvnw generate-sources`)

- `CreateUserRequest`: add **required** `username` with `pattern` + `minLength: 3`,
  `maxLength: 30`.
- `UpdateUserRequest`: add **optional** `username` (same pattern/length).
- `UserResponse`: add `username` (required in response) — this is the schema the user
  endpoints return.
- `MeResponse`: add `username` (required in response) so `authStore.profile.username` is
  available app-wide.
- Duplicate username reuses the existing 409 `ConflictException` mapping; malformed handle is
  400. No new exception type needed.

### Controller wiring (`api/UserController.java`)

`UserController` maps fields **explicitly** between generated requests and the internal
`ch.jp.shooting.dto.*` DTOs (not via a generic copy), so each path needs an added line:

- `createUser`: `dto.setUsername(request.getUsername())`.
- `updateUser`: `dto.setUsername(jsonNullableOrNull(request.getUsername()))` (it is a
  `JsonNullable<String>` since `UpdateUserRequest.username` is optional).
- `toResponse`: `.username(dto.getUsername())`.

### Internal DTO / Mapper (`dto/UserDTO.java`, `dto/CreateUserRequest.java`,
`dto/UpdateUserRequest.java`, `mapper/UserMapper.java`)

- Add `username` field + getter/setter to the internal `UserDTO`, `CreateUserRequest`, and
  `UpdateUserRequest` Java classes (these are handwritten DTOs in `ch.jp.shooting.dto`,
  distinct from the generated `ch.jp.smartground.model.*`).
- `UserMapper.toDto` populates `username` from the entity.
- `AuthController.getMe` sets `.username(user.getUsername())` on `MeResponse`.

### Seed data (`config/DataInitializer.java`)

- Admin seed user gets username `admin`; shooter seed user gets username `user`.

### Dev DB note

Adding a `NOT NULL UNIQUE` column to a populated table will not apply cleanly via
PostgreSQL `ddl-auto=update`. Per the no-prod-release policy, the dev PostgreSQL `users`
data is reset (drop rows / let the seed re-create). H2 test DB rebuilds automatically.
This is documented, not automated (no Liquibase pre-v1.0).

### Backend tests

- Login by **username** succeeds; login by **email** still succeeds.
- Login by username with **different casing** succeeds (case-insensitive).
- Create with duplicate username (incl. case variant) → 409.
- Update username with re-check (collision → 409; keeping own handle → OK).
- Malformed username (too short, leading symbol, contains `@`/space) → 400.
- JWT subject is the email even when logging in by username (so `getMe` resolves).

---

## Frontend (`smart-ground-ui`)

### `views/LoginView.vue`

- Relabel the field `Username` → **"E-Mail oder Benutzername"**; update placeholder
  (e.g. "E-Mail oder Benutzername eingeben"). The `username` ref and
  `authStore.login(username, password)` chain already post the value as `{ username, ... }` —
  no logic change.

### `components/UserFormModal.vue`

- Add **Benutzername** input on the *Basisdaten* tab.
  - Create mode: required (marked `*`).
  - Edit mode: editable (unlike email, which is disabled on edit).
- Wire into `form` reactive state, the edit `watch` (prefill `user.username`), client-side
  `validate()` (required + standard-handle pattern mirror), and the cleaned payload (already
  flows through `cleanPayload`).

### `views/admin/UsersView.vue`

- Surface `username` in the user list/table.

### Shooter self-service edit (`views/shooter/ShooterHomeView.vue` + a small modal)

There is **no profile view in the current UI** (the `CLAUDE.md` `/profile` reference is
stale). To let shooters change their own username without building a full profile page:

- Add a small **"Konto"** affordance in the `ShooterHomeView` header (next to the displayed
  name) that opens a lightweight modal showing the current username with an edit field +
  save.
- Save calls `userApi.updateUser(authStore.profile.id, { username })` — the backend already
  authorizes self-edit. `authStore.profile.id` comes from `MeResponse.id`.
- On success, refresh `authStore.profile` (re-run the existing `getMe` load path) so the
  header reflects the new handle, and surface the 409 (duplicate) / 400 (format) errors
  inline.
- Client-side validation mirrors the standard-handle pattern before submit.

A full multi-field profile page is **out of scope** — this is the minimal surface that
satisfies "shooters can change their username".

### Cleanup

- `services/authApi.js` `createUser(username, password, role)` and `authStore.createUser`
  appear **unused** — the real create flow goes `UserFormModal → userStore.createUser →
  userApi.createUser`, and `authApi.createUser`'s `{username,password,role}` body doesn't even
  match `CreateUserRequest`. Verify no callers and **remove** both (and any stale test mock),
  per the "remove unused code eagerly" convention. If a caller is found, fix it to send a
  proper payload instead.

### Frontend tests

- `UserFormModal.test.js`: username present + required on create; prefilled + editable on
  edit; included in payload; empty-handling unchanged.
- `UsersView.test.js`: username rendered in the list.
- `authStore.test.js`: `profile.username` exposed from `getMe`; remove createUser-related
  mocks if the method is deleted.
- Shooter self-service edit: test the new modal/component — valid save calls
  `userApi.updateUser(profile.id, { username })` and refreshes the profile; invalid handle is
  blocked client-side; a 409 surfaces inline.

---

## Out of scope

- Username history / audit of changes.
- Reserved-word blocklist for usernames.
- Wiring the username into competition/session display names (separate concern).
