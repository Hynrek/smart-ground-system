<template>
  <div class="karriere-view">
    <!-- Header -->
    <div class="karriere-header">
      <button class="back-btn" aria-label="Zurück" @click="router.back()">
        <Icons icon="chevronLeft" :size="20" />
      </button>
      <div class="header-info">
        <div class="header-title">Karriere</div>
        <div class="header-sub">QR-Code & Ergebnisse</div>
      </div>
    </div>

    <!-- Tabs -->
    <div class="tab-bar" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        role="tab"
        :aria-selected="activeTab === tab.id"
        :class="['tab-btn', { active: activeTab === tab.id }]"
        @click="activeTab = tab.id"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- Tab: QR-Code -->
    <div v-if="activeTab === 'qr'" class="tab-content" role="tabpanel">
      <div class="qr-section">
        <p class="qr-explainer">
          Zeige diesen Code am Stand, um dich einer Gruppe anzuschliessen.
          Deine Ergebnisse werden deinem Konto zugeordnet.
        </p>
        <canvas ref="qrCanvas" class="qr-canvas" />
        <button class="save-btn qr-rotate-btn" :disabled="profileStore.isLoading" @click="rotateQr">
          Code erneuern
        </button>
        <p class="qr-rotate-hint">Beim Erneuern wird der alte Code sofort ungültig.</p>
        <div v-if="qrRenderError" class="save-error">{{ qrRenderError }}</div>
        <div v-if="profileStore.error" class="save-error">{{ profileStore.error }}</div>
      </div>
    </div>

    <!-- Tab: Ergebnisse -->
    <div v-if="activeTab === 'ergebnisse'" class="tab-content" role="tabpanel">
      <!-- Stats header -->
      <div v-if="statsContexts.length" class="score-stats">
        <div v-for="c in statsContexts" :key="c.context" class="score-stat-card">
          <span class="score-stat-label">{{ c.context === 'TRAINING' ? 'Training' : 'Wettkampf' }}</span>
          <span class="score-stat-value">{{ Math.round(c.averagePercent) }}<small>% Ø</small></span>
          <span class="score-stat-sub">Beste Serie {{ Math.round(c.bestPercent) }}% · {{ c.serieCount }} Serien</span>
        </div>
      </div>

      <!-- Grouping tabs -->
      <div class="score-group-tabs" role="tablist" aria-label="Ergebnis-Gruppierung">
        <button :class="['score-group-tab', { active: scoreGroup === 'serien' }]"
                data-testid="score-group-serien" role="tab" :aria-selected="scoreGroup === 'serien'"
                @click="scoreGroup = 'serien'">Serien</button>
        <button :class="['score-group-tab', { active: scoreGroup === 'passen' }]"
                data-testid="score-group-passen" role="tab" :aria-selected="scoreGroup === 'passen'"
                @click="scoreGroup = 'passen'">Passen</button>
        <button :class="['score-group-tab', { active: scoreGroup === 'wettkaempfe' }]"
                data-testid="score-group-wettkaempfe" role="tab" :aria-selected="scoreGroup === 'wettkaempfe'"
                @click="scoreGroup = 'wettkaempfe'">Wettkämpfe</button>
      </div>

      <p v-if="currentScoreRows.length === 0" class="empty-results">
        Noch keine Ergebnisse — checke dich am Stand per QR-Code ein.
      </p>

      <!-- Serien: individual rows -->
      <template v-if="scoreGroup === 'serien'">
        <div v-for="r in scoreStore.scores" :key="r.id" class="result-row">
          <div class="result-main">
            <span class="result-name">{{ r.serieAlias }}</span>
            <span class="result-meta">{{ r.parentName ?? '—' }} · {{ r.rangeName ?? '—' }} · {{ formatDate(r.completedAt) }}</span>
          </div>
          <span class="result-score">{{ r.totalPoints }}/{{ r.maxPoints }}</span>
        </div>
      </template>

      <!-- Passen / Wettkämpfe: grouped rows -->
      <template v-else>
        <div v-for="g in currentScoreRows" :key="g.key" class="result-row">
          <div class="result-main">
            <span class="result-name">{{ g.label ?? '—' }}</span>
            <span class="result-meta">{{ g.serieCount }} Serien · {{ formatDate(g.lastCompletedAt) }}</span>
          </div>
          <span class="result-score">{{ g.totalPoints }}/{{ g.maxPoints }}</span>
        </div>
      </template>

      <div v-if="scoreStore.error" class="save-error">{{ scoreStore.error }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useProfileStore } from '@/stores/profileStore.js'
import { useScoreStore } from '@/stores/scoreStore.js'
import { buildCheckinPayload } from '@/constants/qr.js'
import QRCode from 'qrcode'
import Icons from '@/components/Icons.vue'

const router = useRouter()
const profileStore = useProfileStore()
const scoreStore = useScoreStore()
const qrCanvas = ref(null)
const qrRenderError = ref('')
const scoreGroup = ref('serien')

const statsContexts = computed(() =>
  (scoreStore.summary?.contexts ?? []).filter((c) => c.serieCount > 0))

const currentScoreRows = computed(() => {
  if (scoreGroup.value === 'passen') return scoreStore.summary?.passen ?? []
  if (scoreGroup.value === 'wettkaempfe') return scoreStore.summary?.wettkaempfe ?? []
  return scoreStore.scores
})

const tabs = [
  { id: 'qr', label: 'QR-Code' },
  { id: 'ergebnisse', label: 'Ergebnisse' },
]
const activeTab = ref('qr')

// Lazy-load tab data on first visit
watch(activeTab, async (tab) => {
  if (tab === 'qr' && !profileStore.qrToken) await profileStore.loadQrToken()
  if (tab === 'ergebnisse') await Promise.all([scoreStore.loadSummary(), scoreStore.loadScores()])
}, { immediate: true })

// Re-render the QR canvas whenever the token changes (initial load + rotation)
watch(() => profileStore.qrToken, async (token) => {
  if (!token) return
  await nextTick()
  if (!qrCanvas.value) return
  qrRenderError.value = ''
  try {
    await QRCode.toCanvas(qrCanvas.value, buildCheckinPayload(token), { width: 240, margin: 1 })
  } catch {
    qrRenderError.value = 'QR-Code konnte nicht angezeigt werden — bitte Seite neu laden'
  }
})

async function rotateQr() {
  await profileStore.rotateQrToken()
}

function formatDate(value) {
  if (!value) return '—'
  return new Date(value).toLocaleDateString('de-CH', { day: '2-digit', month: '2-digit', year: 'numeric' })
}
</script>

<style scoped>
.karriere-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.karriere-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--sg-border);
}

.back-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--sg-text-faint);
  padding: 4px;
  display: flex;
  align-items: center;
  border-radius: 6px;
  transition: background 0.15s;
}

.back-btn:hover {
  background: rgba(255, 255, 255, 0.07);
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--sg-text-primary);
}

.header-sub {
  font-size: 12px;
  color: var(--sg-text-faint);
}

.tab-bar {
  display: flex;
  border-bottom: 1px solid var(--sg-border);
  padding: 0 20px;
}

.tab-btn {
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  color: var(--sg-text-faint);
  cursor: pointer;
  font-family: inherit;
  font-size: 13px;
  margin-bottom: -1px;
  padding: 12px 14px;
  transition: color 0.15s, border-color 0.15s;
}

.tab-btn.active {
  border-bottom-color: var(--sg-accent);
  color: var(--sg-accent);
}

.tab-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.save-error {
  color: var(--sg-color-danger, #f87171);
  font-size: 13px;
  margin-top: 8px;
}

.save-btn {
  margin-top: 20px;
  width: 100%;
  padding: 12px;
  background: var(--sg-accent);
  border: none;
  border-radius: 10px;
  color: var(--sg-brand, #0f1a2e);
  cursor: pointer;
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  transition: opacity 0.15s;
}

.save-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.save-btn:not(:disabled):hover {
  opacity: 0.9;
}

.qr-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 16px;
}

.qr-explainer,
.qr-rotate-hint {
  color: var(--sg-text-muted);
  font-size: 0.9rem;
  text-align: center;
  max-width: 320px;
  margin: 0;
}

.qr-canvas {
  background: #fff;
  border-radius: 12px;
  padding: 8px;
}

.empty-results {
  color: var(--sg-text-muted);
  text-align: center;
  padding: 24px 16px;
}

.result-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--sg-border);
}

.result-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.result-name {
  font-weight: 600;
}

.result-meta {
  color: var(--sg-text-faint);
  font-size: 0.85rem;
}

.result-score {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.score-stats {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.score-stat-card {
  flex: 1 1 140px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 12px 16px;
  border-radius: 12px;
  background: var(--sg-bg-panel);
}

.score-stat-label {
  font-size: 0.8rem;
  color: var(--sg-text-muted);
}

.score-stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--sg-text-primary);
}

.score-stat-value small {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--sg-text-muted);
}

.score-stat-sub {
  font-size: 0.8rem;
  color: var(--sg-text-faint);
}

.score-group-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.score-group-tab {
  min-height: 48px;
  padding: 0 18px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: var(--sg-bg-panel);
  color: var(--sg-text-muted);
  font-size: 1rem;
  cursor: pointer;
}

.score-group-tab.active {
  color: var(--sg-text-primary);
  border-color: var(--sg-accent);
}

.score-group-tab:focus-visible {
  outline: 2px solid var(--sg-accent);
  outline-offset: 2px;
}
</style>
