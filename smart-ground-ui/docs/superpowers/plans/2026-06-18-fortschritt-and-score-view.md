# Fortschritt Accuracy (B) + Score-View Step-Chip Rework (C) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the live Fortschritt tab show per-Serie completion accurately, and rework the completed-competition step-chips to group by Serie with position letters and per-target hit/miss coloring.

**Architecture:** B is a one-spot fix in `ActiveCompetitionPanel` reading the progress payload's `completedSerien`. C adds a serie-definition map to the completed-results cache, a `getPlayerSerien` composable selector that joins step states to position letters, a reworked `StepScorecard` that renders per-Serie split-letter chips, and switches both results views to it.

**Tech Stack:** Vue 3 `<script setup>`, Pinia, Vitest + @vue/test-utils.

**Spec:** `docs/superpowers/specs/2026-06-18-fortschritt-and-score-view-design.md`

---

## Shared Data Contract (C)

`getPlayerSerien(playerId)` returns an array (sorted by `sortIndex`) of:

```
{
  key: `${passeIndex}:${serieId}`,
  passeIndex: number,
  rangeName: string | null,
  serieName: string,
  sortIndex: number,
  steps: [ { stepIndex, type, letter, letter1, letter2, state } ],   // type/letters null when no serie def
}
```

`StepScorecard` consumes this via a `serien` prop. Step state values come from `@/constants/playEnums.js` `StepState` (`pending`, `done`, `failed-a`, `failed-b`, `failed-both`).

---

## Task 1 (B): Per-Serie status + "Serie:" label in Fortschritt

**Files:**
- Modify: `src/components/competition/ActiveCompetitionPanel.vue`
- Test: `src/components/__tests__/ActiveCompetitionPanel.test.js`

- [ ] **Step 1: Write failing tests.** Append inside the `describe` in `ActiveCompetitionPanel.test.js`:

```javascript
  it('marks an individual Serie done from completedSerien (not whole-Passe)', async () => {
    wettkampfApi.getProgress.mockResolvedValue({
      groups: [{
        groupId: 'g1', groupName: 'Rotte A', passenTotal: 1, passenCompleted: 0,
        completions: [{ passeIndex: 0, passeName: 'Passe 1', completed: false }],
        completedSerien: [{ passeIndex: 0, serieId: 's1' }],
      }],
    })
    const passeStore = usePasseStore()
    passeStore.savedPassen = [{ id: 'pa1', name: 'Passe 1', serien: [
      { id: 's1', alias: 'Erste' },
      { id: 's2', alias: 'Zweite' },
    ] }]
    const event = {
      ...makeEvent(),
      groups: [{ id: 'g1', name: 'Rotte A', members: [] }],
    }
    const { wrapper } = await mountPanel(event)
    await flushPromises()
    const fortschrittTab = wrapper.findAll('.tab-btn').find(t => t.text() === 'Fortschritt')
    await fortschrittTab.trigger('click')
    const cards = wrapper.findAll('.serie-card')
    const erste = cards.find(c => c.find('.serie-card-header').text().includes('Erste'))
    const zweite = cards.find(c => c.find('.serie-card-header').text().includes('Zweite'))
    expect(erste.find('.rotte-chip').text()).toContain('Fertig')
    expect(zweite.find('.rotte-chip').text()).toContain('Offen')
  })

  it('prefixes serie cards with "Serie:"', async () => {
    const passeStore = usePasseStore()
    passeStore.savedPassen = [{ id: 'pa1', name: 'Passe 1', serien: [{ id: 's1', alias: 'Erste' }] }]
    const event = { ...makeEvent(), groups: [{ id: 'g1', name: 'Rotte A', members: [] }] }
    const { wrapper } = await mountPanel(event)
    await flushPromises()
    const fortschrittTab = wrapper.findAll('.tab-btn').find(t => t.text() === 'Fortschritt')
    await fortschrittTab.trigger('click')
    expect(wrapper.find('.serie-card-header').text()).toContain('Serie:')
  })
```

- [ ] **Step 2: Run and verify failure.**

Run: `npm run test -- src/components/__tests__/ActiveCompetitionPanel.test.js`
Expected: FAIL — `s2` reads done (whole-Passe logic) / header lacks "Serie:".

- [ ] **Step 3: Fix `serieCards` status + header.** In `src/components/competition/ActiveCompetitionPanel.vue`, in the `serieCards` computed, replace the `rotteRows` mapping. Current:

```javascript
    rotteRows: (props.event.groups ?? []).map(group => {
      const prog = progressGroups.find(g => g.groupId === group.id)
      const passeCompletion = prog?.completions?.[idx]
      return {
        rotteId: group.id,
        rotteName: group.name,
        status: passeCompletion?.completed ? 'done' : 'pending',
      }
    }),
```

becomes:

```javascript
    rotteRows: (props.event.groups ?? []).map(group => {
      const prog = progressGroups.find(g => g.groupId === group.id)
      const done = (prog?.completedSerien ?? []).some(
        c => c.passeIndex === idx && c.serieId === serie.id,
      )
      return {
        rotteId: group.id,
        rotteName: group.name,
        status: done ? 'done' : 'pending',
      }
    }),
```

In the template, change the serie card header from `{{ card.serieAlias }}` to `Serie: {{ card.serieAlias }}`:

```html
        <div class="serie-card-header">Serie: {{ card.serieAlias }}</div>
```

- [ ] **Step 4: Run and verify pass.**

Run: `npm run test -- src/components/__tests__/ActiveCompetitionPanel.test.js`
Expected: PASS (all, including the two new tests).

- [ ] **Step 5: Commit.**

```bash
git add src/components/competition/ActiveCompetitionPanel.vue src/components/__tests__/ActiveCompetitionPanel.test.js
git commit -m "[ui] competition: per-Serie Fortschritt status + Serie: label"
```

---

## Task 2 (C): Data layer — serieDefs cache + getPlayerSerien

**Files:**
- Modify: `src/stores/competitionEventStore.js`
- Modify: `src/composables/useCompletedResults.js`
- Test: `src/stores/__tests__/competitionEventStore.test.js`, `src/composables/__tests__/useCompletedResults.test.js`

- [ ] **Step 1: Write failing composable tests.** In `src/composables/__tests__/useCompletedResults.test.js`, extend the `seed` object with a `serieDefs` map (add inside the `s1` object, alongside `serieResults`):

```javascript
        serieDefs: {
          se1: { rangeName: 'Stand 1', serieName: 'Morgen', sortIndex: 0, steps: [
            { type: 'solo', letter: 'A', letter1: null, letter2: null },
            { type: 'pair', letter: null, letter1: 'B', letter2: 'D' },
          ] },
          se2: { rangeName: 'Stand 2', serieName: 'Abend', sortIndex: 1, steps: [
            { type: 'solo', letter: 'C', letter1: null, letter2: null },
          ] },
        },
```

Replace the two `getPlayerSteps` tests (`getPlayerSteps groups a player's step states by Passe` and `getPlayerSteps returns [] ...`) with:

```javascript
  it('getPlayerSerien joins step states to serie definitions (range, name, letters)', () => {
    const store = useCompetitionEventStore()
    seed(store)
    const { getPlayerSerien } = useCompletedResults('s1')
    const serien = getPlayerSerien('m1')
    expect(serien.map(s => s.serieName)).toEqual(['Morgen', 'Abend'])
    expect(serien[0].rangeName).toBe('Stand 1')
    expect(serien[0].steps).toEqual([
      { stepIndex: 0, type: 'solo', letter: 'A', letter1: null, letter2: null, state: 'done' },
      { stepIndex: 1, type: 'pair', letter: null, letter1: 'B', letter2: 'D', state: 'failed-a' },
    ])
  })

  it('getPlayerSerien degrades gracefully when a serie has no definition', () => {
    const store = useCompetitionEventStore()
    seed(store)
    store.completedResultsBySession.s1.serieDefs = {} // no defs
    const { getPlayerSerien } = useCompletedResults('s1')
    const serien = getPlayerSerien('m1')
    expect(serien).toHaveLength(2)
    expect(serien[0].steps[0]).toEqual(
      { stepIndex: 0, type: null, letter: null, letter1: null, letter2: null, state: 'done' },
    )
  })

  it('getPlayerSerien returns [] for a player with no serie results', () => {
    const store = useCompetitionEventStore()
    seed(store)
    const { getPlayerSerien } = useCompletedResults('s1')
    expect(getPlayerSerien('m2')).toEqual([])
  })
```

- [ ] **Step 2: Write failing store test.** In `src/stores/__tests__/competitionEventStore.test.js`, locate the existing `loadCompletedResults` test (it mocks `getLeaderboard`/`getSession`/`getSerieResults`). Add a new test after it:

```javascript
  it('loadCompletedResults builds a serieDefs map from the session passen', async () => {
    api.getLeaderboard.mockResolvedValue({ playerScores: [] })
    api.getSerieResults.mockResolvedValue([])
    api.getSession.mockResolvedValue({
      id: 's1', groups: [],
      passen: [
        { serien: [
          { id: 'se1', alias: 'Morgen', rangeName: 'Stand 1', steps: [
            { type: 'solo', letter: 'A' },
            { type: 'pair', letter1: 'B', letter2: 'D' },
          ] },
        ] },
        { serien: [{ id: 'se2', name: 'Abend', steps: [] }] },
      ],
    })
    const store = useCompetitionEventStore()
    await store.loadCompletedResults('s1')
    const defs = store.completedResultsBySession.s1.serieDefs
    expect(defs.se1).toEqual({
      rangeName: 'Stand 1', serieName: 'Morgen', sortIndex: 0,
      steps: [
        { type: 'solo', letter: 'A', letter1: null, letter2: null },
        { type: 'pair', letter: null, letter1: 'B', letter2: 'D' },
      ],
    })
    expect(defs.se2.serieName).toBe('Abend')
    expect(defs.se2.sortIndex).toBe(1)
  })
```

(If the existing `loadCompletedResults` test asserts the full shape of the cached entry with `toEqual`, relax it to `toMatchObject` or add `serieDefs` to its expectation so it still passes.)

- [ ] **Step 3: Run and verify failure.**

Run: `npm run test -- src/composables/__tests__/useCompletedResults.test.js src/stores/__tests__/competitionEventStore.test.js`
Expected: FAIL — `getPlayerSerien` undefined; `serieDefs` undefined.

- [ ] **Step 4: Build serieDefs in the store.** In `src/stores/competitionEventStore.js`, inside `loadCompletedResults`, after the `getSession` result is available and before building `completedResultsBySession`, add:

```javascript
      // Serie definitions (range, name, step position letters) for the per-Serie
      // step-chip view. Keyed by serieId; sortIndex preserves passe→serie order.
      const serieDefs = {}
      let serieSortIndex = 0
      for (const passe of (session.passen ?? [])) {
        for (const serie of (passe.serien ?? [])) {
          serieDefs[serie.id] = {
            rangeName: serie.rangeName ?? null,
            serieName: serie.alias ?? serie.name ?? 'Serie',
            sortIndex: serieSortIndex++,
            steps: (serie.steps ?? []).map(s => ({
              type: s.type ?? null,
              letter: s.letter ?? null,
              letter1: s.letter1 ?? null,
              letter2: s.letter2 ?? null,
            })),
          }
        }
      }
```

Add `serieDefs` to the stored entry:

```javascript
      completedResultsBySession.value = {
        ...completedResultsBySession.value,
        [sessionId]: {
          standings,
          serieResults: serieResults ?? [],
          serieDefs,
          completedAt: session.completedAt ?? null,
        },
      }
```

- [ ] **Step 5: Add `getPlayerSerien`, remove `getPlayerSteps`.** In `src/composables/useCompletedResults.js`, delete the `getPlayerSteps` function and replace it with:

```javascript
  // Per-Serie step groups for a player, joined to the serie definitions (range,
  // name, per-step position letters). Ordered by the serie's sortIndex. Degrades
  // to letter-less steps when a serie definition is absent.
  const getPlayerSerien = (playerId) => {
    const serieResults = entry.value?.serieResults ?? []
    const defs = entry.value?.serieDefs ?? {}
    const groups = []
    for (const sr of serieResults) {
      const playerEntry = (sr.results ?? []).find(r => r.playerId === playerId)
      const states = playerEntry?.stepStates ?? []
      if (states.length === 0) continue
      const def = defs[sr.serieId] ?? null
      const steps = states.map(s => {
        const ds = def?.steps?.[s.stepIndex] ?? null
        return {
          stepIndex: s.stepIndex ?? 0,
          type: ds?.type ?? null,
          letter: ds?.letter ?? null,
          letter1: ds?.letter1 ?? null,
          letter2: ds?.letter2 ?? null,
          state: s.state,
        }
      })
      groups.push({
        key: `${sr.passeIndex ?? 0}:${sr.serieId}`,
        passeIndex: sr.passeIndex ?? 0,
        rangeName: def?.rangeName ?? null,
        serieName: def?.serieName ?? 'Serie',
        sortIndex: def?.sortIndex ?? (sr.passeIndex ?? 0),
        steps,
      })
    }
    return groups.sort((a, b) => a.sortIndex - b.sortIndex)
  }
```

Update the `return { ... }` to export `getPlayerSerien` instead of `getPlayerSteps`:

```javascript
  return { standings, completedAt, loading, error, load, getPlayerDetail, getPlayerSerien }
```

- [ ] **Step 6: Run and verify pass.**

Run: `npm run test -- src/composables/__tests__/useCompletedResults.test.js src/stores/__tests__/competitionEventStore.test.js`
Expected: PASS.

- [ ] **Step 7: Commit.**

```bash
git add src/stores/competitionEventStore.js src/composables/useCompletedResults.js src/stores/__tests__/competitionEventStore.test.js src/composables/__tests__/useCompletedResults.test.js
git commit -m "[ui] competition: serieDefs cache + getPlayerSerien selector"
```

---

## Task 3 (C): StepScorecard — per-Serie split-letter chips

**Files:**
- Modify: `src/components/competition/StepScorecard.vue`
- Test: `src/components/competition/__tests__/StepScorecard.test.js`

- [ ] **Step 1: Rewrite the test for the `serien` prop.** Replace the entire contents of `src/components/competition/__tests__/StepScorecard.test.js` with:

```javascript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StepScorecard from '../StepScorecard.vue'

const serien = [
  { key: '0:se1', passeIndex: 0, rangeName: 'Stand 1', serieName: 'Morgen', sortIndex: 0, steps: [
    { stepIndex: 0, type: 'solo', letter: 'A', letter1: null, letter2: null, state: 'done' },
    { stepIndex: 1, type: 'pair', letter: null, letter1: 'B', letter2: 'D', state: 'failed-a' },
  ] },
  { key: '1:se2', passeIndex: 1, rangeName: null, serieName: 'Abend', sortIndex: 1, steps: [
    { stepIndex: 0, type: 'pair', letter: null, letter1: 'E', letter2: 'F', state: 'pending' },
  ] },
]

describe('StepScorecard', () => {
  it('renders a header per Serie as "RangeName – SerieName" (or just name)', () => {
    const wrapper = mount(StepScorecard, { props: { serien } })
    const headers = wrapper.findAll('.serie-label').map(n => n.text())
    expect(headers[0]).toBe('Stand 1 – Morgen')
    expect(headers[1]).toBe('Abend')
  })

  it('renders a solid chip for a solo step, colored by state', () => {
    const wrapper = mount(StepScorecard, { props: { serien } })
    const solo = wrapper.findAll('.step-chip')[0]
    expect(solo.classes()).toContain('is-done')
    expect(solo.text()).toContain('A')
    expect(solo.find('.half').exists()).toBe(false)
  })

  it('renders a split chip for a doublette, coloring each half by target', () => {
    const wrapper = mount(StepScorecard, { props: { serien } })
    // second chip = pair B/D, failed-a → left (B) miss, right (D) hit
    const split = wrapper.findAll('.step-chip')[1]
    const halves = split.findAll('.half')
    expect(halves).toHaveLength(2)
    expect(halves[0].text()).toBe('B')
    expect(halves[0].classes()).toContain('half--fail')
    expect(halves[1].text()).toBe('D')
    expect(halves[1].classes()).toContain('half--done')
  })

  it('renders pending halves grey', () => {
    const wrapper = mount(StepScorecard, { props: { serien } })
    const pendingChip = wrapper.findAll('.step-chip')[2] // E/F pending
    const halves = pendingChip.findAll('.half')
    expect(halves[0].classes()).toContain('half--pending')
    expect(halves[1].classes()).toContain('half--pending')
  })

  it('gives every chip a non-empty aria-label', () => {
    const wrapper = mount(StepScorecard, { props: { serien } })
    for (const chip of wrapper.findAll('.step-chip')) {
      expect(chip.attributes('aria-label')).toBeTruthy()
    }
  })

  it('renders nothing when there are no Serien', () => {
    const wrapper = mount(StepScorecard, { props: { serien: [] } })
    expect(wrapper.find('.step-chip').exists()).toBe(false)
  })
})
```

- [ ] **Step 2: Run and verify failure.**

Run: `npm run test -- src/components/competition/__tests__/StepScorecard.test.js`
Expected: FAIL — component still expects `passen`.

- [ ] **Step 3: Rewrite `StepScorecard.vue`.** Replace its `<template>` and `<script setup>` with:

```html
<template>
  <div class="step-scorecard">
    <div v-for="serie in serien" :key="serie.key" class="serie-group">
      <span class="serie-label">{{ headerLabel(serie) }}</span>
      <div class="chips">
        <template v-for="step in serie.steps" :key="step.stepIndex">
          <!-- split chip: two-result step (pair / a_schuss / raffale) -->
          <span
            v-if="isSplit(step.type)"
            class="step-chip step-chip--split"
            :aria-label="ariaLabel(step)"
            :title="ariaLabel(step)"
          >
            <span class="half" :class="halfClass(step.state, 'left')">{{ leftLabel(step) }}</span>
            <span class="half" :class="halfClass(step.state, 'right')">{{ rightLabel(step) }}</span>
          </span>
          <!-- solid chip: single-target / unknown -->
          <span
            v-else
            class="step-chip"
            :class="solidClass(step.state)"
            :aria-label="ariaLabel(step)"
            :title="ariaLabel(step)"
          >{{ step.letter ?? '' }}</span>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { StepState, StepType } from '@/constants/playEnums.js'

defineProps({
  serien: { type: Array, required: true },
})

const isSplit = (type) =>
  type === StepType.PAIR || type === StepType.A_SCHUSS || type === StepType.RAFFALE

const headerLabel = (serie) =>
  serie.rangeName ? `${serie.rangeName} – ${serie.serieName}` : serie.serieName

const solidClass = (state) => {
  if (state === StepState.DONE) return 'is-done'
  if (state === StepState.PENDING) return 'is-pending'
  return 'is-fail'
}

// left half = first clay (letter1 / first raffale shot); right half = second.
const leftLabel = (step) => step.letter1 ?? step.letter ?? ''
const rightLabel = (step) => step.letter2 ?? step.letter ?? ''

const halfClass = (state, side) => {
  if (state === StepState.PENDING) return 'half--pending'
  const missed =
    state === StepState.FAILED_BOTH ||
    (side === 'left' && state === StepState.FAILED_A) ||
    (side === 'right' && state === StepState.FAILED_B)
  return missed ? 'half--fail' : 'half--done'
}

const resultWord = (hit, pending) => (pending ? 'Offen' : hit ? 'getroffen' : 'Fehler')

const ariaLabel = (step) => {
  const pending = step.state === StepState.PENDING
  if (isSplit(step.type)) {
    const leftHit = halfClass(step.state, 'left') === 'half--done'
    const rightHit = halfClass(step.state, 'right') === 'half--done'
    return `${leftLabel(step)} ${resultWord(leftHit, pending)}, ${rightLabel(step)} ${resultWord(rightHit, pending)}`
  }
  const hit = step.state === StepState.DONE
  return `${step.letter ?? '?'} ${resultWord(hit, pending)}`
}
</script>
```

Replace the `<style scoped>` block with:

```html
<style scoped>
.step-scorecard { display: flex; flex-direction: column; gap: 8px; }

.serie-group { display: flex; flex-direction: column; gap: 4px; }

.serie-label {
  font-size: 11px; font-weight: 700; letter-spacing: 0.03em; opacity: 0.6;
}

.chips { display: flex; flex-wrap: wrap; gap: 6px; }

.step-chip {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 28px; height: 24px; padding: 0 8px; border-radius: 8px;
  font-size: 12px; font-weight: 700; border: 1px solid transparent; overflow: hidden;
}

.step-chip.is-done { background: rgba(72, 187, 120, 0.16); color: #2f855a; border-color: rgba(72, 187, 120, 0.4); }
.step-chip.is-fail { background: rgba(229, 62, 62, 0.14); color: #c53030; border-color: rgba(229, 62, 62, 0.4); }
.step-chip.is-pending { background: rgba(160, 174, 192, 0.14); color: #718096; border-color: rgba(160, 174, 192, 0.4); }

/* split chip: two independently-colored halves */
.step-chip--split { padding: 0; border-color: rgba(160, 174, 192, 0.4); }
.step-chip--split .half {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 22px; height: 100%; padding: 0 6px; font-size: 12px; font-weight: 700;
}
.half--done { background: rgba(72, 187, 120, 0.16); color: #2f855a; }
.half--fail { background: rgba(229, 62, 62, 0.14); color: #c53030; }
.half--pending { background: rgba(160, 174, 192, 0.14); color: #718096; }

/* On dark kiosk backgrounds the muted colors lose contrast — lift them. */
:global(.results-view) .step-chip.is-done, :global(.results-view) .half--done { color: #9ae6b4; }
:global(.results-view) .step-chip.is-fail, :global(.results-view) .half--fail { color: #feb2b2; }
:global(.results-view) .step-chip.is-pending, :global(.results-view) .half--pending { color: #cbd5e0; }
</style>
```

- [ ] **Step 4: Run and verify pass.**

Run: `npm run test -- src/components/competition/__tests__/StepScorecard.test.js`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add src/components/competition/StepScorecard.vue src/components/competition/__tests__/StepScorecard.test.js
git commit -m "[ui] competition: StepScorecard per-Serie split-letter chips"
```

---

## Task 4 (C): Switch both results views to getPlayerSerien

**Files:**
- Modify: `src/components/competition/CompletedResultsPanel.vue`
- Modify: `src/views/shooter/CompetitionResultsView.vue`
- Test: `src/components/__tests__/CompletedResultsPanel.test.js`, `src/views/shooter/__tests__/CompetitionResultsView.test.js`

- [ ] **Step 1: Update both consumer tests.** In BOTH `CompletedResultsPanel.test.js` and `CompetitionResultsView.test.js`, add a `passen` array to the `session` fixture so serie defs resolve (place it inside the `session` object). For `CompletedResultsPanel.test.js` (serieResults use serieIds `x` and `z`):

```javascript
  passen: [
    { serien: [{ id: 'x', alias: 'Morgen', rangeName: 'Stand 1', steps: [
      { type: 'solo', letter: 'A' },
      { type: 'pair', letter1: 'B', letter2: 'D' },
    ] }] },
    { serien: [{ id: 'z', alias: 'Abend', rangeName: 'Stand 2', steps: [
      { type: 'solo', letter: 'C' },
    ] }] },
  ],
```

For `CompetitionResultsView.test.js` (serieResults use serieId `x`):

```javascript
  passen: [
    { serien: [{ id: 'x', alias: 'Morgen', rangeName: 'Stand 1', steps: [
      { type: 'solo', letter: 'A' },
      { type: 'pair', letter1: 'B', letter2: 'D' },
    ] }] },
  ],
```

In `CompletedResultsPanel.test.js`, replace the `renders per-step chips in the expanded detail` test body with an assertion on the new structure:

```javascript
  it('renders serie-grouped step chips in the expanded detail', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.findAll('.standing-row')[0].trigger('click') // Bob (m2)
    expect(wrapper.find('.serie-label').text()).toContain('Stand 1 – Morgen')
    expect(wrapper.findAll('.step-chip').length).toBeGreaterThanOrEqual(2)
  })
```

In `CompetitionResultsView.test.js`, change the last assertion of `expands per-Passe detail on tap` from `expect(wrapper.findAll('.step-chip').length)...` to also check the serie header:

```javascript
    expect(wrapper.find('.serie-label').exists()).toBe(true)
    expect(wrapper.findAll('.step-chip').length).toBeGreaterThanOrEqual(2)
```

(The per-Passe totals assertions — `Passe 1`, `24 / 25`, `18 / 25` — stay unchanged.)

- [ ] **Step 2: Run and verify failure.**

Run: `npm run test -- src/components/__tests__/CompletedResultsPanel.test.js src/views/shooter/__tests__/CompetitionResultsView.test.js`
Expected: FAIL — `.serie-label` not found (views still pass `getPlayerSteps`/`:passen`).

- [ ] **Step 3: Switch `CompletedResultsPanel.vue`.** Change the composable destructure from `getPlayerSteps` to `getPlayerSerien`:

```javascript
const { standings, completedAt, loading, error, load, getPlayerDetail, getPlayerSerien } = useCompletedResults(sessionId)
```

Rename the steps cache helper to serien and use `getPlayerSerien`:

```javascript
const serienCache = new Map()
const serienFor = (playerId) => {
  if (!serienCache.has(playerId)) serienCache.set(playerId, getPlayerSerien(playerId))
  return serienCache.get(playerId)
}
```

Update `onMounted` to clear `serienCache` instead of `stepsCache`:

```javascript
onMounted(() => {
  detailCache.clear()
  serienCache.clear()
  load()
})
```

In the template, change the `StepScorecard` usage:

```html
          <StepScorecard
            v-if="serienFor(row.playerId).length > 0"
            class="step-detail"
            :serien="serienFor(row.playerId)"
          />
```

- [ ] **Step 4: Switch `CompetitionResultsView.vue`.** Apply the identical changes: destructure `getPlayerSerien` (not `getPlayerSteps`); replace `stepsCache`/`stepsFor` with `serienCache`/`serienFor` using `getPlayerSerien`; clear `serienCache` in `onMounted`; and change the `StepScorecard` binding to `:serien="serienFor(row.playerId)"` with `v-if="serienFor(row.playerId).length > 0"`.

- [ ] **Step 5: Run and verify pass.**

Run: `npm run test -- src/components/__tests__/CompletedResultsPanel.test.js src/views/shooter/__tests__/CompetitionResultsView.test.js`
Expected: PASS.

- [ ] **Step 6: Full verification.**

Run: `npm run test`
Expected: PASS (whole suite).

Run: `npm run lint:check`
Expected: no new errors in touched files.

- [ ] **Step 7: Commit.**

```bash
git add src/components/competition/CompletedResultsPanel.vue src/views/shooter/CompetitionResultsView.vue src/components/__tests__/CompletedResultsPanel.test.js src/views/shooter/__tests__/CompetitionResultsView.test.js
git commit -m "[ui] competition: results views use per-Serie step chips"
```

---

## Self-Review Notes

- **Spec coverage:** B → Task 1; C data join → Task 2; C chip rework (grouping, letters, split coloring, aria-labels, letters-only) → Task 3; C consumers + retained per-Passe totals → Task 4. All covered.
- **Type/name consistency:** `getPlayerSerien` shape (with `key`, `passeIndex`, `rangeName`, `serieName`, `sortIndex`, `steps[{stepIndex,type,letter,letter1,letter2,state}]`) is defined in Task 2 and consumed identically in Tasks 3–4. `serieDefs` shape built in Task 2 store matches what `getPlayerSerien` reads. Chip classes (`is-done`/`is-fail`/`is-pending`, `half--done`/`half--fail`/`half--pending`) match between Task 3 component and its tests.
- **Per-target coloring:** `FAILED_A` → left (`letter1`) miss, `FAILED_B` → right (`letter2`) miss — matches `ScoreTable`'s `getStateLabel` semantics and the spec.
- **YAGNI / dead code:** `getPlayerSteps` removed in Task 2; old `passen`-based `StepScorecard` fully replaced in Task 3.
