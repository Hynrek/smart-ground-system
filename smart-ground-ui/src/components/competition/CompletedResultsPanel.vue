<!-- src/components/competition/CompletedResultsPanel.vue -->
<template>
  <div class="panel-content">
    <div class="panel-header">
      <div>
        <h2 class="panel-title">Rangliste</h2>
        <span v-if="completedAtLabel" class="panel-subtitle">Abgeschlossen am {{ completedAtLabel }}</span>
      </div>
      <button
        class="export-btn"
        :disabled="standings.length === 0 || exporting"
        @click="handleExport"
      >
        <Icons icon="download" :size="14" />
        Export (CSV)
      </button>
    </div>

    <!-- Loading -->
    <div v-if="loading && standings.length === 0" class="state-note">Ergebnisse werden geladen…</div>

    <!-- Error -->
    <div v-else-if="error" class="state-note state-error">
      <span>Ergebnisse konnten nicht geladen werden.</span>
      <button class="retry-btn" @click="load">Erneut versuchen</button>
    </div>

    <!-- Empty -->
    <div v-else-if="standings.length === 0" class="state-note">Noch keine Ergebnisse.</div>

    <!-- Standings -->
    <div v-else class="standings">
      <div
        v-for="row in standings"
        :key="row.playerId"
        class="standing-block"
      >
        <button
          class="standing-row"
          :class="[`rank-${row.rank}`, { expanded: expandedId === row.playerId }]"
          @click="toggle(row.playerId)"
        >
          <span class="rank" :class="{ medal: row.rank <= 3 }">{{ row.rank }}</span>
          <span class="name">{{ row.displayName || '–' }}</span>
          <span v-if="row.rotteName" class="rotte-chip">{{ row.rotteName }}</span>
          <span v-if="row.tieResolvedByStechen" class="stechen-badge">Stechen</span>
          <span class="score">{{ row.totalScore }} / {{ row.maxScore }}</span>
          <Icons icon="chevronDown" :size="14" class="chevron" :class="{ open: expandedId === row.playerId }" />
        </button>

        <div v-if="expandedId === row.playerId" class="player-detail">
          <div v-if="detailFor(row.playerId).passen.length === 0" class="detail-empty">
            Keine Detailauswertung verfügbar — Gesamt {{ detailFor(row.playerId).total }} / {{ detailFor(row.playerId).max }}
          </div>
          <div
            v-for="passe in detailFor(row.playerId).passen"
            :key="passe.label"
            class="passe-line"
          >
            <span class="passe-label">{{ passe.label }}</span>
            <span class="passe-pts">{{ passe.totalPoints }} / {{ passe.maxPoints }}</span>
          </div>
          <StepScorecard
            v-if="stepsFor(row.playerId).length > 0"
            class="step-detail"
            :passen="stepsFor(row.playerId)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import Icons from '@/components/Icons.vue'
import StepScorecard from '@/components/competition/StepScorecard.vue'
import { useCompletedResults } from '@/composables/useCompletedResults.js'
import { exportLeaderboard } from '@/services/wettkampfApi.js'

const props = defineProps({ event: { type: Object, required: true } })

const sessionId = computed(() => props.event.id)
const { standings, completedAt, loading, error, load, getPlayerDetail, getPlayerSteps } = useCompletedResults(sessionId)

const expandedId = ref(null)
const exporting = ref(false)

// Cache per-player detail so repeated renders don't re-parse the JSON blobs.
const detailCache = new Map()
const detailFor = (playerId) => {
  if (!detailCache.has(playerId)) detailCache.set(playerId, getPlayerDetail(playerId))
  return detailCache.get(playerId)
}

const stepsCache = new Map()
const stepsFor = (playerId) => {
  if (!stepsCache.has(playerId)) stepsCache.set(playerId, getPlayerSteps(playerId))
  return stepsCache.get(playerId)
}

const toggle = (playerId) => {
  expandedId.value = expandedId.value === playerId ? null : playerId
}

const completedAtLabel = computed(() =>
  completedAt.value ? new Date(completedAt.value).toLocaleDateString('de-CH') : null,
)

const handleExport = async () => {
  exporting.value = true
  try {
    await exportLeaderboard(props.event.id, 'csv')
  } catch (e) {
    console.error('[CompletedResultsPanel] export failed:', e)
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  detailCache.clear()
  stepsCache.clear()
  load()
})
</script>

<style scoped>
.panel-content { flex: 1; overflow-y: auto; padding: 24px 28px 40px; display: flex; flex-direction: column; gap: 16px; }

.panel-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }

.panel-title { font-size: 18px; font-weight: 700; color: var(--sg-brand); margin: 0; }

.panel-subtitle { font-size: 12px; color: var(--sg-text-faint); }

.export-btn {
  display: flex; align-items: center; gap: 6px;
  background: var(--sg-accent-subtle); border: 1px solid var(--sg-color-info-bg);
  border-radius: 10px; padding: 7px 14px;
  color: var(--sg-color-info-text); font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: background 0.15s; white-space: nowrap;
}
.export-btn:hover:not(:disabled) { background: var(--sg-accent-tint); }
.export-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.state-note {
  padding: 28px; text-align: center; color: var(--sg-text-faint); font-size: 14px;
  border: 1px dashed var(--sg-border); border-radius: 12px;
  display: flex; flex-direction: column; align-items: center; gap: 12px;
}
.state-error { color: var(--sg-color-warning-text); border-color: color-mix(in srgb, var(--sg-color-warning) 40%, transparent); }

.retry-btn {
  background: transparent; border: 1px solid var(--sg-border); border-radius: 8px;
  padding: 6px 14px; color: var(--sg-text-muted); font-size: 13px; font-family: inherit; cursor: pointer;
}
.retry-btn:hover { background: var(--sg-bg-panel); }

.standings { display: flex; flex-direction: column; gap: 8px; }

.standing-block {
  background: var(--sg-bg-card); border: 1px solid var(--sg-border);
  border-radius: 12px; overflow: hidden; box-shadow: var(--sg-shadow-sm);
}

.standing-row {
  display: grid; grid-template-columns: 36px 1fr auto auto auto 18px;
  align-items: center; gap: 12px; width: 100%;
  padding: 12px 16px; background: transparent; border: none; font-family: inherit;
  cursor: pointer; text-align: left; transition: background 0.12s;
}
.standing-row:hover { background: var(--sg-bg-panel); }

.rank { font-size: 14px; font-weight: 700; color: var(--sg-text-muted); text-align: center; }
.rank.medal { color: var(--sg-brand); }
.rank-1 .rank.medal { color: #d4af37; }
.rank-2 .rank.medal { color: #9aa3ad; }
.rank-3 .rank.medal { color: #cd7f32; }

.name { font-size: 14px; font-weight: 600; color: var(--sg-brand); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.rotte-chip {
  font-size: 11px; font-weight: 600; padding: 2px 10px; border-radius: 20px;
  background: var(--sg-bg-panel); border: 1px solid var(--sg-border); color: var(--sg-text-muted);
}

.stechen-badge {
  font-size: 11px; font-weight: 700; padding: 2px 10px; border-radius: 20px;
  background: var(--sg-color-warning-bg); color: var(--sg-color-warning-text);
  border: 1px solid color-mix(in srgb, var(--sg-color-warning) 40%, transparent);
}

.score { font-size: 14px; font-weight: 700; color: var(--sg-brand); white-space: nowrap; }

.chevron { color: var(--sg-text-faint); transition: transform 0.15s; }
.chevron.open { transform: rotate(180deg); }

.player-detail {
  padding: 6px 16px 14px; display: flex; flex-direction: column; gap: 4px;
  border-top: 1px solid var(--sg-border); background: var(--sg-bg-panel);
}

.detail-empty { font-size: 12px; color: var(--sg-text-faint); padding: 6px 0; }

.passe-line { display: flex; align-items: center; justify-content: space-between; padding: 4px 0; }
.passe-label { font-size: 12px; color: var(--sg-text-muted); }
.passe-pts { font-size: 12px; font-weight: 600; color: var(--sg-brand); }

.step-detail { margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--sg-border); }
</style>
