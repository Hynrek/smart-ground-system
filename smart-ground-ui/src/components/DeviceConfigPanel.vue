<template>
  <div>
    <div v-if="deviceTypeStore.isLoading" class="state-center">
      <p class="state-text">Lade Gerätekonfigurationen…</p>
    </div>

    <div v-else-if="deviceTypeStore.error" class="error-banner">
      {{ deviceTypeStore.error }}
    </div>

    <div v-else-if="deviceTypeStore.firmwareConfigs.length === 0" class="state-center">
      <p class="state-text">Keine Firmware-Konfigurationen vorhanden.</p>
      <p class="state-hint">Registriere eine SmartBox, um zu beginnen.</p>
    </div>

    <div v-else class="configs-stack">
      <div
        v-for="fc in deviceTypeStore.firmwareConfigs"
        :key="fc.id"
        class="firmware-block"
      >
        <div class="firmware-heading">
          <span class="firmware-label">v{{ fc.version }} — {{ fc.boxType }}</span>
          <button
            v-if="isAdmin"
            class="new-config-btn"
            @click="toggleCreate(fc.id)"
          >
            + Neue Konfiguration
          </button>
        </div>

        <div class="table-wrap">
          <table class="types-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Gruppe</th>
                <th>GPIO-Pin</th>
                <th>Dauer (ms)</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <template v-for="dt in typesForFirmware(fc.id)" :key="dt.id">
                <tr :class="{ 'row--editing': editingId === dt.id }">
                  <td class="td-name">
                    <template v-if="editingId === dt.id">
                      <input v-model="editForm.name" class="text-input" />
                    </template>
                    <template v-else>{{ dt.name }}</template>
                  </td>
                  <td>
                    <span v-if="isDebugGroup(dt.groupId)" class="debug-badge">
                      ⚙ Debug
                    </span>
                    <span v-else class="group-label">{{ groupName(dt.groupId) }}</span>
                  </td>
                  <td class="td-mono">
                    <template v-if="editingId === dt.id && isAdmin && !isDebugGroup(dt.groupId)">
                      <input
                        v-model="editForm.command"
                        type="number"
                        min="0"
                        max="40"
                        class="pin-input"
                      />
                    </template>
                    <span v-else>{{ isDebugGroup(dt.groupId) ? '— (onboard)' : (dt.command ?? dt.pin ?? '–') }}</span>
                  </td>
                  <td>
                    <template v-if="editingId === dt.id">
                      <input
                        v-model.number="editForm.signalDurationMs"
                        type="number"
                        min="0"
                        class="dur-input"
                      />
                    </template>
                    <span v-else>{{ dt.signalDurationMs ?? '–' }}</span>
                  </td>
                  <td class="td-actions">
                    <template v-if="editingId === dt.id">
                      <button class="save-btn" :disabled="saving" @click="saveEdit(dt)">✓</button>
                      <button class="cancel-btn" @click="editingId = null">✕</button>
                    </template>
                    <button v-else-if="canEdit" class="edit-btn" @click="startEdit(dt)">✏</button>
                  </td>
                </tr>
              </template>
              <tr v-if="typesForFirmware(fc.id).length === 0">
                <td colspan="5" class="empty-row">Keine Konfigurationen</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Inline create form (ADMIN only) -->
        <div v-if="isAdmin && creatingForFirmware === fc.id" class="create-form">
          <p v-if="createError" class="create-error">{{ createError }}</p>
          <div class="form-row">
            <div class="form-group">
              <label>Name *</label>
              <input v-model="createForm.name" placeholder="z.B. Werfer 3" />
            </div>
            <div class="form-group">
              <label>Gruppe *</label>
              <select v-model="createForm.groupId" @change="onGroupChange">
                <option value="" disabled>Gruppe wählen</option>
                <option
                  v-for="g in deviceTypeStore.deviceTypeGroups"
                  :key="g.id"
                  :value="g.id"
                >
                  {{ g.name }}{{ isDebugGroupName(g.name) ? ' (Debug)' : '' }}
                </option>
              </select>
            </div>
            <div class="form-group">
              <label>GPIO-Pin *</label>
              <input
                v-model.number="createForm.pin"
                type="number"
                min="0"
                max="40"
                :disabled="isDebugGroup(createForm.groupId)"
                :placeholder="isDebugGroup(createForm.groupId) ? '— (onboard)' : 'z.B. 8'"
              />
            </div>
            <div class="form-group">
              <label>Dauer (ms) *</label>
              <input
                v-model.number="createForm.signalDurationMs"
                type="number"
                min="0"
                placeholder="z.B. 500"
              />
            </div>
          </div>
          <div class="form-actions">
            <button
              class="save-btn"
              :disabled="creating || !createForm.name.trim() || !createForm.groupId || createForm.signalDurationMs === null || createForm.signalDurationMs === undefined"
              @click="submitCreate(fc.id)"
            >
              Erstellen
            </button>
            <button class="cancel-btn" @click="creatingForFirmware = null">Abbrechen</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useDeviceTypeStore } from '@/stores/deviceTypeStore.js';
import { useAuthStore } from '@/stores/authStore.js';
import { ADMIN_PERMISSION, DEBUG_GROUP_NAMES } from '@/constants/deviceTypes.js';

const deviceTypeStore = useDeviceTypeStore();
const authStore = useAuthStore();

const isAdmin = computed(() => authStore.hasPermission(ADMIN_PERMISSION));
const canEdit = computed(() => authStore.hasPermission('MANAGE_RANGES') || isAdmin.value);

const isDebugGroupName = (name) =>
  DEBUG_GROUP_NAMES.includes((name ?? '').toUpperCase());

const isDebugGroup = (groupId) => {
  if (!groupId) return false;
  const group = deviceTypeStore.deviceTypeGroups.find((g) => g.id === groupId);
  return isDebugGroupName(group?.name ?? '');
};

const groupName = (groupId) =>
  deviceTypeStore.deviceTypeGroups.find((g) => g.id === groupId)?.name ?? '–';

const typesForFirmware = (firmwareConfigId) =>
  deviceTypeStore.deviceTypes.filter((dt) => dt.firmwareConfigId === firmwareConfigId);

// ── Inline edit ──────────────────────────────────────────────────────────────
const editingId = ref(null);
const editForm = ref({ name: '', command: '', signalDurationMs: null });
const saving = ref(false);

const startEdit = (dt) => {
  editingId.value = dt.id;
  editForm.value = {
    name: dt.name ?? '',
    command: dt.command ?? dt.pin ?? '',
    signalDurationMs: dt.signalDurationMs ?? null,
  };
};

const saveEdit = async (dt) => {
  saving.value = true;
  try {
    const payload = {
      name: editForm.value.name.trim(),
      signalDurationMs: editForm.value.signalDurationMs,
    };
    if (isAdmin.value && !isDebugGroup(dt.groupId)) {
      payload.command = String(editForm.value.command);
    }
    await deviceTypeStore.updateDeviceType(dt.id, payload);
    editingId.value = null;
  } catch {
    // error surfaced via deviceTypeStore.error
  } finally {
    saving.value = false;
  }
};

// ── Create form ───────────────────────────────────────────────────────────────
const creatingForFirmware = ref(null);
const createForm = ref({ name: '', groupId: '', pin: null, signalDurationMs: null });
const createError = ref(null);
const creating = ref(false);

const toggleCreate = (firmwareConfigId) => {
  creatingForFirmware.value = creatingForFirmware.value === firmwareConfigId
    ? null
    : firmwareConfigId;
  createForm.value = { name: '', groupId: '', pin: null, signalDurationMs: null };
  createError.value = null;
};

const onGroupChange = () => {
  if (isDebugGroup(createForm.value.groupId)) {
    createForm.value.pin = null;
  }
};

const submitCreate = async (firmwareConfigId) => {
  createError.value = null;
  creating.value = true;
  try {
    await deviceTypeStore.createDeviceConfig(firmwareConfigId, {
      name: createForm.value.name.trim(),
      groupId: createForm.value.groupId,
      pin: isDebugGroup(createForm.value.groupId) ? 0 : (createForm.value.pin ?? 0),
      signalDurationMs: createForm.value.signalDurationMs,
    });
    creatingForFirmware.value = null;
  } catch (e) {
    createError.value = `Erstellen fehlgeschlagen: ${e.message}`;
  } finally {
    creating.value = false;
  }
};
</script>

<style scoped>
.state-center { padding: 48px 24px; text-align: center; }
.state-text { font-size: 15px; color: #a0aec0; margin: 0 0 6px; }
.state-hint { font-size: 13px; color: #c0c8d8; margin: 0; }
.error-banner { background: #fff5f5; border: 1px solid #fca5a5; border-radius: 8px; padding: 12px 16px; color: #c53030; font-size: 13px; margin-bottom: 20px; }

.configs-stack { display: flex; flex-direction: column; gap: 20px; }

.firmware-block { background: #fff; border-radius: 12px; border: 1px solid #f0f4f8; box-shadow: 0 1px 3px rgba(0,0,0,.06); overflow: hidden; }

.firmware-heading { display: flex; align-items: center; justify-content: space-between; padding: 14px 20px; border-bottom: 1px solid #f0f4f8; background: #f9fafb; }

.firmware-label { font-size: 13px; font-weight: 700; color: #1a1a2e; font-family: 'Courier New', monospace; }

.new-config-btn { display: flex; align-items: center; gap: 5px; padding: 5px 10px; background: #1a1a2e; color: #fff; border: none; border-radius: 6px; font-size: 12px; font-weight: 500; font-family: inherit; cursor: pointer; }
.new-config-btn:hover { background: #0f0f1a; }

.table-wrap { overflow-x: auto; }

.types-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.types-table th { padding: 8px 16px; text-align: left; font-size: 11.5px; font-weight: 600; color: #718096; text-transform: uppercase; letter-spacing: 0.4px; border-bottom: 1px solid #f0f4f8; background: #f9fafb; }
.types-table td { padding: 10px 16px; color: #2d3748; border-bottom: 1px solid #f9fafb; }
.types-table tbody tr:last-child td { border-bottom: none; }
.types-table tbody tr:hover { background: #f9fafb; }
.types-table tbody tr.row--editing { background: #f0f9ff; }

.td-name { font-weight: 600; color: #1a1a2e; }
.td-mono { font-family: 'Courier New', monospace; font-size: 12px; color: #555; }
.empty-row { text-align: center; color: #a0aec0; font-size: 13px; padding: 16px; }

.debug-badge { display: inline-flex; align-items: center; gap: 4px; background: rgba(239,159,39,.15); color: #c67c10; border-radius: 4px; padding: 2px 6px; font-size: 11.5px; font-weight: 600; }
.group-label { color: #4a5568; }

.text-input, .pin-input, .dur-input { padding: 4px 8px; border: 1.5px solid #4fc3f7; border-radius: 6px; font-size: 13px; font-family: inherit; outline: none; }
.text-input { width: 140px; }
.pin-input { width: 72px; }
.dur-input { width: 80px; }

.td-actions { text-align: right; white-space: nowrap; }
.td-actions button + button { margin-left: 4px; }

.edit-btn { background: none; border: 1px solid #e2e8f0; border-radius: 6px; padding: 5px 8px; cursor: pointer; color: #718096; }
.edit-btn:hover { background: #e8f5ff; border-color: #4fc3f7; color: #1a5fa0; }

.save-btn { display: inline-flex; align-items: center; gap: 4px; padding: 5px 10px; background: #1a1a2e; color: #fff; border: none; border-radius: 6px; cursor: pointer; font-size: 12px; font-family: inherit; }
.save-btn:hover:not(:disabled) { background: #0f0f1a; }
.save-btn:disabled { opacity: .5; cursor: not-allowed; }

.cancel-btn { display: inline-flex; align-items: center; padding: 5px 9px; background: transparent; border: 1px solid #e2e8f0; border-radius: 6px; cursor: pointer; color: #718096; font-size: 12px; font-family: inherit; }
.cancel-btn:hover { background: #f9fafb; }

.create-form { padding: 16px 20px; border-top: 1px solid #e2e8f0; background: #f9fafb; }
.create-error { color: #c53030; font-size: 13px; margin: 0 0 10px; }
.form-row { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
.form-group { display: flex; flex-direction: column; gap: 4px; }
.form-group label { font-size: 11.5px; font-weight: 600; color: #718096; text-transform: uppercase; letter-spacing: 0.4px; }
.form-group input, .form-group select { padding: 6px 10px; border: 1.5px solid #e2e8f0; border-radius: 6px; font-size: 13px; font-family: inherit; outline: none; min-width: 120px; }
.form-group input:focus, .form-group select:focus { border-color: #4fc3f7; }
.form-actions { display: flex; gap: 8px; }
</style>
