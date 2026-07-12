# URL-Persisted Tab State — Design Spec

**Date:** 2026-05-29  
**Status:** Approved

---

## Problem

Five views contain tab UI whose active tab is stored in component-local state. Refreshing the page, sharing a link, or pressing the browser Back button all lose the selected tab.

## Solution

Introduce a `useUrlTab` composable that derives active tab state from the URL query parameter (`?tab=xxx`) and writes back to it on every tab change. Each affected view replaces its local `ref` with the composable.

---

## Composable — `src/composables/useUrlTab.js`

```js
export function useUrlTab(defaultTab, validTabs) {
  const route = useRoute()
  const router = useRouter()

  const activeTab = computed(() =>
    validTabs.includes(route.query.tab) ? route.query.tab : defaultTab
  )

  function setTab(tab, { replace = false } = {}) {
    const nav = replace ? router.replace : router.push
    nav({ query: { ...route.query, tab } })
  }

  return { activeTab, setTab }
}
```

### Behaviour

- `activeTab` is a computed derived from `route.query.tab`. It is read-only; the URL is the single source of truth.
- `validTabs` guards against arbitrary URL values — an unknown tab falls back to `defaultTab`.
- `...route.query` preserves any other query params already on the URL.
- **User-initiated tab click** → `setTab(x)` → `router.push` → adds a browser history entry; Back button returns to the previous tab.
- **Programmatic tab switch** (e.g. auto-advance after starting a session) → `setTab(x, { replace: true })` → `router.replace` → silently updates the URL, no history entry.

---

## Affected Views

| File | Route | Default tab | Valid tabs |
|---|---|---|---|
| `src/views/shooter/PasseManagementView.vue` | `/meine-passen` | `'passen'` | `['passen', 'active', 'completed']` |
| `src/views/shooter/TrainingManagementView.vue` | `/training` | `'trainings'` | `['trainings', 'active', 'completed']` |
| `src/views/shooter/CompetitionManagementView.vue` | `/wettkampf` | `'competitions'` | `['competitions', 'active', 'completed']` |
| `src/views/competition/CareerStatsView.vue` | `/career-stats` | `'score'` | `['score', 'wins']` |
| `src/views/PassenAdminView.vue` | `/passen` | `'serien'` | `['serien', 'passen']` |

### Per-view change pattern

**Script setup — replace:**
```js
// Before
const activeTab = ref('trainings')

// After
const { activeTab, setTab } = useUrlTab('trainings', ['trainings', 'active', 'completed'])
```

**Script setup — programmatic switches:**
```js
// Before
activeTab.value = 'active'

// After
setTab('active', { replace: true })
```

**Template — tab buttons:**
```html
<!-- Before -->
@click="activeTab = 'trainings'"

<!-- After -->
@click="setTab('trainings')"
```

**Template — content guards are unchanged:**
```html
v-if="activeTab === 'trainings'"   <!-- no change needed -->
```

### PasseManagementView special case

This view already manually reads `router.currentRoute.value.query.tab` on init. That manual init block is removed and replaced by the composable.

---

## What is not changed

- `v-if="activeTab === 'x'"` guards in templates — computed refs are reactive, these work as-is.
- All data-loading logic, store calls, modal state — untouched.
- Router configuration — no new routes needed; query params are handled by the composable.

---

## Out of scope

- Components that use the word "tab" but do not implement switchable tab UI (`SmartBoxCard`, `PositionCard`, `ScoreTable`, etc.) — these were inspected and confirmed to not require changes.
