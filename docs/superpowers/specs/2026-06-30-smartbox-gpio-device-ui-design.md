# Design Spec: SmartBox GPIO / Device Layer UI

**Date:** 2026-06-30  
**Status:** Approved for implementation  
**Depends on:** `docs/superpowers/plans/2026-06-30-backend-capability-registry-permissions.md` (must be implemented first — `adminBlocked`, permission guards, and FirmwareConfig upsert are backend prerequisites)

---

## Summary

Redesigns the SmartBox device management UI to enforce the ADMIN / MANAGE\_RANGES / any-authenticated permission split at the UI layer, give operators a named-device view with no raw GPIO exposure, and add per-device Block/Unblock controls. No new routes are introduced; changes are confined to the existing `SmartBoxesView` tabs and the per-device card/row components.

---

## Permission Model

| Action | Location | Required permission |
|---|---|---|
| Create Device-Config (name + GPIO pin + signalDurationMs, scoped to FirmwareConfig) | Gerätetypen tab | `ADMIN` |
| Edit Device-Config name | Gerätetypen tab | `MANAGE_RANGES` |
| Edit Device-Config GPIO pin | Gerätetypen tab | `ADMIN` |
| Edit Device-Config `signalDurationMs` | Gerätetypen tab | `MANAGE_RANGES` |
| Add device to SmartBox (pick Device-Config by name) | SmartBoxCard | `MANAGE_RANGES` (unchanged) |
| Edit device: alias rename + signalDurationMs override + box-move | SmartBoxCard inline edit | `MANAGE_RANGES` |
| Fire a device command | DeviceCard / SmartBoxCard / PositionCard | any authenticated user |
| Block a device (user-level) | DeviceCard / SmartBoxCard / PositionCard | any authenticated user |
| Unblock a device (user-level, `adminBlocked=false`) | same | any authenticated user |
| Lift an admin-set block (`adminBlocked=true`) | same | `ADMIN` only |

Permission checks use the existing `authStore.hasPermission(permissionString)` pattern. The backend is the enforcement authority; UI permission gates are UX guidance only and must not be relied on as security.

**`delaySignalDurationMs` is removed completely** from all UI, stores, mappers, services, and API calls — the feature was dropped.

---

## 1. Gerätetypen Tab — Device-Config Management

### Layout

The existing `DeviceTypeGroupsPanel.vue` is replaced by a new `DeviceConfigPanel.vue` component (same slot in `SmartBoxesView.vue`).

Content is grouped by **FirmwareConfig** (primary dimension: `appVersion + boxType`). Within each firmware block, Device-Configs are shown as table rows. DeviceTypeGroup is a column, not a section header.

```
Gerätetypen tab
│
├─ Block: v1.0 — xiao-esp32s3
│   ┌──────────────────────────────────────────────────────────────┐
│   │  Name       Gruppe        GPIO-Pin    Dauer (ms)  Aktionen   │
│   │  Werfer 1   Wurfmaschine  GPIO-8      500         [✏️]       │
│   │  Werfer 2   Wurfmaschine  GPIO-5      500         [✏️]       │
│   │  LED Test   ⚙ Debug       — (onboard) 150         [✏️]       │
│   └──────────────────────────────────────────────────────────────┘
│   [+ Neue Konfiguration]   ← visible only if ADMIN
│
├─ Block: v0.6 — xiao-esp32s3
│   ...
│
└─ (empty state if no FirmwareConfigs exist)
    "Keine Firmware-Konfigurationen vorhanden.
     Registriere eine SmartBox, um zu beginnen."
```

### FirmwareConfig Blocks

- Loaded from a new `firmwareConfigApi.js` service + `firmwareConfigStore.js` Pinia store.
- Each block heading shows `v{appVersion} — {boxType}`.
- FirmwareConfigs are auto-created by discovery (backend plan), so no UI to create/delete them is needed here.

### Inline Edit Row

Clicking ✏️ expands an inline edit row:

The ✏️ edit button is hidden entirely for users without `MANAGE_RANGES`. For users who have `MANAGE_RANGES` but not `ADMIN`, the expanded edit row shows name and signalDurationMs as editable, and GPIO-Pin as read-only text (not an input). For `ADMIN`, all three fields are editable inputs.

- **Name** — editable for `MANAGE_RANGES`
- **GPIO-Pin** — editable input only for `ADMIN`; read-only text for `MANAGE_RANGES`; hidden (whole edit row inaccessible) below `MANAGE_RANGES`
- **Signaldauer (ms)** — editable for `MANAGE_RANGES`

Save calls `deviceTypeApi.update(id, { name, command: pin, signalDurationMs })`. Pin change calls require the backend ADMIN permission guard.

### Create Form ("+ Neue Konfiguration")

Button is `v-if="authStore.hasPermission('ADMIN_PERMISSION')"` (exact permission string to be confirmed against `SecurityConfig.java` at implementation time — see backend plan).

Inline create form at bottom of the firmware block:

- **Name** (text, required) — e.g. "Werfer 3"
- **Gruppe** (select from DeviceTypeGroups; LED group shows with `⚙ Debug` badge)
- **GPIO-Pin** (number 0–40, required; hidden/`— (onboard)` and disabled if LED group selected)
- **Signaldauer (ms)** (number ≥ 0, required)

On submit: backend must support creating a SignalType + DeviceType in a single call (or two sequential calls: `POST /signal-types` then `POST /device-types`). **This is an API design assumption flagged for backend coordination** — the current `deviceTypeApi.js` may need a new endpoint or the UI makes two sequential calls and rolls back the DeviceType creation client-side on SignalType failure.

The new Device-Config is appended to the block list on success; no page reload.

### LED / Debug Distinction

Rows belonging to a DeviceTypeGroup marked as LED/debug:

- Show a `⚙ Debug` pill in the Gruppe column (amber/muted, matching the existing `Badge` component's warning variant).
- GPIO-Pin cell shows `— (onboard)` and is never editable (even for ADMIN) — the firmware always targets `board.LED_PIN`.
- Create form: if LED group is selected, pin field disables and displays `— (onboard)`.

**How to identify the LED group:** a group whose name matches `"LED"` (case-insensitive) is treated as debug-only. This avoids needing a new `isDebug` backend field. If the group naming convention changes, a `constants/deviceTypes.js` constant (`DEBUG_GROUP_NAMES = ['LED']`) guards it in one place.

---

## 2. SmartBoxCard — Inline Edit Row Changes

The inline edit row (`SmartBoxCard.vue` lines 121–198) is modified:

**Remove:**
- `GPIO-Pin` input (lines 147–155)
- `Verzögerung (ms)` input (lines 166–173)

**Keep:**
- Name (alias) input
- Signaldauer (ms) override input (MANAGE\_RANGES; renders the field — backend enforces the guard)
- SmartBox selector (box-move with firmware conflict warning — keep as-is)

**Add: Block/Unblock per device row** (see section 3).

---

## 3. Block / Unblock UI

Added to three existing components: `SmartBoxCard.vue` (device table row actions), `DeviceCard.vue`, and `PositionCard.vue`. The pattern is identical in all three.

### Device Status Indicator

New compact status column/indicator per device:

| Condition | Display |
|---|---|
| `healthy=false` | Orange dot, tooltip "Nicht reagiert" |
| `blocked=true`, `adminBlocked=false` | Orange dot + lock icon, tooltip "Gesperrt (Nutzer)" |
| `adminBlocked=true` | Red dot + lock icon, tooltip "Admin-gesperrt" |
| Operational | Green dot |

### Block / Unblock Button Logic

```
if adminBlocked:
    [Entsperren]  disabled, tooltip "Nur Admin kann aufheben"
    (if ADMIN: [Entsperren] enabled → sends UNBLOCK, clears adminBlocked)
else if blocked:
    [Entsperren]  enabled → sends UNBLOCK
else:
    [Sperren]  enabled → opens confirm dialog
```

### Admin Block — Confirm Dialog

When an ADMIN clicks "Sperren", a small confirm dialog appears with two options:

- **Sperren** — sets `blocked=true` only (user-level, any operator can lift)
- **Dauerhaft sperren** — sets `blocked=true` + `adminBlocked=true` (only ADMIN can lift)

Non-ADMIN users see no dialog — "Sperren" sends immediately without the option.

### API Call

Block/Unblock sends `POST /api/devices/{id}/command` with body `{ "command": "BLOCK" | "UNBLOCK" }`. The `adminBlocked` flag is set server-side based on the caller's permission (per backend plan). The UI always re-fetches the device's `blocked`/`adminBlocked` state after the call completes — no optimistic update, since block state is set server-side and may differ from what the client sent.

---

## 4. "Add Device" Form — LED Exclusion

The group dropdown in SmartBoxCard's "Add device" form (`newDeviceForm.groupId`) filters out LED/debug groups by default using the same `DEBUG_GROUP_NAMES` constant.

A `v-if="authStore.hasPermission('ADMIN_PERMISSION')"` toggle link — "Debug-Geräte anzeigen" — below the group select reveals LED groups when activated. This allows ADMIN to assign LED devices for debugging without cluttering the normal flow.

---

## 5. New Store and Service Files

| File | Change |
|---|---|
| `src/stores/firmwareConfigStore.js` | New: loads `GET /firmware-configs`; state: `firmwareConfigs[]`, `isLoading`, `error` |
| `src/services/firmwareConfigApi.js` | New: `list()` → `GET /firmware-configs` |
| `src/mappers/FirmwareConfigMapper.js` | New: `{ id, version, boxType, capabilitiesJson, configSchemaVersion }` → UI model |
| `src/components/DeviceConfigPanel.vue` | New: replaces `DeviceTypeGroupsPanel.vue` (old file deleted) |
| `src/stores/deviceStore.js` | Add: `blockDevice(id)`, `unblockDevice(id)`, update `applyDeviceEvent` to handle `adminBlocked` |
| `src/services/deviceApi.js` | Add: `sendCommand(id, command)` (may already exist on smartBoxApi; consolidate) |
| `src/mappers/DeviceMapper.js` | Add: `adminBlocked` field mapping; remove `delaySignalDurationMs` |
| `src/components/SmartBoxCard.vue` | Modify: remove pin/delay edit fields; add block/unblock buttons + status indicator |
| `src/components/DeviceCard.vue` | Modify: add block/unblock buttons + status indicator |
| `src/components/PositionCard.vue` | Modify: add block/unblock buttons + status indicator |
| `src/constants/deviceTypes.js` | Add: `DEBUG_GROUP_NAMES = ['LED']` |

`DeviceTypeGroupsPanel.vue` is deleted (replaced by `DeviceConfigPanel.vue`). Dead code rule: do not keep the old file as a backup.

---

## 6. Error Handling & Edge Cases

- **Block/Unblock on offline box:** the backend command will still be accepted and queued; the firmware applies it on reconnect. The UI shows a transient toast "Gesperrt (Box offline — wird beim nächsten Verbinden angewendet)" if `box.status !== 'ONLINE'`.
- **Firmware conflict on box-move (existing):** keep the existing `firmwareConflict` computed warning in SmartBoxCard edit row — unchanged.
- **No FirmwareConfigs yet:** Gerätetypen tab shows empty state "Keine Firmware-Konfigurationen vorhanden. Registriere eine SmartBox, um zu beginnen."
- **Creating Device-Config fails (backend ADMIN guard):** show inline error in the create form: "Erstellen fehlgeschlagen: {error.message}". Do not close the form.
- **LED group detection fallback:** if `DEBUG_GROUP_NAMES` doesn't match any group (e.g. group was renamed), no groups are fenced — the feature degrades gracefully (all groups show normally). A console warning is logged.
- **adminBlocked field missing (pre-backend-plan):** `DeviceMapper.toDevice()` defaults `adminBlocked` to `false` if absent in the API response. UI renders correctly as unblocked until backend plan is deployed.

---

## 7. What This Design Does Not Cover

- Creating or deleting DeviceTypeGroups (groups are pre-seeded by backend/admin; no UI for group CRUD in this pass)
- Viewing device command history / last-command detail
- Multi-device bulk block/fire actions
- Sensor / INPUT direction devices (no UI for INPUT signal handling — backend/firmware not yet wired end-to-end)
- Real-time device status updates via WebSocket/STOMP (not wired; `applyDeviceEvent` in deviceStore is the hook for when that lands)
