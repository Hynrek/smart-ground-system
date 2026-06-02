<template>
  <div class="shooter-remote">
    <!-- Header with integrated controls -->
    <div class="page-header">
      <div class="header-left">
        <button v-if="!auth.profile?.assignedRangeId" class="back-btn" @click="goBack">
          <Icons icon="chevronLeft" :size="20" color="rgba(255,255,255,0.8)" />
        </button>
        <div class="header-text">
          <h1 class="page-title">{{ range?.name ?? '…' }}</h1>
          <p v-if="range?.description" class="page-sub">{{ range.description }}</p>
          <span v-if="rotteName" class="rotte-badge">{{ rotteName }}</span>
        </div>
      </div>

      <div class="header-center">
        <!-- Status indicator (emergency stop) -->
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

      </div>

    </div>


    <!-- Device section -->
    <div class="device-section">
      <p v-if="rangePositions.length > 0" class="section-title">
        {{ rangePositions.length }} {{ rangePositions.length === 1 ? 'Position' : 'Positionen' }}
      </p>

      <div v-if="positionsLoading" class="state-center">
        <p class="state-text">Lade Positionen…</p>
      </div>

      <div v-else-if="rangePositions.length === 0" class="state-center">
        <Icons icon="bolt" :size="44" color="rgba(255,255,255,0.1)" />
        <p class="state-text">Keine Positionen konfiguriert</p>
        <p class="state-hint">Bitte einen Administrator kontaktieren.</p>
      </div>

      <div v-else class="device-grid">
        <button
          v-for="position in rangePositions"
          :key="position.id"
          class="device-btn"
          :class="positionBtnClass(position)"
          :disabled="isPositionDisabled(position)"
          @click="handlePositionTap(position)"
        >
          <div class="btn-glow" />
          <div class="btn-icon-wrap">
            <span class="btn-letter" :style="{ color: iconColor(position) }">
              {{ position.label }}
            </span>
          </div>
          <span class="btn-label">{{ position.device?.alias ?? 'Kein Gerät' }}</span>
          <span class="btn-status-chip" :class="chipClass(position)">{{ chipLabel(position) }}</span>
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
            :class="{ active: store.mode === 'a_schuss' }"
            @click="store.setMode('a_schuss')"
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
import { useRouter, useRoute } from 'vue-router';
import { useRangeStore } from '@/stores/rangeStore.js';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import { useActivePasseStore } from '@/stores/activePasseStore.js';
import { usePasseStore } from '@/stores/passeStore.js';
import { sendPositionCommand } from '@/services/rangePositionApi.js';
import { useAuthStore } from '@/stores/authStore.js';
import { fetchRange } from '@/services/rangeApi.js';
import Icons from '@/components/Icons.vue';
import ShooterFlyoutPanel from '@/components/shooter-remote/ShooterFlyoutPanel.vue';

const props = defineProps({ rangeId: { type: String, required: true } });

const router = useRouter();
const route = useRoute();
const rangeStore = useRangeStore();
const store = useShooterRemoteStore();
const auth = useAuthStore();
const passeStore = usePasseStore();
const activePasseStore = useActivePasseStore();

// Optional competition context query params
const rotteId = computed(() => route.query.rotteId ?? null);
const instanceId = computed(() => route.query.instanceId ?? null);

// Derive the rotte's display name from the active instance when context is present
const rotteName = computed(() => {
  if (!rotteId.value || !instanceId.value) return null;
  const inst = activePasseStore.activeInstances.find((i) => i.instanceId === instanceId.value);
  return inst?.rotten?.find((r) => r.rotteId === rotteId.value)?.name ?? null;
});

const positionsLoading = ref(false);

onMounted(async () => {
  // Redirect if this range is assigned to a different user
  const rangeData = await fetchRange(props.rangeId);
  if (rangeData.assignedUserId && rangeData.assignedUserId !== auth.profile?.id) {
    router.replace('/remote');
    return;
  }

  store.ensureReserved(props.rangeId);
  store.setMode('solo');
  // Store competition context so ShooterFlyoutPanel can access it
  store.setCompetitionContext(instanceId.value, rotteId.value);
  // Only fetch from API if positions are not already cached for this range
  if (!rangeStore.positions[props.rangeId]) {
    positionsLoading.value = true;
    try {
      await rangeStore.loadPositions(props.rangeId);
    } finally {
      positionsLoading.value = false;
    }
  }
});

onUnmounted(() => {
  store.releasePlatz();
  store.setCompetitionContext(null, null);
});

watch(() => store.sessionMode, () => {
  store.setMode('solo');
});

// ── Range & positions ──────────────────────────────
const range = computed(() => rangeStore.ranges.find((r) => r.id === props.rangeId));
const isLocked = computed(() => range.value?.locked ?? false);
const rangePositions = computed(() => rangeStore.positions[props.rangeId] ?? []);

// ── Navigation & lock ──────────────────────────────
const goBack = () => {
  store.releasePlatz();
  router.push('/remote');
};

const toggleBlock = async () => {
  if (!range.value) return;
  await rangeStore.setLocked(range.value.id, !isLocked.value);
};

// ── Fire state ─────────────────────────────────────
const firingIds = ref(new Set());
const firedIds = ref(new Set());
const errorIds = ref(new Set());

const handlePositionTap = async (position) => {
  if (isPositionDisabled(position)) return;

  if (store.sessionMode === 'recording' && passeStore.passeMode) {
    passeStore.addStep(position.id, position, position.label);
    return;
  }

  if ((store.mode === 'pair' || store.mode === 'a_schuss') && !passeStore.passeMode) {
    if (!store.throwPairPending) {
      store.throwPairPending = { id: position.id, alias: position.device?.alias ?? position.label };
    } else if (store.throwPairPending.id === position.id) {
      store.throwPairPending = null;
    } else {
      const pendingId = store.throwPairPending.id;
      store.throwPairPending = null;
      await firePairPositions(pendingId, position.id);
      return;
    }
    return;
  }

  if (store.mode === 'raffale' && !passeStore.passeMode) {
    await fireRaffalePosition(position.id);
    return;
  }

  await fireSinglePosition(position.id);
};

const fireSinglePosition = async (positionId) => {
  if (firingIds.value.has(positionId)) return;
  firingIds.value = new Set([...firingIds.value, positionId]);
  firedIds.value.delete(positionId);
  errorIds.value.delete(positionId);
  try {
    await sendPositionCommand(props.rangeId, positionId);
    firedIds.value = new Set([...firedIds.value, positionId]);
    setTimeout(() => {
      firedIds.value = new Set([...firedIds.value].filter((id) => id !== positionId));
    }, 900);
  } catch {
    errorIds.value = new Set([...errorIds.value, positionId]);
    setTimeout(() => {
      errorIds.value = new Set([...errorIds.value].filter((id) => id !== positionId));
    }, 1500);
  } finally {
    firingIds.value = new Set([...firingIds.value].filter((id) => id !== positionId));
  }
};

const firePairPositions = async (posId1, posId2) => {
  if (firingIds.value.has(posId1) || firingIds.value.has(posId2)) return;
  firingIds.value = new Set([...firingIds.value, posId1, posId2]);
  firedIds.value.delete(posId1);
  firedIds.value.delete(posId2);
  errorIds.value.delete(posId1);
  errorIds.value.delete(posId2);
  try {
    await Promise.all([
      sendPositionCommand(props.rangeId, posId1),
      sendPositionCommand(props.rangeId, posId2),
    ]);
    firedIds.value = new Set([...firedIds.value, posId1, posId2]);
    setTimeout(() => {
      firedIds.value = new Set([...firedIds.value].filter((id) => id !== posId1 && id !== posId2));
    }, 900);
  } catch {
    errorIds.value = new Set([...errorIds.value, posId1, posId2]);
    setTimeout(() => {
      errorIds.value = new Set([...errorIds.value].filter((id) => id !== posId1 && id !== posId2));
    }, 1500);
  } finally {
    firingIds.value = new Set([...firingIds.value].filter((id) => id !== posId1 && id !== posId2));
  }
};

const fireRaffalePosition = async (positionId) => {
  if (firingIds.value.has(positionId)) return;
  firingIds.value = new Set([...firingIds.value, positionId]);
  firedIds.value.delete(positionId);
  errorIds.value.delete(positionId);
  try {
    await sendPositionCommand(props.rangeId, positionId);
    await new Promise(resolve => setTimeout(resolve, 2000));
    await sendPositionCommand(props.rangeId, positionId);
    firedIds.value = new Set([...firedIds.value, positionId]);
    setTimeout(() => {
      firedIds.value = new Set([...firedIds.value].filter((id) => id !== positionId));
    }, 900);
  } catch {
    errorIds.value = new Set([...errorIds.value, positionId]);
    setTimeout(() => {
      errorIds.value = new Set([...errorIds.value].filter((id) => id !== positionId));
    }, 1500);
  } finally {
    firingIds.value = new Set([...firingIds.value].filter((id) => id !== positionId));
  }
};

// ── Button helpers ─────────────────────────────────
const isPositionDisabled = (position) => {
  if (!position.device) return true;
  if (position.device.blocked || position.device.healthy === false) return true;
  if (isLocked.value) return true;
  if (!store.reservedByMe) return true;
  if (firingIds.value.has(position.id)) return true;
  return false;
};

const positionBtnClass = (position) => ({
  'device-btn--firing': firingIds.value.has(position.id),
  'device-btn--fired': firedIds.value.has(position.id),
  'device-btn--error': errorIds.value.has(position.id),
  'device-btn--blocked': (position.device?.blocked ?? false) || isLocked.value,
  'device-btn--no-device': !position.device,
  'device-btn--recording': !!passeStore.recording[position.id],
  'device-btn--pair-pending': passeStore.passeMode && passeStore.pairPending?.id === position.id,
  'device-btn--inactive': !store.reservedByMe && !isLocked.value,
});

const iconColor = (position) => {
  if (!position.device) return 'rgba(255,255,255,0.15)';
  if (position.device.blocked || isLocked.value) return 'rgba(252,129,129,0.5)';
  if (!store.reservedByMe) return 'rgba(255,255,255,0.25)';
  return 'rgba(255,255,255,0.95)';
};

const chipClass = (position) => {
  if (!position.device) return 'chip--no-device';
  if (position.device.blocked || isLocked.value) return 'chip--blocked';
  if (!store.reservedByMe) return 'chip--free';
  if (errorIds.value.has(position.id)) return 'chip--error';
  if (firedIds.value.has(position.id)) return 'chip--fired';
  if (passeStore.recording[position.id]) return 'chip--recording';
  if (passeStore.passeMode && passeStore.pairPending?.id === position.id) return 'chip--pending';
  return 'chip--ready';
};

const chipLabel = (position) => {
  if (!position.device) return 'Kein Gerät';
  if (position.device.blocked) return 'Gesperrt';
  if (isLocked.value) return 'Notfall';
  if (!store.reservedByMe) return 'Frei';
  if (errorIds.value.has(position.id)) return 'Fehler';
  if (firedIds.value.has(position.id)) return 'Ausgelöst';
  if (passeStore.recording[position.id]) return 'Erfasst';
  if (passeStore.passeMode && passeStore.pairPending?.id === position.id) return 'Gewählt';
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
  color: var(--sg-accent);
  font-weight: 900;
}

.mode-toggle-btn.is-recording {
  background: rgba(252, 129, 129, 0.25);
  border: 1px solid rgba(252, 129, 129, 0.4);
  color: var(--sg-color-danger-bg);
  animation: mode-record-pulse 1.5s ease-in-out infinite;
  font-weight: 900;
}

@keyframes mode-record-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

/* ── Header ──────────────────────────────────────── */
.page-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: linear-gradient(180deg, rgba(255,255,255,0.02) 0%, transparent 100%);
}

.header-left {
  position: absolute;
  left: 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.header-center {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  height: 36px;
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

.rotte-badge {
  display: inline-block;
  background: rgba(79, 195, 247, 0.15);
  color: var(--sg-accent);
  border-radius: 12px;
  padding: 2px 10px;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
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
  color: var(--sg-color-danger-bg);
  background: rgba(252, 129, 129, 0.08);
}

.status-indicator.is-locked {
  border-color: rgba(72, 187, 120, 0.3);
  color: var(--sg-color-success);
}

.status-text {
  display: none;
}

@media (min-width: 480px) {
  .status-text {
    display: inline;
  }
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

.chip--ready { background: rgba(72,187,120,0.15); color: var(--sg-color-success); }
.chip--fired { background: rgba(72,187,120,0.2); color: var(--sg-color-success); }
.chip--recording { background: rgba(79,195,247,0.15); color: var(--sg-accent); }
.chip--pending { background: rgba(72,187,120,0.15); color: var(--sg-color-success); }
.chip--error { background: rgba(252,129,129,0.15); color: var(--sg-color-danger-bg); }
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
  box-shadow: var(--sg-shadow-md);
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
  color: var(--sg-accent);
}

.toggle-btn:not(.active):hover {
  background: rgba(255, 255, 255, 0.04);
  color: rgba(255, 255, 255, 0.5);
}

/* ── Tablet+ ─────────────────────────────────────── */
@media (min-width: 600px) {
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

.chip--no-device { background: rgba(255,255,255,0.05); color: rgba(255,255,255,0.2); }
.device-btn--no-device { opacity: 0.3; }
</style>
