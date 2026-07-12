# Competition Flow Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix six defects in the competition (Wettkampf) flow: premature Serie commit, lost score corrections, missing first-shooter ready prompt, UUID-instead-of-name after admin correction, stale stores after correction, and moving admin score editing onto the Rangliste with the completed-results visuals.

**Architecture:** Three independent groups. **Group A** is kiosk-side (`ShooterPlayPage.vue` + `playSessionStore.js`): defer the backend commit until "Beenden" and add a ready prompt for the first shooter. **Group B** is admin data integrity (`competitionEventStore.js` + `useCompletedResults.js` + `CompetitionCorrectionPanel.vue`): preserve `displayName` through corrections and refresh ties/leaderboard after a correction. **Group C** is admin UX (`ActiveCompetitionPanel.vue` + `CompletedResultsPanel.vue`): make the Rangliste reuse the completed-results visuals in an editable mode and retire the Fortschritt correction panel. Do groups in order A → B → C; C depends on B.

**Tech Stack:** Vue 3 (`<script setup>`, Composition API), Pinia, Vitest + @vue/test-utils. Run tests from `smart-ground-ui/` with `npm run test`.

---

## Shared Context (read before starting)

**Score model.** A `stepState` is `{ playerId, serieIndex, stepIndex, state, pointValue, pointsEarned, noBirds, corrected, originalState }`. `state ∈ playEnums.StepState` = `pending | done | failed-a | failed-b | failed-both`. Deduction: `failed-both → 2`, `failed-a|failed-b → 1`, else 0. `pointsEarned = max(0, pointValue - deduction)`.

**Kiosk result payload.** `playSessionStore.buildPlayerResults()` ([smart-ground-ui/src/stores/playSessionStore.js:171](../../../smart-ground-ui/src/stores/playSessionStore.js)) returns per-player `{ playerId, displayName, totalPoints, maxPoints, stepStates }`. This is the canonical Serie-result shape sent to `wettkampfApi.completeSerie`. **Admin corrections must send the same shape** (this is the crux of #4).

**Backend endpoints** ([smart-ground-ui/src/services/wettkampfApi.js](../../../smart-ground-ui/src/services/wettkampfApi.js)):
- `completeSerie(sessionId, groupId, serieId, passeIndex, playInstanceId, results)` — POST, kiosk path.
- `correctSerieResult(sessionId, groupId, serieId, passeIndex, results)` — PUT `/serien/{serieId}/results`, admin path. Overwrites the stored Serie result with `results`.
- `getSerieResults(sessionId)` — GET, returns rows `{ groupId, serieId, passeIndex, results: [{ playerId, displayName, totalPoints, maxPoints, stepStates }] }`.
- `getLeaderboard`, `getProgress`, `tiebreakerApi.getTies`.

**Verification convention.** Every task lists the exact Vitest file/command. There is no e2e harness — verify behavior through component/store unit tests plus a final manual smoke test (Group D).

---

## GROUP A — Kiosk: defer commit + first-shooter prompt (#1, #2, #3)

Files in play:
- Modify: `smart-ground-ui/src/views/shooter/ShooterPlayPage.vue`
- Modify: `smart-ground-ui/src/stores/playSessionStore.js`
- Test: `smart-ground-ui/src/stores/__tests__/playSessionStore.*.test.js`, `smart-ground-ui/src/components/__tests__/ShooterPlayPageNextShooter.test.js`

### Task A1: Split "reach program end" from "commit results" in the store (#1, #2)

**Problem:** `confirmComplete()` both sets `playComplete = true` (shows final screen) AND calls `markBlockDone()` (persists to backend). Corrections on the final screen happen after the persist, so they are lost, and the Serie commits before "Beenden".

**Approach:** Introduce a dedicated commit action `commitResults()` that performs the backend submission using the *current* (possibly corrected) `buildPlayerResults()`. `confirmComplete()` only transitions UI state (`playComplete = true`) and must NOT persist. The "Beenden" button calls `commitResults()` then closes.

**Files:**
- Modify: `smart-ground-ui/src/stores/playSessionStore.js:505-526` (`confirmComplete`)
- Test: `smart-ground-ui/src/stores/__tests__/playSessionStore.competition.test.js` (create if absent; an `activePasseStore.competition.test.js` already exists as a pattern reference)

- [ ] **Step 1: Write failing store test** — assert reaching end does not persist, and `commitResults()` sends the corrected payload.

```javascript
// playSessionStore.competition.test.js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePlaySessionStore } from '@/stores/playSessionStore.js'
import { StepState } from '@/constants/playEnums.js'

const markBlockDone = vi.fn()
vi.mock('@/stores/competitionEventStore.js', () => ({
  useCompetitionEventStore: () => ({ markBlockDone, markBlockInProgress: vi.fn() }),
}))

describe('playSessionStore competition commit timing', () => {
  beforeEach(() => { setActivePinia(createPinia()); markBlockDone.mockClear() })

  it('confirmComplete shows final screen but does NOT persist', async () => {
    const store = usePlaySessionStore()
    store.activeBlockContext = { instanceId: 'i1', blockId: 'b1', rotteId: 'r1', instanceType: 'competition' }
    store.sessionPlayers = [{ id: 'p1', displayName: 'Anna' }]
    store.playScore = { totalPoints: 1, stepStates: [
      { playerId: 'p1', serieIndex: 0, stepIndex: 0, state: StepState.DONE, pointValue: 1, pointsEarned: 1 },
    ] }
    await store.confirmComplete()
    expect(store.playComplete).toBe(true)
    expect(markBlockDone).not.toHaveBeenCalled()
  })

  it('commitResults persists the current (corrected) results', async () => {
    const store = usePlaySessionStore()
    store.activeBlockContext = { instanceId: 'i1', blockId: 'b1', rotteId: 'r1', instanceType: 'competition' }
    store.sessionPlayers = [{ id: 'p1', displayName: 'Anna' }]
    store.playScore = { totalPoints: 0, stepStates: [
      { playerId: 'p1', serieIndex: 0, stepIndex: 0, state: StepState.FAILED_BOTH, pointValue: 1, pointsEarned: 0 },
    ] }
    await store.confirmComplete()
    await store.commitResults()
    expect(markBlockDone).toHaveBeenCalledOnce()
    const [, , results] = markBlockDone.mock.calls[0]
    expect(results[0].totalPoints).toBe(0)
    expect(results[0].displayName).toBe('Anna')
  })
})
```

- [ ] **Step 2: Run test, confirm it fails** — `cd smart-ground-ui && npm run test src/stores/__tests__/playSessionStore.competition.test.js` → FAIL (markBlockDone called during confirmComplete; commitResults undefined).

- [ ] **Step 3: Refactor the store.** Move the block-commit side-effects out of `confirmComplete` into a new `commitResults` action. `confirmComplete` keeps only the UI transition + legacy-session cleanup. Make `commitResults` idempotent (guard with a `resultsCommitted` ref so double "Beenden"/unmount can't double-submit).

```javascript
// add near other state refs
const resultsCommitted = ref(false)

// replace confirmComplete body:
const confirmComplete = async () => {
  playComplete.value = true
}

// new action — call this from the "Beenden" handler:
const commitResults = async () => {
  if (resultsCommitted.value) return
  resultsCommitted.value = true
  if (activeBlockContext.value) {
    const ctx = activeBlockContext.value
    const results = buildPlayerResults()
    if (ctx.instanceType === 'competition') {
      const { useCompetitionEventStore } = await import('@/stores/competitionEventStore.js')
      useCompetitionEventStore().markBlockDone(ctx.instanceId, ctx.blockId, results, ctx.rotteId ?? null)
    } else {
      const { useActivePasseStore } = await import('@/stores/activePasseStore.js')
      useActivePasseStore().markBlockDone(ctx.instanceId, ctx.blockId, results)
    }
    activeBlockContext.value = null
  }
  if (currentSessionId.value) {
    activeSessions.value = activeSessions.value.filter((s) => s.sessionId !== currentSessionId.value)
    currentSessionId.value = null
    saveSessions()
  }
}
```

Reset `resultsCommitted.value = false` inside `closePlayback()` and at the start of each new play (`playPasseWithScore`, `startGroupPlay`, `resumeSession`). Export `commitResults` and `resultsCommitted` in the store's return object.

- [ ] **Step 4: Run test, confirm pass.** Same command → PASS.

- [ ] **Step 5: Update the kiosk "Beenden" handler.** In `ShooterPlayPage.vue`, the two `@click="goBack"` Beenden buttons (solo [ShooterPlayPage.vue:177-184](../../../smart-ground-ui/src/views/shooter/ShooterPlayPage.vue) and group [:229-236](../../../smart-ground-ui/src/views/shooter/ShooterPlayPage.vue)) must commit first. Change `goBack` so that when `store.playComplete` is true it commits:

```javascript
const goBack = async () => {
  if (store.playComplete) await store.commitResults()
  store.closePlayback()
  router.push(`/remote/${props.rangeId}`)
}
```

Also update the `onBeforeUnmount` guard: currently `if (!store.playComplete) store.closePlayback()`. Leave abandon-without-complete behavior as-is (no commit). Confirm `commitResults` is not called on abandon.

- [ ] **Step 6: Run the existing kiosk tests** — `npm run test src/components/__tests__/ShooterPlayPageAudit.test.js src/components/__tests__/ShooterPlayPageNextShooter.test.js` → PASS (fix any that asserted persist-on-complete; update them to assert persist-on-Beenden).

- [ ] **Step 7: Commit** — `git add -A && git commit -m "[ui] defer competition Serie commit to Beenden so final-screen corrections persist"`.

### Task A2: First shooter sees the ready prompt (#3)

**Problem:** The ready overlay only shows between shooters. The first shooter has no "schießbereit machen" prompt.

**Approach:** After `beginGroupPlay()` starts a multi-player session, show the same `next-shooter-overlay` for the first shooter before the carousel becomes interactive. Reuse the existing overlay markup/styles; gate it on a flag that starts true for multiplayer.

**Files:**
- Modify: `smart-ground-ui/src/views/shooter/ShooterPlayPage.vue` (`beginGroupPlay`, `confirmNextShooter`, overlay binding)
- Test: `smart-ground-ui/src/components/__tests__/ShooterPlayPageNextShooter.test.js`

- [ ] **Step 1: Write failing component test** — mounting into a fresh multiplayer group session shows the ready overlay naming the first player; clicking "Starten" hides it.

```javascript
it('shows ready overlay for the FIRST shooter on group start', async () => {
  // arrange a store with >1 sessionPlayers and playProg set, playComplete false
  // mount ShooterPlayPage, call beginGroupPlay path
  expect(wrapper.find('.next-shooter-overlay').exists()).toBe(true)
  expect(wrapper.find('.next-shooter-name').text()).toContain('Schütze 1')
  await wrapper.find('.next-shooter-start-btn').trigger('click')
  expect(wrapper.find('.next-shooter-overlay').exists()).toBe(false)
})
```

- [ ] **Step 2: Run test, confirm it fails.** `npm run test src/components/__tests__/ShooterPlayPageNextShooter.test.js` → FAIL.

- [ ] **Step 3: Implement.** The overlay currently shows `store.nextPlayer?.displayName`. Generalize it to show the *upcoming* shooter (first on start, next between turns). Add a `readyPlayer` computed and a single `showReadyOverlay` flag:

```javascript
// readyPlayer = whoever the prompt is about
const readyPlayer = computed(() =>
  pendingFirstShooter.value ? store.currentPlayer : store.nextPlayer
)
const pendingFirstShooter = ref(false)

const beginGroupPlay = () => {
  store.startGroupPlay(/* …existing args… */)
  if (store.isMultiPlayer) { pendingFirstShooter.value = true; showNextShooterOverlay.value = true }
}

const confirmNextShooter = () => {
  showNextShooterOverlay.value = false
  if (pendingFirstShooter.value) { pendingFirstShooter.value = false; return } // first shooter just begins
  store.advanceToNextPlayer()
}
```

Bind the overlay name to `readyPlayer?.displayName` instead of `store.nextPlayer?.displayName` ([ShooterPlayPage.vue:324](../../../smart-ground-ui/src/views/shooter/ShooterPlayPage.vue)). Keep the single-player path untouched (no overlay).

- [ ] **Step 4: Run test, confirm pass.** Same command → PASS.

- [ ] **Step 5: Regression** — `npm run test src/components/__tests__/ShooterPlayPageNextShooter.test.js src/components/__tests__/ShooterPlayPageAudit.test.js` → PASS.

- [ ] **Step 6: Commit** — `git add -A && git commit -m "[ui] show ready prompt for the first shooter in group play"`.

---

## GROUP B — Admin correction data integrity (#4, #5)

Files in play:
- Modify: `smart-ground-ui/src/components/competition/CompetitionCorrectionPanel.vue`
- Modify: `smart-ground-ui/src/composables/useCompletedResults.js`
- Modify: `smart-ground-ui/src/stores/competitionEventStore.js`
- Test: `smart-ground-ui/src/components/competition/__tests__/CompetitionCorrectionPanel.test.js`, `smart-ground-ui/src/composables/__tests__/useCompletedResults.test.js`, `smart-ground-ui/src/stores/__tests__/competitionEventStore.test.js`

### Task B1: Preserve `displayName` through a correction (#4)

**Problem:** `applyPick` builds `results` without `displayName`; the backend overwrites the Serie result and `displayName` is lost → UUID shown after reload.

**Approach (belt + suspenders):**
1. **Primary fix:** include `displayName` in each player's corrected result, matching `buildPlayerResults()`.
2. **Defensive fix:** in `useCompletedResults.getCorrectionData`, resolve `displayName` from `standings` (leaderboard, keyed by `playerId`) when the serie-result row lacks it, so a previously-corrupted record still renders a name.

**Files:**
- Modify: `smart-ground-ui/src/components/competition/CompetitionCorrectionPanel.vue:100-123` (add `displayName` to each result)
- Modify: `smart-ground-ui/src/composables/useCompletedResults.js:118-148` (`getCorrectionData` displayName fallback to standings)

- [ ] **Step 1: Write failing test** — `CompetitionCorrectionPanel.test.js`: after `applyPick`, the payload passed to `store.correctSerieResult` includes `displayName` for every player.

```javascript
it('includes displayName in corrected results payload', async () => {
  // mount with a stubbed useCompletedResults returning one serie, two named players
  // trigger a chip correction → pick a state
  const results = store.correctSerieResult.mock.calls[0][4]
  expect(results.every(r => typeof r.displayName === 'string' && r.displayName.length)).toBe(true)
})
```

- [ ] **Step 2: Run test, confirm fail.** `npm run test src/components/competition/__tests__/CompetitionCorrectionPanel.test.js` → FAIL.

- [ ] **Step 3: Implement primary fix** in `applyPick` — add `displayName: player.displayName` to the returned object:

```javascript
return {
  playerId: player.playerId,
  displayName: player.displayName ?? null,
  totalPoints: totals.totalPoints,
  maxPoints: totals.maxPoints,
  stepStates: steps,
}
```

- [ ] **Step 4: Implement defensive fix** in `getCorrectionData` — join displayName from standings:

```javascript
const standingsById = new Map((entry.value?.standings ?? []).map(s => [s.playerId, s.displayName]))
// inside players map:
displayName: r.displayName ?? standingsById.get(r.playerId) ?? null,
```

Add a `useCompletedResults.test.js` case asserting `getCorrectionData` returns the standings name when a serie-result row has `displayName: null`.

- [ ] **Step 5: Run tests, confirm pass.** `npm run test src/components/competition/__tests__/CompetitionCorrectionPanel.test.js src/composables/__tests__/useCompletedResults.test.js` → PASS.

- [ ] **Step 6: Commit** — `git add -A && git commit -m "[ui] preserve shooter displayName through admin Serie corrections"`.

### Task B2: Refresh ties + leaderboard in-store after a correction (#5)

**Problem:** `correctSerieResult` reloads only `completedResultsBySession`. Stechen/Gleichstand (`tiesBySession`) and the live leaderboard stay stale until a manual page reload.

**Approach:** After the PUT succeeds, reload the affected caches in the store action so every consumer (correction panel, Rangliste, Stechen panel, finish-guard) reacts. Reload `loadCompletedResults` (already) **and** `loadTies`. The Rangliste tab in `ActiveCompetitionPanel` polls `getLeaderboard` every 4s, but to make the update immediate, trigger an out-of-band leaderboard refresh too — simplest is to have the store own a `leaderboardBySession` refresh, but to stay minimal, reload ties here and let Group C drive the Rangliste off `completedResultsBySession` (which we just refreshed).

**Files:**
- Modify: `smart-ground-ui/src/stores/competitionEventStore.js:483-486` (`correctSerieResult`)
- Test: `smart-ground-ui/src/stores/__tests__/competitionEventStore.test.js` (or `.stechen.test.js`)

- [ ] **Step 1: Write failing store test** — `correctSerieResult` calls both `loadCompletedResults` and `loadTies` for the session.

```javascript
it('correctSerieResult refreshes completed results AND ties', async () => {
  vi.mocked(wettkampfApi.correctSerieResult).mockResolvedValue({})
  vi.mocked(tiebreakerApi.getTies).mockResolvedValue({ tiedBlocks: [] })
  // stub loadCompletedResults deps (getLeaderboard/getSession/getSerieResults)
  const store = useCompetitionEventStore()
  await store.correctSerieResult('s1', 'g1', 'serie1', 0, [])
  expect(tiebreakerApi.getTies).toHaveBeenCalledWith('s1')
})
```

- [ ] **Step 2: Run test, confirm fail.** `npm run test src/stores/__tests__/competitionEventStore.test.js` → FAIL.

- [ ] **Step 3: Implement.**

```javascript
const correctSerieResult = async (sessionId, groupId, serieId, passeIndex, playerResults) => {
  await wettkampfApi.correctSerieResult(sessionId, groupId, serieId, passeIndex, playerResults)
  await Promise.all([
    loadCompletedResults(sessionId),
    loadTies(sessionId).catch(e => console.error('[competitionEventStore] tie refresh failed:', e)),
  ])
}
```

(`loadTies` is defined later in the store; reference is fine since both are closures created in the same setup scope.)

- [ ] **Step 4: Run test, confirm pass.** Same command → PASS.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "[ui] refresh ties + standings after a Serie correction (no reload needed)"`.

---

## GROUP C — Move editing onto the Rangliste with completed visuals (#6)

**Goal:** In PRE_COMPLETE, the **Rangliste** tab shows the same expandable standings + per-Serie `StepScorecard` detail used for COMPLETED competitions, but with the chips **editable**. The **Fortschritt** tab no longer hosts score editing.

Files in play:
- Modify: `smart-ground-ui/src/components/competition/CompletedResultsPanel.vue` (add editable mode)
- Modify: `smart-ground-ui/src/components/competition/ActiveCompetitionPanel.vue` (Rangliste tab renders the editable panel for PRE_COMPLETE; remove correction panel from Fortschritt)
- Possibly delete: `smart-ground-ui/src/components/competition/CompetitionCorrectionPanel.vue` once unreferenced
- Reuse: `StepScorecard.vue` (already supports `editable` + `@correct-step`), `StepStatePicker.vue`, `useCompletedResults` (`getCorrectionData`, `recomputeSerieTotals`)
- Test: `smart-ground-ui/src/components/__tests__/CompletedResultsPanel.test.js`, `smart-ground-ui/src/components/__tests__/ActiveCompetitionPanel.test.js`

> **Decision needed before C2** — see "Open Questions". Two designs: (a) extend `CompletedResultsPanel` with an `editable` prop and fold the correction picker + `applyPick` logic into it (single reusable panel, retire `CompetitionCorrectionPanel`); or (b) keep panels separate and only restyle the PRE_COMPLETE Rangliste to mirror the completed layout. Plan assumes **(a)** — one panel, two modes — because it best satisfies "reuse the visuals built for done competitions."

### Task C1: Make `CompletedResultsPanel` support an editable mode

**Approach:** Add `editable: Boolean` (default false). When editable:
- Detail expands as today, but the per-Serie `StepScorecard` is rendered with `:editable="true"` and an `@correct-step` handler.
- Per-player detail/standings come from `getCorrectionData(passeIndex)` joined across passen (editable needs `groupId`/`serieId`/`pointValue` that `getPlayerSerien` drops), while the rank/total header rows still come from `standings`.
- A correction picker overlay (lift `StepStatePicker` + `applyPick` from `CompetitionCorrectionPanel`) calls `store.correctSerieResult(...)`. Because of Group B, that refreshes `completedResultsBySession` + ties, so the panel re-renders corrected scores live.
- **Caching caveat:** `detailCache`/`serienCache` (Maps) memoize per-player detail. In editable mode they must be cleared (or bypassed) whenever `completedResultsBySession` changes, or corrected values won't show. Replace the manual Map cache with `computed` maps keyed off the reactive store entry, or clear caches in a `watch` on the store entry.

**Files:**
- Modify: `smart-ground-ui/src/components/competition/CompletedResultsPanel.vue`
- Test: `smart-ground-ui/src/components/__tests__/CompletedResultsPanel.test.js`

- [ ] **Step 1: Write failing test (display parity).** Mount with `editable: false` and a stubbed `useCompletedResults` → renders standings rows + expandable `StepScorecard` (non-editable). Assert no editable chips. This guards against regressing the COMPLETED view.

- [ ] **Step 2: Write failing test (editable mode).** Mount with `editable: true` → expanded detail renders chips with `step-chip--editable`; clicking one opens the picker; picking a state calls `store.correctSerieResult`.

- [ ] **Step 3: Run tests, confirm fail.** `npm run test src/components/__tests__/CompletedResultsPanel.test.js` → FAIL.

- [ ] **Step 4: Implement editable mode.** Add the prop, the picker overlay, `applyPick` (ported from `CompetitionCorrectionPanel.vue:91-131` — keep the `displayName` fix from B1), and switch the cache to reactive maps. Render `StepScorecard` with `:editable="editable"` and wire `@correct-step`. Source editable detail from `getCorrectionData` so chips carry `serieId/groupId/playerId/stepIndex/pointValue`.

- [ ] **Step 5: Run tests, confirm pass.** Same command → PASS.

- [ ] **Step 6: Commit** — `git add -A && git commit -m "[ui] add editable mode to CompletedResultsPanel reusing completed-results visuals"`.

### Task C2: Wire Rangliste = editable panel for PRE_COMPLETE; drop correction from Fortschritt

**Files:**
- Modify: `smart-ground-ui/src/components/competition/ActiveCompetitionPanel.vue` (Rangliste tab + Fortschritt tab)
- Test: `smart-ground-ui/src/components/__tests__/ActiveCompetitionPanel.test.js`

- [ ] **Step 1: Write failing test.** For a PRE_COMPLETE event: the **Fortschritt** tab no longer renders `CompetitionCorrectionPanel` (renders the normal serie cards), and the **Rangliste** tab renders `CompletedResultsPanel` with `editable` true. For an ACTIVE event the Rangliste still renders the simple polled leaderboard rows (or the read-only panel — confirm in Open Questions).

- [ ] **Step 2: Run test, confirm fail.** `npm run test src/components/__tests__/ActiveCompetitionPanel.test.js` → FAIL.

- [ ] **Step 3: Implement.** In `ActiveCompetitionPanel.vue`:
  - Fortschritt tab ([:98-125](../../../smart-ground-ui/src/components/competition/ActiveCompetitionPanel.vue)): remove the `<CompetitionCorrectionPanel v-if="isPreComplete">` branch; always render the serie cards.
  - Rangliste tab ([:128-140](../../../smart-ground-ui/src/components/competition/ActiveCompetitionPanel.vue)): when `isPreComplete`, render `<CompletedResultsPanel :event="event" editable />`; otherwise keep the existing live leaderboard rows. Ensure `CompletedResultsPanel` is loaded (it calls `loadCompletedResults` on mount). Drop the now-unused `CompetitionCorrectionPanel` import.

- [ ] **Step 4: Run test, confirm pass.** Same command → PASS.

- [ ] **Step 5: Delete dead code.** If `CompetitionCorrectionPanel.vue` is now unreferenced (`grep -r CompetitionCorrectionPanel smart-ground-ui/src`), delete it and its test, per the repo's "remove unused code eagerly" rule. If C1 ported its logic, this is expected.

- [ ] **Step 6: Run the full competition test suite** — `npm run test src/components/__tests__/ActiveCompetitionPanel.test.js src/components/__tests__/CompletedResultsPanel.test.js` → PASS.

- [ ] **Step 7: Commit** — `git add -A && git commit -m "[ui] move PRE_COMPLETE score editing to the Rangliste; retire Fortschritt correction panel"`.

---

## GROUP D — Full verification

- [ ] **Step 1: Whole suite + lint.** `cd smart-ground-ui && npm run test && npm run lint:check`. Both clean.

- [ ] **Step 2: Manual smoke (mock or local backend).** Walk the flows:
  1. Group competition Serie in the kiosk → reach end → correct a score on the final screen → click **Beenden** → reopen admin → corrected value persisted (#1, #2).
  2. Group play → first shooter sees the ready prompt before shooting (#3).
  3. Admin PRE_COMPLETE → correct a score → name stays a name, not a UUID (#4).
  4. Same correction → Stechen/Gleichstand flags and Rangliste totals update without a page reload (#5).
  5. Editing happens on the **Rangliste** tab with the expandable completed-results visuals; **Fortschritt** has no editor (#6).

- [ ] **Step 3: Finish branch** — use superpowers:finishing-a-development-branch.

---

## Decisions (resolved 2026-06-19)

1. **#6 panel design → (a) one panel, two modes.** Extend `CompletedResultsPanel` with an `editable` prop, use it for both COMPLETED and PRE_COMPLETE Rangliste, and retire `CompetitionCorrectionPanel`. Group C is written for this.
2. **Backend recomputes on correction → yes.** A Serie correction (PUT results) re-runs ranking + tie/Stechen detection server-side, so Group B's `loadCompletedResults` + `loadTies` reloads fully fix #5 on the frontend. No backend change needed.
3. **Remaining minor note (not blocking):** the ACTIVE-state read-only Rangliste still refreshes via the 4s `leaderboardData` poll. PRE_COMPLETE Rangliste routes through the immediately-refreshed `completedResultsBySession` (Group C), so the correction flow updates instantly.
