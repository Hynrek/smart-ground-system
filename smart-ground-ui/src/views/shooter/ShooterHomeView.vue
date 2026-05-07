<template>
  <div class="shooter-home">
    <!-- Top bar -->
    <div class="top-bar">
      <div class="user-pill">
        <div class="user-avatar">{{ userInitial }}</div>
        <span class="user-name">{{ displayName }}</span>
      </div>
      <button class="logout-btn" aria-label="Abmelden" @click="handleLogout">
        <Icons icon="logout" :size="18" color="rgba(255,255,255,0.45)" />
      </button>
    </div>

    <!-- Greeting -->
    <div class="greeting">
      <h1 class="greeting-title">Hallo, {{ firstName }}</h1>
      <p class="greeting-sub">Was möchtest du tun?</p>
    </div>

    <!-- App grid -->
    <div class="app-grid">
      <button class="app-tile app-tile--available" @click="router.push('/remote')">
        <div class="tile-icon-wrap tile-icon-wrap--cyan">
          <Icons icon="program" :size="36" color="#4fc3f7" />
        </div>
        <span class="tile-label">Remote</span>
        <span class="tile-desc">Geräte steuern</span>
      </button>

      <div class="app-tile app-tile--soon">
        <div class="tile-icon-wrap tile-icon-wrap--muted">
          <Icons icon="target" :size="36" color="rgba(255,255,255,0.18)" />
        </div>
        <span class="tile-label">Schiessprogramm</span>
        <span class="tile-coming">Bald verfügbar</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/authStore.js';
import Icons from '@/components/Icons.vue';

const router = useRouter();
const authStore = useAuthStore();

const displayName = computed(() => authStore.userName ?? 'Schütze');
const firstName = computed(() => displayName.value.split(/[\s._@]/)[0]);
const userInitial = computed(() => firstName.value.charAt(0).toUpperCase());

const handleLogout = () => {
  authStore.logout();
  router.push('/login');
};
</script>

<style scoped>
.shooter-home {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: env(safe-area-inset-top, 0) 0 env(safe-area-inset-bottom, 0);
}

/* ── Top bar ─────────────────────────────────────── */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px 0;
}

.user-pill {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(79, 195, 247, 0.2);
  border: 1px solid rgba(79, 195, 247, 0.35);
  color: #4fc3f7;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-name {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
  font-weight: 500;
}

.logout-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 6px;
  display: flex;
  align-items: center;
  border-radius: 8px;
  transition: background 0.15s;
}

.logout-btn:hover {
  background: rgba(255, 255, 255, 0.07);
}

/* ── Greeting ────────────────────────────────────── */
.greeting {
  padding: 40px 24px 32px;
}

.greeting-title {
  font-size: 32px;
  font-weight: 700;
  color: #ffffff;
  margin: 0 0 6px;
  letter-spacing: -0.5px;
}

.greeting-sub {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.45);
  margin: 0;
}

/* ── App grid ────────────────────────────────────── */
.app-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
  padding: 0 20px;
}

/* ── App tile ────────────────────────────────────── */
.app-tile {
  border-radius: 20px;
  padding: 24px 20px 20px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  transition: transform 0.12s, background 0.15s;
  text-align: left;
  font-family: inherit;
}

.app-tile--available {
  background: rgba(255, 255, 255, 0.07);
  cursor: pointer;
}

.app-tile--available:hover {
  background: rgba(255, 255, 255, 0.11);
}

.app-tile--available:active {
  transform: scale(0.96);
  background: rgba(255, 255, 255, 0.09);
}

.app-tile--soon {
  background: rgba(255, 255, 255, 0.03);
  border-color: rgba(255, 255, 255, 0.05);
  cursor: default;
}

/* ── Tile icon ───────────────────────────────────── */
.tile-icon-wrap {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tile-icon-wrap--cyan {
  background: rgba(79, 195, 247, 0.15);
}

.tile-icon-wrap--muted {
  background: rgba(255, 255, 255, 0.05);
}

/* ── Tile text ───────────────────────────────────── */
.tile-label {
  font-size: 15px;
  font-weight: 600;
  color: #ffffff;
  line-height: 1;
}

.app-tile--soon .tile-label {
  color: rgba(255, 255, 255, 0.3);
}

.tile-desc {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
  line-height: 1.3;
}

.tile-coming {
  font-size: 11px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.06);
  border-radius: 20px;
  padding: 3px 8px;
}

/* ── Tablet+ ─────────────────────────────────────── */
@media (min-width: 480px) {
  .app-grid {
    grid-template-columns: repeat(3, 1fr);
    max-width: 600px;
  }

  .greeting-title {
    font-size: 36px;
  }
}

@media (min-width: 768px) {
  .greeting {
    padding: 56px 32px 40px;
  }

  .app-grid {
    padding: 0 32px;
    gap: 16px;
  }
}
</style>
