# Wettkampf Detail — Tabbed Setup View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Rotten / Passen" tab bar to the SETUP phase of `WettkampfDetailView`, allow adding/removing global passen to the competition, and rename the "Veröffentlichen" button to "Wettkampf starten".

**Architecture:** New API functions in `wettkampfApi.js` wire to two new store actions in `competitionEventStore.js`. A new `PassePickerDropdown` component mirrors the existing `UserPickerDropdown` pattern. `WettkampfDetailView` gains a local `setupTab` ref and renders the Rotten or Passen section based on it.

**Tech Stack:** Vue 3 Composition API, Pinia, Vitest + @vue/test-utils, native fetch via `apiFetch`

---

## File Map

| Action | File |
|---|---|
| Modify | `src/services/wettkampfApi.js` |
| Modify | `src/stores/competitionEventStore.js` |
| Modify | `src/stores/__tests__/competitionEventStore.test.js` |
| Create | `src/components/competition/PassePickerDropdown.vue` |
| Modify | `src/views/admin/WettkampfDetailView.vue` |

---

## Task 1: API layer — add `addPasse` / `removePasse`

**Files:**
- Modify: `src/services/wettkampfApi.js`

- [ ] **Step 1: Add the two new export functions at the end of the file**

Open `src/services/wettkampfApi.js`. Append after the last export (`getProgress`):

```js
export const addPasse = (sessionId, passeId) =>
  apiFetch(`/sessions/${sessionId}/passen`, {
    method: 'POST',
    body: JSON.stringify({ passeId }),
  })

export const removePasse = (sessionId, passeId) =>
  apiFetch(`/sessions/${sessionId}/passen/${passeId}`, { method: 'DELETE' })
```

- [ ] **Step 2: Commit**

```bash
git add src/services/wettkampfApi.js
git commit -m "[ui] add addPasse/removePasse to wettkampfApi"
```

---

## Task 2: Store tests (write failing first)

**Files:**
- Modify: `src/stores/__tests__/competitionEventStore.test.js`

- [ ] **Step 1: Add `addPasse` and `removePasse` to the `vi.mock` factory**

In `src/stores/__tests__/competitionEventStore.test.js`, the `vi.mock` block currently lists functions. Add the two new ones:

Replace:
```js
vi.mock('@/services/wettkampfApi.js', () => ({
  createSession: vi.fn(),
  listSessions:  vi.fn(),
  patchStatus:   vi.fn(),
  deleteSession: vi.fn(),
  createGroup:   vi.fn(),
  updateGroup:   vi.fn(),
  deleteGroup:   vi.fn(),
  addMember:     vi.fn(),
  removeMember:  vi.fn(),
  patchMember:   vi.fn(),
}))
```

With:
```js
vi.mock('@/services/wettkampfApi.js', () => ({
  createSession: vi.fn(),
  listSessions:  vi.fn(),
  patchStatus:   vi.fn(),
  getSession:    vi.fn(),
  deleteSession: vi.fn(),
  createGroup:   vi.fn(),
  updateGroup:   vi.fn(),
  deleteGroup:   vi.fn(),
  addMember:     vi.fn(),
  removeMember:  vi.fn(),
  patchMember:   vi.fn(),
  addPasse:      vi.fn(),
  removePasse:   vi.fn(),
}))
```

- [ ] **Step 2: Append two new failing tests at the end of the `describe` block**

Add before the closing `})` of `describe('useCompetitionEventStore', ...)`:

```js
  it('addPasseToEvent calls addPasse API and appends to event.passen', async () => {
    const mockPasse = { id: 'p1', name: 'Passe 1', serien: [] }
    api.addPasse.mockResolvedValue(mockPasse)
    const store = useCompetitionEventStore()
    store.events = [mkSession({ passen: [] })]
    await store.addPasseToEvent('s1', 'p1')
    expect(api.addPasse).toHaveBeenCalledWith('s1', 'p1')
    expect(store.events[0].passen).toEqual([mockPasse])
  })

  it('removePasseFromEvent calls removePasse API and removes from event.passen', async () => {
    api.removePasse.mockResolvedValue(undefined)
    const store = useCompetitionEventStore()
    store.events = [mkSession({ passen: [{ id: 'p1', name: 'Passe 1', serien: [] }] })]
    await store.removePasseFromEvent('s1', 'p1')
    expect(api.removePasse).toHaveBeenCalledWith('s1', 'p1')
    expect(store.events[0].passen).toEqual([])
  })
```

- [ ] **Step 3: Run the new tests to verify they fail**

```bash
npm run test src/stores/__tests__/competitionEventStore.test.js
```

Expected: the two new tests fail with something like `store.addPasseToEvent is not a function`.

---

## Task 3: Store actions — implement `addPasseToEvent` / `removePasseFromEvent`

**Files:**
- Modify: `src/stores/competitionEventStore.js`

- [ ] **Step 1: Add the two new actions to the store**

In `src/stores/competitionEventStore.js`, add after `togglePlayerPaid` (before the `// ── Private` section):

```js
  // ── Passe management ──────────────────────────────────────────────────────

  const addPasseToEvent = async (eventId, passeId) => {
    const ev = getEvent(eventId)
    if (!ev) return
    const passe = await wettkampfApi.addPasse(eventId, passeId)
    ev.passen = [...(ev.passen ?? []), passe]
  }

  const removePasseFromEvent = async (eventId, passeId) => {
    await wettkampfApi.removePasse(eventId, passeId)
    const ev = getEvent(eventId)
    if (ev) ev.passen = (ev.passen ?? []).filter(p => p.id !== passeId)
  }
```

- [ ] **Step 2: Export the new actions from the store's return object**

The `return { ... }` block at the end of the store currently ends with `addPlayer, removePlayer, togglePlayerPaid`. Add the new actions:

Replace:
```js
    addRotte, removeRotte, renameRotte,
    addPlayer, removePlayer, togglePlayerPaid,
  }
```

With:
```js
    addRotte, removeRotte, renameRotte,
    addPlayer, removePlayer, togglePlayerPaid,
    addPasseToEvent, removePasseFromEvent,
  }
```

- [ ] **Step 3: Run tests to verify they pass**

```bash
npm run test src/stores/__tests__/competitionEventStore.test.js
```

Expected: all tests pass, including the two new ones.

- [ ] **Step 4: Commit**

```bash
git add src/stores/competitionEventStore.js src/stores/__tests__/competitionEventStore.test.js
git commit -m "[ui] add addPasseToEvent/removePasseFromEvent store actions"
```

---

## Task 4: Create `PassePickerDropdown` component

**Files:**
- Create: `src/components/competition/PassePickerDropdown.vue`

- [ ] **Step 1: Create the component**

Create `src/components/competition/PassePickerDropdown.vue` with the following content:

```vue
<template>
  <div ref="wrapperRef" class="picker-wrapper">
    <div class="picker-list">
      <div v-if="passen.length === 0" class="picker-empty">Keine Passen verfügbar</div>
      <div
        v-for="passe in passen"
        :key="passe.id"
        class="picker-item"
        @click="emit('select', passe)"
      >
        <span class="picker-name">{{ passe.name }}</span>
        <span class="picker-meta">{{ passe.serien?.length ?? 0 }} Serien</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

defineOptions({ name: 'PassePickerDropdown' })

const props = defineProps({
  passen: { type: Array, required: true },
})
const emit = defineEmits(['select', 'close'])

const wrapperRef = ref(null)

const onOutsideClick = (e) => {
  if (wrapperRef.value && !wrapperRef.value.contains(e.target)) {
    emit('close')
  }
}

onMounted(() => document.addEventListener('mousedown', onOutsideClick))
onUnmounted(() => document.removeEventListener('mousedown', onOutsideClick))
</script>

<style scoped>
.picker-wrapper {
  position: absolute;
  z-index: 50;
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 10px;
  box-shadow: var(--sg-shadow-lg);
  min-width: 220px;
  overflow: hidden;
}

.picker-list {
  max-height: 200px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.picker-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 14px;
  cursor: pointer;
  transition: background 0.1s;
}
.picker-item:hover { background: var(--sg-accent-tint); }

.picker-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--sg-brand);
}

.picker-meta {
  font-size: 11px;
  color: var(--sg-text-faint);
}

.picker-empty {
  padding: 12px;
  font-size: 12px;
  color: var(--sg-text-faint);
  text-align: center;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add src/components/competition/PassePickerDropdown.vue
git commit -m "[ui] add PassePickerDropdown component"
```

---

## Task 5: Update `WettkampfDetailView` — tabs, Passen tab, button rename

**Files:**
- Modify: `src/views/admin/WettkampfDetailView.vue`

### Step 1: Update imports and add new script state

- [ ] **Step 1a: Add new imports in `<script setup>`**

In `src/views/admin/WettkampfDetailView.vue`, the current imports are:

```js
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { useActivePasseStore } from '@/stores/activePasseStore.js'
import { useUserStore } from '@/stores/userStore.js'
import Icons from '@/components/Icons.vue'
import RotteEditorCard from '@/components/competition/RotteEditorCard.vue'
import ActiveCompetitionPanel from '@/components/competition/ActiveCompetitionPanel.vue'
import CompletedResultsPanel from '@/components/competition/CompletedResultsPanel.vue'
```

Replace with:

```js
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { useActivePasseStore } from '@/stores/activePasseStore.js'
import { useUserStore } from '@/stores/userStore.js'
import { usePasseStore } from '@/stores/passeStore.js'
import Icons from '@/components/Icons.vue'
import RotteEditorCard from '@/components/competition/RotteEditorCard.vue'
import PassePickerDropdown from '@/components/competition/PassePickerDropdown.vue'
import ActiveCompetitionPanel from '@/components/competition/ActiveCompetitionPanel.vue'
import CompletedResultsPanel from '@/components/competition/CompletedResultsPanel.vue'
```

- [ ] **Step 1b: Add `passeStore`, `setupTab`, `showPassePicker`, and derived passe computeds after the existing `availableUsers` computed**

After this block in `<script setup>`:
```js
const availableUsers = computed(() =>
  userStore.users
    .filter(u => !assignedUserIds.value.has(u.id))
    .map(u => ({ id: u.id, displayName: u.fullName || u.username || u.email || u.id }))
)
```

Add:
```js
const passeStore = usePasseStore()
const setupTab = ref('rotten')
const showPassePicker = ref(false)

const assignedPasseIds = computed(() =>
  new Set((event.value?.passen ?? []).map(p => p.id))
)

const availablePassen = computed(() =>
  passeStore.savedGlobalPassen.filter(p => !assignedPasseIds.value.has(p.id))
)

const handleAddPasse = async (passe) => {
  showPassePicker.value = false
  await store.addPasseToEvent(eventId.value, passe.id)
}
```

- [ ] **Step 1c: Load passen on mount**

The current `onMounted` is:
```js
onMounted(async () => {
  try { await userStore.loadUsers() } catch { /* error handled by userStore */ }
  if (store.events.length === 0) await store.loadEvents()
})
```

Replace with:
```js
onMounted(async () => {
  try { await userStore.loadUsers() } catch { /* error handled by userStore */ }
  if (store.events.length === 0) await store.loadEvents()
  if (passeStore.savedPassen.length === 0) await passeStore.loadPassenFromStorage()
})
```

### Step 2: Replace the SETUP template block

- [ ] **Step 2: Replace the entire SETUP template block**

The current SETUP block (lines 26–69 in the original file) is:

```vue
    <!-- ══ SETUP (Planung) ══ -->
    <template v-else-if="event.status?.toUpperCase() === 'SETUP'">
      <div class="content">
        <div class="info-bar">
          <span class="info-chip">{{ (event.passen?.length ?? 0) }} Passen</span>
          <span class="info-chip">{{ totalThrows }} Würfe</span>
          <span class="info-chip">{{ totalPlayers }} Schützen</span>
        </div>
        <div v-if="unpaidPlayers.length > 0" class="payment-warning">
          <Icons icon="alert" :size="14" color="rgba(246,173,85,0.9)" />
          {{ unpaidPlayers.length }} Schützen haben noch nicht bezahlt
        </div>
        <section class="section">
          <div class="section-header">
            <h2 class="section-title">Rotten</h2>
            <button class="add-rotte-btn" :disabled="(event.groups ?? []).length >= 8" @click="store.addRotte(eventId)">
              <Icons icon="plus" :size="13" /> Rotte hinzufügen
            </button>
          </div>
          <div v-if="(event.groups ?? []).length === 0" class="empty-rotten">
            <p>Noch keine Rotten. Füge mindestens eine Rotte hinzu.</p>
          </div>
          <div class="rotten-grid">
            <RotteEditorCard
              v-for="group in (event.groups ?? [])"
              :key="group.id"
              :rotte="toRotteShape(group)"
              :available-users="availableUsers"
              @rename="(name) => store.renameRotte(eventId, group.id, name)"
              @remove="confirmRemoveRotte(group)"
              @add-player="(user) => store.addPlayer(eventId, group.id, user)"
              @remove-player="(pid) => store.removePlayer(eventId, group.id, pid)"
              @toggle-paid="(pid) => store.togglePlayerPaid(eventId, group.id, pid)"
            />
          </div>
        </section>
        <div class="start-section">
          <div class="payment-total">{{ paidPlayers.length }}/{{ totalPlayers }} Schützen bezahlt</div>
          <button class="start-btn" :disabled="(event.groups ?? []).length === 0" @click="handleOpen">
            <Icons icon="play" :size="15" color="#fff" />
            Veröffentlichen
          </button>
        </div>
      </div>
    </template>
```

Replace with:

```vue
    <!-- ══ SETUP (Planung) ══ -->
    <template v-else-if="event.status?.toUpperCase() === 'SETUP'">
      <div class="content">
        <div class="info-bar">
          <span class="info-chip">{{ (event.passen?.length ?? 0) }} Passen</span>
          <span class="info-chip">{{ totalThrows }} Würfe</span>
          <span class="info-chip">{{ totalPlayers }} Schützen</span>
        </div>
        <div v-if="unpaidPlayers.length > 0" class="payment-warning">
          <Icons icon="alert" :size="14" color="rgba(246,173,85,0.9)" />
          {{ unpaidPlayers.length }} Schützen haben noch nicht bezahlt
        </div>

        <div class="tab-row">
          <button class="tab-btn" :class="{ active: setupTab === 'rotten' }" @click="setupTab = 'rotten'">Rotten</button>
          <button class="tab-btn" :class="{ active: setupTab === 'passen' }" @click="setupTab = 'passen'">Passen</button>
        </div>

        <!-- Rotten tab -->
        <section v-if="setupTab === 'rotten'" class="section">
          <div class="section-header">
            <h2 class="section-title">Rotten</h2>
            <button class="add-rotte-btn" :disabled="(event.groups ?? []).length >= 8" @click="store.addRotte(eventId)">
              <Icons icon="plus" :size="13" /> Rotte hinzufügen
            </button>
          </div>
          <div v-if="(event.groups ?? []).length === 0" class="empty-rotten">
            <p>Noch keine Rotten. Füge mindestens eine Rotte hinzu.</p>
          </div>
          <div class="rotten-grid">
            <RotteEditorCard
              v-for="group in (event.groups ?? [])"
              :key="group.id"
              :rotte="toRotteShape(group)"
              :available-users="availableUsers"
              @rename="(name) => store.renameRotte(eventId, group.id, name)"
              @remove="confirmRemoveRotte(group)"
              @add-player="(user) => store.addPlayer(eventId, group.id, user)"
              @remove-player="(pid) => store.removePlayer(eventId, group.id, pid)"
              @toggle-paid="(pid) => store.togglePlayerPaid(eventId, group.id, pid)"
            />
          </div>
        </section>

        <!-- Passen tab -->
        <section v-else-if="setupTab === 'passen'" class="section">
          <div class="section-header">
            <h2 class="section-title">Passen</h2>
            <div class="add-passe-wrap">
              <button
                class="add-rotte-btn"
                :disabled="availablePassen.length === 0"
                @click="showPassePicker = !showPassePicker"
              >
                <Icons icon="plus" :size="13" /> Passe hinzufügen
              </button>
              <PassePickerDropdown
                v-if="showPassePicker"
                :passen="availablePassen"
                @select="handleAddPasse"
                @close="showPassePicker = false"
              />
            </div>
          </div>
          <div v-if="(event.passen ?? []).length === 0" class="empty-rotten">
            <p>Noch keine Passen. Füge mindestens eine Passe hinzu.</p>
          </div>
          <div v-else class="passen-list">
            <div v-for="passe in (event.passen ?? [])" :key="passe.id" class="passe-row">
              <div class="passe-info">
                <span class="passe-name">{{ passe.name }}</span>
                <span class="passe-meta">{{ passe.serien?.length ?? 0 }} Serien</span>
              </div>
              <button class="remove-btn" @click="store.removePasseFromEvent(eventId, passe.id)">
                <Icons icon="x" :size="12" color="var(--sg-text-faint)" />
              </button>
            </div>
          </div>
        </section>

        <div class="start-section">
          <div class="payment-total">{{ paidPlayers.length }}/{{ totalPlayers }} Schützen bezahlt</div>
          <button class="start-btn" :disabled="(event.groups ?? []).length === 0" @click="handleOpen">
            <Icons icon="play" :size="15" color="#fff" />
            Wettkampf starten
          </button>
        </div>
      </div>
    </template>
```

### Step 3: Add CSS for tabs and Passen tab content

- [ ] **Step 3: Append new CSS rules inside `<style scoped>`**

Add these rules at the end of the `<style scoped>` block (before the closing `</style>`):

```css
/* ── Tabs ── */
.tab-row {
  display: flex;
  border-bottom: 1px solid var(--sg-border);
}

.tab-btn {
  padding: 9px 20px;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  color: var(--sg-text-faint);
  cursor: pointer;
  margin-bottom: -1px;
  transition: color 0.15s, border-color 0.15s;
}

.tab-btn.active {
  color: var(--sg-brand);
  border-bottom-color: var(--sg-accent);
}

.tab-btn:hover:not(.active) { color: var(--sg-text-muted); }

/* ── Passen tab ── */
.add-passe-wrap { position: relative; }

.passen-list { display: flex; flex-direction: column; gap: 8px; }

.passe-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 12px;
}

.passe-info { display: flex; flex-direction: column; gap: 2px; }

.passe-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--sg-brand);
}

.passe-meta {
  font-size: 12px;
  color: var(--sg-text-faint);
}

.remove-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}

.remove-btn:hover {
  background: rgba(229, 62, 62, 0.08);
  border-color: #e53e3e;
}
```

- [ ] **Step 4: Run the full test suite to confirm nothing is broken**

```bash
npm run test
```

Expected: all previously-passing tests still pass.

- [ ] **Step 5: Commit**

```bash
git add src/views/admin/WettkampfDetailView.vue
git commit -m "[ui] add Rotten/Passen tabs and rename button in WettkampfDetailView SETUP"
```

---

## Done

After all tasks complete, the SETUP phase of `WettkampfDetailView` will have:
- A "Rotten" / "Passen" tab bar (local state, no URL sync)
- The existing Rotten editor under the Rotten tab
- A new Passen tab showing assigned passen with remove buttons and an "add" picker
- The action button labelled "Wettkampf starten" instead of "Veröffentlichen"
