<!-- src/views/admin/WettkampfDetailView.vue -->
<template>
  <div class="detail-view">

    <!-- Header -->
    <div class="view-header">
      <div class="header-left">
        <button class="back-btn" @click="router.push('/admin/wettkampf')">
          <Icons icon="chevronLeft" :size="16" color="rgba(255,255,255,0.6)" />
          Zurück
        </button>
        <div>
          <h1 class="view-title">{{ event?.name ?? '–' }}</h1>
          <span class="status-badge" :class="`badge-${event?.status?.toLowerCase()}`">
            {{ statusLabel }}
          </span>
        </div>
      </div>
    </div>

    <div v-if="!event" class="not-found">
      <p>Wettkampf nicht gefunden.</p>
    </div>

    <!-- ══ PLANNING ══ -->
    <template v-else-if="event.status === 'PLANNING'">
      <div class="content">

        <!-- Info bar -->
        <div class="info-bar">
          <span class="info-chip">{{ event.passen.length }} Passen</span>
          <span class="info-chip">{{ totalThrows }} Würfe</span>
          <span class="info-chip">{{ totalPlayers }} Schützen</span>
        </div>

        <!-- Payment summary warning -->
        <div v-if="unpaidPlayers.length > 0" class="payment-warning">
          <Icons icon="alert" :size="14" color="rgba(246,173,85,0.9)" />
          {{ unpaidPlayers.length }} Schützen haben noch nicht bezahlt
        </div>

        <!-- Rotten -->
        <section class="section">
          <div class="section-header">
            <h2 class="section-title">Rotten</h2>
            <button
              class="add-rotte-btn"
              :disabled="event.rotten.length >= 8"
              @click="store.addRotte(eventId)"
            >
              <Icons icon="plus" :size="13" color="rgba(79,195,247,0.8)" />
              Rotte hinzufügen
            </button>
          </div>

          <div v-if="event.rotten.length === 0" class="empty-rotten">
            <p>Noch keine Rotten. Füge mindestens eine Rotte hinzu.</p>
          </div>

          <div class="rotten-grid">
            <RotteEditorCard
              v-for="rotte in event.rotten"
              :key="rotte.rotteId"
              :rotte="rotte"
              @rename="(name) => store.renameRotte(eventId, rotte.rotteId, name)"
              @remove="confirmRemoveRotte(rotte)"
              @add-player="store.addPlayer(eventId, rotte.rotteId)"
              @remove-player="(pid) => store.removePlayer(eventId, rotte.rotteId, pid)"
              @update-player-name="(pid, name) => store.updatePlayerName(eventId, rotte.rotteId, pid, name)"
              @toggle-paid="(pid) => store.togglePlayerPaid(eventId, rotte.rotteId, pid)"
            />
          </div>
        </section>

        <!-- Payment warning modal -->
        <div v-if="showPaymentWarning" class="modal-overlay" @click.self="showPaymentWarning = false">
          <div class="warning-modal">
            <h3 class="modal-title">Nicht alle haben bezahlt</h3>
            <p class="modal-desc">Folgende Schützen haben noch nicht bezahlt:</p>
            <ul class="unpaid-list">
              <li v-for="p in unpaidPlayers" :key="p.id">{{ p.displayName || '(Kein Name)' }}</li>
            </ul>
            <div class="modal-actions">
              <button class="action-btn action-btn--cancel" @click="showPaymentWarning = false">Zurück</button>
              <button class="action-btn action-btn--start" @click="confirmStart">Trotzdem starten</button>
            </div>
          </div>
        </div>

        <!-- Start button -->
        <div class="start-section">
          <div class="payment-total">
            {{ paidPlayers.length }}/{{ totalPlayers }} Schützen bezahlt
          </div>
          <button
            class="start-btn"
            :disabled="event.rotten.length === 0"
            @click="handleStart"
          >
            <Icons icon="play" :size="15" color="#fff" />
            Wettkampf starten
          </button>
        </div>

      </div>
    </template>

    <!-- ══ ACTIVE ══ -->
    <template v-else-if="event.status === 'ACTIVE'">
      <ActiveCompetitionPanel
        :event="event"
        @stop="handleStop"
      />
    </template>

    <!-- ══ COMPLETED ══ -->
    <template v-else-if="event.status === 'COMPLETED'">
      <CompletedResultsPanel :event="event" />
    </template>

    <!-- ══ CANCELLED ══ -->
    <template v-else>
      <div class="content">
        <p class="cancelled-note">Dieser Wettkampf wurde abgebrochen.</p>
      </div>
    </template>

  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { useActivePasseStore } from '@/stores/activePasseStore.js'
import Icons from '@/components/Icons.vue'
import RotteEditorCard from '@/components/competition/RotteEditorCard.vue'
import ActiveCompetitionPanel from '@/components/competition/ActiveCompetitionPanel.vue'
import CompletedResultsPanel from '@/components/competition/CompletedResultsPanel.vue'

const props = defineProps({ id: { type: String, required: true } })
const router = useRouter()
const store = useCompetitionEventStore()
const activePasseStore = useActivePasseStore()

const eventId = computed(() => props.id)
const event = computed(() => store.getEvent(eventId.value))

const statusLabel = computed(() => {
  const map = { PLANNING: 'Planung', ACTIVE: 'Aktiv', COMPLETED: 'Abgeschlossen', CANCELLED: 'Abgebrochen' }
  return map[event.value?.status] ?? '–'
})

const stepCount = (steps) => {
  let c = 0
  for (const s of steps) {
    if (s.type === 'solo') c += 1
    else if (['pair', 'a_schuss', 'raffale'].includes(s.type)) c += 2
  }
  return c
}

const totalThrows = computed(() =>
  (event.value?.passen ?? []).reduce((sum, passe) =>
    sum + (passe.serien ?? []).reduce((s2, serie) => s2 + stepCount(serie.steps ?? []), 0), 0)
)

const totalPlayers = computed(() =>
  (event.value?.rotten ?? []).reduce((sum, r) => sum + r.players.length, 0)
)

const unpaidPlayers = computed(() =>
  (event.value?.rotten ?? []).flatMap(r => r.players.filter(p => !p.paid))
)

const paidPlayers = computed(() =>
  (event.value?.rotten ?? []).flatMap(r => r.players.filter(p => p.paid))
)

// ── Rotte removal ──────────────────────────────────────────────────────────
const confirmRemoveRotte = (rotte) => {
  if (rotte.players.length > 0) {
    if (!confirm(`Rotte "${rotte.name}" mit ${rotte.players.length} Schützen löschen?`)) return
  }
  store.removeRotte(eventId.value, rotte.rotteId)
}

// ── Start flow ─────────────────────────────────────────────────────────────
const showPaymentWarning = ref(false)

const handleStart = () => {
  if (unpaidPlayers.value.length > 0) {
    showPaymentWarning.value = true
  } else {
    confirmStart()
  }
}

const confirmStart = () => {
  showPaymentWarning.value = false
  store.startEvent(eventId.value)
}

// ── Stop ───────────────────────────────────────────────────────────────────
const handleStop = () => {
  if (confirm('Wettkampf wirklich abbrechen?')) {
    store.stopEvent(eventId.value)
  }
}

// ── Auto-complete watch ────────────────────────────────────────────────────
let completionInterval = null

onMounted(() => {
  completionInterval = setInterval(() => {
    if (event.value?.status === 'ACTIVE') {
      store.checkAndCompleteEvent(eventId.value)
    }
  }, 2000)
})

onUnmounted(() => clearInterval(completionInterval))
</script>

<style scoped>
.detail-view {
  display: flex; flex-direction: column; min-height: 100%;
  background: #1a1a2e; color: #fff;
}

.view-header {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: 24px 28px 20px; border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}

.header-left { display: flex; align-items: center; gap: 16px; }

.back-btn {
  display: flex; align-items: center; gap: 6px;
  background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1);
  border-radius: 10px; padding: 8px 14px;
  color: rgba(255,255,255,0.6); font-size: 13px; font-family: inherit;
  cursor: pointer; transition: background 0.15s; white-space: nowrap;
}
.back-btn:hover { background: rgba(255,255,255,0.09); }

.view-title { font-size: 22px; font-weight: 700; margin: 0 0 4px; }

.status-badge {
  font-size: 11px; font-weight: 700; border-radius: 8px; padding: 3px 10px;
}
.badge-planning { background: rgba(79,195,247,0.15); color: rgba(79,195,247,0.9); border: 1px solid rgba(79,195,247,0.25); }
.badge-active { background: rgba(246,173,85,0.15); color: rgba(246,173,85,0.9); border: 1px solid rgba(246,173,85,0.3); }
.badge-completed { background: rgba(72,187,120,0.15); color: rgba(72,187,120,0.9); border: 1px solid rgba(72,187,120,0.25); }
.badge-cancelled { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.4); border: 1px solid rgba(255,255,255,0.1); }

.content { flex: 1; overflow-y: auto; padding: 24px 28px 40px; display: flex; flex-direction: column; gap: 20px; }

.info-bar { display: flex; gap: 8px; flex-wrap: wrap; }

.info-chip {
  font-size: 12px; font-weight: 600; padding: 4px 12px;
  background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08);
  border-radius: 20px; color: rgba(255,255,255,0.5);
}

.payment-warning {
  display: flex; align-items: center; gap: 8px;
  background: rgba(246,173,85,0.08); border: 1px solid rgba(246,173,85,0.2);
  border-radius: 10px; padding: 10px 14px;
  font-size: 13px; color: rgba(246,173,85,0.9);
}

.section { display: flex; flex-direction: column; gap: 14px; }

.section-header { display: flex; align-items: center; justify-content: space-between; }

.section-title { font-size: 16px; font-weight: 700; margin: 0; }

.add-rotte-btn {
  display: flex; align-items: center; gap: 6px;
  background: rgba(79,195,247,0.08); border: 1px solid rgba(79,195,247,0.2);
  border-radius: 10px; padding: 7px 14px;
  color: rgba(79,195,247,0.8); font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.add-rotte-btn:hover:not(:disabled) { background: rgba(79,195,247,0.15); }
.add-rotte-btn:disabled { opacity: 0.3; cursor: not-allowed; }

.empty-rotten {
  padding: 24px; text-align: center;
  border: 1px dashed rgba(255,255,255,0.1); border-radius: 12px;
  color: rgba(255,255,255,0.25); font-size: 13px;
}

.rotten-grid { display: flex; flex-direction: column; gap: 12px; }

.start-section {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px;
  background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.07);
  border-radius: 14px;
}

.payment-total { font-size: 13px; color: rgba(255,255,255,0.4); }

.start-btn {
  display: flex; align-items: center; gap: 8px;
  background: rgba(79,195,247,0.2); border: 1px solid rgba(79,195,247,0.4);
  border-radius: 12px; padding: 11px 24px;
  color: #4fc3f7; font-size: 14px; font-weight: 700; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.start-btn:hover:not(:disabled) { background: rgba(79,195,247,0.3); }
.start-btn:disabled { opacity: 0.3; cursor: not-allowed; }

/* ── Payment warning modal ── */
.modal-overlay {
  position: fixed; inset: 0; z-index: 100;
  background: rgba(0,0,0,0.6); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center; padding: 20px;
}

.warning-modal {
  background: #1e2240; border: 1px solid rgba(246,173,85,0.3);
  border-radius: 18px; padding: 24px; max-width: 360px; width: 100%;
  display: flex; flex-direction: column; gap: 14px;
}

.modal-title { font-size: 16px; font-weight: 700; margin: 0; color: rgba(246,173,85,0.9); }
.modal-desc { font-size: 13px; color: rgba(255,255,255,0.5); margin: 0; }

.unpaid-list {
  margin: 0; padding: 0 0 0 16px;
  display: flex; flex-direction: column; gap: 4px;
}
.unpaid-list li { font-size: 13px; color: rgba(255,255,255,0.7); }

.modal-actions { display: flex; gap: 8px; }

.action-btn {
  flex: 1; padding: 10px; border-radius: 10px;
  font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; border: 1px solid transparent; transition: background 0.15s;
}
.action-btn--cancel {
  background: transparent; border-color: rgba(255,255,255,0.12); color: rgba(255,255,255,0.5);
}
.action-btn--cancel:hover { background: rgba(255,255,255,0.05); }
.action-btn--start {
  background: rgba(79,195,247,0.2); border-color: rgba(79,195,247,0.4); color: #4fc3f7;
}
.action-btn--start:hover { background: rgba(79,195,247,0.3); }

.not-found, .cancelled-note {
  padding: 40px 28px; text-align: center; color: rgba(255,255,255,0.3); font-size: 14px;
}
</style>
