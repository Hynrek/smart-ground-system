# Completed-Competition Score View — Design

**Date:** 2026-06-17
**Status:** Approved (pending spec review)
**Component area:** Competition results (admin + shooter)

## Problem

When a competition reaches `COMPLETED`, the final scores must be visible — reliably,
including after a page reload.

The current `CompletedResultsPanel.vue` (rendered in `WettkampfDetailView`'s COMPLETED
branch) reads from `competitionEventStore.completedCompetitionInstances` — an **in-memory**
array that is only populated when a competition finishes *during the same browser session*.
On reload, a COMPLETED competition is never rebuilt into that array (the store only builds /
hydrates `ACTIVE` and `PRE_COMPLETE` events), so the panel renders empty. It also uses
hardcoded hex colors instead of the design-token system, and presents no ranking.

## Goals

- An **overall combined Rangliste** (all shooters across all Rotten ranked together) that is
  the headline of the view.
- **Expandable per-shooter detail** showing per-Passe totals.
- Works after reload — sourced from the backend, not in-memory runtime state.
- Two audiences: an **admin/organizer** version and a separate **shooter (kiosk)** version.

## Non-Goals (YAGNI)

- Full per-step scorecard (every Serie/step with hit/miss letters). Documented as a **stretch
  goal**; the `programResults` JSON already carries the data, so it can be added later without
  reworking this design.
- Per-Rotte separate rankings. The ranking is one combined standing (Rotte shown as metadata).
- Real-time / live updates. This view is for finished competitions only.

## Data Availability (backend, verified)

For a `COMPLETED` session, the reliably server-backed sources (both survive reload) are:

1. **`GET /sessions/{id}/leaderboard`** → `SessionLeaderboardResponse`:
   - `playerScores[]`: `{ playerId, displayName, totalScore, maxScore, rank, tied, tieResolvedByStechen }`
     — already sorted and ranked, with tie resolution computed server-side (`TieResolver`).
   - `groupScores[]`: per-Rotte `{ groupId, groupName, totalScore, maxScore, rank }`.
2. **`GET /sessions/{id}`** → `SessionResponse` (already loaded by `WettkampfDetailView`):
   - `groups[]` (Rotte → members) — used to join `playerId → rotteName`.
   - `playerResults[]`: `{ playerId, displayName, totalScore, maxScore, completionPct, programResults }`
     where `programResults` is a **JSON string** (`ProgramResult[]` → `segmentResults`).

**Implication:** standings are rock-solid from the leaderboard endpoint. Per-Passe detail is
derivable from the `programResults` blob, best-effort, and must degrade gracefully to a bare
total when the blob is absent or unparseable. This is the soft boundary that keeps the detail
view a "stretch" rather than a hard dependency.

## Architecture

Shared data layer + composable, two thin presentational components. The volatile part
(data fetch + ranking) lives behind one tested boundary; only the visuals diverge per audience.

```
wettkampfApi.getLeaderboard / getSession   (services)
        │
        ▼
competitionEventStore.loadCompletedResults  (Pinia action → server-backed cache)
        │
        ▼
useCompletedResults(sessionId)              (composable: standings, detail, load/loading/error)
        │
        ├────────────────────────┐
        ▼                         ▼
CompletedResultsPanel.vue     CompletionLeaderboard.vue
(admin, MainLayout)           (shooter, ShooterLayout/kiosk)
```

### 1. Store — `competitionEventStore.js`

- **State:** `completedResultsBySession = ref({})` — keyed by sessionId, server-backed cache.
- **Action:** `loadCompletedResults(sessionId)`
  - Calls `wettkampfApi.getLeaderboard(sessionId)`.
  - Joins `playerId → rotteName` from the event's `groups` membership (loads the session if not
    already present).
  - Stores a normalized object:
    ```
    {
      standings: [{ rank, playerId, displayName, rotteName, totalScore, maxScore, tied, tieResolvedByStechen }],
      playerResults: [...],   // raw playerResults from getSession, for detail parsing
      completedAt
    }
    ```
  - try/catch per store convention; surfaces `error`.

### 2. Composable — `src/composables/useCompletedResults.js`

- Accepts `sessionId` (ref or string).
- Returns: `standings` (computed), `loading`, `error`, `load()`, `getPlayerDetail(playerId)`.
- `getPlayerDetail(playerId)`: parses that player's `programResults` JSON into per-Passe totals;
  on missing/invalid JSON, returns `{ total, max }` only (graceful fallback). Wrapped in
  try/catch — a bad blob for one player never breaks the standings.

### 3. Admin view — rewrite `CompletedResultsPanel.vue`

- Rendered in `WettkampfDetailView` COMPLETED branch (unchanged wiring).
- On mount, calls the composable `load()`.
- **Header:** "Rangliste", completion date, and an **Export** button.
- **Standings table** (single combined list), styled entirely with `var(--sg-*)` tokens:
  - Row: rank (medal accent for 1–3) · name · Rotte chip · `total/max`.
  - **"Stechen"** badge when `tieResolvedByStechen` is true.
  - Click a row → expands per-Passe totals (from `getPlayerDetail`).
- **Export:** new service helper `exportLeaderboard(sessionId, format)` hitting
  `/sessions/{id}/leaderboard/export?format=csv`, triggering a file download. Default CSV.

### 4. Shooter view — new `CompletionLeaderboard.vue` (`shooter-remote/`)

- For `ShooterLayout`: large type, ≥48px touch targets, dark kiosk palette consistent with
  `LiveScoreboard.vue`.
- Same `standings` data via the composable.
- **The logged-in shooter's row is highlighted/pinned** so they find their placement instantly;
  top 3 visually emphasized.
- Tap a row → per-Passe detail (same `getPlayerDetail`).
- **Entry point:** a **"Zur Rangliste"** link surfaced from `CompetitionLiveView` when the
  competition reaches COMPLETED, routing into this results screen. No ShooterHomeView list.

### 5. Error / empty / loading (both views)

- Skeleton/spinner while loading.
- Error state with a **retry** button (re-invokes `load()`).
- Empty state ("Noch keine Ergebnisse") for a completed competition with no recorded results.

## Testing

- **Store** (`competitionEventStore.test.js`): `loadCompletedResults` mocks
  `getLeaderboard`/`getSession`, asserts normalized standings (rank order, Rotte join) and that
  the cache is reusable on re-read; error path sets `error`.
- **Composable** (`useCompletedResults.test.js`): ranking join player→Rotte; `getPlayerDetail`
  parse path **and** fallback path (missing/invalid `programResults`).
- **Admin component**: renders ranks in order; Stechen badge appears for resolved ties; row
  expand toggles per-Passe detail; export button calls the service helper.
- **Shooter component**: highlights the current shooter's row; renders top-3 emphasis; expand
  toggles detail.

## Conventions

- `<script setup>` + Composition API; `@/` imports.
- Data flows store → composable → component; no direct API calls in components.
- German display labels (Rangliste, Rotte, Passe, Stechen, Ergebnisse); English identifiers.
- All colors from `var(--sg-*)` design tokens (admin) / kiosk palette consistent with existing
  shooter components.
- Remove the old hardcoded-hex / in-memory logic in `CompletedResultsPanel.vue` rather than
  leaving it alongside the rewrite.
