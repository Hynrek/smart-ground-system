# Admin Wettkampf View — Design Spec
Date: 2026-05-29

## Overview

Implement a full admin-side Wettkampf management flow with a dedicated `competitionEventStore`. The store is independent from `passeStore` and `activePasseStore`, bridging to the latter only via `activeInstanceId` when a competition goes live.

The shooter `/wettkampf` view is simplified to a read-only participant view (progress + leaderboard only).

---

## Data Model

### Competition Event (stored in `sg_competition_events` localStorage key)

```js
{
  id: uuid,
  name: "Frühjahrspokal 2026",
  passen: [...],          // snapshot of Passen at creation time (not a reference)
  status: "PLANNING",     // PLANNING | ACTIVE | COMPLETED | CANCELLED
  rotten: [
    {
      rotteId: uuid,
      name: "Rotte A",
      players: [
        { id: uuid, displayName: "Hans Muster", paid: false },
        { id: uuid, displayName: "Peter Meier", paid: true }
      ]
    }
  ],
  activeInstanceId: null, // set when ACTIVE; links to activePasseStore instance
  createdAt: timestamp,
  startedAt: null,
  completedAt: null
}
```

**Key decisions:**
- `passen` is a snapshot so later edits to `passeStore` templates don't affect running events
- `activeInstanceId` is the only coupling between `competitionEventStore` and `activePasseStore`
- `passeStore` competition templates are untouched — still usable as source when creating a new event
- `paid` is a boolean per player per competition event instance (not global)

---

## Lifecycle

```
PLANNING → ACTIVE → COMPLETED
                 ↘ CANCELLED
```

- **PLANNING**: fully editable — Rotten, players, payment, name
- **ACTIVE**: read-only config; admin can assign Rotten to ranges and stop the competition
- **COMPLETED**: auto-set when all Rotten finish; read-only results view
- **CANCELLED**: admin stopped a running competition; excluded from results

---

## Admin View

### Route & Layout
- **Route:** `/admin/wettkampf` and `/admin/wettkampf/:id`
- **Layout:** `MainLayout` (sidebar, desktop-first)
- **Sidebar entry:** "Wettkampf" → replaces current `/competition` link

### Tab Structure (`/admin/wettkampf`)

| Tab | Shows |
|---|---|
| **Planung** | `PLANNING` events — list + inline create |
| **Aktiv** | `ACTIVE` events — progress monitor, range assignment |
| **Abgeschlossen** | `COMPLETED` events — results summary |

### Creating a New Event (Planung tab)
1. Admin clicks "Neuer Wettkampf"
2. Inline create panel: name field + Passen picker (sourced from `passeStore.savedTrainings` filtered to `type === 'competition'`)
3. On confirm: event saved as `PLANNING`, admin navigated to `/admin/wettkampf/:id`

### Detail View (`/admin/wettkampf/:id`) — PLANNING

**Header:** event name (editable), Passen count, total throw count

**Rotten section:**
- Add / remove Rotten (min 1, max 8)
- Each Rotte: editable name, player list
- Per player row: display name input + PAID/UNPAID toggle chip
  - `UNPAID` chip: red/muted
  - `PAID` chip: green
- Rotte header shows running count: "3/4 bezahlt"
- Global summary above Start button: "5/8 Schützen haben bezahlt"

**Start button (bottom, prominent):**
- If all paid → start immediately
- If any `paid: false` → warning banner listing unpaid players with two actions:
  - "Trotzdem starten" → proceeds
  - "Zurück" → dismisses warning
- On start:
  - `activePasseStore.startCompetition(event, event.rotten)` called
  - Returned `instanceId` stored as `event.activeInstanceId`
  - Event status set to `ACTIVE`
  - Admin navigated to active detail view

### Detail View — ACTIVE

- Read-only Rotten list with current Passe and block status per Rotte
- Range assignment per Rotte (same `assignRotteToRange` / `unassignRotte` logic as today)
- "Wettkampf stoppen" button → sets status to `CANCELLED`, calls `activePasseStore.stopInstance(activeInstanceId)`
- When `activePasseStore` instance completes (all Rotten done) → event auto-transitions to `COMPLETED`, `completedAt` set

### Detail View — COMPLETED

- Results per Rotte, per player: total points / max points broken down by Passe
- No edit controls

---

## Shooter View Changes (`/wettkampf`)

The shooter view is simplified to **read-only**:

- Shows only `ACTIVE` events from `competitionEventStore`
- Per event: Rotten list with progress (current Passe, status badge)
- Simple leaderboard per Rotte (player name + points)
- No creation, no template management, no Rotten setup controls
- The three-tab structure (Wettkämpfe / Aktiv / Abgeschlossen) is replaced by a single list of active competitions
- The `RotteSetupModal` is no longer used from the shooter view

---

## New Store: `competitionEventStore`

**File:** `src/stores/competitionEventStore.js`

**State:**
- `events: ref([])` — all competition events

**Computed:**
- `planningEvents` — filtered by `status === 'PLANNING'`
- `activeEvents` — filtered by `status === 'ACTIVE'`
- `completedEvents` — filtered by `status === 'COMPLETED'`

**Actions:**
- `createEvent(name, passen)` — creates PLANNING event, returns id
- `updateEventName(id, name)`
- `addRotte(id)` — appends new Rotte with default name
- `removeRotte(id, rotteId)`
- `renameRotte(id, rotteId, name)`
- `addPlayer(id, rotteId)` — appends player with empty displayName, paid: false
- `removePlayer(id, rotteId, playerId)`
- `updatePlayerName(id, rotteId, playerId, displayName)`
- `togglePlayerPaid(id, rotteId, playerId)`
- `startEvent(id)` — validates, calls `activePasseStore.startCompetition`, sets ACTIVE + activeInstanceId
- `stopEvent(id)` — sets CANCELLED, calls `activePasseStore.stopInstance`
- `checkAndCompleteEvent(id)` — called when activePasseStore instance finishes; sets COMPLETED + completedAt. Triggered by `WettkampfDetailView` watching `activePasseStore.completedInstances` for a match on `activeInstanceId`.
- `deleteEvent(id)` — only allowed in PLANNING status

**Persistence:** `localStorage.setItem('sg_competition_events', JSON.stringify(events.value))`

---

## New Components & Views

| Path | Purpose |
|---|---|
| `src/views/admin/WettkampfListView.vue` | Tab list: Planung / Aktiv / Abgeschlossen |
| `src/views/admin/WettkampfDetailView.vue` | Competition event detail (all lifecycle states) |
| `src/components/competition/RotteEditorCard.vue` | Single Rotte editor (players + payment) |
| `src/components/competition/PaymentChip.vue` | PAID/UNPAID toggle chip |

---

## Router Changes

```js
// New admin routes
{ path: '/admin/wettkampf', component: WettkampfListView, meta: { layout: 'admin' } },
{ path: '/admin/wettkampf/:id', component: WettkampfDetailView, props: true, meta: { layout: 'admin' } },
```

The existing `/competition` route and `CompetitionManagementView` remain intact (no deletion).

---

## Out of Scope

- Backend persistence (stays localStorage for now)
- Range locking during active competition
- Exporting results
- Bracket / league tournament formats (uses existing Passen-based format only)
- Importing registered users as players (all players are free-text names)
