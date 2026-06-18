<template>
  <div class="live-view">
    <!-- Top bar -->
    <div class="top-bar">
      <button class="back-btn" @click="goBack">
        <Icons icon="chevronLeft" :size="18" color="rgba(255,255,255,0.7)" />
      </button>
      <h1 class="page-title">
        Wettkampf läuft
        <span v-if="instance" class="page-title-name">— {{ instance.templateName }}</span>
      </h1>
      <button class="stop-btn" @click="handleStop">
        <Icons icon="stop" :size="14" color="rgba(252,129,129,0.9)" />
        Beenden
      </button>
    </div>

    <!-- Completed: the competition finished while watching — offer the Rangliste -->
    <div v-if="justCompleted" class="completed-panel">
      <div class="completed-card">
        <Icons icon="check" :size="28" color="rgba(255,255,255,0.9)" />
        <h2 class="completed-title">Wettkampf abgeschlossen</h2>
        <p class="completed-text">Alle Passen sind geschossen.</p>
        <button class="results-link" @click="goToResults">Zur Rangliste</button>
      </div>
    </div>

    <!-- Body -->
    <div v-else class="body">
      <!-- LEFT: ranges -->
      <div class="col col--left">
        <div class="section-label">BEREICHE</div>

        <div v-if="rangeStore.isLoading" class="col-empty">Lade Bereiche…</div>

        <div v-else-if="rangeStore.ranges.length === 0" class="col-empty">
          Keine Bereiche konfiguriert. Bitte einen Administrator kontaktieren.
        </div>

        <div v-else class="range-list">
          <RangePanelCard
            v-for="range in rangeStore.ranges"
            :key="range.id"
            :range="range"
            :instance-id="instanceId"
            @go-to-range="onGoToRange"
          />
        </div>
      </div>

      <!-- RIGHT: scoreboard -->
      <div class="col col--right">
        <div class="section-label">RANGLISTE (live)</div>

        <LocalScoreboard :instance-id="instanceId" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import Icons from '@/components/Icons.vue'
import RangePanelCard from '@/components/competition/RangePanelCard.vue'
import LocalScoreboard from '@/components/competition/LocalScoreboard.vue'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { useRangeStore } from '@/stores/rangeStore.js'

const props = defineProps({
  instanceId: {
    type: String,
    default: null,
  },
})

const router = useRouter()
const competitionEventStore = useCompetitionEventStore()
const rangeStore = useRangeStore()

// ── Computed: current competition instance ────────────────────────────────
const instance = computed(() =>
  props.instanceId ? competitionEventStore.getCompetitionInstance(props.instanceId) : null,
)

// The instance disappears from competitionInstances both when the competition
// completes (moved to completedCompetitionInstances) and when it is abandoned
// (deleted). Distinguish the two: completed → offer the Rangliste; abandoned →
// fall back to the list.
const justCompleted = computed(() =>
  !!props.instanceId &&
  instance.value === null &&
  competitionEventStore.completedCompetitionInstances.some(i => i.instanceId === props.instanceId),
)

// ── Redirect only when the instance is gone for good (abandoned) ───────────
watch(
  instance,
  (val) => {
    if (props.instanceId && val === null && !justCompleted.value) {
      router.push('/wettkampf')
    }
  },
  { immediate: true },
)

// ── Load ranges on mount ──────────────────────────────────────────────────
onMounted(async () => {
  if (rangeStore.ranges.length === 0) {
    await rangeStore.loadApiData()
  }
})

// ── Actions ───────────────────────────────────────────────────────────────
function goBack() {
  router.push('/wettkampf')
}

function goToResults() {
  router.push(`/wettkampf/${props.instanceId}/rangliste`)
}

function handleStop() {
  if (!props.instanceId) return
  if (confirm('Wettkampf wirklich beenden?')) {
    competitionEventStore.stopCompetition(props.instanceId)
    router.push('/wettkampf')
  }
}

function onGoToRange({ rangeId, rotteId }) {
  router.push(
    '/remote/' + rangeId + '?rotteId=' + rotteId + '&instanceId=' + props.instanceId,
  )
}
</script>

<style scoped>
.live-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--sg-brand);
  color: #fff;
}

/* ── Top bar ── */
.top-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.back-btn {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s;
  flex-shrink: 0;
}
.back-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

.page-title {
  font-size: 17px;
  font-weight: 700;
  margin: 0;
  letter-spacing: -0.3px;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.page-title-name {
  font-weight: 400;
  color: rgba(255, 255, 255, 0.55);
}

.stop-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: rgba(252, 129, 129, 0.08);
  border: 1px solid rgba(252, 129, 129, 0.22);
  border-radius: 10px;
  color: rgba(252, 129, 129, 0.9);
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s;
  flex-shrink: 0;
}
.stop-btn:hover {
  background: rgba(252, 129, 129, 0.15);
}

/* ── Completed panel ── */
.completed-panel {
  flex: 1; display: flex; align-items: center; justify-content: center; padding: 24px;
}

.completed-card {
  display: flex; flex-direction: column; align-items: center; gap: 12px; text-align: center;
  background: rgba(255, 255, 255, 0.05); border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 20px; padding: 36px 32px; max-width: 360px; width: 100%;
}

.completed-title { font-size: 20px; font-weight: 800; margin: 4px 0 0; }

.completed-text { font-size: 14px; color: rgba(255, 255, 255, 0.6); margin: 0; }

.results-link {
  margin-top: 8px; min-height: 48px; padding: 12px 28px;
  background: var(--sg-accent, #4299e1); border: none; border-radius: 12px;
  color: #fff; font-size: 16px; font-weight: 700; font-family: inherit; cursor: pointer;
  transition: filter 0.15s;
}
.results-link:hover { filter: brightness(1.1); }

/* ── Body: two-column grid ── */
.body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 55fr 45fr;
  gap: 16px;
  padding: 16px;
  box-sizing: border-box;
}

.col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  min-height: 0;
}

/* ── Section label ── */
.section-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: rgba(255, 255, 255, 0.3);
  text-transform: uppercase;
  flex-shrink: 0;
}

/* ── Range list ── */
.range-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* ── Empty / placeholder states ── */
.col-empty {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.3);
  padding: 4px 0;
}

/* ── Mobile: single column ── */
@media (max-width: 767px) {
  .body {
    grid-template-columns: 1fr;
  }
}
</style>
