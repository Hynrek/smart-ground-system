<!-- src/views/shooter/CompetitionManagementView.vue -->
<template>
  <div class="competition-view">
    <div class="top-bar">
      <button class="back-btn" @click="router.push('/home')">
        <Icons icon="chevronLeft" :size="18" color="rgba(255,255,255,0.7)" />
      </button>
      <h1 class="page-title">Wettkampf</h1>
      <div class="top-bar-spacer" />
    </div>

    <div class="content">
      <template v-if="activeEvents.length > 0">
        <div
          v-for="ev in activeEvents"
          :key="ev.id"
          class="session-card"
        >
          <div class="session-header">
            <span class="session-name">{{ ev.name }}</span>
            <span class="session-meta">{{ ev.rotten.length }} Rotten</span>
          </div>

          <div class="rotte-list">
            <div
              v-for="rotte in getRotten(ev)"
              :key="rotte.rotteId"
              class="rotte-row"
            >
              <span class="rotte-status-dot" :class="`dot-${rotte.status ?? 'waiting'}`" />
              <span class="rotte-name">{{ rotte.name }}</span>
              <span class="rotte-passe">{{ currentPasse(rotte) }}</span>
              <span class="rotte-badge" :class="`badge-${rotte.status ?? 'waiting'}`">
                {{ badgeLabel(rotte.status) }}
              </span>
            </div>
          </div>

          <div class="player-summary">
            <span
              v-for="player in allPlayers(ev)"
              :key="player.id"
              class="player-chip"
            >
              {{ player.displayName || '–' }}
            </span>
          </div>
        </div>
      </template>

      <div v-else class="empty-block">
        <Icons icon="program" :size="36" color="rgba(255,255,255,0.07)" />
        <p>Kein aktiver Wettkampf</p>
        <p class="empty-hint">Der Admin startet den Wettkampf.</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { useActivePasseStore } from '@/stores/activePasseStore.js'
import Icons from '@/components/Icons.vue'

const router = useRouter()
const competitionEventStore = useCompetitionEventStore()
const activePasseStore = useActivePasseStore()

const activeEvents = computed(() => competitionEventStore.activeEvents)

const getRotten = (ev) => {
  const instance = activePasseStore.activeInstances.find(i => i.instanceId === ev.activeInstanceId)
  return instance?.rotten ?? ev.rotten
}

const currentPasse = (rotte) => {
  const phase = rotte.phases?.[rotte.currentPhaseIndex]
  return phase?.passeName ?? '–'
}

const badgeLabel = (status) => {
  const map = { active: 'Aktiv', waiting: 'Wartend', done: 'Fertig', paused: 'Pausiert' }
  return map[status] ?? 'Wartend'
}

const allPlayers = (ev) => ev.rotten.flatMap(r => r.players)
</script>

<style scoped>
.competition-view {
  flex: 1; display: flex; flex-direction: column; min-height: 0;
  background: var(--sg-brand); color: #fff;
}

.top-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 14px 16px; border-bottom: 1px solid rgba(255,255,255,0.06); flex-shrink: 0;
}

.back-btn {
  background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.1);
  border-radius: 10px; width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; transition: background 0.15s;
}
.back-btn:hover { background: rgba(255,255,255,0.1); }

.page-title { font-size: 17px; font-weight: 700; margin: 0; }
.top-bar-spacer { flex: 1; }

.content { flex: 1; overflow-y: auto; padding: 16px 16px 40px; display: flex; flex-direction: column; gap: 12px; }

.session-card {
  background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08);
  border-radius: 16px; padding: 14px 16px; display: flex; flex-direction: column; gap: 10px;
}

.session-header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }

.session-name { font-size: 15px; font-weight: 600; }

.session-meta { font-size: 12px; color: rgba(255,255,255,0.3); }

.rotte-list { display: flex; flex-direction: column; gap: 6px; }

.rotte-row {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06); border-radius: 10px; font-size: 12px;
}

.rotte-status-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.rotte-status-dot.dot-waiting { background: rgba(255,255,255,0.2); }
.rotte-status-dot.dot-active { background: rgba(246,173,85,0.8); }
.rotte-status-dot.dot-done { background: rgba(72,187,120,0.7); }
.rotte-status-dot.dot-paused { background: rgba(255,255,255,0.25); }

.rotte-name { font-weight: 600; color: rgba(255,255,255,0.8); min-width: 52px; }

.rotte-passe { flex: 1; color: rgba(79,195,247,0.7); font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.rotte-badge {
  font-size: 10px; font-weight: 700; border-radius: 6px; padding: 2px 7px; flex-shrink: 0;
}
.rotte-badge.badge-active { background: rgba(246,173,85,0.15); color: rgba(246,173,85,0.9); }
.rotte-badge.badge-waiting { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.4); }
.rotte-badge.badge-done { background: rgba(72,187,120,0.12); color: rgba(72,187,120,0.8); }
.rotte-badge.badge-paused { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.3); }

.player-summary { display: flex; flex-wrap: wrap; gap: 6px; }

.player-chip {
  font-size: 11px; padding: 3px 10px;
  background: rgba(255,255,255,0.05); border-radius: 20px; color: rgba(255,255,255,0.5);
}

.empty-block {
  display: flex; flex-direction: column; align-items: center;
  gap: 8px; padding: 40px 16px; text-align: center;
}
.empty-block p { font-size: 14px; color: rgba(255,255,255,0.2); margin: 0; }
.empty-hint { font-size: 12px !important; color: rgba(255,255,255,0.12) !important; line-height: 1.5; }
</style>
