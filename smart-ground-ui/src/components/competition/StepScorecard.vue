<!-- src/components/competition/StepScorecard.vue -->
<!--
  Per-Serie step scorecard for one shooter. Chips are grouped by Serie
  ("RangeName – SerieName") and show the shooting position letter(s); points are
  conveyed by color (no numbers). Two-result steps (pair/a_schuss/raffale) render
  a split chip whose halves are colored independently per clay. Style-agnostic:
  semantic translucent colors read on both the light admin panel and the dark
  shooter kiosk (see the :global(.results-view) overrides).
-->
<template>
  <div class="step-scorecard">
    <div v-for="serie in serien" :key="serie.key" class="serie-group">
      <span class="serie-label">{{ headerLabel(serie) }}</span>
      <div class="chips">
        <template v-for="step in serie.steps" :key="step.stepIndex">
          <!-- split chip: two-result step (pair / a_schuss / raffale) -->
          <span
            v-if="isSplit(step.type)"
            class="step-chip step-chip--split"
            :class="{ 'step-chip--editable': editable }"
            :role="editable ? 'button' : undefined"
            :tabindex="editable ? 0 : undefined"
            :aria-label="ariaLabel(step)"
            :title="ariaLabel(step)"
            @click="onChipClick(serie, step)"
          >
            <span class="half" :class="halfClass(step.state, 'left')">{{ leftLabel(step) }}</span>
            <span class="half" :class="halfClass(step.state, 'right')">{{ rightLabel(step) }}</span>
          </span>
          <!-- solid chip: single-target / unknown -->
          <span
            v-else
            class="step-chip"
            :class="[solidClass(step.state), { 'step-chip--editable': editable }]"
            :role="editable ? 'button' : undefined"
            :tabindex="editable ? 0 : undefined"
            :aria-label="ariaLabel(step)"
            :title="ariaLabel(step)"
            @click="onChipClick(serie, step)"
          >{{ leftLabel(step) }}</span>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { StepState } from '@/constants/playEnums.js'
import { isMultiResultStep, stepLetters } from '@/constants/stepModes.js'

const props = defineProps({
  serien: { type: Array, required: true },
  // When true, chips are interactive and clicking emits `correct-step` for the admin
  // PRE_COMPLETE correction flow. Default false → display-only (Task C behavior).
  editable: { type: Boolean, default: false },
})

const emit = defineEmits(['correct-step'])

const onChipClick = (serie, step) => {
  if (!props.editable) return
  const { first, second } = stepLetters(step)
  emit('correct-step', {
    serieKey: serie.key,
    serieId: serie.serieId,
    groupId: serie.groupId,
    playerId: serie.playerId,
    stepIndex: step.stepIndex,
    type: step.type,
    currentState: step.state,
    // Resolved position letters so the correction picker can label the per-clay
    // Fehler buttons with the actual positions instead of bare A/B.
    firstLabel: first,
    secondLabel: second,
  })
}

// Split chips render for multi-result steps — see constants/stepModes.js.
const isSplit = (type) => isMultiResultStep(type)

const headerLabel = (serie) =>
  serie.rangeName ? `${serie.rangeName} – ${serie.serieName}` : serie.serieName

const solidClass = (state) => {
  if (state === StepState.DONE) return 'is-done'
  if (state === StepState.PENDING) return 'is-pending'
  return 'is-fail'
}

// left half = first clay (letter1 / raffale shot 1); right half = second.
const leftLabel = (step) => stepLetters(step).first
const rightLabel = (step) => stepLetters(step).second

const halfClass = (state, side) => {
  if (state === StepState.PENDING) return 'half--pending'
  const missed =
    state === StepState.FAILED_BOTH ||
    (side === 'left' && state === StepState.FAILED_A) ||
    (side === 'right' && state === StepState.FAILED_B)
  return missed ? 'half--fail' : 'half--done'
}

const resultWord = (hit, pending) => (pending ? 'Offen' : hit ? 'getroffen' : 'Fehler')

const ariaLabel = (step) => {
  const pending = step.state === StepState.PENDING
  if (isSplit(step.type)) {
    const leftHit = halfClass(step.state, 'left') === 'half--done'
    const rightHit = halfClass(step.state, 'right') === 'half--done'
    return `${leftLabel(step)} ${resultWord(leftHit, pending)}, ${rightLabel(step)} ${resultWord(rightHit, pending)}`
  }
  const hit = step.state === StepState.DONE
  return `${step.letter ?? '?'} ${resultWord(hit, pending)}`
}
</script>

<style scoped>
.step-scorecard { display: flex; flex-direction: column; gap: 8px; }

.serie-group { display: flex; flex-direction: column; gap: 4px; }

.serie-label {
  font-size: 11px; font-weight: 700; letter-spacing: 0.03em; opacity: 0.6;
}

.chips { display: flex; flex-wrap: wrap; gap: 6px; }

.step-chip {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 28px; height: 24px; padding: 0 8px; border-radius: 8px;
  font-size: 12px; font-weight: 700; border: 1px solid transparent; overflow: hidden;
}

.step-chip--editable { cursor: pointer; }
.step-chip--editable:focus-visible { outline: 2px solid var(--sg-accent); outline-offset: 1px; }

.step-chip.is-done { background: rgba(72, 187, 120, 0.16); color: #2f855a; border-color: rgba(72, 187, 120, 0.4); }
.step-chip.is-fail { background: rgba(229, 62, 62, 0.14); color: #c53030; border-color: rgba(229, 62, 62, 0.4); }
.step-chip.is-pending { background: rgba(160, 174, 192, 0.14); color: #718096; border-color: rgba(160, 174, 192, 0.4); }

/* split chip: two independently-colored halves */
.step-chip--split { padding: 0; border-color: rgba(160, 174, 192, 0.4); }
.step-chip--split .half {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 22px; height: 100%; padding: 0 6px; font-size: 12px; font-weight: 700;
}
.half--done { background: rgba(72, 187, 120, 0.16); color: #2f855a; }
.half--fail { background: rgba(229, 62, 62, 0.14); color: #c53030; }
.half--pending { background: rgba(160, 174, 192, 0.14); color: #718096; }

/* On dark kiosk backgrounds the muted colors lose contrast — lift them. */
:global(.results-view) .step-chip.is-done, :global(.results-view) .half--done { color: #9ae6b4; }
:global(.results-view) .step-chip.is-fail, :global(.results-view) .half--fail { color: #feb2b2; }
:global(.results-view) .step-chip.is-pending, :global(.results-view) .half--pending { color: #cbd5e0; }
</style>
