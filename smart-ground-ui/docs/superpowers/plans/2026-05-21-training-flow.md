# Training Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Training session type — a sequentially-ordered collection of 1–n saved Programmes that players work through range-by-range, managed from a dedicated `/training` view and discovered via the FlyoutPanel.

**Architecture:** Training templates are stored in localStorage (same pattern as Programmes). Active Training instances live in `activeProgramStore` alongside Programme instances, distinguished by `type: 'training'`. Each Training instance tracks a `currentPhaseIndex` pointing to the active Programme; `getBlocksForRange()` yields only that phase's blocks. When all blocks in a phase are done, the store auto-advances to the next phase.

**Tech Stack:** Vue 3 (Composition API, `<script setup>`), Pinia, Vitest + `@vue/test-utils`, localStorage.

---

## File Map

| File | Change |
|---|---|
| `src/stores/programStore.js` | Add Training template CRUD (`savedTrainings`, `createTraining`, `deleteTraining`, `renameTraining`, `loadTrainingsFromStorage`) |
| `src/stores/activeProgramStore.js` | Add `startTraining()`; extend `getBlocksForRange()` and `markBlockDone()` for Training instances |
| `src/stores/__tests__/activeProgramStore.test.js` | Extend with Training-specific tests |
| `src/stores/__tests__/programStore.training.test.js` | New — Training template persistence tests |
| `src/views/shooter/TrainingManagementView.vue` | New — three-tab Training management view |
| `src/components/shooter-remote/ShooterFlyoutPanel.vue` | Populate `trainingAblaeufe`; filter `programmeBlocks` by `instanceType` |
| `src/views/shooter/ShooterHomeView.vue` | Add Training navigation tile |
| `src/router/index.js` | Add `/training` route |

---

## Task 1: Training template persistence in `programStore`

**Files:**
- Modify: `src/stores/programStore.js`
- Create: `src/stores/__tests__/programStore.training.test.js`

- [ ] **Step 1.1: Write failing tests**

Create `src/stores/__tests__/programStore.training.test.js`:

```js
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useProgramStore } from '../programStore'

const prog1 = {
  id: 'p1',
  name: 'Aufwärmen',
  ablaeufe: [{ id: 'abl-1', name: 'A1', rangeId: 'r1', rangeName: 'Platz 1', steps: [] }],
}
const prog2 = {
  id: 'p2',
  name: 'Hauptteil',
  ablaeufe: [{ id: 'abl-2', name: 'A2', rangeId: 'r2', rangeName: 'Platz 2', steps: [] }],
}

describe('programStore — Training CRUD', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('createTraining adds a training with snapshotted programmes', () => {
    const store = useProgramStore()
    store.createTraining('Training 1', [prog1, prog2])
    expect(store.savedTrainings).toHaveLength(1)
    expect(store.savedTrainings[0].name).toBe('Training 1')
    expect(store.savedTrainings[0].programmes).toHaveLength(2)
    expect(store.savedTrainings[0].programmes[0].name).toBe('Aufwärmen')
  })

  it('createTraining persists to localStorage', () => {
    const store = useProgramStore()
    store.createTraining('Training 1', [prog1])
    const keys = Object.keys(localStorage).filter(k => k.includes('_training_'))
    expect(keys).toHaveLength(1)
    const data = JSON.parse(localStorage.getItem(keys[0]))
    expect(data.trainingName).toBe('Training 1')
    expect(data.programmes).toHaveLength(1)
  })

  it('deleteTraining removes from memory and localStorage', () => {
    const store = useProgramStore()
    store.createTraining('Training 1', [prog1])
    const id = store.savedTrainings[0].id
    store.deleteTraining(id)
    expect(store.savedTrainings).toHaveLength(0)
    expect(localStorage.getItem(id)).toBeNull()
  })

  it('renameTraining updates name in memory and localStorage', () => {
    const store = useProgramStore()
    store.createTraining('Alter Name', [prog1])
    const id = store.savedTrainings[0].id
    store.renameTraining(id, 'Neuer Name')
    expect(store.savedTrainings[0].name).toBe('Neuer Name')
    const data = JSON.parse(localStorage.getItem(id))
    expect(data.trainingName).toBe('Neuer Name')
  })

  it('loadTrainingsFromStorage reloads after re-init', () => {
    const store = useProgramStore()
    store.createTraining('Training 1', [prog1, prog2])
    setActivePinia(createPinia())
    const store2 = useProgramStore()
    expect(store2.savedTrainings).toHaveLength(1)
    expect(store2.savedTrainings[0].programmes).toHaveLength(2)
  })
})
```

- [ ] **Step 1.2: Run tests to verify they fail**

```
npm run test src/stores/__tests__/programStore.training.test.js
```

Expected: FAIL — `savedTrainings is not a function` or similar.

- [ ] **Step 1.3: Add Training CRUD to `programStore.js`**

Inside `useProgramStore` (after the existing `saveProgram` function, before the `return` statement), add:

```js
// ── Training template persistence ─────────────────────────────────────────
const savedTrainings = ref([]);

const getTrainingPrefix = () => {
  const authStore = useAuthStore();
  return `${authStore.userName ?? 'anonymous'}_training_`;
};

const nextTrainingKey = () => {
  const prefix = getTrainingPrefix();
  const existing = Object.keys(localStorage)
    .filter((k) => k.startsWith(prefix))
    .map((k) => parseInt(k.slice(prefix.length), 10))
    .filter((n) => !isNaN(n));
  return `${prefix}${existing.length > 0 ? Math.max(...existing) + 1 : 1}`;
};

const loadTrainingsFromStorage = () => {
  const prefix = getTrainingPrefix();
  savedTrainings.value = Object.keys(localStorage)
    .filter((k) => k.startsWith(prefix))
    .map((key) => {
      try {
        const data = JSON.parse(localStorage.getItem(key));
        return { id: key, name: data.trainingName, programmes: data.programmes ?? [] };
      } catch {
        return null;
      }
    })
    .filter(Boolean)
    .sort((a, b) => {
      const numA = parseInt(a.id.slice(prefix.length), 10);
      const numB = parseInt(b.id.slice(prefix.length), 10);
      return numA - numB;
    });
};

const createTraining = (trainingName, selectedProgrammes) => {
  if (selectedProgrammes.length === 0) return;
  const name = trainingName?.trim() || `Training ${savedTrainings.value.length + 1}`;
  const key = nextTrainingKey();
  const programmes = selectedProgrammes.map((prog) => ({
    id: prog.id,
    name: prog.name,
    ablaeufe: prog.ablaeufe.map((abl) => ({ ...abl })),
  }));
  localStorage.setItem(key, JSON.stringify({ trainingName: name, programmes }));
  savedTrainings.value = [...savedTrainings.value, { id: key, name, programmes }];
};

const deleteTraining = (trainingId) => {
  localStorage.removeItem(trainingId);
  savedTrainings.value = savedTrainings.value.filter((t) => t.id !== trainingId);
};

const renameTraining = (trainingId, newName) => {
  const training = savedTrainings.value.find((t) => t.id === trainingId);
  if (!training) return;
  training.name = newName;
  try {
    const stored = JSON.parse(localStorage.getItem(trainingId));
    if (stored) {
      stored.trainingName = newName;
      localStorage.setItem(trainingId, JSON.stringify(stored));
    }
  } catch { /* ignorieren */ }
};

loadTrainingsFromStorage();
```

Also add to the `return` statement at the bottom of `useProgramStore`:

```js
// Training actions
savedTrainings,
loadTrainingsFromStorage,
createTraining,
deleteTraining,
renameTraining,
```

- [ ] **Step 1.4: Run tests to verify they pass**

```
npm run test src/stores/__tests__/programStore.training.test.js
```

Expected: All 5 tests PASS.

- [ ] **Step 1.5: Commit**

```bash
git add src/stores/programStore.js src/stores/__tests__/programStore.training.test.js
git commit -m "[ui] Add Training template CRUD to programStore"
```

---

## Task 2: `startTraining()` in `activeProgramStore`

**Files:**
- Modify: `src/stores/activeProgramStore.js`
- Modify: `src/stores/__tests__/activeProgramStore.test.js`

- [ ] **Step 2.1: Write failing tests**

Append to `src/stores/__tests__/activeProgramStore.test.js`:

```js
const trainingTemplate = {
  id: 'training-1',
  name: 'Training Woche 1',
  programmes: [
    {
      id: 'prog-1',
      name: 'Aufwärmen',
      ablaeufe: [
        { id: 'abl-1', name: 'A1', alias: 'A1', rangeId: 'r1', rangeName: 'Platz 1', steps: [{ id: 's1', type: 'solo' }] },
      ],
    },
    {
      id: 'prog-2',
      name: 'Hauptteil',
      ablaeufe: [
        { id: 'abl-2', name: 'A2', alias: 'A2', rangeId: 'r2', rangeName: 'Platz 2', steps: [{ id: 's2', type: 'solo' }] },
      ],
    },
  ],
}

describe('useActiveProgramStore — Training', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('startTraining creates instance with type training', () => {
    const store = useActiveProgramStore()
    const inst = store.startTraining(trainingTemplate, players)
    expect(inst.type).toBe('training')
    expect(store.activeInstances).toHaveLength(1)
  })

  it('startTraining creates one phase per programme', () => {
    const store = useActiveProgramStore()
    const inst = store.startTraining(trainingTemplate, players)
    expect(inst.phases).toHaveLength(2)
    expect(inst.phases[0].programmeName).toBe('Aufwärmen')
    expect(inst.phases[1].programmeName).toBe('Hauptteil')
  })

  it('startTraining sets first phase active, rest pending', () => {
    const store = useActiveProgramStore()
    const inst = store.startTraining(trainingTemplate, players)
    expect(inst.phases[0].status).toBe('active')
    expect(inst.phases[1].status).toBe('pending')
    expect(inst.currentPhaseIndex).toBe(0)
  })

  it('startTraining creates blocks for each ablauf within each phase', () => {
    const store = useActiveProgramStore()
    const inst = store.startTraining(trainingTemplate, players)
    expect(inst.phases[0].blocks).toHaveLength(1)
    expect(inst.phases[0].blocks[0].ablaufId).toBe('abl-1')
    expect(inst.phases[0].blocks[0].status).toBe('pending')
    expect(inst.phases[1].blocks).toHaveLength(1)
    expect(inst.phases[1].blocks[0].ablaufId).toBe('abl-2')
  })

  it('startTraining persists to localStorage', () => {
    const store = useActiveProgramStore()
    store.startTraining(trainingTemplate, players)
    const saved = JSON.parse(localStorage.getItem('sg_active_program_instances'))
    expect(saved).toHaveLength(1)
    expect(saved[0].type).toBe('training')
  })
})
```

- [ ] **Step 2.2: Run tests to verify they fail**

```
npm run test src/stores/__tests__/activeProgramStore.test.js
```

Expected: 5 new tests FAIL — `store.startTraining is not a function`.

- [ ] **Step 2.3: Add `startTraining()` to `activeProgramStore.js`**

Inside `useActiveProgramStore`, after the `startProgram` function:

```js
const startTraining = (template, players) => {
  const instance = {
    instanceId: generateUUID(),
    type: 'training',
    templateId: template.id,
    templateName: template.name,
    players: [...players],
    startedAt: Date.now(),
    currentPhaseIndex: 0,
    phases: template.programmes.map((prog, phaseIndex) => ({
      phaseIndex,
      programmeId: prog.id,
      programmeName: prog.name,
      status: phaseIndex === 0 ? 'active' : 'pending',
      blocks: prog.ablaeufe.map((ablauf) => ({
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
    })),
  }
  activeInstances.value.push(instance)
  _saveActive()
  return instance
}
```

Add `startTraining` to the `return` statement.

- [ ] **Step 2.4: Run tests to verify they pass**

```
npm run test src/stores/__tests__/activeProgramStore.test.js
```

Expected: All tests (existing + 5 new) PASS.

- [ ] **Step 2.5: Commit**

```bash
git add src/stores/activeProgramStore.js src/stores/__tests__/activeProgramStore.test.js
git commit -m "[ui] Add startTraining() to activeProgramStore"
```

---

## Task 3: Phase-aware `getBlocksForRange()` + `instanceType` field

**Files:**
- Modify: `src/stores/activeProgramStore.js`
- Modify: `src/stores/__tests__/activeProgramStore.test.js`

- [ ] **Step 3.1: Write failing tests**

Append to the Training `describe` block in `activeProgramStore.test.js`:

```js
it('getBlocksForRange returns only current-phase blocks for training', () => {
  const store = useActiveProgramStore()
  store.startTraining(trainingTemplate, players)
  // Phase 0 is active (r1), phase 1 is pending (r2)
  expect(store.getBlocksForRange('r1')).toHaveLength(1)
  expect(store.getBlocksForRange('r2')).toHaveLength(0)
})

it('getBlocksForRange attaches instanceType training to training blocks', () => {
  const store = useActiveProgramStore()
  store.startTraining(trainingTemplate, players)
  const blocks = store.getBlocksForRange('r1')
  expect(blocks[0].instanceType).toBe('training')
  expect(blocks[0].programmeName).toBe('Aufwärmen')
})

it('getBlocksForRange attaches instanceType programm to programme blocks', () => {
  const store = useActiveProgramStore()
  store.startProgram(template, players)
  const blocks = store.getBlocksForRange('r1')
  expect(blocks[0].instanceType).toBe('programm')
})
```

- [ ] **Step 3.2: Run tests to verify they fail**

```
npm run test src/stores/__tests__/activeProgramStore.test.js
```

Expected: 3 new tests FAIL.

- [ ] **Step 3.3: Replace `getBlocksForRange` in `activeProgramStore.js`**

Replace the existing `getBlocksForRange` function with:

```js
const getBlocksForRange = (rangeId) => {
  const result = []
  for (const inst of activeInstances.value) {
    if (inst.type === 'training') {
      const phase = inst.phases[inst.currentPhaseIndex]
      if (!phase) continue
      for (const block of phase.blocks) {
        if (block.rangeId === rangeId && block.status !== 'done') {
          result.push({
            ...block,
            instanceId: inst.instanceId,
            templateName: inst.templateName,
            programmeName: phase.programmeName,
            players: inst.players,
            instanceType: 'training',
          })
        }
      }
    } else {
      for (const block of inst.blocks) {
        if (block.rangeId === rangeId && block.status !== 'done') {
          result.push({
            ...block,
            instanceId: inst.instanceId,
            templateName: inst.templateName,
            players: inst.players,
            instanceType: 'programm',
          })
        }
      }
    }
  }
  return result
}
```

- [ ] **Step 3.4: Run tests to verify they pass**

```
npm run test src/stores/__tests__/activeProgramStore.test.js
```

Expected: All tests PASS.

- [ ] **Step 3.5: Commit**

```bash
git add src/stores/activeProgramStore.js src/stores/__tests__/activeProgramStore.test.js
git commit -m "[ui] Extend getBlocksForRange with phase-aware Training support"
```

---

## Task 4: Training branch in `markBlockDone()` with phase advancement

**Files:**
- Modify: `src/stores/activeProgramStore.js`
- Modify: `src/stores/__tests__/activeProgramStore.test.js`

- [ ] **Step 4.1: Write failing tests**

Append to the Training `describe` block in `activeProgramStore.test.js`:

```js
it('markBlockDone on training advances to next phase when all blocks in phase are done', () => {
  const store = useActiveProgramStore()
  const inst = store.startTraining(trainingTemplate, players)
  const block = inst.phases[0].blocks[0]
  store.markBlockDone(inst.instanceId, block.blockId, [])
  const updated = store.activeInstances[0]
  expect(updated.currentPhaseIndex).toBe(1)
  expect(updated.phases[0].status).toBe('done')
  expect(updated.phases[1].status).toBe('active')
  expect(store.getBlocksForRange('r2')).toHaveLength(1)
})

it('markBlockDone on training completes instance when last phase done', () => {
  const store = useActiveProgramStore()
  const inst = store.startTraining(trainingTemplate, players)
  const block0 = inst.phases[0].blocks[0]
  store.markBlockDone(inst.instanceId, block0.blockId, [])
  const block1 = store.activeInstances[0].phases[1].blocks[0]
  store.markBlockDone(inst.instanceId, block1.blockId, [])
  expect(store.activeInstances).toHaveLength(0)
  expect(store.completedInstances).toHaveLength(1)
  expect(store.completedInstances[0].type).toBe('training')
  expect(store.completedInstances[0].completedAt).toBeDefined()
})

it('markBlockDone on training stores result on block', () => {
  const store = useActiveProgramStore()
  const inst = store.startTraining(trainingTemplate, players)
  const block = inst.phases[0].blocks[0]
  const results = [{ playerId: 'p1', displayName: 'Schütze 1', totalPoints: 5, maxPoints: 5, stepStates: [] }]
  store.markBlockDone(inst.instanceId, block.blockId, results)
  expect(store.activeInstances[0].phases[0].blocks[0].result.playerResults).toHaveLength(1)
})
```

- [ ] **Step 4.2: Run tests to verify they fail**

```
npm run test src/stores/__tests__/activeProgramStore.test.js
```

Expected: 3 new tests FAIL.

- [ ] **Step 4.3: Replace `markBlockDone` in `activeProgramStore.js`**

Replace the existing `markBlockDone` function with:

```js
const markBlockDone = (instanceId, blockId, playerResults) => {
  const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
  if (!inst) return

  if (inst.type === 'training') {
    const phase = inst.phases[inst.currentPhaseIndex]
    if (!phase) return
    const block = phase.blocks.find((b) => b.blockId === blockId)
    if (!block) return
    block.status = 'done'
    block.completedAt = Date.now()
    block.result = { playerResults }

    if (phase.blocks.every((b) => b.status === 'done')) {
      phase.status = 'done'
      const nextIndex = inst.currentPhaseIndex + 1
      if (nextIndex < inst.phases.length) {
        inst.currentPhaseIndex = nextIndex
        inst.phases[nextIndex].status = 'active'
      } else {
        completedInstances.value.push({ ...inst, completedAt: Date.now() })
        activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== instanceId)
        _saveCompleted()
      }
    }
  } else {
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
  }
  _saveActive()
}
```

- [ ] **Step 4.4: Run all store tests to verify nothing broke**

```
npm run test src/stores/__tests__/
```

Expected: All tests PASS (existing Programme tests still green).

- [ ] **Step 4.5: Commit**

```bash
git add src/stores/activeProgramStore.js src/stores/__tests__/activeProgramStore.test.js
git commit -m "[ui] Add Training phase advancement to markBlockDone"
```

---

## Task 5: Route + ShooterHomeView Training tile

**Files:**
- Modify: `src/router/index.js`
- Modify: `src/views/shooter/ShooterHomeView.vue`

- [ ] **Step 5.1: Add `/training` route to `src/router/index.js`**

After the `'/programmes'` route entry:

```js
{ path: '/training', component: () => import('@/views/shooter/TrainingManagementView.vue'), meta: { layout: 'shooter' } },
```

- [ ] **Step 5.2: Add Training tile to `ShooterHomeView.vue`**

In the `<div class="app-grid">`, after the "Programme" button and before "Wettkampf":

```html
<button class="app-tile app-tile--available" @click="router.push('/training')">
  <div class="tile-icon-wrap tile-icon-wrap--teal">
    <Icons icon="program" :size="36" color="#4fd1c5" />
  </div>
  <span class="tile-label">Training</span>
  <span class="tile-desc">Strukturierte Einheiten</span>
</button>
```

Also add the CSS class for the teal color in the `<style scoped>` section:

```css
.tile-icon-wrap--teal {
  background: rgba(79, 209, 197, 0.15);
}
```

- [ ] **Step 5.3: Commit**

```bash
git add src/router/index.js src/views/shooter/ShooterHomeView.vue
git commit -m "[ui] Add /training route and Training tile to ShooterHomeView"
```

---

## Task 6: `TrainingManagementView.vue`

**Files:**
- Create: `src/views/shooter/TrainingManagementView.vue`

- [ ] **Step 6.1: Create the view**

Create `src/views/shooter/TrainingManagementView.vue`:

```vue
<template>
  <div class="training-view">
    <!-- Top bar -->
    <div class="top-bar">
      <button class="back-btn" @click="router.push('/home')">
        <Icons icon="chevronLeft" :size="18" color="rgba(255,255,255,0.7)" />
      </button>
      <h1 class="page-title">Training</h1>
      <div class="top-bar-spacer" />
    </div>

    <!-- Tab toggle -->
    <div class="tab-toggle">
      <button class="tab-btn" :class="{ active: activeTab === 'trainings' }" @click="activeTab = 'trainings'">
        Trainings
      </button>
      <button class="tab-btn" :class="{ active: activeTab === 'active' }" @click="activeTab = 'active'">
        Aktive
      </button>
      <button class="tab-btn" :class="{ active: activeTab === 'completed' }" @click="activeTab = 'completed'">
        Abgeschlossen
      </button>
    </div>

    <!-- Content -->
    <div class="content">

      <!-- ══ TAB 1: TEMPLATES ══ -->
      <template v-if="activeTab === 'trainings'">
        <section class="block">
          <div class="block-header">
            <div class="block-title-row">
              <span class="block-title">Trainings</span>
              <span class="block-badge">{{ programStore.savedTrainings.length }}</span>
            </div>
            <p class="block-desc">Geordnete Abfolge von 1–n Programmen. Programme werden nacheinander gespielt.</p>
          </div>

          <div v-if="programStore.savedTrainings.length > 0" class="training-list">
            <div
              v-for="training in programStore.savedTrainings"
              :key="training.id"
              class="training-card"
              :class="{ expanded: expandedTrainingId === training.id }"
            >
              <div class="card-main" @click="renamingId !== training.id ? toggleExpand(training.id) : null">
                <div class="card-info">
                  <template v-if="renamingId === training.id">
                    <input
                      ref="renameInputRef"
                      v-model="renameValue"
                      class="rename-input"
                      type="text"
                      maxlength="50"
                      @keyup.enter="confirmRename(training.id)"
                      @keyup.escape="renamingId = null"
                      @click.stop
                    />
                  </template>
                  <template v-else>
                    <span class="card-name">{{ training.name }}</span>
                    <div class="card-meta-row">
                      <span class="card-meta">{{ training.programmes.length }} Programme</span>
                      <span class="meta-dot">·</span>
                      <span class="card-meta">{{ totalThrows(training) }} Würfe</span>
                    </div>
                  </template>
                </div>
                <div v-if="renamingId === training.id" class="card-actions-inline" @click.stop>
                  <button class="icon-btn icon-btn--confirm" @click="confirmRename(training.id)">
                    <Icons icon="check" :size="13" color="#48bb78" />
                  </button>
                  <button class="icon-btn" @click="renamingId = null">
                    <Icons icon="x" :size="13" color="rgba(255,255,255,0.4)" />
                  </button>
                </div>
                <div v-else class="card-actions-inline" @click.stop>
                  <button class="icon-btn" @click="startRename(training)">
                    <Icons icon="edit" :size="13" color="rgba(255,255,255,0.4)" />
                  </button>
                </div>
                <Icons
                  v-if="renamingId !== training.id"
                  icon="chevronRight"
                  :size="14"
                  color="rgba(255,255,255,0.3)"
                  class="expand-icon"
                  :class="{ rotated: expandedTrainingId === training.id }"
                />
              </div>

              <div v-if="expandedTrainingId === training.id" class="card-detail">
                <div class="phase-list">
                  <div v-for="(prog, i) in training.programmes" :key="prog.id" class="phase-row">
                    <span class="phase-num">{{ i + 1 }}</span>
                    <span class="phase-name">{{ prog.name }}</span>
                    <span class="phase-meta">{{ progThrows(prog) }} W</span>
                  </div>
                </div>
                <div class="card-actions">
                  <button class="action-btn action-btn--start" @click.stop="openStartModal(training)">
                    <Icons icon="play" :size="14" color="#4fc3f7" />
                    Starten
                  </button>
                  <button class="action-btn action-btn--danger" @click.stop="programStore.deleteTraining(training.id)">
                    <Icons icon="trash" :size="13" color="rgba(252,129,129,0.7)" />
                    Löschen
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div v-if="programStore.savedTrainings.length === 0 && !creatingTraining" class="empty-block">
            <Icons icon="program" :size="36" color="rgba(255,255,255,0.07)" />
            <p>Noch keine Trainings</p>
            <p class="empty-hint">Kombiniere Programme zu einem Training</p>
          </div>

          <!-- Create training panel -->
          <div v-if="creatingTraining" class="create-panel">
            <div class="create-panel-header">
              <span class="create-panel-title">Neues Training</span>
            </div>
            <div class="create-field">
              <label class="create-label">Name</label>
              <input v-model="newTrainingName" class="create-input" type="text" placeholder="z.B. Training Woche 1" maxlength="50" />
            </div>
            <div class="create-field">
              <label class="create-label">
                Programme auswählen
                <span class="create-label-count">{{ selectedProgrammes.length }} gewählt</span>
              </label>
              <p v-if="programStore.savedPrograms.length === 0" class="create-hint">
                Noch keine Programme vorhanden.
              </p>
              <p v-else class="create-hint">Tippe auf Programme um sie auszuwählen. Die Reihenfolge entspricht der Spielreihenfolge.</p>
            </div>

            <div v-if="programStore.savedPrograms.length > 0" class="programme-picker">
              <div
                v-for="prog in programStore.savedPrograms"
                :key="prog.id"
                class="picker-item"
                :class="{ selected: isSelected(prog.id) }"
                @click="toggleProgramme(prog)"
              >
                <div class="picker-check">
                  <span v-if="isSelected(prog.id)" class="picker-order">{{ selectedOrder(prog.id) }}</span>
                  <Icons v-else icon="plus" :size="11" color="rgba(255,255,255,0.3)" />
                </div>
                <span class="picker-name">{{ prog.name }}</span>
                <span class="picker-meta">{{ progThrows(prog) }} W</span>
              </div>
            </div>

            <div class="create-actions">
              <button class="action-btn action-btn--cancel" @click="cancelCreate">Abbrechen</button>
              <button
                class="action-btn action-btn--create"
                :disabled="selectedProgrammes.length === 0"
                @click="confirmCreate"
              >
                <Icons icon="check" :size="13" color="#fff" />
                Erstellen
              </button>
            </div>
          </div>

          <button v-if="!creatingTraining" class="new-training-btn" :disabled="programStore.savedPrograms.length === 0" @click="startCreate">
            <Icons icon="plus" :size="16" color="rgba(79,195,247,0.8)" />
            Neues Training
          </button>
        </section>
      </template>

      <!-- ══ TAB 2: AKTIVE TRAININGS ══ -->
      <template v-if="activeTab === 'active'">
        <section class="block">
          <div class="block-header">
            <div class="block-title-row">
              <span class="block-title">Aktive Trainings</span>
              <span class="block-badge">{{ activeTrainings.length }}</span>
            </div>
            <p class="block-desc">Laufende Trainings. Blöcke der aktuellen Phase erscheinen auf den zugehörigen Plätzen.</p>
          </div>

          <div v-if="activeTrainings.length > 0" class="active-list">
            <div v-for="inst in activeTrainings" :key="inst.instanceId" class="session-card">
              <div class="session-main">
                <div class="session-info">
                  <span class="session-name">{{ inst.templateName }}</span>
                  <span class="session-meta">
                    {{ inst.players.map(p => p.displayName).join(', ') }}
                  </span>
                </div>
                <button class="icon-btn" title="Stoppen" @click="stopTraining(inst)">
                  <Icons icon="x" :size="13" color="rgba(252,129,129,0.6)" />
                </button>
              </div>

              <div class="phase-progress">
                <span class="phase-progress-label">
                  Phase {{ inst.currentPhaseIndex + 1 }} von {{ inst.phases.length }} —
                  {{ inst.phases[inst.currentPhaseIndex]?.programmeName }}
                </span>
                <div class="progress-bar-wrap">
                  <div class="progress-bar">
                    <div
                      class="progress-fill"
                      :style="{ width: (inst.phases.filter(p => p.status === 'done').length / inst.phases.length * 100) + '%' }"
                    />
                  </div>
                  <span class="progress-label">
                    {{ inst.phases.filter(p => p.status === 'done').length }}/{{ inst.phases.length }}
                  </span>
                </div>
              </div>

              <div class="block-status-list">
                <div
                  v-for="block in inst.phases[inst.currentPhaseIndex]?.blocks ?? []"
                  :key="block.blockId"
                  class="block-status-row"
                >
                  <span class="block-status-dot" :class="`dot-${block.status}`" />
                  <span class="block-status-range">{{ block.rangeName ?? 'Kein Platz' }}</span>
                  <span class="block-status-name">{{ block.ablaufAlias }}</span>
                </div>
              </div>
            </div>
          </div>

          <div v-if="activeTrainings.length === 0" class="empty-block">
            <Icons icon="program" :size="36" color="rgba(255,255,255,0.07)" />
            <p>Keine aktiven Trainings</p>
            <p class="empty-hint">Starte ein Training über den Trainings-Tab.</p>
          </div>
        </section>
      </template>

      <!-- ══ TAB 3: ABGESCHLOSSENE TRAININGS ══ -->
      <template v-if="activeTab === 'completed'">
        <section class="block">
          <div class="block-header">
            <div class="block-title-row">
              <span class="block-title">Abgeschlossene Trainings</span>
              <span class="block-badge">{{ completedTrainings.length }}</span>
            </div>
            <p class="block-desc">Fertig gespielte Trainings.</p>
          </div>

          <div v-if="completedTrainings.length > 0" class="active-list">
            <div
              v-for="inst in completedTrainings"
              :key="inst.instanceId"
              class="session-card completed-card"
              @click="toggleCompletedCard(inst.instanceId)"
            >
              <div class="session-main">
                <span class="session-name">{{ inst.templateName }}</span>
                <span class="completed-date">{{ new Date(inst.completedAt).toLocaleDateString('de-CH') }}</span>
              </div>

              <div class="player-summaries">
                <div v-for="ps in getPlayerSummaries(inst)" :key="ps.player.id" class="player-summary-chip">
                  <span class="ps-name">{{ ps.player.displayName }}</span>
                  <span class="ps-pts">{{ ps.totalPts }}/{{ ps.maxPts }}</span>
                  <span class="ps-pct">{{ ps.hitPct }}%</span>
                </div>
              </div>

              <div v-if="expandedCompletedCards.has(inst.instanceId)" class="completed-detail" @click.stop>
                <div v-for="ps in getPlayerSummaries(inst)" :key="ps.player.id" class="player-detail-section">
                  <div class="player-detail-header">
                    <span class="pd-name">{{ ps.player.displayName }}</span>
                    <span class="pd-total">{{ ps.totalPts }}/{{ ps.maxPts }}</span>
                  </div>
                  <div v-for="phase in inst.phases" :key="phase.phaseIndex" class="phase-detail-group">
                    <span class="phase-detail-label">{{ phase.programmeName }}</span>
                    <div
                      v-for="block in phase.blocks"
                      :key="block.blockId"
                      class="completed-score-row"
                    >
                      <span class="completed-score-block">
                        <span class="completed-score-range">{{ block.rangeName }}</span>
                        {{ block.ablaufAlias }}
                      </span>
                      <span class="completed-score-pts">
                        {{
                          block.result?.playerResults?.find(r => r.playerId === ps.player.id)?.totalPoints ?? '–'
                        }}/{{
                          block.result?.playerResults?.find(r => r.playerId === ps.player.id)?.maxPoints ?? '–'
                        }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="completedTrainings.length === 0" class="empty-block">
            <Icons icon="program" :size="36" color="rgba(255,255,255,0.07)" />
            <p>Noch keine abgeschlossenen Trainings</p>
          </div>
        </section>
      </template>

    </div>

    <!-- Start modal -->
    <div v-if="startingTraining" class="modal-overlay" @click.self="cancelStartModal">
      <div class="start-modal">
        <h3 class="modal-title">{{ startingTraining.name }} starten</h3>
        <div class="modal-players">
          <div v-for="(player, i) in modalPlayers" :key="player.id" class="modal-player-row">
            <span class="modal-player-num">{{ i + 1 }}:</span>
            <input
              v-model="player.displayName"
              class="modal-player-input"
              type="text"
              :placeholder="`Schütze ${i + 1}`"
              maxlength="30"
            />
            <button v-if="modalPlayers.length > 1" class="icon-btn icon-btn--danger" @click="removeModalPlayer(i)">
              <Icons icon="x" :size="11" color="#fc8181" />
            </button>
          </div>
        </div>
        <button class="add-player-btn" @click="addModalPlayer">+ Schütze hinzufügen</button>
        <div class="modal-actions">
          <button class="action-btn action-btn--cancel" @click="cancelStartModal">Abbrechen</button>
          <button
            class="action-btn action-btn--start"
            :disabled="modalPlayers.length === 0"
            @click="confirmStart"
          >
            <Icons icon="play" :size="14" color="#4fc3f7" />
            Starten
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useProgramStore } from '@/stores/programStore.js'
import { useActiveProgramStore } from '@/stores/activeProgramStore.js'
import Icons from '@/components/Icons.vue'

const router = useRouter()
const programStore = useProgramStore()
const activeProgramStore = useActiveProgramStore()

const activeTab = ref('trainings')

// ── Active / Completed filtered by type ───────────────────────────────────
const activeTrainings = computed(() =>
  activeProgramStore.activeInstances.filter(i => i.type === 'training')
)
const completedTrainings = computed(() =>
  activeProgramStore.completedInstances.filter(i => i.type === 'training')
)

// ── Expand / rename training cards ────────────────────────────────────────
const expandedTrainingId = ref(null)
const renamingId = ref(null)
const renameValue = ref('')
const renameInputRef = ref(null)

const toggleExpand = (id) => {
  expandedTrainingId.value = expandedTrainingId.value === id ? null : id
}

const startRename = (training) => {
  renamingId.value = training.id
  renameValue.value = training.name
  nextTick(() => renameInputRef.value?.[0]?.focus?.() ?? renameInputRef.value?.focus?.())
}

const confirmRename = (id) => {
  if (renameValue.value.trim()) programStore.renameTraining(id, renameValue.value.trim())
  renamingId.value = null
}

// ── Create training flow ──────────────────────────────────────────────────
const creatingTraining = ref(false)
const newTrainingName = ref('')
const selectedProgrammes = ref([]) // ordered array of { id, name, ablaeufe }

const startCreate = () => {
  creatingTraining.value = true
  newTrainingName.value = ''
  selectedProgrammes.value = []
}

const cancelCreate = () => {
  creatingTraining.value = false
  selectedProgrammes.value = []
}

const isSelected = (progId) => selectedProgrammes.value.some(p => p.id === progId)

const selectedOrder = (progId) => selectedProgrammes.value.findIndex(p => p.id === progId) + 1

const toggleProgramme = (prog) => {
  const idx = selectedProgrammes.value.findIndex(p => p.id === prog.id)
  if (idx >= 0) {
    selectedProgrammes.value = selectedProgrammes.value.filter(p => p.id !== prog.id)
  } else {
    selectedProgrammes.value = [...selectedProgrammes.value, prog]
  }
}

const confirmCreate = () => {
  if (selectedProgrammes.value.length === 0) return
  programStore.createTraining(newTrainingName.value, selectedProgrammes.value)
  creatingTraining.value = false
  selectedProgrammes.value = []
  newTrainingName.value = ''
}

// ── Start modal ───────────────────────────────────────────────────────────
const startingTraining = ref(null)
const modalPlayers = ref([])
let _nextPlayerId = 1

const openStartModal = (training) => {
  startingTraining.value = training
  _nextPlayerId = 1
  modalPlayers.value = [{ id: `sp-${_nextPlayerId++}`, displayName: 'Schütze 1', type: 'guest' }]
}

const addModalPlayer = () => {
  const n = modalPlayers.value.length + 1
  modalPlayers.value.push({ id: `sp-${_nextPlayerId++}`, displayName: `Schütze ${n}`, type: 'guest' })
}

const removeModalPlayer = (i) => { modalPlayers.value.splice(i, 1) }

const cancelStartModal = () => { startingTraining.value = null; modalPlayers.value = [] }

const confirmStart = () => {
  if (!startingTraining.value || modalPlayers.value.length === 0) return
  activeProgramStore.startTraining(startingTraining.value, modalPlayers.value)
  startingTraining.value = null
  modalPlayers.value = []
  activeTab.value = 'active'
}

// ── Stop training ─────────────────────────────────────────────────────────
const stopTraining = (inst) => {
  if (confirm(`Möchtest du „${inst.templateName}" wirklich abbrechen?`)) {
    activeProgramStore.stopInstance(inst.instanceId)
  }
}

// ── Completed cards ───────────────────────────────────────────────────────
const expandedCompletedCards = ref(new Set())

const toggleCompletedCard = (instanceId) => {
  const s = new Set(expandedCompletedCards.value)
  if (s.has(instanceId)) s.delete(instanceId)
  else s.add(instanceId)
  expandedCompletedCards.value = s
}

const getPlayerSummaries = (inst) => inst.players.map((player) => {
  let totalPts = 0, maxPts = 0
  for (const phase of inst.phases) {
    for (const block of phase.blocks) {
      const pr = block.result?.playerResults?.find(r => r.playerId === player.id)
      if (!pr) continue
      totalPts += pr.totalPoints
      maxPts += pr.maxPoints
    }
  }
  return { player, totalPts, maxPts, hitPct: maxPts > 0 ? Math.round(totalPts / maxPts * 100) : 0 }
})

// ── Helpers ───────────────────────────────────────────────────────────────
const stepCount = (steps) => {
  let count = 0
  for (const s of steps) {
    if (s.type === 'solo') count += 1
    else if (s.type === 'pair' || s.type === 'a_schuss' || s.type === 'raffale') count += 2
  }
  return count
}

const progThrows = (prog) =>
  (prog.ablaeufe ?? []).reduce((sum, abl) => sum + stepCount(abl.steps ?? []), 0)

const totalThrows = (training) =>
  (training.programmes ?? []).reduce((sum, prog) => sum + progThrows(prog), 0)
</script>

<style scoped>
.training-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #1a1a2e;
  color: #fff;
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}

.back-btn {
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 10px;
  width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  transition: background 0.15s;
  flex-shrink: 0;
}
.back-btn:hover { background: rgba(255,255,255,0.1); }

.page-title { font-size: 17px; font-weight: 700; margin: 0; letter-spacing: -0.3px; }
.top-bar-spacer { flex: 1; }

.tab-toggle {
  display: flex; gap: 8px; padding: 16px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}

.tab-btn {
  flex: 1; padding: 10px 16px;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 10px;
  color: rgba(255,255,255,0.4);
  font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.tab-btn.active {
  background: rgba(79,195,247,0.18);
  border-color: rgba(79,195,247,0.4);
  color: #4fc3f7;
}
.tab-btn:hover:not(.active) { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.6); }

.content { flex: 1; overflow-y: auto; padding: 0 0 40px; }

.block { padding: 20px 16px 0; }

.block-header { margin-bottom: 16px; }

.block-title-row { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }

.block-title { font-size: 18px; font-weight: 700; letter-spacing: -0.3px; }

.block-badge {
  font-size: 11px; font-weight: 700;
  color: rgba(255,255,255,0.4);
  background: rgba(255,255,255,0.08);
  border-radius: 20px; padding: 2px 8px;
}

.block-desc { font-size: 12px; color: rgba(255,255,255,0.3); margin: 0; line-height: 1.5; }

/* ── Training cards ── */
.training-list { display: flex; flex-direction: column; gap: 10px; margin-bottom: 14px; }

.training-card {
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 16px; overflow: hidden;
  transition: border-color 0.15s;
}
.training-card.expanded { border-color: rgba(79,195,247,0.25); }

.card-main {
  display: flex; align-items: center; justify-content: space-between;
  gap: 10px; padding: 14px 16px; cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.card-info { display: flex; flex-direction: column; gap: 4px; min-width: 0; flex: 1; }

.card-name { font-size: 15px; font-weight: 600; color: #fff; }

.card-meta-row { display: flex; align-items: center; gap: 6px; }

.card-meta { font-size: 12px; color: rgba(255,255,255,0.3); }

.meta-dot { font-size: 12px; color: rgba(255,255,255,0.15); }

.card-actions-inline { display: flex; gap: 4px; flex-shrink: 0; }

.expand-icon { flex-shrink: 0; transition: transform 0.2s; }
.expand-icon.rotated { transform: rotate(90deg); }

.card-detail {
  padding: 0 16px 16px;
  border-top: 1px solid rgba(255,255,255,0.06);
  display: flex; flex-direction: column; gap: 12px;
}

.phase-list { display: flex; flex-direction: column; gap: 4px; padding-top: 12px; }

.phase-row {
  display: flex; align-items: center; gap: 10px;
  font-size: 13px; padding: 4px 0;
}

.phase-num {
  width: 20px; height: 20px; border-radius: 50%;
  background: rgba(79,195,247,0.15);
  color: rgba(79,195,247,0.9);
  font-size: 11px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}

.phase-name { flex: 1; color: rgba(255,255,255,0.8); }

.phase-meta { font-size: 11px; color: rgba(255,255,255,0.3); }

.card-actions { display: flex; gap: 8px; }

/* ── New training button ── */
.new-training-btn {
  width: 100%; display: flex; align-items: center; justify-content: center;
  gap: 8px; padding: 13px;
  background: transparent;
  border: 1.5px dashed rgba(79,195,247,0.25);
  border-radius: 14px;
  color: rgba(79,195,247,0.6);
  font-size: 14px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s; margin-top: 4px;
}
.new-training-btn:hover:not(:disabled) {
  background: rgba(79,195,247,0.06);
  border-color: rgba(79,195,247,0.4);
  color: rgba(79,195,247,0.9);
}
.new-training-btn:disabled { opacity: 0.3; cursor: not-allowed; }

/* ── Create panel ── */
.create-panel {
  background: rgba(79,195,247,0.04);
  border: 1px solid rgba(79,195,247,0.2);
  border-radius: 16px; padding: 16px;
  display: flex; flex-direction: column; gap: 14px; margin-top: 4px;
}

.create-panel-header { display: flex; align-items: center; justify-content: space-between; }

.create-panel-title { font-size: 14px; font-weight: 700; color: rgba(79,195,247,0.9); }

.create-field { display: flex; flex-direction: column; gap: 6px; }

.create-label {
  font-size: 11px; font-weight: 600;
  color: rgba(255,255,255,0.3);
  text-transform: uppercase; letter-spacing: 0.5px;
  display: flex; align-items: center; gap: 8px;
}

.create-label-count {
  background: rgba(79,195,247,0.15);
  color: rgba(79,195,247,0.8);
  border-radius: 12px; padding: 2px 8px;
  font-size: 10px; font-weight: 700;
}

.create-input {
  width: 100%; background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 10px; color: #fff;
  font-size: 14px; font-family: inherit;
  padding: 10px 12px; outline: none;
  transition: border-color 0.15s;
}
.create-input:focus { border-color: rgba(79,195,247,0.3); }

.create-hint { font-size: 12px; color: rgba(255,255,255,0.25); margin: 0; line-height: 1.5; }

.programme-picker { display: flex; flex-direction: column; gap: 6px; }

.picker-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 10px; cursor: pointer;
  transition: all 0.15s;
}
.picker-item.selected {
  background: rgba(79,195,247,0.08);
  border-color: rgba(79,195,247,0.3);
}

.picker-check {
  width: 22px; height: 22px; border-radius: 50%;
  background: rgba(79,195,247,0.15);
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; color: rgba(79,195,247,0.9);
  flex-shrink: 0;
}

.picker-name { flex: 1; font-size: 13px; color: rgba(255,255,255,0.8); }

.picker-meta { font-size: 11px; color: rgba(255,255,255,0.3); }

.create-actions {
  display: flex; gap: 8px;
  padding-top: 8px; border-top: 1px solid rgba(79,195,247,0.15);
}

/* ── Action buttons ── */
.action-btn {
  flex: 1; display: flex; align-items: center; justify-content: center;
  gap: 6px; padding: 10px; border-radius: 10px;
  font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: background 0.15s, opacity 0.15s;
  border: 1px solid transparent;
}
.action-btn:disabled { opacity: 0.35; cursor: not-allowed; }

.action-btn--start {
  background: rgba(79,195,247,0.12); border-color: rgba(79,195,247,0.3); color: #4fc3f7;
}
.action-btn--start:hover:not(:disabled) { background: rgba(79,195,247,0.2); }

.action-btn--danger {
  background: rgba(252,129,129,0.08); border-color: rgba(252,129,129,0.2);
  color: rgba(252,129,129,0.7); flex: none; padding: 10px 14px;
}
.action-btn--danger:hover { background: rgba(252,129,129,0.14); }

.action-btn--cancel {
  background: transparent; border-color: rgba(255,255,255,0.12); color: rgba(255,255,255,0.4);
}
.action-btn--cancel:hover { background: rgba(255,255,255,0.05); }

.action-btn--create {
  background: rgba(79,195,247,0.2); border-color: rgba(79,195,247,0.4); color: #4fc3f7;
}
.action-btn--create:hover:not(:disabled) { background: rgba(79,195,247,0.28); }

/* ── Icon buttons ── */
.icon-btn {
  background: none; border: none; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  width: 30px; height: 30px; border-radius: 8px; transition: background 0.15s;
}
.icon-btn:hover { background: rgba(255,255,255,0.07); }
.icon-btn--danger:hover { background: rgba(252,129,129,0.1); }
.icon-btn--confirm:hover { background: rgba(72,187,120,0.1); }

/* ── Active sessions ── */
.active-list { display: flex; flex-direction: column; gap: 10px; margin-bottom: 14px; }

.session-card {
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 16px; padding: 14px 16px;
  display: flex; flex-direction: column; gap: 10px;
}
.session-card.completed-card { cursor: pointer; user-select: none; }

.session-main { display: flex; align-items: center; justify-content: space-between; gap: 10px; }

.session-info { display: flex; flex-direction: column; gap: 4px; min-width: 0; flex: 1; }

.session-name { font-size: 15px; font-weight: 600; color: #fff; }

.session-meta { font-size: 12px; color: rgba(255,255,255,0.3); }

.phase-progress { display: flex; flex-direction: column; gap: 6px; }

.phase-progress-label { font-size: 12px; color: rgba(79,195,247,0.8); font-weight: 600; }

.progress-bar-wrap { display: flex; align-items: center; gap: 8px; }

.progress-bar { flex: 1; height: 6px; background: rgba(255,255,255,0.1); border-radius: 3px; overflow: hidden; }

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, rgba(79,195,247,0.6), rgba(79,195,247,0.9));
  transition: width 0.3s ease;
}

.progress-label { font-size: 11px; font-weight: 700; color: rgba(79,195,247,0.8); min-width: 30px; text-align: right; }

.block-status-list { display: flex; flex-direction: column; gap: 4px; }

.block-status-row { display: flex; align-items: center; gap: 8px; font-size: 12px; }

.block-status-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.block-status-dot.dot-pending { background: rgba(255,255,255,0.2); }
.block-status-dot.dot-in_progress { background: rgba(246,173,85,0.8); }
.block-status-dot.dot-done { background: rgba(72,187,120,0.7); }

.block-status-range { color: rgba(255,255,255,0.35); min-width: 60px; }
.block-status-name { color: rgba(255,255,255,0.7); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ── Completed ── */
.completed-date { font-size: 12px; font-weight: 600; color: rgba(255,255,255,0.35); white-space: nowrap; flex-shrink: 0; }

.player-summaries { display: flex; flex-direction: column; gap: 4px; padding-top: 6px; }

.player-summary-chip { display: flex; align-items: center; gap: 8px; font-size: 12px; }

.ps-name { color: rgba(255,255,255,0.6); flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.ps-pts { font-weight: 700; color: rgba(79,195,247,0.9); }

.ps-pct { font-size: 11px; color: rgba(255,255,255,0.35); min-width: 36px; text-align: right; }

.completed-detail {
  margin-top: 10px; padding-top: 10px;
  border-top: 1px solid rgba(255,255,255,0.07);
  display: flex; flex-direction: column; gap: 14px;
}

.player-detail-section { display: flex; flex-direction: column; gap: 4px; }

.player-detail-header {
  display: flex; justify-content: space-between; align-items: center;
  padding-bottom: 4px; margin-bottom: 2px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}

.pd-name { font-size: 12px; font-weight: 600; color: rgba(255,255,255,0.7); }
.pd-total { font-size: 12px; font-weight: 700; color: rgba(79,195,247,0.8); }

.phase-detail-group { display: flex; flex-direction: column; gap: 2px; margin-top: 6px; }

.phase-detail-label {
  font-size: 10px; font-weight: 600;
  color: rgba(255,255,255,0.25);
  text-transform: uppercase; letter-spacing: 0.5px;
  margin-bottom: 3px;
}

.completed-score-row {
  display: flex; align-items: center; gap: 8px;
  font-size: 11px; padding: 2px 0;
}

.completed-score-block { flex: 1; color: rgba(255,255,255,0.5); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.completed-score-range { color: rgba(255,255,255,0.25); margin-right: 4px; }

.completed-score-pts { font-weight: 600; color: rgba(255,255,255,0.5); white-space: nowrap; }

/* ── Empty state ── */
.empty-block {
  display: flex; flex-direction: column; align-items: center;
  gap: 8px; padding: 32px 16px; text-align: center;
}
.empty-block p { font-size: 14px; color: rgba(255,255,255,0.2); margin: 0; }
.empty-hint { font-size: 12px !important; color: rgba(255,255,255,0.12) !important; line-height: 1.5; }

/* ── Rename input ── */
.rename-input {
  width: 100%; background: rgba(255,255,255,0.07);
  border: 1px solid rgba(79,195,247,0.4);
  border-radius: 8px; color: #fff;
  font-size: 14px; font-family: inherit;
  padding: 4px 8px; outline: none;
}

/* ── Modal ── */
.modal-overlay {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.6); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
  z-index: 100; padding: 24px;
}

.start-modal {
  background: rgba(24,24,40,0.98);
  border: 1.5px solid rgba(255,255,255,0.1);
  border-radius: 20px; padding: 24px;
  width: 100%; max-width: 340px;
  display: flex; flex-direction: column; gap: 16px;
}

.modal-title { font-size: 16px; font-weight: 700; color: #fff; margin: 0; text-align: center; }

.modal-players { display: flex; flex-direction: column; gap: 8px; }

.modal-player-row { display: flex; align-items: center; gap: 8px; }

.modal-player-num { font-size: 13px; font-weight: 700; color: rgba(79,195,247,0.7); min-width: 20px; }

.modal-player-input {
  flex: 1; background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 8px; color: #fff;
  font-size: 13px; font-family: inherit;
  padding: 8px 10px; outline: none;
}
.modal-player-input:focus { border-color: rgba(79,195,247,0.3); }

.add-player-btn {
  background: transparent; border: 1px dashed rgba(255,255,255,0.15);
  border-radius: 8px; color: rgba(255,255,255,0.4);
  font-size: 12px; font-family: inherit; padding: 8px;
  cursor: pointer; transition: all 0.15s;
}
.add-player-btn:hover { background: rgba(255,255,255,0.04); color: rgba(255,255,255,0.6); }

.modal-actions {
  display: flex; gap: 8px; padding-top: 8px;
  border-top: 1px solid rgba(255,255,255,0.06);
}
</style>
```

- [ ] **Step 6.2: Verify the app builds without errors**

```
npm run build 2>&1 | tail -20
```

Expected: Build succeeds with no errors.

- [ ] **Step 6.3: Commit**

```bash
git add src/views/shooter/TrainingManagementView.vue
git commit -m "[ui] Add TrainingManagementView with three-tab layout"
```

---

## Task 7: Wire Training blocks in `ShooterFlyoutPanel.vue`

**Files:**
- Modify: `src/components/shooter-remote/ShooterFlyoutPanel.vue`

- [ ] **Step 7.1: Replace `trainingAblaeufe` computed and fix `programmeBlocks`**

In `src/components/shooter-remote/ShooterFlyoutPanel.vue`, replace:

```js
const trainingAblaeufe = computed(() => {
  // For now, these would come from active training sessions
  // This is a placeholder for future API integration
  return [];
});

const programmeBlocks = computed(() =>
  activeProgramStore.getBlocksForRange(currentRangeId.value)
);
```

With:

```js
const trainingBlocks = computed(() =>
  activeProgramStore.getBlocksForRange(currentRangeId.value)
    .filter(b => b.instanceType === 'training')
)

const programmeBlocks = computed(() =>
  activeProgramStore.getBlocksForRange(currentRangeId.value)
    .filter(b => b.instanceType !== 'training')
)
```

- [ ] **Step 7.2: Update the "Offene Trainings" section template**

In the template, replace the existing "Offene Trainings" section:

```html
<!-- Offene Trainings -->
<template v-if="trainingAblaeufe.length > 0">
  <div class="section">
    <span class="section-label">Offene Trainings</span>
    <div class="ablaeufe-list">
      <div
        v-for="ablauf in trainingAblaeufe"
        :key="ablauf.id"
        class="ablauf-card"
        :class="{ expanded: expandedAblaufId === ablauf.id }"
      >
        <button
          class="ablauf-header-btn"
          @click="toggleExpandAblauf(ablauf.id)"
        >
          <Icons
            :icon="expandedAblaufId === ablauf.id ? 'chevronDown' : 'chevronRight'"
            :size="12"
            color="rgba(255,255,255,0.4)"
          />
          <span class="ablauf-name">{{ ablauf.name }}</span>
          <span v-if="isAblaufCompleted(ablauf.id)" class="completion-badge">✓ Done</span>
        </button>
        <div v-if="expandedAblaufId === ablauf.id" class="ablauf-actions">
          <button class="action-btn action-play" @click="playAblaufSolo(ablauf)">
            <Icons icon="play" :size="12" color="#fff" />
            Als Solo Starten
          </button>
          <button class="action-btn action-group" @click="playAblaufGroup(ablauf)">
            <Icons icon="program" :size="12" color="#fff" />
            Als Gruppe Starten
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
```

With:

```html
<!-- Offene Trainings -->
<template v-if="trainingBlocks.length > 0">
  <div class="section">
    <span class="section-label">Offene Trainings</span>
    <div class="ablaeufe-list">
      <div
        v-for="block in trainingBlocks"
        :key="block.blockId"
        class="ablauf-card"
      >
        <button class="ablauf-header-btn" @click="playBlock(block)">
          <div class="block-info">
            <span class="ablauf-name">{{ block.ablaufAlias }}</span>
            <span class="block-template-name">{{ block.templateName }} — {{ block.programmeName }}</span>
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

Also update the empty state `v-if` condition — replace `trainingAblaeufe.length` with `trainingBlocks.length`:

```html
<div
  v-if="programmeBlocks.length === 0 && competitionAblaeufe.length === 0 && trainingBlocks.length === 0 && userAblaeufe.length === 0 && globalAblaeufe.length === 0"
  class="empty-state"
>
```

- [ ] **Step 7.3: Verify build still passes**

```
npm run build 2>&1 | tail -20
```

Expected: No errors.

- [ ] **Step 7.4: Run full test suite**

```
npm run test
```

Expected: All tests PASS.

- [ ] **Step 7.5: Commit**

```bash
git add src/components/shooter-remote/ShooterFlyoutPanel.vue
git commit -m "[ui] Wire Training blocks into FlyoutPanel Offene Trainings section"
```

---

## Self-Review

**Spec coverage check:**
- ✅ Training template: `savedTrainings`, `createTraining`, `deleteTraining`, `renameTraining` — Task 1
- ✅ `startTraining()` with phase structure — Task 2
- ✅ `getBlocksForRange()` phase-aware, `instanceType` field — Task 3
- ✅ `markBlockDone()` Training branch + phase advancement + instance completion — Task 4
- ✅ `/training` route + ShooterHomeView tile — Task 5
- ✅ `TrainingManagementView` Tab 1 (templates + create flow + start modal) — Task 6
- ✅ `TrainingManagementView` Tab 2 (Aktive) + Tab 3 (Abgeschlossen) — Task 6
- ✅ FlyoutPanel "Offene Trainings" populated — Task 7
- ✅ `programmeBlocks` filter fix — Task 7

**Type consistency check:**
- `block.instanceType` set in `getBlocksForRange` → read in FlyoutPanel computed — ✅
- `block.programmeName` set in `getBlocksForRange` → displayed in FlyoutPanel template — ✅
- `inst.phases[inst.currentPhaseIndex]` used consistently in store and view — ✅
- `startTraining(template, players)` — `template.programmes` array used in store, matches `createTraining` output shape — ✅
