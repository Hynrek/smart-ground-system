# Passen-Vorlagen Admin View — Design Spec

**Date:** 2026-05-28  
**Status:** Approved  
**Route:** `/passen` — `PassenAdminView.vue` (Passen tab, replacing stub)  
**Layout:** `MainLayout` (admin)

---

## Overview

Replace the "Passen" stub tab in `PassenAdminView.vue` with a full admin template management view. Admins create, edit, and delete global Passe templates — named collections of Platz-Serien that downstream consumers (shooter flow, competition engine) will reference. No active instances, no player assignment, no start flow in this view.

---

## Tabs (context)

| Tab | Status | Description |
|---|---|---|
| Serien | Already implemented | Full CRUD for Platz-Serien |
| Passen | This sprint | Full CRUD for Passen-Vorlagen |

---

## Passen Tab

### List (main content area)

- Max-width ~900px, consistent with Serien tab
- **Header row**: "Passen-Vorlagen" title + total count badge + "＋ Neue Passe" primary button
- **Grouped by range**: determined by the first Serie's `rangeId` in each Passe. Each group header shows range name + Passe count; collapsible, expanded by default
- **Passe rows** within each group:
  - Passe name (left)
  - Serie count (center)
  - Pencil (edit) icon button → opens drawer in edit mode
  - Trash (delete) icon button → inline confirm before calling `deleteGlobalPasse(id)`
  - Clicking the row itself also opens the drawer in edit mode
- **Empty state**: "Noch keine Passen-Vorlagen" when `savedGlobalPassen.length === 0`

### Drawer — `GlobalPasseDrawer.vue`

Slide-out panel from the right (~420px on desktop, full-width below 640px). Dimmed backdrop; clicking backdrop closes and discards.

**Two modes: Create and Edit.**

#### Create mode

Triggered by "＋ Neue Passe" button. Fields top to bottom:

1. **Name input** — text field, placeholder "z.B. Olympisch 25m", max 50 chars
2. **Serie picker** — multi-select list of available Serien from `passeStore.savedSerien` filtered by `ownership === 'range'`, grouped by range. Each item shows Serie name + step count. Clicking an item toggles selection.
3. **Selected Serien list** — growing ordered list of selected Serien. Each entry shows Serie name + range name + ✕ remove button.
4. **Action row**:
   - "Speichern" — disabled until name is non-empty and ≥ 1 Serie selected. Calls `passeStore.createGlobalPasse(name, selectedSerien)`.
   - "Abbrechen" — closes drawer, discards changes

#### Edit mode

Triggered by clicking a Passe row or its pencil icon. Identical layout with:

- Name field pre-filled and editable
- Selected Serien list pre-populated with the Passe's existing Serien (as stored snapshots)
- Serie picker shows all range Serien; already-selected ones are highlighted
- Admin can add/remove Serien
- Additional **"Löschen"** danger button — shows inline confirm ("Passe löschen?") before calling `passeStore.deleteGlobalPasse(id)` and closing the drawer

Saving in edit mode calls `passeStore.updateGlobalPasse(id, name, serien)`.

---

## Data & Storage

| Concern | Solution |
|---|---|
| Global Passen list | `passeStore.savedGlobalPassen` — scans localStorage for `_sg_global_passe_` keys |
| Available Serien | `passeStore.savedSerien` filtered by `ownership === 'range'` |
| Create | New `passeStore.createGlobalPasse(name, selectedSerien)` |
| Update | New `passeStore.updateGlobalPasse(id, name, serien)` |
| Delete | New `passeStore.deleteGlobalPasse(id)` |

**Storage key:** `_sg_global_passe_{uuid}`

**Payload shape:**
```json
{
  "passeName": "Olympisch 25m",
  "serien": [
    { "id": "_sg_range_serie_...", "alias": "Platz 1 — Standard", "rangeId": "range-1", "rangeName": "Platz 1", "steps": [...] }
  ],
  "createdAt": 1716900000000,
  "ownership": "global"
}
```

Serien are embedded as snapshots at save time. Changes to the original Platz-Serie after saving do not retroactively update the Passe template.

All data remains localStorage-based. Backend persistence is a future step.

---

## Grouping Logic

Passen are grouped by the `rangeId` of their **first Serie**. This is the simplest approach — no multi-group complexity. If a Passe has Serien from multiple ranges, it appears in the group of its first Serie's range.

---

## Responsive Behavior

| Breakpoint | Behavior |
|---|---|
| ≥ 640px | Drawer is 420px wide, slides over right side with backdrop |
| < 640px | Drawer is full-width, covers entire view |

---

## Component Structure

```
PassenAdminView.vue          ← Passen tab replaces stub (list + drawer toggle)
  └── GlobalPasseDrawer.vue  ← new component (create/edit/delete)
```

`GlobalPasseDrawer` receives:
- `open`: Boolean
- `mode`: `'create'` | `'edit'`
- `passe`: the Passe object (edit mode only)
- emits: `saved`, `deleted`, `close`

---

## Out of Scope (this sprint)

- Starting a Passe / active instances
- Player assignment
- Shooter-side visibility of global Passen
- Competition engine integration
- Backend persistence
- Reordering Serien within a Passe via drag-and-drop

---

## Definition of Done

- [ ] "Passen" tab renders full list (was stub before)
- [ ] Passen-Vorlagen list grouped by range with collapse/expand
- [ ] "＋ Neue Passe" opens drawer in create mode
- [ ] Create flow: name + Serie picker + save → Passe appears in list
- [ ] Edit flow: pre-populated form, name editable, Serien addable/removable, save persists
- [ ] Delete: confirm dialog, removes from list
- [ ] Drawer closes on backdrop click and Abbrechen
- [ ] Empty state shown when no Passen-Vorlagen exist
- [ ] Responsive: full-width drawer below 640px
- [ ] `npm run lint` passes with no warnings
