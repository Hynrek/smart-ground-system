# Range-Centered Block Play Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the linear "play whole Program at once" flow with Range-centered Blocks — each Ablauf in a Program becomes an independently playable Block visible at its assigned Range's FlyoutPanel.

**Architecture:** A new `activeProgramStore` owns the Program instance lifecycle (Template → Active → Completed). `playSessionStore` gains a thin integration layer (`activeBlockContext`) to report completion back to the instance. The FlyoutPanel at each Range becomes the discovery hub, showing Blocks grouped by source type.

**Tech Stack:** Vue 3 Composition API, Pinia, Vitest + @vue/test-utils, localStorage

---

## File Map

| File | Change |
|---|---|
| `src/stores/activeProgramStore.js` | **Create** — full instance lifecycle store |
| `src/stores/__tests__/activeProgramStore.test.js` | **Create** — store unit tests |
| `src/stores/playSessionStore.js` | **Modify** — add `activeBlockContext`, update `startGroupPlay` + `confirmComplete`, add `buildPlayerResults` |
| `src/components/shooter-remote/ShooterFlyoutPanel.vue` | **Modify** — replace "Aktive Programme" section with "Programme" blocks from `activeProgramStore` |
| `src/views/shooter/ProgramManagementView.vue` | **Modify** — new "Starten" flow (inline modal → instance), Tab 2 from `activeProgramStore`, new Tab 3 |
| `src/views/shooter/ShooterPlayPage.vue` | **Modify** — replace `loadPendingProgram` call, pass block context to `startGroupPlay` |

---

## Task 1: Create `activeProgramStore`

**Files:**
- Create: `src/stores/activeProgramStore.js`
- Create: `src/stores/__tests__/activeProgramStore.test.js`

- [ ] **Step 1: Write the failing tests**

Create `src/stores/__tests__/activeProgramStore.test.js`:

```js
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useActiveProgramStore } from '../activeProgramStore'

const template = {
  id: 'prog-1',
  name: 'Test Programm',
  ablaeufe: [
    { id: 'abl-1', name: 'Ablauf 1', rangeId: 'r1', rangeName: 'Platz 1', steps: [{ id: 's1', type: 'solo' }] },
    { id: 'abl-2', name: 'Ablauf 2', rangeId: 'r2', rangeName: 'Platz 2', steps: [{ id: 's2', type: 'solo' }] },
  ],
}
const players = [{ id: 'p1', displayName: 'Schütze 1', type: 'guest' }]

describe('useActiveProgramStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('startProgram creates one block per ablauf with pending status', () => {
    const store = useActiveProgramStore()
    const inst = store.startProgram(template, players)
    expect(store.activeInstances).toHaveLength(1)
    expect(inst.type).toBe('programm')
    expect(inst.blocks).toHaveLength(2)
    expect(inst.blocks[0].ablaufId).toBe('abl-1')
    expect(inst.blocks[0].status).toBe('pending')
    expect(inst.blocks[0].steps).toHaveLength(1)
  })

  it('startProgram persists to localStorage', () => {
    const store = useActiveProgramStore()
    store.startProgram(template, players)
    expect(localStorage.getItem('sg_active_program_instances')).not.toBeNull()
  })

  it('getBlocksForRange returns only pending and in_progress blocks for that range', () => {
    const store = useActiveProgramStore()
    store.startProgram(template, players)
    const blocks = store.getBlocksForRange('r1')
    expect(blocks).toHaveLength(1)
    expect(blocks[0].ablaufId).toBe('abl-1')
    expect(blocks[0].instanceId).toBeDefined()
    expect(blocks[0].templateName).toBe('Test Programm')
  })

  it('getBlocksForRange excludes done blocks', () => {
    const store = useActiveProgramStore()
    const inst = store.startProgram(template, players)
    store.markBlockDone(inst.instanceId, inst.blocks[0].blockId, [])
    expect(store.getBlocksForRange('r1')).toHaveLength(0)
  })

  it('markBlockInProgress sets status to in_progress', () => {
    const store = useActiveProgramStore()
    const inst = store.startProgram(template, players)
    store.markBlockInProgress(inst.instanceId, inst.blocks[0].blockId)
    expect(store.activeInstances[0].blocks[0].status).toBe('in_progress')
  })

  it('markBlockDone stores result on block', () => {
    const store = useActiveProgramStore()
    const inst = store.startProgram(template, players)
    const results = [{ playerId: 'p1', displayName: 'Schütze 1', totalPoints: 1, maxPoints: 1, stepStates: [] }]
    store.markBlockDone(inst.instanceId, inst.blocks[0].blockId, results)
    expect(store.activeInstances[0].blocks[0].status).toBe('done')
    expect(store.activeInstances[0].blocks[0].result.playerResults).toHaveLength(1)
  })

  it('markBlockDone moves instance to completedInstances when all blocks done', () => {
    const store = useActiveProgramStore()
    const inst = store.startProgram(template, players)
    store.markBlockDone(inst.instanceId, inst.blocks[0].blockId, [])
    expect(store.activeInstances).toHaveLength(1) // still active
    store.markBlockDone(inst.instanceId, inst.blocks[1].blockId, [])
    expect(store.activeInstances).toHaveLength(0)
    expect(store.completedInstances).toHaveLength(1)
    expect(store.completedInstances[0].completedAt).toBeDefined()
  })

  it('stopInstance removes active instance', () => {
    const store = useActiveProgramStore()
    const inst = store.startProgram(template, players)
    store.stopInstance(inst.instanceId)
    expect(store.activeInstances).toHaveLength(0)
  })

  it('loads persisted data on init', () => {
    const store = useActiveProgramStore()
    store.startProgram(template, players)

    setActivePinia(createPinia())
    const store2 = useActiveProgramStore()
    expect(store2.activeInstances).toHaveLength(1)
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
npm run test src/stores/__tests__/activeProgramStore.test.js
```

Expected: FAIL with "Cannot find module '../activeProgramStore'"

- [ ] **Step 3: Create `src/stores/activeProgramStore.js`**

```js
import { defineStore } from 'pinia'
import { ref } from 'vue'

const generateUUID = () =>
  typeof globalThis !== 'undefined' && globalThis.crypto?.randomUUID
    ? globalThis.crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
      })

const ACTIVE_KEY = 'sg_active_program_instances'
const COMPLETED_KEY = 'sg_completed_program_instances'

export const useActiveProgramStore = defineStore('activeProgram', () => {
  const activeInstances = ref([])
  const completedInstances = ref([])

  const _saveActive = () =>
    localStorage.setItem(ACTIVE_KEY, JSON.stringify(activeInstances.value))

  const _saveCompleted = () =>
    localStorage.setItem(COMPLETED_KEY, JSON.stringify(completedInstances.value))

  const loadFromStorage = () => {
    try {
      const a = localStorage.getItem(ACTIVE_KEY)
      if (a) activeInstances.value = JSON.parse(a)
      const c = localStorage.getItem(COMPLETED_KEY)
      if (c) completedInstances.value = JSON.parse(c)
    } catch { /* ignore malformed data */ }
  }

  const startProgram = (template, players) => {
    const instance = {
      instanceId: generateUUID(),
      type: 'programm',
      templateId: template.id,
      templateName: template.name,
      players: [...players],
      startedAt: Date.now(),
      blocks: template.ablaeufe.map((ablauf) => ({
        blockId: generateUUID(),
        ablaufId: ablauf.id,
        ablaufAlias: ablauf.name ?? ablauf.alias ?? ablauf.id,
        rangeId: ablauf.rangeId ?? null,
        rangeName: ablauf.rangeName ?? null,
        steps: ablauf.steps ?? [],
        status: 'pending',
        completedAt: null,
        result: null,
      })),
    }
    activeInstances.value.push(instance)
    _saveActive()
    return instance
  }

  const getBlocksForRange = (rangeId) => {
    const result = []
    for (const inst of activeInstances.value) {
      for (const block of inst.blocks) {
        if (block.rangeId === rangeId && block.status !== 'done') {
          result.push({
            ...block,
            instanceId: inst.instanceId,
            templateName: inst.templateName,
            players: inst.players,
          })
        }
      }
    }
    return result
  }

  const markBlockInProgress = (instanceId, blockId) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    const block = inst?.blocks.find((b) => b.blockId === blockId)
    if (block && block.status === 'pending') {
      block.status = 'in_progress'
      _saveActive()
    }
  }

  const markBlockDone = (instanceId, blockId, playerResults) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    if (!inst) return
    const block = inst.blocks.find((b) => b.blockId === blockId)
    if (!block) return
    block.status = 'done'
    block.completedAt = Date.now()
    block.result = { playerResults }

    if (inst.blocks.every((b) => b.status === 'done')) {
      completedInstances.value.push({ ...inst, completedAt: Date.now() })
      activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== instanceId)
      _saveCompleted()
    }
    _saveActive()
  }

  const stopInstance = (instanceId) => {
    activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== instanceId)
    _saveActive()
  }

  loadFromStorage()

  return {
    activeInstances,
    completedInstances,
    startProgram,
    getBlocksForRange,
    markBlockInProgress,
    markBlockDone,
    stopInstance,
  }
})
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
npm run test src/stores/__tests__/activeProgramStore.test.js
```

Expected: All 9 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/stores/activeProgramStore.js src/stores/__tests__/activeProgramStore.test.js
git commit -m "[ui] Add activeProgramStore for program instance lifecycle"
```

---

## Task 2: Update `playSessionStore`

**Files:**
- Modify: `src/stores/playSessionStore.js`

- [ ] **Step 1: Add `activeBlockContext` ref and export it**

In `playSessionStore.js`, add after line 33 (`const playComplete = ref(false);`):

```js
const activeBlockContext = ref(null); // { instanceId, blockId } | null
```

In the `return` block at the bottom, add `activeBlockContext` to the exported state section.

- [ ] **Step 2: Update `startGroupPlay` to accept and store block context**

Replace the existing `startGroupPlay` function signature and add context handling. Find the function starting with `const startGroupPlay = (players, rangeId = null, rangeName = null) => {` and replace with:

```js
const startGroupPlay = (players, rangeId = null, rangeName = null, instanceId = null, blockId = null) => {
    if (!pendingGroupAblaeufe.value) return;
    const ablaeufe = pendingGroupAblaeufe.value;

    setupPlayers(players);
    completedPlayerCount.value = 0;

    playProg.value = ablaeufe;
    currentAblaufIndex.value = 0;
    currentStepIndex.value = 0;
    playScoreMode.value = true;
    playPartialStep.value = null;
    playRaffaleStarted.value = false;
    playLastDeviceStep.value = null;
    playComplete.value = false;

    const stepStates = [];
    players.forEach((player) => {
      ablaeufe.forEach((seg, segIdx) => {
        seg.steps.forEach((step, stepIdx) => {
          stepStates.push({
            playerId: player.id,
            ablaufIndex: segIdx,
            stepIndex: stepIdx,
            state: StepState.PENDING,
            pointValue: getPointValueForStep(step),
            noBirds: 0,
            pointsEarned: 0,
          });
        });
      });
    });
    playScore.value = { totalPoints: 0, stepStates };

    // Set block context when playing from an active program instance
    if (instanceId && blockId) {
      activeBlockContext.value = { instanceId, blockId };
      const { useActiveProgramStore } = await import('@/stores/activeProgramStore.js');
      useActiveProgramStore().markBlockInProgress(instanceId, blockId);
    } else {
      activeBlockContext.value = null;
    }

    // Create legacy active session if rangeId is provided (kept for backward compat)
    if (rangeId && !instanceId) {
      const sessionId = generateUUID();
      currentSessionId.value = sessionId;
      const programId = _pendingProgramId.value;
      const programName = programStore.savedPrograms.find((p) => p.id === programId)?.name || 'Programm';

      const session = {
        sessionId,
        programId,
        programName,
        rangeId,
        rangeName: rangeName || `Platz ${rangeId}`,
        players: [...players],
        startedAt: Date.now(),
        completionPct: 0,
        status: 'active',
      };

      activeSessions.value.push(session);
      saveSessions();
      _pendingProgramId.value = null;
    }

    showGroupSetup.value = false;
    pendingGroupAblaeufe.value = null;
  };
```

Note: The `import` inside the function avoids circular dependency since `activeProgramStore` does not import `playSessionStore`.

- [ ] **Step 3: Add `buildPlayerResults` helper**

Add this function after the `getPointDeduction` helper (around line 164):

```js
  const buildPlayerResults = () => {
    return sessionPlayers.value.map((player) => {
      const states = playScore.value.stepStates.filter((s) => s.playerId === player.id);
      const totalPoints = states.reduce((sum, s) => sum + s.pointsEarned, 0);
      const maxPoints = states.reduce((sum, s) => sum + s.pointValue, 0);
      return {
        playerId: player.id,
        displayName: player.displayName,
        totalPoints,
        maxPoints,
        stepStates: states.map((s) => ({ ...s })),
      };
    });
  };
```

- [ ] **Step 4: Update `confirmComplete` to notify `activeProgramStore`**

Find `confirmComplete` and replace it with:

```js
  const confirmComplete = async () => {
    playComplete.value = true;
    // Notify active program store if playing a block instance
    if (activeBlockContext.value) {
      const { useActiveProgramStore } = await import('@/stores/activeProgramStore.js');
      useActiveProgramStore().markBlockDone(
        activeBlockContext.value.instanceId,
        activeBlockContext.value.blockId,
        buildPlayerResults(),
      );
      activeBlockContext.value = null;
    }
    // Remove legacy session from active sessions
    if (currentSessionId.value) {
      activeSessions.value = activeSessions.value.filter((s) => s.sessionId !== currentSessionId.value);
      currentSessionId.value = null;
      saveSessions();
    }
  };
```

- [ ] **Step 5: Add `activeBlockContext` to the return block**

In the `return { ... }` statement at the bottom of the store, add `activeBlockContext` under `// State`:

```js
    activeBlockContext,
```

- [ ] **Step 6: Run existing tests to verify no regressions**

```bash
npm run test src/stores/__tests__/
```

Expected: All existing store tests PASS

- [ ] **Step 7: Commit**

```bash
git add src/stores/playSessionStore.js
git commit -m "[ui] Add activeBlockContext and buildPlayerResults to playSessionStore"
```

---

## Task 3: Redesign `ShooterFlyoutPanel` — Programme section

**Files:**
- Modify: `src/components/shooter-remote/ShooterFlyoutPanel.vue`

This task replaces the "Aktive Programme" section in the non-recording expanded panel with a new "Programme" section driven by `activeProgramStore`. All other sections (Meine Abläufe, Globale Abläufe, Offene Wettkämpfe, Offene Trainings) stay as-is.

- [ ] **Step 1: Import `useActiveProgramStore` in the script**

In the `<script setup>` section, after the existing imports, add:

```js
import { useActiveProgramStore } from '@/stores/activeProgramStore.js';
const activeProgramStore = useActiveProgramStore();
```

- [ ] **Step 2: Replace `activeSessions` computed with `programmeBlocks`**

Find and remove:
```js
const activeSessions = computed(() => playStore.activeSessions);
```

Replace with:
```js
const programmeBlocks = computed(() =>
  activeProgramStore.getBlocksForRange(currentRangeId.value)
);
```

- [ ] **Step 3: Add `playBlock` function**

Find the `resumeSession` function and replace it with `playBlock`:

```js
const playBlock = (block) => {
  playStore.pendingProgramInfo = {
    ablauf: {
      id: block.ablaufId,
      name: block.ablaufAlias,
      alias: block.ablaufAlias,
      steps: block.steps,
      rangeId: block.rangeId,
      rangeName: block.rangeName,
    },
    rangeId: currentRangeId.value,
    instanceId: block.instanceId,
    blockId: block.blockId,
    players: block.players,
  };
  isOpen.value = false;
  router.push(`/remote/${currentRangeId.value}/play`);
};
```

- [ ] **Step 4: Replace "Aktive Programme" section in the template**

Find the template block:
```html
<!-- Aktive Programme -->
<template v-if="activeSessions.length > 0">
  <div class="section">
    <span class="section-label">Aktive Programme</span>
    <div class="ablaeufe-list">
      <div
        v-for="session in activeSessions"
        :key="session.sessionId"
        class="ablauf-card"
      >
        <button
          class="ablauf-header-btn"
          @click="resumeSession(session)"
        >
          <span class="ablauf-name">{{ session.programName }}</span>
          <span class="completion-badge">{{ session.completionPct }}%</span>
        </button>
        <div class="ablauf-actions">
          <div class="session-meta">
            {{ session.players.length }} {{ session.players.length === 1 ? 'Schütze' : 'Schützen' }}
          </div>
          <button class="action-btn action-play" @click="resumeSession(session)">
            <Icons icon="play" :size="12" color="#fff" />
            Fortfahren
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
```

Replace it with:
```html
<!-- Programme Blöcke -->
<template v-if="programmeBlocks.length > 0">
  <div class="section">
    <span class="section-label">Programme</span>
    <div class="ablaeufe-list">
      <div
        v-for="block in programmeBlocks"
        :key="block.blockId"
        class="ablauf-card"
      >
        <button class="ablauf-header-btn" @click="playBlock(block)">
          <div class="block-info">
            <span class="ablauf-name">{{ block.ablaufAlias }}</span>
            <span class="block-template-name">{{ block.templateName }}</span>
          </div>
          <span class="block-status-badge" :class="`status-${block.status}`">
            {{ block.status === 'in_progress' ? '◑' : '●' }}
          </span>
        </button>
        <div class="ablauf-actions">
          <div class="session-meta">
            {{ block.players.map(p => p.displayName).join(', ') }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 5: Update the empty-state condition**

Find the empty state `v-if` condition:
```html
v-if="competitionAblaeufe.length === 0 && trainingAblaeufe.length === 0 && userAblaeufe.length === 0 && globalAblaeufe.length === 0 && activeSessions.length === 0"
```

Replace with:
```html
v-if="programmeBlocks.length === 0 && competitionAblaeufe.length === 0 && trainingAblaeufe.length === 0 && userAblaeufe.length === 0 && globalAblaeufe.length === 0"
```

- [ ] **Step 6: Add CSS for new block card elements**

In the `<style scoped>` section, add after the last existing style rule:

```css
.block-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
  text-align: left;
}

.block-template-name {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.block-status-badge {
  font-size: 12px;
  flex-shrink: 0;
}

.block-status-badge.status-pending {
  color: rgba(79, 195, 247, 0.5);
}

.block-status-badge.status-in_progress {
  color: rgba(246, 173, 85, 0.8);
}
```

- [ ] **Step 7: Run lint**

```bash
npm run lint
```

Expected: No errors

- [ ] **Step 8: Commit**

```bash
git add src/components/shooter-remote/ShooterFlyoutPanel.vue
git commit -m "[ui] Replace active sessions with programme blocks in FlyoutPanel"
```

---

## Task 4: Update `ProgramManagementView`

**Files:**
- Modify: `src/views/shooter/ProgramManagementView.vue`

This task: (1) changes "Starten" to open an inline group-setup modal and create an active instance, (2) rewires Tab 2 to show `activeProgramStore.activeInstances`, (3) adds Tab 3 for completed instances.

- [ ] **Step 1: Import `useActiveProgramStore` and add refs for inline group setup**

In the `<script setup>` section, add the import after existing imports:

```js
import { useActiveProgramStore } from '@/stores/activeProgramStore.js';
const activeProgramStore = useActiveProgramStore();
```

Add these refs after the existing `showActiveProgramsTab` ref:

```js
// ── Inline program start modal ────────────────────────────────────────────
const startingProgram = ref(null);         // Program template being started
const startModalPlayers = ref([]);         // Players for the new instance
let _nextStartPlayerId = 1;
```

- [ ] **Step 2: Replace `startProgram` function with new inline-modal flow**

Find and remove the existing `startProgram` function:
```js
const startProgram = (prog) => {
  const rangeId = prog.ablaeufe[0]?.rangeId ?? null;
  if (!rangeId) {
    alert('Dieses Programm hat keinen Platz zugeordnet.');
    return;
  }
  playSessionStore.pendingProgramInfo = { programId: prog.id, rangeId };
  router.push(`/remote/${rangeId}/play`);
};
```

Replace with:

```js
const openStartModal = (prog) => {
  startingProgram.value = prog;
  _nextStartPlayerId = 1;
  startModalPlayers.value = [{ id: `sp-${_nextStartPlayerId++}`, displayName: 'Schütze 1', type: 'guest' }];
};

const addStartModalPlayer = () => {
  const n = startModalPlayers.value.length + 1;
  startModalPlayers.value.push({ id: `sp-${_nextStartPlayerId++}`, displayName: `Schütze ${n}`, type: 'guest' });
};

const removeStartModalPlayer = (index) => {
  startModalPlayers.value.splice(index, 1);
};

const confirmStartProgram = () => {
  if (!startingProgram.value || startModalPlayers.value.length === 0) return;
  activeProgramStore.startProgram(startingProgram.value, startModalPlayers.value);
  startingProgram.value = null;
  startModalPlayers.value = [];
  showActiveProgramsTab.value = true;
};

const cancelStartProgram = () => {
  startingProgram.value = null;
  startModalPlayers.value = [];
};
```

- [ ] **Step 3: Update "Starten" button in template to call `openStartModal`**

Find:
```html
<button class="action-btn action-btn--start" @click.stop="startProgram(prog)">
```

Replace with:
```html
<button class="action-btn action-btn--start" @click.stop="openStartModal(prog)">
```

- [ ] **Step 4: Add the start modal overlay to the template**

Add this block just before the closing `</div>` of the `.programme-view` wrapper (after the `.content` div):

```html
<!-- ── Inline Program Start Modal ──────────────────── -->
<div v-if="startingProgram" class="start-modal-overlay" @click.self="cancelStartProgram">
  <div class="start-modal">
    <h3 class="start-modal-title">{{ startingProgram.name }} starten</h3>

    <div class="start-modal-players">
      <div
        v-for="(player, i) in startModalPlayers"
        :key="player.id"
        class="start-player-row"
      >
        <span class="start-player-num">{{ i + 1 }}:</span>
        <input
          v-model="player.displayName"
          class="start-player-input"
          type="text"
          :placeholder="`Schütze ${i + 1}`"
          maxlength="30"
        />
        <button
          v-if="startModalPlayers.length > 1"
          class="icon-btn icon-btn--danger"
          @click="removeStartModalPlayer(i)"
        >
          <Icons icon="x" :size="11" color="#fc8181" />
        </button>
      </div>
    </div>

    <button class="add-player-btn" @click="addStartModalPlayer">
      + Schütze hinzufügen
    </button>

    <div class="start-modal-actions">
      <button class="action-btn action-btn--cancel" @click="cancelStartProgram">Abbrechen</button>
      <button
        class="action-btn action-btn--start"
        :disabled="startModalPlayers.length === 0"
        @click="confirmStartProgram"
      >
        <Icons icon="play" :size="14" color="#4fc3f7" />
        Starten
      </button>
    </div>
  </div>
</div>
```

- [ ] **Step 5: Add CSS for the start modal**

In `<style scoped>`, add:

```css
/* ── Start modal ──────────────────────────────────── */
.start-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: 24px;
}

.start-modal {
  background: rgba(24, 24, 40, 0.98);
  border: 1.5px solid rgba(255, 255, 255, 0.1);
  border-radius: 20px;
  padding: 24px;
  width: 100%;
  max-width: 340px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.start-modal-title {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  margin: 0;
  text-align: center;
}

.start-modal-players {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.start-player-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.start-player-num {
  font-size: 13px;
  font-weight: 700;
  color: rgba(79, 195, 247, 0.7);
  min-width: 20px;
}

.start-player-input {
  flex: 1;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  color: #fff;
  font-size: 13px;
  font-family: inherit;
  padding: 8px 10px;
  outline: none;
}

.start-player-input:focus {
  border-color: rgba(79, 195, 247, 0.3);
}

.add-player-btn {
  background: transparent;
  border: 1px dashed rgba(255, 255, 255, 0.15);
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.4);
  font-size: 12px;
  font-family: inherit;
  padding: 8px;
  cursor: pointer;
  transition: all 0.15s;
}

.add-player-btn:hover {
  background: rgba(255, 255, 255, 0.04);
  color: rgba(255, 255, 255, 0.6);
}

.start-modal-actions {
  display: flex;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}
```

- [ ] **Step 6: Rewire Tab 2 to use `activeProgramStore.activeInstances`**

Find the entire "Aktive Programme" section content (inside `<template v-if="showActiveProgramsTab">`). Replace the inner `<section class="block">` content with:

```html
<section class="block">
  <div class="block-header">
    <div class="block-title-row">
      <span class="block-title">Aktive Programme</span>
      <span class="block-badge">{{ activeProgramStore.activeInstances.length }}</span>
    </div>
    <p class="block-desc">
      Gestartete Programme. Blöcke erscheinen auf den zugehörigen Plätzen.
    </p>
  </div>

  <div v-if="activeProgramStore.activeInstances.length > 0" class="active-sessions-list">
    <div
      v-for="inst in activeProgramStore.activeInstances"
      :key="inst.instanceId"
      class="session-card"
    >
      <div class="session-main">
        <div class="session-info">
          <span class="session-program">{{ inst.templateName }}</span>
          <span class="session-range">
            {{ inst.players.map(p => p.displayName).join(', ') }}
            · {{ inst.blocks.filter(b => b.status === 'done').length }}/{{ inst.blocks.length }} Blöcke
          </span>
        </div>
        <button class="icon-btn" title="Stoppen" @click="stopInstanceConfirm(inst)">
          <Icons icon="x" :size="13" color="rgba(252,129,129,0.6)" />
        </button>
      </div>

      <!-- Block list -->
      <div class="block-status-list">
        <div
          v-for="block in inst.blocks"
          :key="block.blockId"
          class="block-status-row"
        >
          <span class="block-status-dot" :class="`dot-${block.status}`" />
          <span class="block-status-range">{{ block.rangeName ?? 'Kein Platz' }}</span>
          <span class="block-status-name">{{ block.ablaufAlias }}</span>
        </div>
      </div>

      <!-- Progress bar -->
      <div class="progress-bar-container">
        <div class="progress-bar">
          <div
            class="progress-fill"
            :style="{ width: (inst.blocks.filter(b => b.status === 'done').length / inst.blocks.length * 100) + '%' }"
          />
        </div>
        <span class="progress-label">
          {{ inst.blocks.filter(b => b.status === 'done').length }}/{{ inst.blocks.length }}
        </span>
      </div>
    </div>
  </div>

  <div v-if="activeProgramStore.activeInstances.length === 0" class="empty-block">
    <Icons icon="program" :size="36" color="rgba(255,255,255,0.07)" />
    <p>Keine aktiven Programme</p>
    <p class="empty-hint">Starte ein Programm über den Programme-Tab.</p>
  </div>
</section>
```

- [ ] **Step 7: Replace `stopSessionConfirm` with `stopInstanceConfirm`**

Find and remove:
```js
const stopSessionConfirm = (session) => {
  if (confirm(`Möchtest du „${session.programName}" wirklich abbrechen?`)) {
    playSessionStore.stopSession(session.sessionId);
  }
};
```

Replace with:
```js
const stopInstanceConfirm = (inst) => {
  if (confirm(`Möchtest du „${inst.templateName}" wirklich abbrechen?`)) {
    activeProgramStore.stopInstance(inst.instanceId);
  }
};
```

- [ ] **Step 8: Add Tab 3 toggle button and section**

Find the tab toggle section and add a third button:
```html
<!-- In the .tab-toggle div, after the existing two tab-btn elements: -->
<button
  class="tab-btn"
  :class="{ active: activeTab === 'completed' }"
  @click="activeTab = 'completed'"
>
  Abgeschlossen
</button>
```

Replace the existing `showActiveProgramsTab` ref logic for tabs with a three-way `activeTab` ref:

In `<script setup>`, replace:
```js
const showActiveProgramsTab = ref(router.currentRoute.value.query.tab === 'active');
```

With:
```js
const activeTab = ref(
  router.currentRoute.value.query.tab === 'active' ? 'active'
  : router.currentRoute.value.query.tab === 'completed' ? 'completed'
  : 'programmes'
)
```

Update the two existing `v-if` template conditions:
- `v-if="!showActiveProgramsTab"` → `v-if="activeTab === 'programmes'"`
- `v-if="showActiveProgramsTab"` → `v-if="activeTab === 'active'"`
- Also update `showActiveProgramsTab.value = true` in `confirmStartProgram` → `activeTab.value = 'active'`

Add a third `<template v-if="activeTab === 'completed'">` section after the "Aktive Programme" section:

```html
<!-- ═══════════════════════════════════════════ -->
<!-- TAB 3: ABGESCHLOSSENE PROGRAMME             -->
<!-- ═══════════════════════════════════════════ -->
<template v-if="activeTab === 'completed'">
  <section class="block">
    <div class="block-header">
      <div class="block-title-row">
        <span class="block-title">Abgeschlossene Programme</span>
        <span class="block-badge">{{ activeProgramStore.completedInstances.length }}</span>
      </div>
      <p class="block-desc">Fertig gespielte Programme. Basis für zukünftige Statistiken.</p>
    </div>

    <div v-if="activeProgramStore.completedInstances.length > 0" class="active-sessions-list">
      <div
        v-for="inst in activeProgramStore.completedInstances"
        :key="inst.instanceId"
        class="session-card"
      >
        <div class="session-main">
          <div class="session-info">
            <span class="session-program">{{ inst.templateName }}</span>
            <span class="session-range">
              {{ new Date(inst.completedAt).toLocaleDateString('de-CH') }}
              · {{ inst.players.map(p => p.displayName).join(', ') }}
            </span>
          </div>
        </div>

        <!-- Per-player scores -->
        <div class="completed-scores">
          <div
            v-for="block in inst.blocks"
            :key="block.blockId"
          >
            <template v-if="block.result">
              <div
                v-for="pr in block.result.playerResults"
                :key="pr.playerId"
                class="completed-score-row"
              >
                <span class="completed-score-player">{{ pr.displayName }}</span>
                <span class="completed-score-block">{{ block.ablaufAlias }}</span>
                <span class="completed-score-pts">{{ pr.totalPoints }}/{{ pr.maxPoints }}</span>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <div v-if="activeProgramStore.completedInstances.length === 0" class="empty-block">
      <Icons icon="program" :size="36" color="rgba(255,255,255,0.07)" />
      <p>Noch keine abgeschlossenen Programme</p>
    </div>
  </section>
</template>
```

- [ ] **Step 9: Add CSS for new elements**

In `<style scoped>`, add:

```css
/* ── Block status list (in active instances card) ─── */
.block-status-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 4px 0;
}

.block-status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.block-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.block-status-dot.dot-pending { background: rgba(255, 255, 255, 0.2); }
.block-status-dot.dot-in_progress { background: rgba(246, 173, 85, 0.8); }
.block-status-dot.dot-done { background: rgba(72, 187, 120, 0.7); }

.block-status-range {
  color: rgba(255, 255, 255, 0.35);
  min-width: 60px;
}

.block-status-name {
  color: rgba(255, 255, 255, 0.7);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── Completed scores ────────────────────────────── */
.completed-scores {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.completed-score-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.completed-score-player {
  color: rgba(255, 255, 255, 0.6);
  min-width: 80px;
}

.completed-score-block {
  flex: 1;
  color: rgba(255, 255, 255, 0.35);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.completed-score-pts {
  font-weight: 700;
  color: rgba(79, 195, 247, 0.9);
  min-width: 40px;
  text-align: right;
}
```

- [ ] **Step 10: Run lint**

```bash
npm run lint
```

Expected: No errors

- [ ] **Step 11: Commit**

```bash
git add src/views/shooter/ProgramManagementView.vue
git commit -m "[ui] Update ProgramManagementView: inline start modal, active/completed tabs from activeProgramStore"
```

---

## Task 5: Update `ShooterPlayPage` — block context handoff

**Files:**
- Modify: `src/views/shooter/ShooterPlayPage.vue`

- [ ] **Step 1: Replace `loadPendingProgram` call with direct `setPendingGroupAblaeufe`**

Find lines 272–276:
```js
// Load pending program if coming from Programme Management View
if (store.pendingProgramInfo) {
  store.loadPendingProgram();
  store.clearPendingProgram();
}
```

Replace with:
```js
// Capture block context before clearing pendingProgramInfo
const _blockContext = ref(null);
if (store.pendingProgramInfo) {
  const info = store.pendingProgramInfo;
  store.setPendingGroupAblaeufe([info.ablauf]);
  if (info.instanceId && info.blockId) {
    _blockContext.value = { instanceId: info.instanceId, blockId: info.blockId };
  }
  // Pre-populate players from instance if provided
  if (info.players?.length) {
    groupPlayers.value = info.players.map((p, i) => ({
      id: p.id ?? `gp-${i + 1}`,
      displayName: p.displayName,
    }));
  }
  store.clearPendingProgram();
}
```

- [ ] **Step 2: Update `beginGroupPlay` to pass block context and stay on play page**

Find:
```js
const beginGroupPlay = () => {
  store.startGroupPlay(groupPlayers.value, props.rangeId, 'Platz');
  router.push({ path: '/programmes', query: { tab: 'active' } });
};
```

Replace with:
```js
const beginGroupPlay = () => {
  store.startGroupPlay(
    groupPlayers.value,
    props.rangeId,
    'Platz',
    _blockContext.value?.instanceId ?? null,
    _blockContext.value?.blockId ?? null,
  );
};
```

- [ ] **Step 3: Run lint**

```bash
npm run lint
```

Expected: No errors

- [ ] **Step 4: Commit**

```bash
git add src/views/shooter/ShooterPlayPage.vue
git commit -m "[ui] Wire ShooterPlayPage to pass block context into startGroupPlay"
```

---

## Self-Review Checklist

- [x] `activeProgramStore` — `startProgram`, `getBlocksForRange`, `markBlockInProgress`, `markBlockDone`, `stopInstance` all defined and tested
- [x] `playSessionStore` — `activeBlockContext`, updated `startGroupPlay` signature, `buildPlayerResults`, updated `confirmComplete`
- [x] `ShooterFlyoutPanel` — Programme section replaces activeSessions; `playBlock` sets `pendingProgramInfo` with `{ ablauf, rangeId, instanceId, blockId, players }`
- [x] `ProgramManagementView` — inline start modal, Tab 2 from `activeProgramStore.activeInstances`, Tab 3 from `completedInstances`, `stopInstanceConfirm`
- [x] `ShooterPlayPage` — `setPendingGroupAblaeufe([info.ablauf])` replaces `loadPendingProgram`, `beginGroupPlay` passes instanceId/blockId, stays on play page
- [x] Circular dependency avoided — `activeProgramStore` imported lazily inside `playSessionStore` async functions
- [x] `steps` stored in block so FlyoutPanel and play page can use them without additional store lookup
