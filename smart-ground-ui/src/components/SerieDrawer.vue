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

          <!-- Step type toggle -->
          <div v-if="rangePositions.length > 0" class="field">
            <label class="field-label">Schuss-Typ</label>
            <div class="type-toggle">
              <button
                v-for="t in STEP_TYPES"
                :key="t.type"
                class="type-btn"
                :class="{ active: stepMode === t.type }"
                @click="stepMode = t.type; pairPending = null"
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
                <span class="step-dot" :style="modeDotStyle(step.type)" />
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
import { STEP_MODE_LIST, stepNotation, modeDotStyle } from '@/constants/stepModes.js';

const props = defineProps({
  open: { type: Boolean, required: true },
  mode: { type: String, required: true }, // 'create' | 'edit'
  serie: { type: Object, default: null },
});

const emit = defineEmits(['saved', 'deleted', 'close']);

const rangeStore = useRangeStore();
const passeStore = usePasseStore();

// Mode list, labels, notation, and dot colors come from the shared stepModes
// constant so the Serien editor matches the Shooter views. See constants/stepModes.js.
const STEP_TYPES = STEP_MODE_LIST;

const isCreate = computed(() => props.mode === 'create');

// ── Form state ──────────────────────────────────────────────────────────────
const editingName = ref('');
const selectedRangeId = ref('');
const stepMode = ref('solo');
const steps = ref([]);
const pairPending = ref(null);
const confirmingDelete = ref(false);
const published = ref(false);

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
    published.value = props.serie.published ?? false;
    if (props.serie.rangeId) {
      rangeStore.loadPositions(props.serie.rangeId);
    }
  }
}, { immediate: true });

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

const onPublishedChange = async () => {
  if (!props.serie) return;
  await passeStore.setSeriePublished(props.serie.id, published.value);
};

// Keep local ref in sync with store on rollback or external changes
watch(() => props.serie?.published, (v) => {
  if (v !== undefined) published.value = v;
});

// ── Helpers ─────────────────────────────────────────────────────────────────
const totalThrows = computed(() => {
  let count = 0;
  for (const s of steps.value) {
    if (s.type === 'solo' || s.type === 'raffale') count += 1;
    else if (s.type === 'pair' || s.type === 'a_schuss') count += 2;
  }
  return count;
});

const stepLabel = (step) => `${stepNotation(step)} — ${stepNotation(step, { useAlias: true })}`;

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

defineExpose({ stepMode, pairPending, published });
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

/* ── Position grid ───────────────────────────────────────────────────── */
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

/* ── Step list ───────────────────────────────────────────────────────── */
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
</style>
