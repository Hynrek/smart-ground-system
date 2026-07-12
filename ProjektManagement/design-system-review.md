# Design System Review — Smart Ground UI
*April 2026 · smart-ground-ui (Vue 3)*

---

## Summary

**Components reviewed:** 12 components + 7 views  
**Issues found:** 18  
**Overall score: 52/100**

The foundation is solid — routing, state management, and component naming all make sense. The main problem is that the design system exists in three disconnected layers (Vue default tokens in `base.css`, Werfer tokens in `main.css`, and JS constants in `werfertokens.js`) that never talk to each other. The result is that 80 % of color and spacing values are hardcoded directly in component styles, making any visual change a search-and-replace across the entire codebase.

---

## 1. Token System — Three Islands, No Bridges

**Severity: High**

The project has three separate sources of truth for design values, none of which reference each other:

| Source | What it contains | Used by |
|---|---|---|
| `base.css` | Vue default `--vt-c-*` and `--color-*` tokens | Nothing (unused) |
| `main.css` | `--werfer-pine-green`, `--werfer-parchment-*`, etc. | Werfer Remote components |
| `werfertokens.js` | Same Werfer values as JS constants | Werfer Remote components (partially) |

Meanwhile, the admin UI (Sidebar, Button, Badge, all views) uses none of the above. Every component hardcodes its own values directly. Some examples found across the codebase:

- `#1a1a2e` — appears in Sidebar, Button, RangesView, RangeDetailView, SmartBoxDetailView, DeviceCard, and more. It is the primary brand color but is never assigned a token.
- `#4fc3f7` — the accent/active color. Hardcoded in Sidebar active state, DeviceCard fired state, and RangeDetailView drag-over state.
- `#718096` — the muted text color. Hardcoded in at least 8 different places.
- `#f7f8fc` — the page background. Hardcoded in MainLayout and App.vue separately.

**Recommendation:** Define one canonical set of CSS custom properties in `main.css` covering both themes. Map the admin palette to the `:root` block, and the Werfer Remote palette to a `.werfer-remote` class (applied to the root element when that view mounts). Then replace all hardcoded hex values with token references.

Suggested admin token additions to `main.css`:

```css
:root {
  /* Admin UI core palette */
  --sg-brand: #1a1a2e;
  --sg-brand-hover: #0f0f1a;
  --sg-accent: #4fc3f7;
  --sg-accent-hover: #2ba4d0;
  --sg-text-primary: #1a1a2e;
  --sg-text-muted: #718096;
  --sg-text-faint: #a0aec0;
  --sg-bg-page: #f7f8fc;
  --sg-bg-card: #ffffff;
  --sg-bg-panel: #f4f6fb;
  --sg-border: #e2e8f0;
  --sg-border-input: #cbd5e0;
  --sg-radius-card: 10px;
  --sg-radius-btn: 7px;
  --sg-radius-input: 6px;
}
```

---

## 2. Button Component — Exists but Isn't Used

**Severity: High**

`Button.vue` is a well-designed component with a proper variant system (`primary`, `ghost`, `danger`) and size system (`sm`, `md`). Only `RangeDetailView.vue` actually uses it. Every other view defines its own local button classes:

| View | Local button classes |
|---|---|
| RangesView | `.btn-add`, `.btn-save`, `.btn-cancel`, `.btn-remote`, `.btn-edit` |
| SmartBoxDetailView | `.btn-primary`, `.btn-save`, `.btn-cancel`, `.btn-edit-inline`, `.btn-save-inline` |
| RangeDetailView | Uses `<Button>` — the correct pattern |

This means the same button has different border-radius (7px in `Button.vue`, 6px in SmartBoxDetailView, 8px in RangesView), different hover colors, and slightly different padding depending on which view you're looking at.

**Recommendation:** Migrate all inline button definitions to `<Button>`. Add a `btn-icon-only` size variant for the icon-only buttons (the remote/edit icon buttons on range cards) since they don't fit the current `sm/md` system.

---

## 3. Forms — Reimplemented Four Times

**Severity: Medium**

Form fields (label + input + optional hint) are written from scratch in RangesView, SmartBoxDetailView, DeviceTypeView, and the `AGENTS_DeviceType_UI.md` spec. Each has slightly different label font sizes, input padding, and focus ring colors.

There's no `<FormField>`, `<TextInput>`, or `<SelectInput>` component. This makes it impossible to change the focus ring color globally (currently `#4fc3f7` in SmartBoxDetailView, missing entirely in RangesView).

**Recommendation:** Extract a `<FormField>` component that wraps label + input slot + optional hint text. This is the single highest-leverage component addition available right now.

---

## 4. Typography — Two Font Stacks in Conflict

**Severity: Medium**

`base.css` sets `font-family: Inter, -apple-system, ...` on `body`. `main.css` then also sets `font-family: system-ui, -apple-system, ...` on `body`. The second declaration wins, so the Inter stack in `base.css` is completely ignored.

The Werfer Remote design tokens specify Playfair Display, DM Sans, and DM Mono — but no `<link>` to Google Fonts (or equivalent) exists anywhere in `index.html` or `main.js`. If those fonts aren't loaded, the browser falls back silently to serif/sans-serif.

**Recommendation:**
1. Remove the `body` font-family from `base.css` (or delete `base.css` entirely — its tokens are unused).
2. Add the Google Fonts import for Playfair Display, DM Sans, and DM Mono to `index.html`.
3. Apply the Werfer fonts only inside the `.werfer-remote` scope, not globally.

---

## 5. Naming Consistency — Token Names Don't Match

**Severity: Low-Medium**

The werfer token names in `main.css` (CSS variables) and `werfertokens.js` (JS constants) describe the same values but use different naming conventions:

| CSS variable | JS constant |
|---|---|
| `--werfer-pine-green` | `WERFER_COLORS.pineGreen` |
| `--werfer-parchment-light` | `WERFER_COLORS.parchmentLight` |
| `--werfer-orange-accent` | `WERFER_COLORS.orangeAccent` |

The Werfer Remote components use the CSS variables directly in `<style>` blocks (correct), but some also import the JS constants for inline bindings. This means any color update requires touching both files.

**Recommendation:** The CSS variables should be the single source of truth. Remove the JS constants from `werfertokens.js` for anything that can be expressed as a CSS variable. Only keep JS constants for values that truly need to be consumed in JavaScript logic (e.g. animation durations for `setTimeout`).

---

## 6. Status System — Three Different Implementations

**Severity: Medium**

The concept of "device/box status" is expressed three different ways:

1. **`StatusDot.vue`** — reads from `STATUS_COLORS` in `deviceTypes.js` (JS object → inline style)
2. **SmartBoxDetailView** — uses local `.status-badge.online/.offline` CSS classes with hardcoded colors
3. **Badge.vue** — general-purpose color badge (`green`, `red`, `warn`) used for aggregate counts

The result is that "online" renders as a small green dot in DeviceCard, as a pill badge in SmartBoxDetailView header, and as a count badge in RangeDetailView header — with no shared visual language tying them together.

**Recommendation:** Define a `<StatusBadge status="online|offline|warn|blocked">` component that wraps Badge.vue and maps status names to badge colors. Use it everywhere status needs to be displayed as text/pill. Keep `StatusDot` for the compact dot-only case.

---

## 7. Navigation Bugs

**Severity: High**

Two routing issues found:

**Bug 1 — Wrong back-link in SmartBoxDetailView:**
```js
// Current (wrong):
router.push('/smart-boxes')

// Correct (matches router/index.js):
router.push('/smartboxes')
```
This will navigate to a 404. The route is `/smartboxes`, not `/smart-boxes`.

**Bug 2 — Breadcrumb back button doesn't work in RangeDetailView:**
The breadcrumb is defined as:
```js
{ label: 'Schiessplätze', onClick: () => emit('back') }
```
But `RangeDetailView` is loaded inside `<router-view>` — its `emit('back')` goes nowhere. The correct approach is `router.push('/ranges')` inside the onClick.

**Recommendation:** Fix both immediately. Also add a `<Breadcrumb>` to SmartBoxDetailView for consistency — it currently only has the raw `← Zurück` back-link.

---

## 8. Mixed Unit Systems

**Severity: Low**

Some views use `rem` units (`0.875rem`, `1.25rem`, `1.5rem`) while others use `px` (`14px`, `22px`, `28px`). SmartBoxDetailView is almost entirely `rem`; RangesView and RangeDetailView are almost entirely `px`. Since the base font size is `15px` (set in `base.css`), `1rem` ≠ `16px` here, which makes the rem values unintuitive.

**Recommendation:** Pick one convention. Given that this is a fixed-layout tablet/admin UI (not a fluid document), `px` is arguably more predictable. Standardise in the next refactor pass.

---

## 9. Component Coverage Gaps

**Severity: Medium**

Missing components that would reduce duplication significantly:

| Missing component | Currently duplicated in |
|---|---|
| `<FormField label hint>` | RangesView, SmartBoxDetailView, DeviceTypeView |
| `<PageHeader title subtitle>` + action slot | RangesView, SmartBoxDetailView, all views |
| `<EmptyState icon title text>` | RangeDetailView, SmartBoxDetailView, multiple |
| `<StatusBadge status>` | SmartBoxDetailView, RangeDetailView |
| `<ConfirmDialog>` | Currently uses `window.confirm()` — no styling control |

---

## 10. No Loading Skeleton States

**Severity: Low**

All loading states are text strings ("Lade SmartBox…", "Lade Gerätetypen…"). For a tablet-first UI where network latency on Wi-Fi is real, skeleton loaders would provide a significantly better experience. This is a polish item, not critical for now, but worth tracking.

---

## Priority Actions

### Immediate (bugs)
1. Fix the back-link in SmartBoxDetailView: `/smart-boxes` → `/smartboxes`
2. Fix the breadcrumb `onClick` in RangeDetailView to use `router.push('/ranges')`

### High impact (design system)
3. Add admin palette CSS variables to `main.css` (see Section 1 above) and replace hardcoded hex values in Sidebar, Button, and the three main views
4. Migrate all local button classes in RangesView and SmartBoxDetailView to use `<Button variant="…">`
5. Import Google Fonts for Playfair Display, DM Sans, DM Mono in `index.html`

### Medium (component extraction)
6. Create `<FormField>` component and use it across all views
7. Create `<StatusBadge>` component and unify status display
8. Add `<Breadcrumb>` to SmartBoxDetailView

### Low (polish)
9. Standardise on `px` units across all views
10. Consolidate `base.css` into `main.css` (base.css is currently overridden and unused)

---

## Component Score Card

| Component | Token usage | State coverage | Reuse | Docs | Score |
|---|---|---|---|---|---|
| Button.vue | ❌ hardcoded | ✅ disabled | ⚠️ rarely used | ❌ | 5/10 |
| Badge.vue | ❌ hardcoded | ✅ | ✅ | ❌ | 6/10 |
| StatusDot.vue | ❌ JS const | ⚠️ 3 states | ✅ | ❌ | 5/10 |
| TypeChip.vue | ❌ JS const | ✅ | ✅ | ❌ | 6/10 |
| Sidebar.vue | ❌ hardcoded | ✅ active | N/A | ❌ | 6/10 |
| DeviceCard.vue | ❌ hardcoded | ✅ fired | ✅ | ❌ | 6/10 |
| Breadcrumb.vue | ❌ hardcoded | ✅ | ⚠️ 1 view only | ❌ | 4/10 |
| Icons.vue | N/A | N/A | ✅ | ❌ | 7/10 |

---

*Review generated April 2026 — smart-ground-ui @ Vue 3 / Vite*
