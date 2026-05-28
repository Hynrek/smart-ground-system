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
import { ref, computed, watch } from 'vue'
import { usePasseStore } from '@/stores/passeStore.js'
import Icons from '@/components/Icons.vue'

const props = defineProps({
  open: { type: Boolean, required: true },
  mode: { type: String, required: true }, // 'create' | 'edit'
  passe: { type: Object, default: null },
})

const emit = defineEmits(['saved', 'deleted', 'close'])

const passeStore = usePasseStore()

const isCreate = computed(() => props.mode === 'create')

// ── Form state ──────────────────────────────────────────────────────────────
const editingName = ref('')
const selectedSerien = ref([])
const confirmingDelete = ref(false)

watch(() => props.open, (open) => {
  if (!open) return
  confirmingDelete.value = false
  if (props.mode === 'create') {
    editingName.value = ''
    selectedSerien.value = []
  } else if (props.passe) {
    editingName.value = props.passe.name
    // stored serien use 'alias'; picker uses 'name'
    selectedSerien.value = props.passe.serien.map((s) => ({
      id: s.id,
      name: s.alias,
      rangeId: s.rangeId,
      rangeName: s.rangeName,
      steps: s.steps ?? [],
    }))
  }
}, { immediate: true })

// ── Serie picker ─────────────────────────────────────────────────────────────
const rangeSerien = computed(() =>
  passeStore.savedSerien.filter((s) => s.ownership === 'range'),
)

const pickerGroups = computed(() => {
  const map = new Map()
  for (const s of rangeSerien.value) {
    const key = s.rangeId ?? '__none__'
    if (!map.has(key)) {
      map.set(key, { rangeId: s.rangeId, rangeName: s.rangeName, serien: [] })
    }
    map.get(key).serien.push(s)
  }
  return Array.from(map.values())
})

const isSelected = (serieId) => selectedSerien.value.some((s) => s.id === serieId)

const toggleSerie = (serie) => {
  if (isSelected(serie.id)) {
    selectedSerien.value = selectedSerien.value.filter((s) => s.id !== serie.id)
  } else {
    selectedSerien.value = [...selectedSerien.value, serie]
  }
}

const removeSerie = (index) => {
  const list = [...selectedSerien.value]
  list.splice(index, 1)
  selectedSerien.value = list
}

// ── Helpers ─────────────────────────────────────────────────────────────────
const canSave = computed(() =>
  editingName.value.trim().length > 0 && selectedSerien.value.length > 0,
)

// ── Actions ──────────────────────────────────────────────────────────────────
const save = () => {
  if (!canSave.value) return
  if (isCreate.value) {
    passeStore.createGlobalPasse(editingName.value.trim(), selectedSerien.value)
  } else {
    passeStore.updateGlobalPasse(props.passe.id, editingName.value.trim(), selectedSerien.value)
  }
  emit('saved')
  emit('close')
}

const deleteAndClose = () => {
  if (props.passe) {
    passeStore.deleteGlobalPasse(props.passe.id)
    emit('deleted')
    emit('close')
  }
}

defineExpose({ selectedSerien, confirmingDelete })
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
  .drawer {
    width: 100vw;
  }
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

.drawer-close:hover {
  background: #f7fafc;
}

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

.field-input:focus {
  border-color: #4fc3f7;
}

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

.picker-item:hover {
  background: #f7f8fc;
}

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

.step-remove:hover {
  opacity: 1;
}

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

.btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn--primary {
  background: #1a1a2e;
  color: #fff;
  border-color: #1a1a2e;
}

.btn--primary:hover:not(:disabled) {
  background: #0f0f1a;
}

.btn--ghost {
  background: transparent;
  border-color: #e2e8f0;
  color: #4a5568;
}

.btn--ghost:hover {
  background: #f7fafc;
}

.btn--danger-ghost {
  background: transparent;
  border-color: rgba(229, 62, 62, 0.3);
  color: #e53e3e;
}

.btn--danger-ghost:hover {
  background: rgba(229, 62, 62, 0.06);
}

.btn--danger {
  background: rgba(229, 62, 62, 0.1);
  border-color: rgba(229, 62, 62, 0.4);
  color: #e53e3e;
}

.btn--danger:hover {
  background: rgba(229, 62, 62, 0.18);
}
</style>
