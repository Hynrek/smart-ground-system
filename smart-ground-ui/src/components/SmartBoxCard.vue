<template>
  <div class="box-card" :class="{ 'action-mode': actionMode }">
    <!-- Box header -->
    <div class="box-header">
      <div class="box-icon">
        <Icons icon="wifi" :size="16" />
      </div>
      <div class="box-info">
        <div v-if="renamingBox" class="rename-form">
          <input
            v-model="aliasInput"
            autofocus
            @keydown.enter="saveAlias"
            @keydown.escape="cancelRename"
          />
          <button class="check-btn" @click="saveAlias">
            <Icons icon="check" :size="12" />
          </button>
          <button class="cancel-btn" @click="cancelRename">
            <Icons icon="x" :size="11" />
          </button>
        </div>
        <div v-else class="box-name-row">
          <div class="box-name">{{ box.alias }}</div>
          <button
            class="edit-btn"
            aria-label="Namen bearbeiten"
            @click="renamingBox = true"
          >
            <Icons icon="edit" :size="13" />
          </button>
        </div>
        <div class="box-mac">{{ box.macAddress }}</div>
      </div>
      <div class="box-status-info">
        <div class="status-badge">
          <StatusDot :status="box.status" />
          <span class="status-label">{{ getStatusLabel(box.status) }}</span>
        </div>
        <span class="firmware-badge">v{{ box.firmwareVersion }}</span>
        <Badge color="blue">{{ allDevicesCount }} Geräte</Badge>
      </div>
    </div>

    <!-- Device table -->
    <div v-if="devices.length > 0 || isAdding" class="device-table-wrapper">
      <table class="device-table">
        <thead>
          <tr>
            <th>Name</th>
            <th class="typ-col">Typ</th>
            <th>Schiessplatz</th>
            <th class="stat-col">Befehle</th>
            <th class="stat-col">Letzter Befehl</th>
            <th v-if="actionMode">Aktion</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <template v-for="device in devices" :key="device.id">
            <tr :class="{ editing: editingId === device.id }">
              <td class="name-cell">
                {{ device.alias ?? device.name }}
                <span class="type-sub">{{ device.groupName ?? device.type }}</span>
              </td>
              <td class="typ-col"><TypeChip :type="device.groupName ?? device.type" /></td>
              <td>
                <select
                  :value="device.rangeId || ''"
                  class="range-select"
                  @change="onRangeChange(device.id, $event.target.value)"
                >
                  <option value="">Kein Platz</option>
                  <option v-for="r in rangeStore.ranges" :key="r.id" :value="r.id">
                    {{ r.name }}
                  </option>
                </select>
              </td>
              <td class="stat-col stat-num">
                {{ device.commandsProcessed ?? '–' }}
              </td>
              <td class="stat-col">
                {{ formatCommandTime(device.lastCommandProcessedAt) }}
              </td>
              <td v-if="actionMode">
                <button
                  class="action-btn"
                  :class="{ fired: firedDevices[device.id] }"
                  @click="$emit('fire', device.id)"
                >
                  <Icons icon="bolt" :size="12" />
                  {{ firedDevices[device.id] ? 'Ausgelöst!' : 'Auslösen' }}
                </button>
              </td>
              <td class="actions-cell">
                <div v-if="confirmingDelete === device.id" class="confirm-delete">
                  <span class="confirm-text">Löschen?</span>
                  <button class="delete-confirm-btn" @click="deleteDevice(device.id)">Ja</button>
                  <button class="cancel-btn-sm" @click="confirmingDelete = null">Nein</button>
                </div>
                <div v-else class="device-actions">
                  <button
                    class="edit-icon-btn"
                    :class="{ active: editingId === device.id }"
                    aria-label="Bearbeiten"
                    @click="editingId === device.id ? (editingId = null) : startEdit(device)"
                  >
                    <Icons icon="edit" :size="13" />
                  </button>
                  <button
                    class="delete-icon-btn"
                    aria-label="Gerät entfernen"
                    @click="confirmDelete(device.id)"
                  >
                    <Icons icon="trash" :size="13" />
                  </button>
                </div>
              </td>
            </tr>

            <!-- Inline edit row -->
            <tr v-if="editingId === device.id" class="edit-row">
              <td :colspan="actionMode ? 7 : 6">
                <div class="edit-form">
                  <div class="form-group">
                    <label>Name</label>
                    <input v-model="editForm.alias" />
                  </div>
                  <div class="form-group">
                    <label>Signaldauer (ms)</label>
                    <input
                      v-model="editForm.signalDurationMs"
                      type="number"
                      min="10"
                      placeholder="z.B. 100"
                    />
                  </div>
                  <div class="form-group">
                    <label>SmartBox</label>
                    <select v-model="editForm.smartBoxId">
                      <option v-for="b in boxes" :key="b.id" :value="b.id">
                        {{ b.alias }}
                      </option>
                    </select>
                  </div>
                  <div v-if="firmwareConflict" class="firmware-warning">
                    <Icons icon="alert" :size="13" color="#c05621" />
                    {{ firmwareConflict }}
                  </div>
                  <div class="form-actions">
                    <button class="save-btn" :disabled="!!firmwareConflict" @click="saveEdit(device.id)">
                      <Icons icon="check" :size="12" />
                      Speichern
                    </button>
                    <button class="cancel-btn" @click="editingId = null">
                      Abbrechen
                    </button>
                  </div>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <!-- OTA update panel -->
    <OtaUpdatePanel :box="box" />

    <!-- Add device form / button -->
    <div class="add-device-section">
      <div v-if="isAdding" class="add-form">
        <div class="form-group">
          <label>Name *</label>
          <input
            v-model="newDeviceForm.name"
            placeholder="z.B. Werfer 3"
            @keydown.enter="handleAddDevice"
          />
        </div>
        <div class="form-group">
          <label>Typ *</label>
          <select v-model="newDeviceForm.deviceTypeId">
            <option value="" disabled>Typ wählen</option>
            <option v-for="t in availableDeviceTypes" :key="t.id" :value="t.id">
              {{ t.name }}{{ groupNameForType(t) ? ` (${groupNameForType(t)})` : '' }}
            </option>
          </select>
        </div>
        <button
          v-if="isAdmin"
          type="button"
          class="debug-toggle"
          @click="showDebugTypes = !showDebugTypes"
        >
          {{ showDebugTypes ? 'Debug-Geräte ausblenden' : 'Debug-Geräte anzeigen' }}
        </button>
        <div class="form-actions">
          <button
            class="add-btn"
            :disabled="!newDeviceForm.name.trim() || !newDeviceForm.deviceTypeId"
            @click="handleAddDevice"
          >
            Erfassen
          </button>
          <button class="cancel-btn" @click="toggleAddMode">Abbrechen</button>
        </div>
      </div>
      <button v-else class="add-device-btn" @click="toggleAddMode">
        <span>
          <Icons icon="plus" :size="11" />
        </span>
        Gerät erfassen
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useSmartBoxStore } from '../stores/smartBoxStore.js';
import { useRangeStore } from '../stores/rangeStore.js';
import { useDeviceTypeGroupStore } from '../stores/deviceTypeGroupStore.js';
import { useDeviceTypeStore } from '../stores/deviceTypeStore.js';
import { useAuthStore } from '../stores/authStore.js';
import { ADMIN_PERMISSION, DEBUG_GROUP_NAMES, STATUS_LABELS } from '../constants/deviceTypes.js';
import StatusDot from './StatusDot.vue';
import Badge from './Badge.vue';
import TypeChip from './TypeChip.vue';
import Icons from './Icons.vue';
import OtaUpdatePanel from './OtaUpdatePanel.vue';

const props = defineProps({
  box: {
    type: Object,
    required: true,
  },
  devices: {
    type: Array,
    required: true,
  },
  allDevicesCount: {
    type: Number,
    required: true,
  },
  actionMode: Boolean,
  firedDevices: {
    type: Object,
    default: () => ({}),
  },
});

const emit = defineEmits([
  'fire',
  'add-device',
  'remove-device',
  'update-device',
  'rename-box',
]);

const smartBoxStore = useSmartBoxStore();
const rangeStore = useRangeStore();
const deviceTypeGroupStore = useDeviceTypeGroupStore();
const deviceTypeStore = useDeviceTypeStore();
const authStore = useAuthStore();

const isAdmin = computed(() => authStore.hasPermission(ADMIN_PERMISSION));

const availableDeviceTypes = computed(() => {
  return deviceTypeStore.deviceTypes.filter((t) => {
    const group = deviceTypeGroupStore?.groups?.find?.((g) => g.id === t.groupId)
      ?? deviceTypeStore.deviceTypeGroups?.find?.((g) => g.id === t.groupId);
    const isDebug = DEBUG_GROUP_NAMES.includes((group?.name ?? '').toUpperCase());
    if (isDebug) return showDebugTypes.value && isAdmin.value;
    return true;
  });
});

const groupNameForType = (t) => {
  const group = deviceTypeGroupStore?.groups?.find?.((g) => g.id === t.groupId)
    ?? deviceTypeStore.deviceTypeGroups?.find?.((g) => g.id === t.groupId);
  return group?.name ?? '';
};

const renamingBox = ref(false);
const aliasInput = ref(props.box.alias);
const isAdding = ref(false);
const showDebugTypes = ref(false);
const editingId = ref(null);
const editForm = ref({});
const newDeviceForm = ref({ name: '', deviceTypeId: '' });
const confirmingDelete = ref(null);

const boxes = computed(() => smartBoxStore.smartboxes);

const firmwareConflict = computed(() => {
  if (!editingId.value || !editForm.value.smartBoxId) return null;
  if (editForm.value.smartBoxId === props.box.id) return null;
  const targetBox = boxes.value.find((b) => b.id === editForm.value.smartBoxId);
  if (!targetBox) return null;
  if (props.box.firmwareConfigId && targetBox.firmwareConfigId &&
      props.box.firmwareConfigId !== targetBox.firmwareConfigId) {
    return `"${props.box.alias}" und "${targetBox.alias}" haben unterschiedliche Firmware-Konfigurationen. Verschieben nicht möglich.`;
  }
  return null;
});

const resetNewDeviceForm = () => {
  newDeviceForm.value = { name: '', deviceTypeId: '' };
  showDebugTypes.value = false;
};

const formatCommandTime = (iso) => {
  if (!iso) return '–';
  const d = new Date(iso);
  const now = new Date();
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate();
  const hm = d.toLocaleTimeString('de-CH', { hour: '2-digit', minute: '2-digit' });
  if (sameDay) return hm;
  return `${d.toLocaleDateString('de-CH', { day: '2-digit', month: '2-digit' })} ${hm}`;
};

const getRangeName = (rangeId) => {
  return rangeStore.ranges.find((r) => r.id === rangeId)?.name || 'Zugeordnet';
};

const getStatusLabel = (status) => {
  return STATUS_LABELS[status] || status;
};

const startEdit = (device) => {
  editingId.value = device.id;
  editForm.value = {
    alias: device.alias ?? device.name ?? '',
    deviceTypeId: device.deviceTypeId ?? '',
    signalDurationMs: device.signalDurationMs ?? '',
    smartBoxId: device.smartBoxId ?? device.boxId ?? props.box.id,
  };
};

const saveEdit = (deviceId) => {
  const updates = {
    alias: editForm.value.alias?.trim() ?? '',
    smartBoxId: editForm.value.smartBoxId,
  };
  if (editForm.value.signalDurationMs !== '' && editForm.value.signalDurationMs !== null) {
    updates.signalDurationMs = parseInt(editForm.value.signalDurationMs);
  }
  emit('update-device', { deviceId, updates });
  editingId.value = null;
};

const saveAlias = () => {
  if (aliasInput.value.trim()) {
    emit('rename-box', {
      boxId: props.box.id,
      alias: aliasInput.value.trim(),
    });
  }
  renamingBox.value = false;
};

const cancelRename = () => {
  aliasInput.value = props.box.alias;
  renamingBox.value = false;
};

const toggleAddMode = () => {
  isAdding.value = !isAdding.value;
  if (!isAdding.value) {
    resetNewDeviceForm();
  }
};

const handleAddDevice = () => {
  if (!newDeviceForm.value.name.trim() || !newDeviceForm.value.deviceTypeId) return;
  emit('add-device', {
    boxId: props.box.id,
    device: {
      name: newDeviceForm.value.name,
      deviceTypeId: newDeviceForm.value.deviceTypeId,
    },
  });
  resetNewDeviceForm();
  isAdding.value = false;
};

const onRangeChange = (deviceId, rangeId) => {
  emit('update-device', {
    deviceId,
    updates: { rangeId: rangeId || null },
  });
};

const confirmDelete = (deviceId) => {
  editingId.value = null;
  confirmingDelete.value = deviceId;
};

const deleteDevice = (deviceId) => {
  emit('remove-device', deviceId);
  confirmingDelete.value = null;
};
</script>

<style scoped>
.box-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.07);
  border: 1px solid #f0f4f8;
  overflow: hidden;
  transition: border-color 0.2s;
}

.box-card.action-mode {
  border-color: rgba(251, 191, 36, 0.3);
}

.box-header {
  padding: 14px 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #f0f4f8;
  background: #fff;
}

.box-card.action-mode .box-header {
  background: rgba(251, 191, 36, 0.03);
}

.box-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #f4f6fb;
  border: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #718096;
}

.box-info {
  flex: 1;
  min-width: 0;
}

.rename-form {
  display: flex;
  align-items: center;
  gap: 6px;
}

.rename-form input {
  padding: 4px 8px;
  border: 1.5px solid #4fc3f7;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 700;
  outline: none;
  width: 180px;
}

.box-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.box-name {
  font-weight: 700;
  font-size: 15px;
  color: #1a1a2e;
}

.edit-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: #c0c8d8;
  padding: 2px;
  display: flex;
  align-items: center;
  opacity: 0.6;
  transition: opacity 0.15s;
}

.edit-btn:hover {
  opacity: 1;
}

.box-mac {
  font-size: 11.5px;
  color: #a0aec0;
  font-family: monospace;
  margin-top: 1px;
}

.box-status-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 5px;
}

.status-label {
  font-size: 12px;
  color: #555;
}

.firmware-badge {
  font-size: 11px;
  color: #a0aec0;
  background: #f4f6fb;
  border-radius: 5px;
  padding: 2px 7px;
}

.device-table-wrapper {
  border-bottom: 1px solid #f0f4f8;
}

.device-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.device-table thead {
  background: #f9fafb;
}

.device-table th {
  padding: 7px 16px;
  text-align: left;
  font-size: 11.5px;
  font-weight: 600;
  color: #718096;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  border-bottom: 1px solid #f0f4f8;
}

.device-table th:last-child {
  text-align: right;
}

.device-table tbody tr {
  background: #fff;
}

.device-table tbody tr:nth-child(even) {
  background: #fafbfc;
}

.device-table tbody tr.editing {
  background: #f0f9ff;
}

.device-table td {
  padding: 9px 16px;
}

.name-cell {
  font-weight: 600;
  color: #1a1a2e;
}

.unassigned {
  color: #c0c8d8;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  background: #1a1a2e;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  font-family: inherit;
  transition: background 0.2s;
}

.action-btn.fired {
  background: #4fc3f7;
}

.action-btn:hover {
  background: #0f0f1a;
}

.action-btn.fired:hover {
  background: #2ba4d0;
}

.actions-cell {
  text-align: right;
}

.device-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: flex-end;
}

.edit-icon-btn,
.delete-icon-btn {
  background: none;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 5px 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  color: #718096;
  transition: all 0.15s;
}

.edit-icon-btn:hover {
  background: #e8f5ff;
  border-color: #4fc3f7;
  color: #1a5fa0;
}

.edit-icon-btn.active {
  background: #e8f5ff;
  border-color: #4fc3f7;
  color: #1a5fa0;
}

.delete-icon-btn {
  border-color: #fca5a5;
  color: #e05252;
}

.delete-icon-btn:hover {
  background: #fde0e0;
}

.confirm-delete {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: flex-end;
}

.confirm-text {
  font-size: 12px;
  color: #e05252;
  font-weight: 500;
  white-space: nowrap;
}

.delete-confirm-btn {
  padding: 4px 10px;
  background: #e05252;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  font-family: inherit;
  font-weight: 500;
  transition: background 0.15s;
}

.delete-confirm-btn:hover {
  background: #c53030;
}

.cancel-btn-sm {
  padding: 4px 10px;
  background: transparent;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  color: #718096;
  font-family: inherit;
  transition: background 0.15s;
}

.cancel-btn-sm:hover {
  background: #f9fafb;
}

.edit-row {
  background: #f0f9ff;
  border-bottom: 2px solid #4fc3f7;
}

.edit-row td {
  padding: 0;
}

.edit-form {
  padding: 14px 18px;
  display: flex;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-group label {
  font-size: 11.5px;
  color: #718096;
  font-weight: 500;
}

.form-group input,
.form-group select {
  padding: 6px 10px;
  border: 1.5px solid #4fc3f7;
  border-radius: 7px;
  font-size: 13px;
  background: #fff;
  outline: none;
  cursor: pointer;
  font-family: inherit;
}

.form-group input[type='text'],
.form-group input[type='number'] {
  width: 200px;
}

.form-group input[type='number'] {
  width: 100px;
  font-family: monospace;
}

.form-group select {
  cursor: pointer;
}

.firmware-warning {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #fff7ed;
  border: 1px solid #f6ad55;
  border-radius: 7px;
  padding: 7px 12px;
  font-size: 12px;
  color: #c05621;
  width: 100%;
  box-sizing: border-box;
}

.form-actions {
  display: flex;
  gap: 8px;
}

.save-btn,
.add-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 7px 14px;
  background: #1a1a2e;
  color: #fff;
  border: none;
  border-radius: 7px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  transition: background 0.15s;
}

.save-btn:hover:not(:disabled),
.add-btn:hover:not(:disabled) {
  background: #0f0f1a;
}

.add-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.cancel-btn,
.check-btn {
  padding: 7px 12px;
  background: transparent;
  border: 1px solid #e2e8f0;
  border-radius: 7px;
  cursor: pointer;
  font-size: 13px;
  color: #718096;
  font-family: inherit;
  transition: all 0.15s;
}

.cancel-btn:hover,
.check-btn:hover {
  background: #f9fafb;
  border-color: #c0c8d8;
}

.check-btn {
  display: flex;
  align-items: center;
  padding: 4px 8px;
  background: #1a1a2e;
  color: #fff;
  border: none;
}

.check-btn:hover {
  background: #0f0f1a;
  border-color: #1a1a2e;
}

.add-device-section {
  border-top: 1px dashed #e2e8f0;
}

.add-device-btn {
  width: 100%;
  padding: 10px 18px;
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 7px;
  color: #718096;
  font-size: 13px;
  font-family: inherit;
  transition: background 0.12s;
}

.add-device-btn:hover {
  background: #f9fafb;
}

.add-device-btn span {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #f0f4f8;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.add-form {
  padding: 14px 18px;
  background: #f9fafb;
  display: flex;
  align-items: flex-end;
  gap: 10px;
  flex-wrap: wrap;
  border-top: 1px dashed #e2e8f0;
}

.debug-toggle {
  background: none;
  border: none;
  color: #718096;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 0;
  text-decoration: underline;
  font-family: inherit;
}

.stat-col {
  color: #718096;
  font-size: 12.5px;
  white-space: nowrap;
}

.stat-num {
  font-variant-numeric: tabular-nums;
  font-family: monospace;
  font-size: 13px;
  color: #4a5568;
}

.range-select {
  font-size: 13px;
  padding: 6px 10px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: #2d3748;
  font-family: inherit;
  outline: none;
}

.range-select:hover,
.range-select:focus {
  background: #f9fafb;
  border-radius: 4px;
}

/* ── Type subtext (desktop: hidden; mobile: shown) ── */
.type-sub {
  display: none;
  font-size: 11px;
  color: #a0aec0;
  font-weight: 400;
  margin-top: 2px;
}

/* ── Responsive ───────────────────────────────── */
@media (max-width: 600px) {
  /* Box header: status row wraps below name */
  .box-header {
    flex-wrap: wrap;
    gap: 8px 12px;
  }

  .box-status-info {
    width: 100%;
    border-top: 1px solid #f0f4f8;
    padding-top: 8px;
    margin-left: 0;
  }

  /* Shrink rename input so it fits */
  .rename-form input {
    width: 130px;
  }

  /* Hide Typ and stat columns; surface type as name subtext instead */
  .typ-col,
  .stat-col {
    display: none;
  }

  .type-sub {
    display: block;
  }

  /* Horizontal scroll safety net */
  .device-table-wrapper {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  /* Prevent cells wrapping internally */
  .device-table td,
  .device-table th {
    white-space: nowrap;
  }

  /* Name cell: allow wrap since it has two lines */
  .name-cell {
    white-space: normal;
    min-width: 90px;
  }

  /* Compact range select */
  .range-select {
    max-width: 100px;
    font-size: 12px;
    padding: 4px 6px;
  }

  /* Stacked edit / add forms */
  .edit-form,
  .add-form {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .form-group input,
  .form-group select {
    width: 100%;
    box-sizing: border-box;
  }

  /* Fix the explicit pixel widths */
  .form-group input[type='text'],
  .form-group input[type='number'] {
    width: 100%;
  }
}
</style>
