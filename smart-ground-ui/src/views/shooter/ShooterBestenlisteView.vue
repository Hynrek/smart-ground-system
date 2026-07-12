<template>
  <div class="bestenliste">
    <div class="bestenliste-header">
      <button class="back-btn" aria-label="Zurück" @click="router.back()">
        <Icons icon="chevronLeft" :size="20" />
      </button>
      <h1 class="page-title">Bestenliste</h1>
    </div>

    <!-- Filters -->
    <div class="filters">
      <div class="metric-tabs" role="tablist" aria-label="Wertung">
        <button :class="['metric-tab', { active: metric === 'best' }]" data-testid="metric-best"
                role="tab" :aria-selected="metric === 'best'" @click="setMetric('best')">Beste Serie</button>
        <button :class="['metric-tab', { active: metric === 'average' }]" data-testid="metric-average"
                role="tab" :aria-selected="metric === 'average'" @click="setMetric('average')">Durchschnitt</button>
      </div>
      <label class="filter-field">
        <span class="filter-label">Kontext</span>
        <select v-model="context" @change="reload">
          <option value="">Alle</option>
          <option value="TRAINING">Training</option>
          <option value="COMPETITION">Wettkampf</option>
        </select>
      </label>
      <label class="filter-field">
        <span class="filter-label">Platz</span>
        <select v-model="rangeId" data-testid="range-filter" @change="reload">
          <option value="">Alle Plätze</option>
          <option v-for="r in rangeStore.ranges" :key="r.id" :value="r.id">{{ r.name }}</option>
        </select>
      </label>
      <label class="filter-field">
        <span class="filter-label">Serie</span>
        <select v-model="serieId" data-testid="serie-filter" @change="reload">
          <option value="">Alle Serien</option>
          <option v-for="s in passeStore.savedSerien" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
      </label>
    </div>

    <p v-if="scoreStore.error" class="error-text">{{ scoreStore.error }}</p>
    <p v-else-if="entries.length === 0" class="empty-text">
      Noch keine Einträge — schiesse eine Serie, um auf der Bestenliste zu erscheinen.
    </p>

    <ol v-else class="board">
      <li v-for="(entry, index) in entries" :key="entry.userId" class="board-row" data-testid="leaderboard-row">
        <span class="board-rank">{{ index + 1 }}</span>
        <div class="board-main">
          <span class="board-name">{{ entry.displayName }}</span>
          <span class="board-meta">{{ entry.serieCount }} Serien · {{ entry.totalPoints }}/{{ entry.maxPoints }} Punkte</span>
        </div>
        <span class="board-score">{{ Math.round(metric === 'average' ? entry.averagePercent : entry.bestPercent) }}%</span>
      </li>
    </ol>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useScoreStore } from '@/stores/scoreStore.js'
import { useRangeStore } from '@/stores/rangeStore.js'
import { usePasseStore } from '@/stores/passeStore.js'
import Icons from '@/components/Icons.vue'

const router = useRouter()
const scoreStore = useScoreStore()
const rangeStore = useRangeStore()
const passeStore = usePasseStore()
const metric = ref('best')
const context = ref('')
const rangeId = ref('')
const serieId = ref('')

const entries = computed(() => scoreStore.leaderboard?.entries ?? [])

function reload() {
  const params = { metric: metric.value, limit: 25 }
  if (context.value) params.context = context.value
  if (rangeId.value) params.rangeId = rangeId.value
  if (serieId.value) params.serieId = serieId.value
  scoreStore.loadLeaderboard(params)
}

function setMetric(value) {
  metric.value = value
  reload()
}

onMounted(() => {
  reload()
  if (rangeStore.ranges.length === 0) rangeStore.initialize()
  if (passeStore.savedSerien.length === 0) passeStore.loadSerienFromStorage()
})
</script>

<style scoped>
.bestenliste { max-width: 720px; margin: 0 auto; padding: 24px 16px; }
.bestenliste-header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.back-btn {
  background: none; border: none; cursor: pointer; padding: 6px;
  display: flex; align-items: center; border-radius: 8px; color: var(--sg-text-faint);
  transition: background 0.15s;
}
.back-btn:hover { background: rgba(255, 255, 255, 0.07); }
.page-title { color: var(--sg-text-primary); margin: 0; }
.filters { display: flex; gap: 16px; flex-wrap: wrap; align-items: end; margin-bottom: 20px; }
.metric-tabs { display: flex; gap: 8px; }
.metric-tab {
  min-height: 48px; padding: 0 18px; border: 1px solid transparent; border-radius: 10px;
  background: var(--sg-bg-panel); color: var(--sg-text-muted); font-size: 1rem; cursor: pointer;
}
.metric-tab.active { color: var(--sg-text-primary); border-color: var(--sg-accent); }
.metric-tab:focus-visible, .board-row:focus-visible { outline: 2px solid var(--sg-accent); outline-offset: 2px; }
.filter-field { display: flex; flex-direction: column; gap: 4px; color: var(--sg-text-muted); }
.filter-label { font-size: 0.8rem; }
.filter-field select {
  min-height: 48px; padding: 0 12px; border-radius: 10px;
  background: var(--sg-bg-panel); color: var(--sg-text-primary); border: 1px solid var(--sg-bg-panel);
}
.error-text, .empty-text { color: var(--sg-text-muted); }
.board { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.board-row {
  display: flex; align-items: center; gap: 14px; min-height: 56px;
  padding: 10px 16px; border-radius: 12px; background: var(--sg-bg-card);
}
.board-rank { width: 28px; font-size: 1.2rem; font-weight: 700; color: var(--sg-text-faint); }
.board-main { flex: 1; display: flex; flex-direction: column; }
.board-name { color: var(--sg-text-primary); font-weight: 600; }
.board-meta { color: var(--sg-text-faint); font-size: 0.85rem; }
.board-score { font-size: 1.3rem; font-weight: 700; color: var(--sg-accent); }
</style>
