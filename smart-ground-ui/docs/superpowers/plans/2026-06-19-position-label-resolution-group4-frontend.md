# Position Label Resolution — Group 4: Frontend consumes server-resolved labels

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The frontend stops treating local `letter` as truth for completed results and stops the `updateSerie` delete+create hack. Completed scorecards read the Group 3 completion snapshot; editing a Serie uses Group 2's in-place `PUT` (stable ID), so a referencing Passe never needs "repair".

**Architecture:** Two independent changes. (1) `passeStore.updateSerie` calls `serieApi.updateSerie` with `steps` (Group 2's stable-ID PUT) instead of delete+create; the "repair Passen" reload and its `console.warn` are removed. (2) `competitionEventStore.loadCompletedResults` builds its `serieDefs` map from each completed serie-result's frozen `serieSnapshot` (Group 3) instead of joining the live `session.passen` definitions; `useCompletedResults` is unchanged (it still reads `serieDefs`, now sourced from the snapshot).

**Tech Stack:** Vue 3 (Composition API), Pinia, Vite, Vitest + @vue/test-utils, ESLint 9. Work from `smart-ground-ui`.

**Spec:** `docs/superpowers/specs/2026-06-19-position-label-resolution-design.md` (Group 4).

---

## Build / environment notes

- Work from `smart-ground-ui`.
- Run a single test file: `npm run test -- --run src/path/to/file.test.js`. Whole suite: `npm run test -- --run`.
- Lint check (no writes): `npm run lint:check`.
- Backend Group 2 (`serie-results`… no — Group 2 Serie `PUT` accepts `steps`) and Group 3 (`getSerieResults` returns `serieSnapshot`) are already merged on the backend `master`, so the API shapes this group consumes exist.

## Scope notes (deliberate boundaries)

- **`useCompletedResults` keeps reading `serieDefs`.** Rather than rewrite every consumer (`getPlayerSerien`, `getCorrectionData`, …) to read a per-row `serieSnapshot`, the store builds the same `serieDefs` shape from the snapshots. This honours the spec's intent — labels come from the frozen snapshot, never a live join — while keeping the composable's API and its tests stable. The only behavioural change is the **source** of `serieDefs`.
- **`session.passen` is no longer read for labels.** `getSession` is still fetched (for Rotte names / `completedAt`), just not mined for serie step letters.
- **Step builders keep writing `letter` locally.** `passeStore.addStep` still records `letter`/`alias` for the in-memory capture preview — that is live data being captured, not a frozen snapshot, and the backend now ignores/derives it on read. No change needed there.
- **Migration / cross-stack tests are Group 5.**

---

## File Structure

**Modify:**
- `src/services/serieApi.js` — `updateSerie` gains an optional `steps` argument.
- `src/stores/passeStore.js` — `updateSerie` does an in-place PUT (stable ID); drop delete+create, the re-publish dance, the "repair Passen" reload, and the `console.warn`.
- `src/stores/competitionEventStore.js` — `loadCompletedResults` builds `serieDefs` from `serieResults[].serieSnapshot`.

**Test (modify):**
- `src/stores/__tests__/passeStore.test.js`
- `src/stores/__tests__/competitionEventStore.test.js`

---

### Task 1: `updateSerie` — in-place PUT with steps (stable Serie ID)

**Files:**
- Modify: `src/services/serieApi.js`
- Modify: `src/stores/passeStore.js`
- Test: `src/stores/__tests__/passeStore.test.js`

- [ ] **Step 1: Write the failing tests**

Add to `src/stores/__tests__/passeStore.test.js` (inside the `describe('passeStore — Serie layer', …)` block). These assert the PUT carries steps, the ID is unchanged, and delete/create are NOT called:

```javascript
  it('updateSerie edits in place via PUT with steps, keeping the same Serie ID', async () => {
    serieApi.updateSerie.mockResolvedValue({
      id: 'ab1', name: 'Edited', ownership: 'user', rangeId: 'r1', rangeName: 'Stand 1',
      steps: [{ id: '1', type: 'solo', posId: 'pos-uuid', alias: 'A1', letter: 'A1' }], published: false,
    })
    const store = usePasseStore()
    store.savedSerien = [{
      id: 'ab1', name: 'Old', rangeId: 'r1', rangeName: 'Stand 1',
      steps: [], ownership: 'user', published: false,
    }]

    await store.updateSerie('ab1', 'Edited', [
      { id: '1', type: 'solo', positionId: 'pos-uuid', alias: 'A1', letter: 'A1' },
    ])

    expect(serieApi.updateSerie).toHaveBeenCalledWith(
      'ab1', 'Edited', 'r1',
      [{ id: '1', type: 'solo', posId: 'pos-uuid', alias: 'A1', letter: 'A1' }],
    )
    expect(serieApi.deleteSerie).not.toHaveBeenCalled()
    expect(serieApi.createSerie).not.toHaveBeenCalled()
    // same ID retained, name updated
    expect(store.savedSerien).toHaveLength(1)
    expect(store.savedSerien[0].id).toBe('ab1')
    expect(store.savedSerien[0].name).toBe('Edited')
  })

  it('updateSerie preserves the published flag on a range Serie', async () => {
    serieApi.updateSerie.mockResolvedValue({
      id: 'ab9', name: 'Pub', ownership: 'range', rangeId: 'r1', rangeName: 'Stand 1',
      steps: [], published: true,
    })
    const store = usePasseStore()
    store.savedSerien = [{
      id: 'ab9', name: 'Pub', rangeId: 'r1', rangeName: 'Stand 1',
      steps: [], ownership: 'range', published: true,
    }]

    await store.updateSerie('ab9', 'Pub', [])

    expect(serieApi.patchSeriePublished).not.toHaveBeenCalled()
    expect(store.savedSerien[0].published).toBe(true)
  })
```

- [ ] **Step 2: Run to verify failure**

Run: `npm run test -- --run src/stores/__tests__/passeStore.test.js`
Expected: FAIL — `serieApi.updateSerie` is currently called with only `(id, name, rangeId)` (no steps), and the current code calls `deleteSerie`/`createSerie`.

- [ ] **Step 3: Add the optional `steps` argument to `serieApi.updateSerie`**

In `src/services/serieApi.js`, replace `updateSerie`:

```javascript
export async function updateSerie(id, name, rangeId = null, steps = undefined) {
  const body = { name, rangeId }
  if (steps !== undefined) body.steps = steps
  return apiFetch(`/serien/${id}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}
```

(`renameSerie` in `passeStore` calls `updateSerie(id, name, rangeId)` with no `steps` → `steps` stays `undefined` → not sent → backend keeps the existing steps. Unchanged behaviour.)

- [ ] **Step 4: Rewrite `passeStore.updateSerie` to edit in place**

In `src/stores/passeStore.js`, replace the whole `updateSerie` function:

```javascript
  const updateSerie = async (serieId, newName, newSteps) => {
    const serie = savedSerien.value.find((s) => s.id === serieId);
    if (!serie) return;
    // Backend PUT accepts steps -> in-place edit keeps the stable Serie ID,
    // so referencing Passen never need repair.
    try {
      const apiSteps = (newSteps ?? []).map(toApiStep);
      const updated = await serieApi.updateSerie(serieId, newName, serie.rangeId ?? null, apiSteps);
      savedSerien.value = savedSerien.value.map((s) =>
        s.id === serieId ? toUiSerie({ ...updated, rangeName: serie.rangeName }) : s,
      );
    } catch (e) {
      console.error('Failed to update Serie:', e);
      throw e;
    }
  };
```

(This removes the `deleteSerie`+`createSerie` replacement, the `wasPublished`/`patchSeriePublished` re-publish, the `loadPassenFromStorage()` "repair Passen" reload, and the `console.warn`.)

- [ ] **Step 5: Run the tests to verify they pass**

Run: `npm run test -- --run src/stores/__tests__/passeStore.test.js`
Expected: PASS — including the pre-existing Serie-layer tests.

- [ ] **Step 6: Lint**

Run: `npm run lint:check`
Expected: no errors (confirms no now-unused imports/vars left behind, e.g. if `loadPassenFromStorage` becomes unused — verify it is still referenced elsewhere; it is used by `loadPassenFromStorage`'s own callers, so it stays).

- [ ] **Step 7: Commit**

```bash
git add src/services/serieApi.js src/stores/passeStore.js src/stores/__tests__/passeStore.test.js
git commit -m "[ui] updateSerie edits Serie in place via PUT (stable ID)

serieApi.updateSerie sends steps; passeStore.updateSerie replaces the
delete+create hack with an in-place PUT, dropping the published re-apply and the
'repair Passen' reload now that the Serie ID is stable (Group 2 backend).

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Completed results read the frozen snapshot, not the live passen join

**Files:**
- Modify: `src/stores/competitionEventStore.js`
- Test: `src/stores/__tests__/competitionEventStore.test.js`

- [ ] **Step 1: Rewrite the failing test**

In `src/stores/__tests__/competitionEventStore.test.js`, replace the test `builds a serieDefs map from the session passen` with one that drives `serieDefs` from the serie-result snapshots:

```javascript
    it('builds a serieDefs map from the serie-result snapshots (not the live passen)', async () => {
      api.getLeaderboard.mockResolvedValue({ playerScores: [] })
      // Live passen carry STALE labels that must be ignored now
      api.getSession.mockResolvedValue(mkSession({
        status: 'COMPLETED', groups: [],
        passen: [{ serien: [{ id: 'se1', alias: 'STALE', rangeName: 'STALE', steps: [{ type: 'solo', letter: 'STALE' }] }] }],
      }))
      api.getSerieResults.mockResolvedValue([
        { groupId: 'g1', passeIndex: 0, serieId: 'se1', results: [],
          serieSnapshot: { serieName: 'Morgen', rangeName: 'Stand 1', steps: [
            { type: 'solo', letter: 'A' },
            { type: 'pair', letter1: 'B', letter2: 'D' },
          ] } },
        { groupId: 'g1', passeIndex: 1, serieId: 'se2', results: [],
          serieSnapshot: { serieName: 'Abend', rangeName: null, steps: [] } },
      ])
      const store = useCompetitionEventStore()

      await store.loadCompletedResults('s1')

      const defs = store.completedResultsBySession['s1'].serieDefs
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

- [ ] **Step 2: Run to verify failure**

Run: `npm run test -- --run src/stores/__tests__/competitionEventStore.test.js`
Expected: FAIL — `serieDefs` is still built from `session.passen`, so `defs.se1.serieName` is `'STALE'` (and `se2` is absent).

- [ ] **Step 3: Build `serieDefs` from the snapshots**

In `src/stores/competitionEventStore.js`, inside `loadCompletedResults`, replace the `serieDefs` construction block (the `for (const passe of (session.passen ?? []))` loop) with:

```javascript
      // Serie definitions (range, name, step position letters) for the per-Serie
      // step-chip view come from each completed result's FROZEN snapshot (Group 3),
      // never from the live session — so a later position rename can't rewrite a
      // finished scorecard. Keyed by serieId; sortIndex preserves passe→serie order.
      const serieDefs = {}
      let serieSortIndex = 0
      const orderedResults = [...(serieResults ?? [])]
        .sort((a, b) => (a.passeIndex ?? 0) - (b.passeIndex ?? 0))
      for (const sr of orderedResults) {
        if (sr.serieId == null || serieDefs[sr.serieId]) continue
        const snap = sr.serieSnapshot ?? null
        serieDefs[sr.serieId] = {
          rangeName: snap?.rangeName ?? null,
          serieName: snap?.serieName ?? 'Serie',
          sortIndex: serieSortIndex++,
          steps: (snap?.steps ?? []).map(s => ({
            type: s.type ?? null,
            letter: s.letter ?? null,
            letter1: s.letter1 ?? null,
            letter2: s.letter2 ?? null,
          })),
        }
      }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `npm run test -- --run src/stores/__tests__/competitionEventStore.test.js`
Expected: PASS — including the other `loadCompletedResults` tests (the first test does not assert `serieDefs`; its `mkSerieResults` rows lack a snapshot, so their defs fall back to `serieName: 'Serie'` with empty steps, which it never checks).

- [ ] **Step 5: Run the full UI suite + lint**

Run: `npm run test -- --run`
Expected: PASS — confirms `useCompletedResults` and its component/view tests (`CompletedResultsPanel`, `CompetitionResultsView`) still pass; they read `serieDefs`, whose shape is unchanged.

Run: `npm run lint:check`
Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add src/stores/competitionEventStore.js src/stores/__tests__/competitionEventStore.test.js
git commit -m "[ui] completed results read frozen serie snapshot, not live passen

loadCompletedResults builds serieDefs from each serie-result's serieSnapshot
(Group 3) instead of joining live session.passen, so renaming a position after a
competition finishes no longer rewrites the finished scorecard.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Manual verification (end of Group 4)

1. Backend + UI running. Finish a competition; open its results — per-Serie step chips show the position letters (from the snapshot).
2. Rename a position used by that competition's Serie. Reload the completed-results view → letters are **unchanged** (snapshot), proving isolation end-to-end.
3. Edit a Serie's steps in the Serien editor (admin) → the Serie keeps the same ID; a Passe that references it still resolves (no "repair"/reload, no `console.warn` in the console).
4. Rename a position used by a live (not-yet-completed) Serie → the Serie/Passe views show the new label after reload (propagation).

---

## Self-review checklist (to complete during execution)

- **Spec coverage (Group 4):**
  - Serie/Passe display reads server-resolved `letter`/`alias` ✅ (already live since Group 1; verified by manual step 4).
  - `useCompletedResults` uses the Group 3 snapshot, not a live join ✅ (Task 2 — via the store builder; composable API unchanged).
  - `updateSerie` is a real in-place PUT; "repair Passen" reload + `console.warn` dropped ✅ (Task 1).
- **Placeholders:** none — every step has concrete code/commands.
- **Type consistency:** `serieApi.updateSerie(id, name, rangeId, steps?)` matches `passeStore.updateSerie`'s call and `renameSerie`'s 3-arg call; `serieSnapshot.steps[]` field names (`type/letter/letter1/letter2`) match what `serieDefs` consumers (`useCompletedResults.getPlayerSerien` etc.) already expect.
- **Out of scope (Group 5):** migration/reset of existing Passen; cross-stack propagation + isolation tests; deleted-position placeholder copy/aria finalisation.
- **Pre-execution reads to confirm:** that no other caller relies on `passeStore.updateSerie` triggering a Passen reload (grep `updateSerie(` usages in views), and that `loadPassenFromStorage` remains referenced after the reload line is removed.
</content>
