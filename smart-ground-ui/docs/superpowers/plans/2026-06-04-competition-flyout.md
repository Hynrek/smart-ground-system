# Competition Flyout — Active Wettkampf View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the shooter flyout and admin panel to show a Passen progress bar, per-Serie Rotten status cards, and an individual-player leaderboard when a Wettkampf is active.

**Architecture:** A new `useCompetitionProgress` composable derives all display data (Passen stepper, Serien cards, leaderboard) from the in-memory `activePasseStore` competition instance. A new `CompetitionFlyoutContent.vue` renders this for the dark-themed flyout; `ShooterFlyoutPanel` conditionally swaps it in; `ActiveCompetitionPanel` re-renders the same data with its existing light theme. No store changes, no new API calls.

**Tech Stack:** Vue 3 Composition API, Pinia, Vitest + @vue/test-utils

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `src/composables/useCompetitionProgress.js` | Create | Derives passenProgress, activePhaseIndex, serieCards, leaderboard from a competition instance ref |
| `src/composables/__tests__/useCompetitionProgress.test.js` | Create | Unit tests for the composable |
| `src/components/shooter-remote/CompetitionFlyoutContent.vue` | Create | Dark-themed competition view: Passen stepper + Serien/Rangliste tabs |
| `src/components/__tests__/CompetitionFlyoutContent.test.js` | Create | Component smoke tests |
| `src/components/shooter-remote/ShooterFlyoutPanel.vue` | Modify | Add `competitionInstance` computed; wrap existing content in `v-else` |
| `src/components/competition/ActiveCompetitionPanel.vue` | Modify | Replace interactive rotten cards with read-only progress view using the composable |

---

## Task 1: `useCompetitionProgress` composable

**Files:**
- Create: `src/composables/useCompetitionProgress.js`
- Create: `src/composables/__tests__/useCompetitionProgress.test.js`

- [ ] **Step 1: Write failing tests**

Create `src/composables/__tests__/useCompetitionProgress.test.js`:

```js
import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { useCompetitionProgress } from '@/composables/useCompetitionProgress.js'

const makeInstance = () => ({
  instanceId: 'inst-1',
  type: 'competition',
  rotten: [
    {
      rotteId: 'r1',
      name: 'Rotte A',
      currentPhaseIndex: 0,
      phases: [
        {
          phaseIndex: 0,
          passeName: 'Passe 1',
          status: 'active',
          blocks: [
            { blockId: 'b1', serieAlias: 'Serie 1', status: 'pending', result: null },
            { blockId: 'b2', serieAlias: 'Serie 2', status: 'pending', result: null },
          ],
        },
        {
          phaseIndex: 1,
          passeName: 'Passe 2',
          status: 'pending',
          blocks: [
            { blockId: 'b3', serieAlias: 'Serie 1', status: 'pending', result: null },
          ],
        },
      ],
    },
    {
      rotteId: 'r2',
      name: 'Rotte B',
      currentPhaseIndex: 0,
      phases: [
        {
          phaseIndex: 0,
          passeName: 'Passe 1',
          status: 'active',
          blocks: [
            { blockId: 'b4', serieAlias: 'Serie 1', status: 'in_progress', result: null },
            { blockId: 'b5', serieAlias: 'Serie 2', status: 'pending', result: null },
          ],
        },
        {
          phaseIndex: 1,
          passeName: 'Passe 2',
          status: 'pending',
          blocks: [
            { blockId: 'b6', serieAlias: 'Serie 1', status: 'pending', result: null },
          ],
        },
      ],
    },
  ],
})

describe('useCompetitionProgress', () => {
  it('returns empty arrays when instance is null', () => {
    const instance = ref(null)
    const { passenProgress, serieCards, leaderboard } = useCompetitionProgress(instance)
    expect(passenProgress.value).toEqual([])
    expect(serieCards.value).toEqual([])
    expect(leaderboard.value).toEqual([])
  })

  it('activePhaseIndex defaults to 0 when instance is null', () => {
    const instance = ref(null)
    const { activePhaseIndex } = useCompetitionProgress(instance)
    expect(activePhaseIndex.value).toBe(0)
  })

  it('passenProgress: active phase gets status aktiv', () => {
    const instance = ref(makeInstance())
    const { passenProgress } = useCompetitionProgress(instance)
    expect(passenProgress.value[0]).toMatchObject({ passeName: 'Passe 1', status: 'aktiv' })
  })

  it('passenProgress: unreached phase gets status offen', () => {
    const instance = ref(makeInstance())
    const { passenProgress } = useCompetitionProgress(instance)
    expect(passenProgress.value[1]).toMatchObject({ passeName: 'Passe 2', status: 'offen' })
  })

  it('passenProgress: phase is fertig when all rotten have phases[i].status === done', () => {
    const inst = makeInstance()
    inst.rotten[0].phases[0].status = 'done'
    inst.rotten[0].currentPhaseIndex = 1
    inst.rotten[1].phases[0].status = 'done'
    inst.rotten[1].currentPhaseIndex = 1
    const instance = ref(inst)
    const { passenProgress } = useCompetitionProgress(instance)
    expect(passenProgress.value[0].status).toBe('fertig')
    expect(passenProgress.value[1].status).toBe('aktiv')
  })

  it('activePhaseIndex is the lowest phase not fully done across all rotten', () => {
    const inst = makeInstance()
    inst.rotten[0].phases[0].status = 'done'
    inst.rotten[0].currentPhaseIndex = 1
    inst.rotten[1].phases[0].status = 'done'
    inst.rotten[1].currentPhaseIndex = 1
    const instance = ref(inst)
    const { activePhaseIndex } = useCompetitionProgress(instance)
    expect(activePhaseIndex.value).toBe(1)
  })

  it('serieCards: one card per block in the active phase, with a row per rotte', () => {
    const instance = ref(makeInstance())
    const { serieCards } = useCompetitionProgress(instance)
    expect(serieCards.value).toHaveLength(2)
    expect(serieCards.value[0].serieAlias).toBe('Serie 1')
    expect(serieCards.value[0].rotteRows).toHaveLength(2)
    expect(serieCards.value[0].rotteRows[0]).toMatchObject({ rotteName: 'Rotte A', status: 'pending' })
    expect(serieCards.value[0].rotteRows[1]).toMatchObject({ rotteName: 'Rotte B', status: 'in_progress' })
  })

  it('leaderboard is empty when no blocks are done', () => {
    const instance = ref(makeInstance())
    const { leaderboard } = useCompetitionProgress(instance)
    expect(leaderboard.value).toEqual([])
  })

  it('leaderboard aggregates playerResults from all done blocks', () => {
    const inst = makeInstance()
    inst.rotten[0].phases[0].blocks[0].status = 'done'
    inst.rotten[0].phases[0].blocks[0].result = {
      playerResults: [
        { playerId: 'p1', displayName: 'Alice', totalPoints: 10, maxPoints: 12 },
        { playerId: 'p2', displayName: 'Bob', totalPoints: 8, maxPoints: 12 },
      ],
    }
    inst.rotten[1].phases[0].blocks[0].status = 'done'
    inst.rotten[1].phases[0].blocks[0].result = {
      playerResults: [
        { playerId: 'p1', displayName: 'Alice', totalPoints: 6, maxPoints: 12 },
        { playerId: 'p2', displayName: 'Bob', totalPoints: 9, maxPoints: 12 },
      ],
    }
    const instance = ref(inst)
    const { leaderboard } = useCompetitionProgress(instance)
    expect(leaderboard.value).toHaveLength(2)
    expect(leaderboard.value[0]).toMatchObject({ displayName: 'Bob', totalPoints: 17, maxPoints: 24 })
    expect(leaderboard.value[1]).toMatchObject({ displayName: 'Alice', totalPoints: 16, maxPoints: 24 })
  })

  it('leaderboard is sorted descending by totalPoints', () => {
    const inst = makeInstance()
    inst.rotten[0].phases[0].blocks[0].status = 'done'
    inst.rotten[0].phases[0].blocks[0].result = {
      playerResults: [
        { playerId: 'p1', displayName: 'Alice', totalPoints: 5, maxPoints: 10 },
        { playerId: 'p2', displayName: 'Bob', totalPoints: 9, maxPoints: 10 },
      ],
    }
    const instance = ref(inst)
    const { leaderboard } = useCompetitionProgress(instance)
    expect(leaderboard.value[0].displayName).toBe('Bob')
    expect(leaderboard.value[1].displayName).toBe('Alice')
  })
})
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
npm run test src/composables/__tests__/useCompetitionProgress.test.js
```

Expected: FAIL — `Cannot find module '@/composables/useCompetitionProgress.js'`

- [ ] **Step 3: Implement the composable**

Create `src/composables/useCompetitionProgress.js`:

```js
import { computed } from 'vue'

export function useCompetitionProgress(instance) {
  const _rotten = computed(() => instance.value?.rotten ?? [])

  const passenProgress = computed(() => {
    if (_rotten.value.length === 0) return []
    return (_rotten.value[0].phases ?? []).map((phase, i) => {
      const allDone = _rotten.value.every(r => r.phases[i]?.status === 'done')
      const anyActive = _rotten.value.some(r => r.currentPhaseIndex === i)
      const status = allDone ? 'fertig' : anyActive ? 'aktiv' : 'offen'
      return { phaseIndex: i, passeName: phase.passeName, status }
    })
  })

  const activePhaseIndex = computed(() => {
    if (_rotten.value.length === 0) return 0
    const phaseCount = _rotten.value[0].phases?.length ?? 0
    for (let i = 0; i < phaseCount; i++) {
      if (!_rotten.value.every(r => r.phases[i]?.status === 'done')) return i
    }
    return Math.max(0, phaseCount - 1)
  })

  const serieCards = computed(() => {
    if (_rotten.value.length === 0) return []
    const phaseIdx = activePhaseIndex.value
    const referenceBlocks = _rotten.value[0].phases?.[phaseIdx]?.blocks ?? []
    return referenceBlocks.map((block, j) => ({
      serieAlias: block.serieAlias,
      rotteRows: _rotten.value.map(rotte => ({
        rotteId: rotte.rotteId,
        rotteName: rotte.name,
        status: rotte.phases?.[phaseIdx]?.blocks?.[j]?.status ?? 'pending',
      })),
    }))
  })

  const leaderboard = computed(() => {
    const totals = new Map()
    for (const rotte of _rotten.value) {
      for (const phase of (rotte.phases ?? [])) {
        for (const block of (phase.blocks ?? [])) {
          if (block.status !== 'done' || !block.result?.playerResults) continue
          for (const pr of block.result.playerResults) {
            const entry = totals.get(pr.displayName) ?? {
              displayName: pr.displayName,
              totalPoints: 0,
              maxPoints: 0,
            }
            entry.totalPoints += pr.totalPoints
            entry.maxPoints += pr.maxPoints
            totals.set(pr.displayName, entry)
          }
        }
      }
    }
    return [...totals.values()].sort((a, b) => b.totalPoints - a.totalPoints)
  })

  return { passenProgress, activePhaseIndex, serieCards, leaderboard }
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
npm run test src/composables/__tests__/useCompetitionProgress.test.js
```

Expected: All 10 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/composables/useCompetitionProgress.js src/composables/__tests__/useCompetitionProgress.test.js
git commit -m "[ui] add useCompetitionProgress composable"
```

---

## Task 2: `CompetitionFlyoutContent.vue` component

**Files:**
- Create: `src/components/shooter-remote/CompetitionFlyoutContent.vue`
- Create: `src/components/__tests__/CompetitionFlyoutContent.test.js`

- [ ] **Step 1: Write failing tests**

Create `src/components/__tests__/CompetitionFlyoutContent.test.js`:

```js
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import CompetitionFlyoutContent from '../shooter-remote/CompetitionFlyoutContent.vue'

const makeInstance = () => ({
  instanceId: 'inst-1',
  type: 'competition',
  rotten: [
    {
      rotteId: 'r1',
      name: 'Rotte A',
      currentPhaseIndex: 0,
      phases: [
        {
          phaseIndex: 0,
          passeName: 'Passe 1',
          status: 'active',
          blocks: [
            { blockId: 'b1', serieAlias: 'Morgenserie', status: 'done', result: {
              playerResults: [{ playerId: 'p1', displayName: 'Alice', totalPoints: 8, maxPoints: 10 }]
            }},
            { blockId: 'b2', serieAlias: 'Abendserie', status: 'pending', result: null },
          ],
        },
      ],
    },
  ],
})

describe('CompetitionFlyoutContent', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders without errors', () => {
    const wrapper = mount(CompetitionFlyoutContent, {
      props: { instance: makeInstance() },
      global: { stubs: { Icons: true } },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('renders passen stepper with correct phase names', () => {
    const wrapper = mount(CompetitionFlyoutContent, {
      props: { instance: makeInstance() },
      global: { stubs: { Icons: true } },
    })
    expect(wrapper.text()).toContain('Passe 1')
  })

  it('renders serie cards on the Serien tab by default', () => {
    const wrapper = mount(CompetitionFlyoutContent, {
      props: { instance: makeInstance() },
      global: { stubs: { Icons: true } },
    })
    expect(wrapper.text()).toContain('Morgenserie')
    expect(wrapper.text()).toContain('Abendserie')
  })

  it('renders rotte rows inside serie cards', () => {
    const wrapper = mount(CompetitionFlyoutContent, {
      props: { instance: makeInstance() },
      global: { stubs: { Icons: true } },
    })
    expect(wrapper.text()).toContain('Rotte A')
  })

  it('switches to Rangliste tab and shows player scores', async () => {
    const wrapper = mount(CompetitionFlyoutContent, {
      props: { instance: makeInstance() },
      global: { stubs: { Icons: true } },
    })
    const tabs = wrapper.findAll('.tab-btn')
    const ranglisteTab = tabs.find(t => t.text() === 'Rangliste')
    await ranglisteTab.trigger('click')
    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('8')
  })

  it('shows empty state on Rangliste when no blocks are done', async () => {
    const inst = makeInstance()
    inst.rotten[0].phases[0].blocks[0].status = 'pending'
    inst.rotten[0].phases[0].blocks[0].result = null
    const wrapper = mount(CompetitionFlyoutContent, {
      props: { instance: inst },
      global: { stubs: { Icons: true } },
    })
    const tabs = wrapper.findAll('.tab-btn')
    await tabs.find(t => t.text() === 'Rangliste').trigger('click')
    expect(wrapper.text()).toContain('Noch keine Ergebnisse')
  })
})
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
npm run test src/components/__tests__/CompetitionFlyoutContent.test.js
```

Expected: FAIL — `Cannot find module '../shooter-remote/CompetitionFlyoutContent.vue'`

- [ ] **Step 3: Implement the component**

Create `src/components/shooter-remote/CompetitionFlyoutContent.vue`:

```vue
<template>
  <div class="competition-content">

    <!-- Passen stepper -->
    <div class="passen-stepper">
      <template v-for="(passe, i) in passenProgress" :key="i">
        <div class="passe-step" :class="`step-${passe.status}`">
          <span class="step-name">{{ passe.passeName }}</span>
          <span class="step-badge" :class="`badge-${passe.status}`">{{ passeStatusLabel(passe.status) }}</span>
        </div>
        <span v-if="i < passenProgress.length - 1" class="step-arrow">→</span>
      </template>
    </div>

    <!-- Tabs -->
    <div class="tab-bar">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'serien' }"
        @click="activeTab = 'serien'"
      >
        Serien
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'rangliste' }"
        @click="activeTab = 'rangliste'"
      >
        Rangliste
      </button>
    </div>

    <!-- Serien tab -->
    <div v-if="activeTab === 'serien'" class="serien-view">
      <div
        v-for="(card, i) in serieCards"
        :key="i"
        class="serie-card"
      >
        <div class="serie-card-header">{{ card.serieAlias }}</div>
        <div
          v-for="row in card.rotteRows"
          :key="row.rotteId"
          class="rotte-row"
        >
          <span class="rotte-name">{{ row.rotteName }}</span>
          <span class="rotte-chip" :class="`chip-${row.status}`">
            {{ rowStatusLabel(row.status) }}
          </span>
        </div>
      </div>
      <div v-if="serieCards.length === 0" class="empty-state">Keine Serien</div>
    </div>

    <!-- Rangliste tab -->
    <div v-if="activeTab === 'rangliste'" class="rangliste-view">
      <div
        v-for="(entry, i) in leaderboard"
        :key="entry.displayName"
        class="rangliste-row"
      >
        <span class="rank">{{ i + 1 }}</span>
        <span class="player-name">{{ entry.displayName }}</span>
        <span class="score">{{ entry.totalPoints }} / {{ entry.maxPoints }}</span>
      </div>
      <div v-if="leaderboard.length === 0" class="empty-state">Noch keine Ergebnisse</div>
    </div>

  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useCompetitionProgress } from '@/composables/useCompetitionProgress.js'

const props = defineProps({
  instance: { type: Object, required: true },
})

const instanceRef = computed(() => props.instance)
const { passenProgress, serieCards, leaderboard } = useCompetitionProgress(instanceRef)

const activeTab = ref('serien')

const passeStatusLabel = (status) => ({ fertig: 'Fertig', aktiv: 'Aktiv', offen: 'Offen' }[status] ?? status)
const rowStatusLabel = (status) => ({ done: '✓ Fertig', in_progress: '◑ Aktiv', pending: '○ Offen' }[status] ?? status)
</script>

<style scoped>
.competition-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px 0;
}

/* ── Passen stepper ───────────────────────────────── */
.passen-stepper {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.07);
}

.passe-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.step-name {
  font-size: 10px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.5);
  white-space: nowrap;
}

.step-badge {
  font-size: 9px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 10px;
}

.badge-fertig { background: rgba(72, 187, 120, 0.2); color: #48bb78; }
.badge-aktiv  { background: rgba(246, 173, 85, 0.2); color: #f6ad55; }
.badge-offen  { background: rgba(255, 255, 255, 0.07); color: rgba(255, 255, 255, 0.3); }

.step-arrow {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.2);
  padding: 0 2px;
  align-self: center;
}

/* ── Tab bar ──────────────────────────────────────── */
.tab-bar {
  display: flex;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.tab-btn {
  flex: 1;
  padding: 8px 0;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  color: rgba(255, 255, 255, 0.3);
  font-size: 11px;
  font-weight: 700;
  font-family: inherit;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
  margin-bottom: -1px;
}

.tab-btn.active {
  color: rgba(79, 195, 247, 0.9);
  border-bottom-color: rgba(79, 195, 247, 0.7);
}

.tab-btn:hover:not(.active) {
  color: rgba(255, 255, 255, 0.5);
}

/* ── Serien view ──────────────────────────────────── */
.serien-view {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 4px;
}

.serie-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  overflow: hidden;
}

.serie-card-header {
  font-size: 11px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.7);
  padding: 8px 10px 6px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.rotte-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

.rotte-row:last-child { border-bottom: none; }

.rotte-name {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
}

.rotte-chip {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
}

.chip-done       { background: rgba(72, 187, 120, 0.15); color: #48bb78; }
.chip-in_progress { background: rgba(246, 173, 85, 0.15); color: #f6ad55; }
.chip-pending    { background: rgba(255, 255, 255, 0.07); color: rgba(255, 255, 255, 0.3); }

/* ── Rangliste view ───────────────────────────────── */
.rangliste-view {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-top: 4px;
}

.rangliste-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 8px;
}

.rank {
  font-size: 11px;
  font-weight: 700;
  color: rgba(79, 195, 247, 0.6);
  width: 16px;
  flex-shrink: 0;
}

.player-name {
  flex: 1;
  font-size: 12px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.8);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score {
  font-size: 11px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.4);
  white-space: nowrap;
}

/* ── Empty state ──────────────────────────────────── */
.empty-state {
  text-align: center;
  padding: 24px 12px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.2);
}
</style>
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
npm run test src/components/__tests__/CompetitionFlyoutContent.test.js
```

Expected: All 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/components/shooter-remote/CompetitionFlyoutContent.vue src/components/__tests__/CompetitionFlyoutContent.test.js
git commit -m "[ui] add CompetitionFlyoutContent component"
```

---

## Task 3: Wire `CompetitionFlyoutContent` into `ShooterFlyoutPanel`

**Files:**
- Modify: `src/components/shooter-remote/ShooterFlyoutPanel.vue`

- [ ] **Step 1: Add import and `competitionInstance` computed**

In `ShooterFlyoutPanel.vue`, add the import at the top of `<script setup>` (after the existing imports):

```js
import CompetitionFlyoutContent from '@/components/shooter-remote/CompetitionFlyoutContent.vue'
```

Then add this computed directly below the `expandedSerieId` ref declaration (after line `const expandedSerieId = ref(null);`):

```js
const competitionInstance = computed(() => {
  const ctxId = store.competitionContext?.instanceId
  if (!ctxId) return null
  return activePasseStore.activeInstances.find(
    i => i.instanceId === ctxId && i.type === 'competition'
  ) ?? null
})
```

- [ ] **Step 2: Wrap existing non-recording content in `v-else`**

In the template, locate the block starting with:

```html
<!-- Serie-centered view (when not recording) -->
<template v-if="!isRecordingActive && isOpen">
```

Replace everything inside that `<template>` (keep the outer `<template v-if>` tag itself) so it reads:

```html
<!-- Serie-centered view (when not recording) -->
<template v-if="!isRecordingActive && isOpen">

  <!-- Competition mode: full progress view -->
  <CompetitionFlyoutContent
    v-if="competitionInstance"
    :instance="competitionInstance"
  />

  <!-- Normal mode: existing serien/passen/training content -->
  <template v-else>
    <!-- Passen Blöcke -->
    <template v-if="passenBlocks.length > 0">
      <div class="section">
        <span class="section-label">Passen</span>
        <div class="serien-list">
          <div
            v-for="block in passenBlocks"
            :key="block.blockId"
            class="serie-card"
          >
            <button class="serie-header-btn" @click="playBlock(block)">
              <div class="block-info">
                <span class="serie-name">{{ block.serieAlias }}</span>
                <span class="block-template-name">{{ block.templateName }}</span>
              </div>
              <span class="block-status-badge" :class="`status-${block.status}`">
                {{ block.status === 'in_progress' ? '◑' : '●' }}
              </span>
            </button>
            <div class="serie-actions">
              <div class="session-meta">
                {{ block.players.map(p => p.displayName).join(', ') }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- Offene Wettkämpfe -->
    <template v-if="competitionSerien.length > 0">
      <div class="section">
        <span class="section-label">Offene Wettkämpfe</span>
        <div class="serien-list">
          <div
            v-for="item in competitionSerien"
            :key="`${item.instanceId}-${item.rotteId}`"
            class="serie-card"
            data-testid="competition-rotte-card"
          >
            <button class="serie-header-btn" @click="playCompetitionRotte(item)">
              <div class="block-info">
                <span class="rotte-instance-name">{{ item.instanceName }}</span>
                <span class="serie-name">{{ item.rotteName }} · {{ item.passeName }}</span>
              </div>
              <Icons icon="play" :size="12" color="rgba(79,195,247,0.6)" />
            </button>
            <div class="serie-actions">
              <div class="session-meta">
                {{ (item.players ?? []).map(p => p.displayName).join(', ') }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- Offene Trainings -->
    <template v-if="trainingBlocks.length > 0">
      <div class="section">
        <span class="section-label">Offene Trainings</span>
        <div class="serien-list">
          <div
            v-for="block in trainingBlocks"
            :key="block.blockId"
            class="serie-card"
          >
            <button class="serie-header-btn" @click="playBlock(block)">
              <div class="block-info">
                <span class="serie-name">{{ block.serieAlias }}</span>
                <span class="block-template-name">{{ block.templateName }} — {{ block.passeName }}</span>
              </div>
              <span class="block-status-badge" :class="`status-${block.status}`">
                {{ block.status === 'in_progress' ? '◑' : '●' }}
              </span>
            </button>
            <div class="serie-actions">
              <div class="session-meta">
                {{ block.players.map(p => p.displayName).join(', ') }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- Serien from the User -->
    <template v-if="userSerien.length > 0">
      <div class="section">
        <span class="section-label">Meine Serien</span>
        <div class="serien-list">
          <div
            v-for="serie in userSerien"
            :key="serie.id"
            class="serie-card"
            :class="{ expanded: expandedSerieId === serie.id }"
          >
            <button
              class="serie-header-btn"
              @click="toggleExpandSerie(serie.id)"
            >
              <Icons
                :icon="expandedSerieId === serie.id ? 'chevronDown' : 'chevronRight'"
                :size="12"
                color="rgba(255,255,255,0.4)"
              />
              <span class="serie-name">{{ serie.name }}</span>
            </button>
            <div v-if="expandedSerieId === serie.id" class="serie-actions">
              <button class="action-btn action-play" @click="playSerieSolo(serie)">
                <Icons icon="play" :size="12" color="#fff" />
                Als Solo Starten
              </button>
              <button class="action-btn action-group" @click="playSerieGroup(serie)">
                <Icons icon="program" :size="12" color="#fff" />
                Als Gruppe Starten
              </button>
              <button class="action-btn action-edit" @click="editSerie(serie.id)">
                <Icons icon="edit" :size="12" color="#fbb" />
                Bearbeiten
              </button>
              <button class="action-btn action-remove" @click="deleteSerie(serie.id)">
                <Icons icon="trash" :size="12" color="#fc8181" />
                Löschen
              </button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- Globale Serien -->
    <template v-if="globalSerien.length > 0">
      <div class="section">
        <span class="section-label">Globale Serien</span>
        <div class="serien-list">
          <div
            v-for="serie in globalSerien"
            :key="serie.id"
            class="serie-card"
            :class="{ expanded: expandedSerieId === serie.id }"
          >
            <button
              class="serie-header-btn"
              @click="toggleExpandSerie(serie.id)"
            >
              <Icons
                :icon="expandedSerieId === serie.id ? 'chevronDown' : 'chevronRight'"
                :size="12"
                color="rgba(255,255,255,0.4)"
              />
              <span class="serie-name">{{ serie.name }}</span>
            </button>
            <div v-if="expandedSerieId === serie.id" class="serie-actions">
              <button class="action-btn action-play" @click="playSerieSolo(serie)">
                <Icons icon="play" :size="12" color="#fff" />
                Als Solo Starten
              </button>
              <button class="action-btn action-group" @click="playSerieGroup(serie)">
                <Icons icon="program" :size="12" color="#fff" />
                Als Gruppe Starten
              </button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- Empty state -->
    <div
      v-if="passenBlocks.length === 0 && competitionSerien.length === 0 && trainingBlocks.length === 0 && userSerien.length === 0 && globalSerien.length === 0"
      class="empty-state"
    >
      <Icons icon="program" :size="32" color="rgba(255,255,255,0.1)" />
      <p>Keine Serien</p>
      <p class="empty-hint">Erstelle Serien in der Erfassungs-Ansicht</p>
    </div>
  </template>

</template>
```

- [ ] **Step 3: Run existing flyout tests — verify they still pass**

```bash
npm run test src/components/__tests__/ShooterFlyoutPanel.test.js
```

Expected: All existing tests PASS (non-competition paths are unchanged)

- [ ] **Step 4: Run all tests**

```bash
npm run test
```

Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/components/shooter-remote/ShooterFlyoutPanel.vue
git commit -m "[ui] wire CompetitionFlyoutContent into ShooterFlyoutPanel"
```

---

## Task 4: Rewrite `ActiveCompetitionPanel` as read-only progress view

**Files:**
- Modify: `src/components/competition/ActiveCompetitionPanel.vue`

The current panel shows interactive rotten cards with range-assignment dropdowns. Replace the rotten list entirely with the Passen stepper + Serien cards + Rangliste (light-themed, using `useCompetitionProgress`). Keep only the "Wettkampf abbrechen" button.

- [ ] **Step 1: Replace `ActiveCompetitionPanel.vue`**

Overwrite `src/components/competition/ActiveCompetitionPanel.vue` with:

```vue
<!-- src/components/competition/ActiveCompetitionPanel.vue -->
<template>
  <div class="panel-content">

    <!-- Header with abort button only -->
    <div class="panel-header">
      <h2 class="panel-title">Laufender Wettkampf</h2>
      <button class="stop-btn" @click="emit('stop')">
        <Icons icon="x" :size="13" color="rgba(252,129,129,0.8)" />
        Wettkampf abbrechen
      </button>
    </div>

    <!-- Passen stepper -->
    <div class="passen-stepper">
      <template v-for="(passe, i) in passenProgress" :key="i">
        <div class="passe-step">
          <span class="step-name">{{ passe.passeName }}</span>
          <span class="step-badge" :class="`badge-${passe.status}`">{{ passeStatusLabel(passe.status) }}</span>
        </div>
        <span v-if="i < passenProgress.length - 1" class="step-arrow">→</span>
      </template>
    </div>

    <!-- Tabs -->
    <div class="tab-bar">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'serien' }"
        @click="activeTab = 'serien'"
      >
        Serien
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'rangliste' }"
        @click="activeTab = 'rangliste'"
      >
        Rangliste
      </button>
    </div>

    <!-- Serien tab -->
    <div v-if="activeTab === 'serien'" class="serien-view">
      <div
        v-for="(card, i) in serieCards"
        :key="i"
        class="serie-card"
      >
        <div class="serie-card-header">{{ card.serieAlias }}</div>
        <div
          v-for="row in card.rotteRows"
          :key="row.rotteId"
          class="rotte-row"
        >
          <span class="rotte-name">{{ row.rotteName }}</span>
          <span class="rotte-chip" :class="`chip-${row.status}`">
            {{ rowStatusLabel(row.status) }}
          </span>
        </div>
      </div>
      <div v-if="serieCards.length === 0" class="empty-state">Keine Serien in dieser Passe</div>
    </div>

    <!-- Rangliste tab -->
    <div v-if="activeTab === 'rangliste'" class="rangliste-view">
      <div
        v-for="(entry, i) in leaderboard"
        :key="entry.displayName"
        class="rangliste-row"
      >
        <span class="rank">{{ i + 1 }}</span>
        <span class="player-name">{{ entry.displayName }}</span>
        <span class="score">{{ entry.totalPoints }} / {{ entry.maxPoints }}</span>
      </div>
      <div v-if="leaderboard.length === 0" class="empty-state">Noch keine Ergebnisse</div>
    </div>

  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import Icons from '@/components/Icons.vue'
import { useActivePasseStore } from '@/stores/activePasseStore.js'
import { useCompetitionProgress } from '@/composables/useCompetitionProgress.js'

const props = defineProps({ event: { type: Object, required: true } })
const emit = defineEmits(['stop'])

const activePasseStore = useActivePasseStore()

const liveInstance = computed(() =>
  activePasseStore.activeInstances.find(i => i.instanceId === props.event.activeInstanceId) ?? null
)

const { passenProgress, serieCards, leaderboard } = useCompetitionProgress(liveInstance)

const activeTab = ref('serien')

const passeStatusLabel = (status) => ({ fertig: 'Fertig', aktiv: 'Aktiv', offen: 'Offen' }[status] ?? status)
const rowStatusLabel = (status) => ({ done: '✓ Fertig', in_progress: '◑ Aktiv', pending: '○ Offen' }[status] ?? status)
</script>

<style scoped>
.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px 40px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ── Header ── */
.panel-header { display: flex; align-items: center; justify-content: space-between; }

.panel-title { font-size: 18px; font-weight: 700; color: var(--sg-brand); margin: 0; }

.stop-btn {
  display: flex; align-items: center; gap: 6px;
  background: #fff5f5; border: 1px solid #fed7d7;
  border-radius: 10px; padding: 8px 14px;
  color: #c53030; font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.stop-btn:hover { background: #fed7d7; }

/* ── Passen stepper ── */
.passen-stepper {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 14px 16px;
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 12px;
  box-shadow: var(--sg-shadow-sm);
}

.passe-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.step-name {
  font-size: 11px;
  font-weight: 600;
  color: var(--sg-text-muted);
  white-space: nowrap;
}

.step-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
}

.badge-fertig { background: var(--sg-color-success-bg); color: var(--sg-color-success-text); }
.badge-aktiv  { background: var(--sg-color-warning-bg); color: var(--sg-color-warning-text); }
.badge-offen  { background: var(--sg-bg-panel); color: var(--sg-text-faint); }

.step-arrow {
  font-size: 12px;
  color: var(--sg-border-input);
  padding: 0 4px;
  align-self: center;
}

/* ── Tab bar ── */
.tab-bar {
  display: flex;
  border-bottom: 1px solid var(--sg-border);
}

.tab-btn {
  flex: 1;
  padding: 10px 0;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  color: var(--sg-text-faint);
  cursor: pointer;
  margin-bottom: -1px;
  transition: color 0.15s, border-color 0.15s;
}

.tab-btn.active {
  color: var(--sg-brand);
  border-bottom-color: var(--sg-accent);
}

.tab-btn:hover:not(.active) { color: var(--sg-text-muted); }

/* ── Serien view ── */
.serien-view {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.serie-card {
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--sg-shadow-sm);
}

.serie-card-header {
  font-size: 13px;
  font-weight: 700;
  color: var(--sg-brand);
  padding: 10px 14px 8px;
  border-bottom: 1px solid var(--sg-border);
}

.rotte-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  border-bottom: 1px solid var(--sg-border);
}

.rotte-row:last-child { border-bottom: none; }

.rotte-name {
  font-size: 13px;
  color: var(--sg-text-muted);
}

.rotte-chip {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 10px;
}

.chip-done        { background: var(--sg-color-success-bg); color: var(--sg-color-success-text); }
.chip-in_progress { background: var(--sg-color-warning-bg); color: var(--sg-color-warning-text); }
.chip-pending     { background: var(--sg-bg-panel); color: var(--sg-text-faint); }

/* ── Rangliste view ── */
.rangliste-view {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rangliste-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 10px;
  box-shadow: var(--sg-shadow-sm);
}

.rank {
  font-size: 12px;
  font-weight: 700;
  color: var(--sg-accent);
  width: 20px;
  flex-shrink: 0;
}

.player-name {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: var(--sg-brand);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score {
  font-size: 13px;
  font-weight: 700;
  color: var(--sg-text-muted);
  white-space: nowrap;
}

/* ── Empty state ── */
.empty-state {
  text-align: center;
  padding: 32px 16px;
  font-size: 13px;
  color: var(--sg-text-faint);
  background: var(--sg-bg-card);
  border-radius: 12px;
  border: 1px solid var(--sg-border);
}
</style>
```

- [ ] **Step 2: Run all tests**

```bash
npm run test
```

Expected: All tests PASS (the panel previously had no behaviour-level tests; existing tests for other components are unaffected)

- [ ] **Step 3: Commit**

```bash
git add src/components/competition/ActiveCompetitionPanel.vue
git commit -m "[ui] rewrite ActiveCompetitionPanel as read-only competition progress view"
```

---

## Self-Review Checklist

- [x] **Spec coverage**: composable ✓ · CompetitionFlyoutContent ✓ · ShooterFlyoutPanel wiring ✓ · ActiveCompetitionPanel read-only redesign ✓ · only "Wettkampf abbrechen" remains as action ✓
- [x] **No placeholders**: all steps contain complete code
- [x] **Type consistency**: `passenProgress[].status` values (`fertig`/`aktiv`/`offen`) match the label maps in both components · `serieCards[].rotteRows[].status` values (`done`/`in_progress`/`pending`) match `chip-*` CSS classes and `rowStatusLabel` maps in both components · `useCompetitionProgress` signature is identical in both consumers
