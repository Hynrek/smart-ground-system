# Verzögerung & Rufmodus in Play logic — Design Spec

**Date:** 2026-07-05
**Status:** Approved
**Scope:** `smart-ground-ui` — new shared composable + `ShooterRemoteView` refactor + `ShooterPlayPage` wiring

---

## Overview

The guided Play flow (`ShooterPlayPage`) fires device commands immediately when the operator taps a step card — it calls `playSessionStore.advancePlayStep()`, which calls `sendPositionCommand` right away. The free remote view (`ShooterRemoteView`) already supports two additional session modes that gate the fire:

- **Verzögerung** (`sessionMode === 'delayed'`) — a countdown (`delaySeconds`) runs before the command is released.
- **Rufmodus / Rufauslösung** (`sessionMode === 'rufausloesung'`) — the release is armed, then a microphone-detected shout ("Pull") triggers it, via the `useVoiceTrigger` composable and the `rufPeak` / `rufDauer` / `rufTotzeit` settings.

This spec brings those two modes into the Play logic so a card tap respects the active mode instead of always firing immediately.

**Locked design decisions (from brainstorming):**

1. **Mode source — inherit from the remote session mode.** Whatever `shooterRemoteStore.sessionMode` is active when the Serie starts carries into Play. No new mode selector in the Play page.
2. **Gating granularity — gate every manual release.** Each `advancePlayStep()` call that produces a manual device release gets its own countdown / voice call. Raffale's built-in second shot stays automatic and ungated.
3. **Code structure — extract a shared composable used by both views.** Pull the gating logic out of `ShooterRemoteView` into `useTriggerGating`; refactor the remote view to use it and wire it into Play.
4. **Play page shows a mode badge but no config modal.** Settings are configured in the remote view before starting the Serie (they are persisted and shared); Play only consumes them.

---

## 1. Architecture

```
shooterRemoteStore            ← source of truth: sessionMode + settings (UNCHANGED)
  sessionMode                   'throwing' | 'delayed' | 'rufausloesung' | 'recording'
  delaySeconds                  Verzögerung countdown (seconds)
  rufPeak / rufDauer / rufTotzeit   Rufmodus mic settings
        │
        ▼
useTriggerGating(store)       ← NEW composable
  arm(ids, onFire) / cancel()   wraps a fire callback with:
  phase, armedIds, ring state    • throwing (or fallback) → fire immediately
  micLevel/wouldTrigger/         • delayed → countdown, then fire
   micDenied (pass-through)      • rufausloesung → Totzeit + mic listen, then fire
        │
        ├───────────────────────┬────────────────────────┐
        ▼                       ▼                         ▼
ShooterRemoteView          ShooterPlayPage           remote config modal
 (refactored to use         (NEW wiring in            (reuses micLevel /
  the composable)            handleCurrentStepClick)   wouldTrigger / micDenied)
```

`playSessionStore.advancePlayStep()` and `services/rangePositionApi.sendPositionCommand` are **not modified**. The Play page decides *when* to call `advancePlayStep()`:

- `throwing` (or any unexpected mode) → call it immediately (today's behavior).
- `delayed` / `rufausloesung` → call it from the composable's `onFire` callback once the gate resolves.

---

## 2. Shared composable — `useTriggerGating(store)`

New file: `src/composables/useTriggerGating.js`

Absorbs the logic currently inline in `ShooterRemoteView.vue`:

- Verzögerung: `queuedIds`, `queueTotalMs`, `queueRemainingMs`, `scheduleDelayedFire`, `cancelQueue`, `countdownLabel`, `countdownRingStyle`, timers.
- Rufmodus: `rufArmedIds`, `rufPhase`, Totzeit countdown state, `beginListening`, `armPositions`, `cancelRuf`, plus the existing `useVoiceTrigger` instance.

### Public interface

| Export | Type | Description |
|---|---|---|
| `arm(ids, onFire)` | function | Start a gating episode for `ids` (1–2 ids, used for UI feedback only). Runs `onFire()` when the gate resolves. Branches on `store.sessionMode`. |
| `cancel()` | function | Abort any pending episode (clears timers, stops mic, resets state). |
| `phase` | `ref<string>` | `'idle' \| 'counting' \| 'totzeit' \| 'listening'`. |
| `armedIds` | `ref<string[]>` | Ids currently armed (for chip / ring rendering). |
| `totalMs` / `remainingMs` | `ref<number>` | Countdown values (for the ring + seconds label). |
| `ringStyle` | `computed` | Conic-gradient style; hue = amber for `delayed`, cyan for `rufausloesung`. |
| `countdownLabel` | `computed` | `"3s"`-style label from `remainingMs`. |
| `micLevel` / `wouldTrigger` / `micDenied` | `ref` | Pass-through from the internal `useVoiceTrigger` (for the remote's config modal live bar). |

### Behavior of `arm(ids, onFire)`

- **`sessionMode === 'throwing'`** (or `'recording'` / anything unexpected): call `onFire()` synchronously, no gating. `phase` stays `'idle'`.
- **`sessionMode === 'delayed'`**:
  - Guard: if an episode is already active, ignore (one command at a time) — matches the current `scheduleDelayedFire` guard.
  - Set `armedIds = ids`, `phase = 'counting'`, start the `delaySeconds * 1000` countdown (interval updates `remainingMs`, timeout fires).
  - On timeout: clear state, `phase = 'idle'`, `await onFire()`.
- **`sessionMode === 'rufausloesung'`**:
  - Set `armedIds = ids`. If `rufTotzeit > 0`: `phase = 'totzeit'`, run Totzeit countdown; then `phase = 'listening'`, `startListening(onFire)`. Otherwise go straight to `'listening'`.
  - On mic trigger: `useVoiceTrigger` stops the mic; run `onFire()`, reset to `'idle'`.
- **Re-arm with an already-armed id** → `cancel()` (tap-to-abort), consistent with current behavior.

### Cleanup

`cancel()` clears countdown timers, calls `stopListening()`, and resets `phase`/`armedIds`/countdown refs. Callers invoke `cancel()` on unmount / mode change / lock.

---

## 3. `ShooterRemoteView` refactor (behavior-preserving)

Replace the inline gating blocks with the composable. Mechanical mapping:

| Before (inline) | After (composable) |
|---|---|
| `queuedIds` / `rufArmedIds` | `gating.armedIds` |
| `scheduleDelayedFire(ids, fn)` | `gating.arm(ids, fn)` |
| ruf arming (`armPositions`/`beginListening`) | `gating.arm(ids, fn)` |
| `cancelQueue()` + `cancelRuf()` | `gating.cancel()` |
| `countdownLabel` / `countdownRingStyle` | `gating.countdownLabel` / `gating.ringStyle` |
| `micLevel` / `wouldTrigger` / `micDenied` (config modal) | pass-through from `gating` |
| Totzeit ring computed values | `gating.ringStyle` (hue switches by mode) |

Chip/label/disabled helpers (`chipClass`, `chipLabel`, `isPositionDisabled`, `positionBtnClass`) read `gating.armedIds` / `gating.phase` instead of the removed refs. The mode flyout, badge, mic header button, and config modal are unchanged apart from the ref source.

The internal `useVoiceTrigger` moves inside `useTriggerGating`; the remote view no longer imports it directly.

This refactor must not change remote behavior — verified via the existing manual flows and any store/composable tests.

---

## 4. `ShooterPlayPage` wiring

Import `useShooterRemoteStore` and instantiate `const gating = useTriggerGating(shooterRemoteStore)`.

### `handleCurrentStepClick` rewrite

```
handleCurrentStepClick():
  step = currentStep
  if not step: return
  // tap-to-abort
  if gating.phase !== 'idle': gating.cancel(); return
  ids = releaseIdsForNextAdvance(step, store.playPartialStep)
  gating.arm(ids, () => store.advancePlayStep())
```

### `releaseIdsForNextAdvance(step, partialStep)`

Mirrors the branch structure inside `advancePlayStep()` so the UI knows which device(s) the next call will release:

- `solo` → `[posId(step)]`
- `pair` → `[posId1(step), posId2(step)]`
- `a_schuss`:
  - `partialStep === null` → `[posId1(step)]`
  - `partialStep === PartialStep.FIRST` → `[posId2(step)]`
- `raffale`:
  - not started → `[posId(step)]`
  - already started → **not reachable from a tap**; the second shot is auto (see below)

Uses the same `posId` / `posId1` / `posId2` resolution as the store (`positionId ?? posId`, etc.). To avoid divergence, expose the store's `_posId*` helpers (or a small `releaseIdsForStep` helper) so the Play page and store agree.

### Raffale second shot — unchanged

The existing `watch(() => store.playRaffaleStarted)` → `store.completeRaffaleStep()` timer in `ShooterPlayPage` stays exactly as-is. Only the **first** raffale release (the tap that starts it) is gated. This satisfies "the built-in timeout still fires the second automatically."

### a.Schuss — each tap gated separately

First tap arms `[posId1]`, gate resolves, `advancePlayStep()` fires device 1 and sets `playPartialStep = FIRST`. Second tap arms `[posId2]`, gate resolves, fires device 2 and completes the step. Two independent countdowns / voice calls, per the "gate every release" decision.

---

## 5. Play page UI feedback

The hero step card adopts the remote's existing visual language:

- **Verzögerung (`phase === 'counting'`):** amber countdown ring + seconds number overlaid on the current step card; `getHint()` returns "Verzögerung läuft…"; the card remains tappable to cancel.
- **Rufmodus:**
  - `phase === 'totzeit'`: cyan Totzeit ring + seconds; hint "Bereitmachen…".
  - `phase === 'listening'`: pulsing "Lauscht" state (cyan), reusing the `mode-dot-pulse` animation language; hint "Rufen zum Auslösen".
  - `micDenied`: inline card message "Mikrofon-Zugriff verweigert — bitte in den Browser-Einstellungen freigeben." The step stays un-fired; the operator can retry or the mode can be changed in the remote view.
- **Mode badge:** a small badge in the Play top bar showing "Verzögert" (amber) or "Rufauslösung" (cyan) when the corresponding mode is active, reusing the existing badge colors. Hidden in `throwing`.

For a.Schuss the ring/listening indicator attaches to the currently active device sub-item (the one `releaseIdsForNextAdvance` points at), consistent with the existing active-highlight in the a.Schuss display.

**Out of scope for Play:** the mic config modal, the three sliders, and the live level bar. Those remain only in `ShooterRemoteView`. Settings are shared via `shooterRemoteStore` + localStorage.

---

## 6. Cleanup & edge cases

- `gating.cancel()` is called on:
  - `onBeforeUnmount` in `ShooterPlayPage`.
  - `playComplete` becoming true.
  - Advancing to the next shooter (group play), so a stale countdown/mic never carries between shooters.
- `sessionMode === 'recording'` or any unexpected value in Play → `arm()` fires immediately (fallback), never blocks play.
- **Fail / Treffer / No-Bird** act on `playLastDeviceStep` (the previously fired step), independent of the current step's gate — they stay usable during a countdown / listening phase. No change required.
- Only one gating episode runs at a time (composable guard), matching the remote's "one command at a time" rule.

---

## 7. Files to Create / Modify

| Action | Path | Responsibility |
|---|---|---|
| Create | `src/composables/useTriggerGating.js` | Shared countdown + voice gating; wraps `useVoiceTrigger`; reads `shooterRemoteStore` mode + settings |
| Create | `src/composables/__tests__/useTriggerGating.test.js` | Unit tests: immediate/delayed/ruf branches, cancel, tap-to-abort, Totzeit→listening, micDenied |
| Modify | `src/views/shooter/ShooterRemoteView.vue` | Replace inline gating with the composable (behavior-preserving); update chip/label/disabled helpers to read composable state |
| Modify | `src/views/shooter/ShooterPlayPage.vue` | Instantiate gating; rewrite `handleCurrentStepClick`; add `releaseIdsForNextAdvance`; card ring/listening/denied UI; mode badge |
| Modify (optional) | `src/stores/playSessionStore.js` | Export a `releaseIdsForStep` helper (or the `_posId*` helpers) so the Play page and store resolve ids identically — only if reuse is cleaner than duplicating the tiny resolver |

No new views, routes, services, or backend changes. `sendPositionCommand` and `advancePlayStep` are untouched.

---

## 8. Out of Scope

- Backend integration — gating is UI-side only; the same `sendPositionCommand` fires either way.
- A Play-specific mode selector or config modal (modes and settings come from the remote view).
- Recording mode in Play (recording is a remote-only capture flow).
- Any change to scoring, fail/treffer/no-bird, or step-mode notation.
