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
        <div
          v-if="isRecordingActive && !isOpen && currentSteps.length > 0"
          class="recording-summary"
        >
          <div class="captured-items">
            <button
              v-for="step in currentSteps"
              :key="step.id"
              class="captured-item"
              :class="{ 'is-raffale': step.type === 'raffale' }"
              :title="getStepTooltip(step)"
              @click="programStore.removeStep(0, step.id)"
            >
              <span class="item-code">{{ getStepLabel(step) }}</span>
            </button>
          </div>
        </div>

        <!-- Draft steps (full mode, recording active) -->
        <template v-if="programStore.programMode && currentSteps.length > 0 && isOpen">
          <div class="section">
            <span class="section-label">Erfasste Schritte</span>
            <div class="ablauf-list">
              <div v-for="(step, stepIdx) in currentSteps" :key="step.id" class="ablauf-item">
                <div class="step-info">
                  <span class="step-index">{{ stepIdx + 1 }}</span>
                  <span class="step-label">
                    {{ stepDisplayLabel(step) }}
                  </span>
                  <span class="step-type-chip" :class="`type-${step.type}`">
                    {{ stepTypeLabel(step.type) }}
                  </span>
                </div>
                <button class="delete-btn" @click="programStore.removeStep(0, step.id)">
                  <Icons icon="x" :size="12" color="rgba(252,129,129,0.8)" />
                </button>
              </div>
            </div>
          </div>
        </template>

        <!-- Pending program highlight (not recording, pendingProgramId set) -->
        <div
          v-if="!isRecordingActive && pendingProgram"
          class="pending-program-section"
        >
          <span class="section-label">Bereit zum Starten</span>
          <div class="pending-program-card">
            <div class="pending-prog-info">
              <Icons icon="program" :size="14" color="#4fc3f7" />
              <span class="pending-prog-name">{{ pendingProgram.name }}</span>
            </div>
            <div class="pending-prog-meta">
              {{ pendingProgram.ablaeufe.length }} Abläufe ·
              {{ throwCount(pendingProgram.ablaeufe) }} Würfe
            </div>
            <div class="pending-prog-actions">
              <button class="play-btn play-btn--pending" @click="playPendingProgram">
                <Icons icon="play" :size="12" color="#fff" />
                Starten
              </button>
              <button class="trash-btn" @click="programStore.clearPendingProgram()">
                <Icons icon="x" :size="13" color="rgba(255,255,255,0.4)" />
              </button>
            </div>
          </div>
        </div>

        <!-- Saved programmes (only when not recording) -->
        <div v-if="!isRecordingActive && filteredPrograms.length > 0" class="section">
          <span class="section-label">Programme</span>
          <div class="programmes-list">
            <div
              v-for="prog in filteredPrograms"
              :key="prog.id"
              class="programme-card"
              :class="{ 'is-pending': prog.id === programStore.pendingProgramId }"
            >
              <div class="prog-header">
                <span class="prog-title">{{ prog.name }}</span>
                <div class="prog-meta">
                  <span>{{ prog.ablaeufe.length }} Abläufe</span>
                  <span>{{ throwCount(prog.ablaeufe) }} Würfe</span>
                </div>
              </div>
              <div class="prog-actions">
                <button class="play-btn" @click="playProgram(prog.id)">
                  <Icons icon="play" :size="12" color="#fff" />
                  Abspielen
                </button>
                <button class="trash-btn" @click="programStore.deleteProgram(prog.id)">
                  <Icons icon="trash" :size="13" color="rgba(252,129,129,0.8)" />
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty state -->
        <div
          v-if="!isRecordingActive && filteredPrograms.length === 0 && !pendingProgram"
          class="empty-state"
        >
          <Icons icon="program" :size="32" color="rgba(255,255,255,0.1)" />
          <p>Keine Programme</p>
          <p class="empty-hint">Erstelle Programme in der Programme-Ansicht</p>
        </div>
      </div>

      <!-- Footer: Naming prompt OR cancel when capturing -->
      <div v-if="programStore.programMode && currentSteps.length > 0" class="flyout-footer">
        <template v-if="namingMode">
          <div class="naming-row">
            <input
              ref="nameInputRef"
              v-model="ablaufNameInput"
              class="ablauf-name-input"
              type="text"
              placeholder="Ablauf benennen…"
              maxlength="40"
              @keyup.enter="confirmSaveAblauf"
              @keyup.escape="namingMode = false"
            />
            <button class="btn-save" @click="confirmSaveAblauf">
              <Icons icon="check" :size="13" color="#48bb78" />
            </button>
            <button class="btn-clear btn-clear--icon" @click="namingMode = false">
              <Icons icon="x" :size="13" color="#fc8181" />
            </button>
          </div>
          <!-- Platz-weit Option: nur für Admin/Owner sichtbar -->
          <label v-if="authStore.isAdminOrOwner()" class="range-ownership-toggle">
            <input v-model="saveAsRange" type="checkbox" />
            <span>Für alle auf diesem Platz</span>
          </label>
        </template>
        <template v-else>
          <button class="btn-clear" @click="programStore.cancelCapture">Abbrechen</button>
          <button class="btn-save" @click="openNamingMode">Speichern</button>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import { useProgramStore } from '@/stores/programStore.js';
import { usePlaySessionStore } from '@/stores/playSessionStore.js';
import { useDeviceStore } from '@/stores/deviceStore.js';
import { useRangeStore } from '@/stores/rangeStore.js';
import { useAuthStore } from '@/stores/authStore.js';
import Icons from '@/components/Icons.vue';

const router = useRouter();
const store = useShooterRemoteStore();
const programStore = useProgramStore();
const playStore = usePlaySessionStore();
const deviceStore = useDeviceStore();
const rangeStore = useRangeStore();
const authStore = useAuthStore();

const isOpen = ref(false);
const namingMode = ref(false);
const ablaufNameInput = ref('');
const saveAsRange = ref(false);
const nameInputRef = ref(null);

const isRecordingActive = computed(
  () => store.sessionMode === 'recording' && store.recordingActive && programStore.programMode,
);

// Immer Ablauf-Index 0 — Erfassen hat nur noch ein Ablauf
const currentSteps = computed(() => programStore.editingAblauf[0]?.steps ?? []);

// Pending program (gesetzt aus Programme-Ansicht → Starten)
const pendingProgram = computed(() =>
  programStore.pendingProgramId
    ? programStore.savedPrograms.find((p) => p.id === programStore.pendingProgramId) ?? null
    : null,
);

// Programme ohne das pending (um Dopplung zu vermeiden)
const filteredPrograms = computed(() =>
  programStore.savedPrograms.filter((p) => p.id !== programStore.pendingProgramId),
);

const togglePanel = () => {
  if (isRecordingActive.value && !isOpen.value) {
    isOpen.value = true;
  } else {
    isOpen.value = !isOpen.value;
  }
};

// ── Naming mode ────────────────────────────────────────────────────────────
const openNamingMode = () => {
  ablaufNameInput.value = '';
  saveAsRange.value = false;
  namingMode.value = true;
  nextTick(() => nameInputRef.value?.focus());
};

const confirmSaveAblauf = () => {
  const rangeId = store.selectedRangeId;
  const range = rangeStore.ranges.find((r) => r.id === rangeId);
  const ownership = saveAsRange.value ? 'range' : 'user';
  programStore.saveAblauf(ablaufNameInput.value, rangeId, range?.name ?? null, ownership);
  namingMode.value = false;
  ablaufNameInput.value = '';
  saveAsRange.value = false;
};

// ── Helpers ────────────────────────────────────────────────────────────────
const rangeDevices = computed(() =>
  deviceStore.devices.filter((d) => d.rangeId === store.selectedRangeId),
);

const activeDevices = computed(() => {
  if (!store.selectedGroupId) return [];
  return rangeDevices.value.filter(
    (d) => (d.groupId ?? encodeURIComponent(d.groupName ?? '')) === store.selectedGroupId,
  );
});

const getLetterForDevice = (deviceId) => {
  const idx = activeDevices.value.findIndex((d) => d.id === deviceId);
  return idx === -1 ? '?' : String.fromCharCode(65 + idx);
};

const throwCount = (segments) => {
  let count = 0;
  for (const seg of segments) {
    for (const s of seg.steps) {
      if (s.type === 'solo') count += 1;
      else if (s.type === 'pair' || s.type === 'a_schuss') count += 2;
      else if (s.type === 'raffale') count += 2;
    }
  }
  return count;
};

const stepDisplayLabel = (step) => {
  if (step.type === 'solo') return step.alias;
  if (step.type === 'raffale') return `${step.alias} (2×)`;
  return `${step.alias1} + ${step.alias2}`;
};

const stepTypeLabel = (type) => {
  const map = { solo: 'Solo', pair: 'Pair', 'a_schuss': 'a.Schuss', raffale: 'Raffale' };
  return map[type] ?? type;
};

const getStepLabel = (step) => {
  if (step.type === 'solo') return getLetterForDevice(step.deviceId);
  if (step.type === 'pair') return `${getLetterForDevice(step.deviceId1)}+${getLetterForDevice(step.deviceId2)}`;
  if (step.type === 'a_schuss') return `${getLetterForDevice(step.deviceId1)}+${getLetterForDevice(step.deviceId2)}`;
  if (step.type === 'raffale') return `${getLetterForDevice(step.deviceId)}×2`;
  return '?';
};

const getStepTooltip = (step) => {
  const labels = { solo: 'Solo', pair: 'Pair', 'a_schuss': 'a. Schuss', raffale: 'Raffale (2×)' };
  return `Klick zum Löschen: ${getStepLabel(step)} (${labels[step.type]})`;
};

// ── Playback ───────────────────────────────────────────────────────────────
const playProgram = (programId) => {
  playStore.playProgramWithScore(programId);
  router.push(`/remote/${store.selectedRangeId}/play`);
};

const playPendingProgram = () => {
  const id = programStore.pendingProgramId;
  if (!id) return;
  programStore.clearPendingProgram();
  playStore.playProgramWithScore(id);
  router.push(`/remote/${store.selectedRangeId}/play`);
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
  padding: 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
  transition: padding 0.2s, gap 0.2s;
}

.flyout-panel.shrunk .flyout-header {
  padding: 10px 6px;
  gap: 0;
  border-bottom-color: rgba(252, 129, 129, 0.15);
}

.flyout-panel.shrunk .header-divider,
.flyout-panel.shrunk .header-title {
  display: none;
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

/* ── Recording summary (shrunk) ──────────────────── */
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

.captured-item.is-raffale {
  border-color: rgba(79, 195, 247, 0.3);
  background: rgba(79, 195, 247, 0.08);
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

/* ── Sections ────────────────────────────────────── */
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

.step-type-chip.type-pair { color: rgba(72,187,120,0.7); background: rgba(72,187,120,0.1); }
.step-type-chip.type-a\.schuss { color: rgba(246,173,85,0.7); background: rgba(246,173,85,0.1); }
.step-type-chip.type-raffale { color: rgba(168,85,247,0.7); background: rgba(168,85,247,0.1); }

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

.delete-btn:hover { background: rgba(252, 129, 129, 0.1); }

/* ── Pending program ─────────────────────────────── */
.pending-program-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pending-program-card {
  background: rgba(79, 195, 247, 0.08);
  border: 1px solid rgba(79, 195, 247, 0.3);
  border-radius: 12px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pending-prog-info {
  display: flex;
  align-items: center;
  gap: 7px;
}

.pending-prog-name {
  font-size: 13px;
  font-weight: 600;
  color: #4fc3f7;
}

.pending-prog-meta {
  font-size: 11px;
  color: rgba(79, 195, 247, 0.5);
}

.pending-prog-actions {
  display: flex;
  gap: 6px;
  align-items: center;
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

.programme-card.is-pending {
  border-color: rgba(79, 195, 247, 0.3);
  background: rgba(79, 195, 247, 0.05);
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

.play-btn:hover { background: rgba(72, 187, 120, 0.22); }

.play-btn--pending {
  background: rgba(79, 195, 247, 0.2);
  border-color: rgba(79, 195, 247, 0.4);
  color: #4fc3f7;
  flex: 1;
}

.play-btn--pending:hover { background: rgba(79, 195, 247, 0.28); }

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

.trash-btn:hover { background: rgba(252, 129, 129, 0.1); }

/* ── Empty state ─────────────────────────────────── */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 12px;
}

.empty-state p {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.2);
  margin: 0;
}

.empty-hint {
  font-size: 11px !important;
  color: rgba(255, 255, 255, 0.13) !important;
  text-align: center;
}

/* ── Footer ──────────────────────────────────────── */
.flyout-footer {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.naming-row {
  display: flex;
  gap: 8px;
  align-items: center;
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

.btn-clear:hover { background: rgba(252, 129, 129, 0.07); }

.btn-clear--icon {
  flex: none;
  width: 36px;
  height: 36px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-save {
  background: rgba(72, 187, 120, 0.2);
  border: 1px solid rgba(72, 187, 120, 0.35);
  color: #48bb78;
}

.btn-save:hover { background: rgba(72, 187, 120, 0.28); }

/* Name input in footer */
.ablauf-name-input {
  flex: 1;
  height: 36px;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  color: #fff;
  font-size: 12px;
  font-family: inherit;
  padding: 0 10px;
  outline: none;
  transition: border-color 0.15s;
}

.ablauf-name-input:focus {
  border-color: rgba(79, 195, 247, 0.5);
}

.ablauf-name-input::placeholder {
  color: rgba(255, 255, 255, 0.3);
}

/* Range-ownership toggle */
.range-ownership-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
  padding: 0 2px;
}

.range-ownership-toggle input[type="checkbox"] {
  width: 14px;
  height: 14px;
  accent-color: #4fc3f7;
  cursor: pointer;
}

.range-ownership-toggle span {
  font-size: 11.5px;
  color: rgba(255, 255, 255, 0.55);
}

.range-ownership-toggle:has(input:checked) span {
  color: #4fc3f7;
}
</style>
