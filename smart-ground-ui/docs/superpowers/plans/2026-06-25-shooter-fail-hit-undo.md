# Shooter `Fail · Treffer · No Bird` bar + in-play hit undo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a shooter revert an accidental fail mid-passe via a persistent `Treffer` button, and collapse the three fail buttons into one `Fail` button that opens a horizontal flyout for doubles.

**Architecture:** Add a pure label helper to `constants/stepModes.js`; add a `markLastStepHit` action + `canMarkHit` getter to `playSessionStore.js` (mirroring the existing `failStep`/`canRetry` pattern, deliberately bypassing `correctStep`'s audit flags); rewire the `ShooterPlayPage.vue` action bar to three buttons and add a bottom-sheet fail flyout.

**Tech Stack:** Vue 3 `<script setup>`, Pinia (Composition API store), Vitest + @vue/test-utils.

**Spec:** `docs/superpowers/specs/2026-06-25-shooter-fail-hit-undo-design.md`

---

## File Structure

- `src/constants/stepModes.js` — add pure `stepFailCells(step)` (flyout cell labels/costs). Single source of truth for step notation already lives here.
- `src/constants/__tests__/stepModes.test.js` — unit tests for `stepFailCells` (create if absent; otherwise append).
- `src/stores/playSessionStore.js` — add `canMarkHit` computed + `markLastStepHit` action, export both.
- `src/stores/__tests__/playSessionStore.markHit.test.js` — unit tests for the new store members.
- `src/views/shooter/ShooterPlayPage.vue` — three-button bar, fail flyout, wiring.
- `src/views/__tests__/ShooterPlayPage.test.js` — component test for bar behaviour (create).

---

## Task 1: `stepFailCells` label helper

**Files:**
- Modify: `src/constants/stepModes.js`
- Test: `src/constants/__tests__/stepModes.test.js`

- [ ] **Step 1: Write the failing test**

Create or append to `src/constants/__tests__/stepModes.test.js`:

```js
import { describe, it, expect } from 'vitest'
import { stepFailCells } from '../stepModes.js'
import { StepType } from '../playEnums.js'

describe('stepFailCells', () => {
  it('returns letter cells for a pair', () => {
    const cells = stepFailCells({ type: StepType.PAIR, letter1: 'A', letter2: 'B' })
    expect(cells).toEqual([
      { failType: 'a', label: 'A', cost: 1 },
      { failType: 'b', label: 'B', cost: 1 },
      { failType: 'both', label: 'A + B', cost: 2 },
    ])
  })

  it('returns letter cells for a.Schuss', () => {
    const cells = stepFailCells({ type: StepType.A_SCHUSS, letter1: 'A', letter2: 'C' })
    expect(cells.map((c) => c.label)).toEqual(['A', 'C', 'A + C'])
  })

  it('returns shot-indexed cells for raffale', () => {
    const cells = stepFailCells({ type: StepType.RAFFALE, letter: 'A' })
    expect(cells).toEqual([
      { failType: 'a', label: 'A1', cost: 1 },
      { failType: 'b', label: 'A2', cost: 1 },
      { failType: 'both', label: 'A×2', cost: 2 },
    ])
  })

  it('returns an empty array for a null step', () => {
    expect(stepFailCells(null)).toEqual([])
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test -- src/constants/__tests__/stepModes.test.js`
Expected: FAIL — `stepFailCells is not a function` (or import error).

- [ ] **Step 3: Implement the helper**

In `src/constants/stepModes.js`, add after `stepNotation` (it relies on `stepLetters` and `StepType`, both already in the file):

```js
/**
 * Fail-flyout cells for a double step: first-only / second-only / both, each with
 * a compact position-notation label and its point cost. Used by the in-play Fail
 * flyout. Solo steps never reach this (they fail in one tap), so only the double
 * types are handled. Raffale repeats its single trap letter across both shots.
 */
export function stepFailCells(step) {
  if (!step) return [];
  const { first, second } = stepLetters(step);
  if (step.type === StepType.RAFFALE) {
    const l = first || '?';
    return [
      { failType: 'a', label: `${l}1`, cost: 1 },
      { failType: 'b', label: `${l}2`, cost: 1 },
      { failType: 'both', label: `${l}×2`, cost: 2 },
    ];
  }
  const a = first || '?';
  const b = second || '?';
  return [
    { failType: 'a', label: `${a}`, cost: 1 },
    { failType: 'b', label: `${b}`, cost: 1 },
    { failType: 'both', label: `${a} + ${b}`, cost: 2 },
  ];
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test -- src/constants/__tests__/stepModes.test.js`
Expected: PASS (all 4).

- [ ] **Step 5: Commit**

```bash
git add src/constants/stepModes.js src/constants/__tests__/stepModes.test.js
git commit -m "[ui] add stepFailCells helper for fail flyout labels"
```

---

## Task 2: `canMarkHit` + `markLastStepHit` store members

**Files:**
- Modify: `src/stores/playSessionStore.js`
- Test: `src/stores/__tests__/playSessionStore.markHit.test.js`

- [ ] **Step 1: Write the failing test**

Create `src/stores/__tests__/playSessionStore.markHit.test.js`:

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

const seedSinglePlayer = (store, state, pointValue, pointsEarned) => {
  store.sessionPlayers = [{ id: 'p1', displayName: 'Alice' }]
  store.roundOrder = [0]
  store.currentPlayerIndex = 0
  store.playScore.stepStates = [
    {
      playerId: 'p1', serieIndex: 0, stepIndex: 0,
      state, pointValue, pointsEarned,
      noBirds: 0, corrected: false, originalState: null,
    },
  ]
  store.playScore.totalPoints = pointsEarned
  store.playLastDeviceStep = { serieIdx: 0, stepIdx: 0 }
}

describe('canMarkHit', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('is false when there is no last-fired step', () => {
    const store = usePlaySessionStore()
    expect(store.canMarkHit).toBe(false)
  })

  it('is false when the last step is a full hit', () => {
    const store = usePlaySessionStore()
    seedSinglePlayer(store, StepState.DONE, 1, 1)
    expect(store.canMarkHit).toBe(false)
  })

  it('is true when the last step is in a failed state', () => {
    const store = usePlaySessionStore()
    seedSinglePlayer(store, StepState.FAILED_A, 2, 1)
    expect(store.canMarkHit).toBe(true)
  })

  it('is false once the passe is complete', () => {
    const store = usePlaySessionStore()
    seedSinglePlayer(store, StepState.FAILED_A, 2, 1)
    store.playComplete = true
    expect(store.canMarkHit).toBe(false)
  })
})

describe('markLastStepHit', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('restores a partial double fail to a full hit', () => {
    const store = usePlaySessionStore()
    seedSinglePlayer(store, StepState.FAILED_A, 2, 1)

    store.markLastStepHit()

    const s = store.playScore.stepStates[0]
    expect(s.state).toBe(StepState.DONE)
    expect(s.pointsEarned).toBe(2)
    expect(store.playScore.totalPoints).toBe(2)
  })

  it('restores a full double fail to a full hit', () => {
    const store = usePlaySessionStore()
    seedSinglePlayer(store, StepState.FAILED_BOTH, 2, 0)

    store.markLastStepHit()

    const s = store.playScore.stepStates[0]
    expect(s.state).toBe(StepState.DONE)
    expect(s.pointsEarned).toBe(2)
    expect(store.playScore.totalPoints).toBe(2)
  })

  it('restores a solo fail (capped at pointValue) to a full hit', () => {
    const store = usePlaySessionStore()
    seedSinglePlayer(store, StepState.FAILED_BOTH, 1, 0)

    store.markLastStepHit()

    const s = store.playScore.stepStates[0]
    expect(s.state).toBe(StepState.DONE)
    expect(s.pointsEarned).toBe(1)
    expect(store.playScore.totalPoints).toBe(1)
  })

  it('does not flag the step as a correction (no audit trail)', () => {
    const store = usePlaySessionStore()
    seedSinglePlayer(store, StepState.FAILED_A, 2, 1)

    store.markLastStepHit()

    expect(store.playScore.stepStates[0].corrected).toBe(false)
    expect(store.playScore.stepStates[0].originalState).toBe(null)
  })

  it('is a no-op when the last step is already a full hit', () => {
    const store = usePlaySessionStore()
    seedSinglePlayer(store, StepState.DONE, 1, 1)

    store.markLastStepHit()

    expect(store.playScore.totalPoints).toBe(1)
    expect(store.playScore.stepStates[0].state).toBe(StepState.DONE)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test -- src/stores/__tests__/playSessionStore.markHit.test.js`
Expected: FAIL — `store.canMarkHit` is `undefined` / `store.markLastStepHit is not a function`.

- [ ] **Step 3: Implement the store members**

In `src/stores/playSessionStore.js`, add directly after the `canRetry` computed (ends ~line 132). `findStepState`, `getPointDeduction`, `StepState`, `playLastDeviceStep`, `playComplete`, and `playScore` are all already defined in the file:

```js
  // Treffer (hit undo) is only valid when the last-fired step currently carries a
  // fail deduction — i.e. there is an accidental fail to revert.
  const canMarkHit = computed(() => {
    if (!playLastDeviceStep.value || playComplete.value) return false;
    const { serieIdx, stepIdx } = playLastDeviceStep.value;
    const state = findStepState(serieIdx, stepIdx);
    return !!state && getPointDeduction(state.state) > 0;
  });

  // Revert the last-fired step to a full hit. Inverse of updateFailState's point
  // math. Intentionally does NOT use correctStep — corrections set the audit flags
  // (corrected/originalState) meant for the final score screen; an in-play mis-tap
  // fix is not an audited correction.
  const markLastStepHit = () => {
    if (!playLastDeviceStep.value || playComplete.value) return;
    const { serieIdx, stepIdx } = playLastDeviceStep.value;
    const state = findStepState(serieIdx, stepIdx);
    if (!state) return;
    const restore = Math.min(getPointDeduction(state.state), state.pointValue);
    if (restore === 0) return; // already a full hit / pending
    state.state = StepState.DONE;
    state.pointsEarned = Math.min(state.pointValue, state.pointsEarned + restore);
    playScore.value.totalPoints += restore;
  };
```

- [ ] **Step 4: Add both to the store's public return**

In the store's `return { ... }` object, add `canMarkHit` next to `canRetry`, and `markLastStepHit` next to `failStep`:

```js
    canRetry,
    canMarkHit,
```

```js
    failStep,
    markLastStepHit,
```

- [ ] **Step 5: Run test to verify it passes**

Run: `npm run test -- src/stores/__tests__/playSessionStore.markHit.test.js`
Expected: PASS (all cases).

- [ ] **Step 6: Run the existing play-session tests to confirm no regression**

Run: `npm run test -- src/stores/__tests__/playSessionStore.correction.test.js src/stores/__tests__/playSessionStore.competition.test.js`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/stores/playSessionStore.js src/stores/__tests__/playSessionStore.markHit.test.js
git commit -m "[ui] add canMarkHit + markLastStepHit to playSessionStore"
```

---

## Task 3: Rewire the action bar + add the fail flyout

**Files:**
- Modify: `src/views/shooter/ShooterPlayPage.vue` (template ~263-307, script imports ~341 + handlers ~688-691, styles)

- [ ] **Step 1: Update the stepModes import**

In `ShooterPlayPage.vue`, change the existing import (line ~341):

```js
import { stepModeLabel, stepNotation, isMultiResultStep } from '@/constants/stepModes.js';
```

to:

```js
import { stepModeLabel, stepNotation, isMultiResultStep, stepFailCells } from '@/constants/stepModes.js';
```

- [ ] **Step 2: Add flyout state, computeds, and handlers**

In the `<script setup>`, add near the other `ref`s (e.g. after `const correctionTarget = ref(null);`, ~line 374):

```js
// ── Fail flyout (doubles) ───────────────────────────────────────────────────────
const failSheetOpen = ref(false);

const failSheetCells = computed(() => stepFailCells(lastFiredStep.value));
const failSheetNotation = computed(() =>
  lastFiredStep.value ? stepNotation(lastFiredStep.value) : ''
);

// Fail tapped on the bar: solo fails immediately (one outcome); doubles open the sheet.
const onFailTapped = () => {
  if (!canFail.value) return;
  if (lastStepWasADouble.value) {
    failSheetOpen.value = true;
  } else {
    handleFailStep('a');
  }
};

const chooseFail = (failType) => {
  handleFailStep(failType);
  failSheetOpen.value = false;
};

const handleMarkHit = () => {
  store.markLastStepHit();
};
```

Note: `canFail`, `lastStepWasADouble`, `lastFiredStep`, and `handleFailStep` already exist in the file. `computed` and `ref` are already imported.

- [ ] **Step 3: Replace the action bar markup**

Replace the entire `<!-- Action buttons at bottom (always visible) -->` block (lines ~262-307, the `<div class="action-bar"> ... </div>`) with:

```html
    <!-- Action buttons at bottom (always visible) -->
    <div class="action-bar">
      <!-- Fail: solo fails immediately, doubles open the fail flyout -->
      <button
        class="action-btn"
        :disabled="!canFail"
        title="Fehler werten"
        @click="onFailTapped"
      >
        <span class="btn-label">Fail</span>
      </button>

      <!-- Treffer: revert the last-fired step to a full hit -->
      <button
        class="action-btn btn-hit-action"
        :disabled="!store.canMarkHit"
        title="Letzten Schritt als Treffer werten"
        @click="handleMarkHit"
      >
        <span class="btn-label">Treffer</span>
      </button>

      <!-- No Bird: retry the last step -->
      <button
        class="action-btn btn-no-bird"
        :disabled="!store.canRetry"
        title="Letzten Schritt wiederholen"
        @click="store.retryStep()"
      >
        <span class="btn-label">No Bird</span>
        <span class="btn-info">Retry</span>
      </button>
    </div>
```

- [ ] **Step 4: Add the fail flyout markup**

Directly after the closing `</div>` of the `.action-bar` block (before the `<!-- Progress dots -->` comment, ~line 309), add:

```html
    <!-- Fail flyout (doubles) — bottom sheet over the action bar -->
    <Transition name="fail-sheet-fade">
      <div v-if="failSheetOpen" class="fail-sheet-overlay" @click.self="failSheetOpen = false">
        <div class="fail-sheet">
          <div class="fail-sheet-handle" />
          <div class="fail-sheet-header">
            <span class="fail-sheet-title">Fail · {{ failSheetNotation }}</span>
            <button class="fail-sheet-close" aria-label="Schließen" @click="failSheetOpen = false">
              <Icons icon="x" :size="16" color="rgba(255,255,255,0.5)" />
            </button>
          </div>
          <div class="fail-sheet-grid">
            <button
              v-for="cell in failSheetCells"
              :key="cell.failType"
              class="fail-cell"
              @click="chooseFail(cell.failType)"
            >
              <span class="fail-cell-label">{{ cell.label }}</span>
              <span class="fail-cell-cost">−{{ cell.cost }}</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>
```

`Icons` is already imported in this file.

- [ ] **Step 5: Update the action-bar grid + add flyout/Treffer styles**

In `<style scoped>`, change the `.action-bar` grid from 4 to 3 columns:

```css
.action-bar {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 16px 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
  background: rgba(10, 10, 18, 0.8);
}
```

Then append (e.g. after the `.action-btn.btn-no-bird` rules, ~line 1380):

```css
.action-btn.btn-hit-action {
  border-color: rgba(72, 187, 120, 0.35);
  background: rgba(72, 187, 120, 0.12);
  color: var(--sg-color-success);
}

.action-btn.btn-hit-action:hover:not(:disabled) {
  background: rgba(72, 187, 120, 0.2);
}

/* ── Fail flyout (bottom sheet) ───────────────────── */
.fail-sheet-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  z-index: 90;
}

.fail-sheet {
  background: rgba(24, 24, 40, 0.98);
  border-top: 1.5px solid rgba(255, 255, 255, 0.12);
  border-radius: 18px 18px 0 0;
  padding: 12px 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  transition: transform 0.2s ease;
}

.fail-sheet-handle {
  width: 34px;
  height: 4px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.18);
  align-self: center;
}

.fail-sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.fail-sheet-title {
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.75);
}

.fail-sheet-close {
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  padding: 4px;
}

.fail-sheet-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.fail-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 16px 6px;
  border-radius: 12px;
  border: 1px solid rgba(252, 129, 129, 0.3);
  background: rgba(252, 129, 129, 0.13);
  color: var(--sg-color-danger-bg);
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s;
}

.fail-cell:hover {
  background: rgba(252, 129, 129, 0.2);
}

.fail-cell-label {
  font-size: 16px;
  font-weight: 700;
}

.fail-cell-cost {
  font-size: 12px;
  opacity: 0.75;
}

.fail-sheet-fade-enter-active,
.fail-sheet-fade-leave-active {
  transition: opacity 0.18s;
}

.fail-sheet-fade-enter-from,
.fail-sheet-fade-leave-to {
  opacity: 0;
}

.fail-sheet-fade-enter-from .fail-sheet,
.fail-sheet-fade-leave-to .fail-sheet {
  transform: translateY(100%);
}
```

- [ ] **Step 6: Lint**

Run: `npm run lint:check`
Expected: no errors in `ShooterPlayPage.vue`. (If lint auto-fixes formatting, run `npm run lint` then re-check.)

- [ ] **Step 7: Commit**

```bash
git add src/views/shooter/ShooterPlayPage.vue
git commit -m "[ui] collapse fail buttons into Fail/Treffer/No Bird bar with fail flyout"
```

---

## Task 4: Component test for the action bar

**Files:**
- Create: `src/views/__tests__/ShooterPlayPage.test.js`

- [ ] **Step 1: Write the failing test**

Create `src/views/__tests__/ShooterPlayPage.test.js`:

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ShooterPlayPage from '../shooter/ShooterPlayPage.vue'
import { usePlaySessionStore } from '@/stores/playSessionStore.js'
import { StepState, StepType } from '@/constants/playEnums.js'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('@/stores/passeStore.js', () => ({
  usePasseStore: () => ({ savedPassen: [] }),
}))
vi.mock('@/services/rangePositionApi.js', () => ({
  sendPositionCommand: vi.fn().mockResolvedValue(undefined),
}))

const seedPlay = (store, step, state) => {
  store.sessionPlayers = [{ id: 'p1', displayName: 'Alice' }]
  store.roundOrder = [0]
  store.currentPlayerIndex = 0
  store.playProg = [{ steps: [step] }]
  store.currentSerieIndex = 0
  store.currentStepIndex = 1 // advanced past the (single) step → it is "last fired"
  store.playScore = {
    totalPoints: state === StepState.DONE ? step.pointValue : 0,
    stepStates: [
      {
        playerId: 'p1', serieIndex: 0, stepIndex: 0,
        state, pointValue: step.pointValue,
        pointsEarned: state === StepState.DONE ? step.pointValue : 0,
        noBirds: 0, corrected: false, originalState: null,
      },
    ],
  }
  store.playLastDeviceStep = { serieIdx: 0, stepIdx: 0 }
}

const mountPage = () =>
  mount(ShooterPlayPage, {
    props: { rangeId: 'r1' },
    global: { stubs: { Icons: true } },
  })

const soloStep = { type: StepType.SOLO, letter: 'A', alias: 'LED 1', pointValue: 1 }
const pairStep = { type: StepType.PAIR, letter1: 'A', letter2: 'B', pointValue: 2 }

describe('ShooterPlayPage action bar', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('renders Fail, Treffer and No Bird buttons', () => {
    const store = usePlaySessionStore()
    seedPlay(store, pairStep, StepState.DONE)
    const wrapper = mountPage()
    const labels = wrapper.findAll('.action-bar .btn-label').map((n) => n.text())
    expect(labels).toEqual(['Fail', 'Treffer', 'No Bird'])
  })

  it('disables Treffer when the last step is a full hit', () => {
    const store = usePlaySessionStore()
    seedPlay(store, pairStep, StepState.DONE)
    const wrapper = mountPage()
    const treffer = wrapper.get('.btn-hit-action')
    expect(treffer.attributes('disabled')).toBeDefined()
  })

  it('enables Treffer after an accidental fail and reverts on click', async () => {
    const store = usePlaySessionStore()
    seedPlay(store, pairStep, StepState.FAILED_BOTH)
    const wrapper = mountPage()
    const treffer = wrapper.get('.btn-hit-action')
    expect(treffer.attributes('disabled')).toBeUndefined()

    await treffer.trigger('click')

    expect(store.playScore.stepStates[0].state).toBe(StepState.DONE)
    expect(store.playScore.stepStates[0].pointsEarned).toBe(2)
  })

  it('opens the fail flyout for a double', async () => {
    const store = usePlaySessionStore()
    seedPlay(store, pairStep, StepState.DONE)
    const wrapper = mountPage()
    expect(wrapper.find('.fail-sheet').exists()).toBe(false)

    await wrapper.findAll('.action-btn')[0].trigger('click')

    expect(wrapper.find('.fail-sheet').exists()).toBe(true)
    const cells = wrapper.findAll('.fail-cell .fail-cell-label').map((n) => n.text())
    expect(cells).toEqual(['A', 'B', 'A + B'])
  })

  it('fails a solo step immediately without opening the flyout', async () => {
    const store = usePlaySessionStore()
    seedPlay(store, soloStep, StepState.DONE)
    const failSpy = vi.spyOn(store, 'failStep')
    const wrapper = mountPage()

    await wrapper.findAll('.action-btn')[0].trigger('click')

    expect(wrapper.find('.fail-sheet').exists()).toBe(false)
    expect(failSpy).toHaveBeenCalledWith('a')
  })
})
```

- [ ] **Step 2: Run test to verify it fails (or drives correctness)**

Run: `npm run test -- src/views/__tests__/ShooterPlayPage.test.js`
Expected: This task has no new production code — it validates Task 3. If Task 3 is complete, these should PASS. If you are running tests before finishing Task 3, expect failures referencing missing `.btn-hit-action` / `.fail-sheet`. Fix Task 3 markup until green.

- [ ] **Step 3: Run the full suite**

Run: `npm run test`
Expected: PASS across the suite.

- [ ] **Step 4: Commit**

```bash
git add src/views/__tests__/ShooterPlayPage.test.js
git commit -m "[ui] test Fail/Treffer/No Bird bar and fail flyout behaviour"
```

---

## Task 5: Manual verification

- [ ] **Step 1: Start the dev server and exercise the flow**

Run: `npm run dev`, open a shooter play session (`/remote/:rangeId` → start a passe).

Verify:
- Bar shows `Fail · Treffer · No Bird`; `Treffer` is greyed until you register a fail.
- On a Solo step, `Fail` immediately marks the miss (no flyout).
- On a Pair / a.Schuss step, `Fail` opens the bottom sheet with `A · B · A + B`; on a Raffale it shows `A1 · A2 · A×2`. Choosing a cell deducts the right points and closes the sheet.
- After an accidental fail, `Treffer` reverts the last step to a full hit and the points score returns to full.
- The final score screen still shows its own (unchanged) correction picker.
- Resize narrow (phone) and wide (tablet): the bar and sheet stay a clean 3-column layout with large touch targets.

---

## Self-Review Notes

- Spec coverage: three-button bar (Task 3), solo-immediate fail (Task 3 `onFailTapped` + Task 4 test), horizontal fail flyout with Raffale `A1/A2/A×2` (Task 1 + Task 3), `Treffer` undo disabled-when-nothing (Task 2 `canMarkHit` + Task 4 test), no `correctStep` audit flags (Task 2 test), final-screen picker untouched (no change to that markup), completed cards stay inert (no change). All covered.
- Type/name consistency: `stepFailCells`, `canMarkHit`, `markLastStepHit`, `onFailTapped`, `chooseFail`, `handleMarkHit`, `failSheetOpen`, `failSheetCells`, `failSheetNotation` used consistently across tasks; failType strings `'a' | 'b' | 'both'` match `FailType` values and the existing `failStep` signature.
