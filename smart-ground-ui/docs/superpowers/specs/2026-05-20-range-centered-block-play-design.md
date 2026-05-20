# Range-Centered Block Play — Design Spec
**Date:** 2026-05-20  
**Status:** Approved

---

## Problem

The current play logic runs all Abläufe in a Program sequentially in one go on a single Range. This is logically wrong: each Ablauf belongs to a specific physical Range, so a Program with Abläufe on multiple Ranges cannot be played in one linear session.

---

## Solution Overview

Split Programs into independently playable **Blocks** (one Block = one Ablauf). The **FlyoutPanel** at each Range becomes the player's discovery hub, showing all Blocks available at that Range grouped by source. Players approach each Range on their own and play whatever Block they choose.

---

## Core Concepts

### Program Lifecycle
| State | Description |
|---|---|
| **Template** | A saved Program definition (`savedPrograms`). Never consumed. |
| **Aktives Programm** | An instance created when a user taps "Starten". Contains one Block per Ablauf, distributed to their Ranges. Shared by a group of players. |
| **Abgeschlossenes Programm** | All Blocks completed. Stored permanently for future stats. |

### Instance Types
The instance model is generic and extensible via a `type` field:
- `'programm'` — from a Program template (implemented now)
- `'training'` — future
- `'wettkampf'` — future

### Block
One Ablauf from a Program instance. Has its own `status` and stores the full play `result` on completion.

---

## Data Model

### Active Program Instance
```js
{
  instanceId: 'uuid',
  type: 'programm',            // 'programm' | 'training' | 'wettkampf'
  templateId: 'prog-uuid',
  templateName: 'Programm A',
  players: [
    { id: 'uuid', displayName: 'Schütze 1', type: 'guest' }
  ],
  startedAt: 1234567890,
  blocks: [
    {
      blockId: 'uuid',
      ablaufId: 'abl-uuid',
      ablaufAlias: 'Ablauf 1',
      rangeId: 'range-uuid',
      rangeName: 'Platz 1',
      status: 'pending',       // 'pending' | 'in_progress' | 'done'
      completedAt: null,
      result: null,            // null until played; BlockResult on completion
    }
  ]
}
```

### Block Result (stored in `block.result` on completion)
```js
{
  playerResults: [
    {
      playerId: 'uuid',
      displayName: 'Schütze 1',
      totalPoints: 18,
      maxPoints: 20,
      stepStates: [...]        // full step-by-step record (same shape as playScore.stepStates)
    }
  ]
}
```

### Completed Instance
Same shape as active instance, with `completedAt` timestamp set and all blocks in `'done'` status.

### Persistence
| Key | Content |
|---|---|
| `sg_active_program_instances` | `activeInstances[]` |
| `sg_completed_program_instances` | `completedInstances[]` |

---

## New Store: `activeProgramStore`

**File:** `src/stores/activeProgramStore.js`

### State
```js
const activeInstances = ref([])      // loaded from localStorage
const completedInstances = ref([])   // loaded from localStorage
```

### Key Actions
| Action | Description |
|---|---|
| `startProgram(template, players)` | Creates a new instance with one Block per Ablauf in the template. Persists to localStorage. |
| `getBlocksForRange(rangeId)` | Returns all `pending` and `in_progress` blocks across all active instances for a given Range. |
| `markBlockInProgress(instanceId, blockId)` | Sets block status to `'in_progress'`. Called when play starts. |
| `markBlockDone(instanceId, blockId, playerResults)` | Sets block to `'done'`, stores result. If all blocks done → moves instance to `completedInstances`. |
| `stopInstance(instanceId)` | Removes an active instance (user cancels). |

---

## Updated: `playSessionStore`

**File:** `src/stores/playSessionStore.js`

### Additions

**1. Active block context ref**
```js
const activeBlockContext = ref(null) // { instanceId, blockId } | null
```

**2. Extended `pendingProgramInfo` shape**
```js
pendingProgramInfo = {
  ablauf: { ...ablaufObject },   // single Ablauf to play (replaces programId lookup)
  rangeId: 'range-uuid',
  instanceId: 'inst-uuid',       // null for Saved/Global Abläufe
  blockId: 'block-uuid',         // null for Saved/Global Abläufe
}
```

**3. `startGroupPlay` sets `activeBlockContext`**
When `instanceId` and `blockId` are present in `pendingProgramInfo`, `startGroupPlay` sets `activeBlockContext` and calls `activeProgramStore.markBlockInProgress`.

**4. `confirmComplete` notifies `activeProgramStore`**
```js
if (activeBlockContext.value) {
  activeProgramStore.markBlockDone(
    activeBlockContext.value.instanceId,
    activeBlockContext.value.blockId,
    buildPlayerResults()
  )
  activeBlockContext.value = null
}
```

### `buildPlayerResults` helper
Extracts per-player scores from `playScore.stepStates` into the `BlockResult.playerResults` shape.

### Removed / simplified internals
- `loadPendingProgram()` — currently looks up the full program from `savedPrograms` by `programId` and stages all its Abläufe into `pendingGroupAblaeufe`. In the new model, `pendingProgramInfo.ablauf` already contains the single Ablauf object. `ShooterPlayPage` can call `setPendingGroupAblaeufe([pendingProgramInfo.ablauf])` directly, making `loadPendingProgram` no longer needed.
- `_pendingProgramId` ref — used today to persist the programId through the group setup modal. No longer needed since the ablauf is passed directly.

---

## Updated: `ShooterFlyoutPanel`

**File:** `src/components/shooter-remote/ShooterFlyoutPanel.vue`

### Expanded Panel (non-recording mode)

Five sections, each hidden when empty for this Range:

| Section | Source | Behavior on play |
|---|---|---|
| **Programme** | `activeProgramStore.getBlocksForRange(rangeId)` | Block consumed on completion |
| **Training** | future | future |
| **Wettkämpfe** | future | future |
| **Gespeicherte Abläufe** | `programStore.savedAblaeufe` filtered by `rangeId`, `ownership:'user'` | Not consumed |
| **Globale Abläufe** | `programStore.savedAblaeufe` filtered by `rangeId`, `ownership:'range'` | Not consumed |

### Programme Block Card
Each card shows:
- Ablauf alias
- Parent program name (smaller, muted)
- Player names
- Status badge (`● pending` / `◑ in progress`)
- Tap → sets `pendingProgramInfo` with `{ ablauf, rangeId, instanceId, blockId }` → navigates to `/remote/:rangeId/play`

### Gespeicherte / Globale Card
- Ablauf name + throw count
- Tap → sets `pendingProgramInfo` with `{ ablauf, rangeId, instanceId: null, blockId: null }` → navigates to `/remote/:rangeId/play`

### Shrunk mode (recording active)
Unchanged from current implementation.

---

## Updated: `ProgramManagementView`

**File:** `src/views/shooter/ProgramManagementView.vue`

### Tab 1 — Programme (templates)
Unchanged structurally. "Starten" button change:
- **Before:** navigates directly to `/remote/:rangeId/play`
- **After:** opens the existing group setup modal → on confirm calls `activeProgramStore.startProgram(template, players)` → switches to "Aktive Programme" tab

### Tab 2 — Aktive Programme
Reads from `activeProgramStore.activeInstances` instead of `playSessionStore.activeSessions`.

Each card shows:
- Program name + type badge
- Player names
- Block list with Range name, Ablauf alias, and status per block
- Progress bar (X/Y Blöcke)
- "Stoppen" button → `activeProgramStore.stopInstance(instanceId)`

### Tab 3 — Abgeschlossene Programme (new)
Reads from `activeProgramStore.completedInstances`. Read-only history cards showing:
- Program name, completion date
- Per-player total scores
- No actions (foundation for future stats UI)

---

## Updated: `ShooterPlayPage`

**File:** `src/views/shooter/ShooterPlayPage.vue`

Minor change only: `startGroupPlay` receives `instanceId` and `blockId` from `pendingProgramInfo` and forwards them so `playSessionStore` can set `activeBlockContext`. No structural changes to the play UI.

---

## Launch Paths

| Source | Sets pendingProgramInfo | instanceId/blockId |
|---|---|---|
| FlyoutPanel → Programme block | FlyoutPanel | set |
| FlyoutPanel → Gespeicherte Ablauf | FlyoutPanel | null |
| FlyoutPanel → Globale Ablauf | FlyoutPanel | null |
| ProgramManagementView → Starten | Does NOT navigate to play; creates instance | n/a |

---

## Files Changed

| File | Change type |
|---|---|
| `src/stores/activeProgramStore.js` | **New** |
| `src/stores/playSessionStore.js` | Extended (activeBlockContext, pendingProgramInfo shape, confirmComplete, buildPlayerResults) |
| `src/components/shooter-remote/ShooterFlyoutPanel.vue` | Redesigned (5 grouped sections) |
| `src/views/shooter/ProgramManagementView.vue` | Updated (Tab 2 source, new Tab 3, Starten flow) |
| `src/views/shooter/ShooterPlayPage.vue` | Minor (passes block context to startGroupPlay) |

---

## Out of Scope (future)

- Training and Wettkampf instance types (sections reserved in FlyoutPanel)
- Registered user linking to instances for personal stats
- Stats UI on Abgeschlossene Programme
- Removing unused `playSessionStore.activeSessions` logic (cleanup pass after migration)
