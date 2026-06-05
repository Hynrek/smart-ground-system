# smart-ground-ui — Vue 3 Development Guide

## Project Overview

The Smart Ground UI is a Vue 3 single-page application that provides the web interface for managing shooting-range devices. It communicates with the backend REST API, displays real-time device status, and allows operators to configure ranges and devices.

---

## Stack & Versions

- **Vue**: 3 (Composition API)
- **Build**: Vite 8.0.3
- **State**: Pinia 3.0.4
- **Router**: Vue Router 4.6.4
- **Node**: 20.19+ (use nvm for version management)
- **Package Manager**: npm
- **Testing**: Vitest + @vue/test-utils
- **Linting**: ESLint 9 (flat config)
- **HTTP**: Native `fetch` via service modules

---

## Project Structure

```
smart-ground-ui/
├── src/
│   ├── components/                           # Reusable Vue components
│   │   ├── Badge.vue
│   │   ├── Breadcrumb.vue
│   │   ├── BracketVisualizer.vue             # Tournament bracket display
│   │   ├── Button.vue
│   │   ├── DeviceCard.vue
│   │   ├── FormField.vue
│   │   ├── Icons.vue
│   │   ├── PaletteSwitcher.vue               # Theme switcher
│   │   ├── Sidebar.vue
│   │   ├── SmartBoxCard.vue
│   │   ├── StatusBadge.vue
│   │   ├── StatusDot.vue
│   │   ├── TypeChip.vue
│   │   ├── shooter/
│   │   │   └── ScoreTable.vue                # Display competition scores
│   │   ├── shooter-remote/
│   │   │   ├── LiveScoreboard.vue            # Real-time score display for shooter
│   │   │   ├── PlayerHandoverScreen.vue      # Screen between player shots
│   │   │   ├── ShooterFlyoutPanel.vue        # Shooting session panel
│   │   │   └── ShooterPlayOverlay.vue        # Overlay for active shooter
│   │   └── __tests__/                        # Component tests
│   ├── views/                                # Full-page views
│   │   ├── LoginView.vue                     # Authentication
│   │   ├── ProfileView.vue
│   │   ├── UsersView.vue
│   │   ├── SmartBoxesView.vue
│   │   ├── SmartBoxListView.vue
│   │   ├── SmartBoxDetailView.vue
│   │   ├── RangesView.vue
│   │   ├── RangeListView.vue
│   │   ├── RangeDetailView.vue
│   │   ├── DeviceTypeGroupsView.vue
│   │   ├── DeviceTypesView.vue
│   │   ├── DeviceTypeView.vue
│   │   ├── FirmwareConfigsView.vue
│   │   ├── CompetitionManagementView.vue
│   │   ├── CompetitionBracketView.vue
│   │   ├── PlayerSetupView.vue
│   │   ├── ProgrammeAdminView.vue
│   │   ├── competition/                      # Competition-specific views
│   │   │   ├── CareerStatsView.vue
│   │   │   ├── CompetitionLeaderboardView.vue
│   │   │   ├── CompetitionLiveView.vue
│   │   │   ├── CompetitionSetupView.vue
│   │   │   └── CompetitionTemplateListView.vue
│   │   └── shooter/                          # Shooter interface views
│   │       ├── ShooterHomeView.vue
│   │       ├── ShooterRemoteView.vue         # Remote shooting session
│   │       ├── ShooterPlayPage.vue
│   │       ├── ShooterProfileView.vue
│   │       ├── PlayerSetupView.vue
│   │       ├── ShooterRangeSelectView.vue
│   │       └── ProgramManagementView.vue
│   ├── layouts/                              # Layout wrappers
│   │   ├── MainLayout.vue                    # Primary admin/management layout
│   │   └── ShooterLayout.vue                 # Shooter-specific interface
│   ├── assets/                               # Global CSS and static assets
│   │   ├── base.css                          # CSS resets and root variables
│   │   └── main.css                          # App-wide styles, imports base.css
│   ├── mappers/                              # Transform raw API responses into UI models
│   │   ├── SmartBoxMapper.js
│   │   ├── DeviceMapper.js
│   │   ├── DeviceTemplateMapper.js
│   │   ├── RangeMapper.js
│   │   └── index.js                          # Re-exports all mappers
│   ├── models/
│   │   └── SmartBox.js                       # Client-side model/class definition
│   ├── stores/                               # Pinia state management (15 stores)
│   │   ├── appStore.js                       # App-level state (theme, etc)
│   │   ├── authStore.js                      # JWT token, user roles, auth state
│   │   ├── bracketStore.js                   # Tournament bracket state
│   │   ├── competitionStore.js               # Competition/session management
│   │   ├── deviceStore.js                    # Physical device registry
│   │   ├── deviceTypeStore.js                # Device type catalog
│   │   ├── deviceTypeGroupStore.js           # Device type groupings
│   │   ├── guestStore.js                     # Guest player management
│   │   ├── playSessionStore.js               # Active play session state
│   │   ├── programStore.js                   # Shooting programs/courses
│   │   ├── rangeStore.js                     # Shooting range data
│   │   ├── reservationStore.js               # Range reservations
│   │   ├── shooterRemoteStore.js             # Shooter remote interface state
│   │   ├── smartBoxStore.js                  # SmartBox device registry
│   │   ├── userStore.js                      # User account data
│   │   └── __tests__/                        # Store tests
│   ├── services/                             # API client layer
│   │   ├── apiClient.js                      # Core fetch wrapper with auth
│   │   ├── authApi.js                        # Auth endpoints (login, user creation)
│   │   ├── authHeader.js                     # JWT header builder
│   │   ├── bracketService.js                 # Bracket calculation logic
│   │   ├── competitionService.js             # Competition-specific APIs
│   │   ├── deviceApi.js                      # Device CRUD
│   │   ├── deviceTypeApi.js                  # Device type CRUD
│   │   ├── deviceTypeGroupApi.js             # Device type group CRUD
│   │   ├── eventsApi.js                      # Placeholder for STOMP WebSocket client (not yet implemented)
│   │   ├── rangeApi.js                       # Range CRUD
│   │   ├── reservationApi.js                 # Reservation management
│   │   ├── smartBoxApi.js                    # SmartBox CRUD and commands
│   │   ├── userApi.js                        # User management
│   │   └── __tests__/                        # Service tests
│   ├── composables/                          # Vue 3 composition utilities
│   │   ├── useDeviceLoader.js                # Auto-load devices for known boxes
│   │   └── useDeviceTypeFilter.js            # Device type filtering logic
│   ├── constants/                            # Application constants
│   │   ├── deviceTypes.js                    # Device type definitions
│   │   ├── playEnums.js                      # Enum constants for competition state
│   │   └── werfertokens.js                   # (Werfer tokens configuration)
│   ├── router/                               # Vue Router config
│   │   └── index.js
│   ├── App.vue                               # Root component
│   ├── main.js                               # Entry point
│   └── style.css                             # Global styles
├── public/                                   # Static assets
├── vite.config.js                            # Vite configuration (includes `@` → `src/` alias)
├── vitest.config.js                          # Vitest configuration
├── eslint.config.js                          # ESLint flat config
├── Dockerfile                                # Production Docker image (Nginx)
├── docker-compose.yml                        # Compose file for running UI + backend together
├── nginx.conf                                # Nginx config for serving the SPA
├── package.json
└── .env.example                              # Environment variables template
```

---

## Data Flow

```
Backend REST API  (or mock data when VITE_WORK_MODE=mock)
      │
      ▼
  services/          ← Raw fetch calls, returns raw JSON
      │
      ▼
  mappers/           ← Transforms API shape → UI model shape (pure functions)
      │
      ▼
  stores/            ← Pinia state, holds mapped UI models
      │
      ▼
  views/components   ← Read from stores, dispatch store actions
```

Never fetch from the API directly inside a component. Always go through the store → service → mapper chain.

---

## Test Credentials

Use these accounts when running the app locally or in tests:

| Role | Username | Password |
|---|---|---|
| Admin | `admin@smartground.local` | `admin123` |
| User (Shooter) | `user@smartground.local` | `user` |

---

## Running Locally

### Setup
```bash
# Install dependencies
npm install

# Create .env.local from template
cp .env.example .env.local
# Edit .env.local to set VITE_API_BASE_URL=http://localhost:8080/api
```

### Development Server
```bash
npm run dev
# Runs on http://localhost:5173 with hot module replacement
```

### Linting
```bash
npm run lint        # Auto-fix issues
npm run lint:check  # Check only (no writes — use in CI)
```

### Build for Production
```bash
npm run build
# Creates optimized bundle in dist/
```

### Preview Production Build
```bash
npm run preview
# Serves dist/ locally for testing
```

### Docker (Production)
```bash
docker build -t smartground-ui:latest .
# or via Compose (starts UI + backend together):
docker compose up
```
The Dockerfile builds the SPA and serves it via Nginx. All unmatched routes fall back to `index.html` (see `nginx.conf`).

---

## Testing

### Run All Tests
```bash
npm run test
```

### Run Tests in Watch Mode
```bash
npm run test:watch
```

### Generate Coverage Report
```bash
npm run test:coverage
# Report: coverage/
```

### Test Single File
```bash
npm run test src/components/__tests__/SmartBoxList.test.js
```

### Test Structure

**Component Tests**: Mount Vue components, interact, verify output
- Path: `src/components/__tests__/`
- Use `mount()` from `@vue/test-utils`
- Mock Pinia stores and API calls
- Test user interactions (clicks, form input)

Example:
```javascript
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import BracketVisualizer from '../BracketVisualizer.vue'

describe('BracketVisualizer', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders bracket rounds', async () => {
    const wrapper = mount(BracketVisualizer, {
      props: {
        competitionId: 'comp-1',
        seedingStrategy: 'BY_REGISTRATION_ORDER',
      },
    })
    
    // Component should render rounds
    await vi.waitFor(() => {
      expect(wrapper.find('.bracket-round').exists()).toBe(true)
    })
  })

  it('emits match-winner when user clicks winner', async () => {
    const wrapper = mount(BracketVisualizer, {
      props: { competitionId: 'comp-1' },
    })

    await wrapper.find('[data-testid="match-1-winner"]').trigger('click')
    expect(wrapper.emitted('match-winner')).toBeTruthy()
  })
})
```

**Store Tests**: Pinia state, computed properties, and actions
- Path: `src/stores/__tests__/`
- Create fresh Pinia instance per test with `setActivePinia(createPinia())`
- Mock API service calls with `vi.mock()`
- Test state mutations, async actions, and side effects

Example:
```javascript
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useCompetitionStore } from '../competitionStore'
import * as competitionService from '@/services/competitionService'

vi.mock('@/services/competitionService')

describe('useCompetitionStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetches sessions and updates state', async () => {
    const mockSessions = [
      { id: 'sess-1', status: 'ACTIVE', format: 'LEAGUE' },
    ]
    vi.mocked(competitionService.fetchSessions).mockResolvedValue(mockSessions)

    const store = useCompetitionStore()
    await store.fetchSessions()

    expect(store.sessions).toEqual(mockSessions)
    expect(store.loading).toBe(false)
    expect(store.error).toBe(null)
  })

  it('handles API errors gracefully', async () => {
    const error = new Error('Network failed')
    vi.mocked(competitionService.fetchSessions).mockRejectedValue(error)

    const store = useCompetitionStore()
    await store.fetchSessions()

    expect(store.error).toBe('Network failed')
    expect(store.sessions).toEqual([])
  })

  it('computes activeSession correctly', () => {
    const store = useCompetitionStore()
    store.sessions = [
      { id: 'sess-1', status: 'COMPLETED' },
      { id: 'sess-2', status: 'ACTIVE' },
    ]

    expect(store.activeSession?.id).toBe('sess-2')
  })
})
```

**Service Tests**: Business logic and API layer behavior
- Path: `src/services/__tests__/`
- Test bracket generation, sorting, and calculation functions
- Mock `fetch` responses for API service tests
- Test error handling and data transformation

Example:
```javascript
import { describe, it, expect } from 'vitest'
import { initBracket } from '../bracketService'

describe('bracketService', () => {
  it('generates single-elimination bracket with correct structure', () => {
    const players = [
      { id: 'p1', name: 'Alice' },
      { id: 'p2', name: 'Bob' },
      { id: 'p3', name: 'Charlie' },
      { id: 'p4', name: 'Diana' },
    ]

    const bracket = initBracket(players, 'SINGLE_ELIMINATION')

    expect(bracket.rounds).toHaveLength(2)  // Round 1 (semifinals) + Final
    expect(bracket.rounds[0].matches).toHaveLength(2)  // 2 matches in round 1
    expect(bracket.rounds[1].matches).toHaveLength(1)  // 1 match in final
  })

  it('seeds players by registration order', () => {
    const players = [
      { id: 'p1', name: 'Alice', registeredAt: '2024-01-01' },
      { id: 'p2', name: 'Bob', registeredAt: '2024-01-02' },
    ]

    const bracket = initBracket(players, 'BY_REGISTRATION_ORDER')
    
    expect(bracket.rounds[0].matches[0].player1.id).toBe('p1')
    expect(bracket.rounds[0].matches[0].player2.id).toBe('p2')
  })
})

---

## Code Review Checklist (Frontend-Specific)

Before merging any changes, ensure all items are checked:

### Functional Correctness
- [ ] All tests pass: `npm run test`
- [ ] Feature matches approved design (if applicable)
- [ ] No hardcoded API URLs (use `VITE_API_BASE_URL`)
- [ ] Error states handled (loading, error, empty)
- [ ] Unsubscribe from WebSocket (STOMP) topics on component unmount (call `subscription.unsubscribe()`)
- [ ] Auth token is injected for all protected API calls

### Code Quality
- [ ] No ESLint warnings: `npm run lint`
- [ ] Component uses `<script setup>` and Composition API
- [ ] State in Pinia stores (not component-local for shared data)
- [ ] API calls in service layer (not directly in components)
- [ ] No hardcoded values; use constants or store state
- [ ] No dead code or commented-out logic
- [ ] Minimal new dependencies (justify any added in commit message)
- [ ] DRY principle: no unnecessary duplication
- [ ] Inline comments explain *why*, not *what*

### Architecture & State Management
- [ ] New stores follow the `defineStore` + Composition API pattern
- [ ] Composables use `use*` naming and are exported from `src/composables/`
- [ ] Service functions wrap API calls and business logic
- [ ] Store actions handle async operations with try/catch
- [ ] No direct store mutations from components (use actions)
- [ ] Leaderboard/bracket stores compute rankings correctly

### Authentication & Authorization
- [ ] All protected routes check `authStore.isAuthenticated()`
- [ ] Role-based access enforced (ADMIN, SHOOTER, GROUND_OWNER)
- [ ] JWT token refreshed or cleared on 401 errors
- [ ] Sensitive operations confirm user action (e.g., delete)

### Testing
- [ ] Unit test coverage ≥80% for new code
- [ ] Store tests mock API services with `vi.mock()`
- [ ] Component tests mount with fresh Pinia instance
- [ ] Tests are independent (no shared mutable state)
- [ ] Test names clearly describe what they verify

### Accessibility & Responsive Design
- [ ] Responsive layout: test at 320px, 768px, 1920px
- [ ] WCAG AA compliant: keyboard nav, ARIA labels, color contrast ≥4.5:1
- [ ] Form labels linked to inputs with `for` attribute
- [ ] Focus visible (`:focus-visible` styles)
- [ ] No keyboard traps; tab order logical
- [ ] Touch targets ≥48px for mobile interfaces

### Documentation & Language
- [ ] Inline comments are in English
- [ ] JSDoc comments for exported functions/components
- [ ] New routes documented (path, layout, role requirements)
- [ ] Breaking changes noted in commit message

### Build & Performance
- [ ] Build succeeds: `npm run build` with no warnings
- [ ] No unused imports or variables
- [ ] Bundle size reasonable (check output of `npm run build`)
- [ ] Lazy-load routes for code splitting where applicable

---

## Conventions & Patterns

### Naming
- **Components**: PascalCase (e.g., `SmartBoxCard.vue`, `BracketVisualizer.vue`)
- **Subdirectories**: kebab-case (e.g., `shooter-remote/`, `device-types/`)
- **Variables/functions**: camelCase (English identifiers even when domain terms are German)
- **Stores**: `xxxStore.js` (e.g., `authStore.js`, `competitionStore.js`)
- **Services/APIs**: `xxxApi.js` for backend endpoints, `xxxService.js` for local logic (e.g., `competitionService.js` for bracket calculations)
- **Routes**: kebab-case paths (e.g., `/smart-boxes`, `/competition-live`)
- **Composables**: `use*` prefix (e.g., `useDeviceLoader`, `useDeviceTypeFilter`)
- **Constants**: `SCREAMING_SNAKE_CASE` for enums (e.g., `SessionStatus.ACTIVE`)

### General Rules
- **`<script setup>` syntax** — always use for SFCs; do not use Options API (`data()`, `methods:`, etc.)
- **`@` path alias** — use `@/` for all imports within `src/`; never chain `../` more than one level deep
- **No direct API calls in components** — always go through the store → service → mapper chain
- **`storeToRefs()`** — use when destructuring store state in components to preserve reactivity
- **Prop drilling max 2 levels** — if data needs to go deeper, lift it to a store
- **German display labels** — use German shooting-sport terminology in UI text (Platz, Werfer, Ablauf, etc.); JavaScript identifiers stay English
- **Design tokens** — import color/spacing/animation values from `constants/werfertokens.js` in any Werfer-related component; do not hard-code raw values
- **Remove unused code eagerly** — delete unused methods, components, stores, services, imports, and files rather than leaving them. Dead code is actively harmful: it misleads future readers, inflates bundle size, and creates false confidence that something is needed. If it isn't called and isn't planned, delete it.

### Mappers
Mappers (`src/mappers/`) decouple the API response shape from the UI model shape. If the backend renames a field, only the mapper needs updating.
- One file per domain entity (e.g., `SmartBoxMapper.js`)
- Mapper functions are pure: `apiResponse → uiModel`, no side effects
- All mappers are re-exported from `mappers/index.js`
- Stores call the service, then pipe the result through the mapper before setting state

### Component Structure (Composition API)
```vue
<template>
  <div class="bracket-visualizer">
    <div v-for="round in bracket.rounds" :key="round.id">
      <!-- Render bracket round -->
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useBracketStore } from '@/stores/bracketStore'
import { initBracket } from '@/services/bracketService'

const props = defineProps({
  competitionId: {
    type: String,
    required: true,
  },
  seedingStrategy: {
    type: String,
    default: 'BY_REGISTRATION_ORDER',
  },
})

const bracketStore = useBracketStore()
const bracket = computed(() => bracketStore.bracket)
const loading = computed(() => bracketStore.loading)

onMounted(async () => {
  await bracketStore.initBracket(props.competitionId, props.seedingStrategy)
})

const recordWinner = (matchId, winnerId) => {
  bracketStore.recordMatch(matchId, winnerId)
}
</script>

<style scoped>
.bracket-visualizer {
  display: flex;
  gap: 2rem;
  overflow-x: auto;
}
</style>
```

### Store Pattern
All stores use the Composition API factory pattern with clear separation of state, computed properties, and actions:

```javascript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as competitionService from '@/services/competitionService'

export const useCompetitionStore = defineStore('competition', () => {
  // ── State ──
  const sessions = ref([])
  const selectedSession = ref(null)
  const loading = ref(false)
  const error = ref(null)

  // ── Computed ──
  const activeSession = computed(() => 
    sessions.value.find(s => s.status === 'ACTIVE')
  )

  // ── Actions ──
  const fetchSessions = async () => {
    loading.value = true
    error.value = null
    try {
      sessions.value = await competitionService.fetchSessions()
    } catch (err) {
      error.value = err.message
    } finally {
      loading.value = false
    }
  }

  const startSession = async (templateId, rangeId) => {
    loading.value = true
    try {
      const newSession = await competitionService.startSession(templateId, rangeId)
      sessions.value.push(newSession)
      selectedSession.value = newSession
    } finally {
      loading.value = false
    }
  }

  // ── Return public interface ──
  return {
    sessions,
    selectedSession,
    loading,
    error,
    activeSession,
    fetchSessions,
    startSession,
  }
})
```

### Service Pattern
Services are thin wrappers around API calls or local business logic:

```javascript
// src/services/bracketService.js
export const initBracket = (players, strategy) => {
  // Local bracket generation logic
  const sorted = sortPlayersByStrategy(players, strategy)
  const rounds = generateRounds(sorted)
  return { rounds, seedingStrategy: strategy }
}

export const recordMatch = (bracket, matchId, winnerId) => {
  // Update bracket state locally or via API
  // Return updated bracket
}
```

### Core Pinia Stores (15 stores in use)

The Smart Ground UI uses a comprehensive store structure organized by feature domain. Each store follows the Composition API pattern with `defineStore`.

#### 1. **authStore** — Authentication & Authorization
Manages JWT token lifecycle, role-based access control, and user identity.

**State:**
- `token` — JWT stored in localStorage with key `sg_token`
- `isLoading`, `error` — Request state

**Computed:**
- `jwtPayload` — Decoded JWT (claims: sub, role, etc.)
- `role` — User role (ADMIN, SHOOTER, GROUND_OWNER)
- `userName` — Current user identifier

**Actions:**
- `login(username, password)` — Authenticate and store token
- `logout()` — Clear token and localStorage
- `createUser(username, password, role)` — Create new user account
- `isAuthenticated()` — Boolean role check
- `isShooter()` — Role is SHOOTER
- `isAdminOrOwner()` — Role is ADMIN or GROUND_OWNER

#### 2. **smartBoxStore** — SmartBox Device Registry
Manages Pico 2W / ESP32 device discovery and status.

**State:**
- `smartboxes` — Array of SmartBox objects (id, mac, alias, status, firmware_version, etc.)
- `selectedBox` — Currently selected box for detail view
- `loading`, `error` — Request state

**Actions:**
- `fetchSmartBoxes()` — Reload all boxes with MQTT status
- `updateSmartBox(id, data)` — Edit alias or trigger config push
- `pushConfig(boxId)` — Send device config via MQTT

#### 3. **deviceStore** — Device Registry
Physical devices (Werfer, LED, Sensor) registered on boxes.

**State:**
- `devices` — Map of box_id → device array
- `selectedDevice` — Currently selected device
- `loading`, `error` — Per-box loading state

**Actions:**
- `loadDevicesForBox(boxId)` — Fetch devices for a specific box
- `createDevice(boxId, deviceData)` — Register new device
- `updateDevice(id, data)` — Edit device config
- `deleteDevice(id)` — Remove device

#### 4. **deviceTypeStore** — Device Type Catalog
Reusable device definitions (models of Werfers, LED types, etc.).

**State:**
- `deviceTypes` — Array of available device types with signal duration
- `loading`, `error` — Request state

**Actions:**
- `fetchDeviceTypes()` — Load all types
- `createDeviceType(data)` — Define new type
- `updateDeviceType(id, data)` — Edit type

#### 5. **deviceTypeGroupStore** — Device Grouping
Logical groups for organizing device types (e.g., "Wurfmaschine", "LED").

**State:**
- `groups` — Array of group objects (id, name, description)
- `loading`, `error` — Request state

**Actions:**
- `fetchGroups()` — Load all groups
- `createGroup(data)` — Create new group
- `updateGroup(id, data)` — Edit group

#### 6. **rangeStore** — Shooting Range Management
Shooting lanes and range-level configuration.

**State:**
- `ranges` — Array of range objects (id, name, description, locked, devices)
- `selectedRange` — Current range context
- `loading`, `error` — Request state

**Actions:**
- `fetchRanges()` — Load all ranges
- `createRange(data)` — Create new shooting lane
- `updateRange(id, data)` — Edit range
- `lockRange(id)` — Prevent reconfiguration (used during active sessions)

#### 7. **competitionStore** — Competition/Session Management
Session templates, live competitions, and bracket state.

**State:**
- `templates` — Array of SessionTemplate (LEAGUE, BRACKET, KNOCKOUT)
- `liveSessions` — Array of active sessions with bracket/league state
- `selectedSession` — Current competition context
- `loading`, `error` — Request state

**Actions:**
- `fetchTemplates()` — Load session templates
- `createTemplate(data)` — Save new competition blueprint
- `startSession(templateId, rangeId)` — Initiate a competition
- `updateResults(sessionId, playerResults)` — Record scores
- `fetchLeaderboard(sessionId)` — Load ranked players

#### 8. **bracketStore** — Tournament Bracket State
Bracket visualization and seeding logic (single/double elimination, round-robin).

**State:**
- `bracket` — Bracket tree structure (rounds, matches, participants)
- `seedingStrategy` — BY_REGISTRATION_ORDER, BY_SCORE_RANKING, BY_TIEBREAKER
- `loading`, `error` — Request state

**Actions:**
- `initBracket(competitionId, seedingStrategy)` — Generate bracket from players
- `recordMatch(matchId, winnerId, score)` — Update match result
- `reseed()` — Regenerate bracket with new strategy

#### 9. **userStore** — User Account & Profile
Manages user accounts and profile data.

**State:**
- `users` — Array of user objects (id, username, role, email, created_at)
- `currentProfile` — Logged-in user's profile
- `loading`, `error` — Request state

**Actions:**
- `fetchUsers()` — Admin: load all users
- `fetchProfile()` — Load current user details
- `updateProfile(data)` — Edit profile
- `deleteUser(id)` — Admin: remove user

#### 10. **playSessionStore** — Active Play Session
Real-time state during an ongoing competition session.

**State:**
- `sessionId` — Active session UUID
- `currentPhase` — SETUP, ACTIVE, COMPLETED, CANCELLED
- `currentGroup` — Active group/round
- `currentPlayer` — Shooter currently on range
- `scores` — Player results with timestamps
- `loading`, `error` — Request state

**Actions:**
- `loadSession(sessionId)` — Fetch full session state
- `advancePhase()` — Move to next competition phase
- `recordPlayerResult(playerId, score, accuracy)` — Log shot results
- `finishSession()` — Archive and publish results

#### 11. **shooterRemoteStore** — Shooter Remote Interface
Dedicated state for the ShooterLayout and remote shooting experience.

**State:**
- `isRemote` — Boolean: shooter is in remote/kiosk mode
- `currentShooter` — Shooter identity (USER or GUEST)
- `nextUp` — Queue of upcoming shooters
- `shooterDisplay` — Display state (score, timer, instructions)
- `connectionStatus` — WebSocket (STOMP) connection status (connected/disconnected) — not yet wired

**Actions:**
- `startRemote(shooterId)` — Activate remote interface
- `endRemote()` — Exit remote mode
- `registerShot(score)` — Log shot from remote
- `subscribeToEvents()` — Connect to STOMP WebSocket for live updates (not yet implemented)

#### 12. **guestStore** — Guest Player Management
Handles temporary players (non-registered) in competitions.

**State:**
- `guests` — Array of guest player objects (displayName, sessionId, etc.)
- `loading`, `error` — Request state

**Actions:**
- `addGuest(displayName, sessionId)` — Register temporary player
- `removeGuest(guestId)` — Disqualify guest
- `renameGuest(guestId, newName)` — Edit display name

#### 13. **programStore** — Shooting Programs/Courses
Defines shooting courses/programs (sequences of targets, distances, etc.).

**State:**
- `programs` — Array of program definitions
- `selectedProgram` — Current program in use
- `loading`, `error` — Request state

**Actions:**
- `fetchPrograms()` — Load all programs
- `createProgram(data)` — Define new shooting course
- `updateProgram(id, data)` — Edit program

#### 14. **reservationStore** — Range Reservations
Booking and scheduling for ranges.

**State:**
- `reservations` — Array of booking objects (rangeId, userId, startTime, endTime)
- `loading`, `error` — Request state

**Actions:**
- `fetchReservations(rangeId)` — Load bookings for a range
- `createReservation(data)` — Book a range
- `cancelReservation(id)` — Cancel booking

#### 15. **appStore** — Application State
Global UI and app-level settings.

**State:**
- `theme` — light | dark
- `sidebarOpen` — Boolean
- `notifications` — Toast/alert queue
- `locale` — i18n language (if applicable)

**Actions:**
- `toggleTheme()` — Switch theme
- `toggleSidebar()` — Collapse/expand sidebar
- `showNotification(message, type)` — Queue toast message

### Vue 3 Composables

The project includes reusable composition functions for common patterns:

#### useDeviceLoader
Auto-loads all devices for every known SmartBox. Watches `smartBoxStore.smartboxes` and triggers `deviceStore.loadDevicesForBox()` for each new box.

```javascript
import { useDeviceLoader } from '@/composables/useDeviceLoader'

export default {
  setup() {
    useDeviceLoader()  // Automatically manages device loading
    // ... rest of component
  }
}
```

#### useDeviceTypeFilter
Filters device types by group or other criteria. Useful in device creation forms to show only relevant type options.

```javascript
import { useDeviceTypeFilter } from '@/composables/useDeviceTypeFilter'

const { filteredTypes } = useDeviceTypeFilter(groupId)
```

---

## Authentication Flow

The Smart Ground UI implements **JWT-based authentication** with role-based access control.

### Login Flow
1. User enters username/password in `LoginView.vue`
2. `authStore.login()` calls `authApi.login()`
3. Backend returns JWT in response
4. JWT is stored in localStorage with key `sg_token`
5. All subsequent API requests include `Authorization: Bearer {token}` header (added by `authHeader.js`)
6. Router guards check `authStore.isAuthenticated()` to prevent unauthorized access

### JWT Structure
The JWT payload contains:
- `sub` or `username` — User identity
- `role` — ADMIN, SHOOTER, or GROUND_OWNER
- Standard claims: `iat` (issued at), `exp` (expiry)

Example decoded payload:
```json
{
  "sub": "shooter1",
  "role": "SHOOTER",
  "iat": 1684000000,
  "exp": 1684086400
}
```

### Role-Based Access

Three roles control feature visibility:

| Role | Capabilities |
|---|---|
| **ADMIN** | Full backend access: user management, ranges, devices, competition setup, firmware configs |
| **GROUND_OWNER** | Range management, competition setup, but no user deletion |
| **SHOOTER** | View own results, access shooter remote interface, log scores, view leaderboards |

Components check roles via:
```javascript
const { isAdminOrOwner, isShooter } = useAuthStore()

if (isAdminOrOwner()) {
  // Show admin-only controls
}
```

### Header Injection
All API requests automatically include the JWT via `authHeader.js`:
```javascript
// Authorization: Bearer eyJhbGc...
const headers = {
  'Content-Type': 'application/json',
  'Authorization': `Bearer ${token.value}`,
  ...options.headers
}
```

### Logout
`authStore.logout()` clears the token from localStorage and resets the authentication state. User is redirected to login.

---

## Competition & Session Management

The Smart Ground UI provides a full competition management suite for organizing shooting competitions.

### Competition Lifecycle

**1. Template Management** (`CompetitionTemplateListView`)
- Browse existing competition templates (blueprints)
- Create new templates with:
  - Format: LEAGUE (round-robin), BRACKET (single/double elimination), KNOCKOUT
  - Default players (optional pre-registered shooters)
  - Program IDs (linked shooting courses)
  - Bracket configuration (max groups, tiebreaker rule)
  - Publish results flag

**2. Session Setup** (`CompetitionSetupView`)
- Select a template and range
- Invite players (registered users or guests)
- Configure group assignments
- Choose seeding strategy (registration order, score ranking, or custom tiebreaker)

**3. Live Competition** (`CompetitionLiveView`)
- Monitor bracket/league state in real-time
- Watch player handoffs via `PlayerHandoverScreen`
- See live scores updated via `LiveScoreboard`
- View current shooter via `ShooterPlayOverlay`

**4. Results & Leaderboard** (`CompetitionLeaderboardView`)
- Display ranked players by score, accuracy, wins
- Export results
- View match history

### Bracket Visualization
The `BracketVisualizer` component renders tournament brackets with:
- Match nodes showing player names and scores
- Visual feedback for completed matches
- Advancement flow for multi-round tournaments
- Color-coded winner/loser identification

Example bracket structure (managed by `bracketStore`):
```json
{
  "rounds": [
    {
      "roundNumber": 1,
      "matches": [
        {
          "id": "match_1_1",
          "player1": { "id": "user1", "name": "Alice", "score": 95 },
          "player2": { "id": "user2", "name": "Bob", "score": 87 },
          "winner": "user1",
          "status": "COMPLETED"
        }
      ]
    }
  ]
}
```

### Leaderboard Calculation
The `leaderboardStore` ranks players using:
1. **Total Score** — Sum of all shots
2. **Average Score** — Mean shot value
3. **Accuracy** — Percentage of hit targets
4. **Win Ratio** — Bracket matches won (if applicable)
5. **Custom Tiebreaker** — Last-shot head-to-head or other rules

Leaderboard will update via STOMP WebSocket pushes from the backend — not yet wired (see Real-Time Events section).

---

## Shooter Remote Interface

The `ShooterLayout` and associated components provide a dedicated kiosk/remote interface for active shooters during competitions.

### Shooter Views
- **ShooterHomeView** — Welcome screen, session picker
- **ShooterRangeSelectView** — Choose which range/lane to shoot
- **ShooterPlayPage** — Main shooting interface
- **ShooterRemoteView** — Full-screen remote mode
- **ShooterProfileView** — Personal stats and history
- **PlayerSetupView** — Create guest shooter profile

### Key Shooter Components
- **PlayerHandoverScreen** — Displayed between shooters; shows "Next up: [name]"
- **LiveScoreboard** — Real-time score display during session
- **ShooterPlayOverlay** — Active shooter interface with shot logging
- **ShooterFlyoutPanel** — Side panel with instructions and quick actions

### Remote Session State
Managed by `shooterRemoteStore`:
- Track current shooter identity (USER or GUEST)
- Queue of upcoming shooters
- Display mode (score, timer, next-player notification)
- WebSocket (STOMP) connection status (connected/reconnecting/disconnected) — not yet wired

### Shot Logging
When a shooter fires in `ShooterRemoteView`:
1. Tap "Fire" or voice command triggers shot
2. Optional manual score entry or automatic sensor result
3. `shooterRemoteStore.registerShot(score)` posts result
4. `playSessionStore` is updated with new score
5. Leaderboard will refresh via STOMP WebSocket push (not yet wired)
6. UI advances to next shooter or end-of-round screen

---

### Service Layer (API Calls)

All API communication goes through a service layer in `src/services/`. Each service module wraps `apiClient` for a specific domain:

| Service | Purpose | Endpoints |
|---|---|---|
| `authApi.js` | User authentication & creation | POST /auth/login, POST /auth/users |
| `smartBoxApi.js` | SmartBox discovery & commands | GET /smartboxes, POST /smartboxes/{id}/command |
| `deviceApi.js` | Device CRUD | GET /devices, POST /devices/{id}, PUT /devices/{id} |
| `deviceTypeApi.js` | Device type catalog | GET /device-types, POST /device-types |
| `deviceTypeGroupApi.js` | Device grouping | GET /device-type-groups, POST /device-type-groups |
| `rangeApi.js` | Range management | GET /ranges, POST /ranges, PUT /ranges/{id} |
| `competitionService.js` | Competition CRUD | GET /sessions, POST /sessions, PUT /sessions/{id} |
| `bracketService.js` | Bracket logic & seeding | (calculates locally or calls backend) |
| `userApi.js` | User management | GET /users, POST /users, DELETE /users/{id} |
| `eventsApi.js` | Placeholder for future STOMP WebSocket client — **not yet implemented, not yet needed** | — |
| `reservationApi.js` | Range reservations | GET /reservations, POST /reservations |

Example service:
```javascript
// src/services/smartBoxApi.js
import { apiClient } from './apiClient.js'

export const list = async () => apiClient.get('/smartboxes')

export const getById = async (id) => apiClient.get(`/smartboxes/${id}`)

export const update = async (id, data) => apiClient.put(`/smartboxes/${id}`, data)

export const sendCommand = async (id, command) => 
  apiClient.post(`/smartboxes/${id}/command`, command)
```

### API Client Wrapper
The core `apiClient` injects authentication headers and handles errors:

```javascript
// src/services/apiClient.js
import { useAuthStore } from '@/stores/authStore.js'
import { getAuthHeader } from './authHeader.js'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

class ApiClient {
  async request(endpoint, options = {}) {
    const authStore = useAuthStore()
    
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      headers: {
        'Content-Type': 'application/json',
        ...getAuthHeader(authStore.token),
        ...options.headers,
      },
      ...options,
    })

    if (response.status === 401) {
      // Unauthorized: clear token and redirect to login
      authStore.logout()
      throw new Error('Session expired. Please log in again.')
    }

    if (!response.ok) {
      throw new Error(`API error: ${response.status} ${response.statusText}`)
    }

    return response.json()
  }

  get(endpoint) {
    return this.request(endpoint)
  }

  post(endpoint, data) {
    return this.request(endpoint, { method: 'POST', body: JSON.stringify(data) })
  }

  put(endpoint, data) {
    return this.request(endpoint, { method: 'PUT', body: JSON.stringify(data) })
  }

  delete(endpoint) {
    return this.request(endpoint, { method: 'DELETE' })
  }
}

export const apiClient = new ApiClient()
```

### Auth Header Builder
```javascript
// src/services/authHeader.js
export const getAuthHeader = (token) => {
  if (!token) return {}
  return { 'Authorization': `Bearer ${token}` }
}
```

### Real-Time Events (WebSocket / STOMP)

> **⚠️ NOT YET NEEDED — do not implement until the backend service layer calls `SessionWebSocketService`.**
>
> The backend has a fully configured STOMP WebSocket at `/ws/shooting` (SockJS fallback) and a `SessionWebSocketService` with publishing methods for session, bracket, leaderboard, and range updates. However, none of those methods are currently called from any backend service or controller. Wiring up the frontend before the backend emits events would result in a connected client that never receives anything.
>
> When the time comes, add `@stomp/stompjs` + `sockjs-client`, implement `realtimeService.js` (replacing the placeholder `eventsApi.js`), and subscribe to the relevant `/topic/sessions/{id}/...` topics from Pinia stores.

**Do not use SSE (`EventSource`) or add a `/api/events` endpoint.** The chosen transport is STOMP over WebSocket/SockJS — that decision is final. See the backend `CLAUDE.md` for the WebSocket configuration details.

---

## Layouts

The Smart Ground UI uses two distinct layouts based on user role and intent:

### MainLayout (Admin/Ground Owner)
Used for configuration, device management, and competition administration.

**Features:**
- Sidebar navigation with collapsible menu
- Admin-focused routes: ranges, SmartBoxes, device types, users, competition setup
- Breadcrumb navigation for nested views
- Search/filter controls
- Displays full device and competition state
- Role-restricted views (firmware configs for ADMIN only)

**Routes using MainLayout:**
```
/ranges, /ranges/:id, /smartboxes, /device-type-groups, 
/users, /profile, /competition/*, /programme, etc.
```

### ShooterLayout (Shooter)
Dedicated kiosk/remote interface for shooters during competitions.

**Features:**
- Full-screen, touch-friendly interface
- Minimal navigation (back button, home button)
- Large, easy-to-read status displays
- Real-time score and player-up notifications
- Optimized for 7–15" tablets mounted on range stands
- Countdown timers and voice prompts (if available)

**Routes using ShooterLayout:**
```
/home, /remote, /remote/:rangeId, /remote/:rangeId/play,
/competition/live, /competition/leaderboard, /career-stats, /programmes
```

**Key Differences:**
| Aspect | MainLayout | ShooterLayout |
|---|---|---|
| Target | Desktop admin/organizer | Tablet/kiosk on range |
| Navigation | Sidebar + breadcrumbs | Minimal (back button) |
| Text Size | Normal | Large (accessible) |
| Touch Targets | Standard | Large (≥48px) |
| Network | Assumes stable connection | Tolerates brief disconnects |

---

## Environment Variables

Create `.env.local` in the project root:

```env
# Backend API base URL
VITE_API_BASE_URL=http://localhost:8080/api

# Data source: 'mock' loads constants/mockData.js; 'api' hits the backend (default)
VITE_WORK_MODE=api

# Optional: for debugging
VITE_DEBUG=false
```

These are accessible in code as:
```javascript
const apiUrl = import.meta.env.VITE_API_BASE_URL
```

### Mock Mode (`VITE_WORK_MODE`)

Stores that support mock mode check `import.meta.env.VITE_WORK_MODE` at runtime. When set to `mock`, they load from `constants/mockData.js` instead of hitting the backend — useful for UI development without a running backend.

`deviceTypeStore` is the reference implementation of this pattern (`isMockMode()` guard → `loadMockData()` / `loadApiData()`). `constants/mockData.js` is for development/mock mode only — never reference it in `VITE_WORK_MODE=api` code paths.

### CORS

The backend allows requests from `http://localhost:5173`. If you change the dev server port, update `cors.allowed-origins` in the backend's `application.properties`.

---

## Routing

Vue Router config in `src/router/index.js` uses **role-based route guards** to enforce access control:

### Route Structure

**Authentication Route:**
- `/login` — No auth required; redirects to role-based home if already logged in

**Admin / Ground Owner Routes** (`meta.layout: 'admin'`):
- `/ranges` — Browse & manage shooting ranges
- `/ranges/:id` — Range detail and device assignment
- `/smartboxes` — SmartBox registry and status
- `/device-type-groups` — Device type category management
- `/admin/firmware-configs` — Firmware capability registry (admin-only)
- `/users` — User account management
- `/profile` — Current user profile
- `/player-setup` — Manage registered players/shooters
- `/competition` — Competition dashboard
- `/competition/templates` — Save/load competition blueprints
- `/competition/setup` — Initialize new competition session
- `/competition/bracket` — Visualize bracket and record results
- `/programme` — Manage shooting programs/courses

**Shooter Routes** (`meta.layout: 'shooter'`):
- `/home` — Shooter home screen
- `/remote` — Select range for shooting session
- `/remote/:rangeId` — Main shooting interface (kiosk mode)
- `/remote/:rangeId/play` — Active player shoot-through screen
- `/competition/live` — Watch live competition (broadcast view)
- `/competition/leaderboard` — View current leaderboard
- `/career-stats` — Personal shooting statistics
- `/programmes` — Manage shooter's program list

### Route Guards

The router enforces three access-control rules:

```javascript
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  const authenticated = authStore.isAuthenticated()

  // Rule 1: Unauthenticated users must log in
  if (to.meta.requiresAuth !== false && !authenticated) {
    next('/login')
    return
  }

  // Rule 2: Authenticated users on /login are redirected to role-based home
  if (to.path === '/login' && authenticated) {
    next(authStore.isShooter() ? '/home' : '/ranges')
    return
  }

  // Rule 3: Shooters are blocked from admin routes (except /profile)
  if (to.meta.layout === 'admin' && authStore.isShooter()) {
    next('/home')
    return
  }

  // Rule 4: Admins are blocked from shooter routes
  if (to.meta.layout === 'shooter' && authStore.isAdminOrOwner()) {
    next('/ranges')
    return
  }

  next()
})
```

### Layout Selection
Each route declares `meta.layout` to determine which wrapper applies:
- `layout: 'admin'` → Renders with `MainLayout` (sidebar, admin navigation)
- `layout: 'shooter'` → Renders with `ShooterLayout` (kiosk-friendly interface)

---

## Accessibility (WCAG AA)

### Basic Checklist
- [ ] All images have `alt` text
- [ ] Form labels linked to inputs with `for` attribute
- [ ] Color contrast ≥ 4.5:1 for normal text
- [ ] Interactive elements keyboard accessible (tab order)
- [ ] ARIA labels for screen readers (`aria-label`, `aria-describedby`)
- [ ] No keyboard traps
- [ ] Focus visible (`:focus-visible` styles)

### Example: Accessible Form
```vue
<template>
  <form @submit.prevent="submit">
    <label for="alias">Device Alias</label>
    <input 
      id="alias"
      v-model="form.alias"
      type="text"
      aria-describedby="alias-help"
      required
    />
    <span id="alias-help" class="help-text">Name must be unique</span>

    <button type="submit" :disabled="!formValid" aria-label="Create device">
      Create
    </button>
  </form>
</template>
```

---

## Responsive Design

Use CSS Grid and Flexbox with mobile-first approach:

```css
.device-grid {
  display: grid;
  grid-template-columns: 1fr;  /* Mobile: 1 column */
  gap: 1rem;
}

@media (min-width: 768px) {
  .device-grid {
    grid-template-columns: repeat(2, 1fr);  /* Tablet: 2 columns */
  }
}

@media (min-width: 1200px) {
  .device-grid {
    grid-template-columns: repeat(3, 1fr);  /* Desktop: 3 columns */
  }
}
```

Test with browser DevTools at these widths:
- 320px (mobile)
- 768px (tablet)
- 1920px (desktop)

---

## ESLint & Formatting

### Check for Issues
```bash
npm run lint
```

### Auto-fix Issues
```bash
npm run lint -- --fix
```

### ESLint Config
Config in `eslint.config.js` uses flat config:
```javascript
import js from '@eslint/js'
import vue from 'eslint-plugin-vue'

export default [
  js.configs.recommended,
  ...vue.configs['flat/recommended'],
  {
    rules: {
      'vue/multi-word-component-names': 'warn',
    },
  },
]
```

---

## Application Constants

The project uses constants defined in `src/constants/` for application-wide enums and configuration:

### Device Types (`constants/deviceTypes.js`)
Defines available device type templates with their signal behavior:
```javascript
export const DEVICE_TYPES = {
  WERFER_STANDARD: { id: 'werfer-std', group: 'Wurfmaschine', signal: 'GPIO', duration: 100 },
  LED_STATUS: { id: 'led-status', group: 'LED', signal: 'LED', duration: 100 },
  // ...
}
```

### Play Enums (`constants/playEnums.js`)
State machines and status constants for competition sessions:
```javascript
export const SessionStatus = {
  SETUP: 'SETUP',
  ACTIVE: 'ACTIVE',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
}

export const CompetitionFormat = {
  LEAGUE: 'LEAGUE',
  BRACKET: 'BRACKET',
  KNOCKOUT: 'KNOCKOUT',
}
```

### Werfer Tokens (`constants/werfertokens.js`)
Token definitions for the Werfer (clay pigeon thrower) devices.

---

## Troubleshooting

### Port 5173 Already in Use
```bash
# Use different port
npm run dev -- --port 5174
```

### Tests Fail with "Cannot find module"
```bash
# Clear node_modules and reinstall
rm -rf node_modules package-lock.json
npm install
npm run test
```

### API Calls Return 401 Unauthorized
- **Cause**: JWT token missing or expired
- **Fix**: Check `authStore.token` in browser DevTools console (`window.localStorage.getItem('sg_token')`)
- **Solution**: Log in again via LoginView; browser will store new token

### API Calls Return 404
- **Cause**: Backend not running or wrong URL
- Check backend is running: `./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres` in smart-ground-backend/
- Check `VITE_API_BASE_URL` in `.env.local` matches backend port (default `http://localhost:8080/api`)
- Check Network tab in browser DevTools to see actual request URL

### Component State Not Updating from Store
- **Cause**: Store action not awaited or state not marked as reactive
- **Fix**: Ensure actions use `await` and state uses `ref()`:
  ```javascript
  const { fetchSessions } = useCompetitionStore()
  await fetchSessions()  // Must await
  ```

### WebSocket / STOMP Not Yet Wired
Real-time updates via STOMP are not yet implemented. The backend `SessionWebSocketService` exists but its publish methods are never called. Do not attempt to connect a STOMP client until the backend service layer emits events — see the Real-Time Events section for the prerequisite steps.

### ESLint Errors After Update
```bash
# Clear cache and reinstall
rm -rf node_modules
npm install
npm run lint -- --fix
```

### "Missing component definition" errors
- **Cause**: Component not imported or file path wrong
- **Fix**: Ensure component is imported with correct path: `import BracketVisualizer from '@/components/BracketVisualizer.vue'`
- Components with `<script setup>` don't need manual registration

### Theme Switcher Not Persisting
- Check `appStore` is saving theme preference to localStorage
- Verify CSS variables are applied in `style.css` based on `[data-theme]` attribute

### Leaderboard Not Sorting Correctly
- Check `competitionStore.fetchLeaderboard()` is called after session updated
- Verify tiebreaker criteria matches backend implementation
- Check that `bracketStore` recalculates on each score update

---

## Implementation Status & Known Gaps

### Fully Implemented
- ✅ JWT-based authentication with role-based access control
- ✅ 15 Pinia stores covering all major feature domains
- ✅ Device management UI (SmartBox, Device, DeviceType, DeviceTypeGroup)
- ✅ Range management and detail views
- ✅ User account management (ADMIN only)
- ✅ Competition template library
- ✅ Competition setup with player invitation
- ✅ Bracket visualization and single-elimination logic
- ✅ Leaderboard display with ranking
- ✅ Shooter kiosk interface (ShooterRemoteView, ShooterPlayPage)
- ✅ Program/course management (shooter-side)
- ✅ Responsive design tested at 320px–1920px
- ✅ Router guards for role-based access

### Partially Implemented / Pending
- 🟡 **Real-Time Events (WebSocket/STOMP)**: Backend has STOMP configured at `/ws/shooting` but `SessionWebSocketService` is never called from any service/controller. Frontend `eventsApi.js` is an empty placeholder. **Do not wire the frontend until the backend emits events** — see Real-Time Events section.
- 🟡 **Leaderboard live updates**: Leaderboard displays current state; STOMP-driven updates not yet wired
- 🟡 **Bracket result recording**: Bracket visualization complete; recording match results and advancing rounds needs backend integration
- 🟡 **Guest player CRUD**: Guest store exists; UI for adding/removing guests in competition setup incomplete
- 🟡 **Program snapshot capture**: Program selection during competition setup doesn't yet snapshot program state to session

### Not Yet Implemented
- ❌ OTA firmware updates (separate from this UI)
- ❌ Multi-box device assignment UI (backend supports; frontend API not exposed)
- ❌ Advanced filtering & search for large device/user lists
- ❌ Internationalization (i18n) — all text is English
- ❌ Analytics/reporting dashboard (beyond career stats)
- ❌ Voice commands for shooter interface
- ❌ Sensor input validation from live matches

### Known Limitations
- **localStorage-based auth**: Token persists across page refreshes; no backend-issued refresh tokens yet
- **No offline mode**: All views require active API connection
- **Leaderboard tiebreaker**: Complex tiebreaker rules not yet calculated on frontend
- **Bracket seeding**: Frontend supports basic strategies; advanced custom seeding not implemented

---

## Build Optimization

### Tree Shaking
Vite automatically removes unused code during build. Ensure:
- Use named exports (not default)
- Don't import entire modules if using only specific exports

### Code Splitting
Vue Router automatically creates chunks per route:
```javascript
const DeviceConfig = defineAsyncComponent(() =>
  import('@/views/DeviceConfig.vue')
)
```

### Check Bundle Size
```bash
npm run build
# Review dist/ file sizes in output
```

---

## Common Agent Tasks

**Adding a new page/view:**
1. Create the view component in `src/views/`
2. Register the route in `router/index.js`
3. Add a navigation link in `Sidebar.vue` if it should appear in nav
4. Connect it to the appropriate Pinia store for data

**Adding a new API call:**
1. Add the function to the relevant file in `services/`
2. Add/update a mapper in `mappers/` if the response shape needs transforming
3. Add a store action that calls the service + mapper and updates state
4. Call the store action from the view component

**Adding a new store with mock/api mode support:**
1. Check `import.meta.env.VITE_WORK_MODE` via an `isMockMode()` helper
2. Implement `loadMockData()` using `constants/mockData.js`
3. Implement `loadApiData()` calling the relevant `services/` function
4. Call the appropriate branch from `initialize()`
5. Use `deviceTypeStore.js` as the reference implementation

**Adding a new device type:**
1. Add backend seed data (in the backend repo)
2. Update `constants/deviceTypes.js` with the new type's display metadata
3. Update `TypeChip.vue` or `DeviceCard.vue` if the new type needs distinct visual treatment

---

## Key Files

| File | Purpose |
|---|---|
| `src/main.js` | App entry point; initializes Pinia and Vue Router |
| `src/App.vue` | Root component; renders active route and layout |
| `src/router/index.js` | Vue Router with role-based access guards |
| `src/layouts/MainLayout.vue` | Primary admin interface (sidebar, nav) |
| `src/layouts/ShooterLayout.vue` | Kiosk-friendly shooter interface |
| `src/stores/authStore.js` | JWT auth, roles, session management (critical) |
| `src/stores/competitionStore.js` | Competition session state and actions |
| `src/stores/bracketStore.js` | Tournament bracket logic and state |
| `src/stores/smartBoxStore.js` | SmartBox device registry |
| `src/stores/deviceStore.js` | Physical device catalog |
| `src/services/apiClient.js` | Core fetch wrapper with auth header injection |
| `src/services/authApi.js` | Login and user creation endpoints |
| `src/services/eventsApi.js` | Placeholder for STOMP WebSocket client — not yet implemented |
| `src/services/competitionService.js` | Competition-specific APIs |
| `src/services/bracketService.js` | Bracket generation and seeding logic |
| `src/composables/useDeviceLoader.js` | Auto-load devices for known boxes |
| `src/mappers/index.js` | Re-exports all API → UI model mappers |
| `src/components/BracketVisualizer.vue` | Tournament bracket visualization |
| `src/components/scorer-remote/ShooterRemoteView.vue` | Main shooter kiosk interface |
| `src/constants/playEnums.js` | Competition state enums |
| `vite.config.js` | Vite build config; defines `@` alias |
| `vitest.config.js` | Test configuration |
| `eslint.config.js` | Linting rules (flat config) |
| `package.json` | Dependencies & npm scripts |

