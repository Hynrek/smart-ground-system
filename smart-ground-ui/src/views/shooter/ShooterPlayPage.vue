<template>
  <!-- Preview screen ("Serie Anschauen") — precedes setup and play -->
  <div v-if="store.previewMode" class="play-page preview-page">
    <div class="play-topbar">
      <div class="player-info">
        <span class="player-name">Serie anschauen</span>
      </div>
      <div class="topbar-right">
        <div class="score-display">
          <span class="score-label">Gezeigt</span>
          <span class="score-value player-count-val">{{ store.previewFrontier }}/{{ previewTotal }}</span>
        </div>
      </div>
    </div>

    <div class="carousel-area">
      <div class="carousel-section current-section">
        <div class="section-label">Vorschau</div>
        <div
          v-if="store.previewStep"
          class="step-card"
          :class="`is-${store.previewStep.type}`"
          @click="handlePreviewTap"
        >
          <span class="card-badge" :style="modeBadgeStyle(store.previewStep.type)">
            {{ getTypeLabel(store.previewStep.type) }}
          </span>
          <div class="card-label">{{ getStepLetter(store.previewStep) }}</div>
          <p class="hint">Tippen zum Zeigen →</p>
        </div>
        <div v-else class="step-card getroffen-card" @click="store.stopPreview()">
          <span class="card-badge badge-getroffen">Vorschau fertig</span>
          <div class="card-label getroffen-label">Alle gezeigt</div>
          <p class="hint">Tippen zum Beenden →</p>
        </div>
      </div>
    </div>

    <div class="action-bar preview-action-bar">
      <button class="action-btn btn-no-bird" :disabled="!store.previewStep" @click="handlePreviewNoBird">
        <span class="btn-label">No Bird</span>
      </button>
      <button class="action-btn btn-skip" :disabled="!store.previewStep" @click="store.skipPreviewStep()">
        <span class="btn-label">Überspringen</span>
      </button>
      <button class="action-btn btn-stop-preview" @click="store.stopPreview()">
        <span class="btn-label">Stop</span>
      </button>
    </div>
  </div>

  <!-- Group setup modal (shown before play starts) -->
  <div v-else-if="store.showGroupSetup" class="play-page group-setup-page">
    <GroupSetupModal
      v-model:players="groupPlayers"
      v-model:starter-id="starterId"
      v-model:preview-first="previewFirst"
      :title="_isCompetitionMode ? (_rotteName ?? 'Rotte') : 'Gruppe einrichten'"
      :subtitle="_isCompetitionMode ? (_serieName ?? '') : ''"
      :editable="false"
      :allow-add="!_isCompetitionMode"
      :allow-remove="!_isCompetitionMode"
      :allow-reorder="!_isCompetitionMode"
      :allow-starter="!_isCompetitionMode"
      :allow-qr="!_isCompetitionMode"
      :allow-preview="!_isCompetitionMode"
      id-prefix="gp"
      :qr-notice="qrScanNotice"
      @cancel="cancelGroupSetup"
      @confirm="beginGroupPlay"
      @qr-scan="openQrScan"
    />
    <QrScanModal v-if="qrScanOpen" @close="qrScanOpen = false" @resolved="addScannedPlayer" />
  </div>

  <!-- Main play view -->
  <div v-else-if="store.playProg" class="play-page">
    <!-- Top bar -->
    <div class="play-topbar">
      <div class="player-info">
        <button class="back-btn" @click="goBack">
          <Icons icon="chevronLeft" :size="20" color="rgba(255,255,255,0.8)" />
          Zurück
        </button>
        <span v-if="store.currentPlayer" class="player-name">
          {{ store.currentPlayer.displayName }}
        </span>
        <span v-if="playModeBadge" class="mode-badge" :class="playModeBadge.class">
          <span class="mode-dot" />
          {{ playModeBadge.label }}
        </span>
      </div>
      <div class="topbar-right">
        <div v-if="store.isMultiPlayer" class="score-display">
          <span class="score-label">Schütze</span>
          <span class="score-value player-count-val">
            {{ store.currentPlayerIndex + 1 }}/{{ store.sessionPlayers.length }}
          </span>
        </div>
        <div class="score-display">
          <span class="score-label">Punkte</span>
          <span class="score-value">
            {{ currentPlayerPoints }}<span class="score-max">/{{ maxPoints }}</span>
          </span>
        </div>
      </div>
    </div>

    <!-- Vertical Carousel (Top Down: Next → Current → Done) -->
    <div class="carousel-area">
      <!-- Part 1: Next step preview (top) — hidden when next is fertig (Getroffen card takes over) -->
      <div v-if="nextStep && !isFertig(nextStep) && !showFinalScore" class="carousel-section next-section">
        <div class="section-label">
          Nächster Schritt
          <span class="section-label-count">({{ currentFlatStepIndex + 2 }} von {{ totalSteps }})</span>
        </div>
        <div class="preview-card" @click="handleNextStepClick">
          <span class="preview-type">{{ getTypeLabel(nextStep.type) }}</span>
          <span class="preview-label">{{ getStepDisplay(nextStep) }}</span>
        </div>
      </div>

      <!-- Part 2: Current step (center - main focus) -->
      <div v-if="(currentStep || store.isAtProgramEnd) && !showFinalScore" class="carousel-section current-section">
        <div class="section-label">Aktueller Schritt</div>

        <!-- Regular step card -->
        <div
          v-if="currentStep"
          class="step-card"
          :class="[`is-${currentStep.type}`, { 'step-card--listening': gating.phase.value === 'listening' }]"
          @click="handleCurrentStepClick"
        >
          <span class="card-badge" :style="modeBadgeStyle(currentStep.type)">
            {{ getTypeLabel(currentStep.type) }}
          </span>

          <!-- a_schuss: letter per device, active one highlighted -->
          <div v-if="currentStep.type === 'a_schuss'" class="aschuss-display">
            <div class="aschuss-item" :class="{ active: store.playPartialStep === null }">
              <span class="aschuss-main">{{ currentStep.letter1 ?? '?' }}</span>
            </div>
            <span class="separator">{{ stepConnector(currentStep.type) }}</span>
            <div class="aschuss-item" :class="{ active: store.playPartialStep === 'first' }">
              <span class="aschuss-main">{{ currentStep.letter2 ?? '?' }}</span>
            </div>
          </div>

          <template v-else>
            <!-- Gating overlay: countdown ring (delay/totzeit) replaces the hero label -->
            <div
              v-if="gating.phase.value === 'counting' || gating.phase.value === 'totzeit'"
              class="card-gate-ring"
              :style="gating.ringStyle.value"
            >
              <span class="card-gate-num">{{ gating.countdownLabel.value }}</span>
            </div>
            <!-- solo / pair / raffale: position notation is the hero label -->
            <div v-else class="card-label">{{ getStepLetter(currentStep) }}</div>
          </template>

          <!-- Raffale timer -->
          <div v-if="currentStep.type === 'raffale' && store.playRaffaleStarted" class="raffale-bar">
            <div class="progress" :style="{ width: raffaleProgress + '%' }" />
            <span class="label">{{ Math.max(0, 1 - raffaleProgress / 100).toFixed(1) }}s</span>
          </div>

          <p class="hint">{{ getHint(currentStep) }}</p>
        </div>

        <!-- Getroffen card — shown when all steps are done -->
        <div v-else-if="store.isAtProgramEnd" class="step-card getroffen-card" @click="handlePlayerComplete">
          <span class="card-badge badge-getroffen">
            {{ store.isMultiPlayer && store.nextPlayer ? `Schütze ${store.currentPlayerIndex + 1} Fertig` : 'Fertig' }}
          </span>
          <div class="card-label getroffen-label">Serie Fertig</div>
          <p class="hint">
            {{ store.isMultiPlayer && store.nextPlayer
              ? `Weiter zu ${store.nextPlayer.displayName} →`
              : 'Tippen zum Beenden →' }}
          </p>
        </div>
      </div>

      <!-- Part 3: Completed steps (bottom, scrollable) -->
      <div v-if="completedSteps.length > 0 && !showFinalScore" class="carousel-section done-section">
        <div class="section-label">Abgeschlossene Schritte</div>
        <div class="completed-steps-scroll">
          <div
            v-for="(step, idx) in recentCompletedSteps"
            :key="`${step.segIdx}-${step.stepIdx}`"
            class="completed-card"
            :class="[getCompletedCardClass(step.segIdx, step.stepIdx), { 'is-last': idx === recentCompletedSteps.length - 1 }]"
          >
            <span class="step-number">{{ completedSteps.length - recentCompletedSteps.length + idx + 1 }}</span>
            <span class="step-type">{{ getCompletedStepShortLabel(step.segIdx, step.stepIdx) }}</span>
          </div>
        </div>
      </div>

      <!-- Final score screen: solo -->
      <div v-if="showFinalScore && !store.isMultiPlayer" class="final-score-screen">
        <div class="score-card solo-score-card">
          <div class="final-title">Passe Fertig!</div>
          <div class="final-score-value">{{ store.playScore.totalPoints }} Punkte</div>
          <ScoreTable
            v-if="store.sessionPlayers.length > 0"
            :step-states="store.playScore.stepStates"
            :program="store.playProg"
            :players="store.sessionPlayers"
            :editable="true"
            @correct-step="handleCorrectStep"
          />
          <!-- Audit confirmations (competition mode only) -->
          <div v-if="_isCompetitionMode" class="audit-section">
            <div class="audit-title">Bestätigung</div>
            <div v-for="ps in playerFinalScores" :key="ps.player.id" class="audit-row">
              <label class="audit-label">
                <input
                  type="checkbox"
                  class="audit-checkbox"
                  :checked="store.playerConfirmations.get(ps.player.id) ?? false"
                  @change="(e) => e.target.checked ? store.confirmPlayer(ps.player.id) : store.unconfirmPlayer(ps.player.id)"
                />
                <span class="audit-player-name">{{ ps.player.displayName }}</span>
                <span class="audit-score">{{ ps.earnedPts }}/{{ ps.maxPts }}</span>
              </label>
            </div>
          </div>
          <button
            class="btn btn-primary"
            :disabled="_isCompetitionMode && !store.allPlayersConfirmed"
            @click="goBack"
          >
            Beenden
          </button>
        </div>
      </div>

      <!-- Final score screen: group -->
      <div v-else-if="showFinalScore && store.isMultiPlayer" class="final-score-screen">
        <div class="score-card group-score-card">
          <div class="final-title">Gruppe Fertig! 🏆</div>
          <div class="group-scores-list">
            <div
              v-for="(ps, i) in playerFinalScores"
              :key="ps.player.id"
              class="group-score-row"
              :class="{ 'is-top': i === 0 }"
            >
              <span class="rank-badge">{{ i + 1 }}</span>
              <span class="group-player-name">{{ ps.player.displayName }}</span>
              <span class="group-player-points">
                {{ ps.earnedPts }}<span class="group-points-max">/{{ ps.maxPts }}</span>
              </span>
            </div>
          </div>
          <ScoreTable
            v-if="store.sessionPlayers.length > 0"
            :step-states="store.playScore.stepStates"
            :program="store.playProg"
            :players="store.sessionPlayers"
            :editable="true"
            @correct-step="handleCorrectStep"
          />
          <!-- Audit confirmations (competition mode only) -->
          <div v-if="_isCompetitionMode" class="audit-section">
            <div class="audit-title">Bestätigung</div>
            <div v-for="ps in playerFinalScores" :key="ps.player.id" class="audit-row">
              <label class="audit-label">
                <input
                  type="checkbox"
                  class="audit-checkbox"
                  :checked="store.playerConfirmations.get(ps.player.id) ?? false"
                  @change="(e) => e.target.checked ? store.confirmPlayer(ps.player.id) : store.unconfirmPlayer(ps.player.id)"
                />
                <span class="audit-player-name">{{ ps.player.displayName }}</span>
                <span class="audit-score">{{ ps.earnedPts }}/{{ ps.maxPts }}</span>
              </label>
            </div>
          </div>
          <button
            class="btn btn-primary"
            :disabled="_isCompetitionMode && !store.allPlayersConfirmed"
            @click="goBack"
          >
            Beenden
          </button>
        </div>
      </div>

      <!-- Correction picker overlay -->
      <Transition name="correction-fade">
        <div v-if="correctionTarget" class="correction-overlay" @click.self="correctionTarget = null">
          <div class="correction-picker">
            <div class="picker-title">Schritt korrigieren</div>
            <div class="picker-buttons">
              <button class="picker-btn btn-getroffen" @click="applyCorrectionStep(StepState.DONE)">
                Getroffen
              </button>
              <template v-if="correctionTargetIsDouble">
                <button class="picker-btn btn-fail" @click="applyCorrectionStep(StepState.FAILED_A)">{{ correctionFailLabelA }}</button>
                <button class="picker-btn btn-fail" @click="applyCorrectionStep(StepState.FAILED_B)">{{ correctionFailLabelB }}</button>
                <button class="picker-btn btn-fail" @click="applyCorrectionStep(StepState.FAILED_BOTH)">{{ correctionFailLabelBoth }}</button>
              </template>
              <template v-else>
                <button class="picker-btn btn-fail" @click="applyCorrectionStep(StepState.FAILED_BOTH)">{{ correctionFailLabelA }}</button>
              </template>
            </div>
          </div>
        </div>
      </Transition>
    </div>

    <!-- Action buttons at bottom (always visible) -->
    <div class="action-bar">
      <!-- Fail: solo fails immediately, doubles open the fail flyout -->
      <button
        class="action-btn"
        :disabled="!canFail"
        title="Fehler werten"
        @click="onFailTapped"
      >
        <span class="btn-label">Fail</span>
      </button>

      <!-- Treffer: revert the last-fired step to a full hit -->
      <button
        class="action-btn btn-hit-action"
        :disabled="!store.canMarkHit"
        title="Letzten Schritt als Treffer werten"
        @click="handleMarkHit"
      >
        <span class="btn-label">Treffer</span>
      </button>

      <!-- No Bird: retry the last step -->
      <button
        class="action-btn btn-no-bird"
        :disabled="!store.canRetry"
        title="Letzten Schritt wiederholen"
        @click="store.retryStep()"
      >
        <span class="btn-label">No Bird</span>
        <span class="btn-info">Retry</span>
      </button>
    </div>

    <!-- Fail flyout (doubles) — bottom sheet over the action bar -->
    <Transition name="fail-sheet-fade">
      <div v-if="failSheetOpen" class="fail-sheet-overlay" @click.self="failSheetOpen = false">
        <div class="fail-sheet">
          <div class="fail-sheet-handle" />
          <div class="fail-sheet-header">
            <span class="fail-sheet-title">Fail · {{ failSheetNotation }}</span>
            <button class="fail-sheet-close" aria-label="Schließen" @click="failSheetOpen = false">
              <Icons icon="x" :size="16" color="rgba(255,255,255,0.5)" />
            </button>
          </div>
          <div class="fail-sheet-grid">
            <button
              v-for="cell in failSheetCells"
              :key="cell.failType"
              class="fail-cell"
              @click="chooseFail(cell.failType)"
            >
              <span class="fail-cell-label">{{ cell.label }}</span>
              <span class="fail-cell-cost">−{{ cell.cost }}</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Progress dots -->
    <div v-if="store.playProg" class="progress-dots">
      <div
        v-for="(flatIdx) in computeFlatStepCount()"
        :key="flatIdx"
        class="dot"
        :class="getDotClass(flatIdx)"
      />
    </div>

    <!-- Next-shooter overlay -->
    <Transition name="next-shooter-fade">
      <div v-if="showNextShooterOverlay" class="next-shooter-overlay">
        <div class="next-shooter-card">
          <span class="next-shooter-label">Nächster Schütze</span>
          <span class="next-shooter-name">{{ readyPlayer?.displayName }}</span>
          <span class="next-shooter-hint">Bitte schießbereit machen</span>
          <button class="next-shooter-start-btn" @click="confirmNextShooter">
            Starten →
          </button>
        </div>
      </div>
    </Transition>

    <!-- Preview gate: first shooter reached an un-previewed step -->
    <Transition name="next-shooter-fade">
      <div v-if="store.needsPreview" class="next-shooter-overlay preview-gate-overlay">
        <div class="next-shooter-card">
          <span class="next-shooter-label">Vorschau</span>
          <span class="next-shooter-name">Weitere Schritte</span>
          <span class="next-shooter-hint">Nächste Wurfscheiben zeigen, bevor es weitergeht</span>
          <button class="next-shooter-start-btn" @click="store.startPreview()">
            Weitere Schritte anzeigen →
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>


<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { usePlaySessionStore } from '@/stores/playSessionStore.js';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import { useTriggerGating } from '@/composables/useTriggerGating.js';
import { StepState, StepType } from '@/constants/playEnums.js';
import { stepModeLabel, stepNotation, isMultiResultStep, stepFailCells, modeBadgeStyle, stepConnector } from '@/constants/stepModes.js';
import Icons from '@/components/Icons.vue';
import ScoreTable from '@/components/shooter/ScoreTable.vue';
import QrScanModal from '@/components/shooter/QrScanModal.vue';
import GroupSetupModal from '@/components/GroupSetupModal.vue';

const router = useRouter();
const route = useRoute();
const props = defineProps({
  rangeId: { type: String, required: true },
});

const store = usePlaySessionStore();
const remoteStore = useShooterRemoteStore();
const gating = useTriggerGating(remoteStore);

// Transient notice when the mic is blocked during a Rufauslösung arm.
const rufDeniedNotice = ref(false);

const raffaleProgress = ref(0);
const raffaleDelayStart = ref(null);

// ── Next-shooter overlay ──────────────────────────────────────────────────────
const showNextShooterOverlay = ref(false);
const pendingFirstShooter = ref(false);

// Whom the ready overlay is about: the current (first) shooter on group start,
// otherwise the upcoming shooter between turns.
const readyPlayer = computed(() =>
  pendingFirstShooter.value ? store.currentPlayer : store.nextPlayer
);

const confirmNextShooter = () => {
  showNextShooterOverlay.value = false;
  if (pendingFirstShooter.value) {
    pendingFirstShooter.value = false;
    return;
  }
  store.advanceToNextPlayer();
};

// ── Correction picker ─────────────────────────────────────────────────────────
const correctionTarget = ref(null);

// ── Fail flyout (doubles) ───────────────────────────────────────────────────────
const failSheetOpen = ref(false);

const failSheetCells = computed(() => stepFailCells(lastFiredStep.value));
const failSheetNotation = computed(() =>
  lastFiredStep.value ? stepNotation(lastFiredStep.value) : ''
);

// Fail tapped on the bar: solo fails immediately (one outcome); doubles open the sheet.
const onFailTapped = () => {
  if (!canFail.value) return;
  if (lastStepWasADouble.value) {
    failSheetOpen.value = true;
  } else {
    handleFailStep('a');
  }
};

const chooseFail = (failType) => {
  handleFailStep(failType);
  failSheetOpen.value = false;
};

const handleMarkHit = () => {
  store.markLastStepHit();
};

const correctionTargetStep = computed(() => {
  if (!correctionTarget.value) return null;
  const { serieIndex, stepIndex } = correctionTarget.value;
  return store.playProg?.[serieIndex]?.steps[stepIndex] ?? null;
});

const correctionTargetIsDouble = computed(() => {
  const step = correctionTargetStep.value;
  return step ? isMultiResultStep(step.type) : false;
});

const handleCorrectStep = (payload) => {
  correctionTarget.value = payload;
};

const applyCorrectionStep = (newState) => {
  const t = correctionTarget.value;
  if (!t) return;
  store.correctStep(t.playerId, t.serieIndex, t.stepIndex, newState);
  correctionTarget.value = null;
};

if (!store.playProg && !store.showGroupSetup && !store.pendingPasseInfo) {
  router.push({ path: `/remote/${props.rangeId}`, query: route.query });
}

// ── Group setup ───────────────────────────────────────────────────────────────
let _nextPlayerId = 1;
const groupPlayers = ref([{ id: `gp-${_nextPlayerId++}`, displayName: 'Schütze 1' }]);

// Capture block context and stage the serie before clearing pendingPasseInfo
const _blockContext = ref(null);
const _rotteName = ref(null);
const _serieName = ref(null);
const _isCompetitionMode = computed(() => _blockContext.value?.instanceType === 'competition');
if (store.pendingPasseInfo) {
  const info = store.pendingPasseInfo;
  store.setPendingGroupSerien([info.serie]);
  if (info.instanceId && info.blockId) {
    _blockContext.value = { instanceId: info.instanceId, blockId: info.blockId, rotteId: info.rotteId ?? null, instanceType: info.instanceType ?? null, sessionId: info.sessionId ?? null };
  }
  if (info.rotteName) {
    _rotteName.value = info.rotteName;
  }
  if (info.serieName) {
    _serieName.value = info.serieName;
  }
  if (info.players?.length) {
    groupPlayers.value = info.players.map((p, i) => ({
      id: p.id ?? `gp-${i + 1}`,
      displayName: p.displayName,
      userId: p.userId ?? null,
    }));
  }
  store.clearPendingPasse();
}

// ── Reorder + starter marker (non-competition group setup) ─────────────────────
const starterId = ref(null);
const previewFirst = ref(false);

// Default the starter to the first shooter; keep it valid as the list changes.
watch(groupPlayers, (list) => {
  if (!list.length) { starterId.value = null; return; }
  if (!list.some((p) => p.id === starterId.value)) starterId.value = list[0].id;
}, { immediate: true, deep: true });

const starterIndex = computed(() => {
  const idx = groupPlayers.value.findIndex((p) => p.id === starterId.value);
  return idx >= 0 ? idx : 0;
});

// ── QR check-in ───────────────────────────────────────────────────────────────
const qrScanOpen = ref(false);
const qrScanNotice = ref('');

const openQrScan = () => {
  qrScanNotice.value = '';
  qrScanOpen.value = true;
};

const addScannedPlayer = (user) => {
  qrScanOpen.value = false;
  if (groupPlayers.value.some((p) => p.userId === user.userId)) {
    qrScanNotice.value = `${user.displayName} ist bereits in der Gruppe`;
    return;
  }
  groupPlayers.value.push({
    id: `gp-${_nextPlayerId++}`,
    displayName: user.displayName,
    userId: user.userId,
  });
};

const beginGroupPlay = (viewSeriesFirst) => {
  // Always initiate the play (and the starting shooter) up front. When the user
  // opted to preview first, overlay the walkthrough on top of the started play:
  // previewMode wins in the template, so it looks like play hasn't begun. Ending
  // or stopping the preview drops straight into the live play, where the
  // needsPreview gate holds the first shooter at any not-yet-previewed step.
  store.startGroupPlay(
    groupPlayers.value,
    props.rangeId,
    'Platz',
    _blockContext.value?.instanceId ?? null,
    _blockContext.value?.blockId ?? null,
    _blockContext.value?.rotteId ?? null,
    _blockContext.value?.instanceType ?? null,
    _blockContext.value?.sessionId ?? null,
    starterIndex.value,
  );
  if (viewSeriesFirst) {
    store.startPreview();
  } else if (store.isMultiPlayer) {
    pendingFirstShooter.value = true;
    showNextShooterOverlay.value = true;
  }
};

const cancelGroupSetup = () => {
  store.cancelGroupSetup();
  router.push({ path: `/remote/${props.rangeId}`, query: route.query });
};

// ── Preview ("Serie Anschauen") ─────────────────────────────────────────────────
const previewTotal = computed(() => {
  const prog = store.previewProgram;
  return prog ? prog.reduce((sum, seg) => sum + seg.steps.length, 0) : 0;
});

const handlePreviewTap = () => { store.advancePreviewStep(); };
const handlePreviewNoBird = () => { store.retryPreviewStep(); };

// ── Carousel data ─────────────────────────────────────────────────────────────
const fertigStep = { type: 'fertig', alias: 'Fertig' };

const currentStep = computed(() => store.currentStep);

const nextStep = computed(() => {
  const seg = store.currentSerie;
  if (!seg || !store.playProg) return fertigStep;
  if (store.currentStepIndex < seg.steps.length - 1) {
    return seg.steps[store.currentStepIndex + 1];
  }
  if (store.currentSerieIndex < store.playProg.length - 1) {
    return store.playProg[store.currentSerieIndex + 1].steps[0];
  }
  return fertigStep;
});

const completedSteps = computed(() => {
  const completed = [];
  if (!store.playProg) return completed;
  for (let segIdx = 0; segIdx < store.currentSerieIndex; segIdx++) {
    for (let stepIdx = 0; stepIdx < store.playProg[segIdx].steps.length; stepIdx++) {
      completed.push({ segIdx, stepIdx });
    }
  }
  for (let stepIdx = 0; stepIdx < store.currentStepIndex; stepIdx++) {
    completed.push({ segIdx: store.currentSerieIndex, stepIdx });
  }
  return completed;
});

const recentCompletedSteps = computed(() => completedSteps.value.slice(-3));

// ── Progress numbers ──────────────────────────────────────────────────────────
const totalSteps = computed(() => {
  if (!store.playProg) return 0;
  return store.playProg.reduce((sum, seg) => sum + seg.steps.length, 0);
});

const currentFlatStepIndex = computed(() => {
  if (!store.playProg) return 0;
  return (
    store.playProg.slice(0, store.currentSerieIndex).reduce((sum, seg) => sum + seg.steps.length, 0) +
    store.currentStepIndex
  );
});

// ── Max possible points ───────────────────────────────────────────────────────
const maxPoints = computed(() => {
  const playerId = store.currentPlayer?.id;
  return store.playScore.stepStates
    .filter((s) => s.playerId === playerId)
    .reduce((sum, s) => sum + s.pointValue, 0);
});

// Points earned by the current player, accounting for fail deductions
const currentPlayerPoints = computed(() => {
  const playerId = store.currentPlayer?.id;
  if (!playerId) return 0;
  return store.playScore.stepStates
    .filter((s) => s.playerId === playerId)
    .reduce((sum, s) => {
      if (s.state === StepState.PENDING) return sum;
      const deduction =
        s.state === StepState.FAILED_BOTH ? 2
        : (s.state === StepState.FAILED_A || s.state === StepState.FAILED_B) ? 1 : 0;
      return sum + Math.max(0, s.pointValue - deduction);
    }, 0);
});

// Per-player final scores for the group success screen
const playerFinalScores = computed(() =>
  store.sessionPlayers.map((player) => {
    const states = store.playScore.stepStates.filter((s) => s.playerId === player.id);
    const maxPts = states.reduce((sum, s) => sum + s.pointValue, 0);
    const earnedPts = states.reduce((sum, s) => {
      if (s.state === StepState.PENDING) return sum;
      const deduction =
        s.state === StepState.FAILED_BOTH ? 2
        : (s.state === StepState.FAILED_A || s.state === StepState.FAILED_B) ? 1 : 0;
      return sum + Math.max(0, s.pointValue - deduction);
    }, 0);
    return { player, earnedPts, maxPts };
  }).sort((a, b) => b.earnedPts - a.earnedPts)
);

// ── Derived display state ─────────────────────────────────────────────────────
const showFinalScore = computed(() => store.playComplete);

// Mode indicator in the Play top bar — only for the two gated modes.
const playModeBadge = computed(() => {
  if (remoteStore.sessionMode === 'delayed') {
    return { label: 'Verzögert', class: 'mode-badge--delayed' };
  }
  if (remoteStore.sessionMode === 'rufausloesung') {
    return { label: 'Rufauslösung', class: 'mode-badge--rufausloesung' };
  }
  return null;
});

const isFertig = (step) => step?.type === 'fertig';

// Fail buttons are active whenever there is a last fired step and the program
// is not already concluded.
const canFail = computed(() =>
  store.playLastDeviceStep !== null && !store.playComplete
);

// Fail B / Fail A+B only make sense when the last fired step was a multi-target type.
const lastStepWasADouble = computed(() => {
  if (!store.playLastDeviceStep) return false;
  const { serieIdx, stepIdx } = store.playLastDeviceStep;
  const step = store.playProg?.[serieIdx]?.steps[stepIdx];
  return step ? isMultiResultStep(step.type) : false;
});

// The actual step object that was last fired (for dynamic fail button labels)
const lastFiredStep = computed(() => {
  if (!store.playLastDeviceStep) return null;
  const { serieIdx, stepIdx } = store.playLastDeviceStep;
  return store.playProg?.[serieIdx]?.steps[stepIdx] ?? null;
});

// Fail-button label builders — shared by the action bar (last fired step) and
// the score-correction picker (the step being corrected).
const failLabelAFor = (s) => {
  if (!s) return 'Fail 1';
  if (s.type === StepType.SOLO) return `Fail ${s.letter ?? '1'}`;
  if (s.type === StepType.RAFFALE) return `Fail ${s.letter ?? '1'} 1.`;
  return `Fail ${s.letter1 ?? '1'}`;
};

const failLabelBFor = (s) => {
  if (!s) return 'Fail 2';
  if (s.type === StepType.RAFFALE) return `Fail ${s.letter ?? '1'} 2.`;
  return `Fail ${s.letter2 ?? '2'}`;
};

const failLabelBothFor = (s) => {
  if (!s) return 'Fail 1/2';
  if (s.type === StepType.RAFFALE) return `Fail ${s.letter ?? '1'}/2`;
  return `Fail ${s.letter1 ?? '1'}/${s.letter2 ?? '2'}`;
};

// Fail-button label builders are shared with the score-correction picker below.

// Same labels, but for the step targeted by the score-correction picker
const correctionFailLabelA = computed(() => failLabelAFor(correctionTargetStep.value));
const correctionFailLabelB = computed(() => failLabelBFor(correctionTargetStep.value));
const correctionFailLabelBoth = computed(() => failLabelBothFor(correctionTargetStep.value));

// ── Helpers ───────────────────────────────────────────────────────────────────
// Mode labels and notation come from the shared stepModes constant so Shooter
// and Admin views stay identical. See constants/stepModes.js.
const getTypeLabel = (type) => (type === 'fertig' ? 'Fertig' : stepModeLabel(type));

// Letter-based display (main large label on the step card)
const getStepLetter = (step) => {
  if (!step || step.type === 'fertig') return '';
  return stepNotation(step);
};

// Letter-based display for the next-step preview card
const getStepDisplay = (step) => {
  if (step.type === 'fertig') return 'Passe abgeschlossen';
  const hasLetters = step.letter ?? step.letter1;
  return stepNotation(step, { useAlias: !hasLetters });
};

// Short label for completed cards: "Solo – A", "a.Schuss – A → B" etc.
const getCompletedStepShortLabel = (segIdx, stepIdx) => {
  const s = store.playProg?.[segIdx]?.steps[stepIdx];
  if (!s) return '';
  return `${getTypeLabel(s.type)} – ${stepNotation(s)}`;
};

const getHint = (step) => {
  // Gate feedback takes precedence while a countdown / listen is active.
  if (rufDeniedNotice.value) return 'Mikrofon-Zugriff verweigert';
  if (gating.phase.value === 'counting') return 'Verzögerung läuft…';
  if (gating.phase.value === 'totzeit')  return 'Bereitmachen…';
  if (gating.phase.value === 'listening') return 'Rufen zum Auslösen';
  if (step.type === StepType.A_SCHUSS) {
    return store.playPartialStep === null ? 'Erstes Gerät: Tippen' : 'Zweites Gerät: Tippen';
  }
  if (step.type === StepType.RAFFALE) {
    return store.playRaffaleStarted ? 'Warte auf Verzögerung...' : 'Erste Auslösung: Tippen';
  }
  return 'Tippen zum Auslösen →';
};

const getCompletedCardClass = (segIdx, stepIdx) => {
  const step = store.playProg?.[segIdx]?.steps[stepIdx];
  const classes = step?.type ? [`type-${step.type}`] : [];
  const state = store.playScore.stepStates.find(
    (s) => s.playerId === store.currentPlayer?.id && s.serieIndex === segIdx && s.stepIndex === stepIdx
  );
  if (state?.state === StepState.FAILED_A) classes.push('is-failed-a');
  else if (state?.state === StepState.FAILED_B) classes.push('is-failed-b');
  else if (state?.state === StepState.FAILED_BOTH) classes.push('is-failed-full');
  return classes;
};

const computeFlatStepCount = () => {
  if (!store.playProg) return [];
  return Array.from({ length: totalSteps.value }, (_, i) => i);
};

const getDotClass = (flatIdx) => {
  if (!store.playProg) return 'dot--pending';
  if (flatIdx < currentFlatStepIndex.value) return 'dot--completed';
  if (flatIdx === currentFlatStepIndex.value) return 'dot--current';
  return 'dot--pending';
};

// ── Handlers ──────────────────────────────────────────────────────────────────
const handleCurrentStepClick = () => {
  // Preview gate: the first shooter has reached an un-previewed step — the
  // "Weitere Schritte anzeigen" overlay handles it, so swallow the tap.
  if (store.needsPreview) return;
  const step = store.currentStep;
  if (!step) return;
  // Tapping the card while a gate is running aborts it (delay countdown / listening).
  if (gating.phase.value !== 'idle') {
    gating.cancel();
    return;
  }
  rufDeniedNotice.value = false;
  const ids = store.releaseIdsForStep(step, store.playPartialStep);
  gating.arm(ids, () => { store.advancePlayStep(); });
};

// If the browser blocks the mic while arming, abort the listen and notify.
watch(() => gating.micDenied.value, (denied) => {
  if (denied) {
    rufDeniedNotice.value = true;
    gating.cancel();
  }
});

const handleNextStepClick = () => {
  if (isFertig(nextStep.value)) handlePlayerComplete();
};

const handlePlayerComplete = () => {
  if (store.isMultiPlayer && store.nextPlayer) {
    showNextShooterOverlay.value = true;
  } else if (store.isMultiPlayer) {
    store.advanceToNextPlayer();
  } else {
    store.confirmComplete();
  }
};

const handleFailStep = (failType) => {
  // Just record the fail. Like Treffer/No Bird, this must not auto-complete at
  // program end — the "Fertig" card stays up so the operator taps to finish,
  // instead of jumping straight to the score view.
  store.failStep(failType);
};

const goBack = async () => {
  if (store.playComplete) await store.commitResults();
  store.closePlayback();
  router.push({ path: `/remote/${props.rangeId}`, query: route.query });
};

onBeforeUnmount(() => {
  gating.cancel();
  if (previewRaffaleTimeoutId) {
    clearTimeout(previewRaffaleTimeoutId);
    previewRaffaleTimeoutId = null;
  }
  if (!store.playComplete) store.closePlayback();
});

// Cancel any pending gate when play finishes or the active shooter changes,
// so a stale countdown/listen never carries over.
watch(() => store.playComplete, (done) => { if (done) gating.cancel(); });
watch(() => store.currentPlayerIndex, () => { gating.cancel(); });
watch(() => remoteStore.sessionMode, () => { gating.cancel(); });

// Monitor raffale timer
watch(
  () => store.playRaffaleStarted,
  (started) => {
    if (started) {
      raffaleProgress.value = 0;
      raffaleDelayStart.value = Date.now();
      const interval = setInterval(() => {
        const elapsed = Date.now() - raffaleDelayStart.value;
        raffaleProgress.value = Math.min((elapsed / 1000) * 100, 100);
        if (raffaleProgress.value >= 100) {
          clearInterval(interval);
          setTimeout(() => {
            store.completeRaffaleStep();
          }, 500);
        }
      }, 50);
    } else {
      raffaleProgress.value = 0;
    }
  }
);

// Preview raffale: fire the second throw ~1s after the first so the shooter sees
// the real cadence. Mirrors the scored raffale timing (view-driven).
let previewRaffaleTimeoutId = null;
watch(
  () => store.previewRaffaleStarted,
  (started) => {
    if (previewRaffaleTimeoutId) {
      clearTimeout(previewRaffaleTimeoutId);
      previewRaffaleTimeoutId = null;
    }
    if (started) {
      previewRaffaleTimeoutId = setTimeout(() => {
        previewRaffaleTimeoutId = null;
        store.completePreviewRaffaleStep();
      }, 1000);
    }
  }
);
</script>

<style scoped>
.play-page {
  position: fixed;
  inset: 0;
  background: rgba(10, 10, 18, 0.97);
  backdrop-filter: blur(24px);
  display: flex;
  flex-direction: column;
  z-index: 50;
}

/* ── Group setup page ───────────────────────────── */
.group-setup-page {
  background: rgba(10, 10, 18, 0.97);
}

/* ── Top bar ────────────────────────────────────── */
.play-topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
  flex-shrink: 0;
}

.player-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.player-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--sg-text-primary);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.player-count-val {
  font-size: 20px !important;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--sg-accent-tint);
  border: 1px solid color-mix(in srgb, var(--sg-accent) 30%, transparent);
  color: var(--sg-accent);
  border-radius: 10px;
  padding: 9px 16px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
  font-family: inherit;
}

.back-btn:hover {
  background: var(--sg-accent-tint);
}

.score-display {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.score-label {
  font-size: 10px;
  font-weight: 600;
  color: var(--sg-text-disabled);
  text-transform: uppercase;
}

.score-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--sg-color-success);
}

.score-max {
  font-size: 14px;
  font-weight: 500;
  color: var(--sg-text-disabled);
}

/* ── Carousel area ───────────────────────────────── */
.carousel-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 24px;
  gap: 20px;
  overflow-y: auto;
}

/* Carousel sections */
.carousel-section {
  width: 100%;
  max-width: 340px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--sg-text-faint);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.section-label-count {
  font-weight: 500;
  color: var(--sg-text-disabled);
  letter-spacing: 0;
  text-transform: none;
}

/* Next section (top) */
.next-section {
  flex-shrink: 0;
}

/* Current section (middle) */
.current-section {
  flex-shrink: 0;
}

/* Done section (bottom, scrollable) */
.done-section {
  flex-shrink: 1;
  min-height: 0;
}

/* Completed steps */
.completed-steps-scroll {
  display: flex;
  flex-direction: column-reverse;
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
  padding-right: 4px;
}

.completed-card {
  background: linear-gradient(90deg, rgba(72, 187, 120, 0.15) 50%, rgba(72, 187, 120, 0.15) 50%);
  border: 1px solid rgba(72, 187, 120, 0.3);
  border-radius: 12px;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--sg-color-success);
  opacity: 0.7;
  position: relative;
  overflow: hidden;
}

.completed-card.is-failed-a {
  background: linear-gradient(90deg, rgba(252, 129, 129, 0.15) 50%, rgba(72, 187, 120, 0.15) 50%);
  border-color: rgba(252, 129, 129, 0.3);
  color: var(--sg-color-danger-text);
}

.completed-card.is-failed-b {
  background: linear-gradient(90deg, rgba(72, 187, 120, 0.15) 50%, rgba(252, 129, 129, 0.15) 50%);
  border-color: rgba(252, 129, 129, 0.3);
  color: var(--sg-color-danger-text);
}

.completed-card.is-failed-full {
  background: rgba(252, 129, 129, 0.15);
  border-color: rgba(252, 129, 129, 0.3);
  color: var(--sg-color-danger-text);
}

/* The last fired step is what Fail/Treffer act on — give it card-surface-level
   emphasis so it reads as the active target, not just another history row. */
.completed-card.is-last {
  opacity: 1;
  border-width: 2px;
  border-color: color-mix(in srgb, currentColor 55%, transparent);
  box-shadow: inset 0 0 14px color-mix(in srgb, currentColor 22%, transparent);
}

.step-number {
  font-weight: 700;
  min-width: 24px;
}

.step-type {
  font-size: 12px;
  opacity: 0.8;
}


/* Per-step-type accent (matches constants/stepModes.js base hues) so the card
   border, tint and inner glow all share one variable. */
.step-card.is-solo     { --card-accent: #1D9E75; }
.step-card.is-pair     { --card-accent: #378ADD; }
.step-card.is-a_schuss { --card-accent: #EF9F27; }
.step-card.is-raffale  { --card-accent: #7F77DD; }

.step-card {
  /* D card language, dialed up for the kiosk hero card: mode-tinted gradient,
     saturated hued border, and a light glow on the inner side of the border. */
  --card-accent: var(--sg-accent);
  border: 1.5px solid color-mix(in srgb, var(--card-accent) 55%, transparent);
  border-radius: 20px;
  background:
    linear-gradient(150deg,
      color-mix(in srgb, var(--card-accent) 22%, transparent) 0%,
      color-mix(in srgb, var(--card-accent) 10%, transparent) 42%,
      rgba(255, 255, 255, 0.05) 100%);
  box-shadow:
    inset 0 0 14px color-mix(in srgb, var(--card-accent) 24%, transparent),
    0 4px 22px color-mix(in srgb, var(--card-accent) 12%, transparent);
  padding: 24px 24px;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: transform 0.1s;
  animation: slideInUp 300ms ease-out;
}

.step-card:active {
  transform: scale(0.97);
}

@keyframes slideInUp {
  from {
    transform: translateY(40px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

/* Mode badge: fill/text/border come from modeBadgeStyle() (shared stepModes
   source) so the kiosk play card matches the flyout and admin views. */
.card-badge {
  font-size: 14px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 1px;
  border: 1.5px solid transparent;
  padding: 7px 18px;
  border-radius: 20px;
}

.card-label {
  font-size: 46px;
  font-weight: 700;
  color: var(--sg-text-primary);
  text-align: center;
  line-height: 1.1;
  letter-spacing: 1px;
}

/* Getroffen card (program complete) — success green, same D treatment */
.getroffen-card {
  --card-accent: #48BB78;
}

.badge-getroffen {
  color: rgba(72, 187, 120, 0.7);
  background: rgba(72, 187, 120, 0.15);
}

.getroffen-label {
  color: var(--sg-color-success);
}

/* a_schuss: each device shown as letter + alias column */
.aschuss-display {
  display: flex;
  align-items: center;
  gap: 16px;
}

.aschuss-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  opacity: 0.4;
  transition: opacity 0.2s;
}

.aschuss-item.active {
  opacity: 1;
}

.aschuss-main {
  font-size: 40px;
  font-weight: 700;
  color: var(--sg-text-primary);
  line-height: 1;
}

.aschuss-display .separator {
  color: var(--sg-text-faint);
  font-size: 26px;
}

.raffale-bar {
  width: 100%;
  height: 4px;
  background: rgba(168, 85, 247, 0.15);
  border-radius: 2px;
  overflow: hidden;
  position: relative;
  margin: 8px 0;
}

.raffale-bar .progress {
  height: 100%;
  background: linear-gradient(90deg, rgba(168, 85, 247, 0.5), rgba(168, 85, 247, 0.8));
  transition: width 50ms linear;
}

.raffale-bar .label {
  position: absolute;
  top: 6px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 10px;
  font-weight: 600;
  color: rgba(168, 85, 247, 0.6);
  white-space: nowrap;
  pointer-events: none;
}

.hint {
  font-size: 14px;
  font-weight: 500;
  color: color-mix(in srgb, var(--sg-accent) 70%, transparent);
  margin: 0;
  margin-top: 6px;
}


.preview-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--sg-border);
  border-radius: 12px;
  padding: 12px 14px;
  text-align: center;
  opacity: 0.3;
  font-size: 12px;
  color: var(--sg-text-faint);
  cursor: default;
}

.preview-card.is-fertig {
  opacity: 1;
  background: rgba(72, 187, 120, 0.15);
  border-color: rgba(72, 187, 120, 0.3);
  color: var(--sg-color-success);
  cursor: pointer;
  transition: all 0.2s;
}

.preview-card.is-fertig:hover {
  background: rgba(72, 187, 120, 0.25);
  border-color: rgba(72, 187, 120, 0.5);
}

.preview-type {
  display: block;
  font-weight: 600;
  margin-bottom: 4px;
}

.preview-label {
  display: block;
  font-size: 11px;
}

/* Completion and final score screens */
.completion-screen,
.final-score-screen {
  width: 100%;
  max-width: 340px;
  display: flex;
  justify-content: center;
}

.completion-card,
.score-card {
  background: rgba(72, 187, 120, 0.15);
  border: 1.5px solid rgba(72, 187, 120, 0.3);
  border-radius: 20px;
  padding: 32px 28px;
  text-align: center;
  width: 100%;
}

.card-title,
.final-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--sg-text-primary);
  margin-bottom: 16px;
}

.final-score-value {
  font-size: 36px;
  font-weight: 700;
  color: var(--sg-color-success);
  margin-bottom: 24px;
}

.button-group {
  display: flex;
  gap: 12px;
}

.btn {
  flex: 1;
  padding: 12px 16px;
  border-radius: 12px;
  border: none;
  font-family: inherit;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-success {
  background: rgba(72, 187, 120, 0.2);
  color: var(--sg-color-success);
  border: 1px solid rgba(72, 187, 120, 0.35);
}

.btn-success:hover {
  background: rgba(72, 187, 120, 0.28);
}

.btn-danger {
  background: rgba(252, 129, 129, 0.2);
  color: var(--sg-color-danger-bg);
  border: 1px solid rgba(252, 129, 129, 0.35);
}

.btn-danger:hover {
  background: rgba(252, 129, 129, 0.28);
}

.btn-primary {
  width: 100%;
  background: color-mix(in srgb, var(--sg-accent) 20%, transparent);
  color: var(--sg-accent);
  border: 1px solid color-mix(in srgb, var(--sg-accent) 35%, transparent);
}

.btn-primary:hover {
  background: color-mix(in srgb, var(--sg-accent) 28%, transparent);
}

/* ── Action bar (bottom buttons) ─────────────────── */
.action-bar {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 16px 24px;
  border-top: 1px solid var(--sg-border);
  flex-shrink: 0;
  background: rgba(10, 10, 18, 0.8);
}

.action-btn {
  padding: 12px 8px;
  min-height: 48px;
  border-radius: 10px;
  border: 1.5px solid rgba(252, 129, 129, 0.55);
  background: rgba(252, 129, 129, 0.22);
  color: var(--sg-color-danger-text);
  font-family: inherit;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  transition: all 0.2s;
}

.action-btn:hover:not(:disabled) {
  background: rgba(252, 129, 129, 0.32);
}

.action-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.action-btn.btn-no-bird {
  border-color: color-mix(in srgb, var(--sg-accent) 55%, transparent);
  background: color-mix(in srgb, var(--sg-accent) 22%, transparent);
  color: var(--sg-accent);
}

.action-btn.btn-no-bird:hover:not(:disabled) {
  background: color-mix(in srgb, var(--sg-accent) 32%, transparent);
}

.action-btn.btn-skip {
  border-color: color-mix(in srgb, var(--sg-accent) 55%, transparent);
  background: color-mix(in srgb, var(--sg-accent) 12%, transparent);
  color: var(--sg-accent);
}

.action-btn.btn-skip:hover:not(:disabled) {
  background: color-mix(in srgb, var(--sg-accent) 20%, transparent);
}

.action-btn.btn-hit-action {
  border-color: rgba(72, 187, 120, 0.55);
  background: rgba(72, 187, 120, 0.22);
  color: var(--sg-color-success-text);
}

.action-btn.btn-hit-action:hover:not(:disabled) {
  background: rgba(72, 187, 120, 0.32);
}

/* ── Fail flyout (bottom sheet) ───────────────────── */
.fail-sheet-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  z-index: 90;
}

.fail-sheet {
  background: rgba(24, 24, 40, 0.98);
  border-top: 1.5px solid var(--sg-border);
  border-radius: 18px 18px 0 0;
  padding: 12px 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  transition: transform 0.2s ease;
}

.fail-sheet-handle {
  width: 34px;
  height: 4px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.18);
  align-self: center;
}

.fail-sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.fail-sheet-title {
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.75);
}

.fail-sheet-close {
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  padding: 4px;
}

.fail-sheet-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.fail-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 16px 6px;
  border-radius: 12px;
  border: 1.5px solid rgba(252, 129, 129, 0.55);
  background: rgba(252, 129, 129, 0.22);
  color: var(--sg-color-danger-text);
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s;
}

.fail-cell:hover {
  background: rgba(252, 129, 129, 0.32);
}

.fail-cell-label {
  font-size: 16px;
  font-weight: 700;
}

.fail-cell-cost {
  font-size: 12px;
  opacity: 0.75;
}

.fail-sheet-fade-enter-active,
.fail-sheet-fade-leave-active {
  transition: opacity 0.18s;
}

.fail-sheet-fade-enter-from,
.fail-sheet-fade-leave-to {
  opacity: 0;
}

.fail-sheet-fade-enter-from .fail-sheet,
.fail-sheet-fade-leave-to .fail-sheet {
  transform: translateY(100%);
}

.btn-label {
  font-weight: 700;
  font-size: 12px;
}

.btn-info {
  font-size: 10px;
  opacity: 0.7;
}

/* ── Group score screen ─────────────────────────── */
.group-score-card {
  background: rgba(72, 187, 120, 0.1) !important;
}

.group-scores-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 8px;
}

.group-score-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--sg-border);
  border-radius: 10px;
}

.group-score-row.is-top {
  background: rgba(72, 187, 120, 0.15);
  border-color: rgba(72, 187, 120, 0.3);
}

.rank-badge {
  font-size: 13px;
  font-weight: 700;
  color: var(--sg-text-faint);
  min-width: 20px;
}

.group-score-row.is-top .rank-badge {
  color: var(--sg-color-success);
}

.group-player-name {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.85);
  text-align: left;
}

.group-player-points {
  font-size: 16px;
  font-weight: 700;
  color: var(--sg-color-success);
}

.group-points-max {
  font-size: 12px;
  font-weight: 500;
  color: var(--sg-text-disabled);
}

/* ── Progress dots ──────────────────────────────── */
.progress-dots {
  display: flex;
  justify-content: center;
  gap: 6px;
  padding: 16px 24px;
  flex-shrink: 0;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 3px;
  transition: all 0.2s;
}

.dot--completed {
  width: 6px;
  background: rgba(72, 187, 120, 0.4);
}

.dot--current {
  width: 22px;
  background: var(--sg-accent);
}

.dot--pending {
  width: 6px;
  background: rgba(255, 255, 255, 0.08);
}

/* ── Correction picker ───────────────────────────── */
.correction-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.correction-picker {
  background: rgba(24, 24, 40, 0.98);
  border: 1.5px solid var(--sg-border);
  border-radius: 18px;
  padding: 24px 20px;
  width: min(320px, 90vw);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.picker-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--sg-text-primary);
  text-align: center;
}

.picker-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.picker-btn {
  width: 100%;
  padding: 12px;
  border-radius: 10px;
  border: none;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}

.picker-btn.btn-getroffen {
  background: rgba(72, 187, 120, 0.2);
  color: var(--sg-color-success);
  border: 1px solid rgba(72, 187, 120, 0.35);
}

.picker-btn.btn-getroffen:hover {
  background: rgba(72, 187, 120, 0.28);
}

.picker-btn.btn-fail {
  background: rgba(252, 129, 129, 0.22);
  color: var(--sg-color-danger-text);
  border: 1.5px solid rgba(252, 129, 129, 0.55);
}

.picker-btn.btn-fail:hover {
  background: rgba(252, 129, 129, 0.32);
}

.correction-fade-enter-active,
.correction-fade-leave-active {
  transition: opacity 0.15s;
}

.correction-fade-enter-from,
.correction-fade-leave-to {
  opacity: 0;
}

/* ── Audit confirmations ─────────────────────────── */
.audit-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 0 4px;
  border-top: 1px solid var(--sg-border);
}

.audit-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--sg-text-faint);
  text-transform: uppercase;
  letter-spacing: 0.8px;
}

.audit-row {
  display: flex;
}

.audit-label {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  cursor: pointer;
  padding: 8px 10px;
  border-radius: 8px;
  transition: background 0.15s;
}

.audit-label:hover {
  background: rgba(255, 255, 255, 0.04);
}

.audit-checkbox {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  accent-color: var(--sg-accent);
}

.audit-player-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.85);
}

.audit-score {
  font-size: 13px;
  font-weight: 700;
  color: var(--sg-color-success);
}

.btn-primary:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* ── Score card adjustments for ScoreTable ──────────────────────── */
/* The carousel-area is the single scroll container for the score screen, so
   the cards must not scroll on their own — a nested overflow here produced a
   second scrollbar. */
.solo-score-card,
.group-score-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ── Next-shooter overlay ──────────────────────────────── */
.next-shooter-overlay {
  position: fixed;
  inset: 0;
  background: rgba(10, 10, 18, 0.97);
  backdrop-filter: blur(24px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 60;
}

.next-shooter-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 40px 32px;
  text-align: center;
}

.next-shooter-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--sg-text-faint);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.next-shooter-name {
  font-size: 36px;
  font-weight: 700;
  color: var(--sg-text-primary);
  line-height: 1.15;
}

.next-shooter-hint {
  font-size: 14px;
  color: var(--sg-text-faint);
}

.next-shooter-start-btn {
  margin-top: 16px;
  padding: 14px 40px;
  background: color-mix(in srgb, var(--sg-accent) 20%, transparent);
  border: 1px solid color-mix(in srgb, var(--sg-accent) 35%, transparent);
  border-radius: 14px;
  color: var(--sg-accent);
  font-family: inherit;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}

.next-shooter-start-btn:hover {
  background: color-mix(in srgb, var(--sg-accent) 28%, transparent);
}

.next-shooter-start-btn:active {
  transform: scale(0.97);
}

.next-shooter-fade-enter-active,
.next-shooter-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.next-shooter-fade-enter-from,
.next-shooter-fade-leave-to {
  opacity: 0;
  transform: translateY(12px);
}

/* ── Gate mode tokens — kept in sync with ShooterRemoteView's --delay-/--ruf- vars ── */
.play-page {
  --delay-color: #EF9F27;
  --delay-text: #FAC775;
  --ruf-color: #56C8D8;
  --ruf-text: #7AD8E4;
}

/* ── Mode badge in the top bar (class names shared with ShooterRemoteView's mode-badge-btn) ── */
.mode-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.6px;
}

.mode-badge .mode-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  animation: mode-dot-pulse 1s ease-in-out infinite;
}

.mode-badge--delayed {
  background: rgba(239, 159, 39, 0.12);
  color: var(--delay-text);
}
.mode-badge--delayed .mode-dot { background: var(--delay-color); }

.mode-badge--rufausloesung {
  background: rgba(86, 200, 216, 0.12);
  color: var(--ruf-text);
}
.mode-badge--rufausloesung .mode-dot { background: var(--ruf-color); }

@keyframes mode-dot-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.35; transform: scale(0.65); }
}

/* ── Countdown ring on the hero card (delay + totzeit) ── */
.card-gate-ring {
  width: 92px;
  height: 92px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  /* background is set inline via gating.ringStyle (conic-gradient) */
  -webkit-mask: radial-gradient(circle 33px at center, transparent 98%, #000 100%);
  mask: radial-gradient(circle 33px at center, transparent 98%, #000 100%);
}

.card-gate-num {
  position: absolute;
  font-size: 30px;
  font-weight: 700;
  color: var(--sg-text-primary);
  font-variant-numeric: tabular-nums;
}

/* ── Listening pulse on the whole card ── */
.step-card--listening {
  animation: card-listen-pulse 1s ease-in-out infinite;
}

@keyframes card-listen-pulse {
  0%, 100% { box-shadow: inset 0 0 14px rgba(86, 200, 216, 0.24), 0 4px 22px rgba(86, 200, 216, 0.18); }
  50%      { box-shadow: inset 0 0 22px rgba(86, 200, 216, 0.45), 0 4px 30px rgba(86, 200, 216, 0.35); }
}
</style>
