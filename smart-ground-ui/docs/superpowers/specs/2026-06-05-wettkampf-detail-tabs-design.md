# Wettkampf Detail — Tabbed Setup View

**Date:** 2026-06-05
**Status:** Approved

---

## Summary

Add a "Rotten / Passen" tab bar to the SETUP phase of `WettkampfDetailView`, and rename the "Veröffentlichen" button to "Wettkampf starten". The Passen tab lets the admin add and remove passen (from global templates) that are assigned to this competition.

---

## Scope

Only the `SETUP` status block of `WettkampfDetailView` is changed. The `OPEN`, `ACTIVE`, `PRE_COMPLETE`, `COMPLETED`, and `ABANDONED` blocks are untouched.

---

## Layout

```
[ Info bar: Passen · Würfe · Schützen ]
[ Payment warning (if any)            ]

[ Rotten ][ Passen ]   ← tab row
────────────────────────────────────
  <active tab content>

[ X/Y Schützen bezahlt   [▶ Wettkampf starten] ]
```

- Info bar, payment warning, and start-section remain **outside** the tabs.
- Tab state: `ref('rotten')` (local, no URL sync needed).
- Button label: "Veröffentlichen" → **"Wettkampf starten"**.

---

## Rotten Tab

Identical to the current SETUP section's Rotten section — no changes to content or behaviour.

---

## Passen Tab

### Content

- Section header: title "Passen" + count badge (`event.passen.length`) + "Passe hinzufügen" button.
- List: each assigned passe rendered as a row showing:
  - Passe name
  - Muted meta line: "X Serien"
  - Remove button (×) on the right
- Empty state: "Noch keine Passen. Füge mindestens eine Passe hinzu."

### "Passe hinzufügen" picker

- Opens an inline dropdown listing global passen from `passeStore.savedGlobalPassen` that are **not** already in `event.passen`.
- Clicking an entry calls `store.addPasseToEvent(eventId, passeId)` and closes the dropdown.
- Button is **disabled** when all global passen are already assigned, or when the global list is empty.

### Data loading

On `onMounted`, if `passeStore.savedPassen` is empty, call `passeStore.loadPassenFromStorage()`.

---

## API Changes (`wettkampfApi.js`)

Two new functions:

```js
addPasse(sessionId, passeId)
// POST /sessions/{sessionId}/passen
// body: { passeId }

removePasse(sessionId, passeId)
// DELETE /sessions/{sessionId}/passen/{passeId}
```

---

## Store Changes (`competitionEventStore.js`)

Two new actions:

```js
addPasseToEvent(eventId, passeId)
// Calls wettkampfApi.addPasse, then appends the returned passe object to ev.passen

removePasseFromEvent(eventId, passeId)
// Calls wettkampfApi.removePasse, then filters ev.passen
```

---

## Files Changed

| File | Change |
|---|---|
| `src/views/admin/WettkampfDetailView.vue` | Add tab row + Passen tab content; rename button |
| `src/services/wettkampfApi.js` | Add `addPasse`, `removePasse` |
| `src/stores/competitionEventStore.js` | Add `addPasseToEvent`, `removePasseFromEvent` |

---

## Out of Scope

- Backend endpoint implementation (assumed to be added separately).
- Passen tab in the `OPEN` status block.
- Editing or creating passen from within this view (use PassenAdminView for that).
