<template>
  <div class="range-panel-card">
    <!-- Header -->
    <div class="card-header">
      <Icons icon="ranges" :size="14" color="#4fc3f7" />
      <span class="range-name">{{ range.name }}</span>
    </div>

    <hr class="divider" />

    <!-- No competition active -->
    <template v-if="!inst">
      <div class="empty-state">Kein Wettkampf aktiv</div>
    </template>

    <!-- Competition active -->
    <template v-else>
      <!-- ── AKTIV section ── -->
      <div class="section-label">AKTIV</div>

      <template v-if="activeRotte">
        <RotteProgressRow :rotte="activeRotte" />

        <!-- Players -->
        <div class="players-row">
          {{ activeRotte.players.map(p => p.displayName ?? p.name).join(' · ') }}
        </div>

        <!-- Actions -->
        <div class="action-row">
          <button class="btn btn--ghost" @click="handlePause">
            <Icons icon="stop" :size="13" color="rgba(255,255,255,0.7)" />
            Pause
          </button>
          <button class="btn btn--primary" @click="handleVerwalten">
            <Icons icon="arrowR" :size="13" color="#1a1a2e" />
            Verwalten
          </button>
        </div>
      </template>

      <template v-else>
        <!-- Highlighted CTA when queue is non-empty -->
        <template v-if="waitingRotten.length > 0">
          <div class="cta-section">
            <span class="cta-label">Nächste Rotte</span>
            <RotteProgressRow :rotte="waitingRotten[0]" :compact="true" />
            <button class="btn btn--cta" @click="handleActivate(waitingRotten[0])">
              <Icons icon="bolt" :size="13" color="#1a1a2e" />
              Rotte starten
            </button>
          </div>
        </template>
        <template v-else>
          <div class="empty-state">Kein aktiver Schütze</div>
        </template>
      </template>

      <!-- ── WARTESCHLANGE section ── -->
      <div class="section-label section-label--queue">WARTESCHLANGE</div>

      <template v-if="waitingRotten.length > 0">
        <div
          v-for="rotte in waitingRotten"
          :key="rotte.rotteId"
          class="queue-row"
        >
          <RotteProgressRow :rotte="rotte" :compact="true" />
          <!-- Show activate only if none is active on this range -->
          <button
            v-if="!activeRotte"
            class="btn btn--ghost btn--sm"
            @click="handleActivate(rotte)"
          >
            Aktivieren
          </button>
        </div>
      </template>

      <template v-else>
        <div class="empty-state empty-state--sm">Keine wartenden Rotten</div>
      </template>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import Icons from '@/components/Icons.vue'
import RotteProgressRow from '@/components/competition/RotteProgressRow.vue'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'

const props = defineProps({
  range: {
    type: Object,
    required: true,
  },
  instanceId: {
    type: String,
    default: null,
  },
})

const emit = defineEmits(['go-to-range'])

const competitionEventStore = useCompetitionEventStore()

const inst = computed(() =>
  props.instanceId ? competitionEventStore.getCompetitionInstance(props.instanceId) : null,
)

const activeRotte = computed(() =>
  inst.value?.rotten.find(
    (r) => r.assignedRangeId === props.range.id && r.status === 'active',
  ) ?? null,
)

const waitingRotten = computed(() =>
  inst.value?.rotten.filter((r) => r.status === 'waiting' || r.status === 'paused') ?? [],
)

function handlePause() {
  if (!activeRotte.value) return
  competitionEventStore.unassignRotte(props.instanceId, activeRotte.value.rotteId)
}

function handleVerwalten() {
  if (!activeRotte.value) return
  emit('go-to-range', { rangeId: props.range.id, rotteId: activeRotte.value.rotteId })
}

function handleActivate(rotte) {
  competitionEventStore.assignRotteToRange(props.instanceId, rotte.rotteId, props.range.id)
}
</script>

<style scoped>
.range-panel-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

/* ── Header ── */
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.range-name {
  font-size: 15px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
}

/* ── Divider ── */
.divider {
  border: none;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  margin: 0;
}

/* ── Section labels ── */
.section-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: rgba(255, 255, 255, 0.3);
  text-transform: uppercase;
  margin-top: 2px;
}

.section-label--queue {
  margin-top: 4px;
}

/* ── Players row ── */
.players-row {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  padding: 0 2px;
}

/* ── Action row ── */
.action-row {
  display: flex;
  gap: 8px;
}

/* ── Queue row ── */
.queue-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ── CTA section ── */
.cta-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: rgba(79, 195, 247, 0.06);
  border: 1px solid rgba(79, 195, 247, 0.18);
  border-radius: 10px;
  padding: 10px 12px;
}

.cta-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #4fc3f7;
  text-transform: uppercase;
}

/* ── Buttons ── */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  border: none;
  cursor: pointer;
  transition: opacity 0.15s;
  white-space: nowrap;
}

.btn:active {
  opacity: 0.75;
}

.btn--ghost {
  background: rgba(255, 255, 255, 0.07);
  color: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.btn--ghost:hover {
  background: rgba(255, 255, 255, 0.11);
}

.btn--primary {
  background: #4fc3f7;
  color: #1a1a2e;
}

.btn--primary:hover {
  background: #81d4fa;
}

.btn--cta {
  background: #4fc3f7;
  color: #1a1a2e;
  width: 100%;
  justify-content: center;
  padding: 9px 14px;
  font-size: 14px;
}

.btn--cta:hover {
  background: #81d4fa;
}

.btn--sm {
  padding: 5px 10px;
  font-size: 12px;
  flex-shrink: 0;
}

/* ── Empty states ── */
.empty-state {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.3);
  padding: 4px 2px;
}

.empty-state--sm {
  font-size: 12px;
  padding: 2px 2px;
}
</style>
