<template>
  <!-- Group setup modal (shown before play starts) -->
  <div v-if="store.showGroupSetup" class="play-page group-setup-page">
    <div class="group-modal-overlay">
      <div class="group-modal">
        <h2 class="modal-title">{{ _isCompetitionMode ? (_rotteName ?? 'Rotte') : 'Gruppe einrichten' }}</h2>
        <p v-if="_isCompetitionMode && _serieName" class="modal-serie-name">{{ _serieName }}</p>

        <div class="player-list">
          <div v-for="(player, i) in groupPlayers" :key="player.id" class="player-row">
            <span class="player-number">{{ i + 1 }}:</span>
            <span class="player-display-name">{{ player.displayName }}</span>
            <button
              v-if="!_isCompetitionMode && groupPlayers.length > 1"
              class="player-remove-btn"
              @click="removePlayer(i)"
            >
              <Icons icon="x" :size="12" color="var(--sg-color-danger-bg)" />
            </button>
          </div>
        </div>

        <button v-if="!_isCompetitionMode" class="add-player-btn" @click="addPlayer">
          + Schütze hinzufügen
        </button>

        <div class="modal-actions">
          <button class="btn btn-cancel" @click="cancelGroupSetup">Abbrechen</button>
          <button class="btn btn-primary" :disabled="groupPlayers.length === 0" @click="beginGroupPlay">
            Starten
          </button>
        </div>
      </div>
    </div>
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
        <div v-if="currentStep" class="step-card" :class="`is-${currentStep.type}`" @click="handleCurrentStepClick">
          <span class="card-badge" :class="`badge-${currentStep.type}`">
            {{ getTypeLabel(currentStep.type) }}
          </span>

          <!-- a_schuss: letter per device, active one highlighted -->
          <div v-if="currentStep.type === 'a_schuss'" class="aschuss-display">
            <div class="aschuss-item" :class="{ active: store.playPartialStep === null }">
              <span class="aschuss-main">{{ currentStep.letter1 ?? '?' }}</span>
              <span class="aschuss-sub">{{ currentStep.alias1 }}</span>
            </div>
            <span class="separator">+</span>
            <div class="aschuss-item" :class="{ active: store.playPartialStep === 'first' }">
              <span class="aschuss-main">{{ currentStep.letter2 ?? '?' }}</span>
              <span class="aschuss-sub">{{ currentStep.alias2 }}</span>
            </div>
          </div>

          <!-- solo / pair / raffale: letter big, alias below -->
          <template v-else>
            <div class="card-label">{{ getStepLetter(currentStep) }}</div>
            <div class="card-alias">{{ getStepAliasDisplay(currentStep) }}</div>
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
          <div class="card-label getroffen-label">Getroffen</div>
          <p class="hint">
            {{ store.isMultiPlayer && store.nextPlayer
              ? `Weiter zu ${store.nextPlayer.displayName} →`
              : 'Tippen zum Beenden →' }}
          </p>
        </div>
      </div>

      <!-- Part 3: Completed steps (bottom, scrollable) -->
      <div v-if="completedSteps.length > 0" class="carousel-section done-section">
        <div class="section-label">Abgeschlossene Schritte</div>
        <div class="completed-steps-scroll">
          <div
            v-for="(step, idx) in recentCompletedSteps"
            :key="`${step.segIdx}-${step.stepIdx}`"
            class="completed-card"
            :class="getCompletedCardClass(step.segIdx, step.stepIdx)"
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
      <!-- Fail first device -->
      <button
        class="action-btn"
        :disabled="!canFail"
        :title="failLabelA"
        @click="handleFailStep('a')"
      >
        <span class="btn-label">{{ failLabelA }}</span>
        <span class="btn-info">-1</span>
      </button>

      <!-- Fail second device (only for pair/a_schuss/raffale) -->
      <button
        class="action-btn"
        :disabled="!(canFail && lastStepWasADouble)"
        :title="failLabelB"
        @click="handleFailStep('b')"
      >
        <span class="btn-label">{{ failLabelB }}</span>
        <span class="btn-info">-1</span>
      </button>

      <!-- Fail both (only for pair/a_schuss/raffale) -->
      <button
        class="action-btn"
        :disabled="!(canFail && lastStepWasADouble)"
        :title="failLabelBoth"
        @click="handleFailStep('both')"
      >
        <span class="btn-label">{{ failLabelBoth }}</span>
        <span class="btn-info">-2</span>
      </button>

      <!-- No Bird (always visible) -->
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
          <span class="next-shooter-name">{{ store.nextPlayer?.displayName }}</span>
          <span class="next-shooter-hint">Bitte schießbereit machen</span>
          <button class="next-shooter-start-btn" @click="confirmNextShooter">
            Starten →
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>


<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { usePlaySessionStore } from '@/stores/playSessionStore.js';
import { StepState, StepType } from '@/constants/playEnums.js';
import Icons from '@/components/Icons.vue';
import ScoreTable from '@/components/shooter/ScoreTable.vue';

const router = useRouter();
const props = defineProps({
  rangeId: { type: String, required: true },
});

const store = usePlaySessionStore();
const raffaleProgress = ref(0);
const raffaleDelayStart = ref(null);

// ── Next-shooter overlay ──────────────────────────────────────────────────────
const showNextShooterOverlay = ref(false);

const confirmNextShooter = () => {
  showNextShooterOverlay.value = false;
  store.advanceToNextPlayer();
};

// ── Correction picker ─────────────────────────────────────────────────────────
const correctionTarget = ref(null);

const correctionTargetStep = computed(() => {
  if (!correctionTarget.value) return null;
  const { serieIndex, stepIndex } = correctionTarget.value;
  return store.playProg?.[serieIndex]?.steps[stepIndex] ?? null;
});

const correctionTargetIsDouble = computed(() => {
  const step = correctionTargetStep.value;
  return step ? [StepType.PAIR, StepType.A_SCHUSS, StepType.RAFFALE].includes(step.type) : false;
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
  router.push(`/remote/${props.rangeId}`);
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
    _blockContext.value = { instanceId: info.instanceId, blockId: info.blockId, rotteId: info.rotteId ?? null, instanceType: info.instanceType ?? null };
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
    }));
  }
  store.clearPendingPasse();
}

const addPlayer = () => {
  const n = groupPlayers.value.length + 1;
  groupPlayers.value.push({ id: `gp-${_nextPlayerId++}`, displayName: `Schütze ${n}` });
};

const removePlayer = (index) => {
  groupPlayers.value.splice(index, 1);
};

const beginGroupPlay = () => {
  store.startGroupPlay(
    groupPlayers.value,
    props.rangeId,
    'Platz',
    _blockContext.value?.instanceId ?? null,
    _blockContext.value?.blockId ?? null,
    _blockContext.value?.rotteId ?? null,
    _blockContext.value?.instanceType ?? null,
  );
};

const cancelGroupSetup = () => {
  store.cancelGroupSetup();
  router.push(`/remote/${props.rangeId}`);
};

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

const isFertig = (step) => step?.type === 'fertig';

// Fail buttons are active whenever there is a last fired step and the program
// is not already concluded.
const canFail = computed(() =>
  store.playLastDeviceStep !== null && !store.playComplete
);

// Fail B / Fail A+B only make sense when the last fired step was a multi-target type.
const doubleTypes = [StepType.PAIR, StepType.A_SCHUSS, StepType.RAFFALE];
const lastStepWasADouble = computed(() => {
  if (!store.playLastDeviceStep) return false;
  const { serieIdx, stepIdx } = store.playLastDeviceStep;
  const step = store.playProg?.[serieIdx]?.steps[stepIdx];
  return step ? doubleTypes.includes(step.type) : false;
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

const failLabelA = computed(() => failLabelAFor(lastFiredStep.value));
const failLabelB = computed(() => failLabelBFor(lastFiredStep.value));
const failLabelBoth = computed(() => failLabelBothFor(lastFiredStep.value));

// Same labels, but for the step targeted by the score-correction picker
const correctionFailLabelA = computed(() => failLabelAFor(correctionTargetStep.value));
const correctionFailLabelB = computed(() => failLabelBFor(correctionTargetStep.value));
const correctionFailLabelBoth = computed(() => failLabelBothFor(correctionTargetStep.value));

// ── Helpers ───────────────────────────────────────────────────────────────────
const getTypeLabel = (type) => ({
  [StepType.SOLO]: 'Solo',
  [StepType.PAIR]: 'Pair',
  [StepType.A_SCHUSS]: 'a. Schuss',
  [StepType.RAFFALE]: 'Raffale',
  fertig: 'Fertig',
})[type] ?? type;

// Letter-based display (main large label on the step card)
const getStepLetter = (step) => {
  if (!step || step.type === 'fertig') return '';
  if (step.type === StepType.SOLO || step.type === StepType.RAFFALE) return step.letter ?? '?';
  return `${step.letter1 ?? '?'} + ${step.letter2 ?? '?'}`;
};

// Alias-based display (secondary, smaller text under the letter)
const getStepAliasDisplay = (step) => {
  if (!step || step.type === 'fertig') return '';
  if (step.type === StepType.SOLO) return step.alias;
  if (step.type === StepType.RAFFALE) return `${step.alias} (2×)`;
  return `${step.alias1} + ${step.alias2}`;
};

// Letter-based display for the next-step preview card
const getStepDisplay = (step) => {
  if (step.type === 'fertig') return 'Passe abgeschlossen';
  if (step.type === StepType.SOLO) return step.letter ?? step.alias;
  if (step.type === StepType.RAFFALE) return `${step.letter ?? step.alias} (2×)`;
  const l1 = step.letter1 ?? step.alias1;
  const l2 = step.letter2 ?? step.alias2;
  return `${l1} + ${l2}`;
};

// Short label for completed cards: "Solo – A", "Pair – A+B" etc.
const getCompletedStepShortLabel = (segIdx, stepIdx) => {
  const s = store.playProg?.[segIdx]?.steps[stepIdx];
  if (!s) return '';
  const t = getTypeLabel(s.type);
  if (s.type === StepType.SOLO || s.type === StepType.RAFFALE) return `${t} – ${s.letter ?? '?'}`;
  return `${t} – ${s.letter1 ?? '?'}+${s.letter2 ?? '?'}`;
};

const getHint = (step) => {
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
const handleCurrentStepClick = async () => {
  await store.advancePlayStep();
};

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
  store.failStep(failType);
  if (store.isAtProgramEnd) handlePlayerComplete();
};

const goBack = () => {
  store.closePlayback();
  router.push(`/remote/${props.rangeId}`);
};

onBeforeUnmount(() => {
  if (!store.playComplete) store.closePlayback();
});

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

.group-modal-overlay {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.group-modal {
  background: rgba(24, 24, 40, 0.98);
  border: 1.5px solid rgba(255, 255, 255, 0.1);
  border-radius: 20px;
  padding: 28px 24px;
  width: 100%;
  max-width: 360px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.modal-title {
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
  margin: 0;
  text-align: center;
}

.modal-serie-name {
  font-size: 12px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.45);
  text-align: center;
  margin: -12px 0 0;
}

.player-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.player-row {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 10px 12px;
}

.player-number {
  font-size: 13px;
  font-weight: 700;
  color: color-mix(in srgb, var(--sg-accent) 70%, transparent);
  min-width: 22px;
}

.player-display-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.85);
}

.player-remove-btn {
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 3px;
  border-radius: 6px;
  transition: background 0.15s;
}

.player-remove-btn:hover {
  background: rgba(252, 129, 129, 0.1);
}

.add-player-btn {
  width: 100%;
  padding: 11px;
  background: rgba(255, 255, 255, 0.04);
  border: 1.5px dashed rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
}

.add-player-btn:hover {
  background: rgba(255, 255, 255, 0.07);
  border-color: rgba(255, 255, 255, 0.25);
  color: rgba(255, 255, 255, 0.7);
}

.modal-actions {
  display: flex;
  gap: 10px;
}

.btn-cancel {
  flex: 1;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid rgba(252, 129, 129, 0.35);
  background: rgba(252, 129, 129, 0.1);
  color: var(--sg-color-danger-bg);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-cancel:hover {
  background: rgba(252, 129, 129, 0.18);
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
  color: #ffffff;
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
  color: rgba(255, 255, 255, 0.3);
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
  color: rgba(255, 255, 255, 0.3);
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
  color: rgba(255, 255, 255, 0.4);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.section-label-count {
  font-weight: 500;
  color: rgba(255, 255, 255, 0.25);
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
  color: var(--sg-color-danger-bg);
}

.completed-card.is-failed-b {
  background: linear-gradient(90deg, rgba(72, 187, 120, 0.15) 50%, rgba(252, 129, 129, 0.15) 50%);
  border-color: rgba(252, 129, 129, 0.3);
  color: var(--sg-color-danger-bg);
}

.completed-card.is-failed-full {
  background: rgba(252, 129, 129, 0.15);
  border-color: rgba(252, 129, 129, 0.3);
  color: var(--sg-color-danger-bg);
}

.step-number {
  font-weight: 700;
  min-width: 24px;
}

.step-type {
  font-size: 12px;
  opacity: 0.8;
}


.step-card {
  background: rgba(255, 255, 255, 0.06);
  border: 1.5px solid color-mix(in srgb, var(--sg-accent) 35%, transparent);
  border-radius: 20px;
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

.step-card.is-raffale {
  border-color: rgba(168, 85, 247, 0.35);
}

.step-card.is-pair,
.step-card.is-a\.schuss {
  border-color: rgba(72, 187, 120, 0.35);
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

.card-badge {
  font-size: 10px;
  font-weight: 600;
  color: color-mix(in srgb, var(--sg-accent) 70%, transparent);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  background: var(--sg-accent-tint);
  padding: 3px 10px;
  border-radius: 20px;
}

.badge-pair,
.badge-a\.schuss {
  color: rgba(72, 187, 120, 0.7);
  background: rgba(72, 187, 120, 0.1);
}

.badge-raffale {
  color: rgba(168, 85, 247, 0.7);
  background: rgba(168, 85, 247, 0.1);
}

.card-label {
  font-size: 24px;
  font-weight: 700;
  color: #ffffff;
  text-align: center;
  line-height: 1.2;
}

.card-alias {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.35);
  text-align: center;
  margin-top: -4px;
}

/* Getroffen card (program complete) */
.getroffen-card {
  border-color: rgba(72, 187, 120, 0.5) !important;
  background: rgba(72, 187, 120, 0.08) !important;
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
  font-size: 28px;
  font-weight: 700;
  color: var(--sg-accent);
  line-height: 1;
}

.aschuss-sub {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
}

.aschuss-display .separator {
  color: rgba(255, 255, 255, 0.3);
  font-size: 18px;
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
  font-size: 13px;
  color: color-mix(in srgb, var(--sg-accent) 60%, transparent);
  margin: 0;
  margin-top: 4px;
}


.preview-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 12px 14px;
  text-align: center;
  opacity: 0.3;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
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
  color: #ffffff;
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
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  padding: 16px 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
  background: rgba(10, 10, 18, 0.8);
}

.action-btn {
  padding: 12px 8px;
  border-radius: 10px;
  border: 1.5px solid rgba(252, 129, 129, 0.35);
  background: rgba(252, 129, 129, 0.12);
  color: var(--sg-color-danger-bg);
  font-family: inherit;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  transition: all 0.2s;
}

.action-btn:hover:not(:disabled) {
  background: rgba(252, 129, 129, 0.2);
}

.action-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.action-btn.btn-no-bird {
  border-color: color-mix(in srgb, var(--sg-accent) 35%, transparent);
  background: var(--sg-accent-tint);
  color: var(--sg-accent);
}

.action-btn.btn-no-bird:hover:not(:disabled) {
  background: color-mix(in srgb, var(--sg-accent) 20%, transparent);
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
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
}

.group-score-row.is-top {
  background: rgba(72, 187, 120, 0.15);
  border-color: rgba(72, 187, 120, 0.3);
}

.rank-badge {
  font-size: 13px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.4);
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
  color: rgba(255, 255, 255, 0.3);
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
  border: 1.5px solid rgba(255, 255, 255, 0.12);
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
  color: #ffffff;
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
  background: rgba(252, 129, 129, 0.15);
  color: var(--sg-color-danger-bg);
  border: 1px solid rgba(252, 129, 129, 0.3);
}

.picker-btn.btn-fail:hover {
  background: rgba(252, 129, 129, 0.22);
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
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.audit-title {
  font-size: 11px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.4);
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
.solo-score-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 80vh;
  overflow-y: auto;
}

.group-score-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 80vh;
  overflow-y: auto;
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
  color: rgba(255, 255, 255, 0.4);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.next-shooter-name {
  font-size: 36px;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.15;
}

.next-shooter-hint {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.4);
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
</style>
