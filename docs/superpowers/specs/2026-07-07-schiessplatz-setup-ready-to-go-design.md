# Admin Testing: "Schiessplatz Setup" ready-to-go extension — Design

**Date:** 2026-07-07
**Status:** Approved
**Scope:** Backend (`smart-ground-backend`) + Frontend (`smart-ground-ui`)

## Motivation

The existing admin Testing panel's "Schiessplatz Setup" action (`POST
/api/testing/ranges/seed`) only creates the 4 standard ranges (Vorderlader,
Trapstand, Rollhase, Kippreh) with no positions, boxes, or devices — an admin
still has to manually create 8 positions per range, create SmartBoxes, create
devices, and wire everything up by hand before the range is usable for
testing. This extends the same action so one click produces a fully wired,
"ready to go" test setup: 4 ranges × 8 positions, 8 SmartBoxes × 4 devices
(32 devices total), each device assigned to exactly one position.

## Approach

Extend `TestDataService.seedRanges()` and the `SeedRangesResponse` schema in
place — no new endpoint. The existing per-range `created` idempotency flag is
kept; new per-range counts (`positionsCreated`, `boxesCreated`,
`devicesAssigned`) report what else happened.

Rejected alternative: a separate "full setup" endpoint/button distinct from
range seeding. Rejected because the user explicitly asked to extend the
existing Schiessplatz Setup function rather than add a new one, and because
range creation without positions/devices is not a useful intermediate state
worth keeping as a separate action.

## Backend (`ch.jp.shooting`)

### Per-range build logic (`TestDataService.seedRanges`)

For each of the 4 standard range names:

1. Find-or-create the `Range` (unchanged) → `created` flag.
2. Read `positionRepository.countByRangeId(range.getId())`:
   - **0** → fresh range, proceed with the full build (steps 3–5).
   - **≥ 1** (whether `<8` from a manually-modified range, or `≥8` from a
     prior run) → already wired or manually altered; skip steps 3–5 for this
     range. Report `positionsCreated=0, boxesCreated=0, devicesAssigned=0`.
3. Create 8 `RangePosition`s labeled `A`–`H`, `sortOrder` 0–7.
4. Create 2 `SmartBox`es, alias `"<RangeName> Box 1"` / `"<RangeName> Box 2"`
   — same construction as `createMockSmartBox` (status `OFFLINE`, firmware
   `0.6`/`xiao-esp32s3`, random locally-administered MAC via the existing
   `generateUniqueMac()`). Each box gets 4 `Werfer` devices (same
   `DeviceTypeGroup`/`DeviceType` resolution as `createMockSmartBox`, factored
   into a shared private helper). Device alias = the position label it will
   be assigned to (`"Werfer A"`…`"Werfer H"`), not `"Werfer 1..4"`.
5. Assign 1:1 in order: Box 1's 4 devices → positions A–D, Box 2's 4 devices
   → positions E–H. Wiring is direct entity assignment (`position.setDevice`,
   `device.setRangePosition`, `device.setRange`) — the same linkage
   `RangePositionService.assignDevice` performs, but without the
   lock/reservation/occupied checks (positions are brand-new and empty, so
   those checks cannot fail here).

All of this runs inside the existing `@Transactional seedRanges()` method —
one range's build failing (e.g. missing seeded `FirmwareConfig`) rolls back
the whole call, consistent with today's behavior (`IllegalStateException` on
missing seed data).

### Response shape

`SeededRange` gains three required integer fields:

```yaml
SeededRange:
  required: [id, name, created, positionsCreated, boxesCreated, devicesAssigned]
  properties:
    id: {type: string, format: uuid}
    name: {type: string}
    created: {type: boolean}
    positionsCreated: {type: integer, format: int32}
    boxesCreated: {type: integer, format: int32}
    devicesAssigned: {type: integer, format: int32}
```

`SeedRangesResponse` is unchanged (`{ ranges: [SeededRange] }`).
`POST /api/testing/ranges/seed` keeps its operationId, path, and "no request
body" shape.

### New/changed classes

- `service/TestDataService.java` — `seedRanges()` rewritten per above; extract
  a private `createWerferDevice(SmartBox, DeviceTypeGroup, DeviceType,
  String alias)` helper shared between `seedRanges()` and
  `createMockSmartBox()`; extract firmware/group/deviceType resolution into a
  private `resolveWerferDeviceType()` helper shared the same way.
- `TestDataService.SeededRange` record gains the three new fields.
- No new exceptions (missing seed data still throws the existing
  `IllegalStateException`, already mapped generically).

### Testing (TDD)

`TestDataServiceTest` additions:

- Fresh DB: `seedRanges()` creates 4 ranges × (8 positions + 2 boxes + 8
  devices), every position holds a device, `created=true`,
  `positionsCreated=8`, `boxesCreated=2`, `devicesAssigned=8` per range.
- Position→device wiring is correct and ordered: position A holds Box 1's
  first device, …, position H holds Box 2's last device (assert via
  `position.getDevice().getAlias()` matching label, and
  `device.getRange()`/`getRangePosition()` set).
- Second call on an already-fully-seeded DB: `created=false` for all ranges,
  all three new counts `0`, no new `SmartBox`/`Device`/`RangePosition` rows
  (repository counts unchanged before/after).
- A range with an existing partial position count (e.g. 3, from manual
  setup) is left untouched: `positionsCreated=0`, no boxes/devices created
  for that range, existing positions unmodified.
- Missing seeded `FirmwareConfig` or `Werfer` DeviceType → `IllegalStateException`
  (same as today's `createMockSmartBox` guard, now also reachable from
  `seedRanges()`).

Coverage target ≥80% for new/changed code (existing project convention).

## Frontend (`smart-ground-ui`)

- **`src/views/admin/TestingView.vue`** — "Schiessplatz Setup" card:
  - Hint text updated: "Erstellt 4 Ranges mit je 8 Positionen, 2 SmartBoxes
    (4 Geräte je Box) und weist alle 32 Geräte den Positionen zu (falls noch
    nicht vorhanden)."
  - Button label unchanged ("4 Plätze erstellen").
  - Result list per range extended to show the new counts, e.g.:
    - `Vorderlader — erstellt · 8 Positionen · 2 Boxen · 8 Geräte zugeordnet`
    - `Trapstand — bereits vorhanden`
  - Logic: a range line shows the counts only when `positionsCreated > 0`
    (i.e. something was actually built this call); otherwise it shows the
    existing "erstellt"/"bereits vorhanden" wording alone.
- **`src/services/testingApi.js`** — `seedRanges()` call unchanged (response
  shape grows, no client-side mapping needed beyond reading the new fields).

## Out of scope

- No change to `createMockSmartBox` behavior or its endpoint (device alias
  scheme `"Werfer 1..N"` there is untouched — only `seedRanges()` uses
  position-label aliases).
- No handling for a range with 1–7 existing positions beyond "leave it
  alone" — this is dev tooling for a fresh/wiped DB, not a repair tool.
- No `DataInitializer` changes — this remains on-demand only.
- No UI control to reset/undo the ready-to-go setup (DB wipe is external, as
  today).
