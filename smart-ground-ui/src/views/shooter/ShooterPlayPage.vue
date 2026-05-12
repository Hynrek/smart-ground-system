<template>
  <div v-if="store.playProg" class="play-page">
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
      <div class="score-display">
        <span class="score-label">Punkte</span>
        <span class="score-value">{{ store.playScore.totalPoints }}</span>
      </div>
    </div>

    <!-- Vertical Carousel (Top Down: Next → Current → Done) -->
    <div class="carousel-area">
      <!-- Part 1: Next step preview (top) -->
      <div v-if="nextStep && !showFinalScore" class="carousel-section next-section">
        <div class="section-label">Nächster Schritt</div>
        <div class="preview-card" :class="{ 'is-fertig': isFertig(nextStep) }" @click="handleNextStepClick">
          <span class="preview-type">{{ getTypeLabel(nextStep.type) }}</span>
          <span class="preview-label">{{ getStepDisplay(nextStep) }}</span>
        </div>
      </div>

      <!-- Part 2: Current step (center - main focus) -->
      <div v-if="currentStep" class="carousel-section current-section">
        <div class="section-label">Aktueller Schritt</div>
        <div class="step-card" :class="`is-${currentStep.type}`" @click="handleCurrentStepClick">
          <span class="card-badge" :class="`badge-${currentStep.type}`">
            {{ getTypeLabel(currentStep.type) }}
          </span>
          <div v-if="currentStep.type === 'a_schuss'" class="aschuss-display">
            <div :class="{ device: true, bold: store.playPartialStep === null }">
              {{ currentStep.alias1 }}
            </div>
            <span class="separator">+</span>
            <div :class="{ device: true, bold: store.playPartialStep === 'first' }">
              {{ currentStep.alias2 }}
            </div>
          </div>
          <div v-else class="card-label">
            {{ getStepDisplay(currentStep) }}
          </div>

          <!-- Raffale timer -->
          <div v-if="currentStep.type === 'raffale' && store.playRaffaleStarted" class="raffale-bar">
            <div class="progress" :style="{ width: raffaleProgress + '%' }" />
            <span class="label">{{ Math.max(0, 1 - raffaleProgress / 100).toFixed(1) }}s</span>
          </div>

          <p class="hint">{{ getHint(currentStep) }}</p>
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
            <span class="step-type">{{ getTypeLabel(store.playProg[step.segIdx].steps[step.stepIdx].type) }}</span>
          </div>
        </div>
      </div>

      <!-- Final score screen -->
      <div v-if="showFinalScore" class="final-score-screen">
        <div class="score-card">
          <div class="final-title">Programm Fertig!</div>
          <div class="final-score-value">{{ store.playScore.totalPoints }} Punkte</div>
          <button class="btn btn-primary" @click="goBack">
            Zurück zu Programmen
          </button>
        </div>
      </div>
    </div>

    <!-- Action buttons at bottom (always visible) -->
    <div class="action-bar">
      <!-- Fail A -->
      <button
        class="action-btn"
        :disabled="!canFail"
        title="Gerät A fehlgeschossen"
        @click="handleFailStep('a')"
      >
        <span class="btn-label">Fail A</span>
        <span class="btn-info">-1</span>
      </button>

      <!-- Fail B (only for pair/raffale) -->
      <button
        class="action-btn"
        :disabled="!(canFail && lastStepWasADouble)"
        title="Gerät B fehlgeschossen"
        @click="handleFailStep('b')"
      >
        <span class="btn-label">Fail B</span>
        <span class="btn-info">-1</span>
      </button>

      <!-- Fail A/B (only for pair/raffale) -->
      <button
        class="action-btn"
        :disabled="!(canFail && lastStepWasADouble)"
        title="Beide Geräte fehlgeschossen"
        @click="handleFailStep('both')"
      >
        <span class="btn-label">Fail A/B</span>
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

const router = useRouter();
const props = defineProps({
  rangeId: { type: String, required: true },
});

const store = usePlaySessionStore();
const raffaleProgress = ref(0);
const raffaleDelayStart = ref(null);

if (!store.playProg) {
  router.push(`/remote/${props.rangeId}`);
}

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
  const { segmentIdx, stepIdx } = store.playLastDeviceStep;
  const step = store.playProg?.[segmentIdx]?.steps[stepIdx];
  return step ? doubleTypes.includes(step.type) : false;
});

// ── Helpers ───────────────────────────────────────────────────────────────────
const getTypeLabel = (type) => ({
  [StepType.SOLO]: 'Solo',
  [StepType.PAIR]: 'Pair',
  [StepType.A_SCHUSS]: 'a. Schuss',
  [StepType.RAFFALE]: 'Raffale',
  fertig: 'Fertig',
})[type] ?? type;

const getStepDisplay = (step) => {
  if (step.type === 'fertig') return 'Programm abgeschlossen';
  if (step.type === StepType.SOLO) return step.alias;
  if (step.type === StepType.RAFFALE) return `${step.alias} (2×)`;
  return `${step.alias1} + ${step.alias2}`;
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
  const state = store.playScore.stepStates.find(
    (s) => s.playerId === store.currentPlayer?.id && s.ablaufIndex === segIdx && s.stepIndex === stepIdx
  );
  if (!state) return '';
  if (state.state === StepState.FAILED_A) return 'is-failed-a';
  if (state.state === StepState.FAILED_B) return 'is-failed-b';
  if (state.state === StepState.FAILED_BOTH) return 'is-failed-full';
  return '';
};

const computeFlatStepCount = () => {
  if (!store.playProg) return [];
  const total = store.playProg.reduce((sum, seg) => sum + seg.steps.length, 0);
  return Array.from({ length: total }, (_, i) => i);
};

const getDotClass = (flatIdx) => {
  if (!store.playProg) return 'dot--pending';
  const currentFlat =
    store.playProg.slice(0, store.currentAblaufIndex).reduce((sum, seg) => sum + seg.steps.length, 0) +
    store.currentStepIndex;
  if (flatIdx < currentFlat) return 'dot--completed';
  if (flatIdx === currentFlat) return 'dot--current';
  return 'dot--pending';
};

// ── Handlers ──────────────────────────────────────────────────────────────────
const handleCurrentStepClick = async () => {
  await store.advancePlayStep();
};

const handleNextStepClick = () => {
  if (isFertig(nextStep.value)) store.confirmComplete();
};

const handleFailStep = (failType) => {
  store.failStep(failType);
  // At program end the Fertig card is acting as the implicit hit-confirm;
  // using a fail button is the user's explicit confirmation that they're done.
  if (store.isAtProgramEnd) store.confirmComplete();
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
</style>
