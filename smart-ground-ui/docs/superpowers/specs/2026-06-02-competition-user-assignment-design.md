# Competition: Assign Real Users to Rotten

**Date:** 2026-06-02  
**Status:** Approved  
**Scope:** `smart-ground-ui` only

---

## Problem

Players in Rotten are currently anonymous — just a free-text `displayName` string with no link to backend users. This prevents the system from attributing results to real user accounts or enforcing uniqueness.

---

## Requirements

- Every player slot in a Rotte must be filled by a real backend user (no anonymous guests).
- A user can only appear once across all Rotten in a single event (no duplicates).
- Once a user is assigned, their name auto-fills from the backend and is locked (not editable).
- Removing a player returns them to the available pool immediately.

---

## Data Model Change

The player object stored in `competitionEventStore` gains a `userId` field:

```js
// Before
{ id: uuid(), displayName: '', paid: false }

// After
{ id: uuid(), userId: '<backend-user-uuid>', displayName: 'Anna Müller', paid: false }
```

- `addPlayer(eventId, rotteId, user)` — `user = { id, displayName }` from the backend user list.
- `updatePlayerName` is removed entirely.

---

## Component Changes

### 1. `competitionEventStore.js`
- `addPlayer` accepts a `user` object (`{ id, displayName }`), stores `userId` + locked `displayName`.
- Remove `updatePlayerName` action and its localStorage persistence path.

### 2. `WettkampfDetailView.vue`
- Calls `userStore.loadUsers()` on `onMounted`.
- Computes `availableUsers`: all `userStore.users` minus any `userId` already used across all Rotten of the current event. This is a single reactive computed shared across all Rotte cards.
- Passes `availableUsers` as a prop to each `RotteEditorCard`.

### 3. `RotteEditorCard.vue`
- Player row: replace editable `<input>` with a locked `<span>` showing `player.displayName`.
- "+ Schütze hinzufügen" button: when clicked, renders `UserPickerDropdown` below it.
- Emits `add-player` with the selected user object `{ id, displayName }`.
- Button is disabled (with tooltip "Alle Schützen bereits zugewiesen") when `availableUsers` is empty.
- New prop: `availableUsers: Array` (required).

### 4. New `UserPickerDropdown.vue` (`src/components/competition/`)
- Props: `users: Array` (the available users list).
- Emits: `select(user)`, `close`.
- Renders: a text search `<input>` + scrollable filtered list of user rows.
- Closes on: outside click (via `@click.outside` or `onMounted` document listener) or `Escape` key.
- If the filtered list is empty, shows "Keine Schützen verfügbar".

---

## Interaction Flow

1. Admin opens a PLANNING-status event → `WettkampfDetailView` loads all backend users.
2. `availableUsers` computed = `userStore.users` excluding `userId`s already in any Rotte.
3. Each `RotteEditorCard` receives the same `availableUsers` list.
4. Admin clicks "+ Schütze hinzufügen" → `UserPickerDropdown` appears inline with search.
5. Admin filters by name, clicks a user → row locks with name + payment chip.
6. Removing a player (× button) immediately returns them to the available pool.
7. When all users are assigned, the add button is disabled.

---

## What Does NOT Change

- Payment tracking (`paid` / `PaymentChip`) — unchanged.
- Rotte rename, add, remove — unchanged.
- Competition start/active/completed flow — unchanged.
- Backend API — no new endpoints needed; user list comes from existing `GET /users`.

---

## Files Touched

| File | Change |
|---|---|
| `src/stores/competitionEventStore.js` | `addPlayer` signature, remove `updatePlayerName` |
| `src/views/admin/WettkampfDetailView.vue` | Load users on mount, compute `availableUsers`, pass to cards |
| `src/components/competition/RotteEditorCard.vue` | Locked name display, new `availableUsers` prop, emit user on add |
| `src/components/competition/UserPickerDropdown.vue` | **New file** — inline searchable user picker |
