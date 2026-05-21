# ShooterRemoteView — RangePosition-Based Grid

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the device-centric grid in `ShooterRemoteView` with `RangePosition` entities, so buttons represent range slots (labelled A/B/C), are disabled when no device is assigned, and fire commands go through the position endpoint.

**Architecture:** Four sequential changes — (1) add `sendPositionCommand` to the position API, (2) update `programStore.addStep` to key on `positionId` instead of `deviceId`, (3) rewrite `ShooterRemoteView` to render and fire positions, (4) simplify `ShooterFlyoutPanel` to read step letters from the stored step data instead of re-deriving from a device list.

**Tech Stack:** Vue 3 (Composition API), Pinia, Vitest + @vue/test-utils, native fetch via `apiFetch`

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `src/services/rangePositionApi.js` | Modify | Add `sendPositionCommand` |
| `src/services/__tests__/rangePositionApi.test.js` | Create | Test `sendPositionCommand` |
| `src/stores/programStore.js` | Modify | `addStep` uses `positionId` instead of `deviceId` |
| `src/stores/__tests__/programStore.test.js` | Create | Test `addStep` with positionId |
| `src/views/shooter/ShooterRemoteView.vue` | Modify | Position-based grid, position fire functions |
| `src/components/shooter-remote/ShooterFlyoutPanel.vue` | Modify | `getStepLabel` reads stored letters; remove device lookup |

---

## Task 1: Add `sendPositionCommand` to `rangePositionApi.js`

**Files:**
- Modify: `src/services/rangePositionApi.js`
- Create: `src/services/__tests__/rangePositionApi.test.js`

- [ ] **Step 1: Write the failing test**

Create `src/services/__tests__/rangePositionApi.test.js`:

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../apiClient.js', () => ({
  apiFetch: vi.fn(),
}))

describe('rangePositionApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('sendPositionCommand calls correct endpoint with POST', async () => {
    const { apiFetch } = await import('../apiClient.js')
    vi.mocked(apiFetch).mockResolvedValue({})

    const { sendPositionCommand } = await import('../rangePositionApi.js')
    await sendPositionCommand('range-1', 'pos-abc')

    expect(apiFetch).toHaveBeenCalledWith(
      '/ranges/range-1/positions/pos-abc/command',
      { method: 'POST' }
    )
  })
})
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
npm run test src/services/__tests__/rangePositionApi.test.js
```

Expected: FAIL — `sendPositionCommand is not a function`

- [ ] **Step 3: Add `sendPositionCommand` to `rangePositionApi.js`**

Append to the bottom of `src/services/rangePositionApi.js`:

```javascript
export async function sendPositionCommand(rangeId, positionId) {
  return apiFetch(`/ranges/${rangeId}/positions/${positionId}/command`, { method: 'POST' });
}
```

- [ ] **Step 4: Run the test to confirm it passes**

```bash
npm run test src/services/__tests__/rangePositionApi.test.js
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/services/rangePositionApi.js src/services/__tests__/rangePositionApi.test.js
git commit -m "[ui] Add sendPositionCommand to rangePositionApi"
```

---

## Task 2: Update `programStore.addStep` to use `positionId`

**Files:**
- Modify: `src/stores/programStore.js`
- Create: `src/stores/__tests__/programStore.test.js`

- [ ] **Step 1: Write the failing tests**

Create `src/stores/__tests__/programStore.test.js`:

```javascript
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useProgramStore } from '../programStore.js'
import { useShooterRemoteStore } from '../shooterRemoteStore.js'

describe('programStore.addStep', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const remote = useShooterRemoteStore()
    remote.reservePlatz('range-1')
    const store = useProgramStore()
    store.startCapture()
  })

  it('solo step stores positionId and label letter', () => {
    const remote = useShooterRemoteStore()
    remote.setMode('solo')
    const store = useProgramStore()

    const position = { id: 'pos-1', label: 'A', device: { alias: 'Werfer 1' } }
    store.addStep('pos-1', position, 'A')

    const step = store.editingAblauf[0].steps[0]
    expect(step.type).toBe('solo')
    expect(step.positionId).toBe('pos-1')
    expect(step.letter).toBe('A')
    expect(step.alias).toBe('Werfer 1')
  })

  it('solo step uses position.label as alias when device is null', () => {
    const remote = useShooterRemoteStore()
    remote.setMode('solo')
    const store = useProgramStore()

    const position = { id: 'pos-2', label: 'B', device: null }
    store.addStep('pos-2', position, 'B')

    const step = store.editingAblauf[0].steps[0]
    expect(step.alias).toBe('B')
  })

  it('pair step stores positionId1 and positionId2', () => {
    const remote = useShooterRemoteStore()
    remote.setMode('pair')
    const store = useProgramStore()

    const pos1 = { id: 'pos-1', label: 'A', device: { alias: 'Werfer 1' } }
    const pos2 = { id: 'pos-2', label: 'B', device: { alias: 'Werfer 2' } }

    store.addStep('pos-1', pos1, 'A')
    store.addStep('pos-2', pos2, 'B')

    const step = store.editingAblauf[0].steps[0]
    expect(step.type).toBe('pair')
    expect(step.positionId1).toBe('pos-1')
    expect(step.positionId2).toBe('pos-2')
    expect(step.letter1).toBe('A')
    expect(step.letter2).toBe('B')
  })

  it('raffale step stores positionId', () => {
    const remote = useShooterRemoteStore()
    remote.setMode('raffale')
    const store = useProgramStore()

    const position = { id: 'pos-3', label: 'C', device: { alias: 'Werfer 3' } }
    store.addStep('pos-3', position, 'C')

    const step = store.editingAblauf[0].steps[0]
    expect(step.type).toBe('raffale')
    expect(step.positionId).toBe('pos-3')
    expect(step.letter).toBe('C')
  })

  it('recording flash is keyed by positionId', () => {
    const remote = useShooterRemoteStore()
    remote.setMode('solo')
    const store = useProgramStore()

    const position = { id: 'pos-1', label: 'A', device: { alias: 'Werfer 1' } }
    store.addStep('pos-1', position, 'A')

    expect(store.recording['pos-1']).toBe(true)
  })
})
```

- [ ] **Step 2: Run the tests to confirm they fail**

```bash
npm run test src/stores/__tests__/programStore.test.js
```

Expected: FAIL — steps have `deviceId` not `positionId`

- [ ] **Step 3: Replace `addStep` in `programStore.js`**

Find the `addStep` function (line ~170) and replace it entirely:

```javascript
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
      const segs = [...editingAblauf.value];
      segs[0].steps = [...segs[0].steps, step];
      editingAblauf.value = segs;
    } else if (shooterRemoteStore.mode === 'raffale') {
      recording.value = { ...recording.value, [positionId]: true };
      setTimeout(() => {
        const r = { ...recording.value };
        delete r[positionId];
        recording.value = r;
      }, 500);
      const step = { id: Date.now(), type: 'raffale', alias, positionId, letter };
      const segs = [...editingAblauf.value];
      segs[0].steps = [...segs[0].steps, step];
      editingAblauf.value = segs;
      shooterRemoteStore.setMode('solo');
    } else if (shooterRemoteStore.mode === 'pair' || shooterRemoteStore.mode === 'a_schuss') {
      if (!pairPending.value) {
        pairPending.value = { id: positionId, alias, letter };
      } else if (pairPending.value.id === positionId) {
        pairPending.value = null;
      } else {
        const pendingId = pairPending.value.id;
        const pendingAlias = pairPending.value.alias;
        const pendingLetter = pairPending.value.letter;
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
          letter1: pendingLetter,
          letter2: letter,
        };
        const segs = [...editingAblauf.value];
        segs[0].steps = [...segs[0].steps, step];
        editingAblauf.value = segs;
        pairPending.value = null;
        shooterRemoteStore.setMode('solo');
      }
    }
  };
```

- [ ] **Step 4: Run the tests to confirm they pass**

```bash
npm run test src/stores/__tests__/programStore.test.js
```

Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add src/stores/programStore.js src/stores/__tests__/programStore.test.js
git commit -m "[ui] Update programStore.addStep to use positionId"
```

---

## Task 3: Rewrite `ShooterRemoteView.vue` to be position-based

**Files:**
- Modify: `src/views/shooter/ShooterRemoteView.vue`
- Create: `src/components/__tests__/ShooterRemoteView.test.js`

- [ ] **Step 1: Write the failing tests**

Create `src/components/__tests__/ShooterRemoteView.test.js`:

```javascript
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ShooterRemoteView from '../../views/shooter/ShooterRemoteView.vue'
import { useRangeStore } from '../../stores/rangeStore.js'
import { useShooterRemoteStore } from '../../stores/shooterRemoteStore.js'

vi.mock('../../services/rangePositionApi.js', () => ({
  sendPositionCommand: vi.fn().mockResolvedValue({}),
  fetchPositions: vi.fn().mockResolvedValue([]),
}))

vi.mock('../../components/shooter-remote/ShooterFlyoutPanel.vue', () => ({
  default: { template: '<div />' },
}))

const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }] })

function mountView(positions = []) {
  const pinia = createPinia()
  setActivePinia(pinia)

  const rangeStore = useRangeStore()
  rangeStore.ranges = [{ id: 'range-1', name: 'Platz 1', locked: false }]
  rangeStore.positions['range-1'] = positions

  const remoteStore = useShooterRemoteStore()
  remoteStore.reservePlatz('range-1')

  return mount(ShooterRemoteView, {
    props: { rangeId: 'range-1' },
    global: { plugins: [pinia, router] },
  })
}

describe('ShooterRemoteView (position-based)', () => {
  it('shows position label as button letter', async () => {
    const wrapper = mountView([
      { id: 'pos-1', label: 'A', device: { id: 'dev-1', alias: 'Werfer 1', blocked: false, healthy: true } },
    ])
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('A')
    expect(wrapper.text()).toContain('Werfer 1')
  })

  it('renders empty position as disabled button', async () => {
    const wrapper = mountView([
      { id: 'pos-2', label: 'B', device: null },
    ])
    await wrapper.vm.$nextTick()
    const btn = wrapper.find('button.device-btn')
    expect(btn.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Kein Gerät')
  })

  it('shows no group dropdown', async () => {
    const wrapper = mountView([
      { id: 'pos-1', label: 'A', device: { id: 'dev-1', alias: 'W1', blocked: false, healthy: true } },
      { id: 'pos-2', label: 'B', device: { id: 'dev-2', alias: 'W2', blocked: false, healthy: true } },
    ])
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.group-dropdown-wrapper').exists()).toBe(false)
  })
})
```

- [ ] **Step 2: Run the tests to confirm they fail**

```bash
npm run test src/components/__tests__/ShooterRemoteView.test.js
```

Expected: FAIL — component still uses device-based logic

- [ ] **Step 3: Replace `<script setup>` in `ShooterRemoteView.vue`**

Replace the entire `<script setup>` block (lines 155–447) with:

```javascript
<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useRangeStore } from '@/stores/rangeStore.js';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import { useProgramStore } from '@/stores/programStore.js';
import { sendPositionCommand } from '@/services/rangePositionApi.js';
import Icons from '@/components/Icons.vue';
import ShooterFlyoutPanel from '@/components/shooter-remote/ShooterFlyoutPanel.vue';

const props = defineProps({ rangeId: { type: String, required: true } });

const router = useRouter();
const rangeStore = useRangeStore();
const store = useShooterRemoteStore();
const programStore = useProgramStore();

const positionsLoading = ref(false);

onMounted(async () => {
  store.ensureReserved(props.rangeId);
  store.setMode('solo');
  positionsLoading.value = true;
  try {
    await rangeStore.loadPositions(props.rangeId);
  } finally {
    positionsLoading.value = false;
  }
});

onUnmounted(() => {
  store.releasePlatz();
});

watch(() => store.sessionMode, () => {
  store.setMode('solo');
});

// ── Range & positions ──────────────────────────────
const range = computed(() => rangeStore.ranges.find((r) => r.id === props.rangeId));
const isLocked = computed(() => range.value?.locked ?? false);
const rangePositions = computed(() => rangeStore.positions[props.rangeId] ?? []);

// ── Navigation & lock ──────────────────────────────
const goBack = () => {
  store.releasePlatz();
  router.push('/remote');
};

const toggleBlock = async () => {
  if (!range.value) return;
  await rangeStore.setLocked(range.value.id, !isLocked.value);
};

// ── Fire state ─────────────────────────────────────
const firingIds = ref(new Set());
const firedIds = ref(new Set());
const errorIds = ref(new Set());

const handlePositionTap = async (position) => {
  if (isPositionDisabled(position)) return;

  if (store.sessionMode === 'recording' && programStore.programMode) {
    programStore.addStep(position.id, position, position.label);
    return;
  }

  if ((store.mode === 'pair' || store.mode === 'a_schuss') && !programStore.programMode) {
    if (!store.throwPairPending) {
      store.throwPairPending = { id: position.id, alias: position.device?.alias ?? position.label };
    } else if (store.throwPairPending.id === position.id) {
      store.throwPairPending = null;
    } else {
      const pendingId = store.throwPairPending.id;
      store.throwPairPending = null;
      await firePairPositions(pendingId, position.id);
      return;
    }
    return;
  }

  if (store.mode === 'raffale' && !programStore.programMode) {
    await fireRaffalePosition(position.id);
    return;
  }

  await fireSinglePosition(position.id);
};

const fireSinglePosition = async (positionId) => {
  if (firingIds.value.has(positionId)) return;
  firingIds.value = new Set([...firingIds.value, positionId]);
  firedIds.value.delete(positionId);
  errorIds.value.delete(positionId);
  try {
    await sendPositionCommand(props.rangeId, positionId);
    firedIds.value = new Set([...firedIds.value, positionId]);
    setTimeout(() => {
      firedIds.value = new Set([...firedIds.value].filter((id) => id !== positionId));
    }, 900);
  } catch {
    errorIds.value = new Set([...errorIds.value, positionId]);
    setTimeout(() => {
      errorIds.value = new Set([...errorIds.value].filter((id) => id !== positionId));
    }, 1500);
  } finally {
    firingIds.value = new Set([...firingIds.value].filter((id) => id !== positionId));
  }
};

const firePairPositions = async (posId1, posId2) => {
  if (firingIds.value.has(posId1) || firingIds.value.has(posId2)) return;
  firingIds.value = new Set([...firingIds.value, posId1, posId2]);
  firedIds.value.delete(posId1);
  firedIds.value.delete(posId2);
  errorIds.value.delete(posId1);
  errorIds.value.delete(posId2);
  try {
    await Promise.all([
      sendPositionCommand(props.rangeId, posId1),
      sendPositionCommand(props.rangeId, posId2),
    ]);
    firedIds.value = new Set([...firedIds.value, posId1, posId2]);
    setTimeout(() => {
      firedIds.value = new Set([...firedIds.value].filter((id) => id !== posId1 && id !== posId2));
    }, 900);
  } catch {
    errorIds.value = new Set([...errorIds.value, posId1, posId2]);
    setTimeout(() => {
      errorIds.value = new Set([...errorIds.value].filter((id) => id !== posId1 && id !== posId2));
    }, 1500);
  } finally {
    firingIds.value = new Set([...firingIds.value].filter((id) => id !== posId1 && id !== posId2));
  }
};

const fireRaffalePosition = async (positionId) => {
  if (firingIds.value.has(positionId)) return;
  firingIds.value = new Set([...firingIds.value, positionId]);
  firedIds.value.delete(positionId);
  errorIds.value.delete(positionId);
  try {
    await sendPositionCommand(props.rangeId, positionId);
    await new Promise(resolve => setTimeout(resolve, 2000));
    await sendPositionCommand(props.rangeId, positionId);
    firedIds.value = new Set([...firedIds.value, positionId]);
    setTimeout(() => {
      firedIds.value = new Set([...firedIds.value].filter((id) => id !== positionId));
    }, 900);
  } catch {
    errorIds.value = new Set([...errorIds.value, positionId]);
    setTimeout(() => {
      errorIds.value = new Set([...errorIds.value].filter((id) => id !== positionId));
    }, 1500);
  } finally {
    firingIds.value = new Set([...firingIds.value].filter((id) => id !== positionId));
  }
};

// ── Button helpers ─────────────────────────────────
const isPositionDisabled = (position) => {
  if (!position.device) return true;
  if (position.device.blocked || position.device.healthy === false) return true;
  if (isLocked.value) return true;
  if (!store.reservedByMe) return true;
  if (firingIds.value.has(position.id)) return true;
  return false;
};

const positionBtnClass = (position) => ({
  'device-btn--firing': firingIds.value.has(position.id),
  'device-btn--fired': firedIds.value.has(position.id),
  'device-btn--error': errorIds.value.has(position.id),
  'device-btn--blocked': (position.device?.blocked ?? false) || isLocked.value,
  'device-btn--no-device': !position.device,
  'device-btn--recording': !!programStore.recording[position.id],
  'device-btn--pair-pending': programStore.programMode && programStore.pairPending?.id === position.id,
  'device-btn--inactive': !store.reservedByMe && !isLocked.value,
});

const iconColor = (position) => {
  if (!position.device) return 'rgba(255,255,255,0.15)';
  if (position.device.blocked || isLocked.value) return 'rgba(252,129,129,0.5)';
  if (!store.reservedByMe) return 'rgba(255,255,255,0.25)';
  return 'rgba(255,255,255,0.95)';
};

const chipClass = (position) => {
  if (!position.device) return 'chip--no-device';
  if (position.device.blocked || isLocked.value) return 'chip--blocked';
  if (!store.reservedByMe) return 'chip--free';
  if (errorIds.value.has(position.id)) return 'chip--error';
  if (firedIds.value.has(position.id)) return 'chip--fired';
  if (programStore.recording[position.id]) return 'chip--recording';
  if (programStore.programMode && programStore.pairPending?.id === position.id) return 'chip--pending';
  return 'chip--ready';
};

const chipLabel = (position) => {
  if (!position.device) return 'Kein Gerät';
  if (position.device.blocked) return 'Gesperrt';
  if (isLocked.value) return 'Notfall';
  if (!store.reservedByMe) return 'Frei';
  if (errorIds.value.has(position.id)) return 'Fehler';
  if (firedIds.value.has(position.id)) return 'Ausgelöst';
  if (programStore.recording[position.id]) return 'Erfasst';
  if (programStore.programMode && programStore.pairPending?.id === position.id) return 'Gewählt';
  return 'Bereit';
};
</script>
```

- [ ] **Step 4: Replace the `<template>` device section and header**

In the `<template>`, make these changes:

**4a — Remove the group dropdown block** (the entire `<div v-if="deviceGroups.length > 1" class="group-dropdown-wrapper"...>` block inside `.header-center`).

**4b — Replace the device section** (the entire `<div class="device-section">` block):

```html
<!-- Device section -->
<div class="device-section">
  <p v-if="rangePositions.length > 0" class="section-title">
    {{ rangePositions.length }} {{ rangePositions.length === 1 ? 'Position' : 'Positionen' }}
  </p>

  <div v-if="positionsLoading" class="state-center">
    <p class="state-text">Lade Positionen…</p>
  </div>

  <div v-else-if="rangePositions.length === 0" class="state-center">
    <Icons icon="bolt" :size="44" color="rgba(255,255,255,0.1)" />
    <p class="state-text">Keine Positionen konfiguriert</p>
    <p class="state-hint">Bitte einen Administrator kontaktieren.</p>
  </div>

  <div v-else class="device-grid">
    <button
      v-for="position in rangePositions"
      :key="position.id"
      class="device-btn"
      :class="positionBtnClass(position)"
      :disabled="isPositionDisabled(position)"
      @click="handlePositionTap(position)"
    >
      <div class="btn-glow" />
      <div class="btn-icon-wrap">
        <span class="btn-letter" :style="{ color: iconColor(position) }">
          {{ position.label }}
        </span>
      </div>
      <span class="btn-label">{{ position.device?.alias ?? 'Kein Gerät' }}</span>
      <span class="btn-status-chip" :class="chipClass(position)">{{ chipLabel(position) }}</span>
    </button>
  </div>
</div>
```

- [ ] **Step 5: Add missing CSS classes to `<style scoped>`**

Append before the closing `</style>` tag:

```css
.chip--no-device { background: rgba(255,255,255,0.05); color: rgba(255,255,255,0.2); }
.device-btn--no-device { opacity: 0.3; }
```

- [ ] **Step 6: Run the tests to confirm they pass**

```bash
npm run test src/components/__tests__/ShooterRemoteView.test.js
```

Expected: PASS (3 tests)

- [ ] **Step 7: Run the linter**

```bash
npm run lint
```

Expected: no errors. Fix any unused-variable warnings from removed device imports.

- [ ] **Step 8: Commit**

```bash
git add src/views/shooter/ShooterRemoteView.vue src/components/__tests__/ShooterRemoteView.test.js
git commit -m "[ui] Rewrite ShooterRemoteView to use RangePosition entities"
```

---

## Task 4: Simplify `ShooterFlyoutPanel` label display

**Files:**
- Modify: `src/components/shooter-remote/ShooterFlyoutPanel.vue`
- Create: `src/components/__tests__/ShooterFlyoutPanel.test.js`

- [ ] **Step 1: Write the failing test**

Create `src/components/__tests__/ShooterFlyoutPanel.test.js`:

```javascript
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ShooterFlyoutPanel from '../shooter-remote/ShooterFlyoutPanel.vue'
import { useProgramStore } from '../../stores/programStore.js'
import { useShooterRemoteStore } from '../../stores/shooterRemoteStore.js'

const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }] })

describe('ShooterFlyoutPanel getStepLabel', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shrunk recording summary shows stored letter for solo step', async () => {
    const remote = useShooterRemoteStore()
    remote.reservePlatz('range-1')
    remote.setSessionMode('recording')

    const programStore = useProgramStore()
    // Inject a step with stored letter directly (simulates addStep result)
    programStore.editingAblauf = [{
      id: 'abl-1',
      steps: [{ id: 1, type: 'solo', positionId: 'pos-x', letter: 'C', alias: 'Werfer 3' }],
    }]
    programStore.programMode = true

    const wrapper = mount(ShooterFlyoutPanel, {
      global: { plugins: [createPinia(), router] },
    })
    await wrapper.vm.$nextTick()

    // In shrunk mode (panel not open, recording active), captured-items show step labels
    expect(wrapper.text()).toContain('C')
  })

  it('shrunk recording summary shows pair letters from stored fields', async () => {
    const remote = useShooterRemoteStore()
    remote.reservePlatz('range-1')
    remote.setSessionMode('recording')

    const programStore = useProgramStore()
    programStore.editingAblauf = [{
      id: 'abl-1',
      steps: [{
        id: 1, type: 'pair',
        positionId1: 'pos-a', positionId2: 'pos-b',
        letter1: 'A', letter2: 'B',
        alias1: 'W1', alias2: 'W2',
      }],
    }]
    programStore.programMode = true

    const wrapper = mount(ShooterFlyoutPanel, {
      global: { plugins: [createPinia(), router] },
    })
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('A+B')
  })
})
```

- [ ] **Step 2: Run the tests to confirm they fail**

```bash
npm run test src/components/__tests__/ShooterFlyoutPanel.test.js
```

Expected: FAIL — `getLetterForDevice` tries to find `positionId` in a device list and returns `?`

- [ ] **Step 3: Update `ShooterFlyoutPanel.vue` script**

In `<script setup>`, make these changes:

**3a — Remove the `deviceStore` import and usage.** Delete:
```javascript
import { useDeviceStore } from '@/stores/deviceStore.js';
```
and:
```javascript
const deviceStore = useDeviceStore();
```

**3b — Remove `rangeDevices` and `activeDevices` computeds.** Delete these two computed blocks:
```javascript
const rangeDevices = computed(() =>
  deviceStore.devices.filter((d) => d.rangeId === store.selectedRangeId),
);

const activeDevices = computed(() => {
  if (!store.selectedGroupId) return [];
  return rangeDevices.value.filter(
    (d) => (d.groupId ?? encodeURIComponent(d.groupName ?? '')) === store.selectedGroupId,
  );
});
```

**3c — Remove `getLetterForDevice` and replace `getStepLabel`.** Delete:
```javascript
const getLetterForDevice = (deviceId) => {
  const idx = activeDevices.value.findIndex((d) => d.id === deviceId);
  return idx === -1 ? '?' : String.fromCharCode(65 + idx);
};
```

Replace the existing `getStepLabel` function with:
```javascript
const getStepLabel = (step) => {
  if (step.type === 'solo') return step.letter ?? '?';
  if (step.type === 'pair') return `${step.letter1 ?? '?'}+${step.letter2 ?? '?'}`;
  if (step.type === 'a_schuss') return `${step.letter1 ?? '?'}+${step.letter2 ?? '?'}`;
  if (step.type === 'raffale') return `${step.letter ?? '?'}×2`;
  return '?';
};
```

- [ ] **Step 4: Run the tests to confirm they pass**

```bash
npm run test src/components/__tests__/ShooterFlyoutPanel.test.js
```

Expected: PASS (2 tests)

- [ ] **Step 5: Run the full test suite**

```bash
npm run test
```

Expected: all tests pass

- [ ] **Step 6: Run the linter**

```bash
npm run lint
```

Expected: no errors

- [ ] **Step 7: Commit**

```bash
git add src/components/shooter-remote/ShooterFlyoutPanel.vue src/components/__tests__/ShooterFlyoutPanel.test.js
git commit -m "[ui] Simplify ShooterFlyoutPanel: read step letters from stored fields"
```

---

## Self-Review Checklist

- [x] **Spec §1 (Data model):** Task 3 removes device/group logic, uses `rangeStore.positions`
- [x] **Spec §2 (Button grid):** Task 3 uses `position.label`, `position.device?.alias`, disabled on `!position.device`
- [x] **Spec §3 (Fire path):** Task 1 adds `sendPositionCommand`; Task 3 uses it in `fireSinglePosition` / `firePairPositions` / `fireRaffalePosition`
- [x] **Spec §4 (Recording):** Task 2 updates `addStep`; Task 4 removes the device-index letter lookup
- [x] **Type consistency:** `positionId` used consistently across Tasks 2, 3, 4; `positionId1/2` in pair steps matches usage in `getStepLabel`
- [x] **No placeholders:** all steps contain complete code
