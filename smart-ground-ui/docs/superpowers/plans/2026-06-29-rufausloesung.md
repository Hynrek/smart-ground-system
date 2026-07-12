# Rufauslösung Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a voice-triggered shooting mode ("Rufauslösung") to `ShooterRemoteView` where the shooter arms a position by tapping it, then fires it by shouting — detected via the Web Audio API.

**Architecture:** Three self-contained layers: (1) store additions for the three persisted settings (`rufPeak`, `rufDauer`, `rufTotzeit`), (2) a new `useVoiceTrigger` composable owning the entire Web Audio API lifecycle, (3) view integration in `ShooterRemoteView` wiring arming state, Totzeit countdown, and the trigger callback to the existing fire functions.

**Tech Stack:** Vue 3 Composition API, Pinia, Web Audio API (`getUserMedia` + `AnalyserNode`), Vitest + `@vue/test-utils`

---

## Files

| Action | Path | Responsibility |
|---|---|---|
| Create | `src/composables/useVoiceTrigger.js` | Web Audio API lifecycle: mic permission, analysis loop, peak+duration detection |
| Create | `src/composables/__tests__/useVoiceTrigger.test.js` | Unit tests for composable |
| Create | `src/stores/__tests__/shooterRemoteStore.test.js` | Unit tests for new store settings |
| Modify | `src/stores/shooterRemoteStore.js` | Add `rufPeak`, `rufDauer`, `rufTotzeit` state + setters |
| Modify | `src/views/shooter/ShooterRemoteView.vue` | Mode flyout, header button, config modal, arming logic, chip states, CSS |

---

## Task 1: Store — Ruf settings

**Files:**
- Modify: `src/stores/shooterRemoteStore.js`
- Create: `src/stores/__tests__/shooterRemoteStore.test.js`

- [ ] **Step 1: Write failing tests**

Create `src/stores/__tests__/shooterRemoteStore.test.js`:

```js
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useShooterRemoteStore } from '../shooterRemoteStore'

describe('useShooterRemoteStore — Ruf settings', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('has correct default values', () => {
    const store = useShooterRemoteStore()
    expect(store.rufPeak).toBe(70)
    expect(store.rufDauer).toBe(120)
    expect(store.rufTotzeit).toBe(1000)
  })

  it('setRufPeak clamps to 0–100', () => {
    const store = useShooterRemoteStore()
    store.setRufPeak(150)
    expect(store.rufPeak).toBe(100)
    store.setRufPeak(-10)
    expect(store.rufPeak).toBe(0)
    store.setRufPeak(60)
    expect(store.rufPeak).toBe(60)
  })

  it('setRufDauer clamps to 50–500', () => {
    const store = useShooterRemoteStore()
    store.setRufDauer(10)
    expect(store.rufDauer).toBe(50)
    store.setRufDauer(999)
    expect(store.rufDauer).toBe(500)
    store.setRufDauer(200)
    expect(store.rufDauer).toBe(200)
  })

  it('setRufTotzeit clamps to 0–8000', () => {
    const store = useShooterRemoteStore()
    store.setRufTotzeit(-100)
    expect(store.rufTotzeit).toBe(0)
    store.setRufTotzeit(99999)
    expect(store.rufTotzeit).toBe(8000)
    store.setRufTotzeit(2000)
    expect(store.rufTotzeit).toBe(2000)
  })

  it('setRufPeak persists to localStorage', () => {
    const store = useShooterRemoteStore()
    store.setRufPeak(55)
    expect(localStorage.getItem('sg_ruf_peak')).toBe('55')
  })

  it('setRufDauer persists to localStorage', () => {
    const store = useShooterRemoteStore()
    store.setRufDauer(300)
    expect(localStorage.getItem('sg_ruf_dauer')).toBe('300')
  })

  it('setRufTotzeit persists to localStorage', () => {
    const store = useShooterRemoteStore()
    store.setRufTotzeit(3000)
    expect(localStorage.getItem('sg_ruf_totzeit')).toBe('3000')
  })

  it('loads rufPeak from localStorage on init', () => {
    localStorage.setItem('sg_ruf_peak', '42')
    const store = useShooterRemoteStore()
    expect(store.rufPeak).toBe(42)
  })

  it('loads rufDauer from localStorage on init', () => {
    localStorage.setItem('sg_ruf_dauer', '250')
    const store = useShooterRemoteStore()
    expect(store.rufDauer).toBe(250)
  })

  it('loads rufTotzeit from localStorage on init', () => {
    localStorage.setItem('sg_ruf_totzeit', '5000')
    const store = useShooterRemoteStore()
    expect(store.rufTotzeit).toBe(5000)
  })
})
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
npm run test src/stores/__tests__/shooterRemoteStore.test.js
```

Expected: FAIL — `store.rufPeak is not a function` or similar.

- [ ] **Step 3: Add Ruf settings to store**

In `src/stores/shooterRemoteStore.js`, add after the existing `DELAY_*` constants block (before the `defineStore` call):

```js
// Rufauslösung settings — persisted mic trigger configuration.
const RUF_PEAK_KEY    = 'sg_ruf_peak';
const RUF_DAUER_KEY   = 'sg_ruf_dauer';
const RUF_TOTZEIT_KEY = 'sg_ruf_totzeit';

const clampRuf = (v, min, max, def) => {
  const n = Math.round(Number(v));
  if (Number.isNaN(n)) return def;
  return Math.min(max, Math.max(min, n));
};

const loadRuf = (key, min, max, def) => {
  try {
    const raw = localStorage.getItem(key);
    return raw == null ? def : clampRuf(raw, min, max, def);
  } catch {
    return def;
  }
};
```

Then inside `defineStore('shooterRemote', () => { ... })`, add after the `delaySeconds` block:

```js
// Rufauslösung settings
const rufPeak    = ref(loadRuf(RUF_PEAK_KEY,    0,  100,  70));
const rufDauer   = ref(loadRuf(RUF_DAUER_KEY,   50, 500,  120));
const rufTotzeit = ref(loadRuf(RUF_TOTZEIT_KEY, 0,  8000, 1000));

const persistRuf = (key, value) => {
  try { localStorage.setItem(key, String(value)); } catch { /* ignore */ }
};

const setRufPeak = (v) => {
  rufPeak.value = clampRuf(v, 0, 100, 70);
  persistRuf(RUF_PEAK_KEY, rufPeak.value);
};

const setRufDauer = (v) => {
  rufDauer.value = clampRuf(v, 50, 500, 120);
  persistRuf(RUF_DAUER_KEY, rufDauer.value);
};

const setRufTotzeit = (v) => {
  rufTotzeit.value = clampRuf(v, 0, 8000, 1000);
  persistRuf(RUF_TOTZEIT_KEY, rufTotzeit.value);
};
```

Also add `rufPeak`, `rufDauer`, `rufTotzeit`, `setRufPeak`, `setRufDauer`, `setRufTotzeit` to the `return {}` at the bottom of the store.

- [ ] **Step 4: Run tests — verify they pass**

```bash
npm run test src/stores/__tests__/shooterRemoteStore.test.js
```

Expected: all 10 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/stores/shooterRemoteStore.js src/stores/__tests__/shooterRemoteStore.test.js
git commit -m "[ui] shooterRemoteStore: add rufPeak/rufDauer/rufTotzeit settings"
```

---

## Task 2: `useVoiceTrigger` composable

**Files:**
- Create: `src/composables/useVoiceTrigger.js`
- Create: `src/composables/__tests__/useVoiceTrigger.test.js`

- [ ] **Step 1: Write failing tests**

Create `src/composables/__tests__/useVoiceTrigger.test.js`:

```js
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useVoiceTrigger } from '../useVoiceTrigger'

// --- Shared mock setup ---

let mockGetByteFrequencyData
let mockTrackStop
let mockCtxClose
let mockAnalyser
let mockSource
let mockCtx
let mockStream

const setupMocks = (frequencyData = new Uint8Array(128).fill(0)) => {
  mockGetByteFrequencyData = vi.fn().mockImplementation((arr) => arr.set(frequencyData))
  mockTrackStop = vi.fn()
  mockCtxClose = vi.fn()
  mockAnalyser = {
    fftSize: 256,
    frequencyBinCount: 128,
    connect: vi.fn(),
    getByteFrequencyData: mockGetByteFrequencyData,
  }
  mockSource = { connect: vi.fn() }
  mockCtx = {
    createMediaStreamSource: vi.fn().mockReturnValue(mockSource),
    createAnalyser: vi.fn().mockReturnValue(mockAnalyser),
    close: mockCtxClose,
  }
  mockStream = { getTracks: () => [{ stop: mockTrackStop }] }

  vi.stubGlobal('AudioContext', vi.fn().mockImplementation(() => mockCtx))
  vi.stubGlobal('navigator', {
    mediaDevices: {
      getUserMedia: vi.fn().mockResolvedValue(mockStream),
    },
  })

  // Replace requestAnimationFrame with a synchronous single-shot call
  vi.stubGlobal('requestAnimationFrame', vi.fn().mockImplementation((cb) => { cb(); return 1; }))
  vi.stubGlobal('cancelAnimationFrame', vi.fn())
}

describe('useVoiceTrigger', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it('startListening requests microphone access', async () => {
    setupMocks()
    const { startListening } = useVoiceTrigger({ rufPeak: 70, rufDauer: 120, rufTotzeit: 0 })
    await startListening(vi.fn())
    expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({ audio: true })
  })

  it('stopListening stops the mic track and closes the AudioContext', async () => {
    setupMocks()
    const { startListening, stopListening } = useVoiceTrigger({ rufPeak: 70, rufDauer: 120, rufTotzeit: 0 })
    await startListening(vi.fn())
    stopListening()
    expect(mockTrackStop).toHaveBeenCalled()
    expect(mockCtxClose).toHaveBeenCalled()
  })

  it('micLevel is 0 when analyser returns silence', async () => {
    setupMocks(new Uint8Array(128).fill(0))
    const { startListening, micLevel } = useVoiceTrigger({ rufPeak: 70, rufDauer: 120, rufTotzeit: 0 })
    await startListening(vi.fn())
    expect(micLevel.value).toBe(0)
  })

  it('micLevel reflects analyser amplitude', async () => {
    // Fill with 128 → RMS = 128 → normalized = 100
    setupMocks(new Uint8Array(128).fill(128))
    const { startListening, micLevel } = useVoiceTrigger({ rufPeak: 70, rufDauer: 120, rufTotzeit: 0 })
    await startListening(vi.fn())
    expect(micLevel.value).toBeGreaterThan(0)
  })

  it('does NOT call onTrigger when peak is not sustained long enough', async () => {
    // Level will exceed peak threshold
    setupMocks(new Uint8Array(128).fill(200))
    const onTrigger = vi.fn()
    const { startListening } = useVoiceTrigger({ rufPeak: 10, rufDauer: 500, rufTotzeit: 0 })
    await startListening(onTrigger)
    // Advance time less than rufDauer
    vi.advanceTimersByTime(200)
    expect(onTrigger).not.toHaveBeenCalled()
  })

  it('calls onTrigger when peak is sustained for rufDauer', async () => {
    setupMocks(new Uint8Array(128).fill(200))
    const onTrigger = vi.fn()
    const { startListening } = useVoiceTrigger({ rufPeak: 10, rufDauer: 50, rufTotzeit: 0 })
    await startListening(onTrigger)
    vi.advanceTimersByTime(100)
    // RAF loop is synchronous in tests, so trigger should have been called
    expect(onTrigger).toHaveBeenCalledTimes(1)
  })

  it('does not call onTrigger before Totzeit has elapsed', async () => {
    setupMocks(new Uint8Array(128).fill(200))
    const onTrigger = vi.fn()
    const { startListening } = useVoiceTrigger({ rufPeak: 10, rufDauer: 50, rufTotzeit: 2000 })
    startListening(onTrigger) // do not await — Totzeit hasn't elapsed
    vi.advanceTimersByTime(500)
    expect(onTrigger).not.toHaveBeenCalled()
    expect(navigator.mediaDevices.getUserMedia).not.toHaveBeenCalled()
  })

  it('sets micDenied when getUserMedia is rejected', async () => {
    setupMocks()
    navigator.mediaDevices.getUserMedia = vi.fn().mockRejectedValue(new Error('NotAllowedError'))
    const { startListening, micDenied } = useVoiceTrigger({ rufPeak: 70, rufDauer: 120, rufTotzeit: 0 })
    await startListening(vi.fn())
    expect(micDenied.value).toBe(true)
  })
})
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
npm run test src/composables/__tests__/useVoiceTrigger.test.js
```

Expected: FAIL — `useVoiceTrigger is not a function` or module not found.

- [ ] **Step 3: Implement `useVoiceTrigger`**

Create `src/composables/useVoiceTrigger.js`:

```js
import { ref } from 'vue'

export function useVoiceTrigger(settings) {
  const micLevel   = ref(0)
  const wouldTrigger = ref(false)
  const micDenied  = ref(false)

  let stream       = null
  let audioCtx     = null
  let analyser     = null
  let rafHandle    = null
  let peakStart    = null
  let triggered    = false
  let totzeitTimer = null

  const stopListening = () => {
    if (totzeitTimer) { clearTimeout(totzeitTimer); totzeitTimer = null; }
    if (rafHandle)    { cancelAnimationFrame(rafHandle); rafHandle = null; }
    if (stream)       { stream.getTracks().forEach((t) => t.stop()); stream = null; }
    if (audioCtx)     { audioCtx.close(); audioCtx = null; }
    analyser      = null
    peakStart     = null
    triggered     = false
    micLevel.value    = 0
    wouldTrigger.value = false
  }

  const startAnalysis = (onTrigger) => {
    const dataArray = new Uint8Array(analyser.frequencyBinCount)

    const tick = () => {
      if (!analyser) return

      analyser.getByteFrequencyData(dataArray)
      const sumSq = dataArray.reduce((s, v) => s + v * v, 0)
      const rms   = Math.sqrt(sumSq / dataArray.length)
      const level = Math.min(100, Math.round((rms / 128) * 100))
      micLevel.value = level

      const threshold = typeof settings === 'object' && 'rufPeak' in settings
        ? settings.rufPeak
        : (settings?.value?.rufPeak ?? 70)
      const dauer = typeof settings === 'object' && 'rufDauer' in settings
        ? settings.rufDauer
        : (settings?.value?.rufDauer ?? 120)

      if (level >= threshold) {
        if (!peakStart) peakStart = Date.now()
        const held = Date.now() - peakStart
        wouldTrigger.value = held >= dauer
        if (!triggered && held >= dauer) {
          triggered = true
          wouldTrigger.value = true
          stopListening()
          onTrigger()
          return
        }
      } else {
        peakStart = null
        wouldTrigger.value = false
      }

      rafHandle = requestAnimationFrame(tick)
    }

    rafHandle = requestAnimationFrame(tick)
  }

  const startListening = async (onTrigger) => {
    const totzeit = typeof settings === 'object' && 'rufTotzeit' in settings
      ? settings.rufTotzeit
      : (settings?.value?.rufTotzeit ?? 1000)

    const begin = async () => {
      micDenied.value = false
      try {
        stream    = await navigator.mediaDevices.getUserMedia({ audio: true })
        audioCtx  = new AudioContext()
        analyser  = audioCtx.createAnalyser()
        analyser.fftSize = 256
        const source = audioCtx.createMediaStreamSource(stream)
        source.connect(analyser)
        startAnalysis(onTrigger)
      } catch {
        micDenied.value = true
      }
    }

    if (totzeit > 0) {
      totzeitTimer = setTimeout(begin, totzeit)
    } else {
      await begin()
    }
  }

  return { startListening, stopListening, micLevel, wouldTrigger, micDenied }
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
npm run test src/composables/__tests__/useVoiceTrigger.test.js
```

Expected: all 8 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/composables/useVoiceTrigger.js src/composables/__tests__/useVoiceTrigger.test.js
git commit -m "[ui] add useVoiceTrigger composable (Web Audio API mic trigger)"
```

---

## Task 3: Activate mode in mode flyout + badge + CSS token

**Files:**
- Modify: `src/views/shooter/ShooterRemoteView.vue`

This task only touches the mode flyout list, `modeBadgeLabel`/`modeBadgeClass` computed props, and the CSS session color token. No functional logic yet.

- [ ] **Step 1: Activate Rufauslösung in mode flyout**

In `ShooterRemoteView.vue`, find the disabled Rufauslösung `<button>`:

```html
<button class="mode-option mode-option--soon" disabled>
  <span class="option-dot option-dot--rufausloesung" />
  <span class="option-name">Rufauslösung</span>
  <span class="option-tag option-tag--soon">Demnächst</span>
</button>
```

Replace with:

```html
<button
  class="mode-option"
  :class="{ 'is-active': store.sessionMode === 'rufausloesung' }"
  @click="setSessionMode('rufausloesung')"
>
  <span class="option-dot option-dot--rufausloesung" />
  <span class="option-name">Rufauslösung</span>
  <span v-if="store.sessionMode === 'rufausloesung'" class="option-tag option-tag--rufausloesung">Aktiv</span>
</button>
```

- [ ] **Step 2: Update modeBadgeLabel and modeBadgeClass**

Find the `modeBadgeLabel` computed:

```js
const modeBadgeLabel = computed(() => {
  if (store.sessionMode === 'recording') return 'Erfassen';
  if (store.sessionMode === 'delayed') return 'Verzögert';
  return 'Schiessen';
})
```

Replace with:

```js
const modeBadgeLabel = computed(() => {
  if (store.sessionMode === 'recording')     return 'Erfassen';
  if (store.sessionMode === 'delayed')       return 'Verzögert';
  if (store.sessionMode === 'rufausloesung') return 'Rufauslösung';
  return 'Schiessen';
})
```

Find the `modeBadgeClass` computed:

```js
const modeBadgeClass = computed(() => ({
  'mode-badge--recording': store.sessionMode === 'recording',
  'mode-badge--delayed': store.sessionMode === 'delayed',
}))
```

Replace with:

```js
const modeBadgeClass = computed(() => ({
  'mode-badge--recording':     store.sessionMode === 'recording',
  'mode-badge--delayed':       store.sessionMode === 'delayed',
  'mode-badge--rufausloesung': store.sessionMode === 'rufausloesung',
}))
```

- [ ] **Step 3: Add CSS for mode badge, flyout tag, session token, and chip**

In the `<style scoped>` section, add after the `.session--verzoegert` block:

```css
/* Rufauslösung identity colour (cyan) */
.shooter-remote { --ruf-color: #56C8D8; --ruf-text: #7AD8E4; }

.session--rufausloesung .device-btn:not(:disabled) {
  border-color: rgba(86, 200, 216, 0.75);
  box-shadow: 0 0 12px rgba(86, 200, 216, 0.32), inset 0 0 0 1px rgba(86, 200, 216, 0.2);
}
```

After the `.mode-badge--delayed` block:

```css
.mode-badge-btn.mode-badge--rufausloesung {
  border-color: rgba(86, 200, 216, 0.45);
  background: rgba(86, 200, 216, 0.12);
  color: var(--ruf-text);
}

.mode-badge--rufausloesung .mode-dot {
  background: var(--ruf-color);
  animation: mode-dot-pulse 1s ease-in-out infinite;
}
```

After the `.option-tag--verzoegert` block:

```css
.option-tag--rufausloesung {
  background: rgba(86, 200, 216, 0.15);
  color: var(--ruf-text);
}

.option-dot--rufausloesung { background: var(--ruf-color); }
```

Also add the `chip--listening` chip style after `.chip--waiting`:

```css
.chip--listening {
  background: rgba(86, 200, 216, 0.18);
  color: var(--ruf-text);
  animation: mode-dot-pulse 1s ease-in-out infinite;
}
```

And the `session--rufausloesung` class binding on the root div. Find:

```html
:class="[
  `mode--${store.mode}`,
  {
    'session--erfassen': store.sessionMode === 'recording',
    'session--verzoegert': store.sessionMode === 'delayed',
  },
]"
```

Replace with:

```html
:class="[
  `mode--${store.mode}`,
  {
    'session--erfassen':      store.sessionMode === 'recording',
    'session--verzoegert':    store.sessionMode === 'delayed',
    'session--rufausloesung': store.sessionMode === 'rufausloesung',
  },
]"
```

- [ ] **Step 4: Verify in browser — mode flyout now shows Rufauslösung as selectable**

Start the dev server (`npm run dev`) and open `http://localhost:5173`. Log in as `user@smartground.local` / `user`, navigate to a range. Open the mode flyout — confirm "Rufauslösung" is selectable, activates the cyan mode badge, and card borders turn cyan.

- [ ] **Step 5: Commit**

```bash
git add src/views/shooter/ShooterRemoteView.vue
git commit -m "[ui] ShooterRemoteView: activate Rufauslösung mode (flyout, badge, CSS)"
```

---

## Task 4: Header mic button + config modal

**Files:**
- Modify: `src/views/shooter/ShooterRemoteView.vue`

- [ ] **Step 1: Add imports and modal state**

In `<script setup>`, add after the existing `delayModalOpen` / `draftDelay` declarations:

```js
// Rufauslösung: config modal state
const rufModalOpen   = ref(false);
const draftRufPeak   = ref(store.rufPeak);
const draftRufDauer  = ref(store.rufDauer);
const draftRufTotzeit = ref(store.rufTotzeit / 1000); // display in seconds

watch(rufModalOpen, (open) => {
  if (open) {
    draftRufPeak.value    = store.rufPeak;
    draftRufDauer.value   = store.rufDauer;
    draftRufTotzeit.value = store.rufTotzeit / 1000;
  }
});

const saveRuf = () => {
  store.setRufPeak(draftRufPeak.value);
  store.setRufDauer(draftRufDauer.value);
  store.setRufTotzeit(Math.round(draftRufTotzeit.value * 1000));
  rufModalOpen.value = false;
};
```

Also add the `useVoiceTrigger` import at the top of `<script setup>`:

```js
import { useVoiceTrigger } from '@/composables/useVoiceTrigger.js';
```

And instantiate it (we need `micLevel` and `wouldTrigger` for the live bar in the modal — wiring to fire logic comes in Task 5):

```js
const { micLevel, wouldTrigger, micDenied } = useVoiceTrigger(store);
```

- [ ] **Step 2: Add mic button to header-center**

In the template, inside `<div class="header-center">`, add the mic button before the lock button (it will mirror the `delay-btn` pattern). Add it conditional on `rufausloesung` mode:

```html
<!-- Rufauslösung: mic-config button -->
<button
  v-if="store.sessionMode === 'rufausloesung'"
  class="ruf-btn"
  title="Empfindlichkeit einstellen"
  @click="rufModalOpen = true"
>
  <Icons icon="mic" :size="14" />
  <span>{{ store.rufPeak }}</span>
</button>
```

- [ ] **Step 3: Add the config modal template**

Add after the delay modal `</Transition>` block:

```html
<!-- Rufauslösung: config modal -->
<Transition name="delay-modal">
  <div v-if="rufModalOpen" class="delay-modal-backdrop" @click.self="rufModalOpen = false">
    <div class="ruf-modal" role="dialog" aria-modal="true" aria-labelledby="ruf-modal-title">
      <div class="delay-modal-head">
        <h2 id="ruf-modal-title" class="delay-modal-title">Rufauslösung</h2>
        <button class="delay-modal-close" title="Schliessen" @click="rufModalOpen = false">
          <Icons icon="x" :size="14" />
        </button>
      </div>

      <!-- Empfindlichkeit (Peak) -->
      <div class="ruf-slider-group">
        <div class="ruf-slider-header">
          <span class="ruf-slider-label">Empfindlichkeit</span>
          <span class="ruf-slider-value">{{ draftRufPeak }}</span>
        </div>
        <input
          v-model.number="draftRufPeak"
          class="ruf-slider"
          type="range"
          min="0"
          max="100"
          step="1"
          aria-label="Empfindlichkeit (Peak)"
        />
        <div class="ruf-slider-scale"><span>Leise</span><span>Laut</span></div>
      </div>

      <!-- Haltedauer -->
      <div class="ruf-slider-group">
        <div class="ruf-slider-header">
          <span class="ruf-slider-label">Haltedauer</span>
          <span class="ruf-slider-value">{{ draftRufDauer }} ms</span>
        </div>
        <input
          v-model.number="draftRufDauer"
          class="ruf-slider"
          type="range"
          min="50"
          max="500"
          step="10"
          aria-label="Haltedauer in Millisekunden"
        />
        <div class="ruf-slider-scale"><span>50 ms</span><span>500 ms</span></div>
      </div>

      <!-- Totzeit -->
      <div class="ruf-slider-group">
        <div class="ruf-slider-header">
          <span class="ruf-slider-label">Totzeit</span>
          <span class="ruf-slider-value">{{ draftRufTotzeit.toFixed(1) }} s</span>
        </div>
        <input
          v-model.number="draftRufTotzeit"
          class="ruf-slider"
          type="range"
          min="0"
          max="8"
          step="0.5"
          aria-label="Totzeit in Sekunden"
        />
        <div class="ruf-slider-scale"><span>0 s</span><span>8 s</span></div>
      </div>

      <!-- Live level bar -->
      <div class="ruf-level-wrap" aria-label="Mikrofon-Pegel">
        <div v-if="micDenied" class="ruf-denied">
          Mikrofon-Zugriff verweigert — bitte in den Browser-Einstellungen freigeben.
        </div>
        <template v-else>
          <div class="ruf-level-bar-track">
            <div
              class="ruf-level-bar-fill"
              :class="{ 'ruf-level--trigger': wouldTrigger }"
              :style="{ width: `${micLevel}%` }"
            />
            <div class="ruf-level-threshold" :style="{ left: `${draftRufPeak}%` }" />
          </div>
          <div class="ruf-level-hint">{{ wouldTrigger ? 'Würde auslösen' : 'Kein Auslösen' }}</div>
        </template>
      </div>

      <button class="ruf-save-btn" @click="saveRuf">Speichern</button>
    </div>
  </div>
</Transition>
```

- [ ] **Step 4: Add CSS for mic button and config modal**

In `<style scoped>`, add after the `.delay-save-btn` block:

```css
/* Rufauslösung — mic header button */
.ruf-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0 10px;
  height: 36px;
  border-radius: 10px;
  font-family: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
  background: rgba(86, 200, 216, 0.10);
  border: 1.5px solid rgba(86, 200, 216, 0.35);
  color: var(--ruf-text);
}

.ruf-btn:hover  { background: rgba(86, 200, 216, 0.16); }
.ruf-btn:active { transform: scale(0.95); }

/* Rufauslösung config modal */
.ruf-modal {
  width: min(100%, 360px);
  background: rgba(20, 20, 30, 0.98);
  border: 1px solid rgba(86, 200, 216, 0.3);
  border-radius: 20px;
  padding: 20px;
  box-shadow: var(--sg-shadow-md);
}

.ruf-slider-group {
  margin-bottom: 18px;
}

.ruf-slider-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.ruf-slider-label {
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.7);
}

.ruf-slider-value {
  font-size: 13px;
  font-weight: 700;
  color: var(--ruf-text);
  font-variant-numeric: tabular-nums;
}

.ruf-slider {
  width: 100%;
  accent-color: var(--ruf-color);
  cursor: pointer;
}

.ruf-slider-scale {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.3);
  margin-top: 2px;
}

/* Live level bar */
.ruf-level-wrap {
  margin-bottom: 18px;
}

.ruf-level-bar-track {
  position: relative;
  height: 10px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  overflow: visible;
}

.ruf-level-bar-fill {
  height: 100%;
  background: var(--ruf-color);
  border-radius: 6px;
  transition: width 0.05s linear;
}

.ruf-level-bar-fill.ruf-level--trigger {
  background: #48BB78;
  animation: mode-dot-pulse 0.4s ease-out;
}

.ruf-level-threshold {
  position: absolute;
  top: -3px;
  bottom: -3px;
  width: 2px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 2px;
  transform: translateX(-50%);
}

.ruf-level-hint {
  margin-top: 5px;
  font-size: 11px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.35);
  text-align: center;
}

.ruf-denied {
  font-size: 12px;
  color: #fc8181;
  text-align: center;
  padding: 10px 0;
}

.ruf-save-btn {
  width: 100%;
  padding: 13px 0;
  border-radius: 12px;
  background: rgba(86, 200, 216, 0.18);
  border: 1.5px solid rgba(86, 200, 216, 0.45);
  color: var(--ruf-text);
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
}

.ruf-save-btn:hover  { background: rgba(86, 200, 216, 0.26); }
.ruf-save-btn:active { transform: scale(0.97); }
```

- [ ] **Step 5: Check that mic icon exists**

The existing `Icons.vue` must have a `mic` icon. Run:

```bash
grep -n "mic" src/components/Icons.vue
```

If `mic` is not defined, add it to the `Icons.vue` icon map. Find the section where other icons are defined (e.g. `clock`, `alert`) and add:

```js
mic: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="23"/><line x1="8" y1="23" x2="16" y2="23"/></svg>`,
```

- [ ] **Step 6: Verify in browser**

With `npm run dev` running, navigate to a range, switch to Rufauslösung mode. Confirm:
- Cyan mic button appears in the header
- Tapping it opens the config modal
- All three sliders are present and functional
- Live level bar is visible (may require mic permission)
- Saving closes the modal

- [ ] **Step 7: Commit**

```bash
git add src/views/shooter/ShooterRemoteView.vue src/components/Icons.vue
git commit -m "[ui] ShooterRemoteView: Rufauslösung mic button and config modal"
```

---

## Task 5: Arming logic (`handleRufTap`) + chip states + Totzeit ring

**Files:**
- Modify: `src/views/shooter/ShooterRemoteView.vue`

- [ ] **Step 1: Add arming state refs**

In `<script setup>`, after the `useVoiceTrigger` instantiation, add:

```js
// Rufauslösung: arming state
const rufArmedIds  = ref([]);   // position ids currently armed
const rufPhase     = ref(null); // 'totzeit' | 'listening' | null

// Totzeit countdown (for the ring animation — mirrors the delay countdown)
const rufTotzeitTotalMs     = ref(0);
const rufTotzeitRemainingMs = ref(0);
let rufTotzeitInterval = null;
let rufTotzeitTimeout  = null;

const clearRufTimers = () => {
  if (rufTotzeitInterval) { clearInterval(rufTotzeitInterval); rufTotzeitInterval = null; }
  if (rufTotzeitTimeout)  { clearTimeout(rufTotzeitTimeout);  rufTotzeitTimeout  = null; }
};

const cancelRuf = () => {
  clearRufTimers();
  stopListening();
  rufArmedIds.value = [];
  rufPhase.value    = null;
  rufTotzeitRemainingMs.value = 0;
  rufTotzeitTotalMs.value     = 0;
};
```

Also destructure `startListening` and `stopListening` from the `useVoiceTrigger` call (update the existing line):

```js
const { startListening, stopListening, micLevel, wouldTrigger, micDenied } = useVoiceTrigger(store);
```

- [ ] **Step 2: Implement `handleRufTap`**

Add after `cancelRuf`:

```js
const handleRufTap = (position) => {
  // If already armed: re-tapping aborts
  if (rufArmedIds.value.includes(position.id)) {
    cancelRuf();
    return;
  }

  if (store.mode === 'pair') {
    if (rufArmedIds.value.length === 0) {
      // First of the pair — mark as pending
      rufArmedIds.value = [position.id];
      rufPhase.value    = 'waiting-pair';
      return;
    }
    // Second of the pair — arm both
    const ids = [...rufArmedIds.value, position.id];
    armPositions(ids);
    return;
  }

  // Solo
  armPositions([position.id]);
};

const armPositions = (ids) => {
  rufArmedIds.value = ids;
  const totzeitMs   = store.rufTotzeit;

  if (totzeitMs > 0) {
    rufPhase.value              = 'totzeit';
    rufTotzeitTotalMs.value     = totzeitMs;
    rufTotzeitRemainingMs.value = totzeitMs;
    const startedAt = Date.now();

    rufTotzeitInterval = setInterval(() => {
      rufTotzeitRemainingMs.value = Math.max(0, totzeitMs - (Date.now() - startedAt));
    }, 50);

    rufTotzeitTimeout = setTimeout(() => {
      clearRufTimers();
      rufTotzeitRemainingMs.value = 0;
      rufTotzeitTotalMs.value     = 0;
      beginListening(ids);
    }, totzeitMs);
  } else {
    beginListening(ids);
  }
};

const beginListening = (ids) => {
  rufPhase.value = 'listening';
  startListening(() => {
    if (ids.length === 1) {
      fireSinglePosition(ids[0]);
    } else {
      firePairPositions(ids[0], ids[1]);
    }
    rufArmedIds.value = [];
    rufPhase.value    = null;
  });
};
```

- [ ] **Step 3: Wire `handleRufTap` into `handlePositionTap`**

Find `handlePositionTap` and add the Rufauslösung branch at the top, after the `isPositionDisabled` guard:

```js
const handlePositionTap = async (position) => {
  if (isPositionDisabled(position)) return;

  if (store.sessionMode === 'rufausloesung') {
    handleRufTap(position);
    return;
  }

  // ... rest unchanged
```

- [ ] **Step 4: Cancel Ruf on mode switch or lock**

Add to the existing `watch(() => store.sessionMode, ...)` handler (currently calls `cancelQueue`):

```js
watch(() => store.sessionMode, () => {
  store.setMode('solo');
  modeDrawerOpen.value = false;
  cancelQueue();
  cancelRuf();
});
```

Also add to the `watch(isLocked, ...)` handler:

```js
watch(isLocked, (locked) => {
  if (locked) {
    cancelQueue();
    cancelRuf();
  }
});
```

And in `onUnmounted`:

```js
onUnmounted(() => {
  store.releasePlatz();
  store.setCompetitionContext(null, null);
  cancelQueue();
  cancelRuf();
});
```

- [ ] **Step 5: Cancel on Solo/Pair button re-tap**

The bottom bar buttons call `store.setMode(...)`. Wrap these to also cancel when the active mode is re-tapped:

Find the Solo button in the template:

```html
<button
  class="toggle-btn"
  :class="{ active: store.mode === 'solo' }"
  @click="store.setMode('solo')"
>
```

Replace with:

```html
<button
  class="toggle-btn"
  :class="{ active: store.mode === 'solo' }"
  @click="onModeButtonTap('solo')"
>
```

And the Pair button:

```html
<button
  class="toggle-btn"
  :class="{ active: store.mode === 'pair' }"
  @click="onModeButtonTap('pair')"
>
```

Add the handler in `<script setup>`:

```js
const onModeButtonTap = (newMode) => {
  if (store.sessionMode === 'rufausloesung' && rufArmedIds.value.length > 0) {
    cancelRuf();
  }
  store.setMode(newMode);
};
```

- [ ] **Step 6: Update chip helpers for Ruf states**

Update `chipClass`:

```js
const chipClass = (position) => {
  if (!position.device) return 'chip--no-device';
  if (position.device.blocked || isLocked.value) return 'chip--blocked';
  if (!store.reservedByMe) return 'chip--free';
  if (isQueued(position)) return 'chip--queued';
  if (queuedIds.value.length) return 'chip--waiting';
  // Rufauslösung
  if (rufArmedIds.value.includes(position.id) && rufPhase.value === 'listening') return 'chip--listening';
  if (rufArmedIds.value.includes(position.id) && rufPhase.value === 'waiting-pair') return 'chip--pending';
  if (rufArmedIds.value.includes(position.id)) return 'chip--waiting';
  if (rufArmedIds.value.length > 0 && !rufArmedIds.value.includes(position.id)) return 'chip--waiting';
  if (errorIds.value.has(position.id)) return 'chip--error';
  if (firedIds.value.has(position.id)) return 'chip--fired';
  if (passeStore.recording[position.id]) return 'chip--recording';
  if (isThrowPairPending(position)) return 'chip--pending';
  if (passeStore.passeMode && passeStore.pairPending?.id === position.id) return 'chip--pending';
  return 'chip--ready';
};
```

Update `chipLabel`:

```js
const chipLabel = (position) => {
  if (!position.device) return 'Kein Gerät';
  if (position.device.blocked) return 'Gesperrt';
  if (isLocked.value) return 'Notfall';
  if (!store.reservedByMe) return 'Frei';
  if (isQueued(position)) return 'Abbrechen';
  if (queuedIds.value.length) return 'Warten';
  // Rufauslösung
  if (rufArmedIds.value.includes(position.id) && rufPhase.value === 'listening') return 'Lauscht';
  if (rufArmedIds.value.includes(position.id) && rufPhase.value === 'waiting-pair') return 'Gewählt';
  if (rufArmedIds.value.includes(position.id)) return 'Warten';
  if (rufArmedIds.value.length > 0 && !rufArmedIds.value.includes(position.id)) return 'Warten';
  if (errorIds.value.has(position.id)) return 'Fehler';
  if (firedIds.value.has(position.id)) return 'Ausgelöst';
  if (passeStore.recording[position.id]) return 'Erfasst';
  if (isThrowPairPending(position)) return 'Gewählt';
  if (passeStore.passeMode && passeStore.pairPending?.id === position.id) return 'Gewählt';
  return 'Bereit';
};
```

Update `isPositionDisabled` — while armed, lock all non-armed buttons (armed buttons stay tappable for cancel):

```js
const isPositionDisabled = (position) => {
  if (!position.device) return true;
  if (position.device.blocked || position.device.healthy === false) return true;
  if (isLocked.value) return true;
  if (!store.reservedByMe) return true;
  if (queuedIds.value.length) return !isQueued(position);
  // Rufauslösung: while armed, lock non-armed positions
  if (rufArmedIds.value.length > 0 && rufPhase.value !== 'waiting-pair') {
    return !rufArmedIds.value.includes(position.id);
  }
  if (firingIds.value.has(position.id)) return true;
  return false;
};
```

Update `positionBtnClass` — add queued-style class for armed positions:

```js
const positionBtnClass = (position) => ({
  'device-btn--firing':      firingIds.value.has(position.id),
  'device-btn--fired':       firedIds.value.has(position.id),
  'device-btn--error':       errorIds.value.has(position.id),
  'device-btn--blocked':     (position.device?.blocked ?? false) || isLocked.value,
  'device-btn--no-device':   !position.device,
  'device-btn--recording':   !!passeStore.recording[position.id],
  'device-btn--pair-pending':
    isThrowPairPending(position) ||
    (passeStore.passeMode && passeStore.pairPending?.id === position.id),
  'device-btn--queued': isQueued(position),
  'device-btn--ruf-armed':  rufArmedIds.value.includes(position.id),
  'device-btn--inactive':    !store.reservedByMe && !isLocked.value,
});
```

Add CSS for `device-btn--ruf-armed` after `.device-btn--queued`:

```css
.device-btn--ruf-armed {
  background: rgba(86, 200, 216, 0.12) !important;
  border-color: rgba(86, 200, 216, 0.6) !important;
  opacity: 1 !important;
}
```

- [ ] **Step 7: Add Totzeit countdown ring in template for Ruf-armed positions**

Find the existing countdown ring block inside the device button:

```html
<div v-if="isQueued(position)" class="btn-countdown-ring" :style="countdownRingStyle" />
<span v-if="isQueued(position)" class="btn-countdown-num">{{ countdownLabel }}</span>
<span v-else class="btn-letter" :style="{ color: iconColor(position) }">
  {{ position.label }}
</span>
```

Replace with:

```html
<div v-if="isQueued(position)" class="btn-countdown-ring" :style="countdownRingStyle" />
<span v-if="isQueued(position)" class="btn-countdown-num">{{ countdownLabel }}</span>
<div
  v-else-if="rufArmedIds.includes(position.id) && rufPhase === 'totzeit'"
  class="btn-countdown-ring btn-countdown-ring--ruf"
  :style="rufCountdownRingStyle"
/>
<span
  v-else-if="rufArmedIds.includes(position.id) && rufPhase === 'totzeit'"
  class="btn-countdown-num btn-countdown-num--ruf"
>{{ rufCountdownLabel }}</span>
<span v-else class="btn-letter" :style="{ color: iconColor(position) }">
  {{ position.label }}
</span>
```

Add the computed values in `<script setup>`:

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

Add CSS for the cyan countdown ring variant:

```css
.btn-countdown-num--ruf { color: var(--ruf-text); }
```

(The ring itself reuses `.btn-countdown-ring` styles — only the color changes via the inline conic-gradient.)

- [ ] **Step 8: Verify in browser**

Navigate to a range in Rufauslösung mode:
- Tap a position → it shows "Warten" chip (cyan), Totzeit ring drains down
- After Totzeit → chip shows "Lauscht" (pulsing cyan)
- Shout → position fires, resets to "Bereit"
- Tap armed position again during any phase → cancels
- Tap Solo button during armed → cancels
- Switch mode → cancels

- [ ] **Step 9: Commit**

```bash
git add src/views/shooter/ShooterRemoteView.vue
git commit -m "[ui] ShooterRemoteView: Rufauslösung arming logic, Totzeit ring, chip states"
```

---

## Task 6: Wire mic to config modal — live level bar

**Files:**
- Modify: `src/views/shooter/ShooterRemoteView.vue`

The `micLevel`, `wouldTrigger`, and `micDenied` refs from `useVoiceTrigger` are already wired to the modal template in Task 4. The composable's `startListening` / `stopListening` already start/stop the mic during arming/firing in Task 5. However, **the config modal also needs a live mic feed** while it's open so the user can test sensitivity — even when not armed.

- [ ] **Step 1: Start/stop mic for the config modal preview**

The composable can only run one session at a time. While the modal is open (and nothing is armed), we start a "preview" listen that never fires anything. We stop it when the modal closes.

Add a `startListening` / `stopListening` call in the `watch(rufModalOpen, ...)` already created in Task 4. Replace it with:

```js
watch(rufModalOpen, async (open) => {
  if (open) {
    draftRufPeak.value    = store.rufPeak;
    draftRufDauer.value   = store.rufDauer;
    draftRufTotzeit.value = store.rufTotzeit / 1000;
    // Start a no-op listener so the level bar is live; Totzeit = 0 for instant feedback
    await startListening(() => { /* preview only — no fire */ }, { totzeit: 0 });
  } else {
    stopListening();
  }
});
```

The `useVoiceTrigger` composable needs to accept an optional override object for `totzeit` in the preview case. Update the `startListening` signature in `src/composables/useVoiceTrigger.js`:

```js
// Add optional overrides parameter
const startListening = async (onTrigger, overrides = {}) => {
  const totzeit = overrides.totzeit !== undefined
    ? overrides.totzeit
    : (typeof settings === 'object' && 'rufTotzeit' in settings
        ? settings.rufTotzeit
        : (settings?.value?.rufTotzeit ?? 1000))
  // ... rest unchanged
```

Also make `wouldTrigger` in the preview context use `draftRufPeak` instead of `store.rufPeak`, so the threshold marker updates live as the user drags the slider. Update the level bar template to pass the draft value:

```html
<div class="ruf-level-threshold" :style="{ left: `${draftRufPeak}%` }" />
```

This already uses `draftRufPeak` (from Task 4 template) — no further change needed.

- [ ] **Step 2: Run all tests**

```bash
npm run test
```

Expected: all tests PASS. Check that modifying `useVoiceTrigger` didn't break the composable tests.

- [ ] **Step 3: Verify config modal live bar in browser**

Open the config modal in Rufauslösung mode. Grant mic permission. Confirm:
- Level bar moves in real time as you make sounds
- Threshold marker moves as you drag the Peak slider
- "Würde auslösen" appears when you shout above the threshold for the configured duration

- [ ] **Step 4: Commit**

```bash
git add src/views/shooter/ShooterRemoteView.vue src/composables/useVoiceTrigger.js
git commit -m "[ui] Rufauslösung: live mic preview in config modal"
```

---

## Task 7: Final lint + test pass

- [ ] **Step 1: Run linter**

```bash
npm run lint
```

Fix any reported issues.

- [ ] **Step 2: Run full test suite**

```bash
npm run test
```

Expected: all tests PASS.

- [ ] **Step 3: Build check**

```bash
npm run build
```

Expected: build succeeds with no errors or warnings.

- [ ] **Step 4: Final commit if lint fixes were needed**

```bash
git add -A
git commit -m "[ui] Rufauslösung: lint fixes"
```
