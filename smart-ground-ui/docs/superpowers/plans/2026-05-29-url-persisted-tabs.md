# URL-Persisted Tab State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace local `activeTab` refs in five views with a `useUrlTab` composable that reads/writes the active tab from the URL query parameter `?tab=xxx`.

**Architecture:** A single `useUrlTab(defaultTab, validTabs)` composable owns all URL read/write logic. `activeTab` becomes a computed derived from `route.query.tab`; `setTab(value, { replace })` calls `router.push` (user clicks) or `router.replace` (programmatic switches). Each view imports the composable and removes its local ref.

**Tech Stack:** Vue 3 Composition API, Vue Router 4 (`useRoute`/`useRouter`), Vitest + `@vue/test-utils`

---

## File Map

| Action | Path |
|---|---|
| **Create** | `src/composables/useUrlTab.js` |
| **Create** | `src/composables/__tests__/useUrlTab.test.js` |
| **Modify** | `src/views/shooter/PasseManagementView.vue` |
| **Modify** | `src/views/shooter/TrainingManagementView.vue` |
| **Modify** | `src/views/shooter/CompetitionManagementView.vue` |
| **Modify** | `src/views/competition/CareerStatsView.vue` |
| **Modify** | `src/views/PassenAdminView.vue` |

---

### Task 1: Create `useUrlTab` composable with tests

**Files:**
- Create: `src/composables/useUrlTab.js`
- Create: `src/composables/__tests__/useUrlTab.test.js`

- [ ] **Step 1: Write the failing tests**

Create `src/composables/__tests__/useUrlTab.test.js`:

```js
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { useUrlTab } from '@/composables/useUrlTab.js'

const routes = [{ path: '/', component: { template: '<div />' } }]

function setup(defaultTab, validTabs, initialPath = '/') {
  const router = createRouter({ history: createMemoryHistory(), routes })
  let result
  mount(defineComponent({
    setup() { result = useUrlTab(defaultTab, validTabs) },
    template: '<div />',
  }), { global: { plugins: [router] } })
  return { router, result }
}

describe('useUrlTab', () => {
  it('returns defaultTab when no query param is present', async () => {
    const { result } = setup('score', ['score', 'wins'])
    expect(result.activeTab.value).toBe('score')
  })

  it('reads a valid tab from the URL query on mount', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/?tab=wins')
    let result
    mount(defineComponent({
      setup() { result = useUrlTab('score', ['score', 'wins']) },
      template: '<div />',
    }), { global: { plugins: [router] } })
    expect(result.activeTab.value).toBe('wins')
  })

  it('falls back to defaultTab when query tab is not in validTabs', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/?tab=garbage')
    let result
    mount(defineComponent({
      setup() { result = useUrlTab('score', ['score', 'wins']) },
      template: '<div />',
    }), { global: { plugins: [router] } })
    expect(result.activeTab.value).toBe('score')
  })

  it('setTab() calls router.push and updates activeTab', async () => {
    const { router, result } = setup('score', ['score', 'wins'])
    const pushSpy = vi.spyOn(router, 'push')
    await result.setTab('wins')
    expect(pushSpy).toHaveBeenCalledWith({ query: { tab: 'wins' } })
    expect(result.activeTab.value).toBe('wins')
  })

  it('setTab({ replace: true }) calls router.replace instead of push', async () => {
    const { router, result } = setup('score', ['score', 'wins'])
    const replaceSpy = vi.spyOn(router, 'replace')
    const pushSpy = vi.spyOn(router, 'push')
    await result.setTab('wins', { replace: true })
    expect(replaceSpy).toHaveBeenCalledWith({ query: { tab: 'wins' } })
    expect(pushSpy).not.toHaveBeenCalled()
  })

  it('preserves existing query params when setting tab', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/?filter=active')
    let result
    mount(defineComponent({
      setup() { result = useUrlTab('score', ['score', 'wins']) },
      template: '<div />',
    }), { global: { plugins: [router] } })
    const pushSpy = vi.spyOn(router, 'push')
    await result.setTab('wins')
    expect(pushSpy).toHaveBeenCalledWith({ query: { filter: 'active', tab: 'wins' } })
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
npm run test src/composables/__tests__/useUrlTab.test.js
```

Expected: all 6 tests FAIL with `Cannot find module '@/composables/useUrlTab.js'`

- [ ] **Step 3: Create the composable**

Create `src/composables/useUrlTab.js`:

```js
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/**
 * Syncs a tab selection to the URL query parameter `?tab=xxx`.
 *
 * @param {string} defaultTab - Tab value used when the query param is absent or invalid.
 * @param {string[]} validTabs - Allowed tab values; guards against arbitrary URL input.
 * @returns {{ activeTab: import('vue').ComputedRef<string>, setTab: function }}
 */
export function useUrlTab(defaultTab, validTabs) {
  const route = useRoute()
  const router = useRouter()

  // Single source of truth: derived from the URL, never from local state.
  const activeTab = computed(() =>
    validTabs.includes(route.query.tab) ? route.query.tab : defaultTab
  )

  /**
   * Navigate to a new tab.
   * @param {string} tab - The tab to activate. Must be one of validTabs.
   * @param {{ replace?: boolean }} options
   *   replace: true  → router.replace (silent, no browser history entry)
   *   replace: false → router.push    (adds a browser history entry; Back button works)
   */
  function setTab(tab, { replace = false } = {}) {
    const nav = replace ? router.replace : router.push
    nav({ query: { ...route.query, tab } })
  }

  return { activeTab, setTab }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
npm run test src/composables/__tests__/useUrlTab.test.js
```

Expected: 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/composables/useUrlTab.js src/composables/__tests__/useUrlTab.test.js
git commit -m "[ui] Add useUrlTab composable for URL-persisted tab state"
```

---

### Task 2: Update `PasseManagementView.vue`

**Files:**
- Modify: `src/views/shooter/PasseManagementView.vue`

This view already has a partial manual implementation (reads query on init, never writes back). Replace it entirely with the composable.

- [ ] **Step 1: Add the import**

In `src/views/shooter/PasseManagementView.vue`, the import block starts at line 566. Add `useUrlTab` after the existing imports:

Find:
```js
import Icons from '@/components/Icons.vue';
```

Replace with:
```js
import Icons from '@/components/Icons.vue';
import { useUrlTab } from '@/composables/useUrlTab.js';
```

- [ ] **Step 2: Replace the manual activeTab init**

Find (lines 588–593):
```js
// Active passen tab
const activeTab = ref(
  router.currentRoute.value.query.tab === 'active' ? 'active'
  : router.currentRoute.value.query.tab === 'completed' ? 'completed'
  : 'passen'
);
```

Replace with:
```js
// Active passen tab — synced to URL query param ?tab=xxx
const { activeTab, setTab } = useUrlTab('passen', ['passen', 'active', 'completed']);
```

- [ ] **Step 3: Update the template tab buttons**

Find:
```html
        @click="activeTab = 'passen'"
```
Replace with:
```html
        @click="setTab('passen')"
```

Find:
```html
        @click="activeTab = 'active'"
```
Replace with:
```html
        @click="setTab('active')"
```

Find:
```html
        @click="activeTab = 'completed'"
```
Replace with:
```html
        @click="setTab('completed')"
```

- [ ] **Step 4: Update the programmatic tab switch**

In `confirmStartPasse` (around line 620), find:
```js
  activeTab.value = 'active';
```
Replace with:
```js
  setTab('active', { replace: true });
```

- [ ] **Step 5: Run the full test suite**

```bash
npm run test
```

Expected: all tests PASS (no regressions)

- [ ] **Step 6: Commit**

```bash
git add src/views/shooter/PasseManagementView.vue
git commit -m "[ui] Persist PasseManagementView tab in URL via useUrlTab"
```

---

### Task 3: Update `TrainingManagementView.vue`

**Files:**
- Modify: `src/views/shooter/TrainingManagementView.vue`

- [ ] **Step 1: Add the import**

In `src/views/shooter/TrainingManagementView.vue`, find:
```js
import Icons from '@/components/Icons.vue'
```
Replace with:
```js
import Icons from '@/components/Icons.vue'
import { useUrlTab } from '@/composables/useUrlTab.js'
```

- [ ] **Step 2: Replace the local activeTab ref**

Find (line 360):
```js
const activeTab = ref('trainings')
```
Replace with:
```js
// Active tab — synced to URL query param ?tab=xxx
const { activeTab, setTab } = useUrlTab('trainings', ['trainings', 'active', 'completed'])
```

- [ ] **Step 3: Update the template tab buttons**

Find:
```html
@click="activeTab = 'trainings'"
```
Replace with:
```html
@click="setTab('trainings')"
```

Find:
```html
@click="activeTab = 'active'"
```
Replace with:
```html
@click="setTab('active')"
```

Find:
```html
@click="activeTab = 'completed'"
```
Replace with:
```html
@click="setTab('completed')"
```

- [ ] **Step 4: Update the programmatic tab switch**

In `confirmStart` (around line 453), find:
```js
  activeTab.value = 'active'
```
Replace with:
```js
  setTab('active', { replace: true })
```

- [ ] **Step 5: Run the full test suite**

```bash
npm run test
```

Expected: all tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/views/shooter/TrainingManagementView.vue
git commit -m "[ui] Persist TrainingManagementView tab in URL via useUrlTab"
```

---

### Task 4: Update `CompetitionManagementView.vue`

**Files:**
- Modify: `src/views/shooter/CompetitionManagementView.vue`

- [ ] **Step 1: Add the import**

In `src/views/shooter/CompetitionManagementView.vue`, find:
```js
import Icons from '@/components/Icons.vue'
import RotteSetupModal from '@/components/competition/RotteSetupModal.vue'
```
Replace with:
```js
import Icons from '@/components/Icons.vue'
import RotteSetupModal from '@/components/competition/RotteSetupModal.vue'
import { useUrlTab } from '@/composables/useUrlTab.js'
```

- [ ] **Step 2: Replace the local activeTab ref**

Find (line 382):
```js
const activeTab = ref('competitions')
```
Replace with:
```js
// Active tab — synced to URL query param ?tab=xxx
const { activeTab, setTab } = useUrlTab('competitions', ['competitions', 'active', 'completed'])
```

- [ ] **Step 3: Update the template tab buttons**

Find:
```html
@click="activeTab = 'competitions'"
```
Replace with:
```html
@click="setTab('competitions')"
```

Find:
```html
@click="activeTab = 'active'"
```
Replace with:
```html
@click="setTab('active')"
```

Find:
```html
@click="activeTab = 'completed'"
```
Replace with:
```html
@click="setTab('completed')"
```

- [ ] **Step 4: Update the programmatic tab switch**

In `onRotteModalConfirm` (around line 464), find:
```js
  activeTab.value = 'active'
```
Replace with:
```js
  setTab('active', { replace: true })
```

- [ ] **Step 5: Run the full test suite**

```bash
npm run test
```

Expected: all tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/views/shooter/CompetitionManagementView.vue
git commit -m "[ui] Persist CompetitionManagementView tab in URL via useUrlTab"
```

---

### Task 5: Update `CareerStatsView.vue`

**Files:**
- Modify: `src/views/competition/CareerStatsView.vue`

- [ ] **Step 1: Update the script imports**

In `src/views/competition/CareerStatsView.vue`, find:
```js
import { ref, onMounted } from 'vue';
import { useCompetitionStore } from '@/stores/competitionStore.js';
```
Replace with:
```js
import { ref, onMounted } from 'vue';
import { useCompetitionStore } from '@/stores/competitionStore.js';
import { useUrlTab } from '@/composables/useUrlTab.js';
```

- [ ] **Step 2: Replace the local activeTab ref**

Find (line 91):
```js
const activeTab = ref('score');
```
Replace with:
```js
// Active tab — synced to URL query param ?tab=xxx
const { activeTab, setTab } = useUrlTab('score', ['score', 'wins']);
```

- [ ] **Step 3: Update the template tab buttons**

Find:
```html
          @click="activeTab = 'score'"
```
Replace with:
```html
          @click="setTab('score')"
```

Find:
```html
          @click="activeTab = 'wins'"
```
Replace with:
```html
          @click="setTab('wins')"
```

- [ ] **Step 4: Run the full test suite**

```bash
npm run test
```

Expected: all tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/views/competition/CareerStatsView.vue
git commit -m "[ui] Persist CareerStatsView tab in URL via useUrlTab"
```

---

### Task 6: Update `PassenAdminView.vue`

**Files:**
- Modify: `src/views/PassenAdminView.vue`

- [ ] **Step 1: Update the script imports**

In `src/views/PassenAdminView.vue`, find:
```js
import { ref, computed, onMounted, watchEffect } from 'vue';
```
Replace with:
```js
import { ref, computed, onMounted, watchEffect } from 'vue';
import { useUrlTab } from '@/composables/useUrlTab.js';
```

- [ ] **Step 2: Replace the local activeTab ref**

Find (line 246):
```js
const activeTab = ref('serien');
```
Replace with:
```js
// Active tab — synced to URL query param ?tab=xxx
const { activeTab, setTab } = useUrlTab('serien', ['serien', 'passen']);
```

- [ ] **Step 3: Update the template tab buttons**

Find:
```html
        @click="activeTab = 'serien'"
```
Replace with:
```html
        @click="setTab('serien')"
```

Find:
```html
        @click="activeTab = 'passen'"
```
Replace with:
```html
        @click="setTab('passen')"
```

- [ ] **Step 4: Run the full test suite**

```bash
npm run test
```

Expected: all tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/views/PassenAdminView.vue
git commit -m "[ui] Persist PassenAdminView tab in URL via useUrlTab"
```

---

## Final Verification

After all tasks are complete, do a quick smoke-check across all affected routes:

- [ ] Navigate to `/meine-passen` → click "Aktive Passen" → URL shows `?tab=active` → refresh → "Aktive Passen" tab is still selected
- [ ] Navigate to `/training` → click "Abgeschlossen" → URL shows `?tab=completed` → press Back → "Trainings" tab is active again
- [ ] Navigate to `/wettkampf` → start a competition → URL auto-updates to `?tab=active` (no extra history entry)
- [ ] Navigate to `/career-stats?tab=wins` directly → "Nach Siegen" tab is active
- [ ] Navigate to `/passen?tab=garbage` → falls back to "Serien" tab
