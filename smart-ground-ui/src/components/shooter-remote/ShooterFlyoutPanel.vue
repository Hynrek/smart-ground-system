<template>
  <div class="flyout-wrapper" :class="{ 'is-recording': isRecordingActive }">
    <!-- Overlay (blocks device buttons when panel open) -->
    <div v-if="isOpen && !isRecordingActive" class="flyout-overlay" @click="isOpen = false" />

    <!-- Handle — only in normal (non-recording) mode to open the Serien list -->
    <button
      v-if="!isOpen && !isRecordingActive"
      class="flyout-handle"
      @click="togglePanel"
    >
      <Icons icon="program" :size="15" color="rgba(255,255,255,0.8)" />
      <Icons icon="chevronLeft" :size="11" color="rgba(255,255,255,0.5)" />
    </button>

    <!-- Panel — in shrunk/recording state the panel itself is the tap target -->
    <div
      class="flyout-panel"
      :class="{ open: isOpen, 'shrunk': isRecordingActive && !isOpen }"
      @click="handlePanelClick"
    >
      <!-- Header -->
      <div class="flyout-header">
        <button v-if="isOpen || !isRecordingActive" class="close-btn" @click.stop="togglePanel">
          <Icons
            :icon="isOpen ? 'chevronRight' : 'chevronLeft'"
            :size="14"
            color="rgba(255,255,255,0.6)"
          />
        </button>
        <div v-if="isOpen || !isRecordingActive" class="header-divider" />
        <Icons icon="program" :size="14" color="rgba(255,255,255,0.6)" />
        <span class="header-title">{{ isRecordingActive && !isOpen ? 'Erfasst' : 'Serien' }}</span>
      </div>

      <!-- Scrollable content -->
      <div class="flyout-content" :class="{ 'shrunk-content': isRecordingActive && !isOpen }">

        <!-- Recording summary (shrunk mode) -->
        <div
          v-if="isRecordingActive && !isOpen && currentSteps.length > 0"
          class="recording-summary"
        >
          <!-- Green save affordance: expands into the save view -->
          <button class="shrunk-save-btn" title="Serie speichern" @click.stop="expandToSave">
            <Icons icon="check" :size="16" color="#48bb78" />
          </button>
          <div class="captured-items">
            <button
              v-for="step in shrunkSteps"
              :key="step.id"
              class="captured-item"
              :style="modeBadgeStyle(step.type)"
              :title="getStepTooltip(step)"
              @click.stop="passeStore.removeStep(0, step.id)"
            >
              <span class="delete-x" aria-hidden="true">×</span>
              <span class="item-code">{{ getStepLabel(step) }}</span>
            </button>
          </div>
        </div>

        <!-- Draft steps (full mode, recording active) -->
        <template v-if="passeStore.passeMode && currentSteps.length > 0 && isOpen">
          <div class="section">
            <span class="section-label">{{ passeStore.editingId ? `Bearbeiten: ${editingName}` : 'Erfasste Schritte' }}</span>
            <div class="serie-list">
              <div v-for="(step, stepIdx) in currentSteps" :key="step.id" class="serie-item">
                <div class="step-info">
                  <span class="step-index">{{ stepIdx + 1 }}</span>
                  <span class="step-label">
                    {{ getStepLabel(step) }}
                  </span>
                  <span class="step-type-chip" :style="modeBadgeStyle(step.type)">
                    {{ stepTypeLabel(step.type) }}
                  </span>
                </div>
                <button class="delete-btn" @click="passeStore.removeStep(0, step.id)">
                  <Icons icon="x" :size="12" color="rgba(252,129,129,0.8)" />
                </button>
              </div>
            </div>
          </div>
        </template>

        <!-- Serie-centered view (when not recording) -->
        <template v-if="!isRecordingActive && isOpen">
          <!-- Competition mode: full progress view -->
          <CompetitionFlyoutContent
            v-if="competitionInstance"
            :instance="competitionInstance"
          />

          <!-- Normal mode: existing serien/passen/training content -->
          <template v-else>
          <!-- Passen Blöcke -->
          <template v-if="passenBlocks.length > 0">
            <div class="section">
              <span class="section-label">Passen</span>
              <div class="serien-list">
                <div
                  v-for="block in passenBlocks"
                  :key="block.blockId"
                  class="serie-card"
                >
                  <button class="serie-header-btn" @click="playBlock(block)">
                    <div class="block-info">
                      <span class="serie-name">{{ block.serieAlias }}</span>
                      <span class="block-template-name">{{ block.templateName }}</span>
                    </div>
                    <span class="block-status-badge" :class="`status-${block.status}`">
                      {{ block.status === 'in_progress' ? '◑' : '●' }}
                    </span>
                  </button>
                  <div class="serie-actions">
                    <div class="session-meta">
                      {{ block.players.map(p => p.displayName).join(', ') }}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- Offene Wettkämpfe -->
          <template v-if="competitionSerien.length > 0">
            <div class="section">
              <span class="section-label">Offene Wettkämpfe</span>
              <div class="serien-list">
                <div
                  v-for="item in competitionSerien"
                  :key="`${item.instanceId}-${item.rotteId}`"
                  class="serie-card"
                  data-testid="competition-rotte-card"
                >
                  <button class="serie-header-btn" @click="playCompetitionRotte(item)">
                    <div class="block-info">
                      <span class="rotte-instance-name">{{ item.instanceName }}</span>
                      <span class="serie-name">{{ item.rotteName }} · {{ item.passeName }}</span>
                      <span
                        v-if="item.blocks.find(b => b.status !== 'done')?.serieAlias"
                        class="serie-alias-chip"
                      >{{ item.blocks.find(b => b.status !== 'done')?.serieAlias }}</span>
                    </div>
                    <Icons icon="play" :size="12" color="rgba(79,195,247,0.6)" />
                  </button>
                  <div class="serie-actions">
                    <div class="session-meta">
                      {{ (item.players ?? []).map(p => p.displayName).join(', ') }}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- Stechen -->
          <template v-if="stechenBlocks.length > 0">
            <div class="section">
              <span class="section-label">Stechen</span>
              <div class="serien-list">
                <div
                  v-for="item in stechenBlocks"
                  :key="`${item.instanceId}-${item.blockId}`"
                  class="serie-card"
                  data-testid="stechen-card"
                >
                  <button class="serie-header-btn" @click="playStechen(item)">
                    <div class="block-info">
                      <span class="rotte-instance-name">Platz {{ item.tiePosition }} · Stechen</span>
                      <span class="serie-name">{{ item.serieAlias }}</span>
                    </div>
                    <Icons icon="play" :size="12" color="rgba(79,195,247,0.6)" />
                  </button>
                  <div class="serie-actions">
                    <div class="session-meta">
                      {{ (item.players ?? []).map(p => p.displayName).join(', ') }}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- Offene Trainings -->
          <template v-if="trainingBlocks.length > 0">
            <div class="section">
              <span class="section-label">Offene Trainings</span>
              <div class="serien-list">
                <div
                  v-for="block in trainingBlocks"
                  :key="block.blockId"
                  class="serie-card"
                >
                  <button class="serie-header-btn" @click="playBlock(block)">
                    <div class="block-info">
                      <span class="serie-name">{{ block.serieAlias }}</span>
                      <span class="block-template-name">{{ block.templateName }} — {{ block.passeName }}</span>
                    </div>
                    <span class="block-status-badge" :class="`status-${block.status}`">
                      {{ block.status === 'in_progress' ? '◑' : '●' }}
                    </span>
                  </button>
                  <div class="serie-actions">
                    <div class="session-meta">
                      {{ block.players.map(p => p.displayName).join(', ') }}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- Serien from the User -->
          <template v-if="userSerien.length > 0">
            <div class="section">
              <span class="section-label">Meine Serien</span>
              <div class="serien-list">
                <div
                  v-for="serie in userSerien"
                  :key="serie.id"
                  class="serie-card"
                  :class="{ expanded: expandedSerieId === serie.id }"
                >
                  <button
                    class="serie-header-btn"
                    @click="toggleExpandSerie(serie.id)"
                  >
                    <Icons
                      :icon="expandedSerieId === serie.id ? 'chevronDown' : 'chevronRight'"
                      :size="12"
                      color="rgba(255,255,255,0.4)"
                    />
                    <span class="serie-name">{{ serie.name }}</span>
                  </button>
                  <div v-if="expandedSerieId === serie.id" class="serie-actions">
                    <button class="action-btn action-play" @click="playSerieSolo(serie)">
                      <Icons icon="play" :size="12" color="#fff" />
                      Als Solo Starten
                    </button>
                    <button class="action-btn action-group" @click="playSerieGroup(serie)">
                      <Icons icon="program" :size="12" color="#fff" />
                      Als Gruppe Starten
                    </button>
                    <button class="action-btn action-edit" @click="editSerie(serie.id)">
                      <Icons icon="edit" :size="12" color="#fbb" />
                      Bearbeiten
                    </button>
                    <button class="action-btn action-remove" @click="deleteSerie(serie.id)">
                      <Icons icon="trash" :size="12" color="#fc8181" />
                      Löschen
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- Globale Serien -->
          <template v-if="globalSerien.length > 0">
            <div class="section">
              <span class="section-label">Globale Serien</span>
              <div class="serien-list">
                <div
                  v-for="serie in globalSerien"
                  :key="serie.id"
                  class="serie-card"
                  :class="{ expanded: expandedSerieId === serie.id }"
                >
                  <button
                    class="serie-header-btn"
                    @click="toggleExpandSerie(serie.id)"
                  >
                    <Icons
                      :icon="expandedSerieId === serie.id ? 'chevronDown' : 'chevronRight'"
                      :size="12"
                      color="rgba(255,255,255,0.4)"
                    />
                    <span class="serie-name">{{ serie.name }}</span>
                  </button>
                  <div v-if="expandedSerieId === serie.id" class="serie-actions">
                    <button class="action-btn action-play" @click="playSerieSolo(serie)">
                      <Icons icon="play" :size="12" color="#fff" />
                      Als Solo Starten
                    </button>
                    <button class="action-btn action-group" @click="playSerieGroup(serie)">
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
            v-if="passenBlocks.length === 0 && competitionSerien.length === 0 && stechenBlocks.length === 0 && trainingBlocks.length === 0 && userSerien.length === 0 && globalSerien.length === 0"
            class="empty-state"
          >
            <Icons icon="program" :size="32" color="rgba(255,255,255,0.1)" />
            <p>Keine Serien</p>
            <p class="empty-hint">Erstelle Serien in der Erfassungs-Ansicht</p>
          </div>
          </template>
        </template>
      </div>

      <!-- Footer: Naming prompt OR cancel when capturing -->
      <div v-if="passeStore.passeMode && currentSteps.length > 0" class="flyout-footer">
        <template v-if="namingMode">
          <div class="naming-row">
            <input
              ref="nameInputRef"
              v-model="serieNameInput"
              class="serie-name-input"
              type="text"
              placeholder="Serie benennen…"
              maxlength="40"
              @keyup.enter="confirmSaveSerie"
              @keyup.escape="namingMode = false"
            />
            <button class="btn-save" @click.stop="confirmSaveSerie">
              <Icons icon="check" :size="13" color="#48bb78" />
            </button>
            <button class="btn-clear btn-clear--icon" @click.stop="cancelToInit">
              <Icons icon="x" :size="13" color="#fc8181" />
            </button>
          </div>
          <label v-if="authStore.hasPermission('OPERATE_RANGE')" class="range-ownership-toggle">
            <input v-model="saveAsRange" type="checkbox" />
            <span>Für alle auf diesem Platz</span>
          </label>
        </template>
        <template v-else>
          <button class="btn-clear" @click.stop="cancelToInit">Abbrechen</button>
          <button class="btn-save" @click.stop="openNamingMode">Speichern</button>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import { usePasseStore } from '@/stores/passeStore.js';
import { usePlaySessionStore } from '@/stores/playSessionStore.js';
import { useRangeStore } from '@/stores/rangeStore.js';
import { useAuthStore } from '@/stores/authStore.js';
import { useActivePasseStore } from '@/stores/activePasseStore.js';
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js';
import Icons from '@/components/Icons.vue';
import CompetitionFlyoutContent from '@/components/shooter-remote/CompetitionFlyoutContent.vue';
import { stepModeLabel, stepNotation, modeBadgeStyle } from '@/constants/stepModes.js';

const router = useRouter();
const store = useShooterRemoteStore();
const passeStore = usePasseStore();
const playStore = usePlaySessionStore();
const rangeStore = useRangeStore();
const authStore = useAuthStore();
const activePasseStore = useActivePasseStore();
const competitionEventStore = useCompetitionEventStore();

const isOpen = ref(false);
const namingMode = ref(false);
const serieNameInput = ref('');
const saveAsRange = ref(false);
const nameInputRef = ref(null);
const expandedSerieId = ref(null);

const competitionInstance = computed(() => {
  const ctxId = store.competitionContext?.instanceId
  if (!ctxId) return null
  return competitionEventStore.getCompetitionInstance(ctxId)
})

const isRecordingActive = computed(
  () => store.sessionMode === 'recording' && store.recordingActive && passeStore.passeMode,
);

const currentSteps = computed(() => passeStore.editingSerie[0]?.steps ?? []);

// Shrunk capture column shows the newest step on top (reverse chronological).
const shrunkSteps = computed(() => [...currentSteps.value].reverse());

// Name of the serie currently being edited (empty for a fresh capture).
const editingName = computed(() => passeStore.editingSerie[0]?.alias ?? '');

const currentRangeId = computed(() => store.selectedRangeId);

// ── Load completed serien on mount ───────────────────────────────────────────
playStore.loadCompletedSerien();

// ── Serie categories ───────────────────────────────────────────────────────────
const userSerien = computed(() => {
  return passeStore.getUserSerienForRange(currentRangeId.value).map(serie => ({
    ...serie,
    type: 'user'
  }));
});

const globalSerien = computed(() => {
  return passeStore.getGlobalSerien()
    .filter(serie => serie.rangeId === currentRangeId.value)
    .map(serie => ({
      ...serie,
      type: 'global'
    }));
});

const competitionSerien = computed(() =>
  competitionEventStore.getActiveCompetitionRotten(currentRangeId.value),
);

const stechenBlocks = computed(() =>
  competitionEventStore.getActiveStechenForRange(currentRangeId.value),
);

const trainingBlocks = computed(() =>
  activePasseStore.getBlocksForRange(currentRangeId.value)
    .filter(b => b.instanceType === 'training')
);

// A Stechen run is a passe-type PlayInstance; when the kiosk operator also owns it,
// activePasseStore surfaces it too. Exclude those — they belong in the Stechen section.
const stechenInstanceIds = computed(() => new Set(stechenBlocks.value.map(s => s.instanceId)))

const passenBlocks = computed(() =>
  activePasseStore.getBlocksForRange(currentRangeId.value)
    .filter(b => b.instanceType !== 'training' && b.instanceType !== 'competition'
      && !stechenInstanceIds.value.has(b.instanceId))
);

const playBlock = (block) => {
  playStore.pendingPasseInfo = {
    serie: {
      id: block.serieId,
      name: block.serieAlias,
      alias: block.serieAlias,
      steps: block.steps,
      rangeId: block.rangeId,
      rangeName: block.rangeName,
    },
    rangeId: currentRangeId.value,
    instanceId: block.instanceId,
    blockId: block.blockId,
    players: block.players,
    rotteId: block.rotteId ?? null,
    instanceType: block.instanceType ?? null,
  };
  isOpen.value = false;
  router.push(`/remote/${currentRangeId.value}/play`);
};

const playCompetitionRotte = (item) => {
  competitionEventStore.assignRotteToRange(item.instanceId, item.rotteId, currentRangeId.value);
  const firstBlock = item.blocks.find(b => b.status !== 'done');
  if (!firstBlock) return;
  playStore.pendingPasseInfo = {
    serie: {
      id: firstBlock.serieId,
      name: firstBlock.serieAlias,
      alias: firstBlock.serieAlias,
      steps: firstBlock.steps,
      rangeId: firstBlock.rangeId,
      rangeName: firstBlock.rangeName,
    },
    rangeId: currentRangeId.value,
    instanceId: item.instanceId,
    blockId: firstBlock.blockId,
    players: item.players,
    rotteId: item.rotteId,
    rotteName: item.rotteName,
    serieName: firstBlock.serieAlias ?? null,
    instanceType: 'competition',
  };
  isOpen.value = false;
  router.push(`/remote/${currentRangeId.value}/play`);
};

const playStechen = (item) => {
  playStore.pendingPasseInfo = {
    serie: {
      id: item.serieId,
      name: item.serieAlias,
      alias: item.serieAlias,
      steps: item.steps,
      rangeId: item.rangeId,
      rangeName: item.rangeName,
    },
    rangeId: currentRangeId.value,
    instanceId: item.instanceId,
    blockId: item.blockId,
    players: item.players,
    instanceType: 'stechen',
    sessionId: item.sessionId,
    serieName: item.serieAlias,
  };
  isOpen.value = false;
  router.push(`/remote/${currentRangeId.value}/play`);
};

const handlePanelClick = () => {
  if (isRecordingActive.value && !isOpen.value) togglePanel();
};

const togglePanel = () => {
  if (isRecordingActive.value && !isOpen.value) {
    isOpen.value = true;
  } else {
    isOpen.value = !isOpen.value;
  }
  if (isOpen.value) {
    expandedSerieId.value = null;
  }
};

const toggleExpandSerie = (serieId) => {
  expandedSerieId.value = expandedSerieId.value === serieId ? null : serieId;
};

const isSerieCompleted = (serieId) => {
  return playStore.isSerieCompleted(serieId);
};

const buildSerieSegment = (serie) => ({
  id: serie.id,
  alias: serie.name,
  rangeId: serie.rangeId,
  rangeName: serie.rangeName,
  steps: serie.steps,
});

const playSerieSolo = (serie) => {
  const tempPasse = {
    id: `temp_${serie.id}`,
    name: serie.name,
    serien: [buildSerieSegment(serie)],
  };
  passeStore.savedPassen.push(tempPasse);
  playStore.playPasseWithScore(tempPasse.id, [], currentRangeId.value);
  passeStore.savedPassen = passeStore.savedPassen.filter((p) => p.id !== tempPasse.id);
  isOpen.value = false;
  router.push(`/remote/${currentRangeId.value}/play`);
};

const playSerieGroup = (serie) => {
  playStore.setPendingGroupSerien([buildSerieSegment(serie)]);
  isOpen.value = false;
  router.push(`/remote/${currentRangeId.value}/play`);
};

const editSerie = (serieId) => {
  const serie = passeStore.savedSerien.find(a => a.id === serieId);
  if (!serie) return;
  passeStore.editingId = serieId;
  passeStore.editingSerie = [{ id: serie.id, alias: serie.name, steps: [...serie.steps] }];
  passeStore.passeMode = true;
  // A2: editing only makes sense in capture mode — force the recording session
  // (without startCapture, which would wipe the steps we just loaded).
  store.enterRecordingForEdit();
  isOpen.value = true;
  expandedSerieId.value = null;
};

const deleteSerie = (serieId) => {
  if (confirm('Diese Serie wirklich löschen?')) {
    passeStore.deleteSerie(serieId);
    expandedSerieId.value = null;
  }
};

// ── Naming mode ────────────────────────────────────────────────────────────
const openNamingMode = () => {
  // When editing, prefill the existing name so it stays editable and visible.
  serieNameInput.value = passeStore.editingId ? editingName.value : '';
  saveAsRange.value = false;
  namingMode.value = true;
  nextTick(() => nameInputRef.value?.focus());
};

// Expand the shrunk panel straight into the save view (green save affordance).
const expandToSave = () => {
  isOpen.value = true;
  openNamingMode();
};

const confirmSaveSerie = async () => {
  const rangeId = store.selectedRangeId;
  const range = rangeStore.ranges.find((r) => r.id === rangeId);
  const steps = passeStore.editingSerie[0]?.steps ?? [];
  if (passeStore.editingId) {
    // A1: editing updates the existing serie in place (stable ID), never a copy.
    const name = serieNameInput.value?.trim() || editingName.value;
    await passeStore.updateSerie(passeStore.editingId, name, steps);
    passeStore.cancelCapture();
  } else {
    const ownership = saveAsRange.value ? 'range' : 'user';
    await passeStore.saveSerie(serieNameInput.value, rangeId, range?.name ?? null, ownership);
  }
  namingMode.value = false;
  serieNameInput.value = '';
  saveAsRange.value = false;
  expandedSerieId.value = null;
  // A4: after a successful save, drop back to shooting mode and collapse the panel.
  store.setSessionMode('throwing');
  isOpen.value = false;
};

// A3: cancelling (incl. cancelling the save) clears the capture list and shrinks
// the flyout back to the recording init state — it does not leave recording mode.
const cancelToInit = () => {
  passeStore.clearEditingSteps();
  namingMode.value = false;
  serieNameInput.value = '';
  saveAsRange.value = false;
  isOpen.value = false;
};

// ── Helpers ────────────────────────────────────────────────────────────────
// Step labels, notation, and colors come from the shared stepModes constant so
// Shooter and Admin views stay identical. See constants/stepModes.js.
const stepDisplayLabel = (step) => stepNotation(step, { useAlias: true });

const stepTypeLabel = (type) => stepModeLabel(type);

const getStepLabel = (step) => stepNotation(step);

const getStepTooltip = (step) =>
  `Klick zum Löschen: ${getStepLabel(step)} (${stepModeLabel(step.type)})`;
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

/* In recording/shrunk mode, pull away from header and bottom bar */
.flyout-wrapper.is-recording {
  top: 58px;
  bottom: 74px;
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
  cursor: pointer;
  touch-action: manipulation;
  -webkit-tap-highlight-color: transparent;
}

@media (hover: hover) and (pointer: fine) {
  .flyout-panel.shrunk:hover {
    background: rgba(252, 129, 129, 0.06);
  }
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
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(79, 195, 247, 0.12);
  border: 1px solid rgba(79, 195, 247, 0.25);
  border-radius: 8px;
  padding: 8px;
  min-height: 32px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
  font-family: inherit;
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}

/* Delete indicator: a small red × in the corner signals the bubble is removable. */
.delete-x {
  position: absolute;
  top: -5px;
  right: -5px;
  width: 15px;
  height: 15px;
  border-radius: 50%;
  background: #e24b4a;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  line-height: 14px;
  text-align: center;
  pointer-events: none;
}

/* Green save affordance shown above the capture column in shrunk mode. */
.shrunk-save-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 32px;
  background: rgba(72, 187, 120, 0.18);
  border: 1px solid rgba(72, 187, 120, 0.4);
  border-radius: 8px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
  transition: background 0.15s;
}

.shrunk-save-btn:active { background: rgba(72, 187, 120, 0.3); }

@media (hover: hover) and (pointer: fine) {
  .shrunk-save-btn:hover { background: rgba(72, 187, 120, 0.26); }
}

/* Hover only on true pointer devices — avoids flash-then-snap on touch */
@media (hover: hover) and (pointer: fine) {
  .captured-item:hover {
    background: rgba(79, 195, 247, 0.2);
    border-color: rgba(79, 195, 247, 0.4);
  }
}

.captured-item:active {
  background: rgba(79, 195, 247, 0.22);
  border-color: rgba(79, 195, 247, 0.45);
}

.item-code {
  font-size: 12px;
  font-weight: 700;
  color: inherit;
  letter-spacing: -0.5px;
  text-align: center;
  word-spacing: 9999px;
  line-height: 1.5;
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

/* ── Serien list ─────────────────────────────────── */
.serien-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.serie-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  overflow: hidden;
  transition: all 0.2s;
}

.serie-card.expanded {
  background: rgba(79, 195, 247, 0.08);
  border-color: rgba(79, 195, 247, 0.25);
}

.serie-header-btn {
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

.serie-header-btn:hover {
  background: rgba(255, 255, 255, 0.05);
}

.serie-name {
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

.serie-actions {
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
.serie-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.serie-item {
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
  padding: 2px 6px;
  border-radius: 10px;
  flex-shrink: 0;
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
.serie-name-input {
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

.serie-name-input:focus {
  border-color: rgba(79, 195, 247, 0.5);
}

.serie-name-input::placeholder {
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

.serie-alias-chip {
  display: inline-block;
  font-size: 10px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 6px;
  padding: 1px 6px;
  margin-top: 2px;
  align-self: flex-start;
}

.rotte-instance-name {
  font-size: 10px;
  color: rgba(79, 195, 247, 0.5);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.4px;
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
