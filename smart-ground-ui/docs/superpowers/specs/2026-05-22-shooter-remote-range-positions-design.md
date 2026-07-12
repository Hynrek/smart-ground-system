# Design: ShooterRemoteView — RangePosition-Based Grid

**Date:** 2026-05-22  
**Status:** Approved

---

## Goal

Replace the device-centric grid in `ShooterRemoteView` with a `RangePosition`-based grid.
Each position button represents a slot on the range; the slot may or may not have a device assigned.

---

## Section 1: Data Model

**Before:** `deviceStore.devices` filtered by `rangeId` + `groupId`  
**After:** `rangeStore.positions[rangeId]` (already loaded via `rangeStore.loadPositions`)

- The group dropdown, `deviceGroups` computed, `selectedGroupId`, and `initDefaultGroup` are removed.
- The view renders all positions of the range as a flat grid.
- No grouping concept — all positions shown regardless of device type.

---

## Section 2: Button Grid

| Attribute | Before | After |
|---|---|---|
| Letter | `String.fromCharCode(65 + index)` | `position.label` (e.g. "A") |
| Subtitle | `device.alias` | `position.device?.alias \|\| 'Kein Gerät'` |
| Section title | "X Geräte" | "X Positionen" |
| Disabled when | `device.blocked`, unhealthy, locked, not reserved, firing | Same + `!position.device` |
| Fire state keys | `device.id` | `position.id` |
| Pair pending | stores `device.id` | stores `position.id` |

Empty positions (no device) render as disabled tiles with chip label "Kein Gerät".

---

## Section 3: Fire Path

New API function in `rangePositionApi.js`:
```javascript
export async function sendPositionCommand(rangeId, positionId) {
  return apiFetch(`/ranges/${rangeId}/positions/${positionId}/command`, { method: 'POST' });
}
```

Internal fire helpers renamed:
- `fireSingleDevice(id)` → `fireSinglePosition(posId)`
- `firePairDevices(id1, id2)` → `firePairPositions(posId1, posId2)`
- `fireRaffaleDevice(id)` → `fireRaffalePosition(posId)`

`handleDeviceTap` → `handlePositionTap(position, i)` receives the full `position` object.

---

## Section 4: Recording Mode

### `programStore.addStep` signature
`addStep(positionId, position, positionLabel)`

- `positionId` replaces `deviceId` as the recording-flash key and step field
- `alias` = `position.device?.alias ?? position.label`
- `letter` = `positionLabel` (the position's label, e.g. "A")
- Step shape changes:
  - Solo/Raffale: `{ ..., positionId, letter, alias }` (was `deviceId`)
  - Pair/a.Schuss: `{ ..., positionId1, positionId2, letter1, letter2, alias1, alias2 }` (was `deviceId1/2`)

### `ShooterFlyoutPanel.vue`
- `getStepLabel(step)` reads stored `step.letter` / `step.letter1` / `step.letter2` directly — no index lookup
- `getLetterForDevice` helper removed
- `rangeDevices` and `activeDevices` computeds in flyout removed (only used for the now-deleted lookup)

---

## Files Changed

| File | Change |
|---|---|
| `src/services/rangePositionApi.js` | Add `sendPositionCommand` |
| `src/views/shooter/ShooterRemoteView.vue` | Replace device logic with position logic |
| `src/stores/programStore.js` | Update `addStep` to use `positionId` |
| `src/components/shooter-remote/ShooterFlyoutPanel.vue` | Simplify `getStepLabel`, remove device lookup helpers |

---

## Out of Scope

- Playback of saved Abläufe that contain `deviceId` steps (backward compat not needed — no production release)
- ShooterPlayPage step execution (uses `step.deviceId` for playback — separate task)
