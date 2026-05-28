<template>
  <div class="competition-view">
    <!-- Top bar -->
    <div class="top-bar">
      <button class="back-btn" @click="router.push('/home')">
        <Icons icon="chevronLeft" :size="18" color="rgba(255,255,255,0.7)" />
      </button>
      <h1 class="page-title">Wettkampf</h1>
      <div class="top-bar-spacer" />
    </div>

    <!-- Tab toggle -->
    <div class="tab-toggle">
      <button class="tab-btn" :class="{ active: activeTab === 'competitions' }" @click="activeTab = 'competitions'">
        Wettkämpfe
      </button>
      <button class="tab-btn" :class="{ active: activeTab === 'active' }" @click="activeTab = 'active'">
        Aktiv
      </button>
      <button class="tab-btn" :class="{ active: activeTab === 'completed' }" @click="activeTab = 'completed'">
        Abgeschlossen
      </button>
    </div>

    <!-- Content -->
    <div class="content">

      <!-- ══ TAB 1: WETTKAMPF-VORLAGEN ══ -->
      <template v-if="activeTab === 'competitions'">
        <section class="block">
          <div class="block-header">
            <div class="block-title-row">
              <span class="block-title">Wettkämpfe</span>
              <span class="block-badge">{{ savedCompetitions.length }}</span>
            </div>
            <p class="block-desc">Geordnete Abfolge von Passen, gespielt in Rotten. Jede Rotte spielt dieselben Passen auf ihrem zugewiesenen Platz.</p>
          </div>

          <div v-if="savedCompetitions.length > 0" class="training-list">
            <div
              v-for="comp in savedCompetitions"
              :key="comp.id"
              class="training-card"
              :class="{ expanded: expandedId === comp.id }"
            >
              <div class="card-main" @click="toggleExpand(comp.id)">
                <div class="card-info">
                  <span class="card-name">{{ comp.name }}</span>
                  <div class="card-meta-row">
                    <span class="card-meta">{{ comp.passen.length }} Passen</span>
                    <span v-if="comp.rottCountHint" class="meta-dot">·</span>
                    <span v-if="comp.rottCountHint" class="card-meta">{{ comp.rottCountHint }} Rotten vorgesehen</span>
                    <span class="meta-dot">·</span>
                    <span class="card-meta">{{ totalThrows(comp) }} Würfe</span>
                  </div>
                </div>
                <Icons
                  icon="chevronRight"
                  :size="14"
                  color="rgba(255,255,255,0.3)"
                  class="expand-icon"
                  :class="{ rotated: expandedId === comp.id }"
                />
              </div>

              <div v-if="expandedId === comp.id" class="card-detail">
                <div class="phase-list">
                  <div v-for="(passe, i) in comp.passen" :key="passe.id" class="phase-row">
                    <span class="phase-num">{{ i + 1 }}</span>
                    <span class="phase-name">{{ passe.name }}</span>
                    <span class="phase-meta">{{ progThrows(passe) }} W</span>
                  </div>
                </div>
                <div class="card-actions">
                  <button class="action-btn action-btn--start" @click.stop="openRotteModal(comp)">
                    <Icons icon="play" :size="14" color="#4fc3f7" />
                    Starten
                  </button>
                  <button class="action-btn action-btn--danger" @click.stop="passeStore.deleteTraining(comp.id)">
                    <Icons icon="trash" :size="13" color="rgba(252,129,129,0.7)" />
                    Löschen
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div v-if="savedCompetitions.length === 0 && !creatingCompetition" class="empty-block">
            <Icons icon="program" :size="36" color="rgba(255,255,255,0.07)" />
            <p>Noch keine Wettkämpfe</p>
            <p class="empty-hint">Kombiniere Passen zu einem Wettkampf</p>
          </div>

          <!-- Create competition panel -->
          <div v-if="creatingCompetition" class="create-panel">
            <div class="create-panel-header">
              <span class="create-panel-title">Neuer Wettkampf</span>
            </div>
            <div class="create-field">
              <label class="create-label">Name</label>
              <input
                v-model="newCompName"
                class="create-input"
                type="text"
                placeholder="z.B. Frühjahrspokal"
                maxlength="50"
              />
            </div>
            <div class="create-field">
              <label class="create-label">
                Geplante Rotten
                <span class="create-label-hint">(optional)</span>
              </label>
              <input
                v-model.number="newRottCountHint"
                class="create-input create-input--narrow"
                type="number"
                min="1"
                max="8"
                placeholder="z.B. 4"
              />
            </div>
            <div class="create-field">
              <label class="create-label">
                Passen auswählen
                <span class="create-label-count">{{ selectedPassen.length }} gewählt</span>
              </label>
              <p v-if="passeStore.savedPassen.length === 0" class="create-hint">
                Noch keine Passen vorhanden.
              </p>
              <p v-else class="create-hint">Tippe auf Passen um sie auszuwählen. Die Reihenfolge entspricht der Spielreihenfolge.</p>
            </div>

            <div v-if="passeStore.savedPassen.length > 0" class="programme-picker">
              <div
                v-for="passe in passeStore.savedPassen"
                :key="passe.id"
                class="picker-item"
                :class="{ selected: isSelected(passe.id) }"
                @click="togglePasse(passe)"
              >
                <div class="picker-check">
                  <span v-if="isSelected(passe.id)" class="picker-order">{{ selectedPasseOrder(passe.id) }}</span>
                  <Icons v-else icon="plus" :size="11" color="rgba(255,255,255,0.3)" />
                </div>
                <span class="picker-name">{{ passe.name }}</span>
                <span class="picker-meta">{{ progThrows(passe) }} W</span>
              </div>
            </div>

            <div class="create-actions">
              <button class="action-btn action-btn--cancel" @click="cancelCreate">Abbrechen</button>
              <button
                class="action-btn action-btn--create"
                :disabled="selectedPassen.length === 0"
                @click="confirmCreate"
              >
                <Icons icon="check" :size="13" color="#fff" />
                Erstellen
              </button>
            </div>
          </div>

          <button
            v-if="!creatingCompetition"
            class="new-training-btn"
            :disabled="passeStore.savedPassen.length === 0"
            @click="startCreate"
          >
            <Icons icon="plus" :size="16" color="rgba(79,195,247,0.8)" />
            Neuer Wettkampf
          </button>
        </section>
      </template>

      <!-- ══ TAB 2: AKTIVE WETTKÄMPFE ══ -->
      <template v-if="activeTab === 'active'">
        <section class="block">
          <div class="block-header">
            <div class="block-title-row">
              <span class="block-title">Aktive Wettkämpfe</span>
              <span class="block-badge">{{ activeCompetitions.length }}</span>
            </div>
            <p class="block-desc">Laufende Wettkämpfe. Jede Rotte spielt auf ihrem zugewiesenen Platz.</p>
          </div>

          <div v-if="activeCompetitions.length > 0" class="active-list">
            <div
              v-for="inst in activeCompetitions"
              :key="inst.instanceId"
              class="session-card"
            >
              <div class="session-main">
                <div class="session-info">
                  <span class="session-name">{{ inst.templateName }}</span>
                  <span class="session-meta">{{ inst.rotten.length }} Rotten</span>
                </div>
              </div>

              <!-- Per-Rotte rows -->
              <div class="rotte-list">
                <div
                  v-for="rotte in inst.rotten"
                  :key="rotte.rotteId"
                  class="rotte-row"
                >
                  <span
                    class="rotte-status-dot"
                    :class="`dot-${rotte.status}`"
                  />
                  <span class="rotte-name">{{ rotte.name }}</span>
                  <span class="rotte-range">
                    {{ resolveRangeName(rotte.assignedRangeId) }}
                  </span>
                  <span class="rotte-passe">
                    {{ rotte.phases[rotte.currentPhaseIndex]?.passeName ?? '–' }}
                  </span>
                  <span class="rotte-badge" :class="`badge-${rotte.status}`">
                    {{ rotteBadgeLabel(rotte.status) }}
                  </span>
                </div>
              </div>

              <div class="card-actions">
                <button
                  class="action-btn action-btn--start"
                  @click="router.push('/wettkampf/live/' + inst.instanceId)"
                >
                  Verwalten
                </button>
                <button
                  class="action-btn action-btn--danger"
                  @click="stopCompetition(inst)"
                >
                  <Icons icon="x" :size="13" color="rgba(252,129,129,0.7)" />
                  Stoppen
                </button>
              </div>
            </div>
          </div>

          <div v-if="activeCompetitions.length === 0" class="empty-block">
            <Icons icon="program" :size="36" color="rgba(255,255,255,0.07)" />
            <p>Keine aktiven Wettkämpfe</p>
            <p class="empty-hint">Starte einen Wettkampf über den Wettkämpfe-Tab.</p>
          </div>
        </section>
      </template>

      <!-- ══ TAB 3: ABGESCHLOSSENE WETTKÄMPFE ══ -->
      <template v-if="activeTab === 'completed'">
        <section class="block">
          <div class="block-header">
            <div class="block-title-row">
              <span class="block-title">Abgeschlossene Wettkämpfe</span>
              <span class="block-badge">{{ completedCompetitions.length }}</span>
            </div>
            <p class="block-desc">Fertig gespielte Wettkämpfe.</p>
          </div>

          <div v-if="completedCompetitions.length > 0" class="active-list">
            <div
              v-for="inst in completedCompetitions"
              :key="inst.instanceId"
              class="session-card completed-card"
              @click="toggleCompletedCard(inst.instanceId)"
            >
              <div class="session-main">
                <span class="session-name">{{ inst.templateName }}</span>
                <span class="completed-date">
                  {{ new Date(inst.completedAt).toLocaleDateString('de-CH') }}
                </span>
              </div>

              <!-- Per-Rotte point summaries -->
              <div class="rotte-summaries">
                <div
                  v-for="rotte in inst.rotten"
                  :key="rotte.rotteId"
                  class="rotte-summary-row"
                >
                  <span class="rs-name">{{ rotte.name }}</span>
                  <div class="rs-players">
                    <span
                      v-for="ps in getRottePlayerSummaries(rotte)"
                      :key="ps.player.id"
                      class="rs-player-chip"
                    >
                      <span class="rs-player-name">{{ ps.player.displayName }}</span>
                      <span class="rs-player-pts">{{ ps.totalPts }}/{{ ps.maxPts }}</span>
                    </span>
                  </div>
                </div>
              </div>

              <!-- Expanded detail -->
              <div
                v-if="expandedCompletedCards.has(inst.instanceId)"
                class="completed-detail"
                @click.stop
              >
                <div
                  v-for="rotte in inst.rotten"
                  :key="rotte.rotteId"
                  class="rotte-detail-section"
                >
                  <div class="rotte-detail-header">
                    <span class="rd-name">{{ rotte.name }}</span>
                  </div>
                  <div
                    v-for="ps in getRottePlayerSummaries(rotte)"
                    :key="ps.player.id"
                    class="player-detail-section"
                  >
                    <div class="player-detail-header">
                      <span class="pd-name">{{ ps.player.displayName }}</span>
                      <span class="pd-total">{{ ps.totalPts }}/{{ ps.maxPts }}</span>
                    </div>
                    <div
                      v-for="phase in rotte.phases"
                      :key="phase.phaseIndex"
                      class="phase-detail-group"
                    >
                      <span class="phase-detail-label">{{ phase.passeName }}</span>
                      <div
                        v-for="block in phase.blocks"
                        :key="block.blockId"
                        class="completed-score-row"
                      >
                        <span class="completed-score-block">
                          <span class="completed-score-range">{{ block.rangeName }}</span>
                          {{ block.serieAlias }}
                        </span>
                        <span class="completed-score-pts">
                          {{
                            block.result?.playerResults?.find(r => r.playerId === ps.player.id)?.totalPoints ?? '–'
                          }}/{{
                            block.result?.playerResults?.find(r => r.playerId === ps.player.id)?.maxPoints ?? '–'
                          }}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="completedCompetitions.length === 0" class="empty-block">
            <Icons icon="program" :size="36" color="rgba(255,255,255,0.07)" />
            <p>Noch keine abgeschlossenen Wettkämpfe</p>
          </div>
        </section>
      </template>

    </div>

    <!-- RotteSetupModal -->
    <RotteSetupModal
      :open="showRotteModal"
      :template="startingTemplate"
      @close="cancelRotteModal"
      @confirm="onRotteModalConfirm"
    />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { usePasseStore } from '@/stores/passeStore.js'
import { useActivePasseStore } from '@/stores/activePasseStore.js'
import { useRangeStore } from '@/stores/rangeStore.js'
import Icons from '@/components/Icons.vue'
import RotteSetupModal from '@/components/competition/RotteSetupModal.vue'

const router = useRouter()
const passeStore = usePasseStore()
const activePasseStore = useActivePasseStore()
const rangeStore = useRangeStore()

const activeTab = ref('competitions')

// ── Computed: filter savedTrainings by type ───────────────────────────────
const savedCompetitions = computed(() =>
  passeStore.savedTrainings.filter(t => t.type === 'competition')
)

const activeCompetitions = computed(() =>
  activePasseStore.activeInstances.filter(i => i.type === 'competition')
)

const completedCompetitions = computed(() =>
  activePasseStore.completedInstances.filter(i => i.type === 'competition')
)

// ── Template list: expand ────────────────────────────────────────────────
const expandedId = ref(null)

const toggleExpand = (id) => {
  expandedId.value = expandedId.value === id ? null : id
}

// ── Create competition flow ──────────────────────────────────────────────
const creatingCompetition = ref(false)
const newCompName = ref('')
const newRottCountHint = ref(null)
const selectedPassen = ref([])

const startCreate = () => {
  creatingCompetition.value = true
  newCompName.value = ''
  newRottCountHint.value = null
  selectedPassen.value = []
}

const cancelCreate = () => {
  creatingCompetition.value = false
  selectedPassen.value = []
}

const isSelected = (passeId) => selectedPassen.value.some(p => p.id === passeId)

const selectedPasseOrder = (passeId) => selectedPassen.value.findIndex(p => p.id === passeId) + 1

const togglePasse = (passe) => {
  const idx = selectedPassen.value.findIndex(p => p.id === passe.id)
  if (idx >= 0) {
    selectedPassen.value = selectedPassen.value.filter(p => p.id !== passe.id)
  } else {
    selectedPassen.value = [...selectedPassen.value, passe]
  }
}

const confirmCreate = () => {
  if (selectedPassen.value.length === 0) return
  const hint = newRottCountHint.value && newRottCountHint.value >= 1 ? newRottCountHint.value : null
  passeStore.createCompetition(newCompName.value, selectedPassen.value, hint)
  creatingCompetition.value = false
  selectedPassen.value = []
  newCompName.value = ''
  newRottCountHint.value = null
}

// ── RotteSetupModal ───────────────────────────────────────────────────────
const showRotteModal = ref(false)
const startingTemplate = ref(null)

const openRotteModal = (template) => {
  startingTemplate.value = template
  showRotteModal.value = true
}

const cancelRotteModal = () => {
  showRotteModal.value = false
  startingTemplate.value = null
}

const onRotteModalConfirm = (rotten) => {
  if (!startingTemplate.value) return
  activePasseStore.startCompetition(startingTemplate.value, rotten)
  showRotteModal.value = false
  startingTemplate.value = null
  activeTab.value = 'active'
}

// ── Stop competition ───────────────────────────────────────────────────────
const stopCompetition = (inst) => {
  if (confirm(`Möchtest du „${inst.templateName}" wirklich abbrechen?`)) {
    activePasseStore.stopInstance(inst.instanceId)
  }
}

// ── Completed cards ───────────────────────────────────────────────────────
const expandedCompletedCards = ref(new Set())

const toggleCompletedCard = (instanceId) => {
  const s = new Set(expandedCompletedCards.value)
  if (s.has(instanceId)) s.delete(instanceId)
  else s.add(instanceId)
  expandedCompletedCards.value = s
}

const getRottePlayerSummaries = (rotte) =>
  rotte.players.map((player) => {
    let totalPts = 0, maxPts = 0
    for (const phase of rotte.phases) {
      for (const block of phase.blocks) {
        const pr = block.result?.playerResults?.find(r => r.playerId === player.id)
        if (!pr) continue
        totalPts += pr.totalPoints
        maxPts += pr.maxPoints
      }
    }
    return { player, totalPts, maxPts }
  })

// ── Active tab helpers ────────────────────────────────────────────────────
const resolveRangeName = (rangeId) => {
  if (!rangeId) return 'Kein Platz'
  const range = rangeStore.ranges.find(r => r.id === rangeId)
  return range?.name ?? rangeId
}

const rotteBadgeLabel = (status) => {
  switch (status) {
    case 'active': return 'aktiv'
    case 'waiting': return 'wartend'
    case 'done': return 'fertig'
    case 'paused': return 'pausiert'
    default: return status
  }
}

// ── Throw count helpers ───────────────────────────────────────────────────
const stepCount = (steps) => {
  let count = 0
  for (const s of steps) {
    if (s.type === 'solo') count += 1
    else if (s.type === 'pair' || s.type === 'a_schuss' || s.type === 'raffale') count += 2
  }
  return count
}

const progThrows = (passe) =>
  (passe.serien ?? []).reduce((sum, serie) => sum + stepCount(serie.steps ?? []), 0)

const totalThrows = (comp) =>
  (comp.passen ?? []).reduce((sum, passe) => sum + progThrows(passe), 0)
</script>

<style scoped>
.competition-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #1a1a2e;
  color: #fff;
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}

.back-btn {
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 10px;
  width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  transition: background 0.15s;
  flex-shrink: 0;
}
.back-btn:hover { background: rgba(255,255,255,0.1); }

.page-title { font-size: 17px; font-weight: 700; margin: 0; letter-spacing: -0.3px; }
.top-bar-spacer { flex: 1; }

.tab-toggle {
  display: flex; gap: 8px; padding: 16px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}

.tab-btn {
  flex: 1; padding: 10px 16px;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 10px;
  color: rgba(255,255,255,0.4);
  font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.tab-btn.active {
  background: rgba(79,195,247,0.18);
  border-color: rgba(79,195,247,0.4);
  color: #4fc3f7;
}
.tab-btn:hover:not(.active) { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.6); }

.content { flex: 1; overflow-y: auto; padding: 0 0 40px; }

.block { padding: 20px 16px 0; }

.block-header { margin-bottom: 16px; }

.block-title-row { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }

.block-title { font-size: 18px; font-weight: 700; letter-spacing: -0.3px; }

.block-badge {
  font-size: 11px; font-weight: 700;
  color: rgba(255,255,255,0.4);
  background: rgba(255,255,255,0.08);
  border-radius: 20px; padding: 2px 8px;
}

.block-desc { font-size: 12px; color: rgba(255,255,255,0.3); margin: 0; line-height: 1.5; }

/* ── Template cards ── */
.training-list { display: flex; flex-direction: column; gap: 10px; margin-bottom: 14px; }

.training-card {
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 16px; overflow: hidden;
  transition: border-color 0.15s;
}
.training-card.expanded { border-color: rgba(79,195,247,0.25); }

.card-main {
  display: flex; align-items: center; justify-content: space-between;
  gap: 10px; padding: 14px 16px; cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.card-info { display: flex; flex-direction: column; gap: 4px; min-width: 0; flex: 1; }

.card-name { font-size: 15px; font-weight: 600; color: #fff; }

.card-meta-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }

.card-meta { font-size: 12px; color: rgba(255,255,255,0.3); }

.meta-dot { font-size: 12px; color: rgba(255,255,255,0.15); }

.expand-icon { flex-shrink: 0; transition: transform 0.2s; }
.expand-icon.rotated { transform: rotate(90deg); }

.card-detail {
  padding: 0 16px 16px;
  border-top: 1px solid rgba(255,255,255,0.06);
  display: flex; flex-direction: column; gap: 12px;
}

.phase-list { display: flex; flex-direction: column; gap: 4px; padding-top: 12px; }

.phase-row {
  display: flex; align-items: center; gap: 10px;
  font-size: 13px; padding: 4px 0;
}

.phase-num {
  width: 20px; height: 20px; border-radius: 50%;
  background: rgba(79,195,247,0.15);
  color: rgba(79,195,247,0.9);
  font-size: 11px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}

.phase-name { flex: 1; color: rgba(255,255,255,0.8); }

.phase-meta { font-size: 11px; color: rgba(255,255,255,0.3); }

.card-actions { display: flex; gap: 8px; }

/* ── New competition button ── */
.new-training-btn {
  width: 100%; display: flex; align-items: center; justify-content: center;
  gap: 8px; padding: 13px;
  background: transparent;
  border: 1.5px dashed rgba(79,195,247,0.25);
  border-radius: 14px;
  color: rgba(79,195,247,0.6);
  font-size: 14px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s; margin-top: 4px;
}
.new-training-btn:hover:not(:disabled) {
  background: rgba(79,195,247,0.06);
  border-color: rgba(79,195,247,0.4);
  color: rgba(79,195,247,0.9);
}
.new-training-btn:disabled { opacity: 0.3; cursor: not-allowed; }

/* ── Create panel ── */
.create-panel {
  background: rgba(79,195,247,0.04);
  border: 1px solid rgba(79,195,247,0.2);
  border-radius: 16px; padding: 16px;
  display: flex; flex-direction: column; gap: 14px; margin-top: 4px;
}

.create-panel-header { display: flex; align-items: center; justify-content: space-between; }

.create-panel-title { font-size: 14px; font-weight: 700; color: rgba(79,195,247,0.9); }

.create-field { display: flex; flex-direction: column; gap: 6px; }

.create-label {
  font-size: 11px; font-weight: 600;
  color: rgba(255,255,255,0.3);
  text-transform: uppercase; letter-spacing: 0.5px;
  display: flex; align-items: center; gap: 8px;
}

.create-label-hint {
  font-size: 10px; font-weight: 400;
  color: rgba(255,255,255,0.2);
  text-transform: none; letter-spacing: 0;
}

.create-label-count {
  background: rgba(79,195,247,0.15);
  color: rgba(79,195,247,0.8);
  border-radius: 12px; padding: 2px 8px;
  font-size: 10px; font-weight: 700;
}

.create-input {
  width: 100%; background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 10px; color: #fff;
  font-size: 14px; font-family: inherit;
  padding: 10px 12px; outline: none;
  transition: border-color 0.15s;
  box-sizing: border-box;
}
.create-input:focus { border-color: rgba(79,195,247,0.3); }
.create-input--narrow { max-width: 120px; }

.create-hint { font-size: 12px; color: rgba(255,255,255,0.25); margin: 0; line-height: 1.5; }

.programme-picker { display: flex; flex-direction: column; gap: 6px; }

.picker-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 10px; cursor: pointer;
  transition: all 0.15s;
}
.picker-item.selected {
  background: rgba(79,195,247,0.08);
  border-color: rgba(79,195,247,0.3);
}

.picker-check {
  width: 22px; height: 22px; border-radius: 50%;
  background: rgba(79,195,247,0.15);
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; color: rgba(79,195,247,0.9);
  flex-shrink: 0;
}

.picker-name { flex: 1; font-size: 13px; color: rgba(255,255,255,0.8); }

.picker-meta { font-size: 11px; color: rgba(255,255,255,0.3); }

.create-actions {
  display: flex; gap: 8px;
  padding-top: 8px; border-top: 1px solid rgba(79,195,247,0.15);
}

/* ── Action buttons ── */
.action-btn {
  flex: 1; display: flex; align-items: center; justify-content: center;
  gap: 6px; padding: 10px; border-radius: 10px;
  font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: background 0.15s, opacity 0.15s;
  border: 1px solid transparent;
}
.action-btn:disabled { opacity: 0.35; cursor: not-allowed; }

.action-btn--start {
  background: rgba(79,195,247,0.12); border-color: rgba(79,195,247,0.3); color: #4fc3f7;
}
.action-btn--start:hover:not(:disabled) { background: rgba(79,195,247,0.2); }

.action-btn--danger {
  background: rgba(252,129,129,0.08); border-color: rgba(252,129,129,0.2);
  color: rgba(252,129,129,0.7); flex: none; padding: 10px 14px;
}
.action-btn--danger:hover { background: rgba(252,129,129,0.14); }

.action-btn--cancel {
  background: transparent; border-color: rgba(255,255,255,0.12); color: rgba(255,255,255,0.4);
}
.action-btn--cancel:hover { background: rgba(255,255,255,0.05); }

.action-btn--create {
  background: rgba(79,195,247,0.2); border-color: rgba(79,195,247,0.4); color: #4fc3f7;
}
.action-btn--create:hover:not(:disabled) { background: rgba(79,195,247,0.28); }

/* ── Icon buttons ── */
.icon-btn {
  background: none; border: none; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  width: 30px; height: 30px; border-radius: 8px; transition: background 0.15s;
}
.icon-btn:hover { background: rgba(255,255,255,0.07); }

/* ── Active competitions ── */
.active-list { display: flex; flex-direction: column; gap: 10px; margin-bottom: 14px; }

.session-card {
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 16px; padding: 14px 16px;
  display: flex; flex-direction: column; gap: 10px;
}
.session-card.completed-card { cursor: pointer; user-select: none; }

.session-main { display: flex; align-items: center; justify-content: space-between; gap: 10px; }

.session-info { display: flex; flex-direction: column; gap: 4px; min-width: 0; flex: 1; }

.session-name { font-size: 15px; font-weight: 600; color: #fff; }

.session-meta { font-size: 12px; color: rgba(255,255,255,0.3); }

/* ── Rotte rows ── */
.rotte-list { display: flex; flex-direction: column; gap: 6px; }

.rotte-row {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px;
  font-size: 12px;
}

.rotte-status-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.rotte-status-dot.dot-waiting { background: rgba(255,255,255,0.2); }
.rotte-status-dot.dot-active { background: rgba(246,173,85,0.8); }
.rotte-status-dot.dot-done { background: rgba(72,187,120,0.7); }
.rotte-status-dot.dot-paused { background: rgba(255,255,255,0.25); }

.rotte-name { font-weight: 600; color: rgba(255,255,255,0.8); min-width: 52px; }

.rotte-range { color: rgba(255,255,255,0.3); flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.rotte-passe { color: rgba(79,195,247,0.7); font-size: 11px; max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.rotte-badge {
  font-size: 10px; font-weight: 700; border-radius: 6px; padding: 2px 7px;
  flex-shrink: 0;
}
.rotte-badge.badge-active { background: rgba(246,173,85,0.15); color: rgba(246,173,85,0.9); }
.rotte-badge.badge-waiting { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.4); }
.rotte-badge.badge-done { background: rgba(72,187,120,0.12); color: rgba(72,187,120,0.8); }
.rotte-badge.badge-paused { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.3); }

/* ── Completed ── */
.completed-date { font-size: 12px; font-weight: 600; color: rgba(255,255,255,0.35); white-space: nowrap; flex-shrink: 0; }

.rotte-summaries { display: flex; flex-direction: column; gap: 6px; }

.rotte-summary-row { display: flex; align-items: flex-start; gap: 8px; }

.rs-name { font-size: 11px; font-weight: 700; color: rgba(79,195,247,0.6); min-width: 54px; padding-top: 2px; text-transform: uppercase; letter-spacing: 0.4px; }

.rs-players { display: flex; flex-wrap: wrap; gap: 4px; }

.rs-player-chip {
  display: flex; align-items: center; gap: 5px;
  background: rgba(255,255,255,0.04);
  border-radius: 8px; padding: 3px 8px;
  font-size: 11px;
}

.rs-player-name { color: rgba(255,255,255,0.6); }

.rs-player-pts { font-weight: 700; color: rgba(79,195,247,0.8); }

.completed-detail {
  margin-top: 10px; padding-top: 10px;
  border-top: 1px solid rgba(255,255,255,0.07);
  display: flex; flex-direction: column; gap: 16px;
}

.rotte-detail-section { display: flex; flex-direction: column; gap: 8px; }

.rotte-detail-header {
  padding-bottom: 4px;
  border-bottom: 1px solid rgba(79,195,247,0.1);
}

.rd-name { font-size: 12px; font-weight: 700; color: rgba(79,195,247,0.7); text-transform: uppercase; letter-spacing: 0.4px; }

.player-detail-section { display: flex; flex-direction: column; gap: 4px; }

.player-detail-header {
  display: flex; justify-content: space-between; align-items: center;
  padding-bottom: 4px; margin-bottom: 2px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}

.pd-name { font-size: 12px; font-weight: 600; color: rgba(255,255,255,0.7); }
.pd-total { font-size: 12px; font-weight: 700; color: rgba(79,195,247,0.8); }

.phase-detail-group { display: flex; flex-direction: column; gap: 2px; margin-top: 6px; }

.phase-detail-label {
  font-size: 10px; font-weight: 600;
  color: rgba(255,255,255,0.25);
  text-transform: uppercase; letter-spacing: 0.5px;
  margin-bottom: 3px;
}

.completed-score-row {
  display: flex; align-items: center; gap: 8px;
  font-size: 11px; padding: 2px 0;
}

.completed-score-block { flex: 1; color: rgba(255,255,255,0.5); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.completed-score-range { color: rgba(255,255,255,0.25); margin-right: 4px; }

.completed-score-pts { font-weight: 600; color: rgba(255,255,255,0.5); white-space: nowrap; }

/* ── Empty state ── */
.empty-block {
  display: flex; flex-direction: column; align-items: center;
  gap: 8px; padding: 32px 16px; text-align: center;
}
.empty-block p { font-size: 14px; color: rgba(255,255,255,0.2); margin: 0; }
.empty-hint { font-size: 12px !important; color: rgba(255,255,255,0.12) !important; line-height: 1.5; }
</style>
