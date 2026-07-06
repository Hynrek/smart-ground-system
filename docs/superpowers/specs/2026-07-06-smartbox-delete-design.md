# SmartBox Deletion — Design

## Purpose

Admins need to remove a SmartBox from the system permanently once its hardware is dead (e.g. broken box that will never come back online). Today there is no delete endpoint or UI action for SmartBoxes at all.

## Data model

Add `deletedAt` (`Instant`, nullable) to `SmartBox`, mirroring the existing `User.geloeschtAm` soft-delete pattern.

- A SmartBox with `deletedAt != null` is treated as gone from the API's perspective:
  - Excluded from `GET /api/smart-boxes` (list)
  - `GET /api/smart-boxes/{id}` returns 404 (`SmartBoxNotFoundException`) for a deleted box
- The row and its history remain in the DB — nothing is physically removed for the SmartBox itself.

## Cascade to Devices

`Device.smartBox` is non-nullable; a `Device` only makes sense wired to the specific physical GPIO pins of one box. Deleting a SmartBox therefore **hard-deletes all of its `Device` rows**, silently, with no separate confirmation or blocking check — same effect as calling the existing device-delete path for each one.

- Any `RangePosition` referencing one of those devices has its `device_id` nulled (already nullable — same behavior as today's single-device delete).
- No competition/session/score data (`PlayerResult`, `CompetitionSerieResult`, etc.) references `Device` or `SmartBox` directly, so competition history is unaffected.

## API

`DELETE /api/smart-boxes/{id}`

- Success: `204 No Content`
- Not found (missing or already soft-deleted): `404` via `SmartBoxNotFoundException`
- Authorization: `ROLE_ADMIN` only — consistent with the existing rule that structural/hardware changes (device creation, GPIO wiring, device-type-group reassignment) require ADMIN.

Implementation:
1. Add the path to `openapi.yaml` under the SmartBox section.
2. `./mvnw generate-sources` to regenerate `SmartBoxApi`.
3. `SmartBoxController.deleteSmartBox`:
   - Load the SmartBox (404 if not found/already deleted).
   - Delete all `Device` rows where `smartbox_id = box.id` (reuse the same repository delete used by `DeviceController.deleteDevice` — no new device-side logic).
   - Set `box.deletedAt = Instant.now()`, save.

## MQTT reactivation

If the same MAC address later sends a **discovery** message, `SmartBoxDiscoveryHandler` clears `deletedAt` (sets it back to `null`) as part of its normal upsert-by-MAC flow, and the box reappears in listings — starting with zero devices, since they were deleted along with the box. No other MQTT handler (status, config-ack, ota-status, device-executed) needs to change; they operate on a `SmartBox` already found by MAC and are unaffected by the `deletedAt` field.

## UI

- `smartBoxApi.js`: add `deleteSmartBox(id)` → `DELETE /api/smart-boxes/{id}`.
- `SmartBoxCard.vue` / `SmartBoxesView.vue`: add a delete action, visible only when the current user has ADMIN, gated behind a confirmation dialog ("Are you sure you want to delete this SmartBox? All its devices will also be removed.").
- On confirmed delete: call the API, then remove the box from the local list/store (no page reload needed).

## Out of scope

- No manual "undelete" action — reactivation only happens via MQTT discovery.
- No warning/blocking when devices exist — cascade delete is silent, per product decision.
- No changes to status/config-ack/ota MQTT handlers.
