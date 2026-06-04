# Competition Flyout — Active Wettkampf View

**Date:** 2026-06-04  
**Status:** Approved  
**Scope:** ShooterFlyoutPanel + new CompetitionFlyoutContent component

---

## Problem

When a Wettkampf is active the shooter flyout shows one card per Rotte under "Offene Wettkämpfe". This gives no overview of Passen progress, no per-Serie Rotten status, and no score ranking. Operators and shooters have no at-a-glance picture of where the competition stands.

---

## Goal

Restructure the flyout so that, when a competition context is active, it shows:

1. A **Passen progress bar** — step-by-step status of every Passe in the competition.
2. A **Serien view** — one card per Serie in the active Passe, each listing every Rotte's current status.
3. A **Rangliste tab** — individual player scores aggregated across all completed blocks.

The admin `ActiveCompetitionPanel` is **not** changed in this iteration.

---

## Architecture

### Component boundary

**`ShooterFlyoutPanel.vue`** (modified)  
Adds one computed property `competitionInstance`: looks up `shooterRemoteStore.competitionContext.instanceId` in `activePasseStore.activeInstances`, filtered to `type === 'competition'`. When `competitionInstance` is non-null the scrollable content area renders `<CompetitionFlyoutContent :instance="competitionInstance" />` instead of the existing non-competition sections. All panel chrome (handle, slide animation, overlay) remains unchanged.

**`src/components/shooter-remote/CompetitionFlyoutContent.vue`** (new)  
Receives `instance` as a required prop. Owns all competition-specific UI: tab bar, Passen progress bar, Serien cards, and leaderboard. No store writes — read-only derived view.

---

## Data Flow

```
shooterRemoteStore.competitionContext.instanceId
        │
        ▼
activePasseStore.activeInstances  (type === 'competition')
        │
        ▼
CompetitionFlyoutContent (prop: instance)
        │
        ├─ pasenProgress[]  ← derived from rotten[0].phases + all rotten statuses
        ├─ activePhaseIndex ← lowest phase index not fully done by all rotten
        ├─ serieCards[]     ← instance.rotten[*].phases[activePhaseIndex].blocks[i]
        └─ leaderboard[]    ← aggregated block.result.playerResults across all done blocks
```

No new API calls. All data lives in `activePasseStore.activeInstances` (in-memory, set at competition start).

---

## Passen Progress Bar

Rendered as a horizontal stepper above the tab bar. Derived from `rotten[0].phases` (all rotten share the same phase structure). Status per phase index `i`:

| Condition | Status label | Color |
|---|---|---|
| All rotten have `phases[i].status === 'done'` | Fertig | Green |
| At least one rotte has `currentPhaseIndex === i` | Aktiv | Amber |
| No rotte has reached phase `i` yet | Offen | Grey |

**Active phase index** = lowest `i` where not all rotten have `phases[i].status === 'done'`. This index drives the Serien view.

---

## Serien Tab (default)

One card per block in `rotten[0].phases[activePhaseIndex].blocks`, in order. Each card:

- **Header:** `block.serieAlias`
- **Rows:** one row per Rotte showing rotte name + status chip

Rotte–block status mapping for phase `activePhaseIndex`, block `j`:

| `rotte.phases[activePhaseIndex].blocks[j].status` | Label | Style |
|---|---|---|
| `'done'` | ✓ Fertig | Green |
| `'in_progress'` | ◑ Aktiv | Amber |
| `'pending'` | ○ Offen | Grey |

---

## Rangliste Tab

Tab bar: **Serien** (default) · **Rangliste**

Aggregation: iterate all rotten → all phases → all blocks where `status === 'done'` → accumulate `block.result.playerResults` per `displayName`. Each entry:

```
{ displayName, totalPoints, maxPoints }
```

Sorted descending by `totalPoints`. Display: rank number, player name, `totalPoints / maxPoints`.

Updates automatically as blocks are marked done (reactive to `activePasseStore.activeInstances`).

---

## Files Changed

| File | Change |
|---|---|
| `src/components/shooter-remote/ShooterFlyoutPanel.vue` | Add `competitionInstance` computed; conditionally render `CompetitionFlyoutContent` |
| `src/components/shooter-remote/CompetitionFlyoutContent.vue` | New component |

No store changes. No router changes. No new dependencies.

---

## Out of Scope

- Admin `ActiveCompetitionPanel` changes
- Writing scores back to the backend
- Real-time SSE-driven score updates (leaderboard reflects last completed block only)
- Multi-competition support (one active instance per flyout context)
