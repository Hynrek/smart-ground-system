# Rufauslösung — Design Spec

**Date:** 2026-06-29  
**Status:** Approved  
**Scope:** `smart-ground-ui` — `ShooterRemoteView` + related store/composable

---

## Overview

"Rufauslösung" is a new session mode in the shooter remote view. The shooter taps a position (Solo) or two positions (Pair) to arm them, then shouts — the microphone detects the sound and fires the thrower(s) automatically. This replaces the manual tap-to-fire with a voice trigger, matching real-range competition etiquette where the shooter calls "Pull".

---

## 1. New Session Mode

**Store change (`shooterRemoteStore`):**  
Add `'rufausloesung'` as a valid `sessionMode` value alongside `'throwing'`, `'recording'`, `'delayed'`.

**Three new persisted settings** (stored in `localStorage`, same pattern as `delaySeconds`):

| Setting | Key | Range | Default | Meaning |
|---|---|---|---|---|
| `rufPeak` | `sg_ruf_peak` | 0–100 | 70 | Amplitude threshold (0 = silent, 100 = very loud) |
| `rufDauer` | `sg_ruf_dauer` | 50–500 ms | 120 ms | Minimum duration peak must be sustained |
| `rufTotzeit` | `sg_ruf_totzeit` | 0–8 s | 1 s | Dead time after arming before mic starts listening |

**Mode flyout:**  
Activate the existing disabled `mode-option--soon` entry for Rufauslösung (remove `disabled` attribute and `option-tag--soon`). Color: Cyan `#56C8D8` / `#7AD8E4` (already defined as `option-dot--rufausloesung`).

**Header button:**  
When `sessionMode === 'rufausloesung'`, render a mic button in `header-center` (left of the lock button, same position as `delay-btn`). Shows a mic icon + current `rufPeak` value. Opens the config modal on tap. Styled in Cyan, pulses when mic is actively listening.

**Mode badge:**  
Label: "Rufauslösung", Cyan tint. Pulsing dot when mic is actively listening (same `mode-dot-pulse` animation as Erfassen/Verzögert).

**CSS session token:**  
`.session--rufausloesung` — Cyan card borders + glow, analogous to `.session--verzoegert` (Amber).

---

## 2. Interaction Flow

### Arming

**Solo mode:**
1. User taps one position → status becomes `chip--waiting` ("Warten"), Cyan highlight.
2. Totzeit countdown starts (ring animation over the position button, Cyan).
3. After Totzeit: status becomes `chip--listening` ("Lauscht"), mic analysis loop starts.
4. While armed: all other position buttons are disabled (same pattern as `queuedIds` in delayed mode). Only the armed position stays tappable to cancel.

**Pair mode:**
1. User taps first position → `chip--pending` ("Gewählt").
2. User taps second position → both switch to `chip--waiting`. Totzeit starts.
3. Same listening flow as Solo, fires both positions simultaneously on trigger.

### Cancellation

Arming can be aborted at any point (Totzeit or Listening phase) by:
- Tapping the armed position button again
- Tapping the currently active Solo/Pair button in the bottom bar again
- Switching to a different session mode

On cancel: mic stops, all positions return to "Bereit".

### Firing

When `rufPeak` is exceeded for at least `rufDauer` milliseconds:
- Calls `fireSinglePosition` or `firePairPositions` (existing logic, unchanged)
- Mic stops, armed state resets automatically — no user action required
- Positions briefly show `chip--fired` / `chip--error` as usual, then return to "Bereit"

---

## 3. Config Modal

Opened via the mic header button. Styled in Cyan (border, slider accent, level bar). Same modal structure as the Verzögerung modal.

**Layout (top to bottom):**

1. **Header row:** Title "Rufauslösung", close button (×)
2. **Three sliders:**
   - *Empfindlichkeit (Peak)* — range 0–100, displays current value above slider
   - *Haltedauer* — range 50–500 ms, displays current value in ms
   - *Totzeit* — range 0–8 s, displays current value in s
3. **Live level bar:** Horizontal bar showing real-time mic amplitude (0–100). A vertical marker line shows the current `rufPeak` threshold. When peak + duration condition would be satisfied, the bar flashes Cyan-green ("Würde auslösen").
4. **Save button** (Cyan style)

**Mic permission flow:**
- Mic is requested when the modal opens for the first time (or when the mode is activated).
- If denied: error message in modal ("Mikrofon-Zugriff verweigert — bitte in den Browser-Einstellungen freigeben"). Mode remains selectable but fires nothing.

---

## 4. Technical Implementation

### Composable: `useVoiceTrigger`

New file: `src/composables/useVoiceTrigger.js`

**Exports:**

```js
const { startListening, stopListening, micLevel, wouldTrigger } = useVoiceTrigger(store)
```

| Export | Type | Description |
|---|---|---|
| `startListening(onTrigger)` | function | Starts Totzeit then mic analysis loop |
| `stopListening()` | function | Stops loop, releases mic stream |
| `micLevel` | `ref<number>` | Current mic amplitude 0–100 (for live bar in modal) |
| `wouldTrigger` | `ref<boolean>` | True when peak+dauer condition is currently met (for flash) |

**Audio pipeline:**
1. `navigator.mediaDevices.getUserMedia({ audio: true })` → `MediaStream`
2. `AudioContext` + `AnalyserNode` (FFT size 256)
3. `requestAnimationFrame` loop calling `getByteFrequencyData()` → RMS → normalize to 0–100
4. Compare to `store.rufPeak`; if exceeded start a duration timer; if sustained ≥ `store.rufDauer` ms → call `onTrigger()`
5. Peak drops before duration complete → reset duration timer (no false trigger)

**Totzeit:** `setTimeout(store.rufTotzeit * 1000)` before starting the loop.

**Cleanup:** `stopListening()` called:
- `onUnmounted` in `ShooterRemoteView`
- On `watch(() => store.sessionMode)` when leaving `'rufausloesung'`
- On cancel (position re-tap, mode button re-tap)

No open mic stream persists in the background.

### ShooterRemoteView changes

- Add `rufModalOpen` ref + `rufTotzeit` countdown state (parallel to `delayModalOpen` / `queuedIds`)
- Add `handleRufTap(position)` handler called from `handlePositionTap` when `sessionMode === 'rufausloesung'`
- Add `rufListeningIds` ref (positions currently armed and listening) for button state
- Add CSS `.session--rufausloesung` token block
- Add `chip--listening` status chip style (Cyan, pulsing)

### Store additions (`shooterRemoteStore`)

```js
const rufPeak    = ref(loadRufSetting('sg_ruf_peak',    70))
const rufDauer   = ref(loadRufSetting('sg_ruf_dauer',   120))
const rufTotzeit = ref(loadRufSetting('sg_ruf_totzeit', 1000))

const setRufPeak    = (v) => { rufPeak.value    = clamp(v, 0, 100);    persist('sg_ruf_peak',    rufPeak.value) }
const setRufDauer   = (v) => { rufDauer.value   = clamp(v, 50, 500);  persist('sg_ruf_dauer',   rufDauer.value) }
const setRufTotzeit = (v) => { rufTotzeit.value = clamp(v, 0, 8000);  persist('sg_ruf_totzeit', rufTotzeit.value) }
```

---

## 5. Files to Create / Modify

| File | Change |
|---|---|
| `src/composables/useVoiceTrigger.js` | **New** — mic analysis composable |
| `src/stores/shooterRemoteStore.js` | Add `rufPeak`, `rufDauer`, `rufTotzeit` state + setters |
| `src/views/shooter/ShooterRemoteView.vue` | Activate mode, add header button, modal, `handleRufTap`, CSS tokens |

No new views, no new routes, no new services — all changes are self-contained.

---

## 6. Out of Scope

- Backend integration (Rufauslösung triggers the same `sendPositionCommand` as all other modes)
- Waveform visualization (Ansatz B — rejected for complexity)
- Voice command recognition (speech-to-text) — this is amplitude-only detection
- Multi-mic or Bluetooth mic support
