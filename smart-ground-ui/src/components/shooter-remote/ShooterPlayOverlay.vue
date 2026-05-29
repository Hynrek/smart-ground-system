<template>
  <div v-if="playStore.playProg" class="play-overlay">
    <!-- Player Handover Screen (multi-player) -->
    <PlayerHandoverScreen
      v-if="playStore.isMultiPlayer"
      :visible="showHandover"
      @confirm="confirmHandover"
    />

    <!-- Top bar -->
    <div class="play-topbar">
      <button class="end-btn" @click="playStore.closePlayback">Passe beenden</button>
      <div class="score-display">
        <span class="score-label">Punkte</span>
        <span class="score-value">{{ playStore.playScore.totalPoints }}</span>
        <button
          v-if="playStore.isMultiPlayer"
          class="scoreboard-btn"
          :title="showScoreboard ? 'Scoreboard verbergen' : 'Scoreboard anzeigen'"
          @click="showScoreboard = !showScoreboard"
        >
          ⊞
        </button>
      </div>
    </div>

    <!-- Live Scoreboard Panel (multi-player) -->
    <div v-if="playStore.isMultiPlayer && showScoreboard" class="scoreboard-container">
      <LiveScoreboard />
    </div>

    <!-- Vertical Carousel -->
    <div class="carousel-area">
      <!-- Completed steps (scrolled up) -->
      <div v-if="completedSteps.length > 0" class="completed-steps">
        <div
          v-for="(completed, i) in completedSteps"
          :key="`${completed.segIdx}-${completed.stepIdx}`"
          class="completed-card"
          :class="{ 'is-failed': playStore.playScore.stepStates.find(s => s.serieIndex === completed.segIdx && s.stepIndex === completed.stepIdx)?.state?.includes('failed') }"
        >
          <span class="step-number">{{ i + 1 }}</span>
          <span class="step-type">{{ getTypeLabel(playStore.playProg[completed.segIdx].steps[completed.stepIdx].type) }}</span>
        </div>
      </div>

      <!-- Current step (center - main focus) -->
      <div v-if="currentStep" class="current-step-container">
        <div class="step-card" :class="`is-${currentStep.type}`" @click="handleCurrentStepClick">
          <span class="card-badge" :class="`badge-${currentStep.type}`">
            {{ getTypeLabel(currentStep.type) }}
          </span>
          <div v-if="currentStep.type === 'a_schuss'" class="aschuss-display">
            <div :class="{ device: true, bold: playStore.playPartialStep === null }">
              {{ currentStep.alias1 }}
            </div>
            <span class="separator">+</span>
            <div :class="{ device: true, bold: playStore.playPartialStep === 'first' }">
              {{ currentStep.alias2 }}
            </div>
          </div>
          <div v-else class="card-label">
            {{ getStepDisplay(currentStep) }}
          </div>

          <!-- Raffale timer -->
          <div v-if="currentStep.type === 'raffale' && playStore.playRaffaleStarted" class="raffale-bar">
            <div class="progress" :style="{ width: raffaleProgress + '%' }" />
            <span class="label">{{ Math.max(0, 1 - raffaleProgress / 100).toFixed(1) }}s</span>
          </div>

          <p class="hint">{{ getHint(currentStep) }}</p>
        </div>
      </div>

      <!-- Next step preview -->
      <div v-if="nextStep && !showFinalScore" class="next-step-preview">
        <div
          class="preview-card"
          :class="{ 'is-fertig': isFertig(nextStep) }"
          @click="handleNextStepClick"
        >
          <span class="preview-type">{{ getTypeLabel(nextStep.type) }}</span>
          <span class="preview-label">{{ getStepDisplay(nextStep) }}</span>
        </div>
      </div>

      <!-- Final score screen -->
      <div v-if="showFinalScore" class="final-score-screen">
        <div class="score-card">
          <div class="final-title">Passe Fertig!</div>
          <div class="final-score-value">{{ playStore.playScore.totalPoints }} Punkte</div>
          <button class="btn btn-primary" @click="playStore.closePlayback">
            Schließen
          </button>
        </div>
      </div>
    </div>

    <!-- Action buttons at bottom -->
    <div class="action-bar">
      <button
        v-for="btn in actionButtons"
        :key="btn.id"
        class="action-btn"
        :class="{ disabled: !btn.active }"
        :disabled="!btn.active"
        :title="btn.label"
        @click="btn.handler"
      >
        <span class="btn-label">{{ btn.label }}</span>
        <span class="btn-info">{{ btn.info }}</span>
      </button>
    </div>

    <!-- Progress dots (by segment) -->
    <div class="progress-dots">
      <div
        v-for="(_, segIdx) in playStore.playProg"
        :key="segIdx"
        class="dot"
        :class="getDotClass(segIdx)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { usePlaySessionStore } from '@/stores/playSessionStore.js';
import { StepType } from '@/constants/playEnums.js';
import PlayerHandoverScreen from '@/components/shooter-remote/PlayerHandoverScreen.vue';
import LiveScoreboard from '@/components/shooter-remote/LiveScoreboard.vue';

const playStore = usePlaySessionStore();
const raffaleProgress = ref(0);
const raffaleDelayStart = ref(null);
const showHandover = ref(false);
const showScoreboard = ref(false);
const lastPlayerIndex = ref(0);

const fertigStep = { type: 'fertig', alias: 'Fertig' };

const currentStep = computed(() => playStore.currentStep);

const nextStep = computed(() => {
  const seg = playStore.currentSerie;
  if (!seg || !playStore.playProg) return fertigStep;
  if (playStore.currentStepIndex < seg.steps.length - 1) {
    return seg.steps[playStore.currentStepIndex + 1];
  }
  if (playStore.currentSerieIndex < playStore.playProg.length - 1) {
    return playStore.playProg[playStore.currentSerieIndex + 1].steps[0];
  }
  return fertigStep;
});

const completedSteps = computed(() => {
  if (!playStore.playProg) return [];
  const completed = [];
  for (let segIdx = 0; segIdx < playStore.playProg.length; segIdx++) {
    const seg = playStore.playProg[segIdx];
    for (let stepIdx = 0; stepIdx < seg.steps.length; stepIdx++) {
      if (
        segIdx < playStore.currentSerieIndex ||
        (segIdx === playStore.currentSerieIndex && stepIdx < playStore.currentStepIndex)
      ) {
        completed.push({ segIdx, stepIdx });
      }
    }
  }
  return completed;
});

const showFinalScore = computed(() => playStore.playComplete);

const isFertig = (step) => step?.type === 'fertig';

const doubleTypes = [StepType.PAIR, StepType.A_SCHUSS, StepType.RAFFALE];

const actionButtons = computed(() => {
  const canFail = playStore.playLastDeviceStep !== null && !playStore.playComplete;
  if (!canFail) return [];

  const lastStep = playStore.playLastDeviceStep
    ? playStore.playProg?.[playStore.playLastDeviceStep.serieIdx]?.steps[playStore.playLastDeviceStep.stepIdx]
    : null;
  const isDouble = lastStep ? doubleTypes.includes(lastStep.type) : false;
  const atEnd = playStore.isAtProgramEnd;

  const fail = (type) => () => {
    playStore.failStep(type);
    if (atEnd) playStore.confirmComplete();
  };

  const buttons = [
    { id: 'fail-a', label: 'Fail A', info: '-1', active: true, handler: fail('a') },
  ];
  if (isDouble) {
    buttons.push({ id: 'fail-b', label: 'Fail B', info: '-1', active: true, handler: fail('b') });
    buttons.push({ id: 'fail-ab', label: 'Fail A/B', info: '-2', active: true, handler: fail('both') });
  }
  buttons.push({
    id: 'no-bird',
    label: 'No Bird',
    info: 'Retry',
    active: playStore.canRetry,
    handler: () => playStore.retryStep(),
  });

  return buttons;
});

const getTypeLabel = (type) => ({
  [StepType.SOLO]: 'Solo',
  [StepType.PAIR]: 'Pair',
  [StepType.A_SCHUSS]: 'a. Schuss',
  [StepType.RAFFALE]: 'Raffale',
  fertig: 'Fertig',
})[type] ?? type;

const getStepDisplay = (step) => {
  if (step.type === 'fertig') return 'Passe abgeschlossen';
  if (step.type === StepType.SOLO) return step.alias;
  if (step.type === StepType.RAFFALE) return `${step.alias} (2×)`;
  return `${step.alias1} + ${step.alias2}`;
};

const getHint = (step) => {
  if (step.type === StepType.A_SCHUSS) {
    return playStore.playPartialStep === null ? 'Erstes Gerät: Tippen' : 'Zweites Gerät: Tippen';
  }
  if (step.type === StepType.RAFFALE) {
    return playStore.playRaffaleStarted ? 'Warte auf Verzögerung...' : 'Erste Auslösung: Tippen';
  }
  return 'Tippen zum Auslösen →';
};

const getDotClass = (segIdx) => {
  if (segIdx < playStore.currentSerieIndex) return 'dot--completed';
  if (segIdx === playStore.currentSerieIndex) return 'dot--current';
  return 'dot--pending';
};

const handleCurrentStepClick = async () => {
  await playStore.advancePlayStep();
};

const handleNextStepClick = () => {
  if (isFertig(nextStep.value)) playStore.confirmComplete();
};

const confirmHandover = () => {
  showHandover.value = false;
  lastPlayerIndex.value = playStore.currentPlayerIndex;
};

// Monitor player changes for handover (multi-player)
watch(
  () => playStore.currentPlayerIndex,
  (newIndex) => {
    if (playStore.isMultiPlayer && newIndex !== lastPlayerIndex.value && newIndex > 0) {
      showHandover.value = true;
    }
    lastPlayerIndex.value = newIndex;
  }
);

// Monitor raffale timer
watch(
  () => playStore.playRaffaleStarted,
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
            playStore.completeRaffaleStep();
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
.play-overlay {
  position: fixed;
  inset: 0;
  background: rgba(10, 10, 18, 0.97);
  backdrop-filter: blur(24px);
  display: flex;
  flex-direction: column;
  z-index: 50;
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

.end-btn {
  background: rgba(252, 129, 129, 0.1);
  border: 1px solid rgba(252, 129, 129, 0.3);
  color: #fc8181;
  border-radius: 10px;
  padding: 9px 16px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}

.end-btn:hover {
  background: rgba(252, 129, 129, 0.16);
}

.score-display {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.scoreboard-btn {
  background: rgba(100, 180, 220, 0.15);
  border: 1px solid rgba(100, 180, 220, 0.3);
  color: rgba(100, 180, 220, 0.8);
  width: 28px;
  height: 28px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.scoreboard-btn:hover {
  background: rgba(100, 180, 220, 0.25);
  border-color: rgba(100, 180, 220, 0.5);
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

/* ── Carousel area ───────────────────────────────── */
.carousel-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  gap: 16px;
  overflow-y: auto;
}

/* Completed steps */
.completed-steps {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  max-width: 340px;
  margin-bottom: 8px;
}

.completed-card {
  background: rgba(72, 187, 120, 0.15);
  border: 1px solid rgba(72, 187, 120, 0.3);
  border-radius: 12px;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #48bb78;
  opacity: 0.7;
}

.completed-card.is-failed {
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

/* Current step */
.current-step-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 340px;
}

.step-card {
  background: rgba(255, 255, 255, 0.06);
  border: 1.5px solid rgba(79, 195, 247, 0.35);
  border-radius: 20px;
  padding: 32px 28px;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
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
  font-size: 28px;
  font-weight: 700;
  color: #ffffff;
  text-align: center;
  line-height: 1.2;
}

.aschuss-display {
  display: flex;
  align-items: center;
  gap: 12px;
}

.aschuss-display .device {
  font-size: 18px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.6);
  transition: all 0.2s;
}

.aschuss-display .device.bold {
  font-size: 24px;
  font-weight: 700;
  color: #4fc3f7;
}

.aschuss-display .separator {
  color: rgba(255, 255, 255, 0.3);
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

/* Next step preview */
.next-step-preview {
  width: 100%;
  max-width: 340px;
}

.preview-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 16px;
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
  border: 1px solid rgba(252, 129, 129, 0.