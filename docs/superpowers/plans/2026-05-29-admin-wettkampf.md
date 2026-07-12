# Admin Wettkampf Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a full admin Wettkampf management view with a dedicated competition event store (PLANNING → ACTIVE → COMPLETED lifecycle, Rotten + players + payment tracking), and simplify the shooter view to read-only.

**Architecture:** A new `competitionEventStore` (localStorage-persisted, independent from `passeStore`) manages competition events with pre-configured Rotten and per-player payment state. It bridges to `activePasseStore` only via `activeInstanceId` when a competition goes live. Two new admin views handle listing and detail editing.

**Tech Stack:** Vue 3 Composition API, Pinia, Vitest + @vue/test-utils, Vue Router 4, localStorage persistence

---

## File Map

| Action | Path | Responsibility |
|---|---|---|
| Create | `src/stores/competitionEventStore.js` | All competition event state + lifecycle actions |
| Create | `src/stores/__tests__/competitionEventStore.test.js` | Store unit tests |
| Create | `src/components/competition/PaymentChip.vue` | PAID/UNPAID toggle chip |
| Create | `src/components/competition/RotteEditorCard.vue` | Single Rotte editor (players, payment) |
| Create | `src/views/admin/WettkampfDetailView.vue` | Competition event detail (all lifecycle states) |
| Create | `src/views/admin/WettkampfListView.vue` | Tab list: Planung / Aktiv / Abgeschlossen |
| Modify | `src/router/index.js` | Add 2 new admin routes |
| Modify | `src/App.vue` | Update navMap + routeMap for new route |
| Modify | `src/views/shooter/CompetitionManagementView.vue` | Simplify to read-only active competitions |

---

## Task 1: competitionEventStore — implementation

**Files:**
- Create: `src/stores/competitionEventStore.js`

- [ ] **Step 1: Create the store file**

```js
// src/stores/competitionEventStore.js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useActivePasseStore } from './activePasseStore.js'

const STORAGE_KEY = 'sg_competition_events'

const uuid = () =>
  typeof globalThis !== 'undefined' && globalThis.crypto?.randomUUID
    ? globalThis.crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
        const r = (Math.random() * 16) | 0
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
      })

export const useCompetitionEventStore = defineStore('competitionEvent', () => {
  const events = ref([])

  const _save = () => localStorage.setItem(STORAGE_KEY, JSON.stringify(events.value))

  const loadFromStorage = () => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw) events.value = JSON.parse(raw)
    } catch { /* ignore malformed data */ }
  }

  const planningEvents = computed(() => events.value.filter(e => e.status === 'PLANNING'))
  const activeEvents = computed(() => events.value.filter(e => e.status === 'ACTIVE'))
  const completedEvents = computed(() => events.value.filter(e => e.status === 'COMPLETED'))

  const getEvent = (id) => events.value.find(e => e.id === id) ?? null

  const createEvent = (name, passen) => {
    const id = uuid()
    events.value.push({
      id,
      name,
      passen: [...passen],
      status: 'PLANNING',
      rotten: [],
      activeInstanceId: null,
      createdAt: Date.now(),
      startedAt: null,
      completedAt: null,
    })
    _save()
    return id
  }

  const updateEventName = (id, name) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    ev.name = name
    _save()
  }

  const addRotte = (id) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    const letters = 'ABCDEFGH'
    const name = `Rotte ${letters[ev.rotten.length] ?? ev.rotten.length + 1}`
    ev.rotten.push({ rotteId: uuid(), name, players: [] })
    _save()
  }

  const removeRotte = (id, rotteId) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    ev.rotten = ev.rotten.filter(r => r.rotteId !== rotteId)
    _save()
  }

  const renameRotte = (id, rotteId, name) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    const rotte = ev.rotten.find(r => r.rotteId === rotteId)
    if (rotte) { rotte.name = name; _save() }
  }

  const addPlayer = (id, rotteId) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    const rotte = ev.rotten.find(r => r.rotteId === rotteId)
    if (!rotte) return
    rotte.players.push({ id: uuid(), displayName: '', paid: false })
    _save()
  }

  const removePlayer = (id, rotteId, playerId) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    const rotte = ev.rotten.find(r => r.rotteId === rotteId)
    if (!rotte) return
    rotte.players = rotte.players.filter(p => p.id !== playerId)
    _save()
  }

  const updatePlayerName = (id, rotteId, playerId, displayName) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    const rotte = ev.rotten.find(r => r.rotteId === rotteId)
    const player = rotte?.players.find(p => p.id === playerId)
    if (player) { player.displayName = displayName; _save() }
  }

  const togglePlayerPaid = (id, rotteId, playerId) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    const rotte = ev.rotten.find(r => r.rotteId === rotteId)
    const player = rotte?.players.find(p => p.id === playerId)
    if (player) { player.paid = !player.paid; _save() }
  }

  const startEvent = (id) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    const activePasseStore = useActivePasseStore()
    const instance = activePasseStore.startCompetition(ev, ev.rotten)
    ev.activeInstanceId = instance.instanceId
    ev.status = 'ACTIVE'
    ev.startedAt = Date.now()
    _save()
  }

  const stopEvent = (id) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'ACTIVE') return
    const activePasseStore = useActivePasseStore()
    activePasseStore.stopInstance(ev.activeInstanceId)
    ev.status = 'CANCELLED'
    ev.activeInstanceId = null
    _save()
  }

  const checkAndCompleteEvent = (id) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'ACTIVE') return
    const activePasseStore = useActivePasseStore()
    const done = activePasseStore.completedInstances.find(
      i => i.instanceId === ev.activeInstanceId
    )
    if (done) {
      ev.status = 'COMPLETED'
      ev.completedAt = Date.now()
      ev.activeInstanceId = null
      _save()
    }
  }

  const deleteEvent = (id) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    events.value = events.value.filter(e => e.id !== id)
    _save()
  }

  loadFromStorage()

  return {
    events,
    planningEvents,
    activeEvents,
    completedEvents,
    getEvent,
    createEvent,
    updateEventName,
    addRotte,
    removeRotte,
    renameRotte,
    addPlayer,
    removePlayer,
    updatePlayerName,
    togglePlayerPaid,
    startEvent,
    stopEvent,
    checkAndCompleteEvent,
    deleteEvent,
  }
})
```

- [ ] **Step 2: Commit**

```bash
cd smart-ground-ui
git add src/stores/competitionEventStore.js
git commit -m "[ui] Add competitionEventStore with full lifecycle management"
```

---

## Task 2: competitionEventStore — tests (TDD)

**Files:**
- Create: `src/stores/__tests__/competitionEventStore.test.js`

- [ ] **Step 1: Write the test file**

```js
// src/stores/__tests__/competitionEventStore.test.js
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useCompetitionEventStore } from '../competitionEventStore.js'

const mockStartCompetition = vi.fn(() => ({ instanceId: 'inst-abc' }))
const mockStopInstance = vi.fn()
let mockCompletedInstances = []

vi.mock('../activePasseStore.js', () => ({
  useActivePasseStore: vi.fn(() => ({
    startCompetition: mockStartCompetition,
    stopInstance: mockStopInstance,
    get completedInstances() { return mockCompletedInstances },
  }))
}))

const mockPassen = [{ id: 'p1', name: 'Passe 1', serien: [] }]

describe('useCompetitionEventStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    mockCompletedInstances = []
    vi.clearAllMocks()
  })

  it('starts empty', () => {
    const store = useCompetitionEventStore()
    expect(store.events).toHaveLength(0)
  })

  it('creates a PLANNING event and returns its id', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Frühjahrspokal', mockPassen)
    expect(store.planningEvents).toHaveLength(1)
    expect(store.planningEvents[0].name).toBe('Frühjahrspokal')
    expect(store.planningEvents[0].status).toBe('PLANNING')
    expect(store.planningEvents[0].id).toBe(id)
    expect(store.planningEvents[0].passen).toEqual(mockPassen)
  })

  it('updates event name while in PLANNING', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Old Name', mockPassen)
    store.updateEventName(id, 'New Name')
    expect(store.getEvent(id).name).toBe('New Name')
  })

  it('does not update name when ACTIVE', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.startEvent(id)
    store.updateEventName(id, 'Should Not Apply')
    expect(store.getEvent(id).name).toBe('Test')
  })

  it('adds Rotten with auto-generated names A, B, C', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.addRotte(id)
    store.addRotte(id)
    store.addRotte(id)
    const rotten = store.getEvent(id).rotten
    expect(rotten).toHaveLength(3)
    expect(rotten[0].name).toBe('Rotte A')
    expect(rotten[1].name).toBe('Rotte B')
    expect(rotten[2].name).toBe('Rotte C')
  })

  it('renames a Rotte', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.addRotte(id)
    const rotteId = store.getEvent(id).rotten[0].rotteId
    store.renameRotte(id, rotteId, 'Spezialrotte')
    expect(store.getEvent(id).rotten[0].name).toBe('Spezialrotte')
  })

  it('removes a Rotte', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.addRotte(id)
    const rotteId = store.getEvent(id).rotten[0].rotteId
    store.removeRotte(id, rotteId)
    expect(store.getEvent(id).rotten).toHaveLength(0)
  })

  it('adds a player with paid: false by default', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.addRotte(id)
    const rotteId = store.getEvent(id).rotten[0].rotteId
    store.addPlayer(id, rotteId)
    const players = store.getEvent(id).rotten[0].players
    expect(players).toHaveLength(1)
    expect(players[0].paid).toBe(false)
    expect(players[0].displayName).toBe('')
  })

  it('updates player display name', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.addRotte(id)
    const rotteId = store.getEvent(id).rotten[0].rotteId
    store.addPlayer(id, rotteId)
    const playerId = store.getEvent(id).rotten[0].players[0].id
    store.updatePlayerName(id, rotteId, playerId, 'Hans Muster')
    expect(store.getEvent(id).rotten[0].players[0].displayName).toBe('Hans Muster')
  })

  it('toggles player paid status', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.addRotte(id)
    const rotteId = store.getEvent(id).rotten[0].rotteId
    store.addPlayer(id, rotteId)
    const playerId = store.getEvent(id).rotten[0].players[0].id
    expect(store.getEvent(id).rotten[0].players[0].paid).toBe(false)
    store.togglePlayerPaid(id, rotteId, playerId)
    expect(store.getEvent(id).rotten[0].players[0].paid).toBe(true)
    store.togglePlayerPaid(id, rotteId, playerId)
    expect(store.getEvent(id).rotten[0].players[0].paid).toBe(false)
  })

  it('removes a player', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.addRotte(id)
    const rotteId = store.getEvent(id).rotten[0].rotteId
    store.addPlayer(id, rotteId)
    store.addPlayer(id, rotteId)
    const playerId = store.getEvent(id).rotten[0].players[0].id
    store.removePlayer(id, rotteId, playerId)
    expect(store.getEvent(id).rotten[0].players).toHaveLength(1)
  })

  it('starts an event: sets ACTIVE, stores activeInstanceId', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.startEvent(id)
    const ev = store.getEvent(id)
    expect(ev.status).toBe('ACTIVE')
    expect(ev.activeInstanceId).toBe('inst-abc')
    expect(mockStartCompetition).toHaveBeenCalledOnce()
    expect(store.activeEvents).toHaveLength(1)
    expect(store.planningEvents).toHaveLength(0)
  })

  it('stops an event: sets CANCELLED, clears activeInstanceId', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.startEvent(id)
    store.stopEvent(id)
    const ev = store.getEvent(id)
    expect(ev.status).toBe('CANCELLED')
    expect(ev.activeInstanceId).toBeNull()
    expect(mockStopInstance).toHaveBeenCalledWith('inst-abc')
  })

  it('checkAndCompleteEvent transitions to COMPLETED when instance is done', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.startEvent(id)
    mockCompletedInstances = [{ instanceId: 'inst-abc' }]
    store.checkAndCompleteEvent(id)
    const ev = store.getEvent(id)
    expect(ev.status).toBe('COMPLETED')
    expect(ev.completedAt).not.toBeNull()
    expect(ev.activeInstanceId).toBeNull()
    expect(store.completedEvents).toHaveLength(1)
  })

  it('checkAndCompleteEvent does nothing when instance not yet done', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.startEvent(id)
    mockCompletedInstances = []
    store.checkAndCompleteEvent(id)
    expect(store.getEvent(id).status).toBe('ACTIVE')
  })

  it('deletes a PLANNING event', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.deleteEvent(id)
    expect(store.events).toHaveLength(0)
  })

  it('does not delete an ACTIVE event', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.startEvent(id)
    store.deleteEvent(id)
    expect(store.events).toHaveLength(1)
  })

  it('persists events to localStorage on every mutation', () => {
    const store = useCompetitionEventStore()
    store.createEvent('Persist Test', mockPassen)
    const raw = localStorage.getItem('sg_competition_events')
    const parsed = JSON.parse(raw)
    expect(parsed).toHaveLength(1)
    expect(parsed[0].name).toBe('Persist Test')
  })

  it('loads events from localStorage on init', () => {
    localStorage.setItem('sg_competition_events', JSON.stringify([{
      id: 'restored-id', name: 'Restored', passen: [], status: 'PLANNING',
      rotten: [], activeInstanceId: null, createdAt: 0, startedAt: null, completedAt: null,
    }]))
    const store = useCompetitionEventStore()
    expect(store.events).toHaveLength(1)
    expect(store.events[0].name).toBe('Restored')
  })
})
```

- [ ] **Step 2: Run the tests**

```bash
cd smart-ground-ui
npm run test src/stores/__tests__/competitionEventStore.test.js
```

Expected: all 17 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add src/stores/__tests__/competitionEventStore.test.js
git commit -m "[ui] Add competitionEventStore tests"
```

---

## Task 3: PaymentChip + RotteEditorCard components

**Files:**
- Create: `src/components/competition/PaymentChip.vue`
- Create: `src/components/competition/RotteEditorCard.vue`

- [ ] **Step 1: Create PaymentChip.vue**

```vue
<!-- src/components/competition/PaymentChip.vue -->
<template>
  <button
    class="payment-chip"
    :class="paid ? 'chip--paid' : 'chip--unpaid'"
    @click="emit('toggle')"
  >
    {{ paid ? 'Bezahlt' : 'Offen' }}
  </button>
</template>

<script setup>
defineProps({ paid: { type: Boolean, required: true } })
const emit = defineEmits(['toggle'])
</script>

<style scoped>
.payment-chip {
  font-size: 11px; font-weight: 700; font-family: inherit;
  border-radius: 8px; padding: 3px 10px;
  cursor: pointer; border: 1px solid transparent;
  transition: all 0.15s; white-space: nowrap;
}
.chip--paid {
  background: rgba(72,187,120,0.15);
  border-color: rgba(72,187,120,0.3);
  color: rgba(72,187,120,0.9);
}
.chip--unpaid {
  background: rgba(252,129,129,0.1);
  border-color: rgba(252,129,129,0.25);
  color: rgba(252,129,129,0.8);
}
</style>
```

- [ ] **Step 2: Create RotteEditorCard.vue**

```vue
<!-- src/components/competition/RotteEditorCard.vue -->
<template>
  <div class="rotte-card">
    <div class="rotte-header">
      <input
        v-model="localName"
        class="rotte-name-input"
        placeholder="Rotte benennen..."
        @blur="emit('rename', localName)"
      />
      <span class="payment-summary">{{ paidCount }}/{{ rotte.players.length }} bezahlt</span>
      <button class="icon-btn" @click="emit('remove')" title="Rotte löschen">
        <Icons icon="trash" :size="13" color="rgba(252,129,129,0.7)" />
      </button>
    </div>

    <div class="player-list">
      <div v-for="(player, idx) in rotte.players" :key="player.id" class="player-row">
        <span class="player-num">{{ idx + 1 }}.</span>
        <input
          class="player-name-input"
          :value="player.displayName"
          placeholder="Name..."
          maxlength="40"
          @input="emit('update-player-name', player.id, $event.target.value)"
        />
        <PaymentChip :paid="player.paid" @toggle="emit('toggle-paid', player.id)" />
        <button
          v-if="rotte.players.length > 1"
          class="icon-btn"
          @click="emit('remove-player', player.id)"
        >
          <Icons icon="x" :size="11" color="rgba(252,129,129,0.7)" />
        </button>
      </div>
    </div>

    <button class="add-player-btn" @click="emit('add-player')">
      + Schütze hinzufügen
    </button>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import Icons from '@/components/Icons.vue'
import PaymentChip from './PaymentChip.vue'

const props = defineProps({
  rotte: { type: Object, required: true },
})

const emit = defineEmits(['rename', 'remove', 'add-player', 'remove-player', 'update-player-name', 'toggle-paid'])

const localName = ref(props.rotte.name)

watch(() => props.rotte.name, (val) => { localName.value = val })

const paidCount = computed(() => props.rotte.players.filter(p => p.paid).length)
</script>

<style scoped>
.rotte-card {
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 14px;
  padding: 14px 16px;
  display: flex; flex-direction: column; gap: 12px;
}

.rotte-header {
  display: flex; align-items: center; gap: 10px;
}

.rotte-name-input {
  flex: 1; background: transparent;
  border: none; border-bottom: 1px solid rgba(255,255,255,0.15);
  color: #fff; font-size: 14px; font-weight: 700; font-family: inherit;
  padding: 2px 4px; outline: none;
}
.rotte-name-input:focus { border-bottom-color: rgba(79,195,247,0.5); }

.payment-summary {
  font-size: 11px; color: rgba(255,255,255,0.3); white-space: nowrap;
}

.player-list { display: flex; flex-direction: column; gap: 6px; }

.player-row {
  display: flex; align-items: center; gap: 8px;
}

.player-num {
  font-size: 12px; color: rgba(255,255,255,0.3); width: 20px; flex-shrink: 0;
}

.player-name-input {
  flex: 1;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 8px;
  color: #fff; font-size: 13px; font-family: inherit;
  padding: 6px 10px; outline: none;
}
.player-name-input:focus { border-color: rgba(79,195,247,0.3); }

.icon-btn {
  background: none; border: none; cursor: pointer;
  padding: 4px; border-radius: 6px;
  display: flex; align-items: center; flex-shrink: 0;
  transition: background 0.15s;
}
.icon-btn:hover { background: rgba(255,255,255,0.07); }

.add-player-btn {
  background: transparent;
  border: 1px dashed rgba(79,195,247,0.2);
  border-radius: 8px;
  color: rgba(79,195,247,0.5);
  font-size: 12px; font-family: inherit;
  padding: 7px; cursor: pointer; transition: all 0.15s;
}
.add-player-btn:hover {
  background: rgba(79,195,247,0.05);
  border-color: rgba(79,195,247,0.35);
  color: rgba(79,195,247,0.8);
}
</style>
```

- [ ] **Step 3: Commit**

```bash
git add src/components/competition/PaymentChip.vue src/components/competition/RotteEditorCard.vue
git commit -m "[ui] Add PaymentChip and RotteEditorCard components"
```

---

## Task 4: WettkampfDetailView — PLANNING state

**Files:**
- Create: `src/views/admin/WettkampfDetailView.vue`

This view handles all three lifecycle states. Build PLANNING first, add ACTIVE + COMPLETED in Task 5.

- [ ] **Step 1: Create the view (PLANNING state + scaffold for other states)**

```vue
<!-- src/views/admin/WettkampfDetailView.vue -->
<template>
  <div class="detail-view">

    <!-- Header -->
    <div class="view-header">
      <div class="header-left">
        <button class="back-btn" @click="router.push('/admin/wettkampf')">
          <Icons icon="chevronLeft" :size="16" color="rgba(255,255,255,0.6)" />
          Zurück
        </button>
        <div>
          <h1 class="view-title">{{ event?.name ?? '–' }}</h1>
          <span class="status-badge" :class="`badge-${event?.status?.toLowerCase()}`">
            {{ statusLabel }}
          </span>
        </div>
      </div>
    </div>

    <div v-if="!event" class="not-found">
      <p>Wettkampf nicht gefunden.</p>
    </div>

    <!-- ══ PLANNING ══ -->
    <template v-else-if="event.status === 'PLANNING'">
      <div class="content">

        <!-- Info bar -->
        <div class="info-bar">
          <span class="info-chip">{{ event.passen.length }} Passen</span>
          <span class="info-chip">{{ totalThrows }} Würfe</span>
          <span class="info-chip">{{ totalPlayers }} Schützen</span>
        </div>

        <!-- Payment summary warning -->
        <div v-if="unpaidPlayers.length > 0" class="payment-warning">
          <Icons icon="alert" :size="14" color="rgba(246,173,85,0.9)" />
          {{ unpaidPlayers.length }} Schützen haben noch nicht bezahlt
        </div>

        <!-- Rotten -->
        <section class="section">
          <div class="section-header">
            <h2 class="section-title">Rotten</h2>
            <button
              class="add-rotte-btn"
              :disabled="event.rotten.length >= 8"
              @click="store.addRotte(eventId)"
            >
              <Icons icon="plus" :size="13" color="rgba(79,195,247,0.8)" />
              Rotte hinzufügen
            </button>
          </div>

          <div v-if="event.rotten.length === 0" class="empty-rotten">
            <p>Noch keine Rotten. Füge mindestens eine Rotte hinzu.</p>
          </div>

          <div class="rotten-grid">
            <RotteEditorCard
              v-for="rotte in event.rotten"
              :key="rotte.rotteId"
              :rotte="rotte"
              @rename="(name) => store.renameRotte(eventId, rotte.rotteId, name)"
              @remove="confirmRemoveRotte(rotte)"
              @add-player="store.addPlayer(eventId, rotte.rotteId)"
              @remove-player="(pid) => store.removePlayer(eventId, rotte.rotteId, pid)"
              @update-player-name="(pid, name) => store.updatePlayerName(eventId, rotte.rotteId, pid, name)"
              @toggle-paid="(pid) => store.togglePlayerPaid(eventId, rotte.rotteId, pid)"
            />
          </div>
        </section>

        <!-- Payment warning modal -->
        <div v-if="showPaymentWarning" class="modal-overlay" @click.self="showPaymentWarning = false">
          <div class="warning-modal">
            <h3 class="modal-title">Nicht alle haben bezahlt</h3>
            <p class="modal-desc">Folgende Schützen haben noch nicht bezahlt:</p>
            <ul class="unpaid-list">
              <li v-for="p in unpaidPlayers" :key="p.id">{{ p.displayName || '(Kein Name)' }}</li>
            </ul>
            <div class="modal-actions">
              <button class="action-btn action-btn--cancel" @click="showPaymentWarning = false">Zurück</button>
              <button class="action-btn action-btn--start" @click="confirmStart">Trotzdem starten</button>
            </div>
          </div>
        </div>

        <!-- Start button -->
        <div class="start-section">
          <div class="payment-total">
            {{ paidPlayers.length }}/{{ totalPlayers }} Schützen bezahlt
          </div>
          <button
            class="start-btn"
            :disabled="event.rotten.length === 0"
            @click="handleStart"
          >
            <Icons icon="play" :size="15" color="#fff" />
            Wettkampf starten
          </button>
        </div>

      </div>
    </template>

    <!-- ══ ACTIVE ══ -->
    <template v-else-if="event.status === 'ACTIVE'">
      <ActiveCompetitionPanel
        :event="event"
        @stop="handleStop"
      />
    </template>

    <!-- ══ COMPLETED ══ -->
    <template v-else-if="event.status === 'COMPLETED'">
      <CompletedResultsPanel :event="event" />
    </template>

    <!-- ══ CANCELLED ══ -->
    <template v-else>
      <div class="content">
        <p class="cancelled-note">Dieser Wettkampf wurde abgebrochen.</p>
      </div>
    </template>

  </div>
</template>

<script setup>
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { useActivePasseStore } from '@/stores/activePasseStore.js'
import { usePasseStore } from '@/stores/passeStore.js'
import Icons from '@/components/Icons.vue'
import RotteEditorCard from '@/components/competition/RotteEditorCard.vue'
import ActiveCompetitionPanel from '@/components/competition/ActiveCompetitionPanel.vue'
import CompletedResultsPanel from '@/components/competition/CompletedResultsPanel.vue'

const props = defineProps({ id: { type: String, required: true } })
const router = useRouter()
const store = useCompetitionEventStore()
const activePasseStore = useActivePasseStore()
const passeStore = usePasseStore()

const eventId = computed(() => props.id)
const event = computed(() => store.getEvent(eventId.value))

const statusLabel = computed(() => {
  const map = { PLANNING: 'Planung', ACTIVE: 'Aktiv', COMPLETED: 'Abgeschlossen', CANCELLED: 'Abgebrochen' }
  return map[event.value?.status] ?? '–'
})

const stepCount = (steps) => {
  let c = 0
  for (const s of steps) {
    if (s.type === 'solo') c += 1
    else if (['pair', 'a_schuss', 'raffale'].includes(s.type)) c += 2
  }
  return c
}

const totalThrows = computed(() =>
  (event.value?.passen ?? []).reduce((sum, passe) =>
    sum + (passe.serien ?? []).reduce((s2, serie) => s2 + stepCount(serie.steps ?? []), 0), 0)
)

const totalPlayers = computed(() =>
  (event.value?.rotten ?? []).reduce((sum, r) => sum + r.players.length, 0)
)

const unpaidPlayers = computed(() =>
  (event.value?.rotten ?? []).flatMap(r => r.players.filter(p => !p.paid))
)

const paidPlayers = computed(() =>
  (event.value?.rotten ?? []).flatMap(r => r.players.filter(p => p.paid))
)

// ── Rotte removal ──────────────────────────────────────────────────────────
const confirmRemoveRotte = (rotte) => {
  if (rotte.players.length > 0) {
    if (!confirm(`Rotte "${rotte.name}" mit ${rotte.players.length} Schützen löschen?`)) return
  }
  store.removeRotte(eventId.value, rotte.rotteId)
}

// ── Start flow ─────────────────────────────────────────────────────────────
const showPaymentWarning = ref(false)

const handleStart = () => {
  if (unpaidPlayers.value.length > 0) {
    showPaymentWarning.value = true
  } else {
    confirmStart()
  }
}

const confirmStart = () => {
  showPaymentWarning.value = false
  store.startEvent(eventId.value)
}

// ── Stop ───────────────────────────────────────────────────────────────────
const handleStop = () => {
  if (confirm('Wettkampf wirklich abbrechen?')) {
    store.stopEvent(eventId.value)
  }
}

// ── Auto-complete watch ────────────────────────────────────────────────────
let completionInterval = null

onMounted(() => {
  completionInterval = setInterval(() => {
    if (event.value?.status === 'ACTIVE') {
      store.checkAndCompleteEvent(eventId.value)
    }
  }, 2000)
})

onUnmounted(() => clearInterval(completionInterval))
</script>

<style scoped>
.detail-view {
  display: flex; flex-direction: column; min-height: 100%;
  background: #1a1a2e; color: #fff;
}

.view-header {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: 24px 28px 20px; border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}

.header-left { display: flex; align-items: center; gap: 16px; }

.back-btn {
  display: flex; align-items: center; gap: 6px;
  background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1);
  border-radius: 10px; padding: 8px 14px;
  color: rgba(255,255,255,0.6); font-size: 13px; font-family: inherit;
  cursor: pointer; transition: background 0.15s; white-space: nowrap;
}
.back-btn:hover { background: rgba(255,255,255,0.09); }

.view-title { font-size: 22px; font-weight: 700; margin: 0 0 4px; }

.status-badge {
  font-size: 11px; font-weight: 700; border-radius: 8px; padding: 3px 10px;
}
.badge-planning { background: rgba(79,195,247,0.15); color: rgba(79,195,247,0.9); border: 1px solid rgba(79,195,247,0.25); }
.badge-active { background: rgba(246,173,85,0.15); color: rgba(246,173,85,0.9); border: 1px solid rgba(246,173,85,0.3); }
.badge-completed { background: rgba(72,187,120,0.15); color: rgba(72,187,120,0.9); border: 1px solid rgba(72,187,120,0.25); }
.badge-cancelled { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.4); border: 1px solid rgba(255,255,255,0.1); }

.content { flex: 1; overflow-y: auto; padding: 24px 28px 40px; display: flex; flex-direction: column; gap: 20px; }

.info-bar { display: flex; gap: 8px; flex-wrap: wrap; }

.info-chip {
  font-size: 12px; font-weight: 600; padding: 4px 12px;
  background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08);
  border-radius: 20px; color: rgba(255,255,255,0.5);
}

.payment-warning {
  display: flex; align-items: center; gap: 8px;
  background: rgba(246,173,85,0.08); border: 1px solid rgba(246,173,85,0.2);
  border-radius: 10px; padding: 10px 14px;
  font-size: 13px; color: rgba(246,173,85,0.9);
}

.section { display: flex; flex-direction: column; gap: 14px; }

.section-header { display: flex; align-items: center; justify-content: space-between; }

.section-title { font-size: 16px; font-weight: 700; margin: 0; }

.add-rotte-btn {
  display: flex; align-items: center; gap: 6px;
  background: rgba(79,195,247,0.08); border: 1px solid rgba(79,195,247,0.2);
  border-radius: 10px; padding: 7px 14px;
  color: rgba(79,195,247,0.8); font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.add-rotte-btn:hover:not(:disabled) { background: rgba(79,195,247,0.15); }
.add-rotte-btn:disabled { opacity: 0.3; cursor: not-allowed; }

.empty-rotten {
  padding: 24px; text-align: center;
  border: 1px dashed rgba(255,255,255,0.1); border-radius: 12px;
  color: rgba(255,255,255,0.25); font-size: 13px;
}

.rotten-grid { display: flex; flex-direction: column; gap: 12px; }

.start-section {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px;
  background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.07);
  border-radius: 14px;
}

.payment-total { font-size: 13px; color: rgba(255,255,255,0.4); }

.start-btn {
  display: flex; align-items: center; gap: 8px;
  background: rgba(79,195,247,0.2); border: 1px solid rgba(79,195,247,0.4);
  border-radius: 12px; padding: 11px 24px;
  color: #4fc3f7; font-size: 14px; font-weight: 700; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.start-btn:hover:not(:disabled) { background: rgba(79,195,247,0.3); }
.start-btn:disabled { opacity: 0.3; cursor: not-allowed; }

/* ── Payment warning modal ── */
.modal-overlay {
  position: fixed; inset: 0; z-index: 100;
  background: rgba(0,0,0,0.6); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center; padding: 20px;
}

.warning-modal {
  background: #1e2240; border: 1px solid rgba(246,173,85,0.3);
  border-radius: 18px; padding: 24px; max-width: 360px; width: 100%;
  display: flex; flex-direction: column; gap: 14px;
}

.modal-title { font-size: 16px; font-weight: 700; margin: 0; color: rgba(246,173,85,0.9); }
.modal-desc { font-size: 13px; color: rgba(255,255,255,0.5); margin: 0; }

.unpaid-list {
  margin: 0; padding: 0 0 0 16px;
  display: flex; flex-direction: column; gap: 4px;
}
.unpaid-list li { font-size: 13px; color: rgba(255,255,255,0.7); }

.modal-actions { display: flex; gap: 8px; }

.action-btn {
  flex: 1; padding: 10px; border-radius: 10px;
  font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; border: 1px solid transparent; transition: background 0.15s;
}
.action-btn--cancel {
  background: transparent; border-color: rgba(255,255,255,0.12); color: rgba(255,255,255,0.5);
}
.action-btn--cancel:hover { background: rgba(255,255,255,0.05); }
.action-btn--start {
  background: rgba(79,195,247,0.2); border-color: rgba(79,195,247,0.4); color: #4fc3f7;
}
.action-btn--start:hover { background: rgba(79,195,247,0.3); }

.not-found, .cancelled-note {
  padding: 40px 28px; text-align: center; color: rgba(255,255,255,0.3); font-size: 14px;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add src/views/admin/WettkampfDetailView.vue
git commit -m "[ui] Add WettkampfDetailView PLANNING state"
```

---

## Task 5: ActiveCompetitionPanel + CompletedResultsPanel

The detail view references two sub-components for ACTIVE and COMPLETED states. Create them now.

**Files:**
- Create: `src/components/competition/ActiveCompetitionPanel.vue`
- Create: `src/components/competition/CompletedResultsPanel.vue`

- [ ] **Step 1: Create ActiveCompetitionPanel.vue**

```vue
<!-- src/components/competition/ActiveCompetitionPanel.vue -->
<template>
  <div class="panel-content">
    <div class="panel-header">
      <h2 class="panel-title">Laufender Wettkampf</h2>
      <button class="stop-btn" @click="emit('stop')">
        <Icons icon="x" :size="13" color="rgba(252,129,129,0.8)" />
        Wettkampf abbrechen
      </button>
    </div>

    <div class="rotte-list">
      <div
        v-for="rotte in activeRotten"
        :key="rotte.rotteId"
        class="rotte-progress-card"
      >
        <div class="rp-header">
          <div class="rp-dot" :class="`dot-${rotte.status}`" />
          <span class="rp-name">{{ rotte.name }}</span>
          <span class="rp-badge" :class="`badge-${rotte.status}`">{{ rotteBadge(rotte.status) }}</span>
        </div>

        <div class="rp-players">
          <span v-for="p in rotte.players" :key="p.id" class="rp-player">{{ p.displayName || '–' }}</span>
        </div>

        <div class="rp-phase">
          {{ currentPhaseName(rotte) }}
        </div>

        <div class="rp-range-row">
          <span class="rp-range-label">Platz:</span>
          <select
            class="rp-range-select"
            :value="rotte.assignedRangeId ?? ''"
            @change="onRangeChange(rotte, $event.target.value)"
          >
            <option value="">— Kein Platz —</option>
            <option v-for="range in rangeStore.ranges" :key="range.id" :value="range.id">
              {{ range.name }}
            </option>
          </select>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import Icons from '@/components/Icons.vue'
import { useActivePasseStore } from '@/stores/activePasseStore.js'
import { useRangeStore } from '@/stores/rangeStore.js'

const props = defineProps({ event: { type: Object, required: true } })
const emit = defineEmits(['stop'])

const activePasseStore = useActivePasseStore()
const rangeStore = useRangeStore()

const liveInstance = computed(() =>
  activePasseStore.activeInstances.find(i => i.instanceId === props.event.activeInstanceId)
)

const activeRotten = computed(() => liveInstance.value?.rotten ?? [])

const currentPhaseName = (rotte) => {
  const phase = rotte.phases?.[rotte.currentPhaseIndex]
  return phase?.passeName ?? '–'
}

const rotteBadge = (status) => {
  const map = { active: 'Aktiv', waiting: 'Wartend', done: 'Fertig', paused: 'Pausiert' }
  return map[status] ?? status
}

const onRangeChange = (rotte, rangeId) => {
  if (!liveInstance.value) return
  if (rangeId) {
    activePasseStore.assignRotteToRange(liveInstance.value.instanceId, rotte.rotteId, rangeId)
  } else {
    activePasseStore.unassignRotte(liveInstance.value.instanceId, rotte.rotteId)
  }
}
</script>

<style scoped>
.panel-content { flex: 1; overflow-y: auto; padding: 24px 28px 40px; display: flex; flex-direction: column; gap: 20px; }

.panel-header { display: flex; align-items: center; justify-content: space-between; }

.panel-title { font-size: 18px; font-weight: 700; margin: 0; }

.stop-btn {
  display: flex; align-items: center; gap: 6px;
  background: rgba(252,129,129,0.08); border: 1px solid rgba(252,129,129,0.2);
  border-radius: 10px; padding: 8px 14px;
  color: rgba(252,129,129,0.8); font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.stop-btn:hover { background: rgba(252,129,129,0.14); }

.rotte-list { display: flex; flex-direction: column; gap: 12px; }

.rotte-progress-card {
  background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.08);
  border-radius: 14px; padding: 14px 16px; display: flex; flex-direction: column; gap: 10px;
}

.rp-header { display: flex; align-items: center; gap: 10px; }

.rp-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.rp-dot.dot-active { background: rgba(246,173,85,0.8); }
.rp-dot.dot-waiting { background: rgba(255,255,255,0.2); }
.rp-dot.dot-done { background: rgba(72,187,120,0.7); }
.rp-dot.dot-paused { background: rgba(255,255,255,0.25); }

.rp-name { font-size: 14px; font-weight: 700; flex: 1; }

.rp-badge { font-size: 10px; font-weight: 700; border-radius: 6px; padding: 2px 8px; }
.rp-badge.badge-active { background: rgba(246,173,85,0.15); color: rgba(246,173,85,0.9); }
.rp-badge.badge-waiting { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.4); }
.rp-badge.badge-done { background: rgba(72,187,120,0.12); color: rgba(72,187,120,0.8); }
.rp-badge.badge-paused { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.3); }

.rp-players { display: flex; flex-wrap: wrap; gap: 6px; }

.rp-player {
  font-size: 12px; padding: 2px 10px;
  background: rgba(255,255,255,0.05); border-radius: 20px; color: rgba(255,255,255,0.6);
}

.rp-phase { font-size: 12px; color: rgba(79,195,247,0.7); }

.rp-range-row { display: flex; align-items: center; gap: 10px; }

.rp-range-label { font-size: 12px; color: rgba(255,255,255,0.3); white-space: nowrap; }

.rp-range-select {
  flex: 1; background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.1);
  border-radius: 8px; color: #fff; font-size: 13px; font-family: inherit;
  padding: 6px 10px; outline: none; cursor: pointer;
}
</style>
```

- [ ] **Step 2: Create CompletedResultsPanel.vue**

```vue
<!-- src/components/competition/CompletedResultsPanel.vue -->
<template>
  <div class="panel-content">
    <h2 class="panel-title">Ergebnisse</h2>

    <div
      v-for="rotte in completedRotten"
      :key="rotte.rotteId"
      class="rotte-result-card"
    >
      <div class="rr-header">
        <span class="rr-name">{{ rotte.name }}</span>
      </div>

      <div
        v-for="player in rotte.players"
        :key="player.id"
        class="player-result-row"
      >
        <span class="pr-name">{{ player.displayName || '–' }}</span>
        <div class="pr-phases">
          <div v-for="phase in rotte.phases" :key="phase.phaseIndex" class="phase-result">
            <span class="phase-label">{{ phase.passeName }}</span>
            <span class="phase-pts">
              {{ phasePoints(phase, player.id) }}/{{ phaseMaxPoints(phase, player.id) }}
            </span>
          </div>
        </div>
        <span class="pr-total">{{ totalPoints(rotte, player.id) }}/{{ maxPoints(rotte, player.id) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useActivePasseStore } from '@/stores/activePasseStore.js'

const props = defineProps({ event: { type: Object, required: true } })
const activePasseStore = useActivePasseStore()

const completedInstance = computed(() =>
  activePasseStore.completedInstances.find(i => i.instanceId === props.event.activeInstanceId)
    ?? activePasseStore.completedInstances.findLast?.(i =>
        props.event.rotten.some(r => r.rotteId === i.rotten?.[0]?.rotteId)
      )
)

const completedRotten = computed(() => completedInstance.value?.rotten ?? props.event.rotten)

const phasePoints = (phase, playerId) => {
  let pts = 0
  for (const block of phase.blocks ?? []) {
    const pr = block.result?.playerResults?.find(r => r.playerId === playerId)
    if (pr) pts += pr.totalPoints ?? 0
  }
  return pts
}

const phaseMaxPoints = (phase, playerId) => {
  let pts = 0
  for (const block of phase.blocks ?? []) {
    const pr = block.result?.playerResults?.find(r => r.playerId === playerId)
    if (pr) pts += pr.maxPoints ?? 0
  }
  return pts
}

const totalPoints = (rotte, playerId) =>
  (rotte.phases ?? []).reduce((s, phase) => s + phasePoints(phase, playerId), 0)

const maxPoints = (rotte, playerId) =>
  (rotte.phases ?? []).reduce((s, phase) => s + phaseMaxPoints(phase, playerId), 0)
</script>

<style scoped>
.panel-content { flex: 1; overflow-y: auto; padding: 24px 28px 40px; display: flex; flex-direction: column; gap: 20px; }

.panel-title { font-size: 18px; font-weight: 700; margin: 0; }

.rotte-result-card {
  background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.08);
  border-radius: 14px; padding: 14px 16px; display: flex; flex-direction: column; gap: 10px;
}

.rr-header { padding-bottom: 8px; border-bottom: 1px solid rgba(79,195,247,0.12); }

.rr-name { font-size: 13px; font-weight: 700; color: rgba(79,195,247,0.8); text-transform: uppercase; letter-spacing: 0.4px; }

.player-result-row {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 6px 0; border-bottom: 1px solid rgba(255,255,255,0.05);
}
.player-result-row:last-child { border-bottom: none; }

.pr-name { font-size: 13px; font-weight: 600; min-width: 130px; color: rgba(255,255,255,0.8); }

.pr-phases { flex: 1; display: flex; flex-wrap: wrap; gap: 6px; }

.phase-result {
  display: flex; gap: 4px; font-size: 11px;
  background: rgba(255,255,255,0.04); border-radius: 6px; padding: 2px 8px;
}

.phase-label { color: rgba(255,255,255,0.3); }

.phase-pts { color: rgba(255,255,255,0.6); font-weight: 600; }

.pr-total { font-size: 13px; font-weight: 700; color: rgba(79,195,247,0.9); white-space: nowrap; }
</style>
```

- [ ] **Step 3: Commit**

```bash
git add src/components/competition/ActiveCompetitionPanel.vue src/components/competition/CompletedResultsPanel.vue
git commit -m "[ui] Add ActiveCompetitionPanel and CompletedResultsPanel"
```

---

## Task 6: WettkampfListView — admin tab list

**Files:**
- Create: `src/views/admin/WettkampfListView.vue`

- [ ] **Step 1: Create the view**

```vue
<!-- src/views/admin/WettkampfListView.vue -->
<template>
  <div class="list-view">
    <div class="view-header">
      <div>
        <h1 class="view-title">Wettkampf</h1>
        <p class="view-subtitle">Wettkämpfe planen, starten und auswerten</p>
      </div>
    </div>

    <!-- Tab toggle -->
    <div class="tab-bar">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        class="tab-btn"
        :class="{ active: activeTab === tab.id }"
        @click="activeTab = tab.id"
      >
        {{ tab.label }}
        <span class="tab-count">{{ tab.count }}</span>
      </button>
    </div>

    <div class="content">

      <!-- ══ PLANUNG ══ -->
      <template v-if="activeTab === 'planning'">
        <div class="event-list">
          <div
            v-for="ev in store.planningEvents"
            :key="ev.id"
            class="event-card"
            @click="router.push('/admin/wettkampf/' + ev.id)"
          >
            <div class="ec-main">
              <div class="ec-info">
                <span class="ec-name">{{ ev.name }}</span>
                <div class="ec-meta-row">
                  <span class="ec-meta">{{ ev.passen.length }} Passen</span>
                  <span class="ec-dot">·</span>
                  <span class="ec-meta">{{ ev.rotten.length }} Rotten</span>
                  <span class="ec-dot">·</span>
                  <span class="ec-meta">{{ totalPlayers(ev) }} Schützen</span>
                  <template v-if="unpaidCount(ev) > 0">
                    <span class="ec-dot">·</span>
                    <span class="ec-meta ec-meta--warn">{{ unpaidCount(ev) }} offen</span>
                  </template>
                </div>
              </div>
              <Icons icon="chevronRight" :size="14" color="rgba(255,255,255,0.2)" />
            </div>
            <div class="ec-actions" @click.stop>
              <button class="ec-delete-btn" @click="handleDelete(ev)">
                <Icons icon="trash" :size="13" color="rgba(252,129,129,0.6)" />
              </button>
            </div>
          </div>
        </div>

        <!-- Create panel -->
        <div v-if="creating" class="create-panel">
          <div class="create-title">Neuer Wettkampf</div>
          <div class="create-field">
            <label class="create-label">Name</label>
            <input
              v-model="newName"
              class="create-input"
              placeholder="z.B. Frühjahrspokal 2026"
              maxlength="60"
              @keydown.enter="confirmCreate"
            />
          </div>
          <div class="create-field">
            <label class="create-label">Passen auswählen <span class="create-count">{{ selectedPassen.length }} gewählt</span></label>
            <p v-if="availablePassen.length === 0" class="create-hint">Noch keine Passen vorhanden. Erstelle zuerst Passen unter "Passen".</p>
            <div v-else class="passen-picker">
              <div
                v-for="passe in availablePassen"
                :key="passe.id"
                class="picker-item"
                :class="{ selected: isSelected(passe.id) }"
                @click="togglePasse(passe)"
              >
                <div class="picker-check">
                  <span v-if="isSelected(passe.id)" class="picker-order">{{ selectedOrder(passe.id) }}</span>
                  <Icons v-else icon="plus" :size="11" color="rgba(255,255,255,0.3)" />
                </div>
                <span class="picker-name">{{ passe.name }}</span>
              </div>
            </div>
          </div>
          <div class="create-actions">
            <button class="action-btn action-btn--cancel" @click="cancelCreate">Abbrechen</button>
            <button
              class="action-btn action-btn--primary"
              :disabled="!newName.trim() || selectedPassen.length === 0"
              @click="confirmCreate"
            >Erstellen</button>
          </div>
        </div>

        <button v-if="!creating" class="new-btn" @click="creating = true">
          <Icons icon="plus" :size="15" color="rgba(79,195,247,0.8)" />
          Neuer Wettkampf
        </button>

        <div v-if="store.planningEvents.length === 0 && !creating" class="empty-state">
          <p>Noch keine Wettkämpfe geplant.</p>
          <p class="empty-hint">Klicke auf "Neuer Wettkampf" um loszulegen.</p>
        </div>
      </template>

      <!-- ══ AKTIV ══ -->
      <template v-else-if="activeTab === 'active'">
        <div class="event-list">
          <div
            v-for="ev in store.activeEvents"
            :key="ev.id"
            class="event-card event-card--active"
            @click="router.push('/admin/wettkampf/' + ev.id)"
          >
            <div class="ec-main">
              <div class="ec-info">
                <span class="ec-name">{{ ev.name }}</span>
                <div class="ec-meta-row">
                  <span class="ec-meta">{{ ev.rotten.length }} Rotten</span>
                  <span class="ec-dot">·</span>
                  <span class="ec-meta">{{ totalPlayers(ev) }} Schützen</span>
                </div>
              </div>
              <div class="ec-active-dot" />
              <Icons icon="chevronRight" :size="14" color="rgba(255,255,255,0.2)" />
            </div>
          </div>
        </div>
        <div v-if="store.activeEvents.length === 0" class="empty-state">
          <p>Keine aktiven Wettkämpfe.</p>
        </div>
      </template>

      <!-- ══ ABGESCHLOSSEN ══ -->
      <template v-else>
        <div class="event-list">
          <div
            v-for="ev in store.completedEvents"
            :key="ev.id"
            class="event-card"
            @click="router.push('/admin/wettkampf/' + ev.id)"
          >
            <div class="ec-main">
              <div class="ec-info">
                <span class="ec-name">{{ ev.name }}</span>
                <div class="ec-meta-row">
                  <span class="ec-meta">{{ new Date(ev.completedAt).toLocaleDateString('de-CH') }}</span>
                  <span class="ec-dot">·</span>
                  <span class="ec-meta">{{ totalPlayers(ev) }} Schützen</span>
                </div>
              </div>
              <Icons icon="chevronRight" :size="14" color="rgba(255,255,255,0.2)" />
            </div>
          </div>
        </div>
        <div v-if="store.completedEvents.length === 0" class="empty-state">
          <p>Noch keine abgeschlossenen Wettkämpfe.</p>
        </div>
      </template>

    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { usePasseStore } from '@/stores/passeStore.js'
import Icons from '@/components/Icons.vue'

const router = useRouter()
const store = useCompetitionEventStore()
const passeStore = usePasseStore()

const activeTab = ref('planning')

const tabs = computed(() => [
  { id: 'planning', label: 'Planung', count: store.planningEvents.length },
  { id: 'active', label: 'Aktiv', count: store.activeEvents.length },
  { id: 'completed', label: 'Abgeschlossen', count: store.completedEvents.length },
])

// ── Create flow ────────────────────────────────────────────────────────────
const creating = ref(false)
const newName = ref('')
const selectedPassen = ref([])

const availablePassen = computed(() =>
  passeStore.savedTrainings.filter(t => t.type === 'competition')
)

const isSelected = (id) => selectedPassen.value.some(p => p.id === id)

const selectedOrder = (id) => selectedPassen.value.findIndex(p => p.id === id) + 1

const togglePasse = (passe) => {
  const idx = selectedPassen.value.findIndex(p => p.id === passe.id)
  if (idx >= 0) selectedPassen.value = selectedPassen.value.filter(p => p.id !== passe.id)
  else selectedPassen.value = [...selectedPassen.value, passe]
}

const cancelCreate = () => {
  creating.value = false
  newName.value = ''
  selectedPassen.value = []
}

const confirmCreate = () => {
  if (!newName.value.trim() || selectedPassen.value.length === 0) return
  const id = store.createEvent(newName.value.trim(), selectedPassen.value)
  cancelCreate()
  router.push('/admin/wettkampf/' + id)
}

// ── Delete ─────────────────────────────────────────────────────────────────
const handleDelete = (ev) => {
  if (confirm(`"${ev.name}" löschen?`)) {
    store.deleteEvent(ev.id)
  }
}

// ── Helpers ────────────────────────────────────────────────────────────────
const totalPlayers = (ev) => ev.rotten.reduce((s, r) => s + r.players.length, 0)

const unpaidCount = (ev) => ev.rotten.reduce((s, r) => s + r.players.filter(p => !p.paid).length, 0)
</script>

<style scoped>
.list-view { display: flex; flex-direction: column; height: 100%; background: #1a1a2e; color: #fff; }

.view-header {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: 24px 28px 20px; border-bottom: 1px solid rgba(255,255,255,0.06); flex-shrink: 0;
}

.view-title { font-size: 22px; font-weight: 700; margin: 0 0 4px; }

.view-subtitle { font-size: 13px; color: rgba(255,255,255,0.3); margin: 0; }

.tab-bar {
  display: flex; gap: 8px; padding: 16px 28px;
  border-bottom: 1px solid rgba(255,255,255,0.06); flex-shrink: 0;
}

.tab-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 16px; border-radius: 10px;
  background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.08);
  color: rgba(255,255,255,0.4); font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.tab-btn.active {
  background: rgba(79,195,247,0.15); border-color: rgba(79,195,247,0.35); color: #4fc3f7;
}
.tab-btn:hover:not(.active) { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.6); }

.tab-count {
  font-size: 10px; font-weight: 700;
  background: rgba(255,255,255,0.1); border-radius: 10px; padding: 1px 6px;
}

.content { flex: 1; overflow-y: auto; padding: 20px 28px 40px; display: flex; flex-direction: column; gap: 10px; }

.event-list { display: flex; flex-direction: column; gap: 8px; }

.event-card {
  background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.08);
  border-radius: 14px; padding: 14px 16px; cursor: pointer;
  transition: border-color 0.15s; display: flex; flex-direction: column; gap: 8px;
}
.event-card:hover { border-color: rgba(79,195,247,0.25); }
.event-card--active { border-color: rgba(246,173,85,0.2); }

.ec-main { display: flex; align-items: center; gap: 10px; }

.ec-info { flex: 1; display: flex; flex-direction: column; gap: 4px; }

.ec-name { font-size: 15px; font-weight: 600; }

.ec-meta-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }

.ec-meta { font-size: 12px; color: rgba(255,255,255,0.3); }
.ec-meta--warn { color: rgba(246,173,85,0.8); }
.ec-dot { font-size: 12px; color: rgba(255,255,255,0.15); }

.ec-active-dot { width: 8px; height: 8px; border-radius: 50%; background: rgba(246,173,85,0.8); }

.ec-actions { display: flex; justify-content: flex-end; }

.ec-delete-btn {
  background: none; border: none; cursor: pointer;
  padding: 4px 8px; border-radius: 6px;
  display: flex; align-items: center; transition: background 0.15s;
}
.ec-delete-btn:hover { background: rgba(252,129,129,0.08); }

/* ── Create panel ── */
.create-panel {
  background: rgba(79,195,247,0.04); border: 1px solid rgba(79,195,247,0.2);
  border-radius: 16px; padding: 18px; display: flex; flex-direction: column; gap: 14px;
}

.create-title { font-size: 14px; font-weight: 700; color: rgba(79,195,247,0.9); }

.create-field { display: flex; flex-direction: column; gap: 6px; }

.create-label {
  font-size: 11px; font-weight: 600; color: rgba(255,255,255,0.3);
  text-transform: uppercase; letter-spacing: 0.5px;
  display: flex; align-items: center; gap: 8px;
}

.create-count {
  background: rgba(79,195,247,0.15); color: rgba(79,195,247,0.8);
  border-radius: 12px; padding: 2px 8px; font-size: 10px; font-weight: 700; text-transform: none;
}

.create-input {
  background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.1);
  border-radius: 10px; color: #fff; font-size: 14px; font-family: inherit;
  padding: 10px 12px; outline: none; transition: border-color 0.15s;
}
.create-input:focus { border-color: rgba(79,195,247,0.3); }

.create-hint { font-size: 12px; color: rgba(255,255,255,0.25); margin: 0; }

.passen-picker { display: flex; flex-direction: column; gap: 6px; }

.picker-item {
  display: flex; align-items: center; gap: 10px;
  padding: 9px 12px; background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.08); border-radius: 10px;
  cursor: pointer; transition: all 0.15s;
}
.picker-item.selected { background: rgba(79,195,247,0.08); border-color: rgba(79,195,247,0.3); }

.picker-check {
  width: 22px; height: 22px; border-radius: 50%;
  background: rgba(79,195,247,0.15);
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; color: rgba(79,195,247,0.9); flex-shrink: 0;
}

.picker-name { font-size: 13px; color: rgba(255,255,255,0.8); flex: 1; }

.create-actions {
  display: flex; gap: 8px; padding-top: 8px; border-top: 1px solid rgba(79,195,247,0.15);
}

.action-btn {
  flex: 1; padding: 10px; border-radius: 10px;
  font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; border: 1px solid transparent; transition: background 0.15s;
}
.action-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.action-btn--cancel { background: transparent; border-color: rgba(255,255,255,0.12); color: rgba(255,255,255,0.4); }
.action-btn--cancel:hover { background: rgba(255,255,255,0.05); }
.action-btn--primary { background: rgba(79,195,247,0.2); border-color: rgba(79,195,247,0.4); color: #4fc3f7; }
.action-btn--primary:hover:not(:disabled) { background: rgba(79,195,247,0.28); }

.new-btn {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 13px; background: transparent;
  border: 1.5px dashed rgba(79,195,247,0.25); border-radius: 14px;
  color: rgba(79,195,247,0.6); font-size: 14px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.new-btn:hover { background: rgba(79,195,247,0.06); border-color: rgba(79,195,247,0.4); color: rgba(79,195,247,0.9); }

.empty-state {
  padding: 32px; text-align: center; display: flex; flex-direction: column; gap: 6px;
}
.empty-state p { font-size: 14px; color: rgba(255,255,255,0.2); margin: 0; }
.empty-hint { font-size: 12px !important; color: rgba(255,255,255,0.12) !important; }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add src/views/admin/WettkampfListView.vue
git commit -m "[ui] Add WettkampfListView admin tab view"
```

---

## Task 7: Router + App.vue + sidebar navigation

**Files:**
- Modify: `src/router/index.js`
- Modify: `src/App.vue`

- [ ] **Step 1: Add new routes to router/index.js**

Add these two imports at the top of the imports block (after the existing imports):

```js
import WettkampfListView from '@/views/admin/WettkampfListView.vue';
import WettkampfDetailView from '@/views/admin/WettkampfDetailView.vue';
```

Add these two routes inside the `routes` array, after the existing `/competition` routes:

```js
{ path: '/admin/wettkampf', component: WettkampfListView, meta: { layout: 'admin' } },
{ path: '/admin/wettkampf/:id', component: WettkampfDetailView, props: true, meta: { layout: 'admin' } },
```

- [ ] **Step 2: Update App.vue navMap and routeMap**

In `src/App.vue`, update the `navMap` object (currently at lines 14–22). Change the `competition` entry:

```js
const navMap = {
  '/ranges': 'ranges',
  '/smartboxes': 'smartboxes',
  '/device-type-groups': 'device-type-groups',
  '/admin/wettkampf': 'competition',
  '/passen': 'passen',
  '/users': 'users',
  '/profile': 'profile',
};
```

Update the `routeMap` inside `handleNav` (currently at lines 28–35). Add `competition`:

```js
const routeMap = {
  ranges: '/ranges',
  smartboxes: '/smartboxes',
  'device-type-groups': '/device-type-groups',
  competition: '/admin/wettkampf',
  templates: '/deviceTypes',
  users: '/users',
};
```

- [ ] **Step 3: Verify the sidebar link works**

Start the dev server:

```bash
cd smart-ground-ui
npm run dev
```

Log in as admin (`admin@smartground.local` / `admin123`). Click "Wettkampf" in the sidebar — it should navigate to `/admin/wettkampf` and show the new list view with three tabs.

- [ ] **Step 4: Commit**

```bash
git add src/router/index.js src/App.vue
git commit -m "[ui] Wire admin Wettkampf routes and sidebar navigation"
```

---

## Task 8: Simplify shooter CompetitionManagementView to read-only

**Files:**
- Modify: `src/views/shooter/CompetitionManagementView.vue`

The shooter view at `/wettkampf` must become read-only: show only active competitions from `competitionEventStore` with Rotte progress. Remove all creation, template management, and Rotten setup controls.

- [ ] **Step 1: Replace the file contents**

```vue
<!-- src/views/shooter/CompetitionManagementView.vue -->
<template>
  <div class="competition-view">
    <div class="top-bar">
      <button class="back-btn" @click="router.push('/home')">
        <Icons icon="chevronLeft" :size="18" color="rgba(255,255,255,0.7)" />
      </button>
      <h1 class="page-title">Wettkampf</h1>
      <div class="top-bar-spacer" />
    </div>

    <div class="content">
      <template v-if="activeEvents.length > 0">
        <div
          v-for="ev in activeEvents"
          :key="ev.id"
          class="session-card"
        >
          <div class="session-header">
            <span class="session-name">{{ ev.name }}</span>
            <span class="session-meta">{{ ev.rotten.length }} Rotten</span>
          </div>

          <div class="rotte-list">
            <div
              v-for="rotte in getRotten(ev)"
              :key="rotte.rotteId"
              class="rotte-row"
            >
              <span class="rotte-status-dot" :class="`dot-${rotte.status ?? 'waiting'}`" />
              <span class="rotte-name">{{ rotte.name }}</span>
              <span class="rotte-passe">{{ currentPasse(rotte) }}</span>
              <span class="rotte-badge" :class="`badge-${rotte.status ?? 'waiting'}`">
                {{ badgeLabel(rotte.status) }}
              </span>
            </div>
          </div>

          <div class="player-summary">
            <span
              v-for="player in allPlayers(ev)"
              :key="player.id"
              class="player-chip"
            >
              {{ player.displayName || '–' }}
            </span>
          </div>
        </div>
      </template>

      <div v-else class="empty-block">
        <Icons icon="program" :size="36" color="rgba(255,255,255,0.07)" />
        <p>Kein aktiver Wettkampf</p>
        <p class="empty-hint">Der Admin startet den Wettkampf.</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { useActivePasseStore } from '@/stores/activePasseStore.js'
import Icons from '@/components/Icons.vue'

const router = useRouter()
const competitionEventStore = useCompetitionEventStore()
const activePasseStore = useActivePasseStore()

const activeEvents = computed(() => competitionEventStore.activeEvents)

const getRotten = (ev) => {
  const instance = activePasseStore.activeInstances.find(i => i.instanceId === ev.activeInstanceId)
  return instance?.rotten ?? ev.rotten
}

const currentPasse = (rotte) => {
  const phase = rotte.phases?.[rotte.currentPhaseIndex]
  return phase?.passeName ?? '–'
}

const badgeLabel = (status) => {
  const map = { active: 'Aktiv', waiting: 'Wartend', done: 'Fertig', paused: 'Pausiert' }
  return map[status] ?? 'Wartend'
}

const allPlayers = (ev) => ev.rotten.flatMap(r => r.players)
</script>

<style scoped>
.competition-view {
  flex: 1; display: flex; flex-direction: column; min-height: 0;
  background: #1a1a2e; color: #fff;
}

.top-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 14px 16px; border-bottom: 1px solid rgba(255,255,255,0.06); flex-shrink: 0;
}

.back-btn {
  background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.1);
  border-radius: 10px; width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; transition: background 0.15s;
}
.back-btn:hover { background: rgba(255,255,255,0.1); }

.page-title { font-size: 17px; font-weight: 700; margin: 0; }
.top-bar-spacer { flex: 1; }

.content { flex: 1; overflow-y: auto; padding: 16px 16px 40px; display: flex; flex-direction: column; gap: 12px; }

.session-card {
  background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08);
  border-radius: 16px; padding: 14px 16px; display: flex; flex-direction: column; gap: 10px;
}

.session-header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }

.session-name { font-size: 15px; font-weight: 600; }

.session-meta { font-size: 12px; color: rgba(255,255,255,0.3); }

.rotte-list { display: flex; flex-direction: column; gap: 6px; }

.rotte-row {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06); border-radius: 10px; font-size: 12px;
}

.rotte-status-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.rotte-status-dot.dot-waiting { background: rgba(255,255,255,0.2); }
.rotte-status-dot.dot-active { background: rgba(246,173,85,0.8); }
.rotte-status-dot.dot-done { background: rgba(72,187,120,0.7); }
.rotte-status-dot.dot-paused { background: rgba(255,255,255,0.25); }

.rotte-name { font-weight: 600; color: rgba(255,255,255,0.8); min-width: 52px; }

.rotte-passe { flex: 1; color: rgba(79,195,247,0.7); font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.rotte-badge {
  font-size: 10px; font-weight: 700; border-radius: 6px; padding: 2px 7px; flex-shrink: 0;
}
.rotte-badge.badge-active { background: rgba(246,173,85,0.15); color: rgba(246,173,85,0.9); }
.rotte-badge.badge-waiting { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.4); }
.rotte-badge.badge-done { background: rgba(72,187,120,0.12); color: rgba(72,187,120,0.8); }
.rotte-badge.badge-paused { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.3); }

.player-summary { display: flex; flex-wrap: wrap; gap: 6px; }

.player-chip {
  font-size: 11px; padding: 3px 10px;
  background: rgba(255,255,255,0.05); border-radius: 20px; color: rgba(255,255,255,0.5);
}

.empty-block {
  display: flex; flex-direction: column; align-items: center;
  gap: 8px; padding: 40px 16px; text-align: center;
}
.empty-block p { font-size: 14px; color: rgba(255,255,255,0.2); margin: 0; }
.empty-hint { font-size: 12px !important; color: rgba(255,255,255,0.12) !important; line-height: 1.5; }
</style>
```

- [ ] **Step 2: Run all tests to confirm nothing broke**

```bash
cd smart-ground-ui
npm run test
```

Expected: all existing tests pass. The new `competitionEventStore.test.js` also passes.

- [ ] **Step 3: Commit**

```bash
git add src/views/shooter/CompetitionManagementView.vue
git commit -m "[ui] Simplify shooter Wettkampf view to read-only active competitions"
```

---

## Self-Review Notes

- `WettkampfDetailView` uses a 2-second polling interval (`setInterval`) to detect when an `activePasseStore` instance completes and auto-transition the event to `COMPLETED`. This is intentional — the store is localStorage-based with no reactive cross-store subscription.
- `CompletedResultsPanel` reads from `activePasseStore.completedInstances` via `activeInstanceId`. Since the event clears `activeInstanceId` on completion, the panel includes a fallback `findLast` to locate the correct instance by Rotte ID matching.
- `RotteEditorCard` emits `update-player-name` on every `input` event (not `change`). This gives live updates. The store's `updatePlayerName` writes to localStorage on each keystroke — acceptable for this data size.
- The `navMap` in `App.vue` uses a prefix match for `/admin/wettkampf` — sub-routes like `/admin/wettkampf/:id` will not match. Update the `activeNav` computed to use `startsWith` if active-state highlighting on sub-routes is needed.
