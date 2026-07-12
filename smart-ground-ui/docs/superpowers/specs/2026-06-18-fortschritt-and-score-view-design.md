# Fortschritt Accuracy (Task B) + Score-View Step-Chip Rework (Task C) — Design

**Date:** 2026-06-18
**Status:** Draft (pending user review)
**Component area:** Competition progress (admin live panel) + completed-competition score view (admin + shooter)
**Scope:** UI-only. No backend changes — all required data is already returned by existing endpoints.

## Problem

Two issues in how competition scoring is displayed:

- **B — Fortschritt tab shows every Serie as "Offen".** In [ActiveCompetitionPanel.vue](../../../src/components/competition/ActiveCompetitionPanel.vue), `serieCards` derives each Serie's per-Rotte status from the **whole-Passe** `completed` flag (`prog.completions[idx].completed`). A Serie therefore never reads as done until the entire Passe is finished, even though individual Serien complete one at a time. The card header also doesn't say what it's showing.

- **C — Completed score-view step-chips are coarse.** [StepScorecard.vue](../../../src/components/competition/StepScorecard.vue) groups chips by Passe and shows a `✓/✗` glyph plus `earned/max` numbers. It does not show which shooting positions a step used, and it can't show per-clay results on a doublette. The earlier score-view spec deferred position letters as a non-goal; this task delivers them.

## Goals

- **B:** Each Serie's per-Rotte status reflects that specific Serie's completion. Card header reads "Serie: …".
- **C:** In the completed-competition expandable per-shooter detail, the step-chips are grouped by Serie (header "RangeName – SerieName"), each chip shows its position letter(s), and points are conveyed by per-target coloring (no numbers/glyph). The existing per-Passe totals summary is retained above the chip groups.

## Non-Goals (YAGNI)

- Any backend change. B's data (`completedSerien`) and C's data (serie definitions + `stepStates`) are already returned by `GET /sessions/{id}/progress` and `GET /sessions/{id}` + `GET /sessions/{id}/serie-results`.
- Score correction / editing (that is Task E; C is read-only display).
- Reworking the live-play `ScoreTable.vue` (kiosk) — out of scope.

## Data Availability (verified)

- **B:** `GET /sessions/{id}/progress` returns, per group, `completedSerien: [{ passeIndex, serieId }]` (built by `CompetitionProgressService.buildSessionProgressResponse`). The frontend store already consumes it in `_hydrateProgress`; `ActiveCompetitionPanel` polls `getProgress` directly.
- **C:** `GET /sessions/{id}` returns `passen[] → serien[]` where each Serie carries `alias`/`name`, `rangeName`, and `steps[]` with `type` and position letters (`letter` for solo/raffale, `letter1`/`letter2` for pair/a_schuss) — confirmed in `openapi.yaml` (`EmbeddedSerie`/step schema) and `SessionService.mapToApiSessionResponse` (via `PlayMapper.toEmbeddedSerie`). `GET /sessions/{id}/serie-results` returns per-Serie `results[].stepStates[] = { stepIndex, state, pointsEarned, pointValue }`. Join `serieId` + `stepIndex`. `loadCompletedResults` already fetches both `getSession` and `getSerieResults`.

## Task B — Fortschritt per-Serie status

In `ActiveCompetitionPanel.vue`:

- `refresh()` already stores `progressData = getProgress(...)`. Each `progressData.groups[i]` has `completedSerien: [{ passeIndex, serieId }]`.
- In `serieCards`, replace the per-Rotte status derivation. For the active Passe index `idx` and each Serie `serie` of that Passe, for each group `prog`:
  ```js
  const done = (prog?.completedSerien ?? []).some(
    c => c.passeIndex === idx && c.serieId === serie.id,
  )
  return { rotteId: group.id, rotteName: group.name, status: done ? 'done' : 'pending' }
  ```
  (Drop the `passeCompletion?.completed` lookup. There is no per-Serie "in_progress" signal server-side, so status is `done` | `pending` only — `rowStatusLabel` already maps both.)
- Card header: change `{{ card.serieAlias }}` to `Serie: {{ card.serieAlias }}`.
- `serieAlias` is already on each card. No other logic changes (`activePasseIndex`, `passenProgress`, leaderboard untouched).

## Task C — Per-Serie step-chips with position letters

### Data layer — `useCompletedResults.js` + store

`loadCompletedResults` (in [competitionEventStore.js](../../../src/stores/competitionEventStore.js)) already fetches `session`. Extend the cached entry with a **serie-definition map** built from the session payload:

```
serieDefs: Map<serieId, { rangeName, serieName, steps: [{ type, letter, letter1, letter2 }] }>
```
built by iterating `session.passen[].serien[]` (key by `serie.id`; `serieName = serie.alias ?? serie.name`; `steps` keep `type` + letter fields, indexed positionally).

In `useCompletedResults.js`, add `getPlayerSerien(playerId)` returning per-Serie groups (ordered by `passeIndex`, then serie order within the Passe):

```
[{
  key: `${passeIndex}:${serieId}`,
  rangeName, serieName, passeIndex,
  steps: [{ stepIndex, type, letter, letter1, letter2, state }],
}]
```
- Source rows: the player's `serieResults` (rows whose `results[]` contain `playerId`).
- For each such row, look up `serieDefs.get(serieId)` for `rangeName`/`serieName`/step letters; join each `stepStates[i]` (`stepIndex`, `state`) to `serieDefs.steps[stepIndex]` (`type`, letters).
- **Graceful fallback:** if `serieDefs` has no entry for a `serieId` (e.g. older session), omit letters (`type: undefined`) — the chip then renders a plain state-colored chip without letters. Never throw.
- Keep the existing `getPlayerDetail` (per-Passe totals) and `getPlayerSteps` unchanged for now (`getPlayerSteps` becomes unused once views switch to `getPlayerSerien`; remove it in the same change to avoid dead code).

### Presentation — rework `StepScorecard.vue`

New prop: `serien` (array from `getPlayerSerien`) replacing `passen`. Render:

- One group per Serie: a header **`{{ rangeName }} – {{ serieName }}`** (em dash; if `rangeName` is absent, show just `serieName`), then a flex row of chips.
- **Chip rendering by step type:**
  - **Single-target** (`solo`): one solid chip showing `letter`. Class `is-done` (green) when `state === DONE`, `is-fail` (red) for any FAILED state, `is-pending` (grey) for PENDING.
  - **Two-result** (`pair`, `a_schuss`, `raffale`): a **split chip** with a left and right half, each colored independently:
    - left half green if first clay hit, right half green if second hit;
    - from state: `DONE` → both green; `FAILED_A` → left red, right green; `FAILED_B` → left green, right red; `FAILED_BOTH` → both red; `PENDING` → both grey.
    - Labels: `pair`/`a_schuss` show `letter1` (left) and `letter2` (right); `raffale` shows the single `letter` centered across the split.
  - Unknown/missing type (fallback): solid chip, no letter, colored by `DONE`/FAILED/PENDING as for single-target.
- **No numeric points, no `✓/✗` glyph** on chips — color carries the points.
- **Accessibility:** each chip gets a `title` and `aria-label` describing the result textually, e.g. `"B getroffen, D Fehler"` / `"A getroffen"` / `"Offen"`, so the meaning isn't color-only (WCAG). Derive from state + letters.
- Colors: reuse the existing semantic translucent tokens already in `StepScorecard` (`is-done`/`is-fail`/`is-pending` greens/reds that read on both the light admin panel and the dark kiosk via the `:global(.results-view)` overrides). The split chip uses the same green/red fills per half.

### Consumers

Both [CompletedResultsPanel.vue](../../../src/components/competition/CompletedResultsPanel.vue) (admin) and [CompetitionResultsView.vue](../../../src/views/shooter/CompetitionResultsView.vue) (shooter kiosk):
- Keep the per-Passe totals lines (`getPlayerDetail`) exactly as today.
- Replace their `getPlayerSteps` usage + `:passen` binding with `getPlayerSerien` + `:serien`. The per-Serie groups render below the totals.

## Files Touched

| File | Change |
|---|---|
| `src/components/competition/ActiveCompetitionPanel.vue` | B: per-Serie status from `completedSerien`; "Serie:" header |
| `src/stores/competitionEventStore.js` | C: cache `serieDefs` map in `loadCompletedResults` |
| `src/composables/useCompletedResults.js` | C: add `getPlayerSerien`; remove now-unused `getPlayerSteps` |
| `src/components/competition/StepScorecard.vue` | C: `serien` prop; per-Serie groups; split letter chips; aria-labels |
| `src/components/competition/CompletedResultsPanel.vue` | C: use `getPlayerSerien` / `:serien` |
| `src/views/shooter/CompetitionResultsView.vue` | C: use `getPlayerSerien` / `:serien` |
| Corresponding `__tests__` | B + C tests (see below) |

## Testing

**B — `ActiveCompetitionPanel.test.js`:**
- Mock `getProgress` to return one group with `completedSerien: [{ passeIndex: 0, serieId: 's1' }]` and a Passe (in `passeStore.savedPassen`) with serien `s1`, `s2`. On the Fortschritt tab, the `s1` row reads done (`✓ Fertig`) and `s2` reads offen (`○ Offen`).
- Assert a card header contains "Serie:".

**C — `useCompletedResults.test.js`:**
- Seed the store cache with a `session` (passen→serien with letters + rangeName) and `serieResults` (stepStates). `getPlayerSerien(playerId)` returns groups with correct `rangeName`/`serieName` and per-step `{ type, letter(s), state }`, ordered by passeIndex.
- Fallback: a `serieId` absent from `serieDefs` yields chips with no letters and does not throw.

**C — `StepScorecard.test.js`:**
- Given a `serien` prop with a solo step (DONE) and a doublette (`FAILED_A`), renders a serie header `RangeName – SerieName`, a solid green chip with the solo letter, and a split chip whose left half (letter1) is the fail color and right half (letter2) the done color.
- Each chip exposes a non-empty `aria-label`.
- Pending steps render grey.

**C — `CompletedResultsPanel.test.js` / `CompetitionResultsView.test.js`:**
- Update existing expanded-detail tests to the `getPlayerSerien`/`:serien` shape; assert per-Passe totals still render and serie-grouped chips appear.

## Conventions

- `<script setup>` + Composition API; `@/` imports; data flows store → composable → component.
- German display labels (Serie, Passe, Rangliste; chip aria-labels in German: "getroffen"/"Fehler"/"Offen").
- Colors from existing `StepScorecard` semantic tokens; no new hardcoded palette.
- Remove `getPlayerSteps` and the old `passen`-based `StepScorecard` rendering rather than leaving them beside the rework.
