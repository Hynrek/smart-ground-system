# Competition Rotten in ShooterFlyoutPanel — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a Wettkampf is started, each Rotte appears automatically as a separate clickable card in the ShooterFlyoutPanel under "Wettkämpfe", on any range, without manual assignment.

**Architecture:** Add `getActiveCompetitionRotten()` to `activePasseStore` that returns all non-done Rotten from active competitions with no range filter. Wire this into `competitionSerien` in `ShooterFlyoutPanel`, render one card per Rotte, and assign the range implicitly when a Rotte card is tapped. Filter competition blocks out of `passenBlocks` to prevent double-display.

**Tech Stack:** Vue 3 Composition API, Pinia, Vitest + @vue/test-utils

---

## File Map

| File | Role |
|---|---|
| `src/stores/activePasseStore.js` | Add `getActiveCompetitionRotten()` |
| `src/stores/__tests__/activePasseStore.test.js` | Unit tests for new method |
| `src/components/shooter-remote/ShooterFlyoutPanel.vue` | Wire `competitionSerien`, fix `passenBlocks`, add Rotte card UI + handler |
| `src/components/__tests__/ShooterFlyoutPanel.test.js` | Component tests for Rotte card rendering |

---

### Task 1: Add `getActiveCompetitionRotten()` to `activePasseStore`

**Files:**
- Modify: `src/stores/activePasseStore.js`
- Test: `src/stores/__tests__/activePasseStore.test.js`

- [ ] **Step 1: Write the failing test**

Create/open `src/stores/__tests__/activePasseStore.test.js` and add:

```js
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useActivePasseStore } from '../activePasseStore.js'

describe('getActiveCompetitionRotten', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('returns empty array when no competitions are active', () => {
    const store = useActivePasseStore()
    expect(store.getActiveCompetitionRotten()).toEqual([])
  })

  it('returns one entry per non-done rotte across all active competitions', () => {
    const store = useActivePasseStore()

    const template = {
      id: 'comp-1',
      name: 'Frühjahrspokal',
      passen: [
        {
          id: 'passe-1',
          name: 'Passe 1',
          serien: [
            { id: 'serie-1', name: 'A', alias: 'A', steps: [], rangeId: 'range-1', rangeName: 'Platz 1' },
          ],
        },
      ],
    }

    const rotten = [
      { rotteId: 'r1', name: 'Rotte 1', players: [{ id: 'p1', displayName: 'Max' }] },
      { rotteId: 'r2', name: 'Rotte 2', players: [{ id: 'p2', displayName: 'Lisa' }] },
    ]

    store.startCompetition(template, rotten)

    const result = store.getActiveCompetitionRotten()

    expect(result).toHaveLength(2)
    expect(result[0].rotteId).toBe('r1')
    expect(result[0].rotteName).toBe('Rotte 1')
    expect(result[0].passeName).toBe('Passe 1')
    expect(result[0].instanceName).toBe('Frühjahrspokal')
    expect(result[0].players).toEqual([{ id: 'p1', displayName: 'Max' }])
    expect(result[0].blocks).toHaveLength(1)
    expect(result[1].rotteId).toBe('r2')
  })

  it('excludes rotten with status done', () => {
    const store = useActivePasseStore()

    const template = {
      id: 'comp-1',
      name: 'Frühjahrspokal',
      passen: [
        {
          id: 'passe-1',
          name: 'Passe 1',
          serien: [
            { id: 'serie-1', name: 'A', alias: 'A', steps: [], rangeId: 'range-1', rangeName: 'Platz 1' },
          ],
        },
      ],
    }

    const rotten = [
      { rotteId: 'r1', name: 'Rotte 1', players: [] },
      { rotteId: 'r2', name: 'Rotte 2', players: [] },
    ]

    store.startCompetition(template, rotten)

    // Manually mark r1 as done
    const inst = store.activeInstances[0]
    inst.rotten.find(r => r.rotteId === 'r1').status = 'done'

    const result = store.getActiveCompetitionRotten()
    expect(result).toHaveLength(1)
    expect(result[0].rotteId).toBe('r2')
  })

  it('does not filter by range — all rotten appear regardless of assignedRangeId', () => {
    const store = useActivePasseStore()

    const template = {
      id: 'comp-1',
      name: 'Cup',
      passen: [{ id: 'p1', name: 'P1', serien: [{ id: 's1', name: 'A', alias: 'A', steps: [], rangeId: 'range-99', rangeName: 'X' }] }],
    }

    store.startCompetition(template, [
      { rotteId: 'r1', name: 'Rotte 1', players: [] },
    ])

    // No range assigned
    const result = store.getActiveCompetitionRotten()
    expect(result).toHaveLength(1)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm run test src/stores/__tests__/activePasseStore.test.js
```

Expected: FAIL — `store.getActiveCompetitionRotten is not a function`

- [ ] **Step 3: Add `getActiveCompetitionRotten` to `activePasseStore.js`**

Open `src/stores/activePasseStore.js`. Add this method inside the store function, after the existing `getBlocksForRange` method:

```js
const getActiveCompetitionRotten = () => {
  const result = []
  for (const inst of activeInstances.value) {
    if (inst.type !== 'competition') continue
    for (const rotte of inst.rotten) {
      if (rotte.status === 'done') continue
      const phase = rotte.phases[rotte.currentPhaseIndex]
      if (!phase) continue
      result.push({
        instanceId: inst.instanceId,
        instanceName: inst.templateName,
        rotteId: rotte.rotteId,
        rotteName: rotte.name,
        passeName: phase.passeName,
        phaseIndex: rotte.currentPhaseIndex,
        players: rotte.players,
        blocks: phase.blocks,
      })
    }
  }
  return result
}
```

Then add `getActiveCompetitionRotten` to the `return` object at the bottom of the store:

```js
return {
  activeInstances,
  completedInstances,
  startPasse,
  startTraining,
  startCompetition,
  getBlocksForRange,
  getActiveCompetitionRotten,   // ← add this
  markBlockInProgress,
  markBlockDone,
  stopInstance,
  stopCompetition,
  assignRotteToRange,
  unassignRotte,
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
npm run test src/stores/__tests__/activePasseStore.test.js
```

Expected: All `getActiveCompetitionRotten` tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/stores/activePasseStore.js src/stores/__tests__/activePasseStore.test.js
git commit -m "[ui] Add getActiveCompetitionRotten to activePasseStore"
```

---

### Task 2: Wire competition Rotten into ShooterFlyoutPanel

**Files:**
- Modify: `src/components/shooter-remote/ShooterFlyoutPanel.vue`
- Test: `src/components/__tests__/ShooterFlyoutPanel.test.js`

- [ ] **Step 1: Write the failing component test**

Open `src/components/__tests__/ShooterFlyoutPanel.test.js`. Add this describe block (keep any existing tests):

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ShooterFlyoutPanel from '../shooter-remote/ShooterFlyoutPanel.vue'

// Minimal stub for child icons component
vi.mock('@/components/Icons.vue', () => ({
  default: { template: '<span />' },
}))

describe('ShooterFlyoutPanel — competition Rotten', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders one card per active competition Rotte', async () => {
    const { useActivePasseStore } = await import('@/stores/activePasseStore.js')
    const { useShooterRemoteStore } = await import('@/stores/shooterRemoteStore.js')
    const { usePasseStore } = await import('@/stores/passeStore.js')

    const activePasseStore = useActivePasseStore()
    const remoteStore = useShooterRemoteStore()
    const passeStore = usePasseStore()

    // Seed a competition with 2 rotten
    const template = {
      id: 'c1',
      name: 'Frühjahrspokal',
      passen: [
        {
          id: 'p1',
          name: 'Passe 1',
          serien: [{ id: 's1', name: 'A', alias: 'A', steps: [], rangeId: 'range-1', rangeName: 'Platz 1' }],
        },
      ],
    }
    activePasseStore.startCompetition(template, [
      { rotteId: 'r1', name: 'Rotte 1', players: [{ id: 'u1', displayName: 'Max' }] },
      { rotteId: 'r2', name: 'Rotte 2', players: [{ id: 'u2', displayName: 'Lisa' }] },
    ])

    remoteStore.selectedRangeId = 'range-1'
    passeStore.passeMode = false

    const wrapper = mount(ShooterFlyoutPanel, {
      global: { stubs: { Icons: true, RouterLink: true } },
    })

    // Open the panel
    await wrapper.find('.flyout-handle').trigger('click')

    const cards = wrapper.findAll('[data-testid="competition-rotte-card"]')
    expect(cards).toHaveLength(2)
    expect(cards[0].text()).toContain('Rotte 1')
    expect(cards[0].text()).toContain('Passe 1')
    expect(cards[1].text()).toContain('Rotte 2')
  })

  it('does not show done rotten', async () => {
    const { useActivePasseStore } = await import('@/stores/activePasseStore.js')
    const { useShooterRemoteStore } = await import('@/stores/shooterRemoteStore.js')
    const { usePasseStore } = await import('@/stores/passeStore.js')

    const activePasseStore = useActivePasseStore()
    useShooterRemoteStore().selectedRangeId = 'range-1'
    usePasseStore().passeMode = false

    const template = {
      id: 'c2',
      name: 'Cup',
      passen: [{ id: 'p1', name: 'P1', serien: [{ id: 's1', name: 'A', alias: 'A', steps: [], rangeId: null, rangeName: null }] }],
    }
    activePasseStore.startCompetition(template, [
      { rotteId: 'r1', name: 'Rotte 1', players: [] },
    ])
    activePasseStore.activeInstances[0].rotten[0].status = 'done'

    const wrapper = mount(ShooterFlyoutPanel, {
      global: { stubs: { Icons: true, RouterLink: true } },
    })
    await wrapper.find('.flyout-handle').trigger('click')

    const cards = wrapper.findAll('[data-testid="competition-rotte-card"]')
    expect(cards).toHaveLength(0)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm run test src/components/__tests__/ShooterFlyoutPanel.test.js
```

Expected: FAIL — `data-testid="competition-rotte-card"` elements not found

- [ ] **Step 3: Update `competitionSerien` computed in ShooterFlyoutPanel**

In `src/components/shooter-remote/ShooterFlyoutPanel.vue`, find the `<script setup>` section.

Replace:
```js
const competitionSerien = computed(() => {
  // Placeholder for future API integration
  return [];
});
```

With:
```js
const competitionSerien = computed(() => activePasseStore.getActiveCompetitionRotten());
```

- [ ] **Step 4: Fix `passenBlocks` to exclude competition blocks**

In the same `<script setup>`, find:

```js
const passenBlocks = computed(() =>
  activePasseStore.getBlocksForRange(currentRangeId.value)
    .filter(b => b.instanceType !== 'training')
);
```

Replace with:

```js
const passenBlocks = computed(() =>
  activePasseStore.getBlocksForRange(currentRangeId.value)
    .filter(b => b.instanceType !== 'training' && b.instanceType !== 'competition')
);
```

- [ ] **Step 5: Add `playCompetitionRotte` handler**

In `<script setup>`, add this function after the `playBlock` function:

```js
const playCompetitionRotte = (item) => {
  activePasseStore.assignRotteToRange(item.instanceId, item.rotteId, currentRangeId.value);
  const firstBlock = item.blocks.find(b => b.status !== 'done');
  if (!firstBlock) return;
  playStore.pendingPasseInfo = {
    serie: {
      id: firstBlock.serieId,
      name: firstBlock.serieAlias,
      alias: firstBlock.serieAlias,
      steps: firstBlock.steps,
      rangeId: firstBlock.rangeId,
      rangeName: firstBlock.rangeName,
    },
    rangeId: currentRangeId.value,
    instanceId: item.instanceId,
    blockId: firstBlock.blockId,
    players: item.players,
    rotteId: item.rotteId,
  };
  isOpen.value = false;
  router.push(`/remote/${currentRangeId.value}/play`);
};
```

- [ ] **Step 6: Update the "Offene Wettkämpfe" template block**

In `<template>`, find this existing block:

```html
<!-- Offene Wettkämpfe -->
<template v-if="competitionSerien.length > 0">
  <div class="section">
    <span class="section-label">Offene Wettkämpfe</span>
    <div class="serien-list">
      <div
        v-for="serie in competitionSerien"
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
          <span v-if="isSerieCompleted(serie.id)" class="completion-badge">✓ Done</span>
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
```

Replace the entire block with:

```html
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
            {{ item.players.map(p => p.displayName).join(', ') }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 7: Add CSS for `rotte-instance-name`**

In the `<style scoped>` section, add after the `.block-template-name` rule:

```css
.rotte-instance-name {
  font-size: 10px;
  color: rgba(79, 195, 247, 0.5);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
```

- [ ] **Step 8: Also update the `empty-state` v-if to include `competitionSerien`**

Find the existing empty-state condition:

```html
v-if="passenBlocks.length === 0 && competitionSerien.length === 0 && trainingBlocks.length === 0 && userSerien.length === 0 && globalSerien.length === 0"
```

This already includes `competitionSerien.length === 0` — no change needed. ✓

- [ ] **Step 9: Run the component tests**

```bash
npm run test src/components/__tests__/ShooterFlyoutPanel.test.js
```

Expected: competition Rotten tests PASS

- [ ] **Step 10: Run full test suite**

```bash
npm run test
```

Expected: All tests PASS (or only pre-existing failures, none new)

- [ ] **Step 11: Commit**

```bash
git add src/components/shooter-remote/ShooterFlyoutPanel.vue src/components/__tests__/ShooterFlyoutPanel.test.js
git commit -m "[ui] Show competition Rotten as separate cards in ShooterFlyoutPanel"
```

---

### Task 3: Manual verification

- [ ] **Step 1: Start the dev server**

```bash
npm run dev
```

Open `http://localhost:5173` and log in as a shooter.

- [ ] **Step 2: Create and start a competition**

1. Navigate to **Wettkampf** (from ShooterHome).
2. Go to the **Wettkämpfe** tab, click **Neuer Wettkampf**.
3. Name it, select 1–2 Passen, click **Erstellen**.
4. Expand the new competition card, click **Starten**.
5. In the RotteSetupModal, add 2 Rotten with players, confirm.
6. The view should switch to the **Aktiv** tab showing the competition.

- [ ] **Step 3: Open any range's remote view**

Navigate to `/remote` → select any range.

Expected: The flyout panel (pull from right) shows an **Offene Wettkämpfe** section with two cards:
- `Rotte 1 · Passe 1`
- `Rotte 2 · Passe 1`

Each card shows the competition name above and the players below.

- [ ] **Step 4: Tap a Rotte card**

Tap one Rotte card.

Expected:
- Navigates to `/remote/:rangeId/play` with that Rotte's first block loaded.
- The play page shows the correct players.

- [ ] **Step 5: Verify passenBlocks no longer double-shows competition blocks**

After tapping a Rotte card (range is now assigned), go back and reopen the flyout.

Expected: The competition block does NOT appear in the regular Passen section — only in "Offene Wettkämpfe".

- [ ] **Step 6: Commit if any minor fixes were needed during manual testing**

```bash
git add -p
git commit -m "[ui] Fix: <describe what you fixed>"
```

---

## Self-review checklist

- ✅ `getActiveCompetitionRotten()` tested: empty case, 2 rotten, done exclusion, no range filter
- ✅ `competitionSerien` computed wired to new store method
- ✅ `passenBlocks` filter updated to exclude `instanceType === 'competition'`
- ✅ `playCompetitionRotte` assigns range + finds first pending block + navigates
- ✅ Template replaces placeholder with real Rotte cards using `data-testid`
- ✅ `rotte-instance-name` CSS added
- ✅ Empty-state condition already includes `competitionSerien` — no change needed
- ✅ No placeholders in any step
- ✅ Type/name consistency: `getActiveCompetitionRotten` used in both store and component
