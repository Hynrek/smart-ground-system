# Audit Phase & Score Correction — Design Spec

**Date:** 2026-06-09  
**Scope:** `ShooterPlayPage` final screen (competition mode only for audit; correction available whenever final screen shows)

---

## Overview

Two features added to the post-play final score screen:

1. **Audit phase** — Per-shooter confirmation checkboxes that must all be ticked before "Beenden" is enabled. Competition mode only (`_isCompetitionMode === true`).
2. **Score correction** — Any step row in the ScoreTable on the final screen is tappable. Tapping opens an inline picker to override the result. Corrected rows display a pencil indicator.

---

## 1. Store changes — `playSessionStore`

### Audit confirmations

```js
const playerConfirmations = ref(new Map()) // playerId → boolean
```

- Initialised (all `false`) inside `startGroupPlay()` after `setupPlayers()` populates `sessionPlayers`.
- Reset to empty `Map` in `closePlayback()`.

**New actions:**
- `confirmPlayer(playerId)` — sets `playerConfirmations.value.set(playerId, true)`; triggers reactivity via `playerConfirmations.value = new Map(playerConfirmations.value)`
- `unconfirmPlayer(playerId)` — sets the entry to `false`, same reactivity trigger

**New computed:**
- `allPlayersConfirmed` — `true` when `sessionPlayers` is non-empty and every player id maps to `true` in `playerConfirmations`

`confirmComplete()` is **not** gated in the store — the gate lives in the UI (the Beenden button's `:disabled`). The store stays unaware of competition vs. training context for this purpose.

### Score correction

Each `stepState` entry (already has `playerId`, `serieIndex`, `stepIndex`, `state`, `pointValue`, `noBirds`, `pointsEarned`) gains:

```js
corrected: false,      // boolean — true after first manual correction
originalState: null,   // StepState value — set only on the first correction
```

Both fields are included in the initial `stepStates` array built in `startGroupPlay()` and `playPasseWithScore()`.

**New action: `correctStep(playerId, serieIdx, stepIdx, newState)`**

1. Find the stepState matching `(playerId, serieIdx, stepIdx)`.
2. If `stepState.corrected === false`, save `stepState.originalState = stepState.state`.
3. Set `stepState.corrected = true`.
4. Subtract old `pointsEarned` from `playScore.totalPoints`.
5. Apply new state: recalculate `pointsEarned = max(0, pointValue - getPointDeduction(newState))`.
6. Add new `pointsEarned` back to `playScore.totalPoints`.
7. Set `stepState.state = newState`.

`buildPlayerResults()` serialises all stepState fields → `corrected` and `originalState` reach the backend automatically.

---

## 2. ScoreTable component changes

### New prop

```js
editable: { type: Boolean, default: false }
```

### Tappable rows

When `editable` is `true`:
- `.step-row` gets `cursor: pointer` and a subtle hover highlight.
- `@click` on each row emits `correct-step` with payload `{ playerId, serieIndex, stepIndex, currentState }`.
- ScoreTable does **not** manage picker state — it only emits.

### Corrected indicator

- When `stepState.corrected === true`, a pencil icon (✏) is rendered in a fifth column of the step row grid.
- Grid template expands: `40px 1fr auto auto auto` (last column for the badge).
- The badge column is always present in the DOM when `editable` is true (empty for non-corrected rows) to keep alignment consistent.
- No color change to the row — normal green/red state colors are preserved.

### Backward compatibility

`editable` defaults to `false`. All existing ScoreTable usages (non-final-screen) are unaffected.

---

## 3. ShooterPlayPage — final screen changes

### Correction picker

**Local state:**
```js
const correctionTarget = ref(null) // { playerId, serieIndex, stepIndex, currentState } | null
```

**Trigger:** ScoreTable emits `correct-step` → `correctionTarget.value = payload`.

**Picker UI:** A small overlay/modal that appears centered over the score card:
- Title: `Schritt korrigieren`
- For `StepType.SOLO`: 2 buttons — `Getroffen` (→ `DONE`) and `Fail` (→ `FAILED_BOTH`)
- For `StepType.PAIR`, `A_SCHUSS`, `RAFFALE`: 4 buttons — `Getroffen` (→ `DONE`), `Fail A` (→ `FAILED_A`), `Fail B` (→ `FAILED_B`), `Fail Beide` (→ `FAILED_BOTH`)
- On selection: calls `store.correctStep(playerId, serieIndex, stepIndex, newState)`, then clears `correctionTarget`
- Dismiss (tap backdrop or ✕ button): clears `correctionTarget` without changing anything

**Step type lookup for the picker:** resolved via `store.playProg[serieIndex].steps[stepIndex].type`.

### Audit checkboxes

Rendered **between ScoreTable and Beenden**, only when `_isCompetitionMode` is `true`.

**Structure per player row:**
```
[ checkbox ]  [ displayName ]  [ earnedPts / maxPts ]
```

- Ticking: `store.confirmPlayer(player.id)`
- Unticking: `store.unconfirmPlayer(player.id)`
- The checkbox reads from `store.playerConfirmations.get(player.id) ?? false`

**Beenden button:**
```vue
:disabled="_isCompetitionMode && !store.allPlayersConfirmed"
```
Training mode: always enabled (no change from current behaviour).

### ScoreTable wiring on final screen

```vue
<ScoreTable
  :editable="true"
  @correct-step="correctionTarget = $event"
  ...
/>
```

---

## Out of scope

- Correction during active play (only on final screen).
- Audit phase in training mode.
- Displaying correction history / audit log beyond the `corrected` flag on each step.
- Backend schema changes — the existing `buildPlayerResults()` payload already carries the new fields.
