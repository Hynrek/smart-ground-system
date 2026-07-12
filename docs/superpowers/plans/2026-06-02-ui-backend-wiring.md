# UI ↔ Backend Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all localStorage-based persistence in the Smart Ground UI with real backend API calls, connecting Abläufe, Programme, Trainings, PlayInstances, and Guests to their respective backend endpoints.

**Architecture:** The existing `services/apiClient.js` (`apiFetch`) is the foundation for all HTTP calls. New service modules are added per domain, then stores are rewritten to call services instead of manipulating localStorage. In-memory playback state (step index, score, etc.) stays client-side only.

**Tech Stack:** Vue 3 + Pinia, native `fetch` via `apiFetch`, backend REST at `http://localhost:8080/api`. Tests use Vitest + `vi.mock`.

---

## Context & Key Decisions

### Concept Mapping: UI → Backend

| UI concept | Store field | Backend entity | Endpoint |
|---|---|---|---|
| Serie (Ablauf template) | `passeStore.savedSerien` | `Ablauf` | `/api/ablaeufe` |
| Passe (user Programme) | `passeStore.savedPassen` | `Programme` | `/api/programmes` |
| Global Passe | `passeStore.savedGlobalPassen` | `Programme` | `/api/programmes` |
| Training | `passeStore.savedTrainings` | `Training` | `/api/trainings` |
| Active play instance | `activePasseStore.activeInstances` | `PlayInstance` | `/api/play-instances` |
| Guest player | `guestStore.guests` | `Guest` | `/api/guests` |

### Step Field Mapping

The UI stores `positionId` (RangePosition UUID) in steps. The backend `Step` schema uses `posId` (label string, e.g. "A"). For pragmatic reasons (playback uses UUIDs for direct API calls), we store the UUID in `posId`:

```
UI step.positionId (UUID) → backend Step.posId (stored as UUID string)
UI step.letter        → NOT stored separately (alias covers display)
UI step.alias         → backend Step.alias
UI step.positionId1   → backend Step.posId1
UI step.positionId2   → backend Step.posId2
```

### Global Passe → Programme

`savedGlobalPassen` (previously "range-visible" programmes) merges into regular `savedPassen`. Both map to backend `Programme`. Ownership distinction handled at Ablauf level (Ablauf has `ownership: user | range`).

### No Data Migration

This is pre-v1.0. Existing localStorage data is abandoned on migration. Users re-create their templates.

### Competition Store

`competitionEventStore.js` (Session management with Rotten) is **out of scope** — it needs backend Session API work first. It stays localStorage for now.

### SSE Events

`eventsApi.js` exists but is not wired. Task 10 adds proper integration.

---

## File Map

**New files:**
- `src/services/ablaufApi.js`
- `src/services/programmeApi.js`
- `src/services/trainingApi.js`
- `src/services/playInstanceApi.js`
- `src/services/guestApi.js`

**Rewritten:**
- `src/stores/guestStore.js`
- `src/stores/passeStore.js`
- `src/stores/activePasseStore.js`

**Updated:**
- `src/stores/smartBoxStore.js` (Task 10: SSE hookup)
- `src/stores/deviceStore.js` (Task 10: SSE hookup)
- `src/stores/__tests__/passeStore.test.js`
- `src/stores/__tests__/passeStore.training.test.js`
- `src/stores/__tests__/activePasseStore.test.js`
- `src/stores/__tests__/activePasseStore.competition.test.js`

---

## Task 1: Create `ablaufApi.js` — Ablauf CRUD service

> **Model suggestion:** claude-haiku-4-5-20251001

**Files:**
- Create: `smart-ground-ui/src/services/ablaufApi.js`

- [ ] **Step 1: Write the failing test**

Create `smart-ground-ui/src/services/__tests__/ablaufApi.test.js`:

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as apiClient from '../apiClient.js'

vi.mock('../apiClient.js', () => ({ apiFetch: vi.fn() }))

describe('ablaufApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetchAblaeufe calls GET /ablaeufe', async () => {
    apiClient.apiFetch.mockResolvedValue([])
    const { fetchAblaeufe } = await import('../ablaufApi.js')
    await fetchAblaeufe()
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/ablaeufe', undefined)
  })

  it('createAblauf calls POST /ablaeufe with body', async () => {
    apiClient.apiFetch.mockResolvedValue({ id: 'abc', name: 'Test' })
    const { createAblauf } = await import('../ablaufApi.js')
    await createAblauf('Test', [], null, 'user')
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/ablaeufe', {
      method: 'POST',
      body: JSON.stringify({ name: 'Test', steps: [], rangeId: null, ownership: 'user' }),
    })
  })

  it('deleteAblauf calls DELETE /ablaeufe/{id}', async () => {
    apiClient.apiFetch.mockResolvedValue(null)
    const { deleteAblauf } = await import('../ablaufApi.js')
    await deleteAblauf('abc')
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/ablaeufe/abc', { method: 'DELETE' })
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```
cd smart-ground-ui && npm run test src/services/__tests__/ablaufApi.test.js
```
Expected: FAIL — module not found

- [ ] **Step 3: Create `ablaufApi.js`**

```javascript
// src/services/ablaufApi.js
import { apiFetch } from './apiClient.js';

export async function fetchAblaeufe(params) {
  const query = params ? '?' + new URLSearchParams(params).toString() : '';
  return apiFetch(`/ablaeufe${query}`, undefined);
}

export async function getAblauf(id) {
  return apiFetch(`/ablaeufe/${id}`);
}

export async function createAblauf(name, steps, rangeId = null, ownership = 'user') {
  return apiFetch('/ablaeufe', {
    method: 'POST',
    body: JSON.stringify({ name, steps, rangeId, ownership }),
  });
}

export async function updateAblauf(id, name, rangeId = null) {
  return apiFetch(`/ablaeufe/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name, rangeId }),
  });
}

export async function updateAblaufOwnership(id, ownership) {
  return apiFetch(`/ablaeufe/${id}/ownership`, {
    method: 'PATCH',
    body: JSON.stringify({ ownership }),
  });
}

export async function deleteAblauf(id) {
  return apiFetch(`/ablaeufe/${id}`, { method: 'DELETE' });
}
```

- [ ] **Step 4: Run test to verify it passes**

```
cd smart-ground-ui && npm run test src/services/__tests__/ablaufApi.test.js
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add smart-ground-ui/src/services/ablaufApi.js smart-ground-ui/src/services/__tests__/ablaufApi.test.js
git commit -m "[ui] add ablaufApi service for Ablauf CRUD"
```

---

## Task 2: Create `programmeApi.js` and `trainingApi.js`

> **Model suggestion:** claude-haiku-4-5-20251001

**Files:**
- Create: `smart-ground-ui/src/services/programmeApi.js`
- Create: `smart-ground-ui/src/services/trainingApi.js`

- [ ] **Step 1: Create `programmeApi.js`**

```javascript
// src/services/programmeApi.js
import { apiFetch } from './apiClient.js';

export async function fetchProgrammes() {
  return apiFetch('/programmes');
}

export async function getProgramme(id) {
  return apiFetch(`/programmes/${id}`);
}

export async function createProgramme(name, ablaufIds) {
  return apiFetch('/programmes', {
    method: 'POST',
    body: JSON.stringify({ name, ablaufIds }),
  });
}

export async function updateProgramme(id, name) {
  return apiFetch(`/programmes/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  });
}

export async function deleteProgramme(id) {
  return apiFetch(`/programmes/${id}`, { method: 'DELETE' });
}
```

- [ ] **Step 2: Create `trainingApi.js`**

```javascript
// src/services/trainingApi.js
import { apiFetch } from './apiClient.js';

export async function fetchTrainings() {
  return apiFetch('/trainings');
}

export async function getTraining(id) {
  return apiFetch(`/trainings/${id}`);
}

export async function createTraining(name, programmeIds) {
  return apiFetch('/trainings', {
    method: 'POST',
    body: JSON.stringify({ name, programmeIds }),
  });
}

export async function updateTraining(id, name) {
  return apiFetch(`/trainings/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  });
}

export async function deleteTraining(id) {
  return apiFetch(`/trainings/${id}`, { method: 'DELETE' });
}
```

- [ ] **Step 3: Run all existing tests to confirm nothing broke**

```
cd smart-ground-ui && npm run test
```
Expected: same passing count as before

- [ ] **Step 4: Commit**

```bash
git add smart-ground-ui/src/services/programmeApi.js smart-ground-ui/src/services/trainingApi.js
git commit -m "[ui] add programmeApi and trainingApi service modules"
```

---

## Task 3: Create `playInstanceApi.js`

> **Model suggestion:** claude-haiku-4-5-20251001

**Files:**
- Create: `smart-ground-ui/src/services/playInstanceApi.js`

- [ ] **Step 1: Create the file**

```javascript
// src/services/playInstanceApi.js
import { apiFetch } from './apiClient.js';

export async function startProgrammeInstance(programmeId, players) {
  return apiFetch('/play-instances/programme', {
    method: 'POST',
    body: JSON.stringify({ programmeId, players }),
  });
}

export async function startTrainingInstance(trainingId, players) {
  return apiFetch('/play-instances/training', {
    method: 'POST',
    body: JSON.stringify({ trainingId, players }),
  });
}

export async function listPlayInstances(params = {}) {
  const query = Object.keys(params).length
    ? '?' + new URLSearchParams(params).toString()
    : '';
  return apiFetch(`/play-instances${query}`);
}

export async function getPlayInstance(instanceId) {
  return apiFetch(`/play-instances/${instanceId}`);
}

export async function stopPlayInstance(instanceId) {
  return apiFetch(`/play-instances/${instanceId}`, { method: 'DELETE' });
}

export async function startBlock(instanceId, blockId) {
  return apiFetch(`/play-instances/${instanceId}/blocks/${blockId}/start`, { method: 'POST' });
}

export async function completeBlock(instanceId, blockId, playerResults) {
  return apiFetch(`/play-instances/${instanceId}/blocks/${blockId}/complete`, {
    method: 'POST',
    body: JSON.stringify({ playerResults }),
  });
}
```

- [ ] **Step 2: Run tests**

```
cd smart-ground-ui && npm run test
```
Expected: still all green

- [ ] **Step 3: Commit**

```bash
git add smart-ground-ui/src/services/playInstanceApi.js
git commit -m "[ui] add playInstanceApi service"
```

---

## Task 4: Create `guestApi.js` and migrate `guestStore.js`

> **Model suggestion:** claude-haiku-4-5-20251001

**Files:**
- Create: `smart-ground-ui/src/services/guestApi.js`
- Modify: `smart-ground-ui/src/stores/guestStore.js`

- [ ] **Step 1: Write failing test for new guestStore**

In `smart-ground-ui/src/stores/__tests__/guestStore.test.js` (new file):

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useGuestStore } from '../guestStore.js'
import * as guestApi from '@/services/guestApi.js'

vi.mock('@/services/guestApi.js')

describe('guestStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadGuests fetches from API', async () => {
    guestApi.fetchGuests.mockResolvedValue([{ id: 'g1', displayName: 'Alice', createdAt: '2025-01-01T00:00:00Z' }])
    const store = useGuestStore()
    await store.loadGuests()
    expect(store.guests).toHaveLength(1)
    expect(store.guests[0].displayName).toBe('Alice')
  })

  it('addGuest calls POST and adds to list', async () => {
    guestApi.createGuest.mockResolvedValue({ id: 'g2', displayName: 'Bob', createdAt: '2025-01-01T00:00:00Z' })
    const store = useGuestStore()
    store.guests = []
    const result = await store.addGuest('Bob')
    expect(guestApi.createGuest).toHaveBeenCalledWith('Bob')
    expect(result.id).toBe('g2')
    expect(store.guests).toHaveLength(1)
  })

  it('removeGuest calls DELETE and removes from list', async () => {
    guestApi.deleteGuest.mockResolvedValue(null)
    const store = useGuestStore()
    store.guests = [{ id: 'g1', displayName: 'Alice' }]
    await store.removeGuest('g1')
    expect(guestApi.deleteGuest).toHaveBeenCalledWith('g1')
    expect(store.guests).toHaveLength(0)
  })
})
```

- [ ] **Step 2: Run test — expect FAIL**

```
cd smart-ground-ui && npm run test src/stores/__tests__/guestStore.test.js
```

- [ ] **Step 3: Create `guestApi.js`**

```javascript
// src/services/guestApi.js
import { apiFetch } from './apiClient.js';

export async function fetchGuests() {
  return apiFetch('/guests');
}

export async function createGuest(displayName) {
  return apiFetch('/guests', {
    method: 'POST',
    body: JSON.stringify({ displayName }),
  });
}

export async function updateGuest(id, displayName) {
  return apiFetch(`/guests/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ displayName }),
  });
}

export async function deleteGuest(id) {
  return apiFetch(`/guests/${id}`, { method: 'DELETE' });
}
```

- [ ] **Step 4: Rewrite `guestStore.js`**

```javascript
// src/stores/guestStore.js
import { defineStore } from 'pinia';
import { ref } from 'vue';
import * as guestApi from '@/services/guestApi.js';

export const useGuestStore = defineStore('guests', () => {
  const guests = ref([]);
  const isLoading = ref(false);
  const error = ref(null);

  const loadGuests = async () => {
    isLoading.value = true;
    error.value = null;
    try {
      guests.value = await guestApi.fetchGuests();
    } catch (e) {
      error.value = e.message;
    } finally {
      isLoading.value = false;
    }
  };

  const addGuest = async (displayName) => {
    const guest = await guestApi.createGuest(displayName.trim());
    guests.value = [...guests.value, guest];
    return guest;
  };

  const removeGuest = async (id) => {
    await guestApi.deleteGuest(id);
    guests.value = guests.value.filter((g) => g.id !== id);
  };

  const updateGuest = async (id, displayName) => {
    const updated = await guestApi.updateGuest(id, displayName.trim());
    guests.value = guests.value.map((g) => (g.id === id ? updated : g));
  };

  return { guests, isLoading, error, loadGuests, addGuest, removeGuest, updateGuest };
});
```

- [ ] **Step 5: Run test to verify it passes**

```
cd smart-ground-ui && npm run test src/stores/__tests__/guestStore.test.js
```
Expected: PASS (3 tests)

- [ ] **Step 6: Run full test suite**

```
cd smart-ground-ui && npm run test
```
Expected: all tests pass (guestStore tests that previously existed may fail — update them to use the API mock pattern if so)

- [ ] **Step 7: Commit**

```bash
git add smart-ground-ui/src/services/guestApi.js smart-ground-ui/src/stores/guestStore.js smart-ground-ui/src/stores/__tests__/guestStore.test.js
git commit -m "[ui] migrate guestStore from localStorage to /api/guests"
```

---

## Task 5: Rewrite `passeStore.js` — Ablauf (Serie) layer

> **Model suggestion:** claude-sonnet-4-6

**Context:** `passeStore.js` currently has ~622 lines with localStorage key manipulation. We keep the in-memory capture state (recording, passeMode, pairPending, editingSerie, activeSerieIndex, editingId) unchanged. We replace only the persistence layer.

**Key terminology mapping:**
- UI "Serie" = backend "Ablauf"
- UI `savedSerien[].steps[].positionId` → backend `Step.posId` (stored as-is, UUID string)

**Files:**
- Modify: `smart-ground-ui/src/stores/passeStore.js`
- Modify: `smart-ground-ui/src/stores/__tests__/passeStore.test.js`

- [ ] **Step 1: Write failing tests for the API-based Ablauf layer**

Replace the content of `smart-ground-ui/src/stores/__tests__/passeStore.test.js` with:

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePasseStore } from '../passeStore.js'
import * as ablaufApi from '@/services/ablaufApi.js'

vi.mock('@/services/ablaufApi.js')
vi.mock('@/services/programmeApi.js')
vi.mock('@/services/trainingApi.js')
vi.mock('@/stores/shooterRemoteStore.js', () => ({
  useShooterRemoteStore: () => ({ isReserved: true, mode: 'solo', setMode: vi.fn() }),
}))
vi.mock('@/stores/authStore.js', () => ({
  useAuthStore: () => ({ profile: { email: 'test@test.com' } }),
}))

describe('passeStore — Ablauf layer', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadSerienFromStorage fetches Abläufe from API', async () => {
    ablaufApi.fetchAblaeufe.mockResolvedValue([
      { id: 'ab1', name: 'Test Serie', ownership: 'user', rangeId: null, rangeName: null, steps: [], createdAt: '2025-01-01T00:00:00Z' },
    ])
    const store = usePasseStore()
    await store.loadSerienFromStorage()
    expect(store.savedSerien).toHaveLength(1)
    expect(store.savedSerien[0].name).toBe('Test Serie')
  })

  it('saveSerie creates an Ablauf via API', async () => {
    ablaufApi.createAblauf.mockResolvedValue({ id: 'ab2', name: 'New Serie', ownership: 'user', rangeId: null, rangeName: null, steps: [] })
    const store = usePasseStore()
    store.editingSerie = [{ id: 's1', alias: null, steps: [{ id: '1', type: 'solo', positionId: 'pos-uuid', alias: 'A' }] }]
    await store.saveSerie('New Serie')
    expect(ablaufApi.createAblauf).toHaveBeenCalledWith(
      'New Serie',
      [{ id: '1', type: 'solo', posId: 'pos-uuid', alias: 'A' }],
      null,
      'user',
    )
  })

  it('deleteSerie calls deleteAblauf on API', async () => {
    ablaufApi.deleteAblauf.mockResolvedValue(null)
    const store = usePasseStore()
    store.savedSerien = [{ id: 'ab1', name: 'Test', steps: [], ownership: 'user' }]
    await store.deleteSerie('ab1')
    expect(ablaufApi.deleteAblauf).toHaveBeenCalledWith('ab1')
    expect(store.savedSerien).toHaveLength(0)
  })
})
```

- [ ] **Step 2: Run tests — expect FAIL**

```
cd smart-ground-ui && npm run test src/stores/__tests__/passeStore.test.js
```

- [ ] **Step 3: Rewrite `passeStore.js`**

Replace `smart-ground-ui/src/stores/passeStore.js` with the following. The file is split into clearly labeled sections:

```javascript
import { defineStore } from 'pinia';
import { ref } from 'vue';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import { useAuthStore } from '@/stores/authStore.js';
import * as ablaufApi from '@/services/ablaufApi.js';
import * as programmeApi from '@/services/programmeApi.js';
import * as trainingApi from '@/services/trainingApi.js';

const generateUUID = () =>
  typeof globalThis !== 'undefined' && globalThis.crypto?.randomUUID
    ? globalThis.crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
      });

// ── Step mapping: UI step ↔ backend Step schema ──────────────────────────────
// The UI stores positionId (UUID) in steps; backend Step.posId holds the same UUID.

function toApiStep(step) {
  const base = { id: String(step.id), type: step.type };
  if (step.type === 'solo' || step.type === 'raffale') {
    return { ...base, posId: step.positionId ?? null, alias: step.alias ?? null };
  }
  return {
    ...base,
    posId1: step.positionId1 ?? null,
    posId2: step.positionId2 ?? null,
    alias1: step.alias1 ?? null,
    alias2: step.alias2 ?? null,
  };
}

function fromApiStep(step) {
  const base = { id: step.id, type: step.type };
  if (step.type === 'solo' || step.type === 'raffale') {
    return { ...base, positionId: step.posId ?? null, alias: step.alias ?? null };
  }
  return {
    ...base,
    positionId1: step.posId1 ?? null,
    positionId2: step.posId2 ?? null,
    alias1: step.alias1 ?? null,
    alias2: step.alias2 ?? null,
  };
}

function toUiSerie(ablauf) {
  return {
    id: ablauf.id,
    name: ablauf.name,
    rangeId: ablauf.rangeId ?? null,
    rangeName: ablauf.rangeName ?? null,
    steps: (ablauf.steps ?? []).map(fromApiStep),
    ownership: ablauf.ownership ?? 'user',
    createdAt: ablauf.createdAt ?? null,
    ownerUsername: ablauf.ownerUsername ?? null,
  };
}

function toUiPasse(programme) {
  return {
    id: programme.id,
    name: programme.name,
    serien: (programme.ablaeufe ?? []).map((a) => ({
      id: a.id,
      alias: a.alias,
      rangeId: a.rangeId ?? null,
      rangeName: a.rangeName ?? null,
      steps: (a.steps ?? []).map(fromApiStep),
    })),
    ownerUsername: programme.ownerUsername ?? null,
  };
}

function toUiTraining(training) {
  return {
    id: training.id,
    name: training.name,
    passen: (training.programmes ?? []).map((prog) => ({
      id: prog.id,
      name: prog.name,
      serien: (prog.ablaeufe ?? []).map((a) => ({
        id: a.id,
        alias: a.alias,
        rangeId: a.rangeId ?? null,
        rangeName: a.rangeName ?? null,
        steps: (a.steps ?? []).map(fromApiStep),
      })),
    })),
    ownerUsername: training.ownerUsername ?? null,
  };
}

export const usePasseStore = defineStore('passe', () => {
  // ── Capture state (in-memory only, no persistence) ────────────────────────
  const recording = ref({});
  const passeMode = ref(false);
  const pairPending = ref(null);
  const activeSerieIndex = ref(0);
  const editingId = ref(null);
  const editingSerie = ref([]);

  // ── Persisted state (backed by backend API) ────────────────────────────────
  const savedSerien = ref([]);
  const savedPassen = ref([]);
  const savedGlobalPassen = ref([]);
  const savedTrainings = ref([]);
  const pendingPasseId = ref(null);

  // ── Load from backend API ──────────────────────────────────────────────────

  const loadSerienFromStorage = async () => {
    try {
      const ablaeufe = await ablaufApi.fetchAblaeufe();
      savedSerien.value = ablaeufe.map(toUiSerie);
    } catch (e) {
      console.error('Failed to load Abläufe from API:', e);
    }
  };

  const loadPassenFromStorage = async () => {
    try {
      const programmes = await programmeApi.fetchProgrammes();
      savedPassen.value = programmes.map(toUiPasse);
      savedGlobalPassen.value = [];
    } catch (e) {
      console.error('Failed to load Programmes from API:', e);
    }
  };

  const loadGlobalPassenFromStorage = async () => {
    // Global passen now unified with savedPassen (both are Programmes on the backend).
    // This is a no-op kept for API compatibility with callers.
  };

  const loadTrainingsFromStorage = async () => {
    try {
      const trainings = await trainingApi.fetchTrainings();
      savedTrainings.value = trainings.map(toUiTraining);
    } catch (e) {
      console.error('Failed to load Trainings from API:', e);
    }
  };

  // ── Capture lifecycle ──────────────────────────────────────────────────────

  const resetCapture = () => {
    passeMode.value = false;
    pairPending.value = null;
    activeSerieIndex.value = 0;
  };

  const startCapture = () => {
    const shooterRemoteStore = useShooterRemoteStore();
    if (!shooterRemoteStore.isReserved) return;
    passeMode.value = true;
    activeSerieIndex.value = 0;
    editingSerie.value = [{ id: generateUUID(), alias: null, steps: [] }];
    editingId.value = null;
  };

  const cancelCapture = () => {
    passeMode.value = false;
    pairPending.value = null;
    editingSerie.value = [];
    activeSerieIndex.value = 0;
    editingId.value = null;
  };

  // ── Step recording (in-memory, unchanged from original) ───────────────────

  const addStep = (positionId, position, positionLabel) => {
    const alias = position.device?.alias ?? position.label;
    const letter = positionLabel;
    const shooterRemoteStore = useShooterRemoteStore();

    if (shooterRemoteStore.mode === 'solo') {
      recording.value = { ...recording.value, [positionId]: true };
      setTimeout(() => {
        const r = { ...recording.value };
        delete r[positionId];
        recording.value = r;
      }, 500);
      const step = { id: Date.now(), type: 'solo', alias, positionId, letter };
      const segs = [...editingSerie.value];
      segs[0].steps = [...segs[0].steps, step];
      editingSerie.value = segs;
    } else if (shooterRemoteStore.mode === 'raffale') {
      recording.value = { ...recording.value, [positionId]: true };
      setTimeout(() => {
        const r = { ...recording.value };
        delete r[positionId];
        recording.value = r;
      }, 500);
      const step = { id: Date.now(), type: 'raffale', alias, positionId, letter };
      const segs = [...editingSerie.value];
      segs[0].steps = [...segs[0].steps, step];
      editingSerie.value = segs;
      shooterRemoteStore.setMode('solo');
    } else if (shooterRemoteStore.mode === 'pair' || shooterRemoteStore.mode === 'a_schuss') {
      if (!pairPending.value) {
        pairPending.value = { id: positionId, alias, letter };
      } else if (pairPending.value.id === positionId) {
        pairPending.value = null;
      } else {
        const pendingId = pairPending.value.id;
        const pendingAlias = pairPending.value.alias;
        recording.value = { ...recording.value, [positionId]: true, [pendingId]: true };
        setTimeout(() => {
          const r = { ...recording.value };
          delete r[positionId];
          delete r[pendingId];
          recording.value = r;
        }, 500);
        const stepType = shooterRemoteStore.mode === 'a_schuss' ? 'a_schuss' : 'pair';
        const step = {
          id: Date.now(),
          type: stepType,
          alias1: pendingAlias,
          alias2: alias,
          positionId1: pendingId,
          positionId2: positionId,
        };
        const segs = [...editingSerie.value];
        segs[0].steps = [...segs[0].steps, step];
        editingSerie.value = segs;
        pairPending.value = null;
        shooterRemoteStore.setMode('solo');
      }
    }
  };

  const removeStep = (serieIndex, stepId) => {
    const segs = [...editingSerie.value];
    segs[serieIndex] = {
      ...segs[serieIndex],
      steps: segs[serieIndex].steps.filter((s) => s.id !== stepId),
    };
    editingSerie.value = segs;
  };

  // ── Serie (Ablauf) persistence ────────────────────────────────────────────

  const saveSerie = async (serieName, rangeId = null, rangeName = null, ownership = 'user') => {
    const steps = editingSerie.value[0]?.steps ?? [];
    if (steps.length === 0) return;
    const name = serieName?.trim() || `Serie ${savedSerien.value.length + 1}`;
    const apiSteps = steps.map(toApiStep);
    const created = await ablaufApi.createAblauf(name, apiSteps, rangeId, ownership);
    savedSerien.value = [...savedSerien.value, toUiSerie({ ...created, rangeName })];
    cancelCapture();
  };

  const deleteSerie = async (serieId) => {
    await ablaufApi.deleteAblauf(serieId);
    savedSerien.value = savedSerien.value.filter((s) => s.id !== serieId);
  };

  const renameSerie = async (serieId, newName) => {
    const serie = savedSerien.value.find((s) => s.id === serieId);
    if (!serie) return;
    await ablaufApi.updateAblauf(serieId, newName, serie.rangeId ?? null);
    serie.name = newName;
  };

  const createRangeSerie = async (name, rangeId, rangeName, steps) => {
    if (!steps || steps.length === 0) return;
    const trimmedName = name?.trim() || `Serie ${savedSerien.value.length + 1}`;
    const apiSteps = steps.map(toApiStep);
    const created = await ablaufApi.createAblauf(trimmedName, apiSteps, rangeId, 'range');
    savedSerien.value = [...savedSerien.value, toUiSerie({ ...created, rangeName })];
  };

  const updateSerie = async (serieId, newName, newSteps) => {
    const serie = savedSerien.value.find((s) => s.id === serieId);
    if (!serie) return;
    await ablaufApi.updateAblauf(serieId, newName, serie.rangeId ?? null);
    savedSerien.value = savedSerien.value.map((s) =>
      s.id === serieId ? { ...s, name: newName, steps: [...(newSteps ?? [])] } : s,
    );
  };

  // ── Passe (Programme) persistence ─────────────────────────────────────────

  const createPasse = async (passeName, selectedSerien) => {
    if (selectedSerien.length === 0) return;
    const name = passeName?.trim() || `Passe ${savedPassen.value.length + 1}`;
    const ablaufIds = selectedSerien.map((s) => s.id);
    const created = await programmeApi.createProgramme(name, ablaufIds);
    savedPassen.value = [...savedPassen.value, toUiPasse(created)];
  };

  const deletePasse = async (passeId) => {
    await programmeApi.deleteProgramme(passeId);
    savedPassen.value = savedPassen.value.filter((p) => p.id !== passeId);
    if (pendingPasseId.value === passeId) pendingPasseId.value = null;
  };

  const renamePasse = async (passeId, newName) => {
    await programmeApi.updateProgramme(passeId, newName);
    savedPassen.value = savedPassen.value.map((p) =>
      p.id === passeId ? { ...p, name: newName } : p,
    );
  };

  // Global passen are merged into savedPassen — these are API aliases.
  const createGlobalPasse = (name, selectedSerien) => createPasse(name, selectedSerien);
  const updateGlobalPasse = async (id, newName) => renamePasse(id, newName);
  const deleteGlobalPasse = (id) => deletePasse(id);

  // ── Training persistence ───────────────────────────────────────────────────

  const createTraining = async (trainingName, selectedPassen, options = {}) => {
    if (selectedPassen.length === 0) return;
    const name = trainingName?.trim() || `Training ${savedTrainings.value.length + 1}`;
    const programmeIds = selectedPassen.map((p) => p.id);
    const created = await trainingApi.createTraining(name, programmeIds);
    savedTrainings.value = [...savedTrainings.value, toUiTraining(created)];
  };

  const createCompetition = (name, selectedPassen, rottCountHint = null) => {
    return createTraining(name, selectedPassen, { type: 'competition', rottCountHint });
  };

  const deleteTraining = async (trainingId) => {
    await trainingApi.deleteTraining(trainingId);
    savedTrainings.value = savedTrainings.value.filter((t) => t.id !== trainingId);
  };

  const renameTraining = async (trainingId, newName) => {
    await trainingApi.updateTraining(trainingId, newName);
    savedTrainings.value = savedTrainings.value.map((t) =>
      t.id === trainingId ? { ...t, name: newName } : t,
    );
  };

  // ── Pending passe ──────────────────────────────────────────────────────────

  const setPendingPasse = (passeId) => { pendingPasseId.value = passeId; };
  const clearPendingPasse = () => { pendingPasseId.value = null; };

  // ── Serie retrieval helpers ────────────────────────────────────────────────

  const getUserSerien = () => savedSerien.value.filter((s) => s.ownership === 'user');
  const getGlobalSerien = () => savedSerien.value.filter((s) => s.ownership === 'range');
  const getSerienForRange = (rangeId) => savedSerien.value.filter((s) => s.rangeId === rangeId);
  const getUserSerienForRange = (rangeId) => getUserSerien().filter((s) => s.rangeId === rangeId);

  // ── Legacy ─────────────────────────────────────────────────────────────────

  /** @deprecated use saveSerie */
  const savePasse = async (passeName = null) => {
    if (editingSerie.value.every((s) => s.steps.length === 0)) return;
    await saveSerie(passeName);
  };

  return {
    // Capture state
    recording, passeMode, pairPending, activeSerieIndex, editingId, editingSerie,
    // Persisted state
    savedSerien, savedPassen, pendingPasseId, savedTrainings, savedGlobalPassen,
    // Capture lifecycle
    resetCapture, startCapture, cancelCapture,
    // Step recording
    addStep, removeStep,
    // Serie actions
    saveSerie, deleteSerie, renameSerie, createRangeSerie, updateSerie,
    loadSerienFromStorage,
    // Serie retrieval
    getUserSerien, getGlobalSerien, getSerienForRange, getUserSerienForRange,
    // Passe actions
    createPasse, deletePasse, renamePasse, setPendingPasse, clearPendingPasse,
    loadPassenFromStorage,
    // Global Passe actions (aliases to Passe)
    createGlobalPasse, updateGlobalPasse, deleteGlobalPasse, loadGlobalPassenFromStorage,
    // Training actions
    loadTrainingsFromStorage, createTraining, createCompetition, deleteTraining, renameTraining,
    // Legacy
    savePasse,
  };
});
```

- [ ] **Step 4: Run tests**

```
cd smart-ground-ui && npm run test src/stores/__tests__/passeStore.test.js
```
Expected: PASS

- [ ] **Step 5: Run full suite and fix any broken callers**

```
cd smart-ground-ui && npm run test
```

If tests reference `savedGlobalPassen` expecting separate data, update them: global passen are merged into `savedPassen`.

- [ ] **Step 6: Commit**

```bash
git add smart-ground-ui/src/stores/passeStore.js smart-ground-ui/src/stores/__tests__/passeStore.test.js
git commit -m "[ui] rewrite passeStore — replace localStorage with /api/ablaeufe, /api/programmes, /api/trainings"
```

---

## Task 6: Update passeStore training tests

> **Model suggestion:** claude-haiku-4-5-20251001

**Files:**
- Modify: `smart-ground-ui/src/stores/__tests__/passeStore.training.test.js`

- [ ] **Step 1: Read current failing tests**

```
cd smart-ground-ui && npm run test src/stores/__tests__/passeStore.training.test.js
```

- [ ] **Step 2: Update test file to mock API services**

Replace localStorage mocking with API service mocks, following the same pattern as `passeStore.test.js`:

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePasseStore } from '../passeStore.js'
import * as trainingApi from '@/services/trainingApi.js'

vi.mock('@/services/ablaufApi.js')
vi.mock('@/services/programmeApi.js')
vi.mock('@/services/trainingApi.js')
vi.mock('@/stores/shooterRemoteStore.js', () => ({
  useShooterRemoteStore: () => ({ isReserved: true, mode: 'solo', setMode: vi.fn() }),
}))
vi.mock('@/stores/authStore.js', () => ({
  useAuthStore: () => ({ profile: { email: 'test@test.com' } }),
}))

describe('passeStore — Training layer', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('createTraining creates Training via API', async () => {
    trainingApi.createTraining.mockResolvedValue({
      id: 't1', name: 'Training 1', programmes: [], ownerUsername: 'user',
    })
    const store = usePasseStore()
    const passen = [{ id: 'p1', name: 'Passe 1', serien: [] }]
    await store.createTraining('Training 1', passen)
    expect(trainingApi.createTraining).toHaveBeenCalledWith('Training 1', ['p1'])
    expect(store.savedTrainings).toHaveLength(1)
  })

  it('deleteTraining removes via API', async () => {
    trainingApi.deleteTraining.mockResolvedValue(null)
    const store = usePasseStore()
    store.savedTrainings = [{ id: 't1', name: 'Training 1', passen: [] }]
    await store.deleteTraining('t1')
    expect(store.savedTrainings).toHaveLength(0)
  })
})
```

- [ ] **Step 3: Run tests**

```
cd smart-ground-ui && npm run test src/stores/__tests__/passeStore.training.test.js
```
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add smart-ground-ui/src/stores/__tests__/passeStore.training.test.js
git commit -m "[ui] update passeStore training tests for API-based store"
```

---

## Task 7: Rewrite `activePasseStore.js` — PlayInstance API

> **Model suggestion:** claude-sonnet-4-6

**Context:** `activePasseStore.js` currently maintains active play instances entirely in localStorage. The backend has a full `PlayInstance` state machine at `/api/play-instances`. We replace the localStorage layer with API calls while keeping the same public interface for components.

**Key mappings:**
- `startPasse(template, players)` → `POST /api/play-instances/programme { programmeId, players }`
- `startTraining(template, players)` → `POST /api/play-instances/training { trainingId, players }`
- `markBlockInProgress(instanceId, blockId)` → `POST /api/play-instances/{instanceId}/blocks/{blockId}/start`
- `markBlockDone(instanceId, blockId, playerResults)` → `POST /api/play-instances/{instanceId}/blocks/{blockId}/complete`
- `stopInstance(instanceId)` → `DELETE /api/play-instances/{instanceId}`

**Note:** The `competition` type instance (`startCompetition` / `assignRotteToRange`) has no direct backend equivalent in the current API. Keep these as in-memory only for now (they're used by `competitionEventStore` which is out of scope). The backend `PlayInstance` doesn't support "rotten" directly.

**Files:**
- Modify: `smart-ground-ui/src/stores/activePasseStore.js`
- Modify: `smart-ground-ui/src/stores/__tests__/activePasseStore.test.js`

- [ ] **Step 1: Write failing tests**

Replace `smart-ground-ui/src/stores/__tests__/activePasseStore.test.js`:

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useActivePasseStore } from '../activePasseStore.js'
import * as playInstanceApi from '@/services/playInstanceApi.js'

vi.mock('@/services/playInstanceApi.js')

const mockProgrammeInstance = {
  instanceId: 'inst-1',
  type: 'programm',
  templateId: 'prog-1',
  templateName: 'Test Prog',
  status: 'active',
  players: [{ id: 'p1', type: 'user', displayName: 'Alice' }],
  startedAt: '2025-01-01T00:00:00Z',
  completedAt: null,
  blocks: [
    { blockId: 'blk-1', ablaufId: 'ab1', ablaufAlias: 'Serie A', rangeId: 'r1', rangeName: 'Platz 1', steps: [], status: 'pending', completedAt: null, result: null },
  ],
  phases: null,
}

describe('activePasseStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('startPasse calls API and stores instance', async () => {
    playInstanceApi.startProgrammeInstance.mockResolvedValue(mockProgrammeInstance)
    const store = useActivePasseStore()
    const template = { id: 'prog-1', name: 'Test Prog', serien: [] }
    const players = [{ id: 'p1', type: 'user', displayName: 'Alice' }]
    await store.startPasse(template, players)
    expect(playInstanceApi.startProgrammeInstance).toHaveBeenCalledWith('prog-1', players)
    expect(store.activeInstances).toHaveLength(1)
    expect(store.activeInstances[0].instanceId).toBe('inst-1')
  })

  it('stopInstance calls DELETE and removes from list', async () => {
    playInstanceApi.stopPlayInstance.mockResolvedValue(null)
    const store = useActivePasseStore()
    store.activeInstances = [{ ...mockProgrammeInstance }]
    await store.stopInstance('inst-1')
    expect(playInstanceApi.stopPlayInstance).toHaveBeenCalledWith('inst-1')
    expect(store.activeInstances).toHaveLength(0)
  })

  it('markBlockInProgress calls start block API', async () => {
    playInstanceApi.startBlock.mockResolvedValue({ ...mockProgrammeInstance, blocks: [{ ...mockProgrammeInstance.blocks[0], status: 'in_progress' }] })
    const store = useActivePasseStore()
    store.activeInstances = [{ ...mockProgrammeInstance }]
    await store.markBlockInProgress('inst-1', 'blk-1')
    expect(playInstanceApi.startBlock).toHaveBeenCalledWith('inst-1', 'blk-1')
  })

  it('markBlockDone calls complete block API', async () => {
    const completedInstance = {
      ...mockProgrammeInstance,
      status: 'completed',
      completedAt: '2025-01-02T00:00:00Z',
      blocks: [{ ...mockProgrammeInstance.blocks[0], status: 'done' }],
    }
    playInstanceApi.completeBlock.mockResolvedValue(completedInstance)
    const store = useActivePasseStore()
    store.activeInstances = [{ ...mockProgrammeInstance }]
    const results = [{ playerId: 'p1', displayName: 'Alice', totalPoints: 10, maxPoints: 10, stepStates: [] }]
    await store.markBlockDone('inst-1', 'blk-1', results)
    expect(playInstanceApi.completeBlock).toHaveBeenCalledWith('inst-1', 'blk-1', results)
  })

  it('getBlocksForRange returns pending/in-progress blocks for a range', async () => {
    const store = useActivePasseStore()
    store.activeInstances = [{ ...mockProgrammeInstance }]
    const blocks = store.getBlocksForRange('r1')
    expect(blocks).toHaveLength(1)
    expect(blocks[0].blockId).toBe('blk-1')
  })
})
```

- [ ] **Step 2: Run test — expect FAIL**

```
cd smart-ground-ui && npm run test src/stores/__tests__/activePasseStore.test.js
```

- [ ] **Step 3: Rewrite `activePasseStore.js`**

```javascript
// src/stores/activePasseStore.js
import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as playInstanceApi from '@/services/playInstanceApi.js'

export const useActivePasseStore = defineStore('activePasse', () => {
  const activeInstances = ref([])
  const completedInstances = ref([])

  // ── Instance loading ──────────────────────────────────────────────────────

  const loadFromStorage = async () => {
    try {
      const instances = await playInstanceApi.listPlayInstances({ status: 'active' })
      activeInstances.value = instances
    } catch (e) {
      console.error('Failed to load active play instances:', e)
      activeInstances.value = []
    }
  }

  // ── Start instances ───────────────────────────────────────────────────────

  const startPasse = async (template, players) => {
    const instance = await playInstanceApi.startProgrammeInstance(template.id, players)
    activeInstances.value.push(instance)
    return instance
  }

  const startTraining = async (template, players) => {
    const instance = await playInstanceApi.startTrainingInstance(template.id, players)
    activeInstances.value.push(instance)
    return instance
  }

  // Competition instances stay in-memory only (no backend support yet)
  const startCompetition = (template, rotten) => {
    const generateUUID = () =>
      typeof globalThis !== 'undefined' && globalThis.crypto?.randomUUID
        ? globalThis.crypto.randomUUID()
        : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
            const r = (Math.random() * 16) | 0
            return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
          })

    const buildPhases = (passen) =>
      passen.map((passe, phaseIndex) => ({
        phaseIndex,
        passeId: passe.id,
        passeName: passe.name,
        status: phaseIndex === 0 ? 'active' : 'pending',
        blocks: (passe.serien ?? []).map((serie) => ({
          blockId: generateUUID(),
          serieId: serie.id,
          serieAlias: serie.name ?? serie.alias ?? serie.id,
          rangeId: serie.rangeId ?? null,
          rangeName: serie.rangeName ?? null,
          steps: serie.steps ?? [],
          status: 'pending',
          completedAt: null,
          result: null,
        })),
      }))

    const instance = {
      instanceId: generateUUID(),
      type: 'competition',
      templateId: template.id,
      templateName: template.name,
      name: template.name,
      passen: template.passen,
      rotten: rotten.map((r) => ({
        rotteId: r.rotteId,
        name: r.name,
        players: [...r.players],
        status: 'waiting',
        assignedRangeId: null,
        currentPhaseIndex: 0,
        phases: buildPhases(template.passen),
      })),
      startedAt: Date.now(),
      completedAt: null,
    }
    activeInstances.value.push(instance)
    return instance
  }

  // ── Block lifecycle ───────────────────────────────────────────────────────

  const markBlockInProgress = async (instanceId, blockId, rotteId) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    if (!inst) return

    if (inst.type === 'competition') {
      // Competition blocks are in-memory only
      const rotte = inst.rotten?.find((r) => r.rotteId === rotteId)
      const phase = rotte?.phases[rotte.currentPhaseIndex]
      const block = phase?.blocks.find((b) => b.blockId === blockId)
      if (block && block.status === 'pending') block.status = 'in_progress'
      return
    }

    try {
      const updated = await playInstanceApi.startBlock(instanceId, blockId)
      _mergeInstance(updated)
    } catch (e) {
      console.error('Failed to mark block in progress:', e)
    }
  }

  const markBlockDone = async (instanceId, blockId, playerResults, rotteId) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    if (!inst) return

    if (inst.type === 'competition') {
      // Competition blocks are in-memory only
      _completeCompetitionBlock(inst, blockId, playerResults, rotteId)
      return
    }

    try {
      const updated = await playInstanceApi.completeBlock(instanceId, blockId, playerResults)
      _mergeInstance(updated)
      if (updated.status === 'completed') {
        completedInstances.value.push(updated)
        activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== instanceId)
      }
    } catch (e) {
      console.error('Failed to complete block:', e)
    }
  }

  const stopInstance = async (instanceId) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    if (inst?.type === 'competition') {
      activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== instanceId)
      return
    }
    try {
      await playInstanceApi.stopPlayInstance(instanceId)
    } catch (e) {
      console.error('Failed to stop play instance:', e)
    }
    activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== instanceId)
  }

  const stopCompetition = (instanceId) => stopInstance(instanceId)

  // ── Competition-specific (in-memory only) ─────────────────────────────────

  const assignRotteToRange = (instanceId, rotteId, rangeId) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    if (!inst || inst.type !== 'competition') return
    const rotte = inst.rotten.find((r) => r.rotteId === rotteId)
    if (!rotte) return
    rotte.assignedRangeId = rangeId
    rotte.status = 'active'
  }

  const unassignRotte = (instanceId, rotteId) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    if (!inst || inst.type !== 'competition') return
    const rotte = inst.rotten.find((r) => r.rotteId === rotteId)
    if (!rotte) return
    rotte.assignedRangeId = null
    rotte.status = 'paused'
  }

  // ── Queries ───────────────────────────────────────────────────────────────

  const getBlocksForRange = (rangeId) => {
    const result = []
    for (const inst of activeInstances.value) {
      if (inst.type === 'training') {
        const blocks = inst.phases
          ? inst.phases.find((p) => p.status === 'active')?.blocks ?? []
          : []
        for (const block of blocks) {
          if (block.rangeId === rangeId && block.status !== 'done') {
            result.push({ ...block, instanceId: inst.instanceId, templateName: inst.templateName, players: inst.players, instanceType: 'training' })
          }
        }
      } else if (inst.type === 'competition') {
        for (const rotte of (inst.rotten ?? [])) {
          if (rotte.assignedRangeId !== rangeId || rotte.status !== 'active') continue
          const phase = rotte.phases[rotte.currentPhaseIndex]
          if (!phase) continue
          for (const block of phase.blocks) {
            if (block.rangeId === rangeId && block.status !== 'done') {
              result.push({ ...block, instanceId: inst.instanceId, templateName: inst.templateName, players: rotte.players, instanceType: 'competition', rotteName: rotte.name, rotteId: rotte.rotteId })
            }
          }
        }
      } else {
        for (const block of (inst.blocks ?? [])) {
          if (block.rangeId === rangeId && block.status !== 'done') {
            result.push({ ...block, instanceId: inst.instanceId, templateName: inst.templateName, players: inst.players, instanceType: 'passe' })
          }
        }
      }
    }
    return result
  }

  const getActiveCompetitionRotten = () => {
    const result = []
    for (const inst of activeInstances.value) {
      if (inst.type !== 'competition') continue
      for (const rotte of (inst.rotten ?? [])) {
        if (rotte.status === 'done') continue
        const phase = rotte.phases[rotte.currentPhaseIndex]
        if (!phase) continue
        result.push({ instanceId: inst.instanceId, instanceName: inst.templateName, rotteId: rotte.rotteId, rotteName: rotte.name, passeName: phase.passeName, players: rotte.players, blocks: phase.blocks })
      }
    }
    return result
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  function _mergeInstance(updated) {
    const idx = activeInstances.value.findIndex((i) => i.instanceId === updated.instanceId)
    if (idx > -1) activeInstances.value[idx] = updated
  }

  function _completeCompetitionBlock(inst, blockId, playerResults, rotteId) {
    const rotte = inst.rotten?.find((r) => r.rotteId === rotteId)
    if (!rotte) return
    const phase = rotte.phases[rotte.currentPhaseIndex]
    if (!phase) return
    const block = phase.blocks.find((b) => b.blockId === blockId)
    if (!block) return
    block.status = 'done'
    block.completedAt = Date.now()
    block.result = { playerResults }

    if (phase.blocks.every((b) => b.status === 'done')) {
      phase.status = 'done'
      const nextIndex = rotte.currentPhaseIndex + 1
      if (nextIndex < rotte.phases.length) {
        rotte.currentPhaseIndex = nextIndex
        rotte.phases[nextIndex].status = 'active'
      } else {
        rotte.status = 'done'
        if (inst.rotten.every((r) => r.status === 'done')) {
          completedInstances.value.push({ ...inst, completedAt: Date.now() })
          activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== inst.instanceId)
        }
      }
    }
  }

  return {
    activeInstances,
    completedInstances,
    loadFromStorage,
    startPasse,
    startTraining,
    startCompetition,
    getBlocksForRange,
    getActiveCompetitionRotten,
    markBlockInProgress,
    markBlockDone,
    stopInstance,
    stopCompetition,
    assignRotteToRange,
    unassignRotte,
  }
})
```

- [ ] **Step 4: Run tests**

```
cd smart-ground-ui && npm run test src/stores/__tests__/activePasseStore.test.js
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add smart-ground-ui/src/stores/activePasseStore.js smart-ground-ui/src/stores/__tests__/activePasseStore.test.js
git commit -m "[ui] rewrite activePasseStore — replace localStorage with /api/play-instances"
```

---

## Task 8: Update competition activePasseStore tests

> **Model suggestion:** claude-haiku-4-5-20251001

**Files:**
- Modify: `smart-ground-ui/src/stores/__tests__/activePasseStore.competition.test.js`

- [ ] **Step 1: Run existing tests to see failures**

```
cd smart-ground-ui && npm run test src/stores/__tests__/activePasseStore.competition.test.js
```

- [ ] **Step 2: Update tests**

Remove all `localStorage.getItem`/`setItem` assertions. Replace with checks on store state only. Competition instances remain in-memory so no API mocks are needed for competition-specific tests:

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useActivePasseStore } from '../activePasseStore.js'

vi.mock('@/services/playInstanceApi.js', () => ({
  listPlayInstances: vi.fn().mockResolvedValue([]),
  startProgrammeInstance: vi.fn(),
  startTrainingInstance: vi.fn(),
  startBlock: vi.fn(),
  completeBlock: vi.fn(),
  stopPlayInstance: vi.fn(),
}))

describe('activePasseStore — Competition', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('startCompetition creates in-memory competition instance', () => {
    const store = useActivePasseStore()
    const template = { id: 't1', name: 'WK 1', passen: [{ id: 'p1', name: 'Passe 1', serien: [{ id: 's1', name: 'S1', rangeId: 'r1', steps: [] }] }] }
    const rotten = [{ rotteId: 'ro1', name: 'Rotte A', players: [{ id: 'u1', displayName: 'Alice' }] }]
    const inst = store.startCompetition(template, rotten)
    expect(inst.type).toBe('competition')
    expect(inst.rotten).toHaveLength(1)
    expect(store.activeInstances).toHaveLength(1)
  })

  it('assignRotteToRange updates rotte state in-memory', () => {
    const store = useActivePasseStore()
    const template = { id: 't1', name: 'WK 1', passen: [] }
    const rotten = [{ rotteId: 'ro1', name: 'Rotte A', players: [] }]
    const inst = store.startCompetition(template, rotten)
    store.assignRotteToRange(inst.instanceId, 'ro1', 'r1')
    expect(store.activeInstances[0].rotten[0].assignedRangeId).toBe('r1')
    expect(store.activeInstances[0].rotten[0].status).toBe('active')
  })
})
```

- [ ] **Step 3: Run tests**

```
cd smart-ground-ui && npm run test src/stores/__tests__/activePasseStore.competition.test.js
```
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add smart-ground-ui/src/stores/__tests__/activePasseStore.competition.test.js
git commit -m "[ui] update competition store tests for API-based activePasseStore"
```

---

## Task 9: Wire SSE events to smartBoxStore and deviceStore

> **Model suggestion:** claude-sonnet-4-6

**Context:** `eventsApi.js` (`subscribeToEvents`) exists and connects to `/api/events`. The backend sends three event types:
- `smartbox.status` — `{ type, smartBoxId, status }` → update smartbox status in `smartBoxStore`
- `smartbox.synced` — `{ type, smartBoxId }` → update `configSynced` flag
- `device.health` — `{ type, deviceId, healthy, blocked }` → update device flags in `deviceStore`

The subscription should be started from `App.vue` (or `main.js`) when the user is authenticated, and cleaned up on logout.

**Files:**
- Modify: `smart-ground-ui/src/stores/smartBoxStore.js` (add SSE update handler)
- Modify: `smart-ground-ui/src/stores/deviceStore.js` (add SSE update handler)
- Modify: `smart-ground-ui/src/App.vue` (start/stop SSE subscription)

- [ ] **Step 1: Read current `smartBoxStore.js` and `deviceStore.js`**

```
# Read both files before editing
```

- [ ] **Step 2: Add `applySmartBoxEvent` to `smartBoxStore.js`**

Add this action to the `smartBoxStore` (inside `defineStore`):

```javascript
// In smartBoxStore.js — add inside the defineStore factory
const applySmartBoxEvent = (event) => {
  if (event.type === 'smartbox.status') {
    const box = smartboxes.value.find((b) => b.id === event.smartBoxId)
    if (box) box.status = event.status
  } else if (event.type === 'smartbox.synced') {
    const box = smartboxes.value.find((b) => b.id === event.smartBoxId)
    if (box) box.configSynced = true
  }
}
```

Return `applySmartBoxEvent` from the store.

- [ ] **Step 3: Add `applyDeviceEvent` to `deviceStore.js`**

Add this action to the `deviceStore` (inside `defineStore`):

```javascript
// In deviceStore.js — add inside the defineStore factory
const applyDeviceEvent = (event) => {
  if (event.type !== 'device.health') return
  for (const boxId of Object.keys(devices.value)) {
    const device = devices.value[boxId]?.find((d) => d.id === event.deviceId)
    if (device) {
      device.healthy = event.healthy
      device.blocked = event.blocked
      break
    }
  }
}
```

Return `applyDeviceEvent` from the store.

- [ ] **Step 4: Read `App.vue`**

Read the current content of `smart-ground-ui/src/App.vue`.

- [ ] **Step 5: Wire SSE in `App.vue`**

Add SSE subscription that starts when auth is confirmed and stops on logout. Add to `App.vue`:

```javascript
// In App.vue <script setup>
import { watch, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/authStore.js'
import { useSmartBoxStore } from '@/stores/smartBoxStore.js'
import { useDeviceStore } from '@/stores/deviceStore.js'
import { subscribeToEvents } from '@/services/eventsApi.js'

const authStore = useAuthStore()
const smartBoxStore = useSmartBoxStore()
const deviceStore = useDeviceStore()

let sseConnection = null

const startSSE = () => {
  if (sseConnection) return
  sseConnection = subscribeToEvents(
    (event) => {
      smartBoxStore.applySmartBoxEvent(event)
      deviceStore.applyDeviceEvent(event)
    },
    () => {
      // Retry after 5s on error
      sseConnection = null
      setTimeout(() => { if (authStore.isAuthenticated()) startSSE() }, 5000)
    },
  )
}

const stopSSE = () => {
  sseConnection?.close()
  sseConnection = null
}

// Watch auth state: start SSE when logged in, stop on logout
watch(() => authStore.isAuthenticated(), (authenticated) => {
  if (authenticated) startSSE()
  else stopSSE()
}, { immediate: true })

onUnmounted(stopSSE)
```

- [ ] **Step 6: Run tests**

```
cd smart-ground-ui && npm run test
```
Expected: all pass (SSE wiring doesn't have unit tests; verify manually when running the stack)

- [ ] **Step 7: Commit**

```bash
git add smart-ground-ui/src/stores/smartBoxStore.js smart-ground-ui/src/stores/deviceStore.js smart-ground-ui/src/App.vue
git commit -m "[ui] wire SSE events to smartBoxStore and deviceStore via eventsApi"
```

---

## Task 10: Initialize stores on app startup

> **Model suggestion:** claude-haiku-4-5-20251001

**Context:** Now that stores are API-backed, they need to load data on startup after login. Currently, several stores auto-load from localStorage on import. With async APIs, we need to explicitly `initialize()` them after authentication.

**Files:**
- Modify: `smart-ground-ui/src/router/index.js` (or appropriate entry point)

- [ ] **Step 1: Read `router/index.js`**

Read `smart-ground-ui/src/router/index.js`.

- [ ] **Step 2: Add post-login initialization hook**

In the router `beforeEach` guard, after confirming auth, initialize the data stores if not already loaded. Add to the navigation guard:

```javascript
// In router/index.js — inside the beforeEach that runs after auth check

// After confirming user is authenticated and routing to a non-login page:
if (authenticated && to.path !== '/login') {
  const { useGuestStore } = await import('@/stores/guestStore.js')
  const { usePasseStore } = await import('@/stores/passeStore.js')
  const { useActivePasseStore } = await import('@/stores/activePasseStore.js')

  const guestStore = useGuestStore()
  const passeStore = usePasseStore()
  const activePasseStore = useActivePasseStore()

  // Only initialize once per session (check if already loaded)
  if (guestStore.guests.length === 0 && !guestStore.isLoading) {
    guestStore.loadGuests().catch(console.error)
  }
  if (passeStore.savedSerien.length === 0) {
    passeStore.loadSerienFromStorage().catch(console.error)
    passeStore.loadPassenFromStorage().catch(console.error)
    passeStore.loadTrainingsFromStorage().catch(console.error)
  }
  if (activePasseStore.activeInstances.length === 0) {
    activePasseStore.loadFromStorage().catch(console.error)
  }
}
```

- [ ] **Step 3: Run full test suite**

```
cd smart-ground-ui && npm run test
```
Expected: all pass

- [ ] **Step 4: Commit**

```bash
git add smart-ground-ui/src/router/index.js
git commit -m "[ui] initialize API-backed stores on post-login navigation"
```

---

## Self-Review Checklist

**Spec coverage:**
- [x] Guest store → `/api/guests` (Task 4)
- [x] Abläufe (Serien) → `/api/ablaeufe` (Task 5)
- [x] Programme (Passen) → `/api/programmes` (Task 5)
- [x] Trainings → `/api/trainings` (Task 5/6)
- [x] PlayInstances → `/api/play-instances` (Task 7/8)
- [x] SSE events wired (Task 9)
- [x] Startup initialization (Task 10)

**Out of scope (documented):**
- `competitionEventStore.js` (Session + Rotten) — needs backend design work
- Play result history (`/api/play-results`) — no UI view for this yet

**Placeholder scan:** None found. All code blocks are complete.

**Type consistency:**
- `toApiStep` / `fromApiStep` used consistently in Task 5
- `toUiSerie` / `toUiPasse` / `toUiTraining` used consistently
- `applySmartBoxEvent` / `applyDeviceEvent` added to both stores and called from App.vue

---

**Plan saved to `docs/superpowers/plans/2026-06-02-ui-backend-wiring.md`.**

**Two execution options:**

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks
**2. Inline Execution** — execute tasks in this session using executing-plans

Which approach?
