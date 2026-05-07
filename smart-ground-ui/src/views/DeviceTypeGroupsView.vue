<template>
  <div class="dt-view">
    <div class="dt-content">
      <!-- Header -->
      <div class="view-header">
        <div>
          <h1>Gerätetypen &amp; Gruppen</h1>
          <p class="subtitle">
            {{ deviceTypeStore.deviceTypes.length }} Typ{{ deviceTypeStore.deviceTypes.length !== 1 ? 'en' : '' }}
            · {{ deviceTypeStore.deviceTypeGroups.length }} Gruppe{{ deviceTypeStore.deviceTypeGroups.length !== 1 ? 'n' : '' }}
          </p>
        </div>
      </div>

      <!-- Loading -->
      <div v-if="deviceTypeStore.isLoading" class="state-center">
        <p class="state-text">Lade Gerätetypen…</p>
      </div>

      <!-- Error -->
      <div v-else-if="deviceTypeStore.error" class="error-banner">
        {{ deviceTypeStore.error }}
      </div>

      <!-- Groups -->
      <div v-else class="groups-stack">
        <div
          v-for="group in groups"
          :key="group.id"
          class="group-block"
        >
          <div class="group-heading">
            <span class="group-name">{{ group.name }}</span>
            <span class="group-badge">{{ group.types.length }}</span>
          </div>

          <div v-if="group.types.length === 0" class="empty-group">
            Keine Gerätetypen in dieser Gruppe
          </div>

          <div v-else class="table-wrap"><table class="types-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Kommando</th>
                <th>Dauer (ms)</th>
                <th>Verzögerung (ms)</th>
                <th />
              </tr>
            </thead>
            <tbody>
              <template v-for="dt in group.types" :key="dt.id">
                <tr :class="{ 'row--editing': editingId === dt.id }">
                  <td class="td-name">{{ dt.name }}</td>
                  <td class="td-mono">{{ dt.command ?? '–' }}</td>
                  <td>
                    <template v-if="editingId === dt.id">
                      <input
                        v-model.number="editForm.signalDurationMs"
                        type="number"
                        min="0"
                        class="dur-input"
                      />
                    </template>
                    <span v-else class="dur-val">{{ dt.signalDurationMs ?? '–' }}</span>
                  </td>
                  <td>
                    <template v-if="editingId === dt.id">
                      <input
                        v-model.number="editForm.delaySignalDurationMs"
                        type="number"
                        min="0"
                        class="dur-input"
                      />
                    </template>
                    <span v-else class="dur-val">{{ dt.delaySignalDurationMs ?? '–' }}</span>
                  </td>
                  <td class="td-actions">
                    <template v-if="editingId === dt.id">
                      <button class="save-btn" :disabled="saving" @click="saveEdit(dt)">
                        <Icons icon="check" :size="12" />
                      </button>
                      <button class="cancel-btn" @click="editingId = null">
                        <Icons icon="x" :size="11" />
                      </button>
                    </template>
                    <button v-else class="edit-btn" @click="startEdit(dt)">
                      <Icons icon="edit" :size="13" />
                    </button>
                  </td>
                </tr>
              </template>
            </tbody>
          </table></div>
        </div>

        <!-- Nicht Zugeteilt -->
        <div v-if="unassignedTypes.length > 0" class="group-block group-block--unassigned">
          <div class="group-heading">
            <span class="group-name group-name--muted">Nicht Zugeteilt</span>
            <span class="group-badge group-badge--muted">{{ unassignedTypes.length }}</span>
          </div>
          <div class="table-wrap"><table class="types-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Kommando</th>
                <th>Dauer (ms)</th>
                <th>Verzögerung (ms)</th>
                <th />
              </tr>
            </thead>
            <tbody>
              <template v-for="dt in unassignedTypes" :key="dt.id">
                <tr :class="{ 'row--editing': editingId === dt.id }">
                  <td class="td-name td-name--muted">{{ dt.name }}</td>
                  <td class="td-mono">{{ dt.command ?? '–' }}</td>
                  <td>
                    <template v-if="editingId === dt.id">
                      <input
                        v-model.number="editForm.signalDurationMs"
                        type="number"
                        min="0"
                        class="dur-input"
                      />
                    </template>
                    <span v-else class="dur-val">{{ dt.signalDurationMs ?? '–' }}</span>
                  </td>
                  <td>
                    <template v-if="editingId === dt.id">
                      <input
                        v-model.number="editForm.delaySignalDurationMs"
                        type="number"
                        min="0"
                        class="dur-input"
                      />
                    </template>
                    <span v-else class="dur-val">{{ dt.delaySignalDurationMs ?? '–' }}</span>
                  </td>
                  <td class="td-actions">
                    <template v-if="editingId === dt.id">
                      <button class="save-btn" :disabled="saving" @click="saveEdit(dt)">
                        <Icons icon="check" :size="12" />
                      </button>
                      <button class="cancel-btn" @click="editingId = null">
                        <Icons icon="x" :size="11" />
                      </button>
                    </template>
                    <button v-else class="edit-btn" @click="startEdit(dt)">
                      <Icons icon="edit" :size="13" />
                    </button>
                  </td>
                </tr>
              </template>
            </tbody>
          </table></div>
        </div>

        <!-- Empty overall -->
        <div v-if="groups.length === 0 && unassignedTypes.length === 0" class="state-center">
          <p class="state-text">Keine Gerätetypen vorhanden.</p>
          <p class="state-hint">Gruppen entstehen beim Registrieren einer Firmware-Konfiguration.</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useDeviceTypeStore } from '@/stores/deviceTypeStore.js';
import Icons from '@/components/Icons.vue';

const deviceTypeStore = useDeviceTypeStore();

onMounted(() => {
  if (deviceTypeStore.deviceTypes.length === 0) {
    deviceTypeStore.loadApiData();
  }
});

// ── Groups with their device types ────────────────
const groups = computed(() =>
  deviceTypeStore.deviceTypeGroups.map((g) => ({
    ...g,
    types: deviceTypeStore.deviceTypes.filter((dt) => dt.groupId === g.id),
  })),
);

const knownGroupIds = computed(() => new Set(deviceTypeStore.deviceTypeGroups.map((g) => g.id)));

const unassignedTypes = computed(() =>
  deviceTypeStore.deviceTypes.filter((dt) => !dt.groupId || !knownGroupIds.value.has(dt.groupId)),
);

// ── Inline edit ───────────────────────────────────
const editingId = ref(null);
const editForm = ref({ signalDurationMs: null, delaySignalDurationMs: null });
const saving = ref(false);

const startEdit = (dt) => {
  editingId.value = dt.id;
  editForm.value = {
    signalDurationMs: dt.signalDurationMs ?? null,
    delaySignalDurationMs: dt.delaySignalDurationMs ?? null,
  };
};

const saveEdit = async (dt) => {
  saving.value = true;
  try {
    await deviceTypeStore.updateDeviceType(dt.id, {
      signalDurationMs: editForm.value.signalDurationMs,
      delaySignalDurationMs: editForm.value.delaySignalDurationMs,
    });
    editingId.value = null;
  } catch {
    // error already set in store
  } finally {
    saving.value = false;
  }
};
</script>

<style scoped>
.dt-view {
  padding: 28px;
  flex: 1;
  overflow-y: auto;
}

.dt-content {
  max-width: 860px;
}

/* ── Header ──────────────────────────────────────── */
.view-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
}

h1 {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.subtitle {
  font-size: 13px;
  color: #718096;
  margin-top: 4px;
}

/* ── States ──────────────────────────────────────── */
.state-center {
  padding: 48px 24px;
  text-align: center;
}

.state-text {
  font-size: 15px;
  color: #a0aec0;
  margin: 0 0 6px;
}

.state-hint {
  font-size: 13px;
  color: #c0c8d8;
  margin: 0;
}

.error-banner {
  background: #fff5f5;
  border: 1px solid #fca5a5;
  border-radius: 8px;
  padding: 12px 16px;
  color: #c53030;
  font-size: 13px;
  margin-bottom: 20px;
}

/* ── Groups stack ────────────────────────────────── */
.groups-stack {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.group-block {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #f0f4f8;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.group-block--unassigned {
  border-color: #e2e8f0;
  background: #fafbfc;
}

.group-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 20px;
  border-bottom: 1px solid #f0f4f8;
  background: #f9fafb;
}

.group-block--unassigned .group-heading {
  background: #f4f6fb;
  border-bottom-color: #e2e8f0;
}

.group-name {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
}

.group-name--muted {
  color: #718096;
}

.group-badge {
  font-size: 11px;
  font-weight: 600;
  background: #e8edf5;
  color: #4a5568;
  border-radius: 20px;
  padding: 2px 8px;
}

.group-badge--muted {
  background: #f0f4f8;
  color: #a0aec0;
}

.empty-group {
  padding: 16px 20px;
  font-size: 13px;
  color: #a0aec0;
}

/* ── Table ───────────────────────────────────────── */
.table-wrap {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.types-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.types-table thead tr {
  background: #f9fafb;
}

.types-table th {
  padding: 8px 16px;
  text-align: left;
  font-size: 11.5px;
  font-weight: 600;
  color: #718096;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  border-bottom: 1px solid #f0f4f8;
}

.types-table th:last-child {
  text-align: right;
}

.types-table tbody tr {
  border-bottom: 1px solid #f9fafb;
  transition: background 0.1s;
}

.types-table tbody tr:last-child {
  border-bottom: none;
}

.types-table tbody tr:hover {
  background: #f9fafb;
}

.types-table tbody tr.row--editing {
  background: #f0f9ff;
}

.types-table td {
  padding: 10px 16px;
  color: #2d3748;
}

.td-name {
  font-weight: 600;
  color: #1a1a2e;
}

.td-name--muted {
  color: #718096;
  font-weight: 500;
}

.td-mono {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #555;
}

.dur-val {
  color: #4a5568;
}

.dur-input {
  width: 80px;
  padding: 5px 8px;
  border: 1.5px solid #4fc3f7;
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
  outline: none;
}

.td-actions {
  text-align: right;
}

.td-actions button + button {
  margin-left: 4px;
}

.edit-btn {
  background: none;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 5px 8px;
  cursor: pointer;
  color: #718096;
  display: inline-flex;
  align-items: center;
  transition: all 0.15s;
}

.edit-btn:hover {
  background: #e8f5ff;
  border-color: #4fc3f7;
  color: #1a5fa0;
}

.save-btn {
  display: inline-flex;
  align-items: center;
  padding: 5px 9px;
  background: #1a1a2e;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}

.save-btn:hover:not(:disabled) {
  background: #0f0f1a;
}

.save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.cancel-btn {
  display: inline-flex;
  align-items: center;
  padding: 5px 8px;
  background: transparent;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: pointer;
  color: #718096;
  transition: all 0.15s;
}

.cancel-btn:hover {
  background: #f9fafb;
}
</style>
