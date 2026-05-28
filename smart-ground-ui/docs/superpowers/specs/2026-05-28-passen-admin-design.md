# Passen & Serien Admin View — Design Spec

**Date:** 2026-05-28  
**Status:** Approved  
**Route:** `/passen` — `PassenAdminView.vue`  
**Layout:** `MainLayout` (admin)

---

## Overview

Rewrite `PassenAdminView.vue` into a full admin management view for Platz-Serien (range-wide shooting sequences). The view uses a tabbed layout with a slide-out drawer for create/edit operations. Passen management is stubbed as a second tab for a future sprint.

---

## Tabs

| Tab | Status | Description |
|---|---|---|
| Serien | Implemented | Full CRUD for Platz-Serien |
| Passen | Stub | Placeholder — "Kommt bald" |

---

## Serien Tab

### List (main content area)

- Max-width ~900px, consistent with other admin views
- **Header row**: "Platz-Serien" title + total count badge + "＋ Neue Serie" primary button
- **Grouped by range**: each range with Serien gets a collapsible group header showing:
  - Range icon + range name + per-range Serie count
  - Groups expanded by default, toggle on header click
- **Serie rows** within each group:
  - Serie name (left)
  - Würfe count + step-type dot strip (center)
  - Pencil (edit) icon button → opens drawer in edit mode
  - Trash (delete) icon button → inline confirm dialog before deletion
  - Clicking the row itself also opens the drawer in edit mode
- **Empty state**: "Noch keine Platz-Serien" message + hint to click "＋ Neue Serie" when `rangeSerien.length === 0`

Grouping logic reuses the existing `rangeGroups` computed from the current `PassenAdminView`.

### Drawer

A slide-out panel from the right side (~420px wide on desktop, full-width below 640px). A dimmed backdrop sits behind it; clicking the backdrop closes the drawer (cancels without saving).

**Two modes: Create and Edit.**

#### Create mode

Triggered by "＋ Neue Serie" button. Fields top to bottom:

1. **Name input** — text field, placeholder "z.B. Olympisch 25m", max 50 chars
2. **Range picker** — `<select>` dropdown populated from `rangeStore.ranges`. Selecting a range loads the position grid below.
3. **Position grid** — device positions assigned to the selected range, rendered as clickable letter buttons (A, B, C…). Clicking adds a step to the sequence based on the active step type (see step type toggle below).
4. **Step type toggle** — four modes: Solo / Pair / a.Schuss / Raffale. Matches the existing `passeStore.addStep` logic:
   - **Solo / Raffale**: single position click → adds step immediately
   - **Pair / a.Schuss**: first click selects pending position, second click on a different position → adds combined step; clicking the same position cancels the pending selection
5. **Step sequence** — growing ordered list of added steps. Each step shows:
   - Colored dot (matching existing dot-type color scheme)
   - Position label(s) (letter + alias)
   - ✕ remove button to delete that step from the sequence
6. **Action row**:
   - "Speichern" — disabled until name is non-empty, range is selected, and sequence has ≥ 1 step. Calls new `passeStore.createRangeSerie(name, rangeId, rangeName, steps)`.
   - "Abbrechen" — closes drawer, discards changes

#### Edit mode

Triggered by clicking a Serie row or its pencil icon. Identical layout to create mode, with these differences:

- Name field is pre-filled and editable
- Range is displayed as a **read-only label** (not a picker) — range cannot be changed after creation
- Position grid loads for the Serie's range
- Step sequence is **pre-populated** with the Serie's existing steps
- Admin can add new steps and remove existing ones (same interaction as create mode)
- Additional **"Löschen"** danger button below the action row — shows inline confirm ("Serie löschen?") before calling `passeStore.deleteSerie(id)` and closing the drawer

Saving in edit mode updates the localStorage entry directly (name + steps) and refreshes `passeStore.savedSerien`.

---

## Passen Tab (Stub)

Renders a single empty-state card:

- Icon: `program`
- Title: "Passen-Verwaltung"
- Subtitle: "Wird in einer nächsten Version verfügbar sein."

No store connections. No logic. Visual placeholder only.

---

## Data & Storage

| Concern | Solution |
|---|---|
| Platz-Serien list | `passeStore.savedSerien` filtered by `ownership === 'range'` |
| Range list | `rangeStore.ranges` |
| Range positions | Devices assigned to the selected range (from `rangeStore` or `deviceStore`) |
| Create Serie | New `passeStore.createRangeSerie(name, rangeId, rangeName, steps)` — does not depend on `editingSerie` |
| Delete Serie | `passeStore.deleteSerie(id)` (existing) |
| Edit Serie (name + steps) | New `passeStore.updateSerie(id, name, steps)` — updates localStorage + refreshes `savedSerien` |
| Range positions | Devices assigned to the selected range from `deviceStore` (filtered by `rangeId`) |

All data remains localStorage-based. Backend persistence is a future step.

---

## Step Type Logic

Reuses the pattern from `passeStore.addStep`:

```
Solo     → click one position   → { type: 'solo', alias, positionId, letter }
Raffale  → click one position   → { type: 'raffale', alias, positionId, letter }
Pair     → click two positions  → { type: 'pair', alias1, alias2, positionId1, positionId2, letter1, letter2 }
a.Schuss → click two positions  → { type: 'a_schuss', ... same shape as pair ... }
```

The drawer manages its own local `pairPending` ref. No changes to `passeStore` are needed for this interaction.

---

## Responsive Behavior

| Breakpoint | Behavior |
|---|---|
| ≥ 640px | Drawer is 420px wide, slides over the right side with backdrop |
| < 640px | Drawer is full-width, covers the entire view |

---

## Component Structure

```
PassenAdminView.vue          ← full rewrite (view)
  └── SerieDrawer.vue        ← new component (create/edit drawer)
```

`SerieDrawer` receives:
- `mode`: `'create'` | `'edit'`
- `serie`: the Serie object (edit mode only)
- `ranges`: from `rangeStore`
- emits: `saved`, `deleted`, `close`

This separation keeps `PassenAdminView` focused on list/tab logic and `SerieDrawer` focused on form logic.

---

## Out of Scope (this sprint)

- Passen management (stubbed tab only)
- Backend persistence
- Reordering steps via drag-and-drop
- Duplicating a Serie
- Cross-device visibility of admin-created Serien

---

## Definition of Done

- [ ] Tabs render correctly: Serien (full) + Passen (stub)
- [ ] Platz-Serien list grouped by range with collapse/expand
- [ ] "＋ Neue Serie" opens drawer in create mode
- [ ] Create flow: name + range + position grid + step builder + save → Serie appears in list
- [ ] Edit flow: pre-populated form, name editable, steps addable/removable, save persists
- [ ] Delete: confirm dialog, removes from list
- [ ] Drawer closes on backdrop click and Abbrechen
- [ ] Empty state shown when no Platz-Serien exist
- [ ] Responsive: full-width drawer below 640px
- [ ] `npm run lint` passes with no warnings
