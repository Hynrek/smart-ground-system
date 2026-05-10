<template>
  <div class="shooter-remote">
    <!-- Header with integrated controls -->
    <div class="page-header">
      <div class="header-left">
        <button class="back-btn" @click="goBack">
          <Icons icon="chevronLeft" :size="20" color="rgba(255,255,255,0.8)" />
        </button>
        <div class="header-text">
          <h1 class="page-title">{{ range?.name ?? '…' }}</h1>
          <p v-if="range?.description" class="page-sub">{{ range.description }}</p>
        </div>
      </div>

      <div class="header-right">
        <!-- Status indicator (emergency stop) - leftmost -->
        <div class="header-status">
          <button
            class="status-indicator"
            :class="{ 'is-locked': isLocked, 'is-ready': !isLocked }"
            :title="isLocked ? 'Notfallsperrung aktiv - Klick zum Freigeben' : 'Klick zum Notfall Stop'"
            @click="toggleBlock"
          >
            <Icons :icon="isLocked ? 'unlock' : 'alert'" :size="16" />
            <span class="status-text">{{ isLocked ? 'Freigeben' : 'Notfall Stop' }}</span>
          </button>
        </div>

        <!-- Mode toggle (Throwing / Recording) -->
        <div class="header-mode-toggle">
          <button
            class="mode-toggle-btn"
            :class="{ active: store.sessionMode === 'throwing' }"
            title="Wurf-Modus"
            @click="store.setSessionMode('throwing')"
          >
            W
          </button>
          <button
            class="mode-toggle-btn"
            :class="{
              active: store.sessionMode === 'recording',
              'is-recording': store.recordingActive
            }"
            title="Erfassungs-Modus"
            @click="store.setSessionMode('recording')"
          >
            E
          </button>
        </div>

        <!-- Group dropdown (only when multiple groups) -->
        <div v-if="deviceGroups.length > 1" class="group-dropdown-wrapper" :class="{ open: isGroupDropdownOpen }">
          <button class="group-dropdown-btn" @click="isGroupDropdownOpen = !isGroupDropdownOpen">
            <span class="group-name">{{ selectedGroupName }}</span>
            <Icons icon="chevronRight" :size="14" color="rgba(255,255,255,0.5)" />
          </button>
          <div v-if="isGroupDropdownOpen" class="group-dropdown-menu" @click.stop>
            <button
              v-for="group in deviceGroups"
              :key="group.groupId"
              class="group-option"
              :class="{ active: store.selectedGroupId === group.groupId }"
              @click="store.setGroupId(group.groupId); isGroupDropdownOpen = false"
            >
              <span class="option-name">{{ group.name }}</span>
              <span class="option-count">{{ group.count }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>


    <!-- Device section -->
    <div class="device-section">
      <p v-if="activeDevices.length > 0" class="section-title">
        {{ activeDevices.length }} {{ activeDevices.length === 1 ? 'Gerät' : 'Geräte' }}
      </p>

      <div v-if="deviceStore.isLoading" class="state-center">
        <p class="state-text">Lade Geräte…</p>
      </div>

      <div v-else-if="activeDevices.length === 0" class="state-center">
        <Icons icon="bolt" :size="44" color="rgba(255,255,255,0.1)" />
        <p class="state-text">Keine Geräte in dieser Gruppe</p>
        <p class="state-hint">Bitte einen Administrator kontaktieren.</p>
      </div>

      <div v-else class="device-grid">
        <button
          v-for="(device, i) in activeDevices"
          :key="device.id"
          class="device-btn"
          :class="deviceBtnClass(device)"
          :disabled="isDeviceDisabled(device)"
          @click="handleDeviceTap(device)"
        >
          <div class="btn-glow" />
          <div class="btn-icon-wrap">
            <span class="btn-letter" :style="{ color: iconColor(device) }">
              {{ String.fromCharCode(65 + i) }}
            </span>
          </div>
          <span class="btn-label">{{ device.alias ?? `Gerät ${i + 1}` }}</span>
          <span class="btn-status-chip" :class="chipClass(device)">{{ chipLabel(device) }}</span>
        </button>
      </div>
    </div>

    <!-- Mode selector (fixed bottom) -->
    <div class="solo-pair-bar">
      <div class="solo-pair-toggle">
        <button
          class="toggle-btn"
          :class="{ active: store.mode === 'solo' }"
          @click="store.setMode('solo')"
        >
          Solo
        </button>
        <button
          class="toggle-btn"
          :class="{ active: store.mode === 'pair' }"
          @click="store.setMode('pair')"
        >
          Pair
        </button>
        <!-- Extra options in Erfassungs mode -->
        <template v-if="store.sessionMode === 'recording'">
          <button
            class="toggle-btn"
            :class="{ active: store.mode === 'a.schuss' }"
            @click="store.setMode('a.schuss')"
          >
            a. Schuss
          </button>
          <button
            class="toggle-btn"
            :class="{ active: store.mode === 'raffale' }"
            @click="store.setMode('raffale')"
          >
            Raffale
          </button>
        </template>
      </div>
    </div>

    <!-- Flyout panel -->
    <ShooterFlyoutPanel />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useRangeStore } from '@/stores/rangeStore.js';
import { useDeviceStore } from '@/stores/deviceStore.js';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import { useProgramStore } from '@/stores/programStore.js';
import { useDeviceLoader } from '@/composables/useDeviceLoader.js';
import { sendDeviceCommand } from '@/services/deviceApi.js';
import Icons from '@/components/Icons.vue';
import ShooterFlyoutPanel from '@/components/shooter-remote/ShooterFlyoutPanel.vue';

const props = defineProps({ rangeId: { type: String, required: true } });

const router = useRouter();
const rangeStore = useRangeStore();
const deviceStore = useDeviceStore();
const store = useShooterRemoteStore();
const programStore = useProgramStore();

const isGroupDropdownOpen = ref(false);

useDeviceLoader();

onMounted(() => {
  store.ensureReserved(props.rangeId);
  initDefaultGroup();
  // Default to solo mode
  store.setMode('solo');
});

onUnmounted(() => {
  // Auto-release when navigating away
  store.releasePlatz();
});

// ── Range ──────────────────────────────────────────
const range = computed(() => rangeStore.ranges.find((r) => r.id === props.rangeId));
const isLocked = computed(() => range.value?.locked ?? false);

// ── Device groups ──────────────────────────────────
const selectedGroupName = computed(() => {
  const group = deviceGroups.value.find((g) => g.groupId === store.selectedGroupId);
  return group?.name ?? 'Geräte';
});

// ── Status card ────────────────────────────────────
const statusKey = computed(() => {
  if (isLocked.value) return 'blocked';
  if (store.reservedByMe) return 'reserved';
  return 'free';
});

const statusLabel = computed(() => {
  if (isLocked.value) return 'Gesperrt';
  if (store.reservedByMe) return 'Reserviert';
  return 'Frei';
});

const freigeben = () => {
  store.releasePlatz();
  router.push('/remote');
};

const goBack = () => {
  store.releasePlatz();
  router.push('/remote');
};

const toggleBlock = async () => {
  if (!range.value) return;
  await rangeStore.setLocked(range.value.id, !isLocked.value);
};

// ── Device groups ──────────────────────────────────
const rangeDevices = computed(() =>
  deviceStore.devices.filter((d) => d.rangeId === props.rangeId),
);

const deviceGroups = computed(() => {
  const map = new Map();
  for (const d of rangeDevices.value) {
    const name = d.groupName ?? 'Sonstige';
    const groupId = d.groupId ?? encodeURIComponent(name);
    const entry = map.get(groupId) ?? { name, groupId, count: 0 };
    entry.count++;
    map.set(groupId, entry);
  }
  return Array.from(map.values());
});

const initDefaultGroup = () => {
  if (store.selectedGroupId) return;
  if (deviceGroups.value.length === 0) return;
  const wurfmaschine = deviceGroups.value.find((g) =>
    g.name.toLowerCase().includes('wurfmaschine'),
  );
  store.setGroupId((wurfmaschine ?? deviceGroups.value[0]).groupId);
};

watch(deviceGroups, () => {
  if (!store.selectedGroupId) initDefaultGroup();
});

// ── Active devices ─────────────────────────────────
const activeDevices = computed(() => {
  if (!store.selectedGroupId) return [];
  return rangeDevices.value.filter(
    (d) => (d.groupId ?? encodeURIComponent(d.groupName ?? '')) === store.selectedGroupId,
  );
});

// ── Group icon ─────────────────────────────────────
const ICON_MAP = { wurfmaschine: 'target', led: 'bolt', cpu: 'cpu', sensor: 'wifi' };

const activeGroupIcon = computed(() => {
  const group = deviceGroups.value.find((g) => g.groupId === store.selectedGroupId);
  if (!group) return 'bolt';
  const key = group.name.toLowerCase();
  for (const [prefix, icon] of Object.entries(ICON_MAP)) {
    if (key.includes(prefix)) return icon;
  }
  return 'bolt';
});

// ── Fire state ─────────────────────────────────────
const firingIds = ref(new Set());
const firedIds = ref(new Set());
const errorIds = ref(new Set());

const handleDeviceTap = async (device) => {
  if (isDeviceDisabled(device)) return;

  // Recording mode: add step to program
  if (store.sessionMode === 'recording' && programStore.programMode) {
    programStore.addStep(device.id, device);
    return;
  }

  // Throwing mode with pair/a.schuss: handle pair selection
  if ((store.mode === 'pair' || store.mode === 'a.schuss') && !programStore.programMode) {
    if (!store.throwPairPending) {
      store.throwPairPending = { id: device.id, alias: device.alias ?? 'Gerät' };
    } else if (store.throwPairPending.id === device.id) {
      store.throwPairPending = null;
    } else {
      // Fire both devices
      const pendingId = store.throwPairPending.id;
      store.throwPairPending = null;

      await firePairDevices(pendingId, device.id);
      return;
    }
    return;
  }

  // Throwing mode with raffale: fire device twice
  if (store.mode === 'raffale' && !programStore.programMode) {
    await fireRaffaleDevice(device.id);
    return;
  }

  // Solo mode: fire single device
  await fireSingleDevice(device.id);
};

const fireSingleDevice = async (deviceId) => {
  if (firingIds.value.has(deviceId)) return;
  firingIds.value = new Set([...firingIds.value, deviceId]);
  firedIds.value.delete(deviceId);
  errorIds.value.delete(deviceId);

  try {
    await sendDeviceCommand(deviceId);
    firedIds.value = new Set([...firedIds.value, deviceId]);
    setTimeout(() => {
      firedIds.value = new Set([...firedIds.value].filter((id) => id !== deviceId));
    }, 900);
  } catch {
    errorIds.value = new Set([...errorIds.value, deviceId]);
    setTimeout(() => {
      errorIds.value = new Set([...errorIds.value].filter((id) => id !== deviceId));
    }, 1500);
  } finally {
    firingIds.value = new Set([...firingIds.value].filter((id) => id !== deviceId));
  }
};

const firePairDevices = async (deviceId1, deviceId2) => {
  if (firingIds.value.has(deviceId1) || firingIds.value.has(deviceId2)) return;
  firingIds.value = new Set([...firingIds.value, deviceId1, deviceId2]);
  firedIds.value.delete(deviceId1);
  firedIds.value.delete(deviceId2);
  errorIds.value.delete(deviceId1);
  errorIds.value.delete(deviceId2);

  try {
    await Promise.all([
      sendDeviceCommand(deviceId1),
      sendDeviceCommand(deviceId2),
    ]);
    firedIds.value = new Set([...firedIds.value, deviceId1, deviceId2]);
    setTimeout(() => {
      firedIds.value = new Set([...firedIds.value].filter((id) => id !== deviceId1 && id !== deviceId2));
    }, 900);
  } catch {
    errorIds.value = new Set([...errorIds.value, deviceId1, deviceId2]);
    setTimeout(() => {
      errorIds.value = new Set([...errorIds.value].filter((id) => id !== deviceId1 && id !== deviceId2));
    }, 1500);
  } finally {
    firingIds.value = new Set([...firingIds.value].filter((id) => id !== deviceId1 && id !== deviceId2));
  }
};

const fireRaffaleDevice = async (deviceId) => {
  if (firingIds.value.has(deviceId)) return;
  firingIds.value = new Set([...firingIds.value, deviceId]);
  firedIds.value.delete(deviceId);
  errorIds.value.delete(deviceId);

  try {
    // Fire once
    await sendDeviceCommand(deviceId);
    // Wait 2 seconds
    await new Promise(resolve => setTimeout(resolve, 2000));
    // Fire again
    await sendDeviceCommand(deviceId);

    firedIds.value = new Set([...firedIds.value, deviceId]);
    setTimeout(() => {
      firedIds.value = new Set([...firedIds.value].filter((id) => id !== deviceId));
    }, 900);
  } catch {
    errorIds.value = new Set([...errorIds.value, deviceId]);
    setTimeout(() => {
      errorIds.value = new Set([...errorIds.value].filter((id) => id !== deviceId));
    }, 1500);
  } finally {
    firingIds.value = new Set([...firingIds.value].filter((id) => id !== deviceId));
  }
};

// ── Button helpers ─────────────────────────────────
const isDeviceDisabled = (device) => {
  if (device.blocked || device.healthy === false) return true;
  if (isLocked.value) return true;
  if (!store.reservedByMe) return true;
  if (firingIds.value.has(device.id)) return true;
  return false;
};

const deviceBtnClass = (device) => ({
  'device-btn--firing': firingIds.value.has(device.id),
  'device-btn--fired': firedIds.value.has(device.id),
  'device-btn--error': errorIds.value.has(device.id),
  'device-btn--blocked': device.blocked || isLocked.value,
  'device-btn--recording': !!programStore.recording[device.id],
  'device-btn--pair-pending': programStore.programMode && programStore.pairPending?.id === device.id,
  'device-btn--inactive': !store.reservedByMe && !isLocked.value,
});

const iconColor = (device) => {
  if (device.blocked || isLocked.value) return 'rgba(252,129,129,0.5)';
  if (!store.reservedByMe) return 'rgba(255,255,255,0.25)';
  return 'rgba(255,255,255,0.95)';
};

const chipClass = (device) => {
  if (device.blocked || isLocked.value) return 'chip--blocked';
  if (!store.reservedByMe) return 'chip--free';
  if (errorIds.value.has(device.id)) return 'chip--error';
  if (firedIds.value.has(device.id)) return 'chip--fired';
  if (programStore.recording[device.id]) return 'chip--recording';
  if (programStore.programMode && programStore.pairPending?.id === device.id) return 'chip--pending';
  return 'chip--ready';
};

const chipLabel = (device) => {
  if (device.blocked) return 'Gesperrt';
  if (isLocked.value) return 'Notfall';
  if (!store.reservedByMe) return 'Frei';
  if (errorIds.value.has(device.id)) return 'Fehler';
  if (firedIds.value.has(device.id)) return 'Ausgelöst';
  if (programStore.recording[device.id]) return 'Erfasst';
  if (programStore.programMode && programStore.pairPending?.id === device.id) return 'Gewählt';
  return 'Bereit';
};
</script>

<style scoped>
.shooter-remote {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: env(safe-area-inset-top, 0) 0 calc(env(safe-area-inset-bottom, 0px) + 80px);
  min-height: 0;
}

/* ── Header mode toggle ──────────────────────────── */
.header-mode-toggle {
  display: flex;
  gap: 2px;
  background: rgba(255, 255, 255, 0.08);
  border: 1.5px solid rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  overflow: hidden;
  padding: 4px;
  height: 100%;
}

.mode-toggle-btn {
  width: 36px;
  height: 100%;
  padding: 0;
  background: transparent;
  border: none;
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
  font-weight: 800;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
}

.mode-toggle-btn:hover {
  color: rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.05);
}

.mode-toggle-btn:active {
  transform: scale(0.92);
}

.mode-toggle-btn.active {
  background: rgba(79, 195, 247, 0.25);
  border: 1px solid rgba(79, 195, 247, 0.4);
  color: #4fc3f7;
  font-weight: 900;
}

.mode-toggle-btn.is-recording {
  background: rgba(252, 129, 129, 0.25);
  border: 1px solid rgba(252, 129, 129, 0.4);
  color: #fc8181;
  animation: mode-record-pulse 1.5s ease-in-out infinite;
  font-weight: 900;
}

@keyframes mode-record-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

/* ── Header ──────────────────────────────────────── */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: linear-gradient(180deg, rgba(255,255,255,0.02) 0%, transparent 100%);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.back-btn {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  cursor: pointer;
  padding: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  flex-shrink: 0;
  transition: all 0.15s;
  width: 36px;
  height: 36px;
}

.back-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
}

.back-btn:active {
  transform: scale(0.95);
}

.header-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.page-title {
  font-size: 16px;
  font-weight: 700;
  color: #ffffff;
  margin: 0;
  letter-spacing: -0.3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-sub {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.3);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── Header Right (status, mode, group) ─────────── */
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  height: 36px;
}

.header-status {
  position: relative;
  height: 100%;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  height: 100%;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.status-indicator:hover {
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.8);
}

.status-indicator:active {
  transform: scale(0.95);
}

.status-indicator.is-ready {
  border-color: rgba(252, 129, 129, 0.3);
  color: #fc8181;
  background: rgba(252, 129, 129, 0.08);
}

.status-indicator.is-locked {
  border-color: rgba(72, 187, 120, 0.3);
  color: #48bb78;
}

.status-text {
  display: none;
}

@media (min-width: 480px) {
  .status-text {
    display: inline;
  }
}

/* ── Group dropdown ──────────────────────────────── */
.group-dropdown-wrapper {
  position: relative;
}

.group-dropdown-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  height: 36px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.group-dropdown-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.8);
}

.group-dropdown-btn:active {
  transform: scale(0.95);
}

.group-dropdown-btn svg {
  transition: transform 0.2s;
}

.group-dropdown-wrapper.open .group-dropdown-btn svg {
  transform: rotate(90deg);
}

.group-dropdown-menu {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 4px;
  background: rgba(18, 18, 28, 0.95);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  backdrop-filter: blur(20px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  min-width: 160px;
  z-index: 40;
  overflow: hidden;
}

.group-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  background: none;
  border: none;
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
  text-align: left;
}

.group-option:hover {
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.9);
}

.group-option.active {
  background: rgba(79, 195, 247, 0.15);
  color: #4fc3f7;
  border-left: 2px solid #4fc3f7;
  padding-left: 10px;
}

.option-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.option-count {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.35);
  flex-shrink: 0;
}



/* ── Device section ──────────────────────────────── */
.device-section {
  flex: 1;
  padding: 0 16px;
  min-height: 0;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.3);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  margin: 0 0 14px 2px;
}

/* ── States ──────────────────────────────────────── */
.state-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 48px 24px;
}

.state-text {
  font-size: 15px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.3);
  margin: 0;
}

.state-hint {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.18);
  margin: 0;
  text-align: center;
}

/* ── Device grid ─────────────────────────────────── */
.device-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

/* ── Device button ───────────────────────────────── */
.device-btn {
  position: relative;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.06);
  border: 1.5px solid rgba(255, 255, 255, 0.1);
  border-radius: 22px;
  padding: 26px 16px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s, border-color 0.15s, transform 0.1s;
  -webkit-tap-highlight-color: transparent;
}

.device-btn:not(:disabled):hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(79, 195, 247, 0.3);
}

.device-btn:not(:disabled):active {
  transform: scale(0.93);
}

.device-btn:disabled { cursor: not-allowed; }

/* States */
.device-btn--inactive {
  opacity: 0.45;
}

.device-btn--pair-pending {
  background: rgba(72, 187, 120, 0.1) !important;
  border-color: rgba(72, 187, 120, 0.45) !important;
}

.device-btn--fired {
  background: rgba(72, 187, 120, 0.12) !important;
  border-color: rgba(72, 187, 120, 0.4) !important;
  animation: fired-pulse 0.35s ease-out;
}

.device-btn--firing {
  background: rgba(79, 195, 247, 0.1) !important;
  border-color: rgba(79, 195, 247, 0.4) !important;
}

.device-btn--error {
  background: rgba(252, 129, 129, 0.1) !important;
  border-color: rgba(252, 129, 129, 0.4) !important;
}

.device-btn--blocked {
  opacity: 0.45;
}

.device-btn--recording {
  animation: record-flash 500ms ease-out;
  border-color: rgba(79, 195, 247, 0.5) !important;
}

@keyframes fired-pulse {
  0% { transform: scale(1); }
  40% { transform: scale(0.95); }
  100% { transform: scale(1); }
}

@keyframes record-flash {
  0% { background: rgba(79, 195, 247, 0.2); }
  100% { background: rgba(255, 255, 255, 0.06); }
}

/* Glow */
.btn-glow {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: radial-gradient(circle at 50% 40%, rgba(79, 195, 247, 0.18) 0%, transparent 70%);
  opacity: 0;
  transition: opacity 0.3s;
  pointer-events: none;
}

.device-btn--firing .btn-glow,
.device-btn--fired .btn-glow {
  opacity: 1;
}

.device-btn--fired .btn-glow {
  background: radial-gradient(circle at 50% 40%, rgba(72, 187, 120, 0.22) 0%, transparent 70%);
}

/* Icon wrap */
.btn-icon-wrap {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.07);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
  position: relative;
}

.btn-letter {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -1px;
}

.device-btn--firing .btn-icon-wrap {
  background: rgba(79, 195, 247, 0.15);
  animation: icon-pulse 0.7s ease-in-out infinite alternate;
}

.device-btn--fired .btn-icon-wrap { background: rgba(72, 187, 120, 0.15); }
.device-btn--error .btn-icon-wrap { background: rgba(252, 129, 129, 0.15); }
.device-btn--pair-pending .btn-icon-wrap { background: rgba(72, 187, 120, 0.15); }

@keyframes icon-pulse {
  from { transform: scale(0.94); }
  to { transform: scale(1.06); }
}

/* Label */
.btn-label {
  font-size: 14px;
  font-weight: 600;
  color: #ffffff;
  text-align: center;
  line-height: 1.2;
}

/* Status chip */
.btn-status-chip {
  font-size: 11px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 20px;
}

.chip--ready { background: rgba(72,187,120,0.15); color: #48bb78; }
.chip--fired { background: rgba(72,187,120,0.2); color: #68d391; }
.chip--recording { background: rgba(79,195,247,0.15); color: #4fc3f7; }
.chip--pending { background: rgba(72,187,120,0.15); color: #48bb78; }
.chip--error { background: rgba(252,129,129,0.15); color: #fc8181; }
.chip--blocked { background: rgba(252,129,129,0.12); color: rgba(252,129,129,0.7); }
.chip--free { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.35); }

/* ── Solo/Pair bar ────────────────────────────────── */
.solo-pair-bar {
  position: fixed;
  bottom: calc(env(safe-area-inset-bottom, 0px) + 16px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 20;
  width: 60%;
  max-width: 320px;
}

.solo-pair-toggle {
  background: rgba(18, 18, 28, 0.9);
  border: 1.5px solid rgba(255, 255, 255, 0.12);
  border-radius: 14px;
  display: flex;
  overflow: hidden;
  backdrop-filter: blur(16px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
}

.toggle-btn {
  flex: 1;
  padding: 13px 0;
  background: transparent;
  border: none;
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.3);
  cursor: pointer;
  transition: all 0.18s;
}

.toggle-btn.active {
  background: rgba(79, 195, 247, 0.18);
  color: #4fc3f7;
}

.toggle-btn:not(.active):hover {
  background: rgba(255, 255, 255, 0.04);
  color: rgba(255, 255, 255, 0.5);
}

/* ── Tablet+ ─────────────────────────────────────── */
@media (min-width: 600px) {
  .status-card {
    max-width: 560px;
    margin-left: auto;
    margin-right: auto;
  }

  .group-pills {
    max-width: 560px;
    margin-left: auto;
    margin-right: auto;
    padding-left: 24px;
    padding-right: 24px;
  }

  .device-section {
    max-width: 560px;
    margin: 0 auto;
    width: 100%;
    padding: 0 24px;
  }

  .device-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
