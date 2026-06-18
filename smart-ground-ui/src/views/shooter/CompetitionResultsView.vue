<!-- src/views/shooter/CompetitionResultsView.vue -->
<template>
  <div class="results-view">
    <!-- Top bar -->
    <div class="top-bar">
      <button class="back-btn" @click="goBack">
        <Icons icon="chevronLeft" :size="18" color="rgba(255,255,255,0.7)" />
      </button>
      <h1 class="page-title">Rangliste</h1>
    </div>

    <div class="body">
      <!-- Loading -->
      <div v-if="loading && standings.length === 0" class="state-note">Ergebnisse werden geladen…</div>

      <!-- Error -->
      <div v-else-if="error" class="state-note">
        <span>Ergebnisse konnten nicht geladen werden.</span>
        <button class="retry-btn" @click="load">Erneut versuchen</button>
      </div>

      <!-- Empty -->
      <div v-else-if="standings.length === 0" class="state-note">Noch keine Ergebnisse.</div>

      <!-- Standings -->
      <div v-else class="standings">
        <div v-for="row in standings" :key="row.playerId" class="standing-block">
          <button
            class="standing-row"
            :class="[`rank-${row.rank}`, { 'is-me': isMe(row), expanded: expandedId === row.playerId }]"
            @click="toggle(row.playerId)"
          >
            <span class="rank" :class="{ medal: row.rank <= 3 }">{{ row.rank }}</span>
            <span class="name">
              {{ row.displayName || '–' }}
              <span v-if="isMe(row)" class="me-tag">Du</span>
            </span>
            <span v-if="row.tieResolvedByStechen" class="stechen-badge">Stechen</span>
            <span class="score">{{ row.totalScore }}<span class="max">/{{ row.maxScore }}</span></span>
          </button>

          <div v-if="expandedId === row.playerId" class="player-detail">
            <div v-if="detailFor(row.playerId).passen.length === 0" class="detail-empty">
              Keine Detailauswertung — Gesamt {{ detailFor(row.playerId).total }} / {{ detailFor(row.playerId).max }}
            </div>
            <div
              v-for="passe in detailFor(row.playerId).passen"
              :key="passe.label"
              class="passe-line"
            >
              <span class="passe-label">{{ passe.label }}</span>
              <span class="passe-pts">{{ passe.totalPoints }} / {{ passe.maxPoints }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icons from '@/components/Icons.vue'
import { useCompletedResults } from '@/composables/useCompletedResults.js'
import { useAuthStore } from '@/stores/authStore.js'

const props = defineProps({ id: { type: String, required: true } })

const router = useRouter()
const authStore = useAuthStore()

const sessionId = computed(() => props.id)
const { standings, loading, error, load, getPlayerDetail } = useCompletedResults(sessionId)

const expandedId = ref(null)
const detailCache = new Map()
const detailFor = (playerId) => {
  if (!detailCache.has(playerId)) detailCache.set(playerId, getPlayerDetail(playerId))
  return detailCache.get(playerId)
}

const isMe = (row) => !!authStore.profile?.id && row.userId === authStore.profile.id

const toggle = (playerId) => {
  expandedId.value = expandedId.value === playerId ? null : playerId
}

const goBack = () => router.push('/wettkampf')

onMounted(() => {
  detailCache.clear()
  load()
})
</script>

<style scoped>
.results-view {
  flex: 1; display: flex; flex-direction: column; min-height: 0;
  background: var(--sg-brand); color: #fff;
}

.top-bar {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 16px; border-bottom: 1px solid rgba(255, 255, 255, 0.08); flex-shrink: 0;
}

.back-btn {
  background: rgba(255, 255, 255, 0.06); border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px; width: 48px; height: 48px;
  display: flex; align-items: center; justify-content: center; cursor: pointer; flex-shrink: 0;
  transition: background 0.15s;
}
.back-btn:hover { background: rgba(255, 255, 255, 0.12); }

.page-title { font-size: 22px; font-weight: 800; margin: 0; letter-spacing: -0.3px; }

.body { flex: 1; min-height: 0; overflow-y: auto; padding: 16px; }

.state-note {
  margin-top: 24px; padding: 32px; text-align: center;
  color: rgba(255, 255, 255, 0.5); font-size: 16px;
  border: 1px dashed rgba(255, 255, 255, 0.15); border-radius: 16px;
  display: flex; flex-direction: column; align-items: center; gap: 16px;
}

.retry-btn {
  background: rgba(255, 255, 255, 0.08); border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 12px; padding: 12px 24px; min-height: 48px;
  color: #fff; font-size: 15px; font-family: inherit; cursor: pointer;
}

.standings { display: flex; flex-direction: column; gap: 10px; }

.standing-block {
  background: rgba(255, 255, 255, 0.05); border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px; overflow: hidden;
}

.standing-row {
  display: grid; grid-template-columns: 48px 1fr auto auto;
  align-items: center; gap: 14px; width: 100%; min-height: 64px;
  padding: 14px 18px; background: transparent; border: none; font-family: inherit;
  cursor: pointer; text-align: left; color: #fff; transition: background 0.12s;
}
.standing-row:hover { background: rgba(255, 255, 255, 0.04); }

.standing-row.is-me {
  background: rgba(255, 255, 255, 0.1);
  box-shadow: inset 3px 0 0 var(--sg-accent, #4299e1);
}

.rank { font-size: 20px; font-weight: 800; text-align: center; color: rgba(255, 255, 255, 0.6); }
.rank.medal { color: #fff; }
.rank-1 .rank.medal { color: #ffd75e; }
.rank-2 .rank.medal { color: #cfd6dd; }
.rank-3 .rank.medal { color: #e3a06a; }

.name { font-size: 18px; font-weight: 600; display: flex; align-items: center; gap: 8px; overflow: hidden; text-overflow: ellipsis; }

.me-tag {
  font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 10px;
  background: var(--sg-accent, #4299e1); color: #fff; flex-shrink: 0;
}

.stechen-badge {
  font-size: 11px; font-weight: 700; padding: 3px 10px; border-radius: 12px;
  background: rgba(246, 173, 85, 0.18); color: #f6ad55; border: 1px solid rgba(246, 173, 85, 0.35);
}

.score { font-size: 22px; font-weight: 800; white-space: nowrap; }
.score .max { font-size: 14px; font-weight: 600; color: rgba(255, 255, 255, 0.5); }

.player-detail {
  padding: 8px 18px 16px; display: flex; flex-direction: column; gap: 6px;
  border-top: 1px solid rgba(255, 255, 255, 0.08); background: rgba(0, 0, 0, 0.12);
}
.detail-empty { font-size: 14px; color: rgba(255, 255, 255, 0.5); padding: 6px 0; }
.passe-line { display: flex; align-items: center; justify-content: space-between; padding: 6px 0; }
.passe-label { font-size: 15px; color: rgba(255, 255, 255, 0.7); }
.passe-pts { font-size: 15px; font-weight: 700; }
</style>
