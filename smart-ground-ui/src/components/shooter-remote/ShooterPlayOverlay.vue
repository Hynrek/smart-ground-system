<template>
  <div v-if="store.playProg || isComplete" class="play-overlay">
    <!-- Top bar -->
    <div class="play-topbar">
      <button class="end-btn" @click="store.closePlayback">Programm beenden</button>
      <div class="throw-counter">
        <span class="counter-label">Würfe</span>
        <span class="counter-value">{{ currentThrowIndex }} / {{ totalThrows }}</span>
      </div>
    </div>

    <!-- Carousel -->
    <div class="carousel-area">
      <p v-if="store.playCurrentStep > 0 && !isComplete" class="completed-hint">
        Erledigt: {{ store.playCurrentStep }} ({{ completedType }})
      </p>

      <!-- Program Done screen -->
      <div v-if="isComplete" class="step-card step-card--done" @click="handleStepClick">
        <span class="card-badge badge-done">Fertig</span>
        <div class="card-label">Programm Done</div>
        <div v-if="store.playScoreMode" class="final-score">
          <div class="score-row">
            <span class="score-label">Hits:</span>
            <span class="score-value hits">{{ store.playScore.hits }}</span>
          </div>
          <div class="score-row">
            <span class="score-label">Misses:</span>
            <span class="score-value misses">{{ store.playScore.misses }}</span>
          </div>
        </div>
        <p class="card-hint">Tippen zum Schließen →</p>
      </div>

      <!-- Current step -->
      <div v-else-if="currentStep" class="step-card" :class="{ 'is-raffale': currentStep.type === 'raffale', 'is-aschuss': currentStep.type === 'a.schuss' }" @click="handleStepClick">
        <span class="card-badge" :class="`badge-${currentStep.type}`">{{ getTypeLabel(currentStep.type) }}</span>
        <div class="card-label" v-if="currentStep.type !== 'a.schuss'">
          {{ getStepDisplay(currentStep) }}
        </div>
        <!-- a.Schuss two-device display -->
        <div v-else class="aschuss-devices">
          <div :class="{ 'device-name': true, 'is-bold': store.playPartialStep === null }">
            {{ currentStep.alias1 }}
          </div>
          <span class="device-separator">+</span>
          <div :class="{ 'device-name': true, 'is-bold': store.playPartialStep === 'first' }">
            {{ currentStep.alias2 }}
          </div>
        </div>
        <!-- Raffale delay indicator -->
        <div v-if="currentStep.type === 'raffale'" class="raffale-delay-bar" v-show="store.playRaffaleStarted">
          <div class="delay-progress" :style="{ width: raffaleProgress + '%' }" />
          <span class="delay-label">1 Sek Verzögerung</span>
          <div v-if="showMockClick" class="mock-click">{{ raffaleProgress >= 100 ? '✓' : '' }}</div>
        </div>
        <p class="card-hint" v-if="currentStep.type === 'solo'">Tippen zum Weiter →</p>
        <p class="card-hint" v-else-if="currentStep.type === 'pair'">Tippen zum Weiter →</p>
        <p class="card-hint" v-else-if="currentStep.type === 'a.schuss'">{{ aschussHint }}</p>
        <p class="card-hint" v-else-if="currentStep.type === 'raffale'">{{ raffaleDelayText }}</p>
      </div>

      <!-- Next preview -->
      <div v-if="nextStep && !store.playPartialStep" class="step-card step-card--preview">
        <span class="card-badge" :class="`badge-${nextStep.type}`">{{ getTypeLabel(nextStep.type) }}</span>
        <div class="card-label">
          {{ getStepDisplay(nextStep) }}
        </div>
      </div>
    </div>

    <!-- Action buttons (score mode) -->
    <div v-if="store.playScoreMode && store.playLastDeviceStep !== null" class="action-buttons">
      <button class="action-btn btn-fail" @click="store.recordMiss">
        <span class="btn-label">Fail</span>
        <span class="btn-desc">(Not Scored)</span>
      </button>
      <button class="action-btn btn-no-bird" @click="store.handleNoBird">
        <span class="btn-label">No Bird</span>
        <span class="btn-desc">(Retry)</span>
      </button>
    </div>

    <!-- Progress dots -->
    <div class="progress-dots">
      <div
        v-for="(_, idx) in store.playProg"
        :key="idx"
        class="dot"
        :class="dotClass(idx)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';

const store = useShooterRemoteStore();
const raffaleProgress = ref(0);
const raffaleDelayStart = ref(null);
const showMockClick = ref(false);

const isComplete = computed(() => !store.playProg || store.playCurrentStep >= store.playProg.length);
const currentStep = computed(() => store.playProg?.[store.playCurrentStep] ?? null);
const nextStep = computed(() => store.playProg?.[store.playCurrentStep + 1] ?? null);

const aschussHint = computed(() => {
  if (store.playPartialStep === null) return 'Erstes Gerät: Tippen';
  return 'Zweites Gerät: Tippen';
});

const totalThrows = computed(() => {
  if (!store.playProg) return 0;
  return store.playProg.reduce((sum, s) => {
    if (s.type === 'solo') return sum + 1;
    if (s.type === 'pair' || s.type === 'a.schuss') return sum + 2;
    if (s.type === 'raffale') return sum + 2;
    return sum;
  }, 0);
});

const currentThrowIndex = computed(() => {
  if (!store.playProg) return 0;
  let count = 0;
  for (let i = 0; i <= store.playCurrentStep; i++) {
    const step = store.playProg[i];
    if (step.type === 'solo') count += 1;
    else if (step.type === 'pair' || step.type === 'a.schuss') count += 2;
    else if (step.type === 'raffale') count += 2;
  }
  return count;
});

const completedType = computed(() => {
  if (!store.playProg || store.playCurrentStep === 0) return '';
  const prev = store.playProg[store.playCurrentStep - 1];
  const typeLabels = {
    solo: 'Solo',
    pair: 'Pair',
    'a.schuss': 'a. Schuss',
    raffale: 'Raffale',
  };
  return typeLabels[prev.type] || 'Solo';
});

const raffaleDelayText = computed(() => {
  if (raffaleProgress.value >= 100) return 'Tippen zum Weiter →';
  return `Warte... ${Math.ceil((100 - raffaleProgress.value) / 50)} Sek`;
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

const dotClass = (idx) => {
  if (idx < store.playCurrentStep) return 'dot--past';
  if (idx === store.playCurrentStep) return 'dot--current';
  return 'dot--future';
};

const handleStepClick = async () => {
  if (isComplete.value) {
    store.closePlayback();
    return;
  }

  // In score mode: if last device was clicked and waiting for action, auto-record as hit
  if (store.playScoreMode && store.playLastDeviceStep !== null) {
    store.recordHit();
    return;
  }

  await store.advancePlayStep();
};

// Monitor raffale timer
watch(() => store.playRaffaleStarted, (started) => {
  if (started) {
    raffaleProgress.value = 0;
    showMockClick.value = false;
    raffaleDelayStart.value = Date.now();
    const interval = setInterval(() => {
      const elapsed = Date.now() - raffaleDelayStart.value;
      raffaleProgress.value = Math.min((elapsed / 1000) * 100, 100);
      if (raffaleProgress.value >= 100) {
        showMockClick.value = true;
        clearInterval(interval);
        // Auto-complete after mock click is shown
        setTimeout(() => {
          store.completeRaffaleStep();
          showMockClick.value = false;
        }, 500);
      }
    }, 50);
  } else {
    raffaleProgress.value = 0;
    showMockClick.value = false;
  }
});
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

/* ── Top bar ─────────────────────────────────────── */
.play-topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  flex-shrink: 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
}

.end-btn {
  background: rgba(252, 129, 129, 0.1);
  border: 1px solid rgba(252, 129, 129, 0.3);
  color: #fc8181;
  border-radius: 10px;
  padding: 9px 16px;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s;
}

.end-btn:hover {
  background: rgba(252, 129, 129, 0.16);
}

.throw-counter {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.counter-label {
  font-size: 10px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.3);
  text-transform: uppercase;
  letter-spacing: 0.6px;
}

.counter-value {
  font-size: 24px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
  letter-spacing: -0.5px;
}

/* ── Carousel ────────────────────────────────────── */
.carousel-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  gap: 14px;
}

.completed-hint {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.25);
  margin: 0 0 8px;
}

/* ── Step cards ──────────────────────────────────── */
.step-card {
  background: rgba(255, 255, 255, 0.06);
  border: 1.5px solid rgba(79, 195, 247, 0.35);
  border-radius: 20px;
  padding: 32px 28px;
  width: 100%;
  max-width: 340px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  cursor: pointer;
  animation: card-entrance 200ms ease-out;
  transition: transform 0.1s;
}

.step-card:active {
  transform: scale(0.97);
}

.step-card--preview {
  opacity: 0.3;
  border-color: rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.03);
  cursor: default;
  animation: none;
}

.step-card.is-raffale {
  border-color: rgba(168, 85, 247, 0.35);
}

@keyframes card-entrance {
  from { transform: translateY(40px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
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

.card-badge.badge-pair,
.card-badge.badge-a\.schuss {
  color: rgba(72, 187, 120, 0.7);
  background: rgba(72, 187, 120, 0.1);
}

.card-badge.badge-raffale {
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

.card-hint {
  font-size: 13px;
  font-weight: 500;
  color: rgba(79, 195, 247, 0.6);
  margin: 4px 0 0;
}

/* ── a. Schuss device display ───────────────────── */
.aschuss-devices {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin: 8px 0;
}

.device-name {
  font-size: 18px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.6);
  transition: all 0.2s;
}

.device-name.is-bold {
  font-size: 24px;
  font-weight: 700;
  color: #4fc3f7;
}

.device-separator {
  font-size: 20px;
  color: rgba(255, 255, 255, 0.3);
}

/* ── Program Done card ──────────────────────────── */
.step-card--done {
  border-color: rgba(72, 187, 120, 0.35);
}

.badge-done {
  color: rgba(72, 187, 120, 0.7);
  background: rgba(72, 187, 120, 0.1);
}

/* ── Raffale mock click ──────────────────────────– */
.mock-click {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 24px;
  font-weight: 700;
  color: #48bb78;
  animation: mock-click-show 0.4s ease-out;
}

@keyframes mock-click-show {
  0% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.8);
  }
  50% {
    opacity: 1;
  }
  100% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(1.2);
  }
}

/* ── Raffale delay bar ──────────────────────────── */
.raffale-delay-bar {
  width: 100%;
  position: relative;
  height: 4px;
  background: rgba(168, 85, 247, 0.15);
  border-radius: 2px;
  overflow: hidden;
  margin: 8px 0 0;
}

.delay-progress {
  height: 100%;
  background: linear-gradient(90deg, rgba(168, 85, 247, 0.5), rgba(168, 85, 247, 0.8));
  transition: width 50ms linear;
  border-radius: 2px;
}

.delay-label {
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

/* ── Progress dots ───────────────────────────────── */
.progress-dots {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
  padding: 20px 24px;
  flex-shrink: 0;
}

.dot {
  height: 6px;
  border-radius: 3px;
  transition: all 0.2s;
}

.dot--past {
  width: 6px;
  background: rgba(255, 255, 255, 0.2);
}

.dot--current {
  width: 22px;
  background: #4fc3f7;
}

.dot--future {
  width: 6px;
  background: rgba(255, 255, 255, 0.08);
}

/* ── Score display ──────────────────────────────── */
.final-score {
  margin: 16px 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.score-row {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
}

.score-label {
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.5);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.score-value {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.score-value.hits {
  color: #48bb78;
}

.score-value.misses {
  color: #fc8181;
}

/* ── Action buttons (score mode) ────────────────── */
.action-buttons {
  display: flex;
  gap: 12px;
  padding: 16px 24px;
  flex-shrink: 0;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.action-btn {
  flex: 1;
  padding: 12px 16px;
  border-radius: 12px;
  border: 1.5px solid;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.btn-fail {
  background: rgba(252, 129, 129, 0.12);
  border-color: rgba(252, 129, 129, 0.35);
  color: #fc8181;
}

.btn-fail:hover {
  background: rgba(252, 129, 129, 0.2);
}

.btn-fail:active {
  transform: scale(0.95);
}

.btn-no-bird {
  background: rgba(79, 195, 247, 0.12);
  border-color: rgba(79, 195, 247, 0.35);
  color: #4fc3f7;
}

.btn-no-bird:hover {
  background: rgba(79, 195, 247, 0.2);
}

.btn-no-bird:active {
  transform: scale(0.95);
}

.btn-label {
  font-size: 14px;
  font-weight: 700;
}

.btn-desc {
  font-size: 10px;
  font-weight: 500;
  opacity: 0.7;
}
</style>
