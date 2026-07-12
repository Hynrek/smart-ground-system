# Competition Management View — Design Plan

> **Status:** Historisch (Stand ~Mai 2026) — Plan aus der Prä-Hub/Node-Welt; die Wettkampf-Features sind seither weitgehend umgesetzt (siehe `smart-ground-ui/CLAUDE.md`). Für Architektur und Transport gilt die [Hub/Node-Spec](superpowers/specs/2026-07-10-hub-node-architecture-design.md).

## 1. Context & Terminology

| Term | Meaning |
|---|---|
| **Passe** | One shooting round. Consists of 1–n **Serien** (already implemented). |
| **Serie** | One block of steps on a specific Range. The existing play mechanic (ShooterPlayPage) is built around Serien. |
| **Training** | 1 group of players running through 1–n Passen sequentially, solo use case. |
| **Competition** | N **Rotten** each running through the same ordered list of Passen. |
| **Rotte** | A named group of shooters (`ShooterGroup` in backend). All Rotten shoot the same Passen sequence, but progress independently. |

**Core constraint:** only one Rotte per Range may be in an active play state at a time.  
**Core difference from Training:** concurrency — multiple Rotten are in flight simultaneously, each on their own Range.

---

## 2. Data Model

### 2.1 Instance Shape (local store, mirrors Training)

The `activePasseStore` already manages Training instances with this shape:

```
TrainingInstance
  type: 'training'
  players: []
  currentPhaseIndex: number
  phases: [
    { phaseIndex, passeId, passeName, status, blocks: [SerieBlock] }
  ]
```

A **CompetitionInstance** adds a `rotten` array, where each Rotte is an independent sub-instance tracking its own phase progress:

```
CompetitionInstance
  type: 'competition'
  instanceId: UUID
  sessionId: UUID          ← links to backend LiveSession
  name: string
  passen: [PasseTemplate]  ← ordered, shared sequence
  rotten: [
    {
      rotteId: UUID
      name: string           ← "Rotte 1", "Rotte A", etc.
      players: [Player]
      status: 'waiting' | 'active' | 'paused' | 'done'
      assignedRangeId: UUID | null
      currentPhaseIndex: number
      phases: [             ← same structure as Training phases
        { phaseIndex, passeId, passeName, status, blocks: [SerieBlock] }
      ]
    }
  ]
  startedAt: number
  completedAt: number | null
```

### 2.2 Key rules derived from the data model

- A Rotte is `active` when it has an `assignedRangeId` and its current phase `status === 'active'`.
- A Rotte is `waiting` when it has no Range or is queued.
- A Rotte is `done` when all its phases are `'done'`.
- A Range is considered "occupied" when any Rotte has that `assignedRangeId` and `status === 'active'`.

---

## 3. View Architecture

The Competition Management View is built from **three tabs**, mirroring the Training view structure:

```
/shooter/competition   →  CompetitionManagementView.vue
                          ├── Tab 1: Wettkämpfe (templates)
                          ├── Tab 2: Aktiv
                          └── Tab 3: Abgeschlossen
```

Active competitions also push to an **operator view** accessible from the ShooterRemoteView — the Range panel gains a "Rotte" column alongside the existing Serie blocks.

---

## 4. Tab 1 — Wettkämpfe (Templates)

Mostly reuses the Training template UI. Key differences:

- Card meta shows: `N Passen · M Rotten vorgesehen · X Würfe`
- **"Starten" modal** is replaced by a **Rotten-Setup flow** (see § 6).

No new backend entities are needed. A competition template is just a `savedTraining` with `type: 'competition'` and a `rottCount` hint (how many Rotten the organiser intends to run).

---

## 5. Tab 2 — Aktive Wettkämpfe

### 5.1 Overview card per Competition

```
┌──────────────────────────────────────────┐
│  Wettkampf "Herbstcup 2026"              │
│  3 Rotten · Passe 2 von 4 laufend        │
│                                          │
│  ● Rotte A  Range 1  Passe 2  [aktiv]    │
│  ○ Rotte B  Range 2  Passe 1  [aktiv]    │
│  ○ Rotte C  —        Passe 1  [wartend]  │
│                                          │
│  [ Verwalten ]  [ Stoppen ]              │
└──────────────────────────────────────────┘
```

Tapping **Verwalten** opens the Competition Live View (§ 5.2).

### 5.2 Competition Live View (`CompetitionLiveView.vue` — enhanced)

Two-panel layout:

```
┌────────────────────┬──────────────────────┐
│  BEREICHE          │  RANGLISTE (live)     │
│                    │                      │
│  [Range 1 panel]   │  1. Hans M.  47 Pts  │
│  [Range 2 panel]   │  2. Peter K. 45 Pts  │
│  [Range 3 panel]   │  3. ...              │
└────────────────────┴──────────────────────┘
```

#### Range Panel (one per available Range)

```
┌─────────────────────────────────────────┐
│  Range 1                                │
│  ─────────────────────────────────────  │
│  AKTIV: Rotte A                         │
│  Passe 2 · Serie 1/3 ●○○               │
│  Hans M. · Markus F. · …               │
│  [ ⏸ Pause ]  [ ⏹ Abbrechen ]         │
│                                         │
│  WARTESCHLANGE                          │
│  Rotte C  (Passe 1)  [ Aktivieren ]    │
└─────────────────────────────────────────┘
```

- **Aktivieren** assigns the waiting Rotte to this Range and starts its current Passe.
- If Range is free and a Rotte is waiting, the panel shows a prominent "Start" CTA.
- The Serie progress dots (`●○○`) mirror the existing block-status dots in Training.

When the operator taps a Range with an active Rotte, it deep-links to the existing **ShooterRemoteView** / **ShooterPlayPage** for that Rotte's current block — no new play mechanic needed.

---

## 6. Start / Rotten-Setup Flow

Triggered from the "Starten" button on a template card.

**Step 1 — Rotten definieren**

```
┌──────────────────────────────────────┐
│  Wettkampf starten                   │
│  "Herbstcup 2026"                    │
│  ─────────────────────────────────   │
│  Anzahl Rotten: [ - ] 3 [ + ]        │
│                                      │
│  Rotte 1:  Hans M.  Markus F.  [+]  │
│  Rotte 2:  Peter K.            [+]  │
│  Rotte 3:  (leer)              [+]  │
│                                      │
│  [ Abbrechen ]    [ Starten →  ]    │
└──────────────────────────────────────┘
```

- Operator can add/remove Rotten and drag shooters between them (or type names like Training).
- Shooter names are free text (guests) or picked from registered users — same pattern as Training modal.

**Step 2 — Confirmation**

Shows a summary: `3 Rotten · 4 Passen · 12 Serien total`. One tap to confirm.

---

## 7. Store Changes

### 7.1 Extend `activePasseStore.js`

Add a `startCompetition(template, rotten)` action alongside `startTraining`. The competition instance uses the same `phases` array per Rotte, so `markBlockDone` can be reused with an extra `rotteId` parameter.

```js
// New action
startCompetition(template, rotten) → CompetitionInstance

// Extended actions (rotteId optional; null = Training behaviour)
markBlockInProgress(instanceId, blockId, rotteId?)
markBlockDone(instanceId, blockId, playerResults, rotteId?)
```

`getBlocksForRange(rangeId)` already returns blocks across all instances — extend it to include competition Rotte blocks tagged with `instanceType: 'competition'` and a `rotteName` field.

### 7.2 `competitionStore.js` (already exists)

Already tracks `selectedRange`, `groupsAtRange`, `rangeQueue`. Wire these to the new local instance state so the Range Panel reads from a single source.

---

## 8. Scoreboard

Reuse `LiveScoreboard.vue`. For local-only competitions (before backend sync), compute rankings from `activePasseStore` block results:

```
for each Rotte:
  for each completedBlock:
    sum playerResults[].totalPoints per player
rank players globally by total points descending
```

When a `sessionId` is present (backend-linked session), fall back to the existing `competitionService.getLeaderboard(sessionId)` API call.

---

## 9. New Files

| File | Role |
|---|---|
| `src/views/shooter/CompetitionManagementView.vue` | Tab shell (mirrors TrainingManagementView) |
| `src/views/competition/CompetitionLiveView.vue` | Enhanced (replace skeleton) |
| `src/components/competition/RangePanelCard.vue` | Per-Range operator card |
| `src/components/competition/RotteProgressRow.vue` | Compact Rotte status + progress dots |
| `src/components/competition/RotteSetupModal.vue` | Rotten-Setup flow (steps 1 & 2) |

### Modified files

| File | Change |
|---|---|
| `src/stores/activePasseStore.js` | Add `startCompetition`, extend block actions with `rotteId` |
| `src/stores/competitionStore.js` | Wire local instance state into range queue |
| `src/views/shooter/ShooterRemoteView.vue` | Accept optional `rotteId` query param so Range Panel can deep-link |
| `src/router/index.js` | Add route for `CompetitionManagementView` |

---

## 10. Implementation Order

1. **`activePasseStore` — competition instance** — data model foundation; everything else reads from it.
2. **`RotteSetupModal.vue`** — lets the operator create a competition instance.
3. **`CompetitionManagementView.vue` Tab 1 + Tab 2 overview** — template list + active card.
4. **`RangePanelCard.vue` + `RotteProgressRow.vue`** — operator controls in the Live View.
5. **`CompetitionLiveView.vue` layout** — wire Range panels + scoreboard side by side.
6. **Scoreboard aggregation** — local ranking from block results.
7. **Deep-link into ShooterRemoteView** — reuse existing play mechanic for active blocks.

---

## 11. What We Are NOT Building (Yet)

- Backend API sync for local competition instances (the same pattern as Training — local state only for now).
- Range auto-assignment (operator manually assigns Rotten to Ranges).
- Bracket / elimination tournament logic (already exists separately in `BracketController`).
- OTA or device config changes during a competition.
