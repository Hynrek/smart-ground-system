# AGENTS.md — smart-ground-ui

> This file is intended for AI agents and automated tooling. It describes the frontend's architecture, conventions, and key patterns so that agents can navigate and modify the codebase accurately and safely.

---

## Project Overview

**smart-ground-ui** is the admin/management web interface for the Smart Ground IoT shooting range system. It communicates exclusively with the Spring Boot backend (`smart-ground-backend`) via REST API. There is no direct MQTT or database connection from the frontend.

The UI allows operators to:
- Register and monitor SmartBoxes (Raspberry Pi Pico 2W devices)
- Create and manage Devices (traps, LEDs, buttons, sensors) and Device Types
- Configure and manage Ranges (shooting stations/Plätze)
- Control Werfer (clay trap launchers) remotely via the Werfer Remote interface

---

## Technology Stack

| Layer | Technology |
|---|---|
| Framework | Vue 3 (Composition API) |
| Build Tool | Vite 8.0.3 |
| State Management | Pinia 3.0.4 |
| Routing | Vue Router 4.6.4 |
| Node.js | 20.19+ required |
| Linting | ESLint 9 (flat config) + Babel parser + Vue plugin |
| HTTP | Native `fetch` via service modules |
| Dev Tools | vite-plugin-vue-devtools |

---

## Folder Structure

```
smart-ground-ui/
├── src/
│   ├── main.js                    # Entry point — mounts Vue app, registers Pinia + Router
│   ├── App.vue                    # Root component
│   │
│   ├── assets/                    # Global CSS and static assets
│   │   ├── base.css               # CSS resets and root variables
│   │   └── main.css               # App-wide styles, imports base.css
│   │
│   ├── components/                # Reusable, stateless UI components
│   │   ├── Badge.vue
│   │   ├── Breadcrumb.vue
│   │   ├── Button.vue
│   │   ├── DeviceCard.vue         # Displays a single device's info
│   │   ├── SmartBoxCard.vue       # Displays SmartBox status + linked devices
│   │   ├── StatusDot.vue          # Online/offline/pending indicator dot
│   │   ├── TypeChip.vue           # Device type badge/pill
│   │   ├── Icons.vue              # SVG icon definitions (referenced by name)
│   │   ├── Sidebar.vue            # Navigation sidebar
│   │   │
│   │   └── werfer-remote/         # Components specific to the Werfer Remote view
│   │       ├── WerferButton.vue   # Individual trap launcher button (fire / record)
│   │       ├── WerferGrid.vue     # Grid of WerferButtons for a range
│   │       ├── WerferRemoteHeader.vue  # Header: range name, platz status, reserve/release
│   │       ├── RangeSelector.vue  # Dropdown/tab to switch active range
│   │       ├── SoloModeToggle.vue # Toggle between Solo and Pair throw mode
│   │       ├── FlyoutPanel.vue    # Slide-in panel for programme capture & playback
│   │       └── PlayOverlay.vue    # Full-screen overlay shown during programme playback
│   │
│   ├── layouts/
│   │   └── MainLayout.vue         # Master layout shell (sidebar + router-view)
│   │
│   ├── router/
│   │   └── index.js               # Vue Router route definitions
│   │
│   ├── stores/                    # Pinia stores (global state)
│   │   ├── appStore.js            # Global app-level state (loading, errors, etc.)
│   │   ├── smartBoxStore.js       # SmartBox list and per-box state
│   │   ├── deviceStore.js         # Device list and state
│   │   ├── deviceTypeStore.js     # Device type templates; supports mock/api mode toggle
│   │   ├── rangeStore.js          # Range (shooting station) list and state
│   │   └── werferRemoteStore.js   # Full Werfer Remote session state (see section below)
│   │
│   ├── services/                  # API client modules (one per backend resource)
│   │   ├── smartBoxApi.js         # /api/smart-boxes
│   │   ├── deviceApi.js           # /api/devices
│   │   ├── rangeApi.js            # /api/ranges
│   │   ├── deviceTypeApi.js       # /api/device-types
│   │   └── authHeader.js          # Shared HTTP header helpers
│   │
│   ├── mappers/                   # Transform raw API responses into UI models
│   │   ├── SmartBoxMapper.js
│   │   ├── DeviceMapper.js
│   │   ├── DeviceTemplateMapper.js
│   │   ├── RangeMapper.js
│   │   └── index.js               # Re-exports all mappers
│   │
│   ├── models/
│   │   └── SmartBox.js            # Client-side model/class definition
│   │
│   └── constants/
│       ├── deviceTypes.js         # Device type enums and display metadata
│       ├── mockData.js            # Placeholder data for development/testing (mock mode only)
│       └── werfertokens.js        # Design tokens for the Werfer Remote UI (colours, typography, spacing, animation)
│
├── views/                         # Page-level components, one per route
│   ├── RangesView.vue             # /ranges — list of all ranges
│   ├── RangeDetailView.vue        # /ranges/:id — detail & device config for one range
│   ├── RangeListView.vue          # (sub-component used by RangesView)
│   ├── WerferRemoteView.vue       # /ranges/:id/remote — Werfer Remote control surface
│   ├── SmartBoxesView.vue         # /smartboxes — list of all SmartBoxes
│   ├── SmartBoxDetailView.vue     # SmartBox detail (routed from SmartBoxesView)
│   ├── SmartBoxListView.vue       # (sub-component used by SmartBoxesView)
│   ├── DeviceTypesView.vue        # /deviceTypes — device type list & management
│   ├── DeviceTypeView.vue         # Device type detail/edit
│   └── UsersView.vue              # /users — user management (placeholder)
│
├── design_handoff_werfer_remote/  # Static HTML design mockups (reference only, not built)
├── public/                        # Static assets (served as-is)
├── dist/                          # Production build output (git-ignored)
├── .env                           # Local environment variables (not committed)
├── .env.example                   # Template — copy to .env and fill in values
├── vite.config.js                 # Vite bundler config (includes `@` → `src/` alias)
├── eslint.config.js               # ESLint 9 flat config
├── Dockerfile                     # Production Docker image (Nginx)
├── docker-compose.yml             # Compose file for running UI + backend together
├── nginx.conf                     # Nginx config for serving the SPA
└── AGENT_TASKS.md                 # Team task/issue tracker
```

> **Path alias:** `@` resolves to `src/`. Always use `@/` for imports within `src/` — never use relative `../` chains more than one level deep.

---

## Data Flow

```
Backend REST API  (or mock data in VITE_WORK_MODE=mock)
      │
      ▼
  services/          ← Raw fetch calls, returns raw JSON
      │
      ▼
  mappers/           ← Transforms API shape → UI model shape
      │
      ▼
  stores/            ← Pinia state, holds mapped UI models
      │
      ▼
  views/components   ← Read from stores, dispatch store actions
```

Never fetch from the API directly inside a component. Always go through the store, which calls the service, which calls the mapper.

---

## Environment Configuration

### `.env` variables

```
VITE_API_BASE_URL=http://localhost:8080
VITE_WORK_MODE=mock        # 'mock' | 'api'  — controls data source (see Mock Mode below)
```

Copy `.env.example` to `.env` to get started. All API service modules must reference `import.meta.env.VITE_API_BASE_URL` — never hardcode `localhost:8080`.

### Mock Mode (`VITE_WORK_MODE`)

Stores that support mock mode check `import.meta.env.VITE_WORK_MODE` at runtime. When set to `mock`, stores load from `constants/mockData.js` instead of hitting the backend. This allows full UI development without a running backend.

- `VITE_WORK_MODE=mock` — use local mock data (default for new dev environments)
- `VITE_WORK_MODE=api` — use real backend API

The `deviceTypeStore` is a reference implementation of this pattern. When adding mock support to a new store, follow the same `isMockMode()` guard + `loadMockData()` / `loadApiData()` structure.

### CORS

The backend allows requests from `http://localhost:5173` (the Vite dev server default). If you change the dev server port, update `cors.allowed-origins` in the backend's `application.properties`.

---

## Local Development

```bash
npm install
npm run dev          # Dev server at http://localhost:5173
npm run build        # Production SPA output to dist/
npm run lint         # ESLint with auto-fix
npm run lint:check   # ESLint check only (no writes — use in CI)
npm run preview      # Preview the production build locally
```

### Docker (Production)

```bash
docker build -t smartground-ui:latest .
# or via Compose (starts UI + backend together):
docker compose up
```

The Dockerfile builds the SPA and serves it via Nginx. See `nginx.conf` for routing rules (all paths fall back to `index.html` for client-side routing).

---

## State Management (Pinia)

Each domain entity has its own store. Stores are the single source of truth — components never hold their own copies of server data.

| Store | Manages |
|---|---|
| `smartBoxStore` | SmartBox list, per-box state (ONLINE/OFFLINE/CONFIG_PENDING) |
| `deviceStore` | Device list, device assignment state |
| `deviceTypeStore` | Device type templates; supports mock/api mode toggle |
| `rangeStore` | Range list, range lock status |
| `werferRemoteStore` | Full Werfer Remote session: platz reservation, throw mode, programme capture & playback |
| `appStore` | Global UI state: loading flags, error messages, active selections |

**Conventions:**
- Store actions call `services/` functions, then run results through `mappers/` before storing.
- Use `storeToRefs()` in components to maintain reactivity when destructuring store state.
- Do not put derived/computed data into stores — use `computed()` inside components or Pinia `getters` instead.

---

## Werfer Remote Feature (`werferRemoteStore`)

The Werfer Remote is a dedicated control surface at `/ranges/:id/remote` that lets an operator fire individual Werfer (clay trap launchers), capture throw sequences (Abläufe/Programme), and play them back.

**Key state in `werferRemoteStore`:**

| State | Type | Description |
|---|---|---|
| `selectedRangeId` | `string \| null` | Currently active range |
| `platzStatus` | `'frei' \| 'reserviert' \| 'blockiert'` | Reservation status of the shooting station |
| `reservedByMe` | `boolean` | Whether the current session holds the reservation |
| `mode` | `'solo' \| 'pair'` | Throw mode — single Werfer or simultaneous pair |
| `fired` | `Record<id, boolean>` | Transient fire animation flags |
| `recording` | `Record<id, boolean>` | Transient record animation flags |
| `programMode` | `boolean` | Whether the operator is capturing a new programme |
| `ablauf` | `Step[]` | Draft sequence steps being captured |
| `savedPrograms` | `Program[]` | Persisted throw programmes |
| `playProg` | `Step[] \| null` | Steps of the programme currently being played back |
| `playCurrentStep` | `number` | Index into `playProg` |

**Important rules for this feature:**
- A Werfer can only be fired when `isReserved` is true (the current session holds the platz).
- Programme capture (`programMode`) can only start while `isReserved`.
- In `pair` mode, tapping the same Werfer twice cancels the pending selection (`pairPending`).
- Werfer data is currently generated by `generateMockWerfer()` inside the store — this will be replaced by real API data once the backend endpoint exists.
- Design tokens (colours, typography, spacing, animations, breakpoints) live in `constants/werfertokens.js`. Use them instead of raw values in Werfer Remote components.

---

## API Services (`services/`)

One file per backend resource. Each function corresponds to one REST endpoint.

```js
// Example pattern
export async function getSmartBoxes() {
  const res = await fetch(`${import.meta.env.VITE_API_BASE_URL}/api/smart-boxes`, {
    headers: authHeader()
  })
  if (!res.ok) throw new Error(`Failed to fetch SmartBoxes: ${res.status}`)
  return res.json()
}
```

- Always check `res.ok` and throw a descriptive error on failure.
- Return raw parsed JSON — mapping happens in the store or a dedicated mapper.
- `authHeader.js` provides shared header construction (e.g., Content-Type, auth tokens).

---

## Mappers (`mappers/`)

Mappers decouple the API's response shape from the UI's internal model shape. If the backend changes a field name, only the mapper needs to update.

- One mapper file per domain entity.
- Mapper functions are pure: `apiResponse → uiModel`, no side effects.
- All mappers are re-exported from `mappers/index.js`.

---

## Components (`components/`)

Components are designed to be **stateless and reusable** — they receive data via props and emit events upward. They do not call API services or stores directly (unless they are page-level view components in `views/`).

### Shared components

| Component | Props | Purpose |
|---|---|---|
| `SmartBoxCard` | `smartBox` object | Renders SmartBox status, state badge, linked devices |
| `DeviceCard` | `device` object | Renders device info, type chip, health indicator |
| `StatusDot` | `status` string | Colored dot for ONLINE / OFFLINE / CONFIG_PENDING |
| `TypeChip` | `type` string | Pill badge for device type (Werfer, LED, Knopf, Sensor) |
| `Button` | `variant`, `disabled` | Styled button with variants |
| `Badge` | `label`, `variant` | Generic badge/tag element |
| `Sidebar` | — | Navigation links, uses Vue Router |

### Werfer Remote components (`components/werfer-remote/`)

These components are tightly coupled to `werferRemoteStore` and the Werfer Remote domain. Do not use them outside of `WerferRemoteView`.

| Component | Purpose |
|---|---|
| `WerferButton` | Individual launcher button; shows fire/record animation states |
| `WerferGrid` | Lays out all WerferButtons for the selected range |
| `WerferRemoteHeader` | Range name, platz status pill, reserve/release/block actions |
| `RangeSelector` | Allows switching the active range |
| `SoloModeToggle` | Switches between solo and pair throw mode |
| `FlyoutPanel` | Slide-in panel for programme list, capture, and edit |
| `PlayOverlay` | Full-screen playback carousel with step-by-step progression |

---

## Routing (`router/index.js`)

Uses Vue Router 4 with HTML5 history mode. All unmatched routes fall back to `index.html` (handled by Nginx in production, Vite dev server in development).

| Path | Component | Notes |
|---|---|---|
| `/` | — | Redirects to `/ranges` |
| `/ranges` | `RangesView` | |
| `/ranges/:id` | `RangeDetailView` | `id` passed as prop |
| `/ranges/:id/remote` | `WerferRemoteView` | `id` passed as prop |
| `/smartboxes` | `SmartBoxesView` | |
| `/deviceTypes` | `DeviceTypeView` | |
| `/users` | `UsersView` | |

When adding a new route:
1. Add the route definition in `router/index.js`
2. Link to it from `Sidebar.vue` if it should appear in the navigation

---

## Domain Constants

### `constants/deviceTypes.js`

Defines the four device types and their display metadata (labels, icons, signal direction). If a new device type is added to the backend seed data, update this file to keep UI labels consistent.

```
TRAP    (Werfer)   — OUTPUT, fires clay
LED                — OUTPUT, indicator light
BUTTON  (Knopf)   — INPUT, shooter trigger
SENSOR             — INPUT, detection sensor
```

### `constants/werfertokens.js`

Design tokens for the Werfer Remote UI. Always import from here — never hard-code raw values in Werfer Remote components.

| Export | Contents |
|---|---|
| `WERFER_COLORS` | Named palette: `pineGreen`, `parchmentLight`, `orangeAccent`, status colours, etc. |
| `WERFER_TYPOGRAPHY` | Font families (Playfair Display / DM Sans / DM Mono), size & weight presets |
| `WERFER_SPACING` | `xs` → `xxl` spacing scale |
| `WERFER_RADIUS` | Border-radius values keyed by element type |
| `WERFER_BORDERS` | Border width/style presets |
| `WERFER_ANIMATIONS` | Duration + easing for fire, record, flyout, carousel, and status-pulse animations |
| `WERFER_BREAKPOINTS` | `mobile` (375px), `tablet` (768px), `desktop` (1024px) |

---

## Coding Conventions

- **Composition API only** — do not use Options API (`data()`, `methods:`, etc.).
- **`<script setup>` syntax** preferred for Single File Components.
- **`@` path alias** — use `@/` for all imports within `src/`; never chain `../` more than once.
- **No direct API calls in components** — always go through stores.
- **Mapper before store** — raw API data must be transformed before entering Pinia state.
- **Prop drilling max 2 levels** — if data needs to go deeper, lift it to a store.
- **German domain terms** — display labels use German shooting-sport terminology (Platz, Werfer, Ablauf, etc.); JavaScript identifiers use English.
- **Mock data** — `constants/mockData.js` is for development/mock mode only; never reference it in production code paths or inside `VITE_WORK_MODE=api` branches.
- **Design tokens** — Werfer Remote components must use values from `constants/werfertokens.js`, not raw CSS literals.
- **ESLint** — run `npm run lint` before committing; use `npm run lint:check` in CI (no auto-fix). The pipeline will fail on lint errors.

---

## Related Parts of the Monorepo

| Part | Location | Description |
|---|---|---|
| Backend API | `../smart-ground-backend/` | Spring Boot REST API this UI consumes |
| IoT Firmware | `../smart-box/mqtt/` | MicroPython on Raspberry Pi Pico 2W |
| Project Spec | `../ProjektManagement/project.md` | Full domain specification |
| DB Schema | `../ProjektManagement/db-schema.mermaid` | ER diagram including future tables |

---

## Common Agent Tasks

**Adding a new page/view:**
1. Create the view component in `src/views/`
2. Register the route in `router/index.js`
3. Add a navigation link in `Sidebar.vue` if it should appear in nav
4. Connect it to the appropriate Pinia store for data

**Adding a new API call:**
1. Add the function to the relevant file in `services/`
2. Add a mapper if the response shape needs transforming (`mappers/`)
3. Add a store action that calls the service + mapper and updates state
4. Call the store action from the view component

**Adding a new store with mock/api mode support:**
1. Check `import.meta.env.VITE_WORK_MODE` via an `isMockMode()` helper
2. Implement `loadMockData()` using `constants/mockData.js`
3. Implement `loadApiData()` calling the relevant `services/` function
4. Call the appropriate branch from `initialize()`
5. Use `deviceTypeStore.js` as the reference implementation

**Adding a new device type:**
1. Add backend seed data via a Liquibase migration (in the backend repo)
2. Update `constants/deviceTypes.js` with the new type's display metadata
3. Update `TypeChip.vue` or `DeviceCard.vue` if the new type needs distinct visual treatment

**Adding a new Werfer Remote component:**
1. Create the `.vue` file in `src/components/werfer-remote/`
2. Import design tokens from `constants/werfertokens.js` — do not use raw values
3. Read state from `werferRemoteStore` via `storeToRefs()`; do not accept store state as props
4. Keep the component presentation-only; put business logic in the store
