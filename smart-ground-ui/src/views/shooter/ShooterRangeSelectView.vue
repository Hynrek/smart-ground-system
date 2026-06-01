<template>
  <div class="range-select">
    <!-- Header -->
    <div class="page-header">
      <button class="back-btn" @click="router.push('/home')">
        <Icons icon="chevronLeft" :size="20" color="rgba(255,255,255,0.8)" />
      </button>
      <div class="header-text">
        <h1 class="page-title">Platz wählen</h1>
        <p class="page-sub">Welcher Platz bist du heute?</p>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="rangeStore.isLoading" class="state-center">
      <p class="state-text">Lade Plätze…</p>
    </div>

    <!-- Empty -->
    <div v-else-if="ranges.length === 0" class="state-center">
      <Icons icon="ranges" :size="44" color="rgba(255,255,255,0.12)" />
      <p class="state-text">Keine Plätze vorhanden</p>
      <p class="state-hint">Bitte einen Administrator kontaktieren.</p>
    </div>

    <!-- Range list -->
    <div v-else class="range-list">
      <button
        v-for="range in ranges"
        :key="range.id"
        class="range-card"
        :class="{
          'range-card--locked': range.locked,
          'range-card--reserved': isReservedByOther(range),
        }"
        :disabled="range.locked || isReservedByOther(range)"
        @click="selectRange(range)"
      >
        <div
          class="card-accent"
          :class="
            range.locked
              ? 'card-accent--red'
              : isReservedByOther(range)
                ? 'card-accent--blue'
                : 'card-accent--green'
          "
        />

        <div class="card-body">
          <div class="card-main">
            <div class="card-name-row">
              <span class="card-name">{{ range.name }}</span>
              <span v-if="range.locked" class="locked-chip">
                <Icons icon="lock" :size="10" color="#fc8181" />
                Gesperrt
              </span>
              <span v-else-if="isReservedByOther(range)" class="reserved-chip">
                Reserviert
              </span>
            </div>
            <p v-if="range.description" class="card-desc">{{ range.description }}</p>
            <span class="device-count">
              <Icons icon="bolt" :size="11" color="rgba(255,255,255,0.35)" />
              {{ getDeviceCount(range.id) }} Geräte
            </span>
          </div>
          <Icons icon="chevronRight" :size="16" color="rgba(255,255,255,0.2)" />
        </div>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useRangeStore } from '@/stores/rangeStore.js';
import { useDeviceStore } from '@/stores/deviceStore.js';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import { useAuthStore } from '@/stores/authStore.js';
import { useDeviceLoader } from '@/composables/useDeviceLoader.js';
import Icons from '@/components/Icons.vue';

const router = useRouter();
const rangeStore = useRangeStore();
const deviceStore = useDeviceStore();
const shooterStore = useShooterRemoteStore();
const auth = useAuthStore();

useDeviceLoader();

const ranges = computed(() => rangeStore.ranges);
const getDeviceCount = (rangeId) =>
  deviceStore.devices.filter((d) => d.rangeId === rangeId).length;

const isReservedByOther = (range) =>
  !!range.assignedUserId && range.assignedUserId !== auth.profile?.id;

const selectRange = (range) => {
  if (range.locked) return;
  shooterStore.reservePlatz(range.id);
  router.push(`/remote/${range.id}`);
};
</script>

<style scoped>
.range-select {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: env(safe-area-inset-top, 0) 0 env(safe-area-inset-bottom, 24px);
}

/* ── Header ──────────────────────────────────────── */
.page-header {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 16px 20px 24px;
}

.back-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 8px;
  margin-left: -8px;
  display: flex;
  align-items: center;
  border-radius: 10px;
  flex-shrink: 0;
  transition: background 0.15s;
}

.back-btn:hover {
  background: rgba(255, 255, 255, 0.07);
}

.header-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #ffffff;
  margin: 0;
  letter-spacing: -0.3px;
}

.page-sub {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.4);
  margin: 0;
}

/* ── States ──────────────────────────────────────── */
.state-center {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 24px;
}

.state-text {
  font-size: 16px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.35);
  margin: 0;
}

.state-hint {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.2);
  margin: 0;
  text-align: center;
}

/* ── Range list ──────────────────────────────────── */
.range-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 16px;
}

/* ── Range card ──────────────────────────────────── */
.range-card {
  display: flex;
  align-items: stretch;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  transition: background 0.15s, transform 0.1s;
  width: 100%;
}

.range-card:hover {
  background: rgba(255, 255, 255, 0.09);
}

.range-card:active {
  transform: scale(0.98);
  background: rgba(255, 255, 255, 0.07);
}

.range-card--locked {
  opacity: 0.5;
  cursor: not-allowed;
  pointer-events: none;
}

.range-card--reserved {
  opacity: 0.6;
  cursor: not-allowed;
  pointer-events: none;
}

.card-accent {
  width: 4px;
  flex-shrink: 0;
}

.card-accent--green {
  background: #48bb78;
}

.card-accent--red {
  background: #fc8181;
}

.card-accent--blue {
  background: #63b3ed;
}

.card-body {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 16px 14px;
  gap: 12px;
}

.card-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.card-name {
  font-size: 16px;
  font-weight: 600;
  color: #ffffff;
}

.locked-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(252, 129, 129, 0.15);
  color: #fc8181;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 20px;
}

.reserved-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(99, 179, 237, 0.15);
  color: #63b3ed;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 20px;
}

.card-desc {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.4);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.3);
}

/* ── Tablet+ ─────────────────────────────────────── */
@media (min-width: 600px) {
  .range-list {
    max-width: 560px;
    margin: 0 auto;
    width: 100%;
    padding: 0 24px;
  }
}
</style>
