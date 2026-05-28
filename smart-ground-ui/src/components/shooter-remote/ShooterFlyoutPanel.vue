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
        <span class="header-title">{{ isRecordingActive && !isOpen ? 'Erfasst' : 'Serien' }}</span>
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
              @click="passeStore.removeStep(0, step.id)"
            >
              <span class="item-code">{{ getStepLabel(step) }}</span>
            </button>
          </div>
        </div>

        <!-- Draft steps (full mode, recording active) -->
        <template v-if="passeStore.passeMode && currentSteps.length > 0 && isOpen">
          <div class="section">
            <span class="section-label">Erfasste Schritte</span>
            <div class="serie-list">
              <div v-for="(step, stepIdx) in currentSteps" :key="step.id" class="serie-item">
                <div class="step-info">
                  <span class="step-index">{{ stepIdx + 1 }}</span>
                  <span class="step-label">
                    {{ stepDisplayLabel(step) }}
                  </span>
                  <span class="step-type-chip" :class="`type-${step.type}`">
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
                  v-for="serie in competitionSerien"
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
                    <span v-if="isSerieCompleted(serie.id)" class="completion-badge">✓ Done</span>
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
            v-if="passenBlocks.length === 0 && competitionSerien.length === 0 && trainingBlocks.length === 0 && userSerien.length === 0 && globalSerien.length === 0"
            class="empty-state"
          >
            <Icons icon="program" :size="32" color="rgba(255,255,255,0.1)" />
            <p>Keine Serien</p>
            <p class="empty-hint">Erstelle Serien in der Erfassungs-Ansicht</p>
          </div>
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
            <button class="btn-save" @click="confirmSaveSerie">
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
          <button class="btn-clear" @click="passeStore.cancelCapture">Abbrechen</button>
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
import { usePasseStore } from '@/stores/passeStore.js';
import { usePlaySessionStore } from '@/stores/playSessionStore.js';
import { useRangeStore } from '@/stores/rangeStore.js';
import { useAuthStore } from '@/stores/authStore.js';
import { useActivePasseStore } from '@/stores/activePasseStore.js';
import Icons from '@/components/Icons.vue';

const router = useRouter();
const store = useShooterRemoteStore();
const passeStore = usePasseStore();
const playStore = usePlaySessionStore();
const rangeStore = useRangeStore();
const authStore = useAuthStore();
const activePasseStore = useActivePasseStore();

const isOpen = ref(false);
const namingMode = ref(false);
const serieNameInput = ref('');
const saveAsRange = ref(false);
const nameInputRef = ref(null);
const expandedSerieId = ref(null);

const isRecordingActive = computed(
  () => store.sessionMode === 'recording' && store.recordingActive && passeStore.passeMode,
);

const currentSteps = computed(() => passeStore.editingSerie[0]?.steps ?? []);

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

const competitionSerien = computed(() => {
  // Placeholder for future API integration
  return [];
});

const trainingBlocks = computed(() =>
  activePasseStore.getBlocksForRange(currentRangeId.value)
    .filter(b => b.instanceType === 'training')
);

const passenBlocks = computed(() =>
  activePasseStore.getBlocksForRange(currentRangeId.value)
    .filter(b => b.instanceType !== 'training')
);

const playBlock = (block) => {
  // Include rotteId from competition context when the block belongs to a competition rotte
  const rotteId = block.rotteId ?? store.competitionContext?.rotteId ?? null;
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
    rotteId,
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
  serieNameInput.value = '';
  saveAsRange.value = false;
  namingMode.value = true;
  nextTick(() => nameInputRef.value?.focus());
};

const confirmSaveSerie = () => {
  const rangeId = store.selectedRangeId;
  const range = rangeStore.ranges.find((r) => r.id === rangeId);
  const ownership = saveAsRange.value ? 'range' : 'user';
  passeStore.saveSerie(serieNameInput.value, rangeId, range?.name ?? null, ownership);
  namingMode.value = false;
  serieNameInput.value = '';
  saveAsRange.value = false;
  expandedSerieId.value = null;
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
