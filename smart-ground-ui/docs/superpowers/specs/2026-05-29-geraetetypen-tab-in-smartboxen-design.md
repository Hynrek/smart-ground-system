# Design: Gerätetypen Tab in SmartBoxen View

**Date:** 2026-05-29  
**Status:** Approved

---

## Goal

Merge the "Gerätetypen" view into the "SmartBoxen" view as a second tab. Remove the standalone sidebar entry and route for Gerätetypen.

---

## Changes

### 1. Extract `DeviceTypeGroupsPanel.vue`

Create `src/components/DeviceTypeGroupsPanel.vue` by extracting the inner content (template body, script logic, scoped styles) from `DeviceTypeGroupsView.vue`. The panel contains no layout wrapper — no padding, no `max-width` container. That is left to the parent.

The existing `DeviceTypeGroupsView.vue` is deleted (no longer needed once the route is removed).

### 2. Update `SmartBoxesView.vue`

- Add a tab bar below the view header with two tabs: **SmartBoxen** (`smartboxen`) and **Gerätetypen** (`geraetetypen`).
- Use `useUrlTab('smartboxen', ['smartboxen', 'geraetetypen'])` for URL-persisted tab state (`?tab=smartboxen` / `?tab=geraetetypen`).
- Conditionally render:
  - `smartboxen` tab: existing filter chips + SmartBox cards (no change to logic)
  - `geraetetypen` tab: `<DeviceTypeGroupsPanel />`
- The view header (title + subtitle + action button) stays visible on both tabs. The subtitle adapts: on `smartboxen` it shows box/device counts; on `geraetetypen` it is hidden or shows type/group counts.

### 3. Update `Sidebar.vue`

Remove the `device-type-groups` entry from `allNavItems`. The SmartBoxen item (`smartboxes`) already covers this feature.

### 4. Update `router/index.js`

- Remove the `/device-type-groups` route and its `DeviceTypeGroupsView` import.
- Remove the `/deviceTypes` redirect (was pointing to `/device-type-groups`).

---

## What does NOT change

- All SmartBox card logic, filter chips, fire/add/remove/update/rename handlers — untouched.
- The `useUrlTab` composable — used as-is.
- All other routes and views.

---

## File summary

| File | Action |
|---|---|
| `src/components/DeviceTypeGroupsPanel.vue` | **Create** — extracted from DeviceTypeGroupsView |
| `src/views/SmartBoxesView.vue` | **Edit** — add tab bar + conditional render |
| `src/views/DeviceTypeGroupsView.vue` | **Delete** |
| `src/components/Sidebar.vue` | **Edit** — remove device-type-groups nav item |
| `src/router/index.js` | **Edit** — remove route + imports |
