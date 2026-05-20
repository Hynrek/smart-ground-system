<template>
  <!-- Group setup modal (shown before play starts) -->
  <div v-if="store.showGroupSetup" class="play-page group-setup-page">
    <div class="group-modal-overlay">
      <div class="group-modal">
        <h2 class="modal-title">Gruppe einrichten</h2>

        <div class="player-list">
          <div v-for="(player, i) in groupPlayers" :key="player.id" class="player-row">
            <span class="player-number">{{ i + 1 }}:</span>
            <span class="player-display-name">{{ player.displayName }}</span>
            <button
              v-if="groupPlayers.length > 1"
              class="player-remove-btn"
              @click="removePlayer(i)"
            >
              <Icons icon="x" :size="12" color="#fc8181" />
            </button>
          </div>
        </div>

        <button class="add-player-btn" @click="addPlayer">
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
          <span class="score-label">Spieler</span>
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
          <div class="final-title">Programm Fertig!</div>
          <div class="final-score-value">{{ store.playScore.totalPoints }} Punkte</div>
          <ScoreTable
            v-if="store.sessionPlayers.length > 0"
            :step-states="store.playScore.stepStates"
            :program="store.playProg"
            :players="store.sessionPlayers"
          />
          <button class="btn btn-primary" @click="goBack">
            Zurück zu Programmen
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
          />
          <button class="btn btn-primary" @click="goBack">
            Zurück zu Programmen
          </button>
        </div>
      </div>
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

if (!store.playProg && !store.showGroupSetup && !store.pendingProgramInfo) {
  router.push(`/remote/${props.rangeId}`);
}

// ── Group setup ───────────────────────────────────────────────────────────────
let _nextPlayerId = 1;
const groupPlayers = ref([{ id: `gp-${_nextPlayerId++}`, displayName: 'Schütze 1' }]);

// Capture block context and stage the ablauf before clearing pendingProgramInfo
const _blockContext = ref(null);
if (store.pendingProgramInfo) {
  const info = store.pendingProgramInfo;
  store.setPendingGroupAblaeufe([info.ablauf]);
  if (info.instanceId && info.blockId) {
    _blockContext.value = { instanceId: info.instanceId, blockId: info.blockId };
  }
  if (info.players?.length) {
    groupPlayers.value = info.players.map((p, i) => ({
      id: p.id ?? `gp-${i + 1}`,
      displayName: p.displayName,
    }));
  }
  store.clearPendingProgram();
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
  const seg = store.currentAblauf;
  if (!seg || !store.playProg) return fertigStep;
  if (store.currentStepIndex < seg.steps.length - 1) {
    return seg.steps[store.currentStepIndex + 1];
  }
  if (store.currentAblaufIndex < store.playProg.length - 1) {
    return store.playProg[store.currentAblaufIndex + 1].steps[0];
  }
  return fertigStep;
});

const completedSteps = computed(() => {
  const completed = [];
  if (!store.playProg) return completed;
  for (let segIdx = 0; segIdx < store.currentAblaufIndex; segIdx++) {
    for (let stepIdx = 0; stepIdx < store.playProg[segIdx].steps.length; stepIdx++) {
      completed.push({ segIdx, stepIdx });
    }
  }
  for (let stepIdx = 0; stepIdx < store.currentStepIndex; stepIdx++) {
    completed.push({ segIdx: store.currentAblaufIndex, stepIdx });
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
    store.playProg.slice(0, store.currentAblaufIndex).reduce((sum, seg) => sum + seg.steps.length, 0) +
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
  const { ablaufIdx, stepIdx } = store.playLastDeviceStep;
  const step = store.playProg?.[ablaufIdx]?.steps[stepIdx];
  return step ? doubleTypes.includes(step.type) : false;
});

// The actual step object that was last fired (for dynamic fail button labels)
const lastFiredStep = computed(() => {
  if (!store.playLastDeviceStep) return null;
  const { ablaufIdx, stepIdx } = store.playLastDeviceStep;
  return store.playProg?.[ablaufIdx]?.steps[stepIdx] ?? null;
});

const failLabelA = computed(() => {
  const s = lastFiredStep.value;
  if (!s) return 'Fail 1';
  if (s.type === StepType.SOLO) return `Fail ${s.letter ?? '1'}`;
  if (s.type === StepType.RAFFALE) return `Fail ${s.letter ?? '1'} 1.`;
  return `Fail ${s.letter1 ?? '1'}`;
});

const failLabelB = computed(() => {
  const s = lastFiredStep.value;
  if (!s) return 'Fail 2';
  if (s.type === StepType.RAFFALE) return `Fail ${s.letter ?? '1'} 2.`;
  return `Fail ${s.letter2 ?? '2'}`;
});

const failLabelBoth = computed(() => {
  const s = lastFiredStep.value;
  if (!s) return 'Fail 1/2';
  if (s.type === StepType.RAFFALE) return `Fail ${s.letter ?? '1'}/2`;
  return `Fail ${s.letter1 ?? '1'}/${s.letter2 ?? '2'}`;
});

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
  if (step.type === 'fertig') return 'Programm abgeschlossen';
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
    (s) => s.playerId === store.currentPlayer?.id && s.ablaufIndex === segIdx && s.stepIndex === stepIdx
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
  if (store.isMultiPlayer) {
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
  color: rgba(79, 195, 247, 0.7);
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
  color: #fc8181;
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
  background: rgba(79, 195, 247, 0.1);
  border: 1px solid rgba(79, 195, 247, 0.3);
  color: #4fc3f7;
  border-radius: 10px;
  padding: 9px 16px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
  font-family: inherit;
}

.back-btn:hover {
  background: rgba(79, 195, 247, 0.16);
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
  color: #48bb78;
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
  color: #48bb78;
  opacity: 0.7;
  position: relative;
  overflow: hidden;
}

.completed-card.is-failed-a {
  background: linear-gradient(90deg, rgba(252, 129, 129, 0.15) 50%, rgba(72, 187, 120, 0.15) 50%);
  border-color: rgba(252, 129, 129, 0.3);
  color: #fc8181;
}

.completed-card.is-failed-b {
  background: linear-gradient(90deg, rgba(72, 187, 120, 0.15) 50%, rgba(252, 129, 129, 0.15) 50%);
  border-color: rgba(252, 129, 129, 0.3);
  color: #fc8181;
}

.completed-card.is-failed-full {
  background: rgba(252, 129, 129, 0.15);
  border-color: rgba(252, 129, 129, 0.3);
  color: #fc8181;
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
  border: 1.5px solid rgba(79, 195, 247, 0.35);
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
  color: rgba(79, 195, 247, 0.7);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  background: rgba(79, 195, 247, 0.1);
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
  color: #48bb78;
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
  color: #4fc3f7;
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
  color: rgba(79, 195, 247, 0.6);
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
  color: #48bb78;
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
  color: #48bb78;
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
  color: #48bb78;
  border: 1px solid rgba(72, 187, 120, 0.35);
}

.btn-success:hover {
  background: rgba(72, 187, 120, 0.28);
}

.btn-danger {
  background: rgba(252, 129, 129, 0.2);
  color: #fc8181;
  border: 1px solid rgba(252, 129, 129, 0.35);
}

.btn-danger:hover {
  background: rgba(252, 129, 129, 0.28);
}

.btn-primary {
  width: 100%;
  background: rgba(79, 195, 247, 0.2);
  color: #4fc3f7;
  border: 1px solid rgba(79, 195, 247, 0.35);
}

.btn-primary:hover {
  background: rgba(79, 195, 247, 0.28);
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
  color: #fc8181;
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
  border-color: rgba(79, 195, 247, 0.35);
  background: rgba(79, 195, 247, 0.12);
  color: #4fc3f7;
}

.action-btn.btn-no-bird:hover:not(:disabled) {
  background: rgba(79, 195, 247, 0.2);
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
  color: #48bb78;
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
  color: #48bb78;
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
  background: #4fc3f7;
}

.dot--pending {
  width: 6px;
  background: rgba(255, 255, 255, 0.08);
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
</style>
