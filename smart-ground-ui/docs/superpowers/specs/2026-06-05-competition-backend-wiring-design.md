# Competition Backend Wiring — Design Spec

**Date:** 2026-06-05  
**Status:** Approved

---

## Problem

The Wettkampf (competition) system is entirely localStorage-based on the frontend. Competition events are created, tracked, and completed in the browser with no backend persistence. As a result:

- Progress is lost on page refresh.
- Range operators cannot push Serie completions — only the session creator can see updates.
- No score history accumulates for users across competitions.
- `PlayerResult` and `CareerStats` tables exist on the backend but are never populated.

---

## Goal

Replace the localStorage-backed competition store with a fully API-backed implementation. Range operators push Serie completion signals to the backend; all clients derive live progress from backend state. After a competition completes, individual player results and cross-competition career stats are persisted.

---

## Data Model

### `SessionStatus` enum — extended lifecycle

```
SETUP → OPEN → ACTIVE → PRE_COMPLETE → COMPLETED
                   └──────────────────→ ABANDONED
```

| Status | Frontend label | Meaning |
|---|---|---|
| `SETUP` | Planung | Admin building the event: Rotten, players |
| `OPEN` | Offen | Event published, ready to start, no shooting yet |
| `ACTIVE` | Aktiv | Shooting in progress |
| `PRE_COMPLETE` | Auswertung | All Serien done for all Rotten — audit/review stage |
| `COMPLETED` | Abgeschlossen | Results confirmed and final |
| `ABANDONED` | Abgebrochen | Cancelled at any point |

Valid transitions:
- `SETUP → OPEN`, `OPEN → ACTIVE`, `ACTIVE → PRE_COMPLETE`, `PRE_COMPLETE → COMPLETED`
- Any non-terminal status → `ABANDONED`
- `COMPLETED` and `ABANDONED` are terminal (no further transitions)

### `SessionPlayer` — add `paid` field

```java
@Column(nullable = false)
private boolean paid = false;
```

### New entity: `CompetitionSerieResult`

```
competition_serie_results
  id               UUID PK
  session_id       FK → live_sessions (NOT NULL)
  group_id         FK → shooter_groups (NOT NULL)
  passe_index      INT           -- 0-based index into the session's ordered Passen list
  serie_id         UUID          -- which Serie within that Passe
  play_instance_id UUID nullable -- the Play that produced the results
  results          TEXT (JSON)   -- hit/miss per step, per player
  completed_at     TIMESTAMP (NOT NULL)
```

Unique constraint: `(session_id, group_id, passe_index, serie_id)` — each Rotte can only complete a given Serie once per Passe.

### Existing tables populated by this flow

- `player_results` — upserted per player incrementally as each Serie completes. One row per player per session; `programResults` JSON accumulates.
- `career_stats` — updated when session transitions to `COMPLETED`.

---

## Backend API

### Session lifecycle

```
POST   /api/sessions
       body: { type: 'competition', name, groups: [{ name, members: [{ displayName, userId?, paid }] }], passeIds: UUID[] }
       → SessionResponse (status: SETUP)

GET    /api/sessions
       query: type=competition, status=setup|open|active|pre_complete|completed|abandoned (optional)
       → Page<SessionResponse>

GET    /api/sessions/{id}
       → SessionResponse with groups + members

PATCH  /api/sessions/{id}/status
       body: { status: 'open'|'active'|'pre_complete'|'completed'|'abandoned' }
       → SessionResponse

DELETE /api/sessions/{id}
       allowed only in SETUP status → 204
```

### Group (Rotte) management — allowed in SETUP and OPEN

```
POST   /api/sessions/{id}/groups
       body: { name, members?: [...] }
       → GroupResponse

PUT    /api/sessions/{id}/groups/{groupId}
       body: { name }
       → GroupResponse

DELETE /api/sessions/{id}/groups/{groupId}
       → 204

POST   /api/sessions/{id}/groups/{groupId}/members
       body: { displayName, userId?, paid }
       → SessionPlayerResponse

DELETE /api/sessions/{id}/groups/{groupId}/members/{memberId}
       → 204

PATCH  /api/sessions/{id}/groups/{groupId}/members/{memberId}
       body: { paid: true|false }
       → SessionPlayerResponse
```

### Competition progress — allowed in ACTIVE

```
POST   /api/sessions/{sessionId}/groups/{groupId}/serien/{serieId}/complete
       body: { passeIndex: int, playInstanceId?: UUID, results: object }
       → CompetitionSerieResultResponse
       Side effects:
         1. Insert CompetitionSerieResult row
         2. Upsert PlayerResult for each member of the group
         3. If all Rotten have completed all Passen: auto-transition session to PRE_COMPLETE

GET    /api/sessions/{sessionId}/progress
       → { groups: [{ groupId, groupName, currentPasseIndex, completedSerien: [{ passeIndex, serieId, completedAt }] }] }
       Derived entirely from competition_serie_results rows.
```

### Unchanged endpoints

```
GET /api/sessions/{id}/leaderboard     -- CompetitionController (already exists)
GET /api/career-stats/{userId}          -- CompetitionController (already exists)
```

---

## Frontend Changes

### New file: `src/services/wettkampfApi.js`

Thin fetch wrapper for all competition endpoints. No business logic — only API calls. Covers session CRUD, status transition, group/member management, Serie completion, and progress fetch.

### `src/stores/competitionEventStore.js` — full rewrite

Drop all localStorage logic (`STORAGE_KEY`, `_save()`, `loadFromStorage()` reading from localStorage). Replace with API-backed state:

| Old action (localStorage) | New action (API) |
|---|---|
| `createEvent(name, passen)` | `POST /api/sessions` |
| `deleteEvent(id)` | `DELETE /api/sessions/{id}` |
| `updateEventName(id, name)` | Not needed (name set at creation) |
| *(new)* `openEvent(id)` | `PATCH /api/sessions/{id}/status → open` (SETUP → OPEN) |
| `startEvent(id)` | `PATCH /api/sessions/{id}/status → active` (OPEN → ACTIVE) |
| `stopEvent(id)` | `PATCH /api/sessions/{id}/status → abandoned` |
| `checkAndCompleteEvent(id)` | Removed — backend auto-transitions to PRE_COMPLETE |
| `addRotte / removeRotte / renameRotte` | `POST/DELETE/PUT /api/sessions/{id}/groups` |
| `addPlayer / removePlayer / togglePlayerPaid` | `POST/DELETE/PATCH .../members` |
| `loadFromStorage()` | `GET /api/sessions?type=competition` |

Computed `planningEvents`, `activeEvents`, `completedEvents` filter the `events` ref by `status` value from the API.

`createEvent()` signature changes: accepts `passeIds: UUID[]` in addition to name. These are Programme UUIDs from the existing Passen/Programme tables and are stored on the backend session as an ordered array in `programSnapshots`. The order defines the sequential Passen progression.

`WettkampfDetailView` gains a second action button in SETUP state: "Veröffentlichen" (calls `openEvent()` → SETUP → OPEN). The existing "Wettkampf starten" button stays in OPEN state (calls `startEvent()` → OPEN → ACTIVE). Rotte/player editing is allowed in both SETUP and OPEN states.

### `src/stores/activePasseStore.js` — replace competition branch

Remove `startCompetition()` in-memory implementation. Competition instances come from `GET /api/sessions?type=competition&status=active`. The `markBlockDone()` branch for competition type calls `POST .../serien/{serieId}/complete` instead of mutating local state. `getActiveCompetitionRotten()` derives state from the backend progress response.

### `src/components/competition/ActiveCompetitionPanel.vue` — progress polling

Add a `setInterval` (3–5 seconds) that calls `GET /api/sessions/{id}/progress` and refreshes the local state. Interval is started in `onMounted` and cleared in `onUnmounted`. No SSE or WebSocket needed.

### No changes needed

`WettkampfListView`, `WettkampfDetailView`, `RotteEditorCard`, `CompletedResultsPanel` all read from the store — they will work once the store is backed by the API.

---

## Score History Flow

When a Serie completes for a Rotte:

1. `CompetitionSerieResult` row inserted (session, group, passeIndex, serieId, playInstanceId, results, completedAt).
2. For each `SessionPlayer` in the group: upsert `PlayerResult` — accumulate segment scores from `results` into the `programResults` JSON field.
3. When all Rotten complete all Passen, session auto-transitions to `PRE_COMPLETE`.
4. Admin confirms → `PATCH /status → completed` → `CompetitionService.updateCareerStatsForSession()` called to update `CareerStats` for all players.

This means `career_stats` has accurate cross-competition history: totalScore, totalWins, avgScore, participations, lastCompeted.

---

## Out of Scope

- Real-time push (SSE/WebSocket) for progress — polling is sufficient for now.
- Multi-Passe concurrent execution (each Rotte progresses sequentially through Passen).
- Bracket/elimination format — this design covers the sequential Passen format only.
