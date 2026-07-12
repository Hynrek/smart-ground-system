# Smart Ground — Session & Competition System Design

> **Status:** Historisch (Stand ~Mai 2026) — beschreibt die Prä-Hub/Node-Welt mit zentralem Backend, MQTT und direkter Client→Backend-Kommunikation.
> **Überholt durch:** [Hub/Node-Spec](superpowers/specs/2026-07-10-hub-node-architecture-design.md) (die Autorität für Architektur und Transport). Domänenkonzepte (Serie/Passe/Rotte/Competition) bleiben als Referenz brauchbar; alle hier beschriebenen Flows und die WebSocket-/Backend-Annahmen sind vor einer Umsetzung gegen die Spec neu zu denken.
> **Scope (ursprünglich):** Program segments, multi-player sessions, shooter groups, competitions, training, score history.

---

## Table of Contents

1. [Domain Concepts](#1-domain-concepts)
2. [Entity Model](#2-entity-model)
3. [Frontend Store Architecture](#3-frontend-store-architecture)
4. [Backend API](#4-backend-api)
5. [WebSocket Architecture](#5-websocket-architecture)
6. [Key Flows](#6-key-flows)
7. [Scoreboard Design](#7-scoreboard-design)
8. [Implementation Phases](#8-implementation-phases)

---

## 1. Domain Concepts

### Session Types

| Type | Saved to backend | History | Groups | Scoreboard |
|---|---|---|---|---|
| `free` | Optional (user chooses at end) | Only if saved | No | No |
| `training` | At session start | Always | Yes | Yes |
| `competition` | At session start | Always | Yes | Yes (live) |

A **free game** is casual play — a user plays a program alone, no tracking. At the end they can optionally save it and tag it as training. This is distinct from a structured training session defined upfront.

### Programs & Segments

A **Program** is a sequence of device commands divided into named **Segments**. Each segment belongs to one range (1:1 default). If a program has multiple segments on the same range, the operator selects which segment to play during group registration.

Steps within a segment are unchanged from the current model: `solo`, `pair`, `a.schuss`, `raffale`.

### Shooter Groups

In a competition or training, players are organised into **ShooterGroups**. Each group works through all segments of a program together. Groups start at different segments (staggered) and self-route through ranges dynamically — there is no predefined cycle order.

**Registration rule:** A group may register at Range X if they have not yet completed that range's segment in the current program pass, OR if it is their last remaining segment (all others done).

### Range Operators

Each range has one operator tablet. The operator selects their range when opening the session. They see only the steps for their range, the currently registered group, and a queue of groups that still need to visit.

---

## 2. Entity Model

### 2.1 Program (localStorage, unchanged key format)

```
Program {
  id:       string          // localStorage key — {userName}_program_{n}
  name:     string
  segments: Segment[]
}

Segment {
  id:    string             // uuid or sequential string
  alias: string | null      // display name; falls back to "Segment {n}"
  steps: Step[]             // existing Step structure — unchanged
}
```

**Backward compatibility:** Programs saved in the old flat format (`{ programName, steps[] }`) are wrapped on load:
```js
if (data.steps && !data.segments) {
  data.segments = [{ id: 's1', alias: null, steps: data.steps }];
}
```

### 2.2 SessionTemplate (backend)

Defines a reusable Competition or Training configuration.

```
SessionTemplate {
  id:               UUID
  name:             string
  type:             'competition' | 'training'
  programIds:       UUID[]                    // ordered; may contain the same program multiple times
  rangeSegmentMap:  RangeSegmentEntry[]       // which segment(s) each range handles
  defaultPlayers?:  SessionPlayer[]           // optional preset roster (mutable at session start)
}

RangeSegmentEntry {
  rangeId:    UUID
  segmentIds: UUID[]   // normally 1 — multiple only if range handles >1 segment of the same program
}
```

### 2.3 LiveSession (backend — created at startSession for comp/training)

```
LiveSession {
  id:               UUID
  type:             'competition' | 'training' | 'free'
  templateId?:      UUID
  status:           'setup' | 'active' | 'paused' | 'completed' | 'abandoned'
  programs:         Program[]            // snapshots taken at session start
  rangeSegmentMap:  RangeSegmentEntry[]
  groups:           ShooterGroup[]
  playerResults:    PlayerResult[]       // accumulated as play progresses
  startedAt:        ISO timestamp
  completedAt?:     ISO timestamp
}
```

### 2.4 ShooterGroup

```
ShooterGroup {
  id:       UUID
  name:     string             // "Gruppe 1", "Gruppe A", etc.
  members:  SessionPlayer[]
  progress: GroupProgress[]    // one entry per program
}

GroupProgress {
  programId:            UUID
  completedSegmentIds:  UUID[]   // segments this group has finished in this program pass
  activeRangeId?:       UUID     // range where the group is currently registered (null = in transit)
  activeSegmentId?:     UUID     // the segment they are currently playing
}
```

### 2.5 SessionPlayer

```
SessionPlayer {
  id:           string
  type:         'user' | 'guest'
  userId?:      UUID         // null for guests
  displayName:  string
}
```

### 2.6 PlayerResult

Accumulated per player across the session. Built incrementally and POSTed after each player's turn.

```
PlayerResult {
  playerId:       string
  programResults: ProgramResult[]
}

ProgramResult {
  programId:      UUID
  segmentResults: SegmentResult[]
}

SegmentResult {
  segmentId:   UUID
  groupId:     UUID              // which group this player was in for this segment
  stepResults: StepResult[]
  score:       number
  maxScore:    number
}

StepResult {
  stepId:       string
  state:        'pending' | 'done' | 'failed-both' | 'failed-a' | 'failed-b'
  noBirds:      number           // count of machine failures before the actual throw
  pointsEarned: number
  corrections:  Correction[]
}

Correction {
  id:           UUID
  type:         'hit-miss'       // only non-admin-editable type
  oldState:     string
  newState:     string
  correctedBy:  UUID             // userId of admin
  correctedAt:  ISO timestamp
}
```

**Scoring rules:**

| Step type | Max points |
|---|---|
| `solo` | 1 |
| `pair` | 2 |
| `a.schuss` | 2 |
| `raffale` | 2 |

| State | Points earned |
|---|---|
| `done` | full pointValue |
| `failed-both` | 0 |
| `failed-a` or `failed-b` | pointValue − 1 |

Point deductions use a delta model (old deduction vs new deduction) so calling `failStep` multiple times never double-deducts.

`noBirds` is tracked per step and is not editable by non-admins. Admins can correct via the `Correction` audit log only.

### 2.7 GuestPlayer (localStorage only)

```
GuestPlayer {
  id:           UUID             // stable across sessions on the same device
  displayName:  string
  createdAt:    ISO timestamp
}
```

localStorage key: `smart-ground:guests` → `GuestPlayer[]`

Guest results are included in the session payload sent to the backend (for the game record), but no backend user account aggregates their history.

---

## 3. Frontend Store Architecture

The current `shooterRemoteStore.js` is split into four focused stores.

```
shooterRemoteStore      range reservation + device group selection (trimmed)
programStore            program building: segments, steps, save/load/edit
playSessionStore        live step execution for the current player's turn at a range
competitionStore        session-level state: groups, progress, registration, WebSocket sync
guestStore              guest player cache (localStorage)
sessionHistoryStore     backend-backed: history, templates, corrections
```

### 3.1 programStore

**State:**
```js
editingProgram:       Program | null
activeSegmentIndex:   number          // which segment new steps are added to
pairPending:          { id, alias } | null
savedPrograms:        Program[]
```

**Actions:**
```
startNewProgram()                   → create Program with 1 empty Segment, activeSegmentIndex = 0
addSegment()                        → push new Segment, set activeSegmentIndex to last
setActiveSegment(index)             → operator taps a segment tab to switch context
addStep(deviceId, deviceData)       → adds to segments[activeSegmentIndex].steps
removeStep(segmentIndex, stepId)
removeSegment(index)                → guarded: confirm if segment has steps
reorderSegments(from, to)
saveProgram(name?)
editProgram(id)                     → loads into editingProgram
deleteProgram(id)
loadProgramsFromStorage()           → handles old flat format migration
```

### 3.2 playSessionStore

Handles step-by-step execution for the **current player's turn** at a range. Unchanged from the current store's playback logic except:
- Operates on `currentSegment` (a `Segment`) rather than a flat program
- `noBirds: number` replaces the old boolean
- Exposes `completionPct` computed

**State:**
```js
currentSegment:         Segment | null
currentStepIndex:       number
playPartialStep:        'first' | 'second' | null
playRaffaleStarted:     boolean
stepResults:            StepResult[]         // built during the turn
lastCompletedStepIndex: number | null        // for NoBird / failStep targeting
```

**Actions:**
```
startTurn(segment, existingResults?)   → initialise for a player's turn
advancePlayStep()                      → fire device for current step
failStep(type)                         → mark last completed step as failed-{type}
retryStep()                            → NoBird: increment noBirds on current step, re-fire
markStepDone()                         → mark done, accumulate points, advance
completeRaffaleStep()                  → fire second raffale command
endTurn()                              → return StepResult[] to competitionStore
```

**Computed:**
```js
completionPct    // (non-pending steps / total steps) * 100
currentScore     // sum of pointsEarned so far in this turn
maxScore         // sum of all step pointValues in segment
```

### 3.3 competitionStore

Manages the full session lifecycle, group routing, and WebSocket sync.

**State:**
```js
session:              LiveSession | null
myRangeId:            UUID | null           // selected by operator at session open
activeGroup:          ShooterGroup | null   // group currently at my range
groupQueue:           ShooterGroup[]        // groups waiting for my range (not yet registered)
currentPlayerOrder:   UUID[]                // player ids in play order for this turn
currentPlayerIndex:   number
roundStartIndex:      number                // rotates each segment; operator can override
wsConnected:          boolean
```

**Actions:**
```
// Setup
createSession(type, templateId?)           → POST /api/sessions
loadSession(sessionId)                     → GET /api/sessions/{id}
selectRange(rangeId)                       → set myRangeId, subscribe to WS topics
setSessionStatus(status)                   → PATCH /api/sessions/{id}/status

// Group management
addGroup(name, members)
updateGroup(groupId, changes)
removeGroup(groupId)

// Registration
registerGroup(groupId, segmentId)          → POST /api/sessions/{id}/groups/{groupId}/register
unregisterGroup(groupId)                   → DELETE .../register   (called after turn complete)

// Player rotation
buildRoundOrder(group)                     → compute currentPlayerOrder from roundStartIndex
advancePlayer()                            → move to next player in order
setRoundStartOverride(playerIndex)         → change starting player for next round

// Result submission
submitPlayerTurn(playerId, segmentId, stepResults[])
  → PATCH /api/sessions/{id}/results
  → updates session.playerResults locally
  → WebSocket broadcasts to all tablets

// WebSocket
connectWebSocket(sessionId)
handleSessionUpdate(payload)               → merge incoming state
handleRangeUpdate(payload)                 → update activeGroup, groupQueue
```

### 3.4 guestStore

```js
guests: GuestPlayer[]

addGuest(displayName)     → uuid + persist to localStorage
removeGuest(id)
loadGuests()              → called on app init
```

### 3.5 sessionHistoryStore

```js
sessions: SessionSummary[]    // paginated history
templates: SessionTemplate[]

fetchHistory(filters)
fetchTemplate(id)
saveTemplate(template)
deleteTemplate(id)
fetchScoreboard(sessionId)
submitCorrection(sessionId, correction)   // admin only
```

---

## 4. Backend API

### 4.1 Session Templates

```
POST   /api/session-templates
GET    /api/session-templates?type=competition|training
GET    /api/session-templates/{id}
PUT    /api/session-templates/{id}
DELETE /api/session-templates/{id}
```

### 4.2 Sessions

```
POST   /api/sessions
         body: { type, templateId?, programs[], rangeSegmentMap[], groups[] }
         → creates session (status: 'setup'), returns session with id
         → for comp/training only; free games stay local until optional save

GET    /api/sessions/{id}
         → full session state (used for resume after tablet disconnect)

PATCH  /api/sessions/{id}/status
         body: { status: 'active' | 'paused' | 'completed' | 'abandoned' }

GET    /api/sessions?userId=&type=&rangeId=&page=&size=
         → history with filters
```

### 4.3 Groups

```
POST   /api/sessions/{id}/groups
         body: { name, members: SessionPlayer[] }

PUT    /api/sessions/{id}/groups/{groupId}
         body: { name?, members? }                   // mutable until session is 'active'

DELETE /api/sessions/{id}/groups/{groupId}
```

### 4.4 Group Registration

```
POST   /api/sessions/{id}/groups/{groupId}/register
         body: { rangeId, segmentId }
         Validation:
           - segmentId not already in group.progress[program].completedSegmentIds
           - OR it is the only remaining segment (all others completed)
           - segmentId must belong to rangeId per session.rangeSegmentMap
         On success:
           - sets group.progress.activeRangeId + activeSegmentId
           - broadcasts WS update to /topic/sessions/{id} and /topic/sessions/{id}/range/{rangeId}

DELETE /api/sessions/{id}/groups/{groupId}/register
         Called when group completes their segment and leaves the range.
         - adds segmentId to completedSegmentIds
         - clears activeRangeId + activeSegmentId
         - broadcasts WS update
```

### 4.5 Results

```
POST   /api/sessions/{id}/results
         body: {
           groupId:   UUID,
           playerId:  string,
           programId: UUID,
           segmentId: UUID,
           stepResults: StepResult[]
         }
         → appends to session.playerResults
         → triggers WS broadcast with updated scoreboard
```

### 4.6 Scoreboard & Corrections

```
GET    /api/sessions/{id}/scoreboard
         → computed: per-player scores, group totals, completion %, rank

POST   /api/sessions/{id}/corrections                     // admin only
         body: { playerId, programId, segmentId, stepId, oldState, newState }
         → appends Correction to StepResult audit log
         → recalculates scores
         → broadcasts WS update
```

### 4.7 User Stats

```
GET    /api/users/{id}/stats
         → aggregated per-user history (excludes guest results)
```

---

## 5. WebSocket Architecture

**Technology:** Spring WebSocket with STOMP (consistent with the existing MQTT pub/sub model).

### Topics

```
/topic/sessions/{sessionId}
  → broadcast to all tablets in the session
  → payload: full session state delta (groups, playerResults, status)

/topic/sessions/{sessionId}/range/{rangeId}
  → broadcast to the operator of a specific range
  → payload: { activeGroup, groupQueue, currentStepState }
```

### Client behaviour

```
On session open:
  subscribe /topic/sessions/{sessionId}             ← scoreboard, group movements
  subscribe /topic/sessions/{sessionId}/range/{myRangeId}   ← range-specific state

On disconnect / tablet crash:
  Session persists in backend (status stays 'active')
  On reconnect: GET /api/sessions/{id} → restore full state
  Re-subscribe to both WS topics
```

### Mutation pattern

All **mutations** (register group, submit results, change status) go through **REST**.  
WebSocket is **receive-only** on the client — the backend publishes after every successful mutation.

This avoids client-side conflict resolution and keeps the backend as the single source of truth.

### Range Operator Dashboard (live state via WS)

```
Range 2 — Segment 2 — Programm 1
────────────────────────────────────────
● AKTIV   Gruppe 3   Step 4 / 5
  Player: Marc Schmidt

  Wartend: Gruppe 1, Gruppe 7

────────────────────────────────────────
Andere Ranges:
  Range 1: Gruppe 5  (3/5 steps)
  Range 3: Gruppe 2  (5/5 — fertig)
────────────────────────────────────────
```

---

## 6. Key Flows

### 6.1 Free Game ("just play")

```
User selects range → reservePlatz()
User selects program → loads from programStore
User taps play → playSessionStore.startTurn(segment)
  advancePlayStep() / failStep() / retryStep() per step
  endTurn() → results held in memory

At end:
  Option A: Close, discard  → no backend call
  Option B: "Save as training?" → POST /api/sessions (type:'free', saved:true)
```

### 6.2 Competition — Setup & Start

```
Operator creates or selects SessionTemplate
  → programs selected, rangeSegmentMap configured
  → groups created, members added (users + guests)
  → POST /api/sessions → status: 'setup'

When roster is confirmed:
  → PATCH /api/sessions/{id}/status { status: 'active' }
  → WebSocket: all range tablets receive session state
```

### 6.3 Group Registration at a Range

```
Group arrives at range
Operator opens session, selects their range (myRangeId)
Operator registers group:
  → If range has 1 segment:    auto-selected
  → If range has >1 segments:  operator picks from dropdown

POST /api/sessions/{id}/groups/{groupId}/register
  body: { rangeId, segmentId }

Backend validates:
  ✓ segment not already completed by group
  ✓ OR it is the group's last remaining segment
  ✓ segmentId belongs to rangeId per rangeSegmentMap

On success:
  group.progress.activeRangeId = rangeId
  group.progress.activeSegmentId = segmentId
  WS broadcast: session update + range update
```

### 6.4 Playing Through a Segment (at a range)

```
competitionStore.buildRoundOrder(group)
  → currentPlayerOrder = [p2, p0, p1] based on roundStartIndex

For each player in currentPlayerOrder:
  UI: "Jetzt schiesst: {playerName}"
  playSessionStore.startTurn(segment)

  Loop until all steps done:
    advancePlayStep()       → fires device via REST /api/devices/{id}/command
    failStep(type)?         → operator marks miss
    retryStep()?            → NoBird: noBirds++ on step, re-execute step

  playSessionStore.endTurn() → returns StepResult[]

  competitionStore.submitPlayerTurn(playerId, segmentId, results)
    → POST /api/sessions/{id}/results
    → WS broadcast: scoreboard updated

  competitionStore.advancePlayer()
    → currentPlayerIndex++

All players done:
  → roundStartIndex = (roundStartIndex + 1) % players.length  (rotation)
  → DELETE /api/sessions/{id}/groups/{groupId}/register
  → group released, free to move to next range
```

### 6.5 Abandonment & Resume

```
Tablet loses connection mid-session:
  → Backend session stays 'active' (no timeout kills it)
  → Operator reconnects, opens same session
  → GET /api/sessions/{id} restores full state
  → Re-subscribe to WS topics
  → competitionStore hydrates from response
  → playSessionStore restores in-progress turn from last submitted results

Explicit abandon:
  → PATCH /api/sessions/{id}/status { status: 'abandoned' }
  → Partial results already in backend are preserved in history
```

### 6.6 Post-Completion Corrections (Admin)

```
Session status: 'completed'
Admin opens scoreboard
Admin selects a player's step → changes hit/miss state

POST /api/sessions/{id}/corrections
  body: { playerId, programId, segmentId, stepId, oldState, newState }

Backend:
  → appends Correction to StepResult.corrections (append-only audit log)
  → recalculates pointsEarned for that step using delta model
  → recalculates totals
  → WS broadcast (or simple page reload for post-completion)

NoBirds: not correctable by non-admins.
         Admin correction goes through same Correction log with type: 'nobird-adjust'.
```

---

## 7. Scoreboard Design

### Data shape (from GET /api/sessions/{id}/scoreboard)

```json
{
  "players": [
    {
      "playerId": "...",
      "displayName": "Jonas",
      "groupId": "...",
      "groupName": "Gruppe 1",
      "totalScore": 18,
      "maxScore": 25,
      "completionPct": 72,
      "rank": 1,
      "programResults": [
        {
          "programId": "...",
          "programName": "Programm A",
          "subtotal": 18,
          "segmentResults": [
            {
              "segmentId": "...",
              "alias": "Segment 1",
              "score": 5,
              "maxScore": 5,
              "completionPct": 100
            }
          ]
        }
      ]
    }
  ],
  "groupTotals": [
    {
      "groupId": "...",
      "groupName": "Gruppe 1",
      "totalScore": 62,
      "rank": 1
    }
  ]
}
```

### Completion percentage

```
// Per player — overall session
completionPct = (steps where state !== 'pending') / totalSteps * 100

// Per segment
segmentCompletionPct = completedSteps / segment.steps.length * 100
```

The range operator's live view shows `segmentCompletionPct` for the active group (how far through the current segment). The main scoreboard shows the overall session `completionPct` per player.

### Group total

Group total = sum of `totalScore` across all group members. Sorting competitions by group rank uses this value.

---

## 8. Implementation Phases

### Phase 1 — Program Segments (frontend only)

- Split `shooterRemoteStore` → `shooterRemoteStore` (trimmed) + `programStore` + `playSessionStore`
- Add `Segment[]` to Program model
- Add `addSegment()`, `setActiveSegment()`, segment UI in capture mode
- Migration: wrap old flat programs on `loadProgramsFromStorage()`
- Update `playSessionStore` to operate on a single `Segment` (not a flat program)
- Update `noBird: boolean` → `noBirds: number`
- Add `completionPct` computed to `playSessionStore`

### Phase 2 — Multi-player (single device, free game)

- Add player setup UI (users + guests from `guestStore`)
- Implement round-order rotation in `playSessionStore`
- Add "between players" confirmation UI
- Add per-player scoring and local scoreboard
- Guest player localStorage cache (`guestStore`)

### Phase 3 — Backend: Session persistence

- New backend entities: `SessionTemplate`, `LiveSession`, `ShooterGroup`, `PlayerResult`
- REST endpoints: sessions, templates, groups, registration, results, scoreboard, corrections
- Spring WebSocket / STOMP setup
- `competitionStore` frontend store
- Free game optional save flow

### Phase 4 — Competition & Training

- Session template CRUD (UI + API)
- Group registration flow (UI + API + validation)
- Range operator dashboard (WebSocket-driven)
- Live scoreboard (WebSocket updates)
- Post-completion correction UI (admin)

### Phase 5 — History & Stats

- Session history view (filterable by type, user, range, date)
- User stats page (`GET /api/users/{id}/stats`)
- Scoreboard export (optional)

---

## Open Decisions (deferred, not blocking Phase 1–2)

| Topic | Question | Notes |
|---|---|---|
| Session resume granularity | Should in-progress player turns be resumable, or only completed turns? | Completing a turn and then reconnecting is straightforward; mid-turn resume requires local state persistence |
| Multiple programs in one session | Do all groups always play all programs, or can groups be assigned to specific programs? | Current model: all groups play all programs |
| NoBird admin correction | Admin corrects via `Correction` log with `type: 'nobird-adjust'` — confirm this is sufficient or if a dedicated endpoint is needed | |
| Offline mode | If the tablet loses WiFi mid-session, should step results queue locally and sync on reconnect? | Out of scope for now; resume flow covers reconnect |
