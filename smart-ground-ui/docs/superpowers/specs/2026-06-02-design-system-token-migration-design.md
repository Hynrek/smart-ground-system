# Design System Token Migration

**Date:** 2026-06-02  
**Status:** Approved  
**Scope:** `smart-ground-ui` — full migration of hardcoded values to CSS custom properties

---

## Problem

The Smart Ground UI has a 12-palette theme-switching system defined in `src/style.css`, but no component or view actually uses those CSS custom properties. All components and views use hardcoded hex values in scoped styles and JS constants. This means:

- The palette switcher has zero visual effect on the UI
- Colors are defined in three inconsistent places (CSS vars, `deviceTypes.js` constants, scoped styles)
- A warning color mismatch exists (`#ed8936` vs `#f5a623`)
- No spacing, typography, or shadow tokens exist

---

## Approach

**Top-down: Token layer → Components → Views**

1. Complete the token layer in `style.css` first
2. Migrate all 6 atomic components + Sidebar
3. Consolidate JS color constants into CSS classes
4. Sweep all views

---

## Section 1: Token Layer

All changes to `src/style.css`.

### Semantic color tokens (added to every palette block)

Each of the 12 `[data-palette="..."]` blocks gets these new tokens:

```css
--sg-color-success:        #38c97a;
--sg-color-success-bg:     #d4f5e2;
--sg-color-success-text:   #1e6640;
--sg-color-warning:        #ed8936;
--sg-color-warning-bg:     #fff3d4;
--sg-color-warning-text:   #8a5a00;
--sg-color-danger:         #e05252;
--sg-color-danger-bg:      #fde0e0;
--sg-color-danger-text:    #9b2c2c;
--sg-color-info-bg:        #dbeffe;
--sg-color-info-text:      #1a5fa0;
--sg-color-neutral-bg:     #e8edf0;
--sg-color-neutral-text:   #555555;
--sg-color-purple-bg:      #ede8ff;
--sg-color-purple-text:    #4a2da0;
```

### Palette-neutral tokens (added to `:root` once)

```css
/* Spacing */
--sg-space-1: 0.25rem;
--sg-space-2: 0.5rem;
--sg-space-3: 0.75rem;
--sg-space-4: 1rem;
--sg-space-6: 1.5rem;
--sg-space-8: 2rem;

/* Typography */
--sg-text-xs:   0.72rem;
--sg-text-sm:   0.85rem;
--sg-text-base: 1rem;
--sg-text-lg:   1.125rem;
--sg-text-xl:   1.25rem;
--sg-text-2xl:  1.5rem;

/* Shadows */
--sg-shadow-sm: 0 1px 3px rgba(0, 0, 0, 0.06);
--sg-shadow-md: 0 2px 8px rgba(0, 0, 0, 0.08);
--sg-shadow-lg: 0 4px 16px rgba(0, 0, 0, 0.10);

/* Accent with opacity (replaces rgba(79,195,247,X) patterns) */
--sg-accent-subtle:  color-mix(in srgb, var(--sg-accent) 8%,  transparent);
--sg-accent-tint:    color-mix(in srgb, var(--sg-accent) 12%, transparent);
--sg-danger-subtle:  color-mix(in srgb, var(--sg-color-danger) 10%, transparent);
```

### Body styles

Replace hardcoded hex in the global `body` rule and `:focus-visible` outline:

```css
body { background: var(--sg-bg-page); color: var(--sg-brand); }
:focus-visible { outline: 2px solid var(--sg-accent); }
```

---

## Section 2: Component Migrations

### Button.vue

Replace all scoped hex values with tokens. No template or prop changes.

| Selector | Old value | New value |
|----------|-----------|-----------|
| `.btn-primary` bg | `#1a1a2e` | `var(--sg-brand)` |
| `.btn-primary` color | `#fff` | `var(--sg-bg-card)` |
| `.btn-primary:hover` bg | `#0f0f1a` | `var(--sg-accent)` |
| `.btn-ghost` border | `#e2e8f0` | `var(--sg-border)` |
| `.btn-ghost` color | `#4a5568` | `var(--sg-text-muted)` |
| `.btn-danger` border | `#fca5a5` | `var(--sg-color-danger-bg)` |
| `.btn-danger` color | `#e05252` | `var(--sg-color-danger)` |
| `.btn-danger:hover` bg | `#fde0e0` | `var(--sg-color-danger-bg)` |

**Accessibility additions:**
- Add `:aria-disabled="disabled"` binding on the root element
- Accept optional `ariaLabel` prop, bind as `aria-label`

### Badge.vue

Replace hardcoded color blocks. Class names unchanged.

| Class | bg token | text token |
|-------|----------|------------|
| `.badge-blue` | `--sg-color-info-bg` | `--sg-color-info-text` |
| `.badge-green` | `--sg-color-success-bg` | `--sg-color-success-text` |
| `.badge-red` | `--sg-color-danger-bg` | `--sg-color-danger-text` |
| `.badge-gray` | `--sg-color-neutral-bg` | `--sg-color-neutral-text` |
| `.badge-warn` | `--sg-color-warning-bg` | `--sg-color-warning-text` |

### FormField.vue

| Selector | Old | New |
|----------|-----|-----|
| `.form-label` color | `#4a5568` | `var(--sg-text-muted)` |
| `.form-label` font-size | `0.85rem` | `var(--sg-text-sm)` |
| `.form-hint` color | `#a0aec0` | `var(--sg-text-faint)` |
| `.form-hint` font-size | `0.78rem` | `var(--sg-text-xs)` |

### StatusDot.vue

**Remove** the computed `style` binding and `STATUS_COLORS` import entirely.  
**Add** a computed `class` binding: `dot-online`, `dot-offline`, `dot-warning`, `dot-error`.

```css
.dot-online  { background: var(--sg-color-success); }
.dot-offline { background: var(--sg-color-danger); }
.dot-warning { background: var(--sg-color-warning); }
.dot-error   { background: var(--sg-color-danger); }
```

Keep existing `aria-label` and `role="img"` — these are correct.

### TypeChip.vue

**Remove** the computed `style` binding and `DEVICE_COLORS` / `DIRECTION_COLORS` imports.  
**Add** a computed `class` binding: `chip-gpio`, `chip-led`, `chip-input`, `chip-output`.

```css
.chip-gpio   { background: var(--sg-color-info-bg);    color: var(--sg-color-info-text); }
.chip-led    { background: var(--sg-color-purple-bg);  color: var(--sg-color-purple-text); }
.chip-input  { background: var(--sg-color-success-bg); color: var(--sg-color-success-text); }
.chip-output { background: var(--sg-color-warning-bg); color: var(--sg-color-warning-text); }
```

### Sidebar.vue

| Selector | Old | New |
|----------|-----|-----|
| `.sidebar` bg | `#1a1a2e` | `var(--sg-brand)` |
| `.nav-item:hover` bg | `rgba(79,195,247,0.08)` | `var(--sg-accent-subtle)` |
| `.nav-item.active` bg | `rgba(79,195,247,0.12)` | `var(--sg-accent-tint)` |
| `.nav-item.active` icon color | `#4fc3f7` | `var(--sg-accent)` |
| `.user-avatar` bg | `#4fc3f7` | `var(--sg-accent)` |
| `.nav-label` color | `#a0aec0` | `var(--sg-text-faint)` |
| `.logout-btn:hover` bg | `rgba(244,67,54,0.1)` | `var(--sg-danger-subtle)` |
| `.logout-btn:hover` color | (red hex) | `var(--sg-color-danger)` |

**Accessibility addition:**  
Add `:aria-current="isActive ? 'page' : undefined"` to each nav item element.

---

## Section 3: JS Constants Consolidation

### `src/constants/deviceTypes.js`

Remove these exports entirely — they are replaced by CSS classes:
- `DEVICE_COLORS`
- `DIRECTION_COLORS`  
- `STATUS_COLORS`

Keep all non-color exports (string enums, labels, etc.).

Update any import sites that referenced these constants.

---

## Section 4: View Migrations

All views under `src/views/` receive mechanical token substitution. No structural or template changes.

### Global substitution map

| Hardcoded value | Token |
|----------------|-------|
| `#1a1a2e` | `var(--sg-brand)` |
| `#0f0f1a` | `var(--sg-brand)` |
| `#718096` | `var(--sg-text-muted)` |
| `#4a5568` | `var(--sg-text-muted)` |
| `#a0aec0` | `var(--sg-text-faint)` |
| `#2d3748` | `var(--sg-text-muted)` |
| `#ffffff`, `#fff` | `var(--sg-bg-card)` |
| `#f7f8fc` | `var(--sg-bg-page)` |
| `#f4f6fb` | `var(--sg-bg-panel)` |
| `#e2e8f0` | `var(--sg-border)` |
| `#cbd5e0` | `var(--sg-border-input)` |
| `#4fc3f7` (accent) | `var(--sg-accent)` |
| `#2ba4d0`, `#0284c7` | `var(--sg-accent-hover)` |
| `rgba(79,195,247,0.08)` | `var(--sg-accent-subtle)` |
| `rgba(79,195,247,0.12)` | `var(--sg-accent-tint)` |
| `#e05252`, `#c53030` | `var(--sg-color-danger)` |
| `#9b2c2c` | `var(--sg-color-danger-text)` |
| `#fde0e0`, `#fff5f5` | `var(--sg-color-danger-bg)` |
| `#fc8181`, `#fca5a5` | `var(--sg-color-danger-bg)` |
| `#ed8936`, `#f5a623` | `var(--sg-color-warning)` |
| `#38c97a` | `var(--sg-color-success)` |
| `#ebf8ff` | `var(--sg-color-info-bg)` |
| `#fffbeb` | `var(--sg-color-warning-bg)` |
| Box shadows | `var(--sg-shadow-sm/md/lg)` (match by elevation) |

### Views to migrate

- `src/views/RangesView.vue` / `RangeListView.vue` / `RangeDetailView.vue`
- `src/views/SmartBoxesView.vue` / `SmartBoxListView.vue` / `SmartBoxDetailView.vue`
- `src/views/DeviceTypeGroupsView.vue` / `DeviceTypesView.vue` / `DeviceTypeView.vue`
- `src/views/FirmwareConfigsView.vue`
- `src/views/UsersView.vue` / `ProfileView.vue`
- `src/views/LoginView.vue`
- `src/views/competition/` (all files)
- `src/views/shooter/` (all files)
- `src/App.vue`

---

## Definition of Done

- [ ] All 12 palette blocks in `style.css` have the 15 new semantic color tokens
- [ ] `:root` has spacing, typography, shadow, and opacity-mix tokens
- [ ] All 6 atomic components + Sidebar use only `var(--sg-*)` in scoped styles
- [ ] `StatusDot` and `TypeChip` use CSS classes, not inline styles
- [ ] `DEVICE_COLORS`, `DIRECTION_COLORS`, `STATUS_COLORS` removed from `deviceTypes.js`
- [ ] All views pass a grep for hardcoded hex — zero results
- [ ] `npm run lint` passes with no warnings
- [ ] `npm run test` passes
- [ ] Palette switcher visually changes all UI elements
