<!-- src/views/admin/WettkampfListView.vue -->
<template>
  <div class="list-view">
    <div class="view-header">
      <div>
        <h1 class="view-title">Wettkampf</h1>
        <p class="view-subtitle">Wettkämpfe planen, starten und auswerten</p>
      </div>
    </div>

    <!-- Tab toggle -->
    <div class="tab-bar">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        class="tab-btn"
        :class="{ active: activeTab === tab.id }"
        @click="setTab(tab.id)"
      >
        {{ tab.label }}
        <span class="tab-count">{{ tab.count }}</span>
      </button>
    </div>

    <div class="content">

      <!-- ══ PLANUNG ══ -->
      <template v-if="activeTab === 'planning'">
        <div class="event-list">
          <div
            v-for="ev in store.planningEvents"
            :key="ev.id"
            class="event-card"
            @click="router.push('/admin/wettkampf/' + ev.id)"
          >
            <div class="ec-main">
              <div class="ec-info">
                <span class="ec-name">{{ ev.name }}</span>
                <div class="ec-meta-row">
                  <span class="ec-meta">{{ ev.passen?.length ?? 0 }} Passen</span>
                  <span class="ec-dot">·</span>
                  <span class="ec-meta">{{ (ev.groups ?? []).length }} Rotten</span>
                  <span class="ec-dot">·</span>
                  <span class="ec-meta">{{ totalPlayers(ev) }} Schützen</span>
                  <template v-if="unpaidCount(ev) > 0">
                    <span class="ec-dot">·</span>
                    <span class="ec-meta ec-meta--warn">{{ unpaidCount(ev) }} offen</span>
                  </template>
                </div>
              </div>
              <Icons icon="chevronRight" :size="14" color="var(--sg-border-input)" />
            </div>
            <div class="ec-actions" @click.stop>
              <button class="ec-delete-btn" @click="handleDelete(ev)">
                <Icons icon="trash" :size="13" color="rgba(252,129,129,0.6)" />
              </button>
            </div>
          </div>
        </div>

        <!-- Create panel -->
        <div v-if="creating" class="create-panel">
          <div class="create-title">Neuer Wettkampf</div>
          <div class="create-field">
            <label class="create-label">Name</label>
            <input
              v-model="newName"
              class="create-input"
              placeholder="z.B. Frühjahrspokal 2026"
              maxlength="60"
              @keydown.enter="confirmCreate"
            />
          </div>
          <div class="create-field">
            <label class="create-label">Passen auswählen <span class="create-count">{{ selectedPassen.length }} gewählt</span></label>
            <p v-if="availablePassen.length === 0" class="create-hint">Noch keine Passen vorhanden. Erstelle zuerst Passen unter "Passen".</p>
            <div v-else class="passen-picker">
              <div
                v-for="passe in availablePassen"
                :key="passe.id"
                class="picker-item"
                :class="{ selected: isSelected(passe.id) }"
                @click="togglePasse(passe)"
              >
                <div class="picker-check">
                  <span v-if="isSelected(passe.id)" class="picker-order">{{ selectedOrder(passe.id) }}</span>
                  <Icons v-else icon="plus" :size="11" color="rgba(255,255,255,0.3)" />
                </div>
                <span class="picker-name">{{ passe.name }}</span>
              </div>
            </div>
          </div>
          <div class="create-actions">
            <button class="action-btn action-btn--cancel" @click="cancelCreate">Abbrechen</button>
            <button
              class="action-btn action-btn--primary"
              :disabled="!newName.trim() || selectedPassen.length === 0"
              @click="confirmCreate"
            >Erstellen</button>
          </div>
        </div>

        <button v-if="!creating" class="new-btn" @click="creating = true">
          <Icons icon="plus" :size="15" />
          Neuer Wettkampf
        </button>

        <div v-if="store.planningEvents.length === 0 && !creating" class="empty-state">
          <p>Noch keine Wettkämpfe geplant.</p>
          <p class="empty-hint">Klicke auf "Neuer Wettkampf" um loszulegen.</p>
        </div>
      </template>

      <!-- ══ AKTIV ══ -->
      <template v-else-if="activeTab === 'active'">
        <div class="event-list">
          <div
            v-for="ev in store.activeEvents"
            :key="ev.id"
            class="event-card"
            :class="isPreComplete(ev) ? 'event-card--pre-complete' : 'event-card--active'"
            @click="router.push('/admin/wettkampf/' + ev.id)"
          >
            <div class="ec-main">
              <div class="ec-info">
                <div class="ec-name-row">
                  <span class="ec-name">{{ ev.name }}</span>
                  <span class="phase-chip" :class="isPreComplete(ev) ? 'phase-chip--pre-complete' : 'phase-chip--active'">
                    {{ phaseLabel(ev) }}
                  </span>
                </div>
                <div class="ec-meta-row">
                  <span class="ec-meta">{{ (ev.groups ?? []).length }} Rotten</span>
                  <span class="ec-dot">·</span>
                  <span class="ec-meta">{{ totalPlayers(ev) }} Schützen</span>
                </div>
              </div>
              <div class="ec-active-dot" :class="{ 'ec-active-dot--pre-complete': isPreComplete(ev) }" />
              <Icons icon="chevronRight" :size="14" color="var(--sg-border-input)" />
            </div>
          </div>
        </div>
        <div v-if="store.activeEvents.length === 0" class="empty-state">
          <p>Keine aktiven Wettkämpfe.</p>
        </div>
      </template>

      <!-- ══ ABGESCHLOSSEN ══ -->
      <template v-else>
        <div class="event-list">
          <div
            v-for="ev in store.completedEvents"
            :key="ev.id"
            class="event-card"
            @click="router.push('/admin/wettkampf/' + ev.id)"
          >
            <div class="ec-main">
              <div class="ec-info">
                <span class="ec-name">{{ ev.name }}</span>
                <div class="ec-meta-row">
                  <span class="ec-meta">{{ new Date(ev.completedAt).toLocaleDateString('de-CH') }}</span>
                  <span class="ec-dot">·</span>
                  <span class="ec-meta">{{ totalPlayers(ev) }} Schützen</span>
                </div>
              </div>
              <Icons icon="chevronRight" :size="14" color="var(--sg-border-input)" />
            </div>
          </div>
        </div>
        <div v-if="store.completedEvents.length === 0" class="empty-state">
          <p>Noch keine abgeschlossenen Wettkämpfe.</p>
        </div>
      </template>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { usePasseStore } from '@/stores/passeStore.js'
import Icons from '@/components/Icons.vue'

const router = useRouter()
const route = useRoute()
const store = useCompetitionEventStore()
const passeStore = usePasseStore()

const VALID_TABS = ['planning', 'active', 'completed']
const activeTab = computed(() => VALID_TABS.includes(route.query.tab) ? route.query.tab : 'planning')
const setTab = (id) => router.replace({ query: { tab: id } })

const tabs = computed(() => [
  { id: 'planning', label: 'Planung', count: store.planningEvents.length },
  { id: 'active', label: 'Aktiv', count: store.activeEvents.length },
  { id: 'completed', label: 'Abgeschlossen', count: store.completedEvents.length },
])

// ── Create flow ────────────────────────────────────────────────────────────
const creating = ref(false)
const newName = ref('')
const selectedPassen = ref([])

const availablePassen = computed(() => passeStore.savedGlobalPassen)

const isSelected = (id) => selectedPassen.value.some(p => p.id === id)

const selectedOrder = (id) => selectedPassen.value.findIndex(p => p.id === id) + 1

const togglePasse = (passe) => {
  const idx = selectedPassen.value.findIndex(p => p.id === passe.id)
  if (idx >= 0) selectedPassen.value = selectedPassen.value.filter(p => p.id !== passe.id)
  else selectedPassen.value = [...selectedPassen.value, passe]
}

const cancelCreate = () => {
  creating.value = false
  newName.value = ''
  selectedPassen.value = []
}

const confirmCreate = async () => {
  if (!newName.value.trim() || selectedPassen.value.length === 0) return
  const passenSnapshots = selectedPassen.value.map(p => ({
    id: p.id,
    name: p.name,
    serieIds: (p.serien ?? []).map(s => s.id),
  }))
  const id = await store.createEvent(newName.value.trim(), passenSnapshots, [])
  cancelCreate()
  router.push('/admin/wettkampf/' + id)
}

// ── Delete ─────────────────────────────────────────────────────────────────
const handleDelete = async (ev) => {
  if (confirm(`"${ev.name}" löschen?`)) await store.deleteEvent(ev.id)
}

// ── Helpers ────────────────────────────────────────────────────────────────
const totalPlayers = (ev) => (ev.groups ?? []).reduce((s, g) => s + (g.members?.length ?? 0), 0)

// Active-tab events are either still being played (ACTIVE) or done shooting and
// awaiting Stechen/finish (PRE_COMPLETE) — surface that distinction on the card.
const isPreComplete = (ev) => ev.status?.toUpperCase() === 'PRE_COMPLETE'
const phaseLabel = (ev) => isPreComplete(ev) ? 'In Auswertung' : 'Aktiv'

const unpaidCount = (ev) => (ev.groups ?? []).reduce((s, g) => s + (g.members ?? []).filter(m => !m.paid).length, 0)

// ── Load on mount ──────────────────────────────────────────────────────────
onMounted(() => {
  store.loadEvents()
  passeStore.loadPassenFromStorage()
})
</script>

<style scoped>
.list-view { display: flex; flex-direction: column; height: 100%; }

.view-header {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: 24px 28px 20px; border-bottom: 1px solid var(--sg-border); flex-shrink: 0;
}

.view-title { font-size: 22px; font-weight: 700; color: var(--sg-brand); margin: 0 0 4px; }

.view-subtitle { font-size: 13px; color: var(--sg-text-muted); margin: 0; }

.tab-bar {
  display: flex; gap: 0; padding: 0 28px;
  border-bottom: 1px solid var(--sg-border); flex-shrink: 0;
}

.tab-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 12px 18px; background: transparent; border: none;
  border-bottom: 2px solid transparent; margin-bottom: -1px;
  color: var(--sg-text-faint); font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.tab-btn.active { color: var(--sg-brand); border-bottom-color: var(--sg-accent); }
.tab-btn:hover:not(.active) { color: var(--sg-text-muted); }

.tab-count {
  font-size: 10px; font-weight: 700;
  background: var(--sg-accent-tint); color: var(--sg-color-info-text);
  border-radius: 10px; padding: 1px 6px;
}

.content { flex: 1; overflow-y: auto; padding: 20px 28px 40px; display: flex; flex-direction: column; gap: 10px; }

.event-list { display: flex; flex-direction: column; gap: 8px; }

.event-card {
  background: var(--sg-bg-card); border-radius: 12px;
  box-shadow: var(--sg-shadow-sm); padding: 14px 16px; cursor: pointer;
  transition: box-shadow 0.15s; display: flex; flex-direction: column; gap: 8px;
}
.event-card:hover { box-shadow: var(--sg-shadow-md); }
.event-card--active { border-left: 3px solid var(--sg-color-warning); }
.event-card--pre-complete { border-left: 3px solid var(--sg-color-info-text); }

.ec-main { display: flex; align-items: center; gap: 10px; }

.ec-info { flex: 1; display: flex; flex-direction: column; gap: 4px; }

.ec-name-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

.ec-name { font-size: 15px; font-weight: 600; color: var(--sg-brand); }

.phase-chip {
  font-size: 10.5px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.3px;
  padding: 2px 8px; border-radius: 99px;
}
.phase-chip--active { background: var(--sg-color-warning-bg); color: var(--sg-color-warning-text); }
.phase-chip--pre-complete { background: var(--sg-accent-tint); color: var(--sg-color-info-text); }

.ec-meta-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }

.ec-meta { font-size: 12px; color: var(--sg-text-faint); }
.ec-meta--warn { color: var(--sg-color-warning-text); }
.ec-dot { font-size: 12px; color: var(--sg-border-input); }

.ec-active-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--sg-color-warning); flex-shrink: 0; }
.ec-active-dot--pre-complete { background: var(--sg-color-info-text); }

.ec-actions { display: flex; justify-content: flex-end; }

.ec-delete-btn {
  background: none; border: none; cursor: pointer;
  padding: 4px 8px; border-radius: 6px;
  display: flex; align-items: center; transition: background 0.15s;
}
.ec-delete-btn:hover { background: var(--sg-color-danger-bg); }

/* ── Create panel ── */
.create-panel {
  background: var(--sg-bg-card); border: 1px solid var(--sg-color-info-bg);
  border-radius: 12px; padding: 18px; display: flex; flex-direction: column; gap: 14px;
  box-shadow: var(--sg-shadow-sm);
}

.create-title { font-size: 14px; font-weight: 700; color: var(--sg-color-info-text); }

.create-field { display: flex; flex-direction: column; gap: 6px; }

.create-label {
  font-size: 11px; font-weight: 600; color: var(--sg-text-muted);
  text-transform: uppercase; letter-spacing: 0.5px;
  display: flex; align-items: center; gap: 8px;
}

.create-count {
  background: var(--sg-accent-tint); color: var(--sg-color-info-text);
  border-radius: 12px; padding: 2px 8px; font-size: 10px; font-weight: 700; text-transform: none;
}

.create-input {
  background: var(--sg-bg-panel); border: 1px solid var(--sg-border);
  border-radius: 10px; color: var(--sg-brand); font-size: 14px; font-family: inherit;
  padding: 10px 12px; outline: none; transition: border-color 0.15s;
}
.create-input:focus { border-color: var(--sg-accent); }

.create-hint { font-size: 12px; color: var(--sg-text-faint); margin: 0; }

.passen-picker { display: flex; flex-direction: column; gap: 6px; }

.picker-item {
  display: flex; align-items: center; gap: 10px;
  padding: 9px 12px; background: var(--sg-bg-panel);
  border: 1px solid var(--sg-border); border-radius: 10px;
  cursor: pointer; transition: all 0.15s;
}
.picker-item.selected { background: var(--sg-accent-subtle); border-color: var(--sg-accent); }

.picker-check {
  width: 22px; height: 22px; border-radius: 50%;
  background: var(--sg-accent-tint);
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; color: var(--sg-color-info-text); flex-shrink: 0;
}

.picker-name { font-size: 13px; color: var(--sg-text-muted); flex: 1; }

.create-actions {
  display: flex; gap: 8px; padding-top: 8px; border-top: 1px solid var(--sg-border);
}

.action-btn {
  flex: 1; padding: 10px; border-radius: 10px;
  font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; border: 1px solid transparent; transition: background 0.15s;
}
.action-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.action-btn--cancel { background: transparent; border-color: var(--sg-border); color: var(--sg-text-muted); }
.action-btn--cancel:hover { background: var(--sg-bg-panel); }
.action-btn--primary { background: var(--sg-accent-tint); border-color: var(--sg-accent); color: var(--sg-color-info-text); }
.action-btn--primary:hover:not(:disabled) { background: color-mix(in srgb, var(--sg-accent) 20%, transparent); }

.new-btn {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 13px; background: transparent;
  border: 1.5px dashed var(--sg-color-info-bg); border-radius: 12px;
  color: var(--sg-color-info-text); font-size: 14px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.new-btn:hover { background: var(--sg-accent-subtle); border-color: var(--sg-accent); }

.empty-state {
  padding: 32px; text-align: center; display: flex; flex-direction: column; gap: 6px;
}
.empty-state p { font-size: 14px; color: var(--sg-text-faint); margin: 0; }
.empty-hint { font-size: 12px !important; color: var(--sg-border-input) !important; }
</style>
