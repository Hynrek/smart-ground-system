# smart-ground-ui — Vue 3 Development Guide

## Project Overview

The Smart Ground UI is a Vue 3 single-page application that provides the web interface for managing shooting-range devices, running live play sessions (Passen), and organizing competitions (Wettkämpfe). It communicates with the backend REST API defined in `smart-ground-backend/src/main/resources/static/openapi.yaml`.

---

## Stack & Versions

- **Vue**: 3.5 (Composition API, `<script setup>` only)
- **Build**: Vite 8
- **State**: Pinia 3
- **Router**: Vue Router 4.6
- **Node**: `^20.19` (see `engines` in `package.json`)
- **Package Manager**: npm
- **Testing**: Vitest 4 + @vue/test-utils
- **Linting**: ESLint 9 (flat config)
- **HTTP**: Native `fetch` via `services/apiClient.js`

---

## Project Structure

The directory tree is the source of truth for what exists — don't rely on lists in this file. Layout:

```
src/
├── components/        # Reusable components; subfolders competition/, shooter/, shooter-remote/
├── views/             # LoginView, WelcomeView, NoAccessView + admin/ and shooter/ views
├── layouts/           # MainLayout (admin sidebar) / ShooterLayout (kiosk/touch)
├── stores/            # Pinia stores — one per domain (see Stores)
├── services/          # API layer — xxxApi.js per backend domain (see Services)
├── mappers/           # API shape → UI model (device/range/smartbox domains)
├── composables/       # use* composition functions
├── constants/         # stepModes.js, playEnums.js, deviceTypes.js, ota.js, qr.js
├── assets/            # main.css (single source of design tokens), logo SVGs
└── router/index.js    # Permission-based route guards (see Routing)
```

Config at the root: `vite.config.js` (defines the `@` → `src/` alias), `vitest.config.js`, `eslint.config.js`.

---

## Data Flow

```
Backend REST API
      │
      ▼
  services/          ← Raw fetch calls via apiClient, returns raw JSON
      │
      ▼
  mappers/           ← (device/range/smartbox domains) API shape → UI model, pure functions
      │
      ▼
  stores/            ← Pinia state
      │
      ▼
  views/components   ← Read from stores, dispatch store actions
```

Never fetch from the API directly inside a component. Always go through the store → service (→ mapper) chain. Mappers currently exist for the SmartBox/Device/Range domains; newer domains (Passe, Wettkampf) map inside their stores/services.

**There is no mock mode.** The former `VITE_WORK_MODE=mock` mechanism and `constants/mockData.js` were removed — the UI always talks to a running backend.

---

## Test Credentials

Seeded by the backend `DataInitializer` — see the **Seeded users** table in [`smart-ground-backend/CLAUDE.md`](../smart-ground-backend/CLAUDE.md) (single source of truth; login accepts email or username).

---

## Running Locally

```bash
npm install
cp .env.example .env.local     # set VITE_API_BASE_URL=http://localhost:8080/api

npm run dev          # http://localhost:5173 (HMR)
npm run build        # production bundle → dist/
npm run preview      # serve dist/ locally

npm run lint         # ESLint with auto-fix
npm run lint:check   # check only (CI)

npm run test           # Vitest
npm run test:watch
npm run test:coverage  # report → coverage/
```

### CORS
The backend allows `http://localhost:5173`. If you change the dev port, update `cors.allowed-origins` in the backend's `application.properties`.

---

## Authentication & Authorization (permission-based)

Access control is **permission-based, not role-based**. The backend resolves the user's effective permission set (dynamic RBAC — see backend CLAUDE.md); the UI never inspects role names.

### Flow
1. `LoginView` → `authStore.login(username, password)` → `POST /api/auth/login` (email **or** username)
2. JWT stored in localStorage under key `sg_token`
3. `authStore` then loads `GET /api/auth/me` → `profile` + `permissions[]`
4. Every request carries `Authorization: Bearer <token>` (via `authHeader.js` / `apiClient`)
5. On app start, `authStore.init()` restores the session; router guards `await auth.readyPromise` before deciding anything
6. 401 → token cleared, redirect to login

### Permission checks
```javascript
const auth = useAuthStore()
auth.isAuthenticated()          // token present
auth.hasPermission('MANAGE_RANGES')
```
Permission strings come from the backend `Permission` enum: `MANAGE_USERS`, `MANAGE_RANGES`, `MANAGE_SERIES_TEMPLATES`, `MANAGE_PASSE_TEMPLATES`, `MANAGE_COMPETITIONS`, `OPERATE_RANGE`, `START_TRAINING`, `START_COMPETITION`, `MANAGE_SERIES`, `RESERVE_REMOTE`, `VIEW_REMOTE`, `PLAY_SERIES`, `PLAY_COMPETITION`.

### Router guards (`router/index.js`)
Each route declares `meta.layout` (`'admin'` | `'shooter'`) and `meta.permission`. The global guard enforces, in order:

1. Unauthenticated → `/login`
2. Authenticated on `/login` or `/` → `defaultHome(auth)`:
   - user with `profile.assignedRangeId` → `/remote/{assignedRangeId}`
   - `VIEW_REMOTE` → `/home`; else `MANAGE_RANGES` → `/ranges`; else `/no-access`
3. Route `meta.permission` not held → redirect to own home (or `/no-access`)
4. **Hard-lock**: a user with `assignedRangeId` may only visit `/remote/{assignedRangeId}*` (escape routes: `/login`, `/no-access`, `/welcome`) — this is the kiosk-account mechanism
5. API-backed stores are lazily initialized once on first authenticated navigation

### Routes

`router/index.js` is the source of truth for the route inventory. Every route declares `meta.layout` (`'admin'` → MainLayout, `'shooter'` → ShooterLayout) and `meta.permission`. Rough map: admin views live under `/ranges`, `/smartboxes`, `/users`, `/passen`, `/admin/*` (firmware-configs, firmware-updates, wettkampf); shooter views under `/home`, `/remote/*`, `/meine-passen`, `/profil`, `/wettkampf*`, `/competition/live`.

---

## Pinia Stores

One store per domain in `src/stores/` — the directory is the inventory. All stores use the Composition API factory pattern (`defineStore('name', () => { … })`) with `ref` state, `computed` getters, async actions with try/catch, and a returned public interface.

Non-obvious stores (the rest map 1:1 to a backend domain):

| Store | Why it's special |
|---|---|
| `authStore` | JWT lifecycle (`sg_token` in localStorage), profile + permissions from `/api/auth/me`; exposes `readyPromise` that router guards `await` before any decision |
| `passeStore` vs `activePasseStore` vs `playSessionStore` | `passeStore` = templates (CRUD); `activePasseStore` = the currently running Passe (PlayInstance) on a range; `playSessionStore` = live play execution state (blocks, results) |
| `competitionEventStore` | Wettkampf sessions: groups (Rotten), progress, Stechen (e.g. `getActiveStechenForRange`) |
| `otaStore` | Polls `GET /smart-boxes/{id}/ota` every 3 s until a terminal phase (`APPLIED`/`FAILED`/`ROLLED_BACK`); views must call `stopAllPolling()` on unmount |
| `profileStore` | Own QR check-in token (`/users/me/qr`, rotate) and personal play results (`/users/me/play-results`); consumed by `ShooterProfilView` and `QrScanModal` (resolve on scan) |

Store rules:
- Use `storeToRefs()` when destructuring store state in components
- No direct state mutation from components — go through actions
- Store tests: fresh Pinia per test (`setActivePinia(createPinia())`), mock services with `vi.mock()`

---

## Services (API layer)

Thin wrappers around `apiClient` (fetch + auth header + 401 handling + error mapping), one `xxxApi.js` module per backend domain — the directory is the inventory; endpoints must exist in the backend `openapi.yaml`.

Non-obvious name → endpoint mappings:

- `wettkampfApi.js` → `/sessions` (competition sessions, groups, members, progress, serie complete/results)
- `tiebreakerApi.js` → `/sessions/{id}/ties` + `/sessions/{id}/tiebreakers` (Stechen)
- `otaApi.js` → `/ota/releases` uses multipart upload via `apiUpload` (not the JSON `apiClient` path)
- `deviceTypeApi.js` also covers `/device-types/firmware-configs`; `deviceTypeGroupApi.js` covers `/device-types/groups`

Naming: `xxxApi.js` wraps backend endpoints; a `xxxService.js` would hold local business logic (none currently).

---

## Real-Time Events (WebSocket / STOMP)

> **⚠️ NOT YET NEEDED — do not implement until the backend service layer calls `SessionWebSocketService`.**
>
> The backend has a configured STOMP endpoint at `/ws/shooting` (SockJS fallback) but none of `SessionWebSocketService`'s publish methods are called anywhere. There is no frontend STOMP client. Live views that need fresh data poll (e.g. `otaStore` 3 s polling, `StechenPanel` light-polling).
>
> When the backend starts emitting: add `@stomp/stompjs` + `sockjs-client`, implement a `realtimeService.js`, and subscribe from Pinia stores.

**Do not use SSE (`EventSource`) or add a `/api/events` endpoint.** STOMP over WebSocket/SockJS is the chosen transport — that decision is final.

---

## Conventions & Patterns

### Naming
- **Components**: PascalCase (`SmartBoxCard.vue`); subdirectories kebab-case (`shooter-remote/`)
- **Variables/functions**: camelCase, English identifiers (even for German domain terms)
- **Stores**: `xxxStore.js`; **services**: `xxxApi.js`; **composables**: `use*`
- **Routes**: kebab-case or German domain paths (`/meine-passen`, `/admin/wettkampf`)
- **Constants**: `SCREAMING_SNAKE_CASE`

### General Rules
- **`<script setup>` only** — no Options API
- **`@/` alias** for all imports within `src/`; never chain `../` more than one level
- **No direct API calls in components**
- **Prop drilling max 2 levels** — lift deeper data into a store
- **German display labels** in UI text (Platz, Werfer, Passe, Wettkampf, Rotte, Stechen); identifiers stay English; inline comments in English
- **Design tokens** from the `--sg-*` custom properties in `assets/main.css` — no hard-coded colors. **One dark palette for the whole product** (admin and shooter): navy canvas (`--sg-bg-page`), raised surfaces (`--sg-bg-card`/`--sg-bg-panel`), white-alpha text ramp (`--sg-text-primary/muted/faint/disabled`), cyan accent. Card "glow" treatment via the global `.sg-card-surface` utility (`--sg-card-accent` drives the hue); admin/dense screens add `.sg-card-surface--calm` — same language, lower intensity. Solid accent fills carry dark glyphs (`color: var(--sg-surface-0)`), never white. There is no light theme and no palette switcher.
- **Remove unused code eagerly** — delete unused methods, components, stores, services, imports, and files rather than leaving them
- **QR check-in payload**: always build/parse via `constants/qr.js` (`smartground://checkin/<token>`) — never hard-code the prefix

### Branding — Logo & Icon
The brand mark is a monoline **microchip** with a solid center core, paired with the "SMART GROUND" wordmark and the tagline "Grounded in Innovation".

- **Single source of truth: `components/Logo.vue`.** Always render the logo through this component — never inline the SVG or use an emoji/`<img>` placeholder.
  - `<Logo variant="full" :size="60" />` — icon + wordmark + tagline (login, headers, empty states)
  - `<Logo variant="mark" :size="26" />` — icon only (sidebar, compact spots)
- **Color via `currentColor`** — defaults to `var(--sg-brand)`, recolors with the active palette. Override `color` on a parent for contrast (dark sidebar sets `color: #fff`). Never hard-code the fill.
- **Static assets** (`assets/logo-full.svg`, `assets/logo-mark.svg`) exist for non-Vue contexts; prefer `Logo.vue` in the app.
- **Favicon:** `public/favicon.svg` (white mark on navy `#1a1a2e`), intentionally fixed-color.
- If the geometry changes, update `Logo.vue` and both `assets/logo-*.svg` together.

---

## Step Iconography & Color Code

Step cards represent the shot modes of a Serie. The notation and color code are a **shared visual language**: Shooter views and Admin views MUST render steps identically.

### Rules
1. **Always show the position name(s) as the step label.** The trap/position is the anchor; the mode badge qualifies it.
2. **Notation encodes two things at once** — trap count (one vs two positions) and the timing/trigger that releases the next target.
3. **One shared source of truth: `constants/stepModes.js`.** Import `stepModeLabel`, `stepNotation`, `stepLetters`, `isMultiResultStep`, `modeBadgeStyle`, `modeDotStyle`, `STEP_MODE_LIST` from there — never hard-code mode labels, notation, colors, or the `[PAIR, A_SCHUSS, RAFFALE]` "double" check in a component. Consumers include `ShooterFlyoutPanel`, `ShooterPlayPage`, `ScoreTable`, `SerieDrawer`, `PassenAdminView`, `PasseManagementView`, `StepScorecard`.
   - `isMultiResultStep(type)` — the "is this a two-clay step" predicate (drives split-vs-solid scorecard chips and per-clay fail correction)
   - `stepLetters(step)` — resolves a step's position letters into `{ first, second }`

### Modes & notation

| Mode | Notation | Meaning |
|---|---|---|
| **Solo** | `A` | One trap, one target. |
| **Pair** | `A + B` | Two traps, released **simultaneously**. |
| **a.Schuss** (auf Schuss / on report) | `A → B` | Two traps; second target released **because the shooter fired** (event-triggered). |
| **Raffale** | `A ×2` | **One** trap, fires **twice** after a fixed timeout (time-triggered repeat). |

Grammar: single letter = one trap, two letters = two traps; `+` = simultaneous, `→` = on report, `×2` = timed repeat. `→` is reserved for the on-report mechanic — a future timed two-trap double gets its own mark.

### Color code

Colors encode **escalating complexity**. Red is reserved exclusively for Notfall/Stop and the × delete affordance — no mode may use red (or green). Each badge carries color **and** text label (WCAG — meaning never depends on color alone).

| Mode | Hue | Base | Badge text (on dark) |
|---|---|---|---|
| **Solo** | Teal | `#1D9E75` | `#5DCAA5` |
| **Pair** | Blue | `#378ADD` | `#85B7EB` |
| **a.Schuss** | Amber | `#EF9F27` | `#FAC775` |
| **Raffale** | Purple | `#7F77DD` | `#AFA9EC` |
| _Notfall/Stop, × delete_ | Red | `#E24B4A` | _reserved — never a mode_ |

Badge fill on dark surfaces: base hue at ~20% alpha with the badge-text color for the label (e.g. Solo: `background: rgba(29,158,117,.20); color: #5DCAA5`).

---

## Testing

- **Component tests** (`src/components/**/__tests__/`, `src/views/**/__tests__/`): `mount()` from `@vue/test-utils`, fresh Pinia per test, test interactions
- **Store tests** (`src/stores/__tests__/`): `setActivePinia(createPinia())` in `beforeEach`, mock services with `vi.mock()`, test state/actions/computed
- **Service/composable tests** (`src/services/__tests__/`, `src/composables/__tests__/`): mock `fetch`, test error handling and transforms
- Tests are written in **English** and must be independent (no shared mutable state)
- Coverage target: ≥80% for new code
- Single file: `npm run test src/stores/__tests__/authStore.test.js`

---

## Accessibility & Responsive Design

- WCAG AA: contrast ≥4.5:1, keyboard navigable, `:focus-visible` styles, ARIA labels, form labels linked via `for`, no keyboard traps
- Mobile-first CSS Grid/Flexbox; test at 320px / 768px / 1920px
- ShooterLayout is a touch/kiosk interface: large text, touch targets ≥48px, tolerant of brief disconnects

---

## Environment Variables

`.env.local` (from `.env.example`):

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

Access in code: `import.meta.env.VITE_API_BASE_URL`.

---

## Code Review Checklist (Frontend-Specific)

- [ ] All tests pass: `npm run test`; no ESLint warnings: `npm run lint:check`
- [ ] No hardcoded API URLs (use `VITE_API_BASE_URL`)
- [ ] Error states handled (loading, error, empty)
- [ ] `<script setup>` + Composition API; shared state in Pinia, API calls in services
- [ ] Route declares correct `meta.layout` + `meta.permission`; UI hides controls the user lacks permission for
- [ ] Step rendering imports from `constants/stepModes.js` (never hard-coded modes/colors)
- [ ] German display labels, English identifiers/comments/tests
- [ ] Polling loops stopped on unmount (`stopAllPolling()` pattern)
- [ ] No dead code, no unused imports; minimal new dependencies (justify in commit message)
- [ ] Accessibility: labels, contrast, keyboard nav, touch targets (shooter views)
- [ ] Build succeeds: `npm run build` with no warnings
- [ ] Commit message: `[ui] short description`

---

## Common Agent Tasks

**Adding a new page/view:**
1. Create the view in `src/views/admin/` or `src/views/shooter/`
2. Register the route in `router/index.js` with `meta.layout` + `meta.permission`
3. Add a nav link in `Sidebar.vue` (admin) or the shooter nav if needed
4. Connect to the appropriate Pinia store

**Adding a new API call:**
1. Add the function to the relevant `services/xxxApi.js` (endpoint must exist in the backend `openapi.yaml`)
2. Add/update a mapper if the response shape needs transforming
3. Add a store action that calls the service and updates state
4. Call the store action from the view

**Adding a new step mode:** extend `constants/stepModes.js` (label, notation, color, multi-result predicate) — components pick it up automatically.

---

## Troubleshooting

**Port 5173 in use**: `npm run dev -- --port 5174` (then update backend CORS).

**401 Unauthorized**: token missing/expired — check `localStorage.getItem('sg_token')`, log in again.

**404 on API calls**: backend not running or wrong `VITE_API_BASE_URL`; check the Network tab.

**Redirected to `/no-access` or wrong home**: the logged-in user lacks the route's `meta.permission`, or has an `assignedRangeId` (kiosk hard-lock — only `/remote/{id}` is reachable).

**Component state not updating**: destructure store state with `storeToRefs()`; `await` store actions.

**Real-time updates missing**: expected — STOMP is not wired yet (see Real-Time Events). Views poll where needed.
