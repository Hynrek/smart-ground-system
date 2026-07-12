# ShooterPlayPage — `Fail · Treffer · No Bird` bar + in-play hit undo

Date: 2026-06-25
Status: Approved (pending spec review)
Area: `src/views/shooter/ShooterPlayPage.vue`, `src/stores/playSessionStore.js`

## Problem

During an active passe in `ShooterPlayPage`, firing a step records a full hit by
default. The bottom action bar then lets the shooter mark the **last-fired** step
as a miss (`Fail A` / `Fail B` / `Fail A+B`). But there is **no inverse** — once a
fail is tapped by accident, the step stays failed until the passe ends. The only
fail→hit correction lives in the `ScoreTable` picker on the final score screen, so
a mis-tap mid-passe is a dead end (the shooter must finish, then correct).

A secondary annoyance: on Solo steps two of the three fail buttons are disabled
dead weight, adding visual noise to a four-button bar.

## Goal

Give the shooter a persistent, no-timer way to revert an accidental fail *during*
play, while reducing the action-bar noise — without adding a fifth button.

## Design

### Action bar: four buttons → three

Replace the current `Fail A · Fail B · Fail A+B · No Bird` bar with:

```
┌──────────┬──────────┬──────────┐
│   Fail   │ Treffer  │ No Bird  │
│  (red)   │ (green)  │ (accent) │
└──────────┴──────────┴──────────┘
```

- **Fail** (red, `ti-x`) — registers a miss on the last-fired step.
  - On a **Solo** step: applies the miss immediately (one possible outcome), no flyout.
  - On a **double** (Pair / a.Schuss / Raffale): opens the Fail flyout (below).
- **Treffer** (green, `ti-check`) — the undo. Sets the last-fired step back to a
  full hit. **Disabled** unless the last-fired step is currently in a failed state
  (so it visually signals when there is something to undo).
- **No Bird** (accent, `ti-refresh`) — unchanged; retries the last step
  (`store.retryStep()`, gated by `store.canRetry`).

German display label is `Treffer`; the code identifier stays `hit`-flavoured
(`canMarkHit`, `markLastStepHit`).

### Fail flyout (doubles only)

A bottom sheet that slides up directly above the action bar — chosen over a
centered modal so it echoes the 3-column bar beneath it and covers less screen.
Layout is a **horizontal 3-column grid** mirroring the action bar, each cell a
label (position notation) over its point cost:

```
        ⎯⎯⎯  (drag handle)
   Fail · <step notation>            ✕
 ┌───────────┬───────────┬───────────┐
 │    A      │    B      │   A + B   │
 │   −1      │   −1      │    −2     │
 └───────────┴───────────┴───────────┘
```

Labels use compact position notation (not the verbose "Fail A" prose), derived
from the existing per-step letter logic:

| Step type | Col 1 | Col 2 | Col 3 (both) |
|---|---|---|---|
| Pair      | `A`  | `B`  | `A + B` |
| a.Schuss  | `A`  | `B`  | `A + B` |
| Raffale   | `A1` | `A2` | `A×2`   |

(Raffale repeats its single trap letter; `A1`/`A2` are the first/second shot,
`A×2` both. The `×` is the same glyph `stepNotation` already uses.)

Tapping a cell calls `store.failStep(failType)` with `'a' | 'b' | 'both'` and
closes the sheet. Tapping the backdrop or `✕` closes without change.

### Hit undo behaviour

`Treffer` always means *full hit* (`StepState.DONE`) — it never needs sub-options,
which is why `Fail` gets a flyout and `Treffer` is a single tap. It acts on the
same anchor the fail buttons use (`store.playLastDeviceStep`), so its reach is the
last-fired step only — matching the "oops, undo that" scope (not arbitrary earlier
steps).

### Responsiveness

No structural change between sizes. The device frame is already full-screen
(`position: fixed; inset: 0`). On a phone the flyout and bar span the narrow width;
on a tablet/kiosk they get the same layout with larger touch targets (≥48px) using
the vertical space that is currently empty. The 3-column grid simply fills the
width it is given.

### Out of scope (smaller blast radius)

- The **final score screen** keeps its existing centered `ScoreTable` correction
  picker, untouched. Only the *in-play* `Fail` uses the new flyout.
- Undo is scoped to **fail actions** only. `No Bird` / retry is not covered by
  `Treffer` (it already has its own button and `canRetry` gate).
- The *Abgeschlossene Schritte* cards stay inert (no tap-to-correct). That was the
  more general option we considered and explicitly set aside.

## Implementation

### Store — `playSessionStore.js`

Add two members (mirroring the existing `failStep` / `canRetry` pattern):

```js
// Enabled only when the last-fired step currently carries a fail deduction.
const canMarkHit = computed(() => {
  if (!playLastDeviceStep.value || playComplete.value) return false;
  const { serieIdx, stepIdx } = playLastDeviceStep.value;
  const state = findStepState(serieIdx, stepIdx);
  return !!state && getPointDeduction(state.state) > 0;
});

// Revert the last-fired step to a full hit. Inverse of updateFailState's point math.
const markLastStepHit = () => {
  if (!playLastDeviceStep.value || playComplete.value) return;
  const { serieIdx, stepIdx } = playLastDeviceStep.value;
  const state = findStepState(serieIdx, stepIdx);
  if (!state) return;
  const restore = Math.min(getPointDeduction(state.state), state.pointValue);
  if (restore === 0) return;             // already a full hit / pending
  state.state = StepState.DONE;
  state.pointsEarned = Math.min(state.pointValue, state.pointsEarned + restore);
  playScore.value.totalPoints += restore;
};
```

Export both from the store's public interface. This deliberately does **not** go
through `correctStep()` — that path sets the `corrected` / `originalState` audit
flags meant for the final screen; an in-play mis-tap fix should not be recorded as
an audited correction.

### View — `ShooterPlayPage.vue`

1. **Action bar** (`template`): replace the four `action-btn`s with three —
   `Fail`, `Treffer`, `No Bird`.
   - `Fail` → `onFailTapped()`: if `lastStepWasADouble`, open the flyout; else call
     `store.failStep('a')` directly (Solo's only outcome).
   - `Treffer` → `:disabled="!store.canMarkHit"`, `@click="store.markLastStepHit()"`.
   - `No Bird` → unchanged.
   - Reuse `canFail` to gate `Fail`.
2. **Fail flyout**: a new bottom-sheet element (own `Transition`, slide-up) shown
   when `failSheetOpen`. Three cells built from the last-fired step's letters via a
   small `flyoutCells(step)` helper returning `[{ label, cost, failType }]` per the
   table above. Reuse `lastFiredStep`.
3. Remove the now-unused verbose action-bar fail labels if nothing else consumes
   them (`failLabelA/B/Both` are still used by the final-screen correction picker —
   keep those; only the bar wiring changes).

### Styling

- New `Fail · Treffer · No Bird` grid keeps `grid-template-columns: repeat(3, 1fr)`.
- `Treffer` styled with the success palette (`--sg-color-success`), disabled state
  matching the existing `.action-btn:disabled` (opacity 0.3).
- Flyout reuses the dark elevated surface tokens already used by
  `.correction-picker`; cells reuse `.btn-fail` colours; add a slide-up transition.

## Testing

Store tests (`src/stores/__tests__/playSessionStore`):
- `canMarkHit` is false with no last step, false when last step is `DONE`/`PENDING`,
  true after a fail.
- `markLastStepHit` restores `state` to `DONE`, restores `pointsEarned` to
  `pointValue`, and adds the deduction back to `totalPoints` — for Solo
  (`FAILED_BOTH`), partial (`FAILED_A`), and full (`FAILED_BOTH`) doubles.
- `markLastStepHit` is a no-op when there is no deduction.

Component tests (`ShooterPlayPage`):
- Solo step: `Fail` calls `failStep` directly, no flyout shown.
- Double step: `Fail` opens the flyout; choosing a cell calls `failStep` with the
  right type and closes the sheet.
- `Treffer` is disabled until a fail is registered, then reverts the step.

## Edge cases

- Solo `pointValue` may be smaller than the nominal deduction; both fail and undo
  cap via `Math.min(..., pointValue)`, so points never go negative or over max.
- `playComplete` blocks both `canMarkHit` and `markLastStepHit` (final screen owns
  corrections from that point).
- Closing the flyout without a choice leaves the step a hit (the default).
