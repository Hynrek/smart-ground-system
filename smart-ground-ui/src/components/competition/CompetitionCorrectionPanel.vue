<!-- src/components/competition/CompetitionCorrectionPanel.vue -->
<!--
  Admin score correction for a PRE_COMPLETE competition. A Passe switcher selects the
  Passe; per Serie ("RangeName – SerieName") each shooter's step chips are editable.
  Tapping a chip opens a state picker; the corrected Serie (all players' stepStates +
  recomputed totals) is persisted via the store, and the Rangliste refreshes.
-->
<template>
  <div class="competition-correction">
    <div class="passe-switch">
      <button
        v-for="(p, i) in passen"
        :key="p.id ?? i"
        type="button"
        class="passe-tab"
        :class="{ active: activePasse === i }"
        @click="activePasse = i"
      >{{ p.name ?? `Passe ${i + 1}` }}</button>
    </div>

    <div v-if="data.serien.length === 0" class="empty">Keine Ergebnisse für diese Passe</div>

    <div v-for="serie in data.serien" :key="serie.serieId" class="serie-block">
      <div class="serie-head">{{ serieHeader(serie) }}</div>
      <div v-for="player in serie.players" :key="player.playerId" class="player-block">
        <StepScorecard editable :serien="[serieForPlayer(serie, player)]" @correct-step="onCorrect" />
      </div>
    </div>

    <div v-if="correctionTarget" class="picker-overlay" @click.self="correctionTarget = null">
      <div class="picker-box">
        <span class="picker-title">Treffer korrigieren</span>
        <StepStatePicker :type="correctionTarget.type" @pick="applyPick" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, toRef } from 'vue'
import StepScorecard from '@/components/competition/StepScorecard.vue'
import StepStatePicker from '@/components/competition/StepStatePicker.vue'
import { useCompletedResults } from '@/composables/useCompletedResults.js'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'

const props = defineProps({
  sessionId: { type: String, required: true },
  passen: { type: Array, default: () => [] },
})

const store = useCompetitionEventStore()
const { load, getCorrectionData, recomputeSerieTotals } = useCompletedResults(toRef(props, 'sessionId'))

const activePasse = ref(0)
const data = computed(() => getCorrectionData(activePasse.value))

const serieHeader = (serie) =>
  serie.rangeName ? `${serie.rangeName} – ${serie.serieName}` : serie.serieName

// One serie-for-one-player object for the editable StepScorecard. The chip-group label
// shows the shooter's name; the Serie header is rendered separately above.
const serieForPlayer = (serie, player) => ({
  key: `${serie.serieId}:${player.playerId}`,
  serieId: serie.serieId,
  groupId: serie.groupId,
  playerId: player.playerId,
  rangeName: null,
  serieName: player.displayName ?? player.playerId,
  steps: serie.steps.map(def => {
    const st = player.steps.find(s => s.stepIndex === def.stepIndex)
    return {
      stepIndex: def.stepIndex,
      type: def.type,
      letter: def.letter,
      letter1: def.letter1,
      letter2: def.letter2,
      state: st?.state ?? 'pending',
      pointValue: st?.pointValue ?? 0,
      pointsEarned: st?.pointsEarned ?? 0,
    }
  }),
})

const correctionTarget = ref(null)
const onCorrect = (payload) => { correctionTarget.value = payload }

// Per-step earned points, reusing the shared total rule on a single step.
const stepEarned = (state, pointValue) =>
  recomputeSerieTotals([{ state, pointValue }]).totalPoints

const applyPick = async (newState) => {
  const t = correctionTarget.value
  if (!t) return
  const serie = data.value.serien.find(s => s.serieId === t.serieId && s.groupId === t.groupId)
  if (!serie) { correctionTarget.value = null; return }
  const serieIndex = data.value.serien.indexOf(serie)

  // Build every player's corrected stepStates for this Serie (only the targeted step
  // of the targeted player changes), with recomputed totals.
  const results = serie.players.map(player => {
    const steps = serie.steps.map(def => {
      const st = player.steps.find(s => s.stepIndex === def.stepIndex)
      const pointValue = st?.pointValue ?? 0
      const state = (player.playerId === t.playerId && def.stepIndex === t.stepIndex)
        ? newState
        : (st?.state ?? 'pending')
      return {
        playerId: player.playerId,
        serieIndex,
        stepIndex: def.stepIndex,
        state,
        pointValue,
        pointsEarned: stepEarned(state, pointValue),
      }
    })
    const totals = recomputeSerieTotals(steps)
    return {
      playerId: player.playerId,
      displayName: player.displayName ?? null,
      totalPoints: totals.totalPoints,
      maxPoints: totals.maxPoints,
      stepStates: steps,
    }
  })

  correctionTarget.value = null
  try {
    await store.correctSerieResult(props.sessionId, t.groupId, t.serieId, activePasse.value, results)
  } catch (e) {
    console.error('[CompetitionCorrectionPanel] correction failed:', e)
  }
}

onMounted(load)
</script>

<style scoped>
.competition-correction { display: flex; flex-direction: column; gap: 14px; }

.passe-switch { display: flex; flex-wrap: wrap; gap: 6px; }

.passe-tab {
  padding: 6px 14px; border-radius: 999px;
  background: var(--sg-bg-panel); border: 1px solid var(--sg-border);
  color: var(--sg-text-muted); font: inherit; font-size: 13px; font-weight: 600; cursor: pointer;
  transition: all 0.15s;
}
.passe-tab.active { background: var(--sg-accent-tint); border-color: var(--sg-accent); color: var(--sg-color-info-text); }

.empty {
  text-align: center; padding: 24px 16px; font-size: 13px; color: var(--sg-text-faint);
  border: 1px dashed var(--sg-border); border-radius: 12px;
}

.serie-block {
  display: flex; flex-direction: column; gap: 8px;
  background: var(--sg-bg-card); border: 1px solid var(--sg-border);
  border-radius: 12px; padding: 12px 14px; box-shadow: var(--sg-shadow-sm);
}

.serie-head { font-size: 13px; font-weight: 700; color: var(--sg-brand); }

.player-block { padding: 2px 0; }

.picker-overlay {
  position: fixed; inset: 0; z-index: 130;
  background: rgba(0, 0, 0, 0.4); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center; padding: 20px;
}

.picker-box {
  display: flex; flex-direction: column; gap: 12px;
  background: var(--sg-bg-card); border: 1px solid var(--sg-border);
  border-radius: 16px; padding: 20px; box-shadow: var(--sg-shadow-lg);
}

.picker-title { font-size: 14px; font-weight: 700; color: var(--sg-brand); }
</style>
