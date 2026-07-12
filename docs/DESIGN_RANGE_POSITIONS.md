# Feature Design: Range Positions (Letter Containers)

**Status:** Draft — awaiting approval; **historisch, entstand vor der [Hub/Node-Spec](superpowers/specs/2026-07-10-hub-node-architecture-design.md)** (Autorität). Vor einer Umsetzung gegen die Spec prüfen (Backend = Hub, UI-Zugriff künftig via node-api).
**Date:** 2026-05-21  
**Scope:** Backend (entity + API), Frontend (RangeDetailView)

---

## 1. Motivation

Shooters remember physical positions by letter (A, B, C…), not by device alias or UUID.
Programs already store a `posId` field on each step (StepRecord), but there is no backing
entity — the position reference is just an annotation with no enforced relationship.

This feature introduces **RangePosition** as a first-class entity: a named slot on a Range
that holds exactly one Device at a time. Programs and sessions reference positions by `posId`
(the position's label), not by device ID. Swapping a broken device only requires dragging a
replacement into the position — all programs continue to work unchanged.

---

## 2. Core Rules

| Rule | Description |
|---|---|
| 1:1 slot | A position holds exactly one Device at a time. |
| Unique label per range | "A" can only exist once per range. |
| Containers mandatory | Every device on a range must be inside a position. A device cannot be assigned to a range without a position. |
| Locked range | Positions cannot be created, deleted, or reassigned while `range.locked = true`. |
| Label auto-generation | If no label is supplied on creation, the backend assigns the next available uppercase letter (A → B → C → … → Z → AA → AB …). |
| Custom labels allowed | Admin can rename a position to any string that is unique within the range. |
| Play logic indirection | At execution time, the backend resolves `(rangeId, posId) → RangePosition → Device`. If the slot is empty the command is a no-op; the position's fire button is shown **disabled** in the UI. |

---

## 3. Data Model

### 3.1 New table: `range_positions`

```
range_positions
  id             UUID PK
  range_id       UUID FK → ranges  (NOT NULL)
  label          VARCHAR(32)        (NOT NULL)
  sort_order     INT                (NOT NULL, default 0)
  device_id      UUID FK → devices  (UNIQUE, nullable)

  UNIQUE (range_id, label)
```

`device_id` is UNIQUE (not just nullable) — a device may only occupy one position at a time.

### 3.2 Changes to existing tables

**devices** — add column:

```
device.range_position_id   UUID FK → range_positions  (nullable)
```

`device.range_id` is kept in sync automatically by the service layer when a device is
placed in/removed from a position. It must not become inconsistent with the position's range.

### 3.3 JPA entities

**RangePosition.java** (new)

```java
@Entity
@Table(name = "range_positions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"range_id","label"}))
@NullMarked
public class RangePosition {
    @Id @GeneratedValue(strategy = GenerationType.UUID) UUID id;
    @ManyToOne(fetch = LAZY, optional = false)  @JoinColumn(name = "range_id")  Range range;
    @Column(nullable = false, length = 32)      String label;
    @Column(name = "sort_order", nullable = false) int sortOrder = 0;
    @OneToOne(fetch = LAZY)
    @JoinColumn(name = "device_id", unique = true)
    @Nullable Device device;
}
```

**Range.java** — add:

```java
@OneToMany(mappedBy = "range", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("sortOrder ASC")
List<RangePosition> positions = new ArrayList<>();
```

**Device.java** — add:

```java
@OneToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "range_position_id")
@Nullable RangePosition rangePosition;
```

---

## 4. API

All endpoints are appended to the existing Range API. The openapi.yaml must be updated.

### 4.1 New response schema: `RangePositionResponse`

```yaml
RangePositionResponse:
  type: object
  properties:
    id:         { type: string, format: uuid }
    label:      { type: string }
    sortOrder:  { type: integer }
    device:     { $ref: '#/components/schemas/DeviceResponse', nullable: true }
```

### 4.2 Updated `RangeDetailResponse`

Add field:

```yaml
positions:
  type: array
  items: { $ref: '#/components/schemas/RangePositionResponse' }
```

### 4.3 New endpoints

| Method | Path | Body | Description |
|---|---|---|---|
| GET | `/api/ranges/{id}/positions` | — | List all positions for range (ordered by sortOrder) |
| POST | `/api/ranges/{id}/positions` | `{ label?: string }` | Create position; auto-label if omitted |
| PUT | `/api/ranges/{id}/positions/{positionId}` | `{ label: string }` | Rename position |
| DELETE | `/api/ranges/{id}/positions/{positionId}` | — | Delete position (must be empty) |
| PUT | `/api/ranges/{id}/positions/{positionId}/device` | `{ deviceId: UUID }` | Place device in position |
| DELETE | `/api/ranges/{id}/positions/{positionId}/device` | — | Remove device from position |

> Note: `{positionId}` in URL paths is the position's UUID. `posId` in step JSON refers to
> the position's **label** (e.g. `"A"`), which is how programs address positions at runtime.

**Error responses:**

- `409 Conflict` — label already exists on range
- `409 Conflict` — device already in another position
- `400 Bad Request` — deleting a non-empty position
- `423 Locked` — range is locked (mutation endpoints only)

### 4.4 Auto-label algorithm

```
existingLabels = positions.map(p → p.label).filter(label matches /^[A-Z]+$/)
nextLabel = first letter in [A..Z, AA, AB, …] not in existingLabels
```

Custom labels (e.g. "Mitte", "Links") are allowed but not part of auto-generation.

---

## 5. Service Layer

`RangePositionService` (new):

```
createPosition(rangeId, labelOpt) → RangePositionResponse
  - load Range, check not locked
  - resolve label (auto or supplied), check uniqueness
  - persist RangePosition with sortOrder = max(existing) + 1

renamePosition(rangeId, positionId, newLabel) → RangePositionResponse
  - load, check not locked, check label uniqueness

deletePosition(rangeId, positionId)
  - check device == null (or throw 400)
  - delete entity

assignDevice(rangeId, positionId, deviceId) → RangePositionResponse
  - check range not locked
  - check device not already in a position on a DIFFERENT range
  - set position.device = device
  - set device.rangePosition = position
  - set device.range = position.range   ← keep in sync

removeDevice(rangeId, positionId)
  - set position.device = null
  - set device.rangePosition = null
  - set device.range = null             ← keep in sync
```

### 5.1 Play execution: posId → device resolution

Steps reference a position by `posId` (the label string, e.g. `"A"`). At execution time:

```java
// Schritt referenziert eine Position (posId = Label-String, z.B. "A")
@Nullable UUID resolveDeviceForPosition(UUID rangeId, String posId) {
    return rangePositionRepository
        .findByRangeIdAndLabel(rangeId, posId)
        .map(RangePosition::getDevice)
        .map(Device::getId)
        .orElse(null);   // null → Schritt überspringen (leere Position)
}
```

If the resolved device is `null` (empty slot), the command is skipped silently server-side.
The frontend is responsible for disabling the fire button when `position.device == null`
(see section 6.3).

**Steps no longer carry a `deviceId`.** The `deviceId`, `deviceId1`, `deviceId2` fields are
removed from `StepRecord`. Steps reference positions exclusively via `posId` / `posId1` /
`posId2`.

### 5.2 StepRecord field rename

```
Before               After
────────────────      ────────────────
deviceId         →   (removed)
alias            →   alias            (kept — display name for the position label)
letter           →   posId
deviceId1        →   (removed)
deviceId2        →   (removed)
alias1           →   alias1
alias2           →   alias2
letter1          →   posId1
letter2          →   posId2
```

---

## 6. Frontend Changes

### 6.1 New service: `src/services/rangePositionService.js`

Wraps the five new REST endpoints.

### 6.2 Store: `rangeStore.js` additions

```js
// State
positions: {}   // keyed by rangeId: RangePositionResponse[]

// Actions
loadPositions(rangeId)
createPosition(rangeId, label?)
renamePosition(rangeId, positionId, label)
deletePosition(rangeId, positionId)
assignDevice(rangeId, positionId, deviceId)
removeDevice(rangeId, positionId)
```

### 6.3 RangeDetailView.vue — layout refactor

Replace the SmartBox-clustered device grid with a **position grid**.
The header button "Reserve" becomes **"Block"** (blocking the range from other users during
a session). The `[ + ]` toolbar button is removed — instead, a **ghost "next card"** is
always rendered after the last real position. Clicking it calls `createPosition()`.

```
┌──────────────────────────────────────────────────────────────────────┐
│  Range: Bahn 1              [ Block ]  [ Aktionsmodus ]              │
├──────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌───────────── ┐│
│  │      A      │  │      B      │  │      C      │  │      D      ··││
│  │  ─────────  │  │  ─────────  │  │  · · · · ·  │  │  · · · · ·  ··││
│  │ [DeviceCard]│  │ [DeviceCard]│  │  Leer        │  │  + Position  ··││
│  │  [ Fire ]   │  │  [ Fire ]   │  │  [ Fire ✗ ] │  │  hinzufügen ··││
│  └─────────────┘  └─────────────┘  └─────────────┘  └───────────── ┘│
│                                         ↑ empty slot                 │
│                                         fire button disabled         │
└──────────────────────────────────────────────────────────────────────┘
```

**Key interactions:**

- **Ghost card** (last card, always visible, dashed border + muted) → click → `createPosition(rangeId)` → ghost becomes real, new ghost appears after it
- **Label click (admin)** → inline edit → calls `renamePosition`
- **Drag device from sidebar → drop onto position card** → calls `assignDevice`
- **✕ on device inside position** → calls `removeDevice`
- **Delete position** (kebab menu, only if empty) → calls `deletePosition`
- **Fire button** — enabled only when `position.device != null && !position.device.blocked`; shown as disabled/greyed with a lock icon when slot is empty
- Drop zone is per-position card, not the whole page

### 6.4 New component: `src/components/PositionCard.vue`

```
Props:
  position      RangePositionResponse | null   // null = ghost card
  actionMode    boolean

Emits:
  drop(deviceId)
  remove-device
  rename(label)
  delete-position
  fire(deviceId)
  add-position     // emitted by ghost card on click
```

When `position === null` the card renders in ghost/muted style with a dashed border, a `+`
icon, and the label of the next auto-generated position as preview text (e.g. "D" if A–C
exist). This gives the admin a clear affordance for where the next position will land without
requiring a separate button in the toolbar.

When `position.device === null` (real but empty slot), the card renders an empty state
with a drop-target hint, and the fire button is rendered as `disabled`.

### 6.5 Sidebar: no change in concept

The sidebar still shows unassigned devices (those with no `rangePositionId`). Devices already
on another range remain listed under their range group.

---

## 7. Migration path for existing data

Since no production release exists, the schema change is applied via JPA `ddl-auto=update`.

Existing devices with a `range_id` but no `range_position_id` will appear in the **sidebar's
"Nicht zugeteilt"** section for their current range when the admin opens that range. They are
no longer shown inside the range's main view until placed in a position.

No Liquibase changeset is needed until v1.0.

---

## 8. Out of scope (future work)

- Reordering positions via drag-and-drop (sortOrder is stored, UI reorder not in this PR)
- Device health indicators per position in the play view (session screen)
- Position-level fire delay override
- Multi-device backup slots

---

## 9. Files to create / modify

### Backend
| Action | File |
|---|---|
| CREATE | `model/RangePosition.java` |
| CREATE | `repository/RangePositionRepository.java` |
| CREATE | `service/RangePositionService.java` |
| CREATE | `exception/RangePositionNotFoundException.java` |
| CREATE | `exception/RangePositionOccupiedException.java` |
| MODIFY | `model/Range.java` — add positions list |
| MODIFY | `model/Device.java` — add rangePosition field |
| MODIFY | `api/RangeController.java` — add position endpoints |
| MODIFY | `mapper/EntityMappers.java` — toRangePositionResponse |
| MODIFY | `dto/play/StepRecord.java` — rename letter→posId, remove deviceId fields |
| MODIFY | `mapper/PlayMapper.java` — update toStep / parseStep field names |
| MODIFY | `resources/static/openapi.yaml` — new schemas + endpoints, Step schema update |

### Frontend
| Action | File |
|---|---|
| CREATE | `services/rangePositionService.js` |
| CREATE | `components/PositionCard.vue` |
| MODIFY | `stores/rangeStore.js` — position state + actions |
| MODIFY | `views/RangeDetailView.vue` — position grid, ghost card, Block button |

