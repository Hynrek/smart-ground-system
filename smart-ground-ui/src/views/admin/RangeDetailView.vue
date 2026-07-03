<template>
  <div class="range-detail-view">
    <div class="detail-content">
      <Breadcrumb :items="breadcrumbItems" />

      <!-- Header -->
      <div class="detail-header">
        <div>
          <h1>{{ range.name }}</h1>
          <p v-if="range.description" class="subtitle">{{ range.description }}</p>
          <div class="badges-group">
            <Badge color="blue">{{ rangePositions.length }} Positionen</Badge>
            <Badge v-if="onlineCount > 0" color="green">{{ onlineCount }} Online</Badge>
            <Badge v-if="offlineCount > 0" color="red">{{ offlineCount }} Offline</Badge>
            <Badge v-if="warnCount > 0" color="warn">{{ warnCount }} Warnung</Badge>
            <Badge v-if="currentReservation" color="purple">
              <span v-if="currentReservation.username === currentUsername">Blockiert (dir)</span>
              <span v-else>Blockiert ({{ currentReservation.username }})</span>
            </Badge>
          </div>
        </div>
        <div class="header-actions">
          <button
            v-if="!currentReservation"
            class="block-btn"
            :disabled="isReserving"
            @click="reserveRange"
          >
            <Icons icon="lock" :size="13" />
            {{ isReserving ? 'Blockiere...' : 'Blockieren' }}
          </button>
          <button
            v-else-if="currentReservation.username === currentUsername"
            class="release-btn"
            :disabled="isReserving"
            @click="releaseRange"
          >
            <Icons icon="unlock" :size="13" />
            {{ isReserving ? 'Gebe frei...' : 'Freigeben' }}
          </button>
          <button
            v-else-if="isAdmin"
            class="override-btn"
            :disabled="isReserving"
            @click="forceReleaseRange"
          >
            <Icons icon="shield" :size="13" />
            {{ isReserving ? 'Überschreibe...' : 'Admin: Überschreiben' }}
          </button>

          <button
            class="action-mode-btn"
            :class="{ 'action-mode-btn--active': actionMode }"
            @click="actionMode = !actionMode"
          >
            <Icons icon="bolt" :size="13" :color="actionMode ? 'var(--sg-color-warning)' : 'currentColor'" />
            Aktionsmodus
          </button>

          <Button :variant="assignOpen ? 'ghost' : 'primary'" @click="toggleAssignPanel">
            <template #icon>
              <Icons :icon="assignOpen ? 'x' : 'plus'" :size="13" />
            </template>
            {{ assignOpen ? 'Schliessen' : 'Gerät zuordnen' }}
          </Button>
        </div>
      </div>

      <!-- User als Remote zuweisen -->
      <section class="remote-user-section">
        <h4 class="section-label">User als Remote zuweisen</h4>

        <div v-if="assignedUser" class="assigned-user-row">
          <div class="assigned-avatar">{{ assignedUser.fullName?.charAt(0).toUpperCase() }}</div>
          <span class="assigned-name">{{ assignedUser.fullName }}</span>
          <button
            class="release-btn"
            :disabled="isAssigning"
            @click="handleUnassign"
          >
            {{ isAssigning ? '…' : 'Entfernen' }}
          </button>
        </div>

        <div v-else class="unassigned-row">
          <span class="unassigned-hint">Kein Benutzer zugewiesen</span>
          <button class="block-btn" :disabled="isAssigning" @click="openUserModal">
            + Zuweisen
          </button>
        </div>

        <UserSearchModal
          v-if="showUserModal"
          :users="userStore.users"
          @select="handleAssign"
          @close="showUserModal = false"
        />
      </section>

      <!-- Position grid -->
      <div class="positions-grid">
        <PositionCard
          v-for="pos in rangePositions"
          :key="pos.id"
          :position="pos"
          :action-mode="actionMode"
          :fired="firedDevices[pos.device?.id]"
          @drop="(deviceId) => handleDropOnPosition(pos.id, deviceId)"
          @remove-device="removeDeviceFromPosition(pos.id)"
          @rename="(label) => handleRename(pos.id, label)"
          @delete-position="handleDeletePosition(pos.id)"
          @fire="(deviceId) => fireDevice(deviceId)"
        />

        <!-- Ghost card: always one after the last real position -->
        <PositionCard
          :position="null"
          :next-label="nextAutoLabel"
          @add-position="handleAddPosition"
        />
      </div>
    </div>

    <!-- Slide-in panel -->
    <div class="assign-panel" :class="{ open: assignOpen }">
      <div class="panel-inner">
        <div class="panel-header">
          <div class="panel-title">Freie Geräte</div>
          <div class="panel-subtitle">Auf eine Position ziehen zum Zuordnen</div>
        </div>

        <div class="panel-content">
          <div v-if="allSidebarGroups.length === 0" class="empty-panel">
            Keine freien Geräte verfügbar.
          </div>

          <template v-for="group in allSidebarGroups" :key="group.label">
            <div class="panel-section-header">{{ group.label }}</div>
            <div class="devices-list">
              <div
                v-for="device in group.devices"
                :key="device.id"
                class="device-item"
                draggable="true"
                @dragstart="startDrag($event, device)"
                @dragend="endDrag"
              >
                <Icons icon="grip" :size="13" />
                <div class="device-item-info">
                  <div class="device-item-name">{{ device.alias }}</div>
                  <div
                    class="device-item-box"
                    :title="device.smartBoxId || undefined"
                  >{{ device.smartBoxId ? device.smartBoxId.slice(0, 8) + '…' : '–' }}</div>
                </div>
                <div class="device-item-actions">
                  <TypeChip :type="device.groupName ?? device.deviceType" />
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useRangeStore } from '@/stores/rangeStore.js';
import { useDeviceStore } from '@/stores/deviceStore.js';
import { useSmartBoxStore } from '@/stores/smartBoxStore.js';
import { useReservationStore } from '@/stores/reservationStore.js';
import { useUserStore } from '@/stores/userStore.js';
import Breadcrumb from '@/components/Breadcrumb.vue';
import UserSearchModal from '@/components/UserSearchModal.vue';
import Badge from '@/components/Badge.vue';
import Button from '@/components/Button.vue';
import Icons from '@/components/Icons.vue';
import TypeChip from '@/components/TypeChip.vue';
import PositionCard from '@/components/PositionCard.vue';

const props = defineProps({ id: { type: String, required: true } });

const router = useRouter();
const rangeStore = useRangeStore();
const deviceStore = useDeviceStore();
const smartBoxStore = useSmartBoxStore();
const reservationStore = useReservationStore();
const userStore = useUserStore();

const currentReservation = ref(null);
const isReserving = ref(false);
const actionMode = ref(false);
const assignOpen = ref(false);
const draggingDevice = ref(null);
const firedDevices = ref({});
const showUserModal = ref(false);
const isAssigning = ref(false);

const currentUsername = computed(() => userStore.currentUser?.username || '');
const isAdmin = computed(() => userStore.currentUser?.role === 'ADMIN');

onMounted(async () => {
  if (smartBoxStore.smartboxes.length === 0) await smartBoxStore.loadApiData();
  for (const box of smartBoxStore.smartboxes) {
    await deviceStore.loadDevicesForBox(box.id);
  }
  await rangeStore.loadPositions(props.id);
  await loadReservation();
  if (userStore.users.length === 0) await userStore.loadUsers();
});

// ── Range & positions ─────────────────────────────────────────────────────────
const range = computed(
  () => rangeStore.ranges.find((r) => r.id === props.id) || { id: props.id, name: '', description: '' },
);

const rangePositions = computed(() => rangeStore.positions[props.id] ?? []);

// ── Status badges ─────────────────────────────────────────────────────────────
const filledDevices = computed(() => rangePositions.value.map(p => p.device).filter(Boolean));
const onlineCount = computed(() => filledDevices.value.filter(d => d.healthy && !d.blocked).length);
const offlineCount = computed(() => filledDevices.value.filter(d => !d.healthy && !d.blocked).length);
const warnCount = computed(() => filledDevices.value.filter(d => d.blocked).length);

// ── Next auto-label preview (for ghost card) ──────────────────────────────────
const ALPHA_RE = /^[A-Z]+$/;
const nextAutoLabel = computed(() => {
  const used = new Set(rangePositions.value.map(p => p.label).filter(l => ALPHA_RE.test(l)));
  for (let len = 1; len <= 3; len++) {
    for (const label of genLabels(len)) {
      if (!used.has(label)) return label;
    }
  }
  return '+';
});

function genLabels(len) {
  if (len === 1) return Array.from({ length: 26 }, (_, i) => String.fromCharCode(65 + i));
  return genLabels(len - 1).flatMap(prefix =>
    Array.from({ length: 26 }, (_, i) => prefix + String.fromCharCode(65 + i))
  );
}

// ── Breadcrumb ────────────────────────────────────────────────────────────────
const breadcrumbItems = computed(() => [
  { label: 'Schiessplätze', onClick: () => router.push('/ranges') },
  { label: range.value.name },
]);

// ── Sidebar: devices not in any position ──────────────────────────────────────
const assignedDeviceIds = computed(() =>
  new Set(rangePositions.value.map(p => p.device?.id).filter(Boolean))
);

const allSidebarGroups = computed(() => {
  const unassigned = deviceStore.devices.filter(
    d => !d.rangeId && !assignedDeviceIds.value.has(d.id)
  );
  if (unassigned.length === 0) return [];
  return [{ label: 'Nicht zugeteilt', devices: unassigned }];
});

// ── Drag & drop ───────────────────────────────────────────────────────────────
const startDrag = (event, device) => {
  draggingDevice.value = device;
  event.dataTransfer.setData('deviceId', device.id);
  event.dataTransfer.effectAllowed = 'move';
};
const endDrag = () => { draggingDevice.value = null; };

// ── Position actions ──────────────────────────────────────────────────────────
const handleAddPosition = async () => {
  try { await rangeStore.createPosition(props.id); }
  catch (e) { console.error('Failed to add position:', e); }
};

const handleDropOnPosition = async (positionId, deviceId) => {
  try { await rangeStore.assignDeviceToPosition(props.id, positionId, deviceId); }
  catch (e) { console.error('Failed to assign device:', e); }
};

const removeDeviceFromPosition = async (positionId) => {
  try {
    await rangeStore.removeDeviceFromPosition(props.id, positionId);
    await reloadDevices();
  }
  catch (e) { console.error('Failed to remove device:', e); }
};

const handleRename = async (positionId, label) => {
  try { await rangeStore.renamePosition(props.id, positionId, label); }
  catch (e) { console.error('Failed to rename position:', e); }
};

const handleDeletePosition = async (positionId) => {
  const position = rangePositions.value.find(p => p.id === positionId);
  const hadDevice = !!position?.device;
  try {
    await rangeStore.deletePosition(props.id, positionId);
    if (hadDevice) await reloadDevices();
  }
  catch (e) { console.error('Failed to delete position:', e); }
};

const reloadDevices = async () => {
  for (const box of smartBoxStore.smartboxes) {
    await deviceStore.loadDevicesForBox(box.id);
  }
};

const fireDevice = (deviceId) => {
  firedDevices.value[deviceId] = true;
  setTimeout(() => { delete firedDevices.value[deviceId]; }, 1500);
};

const toggleAssignPanel = () => { assignOpen.value = !assignOpen.value; };

// ── Remote user assignment ─────────────────────────────────────────────────────
const assignedUser = computed(() => {
  const id = range.value?.assignedUserId;
  if (!id) return null;
  return userStore.users.find((u) => u.id === id) ?? null;
});

const openUserModal = async () => {
  if (userStore.users.length === 0) await userStore.loadUsers();
  showUserModal.value = true;
};

const handleAssign = async (user) => {
  showUserModal.value = false;
  isAssigning.value = true;
  try {
    await rangeStore.assignUser(props.id, user.id);
  } catch (e) {
    console.error('Failed to assign remote user:', e);
  } finally {
    isAssigning.value = false;
  }
};

const handleUnassign = async () => {
  isAssigning.value = true;
  try {
    await rangeStore.assignUser(props.id, null);
  } catch (e) {
    console.error('Failed to unassign remote user:', e);
  } finally {
    isAssigning.value = false;
  }
};

// ── Reservation ───────────────────────────────────────────────────────────────
const loadReservation = async () => {
  try { currentReservation.value = await reservationStore.getReservationForRange(props.id); }
  catch (e) { console.error('Failed to load reservation:', e); }
};

const reserveRange = async () => {
  isReserving.value = true;
  try { currentReservation.value = await reservationStore.reserve(props.id); }
  catch (e) { console.error('Failed to reserve:', e); }
  finally { isReserving.value = false; }
};

const releaseRange = async () => {
  isReserving.value = true;
  try { await reservationStore.release(props.id); currentReservation.value = null; }
  catch (e) { console.error('Failed to release:', e); }
  finally { isReserving.value = false; }
};

const forceReleaseRange = async () => {
  isReserving.value = true;
  try { await reservationStore.forceRelease(props.id); currentReservation.value = null; }
  catch (e) { console.error('Failed to force release:', e); }
  finally { isReserving.value = false; }
};
</script>

<style scoped>
.range-detail-view {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.detail-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  padding: 28px;
}

h1 {
  font-size: 22px;
  font-weight: 700;
  color: var(--sg-text-primary);
  margin: 0;
  margin-top: 4px;
}

.subtitle {
  font-size: 13px;
  color: var(--sg-text-muted);
  margin-top: 3px;
}

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
  gap: 16px;
  flex-wrap: wrap;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.badges-group {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  flex-wrap: wrap;
}

/* ── Button base ─────────────────────────────────────── */
.block-btn,
.release-btn,
.override-btn,
.action-mode-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 8px;
  border: 1.5px solid;
  background: var(--sg-bg-card);
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
}

.block-btn:disabled,
.release-btn:disabled,
.override-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.block-btn   { border-color: var(--sg-color-info-bg); color: var(--sg-color-info-text); }
.block-btn:hover:not(:disabled) { background: var(--sg-accent-subtle); border-color: var(--sg-accent-hover); }

.release-btn { border-color: var(--sg-color-warning); color: var(--sg-color-warning-text); }
.release-btn:hover:not(:disabled) { background: var(--sg-color-warning-bg); border-color: var(--sg-color-warning-text); }

.override-btn { border-color: var(--sg-color-danger-bg); color: var(--sg-color-danger); }
.override-btn:hover:not(:disabled) { background: var(--sg-danger-subtle); border-color: var(--sg-color-danger); }

.action-mode-btn { border-color: var(--sg-border); color: var(--sg-text-muted); }
.action-mode-btn:hover { border-color: var(--sg-color-warning); color: var(--sg-color-warning-text); }
.action-mode-btn--active {
  background: var(--sg-color-warning-bg);
  border-color: var(--sg-color-warning);
  color: var(--sg-color-warning-text);
}

/* ── Position grid ───────────────────────────────────── */
.positions-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: flex-start;
}

/* ── Assign panel ────────────────────────────────────── */
.assign-panel {
  width: 0;
  height: 100%;
  overflow: hidden;
  transition: width 0.3s cubic-bezier(0.2, 0.8, 0.3, 1);
  flex-shrink: 0;
}
.assign-panel.open { width: 256px; }

.panel-inner {
  width: 256px;
  height: 100%;
  background: var(--sg-bg-panel);
  border-left: 1px solid var(--sg-border);
  display: flex;
  flex-direction: column;
}

.panel-header {
  padding: 20px 16px 12px;
  border-bottom: 1px solid var(--sg-border);
}
.panel-title   { font-weight: 700; font-size: 14px; color: var(--sg-text-primary); margin-bottom: 2px; }
.panel-subtitle { font-size: 12px; color: var(--sg-text-muted); }

.panel-content { flex: 1; overflow-y: auto; }

.panel-section-header {
  padding: 10px 16px 4px;
  font-size: 11px;
  font-weight: 700;
  color: var(--sg-text-faint);
  text-transform: uppercase;
  letter-spacing: 0.6px;
}

.empty-panel {
  padding: 32px 16px;
  text-align: center;
  color: var(--sg-text-faint);
  font-size: 13px;
}

.devices-list {
  padding: 4px 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.device-item {
  background: var(--sg-bg-card);
  border-radius: 8px;
  padding: 8px 10px;
  border: 1px solid var(--sg-border);
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: grab;
  transition: box-shadow 0.15s;
}
.device-item:hover { box-shadow: var(--sg-shadow-md); }
.device-item:active { cursor: grabbing; }

.device-item-info { flex: 1; min-width: 0; }
.device-item-name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.device-item-box { font-size: 11px; color: var(--sg-text-faint); }

.device-item-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ── Remote user section ─────────────────────────────── */
.remote-user-section {
  margin-bottom: 24px;
  padding: 16px 20px;
  background: var(--sg-bg-panel);
  border: 1px solid var(--sg-border);
  border-radius: 12px;
}

.section-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--sg-text-faint);
  text-transform: uppercase;
  letter-spacing: 0.6px;
  margin: 0 0 12px;
}

.assigned-user-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.assigned-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--sg-accent-tint);
  border: 1px solid color-mix(in srgb, var(--sg-accent) 30%, transparent);
  color: var(--sg-color-info-text);
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.assigned-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: var(--sg-text-muted);
}

.unassigned-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.unassigned-hint {
  font-size: 13px;
  color: var(--sg-text-faint);
}
</style>
