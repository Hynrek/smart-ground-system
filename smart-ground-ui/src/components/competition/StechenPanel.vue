<!-- src/components/competition/StechenPanel.vue -->
<!--
  Stechen (tiebreaker) administration for a PRE_COMPLETE competition.
  Surfaces tied blocks, lets the operator start a Stechen run from a Serie template,
  enter results for active rounds, and shows the per-tie round history.
  All data access goes through competitionEventStore — never the services directly.
-->
<template>
  <section class="stechen-panel">
    <div class="panel-header">
      <h2 class="panel-title">Stechen</h2>
      <span class="panel-sub">Gleichstände auflösen</span>
    </div>

    <div v-if="tiedBlocks.length === 0" class="empty-state">
      Keine Gleichstände — alle Plätze sind eindeutig.
    </div>

    <div
      v-for="block in tiedBlocks"
      :key="block.tiePosition"
      class="tie-block"
      :class="{ 'tie-block--resolved': block.resolved }"
    >
      <!-- Block header -->
      <div class="tie-block-header">
        <div class="tie-block-title">
          <span class="tie-platz">Platz {{ block.tiePosition }}</span>
          <Badge :color="block.resolved ? 'green' : 'warn'">
            {{ block.resolved ? 'Aufgelöst' : 'Gleichstand' }}
          </Badge>
          <span class="tie-score">{{ block.sharedScore }} Punkte</span>
        </div>
        <Button
          variant="primary"
          size="sm"
          :disabled="block.resolved || hasActiveRound(block)"
          :aria-label="`Stechen für Platz ${block.tiePosition} starten`"
          @click="openStartModal(block)"
        >
          Stechen starten
        </Button>
      </div>

      <!-- Tied players -->
      <div class="tie-players">
        <span
          v-for="player in block.players"
          :key="player.playerId"
          class="tie-player-chip"
        >{{ player.displayName }}</span>
      </div>

      <!-- Round history -->
      <div v-if="(block.rounds ?? []).length > 0" class="round-history">
        <div
          v-for="round in block.rounds"
          :key="round.id"
          class="round-card"
        >
          <div class="round-card-header">
            <span class="round-number">Runde {{ round.roundNumber }}</span>
            <span class="round-template">{{ round.templateName ?? '–' }}</span>
            <Badge :color="statusColor(round.status)">{{ statusLabel(round.status) }}</Badge>
          </div>

          <!-- Live run status for an ACTIVE round (auto-scored from the range) -->
          <div v-if="round.status === 'ACTIVE'" class="round-live">
            <span class="round-live-dot" aria-hidden="true"></span>
            <span class="round-live-text">
              Läuft am Bereich{{ round.run?.rangeName ? ` ${round.run.rangeName}` : '' }} —
              Ergebnis wird automatisch übernommen.
            </span>
          </div>

          <!-- Read-only results for a COMPLETED round -->
          <div v-else-if="(round.results ?? []).length > 0" class="result-readonly">
            <div
              v-for="res in round.results"
              :key="res.playerId"
              class="result-row result-row--readonly"
            >
              <span class="result-name">{{ nameFor(round, res.playerId) }}</span>
              <span class="result-score">{{ res.totalPoints }} / {{ res.maxPoints }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Start-Stechen modal -->
    <div
      v-if="startModal.open"
      class="modal-overlay"
      @click.self="closeStartModal"
    >
      <div class="stechen-modal" role="dialog" aria-modal="true" aria-labelledby="stechen-modal-title">
        <h3 id="stechen-modal-title" class="modal-title">Stechen starten</h3>
        <p class="modal-desc">Platz {{ startModal.block?.tiePosition }} — {{ startModal.block?.sharedScore }} Punkte</p>

        <div class="modal-section">
          <span class="modal-section-label">Werfer im Stechen</span>
          <ul class="modal-player-list">
            <li v-for="player in startModal.block?.players ?? []" :key="player.playerId">
              {{ player.displayName }}
            </li>
          </ul>
        </div>

        <div class="modal-section">
          <label for="stechen-serie" class="modal-section-label">Serie-Vorlage</label>
          <select
            id="stechen-serie"
            v-model="startModal.serieId"
            class="modal-select"
          >
            <option :value="null" disabled>Serie wählen…</option>
            <option v-for="serie in serieOptions" :key="serie.id" :value="serie.id">
              {{ serie.name }}
            </option>
          </select>
          <span v-if="serieOptions.length === 0" class="modal-hint">
            Keine Serie-Vorlagen verfügbar.
          </span>
        </div>

        <p v-if="startError" class="modal-error">{{ startError }}</p>

        <div class="modal-actions">
          <Button variant="ghost" size="sm" @click="closeStartModal">Abbrechen</Button>
          <Button
            variant="primary"
            size="sm"
            :disabled="!startModal.serieId || starting"
            @click="confirmStart"
          >
            {{ starting ? 'Starten…' : 'Starten' }}
          </Button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import Badge from '@/components/Badge.vue'
import Button from '@/components/Button.vue'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { usePasseStore } from '@/stores/passeStore.js'

const props = defineProps({ sessionId: { type: String, required: true } })

const store = useCompetitionEventStore()
const passeStore = usePasseStore()
const { tiesBySession } = storeToRefs(store)

const tiedBlocks = computed(() => tiesBySession.value[props.sessionId]?.tiedBlocks ?? [])

// A Stechen is always a single Serie — offer published, range-owned Serien only.
const serieOptions = computed(() =>
  passeStore.getGlobalSerien().filter((s) => s.published === true),
)

// ── Round helpers ──────────────────────────────────────────────────────────
const hasActiveRound = (block) => (block.rounds ?? []).some(r => r.status === 'ACTIVE')

const statusLabel = (status) =>
  ({ PENDING: 'Wartet', ACTIVE: 'Läuft', COMPLETED: 'Fertig' }[status] ?? status)

const statusColor = (status) =>
  ({ PENDING: 'gray', ACTIVE: 'warn', COMPLETED: 'green' }[status] ?? 'gray')

const nameFor = (round, playerId) =>
  (round.participants ?? []).find(p => p.playerId === playerId)?.displayName ?? '–'

// ── Start modal ─────────────────────────────────────────────────────────────
const startModal = reactive({ open: false, block: null, serieId: null })
const starting = ref(false)
const startError = ref(null)

const openStartModal = (block) => {
  startModal.open = true
  startModal.block = block
  startModal.serieId = null
  startError.value = null
}

const closeStartModal = () => {
  startModal.open = false
  startModal.block = null
}

const confirmStart = async () => {
  if (!startModal.serieId || starting.value) return
  starting.value = true
  startError.value = null
  const block = startModal.block
  try {
    await store.startStechen(props.sessionId, {
      playerIds: block.players.map(p => p.playerId),
      templateId: startModal.serieId,
      tiePosition: block.tiePosition,
    })
    closeStartModal()
    // The created round (with its playInstanceId) now appears in the block history.
  } catch (e) {
    console.error('[StechenPanel] start Stechen failed:', e)
    startError.value = e?.message ?? 'Stechen konnte nicht gestartet werden.'
  } finally {
    starting.value = false
  }
}

let pollTimer = null
const anyRoundActive = () =>
  tiedBlocks.value.some(b => (b.rounds ?? []).some(r => r.status === 'ACTIVE'))

onMounted(async () => {
  if (passeStore.savedSerien.length === 0) {
    await passeStore.loadSerienFromStorage().catch(() => {})
  }
  await store.loadTies(props.sessionId).catch(() => {})
  // Light-poll while a run is active so auto-resolution surfaces without a manual refresh.
  pollTimer = setInterval(() => {
    if (anyRoundActive()) store.loadTies(props.sessionId).catch(() => {})
  }, 4000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.stechen-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panel-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.panel-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--sg-brand);
  margin: 0;
}

.panel-sub {
  font-size: 12px;
  color: var(--sg-text-faint);
}

.empty-state {
  text-align: center;
  padding: 24px 16px;
  font-size: 13px;
  color: var(--sg-text-faint);
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 12px;
}

/* ── Tie block ── */
.tie-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  background: var(--sg-bg-card);
  border: 1px solid color-mix(in srgb, var(--sg-color-warning) 40%, transparent);
  border-radius: 14px;
  box-shadow: var(--sg-shadow-sm);
}

.tie-block--resolved {
  border-color: color-mix(in srgb, var(--sg-color-success) 40%, transparent);
}

.tie-block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.tie-block-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.tie-platz {
  font-size: 14px;
  font-weight: 700;
  color: var(--sg-brand);
}

.tie-score {
  font-size: 12px;
  font-weight: 600;
  color: var(--sg-text-muted);
}

.tie-players {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tie-player-chip {
  font-size: 12px;
  font-weight: 500;
  padding: 4px 10px;
  background: var(--sg-bg-panel);
  border: 1px solid var(--sg-border);
  border-radius: 20px;
  color: var(--sg-text-muted);
}

/* ── Round history ── */
.round-history {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.round-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  background: var(--sg-bg-panel);
  border: 1px solid var(--sg-border);
  border-radius: 10px;
}

.round-card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.round-number {
  font-size: 13px;
  font-weight: 700;
  color: var(--sg-brand);
}

.round-template {
  font-size: 12px;
  color: var(--sg-text-faint);
  flex: 1;
}

.round-live {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--sg-text-muted);
}

.round-live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--sg-color-warning);
  animation: round-live-pulse 1.2s ease-in-out infinite;
}

@keyframes round-live-pulse {
  0%, 100% { opacity: 0.35; }
  50% { opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .round-live-dot { animation: none; }
}

/* ── Result read-only ── */
.result-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.result-row--readonly {
  padding: 4px 0;
  border-bottom: 1px solid var(--sg-border);
}

.result-row--readonly:last-child {
  border-bottom: none;
}

.result-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--sg-brand);
  flex: 1;
}

.result-score {
  font-size: 13px;
  font-weight: 700;
  color: var(--sg-text-muted);
}

/* ── Modal ── */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 120;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.stechen-modal {
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 18px;
  padding: 24px;
  max-width: 400px;
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
  box-shadow: var(--sg-shadow-lg);
}

.modal-title {
  font-size: 16px;
  font-weight: 700;
  margin: 0;
  color: var(--sg-brand);
}

.modal-desc {
  font-size: 13px;
  color: var(--sg-text-muted);
  margin: 0;
}

.modal-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.modal-section-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--sg-text-muted);
}

.modal-player-list {
  margin: 0;
  padding: 0 0 0 16px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.modal-player-list li {
  font-size: 13px;
  color: var(--sg-text-muted);
}

.modal-select {
  min-height: 44px;
  padding: 8px 12px;
  font-family: inherit;
  font-size: 13px;
  color: var(--sg-brand);
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border-input, var(--sg-border));
  border-radius: 10px;
}

.modal-select:focus-visible {
  outline: 2px solid var(--sg-accent);
  outline-offset: 1px;
}

.modal-hint {
  font-size: 12px;
  color: var(--sg-text-faint);
}

.modal-error {
  font-size: 12px;
  color: var(--sg-color-danger-text, #c53030);
  margin: 0;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
