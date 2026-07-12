# Passen-Vorlagen Admin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the "Passen" stub tab in `PassenAdminView.vue` with a full admin template management UI for global Passe-Vorlagen (named collections of Platz-Serien).

**Architecture:** Three layers — store (localStorage persistence), a new `GlobalPasseDrawer.vue` component (create/edit/delete drawer), and updates to `PassenAdminView.vue` to replace the stub tab with a grouped list. No start/active flow; these are pure templates.

**Tech Stack:** Vue 3 `<script setup>`, Pinia (Composition API store), Vitest + `@vue/test-utils`, localStorage.

---

## File Map

| Action | File | Responsibility |
|---|---|---|
| Modify | `src/stores/passeStore.js` | Add `savedGlobalPassen`, `createGlobalPasse`, `updateGlobalPasse`, `deleteGlobalPasse` |
| Modify | `src/stores/__tests__/passeStore.test.js` | Add test blocks for the three new store methods |
| Create | `src/components/GlobalPasseDrawer.vue` | Slide-out drawer for create/edit/delete of a global Passe |
| Create | `src/components/__tests__/GlobalPasseDrawer.test.js` | Component tests for the drawer |
| Modify | `src/views/PassenAdminView.vue` | Replace Passen stub with grouped list + GlobalPasseDrawer |
| Modify | `src/views/__tests__/PassenAdminView.test.js` | Add/update tests for the Passen tab |

---

## Key Conventions (read before writing any code)

- **Storage key prefix for global Passen**: `_sg_global_passe_` (sequential int suffix, same as `_sg_range_serie_` pattern)
- **Stored serien use `alias` (not `name`)**: `createGlobalPasse` maps `s.name → alias` when embedding serien as snapshots. `GlobalPasseDrawer` maps back `alias → name` when pre-populating edit mode. This matches the existing `createPasse` method.
- **Serien in memory** (`savedGlobalPassen[i].serien[j]`): shape is `{ id, alias, rangeId, rangeName, steps }`.
- **Serien in the drawer** (`selectedSerien` ref): shape is `{ id, name, rangeId, rangeName, steps }`.
- **Immutable updates only**: never mutate Pinia state directly. Use `.map()` / spread, same as `updateSerie`.
- **`watch(..., { immediate: true })`**: required on `props.open` watcher so edit-mode state initialises when the component first mounts with `open: true`.
- **`defineExpose`**: required for `<script setup>` components whose refs are accessed in tests via `wrapper.vm`.
- **`Teleport to="body"`** + **`attachTo: document.body`**: Teleport content lands in `document.body`; tests must mount with `attachTo: document.body`.

---

## Task 1: Add global Passe methods to `passeStore`

**Files:**
- Modify: `src/stores/passeStore.js`
- Modify: `src/stores/__tests__/passeStore.test.js`

### Step 1: Add failing tests

Add these three `describe` blocks at the end of `src/stores/__tests__/passeStore.test.js`:

```javascript
describe('passeStore.createGlobalPasse', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('adds a global passe to savedGlobalPassen', () => {
    const store = usePasseStore()
    const serien = [{ id: '_sg_range_serie_1', name: 'Olympisch', rangeId: 'r1', rangeName: 'Platz 1', steps: [] }]
    store.createGlobalPasse('Test Passe', serien)
    expect(store.savedGlobalPassen).toHaveLength(1)
    const passe = store.savedGlobalPassen[0]
    expect(passe.name).toBe('Test Passe')
    expect(passe.ownership).toBe('global')
    expect(passe.serien).toHaveLength(1)
  })

  it('persists to localStorage with _sg_global_passe_ prefix', () => {
    const store = usePasseStore()
    const serien = [{ id: '_sg_range_serie_1', name: 'Olympisch', rangeId: 'r1', rangeName: 'Platz 1', steps: [] }]
    store.createGlobalPasse('My Passe', serien)
    const key = Object.keys(localStorage).find((k) => k.startsWith('_sg_global_passe_'))
    expect(key).toBeDefined()
    const stored = JSON.parse(localStorage.getItem(key))
    expect(stored.passeName).toBe('My Passe')
    expect(stored.ownership).toBe('global')
  })

  it('does nothing when serien array is empty', () => {
    const store = usePasseStore()
    store.createGlobalPasse('Empty', [])
    expect(store.savedGlobalPassen).toHaveLength(0)
    expect(Object.keys(localStorage)).toHaveLength(0)
  })

  it('falls back to generated name when name is blank', () => {
    const store = usePasseStore()
    const serien = [{ id: '_sg_range_serie_1', name: 'Olympisch', rangeId: 'r1', rangeName: 'Platz 1', steps: [] }]
    store.createGlobalPasse('  ', serien)
    expect(store.savedGlobalPassen[0].name).toBe('Passe 1')
  })
})

describe('passeStore.updateGlobalPasse', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('updates name and serien in memory', () => {
    const store = usePasseStore()
    const serien = [{ id: '_sg_range_serie_1', name: 'Olympisch', rangeId: 'r1', rangeName: 'Platz 1', steps: [] }]
    store.createGlobalPasse('Old Name', serien)
    const passeId = store.savedGlobalPassen[0].id
    const newSerien = [
      { id: '_sg_range_serie_1', name: 'Olympisch', rangeId: 'r1', rangeName: 'Platz 1', steps: [] },
      { id: '_sg_range_serie_2', name: 'Skeet', rangeId: 'r2', rangeName: 'Platz 2', steps: [] },
    ]
    store.updateGlobalPasse(passeId, 'New Name', newSerien)
    expect(store.savedGlobalPassen[0].name).toBe('New Name')
    expect(store.savedGlobalPassen[0].serien).toHaveLength(2)
  })

  it('persists changes to localStorage', () => {
    const store = usePasseStore()
    const serien = [{ id: '_sg_range_serie_1', name: 'Olympisch', rangeId: 'r1', rangeName: 'Platz 1', steps: [] }]
    store.createGlobalPasse('Old Name', serien)
    const passeId = store.savedGlobalPassen[0].id
    store.updateGlobalPasse(passeId, 'New Name', serien)
    const stored = JSON.parse(localStorage.getItem(passeId))
    expect(stored.passeName).toBe('New Name')
  })

  it('does not throw when passe id is not found', () => {
    const store = usePasseStore()
    expect(() => store.updateGlobalPasse('nonexistent', 'Name', [])).not.toThrow()
  })
})

describe('passeStore.deleteGlobalPasse', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('removes passe from savedGlobalPassen and localStorage', () => {
    const store = usePasseStore()
    const serien = [{ id: '_sg_range_serie_1', name: 'Olympisch', rangeId: 'r1', rangeName: 'Platz 1', steps: [] }]
    store.createGlobalPasse('Test', serien)
    const passeId = store.savedGlobalPassen[0].id
    store.deleteGlobalPasse(passeId)
    expect(store.savedGlobalPassen).toHaveLength(0)
    expect(localStorage.getItem(passeId)).toBeNull()
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```
npm run test src/stores/__tests__/passeStore.test.js
```

Expected: the 9 new tests fail with errors like "store.createGlobalPasse is not a function" and "Cannot read properties of undefined (reading 'length')".

- [ ] **Step 3: Add the implementation to `src/stores/passeStore.js`**

After the existing `RANGE_SERIE_PREFIX` constant (line 34), add:

```javascript
const GLOBAL_PASSE_PREFIX = '_sg_global_passe_';
```

After `savedPassen` and `pendingPasseId` refs (around line 31), add:

```javascript
const savedGlobalPassen = ref([]);
```

After `nextRangeSerieKey` function (around line 70), add:

```javascript
const nextGlobalPasseKey = () => {
  const existing = Object.keys(localStorage)
    .filter((k) => k.startsWith(GLOBAL_PASSE_PREFIX))
    .map((k) => parseInt(k.slice(GLOBAL_PASSE_PREFIX.length), 10))
    .filter((n) => !isNaN(n));
  return `${GLOBAL_PASSE_PREFIX}${existing.length > 0 ? Math.max(...existing) + 1 : 1}`;
};
```

After `loadSerienFromStorage` (around line 143), add:

```javascript
const loadGlobalPassenFromStorage = () => {
  savedGlobalPassen.value = Object.keys(localStorage)
    .filter((k) => k.startsWith(GLOBAL_PASSE_PREFIX))
    .map((key) => {
      try {
        const data = JSON.parse(localStorage.getItem(key));
        return {
          id: key,
          name: data.passeName,
          serien: data.serien ?? [],
          createdAt: data.createdAt ?? 0,
          ownership: 'global',
        };
      } catch {
        return null;
      }
    })
    .filter(Boolean)
    .sort((a, b) => a.createdAt - b.createdAt);
};
```

After the two existing `loadPassenFromStorage()` and `loadSerienFromStorage()` calls (around line 146), add:

```javascript
loadGlobalPassenFromStorage();
```

After `updateSerie` (around line 311), add the three new action functions:

```javascript
const createGlobalPasse = (name, selectedSerien) => {
  if (!selectedSerien || selectedSerien.length === 0) return;
  const trimmedName = name?.trim() || `Passe ${savedGlobalPassen.value.length + 1}`;
  const key = nextGlobalPasseKey();
  const createdAt = Date.now();
  const serien = selectedSerien.map((s) => ({
    id: s.id,
    alias: s.name,
    rangeId: s.rangeId,
    rangeName: s.rangeName,
    steps: [...(s.steps ?? [])],
  }));
  localStorage.setItem(key, JSON.stringify({ passeName: trimmedName, serien, createdAt, ownership: 'global' }));
  savedGlobalPassen.value = [
    ...savedGlobalPassen.value,
    { id: key, name: trimmedName, serien, createdAt, ownership: 'global' },
  ];
};

const updateGlobalPasse = (id, newName, newSerien) => {
  const exists = savedGlobalPassen.value.some((p) => p.id === id);
  if (!exists) return;
  const serien = (newSerien ?? []).map((s) => ({
    id: s.id,
    alias: s.name,
    rangeId: s.rangeId,
    rangeName: s.rangeName,
    steps: [...(s.steps ?? [])],
  }));
  savedGlobalPassen.value = savedGlobalPassen.value.map((p) =>
    p.id === id ? { ...p, name: newName, serien } : p
  );
  try {
    const stored = JSON.parse(localStorage.getItem(id));
    if (stored) {
      stored.passeName = newName;
      stored.serien = serien;
      localStorage.setItem(id, JSON.stringify(stored));
    }
  } catch { /* ignorieren */ }
};

const deleteGlobalPasse = (id) => {
  localStorage.removeItem(id);
  savedGlobalPassen.value = savedGlobalPassen.value.filter((p) => p.id !== id);
};
```

In the `return` object at the end of the store, add inside the Passe actions section:

```javascript
savedGlobalPassen,
createGlobalPasse,
updateGlobalPasse,
deleteGlobalPasse,
loadGlobalPassenFromStorage,
```

- [ ] **Step 4: Run tests to verify they pass**

```
npm run test src/stores/__tests__/passeStore.test.js
```

Expected: all tests pass (previous 13 + new 9 = all green).

- [ ] **Step 5: Commit**

```
git add src/stores/passeStore.js src/stores/__tests__/passeStore.test.js
git commit -m "[ui] Add createGlobalPasse, updateGlobalPasse, deleteGlobalPasse to passeStore"
```

---

## Task 2: Build `GlobalPasseDrawer.vue`

**Files:**
- Create: `src/components/GlobalPasseDrawer.vue`
- Create: `src/components/__tests__/GlobalPasseDrawer.test.js`

- [ ] **Step 1: Write the failing tests**

Create `src/components/__tests__/GlobalPasseDrawer.test.js`:

```javascript
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import GlobalPasseDrawer from '../GlobalPasseDrawer.vue'

const mockPasseStore = {
  savedSerien: [
    { id: '_sg_range_serie_1', name: 'Olympisch', rangeId: 'r1', rangeName: 'Platz 1', steps: [], ownership: 'range' },
    { id: '_sg_range_serie_2', name: 'Skeet', rangeId: 'r2', rangeName: 'Platz 2', steps: [], ownership: 'range' },
  ],
  createGlobalPasse: vi.fn(),
  updateGlobalPasse: vi.fn(),
  deleteGlobalPasse: vi.fn(),
}

vi.mock('@/stores/passeStore.js', () => ({ usePasseStore: () => mockPasseStore }))
vi.mock('@/components/Icons.vue', () => ({
  default: { template: '<span />', props: ['icon', 'size', 'color'] },
}))

const mountDrawer = (propsData) =>
  mount(GlobalPasseDrawer, {
    props: propsData,
    attachTo: document.body,
    global: { plugins: [createPinia()] },
  })

describe('GlobalPasseDrawer — create mode', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders name input and Serie picker when open in create mode', () => {
    const wrapper = mountDrawer({ open: true, mode: 'create', passe: null })
    expect(wrapper.find('[data-testid="global-passe-drawer"]').exists()).toBe(true)
    expect(wrapper.find('input').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="picker-item"]').length).toBe(2)
  })

  it('Save button is disabled when name is empty', () => {
    const wrapper = mountDrawer({ open: true, mode: 'create', passe: null })
    const saveBtn = wrapper.find('[data-testid="save-btn"]')
    expect(saveBtn.attributes('disabled')).toBeDefined()
  })

  it('Save button is disabled when name is set but no Serie is selected', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'create', passe: null })
    await wrapper.find('input').setValue('My Passe')
    const saveBtn = wrapper.find('[data-testid="save-btn"]')
    expect(saveBtn.attributes('disabled')).toBeDefined()
  })

  it('Save button is enabled when name is set and at least one Serie is selected', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'create', passe: null })
    await wrapper.find('input').setValue('My Passe')
    await wrapper.findAll('[data-testid="picker-item"]')[0].trigger('click')
    const saveBtn = wrapper.find('[data-testid="save-btn"]')
    expect(saveBtn.attributes('disabled')).toBeUndefined()
  })

  it('clicking backdrop emits close', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'create', passe: null })
    await wrapper.find('.drawer-backdrop').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('clicking Cancel emits close', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'create', passe: null })
    await wrapper.find('[data-testid="cancel-btn"]').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('clicking Save calls createGlobalPasse and emits saved and close', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'create', passe: null })
    await wrapper.find('input').setValue('Neue Vorlage')
    await wrapper.findAll('[data-testid="picker-item"]')[0].trigger('click')
    await wrapper.find('[data-testid="save-btn"]').trigger('click')
    expect(mockPasseStore.createGlobalPasse).toHaveBeenCalledWith(
      'Neue Vorlage',
      expect.arrayContaining([expect.objectContaining({ id: '_sg_range_serie_1' })]),
    )
    expect(wrapper.emitted('saved')).toBeTruthy()
    expect(wrapper.emitted('close')).toBeTruthy()
  })
})

describe('GlobalPasseDrawer — edit mode', () => {
  const existingPasse = {
    id: '_sg_global_passe_1',
    name: 'Olympisch Vorlage',
    serien: [
      { id: '_sg_range_serie_1', alias: 'Olympisch', rangeId: 'r1', rangeName: 'Platz 1', steps: [] },
    ],
    ownership: 'global',
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('pre-fills name field in edit mode', () => {
    const wrapper = mountDrawer({ open: true, mode: 'edit', passe: existingPasse })
    expect(wrapper.find('input').element.value).toBe('Olympisch Vorlage')
  })

  it('pre-populates selectedSerien list from passe serien', () => {
    const wrapper = mountDrawer({ open: true, mode: 'edit', passe: existingPasse })
    const rows = wrapper.findAll('[data-testid="selected-row"]')
    expect(rows).toHaveLength(1)
  })

  it('shows delete button in edit mode', () => {
    const wrapper = mountDrawer({ open: true, mode: 'edit', passe: existingPasse })
    expect(wrapper.find('[data-testid="delete-btn"]').exists()).toBe(true)
  })

  it('delete button is not shown in create mode', () => {
    const wrapper = mountDrawer({ open: true, mode: 'create', passe: null })
    expect(wrapper.find('[data-testid="delete-btn"]').exists()).toBe(false)
  })

  it('clicking Save in edit mode calls updateGlobalPasse', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'edit', passe: existingPasse })
    await wrapper.find('input').setValue('Geänderte Vorlage')
    await wrapper.find('[data-testid="save-btn"]').trigger('click')
    expect(mockPasseStore.updateGlobalPasse).toHaveBeenCalledWith(
      '_sg_global_passe_1',
      'Geänderte Vorlage',
      expect.any(Array),
    )
  })

  it('clicking delete button reveals confirm controls', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'edit', passe: existingPasse })
    await wrapper.find('[data-testid="delete-btn"]').trigger('click')
    expect(wrapper.find('.delete-confirm-text').exists()).toBe(true)
    expect(wrapper.find('[data-testid="delete-btn"]').exists()).toBe(false)
  })

  it('confirming delete calls deleteGlobalPasse and emits deleted and close', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'edit', passe: existingPasse })
    await wrapper.find('[data-testid="delete-btn"]').trigger('click')
    await wrapper.find('.btn--danger').trigger('click')
    expect(mockPasseStore.deleteGlobalPasse).toHaveBeenCalledWith('_sg_global_passe_1')
    expect(wrapper.emitted('deleted')).toBeTruthy()
    expect(wrapper.emitted('close')).toBeTruthy()
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```
npm run test src/components/__tests__/GlobalPasseDrawer.test.js
```

Expected: all tests fail — the component file does not exist yet.

- [ ] **Step 3: Create `src/components/GlobalPasseDrawer.vue`**

```vue
<template>
  <Teleport to="body">
    <template v-if="open">
      <div class="drawer-backdrop" @click="$emit('close')" />
      <div class="drawer" data-testid="global-passe-drawer">
        <!-- Header -->
        <div class="drawer-header">
          <h2 class="drawer-title">
            {{ isCreate ? 'Neue Passe-Vorlage' : editingName }}
          </h2>
          <button class="drawer-close" aria-label="Schließen" @click="$emit('close')">
            <Icons icon="x" :size="16" color="#718096" />
          </button>
        </div>

        <!-- Body -->
        <div class="drawer-body">
          <!-- Name -->
          <div class="field">
            <label class="field-label">Name</label>
            <input
              v-model="editingName"
              class="field-input"
              type="text"
              maxlength="50"
              placeholder="z.B. Olympisch 25m"
            />
          </div>

          <!-- Serie picker -->
          <div class="field">
            <label class="field-label">Serien hinzufügen</label>
            <div v-if="pickerGroups.length > 0" class="serie-picker">
              <div
                v-for="group in pickerGroups"
                :key="group.rangeId ?? '__none__'"
                class="picker-group"
              >
                <div class="picker-group-label">{{ group.rangeName ?? 'Kein Platz' }}</div>
                <button
                  v-for="s in group.serien"
                  :key="s.id"
                  class="picker-item"
                  :class="{ selected: isSelected(s.id) }"
                  data-testid="picker-item"
                  @click="toggleSerie(s)"
                >
                  <span class="picker-item-name">{{ s.name }}</span>
                  <span class="picker-item-meta">{{ s.steps.length }} Schritte</span>
                </button>
              </div>
            </div>
            <p v-else class="picker-empty">Noch keine Platz-Serien vorhanden.</p>
          </div>

          <!-- Selected serien list -->
          <div v-if="selectedSerien.length > 0" class="field">
            <label class="field-label">
              Gewählte Serien
              <span class="step-count">{{ selectedSerien.length }}</span>
            </label>
            <div class="selected-list">
              <div
                v-for="(s, i) in selectedSerien"
                :key="s.id"
                class="selected-row"
                data-testid="selected-row"
              >
                <div class="selected-info">
                  <span class="selected-name">{{ s.name }}</span>
                  <span class="selected-meta">{{ s.rangeName }}</span>
                </div>
                <button
                  class="step-remove"
                  data-testid="remove-serie-btn"
                  @click="removeSerie(i)"
                >
                  <Icons icon="x" :size="11" color="#a0aec0" />
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Footer actions -->
        <div class="drawer-footer">
          <button
            class="btn btn--ghost"
            data-testid="cancel-btn"
            @click="$emit('close')"
          >
            Abbrechen
          </button>
          <button
            class="btn btn--primary"
            data-testid="save-btn"
            :disabled="!canSave"
            @click="save"
          >
            Speichern
          </button>
        </div>

        <!-- Delete (edit mode only) -->
        <div v-if="!isCreate" class="drawer-delete">
          <template v-if="!confirmingDelete">
            <button
              class="btn btn--danger-ghost"
              data-testid="delete-btn"
              @click="confirmingDelete = true"
            >
              <Icons icon="trash" :size="13" color="#e53e3e" />
              Passe löschen
            </button>
          </template>
          <template v-else>
            <span class="delete-confirm-text">Wirklich löschen?</span>
            <button class="btn btn--ghost" @click="confirmingDelete = false">Nein</button>
            <button class="btn btn--danger" @click="deleteAndClose">Ja, löschen</button>
          </template>
        </div>
      </div>
    </template>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { usePasseStore } from '@/stores/passeStore.js';
import Icons from '@/components/Icons.vue';

const props = defineProps({
  open: { type: Boolean, required: true },
  mode: { type: String, required: true }, // 'create' | 'edit'
  passe: { type: Object, default: null },
});

const emit = defineEmits(['saved', 'deleted', 'close']);

const passeStore = usePasseStore();

const isCreate = computed(() => props.mode === 'create');

// ── Form state ──────────────────────────────────────────────────────────────
const editingName = ref('');
const selectedSerien = ref([]);
const confirmingDelete = ref(false);

watch(() => props.open, (open) => {
  if (!open) return;
  confirmingDelete.value = false;
  if (props.mode === 'create') {
    editingName.value = '';
    selectedSerien.value = [];
  } else if (props.passe) {
    editingName.value = props.passe.name;
    // stored serien use 'alias'; picker uses 'name'
    selectedSerien.value = props.passe.serien.map((s) => ({
      id: s.id,
      name: s.alias,
      rangeId: s.rangeId,
      rangeName: s.rangeName,
      steps: s.steps ?? [],
    }));
  }
}, { immediate: true });

// ── Serie picker ─────────────────────────────────────────────────────────────
const rangeSerien = computed(() =>
  passeStore.savedSerien.filter((s) => s.ownership === 'range')
);

const pickerGroups = computed(() => {
  const map = new Map();
  for (const s of rangeSerien.value) {
    const key = s.rangeId ?? '__none__';
    if (!map.has(key)) {
      map.set(key, { rangeId: s.rangeId, rangeName: s.rangeName, serien: [] });
    }
    map.get(key).serien.push(s);
  }
  return Array.from(map.values());
});

const isSelected = (serieId) => selectedSerien.value.some((s) => s.id === serieId);

const toggleSerie = (serie) => {
  if (isSelected(serie.id)) {
    selectedSerien.value = selectedSerien.value.filter((s) => s.id !== serie.id);
  } else {
    selectedSerien.value = [...selectedSerien.value, serie];
  }
};

const removeSerie = (index) => {
  const list = [...selectedSerien.value];
  list.splice(index, 1);
  selectedSerien.value = list;
};

// ── Helpers ─────────────────────────────────────────────────────────────────
const canSave = computed(() =>
  editingName.value.trim().length > 0 && selectedSerien.value.length > 0
);

// ── Actions ──────────────────────────────────────────────────────────────────
const save = () => {
  if (!canSave.value) return;
  if (isCreate.value) {
    passeStore.createGlobalPasse(editingName.value.trim(), selectedSerien.value);
  } else {
    passeStore.updateGlobalPasse(props.passe.id, editingName.value.trim(), selectedSerien.value);
  }
  emit('saved');
  emit('close');
};

const deleteAndClose = () => {
  if (props.passe) {
    passeStore.deleteGlobalPasse(props.passe.id);
    emit('deleted');
    emit('close');
  }
};

defineExpose({ selectedSerien, confirmingDelete });
</script>

<style scoped>
/* ── Backdrop ─────────────────────────────────────────────────────────────── */
.drawer-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  z-index: 40;
}

/* ── Drawer panel ─────────────────────────────────────────────────────────── */
.drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 420px;
  max-width: 100vw;
  background: #fff;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.12);
  z-index: 50;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

@media (max-width: 640px) {
  .drawer { width: 100vw; }
}

/* ── Header ──────────────────────────────────────────────────────────────── */
.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid #e2e8f0;
  flex-shrink: 0;
}

.drawer-title {
  font-size: 16px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drawer-close {
  background: transparent;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.15s;
}

.drawer-close:hover { background: #f7fafc; }

/* ── Body ─────────────────────────────────────────────────────────────── */
.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ── Fields ──────────────────────────────────────────────────────────── */
.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-label {
  font-size: 11.5px;
  font-weight: 600;
  color: #718096;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.field-input {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  color: #1a1a2e;
  background: #fff;
  outline: none;
  transition: border-color 0.15s;
  box-sizing: border-box;
}

.field-input:focus { border-color: #4fc3f7; }

/* ── Serie picker ────────────────────────────────────────────────────── */
.serie-picker {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 260px;
  overflow-y: auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px;
}

.picker-group-label {
  font-size: 10.5px;
  font-weight: 700;
  color: #a0aec0;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 4px 6px 2px;
}

.picker-item {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 7px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
  gap: 8px;
  margin-bottom: 4px;
}

.picker-item:hover { background: #f7f8fc; }

.picker-item.selected {
  background: rgba(79, 195, 247, 0.08);
  border-color: rgba(79, 195, 247, 0.4);
}

.picker-item-name {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: #1a1a2e;
}

.picker-item-meta {
  font-size: 11px;
  color: #a0aec0;
}

.picker-empty {
  font-size: 13px;
  color: #a0aec0;
  margin: 0;
  padding: 8px 0;
}

/* ── Selected list ───────────────────────────────────────────────────── */
.step-count {
  font-size: 11px;
  font-weight: 600;
  color: #a0aec0;
  text-transform: none;
  letter-spacing: 0;
}

.selected-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.selected-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  background: #f7f8fc;
  border-radius: 7px;
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.selected-info {
  flex: 1;
  min-width: 0;
}

.selected-name {
  display: block;
  font-size: 12.5px;
  font-weight: 600;
  color: #1a1a2e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-meta {
  display: block;
  font-size: 11px;
  color: #a0aec0;
}

.step-remove {
  background: transparent;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 2px;
  border-radius: 4px;
  opacity: 0.5;
  transition: opacity 0.15s;
}

.step-remove:hover { opacity: 1; }

/* ── Footer ──────────────────────────────────────────────────────────── */
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 24px;
  border-top: 1px solid #e2e8f0;
  flex-shrink: 0;
}

/* ── Delete zone ─────────────────────────────────────────────────────── */
.drawer-delete {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 12px 24px;
  border-top: 1px solid #fee2e2;
  background: #fff5f5;
  flex-shrink: 0;
  flex-wrap: wrap;
}

.delete-confirm-text {
  font-size: 13px;
  color: #e53e3e;
  font-weight: 500;
}

/* ── Buttons ─────────────────────────────────────────────────────────── */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid transparent;
}

.btn:disabled { opacity: 0.4; cursor: not-allowed; }

.btn--primary {
  background: #1a1a2e;
  color: #fff;
  border-color: #1a1a2e;
}

.btn--primary:hover:not(:disabled) { background: #0f0f1a; }

.btn--ghost {
  background: transparent;
  border-color: #e2e8f0;
  color: #4a5568;
}

.btn--ghost:hover { background: #f7fafc; }

.btn--danger-ghost {
  background: transparent;
  border-color: rgba(229, 62, 62, 0.3);
  color: #e53e3e;
}

.btn--danger-ghost:hover { background: rgba(229, 62, 62, 0.06); }

.btn--danger {
  background: rgba(229, 62, 62, 0.1);
  border-color: rgba(229, 62, 62, 0.4);
  color: #e53e3e;
}

.btn--danger:hover { background: rgba(229, 62, 62, 0.18); }
</style>
```

- [ ] **Step 4: Run tests to verify they pass**

```
npm run test src/components/__tests__/GlobalPasseDrawer.test.js
```

Expected: all 13 tests pass.

- [ ] **Step 5: Commit**

```
git add src/components/GlobalPasseDrawer.vue src/components/__tests__/GlobalPasseDrawer.test.js
git commit -m "[ui] Add GlobalPasseDrawer component with create/edit/delete"
```

---

## Task 3: Update `PassenAdminView.vue` Passen tab + tests

**Files:**
- Modify: `src/views/PassenAdminView.vue`
- Modify: `src/views/__tests__/PassenAdminView.test.js`

- [ ] **Step 1: Update the test file first**

Replace the full content of `src/views/__tests__/PassenAdminView.test.js` with:

```javascript
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import PassenAdminView from '../PassenAdminView.vue'

const mockPasseStore = {
  savedSerien: [
    {
      id: '_sg_range_serie_1',
      name: 'Olympisch',
      rangeId: 'r1',
      rangeName: 'Platz 1',
      steps: [{ id: 1, type: 'solo', alias: 'W1', positionId: 'pos-a', letter: 'A' }],
      ownership: 'range',
    },
    {
      id: '_sg_range_serie_2',
      name: 'Skeet',
      rangeId: 'r2',
      rangeName: 'Platz 2',
      steps: [{ id: 2, type: 'pair', alias1: 'W1', alias2: 'W2', positionId1: 'p1', positionId2: 'p2', letter1: 'A', letter2: 'B' }],
      ownership: 'range',
    },
  ],
  savedPassen: [],
  savedGlobalPassen: [
    {
      id: '_sg_global_passe_1',
      name: 'Olympisch Vorlage',
      serien: [{ id: '_sg_range_serie_1', alias: 'Olympisch', rangeId: 'r1', rangeName: 'Platz 1', steps: [] }],
      ownership: 'global',
    },
    {
      id: '_sg_global_passe_2',
      name: 'Skeet Vorlage',
      serien: [{ id: '_sg_range_serie_2', alias: 'Skeet', rangeId: 'r2', rangeName: 'Platz 2', steps: [] }],
      ownership: 'global',
    },
  ],
  createRangeSerie: vi.fn(),
  updateSerie: vi.fn(),
  deleteSerie: vi.fn(),
  createGlobalPasse: vi.fn(),
  updateGlobalPasse: vi.fn(),
  deleteGlobalPasse: vi.fn(),
}

const mockRangeStore = {
  ranges: [{ id: 'r1', name: 'Platz 1' }, { id: 'r2', name: 'Platz 2' }],
  positions: {},
  loadPositions: vi.fn().mockResolvedValue(undefined),
  initialize: vi.fn().mockResolvedValue(undefined),
}

vi.mock('@/stores/passeStore.js', () => ({ usePasseStore: () => mockPasseStore }))
vi.mock('@/stores/rangeStore.js', () => ({ useRangeStore: () => mockRangeStore }))
vi.mock('@/components/SerieDrawer.vue', () => ({
  default: { template: '<div data-testid="serie-drawer" />', props: ['open', 'mode', 'serie'], emits: ['saved', 'deleted', 'close'] },
}))
vi.mock('@/components/GlobalPasseDrawer.vue', () => ({
  default: { template: '<div data-testid="global-passe-drawer" />', props: ['open', 'mode', 'passe'], emits: ['saved', 'deleted', 'close'] },
}))

describe('PassenAdminView — Serien tab', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders Serien and Passen tab buttons', () => {
    const wrapper = mount(PassenAdminView)
    const tabs = wrapper.findAll('.tab-btn')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].text()).toContain('Serien')
    expect(tabs[1].text()).toContain('Passen')
  })

  it('shows Platz-Serien header with count badge on Serien tab', () => {
    const wrapper = mount(PassenAdminView)
    expect(wrapper.text()).toContain('Platz-Serien')
    expect(wrapper.text()).toContain('2')
  })

  it('groups Serien by range', () => {
    const wrapper = mount(PassenAdminView)
    expect(wrapper.text()).toContain('Platz 1')
    expect(wrapper.text()).toContain('Platz 2')
  })

  it('shows Serien names within their groups', () => {
    const wrapper = mount(PassenAdminView)
    expect(wrapper.text()).toContain('Olympisch')
    expect(wrapper.text()).toContain('Skeet')
  })

  it('opens SerieDrawer in create mode when Neue Serie button is clicked', async () => {
    const wrapper = mount(PassenAdminView)
    await wrapper.find('[data-testid="new-serie-btn"]').trigger('click')
    expect(wrapper.find('[data-testid="serie-drawer"]').exists()).toBe(true)
    expect(wrapper.vm.drawerOpen).toBe(true)
    expect(wrapper.vm.drawerMode).toBe('create')
  })

  it('shows empty state when no Platz-Serien exist', () => {
    const original = mockPasseStore.savedSerien
    mockPasseStore.savedSerien = []
    try {
      const wrapper = mount(PassenAdminView)
      expect(wrapper.text()).toContain('Noch keine Platz-Serien')
    } finally {
      mockPasseStore.savedSerien = original
    }
  })

  it('opens SerieDrawer in edit mode when a serie row is clicked', async () => {
    const wrapper = mount(PassenAdminView)
    await wrapper.vm.$nextTick()
    await wrapper.find('.serie-row').trigger('click')
    expect(wrapper.vm.drawerOpen).toBe(true)
    expect(wrapper.vm.drawerMode).toBe('edit')
    expect(wrapper.vm.drawerSerie).toEqual(mockPasseStore.savedSerien[0])
  })
})

describe('PassenAdminView — Passen tab', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  const switchToPassen = async (wrapper) => {
    await wrapper.findAll('.tab-btn')[1].trigger('click')
  }

  it('shows Passen-Vorlagen header with count badge', async () => {
    const wrapper = mount(PassenAdminView)
    await switchToPassen(wrapper)
    expect(wrapper.text()).toContain('Passen-Vorlagen')
    expect(wrapper.text()).toContain('2')
  })

  it('groups Passen by range', async () => {
    const wrapper = mount(PassenAdminView)
    await switchToPassen(wrapper)
    expect(wrapper.text()).toContain('Platz 1')
    expect(wrapper.text()).toContain('Platz 2')
  })

  it('shows Passen names in list', async () => {
    const wrapper = mount(PassenAdminView)
    await switchToPassen(wrapper)
    expect(wrapper.text()).toContain('Olympisch Vorlage')
    expect(wrapper.text()).toContain('Skeet Vorlage')
  })

  it('opens GlobalPasseDrawer in create mode when Neue Passe button is clicked', async () => {
    const wrapper = mount(PassenAdminView)
    await switchToPassen(wrapper)
    await wrapper.find('[data-testid="new-passe-btn"]').trigger('click')
    expect(wrapper.vm.passeDrawerOpen).toBe(true)
    expect(wrapper.vm.passeDrawerMode).toBe('create')
  })

  it('opens GlobalPasseDrawer in edit mode when a passe row is clicked', async () => {
    const wrapper = mount(PassenAdminView)
    await switchToPassen(wrapper)
    await wrapper.vm.$nextTick()
    await wrapper.find('.passe-row').trigger('click')
    expect(wrapper.vm.passeDrawerOpen).toBe(true)
    expect(wrapper.vm.passeDrawerMode).toBe('edit')
    expect(wrapper.vm.passeDrawerPasse).toEqual(mockPasseStore.savedGlobalPassen[0])
  })

  it('shows empty state when no Passen-Vorlagen exist', async () => {
    const original = mockPasseStore.savedGlobalPassen
    mockPasseStore.savedGlobalPassen = []
    try {
      const wrapper = mount(PassenAdminView)
      await switchToPassen(wrapper)
      expect(wrapper.text()).toContain('Noch keine Passen-Vorlagen')
    } finally {
      mockPasseStore.savedGlobalPassen = original
    }
  })
})
```

- [ ] **Step 2: Run tests to verify the new Passen tab tests fail (Serien tab tests should still pass)**

```
npm run test src/views/__tests__/PassenAdminView.test.js
```

Expected: the 7 existing Serien tab tests pass; the 6 new Passen tab tests fail with errors like "Cannot find element '[data-testid=new-passe-btn]'" and "Cannot read properties of undefined (reading 'passeDrawerOpen')".

- [ ] **Step 3: Update `src/views/PassenAdminView.vue`**

In `<script setup>`, add the import for `GlobalPasseDrawer` after the `SerieDrawer` import:

```javascript
import GlobalPasseDrawer from '@/components/GlobalPasseDrawer.vue';
```

In `<script setup>`, after the existing `rangeGroups` computed, add:

```javascript
// ── Passen data ───────────────────────────────────────────────────────────────
const savedGlobalPassen = computed(() => passeStore.savedGlobalPassen ?? []);

const passeGroups = computed(() => {
  const map = new Map();
  for (const p of savedGlobalPassen.value) {
    const firstSerie = p.serien?.[0];
    const rangeId = firstSerie?.rangeId ?? '__none__';
    const rangeName = firstSerie?.rangeName ?? null;
    if (!map.has(rangeId)) {
      map.set(rangeId, { rangeId, rangeName, passen: [] });
    }
    map.get(rangeId).passen.push(p);
  }
  return Array.from(map.values());
});
```

After `expandedGroups` and `toggleGroup`, add:

```javascript
// ── Passe group expand/collapse ───────────────────────────────────────────────
const expandedPasseGroups = ref(new Set());

const togglePasseGroup = (key) => {
  const next = new Set(expandedPasseGroups.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  expandedPasseGroups.value = next;
};

watchEffect(() => {
  for (const g of passeGroups.value) {
    const key = g.rangeId ?? '__none__';
    if (!expandedPasseGroups.value.has(key)) {
      expandedPasseGroups.value = new Set([...expandedPasseGroups.value, key]);
    }
  }
});
```

After the existing `drawerOpen/drawerMode/drawerSerie` block and `openCreate/openEdit`, add:

```javascript
// ── Passe drawer state ────────────────────────────────────────────────────────
const passeDrawerOpen = ref(false);
const passeDrawerMode = ref('create');
const passeDrawerPasse = ref(null);

const openPasseCreate = () => {
  passeDrawerPasse.value = null;
  passeDrawerMode.value = 'create';
  passeDrawerOpen.value = true;
};

const openPasseEdit = (passe) => {
  passeDrawerPasse.value = passe;
  passeDrawerMode.value = 'edit';
  passeDrawerOpen.value = true;
};
```

Update `defineExpose` to include the new refs:

```javascript
defineExpose({ drawerOpen, drawerMode, drawerSerie, passeDrawerOpen, passeDrawerMode, passeDrawerPasse });
```

Replace the Passen tab stub template block (the `<template v-if="activeTab === 'passen'">` block) with:

```vue
<!-- ══════════════════════════════════════════ -->
<!-- PASSEN TAB                                      -->
<!-- ══════════════════════════════════════════ -->
<template v-if="activeTab === 'passen'">
  <!-- Section header -->
  <div class="section-header">
    <div class="section-title-row">
      <h2 class="section-title">
        <Icons icon="program" :size="18" color="#4fc3f7" />
        Passen-Vorlagen
      </h2>
      <span class="badge badge-blue">{{ savedGlobalPassen.length }}</span>
    </div>
    <button
      class="btn btn--primary"
      data-testid="new-passe-btn"
      @click="openPasseCreate"
    >
      <Icons icon="plus" :size="14" color="#fff" />
      Neue Passe
    </button>
  </div>

  <!-- Grouped list -->
  <div v-if="passeGroups.length > 0" class="range-groups">
    <div
      v-for="group in passeGroups"
      :key="group.rangeId ?? '__none__'"
      class="range-group"
    >
      <!-- Group header -->
      <button
        class="range-group-header"
        @click="togglePasseGroup(group.rangeId ?? '__none__')"
      >
        <Icons
          icon="chevronRight"
          :size="13"
          color="#a0aec0"
          class="group-chevron"
          :class="{ rotated: expandedPasseGroups.has(group.rangeId ?? '__none__') }"
        />
        <Icons icon="ranges" :size="13" color="#4fc3f7" />
        <span class="range-group-name">{{ group.rangeName ?? 'Kein Platz' }}</span>
        <span class="range-group-count">{{ group.passen.length }}</span>
      </button>

      <!-- Passe rows -->
      <div
        v-if="expandedPasseGroups.has(group.rangeId ?? '__none__')"
        class="serie-list"
      >
        <div
          v-for="p in group.passen"
          :key="p.id"
          class="serie-row passe-row"
          @click="openPasseEdit(p)"
        >
          <div class="serie-info">
            <span class="serie-name">{{ p.name }}</span>
            <span class="serie-meta">{{ p.serien.length }} Serien</span>
          </div>
          <div class="serie-actions" @click.stop>
            <button
              class="icon-btn"
              title="Bearbeiten"
              @click="openPasseEdit(p)"
            >
              <Icons icon="edit" :size="13" color="#718096" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Empty state -->
  <div v-else class="empty-state">
    <Icons icon="program" :size="40" color="rgba(0,0,0,0.08)" />
    <p class="empty-title">Noch keine Passen-Vorlagen</p>
    <p class="empty-hint">
      Klicke auf „Neue Passe", um eine Passen-Vorlage zu erstellen.
    </p>
  </div>
</template>
```

After the existing `<SerieDrawer ...>` component, add:

```vue
<GlobalPasseDrawer
  :open="passeDrawerOpen"
  :mode="passeDrawerMode"
  :passe="passeDrawerPasse"
  @saved="passeDrawerOpen = false"
  @deleted="passeDrawerOpen = false"
  @close="passeDrawerOpen = false"
/>
```

- [ ] **Step 4: Run all tests to verify everything passes**

```
npm run test
```

Expected: all tests pass. The Serien tab tests still green; all 6 new Passen tab tests green; drawer tests green; store tests green.

- [ ] **Step 5: Run lint**

```
npm run lint
```

Expected: no warnings or errors.

- [ ] **Step 6: Commit**

```
git add src/views/PassenAdminView.vue src/views/__tests__/PassenAdminView.test.js
git commit -m "[ui] Replace Passen stub tab with full Passen-Vorlagen list and drawer"
```
