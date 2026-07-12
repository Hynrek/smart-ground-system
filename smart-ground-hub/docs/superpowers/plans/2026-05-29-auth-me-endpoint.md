# Auth `/me` Endpoint & Permission-Based UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `GET /api/auth/me` returning full user profile + flat permissions list, then replace all role-based UI checks with `hasPermission()`.

**Architecture:** Backend exposes a new protected endpoint that resolves the authenticated user via `SecurityContextHolder`, loads their roles+permissions, and returns a flat `MeResponse` DTO. The frontend calls this endpoint immediately after login (and on app init after a page refresh) to populate `authStore` with `profile` and `permissions`. All role checks in the router, components, and sidebar are replaced with `hasPermission()`.

**Tech Stack:** Spring Boot 4 / Java 25 / jOOQ OpenAPI Generator (backend); Vue 3 Composition API / Pinia (frontend)

---

## File Map

### Backend — create or modify

| File | Action |
|---|---|
| `src/main/resources/static/openapi.yaml` | Add `GET /api/auth/me` path + `MeResponse` schema |
| `src/main/java/ch/jp/shooting/config/SecurityConfig.java` | Narrow `permitAll` from `/api/auth/**` to `/api/auth/login` |
| `src/main/java/ch/jp/shooting/api/AuthController.java` | Implement `getMe()` |
| *(generated)* `target/generated-sources/openapi/…/AuthApi.java` | Regenerated automatically by `./mvnw generate-sources` |

### Frontend — create or modify

| File | Action |
|---|---|
| `src/services/authApi.js` | Add `getMe()` function |
| `src/stores/authStore.js` | Add `profile`, `permissions`, `hasPermission()`, init logic; remove role helpers |
| `src/main.js` | Call `authStore.init()` before app mount |
| `src/router/index.js` | Replace role guards with permission guards; add `meta.permission` to routes |
| `src/components/Sidebar.vue` | Replace `isAdminOrOwner()` with `hasPermission('MANAGE_USERS')` |
| `src/components/PositionCard.vue` | Replace `isAdminOrOwner()` with `hasPermission('MANAGE_RANGES')` |
| `src/components/shooter-remote/ShooterFlyoutPanel.vue` | Replace `isAdminOrOwner()` with `hasPermission('OPERATE_RANGE')` |
| `src/views/shooter/PasseManagementView.vue` | Replace `isAdminOrOwner()` with `hasPermission('MANAGE_RANGES')` |

---

## Task 1: Add `MeResponse` schema + `GET /api/auth/me` to OpenAPI contract

**Files:**
- Modify: `smart-ground-backend/src/main/resources/static/openapi.yaml`

- [ ] **Step 1: Add the `/api/auth/me` path entry after the `/api/auth/login` block**

Find this block in `openapi.yaml` (around line 60):
```yaml
  /api/users:
```

Insert before it:
```yaml
  /api/auth/me:
    get:
      summary: Get current user profile and permissions
      operationId: getMe
      tags: [Auth]
      responses:
        '200':
          description: Current user profile with flat permission list
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/MeResponse'
        '401':
          description: Not authenticated

```

- [ ] **Step 2: Add `MeResponse` schema to the `components.schemas` section**

Find the `LoginResponse` schema (around line 2941) and add `MeResponse` after `CreateUserRequest`:

```yaml
    MeResponse:
      type: object
      required: [id, email, vorname, nachname, status, erstelltAm, permissions]
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
        schiessLizenzGueltig:
          type: boolean
        status:
          type: string
          enum: [ACTIVE, INACTIVE, SUSPENDED, PENDING_APPROVAL]
        emailBestaetigt:
          type: boolean
          nullable: true
        letzterLogin:
          type: string
          format: date-time
          nullable: true
        erstelltAm:
          type: string
          format: date-time
        permissions:
          type: array
          items:
            type: string
          description: Flat union of all permissions from all assigned roles
```

- [ ] **Step 3: Regenerate the OpenAPI sources**

```bash
cd smart-ground-backend
./mvnw generate-sources -q
```

Expected: Build succeeds, `target/generated-sources/openapi/ch/jp/smartground/api/AuthApi.java` now contains a `getMe()` method, and `target/generated-sources/openapi/ch/jp/smartground/model/MeResponse.java` exists.

---

## Task 2: Narrow `SecurityConfig` public routes

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/config/SecurityConfig.java`

The current config permits all of `/api/auth/**`. Since `/api/auth/me` must be protected (it returns private data), we narrow this to only the login path.

- [ ] **Step 1: Update the `authorizeHttpRequests` block**

Change line 43 from:
```java
.requestMatchers("/api/auth/**").permitAll()
```
to:
```java
.requestMatchers("/api/auth/login").permitAll()
```

- [ ] **Step 2: Verify the app still starts**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2 &
sleep 10
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/auth/me
# Expected: 401 (not 403 or 500)
kill %1
```

---

## Task 3: Implement `getMe()` in `AuthController`

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/api/AuthController.java`

The controller resolves the authenticated email from `SecurityContextHolder`, loads the user via `UserRepository`, then builds a flat permission list from all roles.

- [ ] **Step 1: Add the `getMe()` implementation**

Replace the entire `AuthController.java` with:

```java
package ch.jp.shooting.api;

import ch.jp.shooting.model.auth.Permission;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.shooting.service.JwtService;
import ch.jp.smartground.api.AuthApi;
import ch.jp.smartground.model.LoginRequest;
import ch.jp.smartground.model.LoginResponse;
import ch.jp.smartground.model.MeResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@NullMarked
public class AuthController implements AuthApi {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<LoginResponse> login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        // Rolle aus Authentifizierung extrahieren
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.substring(5))
                .findFirst()
                .orElse(null);

        String token = jwtService.generateToken(request.getUsername(), role);
        return ResponseEntity.ok(new LoginResponse().token(token));
    }

    @Override
    public ResponseEntity<MeResponse> getMe() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        // Alle Berechtigungen aus allen Rollen flach zusammenführen
        List<String> permissions = user.getUserRoles().stream()
                .flatMap(ur -> ur.getRole().getPermissions().stream())
                .map(p -> p.getAction().toUpperCase())
                .distinct()
                .sorted()
                .toList();

        MeResponse response = new MeResponse()
                .id(user.getId())
                .email(user.getEmail())
                .vorname(user.getVorname())
                .nachname(user.getNachname())
                .geburtsdatum(user.getGeburtsdatum())
                .geschlecht(user.getGeschlecht())
                .telefonnummer(user.getTelefonnummer())
                .telefonBestaetigt(user.getTelefonBestaetigt())
                .strasse(user.getStrasse())
                .hausnummer(user.getHausnummer())
                .plz(user.getPlz())
                .stadt(user.getStadt())
                .land(user.getLand())
                .profilbildUrl(user.getProfilbildUrl())
                .biographie(user.getBiographie())
                .sprache(user.getSprache())
                .mitgliedsnummer(user.getMitgliedsnummer())
                .schiessLizenz(user.getSchiessLizenz())
                .schiessLizenzVerfallsdatum(user.getSchiessLizenzVerfallsdatum())
                .schiessLizenzGueltig(user.isSchiessLizenzGueltig())
                .status(MeResponse.StatusEnum.valueOf(user.getStatus().name()))
                .emailBestaetigt(user.getEmailBestaetigt())
                .letzterLogin(user.getLetzterLogin())
                .erstelltAm(user.getErstelltAm())
                .permissions(permissions);

        return ResponseEntity.ok(response);
    }
}
```

- [ ] **Step 2: Build to check for compile errors**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS` with no errors.

- [ ] **Step 3: Run tests**

```bash
./mvnw test -q
```

Expected: `BUILD SUCCESS`. All existing tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/openapi.yaml \
        src/main/java/ch/jp/shooting/config/SecurityConfig.java \
        src/main/java/ch/jp/shooting/api/AuthController.java
git commit -m "[backend] Add GET /api/auth/me with full profile and flat permissions"
```

---

## Task 4: Manual smoke test of `/api/auth/me`

This task has no code — it verifies the endpoint works end-to-end before starting frontend work.

- [ ] **Step 1: Start the backend with the H2 profile**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

Expected: Server starts on port 8080.

- [ ] **Step 2: Log in and capture the token**

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@smartground.local","password":"admin123"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
echo $TOKEN
```

Expected: A non-empty JWT string.

- [ ] **Step 3: Call `/api/auth/me`**

```bash
curl -s http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer $TOKEN" | python -m json.tool
```

Expected: JSON with `id`, `email`, `vorname`, `nachname`, `status`, and `permissions` array. The `permissions` array should contain strings like `"MANAGE_USERS"`, `"MANAGE_RANGES"`, etc.

- [ ] **Step 4: Verify unauthenticated request is rejected**

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/auth/me
```

Expected: `401`

---

## Task 5: Add `getMe()` to `authApi.js`

**Files:**
- Modify: `smart-ground-ui/src/services/authApi.js`

- [ ] **Step 1: Add the `getMe` export**

Replace the entire file content:

```js
import { apiFetch } from './apiClient.js';

export async function login(username, password) {
  return apiFetch('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export async function createUser(username, password, role) {
  return apiFetch('/users', {
    method: 'POST',
    body: JSON.stringify({ username, password, role }),
  });
}

export async function getMe() {
  return apiFetch('/auth/me');
}
```

---

## Task 6: Redesign `authStore.js`

**Files:**
- Modify: `smart-ground-ui/src/stores/authStore.js`

Replace the entire store. Key changes:
- Add `profile` and `permissions` reactive state
- Add `hasPermission(permission)` replacing `isShooter()` / `isAdminOrOwner()`
- Add `init()` action — called on app startup to restore state from a persisted token
- `login()` fetches `/auth/me` immediately after receiving the JWT
- `logout()` clears token, profile, and permissions
- Remove `jwtPayload`, `role`, `userName`, `isShooter`, `isAdminOrOwner`
- Add `displayName` computed from profile

- [ ] **Step 1: Write the failing store test**

Create `smart-ground-ui/src/stores/__tests__/authStore.test.js`:

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../authStore'
import * as authApi from '../../services/authApi'

vi.mock('../../services/authApi')

const mockProfile = {
  id: 'user-1',
  email: 'admin@smartground.local',
  vorname: 'Max',
  nachname: 'Muster',
  status: 'ACTIVE',
  erstelltAm: '2024-01-01T00:00:00Z',
  permissions: ['MANAGE_USERS', 'MANAGE_RANGES'],
}

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('is not authenticated initially', () => {
    const store = useAuthStore()
    expect(store.isAuthenticated()).toBe(false)
    expect(store.permissions).toEqual([])
    expect(store.profile).toBeNull()
  })

  it('login stores token and fetches profile', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ token: 'test.jwt.token' })
    vi.mocked(authApi.getMe).mockResolvedValue(mockProfile)

    const store = useAuthStore()
    await store.login('admin@smartground.local', 'admin123')

    expect(localStorage.getItem('sg_token')).toBe('test.jwt.token')
    expect(store.isAuthenticated()).toBe(true)
    expect(store.profile).toEqual(mockProfile)
    expect(store.permissions).toEqual(['MANAGE_USERS', 'MANAGE_RANGES'])
  })

  it('hasPermission returns true for granted permission', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ token: 'test.jwt.token' })
    vi.mocked(authApi.getMe).mockResolvedValue(mockProfile)

    const store = useAuthStore()
    await store.login('admin@smartground.local', 'admin123')

    expect(store.hasPermission('MANAGE_USERS')).toBe(true)
    expect(store.hasPermission('VIEW_REMOTE')).toBe(false)
  })

  it('logout clears all state', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ token: 'test.jwt.token' })
    vi.mocked(authApi.getMe).mockResolvedValue(mockProfile)

    const store = useAuthStore()
    await store.login('admin@smartground.local', 'admin123')
    store.logout()

    expect(store.isAuthenticated()).toBe(false)
    expect(store.profile).toBeNull()
    expect(store.permissions).toEqual([])
    expect(localStorage.getItem('sg_token')).toBeNull()
  })

  it('init fetches profile if token exists in localStorage', async () => {
    localStorage.setItem('sg_token', 'existing.jwt.token')
    vi.mocked(authApi.getMe).mockResolvedValue(mockProfile)

    const store = useAuthStore()
    await store.init()

    expect(store.profile).toEqual(mockProfile)
    expect(store.permissions).toEqual(['MANAGE_USERS', 'MANAGE_RANGES'])
  })

  it('init clears token if getMe fails', async () => {
    localStorage.setItem('sg_token', 'expired.jwt.token')
    vi.mocked(authApi.getMe).mockRejectedValue(new Error('HTTP 401'))

    const store = useAuthStore()
    await store.init()

    expect(store.isAuthenticated()).toBe(false)
    expect(localStorage.getItem('sg_token')).toBeNull()
  })

  it('displayName returns full name from profile', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ token: 'test.jwt.token' })
    vi.mocked(authApi.getMe).mockResolvedValue(mockProfile)

    const store = useAuthStore()
    await store.login('admin@smartground.local', 'admin123')

    expect(store.displayName).toBe('Max Muster')
  })
})
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
cd smart-ground-ui
npm run test -- src/stores/__tests__/authStore.test.js
```

Expected: Tests fail because `authStore` doesn't have `init()`, `hasPermission()`, `permissions`, or `profile` yet.

- [ ] **Step 3: Replace `authStore.js` with the new implementation**

```js
import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { login as loginApi, createUser as createUserApi, getMe } from '../services/authApi.js';

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('sg_token') || null);
  const profile = ref(null);
  const permissions = ref([]);
  const isLoading = ref(false);
  const error = ref(null);

  const displayName = computed(() => {
    if (!profile.value) return null;
    return `${profile.value.vorname} ${profile.value.nachname}`;
  });

  const isAuthenticated = () => !!token.value;

  const hasPermission = (permission) => permissions.value.includes(permission);

  const _loadProfile = async () => {
    const data = await getMe();
    profile.value = data;
    permissions.value = data.permissions ?? [];
  };

  const login = async (username, password) => {
    isLoading.value = true;
    error.value = null;
    try {
      const data = await loginApi(username, password);
      token.value = data.token;
      localStorage.setItem('sg_token', data.token);
      await _loadProfile();
    } catch (err) {
      error.value = err.message;
      throw err;
    } finally {
      isLoading.value = false;
    }
  };

  const init = async () => {
    if (!token.value) return;
    try {
      await _loadProfile();
    } catch {
      // Token is expired or invalid — clear everything
      token.value = null;
      profile.value = null;
      permissions.value = [];
      localStorage.removeItem('sg_token');
    }
  };

  const createUser = async (username, password, role) => {
    isLoading.value = true;
    error.value = null;
    try {
      await createUserApi(username, password, role);
    } catch (err) {
      error.value = err.message;
      throw err;
    } finally {
      isLoading.value = false;
    }
  };

  const logout = () => {
    token.value = null;
    profile.value = null;
    permissions.value = [];
    localStorage.removeItem('sg_token');
  };

  return {
    token,
    profile,
    permissions,
    displayName,
    isLoading,
    error,
    isAuthenticated,
    hasPermission,
    login,
    init,
    logout,
    createUser,
  };
});
```

- [ ] **Step 4: Run the tests again to confirm they pass**

```bash
npm run test -- src/stores/__tests__/authStore.test.js
```

Expected: All 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/services/authApi.js src/stores/authStore.js src/stores/__tests__/authStore.test.js
git commit -m "[ui] Redesign authStore with permissions and /auth/me integration"
```

---

## Task 7: Call `authStore.init()` on app startup

**Files:**
- Modify: `smart-ground-ui/src/main.js`

On page refresh, the token is in localStorage but `profile` and `permissions` are lost (they're in-memory). `init()` restores them before the router resolves any route guard.

- [ ] **Step 1: Update `main.js`**

```js
import './assets/main.css';
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router/index.js';
import { useAuthStore } from './stores/authStore.js';

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
app.use(router);

// Restore auth state from persisted token before the router processes any route
const authStore = useAuthStore();
authStore.init().finally(() => {
  app.mount('#app');
});
```

- [ ] **Step 2: Verify the app still starts**

```bash
npm run dev
```

Open `http://localhost:5173` in the browser. Expected: Login page loads without errors in the browser console.

---

## Task 8: Update router guards and route `meta`

**Files:**
- Modify: `smart-ground-ui/src/router/index.js`

Replace role-based guards with permission checks. Add `meta.permission` to routes that need specific permissions. Use a `defaultHome()` helper to redirect users to the right starting page based on what they're allowed to do.

- [ ] **Step 1: Replace `router/index.js`**

```js
import { createRouter, createWebHistory } from 'vue-router';
import RangesView from '@/views/RangesView.vue';
import RangeDetailView from '@/views/RangeDetailView.vue';
import SmartBoxesView from '@/views/SmartBoxesView.vue';
import FirmwareConfigsView from '@/views/FirmwareConfigsView.vue';
import UsersView from '@/views/UsersView.vue';
import ProfileView from '@/views/ProfileView.vue';
import LoginView from '@/views/LoginView.vue';
import ShooterHomeView from '@/views/shooter/ShooterHomeView.vue';
import ShooterRangeSelectView from '@/views/shooter/ShooterRangeSelectView.vue';
import ShooterRemoteView from '@/views/shooter/ShooterRemoteView.vue';
import ShooterPlayPage from '@/views/shooter/ShooterPlayPage.vue';
import CompetitionTemplateListView from '@/views/competition/CompetitionTemplateListView.vue';
import CompetitionSetupView from '@/views/competition/CompetitionSetupView.vue';
import CompetitionLiveView from '@/views/competition/CompetitionLiveView.vue';
import CompetitionLeaderboardView from '@/views/competition/CompetitionLeaderboardView.vue';
import CareerStatsView from '@/views/competition/CareerStatsView.vue';
import PasseManagementView from '@/views/shooter/PasseManagementView.vue';
import PassenAdminView from '@/views/PassenAdminView.vue';
import CompetitionBracketView from '@/views/CompetitionBracketView.vue';
import PlayerSetupView from '@/views/PlayerSetupView.vue';
import CompetitionManagementView from '@/views/CompetitionManagementView.vue';
import { useAuthStore } from '@/stores/authStore';

const routes = [
  { path: '/login', component: LoginView, meta: { requiresAuth: false } },
  { path: '/', redirect: '/ranges' },

  // ── Admin routes ──────────────────────────────────────────────────────
  { path: '/ranges',               component: RangesView,                 meta: { layout: 'admin', permission: 'MANAGE_RANGES' } },
  { path: '/ranges/:id',           component: RangeDetailView, props: route => ({ id: route.params.id }), meta: { layout: 'admin', permission: 'MANAGE_RANGES' } },
  { path: '/smartboxes',           component: SmartBoxesView,             meta: { layout: 'admin', permission: 'MANAGE_RANGES' } },
  { path: '/admin/firmware-configs', component: FirmwareConfigsView,      meta: { layout: 'admin', permission: 'MANAGE_RANGES' } },
  { path: '/users',                component: UsersView,                  meta: { layout: 'admin', permission: 'MANAGE_USERS' } },
  { path: '/profile',              component: ProfileView,                meta: { layout: 'admin' } },
  { path: '/player-setup',         component: PlayerSetupView,            meta: { layout: 'admin', permission: 'MANAGE_COMPETITIONS' } },
  { path: '/competition',          component: CompetitionManagementView,  meta: { layout: 'admin', permission: 'MANAGE_COMPETITIONS' } },
  { path: '/competition/templates', component: CompetitionTemplateListView, meta: { layout: 'admin', permission: 'MANAGE_COMPETITIONS' } },
  { path: '/competition/setup',    component: CompetitionSetupView,       meta: { layout: 'admin', permission: 'MANAGE_COMPETITIONS' } },
  { path: '/competition/bracket',  component: CompetitionBracketView,     meta: { layout: 'admin', permission: 'MANAGE_COMPETITIONS' } },
  { path: '/passen',               component: PassenAdminView,            meta: { layout: 'admin', permission: 'MANAGE_PASSE_TEMPLATES' } },

  // ── Shooter routes ────────────────────────────────────────────────────
  { path: '/home',                 component: ShooterHomeView,            meta: { layout: 'shooter', permission: 'VIEW_REMOTE' } },
  { path: '/remote',               component: ShooterRangeSelectView,     meta: { layout: 'shooter', permission: 'VIEW_REMOTE' } },
  { path: '/remote/:rangeId',      component: ShooterRemoteView, props: true, meta: { layout: 'shooter', permission: 'VIEW_REMOTE' } },
  { path: '/remote/:rangeId/play', component: ShooterPlayPage,  props: true, meta: { layout: 'shooter', permission: 'PLAY_SERIES' } },
  { path: '/competition/live',     component: CompetitionLiveView,        meta: { layout: 'shooter', permission: 'PLAY_COMPETITION' } },
  { path: '/wettkampf/live/:instanceId', component: () => import('@/views/competition/CompetitionLiveView.vue'), props: true, meta: { layout: 'shooter', permission: 'PLAY_COMPETITION' } },
  { path: '/competition/leaderboard', component: CompetitionLeaderboardView, meta: { layout: 'shooter', permission: 'VIEW_REMOTE' } },
  { path: '/career-stats',         component: CareerStatsView,            meta: { layout: 'shooter', permission: 'VIEW_REMOTE' } },
  { path: '/meine-passen',         component: PasseManagementView,        meta: { layout: 'shooter', permission: 'PLAY_SERIES' } },
  { path: '/training',             component: () => import('@/views/shooter/TrainingManagementView.vue'), meta: { layout: 'shooter', permission: 'START_TRAINING' } },
  { path: '/wettkampf',            component: () => import('@/views/shooter/CompetitionManagementView.vue'), meta: { layout: 'shooter', permission: 'PLAY_COMPETITION' } },
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

// Returns the appropriate home path based on what the user is permitted to do
const defaultHome = (auth) =>
  auth.hasPermission('VIEW_REMOTE') ? '/home' : '/ranges';

router.beforeEach((to, from, next) => {
  const auth = useAuthStore();
  const authenticated = auth.isAuthenticated();
  const requiresAuth = to.meta.requiresAuth !== false;

  // Not authenticated → login
  if (requiresAuth && !authenticated) {
    next('/login');
    return;
  }

  // Already authenticated on login → role-based home
  if (to.path === '/login' && authenticated) {
    next(defaultHome(auth));
    return;
  }

  // Root redirect → permission-based home
  if (to.path === '/' && authenticated) {
    next(defaultHome(auth));
    return;
  }

  // Route requires a specific permission — check it
  const requiredPermission = to.meta.permission;
  if (requiredPermission && !auth.hasPermission(requiredPermission)) {
    next(defaultHome(auth));
    return;
  }

  next();
});

export default router;
```

- [ ] **Step 2: Run the full test suite**

```bash
npm run test
```

Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/router/index.js src/main.js
git commit -m "[ui] Replace role guards with permission-based route guards"
```

---

## Task 9: Update `Sidebar.vue`

**Files:**
- Modify: `smart-ground-ui/src/components/Sidebar.vue`

The sidebar uses `isAdminOrOwner()` to hide the Users nav item. Replace with `hasPermission('MANAGE_USERS')`. Also update `username` to use `displayName` from the new store.

- [ ] **Step 1: Update the script section**

Find the `navItems` computed and `username` computed in `Sidebar.vue` and replace:

```js
const navItems = computed(() => {
  return allNavItems.filter(item => {
    if (item.adminOnly && !authStore.isAdminOrOwner()) {
      return false;
    }
    return true;
  });
});

const username = computed(() => {
  return authStore.username || 'Benutzer';
});
```

with:

```js
const navItems = computed(() => {
  return allNavItems.filter(item => {
    if (item.requiredPermission && !authStore.hasPermission(item.requiredPermission)) {
      return false;
    }
    return true;
  });
});

const username = computed(() => {
  return authStore.displayName || 'Benutzer';
});
```

- [ ] **Step 2: Update `allNavItems` to use `requiredPermission` instead of `adminOnly`**

Replace:
```js
const allNavItems = [
  { id: 'ranges', label: 'Plätze', icon: 'target' },
  { id: 'smartboxes', label: 'SmartBoxen', icon: 'wifi' },
  { id: 'competition', label: 'Wettkampf', icon: 'award' },
  { id: 'passen', label: 'Passen', icon: 'program' },
  { id: 'users', label: 'Benutzer', icon: 'user', adminOnly: true },
  { id: 'profile', label: 'Profil', icon: 'user' },
];
```

with:

```js
const allNavItems = [
  { id: 'ranges', label: 'Plätze', icon: 'target', requiredPermission: 'MANAGE_RANGES' },
  { id: 'smartboxes', label: 'SmartBoxen', icon: 'wifi', requiredPermission: 'MANAGE_RANGES' },
  { id: 'competition', label: 'Wettkampf', icon: 'award', requiredPermission: 'MANAGE_COMPETITIONS' },
  { id: 'passen', label: 'Passen', icon: 'program', requiredPermission: 'MANAGE_PASSE_TEMPLATES' },
  { id: 'users', label: 'Benutzer', icon: 'user', requiredPermission: 'MANAGE_USERS' },
  { id: 'profile', label: 'Profil', icon: 'user' },
];
```

- [ ] **Step 3: Run tests**

```bash
npm run test
```

Expected: All tests pass.

---

## Task 10: Update remaining components

**Files:**
- Modify: `smart-ground-ui/src/components/PositionCard.vue`
- Modify: `smart-ground-ui/src/components/shooter-remote/ShooterFlyoutPanel.vue`
- Modify: `smart-ground-ui/src/views/shooter/PasseManagementView.vue`

Each file uses `isAdminOrOwner()`. Replace with the appropriate `hasPermission()` call.

- [ ] **Step 1: Update `PositionCard.vue`**

Find (around line 137):
```js
const isAdmin = computed(() => authStore.isAdminOrOwner());
```

Replace with:
```js
const isAdmin = computed(() => authStore.hasPermission('MANAGE_RANGES'));
```

- [ ] **Step 2: Update `ShooterFlyoutPanel.vue`**

Find (around line 284 in template):
```html
<label v-if="authStore.isAdminOrOwner()" class="range-ownership-toggle">
```

Replace with:
```html
<label v-if="authStore.hasPermission('OPERATE_RANGE')" class="range-ownership-toggle">
```

- [ ] **Step 3: Update `PasseManagementView.vue`**

Find (around line 332):
```html
v-if="seg.ownership !== 'range' || authStore.isAdminOrOwner()"
```

Replace with:
```html
v-if="seg.ownership !== 'range' || authStore.hasPermission('MANAGE_RANGES')"
```

- [ ] **Step 4: Run the full test suite and lint**

```bash
npm run test
npm run lint
```

Expected: All tests pass, no lint errors.

- [ ] **Step 5: Commit**

```bash
git add src/components/Sidebar.vue \
        src/components/PositionCard.vue \
        src/components/shooter-remote/ShooterFlyoutPanel.vue \
        src/views/shooter/PasseManagementView.vue
git commit -m "[ui] Replace isAdminOrOwner/isShooter with hasPermission in all components"
```

---

## Task 11: End-to-end verification

- [ ] **Step 1: Start the full stack**

Terminal 1 (backend):
```bash
cd smart-ground-backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

Terminal 2 (frontend):
```bash
cd smart-ground-ui
npm run dev
```

- [ ] **Step 2: Log in as admin and verify permissions**

1. Open `http://localhost:5173`
2. Log in with `admin@smartground.local` / `admin123`
3. Open browser DevTools → Application → Local Storage → `sg_token` should be present
4. Open DevTools console and run:
   ```js
   const { useAuthStore } = await import('/src/stores/authStore.js')
   const s = useAuthStore()
   console.log(s.permissions)
   console.log(s.displayName)
   console.log(s.profile)
   ```
   Expected: `permissions` is a non-empty array, `displayName` is the admin's full name.

- [ ] **Step 3: Verify navigation items are permission-gated**

- Sidebar should show "Benutzer" item for admin (has `MANAGE_USERS`)
- Log in as a shooter (`user@smartground.local` / `user`) — "Benutzer" should be hidden

- [ ] **Step 4: Verify page refresh preserves auth state**

1. Log in as admin
2. Navigate to `/ranges`
3. Hard-refresh the page (Ctrl+F5)
4. Expected: Still on `/ranges`, not redirected to login

- [ ] **Step 5: Verify protected route blocks unauthorized access**

1. Log in as shooter
2. Manually navigate to `http://localhost:5173/users`
3. Expected: Redirected away (to `/home` if shooter has `VIEW_REMOTE`)

- [ ] **Step 6: Final lint and build check**

```bash
cd smart-ground-ui
npm run lint
npm run build
```

Expected: No warnings, build succeeds.

```bash
cd smart-ground-backend
./mvnw clean package -q
```

Expected: `BUILD SUCCESS`
