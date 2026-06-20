<template>
  <div class="score-table">
    <div v-for="player in players" :key="player.id" class="player-section">
      <!-- Player header with total -->
      <div class="player-header">
        <span class="player-name">{{ player.displayName }}</span>
        <span class="player-total">{{ getPlayerTotal(player.id) }}/{{ getPlayerMax(player.id) }}</span>
      </div>

      <!-- Steps table -->
      <div class="steps-list">
        <div
          v-for="step in getPlayerSteps(player.id)"
          :key="`${step.serieIndex}-${step.stepIndex}`"
          class="step-row"
          :class="[getStepRowClass(step), { 'is-editable': props.editable }]"
          @click="props.editable && emit('correct-step', { playerId: player.id, serieIndex: step.serieIndex, stepIndex: step.stepIndex, currentState: step.state })"
        >
          <span class="step-letters">{{ getLetters(step) }}</span>
          <span class="step-type">{{ getTypeLabel(getActualStep(step)) }}</span>
          <span class="step-status">{{ getStateLabel(step.state, getActualStep(step)) }}</span>
          <span class="step-points">{{ getPointsDisplay(step) }}</span>
          <span v-if="props.editable" class="corrected-badge">{{ step.corrected ? '✏' : '' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { StepState, StepType } from '@/constants/playEnums.js';
import { stepModeLabel, stepNotation } from '@/constants/stepModes.js';

const props = defineProps({
  stepStates: {
    type: Array,
    required: true, // Array of step state objects
  },
  program: {
    type: Array,
    required: true, // Array of ablaeufe with steps
  },
  players: {
    type: Array,
    required: true, // Array of player objects
  },
  editable: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['correct-step']);

// Get step state for a specific player and step location
const findStepState = (playerId, serieIdx, stepIdx) => {
  return props.stepStates.find(
    (s) => s.playerId === playerId && s.serieIndex === serieIdx && s.stepIndex === stepIdx
  ) ?? null;
};

// Get actual step object from program
const getActualStep = (stepState) => {
  return props.program?.[stepState.serieIndex]?.steps[stepState.stepIndex] ?? null;
};

// Get all step states for a player
const getPlayerSteps = (playerId) => {
  return props.stepStates.filter((s) => s.playerId === playerId);
};

// Get total points earned by player
const getPlayerTotal = (playerId) => {
  return props.stepStates
    .filter((s) => s.playerId === playerId && s.state !== StepState.PENDING)
    .reduce((sum, s) => {
      const deduction = getPointDeduction(s.state);
      return sum + Math.max(0, s.pointValue - deduction);
    }, 0);
};

// Get max possible points for player
const getPlayerMax = (playerId) => {
  return props.stepStates
    .filter((s) => s.playerId === playerId)
    .reduce((sum, s) => sum + s.pointValue, 0);
};

// Get point deduction based on fail state
const getPointDeduction = (state) => {
  if (state === StepState.FAILED_BOTH) return 2;
  if (state === StepState.FAILED_A || state === StepState.FAILED_B) return 1;
  return 0;
};

// Get display label for step type (shared with all step views — see constants/stepModes.js)
const getTypeLabel = (step) => {
  if (!step) return '?';
  return stepModeLabel(step.type);
};

// Get display label for step state — uses the actual step to resolve position letters
const getStateLabel = (state, step = null) => {
  if (state === StepState.PENDING) return '⏳';
  if (state === StepState.DONE) return '✓';
  if (state === StepState.FAILED_BOTH) return '✗';
  if (state === StepState.FAILED_A) {
    if (step?.type === StepType.RAFFALE) return `✗ ${step.letter ?? '?'} 1.`;
    return `✗ ${step?.letter1 ?? 'A'}`;
  }
  if (state === StepState.FAILED_B) {
    if (step?.type === StepType.RAFFALE) return `✗ ${step.letter ?? '?'} 2.`;
    return `✗ ${step?.letter2 ?? 'B'}`;
  }
  return state;
};

// Get letter(s) display for a step
const getLetters = (stepState) => {
  const step = getActualStep(stepState);
  if (!step) return '?';
  return stepNotation(step);
};

// Get points display (earned/max)
const getPointsDisplay = (stepState) => {
  if (stepState.state === StepState.PENDING) {
    return `-/${stepState.pointValue}`;
  }
  const deduction = getPointDeduction(stepState.state);
  const earned = Math.max(0, stepState.pointValue - deduction);
  return `${earned}/${stepState.pointValue}`;
};

// Get CSS class for step row
const getStepRowClass = (stepState) => {
  const classes = [];
  if (stepState.state === StepState.FAILED_A) classes.push('is-failed-a');
  else if (stepState.state === StepState.FAILED_B) classes.push('is-failed-b');
  else if (stepState.state === StepState.FAILED_BOTH) classes.push('is-failed-both');
  else if (stepState.state === StepState.DONE) classes.push('is-done');
  else if (stepState.state === StepState.PENDING) classes.push('is-pending');
  return classes;
};
</script>

<style scoped>
.score-table {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
}

.player-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.player-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.player-name {
  font-size: 14px;
  font-weight: 600;
  color: #ffffff;
}

.player-total {
  font-size: 13px;
  font-weight: 700;
  color: #48bb78;
}

.steps-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.step-row {
  display: grid;
  grid-template-columns: 40px 1fr auto auto;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  align-items: center;
}

.step-row.is-done {
  background: rgba(72, 187, 120, 0.1);
  color: rgba(255, 255, 255, 0.9);
}

.step-row.is-failed-a,
.step-row.is-failed-b {
  background: rgba(252, 129, 129, 0.1);
  color: rgba(252, 129, 129, 0.9);
}

.step-row.is-failed-both {
  background: rgba(252, 129, 129, 0.15);
  color: rgba(252, 129, 129, 1);
}

.step-row.is-pending {
  background: rgba(255, 255, 255, 0.02);
  color: rgba(255, 255, 255, 0.5);
}

.step-letters {
  font-weight: 600;
  text-align: left;
}

.step-type {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
}

.step-status {
  text-align: center;
  font-weight: 600;
}

.step-points {
  text-align: right;
  font-weight: 600;
}

.step-row.is-editable {
  grid-template-columns: 40px 1fr auto auto 16px;
  cursor: pointer;
  transition: background 0.12s;
}

.step-row.is-editable:hover {
  background: rgba(255, 255, 255, 0.05);
}

.corrected-badge {
  text-align: right;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
}

.player-section + .player-section {
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  padding-top: 12px;
}
</style>
