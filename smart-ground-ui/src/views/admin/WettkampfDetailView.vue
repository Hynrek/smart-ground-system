<!-- src/views/admin/WettkampfDetailView.vue -->
<template>
  <div class="detail-view">

    <!-- Header -->
    <div class="view-header">
      <div class="header-left">
        <button class="back-btn" @click="router.push('/admin/wettkampf?tab=active')">
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

        <div class="tab-row">
          <button class="tab-btn" :class="{ active: setupTab === 'rotten' }" @click="setSetupTab('rotten', { replace: true }); showPassePicker = false">Rotten</button>
          <button class="tab-btn" :class="{ active: setupTab === 'passen' }" @click="setSetupTab('passen', { replace: true }); showPassePicker = false">Passen</button>
        </div>

        <!-- Rotten tab -->
        <section v-if="setupTab === 'rotten'" class="section">
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

        <!-- Passen tab -->
        <section v-else-if="setupTab === 'passen'" class="section">
          <div class="section-header">
            <h2 class="section-title">Passen</h2>
            <div class="add-passe-wrap">
              <button
                class="add-rotte-btn"
                :disabled="availablePassen.length === 0"
                @click="showPassePicker = !showPassePicker"
              >
                <Icons icon="plus" :size="13" /> Passe hinzufügen
              </button>
              <PassePickerDropdown
                v-if="showPassePicker"
                :passen="availablePassen"
                @select="handleAddPasse"
                @close="showPassePicker = false"
              />
            </div>
          </div>
          <div v-if="(event.passen ?? []).length === 0" class="empty-rotten">
            <p>Noch keine Passen. Füge mindestens eine Passe hinzu.</p>
          </div>
          <div v-else class="passen-list">
            <div v-for="passe in (event.passen ?? [])" :key="passe.id" class="passe-row">
              <div class="passe-info">
                <span class="passe-name">{{ passe.name }}</span>
                <span class="passe-meta">{{ passe.serien?.length ?? 0 }} Serien</span>
              </div>
              <button class="remove-btn" @click="store.removePasseFromEvent(eventId, passe.id)">
                <Icons icon="x" :size="12" color="var(--sg-text-faint)" />
              </button>
            </div>
          </div>
        </section>

        <div class="start-section">
          <div class="payment-total">{{ paidPlayers.length }}/{{ totalPlayers }} Schützen bezahlt</div>
          <button class="start-btn" :disabled="(event.groups ?? []).length === 0" @click="handleOpen">
            <Icons icon="play" :size="15" color="#fff" />
            Wettkampf starten
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

    <!-- ══ ACTIVE ══ -->
    <template v-else-if="event.status?.toUpperCase() === 'ACTIVE'">
      <ActiveCompetitionPanel :event="event" @stop="handleStop" />
    </template>

    <!-- ══ PRE_COMPLETE (Auswertung + Stechen) ══ -->
    <template v-else-if="event.status?.toUpperCase() === 'PRE_COMPLETE'">
      <div class="content">
        <ActiveCompetitionPanel :event="event" :tied-blocks="tiedBlocks" @stop="handleStop" />

        <StechenPanel :session-id="eventId" />

        <div class="start-section">
          <div class="payment-total">
            {{ unresolvedTieBlocks.length > 0
              ? `${unresolvedTieBlocks.length} offene Gleichstände`
              : 'Keine offenen Gleichstände' }}
          </div>
          <button class="start-btn" :disabled="finishing" @click="handleFinish">
            <Icons icon="play" :size="15" color="#fff" />
            Wettkampf abschliessen
          </button>
        </div>
      </div>

      <!-- Finish-guard dialog: unresolved decisive ties -->
      <div v-if="showFinishGuard" class="modal-overlay" @click.self="cancelFinish">
        <div class="warning-modal" role="dialog" aria-modal="true" aria-labelledby="finish-guard-title">
          <h3 id="finish-guard-title" class="modal-title">Ungelöste Gleichstände</h3>
          <p class="modal-desc">
            Es gibt noch ungelöste Gleichstände auf den vordersten Plätzen.
          </p>
          <ul class="unpaid-list">
            <li v-for="block in finishGuardTies" :key="block.tiePosition">
              Platz {{ block.tiePosition }} —
              {{ (block.players ?? []).map(p => p.displayName).join(', ') }}
            </li>
          </ul>
          <div class="modal-actions">
            <button class="action-btn action-btn--cancel" @click="cancelFinish">Zurück</button>
            <button class="action-btn action-btn--start" :disabled="finishing" @click="forceFinish">
              Trotzdem abschliessen
            </button>
          </div>
        </div>
      </div>
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
import { computed, ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUrlTab } from '@/composables/useUrlTab.js'
import { storeToRefs } from 'pinia'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { useUserStore } from '@/stores/userStore.js'
import { usePasseStore } from '@/stores/passeStore.js'
import Icons from '@/components/Icons.vue'
import RotteEditorCard from '@/components/competition/RotteEditorCard.vue'
import PassePickerDropdown from '@/components/competition/PassePickerDropdown.vue'
import ActiveCompetitionPanel from '@/components/competition/ActiveCompetitionPanel.vue'
import CompletedResultsPanel from '@/components/competition/CompletedResultsPanel.vue'
import StechenPanel from '@/components/competition/StechenPanel.vue'

const props = defineProps({ id: { type: String, required: true } })
const router = useRouter()
const store = useCompetitionEventStore()
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

const passeStore = usePasseStore()
const { activeTab: setupTab, setTab: setSetupTab } = useUrlTab('rotten', ['rotten', 'passen'])
const showPassePicker = ref(false)

const assignedPasseIds = computed(() =>
  new Set((event.value?.passen ?? []).map(p => p.id))
)

const availablePassen = computed(() =>
  passeStore.savedGlobalPassen.filter(p => !assignedPasseIds.value.has(p.id))
)

const handleAddPasse = async (passe) => {
  showPassePicker.value = false
  await store.addPasseToEvent(eventId.value, passe.id)
}

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
const handleStop = async () => {
  if (confirm('Wettkampf wirklich abbrechen? Der Wettkampf wird gelöscht.')) {
    await store.stopEvent(eventId.value)
    router.push('/admin/wettkampf?tab=active')
  }
}

// ── Stechen (tiebreaker) ─────────────────────────────────────────────────────
const { tiesBySession } = storeToRefs(store)

const tiedBlocks = computed(() => tiesBySession.value[eventId.value]?.tiedBlocks ?? [])
const unresolvedTieBlocks = computed(() => tiedBlocks.value.filter(b => !b.resolved))

const isPreComplete = computed(() => event.value?.status?.toUpperCase() === 'PRE_COMPLETE')

// Load ties whenever this view is showing a PRE_COMPLETE competition. Re-runs if
// the session reaches PRE_COMPLETE while the view is mounted.
watch(isPreComplete, (pre) => {
  if (pre) store.loadTies(eventId.value).catch(e => console.error('[WettkampfDetailView] loadTies failed:', e))
}, { immediate: true })

// ── Finish guard ─────────────────────────────────────────────────────────────
const finishing = ref(false)
const showFinishGuard = ref(false)
const finishGuardTies = ref([])

const handleFinish = async () => {
  if (finishing.value) return
  finishing.value = true
  try {
    const res = await store.finishEvent(eventId.value, false)
    if (res.completed) {
      router.push('/admin/wettkampf?tab=active')
    } else {
      finishGuardTies.value = res.unresolvedTies ?? []
      showFinishGuard.value = true
    }
  } catch (e) {
    console.error('[WettkampfDetailView] finish failed:', e)
  } finally {
    finishing.value = false
  }
}

const forceFinish = async () => {
  if (finishing.value) return
  finishing.value = true
  try {
    const res = await store.finishEvent(eventId.value, true)
    if (res.completed) {
      showFinishGuard.value = false
      router.push('/admin/wettkampf?tab=active')
    }
  } catch (e) {
    console.error('[WettkampfDetailView] force finish failed:', e)
  } finally {
    finishing.value = false
  }
}

const cancelFinish = () => {
  showFinishGuard.value = false
  finishGuardTies.value = []
}

onMounted(async () => {
  try { await userStore.loadUsers() } catch { /* error handled by userStore */ }
  if (store.events.length === 0) await store.loadEvents()
  if (passeStore.savedPassen.length === 0) await passeStore.loadPassenFromStorage()
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

/* ── Tabs ── */
.tab-row {
  display: flex;
  border-bottom: 1px solid var(--sg-border);
}

.tab-btn {
  padding: 9px 20px;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  color: var(--sg-text-faint);
  cursor: pointer;
  margin-bottom: -1px;
  transition: color 0.15s, border-color 0.15s;
}

.tab-btn.active {
  color: var(--sg-brand);
  border-bottom-color: var(--sg-accent);
}

.tab-btn:hover:not(.active) { color: var(--sg-text-muted); }

/* ── Passen tab ── */
.add-passe-wrap { position: relative; }

.passen-list { display: flex; flex-direction: column; gap: 8px; }

.passe-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 12px;
}

.passe-info { display: flex; flex-direction: column; gap: 2px; }

.passe-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--sg-brand);
}

.passe-meta {
  font-size: 12px;
  color: var(--sg-text-faint);
}

.remove-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}

.remove-btn:hover {
  background: rgba(229, 62, 62, 0.08);
  border-color: #e53e3e;
}
</style>
