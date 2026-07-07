<!-- src/components/shooter/SerieScoreCard.vue -->
<!--
  Leaf card for one completed Serie run in the shooter's results drill-down
  (Serien / Passen / Wettkämpfe tabs on ShooterKarriereView). Shows the Serie
  alias, its score, and range/date meta; a toggle reveals the per-clay
  breakdown via StepScorecard, built from `serie.stepStates`.

  Rows recorded before the backend step-enrichment (type/letter/letter1/letter2)
  landed carry stepStates without those fields — StepScorecard already falls
  back gracefully in that case (isMultiResultStep(undefined) → false → solid
  chip; stepLetters(step) → '' for missing letters → blank chip label), so no
  extra guarding is needed here beyond passing the data through as-is.
-->
<template>
  <div class="serie-score-card sg-card-surface sg-card-surface--calm">
    <div class="serie-score-card__header">
      <div class="serie-score-card__main">
        <span class="serie-score-card__name">{{ serie.serieAlias ?? '—' }}</span>
        <span class="serie-score-card__meta">{{ serie.rangeName ?? '—' }} · {{ formattedDate }}</span>
      </div>
      <span class="serie-score-card__score">{{ serie.totalPoints }}/{{ serie.maxPoints }}</span>
      <button
        type="button"
        class="serie-score-card__toggle"
        data-testid="serie-score-toggle"
        :aria-expanded="expanded"
        :aria-label="expanded ? 'Details ausblenden' : 'Details anzeigen'"
        @click="expanded = !expanded"
      >
        <Icons :icon="expanded ? 'chevron' : 'chevronDown'" :size="16" />
      </button>
    </div>

    <div v-if="expanded" class="serie-score-card__body">
      <StepScorecard :serien="stepScorecardSerien" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import Icons from '@/components/Icons.vue'
import StepScorecard from '@/components/competition/StepScorecard.vue'

const props = defineProps({
  serie: { type: Object, required: true },
})

const expanded = ref(false)

const formattedDate = computed(() => {
  if (!props.serie.completedAt) return '—'
  return new Date(props.serie.completedAt).toLocaleDateString('de-CH', {
    day: '2-digit', month: '2-digit', year: 'numeric',
  })
})

// StepScorecard expects `serien: [{ key, rangeName, serieName, steps }]` — a
// completed Serie run is self-contained, so wrap it as a single-entry group.
const stepScorecardSerien = computed(() => [{
  key: props.serie.id,
  rangeName: props.serie.rangeName,
  serieName: props.serie.serieAlias,
  steps: props.serie.stepStates ?? [],
}])
</script>

<style scoped>
.serie-score-card {
  display: flex;
  flex-direction: column;
  gap: var(--sg-space-3);
  padding: var(--sg-space-4);
  border-radius: var(--sg-radius-card);
  border: 1px solid var(--sg-border);
}

.serie-score-card__header {
  display: flex;
  align-items: center;
  gap: var(--sg-space-3);
}

.serie-score-card__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.serie-score-card__name {
  font-weight: 600;
  color: var(--sg-text-primary);
}

.serie-score-card__meta {
  font-size: 0.85rem;
  color: var(--sg-text-faint);
}

.serie-score-card__score {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--sg-text-primary);
}

.serie-score-card__toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border: none;
  border-radius: var(--sg-radius-btn);
  background: transparent;
  color: var(--sg-text-muted);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.serie-score-card__toggle:hover {
  background: var(--sg-accent-subtle);
  color: var(--sg-text-primary);
}

.serie-score-card__toggle:focus-visible {
  outline: 2px solid var(--sg-accent);
  outline-offset: 2px;
}

.serie-score-card__body {
  padding-top: var(--sg-space-2);
  border-top: 1px solid var(--sg-border);
}
</style>
