<template>
  <div class="rotte-progress-row" :class="{ compact }">
    <!-- Status dot -->
    <span class="status-dot" :class="`status-dot--${rotte.status}`" aria-hidden="true" />

    <!-- Name -->
    <span class="rotte-name">{{ rotte.name }}</span>

    <template v-if="!compact">
      <!-- Divider -->
      <span class="divider">|</span>

      <!-- Current passe name -->
      <span class="passe-name">{{ currentPhaseName }}</span>

      <!-- Divider -->
      <span class="divider">|</span>

      <!-- Serie progress dots -->
      <span class="progress-dots" aria-label="Fortschritt">
        <span
          v-for="block in currentPhaseBlocks"
          :key="block.blockId"
          class="dot"
          :class="`dot--${block.status}`"
          :title="block.serieAlias"
        />
      </span>
    </template>

    <!-- Status badge -->
    <span class="status-badge" :class="`badge--${rotte.status}`">{{ statusLabel }}</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  rotte: {
    type: Object,
    required: true,
  },
  compact: {
    type: Boolean,
    default: false,
  },
})

const currentPhase = computed(() =>
  props.rotte.phases?.[props.rotte.currentPhaseIndex] ?? null,
)

const currentPhaseName = computed(() => currentPhase.value?.passeName ?? '—')

const currentPhaseBlocks = computed(() => currentPhase.value?.blocks ?? [])

const statusLabel = computed(() => {
  switch (props.rotte.status) {
    case 'active':
      return 'aktiv'
    case 'waiting':
      return 'wartend'
    case 'paused':
      return 'pausiert'
    case 'done':
      return 'fertig'
    default:
      return props.rotte.status
  }
})
</script>

<style scoped>
.rotte-progress-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.04);
  width: 100%;
  box-sizing: border-box;
  min-height: 36px;
}

/* ── Status dot ── */
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot--active { background: #48bb78; box-shadow: 0 0 6px #48bb7880; }
.status-dot--waiting { background: rgba(255, 255, 255, 0.3); }
.status-dot--paused { background: #f6ad55; }
.status-dot--done { background: #48bb7840; }

/* ── Name ── */
.rotte-name {
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  white-space: nowrap;
}

/* ── Divider ── */
.divider {
  color: rgba(255, 255, 255, 0.2);
  font-size: 12px;
  flex-shrink: 0;
}

/* ── Passe name ── */
.passe-name {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
  white-space: nowrap;
}

/* ── Progress dots ── */
.progress-dots {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  transition: background 0.2s;
}

.dot--done { background: #48bb78; }
.dot--in_progress { background: #f6ad55; }
.dot--pending { background: rgba(255, 255, 255, 0.18); }

/* ── Status badge ── */
.status-badge {
  margin-left: auto;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  padding: 2px 7px;
  border-radius: 4px;
  white-space: nowrap;
}

.badge--active {
  color: #48bb78;
  background: rgba(72, 187, 120, 0.12);
}

.badge--waiting {
  color: rgba(255, 255, 255, 0.4);
  background: rgba(255, 255, 255, 0.06);
}

.badge--paused {
  color: #f6ad55;
  background: rgba(246, 173, 85, 0.12);
}

.badge--done {
  color: rgba(72, 187, 120, 0.5);
  background: rgba(72, 187, 120, 0.06);
}

/* ── Compact mode ── */
.rotte-progress-row.compact {
  padding: 4px 8px;
  min-height: 28px;
  gap: 6px;
}

.rotte-progress-row.compact .rotte-name {
  font-size: 12px;
}
</style>
