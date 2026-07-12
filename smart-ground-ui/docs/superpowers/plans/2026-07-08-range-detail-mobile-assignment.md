# RangeDetailView Mobile Device Assignment — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make device assignment work on phones by adding a tap-to-assign picker, and remove the layout collapse that makes the existing drag unusable below 640px.

**Architecture:** An empty position's slot becomes a real `<button>` that opens a `DeviceSearchModal` listing unassigned devices; selecting one calls the same `handleDropOnPosition` the drag already uses. Below 640px the drag tray is not rendered at all, which structurally removes the 256px flex sibling that collapses `.detail-content` to its padding. The drag auto-scroll switches from a brittle `querySelector` to a template ref.

**Tech Stack:** Vue 3.5 (`<script setup>`), Pinia 3, Vitest 4 + @vue/test-utils, no new dependencies.

Design spec: `docs/superpowers/specs/2026-07-08-range-detail-mobile-assignment-design.md`

## Global Constraints

- `<script setup>` + Composition API only. No Options API.
- `@/` alias for all imports within `src/`.
- No direct API calls in components — go through store → service.
- Design tokens only (`--sg-*` from `assets/main.css`). No hard-coded colors in new CSS.
- German display labels; English identifiers, English inline comments, English tests.
- Breakpoint is exactly `640px` (`(max-width: 640px)`), matching `SerieDrawer.vue` and `GlobalPasseDrawer.vue`.
- No new npm dependencies.
- Commit messages: `[ui] short description`.
- `npm run lint:check` clean and `npm run test` green before each commit.

### Environment facts verified before writing this plan

- `vitest.config.js` uses `environment: 'jsdom'` with **no setup file**.
- **`window.matchMedia` is `undefined` in this jsdom environment.** Verified by probe. Therefore `useMediaQuery` MUST guard for its absence, or every test that mounts `RangeDetailView` will throw.
- `@pinia/testing` is **not** installed. Mock stores with `vi.mock('@/stores/xxxStore.js', ...)`, following `src/views/__tests__/PassenAdminView.test.js`.
- `rangeStore.assignDeviceToPosition(rangeId, positionId, deviceId)` already exists (`src/stores/rangeStore.js:122`). No new store action or API call is needed.

### Correction to the spec

The spec's testing section says to test `Enter` and `Space` on the empty slot. **Do not.** jsdom does not synthesize a `click` event from a `keydown` on a native `<button>`. Asserting `trigger('keydown.enter')` emits `assign-device` would fail, and adding a manual `@keydown.enter` handler to a `<button>` would double-fire in a real browser. The correct assertion is that the element **is** a native `<button>`; keyboard activation is then guaranteed by the browser. Task 3 does this.

---

### Task 1: `useMediaQuery` composable

**Files:**
- Create: `src/composables/useMediaQuery.js`
- Test: `src/composables/__tests__/useMediaQuery.test.js`

**Interfaces:**
- Consumes: nothing.
- Produces: `useMediaQuery(query: string) => Ref<boolean>` — named export. Returns `false` when `window.matchMedia` is unavailable (jsdom, SSR), so the desktop layout is the safe default.

- [ ] **Step 1: Write the failing test**

Create `src/composables/__tests__/useMediaQuery.test.js`:

```javascript
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { useMediaQuery } from '@/composables/useMediaQuery.js'

const originalMatchMedia = window.matchMedia

function stubMatchMedia(matches) {
  const listeners = []
  const mql = {
    matches,
    media: '',
    addEventListener: (_evt, cb) => listeners.push(cb),
    removeEventListener: vi.fn(),
  }
  window.matchMedia = vi.fn(() => mql)
  return { mql, listeners }
}

function mountWith(query) {
  let result
  const wrapper = mount(defineComponent({
    setup() { result = useMediaQuery(query) },
    template: '<div />',
  }))
  return { wrapper, result: () => result }
}

afterEach(() => {
  window.matchMedia = originalMatchMedia
  vi.restoreAllMocks()
})

describe('useMediaQuery', () => {
  it('returns false when matchMedia is unavailable', () => {
    // jsdom has no matchMedia; this is the guard that keeps view tests alive
    window.matchMedia = undefined
    const { result } = mountWith('(max-width: 640px)')
    expect(result().value).toBe(false)
  })

  it('reflects the initial match state', () => {
    stubMatchMedia(true)
    const { result } = mountWith('(max-width: 640px)')
    expect(result().value).toBe(true)
  })

  it('updates when the media query changes', async () => {
    const { listeners } = stubMatchMedia(false)
    const { result } = mountWith('(max-width: 640px)')
    expect(result().value).toBe(false)
    listeners.forEach((cb) => cb({ matches: true }))
    expect(result().value).toBe(true)
  })

  it('removes its listener on unmount', () => {
    const { mql } = stubMatchMedia(false)
    const { wrapper } = mountWith('(max-width: 640px)')
    wrapper.unmount()
    expect(mql.removeEventListener).toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/composables/__tests__/useMediaQuery.test.js`
Expected: FAIL — `Failed to resolve import "@/composables/useMediaQuery.js"`

- [ ] **Step 3: Write minimal implementation**

Create `src/composables/useMediaQuery.js`:

```javascript
import { ref, onUnmounted } from 'vue';

/**
 * Reactive `matchMedia` result. Returns false when matchMedia is unavailable
 * (jsdom, SSR) so callers fall back to the desktop layout.
 */
export function useMediaQuery(query) {
  const supported = typeof window !== 'undefined' && typeof window.matchMedia === 'function';
  const mql = supported ? window.matchMedia(query) : null;
  const matches = ref(mql ? mql.matches : false);

  const update = (event) => { matches.value = event.matches; };
  if (mql) mql.addEventListener('change', update);

  onUnmounted(() => { mql?.removeEventListener('change', update); });

  return matches;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test src/composables/__tests__/useMediaQuery.test.js`
Expected: PASS — 4 tests

- [ ] **Step 5: Commit**

```bash
git add src/composables/useMediaQuery.js src/composables/__tests__/useMediaQuery.test.js
git commit -m "[ui] add useMediaQuery composable with matchMedia guard"
```

---

### Task 2: `DeviceSearchModal` component

**Files:**
- Create: `src/components/DeviceSearchModal.vue`
- Test: `src/components/__tests__/DeviceSearchModal.test.js`

**Interfaces:**
- Consumes: `TypeChip.vue` (props: `type: String`).
- Produces: component `DeviceSearchModal` with prop `devices: Array` (required) and emits `select` (payload: the device object) and `close` (no payload). Row class is `.device-row`; empty state class is `.empty-hint`; backdrop class is `.modal-backdrop`.

Device objects have the shape produced by `DeviceMapper`: `{ id, alias, smartBoxId, deviceType, groupName, rangeId }`.

- [ ] **Step 1: Write the failing test**

Create `src/components/__tests__/DeviceSearchModal.test.js`:

```javascript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DeviceSearchModal from '../DeviceSearchModal.vue'

const devices = [
  { id: 'd1', alias: 'Werfer 1', smartBoxId: 'aaaaaaaa-1111', deviceType: 'THROWER', groupName: null },
  { id: 'd2', alias: 'Werfer 2', smartBoxId: 'bbbbbbbb-2222', deviceType: 'THROWER', groupName: 'Wurfmaschine' },
  { id: 'd3', alias: 'Lampe',    smartBoxId: null,            deviceType: 'LED',     groupName: null },
]

describe('DeviceSearchModal', () => {
  it('renders all devices when search is empty', () => {
    const wrapper = mount(DeviceSearchModal, { props: { devices } })
    expect(wrapper.findAll('.device-row')).toHaveLength(3)
  })

  it('filters devices by alias (case-insensitive)', async () => {
    const wrapper = mount(DeviceSearchModal, { props: { devices } })
    await wrapper.find('input').setValue('lampe')
    expect(wrapper.findAll('.device-row')).toHaveLength(1)
    expect(wrapper.find('.device-row').text()).toContain('Lampe')
  })

  it('shows the empty hint when nothing matches', async () => {
    const wrapper = mount(DeviceSearchModal, { props: { devices } })
    await wrapper.find('input').setValue('zzz')
    expect(wrapper.findAll('.device-row')).toHaveLength(0)
    expect(wrapper.find('.empty-hint').exists()).toBe(true)
  })

  it('shows the empty hint when there are no free devices at all', () => {
    const wrapper = mount(DeviceSearchModal, { props: { devices: [] } })
    expect(wrapper.find('.empty-hint').text()).toContain('Keine freien Geräte verfügbar')
  })

  it('emits select with the device when a row is clicked', async () => {
    const wrapper = mount(DeviceSearchModal, { props: { devices } })
    await wrapper.findAll('.device-row')[1].trigger('click')
    expect(wrapper.emitted('select')[0][0]).toEqual(devices[1])
  })

  it('emits close when the backdrop is clicked', async () => {
    const wrapper = mount(DeviceSearchModal, { props: { devices } })
    await wrapper.find('.modal-backdrop').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('does not emit close when the modal body is clicked', async () => {
    const wrapper = mount(DeviceSearchModal, { props: { devices } })
    await wrapper.find('.modal').trigger('click')
    expect(wrapper.emitted('close')).toBeFalsy()
  })

  it('renders each row as a button so it is keyboard operable', () => {
    const wrapper = mount(DeviceSearchModal, { props: { devices } })
    expect(wrapper.find('.device-row').element.tagName).toBe('BUTTON')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/components/__tests__/DeviceSearchModal.test.js`
Expected: FAIL — cannot resolve `../DeviceSearchModal.vue`

- [ ] **Step 3: Write minimal implementation**

Create `src/components/DeviceSearchModal.vue`:

```vue
<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <div class="modal" role="dialog" aria-modal="true" aria-label="Gerät zuordnen">
      <div class="modal-header">
        <h3 class="modal-title">Gerät zuordnen</h3>
        <button class="modal-close" aria-label="Schliessen" @click="$emit('close')">×</button>
      </div>

      <div class="modal-search">
        <input
          v-model="query"
          type="text"
          class="search-input"
          placeholder="Suchen…"
          aria-label="Gerät suchen"
          autofocus
        />
      </div>

      <div class="modal-list">
        <button
          v-for="device in filtered"
          :key="device.id"
          class="device-row"
          @click="$emit('select', device)"
        >
          <div class="device-row-info">
            <div class="device-row-name">{{ device.alias }}</div>
            <div class="device-row-box">{{ boxLabel(device) }}</div>
          </div>
          <TypeChip :type="device.groupName ?? device.deviceType" />
        </button>
        <p v-if="filtered.length === 0" class="empty-hint">Keine freien Geräte verfügbar</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import TypeChip from './TypeChip.vue';

const props = defineProps({
  devices: { type: Array, required: true },
});

defineEmits(['select', 'close']);

const query = ref('');

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return props.devices;
  return props.devices.filter((d) => d.alias?.toLowerCase().includes(q));
});

const boxLabel = (device) => (device.smartBoxId ? device.smartBoxId.slice(0, 8) + '…' : '–');
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: var(--sg-bg-card);
  border-radius: 14px;
  width: 420px;
  max-width: calc(100vw - 32px);
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--sg-shadow-lg);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 12px;
  border-bottom: 1px solid var(--sg-border);
}

.modal-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--sg-text-primary);
  margin: 0;
}

.modal-close {
  background: none;
  border: none;
  font-size: 20px;
  color: var(--sg-text-faint);
  cursor: pointer;
  padding: 2px 6px;
  line-height: 1;
  border-radius: 6px;
  transition: background 0.15s;
}

.modal-close:hover { background: var(--sg-bg-panel); }

.modal-search {
  padding: 12px 16px;
  border-bottom: 1px solid var(--sg-border);
}

.search-input {
  width: 100%;
  padding: 8px 12px;
  border: 1.5px solid var(--sg-border);
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s;
  box-sizing: border-box;
}

.search-input:focus { border-color: var(--sg-accent); }

.modal-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.device-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 48px;
  padding: 10px 20px;
  background: none;
  border: none;
  text-align: left;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.12s;
}

.device-row:hover { background: var(--sg-bg-panel); }

.device-row:focus-visible {
  outline: 2px solid var(--sg-accent);
  outline-offset: -2px;
}

.device-row-info { min-width: 0; }

.device-row-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--sg-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-row-box {
  font-size: 11px;
  color: var(--sg-text-faint);
}

.empty-hint {
  text-align: center;
  padding: 24px 16px;
  color: var(--sg-text-faint);
  font-size: 13px;
  margin: 0;
}
</style>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test src/components/__tests__/DeviceSearchModal.test.js`
Expected: PASS — 8 tests

- [ ] **Step 5: Lint and commit**

```bash
npm run lint:check
git add src/components/DeviceSearchModal.vue src/components/__tests__/DeviceSearchModal.test.js
git commit -m "[ui] add DeviceSearchModal for tap-to-assign device picker"
```

---

### Task 3: `PositionCard` empty slot becomes a button

**Files:**
- Modify: `src/components/PositionCard.vue` (template lines 128-139; emits array line 162-168; styles near line 354)
- Test: `src/components/__tests__/PositionCard.test.js` (create)

**Interfaces:**
- Consumes: existing `isAdmin` computed (`authStore.hasPermission('MANAGE_RANGES')`).
- Produces: `PositionCard` gains emit `assign-device` (no payload). The parent supplies the position id, since it already iterates positions. The slot element carries class `.empty-slot` in both the button and non-admin `div` forms.

Note: the drag hit-test uses `elementFromPoint(...).closest('[data-position-id]')`. Wrapping the slot in a `<button>` does not break it — `closest()` still walks up to the card. A drag that ends on this button does **not** fire a `click`, because `click` requires `pointerdown` and `pointerup` on the same element and the drag began on a tray row.

- [ ] **Step 1: Write the failing test**

Create `src/components/__tests__/PositionCard.test.js`:

```javascript
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import PositionCard from '../PositionCard.vue'

let permissions = []

vi.mock('@/stores/authStore.js', () => ({
  useAuthStore: () => ({ hasPermission: (p) => permissions.includes(p) }),
}))
vi.mock('@/stores/deviceStore.js', () => ({
  useDeviceStore: () => ({ blockDevice: vi.fn(), unblockDevice: vi.fn() }),
}))

const emptyPosition = { id: 'p1', label: 'A', device: null }

function mountCard(position = emptyPosition) {
  return mount(PositionCard, {
    props: { position },
    global: { stubs: { Icons: true, DeviceCard: true } },
  })
}

describe('PositionCard empty slot', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    permissions = ['MANAGE_RANGES']
  })

  it('renders the empty slot as a native button for an admin', () => {
    const wrapper = mountCard()
    expect(wrapper.find('.empty-slot').element.tagName).toBe('BUTTON')
  })

  it('emits assign-device when the empty slot is clicked', async () => {
    const wrapper = mountCard()
    await wrapper.find('.empty-slot').trigger('click')
    expect(wrapper.emitted('assign-device')).toBeTruthy()
    expect(wrapper.emitted('assign-device')).toHaveLength(1)
  })

  it('renders an inert slot and emits nothing for a non-admin', async () => {
    permissions = []
    const wrapper = mountCard()
    expect(wrapper.find('.empty-slot').element.tagName).not.toBe('BUTTON')
    await wrapper.find('.empty-slot').trigger('click')
    expect(wrapper.emitted('assign-device')).toBeFalsy()
  })

  it('does not render an empty slot when the position holds a device', () => {
    const wrapper = mountCard({ id: 'p2', label: 'B', device: { id: 'd1', healthy: true } })
    expect(wrapper.find('.empty-slot').exists()).toBe(false)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/components/__tests__/PositionCard.test.js`
Expected: FAIL — `.empty-slot` element tagName is `DIV`, not `BUTTON`

- [ ] **Step 3: Write minimal implementation**

In `src/components/PositionCard.vue`, replace the empty-slot block (currently lines 129-133):

```vue
    <!-- Empty slot -->
    <template v-else>
      <button
        v-if="isAdmin"
        class="empty-slot"
        type="button"
        :aria-label="`Gerät zu Position ${position.label} zuordnen`"
        @click="$emit('assign-device')"
      >
        <Icons icon="plus" :size="16" color="rgba(255,255,255,0.45)" />
        <span>Gerät zuordnen</span>
      </button>
      <div v-else class="empty-slot">
        <Icons icon="plus" :size="16" color="rgba(255,255,255,0.45)" />
        <span>Kein Gerät</span>
      </div>
```

Add `'assign-device'` to the emits array (line 162):

```javascript
const emit = defineEmits([
  'add-position',
  'remove-device',
  'rename',
  'delete-position',
  'fire',
  'assign-device',
]);
```

Extend the `.empty-slot` rule so the button form matches the old div visually and meets the 48px touch target:

```css
/* ── Empty slot ── */
.empty-slot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 18px 0;
  color: var(--sg-text-faint);
  font-size: 12px;
  font-weight: 500;
  /* button reset — the admin form is a <button>, the non-admin form a <div> */
  width: 100%;
  min-height: 48px;
  background: none;
  border: none;
  font-family: inherit;
  cursor: pointer;
}

.empty-slot:not(button) { cursor: default; }

.empty-slot:focus-visible {
  outline: 2px solid var(--sg-accent);
  outline-offset: 2px;
  border-radius: 8px;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test src/components/__tests__/PositionCard.test.js`
Expected: PASS — 4 tests

- [ ] **Step 5: Lint and commit**

```bash
npm run lint:check
git add src/components/PositionCard.vue src/components/__tests__/PositionCard.test.js
git commit -m "[ui] make empty position slot a keyboard-operable assign button"
```

---

### Task 4: Extract `unassignedDevices` and wire the picker in `RangeDetailView`

**Files:**
- Modify: `src/views/admin/RangeDetailView.vue`
- Test: `src/views/__tests__/RangeDetailView.test.js` (create)

**Interfaces:**
- Consumes: `DeviceSearchModal` (Task 2, prop `devices`, emits `select`/`close`), `PositionCard` emit `assign-device` (Task 3), `rangeStore.assignDeviceToPosition(rangeId, positionId, deviceId)`.
- Produces: computed `unassignedDevices` (flat `Array` of devices), from which `allSidebarGroups` is derived. Tray and modal therefore read the same list.

- [ ] **Step 1: Write the failing test**

Create `src/views/__tests__/RangeDetailView.test.js`:

```javascript
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import RangeDetailView from '@/views/admin/RangeDetailView.vue'
import PositionCard from '@/components/PositionCard.vue'
import DeviceSearchModal from '@/components/DeviceSearchModal.vue'

const DEVICE = { id: 'd1', alias: 'Werfer 1', smartBoxId: 'aaaaaaaa-1111', deviceType: 'THROWER', groupName: null, rangeId: null }

const mockRangeStore = {
  ranges: [{ id: 'r1', name: 'Platz 1', description: '', assignedUserId: null }],
  positions: { r1: [{ id: 'p1', label: 'A', device: null }] },
  loadPositions: vi.fn().mockResolvedValue(),
  createPosition: vi.fn().mockResolvedValue(),
  assignDeviceToPosition: vi.fn().mockResolvedValue(),
  removeDeviceFromPosition: vi.fn().mockResolvedValue(),
  renamePosition: vi.fn().mockResolvedValue(),
  deletePosition: vi.fn().mockResolvedValue(),
  assignUser: vi.fn().mockResolvedValue(),
}
const mockDeviceStore = { devices: [DEVICE], loadDevicesForBox: vi.fn().mockResolvedValue() }
const mockSmartBoxStore = { smartboxes: [{ id: 'b1' }], loadApiData: vi.fn().mockResolvedValue() }
const mockReservationStore = {
  getReservationForRange: vi.fn().mockResolvedValue(null),
  reserve: vi.fn(), release: vi.fn(), forceRelease: vi.fn(),
}
const mockUserStore = { users: [], currentUser: { username: 'admin', role: 'ADMIN' }, loadUsers: vi.fn().mockResolvedValue() }

vi.mock('@/stores/rangeStore.js', () => ({ useRangeStore: () => mockRangeStore }))
vi.mock('@/stores/deviceStore.js', () => ({ useDeviceStore: () => mockDeviceStore }))
vi.mock('@/stores/smartBoxStore.js', () => ({ useSmartBoxStore: () => mockSmartBoxStore }))
vi.mock('@/stores/reservationStore.js', () => ({ useReservationStore: () => mockReservationStore }))
vi.mock('@/stores/userStore.js', () => ({ useUserStore: () => mockUserStore }))

const originalMatchMedia = window.matchMedia

function stubMatchMedia(matches) {
  window.matchMedia = vi.fn(() => ({
    matches, media: '', addEventListener: vi.fn(), removeEventListener: vi.fn(),
  }))
}

async function mountView() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }] })
  const wrapper = mount(RangeDetailView, {
    props: { id: 'r1' },
    shallow: true,
    global: { plugins: [router] },
  })
  await flushPromises()
  return wrapper
}

describe('RangeDetailView device picker', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    stubMatchMedia(false)
  })
  afterEach(() => { window.matchMedia = originalMatchMedia })

  it('opens the device picker when a position requests assignment', async () => {
    const wrapper = await mountView()
    expect(wrapper.findComponent(DeviceSearchModal).exists()).toBe(false)

    await wrapper.findComponent(PositionCard).vm.$emit('assign-device')
    await flushPromises()

    const modal = wrapper.findComponent(DeviceSearchModal)
    expect(modal.exists()).toBe(true)
    expect(modal.props('devices')).toEqual([DEVICE])
  })

  it('assigns the selected device to the position that opened the picker', async () => {
    const wrapper = await mountView()
    await wrapper.findComponent(PositionCard).vm.$emit('assign-device')
    await flushPromises()

    await wrapper.findComponent(DeviceSearchModal).vm.$emit('select', DEVICE)
    await flushPromises()

    expect(mockRangeStore.assignDeviceToPosition).toHaveBeenCalledWith('r1', 'p1', 'd1')
    expect(wrapper.findComponent(DeviceSearchModal).exists()).toBe(false)
  })

  it('closes the picker without assigning when close is emitted', async () => {
    const wrapper = await mountView()
    await wrapper.findComponent(PositionCard).vm.$emit('assign-device')
    await flushPromises()

    await wrapper.findComponent(DeviceSearchModal).vm.$emit('close')
    await flushPromises()

    expect(mockRangeStore.assignDeviceToPosition).not.toHaveBeenCalled()
    expect(wrapper.findComponent(DeviceSearchModal).exists()).toBe(false)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/views/__tests__/RangeDetailView.test.js`
Expected: FAIL — `DeviceSearchModal` never renders; `PositionCard` has no `assign-device` listener

- [ ] **Step 3: Write minimal implementation**

In `src/views/admin/RangeDetailView.vue`:

Add the import (after the `PositionCard` import):

```javascript
import DeviceSearchModal from '@/components/DeviceSearchModal.vue';
```

Replace the `allSidebarGroups` computed (lines 268-274) with a flat list plus a derived group list:

```javascript
const unassignedDevices = computed(() =>
  deviceStore.devices.filter(d => !d.rangeId && !assignedDeviceIds.value.has(d.id))
);

const allSidebarGroups = computed(() => {
  if (unassignedDevices.value.length === 0) return [];
  return [{ label: 'Nicht zugeteilt', devices: unassignedDevices.value }];
});
```

Add picker state and handlers (near the other position actions):

```javascript
// ── Tap-to-assign picker (works on every viewport; the only keyboard path) ────
const pickerPositionId = ref(null);
const showDeviceModal = ref(false);

const openDevicePicker = (positionId) => {
  pickerPositionId.value = positionId;
  showDeviceModal.value = true;
};

const closeDevicePicker = () => {
  showDeviceModal.value = false;
  pickerPositionId.value = null;
};

const handleSelectDevice = async (device) => {
  const positionId = pickerPositionId.value;
  closeDevicePicker();
  if (positionId) await handleDropOnPosition(positionId, device.id);
};
```

Wire the `PositionCard` listener in the grid (add to the existing `v-for` card):

```vue
          @assign-device="openDevicePicker(pos.id)"
```

Render the modal just before the closing `</div>` of `.range-detail-view`, next to the drag ghost:

```vue
    <DeviceSearchModal
      v-if="showDeviceModal"
      :devices="unassignedDevices"
      @select="handleSelectDevice"
      @close="closeDevicePicker"
    />
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test src/views/__tests__/RangeDetailView.test.js`
Expected: PASS — 3 tests

- [ ] **Step 5: Lint and commit**

```bash
npm run lint:check
git add src/views/admin/RangeDetailView.vue src/views/__tests__/RangeDetailView.test.js
git commit -m "[ui] wire tap-to-assign device picker into RangeDetailView"
```

---

### Task 5: Hide the drag tray below 640px

**Files:**
- Modify: `src/views/admin/RangeDetailView.vue`
- Modify: `src/views/__tests__/RangeDetailView.test.js`

**Interfaces:**
- Consumes: `useMediaQuery` (Task 1).
- Produces: `isMobile: Ref<boolean>`. When true, neither the `Gerät zuordnen` toggle nor `.assign-panel` is rendered, and `assignOpen` is forced `false`.

This is the fix for the reported bug: with `.assign-panel` gone, `.detail-content` has no 256px `flex-shrink: 0` sibling and cannot collapse to its 56px padding.

- [ ] **Step 1: Write the failing test**

Append to `describe` block in `src/views/__tests__/RangeDetailView.test.js`:

```javascript
describe('RangeDetailView tray visibility', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(() => { window.matchMedia = originalMatchMedia })

  it('renders the drag tray above the breakpoint', async () => {
    stubMatchMedia(false)
    const wrapper = await mountView()
    expect(wrapper.find('.assign-panel').exists()).toBe(true)
  })

  it('does not render the drag tray below the breakpoint', async () => {
    stubMatchMedia(true)
    const wrapper = await mountView()
    expect(wrapper.find('.assign-panel').exists()).toBe(false)
  })

  it('queries the 640px breakpoint', async () => {
    stubMatchMedia(true)
    await mountView()
    expect(window.matchMedia).toHaveBeenCalledWith('(max-width: 640px)')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/views/__tests__/RangeDetailView.test.js`
Expected: FAIL — `.assign-panel` renders regardless; `matchMedia` never called

- [ ] **Step 3: Write minimal implementation**

In `src/views/admin/RangeDetailView.vue`, add the import:

```javascript
import { useMediaQuery } from '@/composables/useMediaQuery.js';
```

Add `watch` to the existing `vue` import, and declare the breakpoint state near `assignOpen`:

```javascript
// Below this width a 256px tray plus a 180px position card cannot coexist:
// the tray would collapse .detail-content to its padding and make every drop
// target unreachable. Phones assign via the picker instead.
const isMobile = useMediaQuery('(max-width: 640px)');
watch(isMobile, (mobile) => { if (mobile) assignOpen.value = false; });
```

Guard the toggle button — wrap the existing `<Button>` in the header actions:

```vue
          <Button v-if="!isMobile" :variant="assignOpen ? 'ghost' : 'primary'" @click="toggleAssignPanel">
```

Guard the panel itself:

```vue
    <div v-if="!isMobile" class="assign-panel" :class="{ open: assignOpen }">
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test src/views/__tests__/RangeDetailView.test.js`
Expected: PASS — 6 tests total

- [ ] **Step 5: Lint and commit**

```bash
npm run lint:check
git add src/views/admin/RangeDetailView.vue src/views/__tests__/RangeDetailView.test.js
git commit -m "[ui] hide drag tray below 640px to stop detail-content collapse"
```

---

### Task 6: Fix drag auto-scroll to target the real scroll container

**Files:**
- Create: `src/composables/useDragAutoScroll.js`
- Modify: `src/views/admin/RangeDetailView.vue` (remove `autoScrollNearEdge`, lines 314-324)
- Test: `src/composables/__tests__/useDragAutoScroll.test.js`

**Interfaces:**
- Consumes: nothing.
- Produces: named exports `AUTOSCROLL_EDGE_PX = 72`, `AUTOSCROLL_STEP_PX = 14`, and `scrollNearEdge(container, clientY, viewportHeight) => void`. A `null` container is a no-op.

Background: the old code did `document.querySelector('.layout-main').scrollTop += …`. `.layout-main` never scrolls — `.range-detail-view` is `overflow: hidden` and height-constrained, so it never overflows. Measured: `.layout-main` `scrollHeight === clientHeight` while `.detail-content` had `scrollHeight 2874` vs `clientHeight 804`. The auto-scroll was a no-op on every viewport, desktop included.

The container is now passed by **template ref**, not looked up by class name, so this class of bug cannot recur silently.

- [ ] **Step 1: Write the failing test**

Create `src/composables/__tests__/useDragAutoScroll.test.js`:

```javascript
import { describe, it, expect } from 'vitest'
import { scrollNearEdge, AUTOSCROLL_EDGE_PX, AUTOSCROLL_STEP_PX } from '@/composables/useDragAutoScroll.js'

const VIEWPORT = 800

describe('scrollNearEdge', () => {
  it('scrolls up near the top edge', () => {
    const container = { scrollTop: 100 }
    scrollNearEdge(container, AUTOSCROLL_EDGE_PX - 1, VIEWPORT)
    expect(container.scrollTop).toBe(100 - AUTOSCROLL_STEP_PX)
  })

  it('scrolls down near the bottom edge', () => {
    const container = { scrollTop: 100 }
    scrollNearEdge(container, VIEWPORT - AUTOSCROLL_EDGE_PX + 1, VIEWPORT)
    expect(container.scrollTop).toBe(100 + AUTOSCROLL_STEP_PX)
  })

  it('does nothing in the middle of the viewport', () => {
    const container = { scrollTop: 100 }
    scrollNearEdge(container, VIEWPORT / 2, VIEWPORT)
    expect(container.scrollTop).toBe(100)
  })

  it('is a no-op for a null container', () => {
    expect(() => scrollNearEdge(null, 0, VIEWPORT)).not.toThrow()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/composables/__tests__/useDragAutoScroll.test.js`
Expected: FAIL — cannot resolve `@/composables/useDragAutoScroll.js`

- [ ] **Step 3: Write minimal implementation**

Create `src/composables/useDragAutoScroll.js`:

```javascript
export const AUTOSCROLL_EDGE_PX = 72;
export const AUTOSCROLL_STEP_PX = 14;

/**
 * Nudge a scroll container when a drag pointer nears its top/bottom edge.
 * During a touch drag the browser cannot scroll (touch-action: none plus
 * pointer capture), so off-screen drop targets are otherwise unreachable.
 */
export function scrollNearEdge(container, clientY, viewportHeight) {
  if (!container) return;
  if (clientY < AUTOSCROLL_EDGE_PX) container.scrollTop -= AUTOSCROLL_STEP_PX;
  else if (clientY > viewportHeight - AUTOSCROLL_EDGE_PX) container.scrollTop += AUTOSCROLL_STEP_PX;
}
```

In `src/views/admin/RangeDetailView.vue`:

Import it:

```javascript
import { scrollNearEdge } from '@/composables/useDragAutoScroll.js';
```

Bind a template ref on the scroll container:

```vue
    <div ref="detailContentEl" class="detail-content">
```

Declare the ref alongside the other drag state:

```javascript
const detailContentEl = ref(null);
```

Delete the whole `AUTOSCROLL_EDGE_PX` / `AUTOSCROLL_STEP_PX` / `autoScrollNearEdge` block (lines 314-324) and change the call site in `onDragMove` from:

```javascript
  autoScrollNearEdge(event.clientY);
```

to:

```javascript
  scrollNearEdge(detailContentEl.value, event.clientY, window.innerHeight);
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm run test src/composables/__tests__/useDragAutoScroll.test.js`
Expected: PASS — 4 tests

Run: `npm run test`
Expected: PASS — full suite green, no regression in `RangeDetailView.test.js`

- [ ] **Step 5: Lint and commit**

```bash
npm run lint:check
git add src/composables/useDragAutoScroll.js src/composables/__tests__/useDragAutoScroll.test.js src/views/admin/RangeDetailView.vue
git commit -m "[ui] scroll the real container during drag via template ref"
```

---

### Task 7: Verify in a real browser and close out

**Files:** none modified. This task is verification only.

**Interfaces:**
- Consumes: everything above.
- Produces: evidence that the reported bug is fixed.

Unit tests cannot prove the layout collapse is gone — it is a CSS geometry property. This task measures it, the same way the bug was originally diagnosed.

- [ ] **Step 1: Full suite and lint**

Run: `npm run test`
Expected: PASS, all files.

Run: `npm run lint:check`
Expected: no errors, no warnings.

- [ ] **Step 2: Production build**

Run: `npm run build`
Expected: build succeeds with no warnings.

- [ ] **Step 3: Measure the collapse is gone at 375px**

Start the dev server (`preview_start` with the `smart-ground-ui` config), log in as an admin, navigate to a range detail page, and resize the viewport to 375×812 (`preview_resize` preset `mobile`).

Evaluate in the page:

```javascript
(() => {
  const dc = document.querySelector('.detail-content');
  const card = document.querySelector('[data-position-id]');
  const r = card.getBoundingClientRect();
  const y = Math.round(r.top + r.height / 2);
  const hits = [];
  for (let x = 0; x < innerWidth; x += 4) {
    const el = document.elementFromPoint(x, y);
    if (el && el.closest('[data-position-id]')) hits.push(x);
  }
  return {
    detailContentWidth: Math.round(dc.getBoundingClientRect().width),
    assignPanelPresent: !!document.querySelector('.assign-panel'),
    hittableWidthPx: hits.length ? hits[hits.length - 1] - hits[0] + 4 : 0,
  };
})()
```

Expected:
- `detailContentWidth` ≈ `305` (was `56`)
- `assignPanelPresent` === `false`
- `hittableWidthPx` ≥ `180` (was `28`)

If `detailContentWidth` is still 56, the tray is still rendering — Task 5 did not take effect.

- [ ] **Step 4: Exercise the tap flow**

Tap an empty position's slot. Expect `DeviceSearchModal` to open listing free devices. Tap a device. Expect the modal to close and the position to show the device.

Confirm with `preview_snapshot` (structure/text) rather than a screenshot, then take one `preview_screenshot` as the visual record.

- [ ] **Step 5: Verify the drag still works at desktop width**

Resize to `desktop` (1280×800). Open the tray, drag a device by its grip handle onto a position. Expect assignment. Then confirm auto-scroll: with enough positions to overflow, drag toward the bottom edge and confirm `.detail-content` scrolls.

- [ ] **Step 6: Final commit if anything changed**

```bash
git status --porcelain
```
Expected: clean. If verification exposed fixes, commit them with `[ui] …`.

---

## Self-Review

**Spec coverage:**

| Spec requirement | Task |
|---|---|
| Position-first tap flow, all viewports | 3, 4 |
| `DeviceSearchModal` mirroring `UserSearchModal` | 2 |
| `useMediaQuery` composable | 1 |
| Tray + toggle hidden below 640px, `assignOpen` forced false | 5 |
| `unassignedDevices` extracted, tray and modal share one list | 4 |
| `autoScrollNearEdge` targets the real scroll container | 6 |
| Empty state "Keine freien Geräte verfügbar" | 2 |
| Non-admin sees inert slot | 3 |
| Assignment failure still `console.error` (known gap) | unchanged by design |
| Resize across 640px mid-drag cleaned up by `onUnmounted(onDragCancel)` | unchanged, no new teardown |
| Keyboard-operable assignment | 3 (native `<button>`) |

**Deviations from the spec, recorded deliberately:**

1. Spec says test `Enter`/`Space` on the slot. Replaced with an assertion that the element is a native `<button>` — jsdom does not synthesize `click` from `keydown`, so the spec's test would have been misleading. Documented under Global Constraints.
2. Spec implies fixing `autoScrollNearEdge`'s selector. Task 6 removes the selector entirely in favour of a template ref, which prevents recurrence rather than fixing one instance. Small extra file (`useDragAutoScroll.js`), justified by making the regression unit-testable.

**Placeholder scan:** none. Every code step contains complete code.

**Type consistency:** `assign-device` (kebab, no payload) is emitted in Task 3 and listened for in Tasks 4/5. `unassignedDevices` is defined in Task 4 and consumed by the modal in the same task. `scrollNearEdge(container, clientY, viewportHeight)` is defined in Task 6 and called with exactly three arguments. `useMediaQuery(query) => Ref<boolean>` defined in Task 1, called in Task 5.
