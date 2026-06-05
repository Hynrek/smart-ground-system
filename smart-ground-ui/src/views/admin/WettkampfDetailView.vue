<!-- src/views/admin/WettkampfDetailView.vue -->
<template>
  <div class="detail-view">

    <!-- Header -->
    <div class="view-header">
      <div class="header-left">
        <button class="back-btn" @click="router.push('/admin/wettkampf')">
          <Icons icon="chevronLeft" :size="16" color="var(--sg-text-muted)" />
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

    <!-- ══ SETUP (Planung) ══ -->
    <template v-else-if="event.status?.toUpperCase() === 'SETUP'">
      <div class="content">
        <div class="info-bar">
          <span class="info-chip">{{ (event.passen?.length ?? 0) }} Passen</span>
          <span class="info-chip">{{ totalThrows }} Würfe</span>
          <span class="info-chip">{{ totalPlayers }} Schützen</span>
        </div>
        <div v-if="unpaidPlayers.length > 0" class="payment-warning">
          <Icons icon="alert" :size="14" color="rgba(246,173,85,0.9)" />
          {{ unpaidPlayers.length }} Schützen haben noch nicht bezahlt
        </div>
        <section class="section">
          <div class="section-header">
            <h2 class="section-title">Rotten</h2>
            <button class="add-rotte-btn" :disabled="(event.groups ?? []).length >= 8" @click="store.addRotte(eventId)">
              <Icons icon="plus" :size="13" /> Rotte hinzufügen
            </button>
          </div>
          <div v-if="(event.groups ?? []).length === 0" class="empty-rotten">
            <p>Noch keine Rotten. Füge mindestens eine Rotte hinzu.</p>
          </div>
          <div class="rotten-grid">
            <RotteEditorCard
              v-for="group in (event.groups ?? [])"
              :key="group.id"
              :rotte="toRotteShape(group)"
              :available-users="availableUsers"
              @rename="(name) => store.renameRotte(eventId, group.id, name)"
              @remove="confirmRemoveRotte(group)"
              @add-player="(user) => store.addPlayer(eventId, group.id, user)"
              @remove-player="(pid) => store.removePlayer(eventId, group.id, pid)"
              @toggle-paid="(pid) => store.togglePlayerPaid(eventId, group.id, pid)"
            />
          </div>
        </section>
        <div class="start-section">
          <div class="payment-total">{{ paidPlayers.length }}/{{ totalPlayers }} Schützen bezahlt</div>
          <button class="start-btn" :disabled="(event.groups ?? []).length === 0" @click="handleOpen">
            <Icons icon="play" :size="15" color="#fff" />
            Veröffentlichen
          </button>
        </div>
      </div>
    </template>

    <!-- ══ OPEN ══ -->
    <template v-else-if="event.status?.toUpperCase() === 'OPEN'">
      <div class="content">
        <div class="info-bar">
          <span class="info-chip">{{ totalThrows }} Würfe</span>
          <span class="info-chip">{{ totalPlayers }} Schützen</span>
        </div>
        <div v-if="unpaidPlayers.length > 0" class="payment-warning">
          <Icons icon="alert" :size="14" color="rgba(246,173,85,0.9)" />
          {{ unpaidPlayers.length }} Schützen haben noch nicht bezahlt
        </div>
        <section class="section">
          <div class="section-header">
            <h2 class="section-title">Rotten</h2>
            <button class="add-rotte-btn" :disabled="(event.groups ?? []).length >= 8" @click="store.addRotte(eventId)">
              <Icons icon="plus" :size="13" /> Rotte hinzufügen
            </button>
          </div>
          <div class="rotten-grid">
            <RotteEditorCard
              v-for="group in (event.groups ?? [])"
              :key="group.id"
              :rotte="toRotteShape(group)"
              :available-users="availableUsers"
              @rename="(name) => store.renameRotte(eventId, group.id, name)"
              @remove="confirmRemoveRotte(group)"
              @add-player="(user) => store.addPlayer(eventId, group.id, user)"
              @remove-player="(pid) => store.removePlayer(eventId, group.id, pid)"
              @toggle-paid="(pid) => store.togglePlayerPaid(eventId, group.id, pid)"
            />
          </div>
        </section>
        <div v-if="showPaymentWarning" class="modal-overlay" @click.self="showPaymentWarning = false">
          <div class="warning-modal">
            <h3 class="modal-title">Nicht alle haben bezahlt</h3>
            <p class="modal-desc">Folgende Schützen haben noch nicht bezahlt:</p>
            <ul class="unpaid-list">
              <li v-for="p in unpaidPlayers" :key="p.id">{{ p.displayName }}</li>
            </ul>
            <div class="modal-actions">
              <button class="action-btn action-btn--cancel" @click="showPaymentWarning = false">Zurück</button>
              <button class="action-btn action-btn--start" @click="confirmStart">Trotzdem starten</button>
            </div>
          </div>
        </div>
        <div class="start-section">
          <div class="payment-total">{{ paidPlayers.length }}/{{ totalPlayers }} bezahlt</div>
          <button class="start-btn" @click="handleStart">
            <Icons icon="play" :size="15" color="#fff" />
            Wettkampf starten
          </button>
        </div>
      </div>
    </template>

    <!-- ══ ACTIVE / PRE_COMPLETE ══ -->
    <template v-else-if="['ACTIVE', 'PRE_COMPLETE'].includes(event.status?.toUpperCase())">
      <ActiveCompetitionPanel :event="event" @stop="handleStop" />
    </template>

    <!-- ══ COMPLETED ══ -->
    <template v-else-if="event.status?.toUpperCase() === 'COMPLETED'">
      <CompletedResultsPanel :event="event" />
    </template>

    <!-- ══ ABANDONED / fallback ══ -->
    <template v-else>
      <div class="content">
        <p class="cancelled-note">Dieser Wettkampf wurde abgebrochen.</p>
      </div>
    </template>

  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { useActivePasseStore } from '@/stores/activePasseStore.js'
import { useUserStore } from '@/stores/userStore.js'
import Icons from '@/components/Icons.vue'
import RotteEditorCard from '@/components/competition/RotteEditorCard.vue'
import ActiveCompetitionPanel from '@/components/competition/ActiveCompetitionPanel.vue'
import CompletedResultsPanel from '@/components/competition/CompletedResultsPanel.vue'

const props = defineProps({ id: { type: String, required: true } })
const router = useRouter()
const store = useCompetitionEventStore()
const activePasseStore = useActivePasseStore()
const userStore = useUserStore()

const eventId = computed(() => props.id)
const event = computed(() => store.getEvent(eventId.value))

const statusLabel = computed(() => {
  const map = {
    SETUP: 'Planung', OPEN: 'Offen', ACTIVE: 'Aktiv',
    PRE_COMPLETE: 'Auswertung', COMPLETED: 'Abgeschlossen', ABANDONED: 'Abgebrochen',
  }
  return map[event.value?.status?.toUpperCase()] ?? '–'
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
  (event.value?.groups ?? []).reduce((sum, g) => sum + (g.members?.length ?? 0), 0)
)

const unpaidPlayers = computed(() =>
  (event.value?.groups ?? []).flatMap(g => (g.members ?? []).filter(m => !m.paid))
)

const paidPlayers = computed(() =>
  (event.value?.groups ?? []).flatMap(g => (g.members ?? []).filter(m => m.paid))
)

const assignedUserIds = computed(() =>
  new Set(
    (event.value?.groups ?? [])
      .flatMap(g => (g.members ?? []).map(m => m.userId))
      .filter(Boolean)
  )
)

const toRotteShape = (group) => ({
  rotteId: group.id,
  name: group.name,
  players: (group.members ?? []).map(m => ({
    id: m.id,
    userId: m.userId ?? null,
    displayName: m.displayName,
    paid: m.paid ?? false,
  })),
})

const availableUsers = computed(() =>
  userStore.users
    .filter(u => !assignedUserIds.value.has(u.id))
    .map(u => ({ id: u.id, displayName: u.fullName || u.username || u.email || u.id }))
)

// ── Rotte removal ──────────────────────────────────────────────────────────
const confirmRemoveRotte = (group) => {
  const count = group.members?.length ?? group.players?.length ?? 0
  const name = group.name
  if (count > 0) {
    if (!confirm(`Rotte "${name}" mit ${count} Schützen löschen?`)) return
  }
  store.removeRotte(eventId.value, group.id ?? group.rotteId)
}

// ── Open (SETUP → OPEN) ────────────────────────────────────────────────────
const handleOpen = () => store.openEvent(eventId.value)

// ── Start (OPEN → ACTIVE) ──────────────────────────────────────────────────
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

onMounted(async () => {
  try { await userStore.loadUsers() } catch { /* error handled by userStore */ }
  if (store.events.length === 0) await store.loadEvents()
})
</script>

<style scoped>
.detail-view {
  display: flex; flex-direction: column; min-height: 100%;
}

.view-header {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: 24px 28px 20px; border-bottom: 1px solid var(--sg-border);
  flex-shrink: 0;
}

.header-left { display: flex; align-items: center; gap: 16px; }

.back-btn {
  display: flex; align-items: center; gap: 6px;
  background: var(--sg-bg-panel); border: 1px solid var(--sg-border);
  border-radius: 10px; padding: 8px 14px;
  color: var(--sg-text-muted); font-size: 13px; font-family: inherit;
  cursor: pointer; transition: background 0.15s; white-space: nowrap;
}
.back-btn:hover { background: var(--sg-border); }

.view-title { font-size: 22px; font-weight: 700; color: var(--sg-brand); margin: 0 0 4px; }

.status-badge {
  font-size: 11px; font-weight: 700; border-radius: 8px; padding: 3px 10px;
}
.badge-setup { background: var(--sg-accent-tint); color: var(--sg-color-info-text); border: 1px solid var(--sg-color-info-bg); }
.badge-open { background: var(--sg-color-info-bg); color: var(--sg-color-info-text); border: 1px solid var(--sg-accent); }
.badge-active { background: var(--sg-color-warning-bg); color: var(--sg-color-warning-text); border: 1px solid color-mix(in srgb, var(--sg-color-warning) 40%, transparent); }
.badge-pre_complete { background: var(--sg-color-warning-bg); color: var(--sg-color-warning-text); border: 1px solid color-mix(in srgb, var(--sg-color-warning) 40%, transparent); }
.badge-completed { background: var(--sg-color-success-bg); color: var(--sg-color-success-text); border: 1px solid color-mix(in srgb, var(--sg-color-success) 40%, transparent); }
.badge-abandoned { background: var(--sg-bg-panel); color: var(--sg-text-faint); border: 1px solid var(--sg-border); }

.content { flex: 1; overflow-y: auto; padding: 24px 28px 40px; display: flex; flex-direction: column; gap: 20px; }

.info-bar { display: flex; gap: 8px; flex-wrap: wrap; }

.info-chip {
  font-size: 12px; font-weight: 600; padding: 4px 12px;
  background: var(--sg-bg-panel); border: 1px solid var(--sg-border);
  border-radius: 20px; color: var(--sg-text-muted);
}

.payment-warning {
  display: flex; align-items: center; gap: 8px;
  background: var(--sg-color-warning-bg); border: 1px solid color-mix(in srgb, var(--sg-color-warning) 40%, transparent);
  border-radius: 10px; padding: 10px 14px;
  font-size: 13px; color: var(--sg-color-warning-text);
}

.section { display: flex; flex-direction: column; gap: 14px; }

.section-header { display: flex; align-items: center; justify-content: space-between; }

.section-title { font-size: 16px; font-weight: 700; color: var(--sg-brand); margin: 0; }

.add-rotte-btn {
  display: flex; align-items: center; gap: 6px;
  background: var(--sg-accent-subtle); border: 1px solid var(--sg-color-info-bg);
  border-radius: 10px; padding: 7px 14px;
  color: var(--sg-color-info-text); font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.add-rotte-btn:hover:not(:disabled) { background: var(--sg-accent-tint); }
.add-rotte-btn:disabled { opacity: 0.3; cursor: not-allowed; }

.empty-rotten {
  padding: 24px; text-align: center;
  border: 1px dashed var(--sg-border); border-radius: 12px;
  color: var(--sg-text-faint); font-size: 13px;
}

.rotten-grid { display: flex; flex-direction: column; gap: 12px; }

.start-section {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px;
  background: var(--sg-bg-card); border: 1px solid var(--sg-border);
  border-radius: 14px; box-shadow: var(--sg-shadow-sm);
}

.payment-total { font-size: 13px; color: var(--sg-text-faint); }

.start-btn {
  display: flex; align-items: center; gap: 8px;
  background: var(--sg-accent-hover); border: none;
  border-radius: 12px; padding: 11px 24px;
  color: #fff; font-size: 14px; font-weight: 700; font-family: inherit;
  cursor: pointer; transition: background 0.15s;
}
.start-btn:hover:not(:disabled) { background: var(--sg-accent); }
.start-btn:disabled { opacity: 0.3; cursor: not-allowed; }

/* ── Payment warning modal ── */
.modal-overlay {
  position: fixed; inset: 0; z-index: 100;
  background: rgba(0,0,0,0.4); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center; padding: 20px;
}

.warning-modal {
  background: var(--sg-bg-card); border: 1px solid color-mix(in srgb, var(--sg-color-warning) 40%, transparent);
  border-radius: 18px; padding: 24px; max-width: 360px; width: 100%;
  display: flex; flex-direction: column; gap: 14px;
  box-shadow: var(--sg-shadow-lg);
}

.modal-title { font-size: 16px; font-weight: 700; margin: 0; color: var(--sg-color-warning-text); }
.modal-desc { font-size: 13px; color: var(--sg-text-muted); margin: 0; }

.unpaid-list {
  margin: 0; padding: 0 0 0 16px;
  display: flex; flex-direction: column; gap: 4px;
}
.unpaid-list li { font-size: 13px; color: var(--sg-text-muted); }

.modal-actions { display: flex; gap: 8px; }

.action-btn {
  flex: 1; padding: 10px; border-radius: 10px;
  font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; border: 1px solid transparent; transition: background 0.15s;
}
.action-btn--cancel {
  background: transparent; border-color: var(--sg-border); color: var(--sg-text-muted);
}
.action-btn--cancel:hover { background: var(--sg-bg-panel); }
.action-btn--start {
  background: var(--sg-accent-tint); border-color: var(--sg-accent); color: var(--sg-color-info-text);
}
.action-btn--start:hover { background: color-mix(in srgb, var(--sg-accent) 20%, transparent); }

.not-found, .cancelled-note {
  padding: 40px 28px; text-align: center; color: var(--sg-text-faint); font-size: 14px;
}
</style>
