<template>
  <div v-if="store.playProg" class="play-overlay">
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
      <p v-if="store.playCurrentStep > 0" class="completed-hint">
        Erledigt: {{ store.playCurrentStep }} ({{ completedType }})
      </p>

      <!-- Current step -->
      <div v-if="currentStep" class="step-card" @click="store.advancePlayStep">
        <span class="card-badge">{{ currentStep.type === 'solo' ? 'Solo' : 'Pair' }}</span>
        <div class="card-label">
          {{ currentStep.type === 'solo' ? currentStep.alias : `${currentStep.alias1} + ${currentStep.alias2}` }}
        </div>
        <p class="card-hint">Tippen zum Weiter →</p>
      </div>

      <!-- Next preview -->
      <div v-if="nextStep" class="step-card step-card--preview">
        <span class="card-badge">{{ nextStep.type === 'solo' ? 'Solo' : 'Pair' }}</span>
        <div class="card-label">
          {{ nextStep.type === 'solo' ? nextStep.alias : `${nextStep.alias1} + ${nextStep.alias2}` }}
        </div>
      </div>
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
import { computed } from 'vue';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';

const store = useShooterRemoteStore();

const currentStep = computed(() => store.playProg?.[store.playCurrentStep] ?? null);
const nextStep = computed(() => store.playProg?.[store.playCurrentStep + 1] ?? null);

const totalThrows = computed(() =>
  store.playProg?.reduce((sum, s) => sum + (s.type === 'pair' ? 2 : 1), 0) ?? 0,
);

const currentThrowIndex = computed(() => {
  if (!store.playProg) return 0;
  let count = 0;
  for (let i = 0; i <= store.playCurrentStep; i++) {
    count += store.playProg[i].type === 'pair' ? 2 : 1;
  }
  return count;
});

const completedType = computed(() => {
  if (!store.playProg || store.playCurrentStep === 0) return '';
  return store.playProg[store.playCurrentStep - 1].type === 'solo' ? 'Solo' : 'Pair';
});

const dotClass = (idx) => {
  if (idx < store.playCurrentStep) return 'dot--past';
  if (idx === store.playCurrentStep) return 'dot--current';
  return 'dot--future';
};
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
</style>
