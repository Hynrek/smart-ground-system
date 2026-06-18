<!-- src/components/competition/StepScorecard.vue -->
<!--
  Per-step scorecard for one shooter, grouped by Passe. Style-agnostic: chips use
  semantic translucent colors that read on both the light admin panel and the dark
  shooter kiosk. Driven purely by persisted stepStates (no program needed).
-->
<template>
  <div class="step-scorecard">
    <div v-for="passe in passen" :key="passe.passeIndex" class="passe-group">
      <span class="passe-label">{{ passe.label }}</span>
      <div class="chips">
        <span
          v-for="step in passe.steps"
          :key="step.stepIndex"
          class="step-chip"
          :class="stateClass(step.state)"
          :title="stateTitle(step.state)"
        >
          <span class="glyph">{{ glyph(step.state) }}</span>
          <span class="pts">{{ step.pointsEarned }}/{{ step.pointValue }}</span>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { StepState } from '@/constants/playEnums.js'

defineProps({
  passen: { type: Array, required: true },
})

const stateClass = (state) => {
  if (state === StepState.DONE) return 'is-done'
  if (state === StepState.PENDING) return 'is-pending'
  return 'is-fail'
}

const glyph = (state) => {
  if (state === StepState.DONE) return '✓'
  if (state === StepState.PENDING) return '–'
  if (state === StepState.FAILED_A) return '✗1'
  if (state === StepState.FAILED_B) return '✗2'
  return '✗'
}

const stateTitle = (state) => {
  const map = {
    [StepState.DONE]: 'Treffer',
    [StepState.FAILED_A]: 'Fehler 1. Taube',
    [StepState.FAILED_B]: 'Fehler 2. Taube',
    [StepState.FAILED_BOTH]: 'Beide verfehlt',
    [StepState.PENDING]: 'Offen',
  }
  return map[state] ?? state
}
</script>

<style scoped>
.step-scorecard { display: flex; flex-direction: column; gap: 8px; }

.passe-group { display: flex; flex-direction: column; gap: 4px; }

.passe-label {
  font-size: 11px; font-weight: 700; letter-spacing: 0.03em; text-transform: uppercase;
  opacity: 0.6;
}

.chips { display: flex; flex-wrap: wrap; gap: 6px; }

.step-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 3px 8px; border-radius: 8px;
  font-size: 12px; font-weight: 600;
  border: 1px solid transparent;
}

.step-chip .pts { font-size: 11px; opacity: 0.85; }

.step-chip.is-done {
  background: rgba(72, 187, 120, 0.16);
  color: #2f855a;
  border-color: rgba(72, 187, 120, 0.4);
}

.step-chip.is-fail {
  background: rgba(229, 62, 62, 0.14);
  color: #c53030;
  border-color: rgba(229, 62, 62, 0.4);
}

.step-chip.is-pending {
  background: rgba(160, 174, 192, 0.14);
  color: #718096;
  border-color: rgba(160, 174, 192, 0.4);
}

/* On dark kiosk backgrounds the muted text colors above lose contrast — lift them. */
:global(.results-view) .step-chip.is-done { color: #9ae6b4; }
:global(.results-view) .step-chip.is-fail { color: #feb2b2; }
:global(.results-view) .step-chip.is-pending { color: #cbd5e0; }
</style>
