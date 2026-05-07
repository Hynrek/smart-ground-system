<template>
  <div class="ranges-view">
    <div class="view-header">
      <div>
        <h1 class="view-title">Schiessplätze</h1>
        <p class="view-subtitle">{{ ranges.length }} Plätze · {{ assignedDeviceCount }} Geräte zugeordnet</p>
      </div>
      <Button variant="primary" @click="openCreateForm">
        <Icons icon="plus" :size="14" />
        Neuer Platz
      </Button>
    </div>

    <div class="stats-strip">
      <div class="stat-card">
        <div class="stat-icon stat-icon--blue">
          <Icons icon="ranges" :size="16" color="#4fc3f7" />
        </div>
        <div>
          <div class="stat-value">{{ ranges.length }}</div>
          <div class="stat-label">Plätze</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--green">
          <Icons icon="bolt" :size="16" color="#48bb78" />
        </div>
        <div>
          <div class="stat-value">{{ assignedDeviceCount }}</div>
          <div class="stat-label">Geräte zugeordnet</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--amber">
          <Icons icon="lock" :size="16" color="#ed8936" />
        </div>
        <div>
          <div class="stat-value">{{ lockedCount }}</div>
          <div class="stat-label">Gesperrt</div>
        </div>
      </div>
    </div>

    <div v-if="showCreateForm" class="form-card">
      <div class="form-card-header">
        <span class="form-card-title">Neuer Platz</span>
        <Button variant="ghost" size="icon-only" @click="closeCreateForm">
          <Icons icon="x" :size="13" />
        </Button>
      </div>
      <div class="form-fields">
        <div class="form-field">
          <label class="form-label">Name</label>
          <input
            v-model="newName"
            type="text"
            class="form-input"
            placeholder="z.B. Platz A"
            maxlength="100"
            @keydown.enter="submitCreate"
          />
        </div>
        <div class="form-field">
          <label class="form-label">Beschreibung <span class="optional">(optional)</span></label>
          <input
            v-model="newDescription"
            type="text"
            class="form-input"
            placeholder="Kurze Beschreibung"
            maxlength="200"
          />
        </div>
        <div v-if="createError" class="inline-error">{{ createError }}</div>
        <div class="form-actions">
          <Button variant="primary" :disabled="creating || !newName.trim()" @click="submitCreate">
            {{ creating ? 'Erstellen…' : 'Erstellen' }}
          </Button>
          <Button variant="ghost" @click="closeCreateForm">Abbrechen</Button>
        </div>
      </div>
    </div>

    <div v-if="rangeStore.error" class="error-banner">
      <Icons icon="alert" :size="15" color="#c53030" />
      {{ rangeStore.error }}
    </div>

    <div v-if="loading && ranges.length === 0" class="loading-state">
      Lade Plätze…
    </div>

    <div v-else-if="ranges.length === 0 && !loading" class="empty-state">
      <Icons icon="ranges" :size="40" color="#a0aec0" />
      <div class="empty-title">Keine Plätze vorhanden</div>
      <div class="empty-hint">Erstelle einen Platz, um Geräte zu gruppieren.</div>
    </div>

    <div v-else class="ranges-grid">
      <div
        v-for="range in ranges"
        :key="range.id"
        class="range-card"
        :class="{ locked: range.locked, editing: editingId === range.id }"
      >
        <template v-if="editingId === range.id">
          <div class="card-edit">
            <div class="form-field">
              <label class="form-label">Name</label>
              <input
                v-model="editName"
                type="text"
                class="form-input"
                maxlength="100"
                @keydown.enter="submitEdit"
              />
            </div>
            <div class="form-field">
              <label class="form-label">Beschreibung <span class="optional">(optional)</span></label>
              <input v-model="editDescription" type="text" class="form-input" maxlength="200" />
            </div>
            <div v-if="saveError" class="inline-error">{{ saveError }}</div>
            <div class="form-actions">
              <Button variant="primary" :disabled="saving || !editName.trim()" @click="submitEdit">
                {{ saving ? 'Speichern…' : 'Speichern' }}
              </Button>
              <Button variant="ghost" @click="cancelEdit">Abbrechen</Button>
            </div>
          </div>
        </template>

        <template v-else>
          <div class="card-accent-bar" />
          <div class="card-content">
            <div class="card-top" @click="openRange(range)">
              <div class="card-header-row">
                <span class="range-name">{{ range.name }}</span>
                <span v-if="range.locked" class="lock-chip">
                  <Icons icon="lock" :size="10" />
                  Gesperrt
                </span>
              </div>
              <p v-if="range.description" class="range-description">{{ range.description }}</p>
              <div class="range-meta">
                <span class="device-chip">
                  <Icons icon="bolt" :size="11" />
                  {{ getDeviceCount(range.id) }}
                  {{ getDeviceCount(range.id) === 1 ? 'Gerät' : 'Geräte' }}
                </span>
              </div>
            </div>
            <div class="card-actions">
              <button class="details-btn" @click="openRange(range)">
                Details
                <Icons icon="arrowR" :size="12" />
              </button>
              <div class="action-icons">
                <button class="icon-btn" title="Remote" @click="openRemote(range)">
                  <Icons icon="program" :size="14" />
                </button>
                <button
                  class="icon-btn"
                  :class="{ 'icon-btn--amber': range.locked }"
                  :title="range.locked ? 'Entsperren' : 'Sperren'"
                  @click="toggleLocked(range)"
                >
                  <Icons :icon="range.locked ? 'lock' : 'unlock'" :size="14" />
                </button>
                <button class="icon-btn" title="Bearbeiten" @click="startEdit(range)">
                  <Icons icon="edit" :size="14" />
                </button>
                <button
                  class="icon-btn icon-btn--danger"
                  title="Löschen"
                  :disabled="getDeviceCount(range.id) > 0 || undefined"
                  @click="confirmDelete(range)"
                >
                  <Icons icon="trash" :size="14" />
                </button>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useRangeStore } from '../stores/rangeStore.js';
import { useDeviceStore } from '../stores/deviceStore.js';
import Button from '../components/Button.vue';
import Icons from '../components/Icons.vue';

const router = useRouter();
const rangeStore = useRangeStore();
const deviceStore = useDeviceStore();

const ranges = computed(() => rangeStore.ranges);
const loading = computed(() => rangeStore.isLoading);
const assignedDeviceCount = computed(() => deviceStore.devices.filter((d) => d.rangeId !== null).length);
const lockedCount = computed(() => ranges.value.filter((r) => r.locked).length);

const showCreateForm = ref(false);
const newName = ref('');
const newDescription = ref('');
const creating = ref(false);
const createError = ref(null);

const editingId = ref(null);
const editName = ref('');
const editDescription = ref('');
const saving = ref(false);
const saveError = ref(null);

const getDeviceCount = (rangeId) => deviceStore.devices.filter((d) => d.rangeId === rangeId).length;

function openCreateForm() {
  showCreateForm.value = true;
  newName.value = '';
  newDescription.value = '';
  createError.value = null;
}

function closeCreateForm() {
  showCreateForm.value = false;
  newName.value = '';
  newDescription.value = '';
  createError.value = null;
}

async function submitCreate() {
  if (!newName.value.trim()) return;
  creating.value = true;
  createError.value = null;
  try {
    await rangeStore.createRange(newName.value.trim(), newDescription.value.trim() || null);
    closeCreateForm();
  } catch (e) {
    createError.value = e.message;
  } finally {
    creating.value = false;
  }
}

function openRange(range) {
  router.push(`/ranges/${range.id}`);
}

function openRemote(range) {
  router.push(`/remote/${range.id}`);
}

function startEdit(range) {
  editingId.value = range.id;
  editName.value = range.name;
  editDescription.value = range.description ?? '';
  saveError.value = null;
}

function cancelEdit() {
  editingId.value = null;
  editName.value = '';
  editDescription.value = '';
  saveError.value = null;
}

async function submitEdit() {
  if (!editName.value.trim()) return;
  saving.value = true;
  saveError.value = null;
  try {
    await rangeStore.updateRange(editingId.value, editName.value.trim(), editDescription.value.trim() || null);
    cancelEdit();
  } catch (e) {
    saveError.value = e.message;
  } finally {
    saving.value = false;
  }
}

async function toggleLocked(range) {
  try {
    await rangeStore.setLocked(range.id, !range.locked);
  } catch {
    // error is surfaced via rangeStore.error
  }
}

async function confirmDelete(range) {
  if (getDeviceCount(range.id) > 0) return;
  if (!window.confirm(`Möchtest du den Platz „${range.name}" wirklich löschen?`)) return;
  try {
    await rangeStore.deleteRange(range.id);
  } catch {
    // error is surfaced via rangeStore.error
  }
}
</script>

<style scoped>
.ranges-view {
  padding: 28px;
  flex: 1;
  overflow-y: auto;
}

/* ── Header ─────────────────────────────────── */
.view-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.view-title {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.view-subtitle {
  font-size: 13px;
  color: #718096;
  margin-top: 4px;
}

/* ── Stats strip ─────────────────────────────── */
.stats-strip {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.stat-card {
  flex: 1;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.stat-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon--blue { background: #ebf8ff; }
.stat-icon--green { background: #f0fff4; }
.stat-icon--amber { background: #fffbeb; }

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  line-height: 1;
}

.stat-label {
  font-size: 12px;
  color: #718096;
  margin-top: 3px;
}

/* ── Create form ─────────────────────────────── */
.form-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.form-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.form-card-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
}

.form-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: #4a5568;
}

.optional {
  font-weight: 400;
  color: #a0aec0;
}

.form-input {
  padding: 8px 10px;
  border: 1px solid #cbd5e0;
  border-radius: 6px;
  font-size: 14px;
  color: #2d3748;
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.form-input:focus {
  border-color: #4fc3f7;
  box-shadow: 0 0 0 3px rgba(79, 195, 247, 0.12);
}

.form-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.inline-error {
  font-size: 12px;
  color: #c53030;
}

/* ── Error banner ────────────────────────────── */
.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff5f5;
  border: 1px solid #fc8181;
  color: #c53030;
  padding: 10px 14px;
  border-radius: 8px;
  margin-bottom: 16px;
  font-size: 13.5px;
}

/* ── States ──────────────────────────────────── */
.loading-state {
  text-align: center;
  padding: 3rem;
  color: #718096;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 4rem 2rem;
  gap: 8px;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: #4a5568;
}

.empty-hint {
  font-size: 13px;
  color: #a0aec0;
}

/* ── Ranges grid ─────────────────────────────── */
.ranges-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}

/* ── Range card ──────────────────────────────── */
.range-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: row;
  transition: box-shadow 0.15s, border-color 0.15s;
}

.range-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  border-color: #c5d0e8;
}

.range-card.editing {
  flex-direction: column;
}

.card-accent-bar {
  width: 4px;
  background: #4fc3f7;
  flex-shrink: 0;
}

.range-card.locked .card-accent-bar {
  background: #ed8936;
}

.card-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px;
  min-width: 0;
}

.card-top {
  flex: 1;
  cursor: pointer;
  padding-bottom: 12px;
}

.card-header-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}

.range-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
}

.lock-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  background: #fef3c7;
  color: #92400e;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 7px;
  border-radius: 20px;
}

.range-description {
  font-size: 13px;
  color: #718096;
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.range-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.device-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #4a5568;
  background: #f4f6fb;
  padding: 3px 8px;
  border-radius: 20px;
}

/* ── Card actions row ────────────────────────── */
.card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid #f0f4f8;
}

.details-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  font-weight: 500;
  color: #4fc3f7;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
  font-family: inherit;
  transition: color 0.15s;
}

.details-btn:hover {
  color: #0284c7;
}

.action-icons {
  display: flex;
  gap: 2px;
}

.icon-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  color: #718096;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}

.icon-btn:hover:not(:disabled) {
  background: #f4f6fb;
  color: #1a1a2e;
  border-color: #e2e8f0;
}

.icon-btn--amber {
  color: #ed8936;
}

.icon-btn--danger:hover:not(:disabled) {
  background: #fff5f5;
  color: #c53030;
  border-color: #fc8181;
}

.icon-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

/* ── Card edit mode ──────────────────────────── */
.card-edit {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
