<template>
  <div class="shooter-remote" :class="[`mode--${store.mode}`, { 'session--erfassen': store.sessionMode === 'recording' }]">
    <!-- Header: [← Back + range name (tablet)] [Lock | Notfall] [Mode Badge] -->
    <div class="page-header">
      <!-- Left: back + range name on tablet -->
      <div class="header-left">
        <button v-if="!auth.profile?.assignedRangeId" class="back-btn" @click="goBack">
          <Icons icon="chevronLeft" :size="20" color="rgba(255,255,255,0.8)" />
        </button>
        <span v-if="range?.name" class="range-name">{{ range.name }}</span>
        <span v-if="rotteName" class="rotte-badge">{{ rotteName }}</span>
      </div>

      <!-- Center: Lock + Notfall (truly centered) -->
      <div class="header-center">
        <button class="icon-btn" title="Stand reservieren (Mock)">
          <Icons icon="lock" :size="15" />
        </button>
        <button
          class="emergency-btn"
          :class="{ 'is-locked': isLocked }"
          :title="isLocked ? 'Notfallsperrung aktiv – Klick zum Freigeben' : 'Klick zum Notfall Stop'"
          @click="toggleBlock"
        >
          <Icons :icon="isLocked ? 'unlock' : 'alert'" :size="15" />
          <span>{{ isLocked ? 'Freigeben' : 'Notfall' }}</span>
        </button>
      </div>

      <!-- Right: Mode badge — main indicator, opens mode flyout -->
      <div class="header-right">
        <button
          class="mode-badge-btn"
          :class="modeBadgeClass"
          :aria-expanded="modeDrawerOpen"
          @click="modeDrawerOpen = !modeDrawerOpen"
        >
          <span class="mode-dot" />
          <span class="mode-label">{{ modeBadgeLabel }}</span>
        </button>
      </div>
    </div>

    <!-- Mode flyout (slides down from header) -->
    <Transition name="mode-flyout">
      <div v-if="modeDrawerOpen" class="mode-flyout">
        <button
          class="mode-option"
          :class="{ 'is-active': store.sessionMode !== 'recording' }"
          @click="setSessionMode('throwing')"
        >
          <span class="option-dot option-dot--normal" />
          <span class="option-name">Schiessen</span>
          <span v-if="store.sessionMode !== 'recording'" class="option-tag option-tag--normal">Aktiv</span>
        </button>
        <button
          class="mode-option"
          :class="{ 'is-active': store.sessionMode === 'recording' }"
          @click="setSessionMode('recording')"
        >
          <span class="option-dot option-dot--erfassen" />
          <span class="option-name">Erfassen</span>
          <span v-if="store.sessionMode === 'recording'" class="option-tag option-tag--erfassen">Aktiv</span>
        </button>
        <button class="mode-option mode-option--soon" disabled>
          <span class="option-dot option-dot--verzoegert" />
          <span class="option-name">Verzögert</span>
          <span class="option-tag option-tag--soon">Demnächst</span>
        </button>
      </div>
    </Transition>
    <div v-if="modeDrawerOpen" class="mode-flyout-backdrop" @click="modeDrawerOpen = false" />

    <!-- Device section -->
    <div class="device-section" :class="{ 'is-recording': isRecordingActive }">
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

      <div v-else class="device-grid" :class="`device-grid--${store.mode}`">
        <button
          v-for="(position, idx) in rangePositions"
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
            <span class="btn-position-num">{{ idx + 1 }}</span>
          </div>
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
  modeDrawerOpen.value = false;
});

watch(() => store.mode, () => {
  store.throwPairPending = null;
});

// ── Mode flyout ────────────────────────────────────
const modeDrawerOpen = ref(false);

const setSessionMode = (mode) => {
  store.setSessionMode(mode);
  modeDrawerOpen.value = false;
};

const isRecordingActive = computed(
  () => store.sessionMode === 'recording' && store.recordingActive && passeStore.passeMode,
);

// ── Mode badge ─────────────────────────────────────
const modeBadgeLabel = computed(() =>
  store.sessionMode === 'recording' ? 'Erfassen' : 'Schiessen'
)
const modeBadgeClass = computed(() => ({
  'mode-badge--recording': store.sessionMode === 'recording',
}))

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
/* ── Throw-type color tokens ─────────────────────── */
.mode--solo     { --throw-color: #1D9E75; --throw-tint: rgba(29, 158, 117, 0.18); --throw-glow: rgba(29, 158, 117, 0.22); }
.mode--pair     { --throw-color: #378ADD; --throw-tint: rgba(55, 138, 221, 0.18); --throw-glow: rgba(55, 138, 221, 0.22); }
.mode--a_schuss { --throw-color: #EF9F27; --throw-tint: rgba(239, 159, 39, 0.18);  --throw-glow: rgba(239, 159, 39, 0.22);  }
.mode--raffale  { --throw-color: #7F77DD; --throw-tint: rgba(127, 119, 221, 0.18); --throw-glow: rgba(127, 119, 221, 0.22); }

.shooter-remote {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: env(safe-area-inset-top, 0) 0 calc(env(safe-area-inset-bottom, 0px) + 80px);
  min-height: 0;
}

/* ── Header ──────────────────────────────────────── */
.page-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: linear-gradient(180deg, rgba(255,255,255,0.02) 0%, transparent 100%);
}

/* True 3-zone centering */
.header-left {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.header-center {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

/* Back button */
.back-btn {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  width: 36px;
  height: 36px;
  transition: all 0.15s;
}

.back-btn:hover { background: rgba(255, 255, 255, 0.1); }
.back-btn:active { transform: scale(0.95); }

/* Range name — tablet only */
.range-name {
  display: none;
  font-size: 15px;
  font-weight: 700;
  color: #ffffff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

@media (min-width: 600px) {
  .range-name { display: block; }
}

/* Rotte badge */
.rotte-badge {
  flex-shrink: 0;
  background: var(--sg-accent-tint);
  color: var(--sg-accent);
  border-radius: 12px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

/* Generic icon button (Lock mock) */
.icon-btn {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.4);
  border-radius: 10px;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
}

.icon-btn:hover { background: rgba(255, 255, 255, 0.1); color: rgba(255, 255, 255, 0.7); }
.icon-btn:active { transform: scale(0.95); }

/* Emergency button — always shows text */
.emergency-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0 10px;
  height: 36px;
  border-radius: 10px;
  font-family: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
  background: rgba(252, 129, 129, 0.08);
  border: 1.5px solid rgba(252, 129, 129, 0.3);
  color: #fc8181;
}

.emergency-btn:hover { background: rgba(252, 129, 129, 0.14); }
.emergency-btn:active { transform: scale(0.95); }

.emergency-btn.is-locked {
  background: rgba(72, 187, 120, 0.08);
  border-color: rgba(72, 187, 120, 0.3);
  color: var(--sg-color-success);
}

/* Mode badge button — main session-mode indicator */
.mode-badge-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 12px;
  height: 36px;
  border-radius: 20px;
  /* Schiessen (default): green tint */
  border: 1.5px solid rgba(72, 187, 120, 0.35);
  background: rgba(72, 187, 120, 0.08);
  color: #48BB78;
  font-family: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.mode-badge-btn:hover { background: rgba(72, 187, 120, 0.13); }
.mode-badge-btn:active { transform: scale(0.95); }

.mode-badge-btn.mode-badge--recording {
  border-color: rgba(252, 129, 129, 0.45);
  background: rgba(252, 129, 129, 0.12);
  color: #fc8181;
}

.mode-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  /* Schiessen: solid green */
  background: #48BB78;
}

.mode-badge--recording .mode-dot {
  background: #fc8181;
  animation: mode-dot-pulse 1s ease-in-out infinite;
}

@keyframes mode-dot-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.35; transform: scale(0.65); }
}

/* ── Mode flyout ─────────────────────────────────── */
.mode-flyout {
  position: relative;
  z-index: 50;
  background: rgba(14, 14, 22, 0.98);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(20px);
  padding: 6px 10px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mode-flyout-backdrop {
  position: fixed;
  inset: 0;
  z-index: 40;
}

.mode-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 12px;
  border: none;
  background: transparent;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  transition: background 0.15s;
  text-align: left;
  width: 100%;
}

.mode-option:hover:not(:disabled) { background: rgba(255, 255, 255, 0.05); }
.mode-option.is-active { color: #fff; background: rgba(255, 255, 255, 0.06); }
.mode-option--soon { opacity: 0.4; cursor: not-allowed; }

.option-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.option-dot--normal    { background: #48BB78; }
.option-dot--erfassen  { background: #fc8181; }
.option-dot--verzoegert { background: #FAC775; }

.mode-option.is-active .option-dot--erfassen {
  animation: mode-dot-pulse 1s ease-in-out infinite;
}

.option-name { flex: 1; }

.option-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.45);
}

.option-tag--normal {
  background: rgba(72, 187, 120, 0.15);
  color: #48BB78;
}

.option-tag--erfassen {
  background: rgba(252, 129, 129, 0.15);
  color: #fc8181;
}

.option-tag--soon {
  background: rgba(250, 199, 117, 0.12);
  color: #FAC775;
}

/* Flyout enter/leave animation */
.mode-flyout-enter-active { transition: opacity 0.15s ease, transform 0.15s ease; }
.mode-flyout-leave-active { transition: opacity 0.1s ease, transform 0.1s ease; }
.mode-flyout-enter-from,
.mode-flyout-leave-to   { opacity: 0; transform: translateY(-6px); }

/* ── Device section ──────────────────────────────── */
.device-section {
  flex: 1;
  padding: 0 16px;
  min-height: 0;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.3);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  margin: 0 0 8px 2px;
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
  gap: 8px;
}

/* ── Card borders follow session/view mode ───────── */
/* Schiessen (default): green */
.device-btn:not(:disabled) {
  border-color: rgba(72, 187, 120, 0.35);
}
/* Erfassen: red */
.session--erfassen .device-btn:not(:disabled) {
  border-color: rgba(252, 129, 129, 0.4);
}

/* ── Bottom bar active tab follows throw-type color ─ */
.mode--solo .toggle-btn.active     { background: rgba(29, 158, 117, 0.15); color: #5DCAA5; }
.mode--pair .toggle-btn.active     { background: rgba(55, 138, 221, 0.15); color: #85B7EB; }
.mode--a_schuss .toggle-btn.active { background: rgba(239, 159, 39, 0.15); color: #FAC775; }
.mode--raffale .toggle-btn.active  { background: rgba(127, 119, 221, 0.15); color: #AFA9EC; }

/* Push device grid left when recording shrunk panel is open */
.device-section.is-recording {
  padding-right: 60px;
  transition: padding-right 0.2s ease;
}

/* ── Device button ───────────────────────────────── */
.device-btn {
  position: relative;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.06);
  border: 1.5px solid rgba(255, 255, 255, 0.1);
  border-radius: 18px;
  padding: 12px 10px 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s, border-color 0.15s, transform 0.1s;
  -webkit-tap-highlight-color: transparent;
}

.device-btn:not(:disabled):hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: color-mix(in srgb, var(--sg-accent) 30%, transparent);
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
  background: var(--throw-tint) !important;
  border-color: color-mix(in srgb, var(--throw-color) 50%, transparent) !important;
}

.device-btn--fired {
  background: var(--throw-tint) !important;
  border-color: color-mix(in srgb, var(--throw-color) 40%, transparent) !important;
  animation: fired-pulse 0.35s ease-out;
}

.device-btn--firing {
  background: var(--throw-tint) !important;
  border-color: color-mix(in srgb, var(--throw-color) 40%, transparent) !important;
}

.device-btn--error {
  background: rgba(252, 129, 129, 0.1) !important;
  border-color: rgba(252, 129, 129, 0.4) !important;
}

.device-btn--blocked {
  opacity: 0.45;
}

.device-btn--recording {
  background: transparent !important;
  border-color: color-mix(in srgb, var(--throw-color) 55%, transparent) !important;
}

@keyframes fired-pulse {
  0% { transform: scale(1); }
  40% { transform: scale(0.95); }
  100% { transform: scale(1); }
}

/* Glow */
.btn-glow {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: radial-gradient(circle at 50% 40%, var(--throw-glow) 0%, transparent 70%);
  opacity: 0;
  transition: opacity 0.3s;
  pointer-events: none;
}

.device-btn--firing .btn-glow,
.device-btn--fired .btn-glow {
  opacity: 1;
}

/* Icon wrap */
.btn-icon-wrap {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.07);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
  position: relative;
}

.btn-letter {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.btn-position-num {
  position: absolute;
  bottom: 2px;
  right: 2px;
  font-size: 9px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.25);
  line-height: 1;
  pointer-events: none;
}

.device-btn--firing .btn-icon-wrap {
  background: var(--throw-tint);
  animation: icon-pulse 0.7s ease-in-out infinite alternate;
}

.device-btn--fired .btn-icon-wrap { background: var(--throw-tint); }
.device-btn--error .btn-icon-wrap { background: rgba(252, 129, 129, 0.15); }
.device-btn--pair-pending .btn-icon-wrap { background: var(--throw-tint); }
.device-btn--pair-pending { border-color: color-mix(in srgb, var(--throw-color) 50%, transparent) !important; }

@keyframes icon-pulse {
  from { transform: scale(0.94); }
  to { transform: scale(1.06); }
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
.chip--recording { background: var(--sg-accent-tint); color: var(--sg-accent); }
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
  width: min(90%, 320px);
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
  background: var(--sg-accent-tint);
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
