# Position Label Resolution — ID references over data snapshots

**Date:** 2026-06-19
**Status:** Approved design, pending spec review
**Scope:** Cross-stack (smart-ground-backend + smart-ground-ui)

## Problem

A Position has a stable `id` (UUID) and a mutable `label` (e.g. `"A"`). When a
position is renamed (`A → A1`), the new name does not propagate. Serien, Passen,
active sessions, and the shooter view keep showing the old label. A shooter sees
a "broken" Serie.

The root cause is that the data model snapshots the position label at multiple
levels instead of resolving it live from the position ID — even though every
element already carries the ID it would need to resolve.

### Where labels are snapshotted today

- **`Serie.stepsJson`** (backend) — steps are a JSON blob. Each step carries
  `posId` (the reference) **plus** `letter` / `alias` (a snapshot of the
  position label / device alias taken at Serie-creation time). All display code
  reads the snapshot, never `posId`.
  - Frontend mirrors: `SerieDrawer.vue:233-234`, `passeStore.js:148-149` write
    `letter = position.label` into each step at recording time.
- **`Passe.serienJson`** (backend) — documented as *"Snapshot der Serien zum
  Erstellungszeitpunkt (EmbeddedSerie[])"*. A Passe **copies** its serien (with
  steps) at creation. It does **not** reference them by `serieId`. So even after
  Serie resolution is fixed, a Passe keeps an independent frozen copy; editing
  the Serie or renaming a position never reaches the Passe.
- **`CompetitionSerieResult`** (backend) — stores `serieId` + a `results` blob
  containing only `playerId / totalPoints / maxPoints`. **No letters.** Completed
  scorecards obtain letters by joining live to the *current* Serie def at read
  time (`useCompletedResults.js:70-100`, the Task C `serieDefs` map). So finished
  results are not a snapshot — they silently track live edits, and the letters
  they show are themselves frozen at Serie-creation time. This is "frozen at the
  wrong moment".

### Secondary smell

`passeStore.updateSerie` (`passeStore.js:272-294`) edits a Serie via
delete+create, which **changes the Serie ID**, then reloads Passen "to repair
references". A rename mutates identity. This exists only because the backend
Serie `PUT` accepts name/rangeId but rejects steps.

## Decision (resolution policy)

**Live everywhere except finished results.**

- Serie definitions, Passe definitions, the editor, and active (in-progress)
  sessions resolve position labels **live by ID**. Renaming a position is
  reflected immediately everywhere it is referenced.
- **Completed** competition results keep the label **as it was when the
  competition finished** — an immutable historical record. A later position
  rename must not rewrite a finished scorecard.

## Chosen approach: backend resolves (Approach A)

Resolution happens in the backend, at serialization time, against the live
Position. On completion, the backend captures a true snapshot into the result
record. The frontend stops treating local `letter` / `alias` as truth for live
data and stops joining completed results to live Serie defs.

Rejected alternatives:

- **Frontend resolves (B):** every view showing a Serie would have to ensure
  positions are loaded first (shooter, admin, drawers, kiosk) — more
  orchestration and failure modes; re-derives client-side what the backend
  already knows; and cannot fix the completion snapshot (flaw #3) at all, leaving
  the work half-done.
- **Hybrid (C):** splits one concern across two stacks and maintains two
  resolution mechanisms. Flaw #3 forces backend work regardless, so a pure
  backend approach is cleaner.

## Design — logical work groups

Execution order: **1 → 2 → 3 → 4 → 5**.

### Group 1 — Backend: live-resolve Serie step labels

- On read, populate `StepRecord.letter` / `alias` (and `letter1/2`, `alias1/2`)
  from `posId → Position.label` / device alias, instead of trusting the stored
  copy in `stepsJson`.
- Make the stored copy derived: pre-v1.0, drop `letter` / `alias` from the
  persisted `stepsJson`, keeping only `posId` (+ `type`, `id`). The response DTO
  still carries `letter` / `alias`, now resolved.
- Deleted / missing position → resolve to `null` label. The UI renders a defined
  placeholder (see Group 5), never a broken state.

### Group 2 — Backend: Passe becomes a live reference

- Replace `Passe.serienJson` (embedded snapshot) with an **ordered list of
  `serieId`s**. Join to live Serien on read; those Serien resolve their step
  labels via Group 1.
- Remove the reason for the frontend `updateSerie` hack by **making Serie `PUT`
  accept steps** (in-place edit, stable Serie ID). With a stable ID, a Passe's
  `serieId` references never need "repair".
- A Serie referenced by a Passe that is later deleted → Passe shows the Serie as
  missing (placeholder), consistent with Group 5.

### Group 3 — Backend: snapshot resolved labels into completed results

- When a serie result is finalized (status → PRE_COMPLETE / COMPLETED), persist
  the **resolved** serie definition onto `CompetitionSerieResult` — e.g. a new
  `serieSnapshotJson` holding range name, serie name, and per-step letters as
  resolved at completion time.
- The completed-results read path returns this frozen snapshot. It must **never**
  join to the live Serie def again.

### Group 4 — Frontend: consume resolved labels, drop local snapshot reliance

- Serie / Passe display reads server-provided (now live) `letter` / `alias`.
  Remove any assumption that `letter` is frozen.
- Step builders (`SerieDrawer.vue`, `passeStore.js addStep`) keep sending
  `posId`; they stop depending on the frozen `letter` for display. Sending
  `letter` / `alias` to the backend becomes optional (backend ignores / derives).
- `useCompletedResults` reads the Group 3 snapshot from the result instead of
  joining `serieDefs`.
- Replace `passeStore.updateSerie` delete+create with a real in-place update
  (stable ID, via Group 2's Serie `PUT`); drop the "repair Passen" reload and the
  associated `console.warn`.

### Group 5 — Edge cases, migration, tests

- **Deleted-position placeholder:** define the rendered value (e.g. `"—"` or
  `"gelöscht"`) and German aria-label, applied consistently in Serie/Passe/active
  views.
- **Migration:** pre-v1.0 — existing snapshot-based Passen (`serienJson`) cannot
  be reliably converted to `serieId` references (the originating serien may have
  diverged or been deleted). Reset / drop existing Passen rather than migrate.
- **Tests (cross-stack):**
  - Rename a position → live Serie, Passe, and active session reflect the new
    label (propagation).
  - Rename a position after a competition finished → the completed scorecard is
    unchanged (isolation).
  - Deleted position → placeholder, no crash.
  - Serie `PUT` with steps keeps the same Serie ID; a referencing Passe still
    resolves.

## Out of scope

- STOMP / real-time push of label changes to already-open clients (still
  unwired; a reload picks up the new label).
- Reworking the `results` scoring blob format beyond adding the label snapshot.
- Multi-range positions or position reordering semantics.

## Open question to confirm during planning

- Exact shape and column for the Group 3 completion snapshot
  (`serieSnapshotJson` vs. extending the existing `results` blob). Leaning toward
  a separate column so the scoring blob stays focused.
