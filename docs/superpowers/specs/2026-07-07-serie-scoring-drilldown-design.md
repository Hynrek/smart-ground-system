# Serie Scoring — Structural Kind + Level-Scoped Drill-Down Views — Design

**Date:** 2026-07-07
**Status:** Draft (pending user review)
**Scope:** smart-ground-backend + smart-ground-ui
**Builds on:** [2026-07-06-user-score-tracking-design.md](2026-07-06-user-score-tracking-design.md) (the `UserSerieScore` projection).

## Goal

Make the shooter's score history reflect the real-world nesting of what was shot, and let the shooter drill down to the individual clay-target he missed.

A **Wettkampf** is made of **Passen**; a **Passe** is made of **Serien**; a **Serie** is made of **steps** (clays). Each score sub-view must show only its own level and let the user expand into the level below, ending at the per-clay breakdown. A Serie shot inside a Passe or Wettkampf must **not** appear in the flat "Serien" list — it lives under its parent, reachable by drilling in.

## Problem with today's behavior

The "Ergebnisse" tab in `ShooterKarriereView.vue` has three sub-tabs (Serien / Passen / Wettkämpfe), but:

- **Serien** shows `scoreStore.scores` — a flat list of *every* Serie row, regardless of whether it belonged to a Passe or a Wettkampf. There is no way to tell a truly standalone Serie apart from a Serie that was one block of a Passe.
- **Passen / Wettkämpfe** show grouped totals but offer **no drill-down** — you cannot open a Passe to see its Serien, or a Serie to see which clays were hit/missed.
- `UserSerieScore.stepStatesJson` (the per-clay breakdown) is captured on every persisted row but **never rendered**.
- **Standalone Serie play is never persisted.** "Als Solo Starten" (`playSerieSolo`) and "Als Gruppe Starten" (`playSerieGroup`) both run in a purely client-side `playScoreMode` (`playSessionStore.playPasseWithScore` / `startGroupPlay` with no `instanceId`) — no `PlayInstance` is created, so no `UserSerieScore` row is written. Only range-kiosk Passe play and Wettkampf play persist today.

## Decisions (from brainstorming)

1. **Two contexts only.** "Everything outside a Wettkampf is Training." The `Training` grouping entity was already removed. The existing `UserSerieScore.context` (TRAINING | COMPETITION) is this switch — no new layer.
2. **`kind` is structural, not behavioral.** A Serie is `kind = SERIE` whether run **solo or as a group**. Solo vs. group only decides *which users* receive score rows — never the category. `UserScoreService` already writes one row per participating real user and skips users without a `userId`; that stays.
3. **Three structural kinds:** `SERIE` (a standalone Serie), `PASSE` (a training Passe), `COMPETITION` (a Wettkampf Serie). Derived at write time.
4. **A standalone Serie is a `PlayInstance` of `type = "serie"`.** The backend's `startSerieInstance` already builds a one-block instance; it just needs to stamp the type. This makes a standalone Serie distinguishable from a one-Serie *Passe* (which stays `PASSE`).
5. **Solo play gets a pre-run modal** — user-centered baseline with QR impersonation for fixed kiosk devices (see §4).
6. **Stechen must not leak into training history** (see §2, edge case).

## 1. Persistence — `kind` on `UserSerieScore`

Add one column to the existing `user_serie_scores` table:

| Column | Type | Notes |
|---|---|---|
| `kind` | varchar(12), not null | `SERIE` \| `PASSE` \| `COMPETITION` |

Derivation at write time:

| Write path | Instance / source | `context` | `kind` |
|---|---|---|---|
| Standalone Serie run | `PlayInstance.type = "serie"` | TRAINING | `SERIE` |
| Training Passe run | `PlayInstance.type = "passe"` | TRAINING | `PASSE` |
| Wettkampf Serie | `CompetitionSerieResult` | COMPETITION | `COMPETITION` |

`context` is now derivable from `kind` (`COMPETITION` ↔ COMPETITION, else TRAINING) but is kept as a stored column — existing queries, indexes, and the summary endpoint already use it, and it stays cheap to filter on. No behavioral duplication: `kind` is the fine-grained discriminator, `context` the coarse one.

New index `(user_id, kind, completed_at DESC)` for the level-scoped list queries.

Pre-v1.0: no migration — dev DBs reseed (`ddl-auto`).

## 2. Backend write paths

### `PlayInstance.type = "serie"`

- `PlayInstanceService.startSerieInstance` currently delegates to `buildAndSaveInstance`, which hardcodes `instance.setType("passe")`. Add a `type` parameter to `buildAndSaveInstance` so `startPasseInstance` passes `"passe"` and `startSerieInstance` passes `"serie"`. `PlayInstance.type` column already exists (`length = 10`), no schema change.
- New user-facing endpoint **`POST /api/play-instances/serie`** (`StartSerieInstanceRequest { serieId, players }`) → resolves the Serie snapshot, calls `startSerieInstance` with `type = "serie"`. Symmetric to the existing `POST /api/play-instances/passe`.

### `kind` in `UserScoreService`

- `recordTrainingInstance(PlayInstance)`: set `kind = "serie".equals(instance.getType()) ? SERIE : PASSE`.
- `recordCompetitionSerie(...)`: set `kind = COMPETITION`.

### Stechen edge case (must-fix)

`TiebreakerService` runs a Stechen as a `startSerieInstance` PlayInstance, completed through the normal `completeBlock` path — which now calls `recordTrainingInstance`. A Stechen Serie is part of a **Wettkampf tiebreaker**, not a training Serie, and must never appear in the shooter's training history.

**Resolution:** stamp Stechen instances with a distinct type `"stechen"` (or a boolean flag on the instance). `recordTrainingInstance` skips any instance whose type is not `"serie"`/`"passe"`. This is a targeted correctness fix; the Stechen scoring path (`CompetitionTiebreaker.resultsJson`, reconcile-on-read) is unchanged. Verify during implementation whether Stechen players even carry a `userId` today — if they never do, no row is written regardless, but the type guard makes the exclusion explicit and future-proof.

## 3. Backend read / aggregation

Rework the three read endpoints from the score-tracking spec so each returns exactly one structural level:

| Endpoint | Returns |
|---|---|
| `GET /api/users/me/scores?kind=SERIE` | Flat list of standalone `SERIE` rows, newest first. Each row carries `stepStates` for the clay drill-down. |
| `GET /api/users/me/passen` | Training `PASSE` groups (by `playInstanceId`): total, serieCount, label, completedAt, **plus the child Serie rows** (each with `stepStates`) so the UI can drill Passe → Serie → clays without a second round-trip. |
| `GET /api/users/me/wettkaempfe` | Wettkampf groups (by `sessionId`) → nested Passen (by `passeIndex`) → child Serie rows (with `stepStates`). |

Design note: the grouped endpoints embed their children (a Passe already contains few Serien; a Wettkampf a bounded number of Passen). This favors a single fetch per tab over lazy per-node loading — simpler store, and the payloads stay small. If a Wettkampf ever grows large, lazy child loading can be added later without changing the tab contract.

`kind` also becomes a filter on the existing leaderboard endpoint (`GET /api/scores/leaderboard?kind=`), so a leaderboard can rank standalone Serien, Passen, or Wettkampf Serien distinctly.

The old flat "all rows" summary shape (`passen[]` / `wettkaempfe[]` on `/scores/summary`) is superseded by these level-scoped endpoints and removed (remove-unused-code rule). The small stats header keeps a trimmed `GET /users/me/scores/summary` (per-context average % / best), which is level-agnostic.

## 4. Frontend — standalone Serie persistence

### Solo run modal ("Als Solo Starten")

Replace the immediate `playSerieSolo` launch with a small pre-run modal:

- **Checkbox "Punkte aufnehmen?"** — off by default. When off, the run stays ephemeral practice (today's behavior: `playScoreMode`, nothing persisted).
- **Current shooter shown** — the logged-in user (`authStore`) as the default identity.
- **Button "Schütze ändern"** — opens `QrScanModal`; a scanned QR token resolves via `userApi` (`GET /api/users/by-qr/{token}`) to a user. Selecting a different shooter **auto-checks "Punkte aufnehmen?"** (changing identity implies intent to record).
- On confirm **with recording on**: call `POST /api/play-instances/serie` with the resolved shooter as the single player (carrying `userId`), then run the block lifecycle so `completeBlock` → `recordTrainingInstance` writes a `SERIE` row for that user.
- On confirm **with recording off**: current ephemeral path unchanged.

Rationale: baseline is user-centered (personal phone = the logged-in user); the QR "impersonation" path supports a fixed range device where whoever is physically shooting identifies themselves.

### Group run ("Als Gruppe Starten")

Route through the same persisted instance (`type = "serie"`) instead of the client-side `startGroupPlay` legacy session. Players come from the existing group/QR setup; each participant with a `userId` gets a `SERIE` row, guests are skipped (existing `UserScoreService` logic). Recording is implicit for group runs (a group is set up deliberately) — no "Punkte aufnehmen?" toggle.

### Store / service

- `playInstanceApi.js`: add `startSerieInstance(serieId, players)` → `POST /play-instances/serie`. Remove the dead `startTrainingInstance` / `/play-instances/training` wrapper (endpoint no longer exists).
- `scoreStore.js`: add actions for the two new grouped endpoints; keep `loadScores({ kind: 'SERIE' })` for the Serien tab.

## 5. Frontend — level-scoped drill-down views

Rework the "Ergebnisse" tab of `ShooterKarriereView.vue`. Same three sub-tabs, each now an **accordion** ending at the clay breakdown:

- **Serien** → `kind=SERIE` rows only. Expand a row → per-clay `StepScorecard` rendered from `stepStates` (reuse `components/competition/StepScorecard.vue` + `constants/stepModes.js`; `isMultiResultStep` split rendering already handles two-clay steps).
- **Passen** → `PASSE` groups. Expand a Passe → its Serien; expand a Serie → clays.
- **Wettkämpfe** → sessions. Expand → Passen (`passeIndex`) → Serien → clays.

The per-clay rendering is the *same* `StepScorecard` used in the competition results panel (`CompletedResultsPanel.vue`), so hit/miss/no-bird chips and colors are identical across the app (shared visual language rule).

Design tokens only (`--sg-*`); touch targets ≥48px (shooter layout); German labels; accordion rows keyboard-accessible (`aria-expanded`, focus-visible).

## 6. Error handling

- Score writes stay in the completion transaction (unchanged): a write failure rolls back completion.
- Solo run with recording on but an **unresolvable** QR token → surface an inline error in the modal; do not start the run.
- Solo run where the resolved shooter has no account `userId` (shouldn't happen via QR, but defensively) → run proceeds, no row written (same as a guest).
- Empty states per tab: no standalone Serien / no Passen / no Wettkämpfe → friendly empty state.

## 7. Testing

**Backend**
- `UserScoreService`: `kind = SERIE` for a `type="serie"` instance, `PASSE` for `type="passe"`, `COMPETITION` for competition; Stechen (`type="stechen"`) instance writes **no** training row.
- Repository: level-scoped queries (`findByUserIdAndKind...`), Passe grouping with embedded children, Wettkampf → Passe → Serie nesting.
- Controller: `POST /play-instances/serie` creates a `type="serie"` instance; the three read endpoints return the right shapes; auth required.

**Frontend**
- Solo modal: recording-off → no API call; "Schütze ändern" auto-checks recording; confirm-with-recording → `startSerieInstance` called with the resolved `userId`.
- `scoreStore`: new actions (state, error paths) with mocked `scoreApi`.
- `ShooterKarriereView`: each tab renders only its level; expanding drills to `StepScorecard`; a Serie shot inside a Passe/Wettkampf does **not** appear in the Serien tab.
- English tests, independent, ≥80% coverage on new code.

## Out of scope

- Backfilling historical ephemeral solo runs (they were never persisted — nothing to backfill).
- Lazy per-node child loading (embedded children are sufficient at current data sizes).
- Any change to the Wettkampf or Passe *play* flows beyond routing standalone Serie runs through a persisted instance.
