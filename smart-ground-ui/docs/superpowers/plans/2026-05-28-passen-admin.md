# Passen & Serien Admin View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite `PassenAdminView.vue` into a tabbed admin view with full CRUD for Platz-Serien via a slide-out drawer, plus a Passen stub tab.

**Architecture:** Two new `passeStore` methods handle localStorage persistence without touching `editingSerie`. A new `SerieDrawer.vue` component owns all create/edit form state. `PassenAdminView.vue` owns list + tab + drawer-open state only.

**Tech Stack:** Vue 3 (Composition API, `<script setup>`), Pinia, Vitest + @vue/test-utils, localStorage

---

## File Map

| Action | Path | Responsibility |
|---|---|---|
| Modify | `src/stores/passeStore.js` | Add `createRangeSerie`, `updateSerie` |
| Modify | `src/stores/__tests__/passeStore.test.js` | Tests for new store methods |
| Create | `src/components/SerieDrawer.vue` | Create/edit drawer component |
| Create | `src/components/__tests__/SerieDrawer.test.js` | Component tests for drawer |
| Rewrite | `src/views/PassenAdminView.vue` | Tabs, list, drawer integration |
| Create | `src/views/__tests__/PassenAdminView.test.js` | Component tests for view |

---

## Task 1: Add `createRangeSerie` and `updateSerie` to passeStore

**Files:**
- Modify: `src/stores/__tests__/passeStore.test.js`
- Modify: `src/stores/passeStore.js`

- [ ] **Step 1: Add failing tests for `createRangeSerie`**

Append to `src/stores/__tests__/passeStore.test.js`:

```javascript
describe('passeStore.createRangeSerie', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('adds a range-owned serie to savedSerien', () => {
    const store = usePasseStore()
    const steps = [{ id: 1, type: 'solo', alias: 'Werfer 1', positionId: 'pos-1', letter: 'A' }]
    store.createRangeSerie('Test Serie', 'range-1', 'Platz 1', steps)
    expect(store.savedSerien).toHaveLength(1)
    const serie = store.savedSerien[0]
    expect(serie.name).toBe('Test Serie')
    expect(serie.ownership).toBe('range')
    expect(serie.rangeId).toBe('range-1')
    expect(serie.rangeName).toBe('Platz 1')
    expect(serie.steps).toHaveLength(1)
  })

  it('persists to localStorage with _sg_range_serie_ prefix', () => {
    const store = usePasseStore()
    const steps = [{ id: 1, type: 'solo', alias: 'W1', positionId: 'pos-1', letter: 'A' }]
    store.createRangeSerie('My Serie', 'range-1', 'Platz 1', steps)
    const key = Object.keys(localStorage).find(k => k.startsWith('_sg_range_serie_'))
    expect(key).toBeDefined()
    const stored = JSON.parse(localStorage.getItem(key))
    expect(stored.serieName).toBe('My Serie')
    expect(stored.ownership).toBe('range')
    expect(stored.rangeId).toBe('range-1')
  })

  it('does nothing when steps array is empty', () => {
    const store = usePasseStore()
    store.createRangeSerie('Empty', 'range-1', 'Platz 1', [])
    expect(store.savedSerien).toHaveLength(0)
    expect(Object.keys(localStorage)).toHaveLength(0)
  })

  it('falls back to generated name when name is blank', () => {
    const store = usePasseStore()
    const steps = [{ id: 1, type: 'solo', alias: 'W1', positionId: 'pos-1', letter: 'A' }]
    store.createRangeSerie('  ', 'range-1', 'Platz 1', steps)
    expect(store.savedSerien[0].name).toBe('Serie 1')
  })
})
```

- [ ] **Step 2: Run new tests to confirm they fail**

```
npm run test src/stores/__tests__/passeStore.test.js
```

Expected: 4 new failures — `store.createRangeSerie is not a function`

- [ ] **Step 3: Implement `createRangeSerie` in passeStore**

In `src/stores/passeStore.js`, add after the `renameSerie` function (around line 282):

```javascript
const createRangeSerie = (name, rangeId, rangeName, steps) => {
  if (!steps || steps.length === 0) return;
  const trimmedName = name?.trim() || `Serie ${savedSerien.value.length + 1}`;
  const key = nextRangeSerieKey();
  const createdAt = Date.now();
  const data = { serieName: trimmedName, rangeId, rangeName, steps, createdAt, ownership: 'range' };
  localStorage.setItem(key, JSON.stringify(data));
  savedSerien.value = [
    ...savedSerien.value,
    { id: key, name: trimmedName, rangeId, rangeName, steps: [...steps], createdAt, ownership: 'range' },
  ];
};
```

Also add `createRangeSerie` to the `return` object at the bottom of the store.

- [ ] **Step 4: Run tests to confirm they pass**

```
npm run test src/stores/__tests__/passeStore.test.js
```

Expected: all `createRangeSerie` tests PASS

- [ ] **Step 5: Add failing tests for `updateSerie`**

Append to `src/stores/__tests__/passeStore.test.js`:

```javascript
describe('passeStore.updateSerie', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('updates name and steps in memory', () => {
    const store = usePasseStore()
    const steps = [{ id: 1, type: 'solo', alias: 'W1', positionId: 'pos-1', letter: 'A' }]
    store.createRangeSerie('Old Name', 'range-1', 'Platz 1', steps)
    const serieId = store.savedSerien[0].id
    const newSteps = [
      { id: 1, type: 'solo', alias: 'W1', positionId: 'pos-1', letter: 'A' },
      { id: 2, type: 'solo', alias: 'W2', positionId: 'pos-2', letter: 'B' },
    ]
    store.updateSerie(serieId, 'New Name', newSteps)
    expect(store.savedSerien[0].name).toBe('New Name')
    expect(store.savedSerien[0].steps).toHaveLength(2)
  })

  it('persists changes to localStorage', () => {
    const store = usePasseStore()
    const steps = [{ id: 1, type: 'solo', alias: 'W1', positionId: 'pos-1', letter: 'A' }]
    store.createRangeSerie('Old Name', 'range-1', 'Platz 1', steps)
    const serieId = store.savedSerien[0].id
    const newSteps = [
      { id: 1, type: 'solo', alias: 'W1', positionId: 'pos-1', letter: 'A' },
      { id: 2, type: 'raffale', alias: 'W2', positionId: 'pos-2', letter: 'B' },
    ]
    store.updateSerie(serieId, 'New Name', newSteps)
    const stored = JSON.parse(localStorage.getItem(serieId))
    expect(stored.serieName).toBe('New Name')
    expect(stored.steps).toHaveLength(2)
  })

  it('does not throw when serie id is not found', () => {
    const store = usePasseStore()
    expect(() => store.updateSerie('nonexistent-id', 'Name', [])).not.toThrow()
  })
})
```

- [ ] **Step 6: Run new tests to confirm they fail**

```
npm run test src/stores/__tests__/passeStore.test.js
```

Expected: 3 new failures — `store.updateSerie is not a function`

- [ ] **Step 7: Implement `updateSerie` in passeStore**

In `src/stores/passeStore.js`, add after `createRangeSerie`:

```javascript
const updateSerie = (serieId, newName, newSteps) => {
  const serie = savedSerien.value.find((s) => s.id === serieId);
  if (!serie) return;
  serie.name = newName;
  serie.steps = [...newSteps];
  try {
    const stored = JSON.parse(localStorage.getItem(serieId));
    if (stored) {
      stored.serieName = newName;
      stored.steps = newSteps;
      localStorage.setItem(serieId, JSON.stringify(stored));
    }
  } catch { /* ignorieren */ }
};
```

Also add `updateSerie` to the `return` object.

- [ ] **Step 8: Run all store tests to confirm they pass**

```
npm run test src/stores/__tests__/passeStore.test.js
```

Expected: all tests PASS

- [ ] **Step 9: Commit**

```
git add src/stores/passeStore.js src/stores/__tests__/passeStore.test.js
git commit -m "[ui] Add createRangeSerie and updateSerie to passeStore"
```

---

## Task 2: Build `SerieDrawer.vue` — create mode

**Files:**
- Create: `src/components/__tests__/SerieDrawer.test.js`
- Create: `src/components/SerieDrawer.vue`

- [ ] **Step 1: Write failing tests for create mode**

Create `src/components/__tests__/SerieDrawer.test.js`:

```javascript
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import SerieDrawer from '../SerieDrawer.vue'

const mockRangeStore = {
  ranges: [
    { id: 'r1', name: 'Platz 1' },
    { id: 'r2', name: 'Platz 2' },
  ],
  positions: {
    r1: [
      { id: 'pos-a', label: 'A', device: { alias: 'Werfer 1' } },
      { id: 'pos-b', label: 'B', device: { alias: 'Werfer 2' } },
    ],
  },
  loadPositions: vi.fn().mockResolvedValue(undefined),
}

const mockPasseStore = {
  createRangeSerie: vi.fn(),
  updateSerie: vi.fn(),
  deleteSerie: vi.fn(),
}

vi.mock('@/stores/rangeStore.js', () => ({
  useRangeStore: () => mockRangeStore,
}))

vi.mock('@/stores/passeStore.js', () => ({
  usePasseStore: () => mockPasseStore,
}))

describe('SerieDrawer — create mode', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders name input and range picker when open in create mode', () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'create', serie: null },
    })
    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
    expect(wrapper.find('select').exists()).toBe(true)
  })

  it('does not render when open is false', () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: false, mode: 'create', serie: null },
    })
    expect(wrapper.find('[data-testid="serie-drawer"]').exists()).toBe(false)
  })

  it('save button is disabled when name is empty', () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'create', serie: null },
    })
    const saveBtn = wrapper.find('[data-testid="save-btn"]')
    expect(saveBtn.attributes('disabled')).toBeDefined()
  })

  it('emits close when backdrop is clicked', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'create', serie: null },
    })
    await wrapper.find('.drawer-backdrop').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('emits close when Abbrechen is clicked', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'create', serie: null },
    })
    await wrapper.find('[data-testid="cancel-btn"]').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('calls loadPositions and shows position buttons after range is selected', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'create', serie: null },
    })
    const select = wrapper.find('select')
    await select.setValue('r1')
    await select.trigger('change')
    expect(mockRangeStore.loadPositions).toHaveBeenCalledWith('r1')
    // After loadPositions resolves, position buttons should appear
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('[data-testid="pos-btn"]').length).toBeGreaterThan(0)
  })
})
```

- [ ] **Step 2: Run tests to confirm they fail**

```
npm run test src/components/__tests__/SerieDrawer.test.js
```

Expected: all fail — component does not exist yet

- [ ] **Step 3: Create `SerieDrawer.vue` with create mode**

Create `src/components/SerieDrawer.vue`:

```vue
<template>
  <Teleport to="body">
    <template v-if="open">
      <div class="drawer-backdrop" @click="$emit('close')" />
      <div class="drawer" data-testid="serie-drawer">
        <!-- Header -->
        <div class="drawer-header">
          <h2 class="drawer-title">
            {{ isCreate ? 'Neue Platz-Serie' : editingName }}
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

          <!-- Range picker (create) or range label (edit) -->
          <div class="field">
            <label class="field-label">Platz</label>
            <select
              v-if="isCreate"
              v-model="selectedRangeId"
              class="field-select"
              @change="onRangeChange"
            >
              <option value="">Platz wählen…</option>
              <option v-for="r in rangeStore.ranges" :key="r.id" :value="r.id">
                {{ r.name }}
              </option>
            </select>
            <span v-else class="field-readonly">{{ serie?.rangeName }}</span>
          </div>

          <!-- Step type toggle -->
          <div v-if="rangePositions.length > 0" class="field">
            <label class="field-label">Schuss-Typ</label>
            <div class="type-toggle">
              <button
                v-for="t in STEP_TYPES"
                :key="t.value"
                class="type-btn"
                :class="{ active: stepMode === t.value }"
                @click="stepMode = t.value; pairPending = null"
              >
                {{ t.label }}
              </button>
            </div>
          </div>

          <!-- Position grid -->
          <div v-if="rangePositions.length > 0" class="field">
            <label class="field-label">
              Positionen
              <span v-if="pairPending" class="pending-hint">
                — {{ pairPending.letter }} gewählt, zweite Position tippen
              </span>
            </label>
            <div class="position-grid">
              <button
                v-for="pos in rangePositions"
                :key="pos.id"
                class="pos-btn"
                :class="{ 'pos-btn--pending': pairPending?.id === pos.id }"
                data-testid="pos-btn"
                @click="addStep(pos)"
              >
                {{ pos.label }}
              </button>
            </div>
          </div>

          <!-- Step sequence -->
          <div v-if="steps.length > 0" class="field">
            <label class="field-label">
              Ablauf
              <span class="step-count">{{ totalThrows }} Würfe · {{ steps.length }} Schritte</span>
            </label>
            <div class="step-list">
              <div v-for="(step, i) in steps" :key="step.id" class="step-row">
                <span class="step-dot" :class="`dot-${step.type}`" />
                <span class="step-label">{{ stepLabel(step) }}</span>
                <button class="step-remove" @click="removeStep(i)">
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
              Serie löschen
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
import { useRangeStore } from '@/stores/rangeStore.js';
import { usePasseStore } from '@/stores/passeStore.js';
import Icons from '@/components/Icons.vue';

const props = defineProps({
  open: { type: Boolean, required: true },
  mode: { type: String, required: true }, // 'create' | 'edit'
  serie: { type: Object, default: null },
});

const emit = defineEmits(['saved', 'deleted', 'close']);

const rangeStore = useRangeStore();
const passeStore = usePasseStore();

const STEP_TYPES = [
  { value: 'solo', label: 'Solo' },
  { value: 'pair', label: 'Pair' },
  { value: 'a_schuss', label: 'a.Schuss' },
  { value: 'raffale', label: 'Raffale' },
];

const isCreate = computed(() => props.mode === 'create');

// ── Form state ──────────────────────────────────────────────────────────────
const editingName = ref('');
const selectedRangeId = ref('');
const stepMode = ref('solo');
const steps = ref([]);
const pairPending = ref(null);
const confirmingDelete = ref(false);

// Reset state when drawer opens
watch(() => props.open, (open) => {
  if (!open) return;
  confirmingDelete.value = false;
  pairPending.value = null;
  stepMode.value = 'solo';
  if (props.mode === 'create') {
    editingName.value = '';
    selectedRangeId.value = '';
    steps.value = [];
  } else if (props.serie) {
    editingName.value = props.serie.name;
    selectedRangeId.value = props.serie.rangeId ?? '';
    steps.value = [...(props.serie.steps ?? [])];
    if (props.serie.rangeId) {
      rangeStore.loadPositions(props.serie.rangeId);
    }
  }
});

// ── Positions ───────────────────────────────────────────────────────────────
const activeRangeId = computed(() =>
  isCreate.value ? selectedRangeId.value : props.serie?.rangeId ?? ''
);

const rangePositions = computed(() =>
  activeRangeId.value ? (rangeStore.positions[activeRangeId.value] ?? []) : []
);

const onRangeChange = () => {
  if (selectedRangeId.value) {
    rangeStore.loadPositions(selectedRangeId.value);
  }
};

// ── Step builder ────────────────────────────────────────────────────────────
const addStep = (position) => {
  const alias = position.device?.alias ?? position.label;
  const letter = position.label;

  if (stepMode.value === 'solo') {
    steps.value = [...steps.value, { id: Date.now(), type: 'solo', alias, positionId: position.id, letter }];
  } else if (stepMode.value === 'raffale') {
    steps.value = [...steps.value, { id: Date.now(), type: 'raffale', alias, positionId: position.id, letter }];
    stepMode.value = 'solo';
  } else if (stepMode.value === 'pair' || stepMode.value === 'a_schuss') {
    if (!pairPending.value) {
      pairPending.value = { id: position.id, alias, letter };
    } else if (pairPending.value.id === position.id) {
      pairPending.value = null;
    } else {
      const stepType = stepMode.value === 'a_schuss' ? 'a_schuss' : 'pair';
      steps.value = [...steps.value, {
        id: Date.now(),
        type: stepType,
        alias1: pairPending.value.alias,
        alias2: alias,
        positionId1: pairPending.value.id,
        positionId2: position.id,
        letter1: pairPending.value.letter,
        letter2: letter,
      }];
      pairPending.value = null;
      stepMode.value = 'solo';
    }
  }
};

const removeStep = (index) => {
  const s = [...steps.value];
  s.splice(index, 1);
  steps.value = s;
};

// ── Helpers ─────────────────────────────────────────────────────────────────
const totalThrows = computed(() => {
  let count = 0;
  for (const s of steps.value) {
    if (s.type === 'solo' || s.type === 'raffale') count += 1;
    else if (s.type === 'pair' || s.type === 'a_schuss') count += 2;
  }
  return count;
});

const stepLabel = (step) => {
  if (step.type === 'solo' || step.type === 'raffale') {
    return `${step.letter} — ${step.alias}`;
  }
  return `${step.letter1}+${step.letter2} — ${step.alias1} & ${step.alias2}`;
};

const canSave = computed(() => {
  if (!editingName.value.trim()) return false;
  if (isCreate.value && !selectedRangeId.value) return false;
  return steps.value.length > 0;
});

// ── Actions ──────────────────────────────────────────────────────────────────
const save = () => {
  if (!canSave.value) return;
  if (isCreate.value) {
    const selectedRange = rangeStore.ranges.find(r => r.id === selectedRangeId.value);
    passeStore.createRangeSerie(
      editingName.value.trim(),
      selectedRangeId.value,
      selectedRange?.name ?? null,
      steps.value,
    );
  } else {
    passeStore.updateSerie(props.serie.id, editingName.value.trim(), steps.value);
  }
  emit('saved');
  emit('close');
};

const deleteAndClose = () => {
  if (props.serie) {
    passeStore.deleteSerie(props.serie.id);
    emit('deleted');
    emit('close');
  }
};
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

/* ── Body ─────────────────────────────────────────────────────────────────── */
.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ── Fields ──────────────────────────────────────────────────────────────── */
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

.field-input,
.field-select {
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

.field-input:focus,
.field-select:focus { border-color: #4fc3f7; }

.field-readonly {
  font-size: 14px;
  font-weight: 600;
  color: #4a5568;
  padding: 9px 0;
}

/* ── Step type toggle ────────────────────────────────────────────────────── */
.type-toggle {
  display: flex;
  gap: 6px;
}

.type-btn {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  background: #fff;
  color: #4a5568;
  cursor: pointer;
  transition: all 0.15s;
}

.type-btn.active {
  background: rgba(79, 195, 247, 0.12);
  border-color: rgba(79, 195, 247, 0.5);
  color: #0288d1;
}

.type-btn:hover:not(.active) { background: #f7fafc; }

/* ── Position grid ───────────────────────────────────────────────────────── */
.position-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pos-btn {
  width: 48px;
  height: 48px;
  border: 1.5px solid #e2e8f0;
  border-radius: 10px;
  font-size: 18px;
  font-weight: 800;
  color: #1a1a2e;
  background: #fff;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pos-btn:hover {
  border-color: #4fc3f7;
  background: rgba(79, 195, 247, 0.06);
}

.pos-btn--pending {
  background: rgba(79, 195, 247, 0.15);
  border-color: #4fc3f7;
  color: #0288d1;
}

.pending-hint {
  font-size: 11px;
  color: #0288d1;
  font-weight: 500;
  text-transform: none;
  letter-spacing: 0;
}

/* ── Step list ───────────────────────────────────────────────────────────── */
.step-count {
  font-size: 11px;
  font-weight: 600;
  color: #a0aec0;
  text-transform: none;
  letter-spacing: 0;
}

.step-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 220px;
  overflow-y: auto;
}

.step-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  background: #f7f8fc;
  border-radius: 7px;
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.step-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot-solo     { background: #4fc3f7; }
.dot-pair     { background: #48bb78; }
.dot-a_schuss { background: #f6ad55; }
.dot-raffale  { background: #a855f7; }

.step-label {
  flex: 1;
  font-size: 12.5px;
  color: #4a5568;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

/* ── Footer ──────────────────────────────────────────────────────────────── */
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 24px;
  border-top: 1px solid #e2e8f0;
  flex-shrink: 0;
}

/* ── Delete zone ─────────────────────────────────────────────────────────── */
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

/* ── Buttons ─────────────────────────────────────────────────────────────── */
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

- [ ] **Step 4: Run tests to confirm create mode passes**

```
npm run test src/components/__tests__/SerieDrawer.test.js
```

Expected: all create mode tests PASS

- [ ] **Step 5: Commit**

```
git add src/components/SerieDrawer.vue src/components/__tests__/SerieDrawer.test.js
git commit -m "[ui] Add SerieDrawer component — create mode"
```

---

## Task 3: Extend `SerieDrawer.vue` — edit mode tests

**Files:**
- Modify: `src/components/__tests__/SerieDrawer.test.js`

- [ ] **Step 1: Add failing tests for edit mode**

Append to `src/components/__tests__/SerieDrawer.test.js`:

```javascript
describe('SerieDrawer — edit mode', () => {
  const mockSerie = {
    id: '_sg_range_serie_1',
    name: 'Test Serie',
    rangeId: 'r1',
    rangeName: 'Platz 1',
    steps: [
      { id: 1, type: 'solo', alias: 'Werfer 1', positionId: 'pos-a', letter: 'A' },
    ],
    ownership: 'range',
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('pre-fills name input in edit mode', () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'edit', serie: mockSerie },
    })
    expect(wrapper.find('input[type="text"]').element.value).toBe('Test Serie')
  })

  it('shows range as read-only label instead of picker in edit mode', () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'edit', serie: mockSerie },
    })
    expect(wrapper.find('select').exists()).toBe(false)
    expect(wrapper.find('.field-readonly').text()).toBe('Platz 1')
  })

  it('pre-populates step sequence with existing steps', () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'edit', serie: mockSerie },
    })
    expect(wrapper.findAll('.step-row')).toHaveLength(1)
  })

  it('shows delete button in edit mode', () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'edit', serie: mockSerie },
    })
    expect(wrapper.find('[data-testid="delete-btn"]').exists()).toBe(true)
  })

  it('does not show delete button in create mode', () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'create', serie: null },
    })
    expect(wrapper.find('[data-testid="delete-btn"]').exists()).toBe(false)
  })

  it('calls updateSerie and emits saved+close when save is clicked in edit mode', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'edit', serie: mockSerie },
    })
    // Save button should be enabled (has name + steps from pre-population)
    const saveBtn = wrapper.find('[data-testid="save-btn"]')
    expect(saveBtn.attributes('disabled')).toBeUndefined()
    await saveBtn.trigger('click')
    expect(mockPasseStore.updateSerie).toHaveBeenCalledWith(
      '_sg_range_serie_1',
      'Test Serie',
      expect.arrayContaining([expect.objectContaining({ type: 'solo' })]),
    )
    expect(wrapper.emitted('saved')).toBeTruthy()
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('shows confirm UI after delete button clicked', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'edit', serie: mockSerie },
    })
    await wrapper.find('[data-testid="delete-btn"]').trigger('click')
    expect(wrapper.find('.delete-confirm-text').exists()).toBe(true)
  })

  it('calls deleteSerie and emits deleted+close when confirmed', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'edit', serie: mockSerie },
    })
    await wrapper.find('[data-testid="delete-btn"]').trigger('click')
    await wrapper.find('.btn--danger').trigger('click')
    expect(mockPasseStore.deleteSerie).toHaveBeenCalledWith('_sg_range_serie_1')
    expect(wrapper.emitted('deleted')).toBeTruthy()
    expect(wrapper.emitted('close')).toBeTruthy()
  })
})
```

- [ ] **Step 2: Run tests to confirm new edit mode tests pass**

The edit mode tests should pass because `SerieDrawer.vue` already implements edit mode. Run:

```
npm run test src/components/__tests__/SerieDrawer.test.js
```

Expected: all tests PASS

- [ ] **Step 3: Commit**

```
git add src/components/__tests__/SerieDrawer.test.js
git commit -m "[ui] Add SerieDrawer edit mode tests"
```

---

## Task 4: Rewrite `PassenAdminView.vue`

**Files:**
- Create: `src/views/__tests__/PassenAdminView.test.js`
- Rewrite: `src/views/PassenAdminView.vue`

- [ ] **Step 1: Write failing tests**

Create `src/views/__tests__/PassenAdminView.test.js`:

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
  createRangeSerie: vi.fn(),
  updateSerie: vi.fn(),
  deleteSerie: vi.fn(),
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

describe('PassenAdminView', () => {
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
    expect(wrapper.text()).toContain('2') // total count
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

  it('opens drawer in create mode when Neue Serie button is clicked', async () => {
    const wrapper = mount(PassenAdminView)
    await wrapper.find('[data-testid="new-serie-btn"]').trigger('click')
    expect(wrapper.find('[data-testid="serie-drawer"]').exists()).toBe(true)
    expect(wrapper.vm.drawerOpen).toBe(true)
    expect(wrapper.vm.drawerMode).toBe('create')
  })

  it('shows Passen stub when Passen tab is clicked', async () => {
    const wrapper = mount(PassenAdminView)
    const panenTab = wrapper.findAll('.tab-btn')[1]
    await panenTab.trigger('click')
    expect(wrapper.text()).toContain('Passen-Verwaltung')
  })

  it('shows empty state when no Platz-Serien exist', () => {
    mockPasseStore.savedSerien = []
    const wrapper = mount(PassenAdminView)
    expect(wrapper.text()).toContain('Noch keine Platz-Serien')
    mockPasseStore.savedSerien = [
      {
        id: '_sg_range_serie_1',
        name: 'Olympisch',
        rangeId: 'r1',
        rangeName: 'Platz 1',
        steps: [{ id: 1, type: 'solo', alias: 'W1', positionId: 'pos-a', letter: 'A' }],
        ownership: 'range',
      },
    ]
  })
})
```

- [ ] **Step 2: Run tests to confirm they fail**

```
npm run test src/views/__tests__/PassenAdminView.test.js
```

Expected: all fail (view is the old implementation)

- [ ] **Step 3: Rewrite `PassenAdminView.vue`**

Replace the entire content of `src/views/PassenAdminView.vue`:

```vue
<template>
  <div class="passen-admin">
    <!-- Header -->
    <div class="view-header">
      <h1 class="view-title">Passen & Serien</h1>
      <p class="view-subtitle">Platz-weite Serien und Passen verwalten</p>
    </div>

    <!-- Tabs -->
    <div class="tab-row">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'serien' }"
        @click="activeTab = 'serien'"
      >
        Serien
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'passen' }"
        @click="activeTab = 'passen'"
      >
        Passen
      </button>
    </div>

    <!-- ══════════════════════════════════════════════ -->
    <!-- SERIEN TAB                                     -->
    <!-- ══════════════════════════════════════════════ -->
    <template v-if="activeTab === 'serien'">
      <!-- Section header -->
      <div class="section-header">
        <div class="section-title-row">
          <h2 class="section-title">
            <Icons icon="target" :size="18" color="#4fc3f7" />
            Platz-Serien
          </h2>
          <span class="badge badge-blue">{{ rangeSerien.length }}</span>
        </div>
        <button
          class="btn btn--primary"
          data-testid="new-serie-btn"
          @click="openCreate"
        >
          <Icons icon="plus" :size="14" color="#fff" />
          Neue Serie
        </button>
      </div>

      <!-- Grouped list -->
      <div v-if="rangeGroups.length > 0" class="range-groups">
        <div
          v-for="group in rangeGroups"
          :key="group.rangeId ?? '__none__'"
          class="range-group"
        >
          <!-- Group header -->
          <button
            class="range-group-header"
            @click="toggleGroup(group.rangeId ?? '__none__')"
          >
            <Icons
              icon="chevronRight"
              :size="13"
              color="#a0aec0"
              class="group-chevron"
              :class="{ rotated: expandedGroups.has(group.rangeId ?? '__none__') }"
            />
            <Icons icon="ranges" :size="13" color="#4fc3f7" />
            <span class="range-group-name">{{ group.rangeName ?? 'Kein Platz' }}</span>
            <span class="range-group-count">{{ group.serien.length }}</span>
          </button>

          <!-- Serie rows -->
          <div
            v-if="expandedGroups.has(group.rangeId ?? '__none__')"
            class="serie-list"
          >
            <div
              v-for="s in group.serien"
              :key="s.id"
              class="serie-row"
              @click="openEdit(s)"
            >
              <div class="serie-info">
                <span class="serie-name">{{ s.name }}</span>
                <span class="serie-meta">{{ stepCount(s.steps) }} Würfe · {{ s.steps.length }} Schritte</span>
              </div>
              <div class="serie-dots">
                <span
                  v-for="(step, i) in s.steps.slice(0, 10)"
                  :key="step.id ?? i"
                  class="step-dot"
                  :class="`dot-${step.type}`"
                />
                <span v-if="s.steps.length > 10" class="step-dot-more">
                  +{{ s.steps.length - 10 }}
                </span>
              </div>
              <div class="serie-actions" @click.stop>
                <button
                  class="icon-btn"
                  title="Bearbeiten"
                  @click="openEdit(s)"
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
        <Icons icon="target" :size="40" color="rgba(0,0,0,0.08)" />
        <p class="empty-title">Noch keine Platz-Serien</p>
        <p class="empty-hint">
          Klicke auf „Neue Serie", um eine Platz-Serie direkt hier zu erstellen.
        </p>
      </div>
    </template>

    <!-- ══════════════════════════════════════════════ -->
    <!-- PASSEN TAB — STUB                              -->
    <!-- ══════════════════════════════════════════════ -->
    <template v-if="activeTab === 'passen'">
      <div class="stub-card">
        <Icons icon="program" :size="40" color="rgba(0,0,0,0.08)" />
        <p class="stub-title">Passen-Verwaltung</p>
        <p class="stub-desc">Wird in einer nächsten Version verfügbar sein.</p>
      </div>
    </template>

    <!-- Drawer -->
    <SerieDrawer
      :open="drawerOpen"
      :mode="drawerMode"
      :serie="drawerSerie"
      @saved="drawerOpen = false"
      @deleted="drawerOpen = false"
      @close="drawerOpen = false"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watchEffect } from 'vue';
import { usePasseStore } from '@/stores/passeStore.js';
import { useRangeStore } from '@/stores/rangeStore.js';
import Icons from '@/components/Icons.vue';
import SerieDrawer from '@/components/SerieDrawer.vue';

const passeStore = usePasseStore();
const rangeStore = useRangeStore();

onMounted(async () => {
  await rangeStore.initialize();
});

// ── Tabs ─────────────────────────────────────────────────────────────────────
const activeTab = ref('serien');

// ── Serien data ───────────────────────────────────────────────────────────────
const rangeSerien = computed(() =>
  passeStore.savedSerien.filter((s) => s.ownership === 'range')
);

const rangeGroups = computed(() => {
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

// ── Group expand/collapse ─────────────────────────────────────────────────────
const expandedGroups = ref(new Set());

const toggleGroup = (key) => {
  const next = new Set(expandedGroups.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  expandedGroups.value = next;
};

// Auto-expand all groups whenever the list changes
watchEffect(() => {
  for (const g of rangeGroups.value) {
    const key = g.rangeId ?? '__none__';
    if (!expandedGroups.value.has(key)) {
      expandedGroups.value = new Set([...expandedGroups.value, key]);
    }
  }
});

// ── Drawer state ──────────────────────────────────────────────────────────────
const drawerOpen = ref(false);
const drawerMode = ref('create');
const drawerSerie = ref(null);

const openCreate = () => {
  drawerSerie.value = null;
  drawerMode.value = 'create';
  drawerOpen.value = true;
};

const openEdit = (serie) => {
  drawerSerie.value = serie;
  drawerMode.value = 'edit';
  drawerOpen.value = true;
};

// ── Helpers ───────────────────────────────────────────────────────────────────
const stepCount = (steps) => {
  let count = 0;
  for (const s of steps) {
    if (s.type === 'solo' || s.type === 'raffale') count += 1;
    else if (s.type === 'pair' || s.type === 'a_schuss') count += 2;
  }
  return count;
};
</script>

<style scoped>
.passen-admin {
  padding: 24px;
  max-width: 900px;
}

/* ── Header ─────────────────────────────────────────────────────────────── */
.view-header {
  margin-bottom: 20px;
}

.view-title {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 4px;
}

.view-subtitle {
  font-size: 13.5px;
  color: #718096;
  margin: 0;
}

/* ── Tabs ───────────────────────────────────────────────────────────────── */
.tab-row {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
  border-bottom: 1px solid #e2e8f0;
  padding-bottom: 0;
}

.tab-btn {
  padding: 10px 20px;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  color: #a0aec0;
  cursor: pointer;
  margin-bottom: -1px;
  transition: color 0.15s, border-color 0.15s;
}

.tab-btn.active {
  color: #1a1a2e;
  border-bottom-color: #4fc3f7;
}

.tab-btn:hover:not(.active) { color: #4a5568; }

/* ── Section header ─────────────────────────────────────────────────────── */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
}

.section-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.badge {
  font-size: 11px;
  font-weight: 700;
  border-radius: 20px;
  padding: 2px 8px;
}

.badge-blue {
  background: rgba(79, 195, 247, 0.12);
  color: #0288d1;
}

/* ── Range groups ───────────────────────────────────────────────────────── */
.range-groups {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.range-group {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.07);
  overflow: hidden;
}

.range-group-header {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: none;
  border: none;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;
}

.range-group-header:hover { background: #f7fafc; }

.group-chevron {
  flex-shrink: 0;
  transition: transform 0.2s;
}

.group-chevron.rotated { transform: rotate(90deg); }

.range-group-name {
  font-size: 13px;
  font-weight: 600;
  color: #4a5568;
  flex: 1;
}

.range-group-count {
  font-size: 11px;
  color: #a0aec0;
  background: #f7fafc;
  border-radius: 20px;
  padding: 1px 7px;
}

/* ── Serie rows ─────────────────────────────────────────────────────────── */
.serie-list {
  border-top: 1px solid #f0f4f8;
  display: flex;
  flex-direction: column;
}

.serie-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid #f0f4f8;
  cursor: pointer;
  transition: background 0.12s;
}

.serie-row:last-child { border-bottom: none; }
.serie-row:hover { background: #f7f8fc; }

.serie-info {
  flex: 1;
  min-width: 0;
}

.serie-name {
  display: block;
  font-size: 13.5px;
  font-weight: 600;
  color: #1a1a2e;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.serie-meta {
  display: block;
  font-size: 11.5px;
  color: #a0aec0;
  margin-top: 1px;
}

.serie-dots {
  display: flex;
  gap: 3px;
  align-items: center;
  flex-shrink: 0;
}

.step-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot-solo     { background: #4fc3f7; }
.dot-pair     { background: #48bb78; }
.dot-a_schuss { background: #f6ad55; }
.dot-raffale  { background: #a855f7; }

.step-dot-more {
  font-size: 10px;
  color: #a0aec0;
  margin-left: 2px;
}

.serie-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.icon-btn {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}

.icon-btn:hover {
  background: #f0f4f8;
  border-color: #e2e8f0;
}

/* ── Empty state ────────────────────────────────────────────────────────── */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 48px 16px;
  text-align: center;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.07);
}

.empty-title {
  font-size: 14px;
  font-weight: 600;
  color: #a0aec0;
  margin: 0;
}

.empty-hint {
  font-size: 13px;
  color: #cbd5e0;
  margin: 0;
  max-width: 340px;
  line-height: 1.5;
}

/* ── Stub card ──────────────────────────────────────────────────────────── */
.stub-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 48px 16px;
  text-align: center;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.07);
}

.stub-title {
  font-size: 15px;
  font-weight: 700;
  color: #4a5568;
  margin: 0;
}

.stub-desc {
  font-size: 13px;
  color: #a0aec0;
  margin: 0;
}

/* ── Buttons ────────────────────────────────────────────────────────────── */
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

.btn--primary {
  background: #1a1a2e;
  color: #fff;
  border-color: #1a1a2e;
}

.btn--primary:hover { background: #0f0f1a; }
</style>
```

- [ ] **Step 4: Run view tests to confirm they pass**

```
npm run test src/views/__tests__/PassenAdminView.test.js
```

Expected: all tests PASS

- [ ] **Step 5: Run full test suite**

```
npm run test
```

Expected: all tests PASS

- [ ] **Step 6: Run linter**

```
npm run lint
```

Expected: no warnings or errors

- [ ] **Step 7: Commit**

```
git add src/views/PassenAdminView.vue src/views/__tests__/PassenAdminView.test.js
git commit -m "[ui] Rewrite PassenAdminView — tabs, grouped list, SerieDrawer integration"
```

---

## Definition of Done Checklist

- [ ] `passeStore.createRangeSerie` creates a `_sg_range_serie_*` key in localStorage
- [ ] `passeStore.updateSerie` updates name + steps in memory and localStorage
- [ ] Drawer renders in create mode with name input, range picker, position grid, step builder
- [ ] Drawer renders in edit mode with pre-filled name, locked range, pre-populated steps
- [ ] Delete in edit mode shows confirm UI, then calls `deleteSerie`
- [ ] `PassenAdminView` renders Serien and Passen tabs
- [ ] Platz-Serien grouped by range with expand/collapse
- [ ] "Neue Serie" button opens drawer in create mode
- [ ] Clicking a Serie row opens drawer in edit mode
- [ ] Empty state shown when no Platz-Serien exist
- [ ] Passen tab shows stub card
- [ ] `npm run test` — all pass
- [ ] `npm run lint` — no warnings
