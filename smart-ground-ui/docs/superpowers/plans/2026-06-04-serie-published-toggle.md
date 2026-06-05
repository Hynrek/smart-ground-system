# Serie Published Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Veröffentlicht" toggle to the `SerieDrawer` (edit mode only) that calls `PATCH /api/serien/{id}/published` to update the published state of a Serie.

**Architecture:** The change is additive: extend the API service with one function, wire `published` through the mapper and a new store action, then render a toggle in the drawer. The toggle fires immediately on change (optimistic update with rollback) — no changes to the existing save flow.

**Tech Stack:** Vue 3 (Composition API, `<script setup>`), Pinia, native `fetch` via `apiFetch`

---

## File Map

| File | Change |
|---|---|
| `src/services/serieApi.js` | Add `patchSeriePublished(id, published)` |
| `src/stores/passeStore.js` | Map `published` in `toUiSerie`; add `setSeriePublished` action |
| `src/components/SerieDrawer.vue` | Add toggle field in edit mode |
| `src/stores/__tests__/passeStore.test.js` | Add tests for `setSeriePublished` |

---

### Task 1: Add `patchSeriePublished` to `serieApi.js`

**Files:**
- Modify: `src/services/serieApi.js`

- [ ] **Step 1: Add the API function**

Open `src/services/serieApi.js` and append after the `updateSerieOwnership` export:

```js
export async function patchSeriePublished(id, published) {
  return apiFetch(`/serien/${id}/published`, {
    method: 'PATCH',
    body: JSON.stringify({ published }),
  })
}
```

The file already imports `apiFetch` from `./apiClient.js` — no new import needed.

- [ ] **Step 2: Commit**

```bash
git add src/services/serieApi.js
git commit -m "[ui] add patchSeriePublished to serieApi"
```

---

### Task 2: Wire `published` through the store

**Files:**
- Modify: `src/stores/passeStore.js`
- Test: `src/stores/__tests__/passeStore.test.js`

- [ ] **Step 1: Write the failing tests**

Open `src/stores/__tests__/passeStore.test.js`. Add a new `describe` block at the bottom of the file, before the closing of any outer block (the file currently ends at line 52 — append after that):

```js
describe('passeStore — setSeriePublished', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('maps published field from API response', async () => {
    serieApi.fetchSerien.mockResolvedValue([
      { id: 'ab1', name: 'Test', ownership: 'range', rangeId: null, rangeName: null, steps: [], published: true },
    ])
    const store = usePasseStore()
    await store.loadSerienFromStorage()
    expect(store.savedSerien[0].published).toBe(true)
  })

  it('defaults published to false when missing from API response', async () => {
    serieApi.fetchSerien.mockResolvedValue([
      { id: 'ab2', name: 'Test2', ownership: 'range', rangeId: null, rangeName: null, steps: [] },
    ])
    const store = usePasseStore()
    await store.loadSerienFromStorage()
    expect(store.savedSerien[0].published).toBe(false)
  })

  it('setSeriePublished updates state optimistically and calls API', async () => {
    serieApi.patchSeriePublished = vi.fn().mockResolvedValue({})
    const store = usePasseStore()
    store.savedSerien = [{ id: 'ab1', name: 'Test', steps: [], ownership: 'range', published: false }]
    await store.setSeriePublished('ab1', true)
    expect(store.savedSerien[0].published).toBe(true)
    expect(serieApi.patchSeriePublished).toHaveBeenCalledWith('ab1', true)
  })

  it('setSeriePublished rolls back on API error', async () => {
    serieApi.patchSeriePublished = vi.fn().mockRejectedValue(new Error('Network'))
    const store = usePasseStore()
    store.savedSerien = [{ id: 'ab1', name: 'Test', steps: [], ownership: 'range', published: false }]
    await expect(store.setSeriePublished('ab1', true)).rejects.toThrow('Network')
    expect(store.savedSerien[0].published).toBe(false)
  })

  it('setSeriePublished does nothing for unknown id', async () => {
    serieApi.patchSeriePublished = vi.fn()
    const store = usePasseStore()
    store.savedSerien = []
    await store.setSeriePublished('nonexistent', true)
    expect(serieApi.patchSeriePublished).not.toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
npm run test src/stores/__tests__/passeStore.test.js
```

Expected: the new `describe` block fails with errors like `store.setSeriePublished is not a function` and `published` being `undefined`.

- [ ] **Step 3: Update `toUiSerie` mapper in `passeStore.js`**

In `src/stores/passeStore.js`, find `toUiSerie` (line ~50). Add `published` to the returned object:

```js
function toUiSerie(ablauf) {
  return {
    id: ablauf.id,
    name: ablauf.name,
    rangeId: ablauf.rangeId ?? null,
    rangeName: ablauf.rangeName ?? null,
    steps: (ablauf.steps ?? []).map(fromApiStep),
    ownership: ablauf.ownership ?? 'user',
    createdAt: ablauf.createdAt ?? null,
    ownerUsername: ablauf.ownerUsername ?? null,
    published: ablauf.published ?? false,
  };
}
```

- [ ] **Step 4: Add `setSeriePublished` action in `passeStore.js`**

In `src/stores/passeStore.js`, after the `updateSerie` function (around line 286), add:

```js
const setSeriePublished = async (serieId, published) => {
  const serie = savedSerien.value.find((s) => s.id === serieId);
  if (!serie) return;
  serie.published = published;
  try {
    await serieApi.patchSeriePublished(serieId, published);
  } catch (e) {
    serie.published = !published;
    throw e;
  }
};
```

- [ ] **Step 5: Export `setSeriePublished` from the store's return object**

Find the `return {` block at the bottom of `usePasseStore` and add `setSeriePublished` to the Serie actions group:

```js
// Serie actions
saveSerie, deleteSerie, renameSerie, createRangeSerie, updateSerie, setSeriePublished,
loadSerienFromStorage,
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
npm run test src/stores/__tests__/passeStore.test.js
```

Expected: all tests in both `describe` blocks pass.

- [ ] **Step 7: Commit**

```bash
git add src/stores/passeStore.js src/stores/__tests__/passeStore.test.js
git commit -m "[ui] wire published field through passeStore mapper and add setSeriePublished action"
```

---

### Task 3: Add toggle to `SerieDrawer.vue` (edit mode only)

**Files:**
- Modify: `src/components/SerieDrawer.vue`

- [ ] **Step 1: Add `published` to local form state**

In `src/components/SerieDrawer.vue`, in the `<script setup>` section, after the existing `ref` declarations (around line 174), add:

```js
const published = ref(false);
```

- [ ] **Step 2: Initialize `published` from the prop when the drawer opens**

Still in `<script setup>`, find the `watch(() => props.open, ...)` block (around line 180). Inside the `else if (props.serie)` branch, add:

```js
published.value = props.serie.published ?? false;
```

So the full branch reads:

```js
} else if (props.serie) {
  editingName.value = props.serie.name;
  selectedRangeId.value = props.serie.rangeId ?? '';
  steps.value = [...(props.serie.steps ?? [])];
  published.value = props.serie.published ?? false;
  if (props.serie.rangeId) {
    rangeStore.loadPositions(props.serie.rangeId);
  }
}
```

- [ ] **Step 3: Call `setSeriePublished` on toggle change**

In `<script setup>`, after the `removeStep` function, add:

```js
const onPublishedChange = async () => {
  if (!props.serie) return;
  await passeStore.setSeriePublished(props.serie.id, published.value);
};
```

- [ ] **Step 4: Add the toggle field to the template (edit mode only)**

In `<template>`, inside `.drawer-body`, add the following block after the Range field (`<div class="field">` containing the `<select>` / `<span class="field-readonly">`). Place it only when `!isCreate`:

```html
<!-- Published toggle (edit only) -->
<div v-if="!isCreate" class="field field--row">
  <label class="field-label" for="serie-published">Veröffentlicht</label>
  <button
    id="serie-published"
    class="toggle-btn"
    :class="{ 'toggle-btn--on': published }"
    role="switch"
    :aria-checked="published"
    @click="published = !published; onPublishedChange()"
  >
    <span class="toggle-thumb" />
  </button>
</div>
```

- [ ] **Step 5: Add styles for the toggle**

In `<style scoped>`, append at the end:

```css
/* ── Published toggle ─────────────────────────────────────────────── */
.field--row {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.toggle-btn {
  position: relative;
  width: 40px;
  height: 22px;
  border-radius: 11px;
  border: none;
  background: #cbd5e0;
  cursor: pointer;
  transition: background 0.2s;
  flex-shrink: 0;
  padding: 0;
}

.toggle-btn--on {
  background: #4fc3f7;
}

.toggle-thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.2s;
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}

.toggle-btn--on .toggle-thumb {
  transform: translateX(18px);
}
```

- [ ] **Step 6: Update `defineExpose` to include `published`**

At the bottom of `<script setup>`, find `defineExpose(...)` and add `published`:

```js
defineExpose({ stepMode, pairPending, published });
```

- [ ] **Step 7: Run the full test suite**

```bash
npm run test
```

Expected: all tests pass, no regressions.

- [ ] **Step 8: Commit**

```bash
git add src/components/SerieDrawer.vue
git commit -m "[ui] add Veröffentlicht toggle to SerieDrawer edit mode"
```
