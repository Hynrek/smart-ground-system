# User Score Tracking — Design

**Date:** 2026-07-06
**Status:** Approved
**Scope:** smart-ground-backend + smart-ground-ui

## Goal

Give user scores a first-class, queryable home at three granularities — individual Serie, Passe (n Serien), Wettkampf (n Passen) — to power personal history/statistics and leaderboards.

Today, training results live only inside `PlayInstance.state_json` JSON blobs (participant lookup via string `LIKE` over `players_json`), and Wettkampf results live in `CompetitionSerieResult` rows with per-player JSON. There is no efficient way to query a user's score history or compare users.

## Decisions (from brainstorming)

- **Purpose:** personal history & stats + leaderboards/comparison.
- **Identity:** registered users only — only players with a `userId` (QR check-in or account) get score rows. Anonymous placeholders and Guests stay in the play JSON but produce no score records.
- **Unification:** one store for training and competition results, distinguished by a `context` field.
- **Detail level:** each score row carries totals plus its own JSON copy of the step breakdown (self-contained even if source instances are purged).
- **Approach:** projection table at Serie granularity; Passe and Wettkampf scores are aggregation queries, never stored rows (Approach A). Corrections can therefore never leave aggregate levels inconsistent.

## 1. Persistence

New entity **`UserSerieScore`** (table `user_serie_scores`) — one row per user × completed Serie:

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | generated |
| `user_id` | UUID, not null | indexed |
| `context` | varchar | `TRAINING` \| `COMPETITION` |
| `total_points` | int | |
| `max_points` | int | |
| `step_states_json` | TEXT | self-contained copy of the player's step states for this Serie |
| `serie_id` | UUID | Serie definition |
| `serie_alias` | varchar | denormalized display name |
| `source_id` | UUID, not null | training: `blockId`; competition: `CompetitionSerieResult` id |
| `play_instance_id` | UUID, nullable | groups training rows into a Passe |
| `session_id` | UUID, nullable | Wettkampf session |
| `group_id` | UUID, nullable | Rotte |
| `passe_index` | int, nullable | position within the Wettkampf |
| `range_id` | UUID, nullable | where it was shot |
| `range_name` | varchar, nullable | denormalized |
| `completed_at` | timestamp, not null | |

Constraints and indexes:

- **Unique (`source_id`, `user_id`)** — idempotent writes; competition corrections are an upsert on this key.
- Index (`user_id`, `completed_at`) — history queries.
- Index (`serie_id`, `total_points`) — leaderboards.

Aggregation (no stored aggregate rows):

- **Passe score** = `SUM(total_points) GROUP BY play_instance_id` (training) or `GROUP BY session_id, passe_index` (competition).
- **Wettkampf score** = `GROUP BY session_id`.

`CareerStats` stays untouched for now; it becomes retirable later since its numbers are derivable from this table.

Pre-v1.0: no backfill migration — no production data exists; dev databases reseed via `DataInitializer` / replay.

## 2. Write paths — new `UserScoreService`

Called in the same transaction as the existing completion logic:

- **Training** (`PlayInstanceService.completeBlock`): when the last block completes and the instance flips to `completed`, write one row per block × player-with-`userId`. Writing only on full instance completion keeps cancel handling trivial — a cancelled Passe leaves no score rows. Accepted trade-off: Serien shot inside an aborted Passe are not tracked.
- **Wettkampf** (`CompetitionProgressService`):
  - `completeSerieForGroup`: insert rows for every result entry whose `SessionPlayer` has a linked `User`.
  - `correctSerieForGroup`: upsert by (`source_id`, `user_id`); delete rows for players no longer present in the corrected results.
- Players without a `userId` are skipped silently.
- `step_states_json` is extracted per player from the block result (training) / serie result payload (competition).

## 3. API (contract-first: edit `openapi.yaml`, generate, implement)

| Endpoint | Purpose |
|---|---|
| `GET /users/me/scores` | Paged Serie score rows. Filters: `context`, `from`, `to`, `serieId`. |
| `GET /users/me/scores/summary` | Aggregate stats: per-context counts, average hit %, personal bests, grouped Passe/Wettkampf totals. |
| `GET /scores/leaderboard` | Top N by best or average Serie score. Params: `serieId`, `rangeId`, `context`, `period`. Any authenticated user; no new permission. |

Retired and replaced (delete per "remove unused code eagerly"):

- `GET /users/me/play-results` (`listMyPlayResults` — the JSON-`LIKE` scan) plus `findCompletedByParticipantUserId` and the UI's `fetchMyPlayResults`.

`/play-results` (owner view of completed instances) stays — it serves the instance/replay view, not score history.

## 4. UI

- **`services/scoreApi.js`**: wrappers for the three endpoints.
- **`stores/scoreStore.js`**: Composition-API Pinia store — score list (paged), summary, leaderboard; loading/error state; actions per endpoint. No mapper needed (shapes map 1:1).
- **`ShooterProfilView`**: replace the current play-results list with history from `scoreStore`; Serie / Passe / Wettkampf grouping tabs; small stats header (avg %, personal best). Step breakdown rendered from `step_states_json` via `constants/stepModes.js` (StepScorecard-style chips, `isMultiResultStep` split rendering).
- **New Bestenliste view** (shooter layout, route `/bestenliste`, `meta.permission` = authenticated-level like other shooter views): leaderboard filterable by Serie and range. German display labels; nav entry where shooter nav lives.
- Design tokens only (`--sg-*`); touch targets ≥48px (shooter layout).

## 5. Error handling

- Score writes share the completion transaction: if the write fails, completion rolls back (consistency over availability — completion is retryable).
- Empty states in UI: no scores yet → friendly empty state; leaderboard with no entries for filter → empty state.
- Unique-constraint conflict on re-delivered completion → upsert semantics make it a no-op.

## 6. Testing

- **Backend:** `UserScoreService` unit tests — rows written on instance completion, userId-less players skipped, idempotency (duplicate completion), correction upsert + removal; repository tests for Passe/Wettkampf aggregation and leaderboard queries; controller tests for the three endpoints (auth required).
- **UI:** store tests with mocked `scoreApi` (state, actions, error paths); component tests for ShooterProfilView grouping tabs and Bestenliste rendering/filters. English, independent, ≥80% coverage on new code.
