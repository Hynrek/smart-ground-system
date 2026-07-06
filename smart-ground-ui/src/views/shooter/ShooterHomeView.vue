<template>
  <div class="shooter-home">
    <!-- Top bar -->
    <div class="top-bar">
      <div class="user-pill">
        <div class="user-avatar">{{ userInitial }}</div>
        <span class="user-name">{{ displayName }}</span>
        <button class="account-edit-btn" data-testid="open-account" @click="router.push('/profil')">Konto</button>
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
      <button class="app-tile app-tile--available app-tile--cyan" @click="router.push('/remote')">
        <div class="tile-icon-wrap">
          <Icons icon="target" :size="36" color="#1a1a2e" />
        </div>
        <span class="tile-label">Schiessplätze</span>
        <span class="tile-desc">Plätze & Geräte</span>
      </button>

      <button class="app-tile app-tile--available app-tile--orange" @click="router.push('/meine-passen')">
        <div class="tile-icon-wrap">
          <Icons icon="program" :size="36" color="#1a1a2e" />
        </div>
        <span class="tile-label">Trainings</span>
        <span class="tile-desc">Serien & Passen vorbereiten</span>
      </button>

      <button class="app-tile app-tile--available app-tile--purple" data-testid="open-profile" @click="router.push('/profil')">
        <div class="tile-icon-wrap">
          <Icons icon="user" :size="36" color="#1a1a2e" />
        </div>
        <span class="tile-label">Mein Profil</span>
        <span class="tile-desc">Konto & Daten</span>
      </button>

      <button class="app-tile app-tile--available app-tile--cyan" data-testid="open-bestenliste" @click="router.push('/bestenliste')">
        <div class="tile-icon-wrap">
          <Icons icon="stats" :size="36" color="#1a1a2e" />
        </div>
        <span class="tile-label">Bestenliste</span>
        <span class="tile-desc">Ranglisten & Rekorde</span>
      </button>

      <button v-if="canManage" class="app-tile app-tile--available app-tile--green" data-testid="open-admin" @click="router.push('/ranges')">
        <div class="tile-icon-wrap">
          <Icons icon="templates" :size="36" color="#1a1a2e" />
        </div>
        <span class="tile-label">Verwaltung</span>
        <span class="tile-desc">Schiessplätze verwalten</span>
      </button>

      <button class="app-tile app-tile--soon" disabled>
        <div class="tile-icon-wrap tile-icon-wrap--muted">
          <Icons icon="award" :size="36" color="rgba(255,255,255,0.2)" />
        </div>
        <span class="tile-label">Wettkämpfe</span>
        <span class="tile-desc">Teilnehmen & Ergebnisse</span>
        <span class="tile-coming">Bald verfügbar</span>
      </button>

      <button class="app-tile app-tile--soon" disabled>
        <div class="tile-icon-wrap tile-icon-wrap--muted">
          <Icons icon="stats" :size="36" color="rgba(255,255,255,0.2)" />
        </div>
        <span class="tile-label">Karriere</span>
        <span class="tile-desc">Meine Statistiken</span>
        <span class="tile-coming">Bald verfügbar</span>
      </button>
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

const displayName = computed(() => authStore.displayName ?? 'Schütze');
const firstName = computed(() => displayName.value.split(/[\s._@]/)[0]);
const userInitial = computed(() => firstName.value.charAt(0).toUpperCase());

// Admin entry to the management area is permission-gated, mirroring the router guard on /ranges
const canManage = computed(() => authStore.hasPermission('MANAGE_RANGES'));

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
  background: color-mix(in srgb, var(--sg-accent) 20%, transparent);
  border: 1px solid color-mix(in srgb, var(--sg-accent) 35%, transparent);
  color: var(--sg-accent);
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-name {
  font-size: 13px;
  color: var(--sg-text-faint);
  font-weight: 500;
}

.account-edit-btn {
  background: none;
  border: 1px solid var(--sg-border-input);
  border-radius: 6px;
  color: var(--sg-text-faint);
  cursor: pointer;
  font-size: 11px;
  font-family: inherit;
  padding: 3px 8px;
  transition: background 0.15s, color 0.15s;
}

.account-edit-btn:hover {
  background: rgba(255, 255, 255, 0.09);
  color: rgba(255, 255, 255, 0.8);
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
  color: var(--sg-text-primary);
  margin: 0 0 6px;
  letter-spacing: -0.5px;
}

.greeting-sub {
  font-size: 15px;
  color: var(--sg-text-faint);
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
  border: 1px solid var(--sg-border);
  transition: transform 0.12s, background 0.15s;
  text-align: left;
  font-family: inherit;
}

/* Per-category hue — carried on the tile so border, tint and chip all share it */
.app-tile--cyan   { --tile: var(--sg-app-cyan,   #4fc3f7); }
.app-tile--orange { --tile: var(--sg-app-orange, #F6AD55); }
.app-tile--purple { --tile: var(--sg-app-purple, #C084FC); }
.app-tile--green  { --tile: var(--sg-app-green,  #48BB78); }

.app-tile--available {
  cursor: pointer;
  border-color: color-mix(in srgb, var(--tile) 35%, transparent);
  background:
    linear-gradient(150deg,
      color-mix(in srgb, var(--tile) 15%, transparent) 0%,
      color-mix(in srgb, var(--tile) 6%,  transparent) 42%,
      rgba(255, 255, 255, 0.05) 100%);
}

.app-tile--available:hover {
  border-color: color-mix(in srgb, var(--tile) 50%, transparent);
  background:
    linear-gradient(150deg,
      color-mix(in srgb, var(--tile) 22%, transparent) 0%,
      color-mix(in srgb, var(--tile) 9%,  transparent) 42%,
      rgba(255, 255, 255, 0.07) 100%);
}

.app-tile--available:active {
  transform: scale(0.96);
}

/* Solid icon chip — the daylight-contrast win. Glyph is dark (#1a1a2e),
   set via the Icons `color` prop in the template. */
.app-tile--available .tile-icon-wrap {
  background: var(--tile);
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

.tile-icon-wrap--muted {
  background: rgba(255, 255, 255, 0.05);
}

/* ── Tile text ───────────────────────────────────── */
.tile-label {
  font-size: 15px;
  font-weight: 600;
  color: var(--sg-text-primary);
  line-height: 1;
}

.app-tile--soon .tile-label {
  color: var(--sg-text-disabled);
}

.tile-desc {
  font-size: 12px;
  color: var(--sg-text-faint);
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
