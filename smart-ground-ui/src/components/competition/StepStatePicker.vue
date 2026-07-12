<!-- src/components/competition/StepStatePicker.vue -->
<!--
  State picker for correcting one step's result (admin PRE_COMPLETE flow). Emits `pick`
  with a StepState. Modeled on the kiosk ShooterPlayPage picker; the kiosk is left
  unchanged. Single-target steps (solo/raffale) offer Treffer/Fehler; doublettes offer
  Treffer + Fehler <pos1> / Fehler <pos2> / Beide, labelled with the step's positions.
-->
<template>
  <div class="picker">
    <button type="button" class="picker-btn picker-btn--hit" @click="emit('pick', StepState.DONE)">
      Treffer
    </button>
    <template v-if="isDouble">
      <button type="button" class="picker-btn" @click="emit('pick', StepState.FAILED_A)">Fehler {{ firstLabel }}</button>
      <button type="button" class="picker-btn" @click="emit('pick', StepState.FAILED_B)">Fehler {{ secondLabel }}</button>
      <button type="button" class="picker-btn" @click="emit('pick', StepState.FAILED_BOTH)">Beide Fehler</button>
    </template>
    <button v-else type="button" class="picker-btn" @click="emit('pick', StepState.FAILED_BOTH)">
      Fehler
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { StepState, StepType } from '@/constants/playEnums.js'

const props = defineProps({
  type: { type: String, default: null },
  // Position letters of the two clays, used to label the per-clay Fehler buttons
  // (e.g. "Fehler B" / "Fehler D"). Fall back to A/B when a position is unknown.
  firstLabel: { type: String, default: 'A' },
  secondLabel: { type: String, default: 'B' },
})
const emit = defineEmits(['pick'])

const isDouble = computed(() => [StepType.PAIR, StepType.A_SCHUSS].includes(props.type))
</script>

<style scoped>
.picker { display: flex; flex-wrap: wrap; gap: 8px; }

.picker-btn {
  padding: 8px 14px;
  border-radius: 10px;
  border: 1px solid var(--sg-border);
  background: var(--sg-bg-card);
  color: var(--sg-text-primary);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}
.picker-btn:hover { background: var(--sg-bg-panel); }

.picker-btn--hit {
  background: var(--sg-color-success-bg);
  color: var(--sg-color-success-text);
  border-color: color-mix(in srgb, var(--sg-color-success) 40%, transparent);
}
</style>
