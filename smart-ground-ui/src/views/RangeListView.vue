<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useRangeStore } from '@/stores/rangeStore.js'

const router = useRouter()
const rangeStore = useRangeStore()

const ranges = computed(() => rangeStore.ranges)
const loading = computed(() => rangeStore.isLoading)

const showCreateForm = ref(false)
const newName = ref('')
const newDescription = ref('')
const creating = ref(false)
const createError = ref(null)

const editingId = ref(null)
const editName = ref('')
const editDescription = ref('')
const saving = ref(false)
const saveError = ref(null)

function openCreateForm() {
  showCreateForm.value = true
  newName.value = ''
  newDescription.value = ''
  createError.value = null
}

function closeCreateForm() {
  showCreateForm.value = false
  newName.value = ''
  newDescription.value = ''
  createError.value = null
}

async function submitCreate() {
  if (!newName.value.trim()) return
  creating.value = true
  createError.value = null
  try {
    await rangeStore.createRange(newName.value.trim(), newDescription.value.trim() || null)
    closeCreateForm()
  } catch (e) {
    createError.value = e.message
  } finally {
    creating.value = false
  }
}

function startEdit(range) {
  editingId.value = range.id
  editName.value = range.name
  editDescription.value = range.description ?? ''
  saveError.value = null
}

function cancelEdit() {
  editingId.value = null
  editName.value = ''
  editDescription.value = ''
  saveError.value = null
}

async function submitEdit() {
  if (!editName.value.trim()) return
  saving.value = true
  saveError.value = null
  try {
    await rangeStore.updateRange(editingId.value, editName.value.trim(), editDescription.value.trim() || null)
    cancelEdit()
  } catch (e) {
    saveError.value = e.message
  } finally {
    saving.value = false
  }
}

async function confirmDelete(range) {
  if (range.deviceCount > 0) return
  if (!window.confirm(`Möchtest du den Platz „${range.name}" wirklich löschen?`)) return
  try {
    await rangeStore.deleteRange(range.id)
  } catch (e) {
    // handle error
  }
}
</script>

<template>
  <div class="view">
    <div class="view-header">
      <h1 class="view-title">
        Plätze
        <button class="btn-add-inline" :disabled="showCreateForm" title="Neuer Platz" @click="openCreateForm">+</button>
      </h1>
      <div class="header-actions">
        <button class="btn-back" @click="router.push('/smart-boxes')">↩ Zurück zu SmartBoxen</button>
      </div>
    </div>

    <div v-if="rangeStore.error" class="error-banner">
      ⚠️ {{ rangeStore.error }}
    </div>

    <div v-if="showCreateForm" class="create-form-card">
      <h3 class="form-title">Neuen Platz erstellen</h3>
      <div class="form-fields">
        <div class="form-field">
          <label class="form-label">Name</label>
          <input
            v-model="newName"
            type="text"
            maxlength="100"
            class="form-input"
            placeholder="z.B. Parkplatz Nord"
          />
        </div>
        <div class="form-field">
          <label class="form-label">Beschreibung (optional)</label>
          <textarea
            v-model="newDescription"
            class="form-textarea"
            placeholder="Kurze Beschreibung des Platzes"
            rows="2"
          ></textarea>
        </div>
        <div v-if="createError" class="inline-error">{{ createError }}</div>
        <div class="form-actions">
          <button class="btn-save" :disabled="creating || !newName.trim()" @click="submitCreate">
            {{ creating ? 'Erstellen…' : 'Erstellen' }}
          </button>
          <button class="btn-cancel" @click="closeCreateForm">Abbrechen</button>
        </div>
      </div>
    </div>

    <div v-if="loading && ranges.length === 0" class="loading-state">
      Lade Plätze…
    </div>

    <div v-else-if="ranges.length === 0 && !loading" class="empty-state">
      <p>Keine Plätze vorhanden.</p>
      <p class="empty-hint">Erstelle einen Platz, um Geräte zu gruppieren.</p>
    </div>

    <div v-else class="ranges-list">
      <div v-for="range in ranges" :key="range.id" class="range-card">
        <template v-if="editingId === range.id">
          <div class="edit-form">
            <div class="form-field">
              <label class="form-label">Name</label>
              <input
                v-model="editName"
                type="text"
                maxlength="100"
                class="form-input"
              />
            </div>
            <div class="form-field">
              <label class="form-label">Beschreibung</label>
              <textarea
                v-model="editDescription"
                class="form-textarea"
                rows="2"
              ></textarea>
            </div>
            <div v-if="saveError" class="inline-error">{{ saveError }}</div>
            <div class="form-actions">
              <button class="btn-save" :disabled="saving || !editName.trim()" @click="submitEdit">
                {{ saving ? 'Speichern…' : 'Speichern' }}
              </button>
              <button class="btn-cancel" @click="cancelEdit">Abbrechen</button>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="range-info">
            <h3 class="range-name">{{ range.name }}</h3>
            <p v-if="range.description" class="range-description">{{ range.description }}</p>
            <p class="range-device-count">
              {{ range.deviceCount }} Gerät{{ range.deviceCount !== 1 ? 'e' : '' }} zugeordnet
            </p>
          </div>
          <div class="range-actions">
            <button class="btn-edit" @click="startEdit(range)">Bearbeiten</button>
            <button
              class="btn-delete"
              :disabled="range.deviceCount > 0"
              :title="range.deviceCount > 0 ? `Platz hat noch ${range.deviceCount} zugeordnete Geräte` : ''"
              @click="confirmDelete(range)"
            >
              Löschen
            </button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.view {
  max-width: 800px;
  margin: 0 auto;
}

.view-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.5rem;
}

.view-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.btn-add-inline {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.75rem;
  height: 1.75rem;
  padding: 0;
  background: #1a1a2e;
  color: #fff;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  font-size: 1rem;
  font-weight: 600;
  transition: background 0.2s, transform 0.2s;
}

.btn-add-inline:hover:not(:disabled) {
  background: #2d3a6e;
  transform: scale(1.1);
}

.btn-add-inline:disabled {
  opacity: 0.5;
  cursor: default;
}

.header-actions {
  display: flex;
  gap: 0.75rem;
}

.btn-back {
  padding: 0.4rem 1rem;
  border: 1px solid #cbd5e0;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  font-size: 0.875rem;
  color: #4a5568;
  transition: background 0.2s;
}

.btn-back:hover {
  background: #edf2f7;
}

.btn-primary {
  padding: 0.4rem 1rem;
  background: #1a1a2e;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.875rem;
  font-weight: 500;
  transition: background 0.2s;
}

.btn-primary:hover:not(:disabled) {
  background: #2d3a6e;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: default;
}

.error-banner {
  background: #fff5f5;
  border: 1px solid #fc8181;
  color: #c53030;
  padding: 0.75rem 1rem;
  border-radius: 8px;
  margin-bottom: 1rem;
}

.create-form-card {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}

.form-title {
  font-size: 1rem;
  font-weight: 600;
  color: #2d3748;
  margin: 0 0 1rem 0;
}

.form-fields {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.form-label {
  font-size: 0.85rem;
  font-weight: 500;
  color: #4a5568;
}

.form-input,
.form-textarea {
  padding: 0.5rem 0.75rem;
  border: 1px solid #cbd5e0;
  border-radius: 6px;
  font-size: 0.9rem;
  color: #2d3748;
  outline: none;
  transition: border-color 0.2s;
  font-family: inherit;
}

.form-input:focus,
.form-textarea:focus {
  border-color: #4fc3f7;
  box-shadow: 0 0 0 3px rgba(79, 195, 247, 0.15);
}

.form-textarea {
  resize: vertical;
  min-height: 60px;
}

.form-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.btn-save {
  padding: 0.5rem 1rem;
  background: #1a1a2e;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.875rem;
  font-weight: 500;
  transition: background 0.2s;
}

.btn-save:hover:not(:disabled) {
  background: #2d3a6e;
}

.btn-save:disabled {
  opacity: 0.5;
  cursor: default;
}

.btn-cancel {
  padding: 0.5rem 1rem;
  background: #fff;
  border: 1px solid #cbd5e0;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.875rem;
  color: #4a5568;
  transition: background 0.2s;
}

.btn-cancel:hover {
  background: #f7fafc;
}

.loading-state,
.empty-state {
  text-align: center;
  padding: 4rem 2rem;
  color: #718096;
}

.empty-hint {
  font-size: 0.875rem;
  margin-top: 0.5rem;
  color: #a0aec0;
}

.ranges-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.range-card {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  padding: 1.25rem;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.range-info {
  flex: 1;
}

.range-name {
  font-size: 1.1rem;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 0.25rem 0;
}

.range-description {
  font-size: 0.875rem;
  color: #718096;
  margin: 0 0 0.5rem 0;
}

.range-device-count {
  font-size: 0.8rem;
  color: #a0aec0;
  margin: 0;
}

.range-actions {
  display: flex;
  gap: 0.5rem;
}

.btn-edit {
  padding: 0.35rem 0.75rem;
  background: #fff;
  border: 1px solid #cbd5e0;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.8rem;
  color: #4a5568;
  transition: background 0.2s;
}

.btn-edit:hover {
  background: #edf2f7;
}

.btn-delete {
  padding: 0.35rem 0.75rem;
  background: #fff;
  border: 1px solid #fc8181;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.8rem;
  color: #c53030;
  transition: background 0.2s;
}

.btn-delete:hover:not(:disabled) {
  background: #fff5f5;
}

.btn-delete:disabled {
  border-color: #e2e8f0;
  color: #a0aec0;
  cursor: not-allowed;
}

.edit-form {
  width: 100%;
}

.inline-error {
  color: #c53030;
  font-size: 0.8rem;
  margin-top: 0.25rem;
}
</style>