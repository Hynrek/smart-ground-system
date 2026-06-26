# Stechen scored by a real range run — Design

**Date:** 2026-06-26
**Scope:** Full stack — `smart-ground-backend` (Spring Boot 4) + `smart-ground-ui` (Vue 3)
**Status:** Draft — awaiting review
**Builds on:** `smart-ground-ui/docs/superpowers/specs/2026-06-19-stechen-as-serie-design.md` (Stechen is always a single Serie)

---

## 1. Problem & Goal

A Stechen (tiebreaker shoot-off) is already started as a real one-block live `PlayInstance`
built from the chosen Serie — but that run is **orphaned**:

- Nothing surfaces it on a range to actually be shot.
- Nothing connects its completion back to the `CompetitionTiebreaker`.

The operator bridges the gap by hand: `StechenPanel.vue` shows a "run it on the range, then
type the score here" form (`submitStechenResults`).

**Goal:** The Stechen Serie is shot as a live run on its range (via the existing live-play
flow, surfaced on the range kiosk like competition Serien), and the run's score flows into the
tiebreaker **automatically**, resolving the tied block. Manual score entry is **removed** — the
live run is the only scoring path.

### Decisions (locked during brainstorming)

| Question | Decision |
|---|---|
| How is the Stechen shot? | **Existing live-play flow** — the run rides the same play page / `completeBlock` path as any Serie. |
| On completion? | **Auto-resolve immediately** — derived scores written to the tiebreaker, block re-ordered, no admin confirmation step. |
| Manual entry kept? | **Removed** — the live run is the only scoring path. |
| Where does it appear? | **On the range kiosk** (shooter flyout), scoped to the Serie's range, like competition Serien — visible to whoever operates that range, regardless of who started it. |
| Surfacing transport | **Enriched ties endpoint** (no new endpoint, no owner-scope change, no extra play-instance fetch by the client). |
| Resolution transport | **Reconciliation-on-read** in `TiebreakerService` (no STOMP — `CLAUDE.md` defers WebSocket wiring; no coupling added to the generic play engine). |

---

## 2. Invariant preserved

**Stechen results never touch `PlayerResult`.** `PlayInstanceService.completeBlock` writes only
to the instance's `stateJson`; the reconciliation step writes only to
`CompetitionTiebreaker.resultsJson`. Main competition points stay untouched.

`TieResolver`, leaderboard tie-resolution, `tieGroupId`/round-number/earliest-decisive-round
logic, repeat rounds, and the warn-don't-block PRE_COMPLETE finish guard are **unchanged**.

---

## 3. Backend changes (`smart-ground-backend`)

### 3.1 `TiebreakerService` — one instance fetch per ACTIVE round

For each round with status `ACTIVE` and a non-null `playInstanceId`, fetch the linked instance
once via `playInstanceService.getPlayInstance(...)` (it is **not** owner-scoped and already
resolves step letters). Then:

- **Instance `completed`** → reconcile: derive per-player `{playerId, totalPoints, maxPoints}`
  from the instance's single block result, write to `tb.resultsJson`, set status `COMPLETED` +
  `completedAt`, save. `playerId` in the block result equals the `SessionPlayer` UUID (the run
  was started with `PlayerRef.id = SessionPlayer.id.toString()`), so it maps straight into
  `resultsJson`. The existing `TieResolver` then re-orders the block on the same `listTies` call.
- **Instance still `active`** → attach the run's block info (`blockId`, `run`) to the response
  so the kiosk can surface and play it.

This reconciliation runs inside `listTies` and within `toResponse` for ACTIVE rounds (the single
fetch serves both surfacing and completion). It is a deliberate "catch-up" side effect on read.

> **Verify in the plan:** confirm `GET /play-instances/{id}` and the underlying
> `PlayInstanceService.getPlayInstance` carry no owner/permission restriction that would block a
> kiosk operator or the tie-read path. (`completeBlock` already has no owner check; only
> `stopPlayInstance` does.)

### 3.2 Remove manual entry

- **Remove** endpoint `POST /api/sessions/{sessionId}/tiebreakers/{tiebreakerId}/results`
  (line ~1434 in `openapi.yaml`) and the `SubmitTiebreakerResultsRequest` schema (~3796).
- **Remove** `TiebreakerService.submitResults` and the `submitTiebreakerResults` controller
  method in `SessionController`.

### 3.3 Enrich `TiebreakerResponse` (`openapi.yaml` source of truth — edit, regenerate, implement)

Add two fields, populated only for ACTIVE rounds:

```yaml
TiebreakerResponse:
  properties:
    # …existing…
    blockId:  { type: string, format: uuid, nullable: true }
    run:      { $ref: '#/components/schemas/EmbeddedSerie' }   # nullable; serieId, alias, rangeId, rangeName, steps[]
```

`EmbeddedSerie` (existing) carries exactly what the kiosk needs to surface and play the run.
`TiebreakerPlayerScore` stays (still used by `results` and by the reconciliation write shape).

### 3.4 Workflow & conventions

- Edit `openapi.yaml` first → `./mvnw generate-sources` → implement.
- `@NullMarked`; German inline comments for domain logic.
- Schema unchanged (no new columns) — `CompetitionTiebreaker` already has `resultsJson`,
  `playInstanceId`, `programSnapshot`, `status`, `completedAt`.

---

## 4. Frontend changes (`smart-ground-ui`)

### 4.1 Surface on the range kiosk

- `competitionEventStore`: for PRE_COMPLETE sessions, load ties and expose
  `getActiveStechenForRange(rangeId)` — filters rounds where `status === 'ACTIVE'` and
  `run.rangeId === rangeId`, returning a playable block (`{ instanceId: playInstanceId, blockId,
  serieId, serieAlias, steps, rangeId, rangeName, players, sessionId }`). Wire tie-loading for
  active/PRE_COMPLETE sessions into the kiosk's existing load path (alongside `loadEvents`).
- `ShooterFlyoutPanel.vue`: add a **"Stechen"** section listing
  `getActiveStechenForRange(currentRangeId)`. Clicking stages `pendingPasseInfo` with
  `instanceType: 'stechen'`, `instanceId: playInstanceId`, `blockId`, the tied players, and the
  run's steps — then routes to `/remote/{rangeId}/play`. The play page runs it like any Serie.

### 4.2 Completion → auto-resolve

- `playSessionStore.commitResults`: add an `instanceType === 'stechen'` branch →
  `competitionEventStore.completeStechenRun({ instanceId, blockId, results, sessionId })`, which
  calls `playInstanceApi.completeBlock(instanceId, blockId, results)` then `loadTies(sessionId)`.

### 4.3 `StechenPanel.vue` — remove entry form, add live status + poll

- **Remove** the result-entry `<form>`, `drafts`, `syncDrafts`, `saving`, `saveResults`.
- An **ACTIVE** round now renders a live status ("läuft am Bereich…") with the participants,
  replacing the manual inputs. A **COMPLETED** round renders read-only results + "Aufgelöst"
  (already present).
- **Light-poll** `loadTies(sessionId)` on an interval (e.g. ~4s) while any round is `ACTIVE`,
  cleared when none are active and on unmount — so the admin's screen reflects the auto-resolution
  without a manual refresh. (No STOMP.)

### 4.4 API + store cleanup

- `tiebreakerApi.js`: remove `submitTiebreakerResults`.
- `competitionEventStore.js`: remove `submitStechenResults`; add `completeStechenRun` and
  `getActiveStechenForRange` (+ the active-session tie loading they depend on).

---

## 5. Testing

**Backend** (≥80% for new/changed code):
- `TiebreakerServiceTest`:
  - **Remove** the `submitResults` invariant test (method is gone).
  - **Add:** an ACTIVE round whose linked instance is `completed` → `listTies` derives per-player
    scores, sets the tiebreaker `COMPLETED`, and the block resolves; assert `PlayerResult` is
    never saved.
  - **Add:** an ACTIVE round whose instance is still `active` → response carries `blockId` + `run`
    (rangeId/steps) and no `results`.
- Full suite green: `./mvnw clean test`.

**Frontend:**
- `StechenPanel.test.js`: no entry form; ACTIVE shows live status; COMPLETED shows read-only
  results; poll starts/stops with active rounds.
- `competitionEventStore.stechen.test.js`: `getActiveStechenForRange` filters by range/active;
  `completeStechenRun` calls `completeBlock` then `loadTies`; `submitStechenResults` gone.
- `tiebreakerApi.test.js`: `submitTiebreakerResults` removed.
- Flyout: a "Stechen" block on the current range is listed and playable.

---

## 6. Conventions to honour

- `openapi.yaml` is the source of truth — edit + regenerate before implementing.
- No Liquibase changeset (no schema change).
- Remove unused code eagerly (results endpoint, `submitResults`, `submitStechenResults`,
  `submitTiebreakerResults`, entry form).
- Record decisions back into both `CLAUDE.md`s: Stechen is auto-scored via its live run; the
  results endpoint is removed; `TiebreakerResponse` is enriched with `blockId` + `run` for ACTIVE
  rounds; resolution is reconciliation-on-read.
- Commits: `[backend] …` / `[ui] …`.
