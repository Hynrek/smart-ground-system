# Nächster Schütze Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a fullscreen "Nächster Schütze: ..." overlay between players in a multi-player session so the handover is unambiguous.

**Architecture:** All changes are self-contained in `ShooterPlayPage.vue`. A new boolean ref (`showNextShooterOverlay`) gates the overlay. `handlePlayerComplete` shows the overlay instead of immediately advancing when there is a next player; the overlay's "Starten" button actually advances. Tests go in a new file that follows the pattern of `ShooterPlayPageAudit.test.js`.

**Tech Stack:** Vue 3 Composition API (`<script setup>`), Pinia (`usePlaySessionStore`), Vitest + `@vue/test-utils`

---

### Task 1: Write failing tests for the overlay

**Files:**
- Create: `src/components/__tests__/ShooterPlayPageNextShooter.test.js`

The tests verify three behaviours:
1. Overlay is hidden by default during active play.
2. Tapping the "Getroffen / Fertig" card in multi-player mode (when `store.nextPlayer` is set) shows the overlay, **not** the final score screen.
3. Tapping "Starten →" on the overlay calls `store.advanceToNextPlayer` and hides the overlay.

- [ ] **Step 1: Create the test file**

```js
// src/components/__tests__/ShooterPlayPageNextShooter.test.js
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ShooterPlayPage from '@/views/shooter/ShooterPlayPage.vue'
import { usePlaySessionStore } from '@/stores/playSessionStore.js'
import { StepType } from '@/constants/playEnums.js'

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
vi.mock('@/components/shooter/ScoreTable.vue', () => ({
  default: {
    name: 'ScoreTable',
    props: ['stepStates', 'program', 'players', 'editable'],
    emits: ['correct-step'],
    template: '<div class="score-table-stub" />',
  },
}))

const mockProgram = [
  { steps: [{ type: StepType.SOLO, letter: 'A', positionId: 'pos-1' }] },
]
const mockPlayers = [
  { id: 'p1', displayName: 'Alice' },
  { id: 'p2', displayName: 'Max Mustermann' },
]

const makeRouter = () =>
  createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }],
  })

// Mounts ShooterPlayPage in an active multi-player session, at program end
// (isAtProgramEnd = true, nextPlayer set to p2), so the Getroffen card is visible.
const mountAtProgramEnd = async () => {
  const router = makeRouter()
  await router.push('/remote/r1/play')
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = usePlaySessionStore()

  store.playProg = mockProgram
  store.showGroupSetup = false
  store.playComplete = false
  store.sessionPlayers = mockPlayers
  store.currentPlayerIndex = 0
  store.isMultiPlayer = true
  store.isAtProgramEnd = true
  store.nextPlayer = mockPlayers[1]   // p2 is next
  store.playScore = { totalPoints: 0, stepStates: [] }
  store.advanceToNextPlayer = vi.fn()
  store.confirmComplete = vi.fn()
  store.closePlayback = vi.fn()

  const wrapper = mount(ShooterPlayPage, {
    props: { rangeId: 'r1' },
    global: { plugins: [router, pinia] },
  })
  await nextTick()
  return { wrapper, store }
}

describe('ShooterPlayPage — Nächster Schütze overlay', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('overlay is not visible during normal play', async () => {
    const { wrapper } = await mountAtProgramEnd()
    expect(wrapper.find('.next-shooter-overlay').exists()).toBe(false)
  })

  it('tapping the Getroffen card in multi-player shows the overlay instead of advancing', async () => {
    const { wrapper, store } = await mountAtProgramEnd()
    await wrapper.find('.getroffen-card').trigger('click')
    await nextTick()
    expect(wrapper.find('.next-shooter-overlay').exists()).toBe(true)
    expect(store.advanceToNextPlayer).not.toHaveBeenCalled()
  })

  it('overlay displays the next shooter name', async () => {
    const { wrapper } = await mountAtProgramEnd()
    await wrapper.find('.getroffen-card').trigger('click')
    await nextTick()
    expect(wrapper.find('.next-shooter-overlay').text()).toContain('Max Mustermann')
  })

  it('tapping Starten calls advanceToNextPlayer and hides the overlay', async () => {
    const { wrapper, store } = await mountAtProgramEnd()
    await wrapper.find('.getroffen-card').trigger('click')
    await nextTick()
    await wrapper.find('.next-shooter-start-btn').trigger('click')
    await nextTick()
    expect(store.advanceToNextPlayer).toHaveBeenCalledOnce()
    expect(wrapper.find('.next-shooter-overlay').exists()).toBe(false)
  })
})
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd "C:/Users/hynre/IdeaProjects/Smart Ground/smart-ground-ui"
npm run test src/components/__tests__/ShooterPlayPageNextShooter.test.js
```

Expected: 4 failing tests (`.next-shooter-overlay` not found, `.next-shooter-start-btn` not found).

---

### Task 2: Implement the overlay in ShooterPlayPage.vue

**Files:**
- Modify: `src/views/shooter/ShooterPlayPage.vue`

Three changes: (a) new ref + handler in `<script setup>`, (b) guard in `handlePlayerComplete`, (c) overlay markup + transition + CSS.

- [ ] **Step 1: Add `showNextShooterOverlay` ref and `confirmNextShooter` handler**

In `<script setup>`, after the existing `const correctionTarget = ref(null);` line (~line 340), add:

```js
// ── Next-shooter overlay ──────────────────────────────────────────────────────
const showNextShooterOverlay = ref(false);

const confirmNextShooter = () => {
  showNextShooterOverlay.value = false;
  store.advanceToNextPlayer();
};
```

- [ ] **Step 2: Guard `handlePlayerComplete` to show the overlay instead of advancing immediately**

Replace the existing `handlePlayerComplete` function (~line 653):

```js
const handlePlayerComplete = () => {
  if (store.isMultiPlayer && store.nextPlayer) {
    showNextShooterOverlay.value = true;
  } else if (store.isMultiPlayer) {
    store.advanceToNextPlayer();
  } else {
    store.confirmComplete();
  }
};
```

- [ ] **Step 3: Add the overlay markup to the template**

Directly before the closing `</div>` of the outermost `<div v-else-if="store.playProg" class="play-page">` block (just before line 318's `</div>`), add:

```html
    <!-- Next-shooter overlay -->
    <Transition name="next-shooter-fade">
      <div v-if="showNextShooterOverlay" class="next-shooter-overlay">
        <div class="next-shooter-card">
          <span class="next-shooter-label">Nächster Schütze</span>
          <span class="next-shooter-name">{{ store.nextPlayer?.displayName }}</span>
          <span class="next-shooter-hint">Bitte schießbereit machen</span>
          <button class="next-shooter-start-btn" @click="confirmNextShooter">
            Starten →
          </button>
        </div>
      </div>
    </Transition>
```

- [ ] **Step 4: Add scoped CSS for the overlay**

At the bottom of `<style scoped>`, before the closing `</style>`, add:

```css
/* ── Next-shooter overlay ──────────────────────────────── */
.next-shooter-overlay {
  position: fixed;
  inset: 0;
  background: rgba(10, 10, 18, 0.97);
  backdrop-filter: blur(24px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 60;
}

.next-shooter-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 40px 32px;
  text-align: center;
}

.next-shooter-label {
  font-size: 11px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.4);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.next-shooter-name {
  font-size: 36px;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.15;
}

.next-shooter-hint {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.4);
}

.next-shooter-start-btn {
  margin-top: 16px;
  padding: 14px 40px;
  background: color-mix(in srgb, var(--sg-accent) 20%, transparent);
  border: 1px solid color-mix(in srgb, var(--sg-accent) 35%, transparent);
  border-radius: 14px;
  color: var(--sg-accent);
  font-family: inherit;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}

.next-shooter-start-btn:hover {
  background: color-mix(in srgb, var(--sg-accent) 28%, transparent);
}

.next-shooter-start-btn:active {
  transform: scale(0.97);
}

.next-shooter-fade-enter-active,
.next-shooter-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.next-shooter-fade-enter-from,
.next-shooter-fade-leave-to {
  opacity: 0;
  transform: translateY(12px);
}
```

- [ ] **Step 5: Run all tests — verify they now pass**

```bash
npm run test src/components/__tests__/ShooterPlayPageNextShooter.test.js
```

Expected: 4 passing tests.

- [ ] **Step 6: Run the full test suite to check for regressions**

```bash
npm run test
```

Expected: all existing tests still pass.

- [ ] **Step 7: Commit**

```bash
git add src/views/shooter/ShooterPlayPage.vue src/components/__tests__/ShooterPlayPageNextShooter.test.js
git commit -m "[ui] ShooterPlayPage: show Nächster-Schütze overlay between players"
```
