<!-- src/components/competition/StechenPanel.vue -->
<!--
  Stechen (tiebreaker) administration for a PRE_COMPLETE competition.
  Surfaces tied blocks, lets the operator start a Stechen run from a Passe template,
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

          <p v-if="round.playInstanceId" class="round-instance-note">
            Live-Lauf bereit — Instanz-ID
            <code class="instance-id">{{ round.playInstanceId }}</code>.
            Lauf am Bereich durchführen, danach die Ergebnisse unten eintragen.
          </p>

          <!-- Editable result entry for an ACTIVE round -->
          <form
            v-if="round.status === 'ACTIVE'"
            class="result-form"
            @submit.prevent="saveResults(round)"
          >
            <div
              v-for="participant in round.participants"
              :key="participant.playerId"
              class="result-row"
            >
              <span class="result-name">{{ participant.displayName }}</span>
              <div class="result-inputs">
                <label
                  :for="`pts-${round.id}-${participant.playerId}`"
                  class="result-label"
                >Punkte</label>
                <input
                  :id="`pts-${round.id}-${participant.playerId}`"
                  v-model.number="drafts[round.id][participant.playerId].totalPoints"
                  type="number"
                  min="0"
                  class="result-input"
                  inputmode="numeric"
                />
                <span class="result-sep">/</span>
                <label
                  :for="`max-${round.id}-${participant.playerId}`"
                  class="result-label"
                >Max</label>
                <input
                  :id="`max-${round.id}-${participant.playerId}`"
                  v-model.number="drafts[round.id][participant.playerId].maxPoints"
                  type="number"
                  min="0"
                  class="result-input"
                  inputmode="numeric"
                />
              </div>
            </div>
            <div class="result-actions">
              <Button variant="primary" size="sm" :disabled="saving">
                {{ saving ? 'Speichern…' : 'Speichern' }}
              </Button>
            </div>
          </form>

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
import { ref, reactive, computed, watch, onMounted } from 'vue'
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

// ── Result entry drafts ─────────────────────────────────────────────────────
// One editable draft per ACTIVE round, keyed by round id → playerId → score.
const drafts = reactive({})

const syncDrafts = () => {
  for (const block of tiedBlocks.value) {
    for (const round of block.rounds ?? []) {
      if (round.status !== 'ACTIVE') continue
      if (!drafts[round.id]) drafts[round.id] = {}
      for (const participant of round.participants ?? []) {
        if (!drafts[round.id][participant.playerId]) {
          const existing = (round.results ?? []).find(r => r.playerId === participant.playerId)
          drafts[round.id][participant.playerId] = {
            totalPoints: existing?.totalPoints ?? 0,
            maxPoints: existing?.maxPoints ?? 0,
          }
        }
      }
    }
  }
}

watch(tiedBlocks, syncDrafts, { immediate: true, deep: true })

const saving = ref(false)

const saveResults = async (round) => {
  if (saving.value) return
  saving.value = true
  try {
    const results = (round.participants ?? []).map(p => ({
      playerId: p.playerId,
      totalPoints: drafts[round.id]?.[p.playerId]?.totalPoints ?? 0,
      maxPoints: drafts[round.id]?.[p.playerId]?.maxPoints ?? 0,
    }))
    await store.submitStechenResults(props.sessionId, round.id, results)
    // Refresh ties so the leaderboard re-orders after the round resolves.
    await store.loadTies(props.sessionId)
  } catch (e) {
    console.error('[StechenPanel] submit results failed:', e)
  } finally {
    saving.value = false
  }
}

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

onMounted(async () => {
  if (passeStore.savedSerien.length === 0) {
    await passeStore.loadSerienFromStorage().catch(() => {})
  }
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

.round-instance-note {
  font-size: 12px;
  color: var(--sg-text-muted);
  margin: 0;
  line-height: 1.4;
}

.instance-id {
  font-size: 11px;
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 4px;
  padding: 1px 5px;
}

/* ── Result entry ── */
.result-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

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

.result-inputs {
  display: flex;
  align-items: center;
  gap: 6px;
}

.result-label {
  font-size: 11px;
  color: var(--sg-text-faint);
}

.result-input {
  width: 56px;
  min-height: 40px;
  padding: 6px 8px;
  font-family: inherit;
  font-size: 13px;
  text-align: center;
  color: var(--sg-brand);
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border-input, var(--sg-border));
  border-radius: 8px;
}

.result-input:focus-visible {
  outline: 2px solid var(--sg-accent);
  outline-offset: 1px;
}

.result-sep {
  font-size: 13px;
  color: var(--sg-text-faint);
}

.result-score {
  font-size: 13px;
  font-weight: 700;
  color: var(--sg-text-muted);
}

.result-actions {
  display: flex;
  justify-content: flex-end;
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
