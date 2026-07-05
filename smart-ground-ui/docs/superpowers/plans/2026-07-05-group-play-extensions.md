# Group Play Extensions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add shooter reordering, a separate "starter" marker with wrap-around turn order, and an opt-in "Serie Anschauen" preview run with a just-in-time seen-frontier gate to the group-play flow.

**Architecture:** All state lives in the existing `playSessionStore` (Pinia). Section A rewires the already-present `roundOrder`/`roundStartIndex` rotation scaffolding so the marked starter becomes the entry point and turns wrap. Section B adds an independent preview cursor + frontier over the same program; the UI (`ShooterPlayPage.vue`) grows a preview screen and a "Weitere Schritte anzeigen" gate overlay. No backend/API changes.

**Tech Stack:** Vue 3 (`<script setup>`), Pinia 3, Vitest 4 + @vue/test-utils.

## Global Constraints

- `<script setup>` + Composition API only; `@/` alias for `src/` imports.
- German display labels; English identifiers, comments, and tests.
- Design tokens from `--sg-*` custom properties; no hard-coded colors except where the file already uses literal rgba (match surrounding style).
- Step rendering imports from `constants/stepModes.js` (already used in the file) — never hard-code modes/notation/colors.
- Touch targets ≥48px on shooter (kiosk) controls.
- No new dependencies. No backend or OpenAPI changes. Preview state is not persisted across reloads.
- Available `Icons` names include `chevronDown`, `check`, `x` — there is **no** `chevronUp` or `star` icon (use `chevronDown` rotated 180° for "up", and a text "Start" pill for the starter marker).
- Commit messages: `[ui] short description`.
- Scope: reorder / starter / preview apply to **non-competition** group play only (the modal already branches on `_isCompetitionMode`).

---

### Task 1: Starter wrap-around in the store

Wire the marked starter into the existing rotation scaffolding and fix `nextPlayer` to read through `roundOrder` so the wrap is respected.

**Files:**
- Modify: `src/stores/playSessionStore.js` (`setupPlayers` ~214, `nextPlayer` ~91, `startGroupPlay` ~616)
- Test: `src/stores/__tests__/playSessionStore.groupOrder.test.js` (create)

**Interfaces:**
- Produces:
  - `setupPlayers(players, startIndex = 0)` — sets `roundStartIndex = startIndex` then `buildRoundOrder()`.
  - `startGroupPlay(players, rangeId?, rangeName?, instanceId?, blockId?, rotteId?, instanceType?, sessionId?, starterIndex = 0)` — `starterIndex` is the new **last** positional arg.
  - `nextPlayer` — computed, returns `sessionPlayers[roundOrder[currentPlayerIndex + 1]]` or `null` past the end.

- [ ] **Step 1: Write the failing test**

Create `src/stores/__tests__/playSessionStore.groupOrder.test.js`:

```js
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePlaySessionStore } from '../playSessionStore'
import { StepType } from '@/constants/playEnums.js'

const players = () => [
  { id: 'A', displayName: 'A' },
  { id: 'B', displayName: 'B' },
  { id: 'C', displayName: 'C' },
  { id: 'D', displayName: 'D' },
]
const oneSoloProg = () => [{ steps: [{ type: StepType.SOLO, positionId: 'p' }] }]

describe('playSessionStore — starter wrap order', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('starts at the marked starter and wraps through the rotation', async () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien(oneSoloProg())
    // starterIndex 2 = C
    await store.startGroupPlay(players(), null, null, null, null, null, null, null, 2)

    expect(store.currentPlayer.id).toBe('C')
    expect(store.nextPlayer.id).toBe('D')
    store.advanceToNextPlayer()
    expect(store.currentPlayer.id).toBe('D')
    expect(store.nextPlayer.id).toBe('A')
    store.advanceToNextPlayer()
    expect(store.currentPlayer.id).toBe('A')
    expect(store.nextPlayer.id).toBe('B')
    store.advanceToNextPlayer()
    expect(store.currentPlayer.id).toBe('B')
    expect(store.nextPlayer).toBe(null)
  })

  it('defaults to index 0 (identity order) when no starter is given', async () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien(oneSoloProg())
    await store.startGroupPlay(players())
    expect(store.currentPlayer.id).toBe('A')
    expect(store.nextPlayer.id).toBe('B')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test src/stores/__tests__/playSessionStore.groupOrder.test.js`
Expected: FAIL — `currentPlayer.id` is `'A'` not `'C'` (starter arg ignored) and/or `nextPlayer` bypasses `roundOrder`.

- [ ] **Step 3: Update `setupPlayers` to accept a start index**

In `src/stores/playSessionStore.js` replace the `setupPlayers` function (~214):

```js
  const setupPlayers = (players, startIndex = 0) => {
    sessionPlayers.value = players;
    roundStartIndex.value = startIndex;
    currentPlayerIndex.value = 0;
    buildRoundOrder();
  };
```

- [ ] **Step 4: Fix `nextPlayer` to read through `roundOrder`**

Replace the `nextPlayer` computed (~91):

```js
  const nextPlayer = computed(() => {
    if (!isMultiPlayer.value) return null;
    const nextIdx = currentPlayerIndex.value + 1;
    if (nextIdx >= roundOrder.value.length) return null;
    return sessionPlayers.value[roundOrder.value[nextIdx]] ?? null;
  });
```

- [ ] **Step 5: Thread `starterIndex` through `startGroupPlay`**

Change the `startGroupPlay` signature (~616) to add the final param and pass it to `setupPlayers`:

```js
  const startGroupPlay = async (players, rangeId = null, rangeName = null, instanceId = null, blockId = null, rotteId = null, instanceType = null, sessionId = null, starterIndex = 0) => {
    if (!pendingGroupSerien.value) return;
    const serien = pendingGroupSerien.value;

    currentRangeId.value = rangeId;
    setupPlayers(players, starterIndex);
```

(Leave the rest of `startGroupPlay` unchanged.)

- [ ] **Step 6: Run the test to verify it passes**

Run: `npm run test src/stores/__tests__/playSessionStore.groupOrder.test.js`
Expected: PASS (both cases).

- [ ] **Step 7: Run the existing store tests to check for regressions**

Run: `npm run test src/stores/__tests__/playSessionStore.test.js src/stores/__tests__/playSessionStore.competition.test.js`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/stores/playSessionStore.js src/stores/__tests__/playSessionStore.groupOrder.test.js
git commit -m "$(cat <<'EOF'
[ui] group play: starter marker with wrap-around turn order

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Group setup UI — reorder + starter marker

Add up/down repositioning and a "Start" marker to each shooter row in the group setup modal, and pass the resolved starter index to `startGroupPlay`.

**Files:**
- Modify: `src/views/shooter/ShooterPlayPage.vue` (setup modal template ~9-42, script ~475-551, styles)
- Test: `src/views/__tests__/ShooterPlayPage.test.js` (extend)

**Interfaces:**
- Consumes: `startGroupPlay(..., starterIndex)` from Task 1.
- Produces (component-local): `starterId` ref, `setStarter(id)`, `moveUp(i)`, `moveDown(i)`, `starterIndex` computed; `beginGroupPlay` appends `starterIndex.value` to the `startGroupPlay` call.

- [ ] **Step 1: Write the failing tests**

Append to `src/views/__tests__/ShooterPlayPage.test.js`:

```js
describe('ShooterPlayPage group setup — reorder & starter', () => {
  beforeEach(() => setActivePinia(createPinia()))

  const openSetup = (store) => {
    store.setPendingGroupSerien([{ steps: [soloStep] }])
    return mountPage()
  }

  it('marks the first shooter as starter by default and passes starterIndex 0', async () => {
    const store = usePlaySessionStore()
    const startSpy = vi.spyOn(store, 'startGroupPlay').mockResolvedValue(undefined)
    const wrapper = openSetup(store)
    await wrapper.get('.add-player-btn').trigger('click') // 2 shooters now
    await wrapper.get('.modal-actions .btn-primary').trigger('click')
    const starterIndex = startSpy.mock.calls[0].at(-1)
    expect(starterIndex).toBe(0)
  })

  it('moves a shooter up and keeps the starter marker on the person', async () => {
    const store = usePlaySessionStore()
    const startSpy = vi.spyOn(store, 'startGroupPlay').mockResolvedValue(undefined)
    const wrapper = openSetup(store)
    await wrapper.get('.add-player-btn').trigger('click') // Schütze 1, Schütze 2

    // Mark the second shooter as starter
    await wrapper.findAll('.player-star-btn')[1].trigger('click')
    // Move the second shooter up to row 1
    await wrapper.findAll('.player-move-up')[1].trigger('click')
    await wrapper.get('.modal-actions .btn-primary').trigger('click')

    const args = startSpy.mock.calls[0]
    const passedPlayers = args[0]
    const starterIndex = args.at(-1)
    expect(passedPlayers[0].displayName).toBe('Schütze 2')
    expect(starterIndex).toBe(0)
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm run test src/views/__tests__/ShooterPlayPage.test.js`
Expected: FAIL — `.player-star-btn` / `.player-move-up` do not exist; `startGroupPlay` receives no starter index.

- [ ] **Step 3: Add reorder + starter state to the script**

In `src/views/shooter/ShooterPlayPage.vue`, in the group-setup script region (just after the `groupPlayers` ref, ~476), add `watch` to the imports from `vue` if missing, then:

```js
// ── Reorder + starter marker (non-competition group setup) ─────────────────────
const starterId = ref(null);

// Default the starter to the first shooter; keep it valid as the list changes.
watch(groupPlayers, (list) => {
  if (!list.length) { starterId.value = null; return; }
  if (!list.some((p) => p.id === starterId.value)) starterId.value = list[0].id;
}, { immediate: true, deep: true });

const setStarter = (id) => { starterId.value = id; };

const moveUp = (i) => {
  if (i <= 0) return;
  const list = groupPlayers.value;
  const [item] = list.splice(i, 1);
  list.splice(i - 1, 0, item);
};

const moveDown = (i) => {
  const list = groupPlayers.value;
  if (i >= list.length - 1) return;
  const [item] = list.splice(i, 1);
  list.splice(i + 1, 0, item);
};

const starterIndex = computed(() => {
  const idx = groupPlayers.value.findIndex((p) => p.id === starterId.value);
  return idx >= 0 ? idx : 0;
});
```

Confirm the top-of-file import reads: `import { computed, ref, watch, onBeforeUnmount } from 'vue';` (add `watch` if it is not already there — it is imported in this file).

- [ ] **Step 4: Pass `starterIndex` into `beginGroupPlay`**

Replace the `store.startGroupPlay(...)` call inside `beginGroupPlay` (~537) so `starterIndex.value` is the final argument:

```js
  store.startGroupPlay(
    groupPlayers.value,
    props.rangeId,
    'Platz',
    _blockContext.value?.instanceId ?? null,
    _blockContext.value?.blockId ?? null,
    _blockContext.value?.rotteId ?? null,
    _blockContext.value?.instanceType ?? null,
    _blockContext.value?.sessionId ?? null,
    starterIndex.value,
  );
```

- [ ] **Step 5: Add the reorder + starter controls to the player row**

Replace the player-row block in the template (~10-23) with:

```html
          <div
            v-for="(player, i) in groupPlayers"
            :key="player.id"
            class="player-row"
            :class="{ 'is-starter-row': starterId === player.id }"
          >
            <span class="player-number">{{ i + 1 }}:</span>
            <span class="player-display-name">{{ player.displayName }}</span>
            <span v-if="player.userId" class="player-account-badge" title="Mit Account verknüpft — Ergebnis wird gespeichert">
              <Icons icon="check" :size="12" color="var(--sg-color-success)" />
            </span>
            <template v-if="!_isCompetitionMode">
              <button class="player-order-btn player-move-up" :disabled="i === 0" title="Nach oben" @click="moveUp(i)">
                <Icons icon="chevronDown" :size="14" color="rgba(255,255,255,0.6)" class="rot-180" />
              </button>
              <button class="player-order-btn player-move-down" :disabled="i === groupPlayers.length - 1" title="Nach unten" @click="moveDown(i)">
                <Icons icon="chevronDown" :size="14" color="rgba(255,255,255,0.6)" />
              </button>
              <button
                class="player-star-btn"
                :class="{ 'is-starter': starterId === player.id }"
                :title="starterId === player.id ? 'Startet' : 'Als Starter markieren'"
                @click="setStarter(player.id)"
              >
                Start
              </button>
              <button
                v-if="groupPlayers.length > 1"
                class="player-remove-btn"
                @click="removePlayer(i)"
              >
                <Icons icon="x" :size="12" color="var(--sg-color-danger-bg)" />
              </button>
            </template>
          </div>
```

- [ ] **Step 6: Add styles for the new controls**

In the `<style scoped>` block, near `.player-remove-btn` (~939), add:

```css
.player-order-btn {
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  min-height: 32px;
  padding: 6px;
  border-radius: 8px;
  transition: background 0.15s;
}

.player-order-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.07);
}

.player-order-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.rot-180 {
  transform: rotate(180deg);
}

.player-star-btn {
  min-height: 32px;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid var(--sg-border);
  background: rgba(255, 255, 255, 0.04);
  color: var(--sg-text-faint);
  font-family: inherit;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
}

.player-star-btn.is-starter {
  background: color-mix(in srgb, var(--sg-accent) 20%, transparent);
  border-color: color-mix(in srgb, var(--sg-accent) 40%, transparent);
  color: var(--sg-accent);
}

.player-row.is-starter-row {
  border-color: color-mix(in srgb, var(--sg-accent) 35%, transparent);
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `npm run test src/views/__tests__/ShooterPlayPage.test.js`
Expected: PASS (all cases, including the pre-existing action-bar tests).

- [ ] **Step 8: Commit**

```bash
git add src/views/shooter/ShooterPlayPage.vue src/views/__tests__/ShooterPlayPage.test.js
git commit -m "$(cat <<'EOF'
[ui] group setup: reorder shooters and mark the starter

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Preview cursor, frontier, and actions in the store

Add an independent preview cursor + seen-frontier over the group program, with actions to run, skip, retry, and stop the preview, plus the `needsPreview` gate computed.

**Files:**
- Modify: `src/stores/playSessionStore.js` (new state ~49, computeds, actions; `closePlayback` ~586; `cancelGroupSetup` ~611; store return ~768)
- Test: `src/stores/__tests__/playSessionStore.preview.test.js` (create)

**Interfaces:**
- Consumes: existing `releaseIdsForStep`, `_posId/_posId1/_posId2`, `sendPositionCommand`, `StepType`, `PartialStep`.
- Produces (all added to the store's returned object):
  - State refs: `previewMode`, `previewEngaged`, `previewFrontier`, `previewSerieIdx`, `previewStepIdx`, `previewPartial`, `previewRaffaleStarted`.
  - Computeds: `previewProgram`, `previewStep`, `scoredFlatIndex`, `needsPreview`.
  - Actions: `startPreview()`, `advancePreviewStep()`, `retryPreviewStep()`, `skipPreviewStep()`, `stopPreview()`, `completePreviewRaffaleStep()`.

- [ ] **Step 1: Write the failing tests**

Create `src/stores/__tests__/playSessionStore.preview.test.js`:

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePlaySessionStore } from '../playSessionStore'
import { StepType } from '@/constants/playEnums.js'
import { sendPositionCommand } from '@/services/rangePositionApi.js'

vi.mock('@/services/rangePositionApi.js', () => ({
  sendPositionCommand: vi.fn().mockResolvedValue(undefined),
}))

const prog = () => [{
  steps: [
    { type: StepType.SOLO, positionId: 'p0' },
    { type: StepType.SOLO, positionId: 'p1' },
    { type: StepType.SOLO, positionId: 'p2' },
  ],
}]

const seedFirstShooter = (store) => {
  store.playProg = prog()
  store.sessionPlayers = [{ id: 'A', displayName: 'A' }, { id: 'B', displayName: 'B' }]
  store.roundOrder = [0, 1]
  store.currentPlayerIndex = 0
  store.currentSerieIndex = 0
  store.currentStepIndex = 0
}

describe('playSessionStore — preview', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('startPreview engages preview and places the cursor at the frontier', () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien(prog())
    store.startPreview()
    expect(store.previewMode).toBe(true)
    expect(store.previewEngaged).toBe(true)
    expect(store.previewStep.positionId).toBe('p0')
  })

  it('advancePreviewStep fires the device and moves the frontier forward', async () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien(prog())
    store.startPreview()
    await store.advancePreviewStep()
    expect(sendPositionCommand).toHaveBeenCalledWith(null, 'p0')
    expect(store.previewFrontier).toBe(1)
    expect(store.previewStep.positionId).toBe('p1')
  })

  it('skipPreviewStep advances the frontier without firing a device', () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien(prog())
    store.startPreview()
    store.skipPreviewStep()
    expect(sendPositionCommand).not.toHaveBeenCalled()
    expect(store.previewFrontier).toBe(1)
    expect(store.previewStep.positionId).toBe('p1')
  })

  it('needsPreview is false when preview was never engaged', () => {
    const store = usePlaySessionStore()
    seedFirstShooter(store)
    expect(store.needsPreview).toBe(false)
  })

  it('needsPreview pauses the first shooter at an unseen step and clears after previewing', async () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien(prog())
    store.startPreview()
    await store.advancePreviewStep() // frontier = 1 (p0 seen)
    store.stopPreview()
    seedFirstShooter(store)

    store.currentStepIndex = 0
    expect(store.needsPreview).toBe(false) // scored index 0 < frontier 1
    store.currentStepIndex = 1
    expect(store.needsPreview).toBe(true)  // scored index 1 >= frontier 1

    store.startPreview()
    await store.advancePreviewStep() // frontier = 2
    store.stopPreview()
    expect(store.needsPreview).toBe(false)
  })

  it('needsPreview is false for shooters after the first', () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien(prog())
    store.startPreview()
    store.stopPreview() // engaged, frontier 0
    seedFirstShooter(store)
    store.currentPlayerIndex = 1
    expect(store.needsPreview).toBe(false)
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm run test src/stores/__tests__/playSessionStore.preview.test.js`
Expected: FAIL — `startPreview`, `previewStep`, `needsPreview`, etc. are undefined.

- [ ] **Step 3: Add preview state refs**

In `src/stores/playSessionStore.js`, just after the group-setup state (~49, below `showGroupSetup`), add:

```js
  // ── Preview ("Serie Anschauen") state ─────────────────────────────────────────
  // A seen-frontier preview that runs on its own cursor, independent of the scored
  // position, so it can run ahead of (and interleave with) the first shooter.
  const previewMode = ref(false);          // currently on the preview screen
  const previewEngaged = ref(false);       // preview started at least once → gate active
  const previewFrontier = ref(0);          // flat index of fully-seen steps (high-water mark)
  const previewSerieIdx = ref(0);
  const previewStepIdx = ref(0);
  const previewPartial = ref(null);        // a_schuss phase during preview
  const previewRaffaleStarted = ref(false);// raffale phase during preview
```

- [ ] **Step 4: Add preview computeds**

Add these computeds after the preview state (they depend on `playProg` / `pendingGroupSerien`, both defined above). Place them below the `nextPlayer` computed (~96):

```js
  // ── Preview computeds ─────────────────────────────────────────────────────────
  // The step source for preview: the live program during play, else the staged
  // group serien while still on the setup screen.
  const previewProgram = computed(() => playProg.value ?? pendingGroupSerien.value ?? null);

  const previewStep = computed(() =>
    previewProgram.value?.[previewSerieIdx.value]?.steps[previewStepIdx.value] ?? null
  );

  const _previewTotalSteps = computed(() =>
    (previewProgram.value ?? []).reduce((sum, seg) => sum + seg.steps.length, 0)
  );

  // Flat index of the current scored step (what the first shooter is about to fire).
  const scoredFlatIndex = computed(() => {
    if (!playProg.value) return 0;
    return (
      playProg.value.slice(0, currentSerieIndex.value)
        .reduce((sum, seg) => sum + seg.steps.length, 0) + currentStepIndex.value
    );
  });

  // The gate: pause the first shooter when they reach an un-previewed step.
  const needsPreview = computed(() =>
    previewEngaged.value &&
    !previewMode.value &&
    currentPlayerIndex.value === 0 &&
    previewFrontier.value < _previewTotalSteps.value &&
    scoredFlatIndex.value >= previewFrontier.value
  );
```

- [ ] **Step 5: Add preview cursor helpers + actions**

Add these near the other playback actions (after `completeRaffaleStep` ~426):

```js
  // ── Preview actions ───────────────────────────────────────────────────────────
  const _previewFlatToPos = (flat) => {
    const prog = previewProgram.value ?? [];
    let remaining = flat;
    for (let s = 0; s < prog.length; s++) {
      if (remaining < prog[s].steps.length) return { serieIdx: s, stepIdx: remaining };
      remaining -= prog[s].steps.length;
    }
    const last = Math.max(0, prog.length - 1);
    return { serieIdx: last, stepIdx: prog[last]?.steps.length ?? 0 };
  };

  // Advance the preview cursor one full step and raise the frontier.
  const _advancePreviewCursor = () => {
    const prog = previewProgram.value;
    if (!prog) return;
    const seg = prog[previewSerieIdx.value];
    previewFrontier.value += 1;
    if (previewStepIdx.value < seg.steps.length - 1) {
      previewStepIdx.value += 1;
    } else if (previewSerieIdx.value < prog.length - 1) {
      previewSerieIdx.value += 1;
      previewStepIdx.value = 0;
    } else {
      previewStepIdx.value = seg.steps.length; // past end → previewStep null
    }
    previewPartial.value = null;
    previewRaffaleStarted.value = false;
  };

  const startPreview = () => {
    previewEngaged.value = true;
    const pos = _previewFlatToPos(previewFrontier.value);
    previewSerieIdx.value = pos.serieIdx;
    previewStepIdx.value = pos.stepIdx;
    previewPartial.value = null;
    previewRaffaleStarted.value = false;
    previewMode.value = true;
  };

  // Fire the current preview step's devices (no scoring). Mirrors advancePlayStep's
  // branch structure for a_schuss two-tap and raffale two-throw timing.
  const advancePreviewStep = async () => {
    const step = previewStep.value;
    if (!step) return;
    const rangeId = currentRangeId.value;
    try {
      if (step.type === StepType.SOLO) {
        await sendPositionCommand(rangeId, _posId(step));
        _advancePreviewCursor();
      } else if (step.type === StepType.PAIR) {
        await Promise.all([
          sendPositionCommand(rangeId, _posId1(step)),
          sendPositionCommand(rangeId, _posId2(step)),
        ]);
        _advancePreviewCursor();
      } else if (step.type === StepType.A_SCHUSS) {
        if (previewPartial.value === null) {
          await sendPositionCommand(rangeId, _posId1(step));
          previewPartial.value = PartialStep.FIRST;
        } else {
          await sendPositionCommand(rangeId, _posId2(step));
          _advancePreviewCursor(); // resets previewPartial
        }
      } else if (step.type === StepType.RAFFALE) {
        if (!previewRaffaleStarted.value) {
          await sendPositionCommand(rangeId, _posId(step));
          previewRaffaleStarted.value = true;
        }
      }
    } catch (err) {
      console.error('Failed to send position command during preview:', err);
    }
  };

  const completePreviewRaffaleStep = async () => {
    const step = previewStep.value;
    if (step?.type !== StepType.RAFFALE) return;
    try {
      await sendPositionCommand(currentRangeId.value, _posId(step));
    } catch (err) {
      console.error('Failed to send second preview raffale command:', err);
    }
    _advancePreviewCursor(); // resets previewRaffaleStarted
  };

  // No Bird in preview: re-throw the current step from its first phase; frontier stays.
  const retryPreviewStep = async () => {
    previewPartial.value = null;
    previewRaffaleStarted.value = false;
    await advancePreviewStep();
  };

  // Überspringen: mark the current step seen without firing a device.
  const skipPreviewStep = () => {
    if (!previewStep.value) return;
    _advancePreviewCursor();
  };

  const stopPreview = () => {
    previewMode.value = false;
    previewPartial.value = null;
    previewRaffaleStarted.value = false;
  };

  const _resetPreview = () => {
    previewMode.value = false;
    previewEngaged.value = false;
    previewFrontier.value = 0;
    previewSerieIdx.value = 0;
    previewStepIdx.value = 0;
    previewPartial.value = null;
    previewRaffaleStarted.value = false;
  };
```

- [ ] **Step 6: Reset preview state on close and cancel**

In `closePlayback` (~586), add `_resetPreview();` before the closing brace of the function (after `playerConfirmations.value = new Map();`):

```js
    playerConfirmations.value = new Map();
    _resetPreview();
  };
```

In `cancelGroupSetup` (~611), add `_resetPreview();`:

```js
  const cancelGroupSetup = () => {
    showGroupSetup.value = false;
    pendingGroupSerien.value = null;
    _resetPreview();
  };
```

(`startGroupPlay` intentionally does **not** reset preview state — the frontier and engaged flag must survive the setup→play transition.)

- [ ] **Step 7: Export the new state, computeds, and actions**

In the store's returned object (~768), add to the State group:

```js
    previewMode,
    previewEngaged,
    previewFrontier,
    previewSerieIdx,
    previewStepIdx,
    previewPartial,
    previewRaffaleStarted,
```

to the Computed group:

```js
    previewProgram,
    previewStep,
    scoredFlatIndex,
    needsPreview,
```

and to the Actions group:

```js
    startPreview,
    advancePreviewStep,
    completePreviewRaffaleStep,
    retryPreviewStep,
    skipPreviewStep,
    stopPreview,
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `npm run test src/stores/__tests__/playSessionStore.preview.test.js`
Expected: PASS.

- [ ] **Step 9: Run the full store test folder for regressions**

Run: `npm run test src/stores/__tests__/`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add src/stores/playSessionStore.js src/stores/__tests__/playSessionStore.preview.test.js
git commit -m "$(cat <<'EOF'
[ui] play session: Serie-Anschauen preview cursor, frontier, and gate

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Preview screen, setup entry, and the "Weitere Schritte anzeigen" gate

Add the "Serie anschauen" setup button, the preview screen (Tap / No Bird / Überspringen / Stop), and the mid-play gate overlay that intercepts the first shooter at an unseen step.

**Files:**
- Modify: `src/views/shooter/ShooterPlayPage.vue` (template top-level branches, `handleCurrentStepClick` ~774, raffale watch ~834, styles)
- Test: `src/views/__tests__/ShooterPlayPage.test.js` (extend)

**Interfaces:**
- Consumes: `startPreview`, `advancePreviewStep`, `retryPreviewStep`, `skipPreviewStep`, `stopPreview`, `completePreviewRaffaleStep`, `previewMode`, `previewStep`, `previewFrontier`, `previewProgram`, `previewRaffaleStarted`, `needsPreview` from Task 3; existing `getTypeLabel`, `getStepLetter`, `modeBadgeStyle`.
- Produces (component-local): `previewFromSetup()`, `handlePreviewTap()`, `handlePreviewNoBird()`, `previewTotal` computed.

- [ ] **Step 1: Write the failing tests**

Append to `src/views/__tests__/ShooterPlayPage.test.js`:

```js
describe('ShooterPlayPage — Serie Anschauen preview', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shows the Serie anschauen button in group setup', () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien([{ steps: [soloStep] }])
    const wrapper = mountPage()
    const labels = wrapper.findAll('.modal-actions .btn').map((n) => n.text())
    expect(labels).toContain('Serie anschauen')
  })

  it('renders the preview screen and Überspringen calls skipPreviewStep', async () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien([{ steps: [soloStep, pairStep] }])
    store.startPreview()
    const skipSpy = vi.spyOn(store, 'skipPreviewStep')
    const wrapper = mountPage()
    expect(wrapper.find('.preview-page').exists()).toBe(true)
    await wrapper.get('.btn-skip').trigger('click')
    expect(skipSpy).toHaveBeenCalled()
  })

  it('gates the first shooter at an unseen step and does not release on tap', async () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien([{ steps: [soloStep] }])
    store.startPreview()
    store.stopPreview() // engaged, frontier 0

    store.showGroupSetup = false // startGroupPlay normally clears this; we bypass it
    store.playProg = [{ steps: [soloStep] }]
    store.sessionPlayers = [{ id: 'A', displayName: 'A' }, { id: 'B', displayName: 'B' }]
    store.roundOrder = [0, 1]
    store.currentPlayerIndex = 0
    store.currentSerieIndex = 0
    store.currentStepIndex = 0

    const advanceSpy = vi.spyOn(store, 'advancePlayStep')
    const wrapper = mountPage()
    expect(wrapper.find('.preview-gate-overlay').exists()).toBe(true)
    await wrapper.get('.step-card').trigger('click')
    expect(advanceSpy).not.toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm run test src/views/__tests__/ShooterPlayPage.test.js`
Expected: FAIL — no `Serie anschauen` button, no `.preview-page`, no `.preview-gate-overlay`.

- [ ] **Step 3: Add the "Serie anschauen" setup button**

In the setup modal's `.modal-actions` (~35-40), insert the preview button between Abbrechen and Starten:

```html
        <div class="modal-actions">
          <button class="btn btn-cancel" @click="cancelGroupSetup">Abbrechen</button>
          <button v-if="!_isCompetitionMode" class="btn btn-preview" @click="previewFromSetup">Serie anschauen</button>
          <button class="btn btn-primary" :disabled="groupPlayers.length === 0" @click="beginGroupPlay">
            Starten
          </button>
        </div>
```

- [ ] **Step 4: Add the preview screen as the first top-level branch**

At the very top of the `<template>`, **above** the existing group-setup `<div v-if="store.showGroupSetup" ...>`, add the preview branch (so preview takes precedence over both setup and play):

```html
  <!-- Preview screen ("Serie Anschauen") — precedes setup and play -->
  <div v-if="store.previewMode" class="play-page preview-page">
    <div class="play-topbar">
      <div class="player-info">
        <span class="player-name">Serie anschauen</span>
      </div>
      <div class="topbar-right">
        <div class="score-display">
          <span class="score-label">Gezeigt</span>
          <span class="score-value player-count-val">{{ store.previewFrontier }}/{{ previewTotal }}</span>
        </div>
      </div>
    </div>

    <div class="carousel-area">
      <div class="carousel-section current-section">
        <div class="section-label">Vorschau</div>
        <div
          v-if="store.previewStep"
          class="step-card"
          :class="`is-${store.previewStep.type}`"
          @click="handlePreviewTap"
        >
          <span class="card-badge" :style="modeBadgeStyle(store.previewStep.type)">
            {{ getTypeLabel(store.previewStep.type) }}
          </span>
          <div class="card-label">{{ getStepLetter(store.previewStep) }}</div>
          <p class="hint">Tippen zum Zeigen →</p>
        </div>
        <div v-else class="step-card getroffen-card" @click="store.stopPreview()">
          <span class="card-badge badge-getroffen">Vorschau fertig</span>
          <div class="card-label getroffen-label">Alle gezeigt</div>
          <p class="hint">Tippen zum Beenden →</p>
        </div>
      </div>
    </div>

    <div class="action-bar preview-action-bar">
      <button class="action-btn btn-no-bird" :disabled="!store.previewStep" @click="handlePreviewNoBird">
        <span class="btn-label">No Bird</span>
      </button>
      <button class="action-btn btn-skip" :disabled="!store.previewStep" @click="store.skipPreviewStep()">
        <span class="btn-label">Überspringen</span>
      </button>
      <button class="action-btn btn-stop-preview" @click="store.stopPreview()">
        <span class="btn-label">Stop</span>
      </button>
    </div>
  </div>

```

Change the existing setup line from `v-if` to `v-else-if`:

```html
  <div v-else-if="store.showGroupSetup" class="play-page group-setup-page">
```

- [ ] **Step 5: Add the mid-play gate overlay**

Inside the main play view (the `v-else-if="store.playProg"` block), add the gate overlay just before the closing `</div>` of that block — next to the existing next-shooter overlay (~366):

```html
    <!-- Preview gate: first shooter reached an un-previewed step -->
    <Transition name="next-shooter-fade">
      <div v-if="store.needsPreview" class="next-shooter-overlay preview-gate-overlay">
        <div class="next-shooter-card">
          <span class="next-shooter-label">Vorschau</span>
          <span class="next-shooter-name">Weitere Schritte</span>
          <span class="next-shooter-hint">Nächste Wurfscheiben zeigen, bevor es weitergeht</span>
          <button class="next-shooter-start-btn" @click="store.startPreview()">
            Weitere Schritte anzeigen →
          </button>
        </div>
      </div>
    </Transition>
```

- [ ] **Step 6: Add the preview script helpers**

In `<script setup>`, add near the group-setup handlers (after `cancelGroupSetup` ~556):

```js
// ── Preview ("Serie Anschauen") ─────────────────────────────────────────────────
const previewTotal = computed(() => {
  const prog = store.previewProgram;
  return prog ? prog.reduce((sum, seg) => sum + seg.steps.length, 0) : 0;
});

const previewFromSetup = () => { store.startPreview(); };
const handlePreviewTap = () => { store.advancePreviewStep(); };
const handlePreviewNoBird = () => { store.retryPreviewStep(); };
```

- [ ] **Step 7: Gate the scored tap handler**

At the top of `handleCurrentStepClick` (~774), return early when the gate is active:

```js
const handleCurrentStepClick = () => {
  // Preview gate: the first shooter has reached an un-previewed step — the
  // "Weitere Schritte anzeigen" overlay handles it, so swallow the tap.
  if (store.needsPreview) return;
  const step = store.currentStep;
  if (!step) return;
```

- [ ] **Step 8: Drive the preview raffale second throw**

After the existing raffale monitor `watch` (~834), add a preview equivalent that fires the second throw after the same ~1s cadence:

```js
// Preview raffale: fire the second throw ~1s after the first so the shooter sees
// the real cadence. Mirrors the scored raffale timing (view-driven).
watch(
  () => store.previewRaffaleStarted,
  (started) => {
    if (started) {
      setTimeout(() => { store.completePreviewRaffaleStep(); }, 1000);
    }
  }
);
```

- [ ] **Step 9: Add styles for the preview button and skip button**

In `<style scoped>`, near `.btn-primary` (~1463) and the action-bar styles, add:

```css
.btn-preview {
  flex: 1;
  background: color-mix(in srgb, var(--sg-accent) 12%, transparent);
  color: var(--sg-accent);
  border: 1px solid color-mix(in srgb, var(--sg-accent) 30%, transparent);
}

.btn-preview:hover {
  background: color-mix(in srgb, var(--sg-accent) 20%, transparent);
}

.action-btn.btn-skip {
  border-color: color-mix(in srgb, var(--sg-accent) 55%, transparent);
  background: color-mix(in srgb, var(--sg-accent) 12%, transparent);
  color: var(--sg-accent);
}

.action-btn.btn-skip:hover:not(:disabled) {
  background: color-mix(in srgb, var(--sg-accent) 20%, transparent);
}
```

(`.btn-stop-preview` intentionally keeps the default red `.action-btn` styling — Stop is a halting affordance — so it needs no extra rule.)

- [ ] **Step 10: Run the tests to verify they pass**

Run: `npm run test src/views/__tests__/ShooterPlayPage.test.js`
Expected: PASS (all cases).

- [ ] **Step 11: Run lint and the full test suite**

Run: `npm run lint:check`
Expected: no warnings.

Run: `npm run test`
Expected: PASS.

- [ ] **Step 12: Commit**

```bash
git add src/views/shooter/ShooterPlayPage.vue src/views/__tests__/ShooterPlayPage.test.js
git commit -m "$(cat <<'EOF'
[ui] play page: Serie-Anschauen preview screen and unseen-step gate

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Verification (whole feature)

- [ ] `npm run test` — all green.
- [ ] `npm run lint:check` — no warnings.
- [ ] `npm run build` — succeeds with no warnings.
- [ ] Manual (kiosk preview): "Als Gruppe Starten" → add 3–4 shooters → reorder with the up/down controls → mark a non-top shooter as Start → "Serie anschauen" → tap through a couple of steps, use Überspringen once and No Bird once → Stop → "Starten" → confirm turn order begins at the marked starter and wraps → shoot until the first shooter hits the frontier → confirm "Weitere Schritte anzeigen" appears → preview more → Stop → play resumes → confirm shooters 2..n run without preview pauses.

## Notes / Assumptions carried from the spec

- **A1** — reorder/starter/preview are non-competition only.
- **B1** — preview fires immediately on tap; Verzögert/Rufauslösung gating does not apply in preview.
- Preview state is not persisted across reloads.
