# Competition & Training Management System Overview

**Status**: Phase 5 Complete — Extended Tournament Capabilities implemented

---

## System Architecture

The competition and training system is organized into two parallel workflows:

```
┌─────────────────────────────────────────────────────────────┐
│         Competition & Training Management System             │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────────────┐   ┌────────────────────────┐  │
│  │  Training Mode           │   │  Tournament Mode       │  │
│  │  (Live Range Sessions)   │   │  (Bracket Elimination) │  │
│  │                          │   │                        │  │
│  │ • Templates              │   │ • Bracket Init         │  │
│  │ • Sessions (ACTIVE)      │   │ • Seeding Strategy     │  │
│  │ • Range Management       │   │ • Match Recording      │  │
│  │ • Group Queuing         │   │ • Phase Management     │  │
│  │ • Live Scoreboard       │   │ • Tournament Progress  │  │
│  └──────────────────────────┘   └────────────────────────┘  │
│          ↓                                ↓                   │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │         Career Statistics & Global Leaderboards        │ │
│  │  • Top Players (by points)                             │ │
│  │  • Top Players (by wins)                               │ │
│  │  • Per-Player Career Stats                             │ │
│  │  • Session Leaderboard Export                          │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 1. Training Mode (Live Range Sessions)

### Overview
Training mode manages shooting sessions where players practice on ranges. Sessions are based on **templates**, which define session configuration and rules. Multiple groups can shoot on different ranges during the same session.

### Key Entities

#### Session Templates
Templates are reusable session configurations stored in the backend.

**API Layer** (`competitionService.js`):
```javascript
listTemplates(params)       // List all templates, optionally filtered by type
createTemplate(data)        // Create new template
updateTemplate(id, data)    // Update existing template
deleteTemplate(id)          // Delete template
```

**State Management** (`competitionStore.js`):
```javascript
templates[]                 // Array of loaded templates
selectedTemplate           // Currently selected template
templatesByType            // Computed: templates grouped by type
```

#### Live Sessions
A session is an active instance of a template, with associated shooter groups and ranges.

**Session States**:
- `ACTIVE` — Session is running, groups are shooting
- `PAUSED` — Session temporarily halted
- `COMPLETED` — Session finished, results finalized
- `ABANDONED` — Session cancelled

**API Layer**:
```javascript
createSession(request)           // Create session from template + groups
updateSessionStatus(id, status)  // Change session status
getSessionLeaderboard(id)        // Get current live leaderboard
exportLeaderboard(id, format)    // Export to JSON/CSV
```

**State Management**:
```javascript
currentSession              // Active session object
sessionLeaderboard          // Current live rankings
isSessionActive             // Computed: session.status === 'ACTIVE'
isSessionCompleted          // Computed: session.status === 'COMPLETED'
```

### Range Management

Each session can spread across **multiple ranges** (shooting lanes). Ranges are selected by range operators to manage group flow.

**Group Registration** — Manages which groups are assigned to which ranges:

```javascript
registerGroupAtRange(sessionId, groupId, rangeId)    // Activate group on range
unregisterGroupFromRange(sessionId, groupId)         // Remove group from range
getGroupsAtRange(sessionId, rangeId)                 // Get active + queued groups
```

**Range State**:
```javascript
selectedRange              // UUID of currently-viewed range
groupsAtRange[]            // [active group, ...other groups shooting]
rangeQueue[]               // Groups waiting to shoot
```

**Group Flow**:
1. Groups register at a range
2. First group in queue becomes **active** (currently shooting)
3. Remaining groups are **queued** (waiting their turn)
4. When active group finishes, next group from queue becomes active
5. New groups can be registered at any time

### UI Components

#### CompetitionLiveView
Admin/range-operator view for managing live sessions.

**Features**:
- Range selector (tabs for each range)
- Active group display
- Group queue with activation buttons
- Session status controls (pause/end)
- Live scoreboard integration

**Routes**:
```
/competition/live              # Main live session view
```

#### LiveScoreboard Component
Displays current player rankings for the selected range.

**Data**: Real-time rankings from `sessionLeaderboard`

### WebSocket Integration (Planned)

Real-time updates for:
- `onLeaderboardUpdate(data)` — New scores update rankings
- `onRangeUpdate(data)` — Group queue/active group changes

---

## 2. Tournament Mode (Bracket System)

### Overview
Tournament mode runs **elimination bracket** tournaments. Competitors are seeded and progress through rounds until a champion is determined. Supports single and double elimination formats.

### Bracket Lifecycle

```
SETUP
  ↓
  Create bracket + seed players
  ↓
SEEDING
  ↓
  Review/adjust seeding
  ↓ [confirmSeeding]
IN_PROGRESS
  ↓
  Record match results
  ↓ [round after round]
FINALS
  ↓
  Final match recorded
  ↓ [final winner]
COMPLETED
  ↓
  Results finalized, leaderboard generated
```

### Key Entities

#### Bracket Configuration

**Bracket Type**:
- `SINGLE_ELIMINATION` — Lose once, eliminated
- `DOUBLE_ELIMINATION` — Lose twice before elimination

**Seeding Strategies**:
- `BY_CAREER_STATS` — Seed by current player ranking
- `MANUAL` — Admin manually orders seeds
- `BALANCED` — Distribute top players across bracket

**Tiebreakers** (in match recording):
```javascript
[
  "TOTAL_SCORE",      // Higher total points wins
  "WINS",             // More match wins
  "RECENT_PERFORMANCE" // Latest session performance
]
```

#### Bracket State

**API Layer** (`bracketService.js`):
```javascript
initializeBracket(sessionId, config)      // Initialize new bracket
getBracketState(sessionId)                // Get full bracket structure
getBracketPhase(sessionId)                // Get current phase (SETUP|SEEDING|etc)
confirmSeeding(sessionId)                 // Lock seeding, move to IN_PROGRESS
startBracketPlay(sessionId)               // Start match play (SEEDING → IN_PROGRESS)
listMatches(sessionId, roundNumber?)      // Get all matches, optionally filtered
getNextMatch(sessionId)                   // Get next unplayed match
recordMatchWinner(sessionId, matchNum, result)  // Record match outcome
getBracketLeaderboard(sessionId)          // Get final standings
exportBracket(sessionId, format)          // Export (json|pdf|image)
```

#### Match Structure

A **match** represents a competition between two contestants (or a BYE).

**Match Object**:
```javascript
{
  matchNumber: 1,          // Unique identifier
  roundNumber: 1,          // Which round this match is in
  contestant1Id: "uuid",   // First competitor
  contestant2Id: "uuid",   // Second competitor
  winnerId: "uuid",        // null if unplayed
  score1: 85,              // Contestant 1 score
  score2: 92,              // Contestant 2 score
  isBye: false,            // True if only 1 contestant (auto-advances)
  isPlayed: false          // True if winnerId is set
}
```

**BYE Matches** — When round structure requires fewer matches than competitors. Winner auto-advances (no match played).

### State Management (`bracketStore.js`)

**Core State**:
```javascript
currentSession              // Session ID for bracket
bracketType                 // SINGLE_ELIMINATION | DOUBLE_ELIMINATION
bracketPhase               // SETUP | SEEDING | IN_PROGRESS | FINALS | COMPLETED
seededPlayers[]            // [{ playerId, seed, displayName }, ...]
matchesByRound             // { 1: [matches], 2: [matches], ... }
bracketMetadata            // { totalRounds, totalByes, roundCount }
selectedMatch              // Currently selected match (UI)
champion                   // { id, name } — tournament winner
```

**Computed**:
```javascript
nextUnplayedMatch          // Next match without a winner
roundProgress{}            // Progress % per round
bracketProgress            // Overall tournament completion %
isComplete                 // Phase === 'COMPLETED'
currentRound               // Which round is active
```

### Match Recording Flow

1. **Select Match** — Admin clicks a match in bracket visualization
2. **Enter Scores** — Input both contestants' final scores
3. **Determine Winner** — Confirm which contestant won
4. **Submit** — POST to backend
5. **Update** — Bracket refreshes, next match becomes available

**CompetitionBracketView** manages:
- Bracket visualization (with zoom)
- Match selection
- Score entry
- Result submission
- BYE handling

### UI Components

#### CompetitionBracketView
Tournament bracket display and match recording interface.

**Layout**:
- **Left Panel**: Bracket visualization (zoomable)
- **Right Panel**: Match recording form

**Features**:
- Zoom controls (in/out/reset)
- Match selection highlighting
- Score input fields
- Winner confirmation
- BYE auto-advance button
- Connection status indicator
- Phase/progress display

**Routes**:
```
/competition/bracket        # Main bracket view
```

#### BracketVisualizer Component
Renders the bracket tree structure.

**Handles**:
- Multi-round visualization
- Match flow/lines
- Contestant display
- Winner highlighting
- Interactive match selection

---

## 3. Career Statistics & Leaderboards

### Global Rankings

**Top Players** — Sorted by total career points:
```javascript
getTopPlayers(page?, size?)          // List top players overall
getCareerStats(userId)                // Single player career stats
```

**Top Players by Wins** — Sorted by number of wins:
```javascript
getTopPlayersByWins(page?, size?)
```

**State Management** (`competitionStore.js`):
```javascript
topPlayers[]                // Top performers by score
topPlayersByWins[]          // Top performers by wins
```

### Per-Session Leaderboards

Each session generates a live leaderboard showing current standings.

```javascript
getSessionLeaderboard(sessionId)      // Session-specific rankings
exportLeaderboard(sessionId, format)  // Export as JSON or CSV
```

**Leaderboard Structure**:
```javascript
{
  sessionId: "uuid",
  rankings: [
    { rank: 1, playerId: "uuid", name: "Alice", score: 450, wins: 12 },
    { rank: 2, playerId: "uuid", name: "Bob", score: 420, wins: 10 },
    ...
  ]
}
```

### UI Components

#### CompetitionLeaderboardView
Display session or global leaderboards.

**Routes**:
```
/competition/leaderboard    # Session leaderboard during play
/career-stats               # Global career stats & top players
```

---

## 4. Routes & Navigation

### Admin Routes (Range Owner/Operator)

```
/competition/templates      # CompetitionTemplateListView
                           # Create/edit/delete session templates

/competition/setup         # CompetitionSetupView
                           # Create session from template, assign groups/ranges

/competition/bracket       # CompetitionBracketView
                           # Manage bracket tournament (seeding, match recording)

/competition/live          # CompetitionLiveView
                           # Live range operator view (group management, scoreboard)

/player-setup              # PlayerSetupView
                           # Manage player registry
```

### Shooter Routes (Players)

```
/home                      # ShooterHomeView
                           # Main shooter dashboard

/competition/live          # CompetitionLiveView
                           # View live session progress (read-only)

/competition/leaderboard   # CompetitionLeaderboardView
                           # View session leaderboard

/career-stats              # CareerStatsView
                           # View personal statistics & global rankings
```

---

## 5. API Endpoints Summary

### Templates
- `GET /api/session-templates` — List templates
- `POST /api/session-templates` — Create template
- `PUT /api/session-templates/{id}` — Update template
- `DELETE /api/session-templates/{id}` — Delete template

### Sessions
- `POST /api/sessions` — Create session from template
- `PATCH /api/sessions/{id}/status` — Update status
- `GET /api/sessions/{id}/leaderboard` — Get session leaderboard
- `GET /api/sessions/{id}/leaderboard/export?format=json|csv` — Export leaderboard

### Groups & Ranges
- `POST /api/sessions/{id}/groups/{groupId}/register` — Register group at range
- `DELETE /api/sessions/{id}/groups/{groupId}/register` — Unregister group
- `GET /api/sessions/{id}/range/{rangeId}/groups` — Get active/queued groups

### Brackets
- `POST /api/sessions/{id}/bracket` — Initialize bracket
- `GET /api/sessions/{id}/bracket` — Get bracket state
- `GET /api/sessions/{id}/bracket/phase` — Get current phase
- `PUT /api/sessions/{id}/bracket/seeding` — Confirm seeding
- `PUT /api/sessions/{id}/bracket/start` — Start play
- `GET /api/sessions/{id}/bracket/matches[?round=N]` — List matches
- `GET /api/sessions/{id}/bracket/matches/next` — Get next unplayed match
- `POST /api/sessions/{id}/bracket/matches/{matchNum}` — Record winner
- `GET /api/sessions/{id}/bracket/leaderboard` — Final standings
- `GET /api/sessions/{id}/bracket/export?format=json|pdf|image` — Export bracket

### Career Stats
- `GET /api/career-stats/top-players` — Top by score
- `GET /api/career-stats/top-players/wins` — Top by wins
- `GET /api/career-stats/{userId}` — Individual stats

---

## 6. State Management Overview

### Store Hierarchy

```
competitionStore              (Template, Session, Leaderboard management)
├── templates, selectedTemplate
├── currentSession
├── sessionLeaderboard
├── selectedRange, groupsAtRange, rangeQueue
├── topPlayers, topPlayersByWins
└── isLoading, error

bracketStore                  (Bracket state & match management)
├── currentSession, bracketType, bracketPhase
├── seededPlayers, matchesByRound, bracketMetadata
├── selectedMatch, champion
└── isLoading, error
```

### WebSocket/Real-time Updates

**Competition Store**:
- `onLeaderboardUpdate(data)` — Update session leaderboard
- `onRangeUpdate(data)` — Update group queue/active group

**Bracket Store**:
- `onBracketStateUpdate(data)` — Update bracket structure
- `onMatchResultUpdate(data)` — Match result recorded
- `onPhaseChange(data)` — Bracket phase changed
- `onRoundCompletion(data)` — Round finished
- `onBracketCompleted(data)` — Tournament winner determined

---

## 7. Current Implementation Status

### ✅ Completed
- **Service Layer**: Full API integration for templates, sessions, brackets, leaderboards
- **State Management**: Pinia stores with computed properties and actions
- **Bracket Tournament**: Phase management (SETUP → SEEDING → IN_PROGRESS → COMPLETED)
- **Match Recording**: Score entry, winner determination, BYE handling
- **Bracket Visualization**: UI for rendering bracket trees
- **Range Management**: Group registration/queueing system
- **Leaderboard**: Session and global rankings
- **Career Stats**: Top players, wins, per-player statistics

### 🔄 Partially Complete
- **Real-time Updates**: WebSocket handlers defined but integration pending
- **Export Functionality**: Endpoints defined, UI not yet complete
- **Admin UI**: Templates and setup views need full implementation
- **Player Setup**: View exists but business logic needs completion

### ⏳ Not Yet Implemented
- **Match History**: Detailed match replays and analysis
- **Drawing/Bracket Editor**: UI for manual seeding adjustment
- **Notification System**: Real-time alerts for players/operators
- **PDF/Image Export**: Bracket image generation
- **Multi-Session Management**: Concurrent tournament support
- **Advanced Filtering**: Leaderboard filters and sort options

---

## 8. Development Notes

### Key Files
- **Services**: `src/services/competitionService.js`, `src/services/bracketService.js`
- **Stores**: `src/stores/competitionStore.js`, `src/stores/bracketStore.js`
- **Views**: `src/views/competition/`, `src/views/CompetitionBracketView.vue`, `src/views/PlayerSetupView.vue`
- **Components**: `src/components/` (BracketVisualizer, LiveScoreboard, etc.)
- **Router**: `src/router/index.js` (route definitions with layout meta)

### Backend Integration Points
The frontend consumes REST APIs from `smart-ground-backend`. All endpoints are prefixed with `VITE_API_BASE_URL` (default: `http://localhost:8080/api`).

### Error Handling
- Service layer: Throws errors on non-2xx responses
- Store layer: Catches errors, stores in `error` ref, logs to console
- UI: Displays error state, provides user feedback

---

## Next Steps for Development

1. **WebSocket Integration** — Implement real-time leaderboard and bracket updates
2. **Admin UX** — Complete template creation and session setup flows
3. **Export Features** — Implement leaderboard CSV export and bracket PDF generation
4. **Player Management** — Build player registry UI
5. **Testing** — Add unit tests for stores and components
6. **Performance** — Optimize large bracket rendering, leaderboard updates

---

**Last Updated**: 2026-05-10
**Phase**: 5 (Extended Tournament Capabilities)
