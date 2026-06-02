# Design System Token Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate every hardcoded color/spacing/shadow value in smart-ground-ui to CSS custom properties so the 12-palette theme switcher actually works.

**Architecture:** Top-down — extend the token layer first (main.css + color-palettes.css), then migrate the 6 atomic components, then sweep all 19 view files with mechanical substitution.

**Tech Stack:** Vue 3, Vite, CSS custom properties, PowerShell for batch substitutions

---

## File Map

| File | Change |
|------|--------|
| `src/assets/main.css` | Add 15 semantic color tokens + spacing/typography/shadow scales + opacity-mix tokens; fix body and `:focus-visible` |
| `src/styles/color-palettes.css` | Add `--sg-*` variable overrides to all 12 palette blocks + default `:root` |
| `src/components/Button.vue` | Replace hardcoded hex; add `aria-disabled` and `ariaLabel` prop |
| `src/components/Badge.vue` | Replace hardcoded hex with tokens |
| `src/components/FormField.vue` | Replace hardcoded hex + font sizes with tokens |
| `src/components/StatusDot.vue` | Remove JS inline styles; add CSS class-based coloring |
| `src/components/TypeChip.vue` | Remove JS inline styles; add CSS class-based coloring |
| `src/components/Sidebar.vue` | Replace all hardcoded hex + rgba; add `aria-current` |
| `src/constants/deviceTypes.js` | Remove `DEVICE_COLORS`, `DIRECTION_COLORS`, `STATUS_COLORS`, `TYPE_COLORS` |
| `src/views/admin/*.vue` (9 files) | Mechanical hex→token substitution |
| `src/views/shooter/*.vue` (7 files) | Mechanical hex→token substitution |
| `src/views/LoginView.vue` | Mechanical hex→token substitution |
| `src/views/NoAccessView.vue` | Mechanical hex→token substitution |
| `src/App.vue` | Mechanical hex→token substitution |

---

## Task 1: Extend main.css — semantic color + neutral tokens

**Files:**
- Modify: `src/assets/main.css`

- [ ] **Step 1: Add semantic color tokens, neutral tokens, and fix body/focus-visible**

Replace the existing `:root` block and body rule in `src/assets/main.css` with the following. Preserve all existing content above and below — only the `:root` block and `body` rule change:

```css
/* Global Styles */
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

html,
body,
#app {
  height: 100%;
  width: 100%;
}

:root {
  /* Admin UI core palette — overridden per palette in color-palettes.css */
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

  /* Semantic status colors — overridden per palette in color-palettes.css */
  --sg-color-success: #38c97a;
  --sg-color-success-bg: #d4f5e2;
  --sg-color-success-text: #1e6640;
  --sg-color-warning: #ed8936;
  --sg-color-warning-bg: #fff3d4;
  --sg-color-warning-text: #8a5a00;
  --sg-color-danger: #e05252;
  --sg-color-danger-bg: #fde0e0;
  --sg-color-danger-text: #9b2c2c;
  --sg-color-info-bg: #dbeffe;
  --sg-color-info-text: #1a5fa0;
  --sg-color-neutral-bg: #e8edf0;
  --sg-color-neutral-text: #555555;
  --sg-color-purple-bg: #ede8ff;
  --sg-color-purple-text: #4a2da0;

  /* Spacing scale */
  --sg-space-1: 0.25rem;
  --sg-space-2: 0.5rem;
  --sg-space-3: 0.75rem;
  --sg-space-4: 1rem;
  --sg-space-6: 1.5rem;
  --sg-space-8: 2rem;

  /* Typography scale */
  --sg-text-xs: 0.72rem;
  --sg-text-sm: 0.85rem;
  --sg-text-base: 1rem;
  --sg-text-lg: 1.125rem;
  --sg-text-xl: 1.25rem;
  --sg-text-2xl: 1.5rem;

  /* Shadow scale */
  --sg-shadow-sm: 0 1px 3px rgba(0, 0, 0, 0.06);
  --sg-shadow-md: 0 2px 8px rgba(0, 0, 0, 0.08);
  --sg-shadow-lg: 0 4px 16px rgba(0, 0, 0, 0.10);

  /* Accent/danger with opacity — used for hover/active backgrounds */
  --sg-accent-subtle: color-mix(in srgb, var(--sg-accent) 8%, transparent);
  --sg-accent-tint: color-mix(in srgb, var(--sg-accent) 12%, transparent);
  --sg-danger-subtle: color-mix(in srgb, var(--sg-color-danger) 10%, transparent);

  /* Werfer Remote Colors */
  --werfer-pine-green: #1c3420;
  --werfer-parchment-light: #e8dfd0;
  --werfer-parchment-medium: #f0e8d8;
  --werfer-cream: #fff8f2;
  --werfer-orange-accent: #c85a0e;
  --werfer-orange-light: #f2cdb0;
  --werfer-green-status: #28a050;
  --werfer-green-success: #1a6830;
  --werfer-red-danger: #b82010;
  --werfer-orange-warn: #c89020;

  /* Werfer Typography */
  --font-serif: 'Playfair Display', serif;
  --font-sans: 'DM Sans', sans-serif;
  --font-mono: 'DM Mono', monospace;
}

body {
  font-family: system-ui, -apple-system, sans-serif;
  background: var(--sg-bg-page);
  color: var(--sg-brand);
  margin: 0;
  padding: 0;
}

input,
select,
textarea,
button {
  font-family: inherit;
}

/* Remove default button styles */
button {
  border: none;
  background: none;
  color: inherit;
  cursor: pointer;
  padding: 0;
  margin: 0;
}

/* Scrollbar styling */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: #9ca3af;
}

/* Accessibility */
:focus-visible {
  outline: 2px solid var(--sg-accent);
  outline-offset: 2px;
}

/* Remove focus outline for mouse users */
:focus:not(:focus-visible) {
  outline: none;
}
```

- [ ] **Step 2: Verify the file saved correctly**

```bash
grep "sg-color-success" src/assets/main.css
grep "sg-shadow-sm" src/assets/main.css
grep "sg-space-4" src/assets/main.css
```

Expected: each grep returns one matching line.

- [ ] **Step 3: Commit**

```bash
git add src/assets/main.css
git commit -m "[ui] Design system: add semantic color, spacing, typography, and shadow tokens to main.css"
```

---

## Task 2: Extend color-palettes.css — add --sg-* overrides per palette

**Files:**
- Modify: `src/styles/color-palettes.css`

Each palette block needs `--sg-*` variable overrides so that selecting a palette overrides the defaults defined in main.css. Replace the entire file content with the following:

- [ ] **Step 1: Replace color-palettes.css**

```css
/* Color Palette Variations for Smart Ground UI */
/* Each block overrides --sg-* variables so all components respond to palette switching */

/* ────────────────────────────────────────────────────────── */
/* PALETTE 1: Current (Base) - Dark Navy + Cyan */
/* ────────────────────────────────────────────────────────── */
:root[data-palette="current"] {
  --color-primary: #4fc3f7;
  --color-primary-dark: #1a5fa0;
  --color-primary-light: #81d4fa;
  --color-neutral: #1a1a2e;
  --color-neutral-light: #2d3748;
  --color-bg-main: #f7f8fc;
  --color-bg-card: #ffffff;
  --color-bg-hover: #f9fafb;
  --color-text-primary: #1a1a2e;
  --color-text-secondary: #4a5568;
  --color-text-muted: #718096;
  --color-text-light: #a0aec0;
  --color-border: #f0f4f8;
  --color-border-light: #e2e8f0;
  --color-success: #48bb78;
  --color-warning: #ed8936;
  --color-error: #f56565;
  --color-sidebar-bg: #1a1a2e;
  --color-sidebar-text: #ffffff;
  --color-sidebar-text-muted: #a0aec0;

  /* sg-* overrides */
  --sg-brand: #1a1a2e;
  --sg-brand-hover: #2d3748;
  --sg-accent: #4fc3f7;
  --sg-accent-hover: #1a5fa0;
  --sg-text-primary: #1a1a2e;
  --sg-text-muted: #718096;
  --sg-text-faint: #a0aec0;
  --sg-bg-page: #f7f8fc;
  --sg-bg-card: #ffffff;
  --sg-bg-panel: #f9fafb;
  --sg-border: #e2e8f0;
  --sg-border-input: #cbd5e0;
  --sg-color-success: #48bb78;
  --sg-color-warning: #ed8936;
  --sg-color-danger: #f56565;
}

/* ────────────────────────────────────────────────────────── */
/* PALETTE 2: Professional - Dark Blue + Teal */
/* ────────────────────────────────────────────────────────── */
:root[data-palette="professional"] {
  --color-primary: #0ea5e9;
  --color-primary-dark: #0369a1;
  --color-primary-light: #38bdf8;
  --color-neutral: #1e293b;
  --color-neutral-light: #334155;
  --color-bg-main: #f8fafc;
  --color-bg-card: #ffffff;
  --color-bg-hover: #f1f5f9;
  --color-text-primary: #1e293b;
  --color-text-secondary: #475569;
  --color-text-muted: #64748b;
  --color-text-light: #94a3b8;
  --color-border: #f1f5f9;
  --color-border-light: #e2e8f0;
  --color-success: #10b981;
  --color-warning: #f59e0b;
  --color-error: #ef4444;
  --color-sidebar-bg: #1e293b;
  --color-sidebar-text: #ffffff;
  --color-sidebar-text-muted: #94a3b8;

  --sg-brand: #1e293b;
  --sg-brand-hover: #334155;
  --sg-accent: #0ea5e9;
  --sg-accent-hover: #0369a1;
  --sg-text-primary: #1e293b;
  --sg-text-muted: #64748b;
  --sg-text-faint: #94a3b8;
  --sg-bg-page: #f8fafc;
  --sg-bg-card: #ffffff;
  --sg-bg-panel: #f1f5f9;
  --sg-border: #e2e8f0;
  --sg-border-input: #cbd5e0;
  --sg-color-success: #10b981;
  --sg-color-warning: #f59e0b;
  --sg-color-danger: #ef4444;
}

/* ────────────────────────────────────────────────────────── */
/* PALETTE 3: Modern - Deep Purple + Electric Blue */
/* ────────────────────────────────────────────────────────── */
:root[data-palette="modern"] {
  --color-primary: #3b82f6;
  --color-primary-dark: #1e40af;
  --color-primary-light: #60a5fa;
  --color-neutral: #1f2937;
  --color-neutral-light: #374151;
  --color-bg-main: #f9fafb;
  --color-bg-card: #ffffff;
  --color-bg-hover: #f3f4f6;
  --color-text-primary: #111827;
  --color-text-secondary: #374151;
  --color-text-muted: #6b7280;
  --color-text-light: #9ca3af;
  --color-border: #e5e7eb;
  --color-border-light: #d1d5db;
  --color-success: #06b6d4;
  --color-warning: #eab308;
  --color-error: #dc2626;
  --color-sidebar-bg: #1f2937;
  --color-sidebar-text: #ffffff;
  --color-sidebar-text-muted: #9ca3af;

  --sg-brand: #1f2937;
  --sg-brand-hover: #374151;
  --sg-accent: #3b82f6;
  --sg-accent-hover: #1e40af;
  --sg-text-primary: #111827;
  --sg-text-muted: #6b7280;
  --sg-text-faint: #9ca3af;
  --sg-bg-page: #f9fafb;
  --sg-bg-card: #ffffff;
  --sg-bg-panel: #f3f4f6;
  --sg-border: #d1d5db;
  --sg-border-input: #d1d5db;
  --sg-color-success: #06b6d4;
  --sg-color-warning: #eab308;
  --sg-color-danger: #dc2626;
}

/* ────────────────────────────────────────────────────────── */
/* PALETTE 4: Tech/Gaming - Dark + Neon Cyan & Purple */
/* ────────────────────────────────────────────────────────── */
:root[data-palette="gaming"] {
  --color-primary: #06b6d4;
  --color-primary-dark: #0891b2;
  --color-primary-light: #22d3ee;
  --color-neutral: #0f172a;
  --color-neutral-light: #1e293b;
  --color-bg-main: #0f172a;
  --color-bg-card: #1e293b;
  --color-bg-hover: #334155;
  --color-text-primary: #f1f5f9;
  --color-text-secondary: #cbd5e1;
  --color-text-muted: #94a3b8;
  --color-text-light: #64748b;
  --color-border: #475569;
  --color-border-light: #334155;
  --color-success: #10b981;
  --color-warning: #f59e0b;
  --color-error: #ef4444;
  --color-sidebar-bg: #0f172a;
  --color-sidebar-text: #f1f5f9;
  --color-sidebar-text-muted: #94a3b8;

  --sg-brand: #0f172a;
  --sg-brand-hover: #1e293b;
  --sg-accent: #06b6d4;
  --sg-accent-hover: #0891b2;
  --sg-text-primary: #f1f5f9;
  --sg-text-muted: #94a3b8;
  --sg-text-faint: #64748b;
  --sg-bg-page: #0f172a;
  --sg-bg-card: #1e293b;
  --sg-bg-panel: #334155;
  --sg-border: #334155;
  --sg-border-input: #475569;
  --sg-color-success: #10b981;
  --sg-color-warning: #f59e0b;
  --sg-color-danger: #ef4444;
}

/* ────────────────────────────────────────────────────────── */
/* PALETTE 5: Vibrant - Dark Navy + Vibrant Orange */
/* ────────────────────────────────────────────────────────── */
:root[data-palette="vibrant"] {
  --color-primary: #ff6b35;
  --color-primary-dark: #d84315;
  --color-primary-light: #ff8a50;
  --color-neutral: #004e89;
  --color-neutral-light: #1a5f8c;
  --color-bg-main: #f5f5f5;
  --color-bg-card: #ffffff;
  --color-bg-hover: #f0f0f0;
  --color-text-primary: #004e89;
  --color-text-secondary: #1a5f8c;
  --color-text-muted: #52657a;
  --color-text-light: #7a8da1;
  --color-border: #e0e0e0;
  --color-border-light: #d0d0d0;
  --color-success: #1ec71f;
  --color-warning: #ffa500;
  --color-error: #ff4444;
  --color-sidebar-bg: #004e89;
  --color-sidebar-text: #ffffff;
  --color-sidebar-text-muted: #a8bcc9;

  --sg-brand: #004e89;
  --sg-brand-hover: #1a5f8c;
  --sg-accent: #ff6b35;
  --sg-accent-hover: #d84315;
  --sg-text-primary: #004e89;
  --sg-text-muted: #52657a;
  --sg-text-faint: #7a8da1;
  --sg-bg-page: #f5f5f5;
  --sg-bg-card: #ffffff;
  --sg-bg-panel: #f0f0f0;
  --sg-border: #d0d0d0;
  --sg-border-input: #d0d0d0;
  --sg-color-success: #1ec71f;
  --sg-color-warning: #ffa500;
  --sg-color-danger: #ff4444;
}

/* ────────────────────────────────────────────────────────── */
/* PALETTE 6: Minimal - Clean Light + Soft Gray */
/* ────────────────────────────────────────────────────────── */
:root[data-palette="minimal"] {
  --color-primary: #2563eb;
  --color-primary-dark: #1d4ed8;
  --color-primary-light: #3b82f6;
  --color-neutral: #374151;
  --color-neutral-light: #6b7280;
  --color-bg-main: #ffffff;
  --color-bg-card: #f9fafb;
  --color-bg-hover: #f3f4f6;
  --color-text-primary: #111827;
  --color-text-secondary: #374151;
  --color-text-muted: #9ca3af;
  --color-text-light: #d1d5db;
  --color-border: #e5e7eb;
  --color-border-light: #f3f4f6;
  --color-success: #059669;
  --color-warning: #d97706;
  --color-error: #dc2626;
  --color-sidebar-bg: #f3f4f6;
  --color-sidebar-text: #111827;
  --color-sidebar-text-muted: #9ca3af;

  --sg-brand: #374151;
  --sg-brand-hover: #6b7280;
  --sg-accent: #2563eb;
  --sg-accent-hover: #1d4ed8;
  --sg-text-primary: #111827;
  --sg-text-muted: #9ca3af;
  --sg-text-faint: #d1d5db;
  --sg-bg-page: #ffffff;
  --sg-bg-card: #f9fafb;
  --sg-bg-panel: #f3f4f6;
  --sg-border: #e5e7eb;
  --sg-border-input: #e5e7eb;
  --sg-color-success: #059669;
  --sg-color-warning: #d97706;
  --sg-color-danger: #dc2626;
}

/* ────────────────────────────────────────────────────────── */
/* Default: Current palette (mirrors palette 1) */
/* ────────────────────────────────────────────────────────── */
:root {
  --color-primary: #4fc3f7;
  --color-primary-dark: #1a5fa0;
  --color-primary-light: #81d4fa;
  --color-neutral: #1a1a2e;
  --color-neutral-light: #2d3748;
  --color-bg-main: #f7f8fc;
  --color-bg-card: #ffffff;
  --color-bg-hover: #f9fafb;
  --color-text-primary: #1a1a2e;
  --color-text-secondary: #4a5568;
  --color-text-muted: #718096;
  --color-text-light: #a0aec0;
  --color-border: #f0f4f8;
  --color-border-light: #e2e8f0;
  --color-success: #48bb78;
  --color-warning: #ed8936;
  --color-error: #f56565;
  --color-sidebar-bg: #1a1a2e;
  --color-sidebar-text: #ffffff;
  --color-sidebar-text-muted: #a0aec0;
}

/* ────────────────────────────────────────────────────────── */
/* PALETTE 7: Professional Green - Enterprise Green + Slate */
/* ────────────────────────────────────────────────────────── */
:root[data-palette="professional-green"] {
  --color-primary: #27ae60;
  --color-primary-dark: #1e8449;
  --color-primary-light: #58d68d;
  --color-neutral: #1e293b;
  --color-neutral-light: #334155;
  --color-bg-main: #f8fafc;
  --color-bg-card: #ffffff;
  --color-bg-hover: #f1f5f9;
  --color-text-primary: #1e293b;
  --color-text-secondary: #475569;
  --color-text-muted: #64748b;
  --color-text-light: #94a3b8;
  --color-border: #f1f5f9;
  --color-border-light: #e2e8f0;
  --color-success: #16a34a;
  --color-warning: #f59e0b;
  --color-error: #ef4444;
  --color-sidebar-bg: #1e293b;
  --color-sidebar-text: #ffffff;
  --color-sidebar-text-muted: #94a3b8;

  --sg-brand: #1e293b;
  --sg-brand-hover: #334155;
  --sg-accent: #27ae60;
  --sg-accent-hover: #1e8449;
  --sg-text-primary: #1e293b;
  --sg-text-muted: #64748b;
  --sg-text-faint: #94a3b8;
  --sg-bg-page: #f8fafc;
  --sg-bg-card: #ffffff;
  --sg-bg-panel: #f1f5f9;
  --sg-border: #e2e8f0;
  --sg-border-input: #cbd5e0;
  --sg-color-success: #16a34a;
  --sg-color-warning: #f59e0b;
  --sg-color-danger: #ef4444;
}

/* ────────────────────────────────────────────────────────── */
/* PALETTE 8: Vibrant Green - Navy + Bright Green */
/* ────────────────────────────────────────────────────────── */
:root[data-palette="vibrant-green"] {
  --color-primary: #2ecc71;
  --color-primary-dark: #27ae60;
  --color-primary-light: #52e89f;
  --color-neutral: #004e89;
  --color-neutral-light: #1a5f8c;
  --color-bg-main: #f5f5f5;
  --color-bg-card: #ffffff;
  --color-bg-hover: #f0f0f0;
  --color-text-primary: #004e89;
  --color-text-secondary: #1a5f8c;
  --color-text-muted: #52657a;
  --color-text-light: #7a8da1;
  --color-border: #e0e0e0;
  --color-border-light: #d0d0d0;
  --color-success: #27ae60;
  --color-warning: #ffa500;
  --color-error: #ff4444;
  --color-sidebar-bg: #004e89;
  --color-sidebar-text: #ffffff;
  --color-sidebar-text-muted: #a8bcc9;

  --sg-brand: #004e89;
  --sg-brand-hover: #1a5f8c;
  --sg-accent: #2ecc71;
  --sg-accent-hover: #27ae60;
  --sg-text-primary: #004e89;
  --sg-text-muted: #52657a;
  --sg-text-faint: #7a8da1;
  --sg-bg-page: #f5f5f5;
  --sg-bg-card: #ffffff;
  --sg-bg-panel: #f0f0f0;
  --sg-border: #d0d0d0;
  --sg-border-input: #d0d0d0;
  --sg-color-success: #27ae60;
  --sg-color-warning: #ffa500;
  --sg-color-danger: #ff4444;
}

/* ────────────────────────────────────────────────────────── */
/* PALETTE 9: Eco Professional - Emerald + Forest */
/* ────────────────────────────────────────────────────────── */
:root[data-palette="eco-professional"] {
  --color-primary: #10b981;
  --color-primary-dark: #047857;
  --color-primary-light: #6ee7b7;
  --color-neutral: #1b3a1b;
  --color-neutral-light: #2d5016;
  --color-bg-main: #f5f9f2;
  --color-bg-card: #ffffff;
  --color-bg-hover: #eef6eb;
  --color-text-primary: #1b3a1b;
  --color-text-secondary: #2d5016;
  --color-text-muted: #4a7c2c;
  --color-text-light: #6b9d4a;
  --color-border: #d1e8c0;
  --color-border-light: #c4ddb5;
  --color-success: #10b981;
  --color-warning: #f59e0b;
  --color-error: #ef4444;
  --color-sidebar-bg: #1b3a1b;
  --color-sidebar-text: #ffffff;
  --color-sidebar-text-muted: #a8c894;

  --sg-brand: #1b3a1b;
  --sg-brand-hover: #2d5016;
  --sg-accent: #10b981;
  --sg-accent-hover: #047857;
  --sg-text-primary: #1b3a1b;
  --sg-text-muted: #4a7c2c;
  --sg-text-faint: #6b9d4a;
  --sg-bg-page: #f5f9f2;
  --sg-bg-card: #ffffff;
  --sg-bg-panel: #eef6eb;
  --sg-border: #c4ddb5;
  --sg-border-input: #c4ddb5;
  --sg-color-success: #10b981;
  --sg-color-warning: #f59e0b;
  --sg-color-danger: #ef4444;
}

/* ────────────────────────────────────────────────────────── */
/* PALETTE 10: Active Green - Dark + Neon Green */
/* ────────────────────────────────────────────────────────── */
:root[data-palette="active-green"] {
  --color-primary: #00ff00;
  --color-primary-dark: #00cc00;
  --color-primary-light: #66ff66;
  --color-neutral: #0f172a;
  --color-neutral-light: #1e293b;
  --color-bg-main: #0f172a;
  --color-bg-card: #1e293b;
  --color-bg-hover: #334155;
  --color-text-primary: #f1f5f9;
  --color-text-secondary: #cbd5e1;
  --color-text-muted: #94a3b8;
  --color-text-light: #64748b;
  --color-border: #475569;
  --color-border-light: #334155;
  --color-success: #00ff00;
  --color-warning: #ffff00;
  --color-error: #ff4444;
  --color-sidebar-bg: #0f172a;
  --color-sidebar-text: #f1f5f9;
  --color-sidebar-text-muted: #94a3b8;

  --sg-brand: #0f172a;
  --sg-brand-hover: #1e293b;
  --sg-accent: #00ff00;
  --sg-accent-hover: #00cc00;
  --sg-text-primary: #f1f5f9;
  --sg-text-muted: #94a3b8;
  --sg-text-faint: #64748b;
  --sg-bg-page: #0f172a;
  --sg-bg-card: #1e293b;
  --sg-bg-panel: #334155;
  --sg-border: #334155;
  --sg-border-input: #475569;
  --sg-color-success: #00ff00;
  --sg-color-warning: #ffff00;
  --sg-color-danger: #ff4444;
}

/* ────────────────────────────────────────────────────────── */
/* PALETTE 11: Sage Green - Soft Green + Warm Gray */
/* ────────────────────────────────────────────────────────── */
:root[data-palette="sage-green"] {
  --color-primary: #6b8e7f;
  --color-primary-dark: #557367;
  --color-primary-light: #9db0a8;
  --color-neutral: #42464a;
  --color-neutral-light: #5a5e62;
  --color-bg-main: #f5f7f6;
  --color-bg-card: #ffffff;
  --color-bg-hover: #f0f3f1;
  --color-text-primary: #42464a;
  --color-text-secondary: #5a5e62;
  --color-text-muted: #7a7e82;
  --color-text-light: #a0a4a8;
  --color-border: #d8dcdb;
  --color-border-light: #d0d4d3;
  --color-success: #6b8e7f;
  --color-warning: #d4a574;
  --color-error: #d8756b;
  --color-sidebar-bg: #42464a;
  --color-sidebar-text: #ffffff;
  --color-sidebar-text-muted: #a8aaac;

  --sg-brand: #42464a;
  --sg-brand-hover: #5a5e62;
  --sg-accent: #6b8e7f;
  --sg-accent-hover: #557367;
  --sg-text-primary: #42464a;
  --sg-text-muted: #7a7e82;
  --sg-text-faint: #a0a4a8;
  --sg-bg-page: #f5f7f6;
  --sg-bg-card: #ffffff;
  --sg-bg-panel: #f0f3f1;
  --sg-border: #d0d4d3;
  --sg-border-input: #d0d4d3;
  --sg-color-success: #6b8e7f;
  --sg-color-warning: #d4a574;
  --sg-color-danger: #d8756b;
}

/* ────────────────────────────────────────────────────────── */
/* PALETTE 12: Forest Teal - Deep Forest + Teal Accent */
/* ────────────────────────────────────────────────────────── */
:root[data-palette="forest-teal"] {
  --color-primary: #0d9488;
  --color-primary-dark: #0a6b61;
  --color-primary-light: #2dd4bf;
  --color-neutral: #1b3a1b;
  --color-neutral-light: #2d5016;
  --color-bg-main: #f5f9f2;
  --color-bg-card: #ffffff;
  --color-bg-hover: #eef6eb;
  --color-text-primary: #1b3a1b;
  --color-text-secondary: #2d5016;
  --color-text-muted: #4a7c2c;
  --color-text-light: #6b9d4a;
  --color-border: #d1e8c0;
  --color-border-light: #c4ddb5;
  --color-success: #10b981;
  --color-warning: #f59e0b;
  --color-error: #ef4444;
  --color-sidebar-bg: #1b3a1b;
  --color-sidebar-text: #ffffff;
  --color-sidebar-text-muted: #a8c894;

  --sg-brand: #1b3a1b;
  --sg-brand-hover: #2d5016;
  --sg-accent: #0d9488;
  --sg-accent-hover: #0a6b61;
  --sg-text-primary: #1b3a1b;
  --sg-text-muted: #4a7c2c;
  --sg-text-faint: #6b9d4a;
  --sg-bg-page: #f5f9f2;
  --sg-bg-card: #ffffff;
  --sg-bg-panel: #eef6eb;
  --sg-border: #c4ddb5;
  --sg-border-input: #c4ddb5;
  --sg-color-success: #10b981;
  --sg-color-warning: #f59e0b;
  --sg-color-danger: #ef4444;
}

/* Color palette switcher button (utility) */
.palette-switcher {
  position: fixed;
  bottom: 20px;
  right: 20px;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.8);
  border-radius: 8px;
  padding: 12px;
  color: white;
}

.palette-switcher select {
  padding: 8px;
  border-radius: 4px;
  border: 1px solid #ccc;
  background: white;
  color: black;
  font-size: 12px;
  cursor: pointer;
}
```

- [ ] **Step 2: Verify palette overrides are present**

```bash
grep "sg-brand" src/styles/color-palettes.css | wc -l
```

Expected: 24 (12 palette blocks × 2 occurrences each of `--sg-brand` and `--sg-brand-hover`)

- [ ] **Step 3: Commit**

```bash
git add src/styles/color-palettes.css
git commit -m "[ui] Design system: add --sg-* overrides to all 12 palette blocks"
```

---

## Task 3: Migrate Button.vue

**Files:**
- Modify: `src/components/Button.vue`

- [ ] **Step 1: Replace Button.vue with token-based version**

```vue
<template>
  <button
    class="btn"
    :class="`btn-${variant} btn-${size}`"
    :disabled="disabled"
    :aria-disabled="disabled || undefined"
    :aria-label="ariaLabel || undefined"
    @click="$emit('click')"
  >
    <span v-if="icon" class="btn-icon" v-html="icon"></span>
    <slot />
  </button>
</template>

<script setup>
defineProps({
  variant: {
    type: String,
    default: 'primary',
    validator: (v) => ['primary', 'ghost', 'danger'].includes(v),
  },
  size: {
    type: String,
    default: 'md',
    validator: (v) => ['sm', 'md', 'icon-only'].includes(v),
  },
  icon: String,
  disabled: Boolean,
  ariaLabel: String,
});

defineEmits(['click']);
</script>

<style scoped>
.btn {
  display: flex;
  align-items: center;
  gap: 5px;
  border: none;
  cursor: pointer;
  font-family: inherit;
  font-weight: 500;
  transition: all 0.15s;
  border-radius: var(--sg-radius-btn);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-sm {
  padding: 5px 11px;
  font-size: var(--sg-text-xs);
}

.btn-md {
  padding: 8px 16px;
  font-size: var(--sg-text-sm);
}

.btn-icon-only {
  padding: 10px;
  width: 44px;
  height: 44px;
  justify-content: center;
}

.btn-primary {
  background: var(--sg-brand);
  color: var(--sg-bg-card);
}

.btn-primary:hover:not(:disabled) {
  background: var(--sg-accent);
}

.btn-ghost {
  background: transparent;
  border: 1px solid var(--sg-border);
  color: var(--sg-text-muted);
}

.btn-ghost:hover:not(:disabled) {
  background: var(--sg-bg-panel);
}

.btn-danger {
  background: transparent;
  border: 1px solid var(--sg-color-danger-bg);
  color: var(--sg-color-danger);
}

.btn-danger:hover:not(:disabled) {
  background: var(--sg-color-danger-bg);
}

.btn-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
```

- [ ] **Step 2: Verify no hardcoded hex remains**

```bash
grep -n "#[0-9a-fA-F]\{3,6\}" src/components/Button.vue
```

Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add src/components/Button.vue
git commit -m "[ui] Design system: migrate Button.vue to CSS tokens"
```

---

## Task 4: Migrate Badge.vue

**Files:**
- Modify: `src/components/Badge.vue`

- [ ] **Step 1: Replace Badge.vue with token-based version**

```vue
<template>
  <span class="badge" :class="`badge-${color}`">
    <slot />
  </span>
</template>

<script setup>
defineProps({
  color: {
    type: String,
    default: 'gray',
    validator: (v) => ['blue', 'green', 'red', 'gray', 'warn'].includes(v),
  },
});
</script>

<style scoped>
.badge {
  display: inline-block;
  border-radius: 99px;
  padding: 2px 9px;
  font-size: 11.5px;
  font-weight: 600;
}

.badge-blue {
  background: var(--sg-color-info-bg);
  color: var(--sg-color-info-text);
}

.badge-green {
  background: var(--sg-color-success-bg);
  color: var(--sg-color-success-text);
}

.badge-red {
  background: var(--sg-color-danger-bg);
  color: var(--sg-color-danger-text);
}

.badge-gray {
  background: var(--sg-color-neutral-bg);
  color: var(--sg-color-neutral-text);
}

.badge-warn {
  background: var(--sg-color-warning-bg);
  color: var(--sg-color-warning-text);
}
</style>
```

- [ ] **Step 2: Verify no hardcoded hex remains**

```bash
grep -n "#[0-9a-fA-F]\{3,6\}" src/components/Badge.vue
```

Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add src/components/Badge.vue
git commit -m "[ui] Design system: migrate Badge.vue to CSS tokens"
```

---

## Task 5: Migrate FormField.vue

**Files:**
- Modify: `src/components/FormField.vue`

- [ ] **Step 1: Replace FormField.vue with token-based version**

```vue
<template>
  <div class="form-field">
    <label v-if="label" class="form-label">{{ label }}</label>
    <slot />
    <span v-if="hint" class="form-hint">{{ hint }}</span>
  </div>
</template>

<script setup>
defineProps({
  label: String,
  hint: String,
});
</script>

<style scoped>
.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.form-label {
  font-size: var(--sg-text-sm);
  font-weight: 500;
  color: var(--sg-text-muted);
}

.form-hint {
  font-size: var(--sg-text-xs);
  color: var(--sg-text-faint);
  margin-top: 0.2rem;
}
</style>
```

- [ ] **Step 2: Verify no hardcoded hex remains**

```bash
grep -n "#[0-9a-fA-F]\{3,6\}" src/components/FormField.vue
```

Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add src/components/FormField.vue
git commit -m "[ui] Design system: migrate FormField.vue to CSS tokens"
```

---

## Task 6: Migrate StatusDot.vue — remove inline JS styles

**Files:**
- Modify: `src/components/StatusDot.vue`

- [ ] **Step 1: Replace StatusDot.vue — swap computed style for computed class**

```vue
<template>
  <span
    class="status-dot"
    :class="`dot-${status}`"
    :aria-label="`Status: ${statusLabel}`"
    role="img"
  />
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  status: {
    type: String,
    default: 'online',
  },
});

const statusLabel = computed(() => {
  const labels = {
    online: 'Online',
    offline: 'Offline',
    error: 'Error',
    warning: 'Warning',
  };
  return labels[props.status] || props.status;
});
</script>

<style scoped>
.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot-online {
  background: var(--sg-color-success);
}

.dot-offline {
  background: var(--sg-color-danger);
}

.dot-warning {
  background: var(--sg-color-warning);
}

.dot-error {
  background: var(--sg-color-danger);
}
</style>
```

- [ ] **Step 2: Verify STATUS_COLORS import is gone and no hardcoded hex**

```bash
grep -n "STATUS_COLORS\|#[0-9a-fA-F]\{3,6\}" src/components/StatusDot.vue
```

Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add src/components/StatusDot.vue
git commit -m "[ui] Design system: migrate StatusDot.vue to CSS class-based coloring"
```

---

## Task 7: Migrate TypeChip.vue — remove inline JS styles

**Files:**
- Modify: `src/components/TypeChip.vue`

- [ ] **Step 1: Replace TypeChip.vue — swap computed style for computed class**

The `device` prop maps to `chip-gpio` / `chip-led`. The `type` (direction) prop maps to `chip-input` / `chip-output`. The fallback class is `chip-default`.

```vue
<template>
  <span class="type-chip" :class="chipClass">
    {{ label }}
  </span>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  device: {
    type: String,
    default: null,
  },
  direction: {
    type: String,
    default: null,
  },
  type: {
    type: String,
    default: null,
  },
});

const label = computed(() => {
  if (props.device && props.direction) {
    return `${props.device} / ${props.direction}`;
  }
  return props.type || 'Unknown';
});

const chipClass = computed(() => {
  if (props.device) {
    return `chip-${props.device.toLowerCase()}`;
  }
  if (props.type) {
    return `chip-${props.type.toLowerCase()}`;
  }
  return 'chip-default';
});
</script>

<style scoped>
.type-chip {
  display: inline-block;
  border-radius: 5px;
  padding: 2px 8px;
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.chip-gpio {
  background: var(--sg-color-info-bg);
  color: var(--sg-color-info-text);
}

.chip-led {
  background: var(--sg-color-purple-bg);
  color: var(--sg-color-purple-text);
}

.chip-input {
  background: var(--sg-color-success-bg);
  color: var(--sg-color-success-text);
}

.chip-output {
  background: var(--sg-color-warning-bg);
  color: var(--sg-color-warning-text);
}

.chip-default {
  background: var(--sg-color-neutral-bg);
  color: var(--sg-color-neutral-text);
}
</style>
```

- [ ] **Step 2: Verify DEVICE_COLORS / DIRECTION_COLORS imports are gone and no hardcoded hex**

```bash
grep -n "DEVICE_COLORS\|DIRECTION_COLORS\|#[0-9a-fA-F]\{3,6\}" src/components/TypeChip.vue
```

Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add src/components/TypeChip.vue
git commit -m "[ui] Design system: migrate TypeChip.vue to CSS class-based coloring"
```

---

## Task 8: Migrate Sidebar.vue

**Files:**
- Modify: `src/components/Sidebar.vue`

- [ ] **Step 1: Replace Sidebar.vue with token-based version**

Replace the entire `<style scoped>` block and add `aria-current` to the nav button:

```vue
<template>
  <aside class="sidebar">
    <div class="sidebar-header">
      <span class="brand-icon">📡</span>
      <span class="brand-name">Smart Ground</span>
    </div>

    <nav class="sidebar-nav">
      <button
        v-for="item in navItems"
        :key="item.id"
        :class="{ active: activeNav === item.id }"
        :aria-current="activeNav === item.id ? 'page' : undefined"
        class="nav-item"
        @click="handleNavClick(item.id)"
      >
        <Icons :icon="item.icon" :size="15" class="nav-icon" />
        <span class="nav-label">{{ item.label }}</span>
      </button>
    </nav>

    <div class="sidebar-footer">
      <router-link to="/profile" class="user-profile">
        <div class="user-avatar">{{ userAvatarLetter }}</div>
        <div class="user-info">
          <div class="user-name">{{ username }}</div>
        </div>
      </router-link>
      <button class="logout-btn" title="Logout" @click="handleLogout">
        <Icons icon="logout" :size="15" />
      </button>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/authStore.js';
import Icons from './Icons.vue';

const props = defineProps({
  activeNav: String,
});

defineEmits(['nav']);

const router = useRouter();
const authStore = useAuthStore();

const allNavItems = [
  { id: 'ranges', label: 'Plätze', icon: 'target', requiredPermission: 'MANAGE_RANGES' },
  { id: 'smartboxes', label: 'SmartBoxen', icon: 'wifi', requiredPermission: 'MANAGE_RANGES' },
  { id: 'competition', path: '/admin/wettkampf', label: 'Wettkampf', icon: 'award', requiredPermission: 'MANAGE_COMPETITIONS' },
  { id: 'passen', label: 'Passen', icon: 'program', requiredPermission: 'MANAGE_PASSE_TEMPLATES' },
  { id: 'users', label: 'Benutzer', icon: 'user', requiredPermission: 'MANAGE_USERS' },
  { id: 'profile', label: 'Profil', icon: 'user' },
];

const navItems = computed(() => {
  return allNavItems.filter(item => {
    if (item.requiredPermission && !authStore.hasPermission(item.requiredPermission)) {
      return false;
    }
    return true;
  });
});

const username = computed(() => {
  return authStore.displayName || 'Benutzer';
});

const userAvatarLetter = computed(() => {
  return username.value[0].toUpperCase();
});

const handleNavClick = (itemId) => {
  const item = allNavItems.find(i => i.id === itemId);
  router.push(item?.path ?? `/${itemId}`);
};

const handleLogout = () => {
  authStore.logout();
  router.push('/login');
};
</script>

<style scoped>
.sidebar {
  width: 210px;
  background: var(--sg-brand);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  height: 100vh;
  overflow-y: auto;
}

.sidebar-header {
  padding: 18px 18px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  gap: 8px;
}

.brand-icon {
  font-size: 20px;
}

.brand-name {
  font-size: 15.5px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.2px;
}

.sidebar-nav {
  flex: 1;
  padding: 12px 0;
  display: flex;
  flex-direction: column;
}

.nav-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 18px;
  background: transparent;
  border: none;
  border-left: 3px solid transparent;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
}

.nav-item:hover {
  background: var(--sg-accent-subtle);
}

.nav-item.active {
  background: var(--sg-accent-tint);
  border-left-color: var(--sg-accent);
}

.nav-icon {
  color: var(--sg-text-faint);
  transition: color 0.15s;
}

.nav-item.active .nav-icon {
  color: var(--sg-accent);
}

.nav-label {
  font-size: 13.5px;
  color: var(--sg-text-faint);
  font-weight: 400;
  transition: all 0.15s;
}

.nav-item.active .nav-label {
  color: #fff;
  font-weight: 600;
}

.sidebar-footer {
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  padding: 12px 18px;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  transition: opacity 0.2s;
}

.user-profile:hover {
  opacity: 0.8;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--sg-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: var(--sg-brand);
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 13px;
  color: #fff;
  font-weight: 600;
}

.user-email {
  font-size: 11px;
  color: var(--sg-text-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logout-btn {
  margin-top: 10px;
  width: 100%;
  padding: 8px;
  background: var(--sg-danger-subtle);
  border: 1px solid var(--sg-color-danger-bg);
  border-radius: 4px;
  cursor: pointer;
  color: var(--sg-color-danger);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.logout-btn:hover {
  background: var(--sg-color-danger-bg);
  border-color: var(--sg-color-danger);
}

/* Mobile: Icon-only mode */
@media (max-width: 768px) {
  .sidebar {
    width: 70px;
  }

  .sidebar-header {
    justify-content: center;
    padding: 18px 12px 16px;
  }

  .brand-name {
    display: none;
  }

  .brand-icon {
    font-size: 24px;
  }

  .nav-item {
    justify-content: center;
    padding: 12px;
    gap: 0;
    border-left: none;
    border-top: 3px solid transparent;
  }

  .nav-item:hover {
    background: var(--sg-accent-subtle);
  }

  .nav-item.active {
    background: var(--sg-accent-tint);
    border-top-color: var(--sg-accent);
  }

  .nav-label {
    display: none;
  }

  .sidebar-footer {
    padding: 12px;
    justify-content: center;
  }

  .user-profile {
    justify-content: center;
    gap: 0;
  }

  .user-info {
    display: none;
  }

  .user-avatar {
    width: 36px;
    height: 36px;
  }

  .logout-btn {
    margin-top: 8px;
    width: 100%;
    padding: 8px;
  }
}
</style>
```

- [ ] **Step 2: Verify no hardcoded hex or rgba with hex remains (white/black opacity is acceptable)**

```bash
grep -n "rgba(79\|rgba(244\|#4fc3f7\|#1a1a2e\|#718096\|#a0aec0\|#f44336" src/components/Sidebar.vue
```

Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add src/components/Sidebar.vue
git commit -m "[ui] Design system: migrate Sidebar.vue to CSS tokens + add aria-current"
```

---

## Task 9: Clean up deviceTypes.js

**Files:**
- Modify: `src/constants/deviceTypes.js`

- [ ] **Step 1: Remove color constant exports**

Replace the entire file with:

```javascript
export const STATUS_LABELS = {
  online: 'Online',
  offline: 'Offline',
  warn: 'Warnung',
};
```

- [ ] **Step 2: Verify no imports of removed exports break the build**

```bash
grep -rn "DEVICE_COLORS\|DIRECTION_COLORS\|STATUS_COLORS\|TYPE_COLORS" src/
```

Expected: no output (StatusDot and TypeChip were already migrated away from these).

- [ ] **Step 3: Commit**

```bash
git add src/constants/deviceTypes.js
git commit -m "[ui] Design system: remove JS color constants from deviceTypes.js"
```

---

## Task 10: Migrate admin views

**Files:**
- Modify: `src/views/admin/RangesView.vue`
- Modify: `src/views/admin/RangeDetailView.vue`
- Modify: `src/views/admin/SmartBoxesView.vue`
- Modify: `src/views/admin/UsersView.vue`
- Modify: `src/views/admin/ProfileView.vue`
- Modify: `src/views/admin/FirmwareConfigsView.vue`
- Modify: `src/views/admin/PlayerSetupView.vue`
- Modify: `src/views/admin/PassenAdminView.vue`
- Modify: `src/views/admin/WettkampfListView.vue`
- Modify: `src/views/admin/WettkampfDetailView.vue`

All changes are mechanical CSS token substitutions inside `<style scoped>` blocks. Run each substitution command from the project root (`smart-ground-ui/`).

- [ ] **Step 1: Apply color token substitutions to all admin views**

Run these PowerShell commands from the `smart-ground-ui` directory. Each command applies one substitution across all admin view files:

```powershell
$files = Get-ChildItem src/views/admin -Filter "*.vue" -Recurse | Select-Object -ExpandProperty FullName

$subs = @(
  @('#1a1a2e', 'var(--sg-brand)'),
  @('#0f0f1a', 'var(--sg-brand)'),
  @('#2d3748', 'var(--sg-text-muted)'),
  @('#4a5568', 'var(--sg-text-muted)'),
  @('#718096', 'var(--sg-text-muted)'),
  @('#a0aec0', 'var(--sg-text-faint)'),
  @('#ffffff(?![\da-fA-F])', 'var(--sg-bg-card)'),
  @('#fff(?![\da-fA-F])', 'var(--sg-bg-card)'),
  @('#f7f8fc', 'var(--sg-bg-page)'),
  @('#f4f6fb', 'var(--sg-bg-panel)'),
  @('#f9fafb', 'var(--sg-bg-panel)'),
  @('#e2e8f0', 'var(--sg-border)'),
  @('#cbd5e0', 'var(--sg-border-input)'),
  @('#4fc3f7', 'var(--sg-accent)'),
  @('#2ba4d0', 'var(--sg-accent-hover)'),
  @('#0284c7', 'var(--sg-accent-hover)'),
  @('rgba\(79,\s*195,\s*247,\s*0\.08\)', 'var(--sg-accent-subtle)'),
  @('rgba\(79,\s*195,\s*247,\s*0\.12\)', 'var(--sg-accent-tint)'),
  @('#e05252', 'var(--sg-color-danger)'),
  @('#c53030', 'var(--sg-color-danger)'),
  @('#9b2c2c', 'var(--sg-color-danger-text)'),
  @('#fde0e0', 'var(--sg-color-danger-bg)'),
  @('#fff5f5', 'var(--sg-color-danger-bg)'),
  @('#fc8181', 'var(--sg-color-danger-bg)'),
  @('#fca5a5', 'var(--sg-color-danger-bg)'),
  @('#ed8936', 'var(--sg-color-warning)'),
  @('#f5a623', 'var(--sg-color-warning)'),
  @('#38c97a', 'var(--sg-color-success)'),
  @('#ebf8ff', 'var(--sg-color-info-bg)'),
  @('#dbeffe', 'var(--sg-color-info-bg)'),
  @('#fffbeb', 'var(--sg-color-warning-bg)'),
  @('#fff3d4', 'var(--sg-color-warning-bg)')
)

foreach ($file in $files) {
  $content = Get-Content $file -Raw -Encoding UTF8
  foreach ($sub in $subs) {
    $content = $content -replace $sub[0], $sub[1]
  }
  Set-Content $file $content -Encoding UTF8 -NoNewline
}

Write-Host "Done. $($files.Count) files processed."
```

- [ ] **Step 2: Fix hardcoded color props in templates (Icons component)**

Some views pass hardcoded `color` props to the `Icons` component inline in templates, e.g. `color="#4fc3f7"`. Search for these and remove the `color` prop so the icon inherits its color from the surrounding CSS context:

```bash
grep -n 'color="#' src/views/admin/*.vue
```

For each match found, remove the `color="..."` attribute from that `<Icons>` tag. The icon will inherit color from its parent's CSS `color` property.

- [ ] **Step 3: Verify — check for remaining hardcoded hex in admin views**

```bash
grep -rn "#[0-9a-fA-F]\{6\}\b\|#[0-9a-fA-F]\{3\}\b" src/views/admin/
```

Expected: no output (or only values inside template strings/data that are not CSS, e.g. chart colors — inspect each hit and fix manually if needed).

- [ ] **Step 4: Commit**

```bash
git add src/views/admin/
git commit -m "[ui] Design system: migrate all admin views to CSS tokens"
```

---

## Task 11: Migrate shooter views, root views, and App.vue

**Files:**
- Modify: `src/views/shooter/*.vue` (7 files)
- Modify: `src/views/LoginView.vue`
- Modify: `src/views/NoAccessView.vue`
- Modify: `src/App.vue`

- [ ] **Step 1: Apply the same substitutions to shooter views, root views, and App.vue**

```powershell
$files = @(
  (Get-ChildItem src/views/shooter -Filter "*.vue" -Recurse),
  (Get-Item src/views/LoginView.vue),
  (Get-Item src/views/NoAccessView.vue),
  (Get-Item src/App.vue)
) | ForEach-Object { $_.FullName }

$subs = @(
  @('#1a1a2e', 'var(--sg-brand)'),
  @('#0f0f1a', 'var(--sg-brand)'),
  @('#2d3748', 'var(--sg-text-muted)'),
  @('#4a5568', 'var(--sg-text-muted)'),
  @('#718096', 'var(--sg-text-muted)'),
  @('#a0aec0', 'var(--sg-text-faint)'),
  @('#ffffff(?![\da-fA-F])', 'var(--sg-bg-card)'),
  @('#fff(?![\da-fA-F])', 'var(--sg-bg-card)'),
  @('#f7f8fc', 'var(--sg-bg-page)'),
  @('#f4f6fb', 'var(--sg-bg-panel)'),
  @('#f9fafb', 'var(--sg-bg-panel)'),
  @('#e2e8f0', 'var(--sg-border)'),
  @('#cbd5e0', 'var(--sg-border-input)'),
  @('#4fc3f7', 'var(--sg-accent)'),
  @('#2ba4d0', 'var(--sg-accent-hover)'),
  @('#0284c7', 'var(--sg-accent-hover)'),
  @('rgba\(79,\s*195,\s*247,\s*0\.08\)', 'var(--sg-accent-subtle)'),
  @('rgba\(79,\s*195,\s*247,\s*0\.12\)', 'var(--sg-accent-tint)'),
  @('#e05252', 'var(--sg-color-danger)'),
  @('#c53030', 'var(--sg-color-danger)'),
  @('#9b2c2c', 'var(--sg-color-danger-text)'),
  @('#fde0e0', 'var(--sg-color-danger-bg)'),
  @('#fff5f5', 'var(--sg-color-danger-bg)'),
  @('#fc8181', 'var(--sg-color-danger-bg)'),
  @('#fca5a5', 'var(--sg-color-danger-bg)'),
  @('#ed8936', 'var(--sg-color-warning)'),
  @('#f5a623', 'var(--sg-color-warning)'),
  @('#38c97a', 'var(--sg-color-success)'),
  @('#ebf8ff', 'var(--sg-color-info-bg)'),
  @('#dbeffe', 'var(--sg-color-info-bg)'),
  @('#fffbeb', 'var(--sg-color-warning-bg)'),
  @('#fff3d4', 'var(--sg-color-warning-bg)')
)

foreach ($file in $files) {
  $content = Get-Content $file -Raw -Encoding UTF8
  foreach ($sub in $subs) {
    $content = $content -replace $sub[0], $sub[1]
  }
  Set-Content $file $content -Encoding UTF8 -NoNewline
}

Write-Host "Done. $($files.Count) files processed."
```

- [ ] **Step 2: Fix hardcoded color props in shooter templates**

```bash
grep -rn 'color="#' src/views/shooter/ src/views/LoginView.vue src/views/NoAccessView.vue src/App.vue
```

For each match found, remove the `color="..."` prop from the `<Icons>` tag.

- [ ] **Step 3: Verify — check for remaining hardcoded hex**

```bash
grep -rn "#[0-9a-fA-F]\{6\}\b\|#[0-9a-fA-F]\{3\}\b" src/views/shooter/ src/views/LoginView.vue src/views/NoAccessView.vue src/App.vue
```

Expected: no output (or non-CSS hits — inspect each manually).

- [ ] **Step 4: Commit**

```bash
git add src/views/shooter/ src/views/LoginView.vue src/views/NoAccessView.vue src/App.vue
git commit -m "[ui] Design system: migrate shooter views and App.vue to CSS tokens"
```

---

## Task 12: Final verification

- [ ] **Step 1: Lint check**

```bash
npm run lint
```

Expected: no errors or warnings.

- [ ] **Step 2: Test suite**

```bash
npm run test
```

Expected: all tests pass.

- [ ] **Step 3: Full hex scan — zero hardcoded colors in src/**

```bash
grep -rn "#[0-9a-fA-F]\{6\}\b\|#[0-9a-fA-F]\{3\}\b" src/components/ src/views/
```

Expected: no output. Any remaining hits should be investigated — if they are semantic values that intentionally don't need palette theming (e.g., werfer remote colors), move them to `:root` as named tokens rather than leaving them inline.

- [ ] **Step 4: Verify palette switcher works visually**

```bash
npm run dev
```

Open http://localhost:5173, log in, and use the palette switcher. Confirm that switching between palettes (e.g., "current" → "gaming" → "professional") changes the sidebar, buttons, badges, and page background colors.

- [ ] **Step 5: Final commit**

```bash
git add -A
git commit -m "[ui] Design system: token migration complete — palette switcher fully functional"
```
