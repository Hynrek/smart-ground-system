# Competition Rotten in ShooterFlyoutPanel

**Date:** 2026-05-29
**Status:** Approved

## Problem

When a Wettkampf (competition) is started, shooters had to navigate to a separate live-management page (`/wettkampf/live/:instanceId`) to see active Rotten and assign them to ranges. This is an unnecessary extra step.

## Goal

When a Wettkampf is started, each Rotte appears automatically as a separate card in the ShooterFlyoutPanel under a "Wettkämpfe" section — on any range's remote view — without any manual assignment step.

## Design

### Section in the flyout

The existing "Offene Wettkämpfe" section (currently `competitionSerien = []`) will be populated with one card per Rotte across all active competition instances. Cards are not grouped per competition; each Rotte is its own card.

Each card displays:
- Competition name (small, above)
- Rotte name · current Passe name
- Player names

Rotten with `status === 'done'` are excluded.

### Interaction

Tapping a Rotte card:
1. Calls `activePasseStore.assignRotteToRange(instanceId, rotteId, currentRangeId)` — implicitly assigns the current range to this Rotte.
2. Sets `playStore.pendingPasseInfo` for the first pending block of that Rotte's current phase.
3. Navigates to `/remote/:rangeId/play`.

### Data changes

**`activePasseStore.js`** — new method:
```js
getActiveCompetitionRotten()
```
Returns all Rotten (across all active `type === 'competition'` instances) where `rotte.status !== 'done'`. No range filtering — all Rotten appear in every range's flyout.

Each entry contains: `{ instanceId, instanceName, rotteId, rotteName, passeName, players, blocks, phaseIndex }`.

**`ShooterFlyoutPanel.vue`** — two changes:
1. `competitionSerien` computed uses `activePasseStore.getActiveCompetitionRotten()` instead of returning `[]`.
2. `passenBlocks` filters out competition-type blocks: `b.instanceType !== 'training' && b.instanceType !== 'competition'` — prevents double-display.

A new `playCompetitionRotte(item)` handler replaces the existing expand/solo/group buttons for competition cards.

## Files changed

| File | Change |
|---|---|
| `src/stores/activePasseStore.js` | Add `getActiveCompetitionRotten()` method |
| `src/components/shooter-remote/ShooterFlyoutPanel.vue` | Populate `competitionSerien`, fix `passenBlocks` filter, add Rotte card template + handler |

## Out of scope

- The existing CompetitionManagementView (create, start, view completed) is unchanged.
- The RotteSetupModal and competition start flow are unchanged.
- The `/wettkampf/live/:instanceId` route remains for now (not removed).
