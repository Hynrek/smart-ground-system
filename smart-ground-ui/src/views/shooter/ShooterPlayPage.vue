<template>
  <div v-if="store.playProg" class="play-page">
    <!-- Top bar -->
    <div class="play-topbar">
      <button class="back-btn" @click="goBack">
        <Icons icon="chevronLeft" :size="20" color="rgba(255,255,255,0.8)" />
        Zurück
      </button>
      <div class="score-display">
        <span class="score-label">Punkte</span>
        <span class="score-value">{{ store.playScore.totalPoints }}</span>
      </div>
    </div>

    <!-- Vertical Carousel -->
    <div class="carousel-area">
      <!-- Completed steps (scrolled up) -->
      <div v-if="completedSteps.length > 0" class="completed-steps">
        <div
          v-for="stepIdx in completedSteps"
          :key="stepIdx"
          class="completed-card"
          :class="{ 'is-failed': store.playScore.stepStates[stepIdx].state === 'failed' }"
        >
          <span class="step-number">{{ stepIdx + 1 }}</span>
          <span class="step-type">{{ getTypeLabel(store.playProg[stepIdx].type) }}</span>
        </div>
      </div>

      <!-- Current step (center - main focus) -->
      <div v-if="currentStep" class="current-step-container">
        <div class="step-card" :class="`is-${currentStep.type}`" @click="handleCurrentStepClick">
          <span class="card-badge" :class="`badge-${currentStep.type}`">
            {{ getTypeLabel(currentStep.type) }}
          </span>
          <div v-if="currentStep.type === 'a.schuss'" class="aschuss-display">
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

      <!-- Next step preview -->
      <div v-if="nextStep && !showCompletion" class="next-step-preview">
        <div class="preview-card">
          <span class="preview-type">{{ getTypeLabel(nextStep.type) }}</span>
          <span class="preview-label">{{ getStepDisplay(nextStep) }}</span>
        </div>
      </div>

      <!-- Completion screen -->
      <div v-if="showCompletion && !showFinalScore" class="completion-screen">
        <div class="completion-card">
          <div class="card-title">Letzten Schritt überprüfen?</div>
          <div class="button-group">
            <button class="btn btn-success" @click="markLastStepDone">
              Treffer ✓
            </button>
            <button class="btn btn-danger" @click="markLastStepFailed">
              Fehlschuss ✗
            </button>
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
        @click="store.failStep('a')"
      >
        <span class="btn-label">Fail A</span>
        <span class="btn-info">-1</span>
      </button>

      <!-- Fail B (only for pair/raffale) -->
      <button
        v-if="currentStep && ['pair', 'a.schuss', 'raffale'].includes(currentStep.type)"
        class="action-btn"
        :disabled="!canFail"
        title="Gerät B fehlgeschossen"
        @click="store.failStep('b')"
      >
        <span class="btn-label">Fail B</span>
        <span class="btn-info">-1</span>
      </button>

      <!-- Fail A/B (only for pair/raffale) -->
      <button
        v-if="currentStep && ['pair', 'a.schuss', 'raffale'].includes(currentStep.type)"
        class="action-btn"
        :disabled="!canFail"
        title="Beide Geräte fehlgeschossen"
        @click="store.failStep('both')"
      >
        <span class="btn-label">Fail A/B</span>
        <span class="btn-info">-2</span>
      </button>

      <!-- No Bird (always visible) -->
      <button
        class="action-btn btn-no-bird"
        :disabled="store.playLastDeviceStep === null"
        title="Letzten Schritt wiederholen"
        @click="store.retryStep()"
      >
        <span class="btn-label">No Bird</span>
        <span class="btn-info">Retry</span>
      </button>
    </div>

    <!-- Progress dots -->
    <div class="progress-dots">
      <div
        v-for="(_, idx) in store.playProg"
        :key="idx"
        class="dot"
        :class="getDotClass(idx)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import Icons from '@/components/Icons.vue';

const router = useRouter();
const props = defineProps({
  rangeId: { type: String, required: true },
  playId: { type: String, required: true }, // This is now the sessionId
});

const store = useShooterRemoteStore();
const raffaleProgress = ref(0);
const raffaleDelayStart = ref(null);
const completionVerificationDone = ref(false);

// Initialize play program from localStorage session
if (!store.playProg) {
  const sessionData = store.loadPlaySession(props.playId);
  if (sessionData) {
    // Initialize play state from session data
    store.playProg = sessionData.steps;
    store.playCurrentStep = 0;
    store.playScoreMode = true;
    const stepStates = sessionData.steps.map((_, idx) => ({
      stepIndex: idx,
      state: 'pending',
      pointValue: store.getPointValueForStep(sessionData.steps[idx]),
    }));
    store.playScore = { totalPoints: 0, stepStates };
    store.playLastDeviceStep = null;
  } else {
    // Session not found, redirect back
    router.push(`/remote/${props.rangeId}`);
  }
}

const currentStep = computed(() => store.playProg?.[store.playCurrentStep] ?? null);
const nextStep = computed(() => store.playProg?.[store.playCurrentStep + 1] ?? null);

const completedSteps = computed(() => {
  const completed = [];
  for (let i = 0; i < store.playCurrentStep; i++) {
    completed.push(i);
  }
  return completed;
});

const showCompletion = computed(() => {
  return (
    store.playCurrentStep >= store.playProg.length - 1 &&
    !completionVerificationDone.value &&
    store.playLastDeviceStep !== null
  );
});

const showFinalScore = computed(() => {
  return (
    store.playCurrentStep >= store.playProg.length - 1 &&
    completionVerificationDone.value
  );
});

const canFail = computed(() => {
  // Can fail only when a step has been completed (has been done, is now green)
  return store.playLastDeviceStep !== null && !showCompletion.value && !showFinalScore.value;
});

const getTypeLabel = (type) => {
  const labels = {
    solo: 'Solo',
    pair: 'Pair',
    'a.schuss': 'a. Schuss',
    raffale: 'Raffale',
  };
  return labels[type] || type;
};

const getStepDisplay = (step) => {
  if (step.type === 'solo') return step.alias;
  if (step.type === 'raffale') return `${step.alias} (2×)`;
  return `${step.alias1} + ${step.alias2}`;
};

const getHint = (step) => {
  if (step.type === 'a.schuss') {
    return store.playPartialStep === null ? 'Erstes Gerät: Tippen' : 'Zweites Gerät: Tippen';
  }
  if (step.type === 'raffale') {
    return store.playRaffaleStarted ? 'Warte auf Verzögerung...' : 'Erste Auslösung: Tippen';
  }
  return 'Tippen zum Auslösen →';
};

const getDotClass = (idx) => {
  if (idx < store.playCurrentStep) return 'dot--completed';
  if (idx === store.playCurrentStep) return 'dot--current';
  return 'dot--pending';
};

const handleCurrentStepClick = async () => {
  await store.advancePlayStep();
};

const markLastStepDone = () => {
  completionVerificationDone.value = true;
};

const markLastStepFailed = () => {
  if (store.playLastDeviceStep !== null) {
    store.failStep('a');
    completionVerificationDone.value = true;
  }
};

const goBack = () => {
  store.closePlayback();
  // Clear the play session from localStorage
  store.clearPlaySession(props.playId);
  router.push(`/remote/${props.rangeId}`);
};

// Cleanup when leaving the page
onBeforeUnmount(() => {
  if (!showFinalScore.value) {
    store.closePlayback();
    // Clear the session if program wasn't completed
    store.clearPlaySession(props.playId);
  }
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

.completed-card.is-failed {
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
