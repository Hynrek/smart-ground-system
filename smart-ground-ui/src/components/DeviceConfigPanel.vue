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
              :disabled="creating || !createForm.name.trim() || !createForm.groupId || createForm.signalDurationMs == null || (!isDebugGroup(createForm.groupId) && createForm.pin == null)"
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
import { ref, computed, onMounted } from 'vue';
import { useDeviceTypeStore } from '@/stores/deviceTypeStore.js';
import { useAuthStore } from '@/stores/authStore.js';
import { ADMIN_PERMISSION, DEBUG_GROUP_NAMES, MANAGE_RANGES_PERMISSION } from '@/constants/deviceTypes.js';

const deviceTypeStore = useDeviceTypeStore();
const authStore = useAuthStore();

const isAdmin = computed(() => authStore.hasPermission(ADMIN_PERMISSION));
const canEdit = computed(() => authStore.hasPermission(MANAGE_RANGES_PERMISSION) || isAdmin.value);

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
  deviceTypeStore.deviceTypesByFirmware[firmwareConfigId] ?? [];

onMounted(async () => {
  // Ensure firmware configs and groups are loaded
  if (deviceTypeStore.firmwareConfigs.length === 0) {
    await deviceTypeStore.loadApiData?.();
  }
  // Load device types per firmware config
  for (const fc of deviceTypeStore.firmwareConfigs) {
    await deviceTypeStore.loadDeviceTypesForFirmware(fc.id);
  }
});

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
.state-text { font-size: 15px; color: var(--sg-text-faint); margin: 0 0 6px; }
.state-hint { font-size: 13px; color: var(--sg-text-faint); margin: 0; }
.error-banner { background: var(--sg-color-danger-bg); border: 1px solid var(--sg-color-danger); border-radius: 8px; padding: 12px 16px; color: var(--sg-color-danger-text); font-size: 13px; margin-bottom: 20px; }

.configs-stack { display: flex; flex-direction: column; gap: 20px; }

.firmware-block { background: var(--sg-bg-card); border-radius: 12px; border: 1px solid var(--sg-bg-panel); box-shadow: 0 1px 3px rgba(0,0,0,.06); overflow: hidden; }

.firmware-heading { display: flex; align-items: center; justify-content: space-between; padding: 14px 20px; border-bottom: 1px solid var(--sg-bg-panel); background: var(--sg-bg-panel); }

.firmware-label { font-size: 13px; font-weight: 700; color: var(--sg-text-primary); font-family: 'Courier New', monospace; }

.new-config-btn { display: flex; align-items: center; gap: 5px; padding: 5px 10px; background: var(--sg-accent); color: var(--sg-surface-0); border: none; border-radius: 6px; font-size: 12px; font-weight: 500; font-family: inherit; cursor: pointer; }
.new-config-btn:hover { background: var(--sg-accent-hover); }

.table-wrap { overflow-x: auto; }

.types-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.types-table th { padding: 8px 16px; text-align: left; font-size: 11.5px; font-weight: 600; color: var(--sg-text-muted); text-transform: uppercase; letter-spacing: 0.4px; border-bottom: 1px solid var(--sg-bg-panel); background: var(--sg-bg-panel); }
.types-table td { padding: 10px 16px; color: var(--sg-text-primary); border-bottom: 1px solid var(--sg-border); }
.types-table tbody tr:last-child td { border-bottom: none; }
.types-table tbody tr:hover { background: var(--sg-bg-panel); }
.types-table tbody tr.row--editing { background: var(--sg-color-info-bg); }

.td-name { font-weight: 600; color: var(--sg-text-primary); }
.td-mono { font-family: 'Courier New', monospace; font-size: 12px; color: var(--sg-text-muted); }
.empty-row { text-align: center; color: var(--sg-text-faint); font-size: 13px; padding: 16px; }

.debug-badge { display: inline-flex; align-items: center; gap: 4px; background: rgba(239,159,39,.15); color: var(--sg-color-warning-text); border-radius: 4px; padding: 2px 6px; font-size: 11.5px; font-weight: 600; }
.group-label { color: var(--sg-text-muted); }

.text-input, .pin-input, .dur-input { padding: 4px 8px; border: 1.5px solid var(--sg-accent); border-radius: 6px; font-size: 13px; font-family: inherit; outline: none; }
.text-input { width: 140px; }
.pin-input { width: 72px; }
.dur-input { width: 80px; }

.td-actions { text-align: right; white-space: nowrap; }
.td-actions button + button { margin-left: 4px; }

.edit-btn { background: none; border: 1px solid var(--sg-border); border-radius: 6px; padding: 5px 8px; cursor: pointer; color: var(--sg-text-muted); }
.edit-btn:hover { background: var(--sg-color-info-bg); border-color: var(--sg-accent); color: var(--sg-accent-hover); }

.save-btn { display: inline-flex; align-items: center; gap: 4px; padding: 5px 10px; background: var(--sg-accent); color: var(--sg-surface-0); border: none; border-radius: 6px; cursor: pointer; font-size: 12px; font-family: inherit; }
.save-btn:hover:not(:disabled) { background: var(--sg-accent-hover); }
.save-btn:disabled { opacity: .5; cursor: not-allowed; }

.cancel-btn { display: inline-flex; align-items: center; padding: 5px 9px; background: transparent; border: 1px solid var(--sg-border); border-radius: 6px; cursor: pointer; color: var(--sg-text-muted); font-size: 12px; font-family: inherit; }
.cancel-btn:hover { background: var(--sg-bg-panel); }

.create-form { padding: 16px 20px; border-top: 1px solid var(--sg-border); background: var(--sg-bg-panel); }
.create-error { color: var(--sg-color-danger-text); font-size: 13px; margin: 0 0 10px; }
.form-row { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
.form-group { display: flex; flex-direction: column; gap: 4px; }
.form-group label { font-size: 11.5px; font-weight: 600; color: var(--sg-text-muted); text-transform: uppercase; letter-spacing: 0.4px; }
.form-group input, .form-group select { padding: 6px 10px; border: 1.5px solid var(--sg-border); border-radius: 6px; font-size: 13px; font-family: inherit; outline: none; min-width: 120px; }
.form-group input:focus, .form-group select:focus { border-color: var(--sg-accent); }
.form-actions { display: flex; gap: 8px; }
</style>
