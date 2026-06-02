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
        @click="activeTab = tab.id"
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
                  <span class="ec-meta">{{ ev.passen.length }} Passen</span>
                  <span class="ec-dot">·</span>
                  <span class="ec-meta">{{ ev.rotten.length }} Rotten</span>
                  <span class="ec-dot">·</span>
                  <span class="ec-meta">{{ totalPlayers(ev) }} Schützen</span>
                  <template v-if="unpaidCount(ev) > 0">
                    <span class="ec-dot">·</span>
                    <span class="ec-meta ec-meta--warn">{{ unpaidCount(ev) }} offen</span>
                  </template>
                </div>
              </div>
              <Icons icon="chevronRight" :size="14" color="#cbd5e0" />
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
          <Icons icon="plus" :size="15" color="rgba(79,195,247,0.8)" />
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
            class="event-card event-card--active"
            @click="router.push('/admin/wettkampf/' + ev.id)"
          >
            <div class="ec-main">
              <div class="ec-info">
                <span class="ec-name">{{ ev.name }}</span>
                <div class="ec-meta-row">
                  <span class="ec-meta">{{ ev.rotten.length }} Rotten</span>
                  <span class="ec-dot">·</span>
                  <span class="ec-meta">{{ totalPlayers(ev) }} Schützen</span>
                </div>
              </div>
              <div class="ec-active-dot" />
              <Icons icon="chevronRight" :size="14" color="#cbd5e0" />
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
              <Icons icon="chevronRight" :size="14" color="#cbd5e0" />
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
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { usePasseStore } from '@/stores/passeStore.js'
import Icons from '@/components/Icons.vue'

const router = useRouter()
const store = useCompetitionEventStore()
const passeStore = usePasseStore()

const activeTab = ref('planning')

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

const confirmCreate = () => {
  if (!newName.value.trim() || selectedPassen.value.length === 0) return
  const id = store.createEvent(newName.value.trim(), selectedPassen.value)
  cancelCreate()
  router.push('/admin/wettkampf/' + id)
}

// ── Delete ─────────────────────────────────────────────────────────────────
const handleDelete = (ev) => {
  if (confirm(`"${ev.name}" löschen?`)) {
    store.deleteEvent(ev.id)
  }
}

// ── Helpers ────────────────────────────────────────────────────────────────
const totalPlayers = (ev) => ev.rotten.reduce((s, r) => s + r.players.length, 0)

const unpaidCount = (ev) => ev.rotten.reduce((s, r) => s + r.players.filter(p => !p.paid).length, 0)
</script>

<style scoped>
.list-view { display: flex; flex-direction: column; height: 100%; }

.view-header {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: 24px 28px 20px; border-bottom: 1px solid #e2e8f0; flex-shrink: 0;
}

.view-title { font-size: 22px; font-weight: 700; color: #1a1a2e; margin: 0 0 4px; }

.view-subtitle { font-size: 13px; color: #718096; margin: 0; }

.tab-bar {
  display: flex; gap: 0; padding: 0 28px;
  border-bottom: 1px solid #e2e8f0; flex-shrink: 0;
}

.tab-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 12px 18px; background: transparent; border: none;
  border-bottom: 2px solid transparent; margin-bottom: -1px;
  color: #a0aec0; font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.tab-btn.active { color: #1a1a2e; border-bottom-color: #4fc3f7; }
.tab-btn:hover:not(.active) { color: #4a5568; }

.tab-count {
  font-size: 10px; font-weight: 700;
  background: rgba(79,195,247,0.12); color: #0288d1;
  border-radius: 10px; padding: 1px 6px;
}

.content { flex: 1; overflow-y: auto; padding: 20px 28px 40px; display: flex; flex-direction: column; gap: 10px; }

.event-list { display: flex; flex-direction: column; gap: 8px; }

.event-card {
  background: #fff; border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.07); padding: 14px 16px; cursor: pointer;
  transition: box-shadow 0.15s; display: flex; flex-direction: column; gap: 8px;
}
.event-card:hover { box-shadow: 0 3px 8px rgba(0,0,0,0.1); }
.event-card--active { border-left: 3px solid #f6ad55; }

.ec-main { display: flex; align-items: center; gap: 10px; }

.ec-info { flex: 1; display: flex; flex-direction: column; gap: 4px; }

.ec-name { font-size: 15px; font-weight: 600; color: #1a1a2e; }

.ec-meta-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }

.ec-meta { font-size: 12px; color: #a0aec0; }
.ec-meta--warn { color: #dd6b20; }
.ec-dot { font-size: 12px; color: #cbd5e0; }

.ec-active-dot { width: 8px; height: 8px; border-radius: 50%; background: #f6ad55; }

.ec-actions { display: flex; justify-content: flex-end; }

.ec-delete-btn {
  background: none; border: none; cursor: pointer;
  padding: 4px 8px; border-radius: 6px;
  display: flex; align-items: center; transition: background 0.15s;
}
.ec-delete-btn:hover { background: #fff5f5; }

/* ── Create panel ── */
.create-panel {
  background: #fff; border: 1px solid #bee3f8;
  border-radius: 12px; padding: 18px; display: flex; flex-direction: column; gap: 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.create-title { font-size: 14px; font-weight: 700; color: #0288d1; }

.create-field { display: flex; flex-direction: column; gap: 6px; }

.create-label {
  font-size: 11px; font-weight: 600; color: #718096;
  text-transform: uppercase; letter-spacing: 0.5px;
  display: flex; align-items: center; gap: 8px;
}

.create-count {
  background: rgba(79,195,247,0.12); color: #0288d1;
  border-radius: 12px; padding: 2px 8px; font-size: 10px; font-weight: 700; text-transform: none;
}

.create-input {
  background: #f7fafc; border: 1px solid #e2e8f0;
  border-radius: 10px; color: #1a1a2e; font-size: 14px; font-family: inherit;
  padding: 10px 12px; outline: none; transition: border-color 0.15s;
}
.create-input:focus { border-color: #4fc3f7; }

.create-hint { font-size: 12px; color: #a0aec0; margin: 0; }

.passen-picker { display: flex; flex-direction: column; gap: 6px; }

.picker-item {
  display: flex; align-items: center; gap: 10px;
  padding: 9px 12px; background: #f7fafc;
  border: 1px solid #e2e8f0; border-radius: 10px;
  cursor: pointer; transition: all 0.15s;
}
.picker-item.selected { background: rgba(79,195,247,0.08); border-color: #4fc3f7; }

.picker-check {
  width: 22px; height: 22px; border-radius: 50%;
  background: rgba(79,195,247,0.12);
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; color: #0288d1; flex-shrink: 0;
}

.picker-name { font-size: 13px; color: #2d3748; flex: 1; }

.create-actions {
  display: flex; gap: 8px; padding-top: 8px; border-top: 1px solid #e2e8f0;
}

.action-btn {
  flex: 1; padding: 10px; border-radius: 10px;
  font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; border: 1px solid transparent; transition: background 0.15s;
}
.action-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.action-btn--cancel { background: transparent; border-color: #e2e8f0; color: #718096; }
.action-btn--cancel:hover { background: #f7fafc; }
.action-btn--primary { background: rgba(79,195,247,0.12); border-color: #4fc3f7; color: #0288d1; }
.action-btn--primary:hover:not(:disabled) { background: rgba(79,195,247,0.2); }

.new-btn {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 13px; background: transparent;
  border: 1.5px dashed #bee3f8; border-radius: 12px;
  color: #0288d1; font-size: 14px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.new-btn:hover { background: rgba(79,195,247,0.06); border-color: #4fc3f7; }

.empty-state {
  padding: 32px; text-align: center; display: flex; flex-direction: column; gap: 6px;
}
.empty-state p { font-size: 14px; color: #a0aec0; margin: 0; }
.empty-hint { font-size: 12px !important; color: #cbd5e0 !important; }
</style>
