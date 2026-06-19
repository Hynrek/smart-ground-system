# PRE_COMPLETE Score Correction (Task E) — Design

**Date:** 2026-06-18
**Status:** Draft (pending user review)
**Component area:** Competition Auswertung (PRE_COMPLETE) — admin score correction. Backend (serie-result overwrite) + frontend (admin Fortschritt-tab correction UI).
**Scope:** Cross-stack.

## Problem

In the Auswertung stage (`PRE_COMPLETE`) the admin must be able to **correct** a shooter's scores
per step (hit/miss), switch between Passen, and see a per-shooter score overview — all in the
**Fortschritt tab**. Today the Fortschritt tab only shows Serie completion status, and the backend
`completeSerie` **rejects** an already-completed Serie and **appends** to `programResults` (no
overwrite), so there is no correction path.

## Data model recap (verified)

For a completed Serie, results are persisted in two places:
- **`CompetitionSerieResult.results`** (JSON) — full per-player `stepStates`
  (`StepStateRecord`: `playerId, serieIndex, stepIndex, state, pointValue, pointsEarned, noBirds`).
  Returned by `GET /sessions/{id}/serie-results`.
- **`PlayerResult.programResults`** (JSON array) — per-passe/serie aggregates
  (`{passeIndex, serieId, totalPoints, maxPoints, completedAt}`). This is what `computeLeaderboard`
  sums.

A correction must overwrite **both**: the CSR `results` (corrected stepStates) and replace the
matching `{passeIndex, serieId}` entry in each player's `programResults`.

## Scoring rule (matches the kiosk `ScoreTable`)

`pointsEarned = max(0, pointValue - deduction(state))`, where `deduction`: `FAILED_BOTH → 2`,
`FAILED_A`/`FAILED_B → 1`, `DONE → 0`, `PENDING → pointValue` (0 earned). A Serie's per-player
totals are the sums of `pointsEarned` / `pointValue` over its steps.

## Goals

- In the PRE_COMPLETE Fortschritt tab: a **Passe switcher**, and per-shooter editable step
  scorecards grouped by Serie ("RangeName – SerieName").
- Tapping a step opens a state picker (Treffer / Fehler A / Fehler B / Beide, by step type);
  applying recomputes that Serie's totals and **persists** the whole corrected Serie result.
- The Rangliste reflects corrections (via the existing 4 s poll → `computeLeaderboard`).

## Non-Goals (YAGNI)

- Correcting in any status other than `PRE_COMPLETE`.
- Editing Passe/Serie structure, adding/removing steps, or no-bird counts.
- Live cross-device push (kiosks already don't live-sync; the admin corrects from the admin device).
- Touching the kiosk live-play correction path (`playSessionStore.correctStep`) — unchanged.

## Backend (contract-first)

### 1. `openapi.yaml`
New path (reuses `CompleteSerieRequest` as the body — it already carries `passeIndex` + `results:
PlayerResult[]` with `stepStates`):
```yaml
  /api/sessions/{sessionId}/groups/{groupId}/serien/{serieId}/results:
    put:
      summary: Correct a completed Serie's results (admin, PRE_COMPLETE)
      operationId: correctSerieResult
      tags: [Session]
      parameters: [ sessionId, groupId, serieId (all uuid path) ]
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: '#/components/schemas/CompleteSerieRequest' } } }
      responses:
        '200': SessionProgressResponse
        '404': Session/group not found
        '409': Serie not completed, or session not in PRE_COMPLETE
```
`mvn generate-sources` regenerates `SessionApi.correctSerieResult`.

### 2. `CompetitionSerieResultRepository`
Add finder: `Optional<CompetitionSerieResult> findBySessionIdAndGroupIdAndPasseIndexAndSerieId(UUID sessionId, UUID groupId, int passeIndex, UUID serieId)`.

### 3. `CompetitionProgressService.correctSerieResult(sessionId, groupId, serieId, request)`
- Load session; guard `status == PRE_COMPLETE` else `ConflictException`.
- `passeIndex` from request.
- Load the existing CSR row via the new finder; if absent → `ConflictException("Serie noch nicht abgeschlossen")`.
- Overwrite `csr.setResults(objectMapper.writeValueAsString(request.getResults()))`; save.
- **Replace** (not append) each member's `programResults` entry for `{passeIndex, serieId}` with
  the corrected `{totalPoints, maxPoints, completedAt(existing or now)}` from `request.getResults()`
  matching `playerId`. Extract the existing append logic in `upsertPlayerResultsFromApi` into a
  shared `writePlayerResultEntry(..., boolean replaceExisting)` so completeSerie keeps append-by-new
  (guarded against dupes) and correction uses replace-by-`{passeIndex,serieId}`.
- Return `buildSessionProgressResponse(sessionId)`.

### 4. `SessionController.correctSerieResult`
Implement the generated method; rethrow `RuntimeException` as-is (so `ConflictException` → 409),
wrap checked exceptions — mirroring `releaseNextPasse`.

## Frontend

### 1. `wettkampfApi.js`
```js
export const correctSerieResult = (sessionId, groupId, serieId, passeIndex, results) =>
  apiFetch(`/sessions/${sessionId}/groups/${groupId}/serien/${serieId}/results`, {
    method: 'PUT',
    body: JSON.stringify({ passeIndex, results }),
  })
```

### 2. `competitionEventStore.js`
- The existing `loadCompletedResults(sessionId)` already fetches leaderboard + session + serie-results
  and builds `serieDefs` — it works in PRE_COMPLETE too. Reuse it to load the correction data
  (standings + serieResults + serieDefs cached by session).
- New action `correctSerieResult(sessionId, groupId, serieId, passeIndex, playerResults)`: calls the
  API, then re-runs `loadCompletedResults(sessionId)` to refresh the cached serie-results/standings.

### 3. Correction selector — `useCompletedResults.js` (or a sibling)
Add `getCorrectionData(passeIndex)` deriving, for the chosen Passe, from the cached `serieResults` +
`serieDefs`:
- `serien`: `[{ groupId, serieId, serieIndex, rangeName, serieName, steps:[{stepIndex,type,letter,letter1,letter2}] }]` (ordered).
- per Serie, per player: the `stepStates` (`{stepIndex, state, pointValue, pointsEarned}`).
Plus a pure helper `recomputeSerieTotals(stepStates)` → `{totalPoints, maxPoints}` using the scoring rule.

### 4. UI — `CompetitionCorrectionPanel.vue` (new), in the Fortschritt tab (PRE_COMPLETE only)
Rendered by `ActiveCompetitionPanel` inside the Fortschritt tab **when `event.status === 'PRE_COMPLETE'`**
(the existing Serie-completion view stays for ACTIVE):
- **Passe switcher** (segmented buttons over `event.passen`).
- For the selected Passe, per Serie ("RangeName – SerieName"), per shooter: editable step chips
  (reuse a `StepScorecard` extended with an `editable` prop that emits `correct-step`
  `{ groupId, serieId, serieIndex, stepIndex, playerId, type, currentState }`).
- A **state-picker overlay** (`StepStatePicker.vue`, a new component modeled on the `ShooterPlayPage`
  picker — `ShooterPlayPage` is left unchanged to avoid touching the working kiosk path):
  Treffer + Fehler options by step type (single → Treffer/Fehler; double → Treffer/Fehler A/Fehler
  B/Beide). Selecting a state:
  - updates that step's `state` + `pointsEarned` locally;
  - recomputes the Serie's totals for that player (`recomputeSerieTotals`);
  - builds the full corrected `results` (all players' `stepStates` + recomputed totals for that Serie)
    and calls `store.correctSerieResult(sessionId, groupId, serieId, passeIndex, results)`.
- The Rangliste tab already polls `getLeaderboard` (4 s) and reflects the change.

### 5. `StepScorecard.vue` — editable mode
Add an optional `editable` prop. When true, chips are `<button>`s that emit
`correct-step` with the step identity; non-editable mode renders exactly as today (Task C). Keep the
per-target split coloring; PENDING is not a correction target.

## Testing

**Backend:**
- `CompetitionProgressServiceTest`: `correctSerieResult` overwrites the CSR row and replaces (not
  appends) the player's programResults entry; recomputed totals flow to `computeLeaderboard`;
  throws (→409) when the Serie isn't completed or status ≠ PRE_COMPLETE.
- Repository finder returns the row.

**Frontend:**
- `wettkampfApi` PUT shape.
- `competitionEventStore`: `correctSerieResult` posts then reloads.
- selector: `getCorrectionData(passeIndex)` shape; `recomputeSerieTotals` for solo/doublette across
  DONE/FAILED_A/FAILED_B/FAILED_BOTH/PENDING.
- `StepScorecard`: editable mode emits `correct-step`; non-editable unchanged.
- `CompetitionCorrectionPanel`: Passe switcher swaps the rendered Serien; picking a state calls the
  store action with the corrected Serie results; renders only the chosen Passe.
- `ActiveCompetitionPanel`: the correction panel shows in the Fortschritt tab only for PRE_COMPLETE.

## Conventions

- Backend: openapi-first → generate-sources → implement; `ConflictException` → 409; German messages.
- Frontend: `<script setup>`; store → composable → component; German labels ("Treffer", "Fehler",
  "Beide", "Passe"); colors via existing `StepScorecard` tokens; new `StepStatePicker` modeled on the
  kiosk picker (kiosk left unchanged).
- Extract the backend append logic into a shared replace-or-append helper rather than duplicating it.
