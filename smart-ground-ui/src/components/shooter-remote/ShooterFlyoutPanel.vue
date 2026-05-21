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
        <span class="header-title">{{ isRecordingActive && !isOpen ? 'Erfasst' : 'Abläufe' }}</span>
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
              :class="`type-${step.type}`"
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

        <!-- Ablauf-centered view (when not recording) -->
        <template v-if="!isRecordingActive && isOpen">
          <!-- Programme Blöcke -->
          <template v-if="programmeBlocks.length > 0">
            <div class="section">
              <span class="section-label">Programme</span>
              <div class="ablaeufe-list">
                <div
                  v-for="block in programmeBlocks"
                  :key="block.blockId"
                  class="ablauf-card"
                >
                  <button class="ablauf-header-btn" @click="playBlock(block)">
                    <div class="block-info">
                      <span class="ablauf-name">{{ block.ablaufAlias }}</span>
                      <span class="block-template-name">{{ block.templateName }}</span>
                    </div>
                    <span class="block-status-badge" :class="`status-${block.status}`">
                      {{ block.status === 'in_progress' ? '◑' : '●' }}
                    </span>
                  </button>
                  <div class="ablauf-actions">
                    <div class="session-meta">
                      {{ block.players.map(p => p.displayName).join(', ') }}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- Offene Wettkämpfe -->
          <template v-if="competitionAblaeufe.length > 0">
            <div class="section">
              <span class="section-label">Offene Wettkämpfe</span>
              <div class="ablaeufe-list">
                <div
                  v-for="ablauf in competitionAblaeufe"
                  :key="ablauf.id"
                  class="ablauf-card"
                  :class="{ expanded: expandedAblaufId === ablauf.id }"
                >
                  <button
                    class="ablauf-header-btn"
                    @click="toggleExpandAblauf(ablauf.id)"
                  >
                    <Icons
                      :icon="expandedAblaufId === ablauf.id ? 'chevronDown' : 'chevronRight'"
                      :size="12"
                      color="rgba(255,255,255,0.4)"
                    />
                    <span class="ablauf-name">{{ ablauf.name }}</span>
                    <span v-if="isAblaufCompleted(ablauf.id)" class="completion-badge">✓ Done</span>
                  </button>
                  <div v-if="expandedAblaufId === ablauf.id" class="ablauf-actions">
                    <button class="action-btn action-play" @click="playAblaufSolo(ablauf)">
                      <Icons icon="play" :size="12" color="#fff" />
                      Als Solo Starten
                    </button>
                    <button class="action-btn action-group" @click="playAblaufGroup(ablauf)">
                      <Icons icon="program" :size="12" color="#fff" />
                      Als Gruppe Starten
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- Offene Trainings -->
          <template v-if="trainingBlocks.length > 0">
            <div class="section">
              <span class="section-label">Offene Trainings</span>
              <div class="ablaeufe-list">
                <div
                  v-for="block in trainingBlocks"
                  :key="block.blockId"
                  class="ablauf-card"
                >
                  <button class="ablauf-header-btn" @click="playBlock(block)">
                    <div class="block-info">
                      <span class="ablauf-name">{{ block.ablaufAlias }}</span>
                      <span class="block-template-name">{{ block.templateName }} — {{ block.programmeName }}</span>
                    </div>
                    <span class="block-status-badge" :class="`status-${block.status}`">
                      {{ block.status === 'in_progress' ? '◑' : '●' }}
                    </span>
                  </button>
                  <div class="ablauf-actions">
                    <div class="session-meta">
                      {{ block.players.map(p => p.displayName).join(', ') }}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- Abläufe from the User -->
          <template v-if="userAblaeufe.length > 0">
            <div class="section">
              <span class="section-label">Meine Abläufe</span>
              <div class="ablaeufe-list">
                <div
                  v-for="ablauf in userAblaeufe"
                  :key="ablauf.id"
                  class="ablauf-card"
                  :class="{ expanded: expandedAblaufId === ablauf.id }"
                >
                  <button
                    class="ablauf-header-btn"
                    @click="toggleExpandAblauf(ablauf.id)"
                  >
                    <Icons
                      :icon="expandedAblaufId === ablauf.id ? 'chevronDown' : 'chevronRight'"
                      :size="12"
                      color="rgba(255,255,255,0.4)"
                    />
                    <span class="ablauf-name">{{ ablauf.name }}</span>
                  </button>
                  <div v-if="expandedAblaufId === ablauf.id" class="ablauf-actions">
                    <button class="action-btn action-play" @click="playAblaufSolo(ablauf)">
                      <Icons icon="play" :size="12" color="#fff" />
                      Als Solo Starten
                    </button>
                    <button class="action-btn action-group" @click="playAblaufGroup(ablauf)">
                      <Icons icon="program" :size="12" color="#fff" />
                      Als Gruppe Starten
                    </button>
                    <button class="action-btn action-edit" @click="editAblauf(ablauf.id)">
                      <Icons icon="edit" :size="12" color="#fbb" />
                      Bearbeiten
                    </button>
                    <button class="action-btn action-remove" @click="deleteAblauf(ablauf.id)">
                      <Icons icon="trash" :size="12" color="#fc8181" />
                      Löschen
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- Global Abläufe -->
          <template v-if="globalAblaeufe.length > 0">
            <div class="section">
              <span class="section-label">Globale Abläufe</span>
              <div class="ablaeufe-list">
                <div
                  v-for="ablauf in globalAblaeufe"
                  :key="ablauf.id"
                  class="ablauf-card"
                  :class="{ expanded: expandedAblaufId === ablauf.id }"
                >
                  <button
                    class="ablauf-header-btn"
                    @click="toggleExpandAblauf(ablauf.id)"
                  >
                    <Icons
                      :icon="expandedAblaufId === ablauf.id ? 'chevronDown' : 'chevronRight'"
                      :size="12"
                      color="rgba(255,255,255,0.4)"
                    />
                    <span class="ablauf-name">{{ ablauf.name }}</span>
                  </button>
                  <div v-if="expandedAblaufId === ablauf.id" class="ablauf-actions">
                    <button class="action-btn action-play" @click="playAblaufSolo(ablauf)">
                      <Icons icon="play" :size="12" color="#fff" />
                      Als Solo Starten
                    </button>
                    <button class="action-btn action-group" @click="playAblaufGroup(ablauf)">
                      <Icons icon="program" :size="12" color="#fff" />
                      Als Gruppe Starten
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- Empty state -->
          <div
            v-if="programmeBlocks.length === 0 && competitionAblaeufe.length === 0 && trainingBlocks.length === 0 && userAblaeufe.length === 0 && globalAblaeufe.length === 0"
            class="empty-state"
          >
            <Icons icon="program" :size="32" color="rgba(255,255,255,0.1)" />
            <p>Keine Abläufe</p>
            <p class="empty-hint">Erstelle Abläufe in der Erfassungs-Ansicht</p>
          </div>
        </template>
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
import { useRangeStore } from '@/stores/rangeStore.js';
import { useAuthStore } from '@/stores/authStore.js';
import { useActiveProgramStore } from '@/stores/activeProgramStore.js';
import Icons from '@/components/Icons.vue';

const router = useRouter();
const store = useShooterRemoteStore();
const programStore = useProgramStore();
const playStore = usePlaySessionStore();
const rangeStore = useRangeStore();
const authStore = useAuthStore();
const activeProgramStore = useActiveProgramStore();

const isOpen = ref(false);
const namingMode = ref(false);
const ablaufNameInput = ref('');
const saveAsRange = ref(false);
const nameInputRef = ref(null);
const expandedAblaufId = ref(null);

const isRecordingActive = computed(
  () => store.sessionMode === 'recording' && store.recordingActive && programStore.programMode,
);

const currentSteps = computed(() => programStore.editingAblauf[0]?.steps ?? []);

const currentRangeId = computed(() => store.selectedRangeId);

// ── Load completed ablaeufe on mount ────────────────────────────────────────
playStore.loadCompletedAblaeufe();

// ── Ablauf categories ──────────────────────────────────────────────────────────
const userAblaeufe = computed(() => {
  return programStore.getUserAblaeufeForRange(currentRangeId.value).map(ablauf => ({
    ...ablauf,
    type: 'user'
  }));
});

const globalAblaeufe = computed(() => {
  return programStore.getGlobalAblaeufe()
    .filter(ablauf => ablauf.rangeId === currentRangeId.value)
    .map(ablauf => ({
      ...ablauf,
      type: 'global'
    }));
});

const competitionAblaeufe = computed(() => {
  // For now, these would come from active competition sessions
  // This is a placeholder for future API integration
  return [];
});

const trainingBlocks = computed(() =>
  activeProgramStore.getBlocksForRange(currentRangeId.value)
    .filter(b => b.instanceType === 'training')
)

const programmeBlocks = computed(() =>
  activeProgramStore.getBlocksForRange(currentRangeId.value)
    .filter(b => b.instanceType !== 'training')
);

const playBlock = (block) => {
  playStore.pendingProgramInfo = {
    ablauf: {
      id: block.ablaufId,
      name: block.ablaufAlias,
      alias: block.ablaufAlias,
      steps: block.steps,
      rangeId: block.rangeId,
      rangeName: block.rangeName,
    },
    rangeId: currentRangeId.value,
    instanceId: block.instanceId,
    blockId: block.blockId,
    players: block.players,
  };
  isOpen.value = false;
  router.push(`/remote/${currentRangeId.value}/play`);
};

const togglePanel = () => {
  if (isRecordingActive.value && !isOpen.value) {
    isOpen.value = true;
  } else {
    isOpen.value = !isOpen.value;
  }
  if (isOpen.value) {
    expandedAblaufId.value = null;
  }
};

const toggleExpandAblauf = (ablaufId) => {
  expandedAblaufId.value = expandedAblaufId.value === ablaufId ? null : ablaufId;
};

const isAblaufCompleted = (ablaufId) => {
  return playStore.isAblaufCompleted(ablaufId);
};

const buildAblaufSegment = (ablauf) => ({
  id: ablauf.id,
  alias: ablauf.name,
  rangeId: ablauf.rangeId,
  rangeName: ablauf.rangeName,
  steps: ablauf.steps,
});

const playAblaufSolo = (ablauf) => {
  const tempProgram = {
    id: `temp_${ablauf.id}`,
    name: ablauf.name,
    ablaeufe: [buildAblaufSegment(ablauf)],
  };
  programStore.savedPrograms.push(tempProgram);
  playStore.playProgramWithScore(tempProgram.id);
  programStore.savedPrograms = programStore.savedPrograms.filter((p) => p.id !== tempProgram.id);
  isOpen.value = false;
  router.push(`/remote/${currentRangeId.value}/play`);
};

const playAblaufGroup = (ablauf) => {
  playStore.setPendingGroupAblaeufe([buildAblaufSegment(ablauf)]);
  isOpen.value = false;
  router.push(`/remote/${currentRangeId.value}/play`);
};

const editAblauf = (ablaufId) => {
  const ablauf = programStore.savedAblaeufe.find(a => a.id === ablaufId);
  if (!ablauf) return;
  programStore.editingId = ablaufId;
  programStore.editingAblauf = [{ id: ablauf.id, alias: ablauf.name, steps: [...ablauf.steps] }];
  programStore.programMode = true;
  isOpen.value = true;
  expandedAblaufId.value = null;
};

const deleteAblauf = (ablaufId) => {
  if (confirm('Diesen Ablauf wirklich löschen?')) {
    programStore.deleteAblauf(ablaufId);
    expandedAblaufId.value = null;
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
  expandedAblaufId.value = null;
};

// ── Helpers ────────────────────────────────────────────────────────────────
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
  if (step.type === 'solo') return step.letter ?? '?';
  if (step.type === 'pair') return `${step.letter1 ?? '?'}+${step.letter2 ?? '?'}`;
  if (step.type === 'a_schuss') return `${step.letter1 ?? '?'}+${step.letter2 ?? '?'}`;
  if (step.type === 'raffale') return `${step.letter ?? '?'}×2`;
  return '?';
};

const getStepTooltip = (step) => {
  const labels = { solo: 'Solo', pair: 'Pair', 'a_schuss': 'a. Schuss', raffale: 'Raffale (2×)' };
  return `Klick zum Löschen: ${getStepLabel(step)} (${labels[step.type]})`;
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
  background: rgba(79, 195, 247, 0.12);
  border: 1px solid rgba(79, 195, 247, 0.25);
  border-radius: 8px;
  padding: 8px;
  min-height: 32px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
  -webkit-tap-highlight-color: transparent;
}

.captured-item:hover {
  background: rgba(79, 195, 247, 0.2);
  border-color: rgba(79, 195, 247, 0.4);
  transform: scale(1.05);
}

.captured-item.type-solo {
  border-color: rgba(79, 195, 247, 0.25);
  background: rgba(79, 195, 247, 0.12);
}

.captured-item.type-solo .item-code {
  color: rgba(79, 195, 247, 0.7);
}

.captured-item.type-pair {
  border-color: rgba(72, 187, 120, 0.25);
  background: rgba(72, 187, 120, 0.12);
}

.captured-item.type-pair .item-code {
  color: rgba(72, 187, 120, 0.7);
}

.captured-item.type-a_schuss {
  border-color: rgba(246, 173, 85, 0.25);
  background: rgba(246, 173, 85, 0.12);
}

.captured-item.type-a_schuss .item-code {
  color: rgba(246, 173, 85, 0.7);
}

.captured-item.type-raffale {
  border-color: rgba(168, 85, 247, 0.25);
  background: rgba(168, 85, 247, 0.12);
}

.captured-item.type-raffale .item-code {
  color: rgba(168, 85, 247, 0.7);
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

/* ── Abläufe list ────────────────────────────────── */
.ablaeufe-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ablauf-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  overflow: hidden;
  transition: all 0.2s;
}

.ablauf-card.expanded {
  background: rgba(79, 195, 247, 0.08);
  border-color: rgba(79, 195, 247, 0.25);
}

.ablauf-header-btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  background: none;
  border: none;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s;
}

.ablauf-header-btn:hover {
  background: rgba(255, 255, 255, 0.05);
}

.ablauf-name {
  font-size: 12px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.8);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
}

.completion-badge {
  font-size: 10px;
  font-weight: 600;
  color: #48bb78;
  background: rgba(72, 187, 120, 0.15);
  padding: 2px 6px;
  border-radius: 8px;
  flex-shrink: 0;
}

.ablauf-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 6px 10px 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.session-meta {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.3);
  padding: 0 10px 4px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 7px 10px;
  border-radius: 8px;
  border: 1px solid;
  font-size: 11px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
  -webkit-tap-highlight-color: transparent;
}

.action-play {
  background: rgba(72, 187, 120, 0.15);
  border-color: rgba(72, 187, 120, 0.3);
  color: #48bb78;
}

.action-play:hover {
  background: rgba(72, 187, 120, 0.22);
}

.action-group {
  background: rgba(168, 85, 247, 0.15);
  border-color: rgba(168, 85, 247, 0.3);
  color: #c084fc;
}

.action-group:hover {
  background: rgba(168, 85, 247, 0.22);
}

.action-edit {
  background: rgba(79, 195, 247, 0.15);
  border-color: rgba(79, 195, 247, 0.3);
  color: #4fc3f7;
}

.action-edit:hover {
  background: rgba(79, 195, 247, 0.22);
}

.action-remove {
  background: rgba(252, 129, 129, 0.12);
  border-color: rgba(252, 129, 129, 0.25);
  color: #fc8181;
}

.action-remove:hover {
  background: rgba(252, 129, 129, 0.2);
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

.block-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
  text-align: left;
}

.block-template-name {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.block-status-badge {
  font-size: 12px;
  flex-shrink: 0;
}

.block-status-badge.status-pending {
  color: rgba(79, 195, 247, 0.5);
}

.block-status-badge.status-in_progress {
  color: rgba(246, 173, 85, 0.8);
}
</style>
