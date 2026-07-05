# Group Play Extensions — Design

**Date:** 2026-07-05
**Area:** smart-ground-ui — Shooter Remote / Group Play
**Status:** Approved (brainstorming), pending implementation plan

## Summary

Extend the existing "Als Gruppe Starten" flow with three capabilities:

1. **Reorder** shooters in the group (reposition a person).
2. **Mark a starter** — a separate flag from list position; the list is a fixed
   rotation and the starter is the entry point, wrapping around.
3. **"Serie Anschauen"** — an opt-in preview run so shooters can watch the clays
   before scored play, with a just-in-time "seen frontier" that pauses the first
   shooter whenever they reach an un-previewed step.

Everything is scoped to **normal group play** (a serie started via "Als Gruppe
Starten"), not competition mode (rotte order and rules are backend-defined).

## Current State

- **"Als Gruppe Starten"** ([ShooterFlyoutPanel.vue:242](../../../src/components/shooter-remote/ShooterFlyoutPanel.vue))
  stages the serie (`setPendingGroupSerien`) and routes to the play page.
- **Group setup modal** ([ShooterPlayPage.vue:3](../../../src/views/shooter/ShooterPlayPage.vue))
  lets the operator add (manual/QR) and remove shooters, numbered `1..n` in list order.
- **On Starten** `startGroupPlay` runs; play goes shooter-by-shooter in list order,
  shooter #1 (index 0) always first.
- The store ([playSessionStore.js](../../../src/stores/playSessionStore.js)) already
  has rotation scaffolding — `roundOrder`, `roundStartIndex`, `buildRoundOrder`,
  `setRoundStartOverride` — but it is only half-wired for group play:
  - `currentPlayer` (line 83) reads through `roundOrder`.
  - `nextPlayer` (line 91) and `advanceToNextPlayer` (line 700) index
    `sessionPlayers` **directly**, bypassing `roundOrder`.
  - This works today only because `roundStartIndex` is always 0 (identity order).

## Section A — Reorder + Starter Marker

### UI (group setup modal, non-competition only)

Each player row gains:
- **Reposition controls** — up/down arrows that swap the row with its neighbor in
  `groupPlayers`. Touch targets ≥48px. The visible `1..n` number is the rotation order.
- **Starter marker** — a star/flag toggle; exactly one shooter is the starter,
  defaulting to the top row. Marking a shooter tags them as the rotation entry
  point; it does **not** move them in the list.

Competition mode (`_isCompetitionMode`) keeps its current fixed, read-only list.

### Behaviour

Turn order follows `roundOrder = [starter, starter+1, … wrap]`.
Example: order `[A,B,C,D]`, starter `C` → **C, D, A, B**.

### Store changes

- `setupPlayers(players, startIndex = 0)` — sets `roundStartIndex = startIndex`,
  then `buildRoundOrder()`.
- `startGroupPlay(players, …, starterIndex = 0)` — passes `starterIndex` into
  `setupPlayers`.
- `nextPlayer` — read through `roundOrder`:
  `roundOrder[currentPlayerIndex + 1]` → `sessionPlayers[…]`, or `null` past the end.
- `advanceToNextPlayer` already increments `currentPlayerIndex` (indexes `roundOrder`
  via `currentPlayer`) — no change beyond verifying it respects the wrap.

## Section B — "Serie Anschauen" Preview State Machine

### Top-level states in ShooterPlayPage (mutually exclusive)

1. `showGroupSetup` — setup modal, gains a **"Serie anschauen"** button beside "Starten".
2. `previewMode` — the preview screen.
3. Scored play (`playProg && !previewMode`) — unchanged.

### Preview screen

Reuses the existing step-card rendering, stripped down:

- **Tap card** → fire the step's devices (reuses `releaseIdsForStep` +
  `sendPositionCommand`, including a_schuss two-tap and raffale timing), mark the
  step seen, advance the preview cursor.
- **No Bird** → re-throw the current clay; cursor stays put.
- **Überspringen** → mark seen + advance cursor, **no throw** (for when the same
  target repeats and re-throwing is pointless). A skipped step counts as seen and
  will not trigger a later pause.
- **Stop** → leave preview, return to origin (setup modal, or scored play mid-session).
- **No** Fail / Treffer / score. **No** Verzögert/Rufauslösung gating — preview fires
  immediately on tap (B1).

A step is "seen" only once **fully** previewed (both a_schuss taps done / raffale
second throw done), matching how `markStepDone` advances scored play.

### The frontier

`previewFrontier` is a flat step index = count of fully-seen steps. It only moves
forward (high-water mark); re-previewing already-seen steps never lowers it.

### Entry point 1 — from setup

"Serie anschauen" → `startPreview()` reads the staged `pendingGroupSerien`, opens
preview at the frontier (0 first time). Stop → back to setup. "Starten" begins scored
play with the frontier preserved.

### Entry point 2 — mid-play (Stop & Go)

During shooter 1's turn, before a step can be shot, if
`stepFlatIndex ≥ previewFrontier` **AND** preview was ever engaged **AND**
`currentPlayerIndex === 0`, the tap is intercepted and a **"Weitere Schritte
anzeigen"** overlay appears instead of releasing. Tapping it → `startPreview()` from
the frontier → operator previews ahead → Stop → back to scored play, which can now
proceed. Repeatable until the frontier reaches program end.

### Gate retirement (automatic)

The pause never fires when any of these hold:
- Preview was never engaged (plain Starten = today's behaviour), OR
- the frontier has reached program end, OR
- the active shooter is not the first (`currentPlayerIndex > 0`).

So shooters 2..n always run straight through (they watched shooter 1 reveal everything).

## Section C — Store Model & Data Flow

### New preview state in playSessionStore

```
previewMode          : boolean   // currently on the preview screen
previewEngaged       : boolean   // preview started at least once → gate active
previewFrontier      : number    // flat index of fully-seen steps (high-water mark)
previewSerieIdx      : number    // preview cursor (independent of scored position)
previewStepIdx       : number
previewPartial       : PartialStep | null  // a_schuss phase during preview
previewRaffaleStarted: boolean              // raffale phase during preview
```

The preview cursor is deliberately **separate** from `currentSerieIndex` /
`currentStepIndex` (the scored position) because preview runs ahead of, and
interleaves with, shooter 1's actual shooting.

### New actions

- `startPreview()` — set `previewMode = true`, `previewEngaged = true`; place the
  preview cursor at the frontier.
- `advancePreviewStep()` — fire current preview step (reusing release primitives),
  advance cursor + frontier on full completion. No score mutation.
- `retryPreviewStep()` — No Bird: re-fire current preview step, cursor/frontier unchanged.
- `skipPreviewStep()` — Überspringen: advance cursor + frontier, no device command.
- `stopPreview()` — set `previewMode = false`; frontier is left at the cursor.
- `closePlayback()` — also resets all preview state.
- `startGroupPlay` — must **preserve** `previewFrontier` / `previewEngaged` on the
  setup→play transition (do not wipe them).

### New computed

- `previewProgram` = `playProg ?? pendingGroupSerien` — the step source for preview.
- `scoredFlatIndex` — flat index of the current scored step.
- `needsPreview` — the gate condition (Entry point 2). The view watches this to show
  the "Weitere Schritte anzeigen" overlay and to suppress the release on tap.

### Data flow

Unchanged pipeline: view → store action → service (`sendPositionCommand`). Preview
adds no new service calls; it reuses `releaseIdsForStep` and `sendPositionCommand`.
No backend/API changes.

## Testing

Extend existing `ShooterPlayPage.test.js` and the play-session store tests:

- **Wrap order:** `[A,B,C,D]` starter `C` → turn sequence C,D,A,B; `nextPlayer`
  correct across the wrap; `currentPlayer` consistent with `nextPlayer`.
- **Frontier pause:** partial preview then scored play pauses at the first unseen
  step; resuming preview then stopping clears the pause and lets the step fire.
- **Überspringen** advances the frontier with **no** `sendPositionCommand`; **No Bird**
  re-fires without advancing the frontier.
- **Plain Starten** (no preview engaged) never pauses — parity with today.
- **Gate scope:** no pause for shooters 2..n; no pause once frontier reaches end.

Tests are English; fresh Pinia per test; mock services with `vi.mock()`.

## Scope / Non-Goals

- Competition mode is **out of scope** for reorder/starter/preview (order and rules
  are backend-defined).
- No backend or OpenAPI changes.
- No persistence of preview state across page reloads (preview is a within-session
  operator aid).

## Assumptions

- **A1** — reorder + starter apply to normal group play only, not competition.
- **B1** — preview fires immediately on tap; Verzögert/Rufauslösung gating does not
  apply during preview.
