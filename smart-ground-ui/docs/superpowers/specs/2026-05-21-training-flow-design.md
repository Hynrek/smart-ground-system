# Training Flow — Design Spec
**Date:** 2026-05-21  
**Status:** Approved

---

## Overview

A **Training** is a higher-level session type that consists of 1–n saved Programmes played **sequentially**. Programme 1's Blocks appear on the Ranges; only after all are completed do Programme 2's Blocks unlock, and so on. The play mechanics (Blocks → FlyoutPanel → ShooterPlayPage) are identical to the existing Programme flow.

---

## Instance Type

Training instances use `type: 'training'` in the shared `activeProgramStore`. This matches the reserved type value established in the Range-Centered Block Play spec.

---

## Data Model

### Training Template (`localStorage`)

**Storage key:** `{username}_training_{n}`

```js
{
  trainingName: 'Training Woche 1',
  programmes: [
    { id: 'prog-key-1', name: 'Aufwärmen', ablaeufe: [...] },
    { id: 'prog-key-2', name: 'Hauptteil', ablaeufe: [...] },
    { id: 'prog-key-3', name: 'Cool-down', ablaeufe: [...] },
  ]
}
```

Programmes are **snapshotted on creation** — the full Ablauf data is embedded at Training creation time. Later edits to saved Programmes do not affect running or saved Training instances.

---

### Training Active Instance

Stored in `sg_active_program_instances` alongside Programme instances (same key, distinguished by `type`).

```js
{
  instanceId: 'uuid',
  type: 'training',
  templateId: 'training-key-1',
  templateName: 'Training Woche 1',
  players: [{ id: 'uuid', displayName: 'Schütze 1', type: 'guest' }],
  startedAt: 1234567890,
  currentPhaseIndex: 0,
  phases: [
    {
      phaseIndex: 0,
      programmeId: 'prog-key-1',
      programmeName: 'Aufwärmen',
      status: 'active',           // 'pending' | 'active' | 'done'
      blocks: [
        {
          blockId: 'uuid',
          ablaufId: 'abl-uuid',
          ablaufAlias: 'Ablauf 1',
          rangeId: 'range-uuid',
          rangeName: 'Platz 1',
          steps: [...],
          status: 'pending',      // 'pending' | 'in_progress' | 'done'
          completedAt: null,
          result: null,
        }
      ]
    },
    {
      phaseIndex: 1,
      programmeName: 'Hauptteil',
      status: 'pending',
      blocks: [...]
    },
    {
      phaseIndex: 2,
      programmeName: 'Cool-down',
      status: 'pending',
      blocks: [...]
    }
  ]
}
```

**Phase advancement rule:** When all blocks in `phases[currentPhaseIndex]` are `'done'`, `currentPhaseIndex` increments and the next phase's `status` becomes `'active'`. When the last phase completes, the instance moves to `completedInstances`.

---

### Completed Training Instance

Same shape as the active instance, with a top-level `completedAt` timestamp and all phase statuses `'done'`. Stored in `sg_completed_program_instances`.

---

## Store Changes

### `activeProgramStore` — extensions to existing store

**New action: `startTraining(template, players)`**
- Creates a Training instance from the template snapshot
- Sets `phases[0].status = 'active'`, all others `'pending'`
- Pushes to `activeInstances` and persists to localStorage

**Extended: `getBlocksForRange(rangeId)`**
- Already used by FlyoutPanel for Programme blocks
- Extended to also walk Training instances, yielding only blocks from `phases[currentPhaseIndex]` (the active phase)
- Blocks in `'pending'` or `'done'` phases are never returned
- Each returned block gets the Training context attached: `{ ...block, instanceId, templateName, programmeName, players, instanceType: 'training' }`

**Extended: `markBlockDone(instanceId, blockId, playerResults)`**
- Existing Programme branch unchanged
- New Training branch: after marking the block done, checks if all blocks in the current phase are `'done'`
  - If yes and there is a next phase: increments `currentPhaseIndex`, sets next phase `status = 'active'`, persists
  - If yes and this was the last phase: moves instance to `completedInstances`, persists both lists

No changes needed to `playSessionStore`, `activeBlockContext`, or `ShooterPlayPage` — block shape is identical.

---

### `programStore` — Training template persistence

New state and actions, mirroring the existing Programme pattern:

| Addition | Description |
|---|---|
| `savedTrainings` ref | Array of loaded Training templates |
| `loadTrainingsFromStorage()` | Reads all `{username}_training_{n}` keys from localStorage |
| `createTraining(name, programmes)` | Snapshots selected programmes, writes to localStorage, pushes to `savedTrainings` |
| `deleteTraining(id)` | Removes from localStorage and `savedTrainings` |
| `renameTraining(id, name)` | Updates name in memory and localStorage |

`loadTrainingsFromStorage()` is called at store init (same as `loadProgramsFromStorage`).

---

## New View: `TrainingManagementView.vue`

**Route:** `/training`  
**Layout:** `ShooterLayout`  
**Entry point:** New "Training" button in `ShooterHomeView` alongside the existing "Programme" button.

### Three tabs

#### Tab 1 — Trainings (templates)

- List of saved Training templates
- Each card: name, badge ("3 Programme · 45 Würfe total"), expand/collapse
- Expanded detail: ordered Programme list with Ablauf chips per Programme
- Actions per card: **Starten**, Umbenennen, Löschen
- "Starten" opens a player setup modal (same as Programme modal: add shooters by name → confirm → calls `activeProgramStore.startTraining(template, players)` → switches to Aktive Trainings tab)
- "Neues Training" button opens an inline creation panel:
  - Name input
  - Pick from `programStore.savedPrograms` — selected in sequence (selection order = play order)
  - Preview: ordered Programme list with Ablauf count per Programme
  - Erstellen / Abbrechen
  - Disabled if no saved Programmes exist

#### Tab 2 — Aktive Trainings

- Reads from `activeProgramStore.activeInstances` filtered by `type === 'training'`
- Each card:
  - Training name + player names
  - Phase progress line: "Phase 2 von 3 — Hauptteil"
  - Progress bar: phases done / total phases
  - Block status list for the **current phase only** (Range name, Ablauf alias, status dot)
  - Stoppen button → `activeProgramStore.stopInstance(instanceId)` with confirm dialog

#### Tab 3 — Abgeschlossen

- Reads from `activeProgramStore.completedInstances` filtered by `type === 'training'`
- Read-only cards: Training name, completion date, per-player total scores across all phases
- Expandable: per-player breakdown by phase → per-block scores

---

## FlyoutPanel — "Offene Trainings" section

The existing placeholder section in `ShooterFlyoutPanel.vue` is populated with:

```js
const trainingBlocks = computed(() =>
  activeProgramStore.getBlocksForRange(props.rangeId)
    .filter(b => b.instanceType === 'training')
)
```

Each block card shows:
- Ablauf alias (primary)
- Training name + phase name (muted, secondary line)
- Player names
- Status badge (● pending / ◑ in_progress)

Tap → sets `pendingProgramInfo` with `{ ablauf, rangeId, instanceId, blockId }` → navigates to `/remote/:rangeId/play`.

Play flow and scoring are identical to Programme blocks. `markBlockDone` receives the same `playerResults` shape and handles phase advancement internally.

---

## FlyoutPanel — "Programme" section adjustment

The existing `programmeBlocks` computed also needs to filter for `instanceType !== 'training'` to avoid double-listing blocks when Training and Programme instances coexist.

```js
const programmeBlocks = computed(() =>
  activeProgramStore.getBlocksForRange(props.rangeId)
    .filter(b => b.instanceType !== 'training')
)
```

---

## Router

Add to `src/router/index.js`:

```js
{
  path: '/training',
  name: 'TrainingManagement',
  component: () => import('@/views/shooter/TrainingManagementView.vue'),
  meta: { layout: 'shooter', requiresAuth: true }
}
```

---

## ShooterHomeView

Add a "Training" navigation button alongside the existing "Programme" button, routing to `/training`.

---

## Files Changed

| File | Change type |
|---|---|
| `src/stores/activeProgramStore.js` | Extended (`startTraining`, extended `getBlocksForRange` and `markBlockDone`) |
| `src/stores/programStore.js` | Extended (`savedTrainings`, `loadTrainingsFromStorage`, `createTraining`, `deleteTraining`, `renameTraining`) |
| `src/views/shooter/TrainingManagementView.vue` | **New** |
| `src/components/shooter-remote/ShooterFlyoutPanel.vue` | Updated ("Offene Trainings" section populated, `programmeBlocks` filter adjusted) |
| `src/views/shooter/ShooterHomeView.vue` | Updated (add Training navigation button) |
| `src/router/index.js` | Updated (add `/training` route) |

---

## Out of Scope

- Backend persistence (Training templates and instances remain localStorage-only, consistent with Programmes)
- Registered user linking for career stats (guest players only for now)
- Stats UI on completed Trainings beyond basic score display
- Drag-to-reorder Programmes in Training creation (selection order determines play order)
