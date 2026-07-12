# Competition Flyout & Admin Panel — Active Wettkampf View

**Date:** 2026-06-04  
**Status:** Approved  
**Scope:** ShooterFlyoutPanel + new CompetitionFlyoutContent component + ActiveCompetitionPanel read-only redesign

---

## Problem

When a Wettkampf is active:
- The shooter flyout shows one card per Rotte under "Offene Wettkämpfe" — no Passen progress, no per-Serie status, no score ranking.
- The admin `ActiveCompetitionPanel` shows interactive rotten cards with range-assignment dropdowns that serve no purpose during execution (scoring and Serie completion happen in the ShooterRemoteView play mode).

Neither view gives operators or shooters an at-a-glance picture of where the competition stands.

---

## Goal

1. **Shooter flyout**: when a competition context is active, show a Passen progress bar, a per-Serie Rotten status view, and an individual-player leaderboard.
2. **Admin panel**: replace the current interactive rotten cards with the same read-only progress view. The only action retained is "Wettkampf abbrechen".

---

## Architecture

### Shared composable

`src/composables/useCompetitionProgress.js` (new) — accepts a competition `instance` ref and exposes:

- `passenProgress[]` — per-phase status (fertig / aktiv / offen)
- `activePhaseIndex` — lowest phase index not fully done by all rotten
- `serieCards[]` — blocks for the active phase with per-rotte status
- `leaderboard[]` — players sorted by aggregated totalPoints

Both the flyout component and the admin panel consume this composable, keeping derivation logic in one place.

### Component boundary

**`ShooterFlyoutPanel.vue`** (modified)  
Adds one computed `competitionInstance`: looks up `shooterRemoteStore.competitionContext.instanceId` in `activePasseStore.activeInstances` filtered to `type === 'competition'`. When non-null, the scrollable content area renders `<CompetitionFlyoutContent :instance="competitionInstance" />` instead of the existing non-competition sections. Panel chrome (handle, slide animation, overlay) is untouched.

**`src/components/shooter-remote/CompetitionFlyoutContent.vue`** (new)  
Receives `instance` prop. Uses `useCompetitionProgress`. Renders: Passen stepper, tab bar (Serien / Rangliste), Serien cards, leaderboard. No store writes.

**`src/components/competition/ActiveCompetitionPanel.vue`** (modified)  
Replaces the current interactive rotten cards (range-assignment dropdowns, status badges) with the same read-only Passen stepper + Serien cards + Rangliste view via `useCompetitionProgress`. The only remaining action is the existing "Wettkampf abbrechen" button.

---

## Data Flow

```
activePasseStore.activeInstances  (type === 'competition')
        │
        ├─── ShooterFlyoutPanel ──▶ CompetitionFlyoutContent
        │         (via competitionContext.instanceId)
        │
        └─── ActiveCompetitionPanel
                    │
              useCompetitionProgress(instance)
                    │
                    ├─ passenProgress[]
                    ├─ activePhaseIndex
                    ├─ serieCards[]
                    └─ leaderboard[]
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
| `src/composables/useCompetitionProgress.js` | New composable — shared derivation logic |
| `src/components/shooter-remote/ShooterFlyoutPanel.vue` | Add `competitionInstance` computed; conditionally render `CompetitionFlyoutContent` |
| `src/components/shooter-remote/CompetitionFlyoutContent.vue` | New component — flyout competition view |
| `src/components/competition/ActiveCompetitionPanel.vue` | Replace interactive rotten cards with read-only progress view; keep only abort button |

No store changes. No router changes. No new dependencies.

---

## Out of Scope

- Writing scores back to the backend
- Real-time SSE-driven score updates (leaderboard reflects last completed block only)
- Multi-competition support (one active instance per flyout context)
- Any admin interactions with Rotten, Serien, or Passen during an active Wettkampf
