<template>
  <div class="range-detail-view">
    <div class="detail-content">
      <Breadcrumb :items="breadcrumbItems" />
      <div class="detail-header">
        <div>
          <h1>{{ range.name }}</h1>
          <p v-if="range.description" class="subtitle">{{ range.description }}</p>
          <div class="badges-group">
            <Badge color="blue">{{ rangeDevices.length }} Geräte</Badge>
            <Badge v-if="onlineCount > 0" color="green">{{ onlineCount }} Online</Badge>
            <Badge v-if="offlineCount > 0" color="red">{{ offlineCount }} Offline</Badge>
            <Badge v-if="warnCount > 0" color="warn">{{ warnCount }} Warnung</Badge>
            <Badge v-if="currentReservation" color="purple">
              <span v-if="currentReservation.username === currentUsername">Reserviert (dir)</span>
              <span v-else>Reserviert ({{ currentReservation.username }})</span>
            </Badge>
          </div>
        </div>
        <div class="header-actions">
          <button
            v-if="!currentReservation"
            class="reserve-btn"
            :disabled="isReserving"
            @click="reserveRange"
          >
            <Icons icon="lock" :size="13" />
            {{ isReserving ? 'Reserviere...' : 'Reservieren' }}
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
            <Icons icon="bolt" :size="13" :color="actionMode ? '#f6ad55' : 'currentColor'" />
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

      <!-- Filter chips -->
      <div class="filter-group">
        <button
          v-for="type in filterOptions"
          :key="type"
          :class="{ active: filterType === type }"
          class="filter-chip"
          @click="filterType = type"
        >
          {{ type }}
          <span class="filter-count">{{ getFilterCount(type) }}</span>
        </button>
      </div>

      <!-- Drop zone -->
      <div
        class="drop-zone"
        :class="{ 'drag-over': dragOver, 'action-mode': actionMode }"
        @dragover.prevent="dragOver = true"
        @dragleave="dragOver = false"
        @drop.prevent="handleDrop"
      >
        <div v-if="filteredDevices.length === 0" class="empty-state">
          <div class="empty-icon">◎</div>
          <div class="empty-title">
            Keine {{ filterType !== 'Alle' ? filterType + '-' : '' }}Geräte
          </div>
          <div class="empty-text">
            {{
              filterType !== 'Alle'
                ? 'Wähle "Alle" um alle Geräte zu sehen.'
                : 'Ziehe Geräte aus der Seitenleiste hierher.'
            }}
          </div>
        </div>

        <!-- Clustered by SmartBox -->
        <div v-else class="box-clusters">
          <div
            v-for="cluster in devicesByBox"
            :key="cluster.boxId"
            class="box-cluster"
          >
            <div class="cluster-heading">
              <Icons icon="wifi" :size="12" color="#a0aec0" />
              <span>{{ cluster.boxName }}</span>
              <span class="cluster-count">{{ cluster.devices.length }}</span>
            </div>
            <div class="devices-grid">
              <div
                v-for="device in cluster.devices"
                :key="device.id"
                class="device-wrapper"
              >
                <DeviceCard
                  :device="device"
                  :fired="firedDevices[device.id]"
                  :action-mode="actionMode"
                  @fire="fireDevice(device.id)"
                />
                <button
                  v-if="assignOpen"
                  class="unassign-btn"
                  aria-label="Aus Platz entfernen"
                  @click="unassignDevice(device.id)"
                >
                  <Icons icon="x" :size="10" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Slide-in panel -->
    <div class="assign-panel" :class="{ open: assignOpen }">
      <div class="panel-inner">
        <div class="panel-header">
          <div class="panel-title">Alle Geräte</div>
          <div class="panel-subtitle">Klicken oder ziehen zum Zuordnen</div>
        </div>

        <div class="panel-content">
          <div v-if="allSidebarGroups.length === 0" class="empty-panel">
            Keine weiteren Geräte verfügbar.
          </div>

          <template v-for="group in allSidebarGroups" :key="group.label">
            <div class="panel-section-header">{{ group.label }}</div>
            <div class="devices-list">
              <div
                v-for="device in group.devices"
                :key="device.id"
                class="device-item"
                draggable="true"
                @dragstart="startDrag(device)"
                @dragend="endDrag"
              >
                <Icons icon="grip" :size="13" />
                <div class="device-item-info">
                  <div class="device-item-name">{{ device.alias }}</div>
                  <div class="device-item-box">{{ device.smartBoxId || '–' }}</div>
                </div>
                <div class="device-item-actions">
                  <TypeChip :type="device.groupName ?? device.deviceType" />
                  <button class="assign-btn" @click="assignDevice(device.id)">
                    + Zuordnen
                  </button>
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
import { useRangeStore } from '../stores/rangeStore.js';
import { useDeviceStore } from '../stores/deviceStore.js';
import { useSmartBoxStore } from '../stores/smartBoxStore.js';
import { useReservationStore } from '../stores/reservationStore.js';
import { useUserStore } from '../stores/userStore.js';
import Breadcrumb from '../components/Breadcrumb.vue';
import Badge from '../components/Badge.vue';
import Button from '../components/Button.vue';
import Icons from '../components/Icons.vue';
import DeviceCard from '../components/DeviceCard.vue';
import TypeChip from '../components/TypeChip.vue';

const props = defineProps({ id: { type: String, required: true } });

const router = useRouter();
const rangeStore = useRangeStore();
const deviceStore = useDeviceStore();
const smartBoxStore = useSmartBoxStore();
const reservationStore = useReservationStore();
const userStore = useUserStore();

const currentReservation = ref(null);
const isReserving = ref(false);

const currentUsername = computed(() => userStore.currentUser?.username || '');
const isAdmin = computed(() => userStore.currentUser?.role === 'ADMIN');

onMounted(async () => {
  if (smartBoxStore.smartboxes.length === 0) {
    await smartBoxStore.loadApiData();
  }
  for (const box of smartBoxStore.smartboxes) {
    await deviceStore.loadDevicesForBox(box.id);
  }
  // Load current reservation for this range
  await loadReservation();
});

const range = computed(
  () => rangeStore.ranges.find((r) => r.id === props.id) || { id: props.id, name: '', description: '' },
);

const assignOpen = ref(false);
const actionMode = ref(false);
const dragOver = ref(false);
const draggingDevice = ref(null);
const filterType = ref('Alle');
const firedDevices = ref({});

// ── Devices on this range ─────────────────────────
const rangeDevices = computed(() => deviceStore.devices.filter((d) => d.rangeId === props.id));

const filterOptions = computed(() => {
  const groupNames = new Set(rangeDevices.value.map((d) => d.groupName).filter(Boolean));
  return ['Alle', ...Array.from(groupNames).sort()];
});

const filteredDevices = computed(() =>
  filterType.value === 'Alle'
    ? rangeDevices.value
    : rangeDevices.value.filter((d) => d.groupName === filterType.value),
);

// ── Cluster filtered devices by SmartBox ─────────
const devicesByBox = computed(() => {
  const map = new Map();
  for (const device of filteredDevices.value) {
    const boxId = device.smartBoxId ?? device.boxId ?? '__unknown__';
    if (!map.has(boxId)) map.set(boxId, []);
    map.get(boxId).push(device);
  }
  return Array.from(map.entries()).map(([boxId, devices]) => ({
    boxId,
    boxName: smartBoxStore.smartboxes.find((b) => b.id === boxId)?.alias ?? boxId,
    devices,
  }));
});

// ── Status badges ─────────────────────────────────
const onlineCount = computed(() => rangeDevices.value.filter((d) => d.healthy && !d.blocked).length);
const offlineCount = computed(() => rangeDevices.value.filter((d) => !d.healthy || d.blocked).length);
const warnCount = computed(() => rangeDevices.value.filter((d) => d.blocked).length);

const breadcrumbItems = computed(() => [
  { label: 'Schiessplätze', onClick: () => router.push('/ranges') },
  { label: range.value.name },
]);

const getFilterCount = (type) => {
  if (type === 'Alle') return rangeDevices.value.length;
  return rangeDevices.value.filter((d) => d.groupName === type).length;
};

// ── Sidebar: all devices not on this range ────────
const allSidebarGroups = computed(() => {
  const groups = [];

  const unassigned = deviceStore.devices.filter((d) => !d.rangeId);
  if (unassigned.length) {
    groups.push({ label: 'Nicht zugeteilt', devices: unassigned });
  }

  const byRange = new Map();
  for (const d of deviceStore.devices) {
    if (d.rangeId && d.rangeId !== props.id) {
      if (!byRange.has(d.rangeId)) byRange.set(d.rangeId, []);
      byRange.get(d.rangeId).push(d);
    }
  }
  for (const [rangeId, devices] of byRange.entries()) {
    const name = rangeStore.ranges.find((r) => r.id === rangeId)?.name ?? rangeId;
    groups.push({ label: name, devices });
  }

  return groups;
});

// ── Actions ───────────────────────────────────────
const toggleAssignPanel = () => {
  assignOpen.value = !assignOpen.value;
};

const assignDevice = async (deviceId) => {
  try {
    await rangeStore.assignDeviceToRange(deviceId, range.value.id);
    await deviceStore.loadDevicesForBox(smartBoxStore.smartboxes.find(b =>
      deviceStore.devices.find(d => d.id === deviceId)?.smartBoxId === b.id
    )?.id);
  } catch (e) {
    console.error('Failed to assign device:', e);
  }
};

const unassignDevice = async (deviceId) => {
  try {
    await rangeStore.unassignDeviceFromRange(deviceId, range.value.id);
    await deviceStore.loadDevicesForBox(smartBoxStore.smartboxes.find(b =>
      deviceStore.devices.find(d => d.id === deviceId)?.smartBoxId === b.id
    )?.id);
  } catch (e) {
    console.error('Failed to unassign device:', e);
  }
};

const fireDevice = (deviceId) => {
  firedDevices.value[deviceId] = true;
  setTimeout(() => {
    delete firedDevices.value[deviceId];
  }, 1500);
};

const startDrag = (device) => { draggingDevice.value = device; };
const endDrag = () => { draggingDevice.value = null; dragOver.value = false; };
const handleDrop = async () => {
  if (draggingDevice.value) {
    await assignDevice(draggingDevice.value.id);
    draggingDevice.value = null;
  }
  dragOver.value = false;
};

// ── Reservation functions ────────────────────────
const loadReservation = async () => {
  try {
    const reservation = await reservationStore.getReservationForRange(props.id);
    currentReservation.value = reservation;
  } catch (e) {
    console.error('Failed to load reservation:', e);
  }
};

const reserveRange = async () => {
  if (currentReservation.value) return;
  isReserving.value = true;
  try {
    const reservation = await reservationStore.reserve(props.id);
    currentReservation.value = reservation;
  } catch (e) {
    console.error('Failed to reserve:', e);
  } finally {
    isReserving.value = false;
  }
};

const releaseRange = async () => {
  isReserving.value = true;
  try {
    await reservationStore.release(props.id);
    currentReservation.value = null;
  } catch (e) {
    console.error('Failed to release:', e);
  } finally {
    isReserving.value = false;
  }
};

const forceReleaseRange = async () => {
  isReserving.value = true;
  try {
    await reservationStore.forceRelease(props.id);
    currentReservation.value = null;
  } catch (e) {
    console.error('Failed to force release:', e);
  } finally {
    isReserving.value = false;
  }
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
  color: #1a1a2e;
  margin: 0;
  margin-top: 4px;
}

.subtitle {
  font-size: 13px;
  color: #718096;
  margin-top: 3px;
}

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
  gap: 16px;
  flex-wrap: wrap;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.action-mode-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 8px;
  border: 1.5px solid #e2e8f0;
  background: #fff;
  color: #4a5568;
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
}

.action-mode-btn:hover {
  border-color: #f6ad55;
  color: #c05621;
}

.action-mode-btn--active {
  background: rgba(246, 173, 85, 0.12);
  border-color: #f6ad55;
  color: #c05621;
}

.reserve-btn,
.release-btn,
.override-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 8px;
  border: 1.5px solid;
  background: #fff;
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
}

.reserve-btn:disabled,
.release-btn:disabled,
.override-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.reserve-btn {
  border-color: #90cdf4;
  color: #2c5aa0;
}

.reserve-btn:hover:not(:disabled) {
  background: rgba(144, 205, 244, 0.1);
  border-color: #2c5aa0;
}

.release-btn {
  border-color: #f6ad55;
  color: #c05621;
}

.release-btn:hover:not(:disabled) {
  background: rgba(246, 173, 85, 0.1);
  border-color: #c05621;
}

.override-btn {
  border-color: #fc8181;
  color: #c53030;
}

.override-btn:hover:not(:disabled) {
  background: rgba(252, 129, 129, 0.1);
  border-color: #c53030;
}

.badges-group {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  flex-wrap: wrap;
}

/* ── Filter chips ────────────────────────────────── */
.filter-group {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.filter-chip {
  padding: 5px 13px;
  border-radius: 99px;
  border: 1.5px solid #e2e8f0;
  background: #fff;
  color: #4a5568;
  cursor: pointer;
  font-size: 13px;
  font-weight: 400;
  font-family: inherit;
  display: flex;
  align-items: center;
  gap: 5px;
  transition: all 0.15s;
}

.filter-chip.active {
  border-color: #1a1a2e;
  background: #1a1a2e;
  color: #fff;
  font-weight: 600;
}

.filter-count {
  background: #e8edf0;
  color: #718096;
  border-radius: 99px;
  font-size: 11px;
  padding: 1px 6px;
  font-weight: 600;
}

.filter-chip.active .filter-count {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
}

/* ── Drop zone ───────────────────────────────────── */
.drop-zone {
  flex: 1;
  border: 2px dashed transparent;
  border-radius: 12px;
  transition: border 0.15s, background 0.15s;
  background: transparent;
  min-height: 200px;
}

.drop-zone.drag-over {
  border-color: #4fc3f7;
  background: rgba(79, 195, 247, 0.04);
}

.drop-zone.action-mode {
  border-color: rgba(246, 173, 85, 0.25);
  background: rgba(246, 173, 85, 0.02);
  border-style: solid;
}

/* ── Empty state ─────────────────────────────────── */
.empty-state {
  text-align: center;
  padding: 48px 24px;
  color: #a0aec0;
}

.empty-icon { font-size: 32px; margin-bottom: 12px; }
.empty-title { font-size: 14px; font-weight: 500; color: #a0aec0; }
.empty-text { font-size: 13px; margin-top: 4px; color: #a0aec0; }

/* ── Box clusters ────────────────────────────────── */
.box-clusters {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.cluster-heading {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 600;
  color: #718096;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.cluster-count {
  background: #e8edf0;
  color: #718096;
  border-radius: 99px;
  font-size: 11px;
  padding: 1px 6px;
}

.devices-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.device-wrapper {
  position: relative;
}

.unassign-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #fde0e0;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}

.unassign-btn:hover { background: #fca5a5; }

/* ── Assign panel ────────────────────────────────── */
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
  background: #f4f6fb;
  border-left: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
}

.panel-header {
  padding: 20px 16px 12px;
  border-bottom: 1px solid #e2e8f0;
}

.panel-title {
  font-weight: 700;
  font-size: 14px;
  color: #1a1a2e;
  margin-bottom: 2px;
}

.panel-subtitle {
  font-size: 12px;
  color: #718096;
}

.panel-content {
  flex: 1;
  overflow-y: auto;
}

.panel-section-header {
  padding: 10px 16px 4px;
  font-size: 11px;
  font-weight: 700;
  color: #a0aec0;
  text-transform: uppercase;
  letter-spacing: 0.6px;
}

.empty-panel {
  padding: 32px 16px;
  text-align: center;
  color: #a0aec0;
  font-size: 13px;
}

.devices-list {
  padding: 4px 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.device-item {
  background: #fff;
  border-radius: 8px;
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: grab;
  transition: opacity 0.15s, box-shadow 0.15s;
}

.device-item:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
.device-item:active { cursor: grabbing; }

.device-item-info { flex: 1; min-width: 0; }

.device-item-name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-item-box { font-size: 11px; color: #a0aec0; }

.device-item-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.assign-btn {
  background: #1a1a2e;
  color: #fff;
  border: none;
  border-radius: 5px;
  padding: 3px 8px;
  font-size: 11px;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s;
  white-space: nowrap;
}

.assign-btn:hover { background: #0f0f1a; }
</style>
