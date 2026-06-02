# Competition: Assign Real Users to Rotten — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace anonymous free-text player entries in Rotten with real backend user assignments, preventing duplicates across Rotten and locking the display name once a user is selected.

**Architecture:** `competitionEventStore` gains a `userId` field on player objects. `WettkampfDetailView` loads backend users on mount, computes a globally-available (unassigned) user list, and passes it to each `RotteEditorCard`. A new `UserPickerDropdown` component handles inline search and selection.

**Tech Stack:** Vue 3 (Composition API, `<script setup>`), Pinia, Vitest + @vue/test-utils

---

## File Map

| File | Change |
|---|---|
| `src/stores/competitionEventStore.js` | `addPlayer` accepts user object; remove `updatePlayerName` |
| `src/stores/__tests__/competitionEventStore.test.js` | New — store unit tests |
| `src/components/competition/UserPickerDropdown.vue` | New — inline searchable user picker |
| `src/components/__tests__/UserPickerDropdown.test.js` | New — component tests |
| `src/components/competition/RotteEditorCard.vue` | Locked name display; wire picker; new `availableUsers` prop |
| `src/components/__tests__/RotteEditorCard.test.js` | New — component tests |
| `src/views/admin/WettkampfDetailView.vue` | Load users; compute `availableUsers`; update card bindings |

---

## Task 1: Update `competitionEventStore` — `addPlayer` + remove `updatePlayerName`

**Files:**
- Modify: `src/stores/competitionEventStore.js`
- Create: `src/stores/__tests__/competitionEventStore.test.js`

- [ ] **Step 1: Write the failing test**

```js
// src/stores/__tests__/competitionEventStore.test.js
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useCompetitionEventStore } from '../competitionEventStore.js'

describe('useCompetitionEventStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('addPlayer stores userId and displayName from user object', () => {
    const store = useCompetitionEventStore()
    const eventId = store.createEvent('Test', [])
    store.addRotte(eventId)
    const rotte = store.getEvent(eventId).rotten[0]
    const user = { id: 'user-uuid-1', displayName: 'Anna Müller' }

    store.addPlayer(eventId, rotte.rotteId, user)

    const player = store.getEvent(eventId).rotten[0].players[0]
    expect(player.userId).toBe('user-uuid-1')
    expect(player.displayName).toBe('Anna Müller')
    expect(player.paid).toBe(false)
    expect(player.id).toBeDefined()
  })

  it('does not expose updatePlayerName', () => {
    const store = useCompetitionEventStore()
    expect(store.updatePlayerName).toBeUndefined()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```
npm run test src/stores/__tests__/competitionEventStore.test.js
```

Expected: FAIL — `addPlayer` ignores the user arg, `updatePlayerName` still exists.

- [ ] **Step 3: Update `addPlayer` and remove `updatePlayerName` in `competitionEventStore.js`**

Replace the current `addPlayer` function:

```js
const addPlayer = (id, rotteId, user) => {
  const ev = getEvent(id)
  if (!ev || ev.status !== 'PLANNING') return
  const rotte = ev.rotten.find(r => r.rotteId === rotteId)
  if (!rotte) return
  rotte.players.push({ id: uuid(), userId: user.id, displayName: user.displayName, paid: false })
  _save()
}
```

Delete the entire `updatePlayerName` function body, and remove `updatePlayerName` from the `return` object at the bottom of the store.

- [ ] **Step 4: Run test to verify it passes**

```
npm run test src/stores/__tests__/competitionEventStore.test.js
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/stores/competitionEventStore.js src/stores/__tests__/competitionEventStore.test.js
git commit -m "[ui] Update addPlayer to accept user object, remove updatePlayerName"
```

---

## Task 2: Create `UserPickerDropdown.vue`

**Files:**
- Create: `src/components/competition/UserPickerDropdown.vue`
- Create: `src/components/__tests__/UserPickerDropdown.test.js`

- [ ] **Step 1: Write the failing test**

```js
// src/components/__tests__/UserPickerDropdown.test.js
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import UserPickerDropdown from '../competition/UserPickerDropdown.vue'

const users = [
  { id: 'u1', displayName: 'Anna Müller' },
  { id: 'u2', displayName: 'Bernd Koch' },
  { id: 'u3', displayName: 'Clara Zinn' },
]

describe('UserPickerDropdown', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('renders all users when search is empty', () => {
    const wrapper = mount(UserPickerDropdown, { props: { users } })
    const items = wrapper.findAll('.picker-item')
    expect(items).toHaveLength(3)
    expect(items[0].text()).toBe('Anna Müller')
  })

  it('filters users by search text (case-insensitive)', async () => {
    const wrapper = mount(UserPickerDropdown, { props: { users } })
    await wrapper.find('.picker-search').setValue('anna')
    const items = wrapper.findAll('.picker-item')
    expect(items).toHaveLength(1)
    expect(items[0].text()).toBe('Anna Müller')
  })

  it('emits select with user when a row is clicked', async () => {
    const wrapper = mount(UserPickerDropdown, { props: { users } })
    await wrapper.findAll('.picker-item')[1].trigger('click')
    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')[0][0]).toEqual({ id: 'u2', displayName: 'Bernd Koch' })
  })

  it('emits close on Escape keydown', async () => {
    const wrapper = mount(UserPickerDropdown, { props: { users } })
    await wrapper.find('.picker-search').trigger('keydown', { key: 'Escape' })
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('shows empty state when no users match search', async () => {
    const wrapper = mount(UserPickerDropdown, { props: { users } })
    await wrapper.find('.picker-search').setValue('zzznomatch')
    expect(wrapper.find('.picker-empty').exists()).toBe(true)
    expect(wrapper.findAll('.picker-item')).toHaveLength(0)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```
npm run test src/components/__tests__/UserPickerDropdown.test.js
```

Expected: FAIL — component file does not exist.

- [ ] **Step 3: Create `src/components/competition/UserPickerDropdown.vue`**

```vue
<template>
  <div class="picker-wrapper" ref="wrapperRef">
    <input
      v-model="search"
      class="picker-search"
      placeholder="Schütze suchen..."
      autofocus
      @keydown.escape="emit('close')"
    />
    <div class="picker-list">
      <div v-if="filtered.length === 0" class="picker-empty">Keine Schützen verfügbar</div>
      <div
        v-for="user in filtered"
        :key="user.id"
        class="picker-item"
        @click="emit('select', user)"
      >
        {{ user.displayName }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  users: { type: Array, required: true },
})
const emit = defineEmits(['select', 'close'])

const search = ref('')
const wrapperRef = ref(null)

const filtered = computed(() =>
  props.users.filter(u =>
    u.displayName.toLowerCase().includes(search.value.toLowerCase())
  )
)

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
  background: #fff;
  border: 1px solid #bee3f8;
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  min-width: 200px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.picker-search {
  border: none;
  border-bottom: 1px solid #e2e8f0;
  padding: 9px 12px;
  font-size: 13px;
  font-family: inherit;
  color: #2d3748;
  outline: none;
  background: #f7fafc;
}
.picker-search:focus { background: #fff; }

.picker-list {
  max-height: 180px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.picker-item {
  padding: 9px 12px;
  font-size: 13px;
  color: #2d3748;
  cursor: pointer;
  transition: background 0.1s;
}
.picker-item:hover { background: rgba(79, 195, 247, 0.08); color: #0288d1; }

.picker-empty {
  padding: 12px;
  font-size: 12px;
  color: #a0aec0;
  text-align: center;
}
</style>
```

- [ ] **Step 4: Run test to verify it passes**

```
npm run test src/components/__tests__/UserPickerDropdown.test.js
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/components/competition/UserPickerDropdown.vue src/components/__tests__/UserPickerDropdown.test.js
git commit -m "[ui] Add UserPickerDropdown for competition player selection"
```

---

## Task 3: Update `RotteEditorCard.vue`

**Files:**
- Modify: `src/components/competition/RotteEditorCard.vue`
- Create: `src/components/__tests__/RotteEditorCard.test.js`

- [ ] **Step 1: Write the failing test**

```js
// src/components/__tests__/RotteEditorCard.test.js
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import RotteEditorCard from '../competition/RotteEditorCard.vue'

const rotte = {
  rotteId: 'r1',
  name: 'Rotte A',
  players: [
    { id: 'p1', userId: 'u1', displayName: 'Anna Müller', paid: false },
    { id: 'p2', userId: 'u2', displayName: 'Bernd Koch', paid: true },
  ],
}
const availableUsers = [
  { id: 'u3', displayName: 'Clara Zinn' },
]

describe('RotteEditorCard', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shows locked display name span instead of editable input per player', () => {
    const wrapper = mount(RotteEditorCard, { props: { rotte, availableUsers } })
    const lockedNames = wrapper.findAll('.player-name-locked')
    expect(lockedNames).toHaveLength(2)
    expect(lockedNames[0].text()).toBe('Anna Müller')
    expect(wrapper.find('.player-name-input').exists()).toBe(false)
  })

  it('shows UserPickerDropdown when add button is clicked', async () => {
    const wrapper = mount(RotteEditorCard, { props: { rotte, availableUsers } })
    await wrapper.find('.add-player-btn').trigger('click')
    expect(wrapper.findComponent({ name: 'UserPickerDropdown' }).exists()).toBe(true)
  })

  it('emits add-player with user object when picker selects a user', async () => {
    const wrapper = mount(RotteEditorCard, { props: { rotte, availableUsers } })
    await wrapper.find('.add-player-btn').trigger('click')
    await wrapper.findComponent({ name: 'UserPickerDropdown' }).vm.$emit('select', availableUsers[0])
    expect(wrapper.emitted('add-player')).toBeTruthy()
    expect(wrapper.emitted('add-player')[0][0]).toEqual(availableUsers[0])
  })

  it('closes picker after user is selected', async () => {
    const wrapper = mount(RotteEditorCard, { props: { rotte, availableUsers } })
    await wrapper.find('.add-player-btn').trigger('click')
    await wrapper.findComponent({ name: 'UserPickerDropdown' }).vm.$emit('select', availableUsers[0])
    expect(wrapper.findComponent({ name: 'UserPickerDropdown' }).exists()).toBe(false)
  })

  it('closes picker when picker emits close', async () => {
    const wrapper = mount(RotteEditorCard, { props: { rotte, availableUsers } })
    await wrapper.find('.add-player-btn').trigger('click')
    await wrapper.findComponent({ name: 'UserPickerDropdown' }).vm.$emit('close')
    expect(wrapper.findComponent({ name: 'UserPickerDropdown' }).exists()).toBe(false)
  })

  it('disables add button when availableUsers is empty', () => {
    const wrapper = mount(RotteEditorCard, { props: { rotte, availableUsers: [] } })
    expect(wrapper.find('.add-player-btn').attributes('disabled')).toBeDefined()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```
npm run test src/components/__tests__/RotteEditorCard.test.js
```

Expected: FAIL — locked span doesn't exist, no `availableUsers` prop, picker not wired.

- [ ] **Step 3: Replace `RotteEditorCard.vue` with updated version**

```vue
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
      <button class="icon-btn" title="Rotte löschen" @click="emit('remove')">
        <Icons icon="trash" :size="13" color="rgba(252,129,129,0.7)" />
      </button>
    </div>

    <div class="player-list">
      <div v-for="(player, idx) in rotte.players" :key="player.id" class="player-row">
        <span class="player-num">{{ idx + 1 }}.</span>
        <span class="player-name-locked">{{ player.displayName }}</span>
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

    <div class="add-section">
      <button
        class="add-player-btn"
        :disabled="availableUsers.length === 0"
        :title="availableUsers.length === 0 ? 'Alle Schützen bereits zugewiesen' : ''"
        @click="showPicker = true"
      >
        + Schütze hinzufügen
      </button>
      <UserPickerDropdown
        v-if="showPicker"
        :users="availableUsers"
        @select="onPickUser"
        @close="showPicker = false"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import Icons from '@/components/Icons.vue'
import PaymentChip from './PaymentChip.vue'
import UserPickerDropdown from './UserPickerDropdown.vue'

const props = defineProps({
  rotte: { type: Object, required: true },
  availableUsers: { type: Array, required: true },
})

const emit = defineEmits(['rename', 'remove', 'add-player', 'remove-player', 'toggle-paid'])

const localName = ref(props.rotte.name)
const showPicker = ref(false)

watch(() => props.rotte.name, (val) => { localName.value = val })

const paidCount = computed(() => props.rotte.players.filter(p => p.paid).length)

const onPickUser = (user) => {
  emit('add-player', user)
  showPicker.value = false
}
</script>

<style scoped>
.rotte-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 14px 16px;
  display: flex; flex-direction: column; gap: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.rotte-header {
  display: flex; align-items: center; gap: 10px;
}

.rotte-name-input {
  flex: 1; background: transparent;
  border: none; border-bottom: 1px solid #e2e8f0;
  color: #1a1a2e; font-size: 14px; font-weight: 700; font-family: inherit;
  padding: 2px 4px; outline: none;
}
.rotte-name-input:focus { border-bottom-color: #4fc3f7; }

.payment-summary {
  font-size: 11px; color: #a0aec0; white-space: nowrap;
}

.player-list { display: flex; flex-direction: column; gap: 6px; }

.player-row {
  display: flex; align-items: center; gap: 8px;
}

.player-num {
  font-size: 12px; color: #a0aec0; width: 20px; flex-shrink: 0;
}

.player-name-locked {
  flex: 1;
  background: #f7fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  color: #2d3748; font-size: 13px;
  padding: 6px 10px;
}

.icon-btn {
  background: none; border: none; cursor: pointer;
  padding: 4px; border-radius: 6px;
  display: flex; align-items: center; flex-shrink: 0;
  transition: background 0.15s;
}
.icon-btn:hover { background: #fff5f5; }

.add-section {
  position: relative;
}

.add-player-btn {
  width: 100%;
  background: transparent;
  border: 1px dashed #bee3f8;
  border-radius: 8px;
  color: #0288d1;
  font-size: 12px; font-family: inherit;
  padding: 7px; cursor: pointer; transition: all 0.15s;
}
.add-player-btn:hover:not(:disabled) {
  background: rgba(79,195,247,0.06);
  border-color: #4fc3f7;
}
.add-player-btn:disabled {
  opacity: 0.35; cursor: not-allowed;
  border-color: #e2e8f0; color: #a0aec0;
}
</style>
```

- [ ] **Step 4: Run test to verify it passes**

```
npm run test src/components/__tests__/RotteEditorCard.test.js
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/components/competition/RotteEditorCard.vue src/components/__tests__/RotteEditorCard.test.js
git commit -m "[ui] Update RotteEditorCard: locked names, inline user picker"
```

---

## Task 4: Update `WettkampfDetailView.vue`

**Files:**
- Modify: `src/views/admin/WettkampfDetailView.vue`

> Note: The backend `/users` endpoint returns user objects with a `username` field (per CLAUDE.md store documentation). The `availableUsers` computed maps each user to `{ id, displayName: u.username }` so downstream components always work with a consistent `displayName` field.

- [ ] **Step 1: Add `useUserStore` import**

In `<script setup>`, add after the existing store imports:

```js
import { useUserStore } from '@/stores/userStore.js'
```

And after `const activePasseStore = useActivePasseStore()`:

```js
const userStore = useUserStore()
```

- [ ] **Step 2: Load users on mount**

Replace the existing `onMounted` block:

```js
onMounted(async () => {
  await userStore.loadUsers()
  completionInterval = setInterval(() => {
    if (event.value?.status === 'ACTIVE') {
      store.checkAndCompleteEvent(eventId.value)
    }
  }, 2000)
})
```

- [ ] **Step 3: Add `availableUsers` computed**

Add after the `paidPlayers` computed:

```js
const assignedUserIds = computed(() =>
  new Set((event.value?.rotten ?? []).flatMap(r => r.players.map(p => p.userId)))
)

const availableUsers = computed(() =>
  userStore.users
    .filter(u => !assignedUserIds.value.has(u.id))
    .map(u => ({ id: u.id, displayName: u.username }))
)
```

- [ ] **Step 4: Update `RotteEditorCard` usage in the template**

Find the existing `<RotteEditorCard ... />` block and replace it with:

```vue
<RotteEditorCard
  v-for="rotte in event.rotten"
  :key="rotte.rotteId"
  :rotte="rotte"
  :available-users="availableUsers"
  @rename="(name) => store.renameRotte(eventId, rotte.rotteId, name)"
  @remove="confirmRemoveRotte(rotte)"
  @add-player="(user) => store.addPlayer(eventId, rotte.rotteId, user)"
  @remove-player="(pid) => store.removePlayer(eventId, rotte.rotteId, pid)"
  @toggle-paid="(pid) => store.togglePlayerPaid(eventId, rotte.rotteId, pid)"
/>
```

(`@update-player-name` is removed — `updatePlayerName` no longer exists in the store.)

- [ ] **Step 5: Run the full test suite**

```
npm run test
```

Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/views/admin/WettkampfDetailView.vue
git commit -m "[ui] Wire availableUsers into WettkampfDetailView for real user assignment"
```
