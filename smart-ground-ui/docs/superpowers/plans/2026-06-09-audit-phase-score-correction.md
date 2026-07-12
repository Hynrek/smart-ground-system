# Audit Phase & Score Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-shooter confirmation checkboxes (competition mode only) and tappable score correction to the final score screen of `ShooterPlayPage`.

**Architecture:** Store-level: `playSessionStore` gains `correctStep`, `confirmPlayer`, `unconfirmPlayer`, `allPlayersConfirmed`, and `playerConfirmations`. `ScoreTable` gets an `editable` prop that enables tap-to-correct and a corrected indicator. `ShooterPlayPage` adds a correction picker overlay and the audit checkbox section above the Beenden button.

**Tech Stack:** Vue 3 Composition API, Pinia, Vitest + @vue/test-utils

---

## File Map

| File | Change |
|---|---|
| `src/stores/playSessionStore.js` | Add `corrected`/`originalState` to stepState init; add `correctStep`, `confirmPlayer`, `unconfirmPlayer`, `allPlayersConfirmed`, `playerConfirmations`; init/reset confirmations in `startGroupPlay` / `closePlayback` |
| `src/stores/__tests__/playSessionStore.correction.test.js` | New test file covering `correctStep` and confirmation state |
| `src/components/shooter/ScoreTable.vue` | Add `editable` prop, `correct-step` emit, `corrected` badge column |
| `src/components/__tests__/ScoreTable.test.js` | New test file covering editable behaviour |
| `src/views/shooter/ShooterPlayPage.vue` | Add correction picker overlay, audit checkbox section, gated Beenden |
| `src/components/__tests__/ShooterPlayPageAudit.test.js` | New test file covering picker and audit checkbox |

---

## Task 1: `correctStep` action + stepState shape

**Files:**
- Modify: `src/stores/playSessionStore.js`
- Create: `src/stores/__tests__/playSessionStore.correction.test.js`

- [ ] **Step 1: Create the test file**

`src/stores/__tests__/playSessionStore.correction.test.js`:

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePlaySessionStore } from '../playSessionStore.js'
import { StepState } from '@/constants/playEnums.js'

vi.mock('@/stores/passeStore.js', () => ({
  usePasseStore: () => ({ savedPassen: [] }),
}))
vi.mock('@/services/rangePositionApi.js', () => ({
  sendPositionCommand: vi.fn().mockResolvedValue(undefined),
}))

describe('correctStep', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('overrides step state, marks corrected, saves originalState, and recalculates points', () => {
    const store = usePlaySessionStore()
    store.playScore.stepStates = [
      {
        playerId: 'p1', serieIndex: 0, stepIndex: 0,
        state: StepState.DONE, pointValue: 1, pointsEarned: 1,
        noBirds: 0, corrected: false, originalState: null,
      },
    ]
    store.playScore.totalPoints = 1

    store.correctStep('p1', 0, 0, StepState.FAILED_BOTH)

    const s = store.playScore.stepStates[0]
    expect(s.state).toBe(StepState.FAILED_BOTH)
    expect(s.corrected).toBe(true)
    expect(s.originalState).toBe(StepState.DONE)
    expect(s.pointsEarned).toBe(0)
    expect(store.playScore.totalPoints).toBe(0)
  })

  it('does not overwrite originalState on a second correction', () => {
    const store = usePlaySessionStore()
    store.playScore.stepStates = [
      {
        playerId: 'p1', serieIndex: 0, stepIndex: 0,
        state: StepState.FAILED_A, pointValue: 2, pointsEarned: 1,
        noBirds: 0, corrected: true, originalState: StepState.DONE,
      },
    ]
    store.playScore.totalPoints = 1

    store.correctStep('p1', 0, 0, StepState.FAILED_BOTH)

    expect(store.playScore.stepStates[0].originalState).toBe(StepState.DONE)
    expect(store.playScore.stepStates[0].state).toBe(StepState.FAILED_BOTH)
    expect(store.playScore.totalPoints).toBe(0)
  })

  it('correctly updates points when correcting a fail back to done', () => {
    const store = usePlaySessionStore()
    store.playScore.stepStates = [
      {
        playerId: 'p1', serieIndex: 0, stepIndex: 0,
        state: StepState.FAILED_BOTH, pointValue: 2, pointsEarned: 0,
        noBirds: 0, corrected: false, originalState: null,
      },
    ]
    store.playScore.totalPoints = 0

    store.correctStep('p1', 0, 0, StepState.DONE)

    expect(store.playScore.stepStates[0].pointsEarned).toBe(2)
    expect(store.playScore.totalPoints).toBe(2)
  })

  it('does nothing when playerId/index does not match', () => {
    const store = usePlaySessionStore()
    store.playScore.stepStates = []
    store.playScore.totalPoints = 0

    store.correctStep('unknown', 0, 0, StepState.DONE)

    expect(store.playScore.totalPoints).toBe(0)
  })
})
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
npm run test src/stores/__tests__/playSessionStore.correction.test.js
```

Expected: FAIL — `store.correctStep is not a function`

- [ ] **Step 3: Add `corrected` and `originalState` to all three stepState init blocks**

In `src/stores/playSessionStore.js`, there are three places that push to `stepStates`. Find each `stepStates.push({` call and add the two new fields. The three locations are:

1. `playPasseWithScore` (~line 306)
2. `startGroupPlay` (~line 527)
3. `resumeSession` (~line 263)

For every `stepStates.push({...})` block, add after `pointsEarned: 0,`:

```js
corrected: false,
originalState: null,
```

Example — the push block becomes:

```js
stepStates.push({
  playerId: player.id,
  serieIndex: segIdx,
  stepIndex: stepIdx,
  state: StepState.PENDING,
  pointValue: getPointValueForStep(step),
  noBirds: 0,
  pointsEarned: 0,
  corrected: false,
  originalState: null,
})
```

- [ ] **Step 4: Add the `correctStep` action**

In `src/stores/playSessionStore.js`, add after the `retryStep` function (around line 454):

```js
const correctStep = (playerId, serieIdx, stepIdx, newState) => {
  const stepState = playScore.value.stepStates.find(
    (s) => s.playerId === playerId && s.serieIndex === serieIdx && s.stepIndex === stepIdx
  )
  if (!stepState) return
  if (!stepState.corrected) {
    stepState.originalState = stepState.state
  }
  stepState.corrected = true
  playScore.value.totalPoints -= stepState.pointsEarned
  stepState.state = newState
  stepState.pointsEarned = Math.max(0, stepState.pointValue - getPointDeduction(newState))
  playScore.value.totalPoints += stepState.pointsEarned
}
```

Then add `correctStep` to the return object at the bottom of the store.

- [ ] **Step 5: Run tests and confirm they pass**

```bash
npm run test src/stores/__tests__/playSessionStore.correction.test.js
```

Expected: PASS — 4 tests

- [ ] **Step 6: Commit**

```bash
git add src/stores/playSessionStore.js src/stores/__tests__/playSessionStore.correction.test.js
git commit -m "[ui] add correctStep action and corrected/originalState fields to stepState"
```

---

## Task 2: `playerConfirmations` state + confirmation actions

**Files:**
- Modify: `src/stores/playSessionStore.js`
- Modify: `src/stores/__tests__/playSessionStore.correction.test.js`

- [ ] **Step 1: Add confirmation tests to the test file**

Append to `src/stores/__tests__/playSessionStore.correction.test.js`:

```js
describe('playerConfirmations', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('confirmPlayer marks the player as confirmed', () => {
    const store = usePlaySessionStore()
    store.sessionPlayers = [{ id: 'p1', displayName: 'Alice' }]
    store.playerConfirmations = new Map([['p1', false]])

    store.confirmPlayer('p1')

    expect(store.playerConfirmations.get('p1')).toBe(true)
  })

  it('unconfirmPlayer marks the player as not confirmed', () => {
    const store = usePlaySessionStore()
    store.sessionPlayers = [{ id: 'p1', displayName: 'Alice' }]
    store.playerConfirmations = new Map([['p1', true]])

    store.unconfirmPlayer('p1')

    expect(store.playerConfirmations.get('p1')).toBe(false)
  })

  it('allPlayersConfirmed is false when any player is unconfirmed', () => {
    const store = usePlaySessionStore()
    store.sessionPlayers = [
      { id: 'p1', displayName: 'Alice' },
      { id: 'p2', displayName: 'Bob' },
    ]
    store.playerConfirmations = new Map([['p1', true], ['p2', false]])

    expect(store.allPlayersConfirmed).toBe(false)
  })

  it('allPlayersConfirmed is true when every player is confirmed', () => {
    const store = usePlaySessionStore()
    store.sessionPlayers = [
      { id: 'p1', displayName: 'Alice' },
      { id: 'p2', displayName: 'Bob' },
    ]
    store.playerConfirmations = new Map([['p1', true], ['p2', true]])

    expect(store.allPlayersConfirmed).toBe(true)
  })

  it('allPlayersConfirmed is false when sessionPlayers is empty', () => {
    const store = usePlaySessionStore()
    store.sessionPlayers = []
    store.playerConfirmations = new Map()

    expect(store.allPlayersConfirmed).toBe(false)
  })
})
```

- [ ] **Step 2: Run to confirm failure**

```bash
npm run test src/stores/__tests__/playSessionStore.correction.test.js
```

Expected: FAIL — `store.confirmPlayer is not a function` / `store.allPlayersConfirmed` is undefined

- [ ] **Step 3: Add `playerConfirmations` ref**

In `src/stores/playSessionStore.js`, in the state declarations section (near the other `ref()` declarations, around line 43), add:

```js
const playerConfirmations = ref(new Map()) // playerId → boolean
```

- [ ] **Step 4: Add `confirmPlayer`, `unconfirmPlayer`, `allPlayersConfirmed`**

Add after the `correctStep` function:

```js
const confirmPlayer = (playerId) => {
  const map = new Map(playerConfirmations.value)
  map.set(playerId, true)
  playerConfirmations.value = map
}

const unconfirmPlayer = (playerId) => {
  const map = new Map(playerConfirmations.value)
  map.set(playerId, false)
  playerConfirmations.value = map
}

const allPlayersConfirmed = computed(() =>
  sessionPlayers.value.length > 0 &&
  sessionPlayers.value.every((p) => playerConfirmations.value.get(p.id) === true)
)
```

- [ ] **Step 5: Initialise confirmations in `startGroupPlay`**

In the `startGroupPlay` function, directly after the `setupPlayers(players)` call, add:

```js
playerConfirmations.value = new Map(players.map((p) => [p.id, false]))
```

- [ ] **Step 6: Reset confirmations in `closePlayback`**

In the `closePlayback` function, add:

```js
playerConfirmations.value = new Map()
```

- [ ] **Step 7: Add to the store's return object**

Add these four entries to the `return { ... }` block at the bottom:

```js
playerConfirmations,
allPlayersConfirmed,
confirmPlayer,
unconfirmPlayer,
```

- [ ] **Step 8: Run all confirmation tests**

```bash
npm run test src/stores/__tests__/playSessionStore.correction.test.js
```

Expected: PASS — all 9 tests (4 from Task 1 + 5 from Task 2)

- [ ] **Step 9: Commit**

```bash
git add src/stores/playSessionStore.js src/stores/__tests__/playSessionStore.correction.test.js
git commit -m "[ui] add playerConfirmations state and confirmation actions to playSessionStore"
```

---

## Task 3: ScoreTable — `editable` prop, `correct-step` emit, corrected badge

**Files:**
- Modify: `src/components/shooter/ScoreTable.vue`
- Create: `src/components/__tests__/ScoreTable.test.js`

- [ ] **Step 1: Create test file**

`src/components/__tests__/ScoreTable.test.js`:

```js
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ScoreTable from '@/components/shooter/ScoreTable.vue'
import { StepState, StepType } from '@/constants/playEnums.js'

const mockProgram = [
  { steps: [{ type: StepType.SOLO, letter: 'A', positionId: 'pos-1' }] },
]

const baseStepState = {
  playerId: 'p1',
  serieIndex: 0,
  stepIndex: 0,
  state: StepState.DONE,
  pointValue: 1,
  pointsEarned: 1,
  noBirds: 0,
  corrected: false,
  originalState: null,
}

const mockPlayers = [{ id: 'p1', displayName: 'Alice' }]

describe('ScoreTable', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('does not emit correct-step on row click when editable is false', async () => {
    const wrapper = mount(ScoreTable, {
      props: {
        stepStates: [baseStepState],
        program: mockProgram,
        players: mockPlayers,
        editable: false,
      },
    })

    await wrapper.find('.step-row').trigger('click')

    expect(wrapper.emitted('correct-step')).toBeFalsy()
  })

  it('emits correct-step with full payload on row click when editable is true', async () => {
    const wrapper = mount(ScoreTable, {
      props: {
        stepStates: [baseStepState],
        program: mockProgram,
        players: mockPlayers,
        editable: true,
      },
    })

    await wrapper.find('.step-row').trigger('click')

    expect(wrapper.emitted('correct-step')).toBeTruthy()
    expect(wrapper.emitted('correct-step')[0][0]).toEqual({
      playerId: 'p1',
      serieIndex: 0,
      stepIndex: 0,
      currentState: StepState.DONE,
    })
  })

  it('shows corrected-badge when step is corrected and editable is true', () => {
    const correctedState = { ...baseStepState, corrected: true }
    const wrapper = mount(ScoreTable, {
      props: {
        stepStates: [correctedState],
        program: mockProgram,
        players: mockPlayers,
        editable: true,
      },
    })

    expect(wrapper.find('.corrected-badge').text()).toBe('✏')
  })

  it('does not show corrected badge content when step is not corrected', () => {
    const wrapper = mount(ScoreTable, {
      props: {
        stepStates: [baseStepState],
        program: mockProgram,
        players: mockPlayers,
        editable: true,
      },
    })

    expect(wrapper.find('.corrected-badge').text()).toBe('')
  })
})
```

- [ ] **Step 2: Run to confirm failure**

```bash
npm run test src/components/__tests__/ScoreTable.test.js
```

Expected: FAIL — `correct-step` never emitted

- [ ] **Step 3: Add `editable` prop and `correct-step` emit to ScoreTable**

In `src/components/shooter/ScoreTable.vue`, in `<script setup>`, after the existing `defineProps`, add:

```js
const emit = defineEmits(['correct-step'])
```

And extend the props definition to include `editable`:

```js
const props = defineProps({
  stepStates: {
    type: Array,
    required: true,
  },
  program: {
    type: Array,
    required: true,
  },
  players: {
    type: Array,
    required: true,
  },
  editable: {
    type: Boolean,
    default: false,
  },
})
```

- [ ] **Step 4: Update the step row template**

Replace the existing `<div v-for="step in getPlayerSteps(player.id)" ...>` block in `<template>` with:

```vue
<div
  v-for="step in getPlayerSteps(player.id)"
  :key="`${step.serieIndex}-${step.stepIndex}`"
  class="step-row"
  :class="[getStepRowClass(step), { 'is-editable': props.editable }]"
  @click="props.editable && emit('correct-step', { playerId: player.id, serieIndex: step.serieIndex, stepIndex: step.stepIndex, currentState: step.state })"
>
  <span class="step-letters">{{ getLetters(step) }}</span>
  <span class="step-type">{{ getTypeLabel(getActualStep(step)) }}</span>
  <span class="step-status">{{ getStateLabel(step.state, getActualStep(step)) }}</span>
  <span class="step-points">{{ getPointsDisplay(step) }}</span>
  <span v-if="props.editable" class="corrected-badge">{{ step.corrected ? '✏' : '' }}</span>
</div>
```

- [ ] **Step 5: Update grid template and add new CSS**

In `<style scoped>`, change the `.step-row` rule's `grid-template-columns` and add new rules below:

```css
.step-row {
  display: grid;
  grid-template-columns: 40px 1fr auto auto;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  align-items: center;
}

.step-row.is-editable {
  grid-template-columns: 40px 1fr auto auto 16px;
  cursor: pointer;
  transition: background 0.12s;
}

.step-row.is-editable:hover {
  background: rgba(255, 255, 255, 0.05);
}

.corrected-badge {
  text-align: right;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
}
```

- [ ] **Step 6: Run tests and confirm they pass**

```bash
npm run test src/components/__tests__/ScoreTable.test.js
```

Expected: PASS — 4 tests

- [ ] **Step 7: Run full test suite to check for regressions**

```bash
npm run test
```

Expected: all existing tests still pass

- [ ] **Step 8: Commit**

```bash
git add src/components/shooter/ScoreTable.vue src/components/__tests__/ScoreTable.test.js
git commit -m "[ui] add editable prop, correct-step emit, and corrected badge to ScoreTable"
```

---

## Task 4: ShooterPlayPage — correction picker overlay

**Files:**
- Modify: `src/views/shooter/ShooterPlayPage.vue`
- Create: `src/components/__tests__/ShooterPlayPageAudit.test.js`

- [ ] **Step 1: Create test file**

`src/components/__tests__/ShooterPlayPageAudit.test.js`:

```js
import { describe, it, expect, beforeEach, vi, nextTick } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ShooterPlayPage from '@/views/shooter/ShooterPlayPage.vue'
import { usePlaySessionStore } from '@/stores/playSessionStore.js'
import { StepState, StepType } from '@/constants/playEnums.js'

vi.mock('@/stores/passeStore.js', () => ({
  usePasseStore: () => ({
    savedPassen: [{ id: 'passe-1', name: 'Test', serien: [{ steps: [] }] }],
  }),
}))
vi.mock('@/services/rangePositionApi.js', () => ({
  sendPositionCommand: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('@/components/Icons.vue', () => ({
  default: { name: 'Icons', props: ['icon', 'size', 'color'], template: '<span />' },
}))
// Stub ScoreTable so we can emit correct-step directly from the component instance
vi.mock('@/components/shooter/ScoreTable.vue', () => ({
  default: {
    name: 'ScoreTable',
    props: ['stepStates', 'program', 'players', 'editable'],
    emits: ['correct-step'],
    template: '<div class="score-table-stub" />',
  },
}))

const mockProgram = [{ steps: [{ type: StepType.SOLO, letter: 'A', positionId: 'pos-1' }] }]
const mockPlayers = [{ id: 'p1', displayName: 'Alice' }]
const mockStepStates = [
  { playerId: 'p1', serieIndex: 0, stepIndex: 0, state: StepState.DONE,
    pointValue: 1, pointsEarned: 1, noBirds: 0, corrected: false, originalState: null },
]

const makeRouter = () =>
  createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }],
  })

// Mounts ShooterPlayPage showing the final score screen in competition mode.
// pendingPasseInfo with instanceType 'competition' sets _blockContext (_isCompetitionMode = true)
// during script setup. After mount we force playComplete = true to show the final screen.
const mountFinalScreen = async () => {
  const router = makeRouter()
  await router.push('/remote/r1/play')
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = usePlaySessionStore()

  // Pre-set playProg so the redirect guard at top of setup does NOT fire
  store.playProg = mockProgram

  // pendingPasseInfo sets _blockContext (instanceType: 'competition') during setup
  store.pendingPasseInfo = {
    passeId: 'passe-1',
    rangeId: 'r1',
    instanceId: 'inst-1',
    blockId: 'blk-1',
    rotteId: null,
    instanceType: 'competition',
    rotteName: 'Rotte 1',
    serieName: 'Serie A',
    serie: { steps: [] },
    players: mockPlayers,
  }

  const wrapper = mount(ShooterPlayPage, {
    props: { rangeId: 'r1' },
    global: { plugins: [router, pinia] },
  })

  // Script setup has now consumed pendingPasseInfo; force the final screen state
  store.showGroupSetup = false
  store.playComplete = true
  store.sessionPlayers = mockPlayers
  store.playScore = { totalPoints: 1, stepStates: mockStepStates }
  store.playerConfirmations = new Map([['p1', false]])
  await nextTick()

  return { wrapper, store }
}

describe('ShooterPlayPage — correction picker', () => {
  it('correction picker is hidden by default on final screen', async () => {
    const { wrapper } = await mountFinalScreen()
    expect(wrapper.find('.correction-overlay').exists()).toBe(false)
  })

  it('picker appears after ScoreTable emits correct-step', async () => {
    const { wrapper } = await mountFinalScreen()
    const scoreTable = wrapper.findComponent({ name: 'ScoreTable' })
    await scoreTable.vm.$emit('correct-step', {
      playerId: 'p1', serieIndex: 0, stepIndex: 0, currentState: StepState.DONE,
    })
    await nextTick()
    expect(wrapper.find('.correction-overlay').exists()).toBe(true)
  })

  it('clicking the backdrop dismisses picker and does not call correctStep', async () => {
    const { wrapper, store } = await mountFinalScreen()
    store.correctStep = vi.fn()
    const scoreTable = wrapper.findComponent({ name: 'ScoreTable' })
    await scoreTable.vm.$emit('correct-step', {
      playerId: 'p1', serieIndex: 0, stepIndex: 0, currentState: StepState.DONE,
    })
    await nextTick()
    await wrapper.find('.correction-overlay').trigger('click')
    await nextTick()
    expect(wrapper.find('.correction-overlay').exists()).toBe(false)
    expect(store.correctStep).not.toHaveBeenCalled()
  })
})

- [ ] **Step 2: Run to confirm failure**

```bash
npm run test src/components/__tests__/ShooterPlayPageAudit.test.js
```

Expected: FAIL — `.correction-overlay` not found

- [ ] **Step 3: Add `correctionTarget` ref and handlers to ShooterPlayPage**

In `src/views/shooter/ShooterPlayPage.vue`, in `<script setup>`, after the existing refs (around line 270), add:

```js
const correctionTarget = ref(null)

const correctionTargetIsDouble = computed(() => {
  if (!correctionTarget.value) return false
  const { serieIndex, stepIndex } = correctionTarget.value
  const step = store.playProg?.[serieIndex]?.steps[stepIndex]
  return step ? [StepType.PAIR, StepType.A_SCHUSS, StepType.RAFFALE].includes(step.type) : false
})

const handleCorrectStep = (payload) => {
  correctionTarget.value = payload
}

const applyCorrectionStep = (newState) => {
  const t = correctionTarget.value
  if (!t) return
  store.correctStep(t.playerId, t.serieIndex, t.stepIndex, newState)
  correctionTarget.value = null
}
```

Also add `StepState` to the existing import at the top of `<script setup>` — it's already imported from `playEnums.js`, so just confirm `StepState` is in the destructure:

```js
import { StepState, StepType } from '@/constants/playEnums.js';
```

- [ ] **Step 4: Add the correction picker overlay to the template**

In `src/views/shooter/ShooterPlayPage.vue`, inside the `v-else-if="store.playProg"` main play div, add this as the **last child** of the outer `.play-page` div (after the progress dots, before the closing `</div>`):

```vue
<!-- Correction picker overlay -->
<Transition name="correction-fade">
  <div v-if="correctionTarget" class="correction-overlay" @click.self="correctionTarget = null">
    <div class="correction-picker">
      <div class="picker-title">Schritt korrigieren</div>
      <div class="picker-buttons">
        <button class="picker-btn btn-getroffen" @click="applyCorrectionStep(StepState.DONE)">
          Getroffen
        </button>
        <template v-if="correctionTargetIsDouble">
          <button class="picker-btn btn-fail" @click="applyCorrectionStep(StepState.FAILED_A)">Fail A</button>
          <button class="picker-btn btn-fail" @click="applyCorrectionStep(StepState.FAILED_B)">Fail B</button>
          <button class="picker-btn btn-fail" @click="applyCorrectionStep(StepState.FAILED_BOTH)">Fail Beide</button>
        </template>
        <template v-else>
          <button class="picker-btn btn-fail" @click="applyCorrectionStep(StepState.FAILED_BOTH)">Fail</button>
        </template>
      </div>
    </div>
  </div>
</Transition>
```

- [ ] **Step 5: Add picker CSS to `<style scoped>`**

Append to the scoped styles:

```css
/* ── Correction picker ───────────────────────────── */
.correction-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.correction-picker {
  background: rgba(24, 24, 40, 0.98);
  border: 1.5px solid rgba(255, 255, 255, 0.12);
  border-radius: 18px;
  padding: 24px 20px;
  width: min(320px, 90vw);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.picker-title {
  font-size: 15px;
  font-weight: 700;
  color: #ffffff;
  text-align: center;
}

.picker-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.picker-btn {
  width: 100%;
  padding: 12px;
  border-radius: 10px;
  border: none;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}

.picker-btn.btn-getroffen {
  background: rgba(72, 187, 120, 0.2);
  color: var(--sg-color-success);
  border: 1px solid rgba(72, 187, 120, 0.35);
}

.picker-btn.btn-getroffen:hover {
  background: rgba(72, 187, 120, 0.28);
}

.picker-btn.btn-fail {
  background: rgba(252, 129, 129, 0.15);
  color: var(--sg-color-danger-bg);
  border: 1px solid rgba(252, 129, 129, 0.3);
}

.picker-btn.btn-fail:hover {
  background: rgba(252, 129, 129, 0.22);
}

.correction-fade-enter-active,
.correction-fade-leave-active {
  transition: opacity 0.15s;
}

.correction-fade-enter-from,
.correction-fade-leave-to {
  opacity: 0;
}
```

- [ ] **Step 6: Wire `editable` and `@correct-step` on both ScoreTable usages in the final screens**

There are two `<ScoreTable>` usages in `ShooterPlayPage.vue` — one in the solo final screen and one in the group final screen. Update both from:

```vue
<ScoreTable
  v-if="store.sessionPlayers.length > 0"
  :step-states="store.playScore.stepStates"
  :program="store.playProg"
  :players="store.sessionPlayers"
/>
```

to:

```vue
<ScoreTable
  v-if="store.sessionPlayers.length > 0"
  :step-states="store.playScore.stepStates"
  :program="store.playProg"
  :players="store.sessionPlayers"
  :editable="true"
  @correct-step="handleCorrectStep"
/>
```

- [ ] **Step 7: Run tests**

```bash
npm run test src/components/__tests__/ShooterPlayPageAudit.test.js
```

Expected: PASS — 3 tests

- [ ] **Step 8: Commit**

```bash
git add src/views/shooter/ShooterPlayPage.vue src/components/__tests__/ShooterPlayPageAudit.test.js
git commit -m "[ui] add score correction picker overlay to ShooterPlayPage final screen"
```

---

## Task 5: ShooterPlayPage — audit checkboxes + gated Beenden

**Files:**
- Modify: `src/views/shooter/ShooterPlayPage.vue`
- Modify: `src/components/__tests__/ShooterPlayPageAudit.test.js`

- [ ] **Step 1: Add audit tests to the test file**

Append to `src/components/__tests__/ShooterPlayPageAudit.test.js` (uses `mountFinalScreen` already defined in the same file):

```js
describe('ShooterPlayPage — audit checkboxes', () => {
  it('audit section is visible in competition mode on final screen', async () => {
    const { wrapper } = await mountFinalScreen()
    expect(wrapper.find('.audit-section').exists()).toBe(true)
  })

  it('Beenden button is disabled when playerConfirmations has an unconfirmed player', async () => {
    const { wrapper } = await mountFinalScreen()
    // store.allPlayersConfirmed is false (p1 not confirmed) so Beenden should be disabled
    const beenden = wrapper.findAll('button').find((b) => b.text() === 'Beenden')
    expect(beenden.attributes('disabled')).toBeDefined()
  })

  it('ticking a checkbox calls store.confirmPlayer with the player id', async () => {
    const { wrapper, store } = await mountFinalScreen()
    store.confirmPlayer = vi.fn()
    const checkbox = wrapper.find('.audit-checkbox')
    // Simulate checking the checkbox
    await checkbox.setValue(true)
    expect(store.confirmPlayer).toHaveBeenCalledWith('p1')
  })

  it('unticking a checkbox calls store.unconfirmPlayer with the player id', async () => {
    const { wrapper, store } = await mountFinalScreen()
    // Start with confirmed
    store.playerConfirmations = new Map([['p1', true]])
    store.unconfirmPlayer = vi.fn()
    await nextTick()
    const checkbox = wrapper.find('.audit-checkbox')
    await checkbox.setValue(false)
    expect(store.unconfirmPlayer).toHaveBeenCalledWith('p1')
  })
})
```

- [ ] **Step 2: Run to confirm failure**

```bash
npm run test src/components/__tests__/ShooterPlayPageAudit.test.js
```

Expected: FAIL — `.audit-section` not found

- [ ] **Step 3: Add audit section template — solo final screen**

In `src/views/shooter/ShooterPlayPage.vue`, in the **solo final score card** (`<div class="score-card solo-score-card">`), insert the audit section between the `<ScoreTable>` and the `<button class="btn btn-primary">` (Beenden):

```vue
<!-- Audit confirmations (competition mode only) -->
<div v-if="_isCompetitionMode" class="audit-section">
  <div class="audit-title">Bestätigung</div>
  <div v-for="ps in playerFinalScores" :key="ps.player.id" class="audit-row">
    <label class="audit-label">
      <input
        type="checkbox"
        class="audit-checkbox"
        :checked="store.playerConfirmations.get(ps.player.id) ?? false"
        @change="(e) => e.target.checked ? store.confirmPlayer(ps.player.id) : store.unconfirmPlayer(ps.player.id)"
      />
      <span class="audit-player-name">{{ ps.player.displayName }}</span>
      <span class="audit-score">{{ ps.earnedPts }}/{{ ps.maxPts }}</span>
    </label>
  </div>
</div>
```

Then update the **solo Beenden button** from:

```vue
<button class="btn btn-primary" @click="goBack">
  Beenden
</button>
```

to:

```vue
<button
  class="btn btn-primary"
  :disabled="_isCompetitionMode && !store.allPlayersConfirmed"
  @click="goBack"
>
  Beenden
</button>
```

- [ ] **Step 4: Add audit section template — group final screen**

In the **group final score card** (`<div class="score-card group-score-card">`), insert the same audit section between the `<ScoreTable>` and the group Beenden button:

```vue
<!-- Audit confirmations (competition mode only) -->
<div v-if="_isCompetitionMode" class="audit-section">
  <div class="audit-title">Bestätigung</div>
  <div v-for="ps in playerFinalScores" :key="ps.player.id" class="audit-row">
    <label class="audit-label">
      <input
        type="checkbox"
        class="audit-checkbox"
        :checked="store.playerConfirmations.get(ps.player.id) ?? false"
        @change="(e) => e.target.checked ? store.confirmPlayer(ps.player.id) : store.unconfirmPlayer(ps.player.id)"
      />
      <span class="audit-player-name">{{ ps.player.displayName }}</span>
      <span class="audit-score">{{ ps.earnedPts }}/{{ ps.maxPts }}</span>
    </label>
  </div>
</div>
```

Then update the **group Beenden button** the same way:

```vue
<button
  class="btn btn-primary"
  :disabled="_isCompetitionMode && !store.allPlayersConfirmed"
  @click="goBack"
>
  Beenden
</button>
```

- [ ] **Step 5: Add audit CSS to `<style scoped>`**

Append to scoped styles:

```css
/* ── Audit confirmations ─────────────────────────── */
.audit-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 0 4px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.audit-title {
  font-size: 11px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.4);
  text-transform: uppercase;
  letter-spacing: 0.8px;
}

.audit-row {
  display: flex;
}

.audit-label {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  cursor: pointer;
  padding: 8px 10px;
  border-radius: 8px;
  transition: background 0.15s;
}

.audit-label:hover {
  background: rgba(255, 255, 255, 0.04);
}

.audit-checkbox {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  accent-color: var(--sg-accent);
}

.audit-player-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.85);
}

.audit-score {
  font-size: 13px;
  font-weight: 700;
  color: var(--sg-color-success);
}

.btn-primary:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
```

- [ ] **Step 6: Run all tests**

```bash
npm run test src/components/__tests__/ShooterPlayPageAudit.test.js
```

Expected: PASS — 6 tests

- [ ] **Step 7: Run full suite**

```bash
npm run test
```

Expected: all tests pass

- [ ] **Step 8: Commit**

```bash
git add src/views/shooter/ShooterPlayPage.vue src/components/__tests__/ShooterPlayPageAudit.test.js
git commit -m "[ui] add audit checkboxes and gated Beenden to ShooterPlayPage final screen"
```
