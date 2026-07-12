# Verzögerung & Rufmodus in Play logic — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a step-card tap in the guided Play flow (`ShooterPlayPage`) respect the active remote session mode — firing immediately (`throwing`), after a countdown (`delayed` / Verzögerung), or on a mic-detected shout (`rufausloesung` / Rufmodus) — by extracting the remote view's gating logic into a shared composable and wiring it into Play.

**Architecture:** A new `useTriggerGating(store)` composable owns the gate: given a set of ids and a fire callback, it either fires immediately, runs a countdown, or arms the microphone (via the existing `useVoiceTrigger`), driven entirely by `shooterRemoteStore.sessionMode` + settings. `ShooterRemoteView` is refactored onto it (behavior-preserving). `ShooterPlayPage` calls `gating.arm(ids, () => store.advancePlayStep())` instead of firing directly, and renders countdown/listening feedback on the current step card. `playSessionStore.advancePlayStep()` and `sendPositionCommand` are untouched.

**Tech Stack:** Vue 3 Composition API (`<script setup>`), Pinia, Vitest + `@vue/test-utils`, existing `useVoiceTrigger` (Web Audio API).

---

## Files

| Action | Path | Responsibility |
|---|---|---|
| Create | `src/composables/useTriggerGating.js` | Shared gate: immediate / countdown / mic-armed fire, driven by `shooterRemoteStore` |
| Create | `src/composables/__tests__/useTriggerGating.test.js` | Unit tests for the composable (mocks `useVoiceTrigger`) |
| Modify | `src/stores/playSessionStore.js` | Export `releaseIdsForStep(step, partialStep)` so the Play view and store resolve device ids identically |
| Modify | `src/stores/__tests__/playSessionStore.test.js` (create if absent) | Unit tests for `releaseIdsForStep` |
| Modify | `src/views/shooter/ShooterRemoteView.vue` | Replace inline gating with the composable (behavior-preserving) |
| Modify | `src/views/shooter/ShooterPlayPage.vue` | Wire gating into `handleCurrentStepClick`; card ring/listening/denied UI; mode badge; cleanup |

---

## Task 1: `useTriggerGating` composable

**Files:**
- Create: `src/composables/useTriggerGating.js`
- Test: `src/composables/__tests__/useTriggerGating.test.js`

- [ ] **Step 1: Write the failing tests**

Create `src/composables/__tests__/useTriggerGating.test.js`:

```js
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

// Mock useVoiceTrigger so we can drive the mic trigger synchronously.
const mocks = vi.hoisted(() => {
  const captured = { onTrigger: null }
  return {
    captured,
    startListening: vi.fn((cb) => { captured.onTrigger = cb }),
    stopListening: vi.fn(() => { captured.onTrigger = null }),
    micLevel: { value: 0 },
    wouldTrigger: { value: false },
    micDenied: { value: false },
  }
})

vi.mock('@/composables/useVoiceTrigger.js', () => ({
  useVoiceTrigger: () => ({
    startListening: mocks.startListening,
    stopListening: mocks.stopListening,
    micLevel: mocks.micLevel,
    wouldTrigger: mocks.wouldTrigger,
    micDenied: mocks.micDenied,
  }),
}))

import { useTriggerGating } from '../useTriggerGating'

const makeStore = (over = {}) => ({
  sessionMode: 'throwing',
  delaySeconds: 3,
  rufPeak: 70,
  rufDauer: 120,
  rufTotzeit: 1000,
  ...over,
})

describe('useTriggerGating', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mocks.startListening.mockClear()
    mocks.stopListening.mockClear()
    mocks.captured.onTrigger = null
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('throwing mode fires immediately and stays idle', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'throwing' }))
    const onFire = vi.fn()
    gating.arm(['p1'], onFire)
    expect(onFire).toHaveBeenCalledTimes(1)
    expect(gating.phase.value).toBe('idle')
    expect(gating.armedIds.value).toEqual([])
  })

  it('delayed mode does not fire before the countdown elapses', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'delayed', delaySeconds: 3 }))
    const onFire = vi.fn()
    gating.arm(['p1'], onFire)
    expect(gating.phase.value).toBe('counting')
    expect(gating.armedIds.value).toEqual(['p1'])
    vi.advanceTimersByTime(2000)
    expect(onFire).not.toHaveBeenCalled()
  })

  it('delayed mode fires after the countdown elapses and resets to idle', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'delayed', delaySeconds: 3 }))
    const onFire = vi.fn()
    gating.arm(['p1'], onFire)
    vi.advanceTimersByTime(3000)
    expect(onFire).toHaveBeenCalledTimes(1)
    expect(gating.phase.value).toBe('idle')
    expect(gating.armedIds.value).toEqual([])
  })

  it('re-arming an already-armed id cancels the pending fire', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'delayed', delaySeconds: 3 }))
    const onFire = vi.fn()
    gating.arm(['p1'], onFire)
    gating.arm(['p1'], onFire) // second tap on the same id aborts
    expect(gating.phase.value).toBe('idle')
    vi.advanceTimersByTime(3000)
    expect(onFire).not.toHaveBeenCalled()
  })

  it('delayed mode ignores a new arm while an episode is already running', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'delayed', delaySeconds: 3 }))
    const first = vi.fn()
    const second = vi.fn()
    gating.arm(['p1'], first)
    gating.arm(['p2'], second) // different id, episode busy → ignored
    expect(gating.armedIds.value).toEqual(['p1'])
    vi.advanceTimersByTime(3000)
    expect(first).toHaveBeenCalledTimes(1)
    expect(second).not.toHaveBeenCalled()
  })

  it('rufausloesung with totzeit=0 starts listening immediately', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'rufausloesung', rufTotzeit: 0 }))
    const onFire = vi.fn()
    gating.arm(['p1'], onFire)
    expect(gating.phase.value).toBe('listening')
    expect(mocks.startListening).toHaveBeenCalledTimes(1)
    expect(onFire).not.toHaveBeenCalled()
    // Simulate the mic trigger
    mocks.captured.onTrigger()
    expect(onFire).toHaveBeenCalledTimes(1)
    expect(gating.phase.value).toBe('idle')
  })

  it('rufausloesung with totzeit>0 waits in totzeit before listening', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'rufausloesung', rufTotzeit: 1000 }))
    const onFire = vi.fn()
    gating.arm(['p1'], onFire)
    expect(gating.phase.value).toBe('totzeit')
    expect(mocks.startListening).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1000)
    expect(gating.phase.value).toBe('listening')
    expect(mocks.startListening).toHaveBeenCalledTimes(1)
  })

  it('cancel() stops the mic and resets state', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'rufausloesung', rufTotzeit: 0 }))
    gating.arm(['p1'], vi.fn())
    gating.cancel()
    expect(mocks.stopListening).toHaveBeenCalled()
    expect(gating.phase.value).toBe('idle')
    expect(gating.armedIds.value).toEqual([])
  })

  it('isArmed reflects the armed ids', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'delayed', delaySeconds: 3 }))
    gating.arm(['p1', 'p2'], vi.fn())
    expect(gating.isArmed('p1')).toBe(true)
    expect(gating.isArmed('p2')).toBe(true)
    expect(gating.isArmed('p3')).toBe(false)
  })

  it('countdownLabel and remainingMs track the countdown', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'delayed', delaySeconds: 3 }))
    gating.arm(['p1'], vi.fn())
    expect(gating.totalMs.value).toBe(3000)
    vi.advanceTimersByTime(1000)
    expect(gating.remainingMs.value).toBeLessThanOrEqual(2000)
    expect(gating.countdownLabel.value).toMatch(/^\d+s$/)
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm run test src/composables/__tests__/useTriggerGating.test.js`
Expected: FAIL — `useTriggerGating is not a function` / module not found.

- [ ] **Step 3: Implement the composable**

Create `src/composables/useTriggerGating.js`:

```js
import { ref, computed } from 'vue';
import { useVoiceTrigger } from '@/composables/useVoiceTrigger.js';

/**
 * Shared trigger gate for the shooter flows. Given a set of ids (for UI feedback)
 * and a fire callback, it releases the callback either immediately (throwing),
 * after a countdown (Verzögerung), or on a mic-detected shout (Rufauslösung).
 * Mode + settings are read from the passed shooterRemoteStore.
 */
export function useTriggerGating(store) {
  const { startListening, stopListening, micLevel, wouldTrigger, micDenied } =
    useVoiceTrigger(store);

  const phase = ref('idle');   // 'idle' | 'counting' | 'totzeit' | 'listening'
  const armedIds = ref([]);
  const totalMs = ref(0);
  const remainingMs = ref(0);

  let interval = null;
  let timeout = null;

  const clearTimers = () => {
    if (interval) { clearInterval(interval); interval = null; }
    if (timeout) { clearTimeout(timeout); timeout = null; }
  };

  const resetCountdown = () => { totalMs.value = 0; remainingMs.value = 0; };

  const cancel = () => {
    clearTimers();
    stopListening();
    armedIds.value = [];
    phase.value = 'idle';
    resetCountdown();
  };

  // Run a visible countdown of `ms`, then invoke done().
  const runCountdown = (ms, done) => {
    totalMs.value = ms;
    remainingMs.value = ms;
    const startedAt = Date.now();
    interval = setInterval(() => {
      remainingMs.value = Math.max(0, ms - (Date.now() - startedAt));
    }, 50);
    timeout = setTimeout(() => {
      clearTimers();
      resetCountdown();
      done();
    }, ms);
  };

  const beginListening = (onFire) => {
    phase.value = 'listening';
    startListening(() => {
      if (phase.value !== 'listening') return;
      armedIds.value = [];
      phase.value = 'idle';
      onFire();
    });
  };

  const arm = (ids, onFire) => {
    // Re-arming a currently-armed id aborts the episode (tap-to-cancel).
    if (phase.value !== 'idle') {
      if (ids.some((id) => armedIds.value.includes(id))) cancel();
      return; // one episode at a time
    }

    const mode = store.sessionMode;

    if (mode === 'delayed') {
      armedIds.value = ids;
      phase.value = 'counting';
      runCountdown(store.delaySeconds * 1000, () => {
        armedIds.value = [];
        phase.value = 'idle';
        onFire();
      });
      return;
    }

    if (mode === 'rufausloesung') {
      armedIds.value = ids;
      const totzeitMs = store.rufTotzeit;
      if (totzeitMs > 0) {
        phase.value = 'totzeit';
        runCountdown(totzeitMs, () => beginListening(onFire));
      } else {
        beginListening(onFire);
      }
      return;
    }

    // throwing / recording / anything unexpected → fire immediately.
    onFire();
  };

  const isArmed = (id) => armedIds.value.includes(id);

  const ringStyle = computed(() => {
    const hue = phase.value === 'counting'
      ? 'var(--delay-color, #EF9F27)'
      : 'var(--ruf-color, #56C8D8)';
    const pct = totalMs.value ? (remainingMs.value / totalMs.value) * 360 : 0;
    return {
      background: `conic-gradient(${hue} ${pct}deg, rgba(255,255,255,0.12) ${pct}deg)`,
    };
  });

  const countdownLabel = computed(() => `${Math.ceil(remainingMs.value / 1000)}s`);

  return {
    // actions
    arm,
    cancel,
    isArmed,
    // state
    phase,
    armedIds,
    totalMs,
    remainingMs,
    ringStyle,
    countdownLabel,
    // voice pass-through (config modal preview + denial handling)
    startListening,
    stopListening,
    micLevel,
    wouldTrigger,
    micDenied,
  };
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `npm run test src/composables/__tests__/useTriggerGating.test.js`
Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/composables/useTriggerGating.js src/composables/__tests__/useTriggerGating.test.js
git commit -m "[ui] add useTriggerGating composable (shared countdown + voice gate)"
```

---

## Task 2: `releaseIdsForStep` helper in playSessionStore

Gives the Play view the exact device ids the next `advancePlayStep()` call will release, using the same posId resolution the store uses internally.

**Files:**
- Modify: `src/stores/playSessionStore.js`
- Test: `src/stores/__tests__/playSessionStore.test.js` (create if it does not exist)

- [ ] **Step 1: Write the failing test**

Create (or add to) `src/stores/__tests__/playSessionStore.test.js`:

```js
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePlaySessionStore } from '../playSessionStore'
import { StepType, PartialStep } from '@/constants/playEnums.js'

describe('playSessionStore — releaseIdsForStep', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('returns the single position id for a solo step', () => {
    const store = usePlaySessionStore()
    const step = { type: StepType.SOLO, positionId: 'pos-a' }
    expect(store.releaseIdsForStep(step, null)).toEqual(['pos-a'])
  })

  it('returns both position ids for a pair step', () => {
    const store = usePlaySessionStore()
    const step = { type: StepType.PAIR, positionId1: 'pos-a', positionId2: 'pos-b' }
    expect(store.releaseIdsForStep(step, null)).toEqual(['pos-a', 'pos-b'])
  })

  it('returns the first id for an a_schuss step before the first tap', () => {
    const store = usePlaySessionStore()
    const step = { type: StepType.A_SCHUSS, positionId1: 'pos-a', positionId2: 'pos-b' }
    expect(store.releaseIdsForStep(step, null)).toEqual(['pos-a'])
  })

  it('returns the second id for an a_schuss step after the first tap', () => {
    const store = usePlaySessionStore()
    const step = { type: StepType.A_SCHUSS, positionId1: 'pos-a', positionId2: 'pos-b' }
    expect(store.releaseIdsForStep(step, PartialStep.FIRST)).toEqual(['pos-b'])
  })

  it('returns the single position id for a raffale step', () => {
    const store = usePlaySessionStore()
    const step = { type: StepType.RAFFALE, positionId: 'pos-a' }
    expect(store.releaseIdsForStep(step, null)).toEqual(['pos-a'])
  })

  it('supports the API posId shape as well as positionId', () => {
    const store = usePlaySessionStore()
    const step = { type: StepType.PAIR, posId1: 'pos-a', posId2: 'pos-b' }
    expect(store.releaseIdsForStep(step, null)).toEqual(['pos-a', 'pos-b'])
  })

  it('returns an empty array for a null step', () => {
    const store = usePlaySessionStore()
    expect(store.releaseIdsForStep(null, null)).toEqual([])
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test src/stores/__tests__/playSessionStore.test.js`
Expected: FAIL — `store.releaseIdsForStep is not a function`.

- [ ] **Step 3: Add the helper to the store**

In `src/stores/playSessionStore.js`, find the existing posId resolvers (around line 360):

```js
  // Resolve position ID supporting both UI format (positionId) and API format (posId).
  const _posId  = (step) => step.positionId  ?? step.posId  ?? null;
  const _posId1 = (step) => step.positionId1 ?? step.posId1 ?? null;
  const _posId2 = (step) => step.positionId2 ?? step.posId2 ?? null;
```

Immediately **after** those three lines, add:

```js
  // Ids the NEXT advancePlayStep() call will release, for UI gating feedback.
  // Mirrors the branch structure of advancePlayStep so the view and store agree.
  const releaseIdsForStep = (step, partialStep) => {
    if (!step) return [];
    if (step.type === StepType.SOLO)  return [_posId(step)].filter(Boolean);
    if (step.type === StepType.PAIR)  return [_posId1(step), _posId2(step)].filter(Boolean);
    if (step.type === StepType.A_SCHUSS) {
      return partialStep === PartialStep.FIRST
        ? [_posId2(step)].filter(Boolean)
        : [_posId1(step)].filter(Boolean);
    }
    if (step.type === StepType.RAFFALE) return [_posId(step)].filter(Boolean);
    return [];
  };
```

Then add `releaseIdsForStep` to the `return { ... }` object at the bottom of the store. Find the `// Actions` group in the return (near `advancePlayStep,`) and add the line right after it:

```js
    advancePlayStep,
    releaseIdsForStep,
```

(`StepType` and `PartialStep` are already imported at the top of the file — line 5 — so no new import is needed.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm run test src/stores/__tests__/playSessionStore.test.js`
Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/stores/playSessionStore.js src/stores/__tests__/playSessionStore.test.js
git commit -m "[ui] playSessionStore: add releaseIdsForStep helper"
```

---

## Task 3: Refactor `ShooterRemoteView` onto the composable (behavior-preserving)

The remote view's inline gating (Verzögerung queue + Rufauslösung arming) is replaced by `useTriggerGating`. The **template is left essentially unchanged** — the existing refs it reads (`queuedIds`, `isQueued`, `countdownLabel`, `countdownRingStyle`, `rufArmedIds`, `rufPhase`, `rufCountdownLabel`, `rufCountdownRingStyle`) are re-provided as thin computeds/aliases over the composable, plus a small view-local `rufPairPending` for the two-tap pre-selection.

**Files:**
- Modify: `src/views/shooter/ShooterRemoteView.vue`

- [ ] **Step 1: Swap the import and instantiate the composable**

In `<script setup>`, find (line ~338):

```js
import { useVoiceTrigger } from '@/composables/useVoiceTrigger.js';
```

Replace with:

```js
import { useTriggerGating } from '@/composables/useTriggerGating.js';
```

- [ ] **Step 2: Replace the Rufauslösung arming block with composable wiring**

Find and **delete** the entire block from line ~350 through line ~442 — i.e. from:

```js
const { startListening, stopListening, micLevel, wouldTrigger, micDenied } = useVoiceTrigger(store);

// Rufauslösung: arming state
const rufArmedIds  = ref([]);
...
const onModeButtonTap = (newMode) => {
  if (store.sessionMode === 'rufausloesung' && rufArmedIds.value.length > 0) {
    cancelRuf();
  }
  store.setMode(newMode);
};
```

Replace that whole block with:

```js
const gating = useTriggerGating(store);
const { startListening, stopListening, micLevel, wouldTrigger, micDenied } = gating;

// View-local two-tap pre-selection for Pair mode (before both ids are armed).
const rufPairPending = ref(null); // position id | null

// Template-facing aliases so the existing template markup keeps working while
// the gate logic now lives in useTriggerGating.
const queuedIds = computed(() =>
  gating.phase.value === 'counting' ? gating.armedIds.value : []
);
const isQueued = (position) => queuedIds.value.includes(position.id);
const countdownLabel = gating.countdownLabel;
const countdownRingStyle = gating.ringStyle;

const rufArmedIds = computed(() => {
  if (gating.phase.value === 'totzeit' || gating.phase.value === 'listening') {
    return gating.armedIds.value;
  }
  return rufPairPending.value ? [rufPairPending.value] : [];
});
const rufPhase = computed(() => {
  if (gating.phase.value === 'totzeit') return 'totzeit';
  if (gating.phase.value === 'listening') return 'listening';
  if (rufPairPending.value) return 'waiting-pair';
  return null;
});
const rufCountdownLabel = gating.countdownLabel;
const rufCountdownRingStyle = gating.ringStyle;

const cancelRuf = () => {
  rufPairPending.value = null;
  gating.cancel();
};

const handleRufTap = (position) => {
  // Re-tapping an armed position aborts.
  if (rufArmedIds.value.includes(position.id)) {
    cancelRuf();
    return;
  }
  if (store.mode === 'pair') {
    if (!rufPairPending.value) {
      rufPairPending.value = position.id;
      return;
    }
    const firstId = rufPairPending.value;
    rufPairPending.value = null;
    gating.arm([firstId, position.id], () => firePairPositions(firstId, position.id));
    return;
  }
  // Solo
  gating.arm([position.id], () => fireSinglePosition(position.id));
};

const onModeButtonTap = (newMode) => {
  if (store.sessionMode === 'rufausloesung' && rufArmedIds.value.length > 0) {
    cancelRuf();
  }
  store.setMode(newMode);
};
```

- [ ] **Step 3: Update `onUnmounted` and the mode/lock watchers**

Find (line ~480):

```js
onUnmounted(() => {
  store.releasePlatz();
  store.setCompetitionContext(null, null);
  cancelQueue();
  cancelRuf();
});

watch(() => store.sessionMode, () => {
  store.setMode('solo');
  modeDrawerOpen.value = false;
  cancelQueue();
  cancelRuf();
});
```

Replace with:

```js
onUnmounted(() => {
  store.releasePlatz();
  store.setCompetitionContext(null, null);
  cancelRuf();
});

watch(() => store.sessionMode, () => {
  store.setMode('solo');
  modeDrawerOpen.value = false;
  cancelRuf();
});
```

(`cancelRuf` now calls `gating.cancel()`, which clears both the delay countdown and the mic — so the separate `cancelQueue()` call is no longer needed anywhere.)

- [ ] **Step 4: Update the config-modal preview watcher (`isLocked` + `rufModalOpen`)**

Find the `isLocked` watcher (line ~653):

```js
watch(isLocked, (locked) => {
  if (locked) {
    cancelQueue();
    cancelRuf();
  }
});
```

Replace with:

```js
watch(isLocked, (locked) => {
  if (locked) cancelRuf();
});
```

The `rufModalOpen` watcher (line ~564) already destructures `startListening` / `stopListening` — these now come from `gating` (Step 2 re-exports them), so **no change is needed there**. Verify it still reads:

```js
watch(rufModalOpen, async (open) => {
  if (open) {
    ...
    await startListening(() => { /* preview only — no fire */ }, { totzeit: 0 });
  } else {
    stopListening();
  }
});
```

- [ ] **Step 5: Replace the old Verzögert queue block**

Find and **delete** the entire delayed-queue block from line ~582 through the `handleDelayedTap` function (line ~650) — from the comment `// Command queue — only ONE command...` down to the end of `handleDelayedTap`. That block defines: `queuedIds`, `queueTotalMs`, `queueRemainingMs`, `queueTimeout`, `queueInterval`, `clearQueueTimers`, `cancelQueue`, `scheduleDelayedFire`, `isQueued`, `countdownLabel`, `countdownRingStyle`, and `handleDelayedTap`.

Replace the whole block with just the new `handleDelayedTap` (the other names are now provided by the aliases in Step 2):

```js
const handleDelayedTap = (position) => {
  // A command is already gating: tapping the queued position aborts it; the
  // others are locked (isPositionDisabled) so they never reach here.
  if (gating.phase.value !== 'idle') {
    if (gating.isArmed(position.id)) gating.cancel();
    return;
  }
  if (store.mode === 'pair' || store.mode === 'a_schuss') {
    if (!store.throwPairPending) {
      store.throwPairPending = { id: position.id, alias: position.device?.alias ?? position.label };
    } else if (store.throwPairPending.id === position.id) {
      store.throwPairPending = null;
    } else {
      const pendingId = store.throwPairPending.id;
      store.throwPairPending = null;
      gating.arm([pendingId, position.id], () => firePairPositions(pendingId, position.id));
    }
    return;
  }
  gating.arm([position.id], () => fireSinglePosition(position.id));
};
```

- [ ] **Step 6: Remove the now-duplicated `rufCountdown*` computeds**

Because Step 2 defines `rufCountdownLabel` and `rufCountdownRingStyle` as aliases of `gating.*`, the original definitions further down (line ~830) must be removed to avoid duplicate declarations. Find and **delete**:

```js
const rufCountdownLabel = computed(() =>
  `${Math.ceil(rufTotzeitRemainingMs.value / 1000)}s`
);
const rufCountdownRingStyle = computed(() => {
  const pct = rufTotzeitTotalMs.value
    ? (rufTotzeitRemainingMs.value / rufTotzeitTotalMs.value) * 360
    : 0;
  return {
    background: `conic-gradient(var(--ruf-color, #56C8D8) ${pct}deg, rgba(255,255,255,0.12) ${pct}deg)`,
  };
});
```

- [ ] **Step 7: Verify no orphaned references remain**

Run:

```bash
grep -n "cancelQueue\|scheduleDelayedFire\|armPositions\|beginListening\|clearRufTimers\|clearQueueTimers\|rufTotzeitTotalMs\|rufTotzeitRemainingMs\|queueTotalMs\|queueRemainingMs" src/views/shooter/ShooterRemoteView.vue
```

Expected: **no output** (all removed). If any line prints, it is a leftover reference — remove or repoint it to `gating.*`.

- [ ] **Step 8: Lint the file**

Run: `npm run lint:check`
Expected: no errors for `ShooterRemoteView.vue` (in particular, no "already declared" or "no-undef" errors). If ESLint reports an unused `computed`/`ref` import, leave the imports — they are still used elsewhere in the file.

- [ ] **Step 9: Verify remote behavior in the browser (behavior-preserving check)**

Start the dev server via the preview tooling. Log in as `user@smartground.local` / `user`, open a range remote (`/remote/{id}`). Confirm — **unchanged** from before:
- **Verzögert:** switch to Verzögert mode, tap a Solo position → amber countdown ring drains, then fires. Tap the counting position again → aborts. Pair: tap two positions → both count then fire together.
- **Rufauslösung:** switch to Rufauslösung, open the mic modal (level bar moves), close it. Tap a Solo position → Totzeit ring, then "Lauscht" (pulsing) → shout fires it. Re-tap while armed → aborts. Pair: tap two → both arm together.
- Switching modes or triggering Notfall lock cancels any pending countdown/listen.

- [ ] **Step 10: Commit**

```bash
git add src/views/shooter/ShooterRemoteView.vue
git commit -m "[ui] ShooterRemoteView: use shared useTriggerGating (behavior-preserving)"
```

---

## Task 4: Wire gating into `ShooterPlayPage` (logic + cleanup)

**Files:**
- Modify: `src/views/shooter/ShooterPlayPage.vue`

- [ ] **Step 1: Add imports and instantiate the gate**

In `<script setup>`, find the store imports (line ~355):

```js
import { usePlaySessionStore } from '@/stores/playSessionStore.js';
```

Add directly below it:

```js
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import { useTriggerGating } from '@/composables/useTriggerGating.js';
```

Then find where the store is instantiated (line ~367):

```js
const store = usePlaySessionStore();
```

Add below it:

```js
const remoteStore = useShooterRemoteStore();
const gating = useTriggerGating(remoteStore);

// Transient notice when the mic is blocked during a Rufauslösung arm.
const rufDeniedNotice = ref(false);
```

- [ ] **Step 2: Route the current-step tap through the gate**

Find `handleCurrentStepClick` (line ~730):

```js
const handleCurrentStepClick = async () => {
  await store.advancePlayStep();
};
```

Replace with:

```js
const handleCurrentStepClick = () => {
  const step = store.currentStep;
  if (!step) return;
  // Tapping the card while a gate is running aborts it (delay countdown / listening).
  if (gating.phase.value !== 'idle') {
    gating.cancel();
    return;
  }
  rufDeniedNotice.value = false;
  const ids = store.releaseIdsForStep(step, store.playPartialStep);
  gating.arm(ids, () => { store.advancePlayStep(); });
};
```

- [ ] **Step 3: Cancel the gate when the mic is denied**

The `useVoiceTrigger` sets `micDenied` if the browser blocks the mic. When that happens mid-arm, drop out of the listening phase so the operator can retry, and surface a notice. Add after `handleCurrentStepClick`:

```js
// If the browser blocks the mic while arming, abort the listen and notify.
watch(() => gating.micDenied.value, (denied) => {
  if (denied) {
    rufDeniedNotice.value = true;
    gating.cancel();
  }
});
```

- [ ] **Step 4: Cancel the gate on unmount, completion, and shooter change**

Find `onBeforeUnmount` (line ~761):

```js
onBeforeUnmount(() => {
  if (!store.playComplete) store.closePlayback();
});
```

Replace with:

```js
onBeforeUnmount(() => {
  gating.cancel();
  if (!store.playComplete) store.closePlayback();
});

// Cancel any pending gate when play finishes or the active shooter changes,
// so a stale countdown/listen never carries over.
watch(() => store.playComplete, (done) => { if (done) gating.cancel(); });
watch(() => store.currentPlayerIndex, () => { gating.cancel(); });
```

- [ ] **Step 5: Run the full test suite (nothing should regress)**

Run: `npm run test`
Expected: all tests PASS (this task adds no tests; it must not break existing ones).

- [ ] **Step 6: Commit**

```bash
git add src/views/shooter/ShooterPlayPage.vue
git commit -m "[ui] ShooterPlayPage: gate step-card fire through useTriggerGating"
```

---

## Task 5: Play page UI — countdown ring, listening state, mode badge

**Files:**
- Modify: `src/views/shooter/ShooterPlayPage.vue`

- [ ] **Step 1: Add a mode badge to the top bar**

In the template, find the `player-info` block (line ~49):

```html
      <div class="player-info">
        <button class="back-btn" @click="goBack">
          <Icons icon="chevronLeft" :size="20" color="rgba(255,255,255,0.8)" />
          Zurück
        </button>
        <span v-if="store.currentPlayer" class="player-name">
          {{ store.currentPlayer.displayName }}
        </span>
      </div>
```

Add the badge right after the closing `</span>` of `player-name`, still inside `player-info`:

```html
      <div class="player-info">
        <button class="back-btn" @click="goBack">
          <Icons icon="chevronLeft" :size="20" color="rgba(255,255,255,0.8)" />
          Zurück
        </button>
        <span v-if="store.currentPlayer" class="player-name">
          {{ store.currentPlayer.displayName }}
        </span>
        <span v-if="playModeBadge" class="play-mode-badge" :class="playModeBadge.class">
          <span class="play-mode-dot" />
          {{ playModeBadge.label }}
        </span>
      </div>
```

- [ ] **Step 2: Add the `playModeBadge` computed**

In `<script setup>`, add near the other computeds (e.g. after the `showFinalScore` computed, line ~617):

```js
// Mode indicator in the Play top bar — only for the two gated modes.
const playModeBadge = computed(() => {
  if (remoteStore.sessionMode === 'delayed') {
    return { label: 'Verzögert', class: 'play-mode-badge--delayed' };
  }
  if (remoteStore.sessionMode === 'rufausloesung') {
    return { label: 'Rufauslösung', class: 'play-mode-badge--ruf' };
  }
  return null;
});
```

- [ ] **Step 3: Add the countdown ring + listening overlay to the current step card**

In the template, find the current step card's label region (line ~109):

```html
          <!-- solo / pair / raffale: position notation is the hero label -->
          <div v-else class="card-label">{{ getStepLetter(currentStep) }}</div>
```

Replace with a wrapper that swaps the label for the ring while gating:

```html
          <!-- Gating overlay: countdown ring (delay/totzeit) replaces the hero label -->
          <div
            v-if="gating.phase.value === 'counting' || gating.phase.value === 'totzeit'"
            class="card-gate-ring"
            :style="gating.ringStyle.value"
          >
            <span class="card-gate-num">{{ gating.countdownLabel.value }}</span>
          </div>
          <!-- solo / pair / raffale: position notation is the hero label -->
          <div v-else class="card-label">{{ getStepLetter(currentStep) }}</div>
```

Note: this sits inside the `v-else` (non-a_schuss) branch. For a_schuss, the existing per-device highlight remains; the pulsing card class (Step 5) covers its listening feedback.

- [ ] **Step 4: Drive the card hint text from the gate phase**

Find `getHint` (line ~695):

```js
const getHint = (step) => {
  if (step.type === StepType.A_SCHUSS) {
    return store.playPartialStep === null ? 'Erstes Gerät: Tippen' : 'Zweites Gerät: Tippen';
  }
  if (step.type === StepType.RAFFALE) {
    return store.playRaffaleStarted ? 'Warte auf Verzögerung...' : 'Erste Auslösung: Tippen';
  }
  return 'Tippen zum Auslösen →';
};
```

Replace with:

```js
const getHint = (step) => {
  // Gate feedback takes precedence while a countdown / listen is active.
  if (rufDeniedNotice.value) return 'Mikrofon-Zugriff verweigert';
  if (gating.phase.value === 'counting') return 'Verzögerung läuft…';
  if (gating.phase.value === 'totzeit')  return 'Bereitmachen…';
  if (gating.phase.value === 'listening') return 'Rufen zum Auslösen';
  if (step.type === StepType.A_SCHUSS) {
    return store.playPartialStep === null ? 'Erstes Gerät: Tippen' : 'Zweites Gerät: Tippen';
  }
  if (step.type === StepType.RAFFALE) {
    return store.playRaffaleStarted ? 'Warte auf Verzögerung...' : 'Erste Auslösung: Tippen';
  }
  return 'Tippen zum Auslösen →';
};
```

- [ ] **Step 5: Add a `listening` class to the step card**

Find the step card element (line ~93):

```html
        <div v-if="currentStep" class="step-card" :class="`is-${currentStep.type}`" @click="handleCurrentStepClick">
```

Replace with:

```html
        <div
          v-if="currentStep"
          class="step-card"
          :class="[`is-${currentStep.type}`, { 'step-card--listening': gating.phase.value === 'listening' }]"
          @click="handleCurrentStepClick"
        >
```

- [ ] **Step 6: Add the CSS**

In the `<style scoped>` section of `ShooterPlayPage.vue`, add at the end (before the closing `</style>`):

```css
/* ── Gate mode tokens (fallbacks match the remote's amber/cyan) ── */
.play-page {
  --delay-color: #EF9F27;
  --ruf-color: #56C8D8;
}

/* ── Mode badge in the top bar ── */
.play-mode-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.6px;
}

.play-mode-badge .play-mode-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  animation: play-mode-pulse 1s ease-in-out infinite;
}

.play-mode-badge--delayed {
  background: rgba(239, 159, 39, 0.12);
  color: #FAC775;
}
.play-mode-badge--delayed .play-mode-dot { background: #EF9F27; }

.play-mode-badge--ruf {
  background: rgba(86, 200, 216, 0.12);
  color: #7AD8E4;
}
.play-mode-badge--ruf .play-mode-dot { background: #56C8D8; }

@keyframes play-mode-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}

/* ── Countdown ring on the hero card (delay + totzeit) ── */
.card-gate-ring {
  width: 92px;
  height: 92px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  /* background is set inline via gating.ringStyle (conic-gradient) */
  -webkit-mask: radial-gradient(circle 33px at center, transparent 98%, #000 100%);
  mask: radial-gradient(circle 33px at center, transparent 98%, #000 100%);
}

.card-gate-num {
  position: absolute;
  font-size: 30px;
  font-weight: 700;
  color: var(--sg-text-primary);
  font-variant-numeric: tabular-nums;
}

/* ── Listening pulse on the whole card ── */
.step-card--listening {
  animation: card-listen-pulse 1s ease-in-out infinite;
}

@keyframes card-listen-pulse {
  0%, 100% { box-shadow: inset 0 0 14px rgba(86, 200, 216, 0.24), 0 4px 22px rgba(86, 200, 216, 0.18); }
  50%      { box-shadow: inset 0 0 22px rgba(86, 200, 216, 0.45), 0 4px 30px rgba(86, 200, 216, 0.35); }
}
```

- [ ] **Step 7: Verify Play gating in the browser**

With the dev server running, log in as `user@smartground.local` / `user`. Open a range remote, switch the session mode to **Verzögert**, then start a Serie (e.g. "Meine Serien" → Als Solo Starten) to reach the Play page. Confirm:
- **Verzögert:** the top bar shows the amber "Verzögert" badge. Tap the current step card → the hero label is replaced by an amber countdown ring counting down; hint reads "Verzögerung läuft…"; the device fires when it reaches 0 and the card advances. Tapping the card again mid-countdown aborts.
- **a.Schuss under Verzögert:** each of the two taps runs its own countdown before its device fires.
- **Raffale under Verzögert:** the first tap runs a countdown then fires; the built-in raffale bar still auto-fires the second shot.

Then go back, switch the remote to **Rufauslösung**, start a Serie again. Confirm:
- The top bar shows the cyan "Rufauslösung" badge. Tap the card → cyan Totzeit ring ("Bereitmachen…"), then the card pulses cyan with hint "Rufen zum Auslösen". Shout → fires and advances. Re-tap while armed → aborts.
- If you deny mic permission, the hint shows "Mikrofon-Zugriff verweigert" and the card returns to idle (tappable again).

Then switch the remote back to **Schiessen** and confirm a Serie fires immediately on tap (no badge, no ring) — unchanged from today.

- [ ] **Step 8: Commit**

```bash
git add src/views/shooter/ShooterPlayPage.vue
git commit -m "[ui] ShooterPlayPage: countdown ring, listening state, mode badge for gated modes"
```

---

## Task 6: Final lint, test, and build

- [ ] **Step 1: Lint**

Run: `npm run lint:check`
Expected: no errors or warnings. If `npm run lint` (auto-fix) is preferred, run it and re-check.

- [ ] **Step 2: Full test suite**

Run: `npm run test`
Expected: all tests PASS, including the new `useTriggerGating` and `releaseIdsForStep` suites and the existing `shooterRemoteStore` / `useVoiceTrigger` suites.

- [ ] **Step 3: Production build**

Run: `npm run build`
Expected: build succeeds with no errors or warnings.

- [ ] **Step 4: Commit any lint fixes**

```bash
git add -A
git commit -m "[ui] Play trigger modes: lint fixes"
```

(Skip if there was nothing to fix.)

---

## Self-review notes (traceability to the spec)

- **Spec §1 mode source (inherit):** Task 4 reads `remoteStore.sessionMode` directly; no new selector. ✔
- **Spec §2 composable API:** Task 1 implements `arm`/`cancel`/`isArmed`/`phase`/`armedIds`/`totalMs`/`remainingMs`/`ringStyle`/`countdownLabel` + voice pass-through. ✔
- **Spec §2 gate-every-release:** Task 4 arms per `advancePlayStep()` call; Task 2's `releaseIdsForStep` returns the exact 1–2 ids per step/partial. ✔
- **Spec §2 raffale second shot ungated:** Task 4 gates only the tap; the existing `watch(playRaffaleStarted)`→`completeRaffaleStep` is untouched (verified in Task 5 Step 7). ✔
- **Spec §3 behavior-preserving remote refactor:** Task 3 aliases the template refs and delegates to the composable; Step 9 verifies parity. ✔
- **Spec §4 Play wiring:** Task 4. ✔
- **Spec §5 Play UI (ring, listening, denied, badge; no config modal):** Task 5 adds ring/listening/badge and the denied hint; no modal is added. ✔
- **Spec §6 cleanup/edges:** Task 4 Step 4 (unmount/complete/shooter-change cancel) + Step 3 (micDenied) + composable fallback to immediate for unexpected modes. ✔
- **Spec §7 files:** Matches the Files table above. ✔
