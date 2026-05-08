<template>
  <div class="flyout-wrapper">
    <!-- Overlay (blocks device buttons when panel open) -->
    <div v-if="isOpen && !isRecordingActive" class="flyout-overlay" @click="isOpen = false" />

    <!-- Handle (visible in both throwing and recording modes) -->
    <button
      v-if="!isOpen"
      class="flyout-handle"
      :class="{ 'shrunk-mode': isRecordingActive }"
      @click="togglePanel"
    >
      <Icons icon="program" :size="15" color="rgba(255,255,255,0.8)" />
      <Icons v-if="!isRecordingActive" icon="chevronLeft" :size="11" color="rgba(255,255,255,0.5)" />
    </button>

    <!-- Panel -->
    <div
      class="flyout-panel"
      :class="{ open: isOpen, 'shrunk': isRecordingActive && !isOpen }"
    >
      <!-- Header -->
      <div class="flyout-header">
        <button v-if="isOpen || !isRecordingActive" class="close-btn" @click="togglePanel">
          <Icons
            :icon="isOpen ? 'chevronRight' : 'chevronLeft'"
            :size="14"
            color="rgba(255,255,255,0.6)"
          />
        </button>
        <div v-if="isOpen || !isRecordingActive" class="header-divider" />
        <Icons icon="program" :size="14" color="rgba(255,255,255,0.6)" />
        <span class="header-title">{{ isRecordingActive && !isOpen ? 'Erfasst' : 'Programme' }}</span>
      </div>

      <!-- Scrollable content -->
      <div class="flyout-content" :class="{ 'shrunk-content': isRecordingActive && !isOpen }">
        <!-- Recording summary (shrunk mode) -->
        <div v-if="isRecordingActive && !isOpen && store.ablauf.length > 0" class="recording-summary">
          <div class="captured-items">
            <button
              v-for="step in store.ablauf"
              :key="step.id"
              class="captured-item"
              :class="{ 'is-raffale': step.type === 'raffale' }"
              :title="getStepTooltip(step)"
              @click="store.removeStep(step.id)"
            >
              <span class="item-code">
                {{ getStepLabel(step) }}
              </span>
            </button>
          </div>
        </div>

        <!-- Draft steps (full mode) -->
        <div v-if="store.programMode && store.ablauf.length > 0 && isOpen" class="section">
          <span class="section-label">Entwurf</span>
          <div class="ablauf-list">
            <div v-for="(step, idx) in store.ablauf" :key="step.id" class="ablauf-item">
              <div class="step-info">
                <span class="step-index">{{ idx + 1 }}</span>
                <span class="step-label">
                  {{ step.type === 'solo' ? step.alias : step.type === 'raffale' ? `${step.alias} (2x)` : `${step.alias1} + ${step.alias2}` }}
                </span>
                <span class="step-type-chip" :class="`type-${step.type}`">
                  {{ step.type === 'solo' ? 'Solo' : step.type === 'pair' ? 'Pair' : step.type === 'a.schuss' ? 'a.Schuss' : 'Raffale' }}
                </span>
              </div>
              <button class="delete-btn" @click="store.removeStep(step.id)">
                <Icons icon="x" :size="12" color="rgba(252,129,129,0.8)" />
              </button>
            </div>
          </div>
        </div>

        <!-- Saved programmes (only when not recording) -->
        <div v-if="!isRecordingActive && store.savedPrograms.length > 0" class="section">
          <span class="section-label">Gespeichert</span>
          <div class="programmes-list">
            <div v-for="prog in store.savedPrograms" :key="prog.id" class="programme-card">
              <div class="prog-header">
                <span class="prog-title">{{ prog.name }}</span>
                <div class="prog-meta">
                  <span>{{ prog.steps.length }} Schritte</span>
                  <span>{{ throwCount(prog.steps) }} Würfe</span>
                </div>
              </div>
              <div class="prog-actions">
                <button class="play-btn" @click="playProgram(prog.id)">
                  <Icons icon="play" :size="12" color="#fff" />
                  Abspielen
                </button>
                <button class="trash-btn" @click="store.deleteProgram(prog.id)">
                  <Icons icon="trash" :size="13" color="rgba(252,129,129,0.8)" />
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty state -->
        <div v-if="!isRecordingActive && store.savedPrograms.length === 0 && !store.programMode" class="empty-state">
          <Icons icon="program" :size="32" color="rgba(255,255,255,0.1)" />
          <p>Keine Programme</p>
        </div>
      </div>

      <!-- Footer (save/clear when capturing) -->
      <div v-if="store.programMode && store.ablauf.length > 0" class="flyout-footer">
        <button class="btn-clear" @click="store.clearAblauf">Leeren</button>
        <button class="btn-save" @click="store.saveProgram()">Speichern</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import { useDeviceStore } from '@/stores/deviceStore.js';
import { useRangeStore } from '@/stores/rangeStore.js';
import Icons from '@/components/Icons.vue';

const router = useRouter();
const store = useShooterRemoteStore();
const deviceStore = useDeviceStore();
const rangeStore = useRangeStore();
const isOpen = ref(false);

const isRecordingActive = computed(
  () => store.sessionMode === 'recording' && store.recordingActive && store.programMode,
);

const togglePanel = () => {
  if (isRecordingActive.value && !isOpen.value) {
    isOpen.value = true;
  } else {
    isOpen.value = !isOpen.value;
  }
};

const range = computed(() =>
  rangeStore.ranges.find((r) => r.id === store.selectedRangeId),
);

const rangeDevices = computed(() =>
  deviceStore.devices.filter((d) => d.rangeId === store.selectedRangeId),
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

const activeDevices = computed(() => {
  if (!store.selectedGroupId) return [];
  return rangeDevices.value.filter(
    (d) => (d.groupId ?? encodeURIComponent(d.groupName ?? '')) === store.selectedGroupId,
  );
});

const getLetterForDevice = (deviceId) => {
  const idx = activeDevices.value.findIndex((d) => d.id === deviceId);
  if (idx === -1) return '?';
  return String.fromCharCode(65 + idx);
};

const throwCount = (steps) => {
  let count = 0;
  for (const s of steps) {
    if (s.type === 'solo') count += 1;
    else if (s.type === 'pair' || s.type === 'a.schuss') count += 2;
    else if (s.type === 'raffale') count += 2; // Two triggers of same device
  }
  return count;
};

const getStepLabel = (step) => {
  if (step.type === 'solo') return getLetterForDevice(step.deviceId);
  if (step.type === 'pair') return `${getLetterForDevice(step.deviceId1)}+${getLetterForDevice(step.deviceId2)}`;
  if (step.type === 'a.schuss') return `${getLetterForDevice(step.deviceId1)}+${getLetterForDevice(step.deviceId2)}`;
  if (step.type === 'raffale') return `${getLetterForDevice(step.deviceId)}×2`;
  return '?';
};

const getStepTooltip = (step) => {
  const labels = {
    solo: 'Solo',
    pair: 'Pair',
    'a.schuss': 'a. Schuss',
    raffale: 'Raffale (2x mit 1 Sek Verzögerung)',
  };
  const label = getStepLabel(step);
  return `Klick zum Löschen: ${label} (${labels[step.type]})`;
};

const playProgram = (programId) => {
  const sessionId = store.createPlaySession(programId);
  if (sessionId) {
    router.push(`/remote/${store.selectedRangeId}/play/${sessionId}`);
  }
};
</script>

<style scoped>
.flyout-wrapper {
  position: fixed;
  right: 0;
  top: 0;
  bottom: 0;
  width: 280px;
  pointer-events: none;
  z-index: 30;
  /* Panel slides in from the right without affecting layout when closed */
}

/* ── Overlay ─────────────────────────────────────── */
.flyout-overlay {
  position: fixed;
  left: 0;
  top: 0;
  right: 280px;
  bottom: 0;
  background: rgba(0, 0, 0, 0.2);
  pointer-events: auto;
  z-index: 25;
  /* Overlay only covers device area on left, allows panel interaction on right */
}

/* ── Handle ──────────────────────────────────────── */
.flyout-handle {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 32px;
  height: 56px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-right: none;
  border-radius: 10px 0 0 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  cursor: pointer;
  pointer-events: auto;
  transition: background 0.15s, width 0.2s;
  backdrop-filter: blur(8px);
}

.flyout-handle:hover {
  background: rgba(255, 255, 255, 0.15);
}

.flyout-handle.shrunk-mode {
  background: rgba(252, 129, 129, 0.12);
  border-color: rgba(252, 129, 129, 0.35);
  width: 52px;
  right: 52px;
}

/* ── Panel ───────────────────────────────────────── */
.flyout-panel {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 280px;
  background: rgba(18, 18, 28, 0.96);
  border-left: 1px solid rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
  display: flex;
  flex-direction: column;
  transform: translateX(100%);
  transition: transform 220ms ease-out, width 0.2s ease-out;
  pointer-events: auto;
}

.flyout-panel.open {
  transform: translateX(0);
  transition: transform 180ms ease-in;
}

.flyout-panel.shrunk {
  transform: translateX(0);
  width: 52px;
  border-left: 1px solid rgba(252, 129, 129, 0.25);
}

/* ── Header ──────────────────────────────────────── */
.flyout-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 14px 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
  transition: padding 0.2s, gap 0.2s;
}

.flyout-panel.shrunk .flyout-header {
  padding: 10px 6px;
  gap: 0;
  border-bottom-color: rgba(252, 129, 129, 0.15);
}

.flyout-panel.shrunk .close-btn {
  flex: 1;
}

.flyout-panel.shrunk .header-divider,
.flyout-panel.shrunk .header-title {
  display: none;
}

.flyout-panel.shrunk .flyout-header svg {
  width: 14px;
  height: 14px;
}

.close-btn {
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 2px;
  border-radius: 6px;
  transition: background 0.15s;
}

.close-btn:hover {
  background: rgba(255, 255, 255, 0.07);
}

.header-divider {
  width: 1px;
  height: 16px;
  background: rgba(255, 255, 255, 0.08);
}

.header-title {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.7);
}

/* ── Scrollable content ──────────────────────────── */
.flyout-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  transition: padding 0.2s, gap 0.2s;
}

.flyout-content.shrunk-content {
  padding: 8px 6px;
  gap: 6px;
  overflow-x: hidden;
}

/* ── Recording summary ───────────────────────────── */
.recording-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.captured-items {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.captured-item {
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(252, 129, 129, 0.12);
  border: 1px solid rgba(252, 129, 129, 0.25);
  border-radius: 8px;
  padding: 8px;
  min-height: 32px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
  -webkit-tap-highlight-color: transparent;
}

.captured-item:hover {
  background: rgba(252, 129, 129, 0.2);
  border-color: rgba(252, 129, 129, 0.4);
  transform: scale(1.05);
}

.captured-item:active {
  transform: scale(0.95);
}

.captured-item.is-raffale {
  border-color: rgba(79, 195, 247, 0.3);
  background: rgba(79, 195, 247, 0.08);
}

.captured-item.is-raffale:hover {
  background: rgba(79, 195, 247, 0.15);
  border-color: rgba(79, 195, 247, 0.4);
}

.captured-item.is-raffale .item-code {
  color: #4fc3f7;
}

.item-code {
  font-size: 12px;
  font-weight: 700;
  color: #fc8181;
  letter-spacing: -0.5px;
  text-align: center;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-label {
  font-size: 10px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.25);
  text-transform: uppercase;
  letter-spacing: 0.7px;
}

.flyout-panel.shrunk .section-label {
  display: none;
}

/* ── Draft steps ─────────────────────────────────── */
.ablauf-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ablauf-item {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 8px 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.step-info {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.step-index {
  font-size: 10px;
  font-weight: 700;
  color: rgba(79, 195, 247, 0.6);
  width: 14px;
  flex-shrink: 0;
}

.step-label {
  font-size: 12px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.8);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-type-chip {
  font-size: 9px;
  font-weight: 600;
  color: rgba(79, 195, 247, 0.7);
  background: rgba(79, 195, 247, 0.1);
  padding: 2px 6px;
  border-radius: 10px;
  flex-shrink: 0;
}

.step-type-chip.type-pair {
  color: rgba(72, 187, 120, 0.7);
  background: rgba(72, 187, 120, 0.1);
}

.step-type-chip.type-a\.schuss {
  color: rgba(246, 173, 85, 0.7);
  background: rgba(246, 173, 85, 0.1);
}

.step-type-chip.type-raffale {
  color: rgba(168, 85, 247, 0.7);
  background: rgba(168, 85, 247, 0.1);
}

.delete-btn {
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 3px;
  border-radius: 6px;
  transition: background 0.15s;
  flex-shrink: 0;
}

.delete-btn:hover {
  background: rgba(252, 129, 129, 0.1);
}

/* ── Saved programmes ────────────────────────────── */
.programmes-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.programme-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.prog-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.prog-title {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.85);
}

.prog-meta {
  display: flex;
  gap: 10px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.3);
}

.prog-actions {
  display: flex;
  gap: 8px;
}

.play-btn {
  flex: 1;
  background: rgba(72, 187, 120, 0.15);
  border: 1px solid rgba(72, 187, 120, 0.3);
  color: #48bb78;
  border-radius: 8px;
  padding: 7px 10px;
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  transition: background 0.15s;
}

.play-btn:hover {
  background: rgba(72, 187, 120, 0.22);
}

.trash-btn {
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
  border-radius: 8px;
  transition: background 0.15s;
}

.trash-btn:hover {
  background: rgba(252, 129, 129, 0.1);
}

/* ── Empty state ─────────────────────────────────── */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 32px 12px;
}

.empty-state p {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.2);
  margin: 0;
}

/* ── Footer ──────────────────────────────────────── */
.flyout-footer {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.flyout-panel.shrunk .flyout-footer {
  display: none;
}

.btn-clear,
.btn-save {
  flex: 1;
  border-radius: 10px;
  padding: 9px;
  font-size: 12px;
  font-weight: 700;
  font-family: inherit;
  cursor: pointer;
  transition: opacity 0.15s;
}

.btn-clear {
  background: transparent;
  border: 1px solid rgba(252, 129, 129, 0.4);
  color: #fc8181;
}

.btn-clear:hover {
  background: rgba(252, 129, 129, 0.07);
}

.btn-save {
  background: rgba(72, 187, 120, 0.2);
  border: 1px solid rgba(72, 187, 120, 0.35);
  color: #48bb78;
}

.btn-save:hover {
  background: rgba(72, 187, 120, 0.28);
}
</style>
