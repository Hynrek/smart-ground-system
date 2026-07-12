# Design: `/auth/me` Endpoint & Permission-Based UI

**Date:** 2026-05-29  
**Status:** Approved

---

## Problem

The JWT token currently carries only `sub` (email) and a single `role` string. The frontend cannot:

- Display full user profile info without a separate API call
- Make fine-grained access decisions based on permissions
- Know which specific actions the logged-in user is allowed to perform

The `User` model has a rich RBAC structure (`User → UserRoleEntity → Role → PermissionEntity`) that is invisible to the UI.

---

## Decision

**JWT stays lean** — token keeps only `sub` (email) and `role`. No changes to `JwtService` or `AuthController`.

A new `GET /api/auth/me` endpoint returns the full user profile plus a flat list of permissions derived from all the user's roles.

The frontend replaces all role-based checks (`isShooter()`, `isAdminOrOwner()`) with permission-based checks (`hasPermission('VIEW_REMOTE')`). Roles are an implementation detail — only permissions matter to the UI.

---

## Backend

### New endpoint

```
GET /api/auth/me
Authorization: Bearer <token>
→ 200 MeResponse
→ 401 if token missing or invalid
```

- Defined in `openapi.yaml` under the `Auth` tag, operationId `getMe`
- Implemented in `AuthController` (implements generated `AuthApi`)
- Resolves current user via `SecurityContextHolder` principal (email → `UserRepository`)
- Loads `User` with roles and their permissions (roles are `EAGER` on `Role.permissions`)
- Returns flat union of all permissions across all roles

### `MeResponse` DTO fields

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `email` | String | |
| `vorname` | String | |
| `nachname` | String | |
| `geburtsdatum` | LocalDate (nullable) | |
| `geschlecht` | String (nullable) | MAENNLICH / WEIBLICH / DIVERS / UNBEKANNT |
| `telefonnummer` | String (nullable) | |
| `telefonBestaetigt` | Boolean (nullable) | |
| `strasse` | String (nullable) | |
| `hausnummer` | String (nullable) | |
| `plz` | String (nullable) | |
| `stadt` | String (nullable) | |
| `land` | String (nullable) | |
| `profilbildUrl` | String (nullable) | |
| `biographie` | String (nullable) | |
| `sprache` | String (nullable) | DE / EN / FR / IT |
| `mitgliedsnummer` | String (nullable) | |
| `schiessLizenz` | String (nullable) | |
| `schiessLizenzVerfallsdatum` | LocalDate (nullable) | |
| `schiessLizenzGueltig` | Boolean | computed from expiry date |
| `status` | String | ACTIVE / INACTIVE / SUSPENDED / PENDING_APPROVAL |
| `emailBestaetigt` | Boolean (nullable) | |
| `letzterLogin` | Instant (nullable) | |
| `erstelltAm` | Instant | |
| `permissions` | List\<String\> | flat union of all role permissions, e.g. `["MANAGE_USERS", "VIEW_REMOTE"]` |

### Security config

`/api/auth/me` requires a valid JWT (same as all other protected endpoints). `/api/auth/login` stays public. No other security changes.

---

## Frontend

### `authStore` changes

**New state:**
```js
const token = ref(localStorage.getItem('sg_token') || null)
const profile = ref(null)        // MeResponse object
const permissions = ref([])      // flat string array
```

**Login flow:**
1. `POST /auth/login` → store JWT in localStorage (unchanged)
2. Immediately call `GET /auth/me` → store profile + permissions

**On app init (page refresh):**  
If token exists in localStorage, call `/auth/me` to restore profile + permissions before rendering protected routes.

**New API (replaces role checks):**
```js
const hasPermission = (permission) => permissions.value.includes(permission)
const currentUser = computed(() => profile.value)
const isAuthenticated = () => !!token.value
```

**Removed:** `isShooter()`, `isAdminOrOwner()`, `role` computed, `jwtPayload` computed

**`logout()`** clears token, profile, and permissions; removes `sg_token` from localStorage.

### Router guard update

Replace layout/role-based guards with permission checks:

```js
router.beforeEach(async (to, from, next) => {
  const auth = useAuthStore()

  if (!auth.isAuthenticated()) {
    return next('/login')
  }

  if (to.path === '/login') {
    return next('/') // redirect authenticated users away from login
  }

  if (to.meta.permission && !auth.hasPermission(to.meta.permission)) {
    return next('/unauthorized') // or redirect to role-appropriate home
  }

  next()
})
```

Routes declare required permission in `meta.permission`. Example:
```js
{ path: '/users', meta: { permission: 'MANAGE_USERS' } }
{ path: '/remote', meta: { permission: 'VIEW_REMOTE' } }
```

### Component changes

Replace all `isShooter()` / `isAdminOrOwner()` calls in components and views with `hasPermission(...)`.

### New `authApi.js` function

```js
export const getMe = () => apiClient.get('/auth/me')
```

---

## Permission reference

| Permission | Meaning |
|---|---|
| `MANAGE_USERS` | Create, edit, delete user accounts |
| `MANAGE_RANGES` | Create, edit, delete shooting ranges |
| `MANAGE_SERIES_TEMPLATES` | Manage series/program templates |
| `MANAGE_PASSE_TEMPLATES` | Manage Passe templates |
| `MANAGE_COMPETITIONS` | Create and manage competitions |
| `OPERATE_RANGE` | Operate range devices (SmartBox commands) |
| `START_TRAINING` | Start a training session |
| `START_COMPETITION` | Start a competition session |
| `MANAGE_SERIES` | Manage active series |
| `RESERVE_REMOTE` | Reserve a remote shooting position |
| `VIEW_REMOTE` | Access the shooter remote interface |
| `PLAY_SERIES` | Participate in a series |
| `PLAY_COMPETITION` | Participate in a competition |

---

## Files affected

### Backend
- `src/main/resources/static/openapi.yaml` — add `GET /auth/me` + `MeResponse` schema
- `src/main/java/ch/jp/shooting/api/AuthController.java` — implement `getMe()`
- `src/main/java/ch/jp/shooting/dto/MeResponse.java` — new DTO record

### Frontend
- `src/stores/authStore.js` — add profile/permissions state, `hasPermission()`, fetch on login + init
- `src/services/authApi.js` — add `getMe()` function
- `src/router/index.js` — replace role guards with permission guards, add `meta.permission` to routes
- All components/views using `isShooter()` / `isAdminOrOwner()` — replace with `hasPermission()`

---

## Out of scope

- JWT payload changes (no permissions in token)
- Role management UI
- Permission editing UI
- Refresh token / token expiry handling
