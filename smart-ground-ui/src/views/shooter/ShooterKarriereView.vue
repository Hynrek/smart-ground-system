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

      <!-- Serien: flat list of standalone Serie runs -->
      <template v-if="scoreGroup === 'serien'">
        <SerieScoreCard v-for="r in scoreStore.scores" :key="r.id" :serie="r" />
      </template>

      <!-- Passen: group header per Passe, drills into its Serien -->
      <template v-else-if="scoreGroup === 'passen'">
        <div v-for="g in scoreStore.passen" :key="g.key" class="score-group">
          <div class="score-group__header">
            <div class="score-group__main">
              <span class="score-group__name">{{ g.label ?? '—' }}</span>
              <span class="score-group__meta">{{ g.serieCount }} Serien · {{ formatDate(g.lastCompletedAt) }}</span>
            </div>
            <span class="score-group__score">{{ g.totalPoints }}/{{ g.maxPoints }}</span>
            <button
              type="button"
              class="score-group__toggle"
              data-testid="passe-group-toggle"
              :aria-expanded="isPasseExpanded(g.key)"
              :aria-label="isPasseExpanded(g.key) ? 'Serien ausblenden' : 'Serien anzeigen'"
              @click="togglePasse(g.key)"
            >
              <Icons :icon="isPasseExpanded(g.key) ? 'chevron' : 'chevronDown'" :size="16" />
            </button>
          </div>
          <div v-if="isPasseExpanded(g.key)" class="score-group__children">
            <SerieScoreCard v-for="s in g.serien" :key="s.id" :serie="s" />
          </div>
        </div>
      </template>

      <!-- Wettkämpfe: session header → Passe header → Serien -->
      <template v-else>
        <div v-for="w in scoreStore.wettkaempfe" :key="w.key" class="score-group">
          <div class="score-group__header">
            <div class="score-group__main">
              <span class="score-group__name">{{ w.label ?? '—' }}</span>
              <span class="score-group__meta">{{ w.serieCount }} Serien · {{ formatDate(w.lastCompletedAt) }}</span>
            </div>
            <span class="score-group__score">{{ w.totalPoints }}/{{ w.maxPoints }}</span>
            <button
              type="button"
              class="score-group__toggle"
              data-testid="wettkampf-group-toggle"
              :aria-expanded="isWettkampfExpanded(w.key)"
              :aria-label="isWettkampfExpanded(w.key) ? 'Passen ausblenden' : 'Passen anzeigen'"
              @click="toggleWettkampf(w.key)"
            >
              <Icons :icon="isWettkampfExpanded(w.key) ? 'chevron' : 'chevronDown'" :size="16" />
            </button>
          </div>
          <div v-if="isWettkampfExpanded(w.key)" class="score-group__children">
            <div v-for="(p, passeIndex) in w.passen" :key="passeIndex" class="score-subgroup">
              <div class="score-group__header">
                <span class="score-group__name">Passe {{ passeIndex + 1 }}</span>
                <span class="score-group__score">{{ p.totalPoints }}/{{ p.maxPoints }}</span>
                <button
                  type="button"
                  class="score-group__toggle"
                  data-testid="wettkampf-passe-toggle"
                  :aria-expanded="isWettkampfPasseExpanded(w.key, passeIndex)"
                  :aria-label="isWettkampfPasseExpanded(w.key, passeIndex) ? 'Serien ausblenden' : 'Serien anzeigen'"
                  @click="toggleWettkampfPasse(w.key, passeIndex)"
                >
                  <Icons :icon="isWettkampfPasseExpanded(w.key, passeIndex) ? 'chevron' : 'chevronDown'" :size="16" />
                </button>
              </div>
              <div v-if="isWettkampfPasseExpanded(w.key, passeIndex)" class="score-group__children">
                <SerieScoreCard v-for="s in p.serien" :key="s.id" :serie="s" />
              </div>
            </div>
          </div>
        </div>
      </template>

      <div v-if="scoreStore.error" class="save-error">{{ scoreStore.error }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useProfileStore } from '@/stores/profileStore.js'
import { useScoreStore } from '@/stores/scoreStore.js'
import { buildCheckinPayload } from '@/constants/qr.js'
import QRCode from 'qrcode'
import Icons from '@/components/Icons.vue'
import SerieScoreCard from '@/components/shooter/SerieScoreCard.vue'

const router = useRouter()
const profileStore = useProfileStore()
const scoreStore = useScoreStore()
const qrCanvas = ref(null)
const qrRenderError = ref('')
const scoreGroup = ref('serien')

const statsContexts = computed(() =>
  (scoreStore.summary?.contexts ?? []).filter((c) => c.serieCount > 0))

// Drives only the per-tab empty state — the actual rows are rendered directly
// from the level-scoped store fields (scores / passen / wettkaempfe) below.
const currentScoreRows = computed(() => {
  if (scoreGroup.value === 'passen') return scoreStore.passen
  if (scoreGroup.value === 'wettkaempfe') return scoreStore.wettkaempfe
  return scoreStore.scores
})

// Expand/collapse state for the Passen and Wettkämpfe drill-down groups.
// Keyed by group key (Passe) or `${sessionKey}:${passeIndex}` (Wettkampf → Passe).
const expandedPassen = reactive(new Set())
const expandedWettkaempfe = reactive(new Set())
const expandedWettkampfPassen = reactive(new Set())

const isPasseExpanded = (key) => expandedPassen.has(key)
const togglePasse = (key) => {
  if (expandedPassen.has(key)) expandedPassen.delete(key)
  else expandedPassen.add(key)
}

const isWettkampfExpanded = (key) => expandedWettkaempfe.has(key)
const toggleWettkampf = (key) => {
  if (expandedWettkaempfe.has(key)) expandedWettkaempfe.delete(key)
  else expandedWettkaempfe.add(key)
}

const isWettkampfPasseExpanded = (sessionKey, passeIndex) =>
  expandedWettkampfPassen.has(`${sessionKey}:${passeIndex}`)
const toggleWettkampfPasse = (sessionKey, passeIndex) => {
  const k = `${sessionKey}:${passeIndex}`
  if (expandedWettkampfPassen.has(k)) expandedWettkampfPassen.delete(k)
  else expandedWettkampfPassen.add(k)
}

const tabs = [
  { id: 'qr', label: 'QR-Code' },
  { id: 'ergebnisse', label: 'Ergebnisse' },
]
const activeTab = ref('qr')

// Lazy-load tab data on first visit
watch(activeTab, async (tab) => {
  if (tab === 'qr' && !profileStore.qrToken) await profileStore.loadQrToken()
  if (tab === 'ergebnisse') {
    await Promise.all([
      scoreStore.loadSummary(),
      scoreStore.loadScores({ kind: 'SERIE' }),
      scoreStore.loadPassen(),
      scoreStore.loadWettkaempfe(),
    ])
  }
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

.score-group {
  display: flex;
  flex-direction: column;
  gap: var(--sg-space-2);
  padding: var(--sg-space-4);
  border-radius: var(--sg-radius-card);
  border: 1px solid var(--sg-border);
  background: var(--sg-bg-panel);
  margin-bottom: var(--sg-space-3);
}

.score-subgroup {
  display: flex;
  flex-direction: column;
  gap: var(--sg-space-2);
  padding: var(--sg-space-3);
  border-radius: var(--sg-radius-card);
  border: 1px solid var(--sg-border);
  margin-bottom: var(--sg-space-2);
}

.score-group__header {
  display: flex;
  align-items: center;
  gap: var(--sg-space-3);
}

.score-group__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.score-group__name {
  font-weight: 600;
  color: var(--sg-text-primary);
}

.score-group__meta {
  color: var(--sg-text-faint);
  font-size: 0.85rem;
}

.score-group__score {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--sg-text-primary);
}

.score-group__toggle {
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

.score-group__toggle:hover {
  background: var(--sg-accent-subtle);
  color: var(--sg-text-primary);
}

.score-group__toggle:focus-visible {
  outline: 2px solid var(--sg-accent);
  outline-offset: 2px;
}

.score-group__children {
  display: flex;
  flex-direction: column;
  gap: var(--sg-space-2);
  padding-top: var(--sg-space-2);
  border-top: 1px solid var(--sg-border);
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
