# Data Lifecycle Layer — Design

**Date:** 2026-07-06
**Status:** Approved (design), pending implementation plan

## Problem

The UI has no consistent data-lifecycle logic. Two symptoms:

1. **Stale data survives a user switch.** Log out as UserA, log in as UserB → UserB still
   sees UserA's Serien (and other domain data).
2. **Admin changes are invisible until reload.** An admin removes a Position; the shooter
   does not see the change until the page is manually reloaded.

### Root causes

- **Problem 1:** `authStore.logout()` (`src/stores/authStore.js:82`) clears only auth state
  (token / profile / permissions). All other Pinia stores keep their in-memory refs
  (`passeStore.savedSerien`, `rangeStore`, `competitionEventStore`, …). Additionally the
  router's `_storesInitialized` flag (`src/router/index.js:111`) is a module-level boolean
  set once and never reset, so a re-login skips the lazy-init block entirely — nothing
  refetches. Result: UserB sees UserA's data.
- **Problem 2:** Domain data is fetched once at first authenticated navigation and cached in
  refs indefinitely. There is no revalidation on navigation, no polling, and no push. A
  manual reload "fixes" it only because it re-runs the full init.

## Decisions

- **Freshness target:** near-real-time (seconds), achieved via lightweight polling on active
  views — consistent with the existing OTA / Stechen light-polling convention. STOMP push
  stays parked per `smart-ground-ui/CLAUDE.md` (backend does not emit yet).
- **Approach:** in-house Pinia layer. Keep the store → service architecture and the
  "no API calls in components" rule. No new dependencies (no TanStack Query).
- **Scope framing:** first-pass solution. "Well enough" for the outdated-data problem; not a
  full caching framework.

## Design

Three pieces, all inside the current store → service architecture.

### Piece 1 — Universal store reset (`stores/plugins/resettable.js`)

Pinia setup stores (the factory pattern used here) do not get `$reset()` for free — only
option stores do. A small Pinia plugin snapshots each store's initial state at creation and
attaches a working `$reset()`:

```js
// stores/plugins/resettable.js
import { toRaw } from 'vue';

export function resettablePlugin({ store }) {
  const initial = structuredClone(toRaw(store.$state));
  store.$reset = () => store.$patch(structuredClone(initial));
}
```

Registered on the Pinia instance in `main.js`. Every store gets a real `$reset()` with zero
per-store boilerplate.

**Caveat — polling stores.** Stores that own timers (e.g. `otaStore`) must stop their timers
on reset, not just clear state. Because the Pinia plugin assigns `store.$reset` *after* the
store factory returns (clobbering any factory-returned override), the reset caller — not the
store — stops timers: `resetAppData()` calls `store.stopAllPolling?.()` before `store.$reset()`.
Any future polling store that adopts the `stopAllPolling` name is covered automatically.

### Piece 2 — Central session lifecycle (`stores/dataLifecycle.js`)

Pull the lazy-init (currently inline in the router guard, `src/router/index.js:111-130`) and
the init flag into one module:

```js
// stores/dataLifecycle.js
let _initialized = false;

export async function initializeAppData() {
  if (_initialized) return;
  _initialized = true;
  // the store-initialization block currently inline in the router guard
}

export function resetAppData(pinia) {
  pinia._s.forEach((store) => {
    if (store.$id !== 'auth') store.$reset();
  });
  _initialized = false; // next login re-inits
}
```

Wiring:

- **Router guard** calls `initializeAppData()` instead of the inline block. The
  `_storesInitialized` module boolean in the router is removed.
- **`authStore.logout()`** calls `resetAppData(pinia)` → all non-auth stores wiped, init flag
  cleared.
- **`authStore.login()`** calls `resetAppData(pinia)` before loading the new profile, as a
  belt-and-suspenders guard against a lingering session.

Only instantiated stores appear in `pinia._s`; uninstantiated stores hold no stale data, so
iterating `pinia._s` is sufficient. `authStore` is excluded so its own login/logout flow
remains the single owner of auth state.

This closes Problem 1 at the root: identity change → clean slate → refetch on next
navigation.

### Piece 3 — Opt-in revalidation for active views (`composables/useRevalidate.js`)

A reusable composable modeled on the existing OTA / Stechen polling:

```js
// composables/useRevalidate.js — usage
useRevalidate(() => passeStore.loadSerienFromStorage(), { interval: 10000 });
```

Behavior:

- Polls the provided loader on an interval (near-real-time, seconds).
- **Pauses when the tab is hidden** (`document.visibilitychange`) and refetches immediately on
  regaining visibility/focus — stale-while-revalidate feel without wasting requests on a
  backgrounded tab.
- Clears the interval and listeners on `onUnmounted`.

**Opt-in per view, not global.** Only screens that display admin-editable shared data
(Serien / Positionen lists, competition state) subscribe. Live-play views that own transient
UI state stay out of it, so polling does not clobber in-progress interactions and the backend
is not hammered globally.

## Data flow (unchanged contract)

```
Backend REST API → services/ → stores/ (refs) → views/components
```

The layer adds lifecycle hooks around this flow; it does not change the flow itself. No API
calls move into components.

## Files touched

New:
- `src/stores/plugins/resettable.js`
- `src/stores/dataLifecycle.js`
- `src/composables/useRevalidate.js`

Edited:
- `src/main.js` — register the resettable plugin on Pinia
- `src/stores/authStore.js` — call `resetAppData()` on `logout()` and at the top of `login()`
- `src/router/index.js` — replace inline lazy-init + `_storesInitialized` flag with
  `initializeAppData()`
- The handful of views showing shared admin-editable data — opt-in `useRevalidate(...)` calls

No `otaStore` change is needed: it already exposes `stopAllPolling()`, which `resetAppData()`
calls before `$reset()`.

## Testing

- **`resettablePlugin`**: a store mutated then `$reset()` returns to initial state; nested
  objects are cloned (no shared references).
- **`dataLifecycle.resetAppData`**: all non-auth instantiated stores are reset; `auth` store
  is untouched; init flag flips back so `initializeAppData()` runs again.
- **`authStore`**: `logout()` triggers `resetAppData`; `login()` resets before loading the new
  profile (user-switch scenario shows no cross-user leakage).
- **`useRevalidate`**: calls loader on interval; pauses when hidden and refetches on
  visibility regain; clears on unmount. Use fake timers and a mocked `document.hidden`.

## Out of scope (first pass)

- STOMP / real-time push (parked per CLAUDE.md until the backend emits).
- A general caching/query framework (TanStack Query) — deliberately not adopted to preserve
  the store → service architecture and avoid a dependency.
- Global polling of all data — polling stays opt-in per view.
