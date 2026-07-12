<!-- src/components/competition/ActiveCompetitionPanel.vue -->
<template>
  <div class="panel-content">

    <!-- Header with abort button only -->
    <div class="panel-header">
      <h2 class="panel-title">Laufender Wettkampf</h2>
      <button v-if="showStop" class="stop-btn" @click="emit('stop')">
        <Icons icon="x" :size="13" color="rgba(252,129,129,0.8)" />
        Wettkampf abbrechen
      </button>
    </div>

    <!-- Passen progress bar -->
    <div v-if="passenProgress.length > 0" class="passen-progress">
      <div class="progress-header">
        <span class="progress-title">Passen</span>
        <span class="progress-count">{{ passenDoneCount }} / {{ passenProgress.length }} abgeschlossen</span>
      </div>
      <div class="progress-track">
        <div
          v-for="passe in passenProgress"
          :key="passe.phaseIndex"
          class="progress-segment"
          :class="`seg-${passe.status}`"
        />
      </div>
      <div class="progress-labels">
        <span
          v-for="passe in passenProgress"
          :key="passe.phaseIndex"
          class="progress-name"
          :class="`pname-${passe.status}`"
        >{{ passe.passeName }}</span>
      </div>
    </div>

    <!-- Admin Passe gate -->
    <button
      v-if="(event.passen?.length ?? 0) > 1"
      class="release-passe-btn"
      :disabled="!canReleaseNextPasse || releasing"
      @click="releaseNext"
    >
      Nächste Passe freigeben
    </button>

    <!-- Tabs -->
    <div class="tab-bar">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'fortschritt' }"
        @click="setTab('fortschritt', { replace: true })"
      >
        Fortschritt
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'rangliste' }"
        @click="setTab('rangliste', { replace: true })"
      >
        Rangliste
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'rotten' }"
        @click="setTab('rotten', { replace: true })"
      >
        Rotten
      </button>
    </div>

    <!-- Rotten tab -->
    <div v-if="activeTab === 'rotten'" class="rotten-overview">
      <div
        v-for="rotte in rottenRows"
        :key="rotte.id"
        class="rotte-overview-card"
      >
        <div class="rotte-ov-header">
          <span class="rotte-ov-name">{{ rotte.name }}</span>
          <span v-if="rotte.currentPasse" class="rotte-ov-passe">{{ rotte.currentPasse }}</span>
          <span class="rotte-ov-chip" :class="`chip-${rotte.status}`">{{ rotteStatusLabel(rotte.status) }}</span>
        </div>
        <div class="rotte-ov-members">
          <span
            v-for="member in rotte.members"
            :key="member.id"
            class="member-chip"
          >{{ member.displayName }}</span>
          <span v-if="rotte.members.length === 0" class="member-empty">Keine Schützen</span>
        </div>
      </div>
      <div v-if="rottenRows.length === 0" class="empty-state">Keine Rotten vorhanden</div>
    </div>

    <!-- Serien tab -->
    <div v-if="activeTab === 'fortschritt'" class="serien-view">
      <div
        v-for="card in serieCards"
        :key="card.serieAlias"
        class="serie-card"
      >
        <div class="serie-card-header">{{ card.headerLabel }}</div>
        <div
          v-for="row in card.rotteRows"
          :key="row.rotteId"
          class="rotte-row"
        >
          <span class="rotte-name">{{ row.rotteName }}</span>
          <span class="rotte-chip" :class="`chip-${row.status}`">
            {{ rowStatusLabel(row.status) }}
          </span>
        </div>
      </div>
      <div v-if="serieCards.length === 0" class="empty-state">Keine Serien in dieser Passe</div>
    </div>

    <!-- Rangliste tab -->
    <div v-if="activeTab === 'rangliste'" class="rangliste-view">
      <!-- PRE_COMPLETE: editable completed-results view (expandable detail + correction chips) -->
      <CompletedResultsPanel v-if="isPreComplete" :event="event" editable />
      <template v-else>
        <div
          v-for="(entry, i) in leaderboard"
          :key="entry.playerId"
          class="rangliste-row"
        >
          <span class="rank">{{ i + 1 }}</span>
          <span class="player-name">{{ entry.displayName }}</span>
          <span v-if="tiedPlayerIds.has(entry.playerId)" class="tie-flag">Gleichstand</span>
          <span class="score">{{ entry.totalPoints }} / {{ entry.maxPoints }}</span>
        </div>
        <div v-if="leaderboard.length === 0" class="empty-state">Noch keine Ergebnisse</div>
      </template>
    </div>

  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import Icons from '@/components/Icons.vue'
import { usePasseStore } from '@/stores/passeStore.js'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import CompletedResultsPanel from '@/components/competition/CompletedResultsPanel.vue'
import { getProgress, getLeaderboard } from '@/services/wettkampfApi.js'
import { useUrlTab } from '@/composables/useUrlTab.js'

const props = defineProps({
  event: { type: Object, required: true },
  // Tied blocks from the Stechen ties view; used to flag tied rows in the leaderboard.
  tiedBlocks: { type: Array, default: () => [] },
  // Hide "Wettkampf abbrechen" in views where cancelling shouldn't be offered (e.g. Auswertung).
  showStop: { type: Boolean, default: true },
})
const emit = defineEmits(['stop'])

// Set of player ids that share a rank in an unresolved tied block.
const tiedPlayerIds = computed(() => {
  const ids = new Set()
  for (const block of props.tiedBlocks) {
    if (block.resolved) continue
    for (const player of block.players ?? []) ids.add(player.playerId)
  }
  return ids
})

const passeStore = usePasseStore()
const competitionStore = useCompetitionEventStore()
const { activeTab, setTab } = useUrlTab('fortschritt', ['fortschritt', 'rangliste', 'rotten'])

const isPreComplete = computed(() => props.event.status?.toUpperCase() === 'PRE_COMPLETE')

const progressData = ref(null)
const leaderboardData = ref(null)
let pollInterval = null

const refresh = async () => {
  if (!props.event?.id) return
  try {
    const [progress, leaderboard] = await Promise.all([
      getProgress(props.event.id),
      getLeaderboard(props.event.id),
    ])
    progressData.value = progress
    leaderboardData.value = leaderboard
  } catch (e) {
    console.error('[ActiveCompetitionPanel] poll failed:', e)
  }
}

// ── Admin Passe gate ────────────────────────────────────────────────────────
const releasedPasseIndex = computed(() => progressData.value?.releasedPasseIndex ?? 0)

const releasedPasseComplete = computed(() => {
  const groups = progressData.value?.groups ?? []
  if (groups.length === 0) return false
  return groups.every(g => g.completions?.[releasedPasseIndex.value]?.completed === true)
})

const canReleaseNextPasse = computed(() =>
  releasedPasseComplete.value && releasedPasseIndex.value + 1 < (props.event.passen?.length ?? 0),
)

const releasing = ref(false)
const releaseNext = async () => {
  if (!canReleaseNextPasse.value || releasing.value) return
  releasing.value = true
  try {
    await competitionStore.releaseNextPasse(props.event.id)
    await refresh()
  } catch (e) {
    console.error('[ActiveCompetitionPanel] release passe failed:', e)
  } finally {
    releasing.value = false
  }
}

onMounted(async () => {
  refresh()
  pollInterval = setInterval(refresh, 4000)
  if (passeStore.savedGlobalPassen.length === 0) {
    await passeStore.loadPassenFromStorage()
  }
})

onUnmounted(() => clearInterval(pollInterval))

// ── Passen progress ────────────────────────────────────────────────────────────

const passenProgress = computed(() => {
  const passen = props.event.passen ?? []
  if (passen.length === 0) return []
  const groups = progressData.value?.groups ?? []
  let foundActive = false
  return passen.map((passe, idx) => {
    const allDone = groups.length > 0 && groups.every(g => g.completions?.[idx]?.completed === true)
    let status
    if (allDone) {
      status = 'fertig'
    } else if (!foundActive) {
      status = 'aktiv'
      foundActive = true
    } else {
      status = 'offen'
    }
    return { phaseIndex: idx, passeName: passe.name, status }
  })
})

const passenDoneCount = computed(() => passenProgress.value.filter(p => p.status === 'fertig').length)

const activePasseIndex = computed(() => {
  const idx = passenProgress.value.findIndex(p => p.status === 'aktiv')
  return idx >= 0 ? idx : 0
})

// ── Rotten overview ────────────────────────────────────────────────────────────

const rottenRows = computed(() => {
  const progressGroups = progressData.value?.groups ?? []
  return (props.event.groups ?? []).map(group => {
    const prog = progressGroups.find(g => g.groupId === group.id)
    const activeCompletion = prog?.completions?.find(c => !c.completed)
    const fallbackPasse = props.event.passen?.[0]?.name ?? null
    return {
      id: group.id,
      name: group.name,
      members: group.members ?? [],
      currentPasse: activeCompletion?.passeName ?? fallbackPasse,
      status: (prog?.passenCompleted ?? 0) === (prog?.passenTotal ?? 0) && (prog?.passenTotal ?? 0) > 0 ? 'done' : 'active',
    }
  })
})

// ── Serie cards for active passe ───────────────────────────────────────────────

const serieCards = computed(() => {
  const passen = props.event.passen ?? []
  if (passen.length === 0) return []
  const idx = activePasseIndex.value
  const activePasse = passen[idx]
  if (!activePasse) return []

  const fullPasse = passeStore.savedGlobalPassen.find(p => p.id === activePasse.id)
  const serien = fullPasse?.serien ?? []
  if (serien.length === 0) return []

  const progressGroups = progressData.value?.groups ?? []

  return serien.map(serie => {
    const serieName = serie.alias ?? serie.name ?? '?'
    return {
      serieAlias: serieName,
      // Header reads "StandName: SerienName"; fall back to just the serie name when no Stand.
      headerLabel: serie.rangeName ? `${serie.rangeName}: ${serieName}` : serieName,
      rotteRows: (props.event.groups ?? []).map(group => {
        const prog = progressGroups.find(g => g.groupId === group.id)
        const done = (prog?.completedSerien ?? []).some(
          c => c.passeIndex === idx && c.serieId === serie.id,
        )
        return {
          rotteId: group.id,
          rotteName: group.name,
          status: done ? 'done' : 'pending',
        }
      }),
    }
  })
})

// ── Leaderboard ────────────────────────────────────────────────────────────────
// Live player ranking from the session leaderboard endpoint (polled above).

const leaderboard = computed(() =>
  (leaderboardData.value?.playerScores ?? []).map(p => ({
    playerId: p.playerId,
    displayName: p.displayName,
    totalPoints: p.totalScore,
    maxPoints: p.maxScore,
  }))
)

// ── Labels ─────────────────────────────────────────────────────────────────────

const rowStatusLabel = (status) => ({ done: '✓ Fertig', in_progress: '◑ Aktiv', pending: '○ Offen' }[status] ?? status)
const rotteStatusLabel = (status) => ({ active: 'Aktiv', done: 'Fertig', paused: 'Pausiert' }[status] ?? status)
</script>

<style scoped>
.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px 40px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ── Header ── */
.panel-header { display: flex; align-items: center; justify-content: space-between; }

.panel-title { font-size: 18px; font-weight: 700; color: var(--sg-text-primary); margin: 0; }

.stop-btn {
  display: flex; align-items: center; gap: 6px;
  background: var(--sg-color-danger-bg, var(--sg-color-danger-bg)); border: 1px solid var(--sg-color-danger-border, var(--sg-color-danger-bg));
  border-radius: 10px; padding: 8px 14px;
  color: var(--sg-color-danger-text, var(--sg-color-danger-text)); font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.stop-btn:hover { background: var(--sg-color-danger-bg-hover, var(--sg-color-danger-bg)); }

/* ── Passen progress bar ── */
.passen-progress {
  padding: 14px 16px;
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 12px;
  box-shadow: var(--sg-shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.progress-title {
  font-size: 11px;
  font-weight: 700;
  color: var(--sg-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.progress-count {
  font-size: 11px;
  font-weight: 600;
  color: var(--sg-text-faint);
}

.progress-track {
  display: flex;
  gap: 3px;
  height: 8px;
}

.progress-segment {
  flex: 1;
  border-radius: 4px;
  transition: background 0.3s;
}

.seg-fertig { background: var(--sg-color-success-text); }
.seg-aktiv  { background: var(--sg-color-warning); }
.seg-offen  { background: var(--sg-border-input); opacity: 0.4; }

.progress-labels {
  display: flex;
  gap: 3px;
}

.progress-name {
  flex: 1;
  font-size: 10px;
  font-weight: 600;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pname-fertig { color: var(--sg-color-success-text); }
.pname-aktiv  { color: var(--sg-color-warning-text); }
.pname-offen  { color: var(--sg-text-faint); }

/* ── Admin Passe gate ── */
.release-passe-btn {
  align-self: flex-start;
  padding: 8px 16px;
  border-radius: 10px;
  background: var(--sg-accent-hover);
  border: none;
  color: var(--sg-text-primary);
  font-size: 13px;
  font-weight: 700;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s;
}
.release-passe-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.release-passe-btn:hover:not(:disabled) { background: var(--sg-accent); }

/* ── Tab bar ── */
.tab-bar {
  display: flex;
  border-bottom: 1px solid var(--sg-border);
}

.tab-btn {
  flex: 1;
  padding: 10px 0;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  color: var(--sg-text-faint);
  cursor: pointer;
  margin-bottom: -1px;
  transition: color 0.15s, border-color 0.15s;
}

.tab-btn.active {
  color: var(--sg-text-primary);
  border-bottom-color: var(--sg-accent);
}

.tab-btn:hover:not(.active) { color: var(--sg-text-muted); }

/* ── Rotten overview ── */
.rotten-overview {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rotte-overview-card {
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--sg-shadow-sm);
}

.rotte-ov-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--sg-border);
}

.rotte-ov-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--sg-text-primary);
  flex: 1;
}

.rotte-ov-passe {
  font-size: 11px;
  color: var(--sg-text-faint);
}

.rotte-ov-chip {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 10px;
}

.rotte-ov-members {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px 14px;
}

.member-chip {
  font-size: 12px;
  font-weight: 500;
  padding: 4px 10px;
  background: var(--sg-bg-panel);
  border: 1px solid var(--sg-border);
  border-radius: 20px;
  color: var(--sg-text-muted);
}

.member-empty {
  font-size: 12px;
  color: var(--sg-text-faint);
  font-style: italic;
}

/* ── Serien view ── */
.serien-view {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.serie-card {
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--sg-shadow-sm);
}

.serie-card-header {
  font-size: 13px;
  font-weight: 700;
  color: var(--sg-text-primary);
  padding: 10px 14px 8px;
  border-bottom: 1px solid var(--sg-border);
}

.rotte-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  border-bottom: 1px solid var(--sg-border);
}

.rotte-row:last-child { border-bottom: none; }

.rotte-name {
  font-size: 13px;
  color: var(--sg-text-muted);
}

.rotte-chip {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 10px;
}

.chip-done        { background: var(--sg-color-success-bg); color: var(--sg-color-success-text); }
.chip-in_progress { background: var(--sg-color-warning-bg); color: var(--sg-color-warning-text); }
.chip-pending     { background: var(--sg-bg-panel); color: var(--sg-text-faint); }
.chip-active      { background: var(--sg-color-warning-bg); color: var(--sg-color-warning-text); }
.chip-paused      { background: var(--sg-bg-panel); color: var(--sg-text-faint); }

/* ── Rangliste view ── */
.rangliste-view {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rangliste-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 10px;
  box-shadow: var(--sg-shadow-sm);
}

.rank {
  font-size: 12px;
  font-weight: 700;
  color: var(--sg-accent);
  width: 20px;
  flex-shrink: 0;
}

.player-name {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: var(--sg-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tie-flag {
  font-size: 10.5px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 99px;
  background: var(--sg-color-warning-bg);
  color: var(--sg-color-warning-text);
  white-space: nowrap;
}

.score {
  font-size: 13px;
  font-weight: 700;
  color: var(--sg-text-muted);
  white-space: nowrap;
}

/* ── Empty state ── */
.empty-state {
  text-align: center;
  padding: 32px 16px;
  font-size: 13px;
  color: var(--sg-text-faint);
  background: var(--sg-bg-card);
  border-radius: 12px;
  border: 1px solid var(--sg-border);
}
</style>
