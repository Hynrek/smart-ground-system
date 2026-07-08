# RangeDetailView — Mobile Device Assignment

**Date:** 2026-07-08
**Status:** Design — approved for planning

## Problem

On phones, dragging a device from the "Freie Geräte" tray onto a position does not work.

The pointer-drag implementation itself is sound: it uses Pointer Events (not HTML5 drag & drop, which never fires on touch), gates touch drags to a `.drag-handle` carrying `touch-action: none`, takes pointer capture, and keeps `pointer-events: none` on the drag ghost so the `elementFromPoint` hit-test is not swallowed. The failure is in layout, upstream of the drag code.

### Measured root cause

Reproducing the exact nesting and CSS of `MainLayout` → `RangeDetailView` → `PositionCard` at 375×812 with the assign panel open:

| element | width |
|---|---|
| viewport | 375px |
| `.sidebar` (collapses at ≤768px) | 70px |
| `.range-detail-view` | 305px |
| `.assign-panel.open` (`flex-shrink: 0`) | 256px |
| **`.detail-content`** | **56px** |

56px is exactly `padding: 28px` left + right — zero content width.

`min-width: auto` would normally stop a flex item shrinking below its min-content width (the 180px `.position-card`). It does not apply because `.detail-content` sets `overflow-y: auto`; a scroll container's CSS automatic minimum size is `0`. So `flex: 1` collapses it while the panel holds its fixed 256px.

Position cards still lay out at 180px, overflow, and are clipped. Probing the drag code's own hit-test (`elementFromPoint` → `.closest('[data-position-id]')`) across the full width at the cards' vertical centre:

```
hittable x range: [100, 124]      →  28px of 375px
element at card's visual centre (x=188): .panel-inner
```

The drop target is a 28px sliver; the rest of the card sits behind the tray being dragged from. The drag starts, the ghost follows the finger, and `dropTargetId` almost never resolves.

This survived because the tray must be open to have anything to drag, and at desktop width 256px is negligible.

### Second, independent defect

`autoScrollNearEdge` mutates `document.querySelector('.layout-main').scrollTop`. `.layout-main` never scrolls — `.range-detail-view` is `overflow: hidden` and height-constrained, so it never overflows its parent. Measured with 20 position cards:

```
.layout-main    scrollHeight 812 == clientHeight 812   scrollTop moved by 0   ← what the code targets
.detail-content scrollHeight 2874 > clientHeight 804   scrollTop moved by 200 ← what actually scrolls
```

The auto-scroll is a no-op on **every** viewport, desktop included. Because `touch-action: none` plus pointer capture also prevents scrolling by hand mid-drag, positions below the fold are unreachable during a drag on any range taller than one screen.

## Constraint

At 375px a 256px tray and a 180px card cannot coexist **horizontally**. Vertically there is 812px to spare. The house overlay pattern (`SerieDrawer`, `GlobalPasseDrawer`: `position: fixed`, `width: 100vw` at ≤640px) cannot be copied here — a full-width drawer would cover the drop targets. Drag requires source and target visible simultaneously; a browse/edit drawer does not.

## Decisions

1. **Phones do not drag.** They get a discrete tap flow.
2. **Position-first flow.** Tap an empty position's slot → modal of free devices → tap one to assign.
3. **The tap path exists at all viewport sizes.** Drag remains as a pointer-only enhancement. This is one interaction path to test, and it supplies the keyboard-accessible assignment route the drag never had (required by CLAUDE.md's accessibility rules).
4. **Below 640px the tray and its toggle are not rendered**, and `assignOpen` is forced `false`. The 256px flex sibling ceases to exist, so the collapse is structurally impossible rather than compensated for.

Breakpoint `640px` matches the existing drawer breakpoint. Above it the current flex layout is safe: at 641px the content column gets 315px (259px inside padding), enough for one 180px card.

## Scope

Three separable parts, layered:

- **A — Assignment path (feature).** Makes phones work.
- **B — Structural layout fix.** Makes the broken layout unreachable.
- **C — `autoScrollNearEdge` selector fix.** Makes drag work on long ranges.

Cutting C leaves desktop drag unable to reach off-screen positions. Cutting B leaves the phone layout crushed even though the picker would function through it.

## Components

**New — `components/DeviceSearchModal.vue`**
Near-mirror of `UserSearchModal.vue`. Props: `devices: Array`. Emits: `select(device)`, `close`. Filters on `alias`. Each row renders alias, truncated `smartBoxId`, and a `TypeChip` — the same three facts the tray rows show, so both lists read identically. Backdrop `@click.self` closes; `role="dialog"`, `aria-modal="true"`.

**New — `composables/useMediaQuery.js`**
`matchMedia` wrapper: reactive boolean, listener attached on mount, removed on unmount. No existing equivalent in `composables/`.

**Changed — `PositionCard.vue`**
`.empty-slot` becomes a `<button>` emitting `assign-device`. Gated on the existing `isAdmin` (`MANAGE_RANGES`) check that already guards rename and delete; a non-admin sees an inert slot, as today. Copy changes from "Gerät hierher ziehen" to "Gerät zuordnen", since dragging is no longer the only verb. The drag hint is retained only where a tray exists.

**Changed — `RangeDetailView.vue`**
Adds `pickerPositionId` and `showDeviceModal` refs plus one handler funnelling into the existing `handleDropOnPosition`. Gates the tray on `useMediaQuery`.

`isMobile` is driven by `matchMedia` in JS, not CSS alone: `assignOpen` is JS state, and a pure CSS hide would leave the panel logically open while invisible, with `onDevicePointerDown` still bound to hidden nodes.

## Data flow

```
tap empty slot ─► PositionCard emits assign-device
                    │
RangeDetailView: pickerPositionId = pos.id; showDeviceModal = true
                    │
DeviceSearchModal :devices="unassignedDevices"   ← same computed the tray uses
                    │  select(device)
                    ▼
handleDropOnPosition(pickerPositionId, device.id)   ← unchanged, shared with drag
                    │
rangeStore.assignDeviceToPosition(rangeId, positionId, deviceId)
```

No new store action, no new API call, no mapper change. Drag and tap converge on one function.

`allSidebarGroups` currently wraps the device list in a single-group array purely to give the tray a section header. Extract the underlying `unassignedDevices` computed and derive `allSidebarGroups` from it, so the modal and the tray provably show the same set rather than two filters that can drift.

## Edge cases

- **No free devices** — modal opens showing "Keine freien Geräte verfügbar", the string the empty tray already uses. The modal opens rather than the tap no-opping, so the tap always has a visible response.
- **Assignment fails** — current code `console.error`s and swallows. Retained. There is no toast or notification primitive in the codebase to reuse; introducing one is out of scope for this change. Recorded as a known gap.
- **Non-admin** — slot is not a button; no picker. Unchanged.
- **Filled positions** — untouched. Removal still goes through the hover overlay.
- **Resize across 640px mid-drag** — the tray unmounts; the existing `onUnmounted(onDragCancel)` releases window listeners and pointer capture. No new teardown required.

## Testing

- `DeviceSearchModal` — filter narrows the list; `select` emits the device; backdrop closes; empty state renders.
- `PositionCard` — emits `assign-device` on click, `Enter`, and `Space`; emits nothing for a non-admin.
- `RangeDetailView` — picker carries the correct `positionId`; selecting calls `rangeStore.assignDeviceToPosition`; tray is absent when `matchMedia` is mocked to match.
- `useMediaQuery` — updates on change event; removes its listener on unmount.
- `autoScrollNearEdge` — asserts it mutates the `.detail-content` node's `scrollTop` in jsdom. This is the regression that would otherwise silently return.

## Out of scope

- Any toast/notification system for assignment failures.
- Reassigning a device between two positions without removing it first.
- Drag support on phones.
- Horizontal auto-scroll during drag.
