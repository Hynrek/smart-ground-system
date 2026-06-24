<template>
  <div class="passen-admin">
    <!-- Header -->
    <div class="view-header">
      <h1 class="view-title">Passen & Serien</h1>
      <p class="view-subtitle">Platz-weite Serien und Passen verwalten</p>
    </div>

    <!-- Tabs -->
    <div class="tab-row">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'serien' }"
        @click="setTab('serien')"
      >
        Serien
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'passen' }"
        @click="setTab('passen')"
      >
        Passen
      </button>
    </div>

    <!-- ══════════════════════════════════════════ -->
    <!-- SERIEN TAB                                     -->
    <!-- ══════════════════════════════════════════ -->
    <template v-if="activeTab === 'serien'">
      <!-- Section header -->
      <div class="section-header">
        <div class="section-title-row">
          <h2 class="section-title">
            <Icons icon="target" :size="18" color="var(--sg-accent)" />
            Platz-Serien
          </h2>
          <span class="badge badge-blue">{{ rangeSerien.length }}</span>
        </div>
        <button
          class="btn btn--primary"
          data-testid="new-serie-btn"
          @click="openCreate"
        >
          <Icons icon="plus" :size="14" />
          Neue Serie
        </button>
      </div>

      <!-- Grouped list -->
      <div v-if="rangeGroups.length > 0" class="range-groups">
        <div
          v-for="group in rangeGroups"
          :key="group.rangeId ?? '__none__'"
          class="range-group"
        >
          <!-- Group header -->
          <button
            class="range-group-header"
            @click="toggleGroup(group.rangeId ?? '__none__')"
          >
            <Icons
              icon="chevronRight"
              :size="13"
              color="var(--sg-text-faint)"
              class="group-chevron"
              :class="{ rotated: expandedGroups.has(group.rangeId ?? '__none__') }"
            />
            <Icons icon="ranges" :size="13" color="var(--sg-accent)" />
            <span class="range-group-name">{{ group.rangeName ?? 'Kein Platz' }}</span>
            <span class="range-group-count">{{ group.serien.length }}</span>
          </button>

          <!-- Serie rows -->
          <div
            v-if="expandedGroups.has(group.rangeId ?? '__none__')"
            class="serie-list"
          >
            <div
              v-for="s in group.serien"
              :key="s.id"
              class="serie-row"
              @click="openEdit(s)"
            >
              <div class="serie-info">
                <span class="serie-name">{{ s.name }}</span>
                <span class="serie-meta">{{ stepCount(s.steps) }} Würfe · {{ s.steps.length }} Schritte</span>
              </div>
              <div class="serie-dots">
                <span
                  v-for="(step, i) in s.steps.slice(0, 10)"
                  :key="step.id ?? i"
                  class="step-dot"
                  :style="modeDotStyle(step.type)"
                />
                <span v-if="s.steps.length > 10" class="step-dot-more">
                  +{{ s.steps.length - 10 }}
                </span>
              </div>
              <div class="serie-actions" @click.stop>
                <button
                  class="icon-btn"
                  title="Bearbeiten"
                  @click="openEdit(s)"
                >
                  <Icons icon="edit" :size="13" color="var(--sg-text-muted)" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Empty state -->
      <div v-else class="empty-state">
        <Icons icon="target" :size="40" color="rgba(0,0,0,0.08)" />
        <p class="empty-title">Noch keine Platz-Serien</p>
        <p class="empty-hint">
          Klicke auf „Neue Serie", um eine Platz-Serie direkt hier zu erstellen.
        </p>
      </div>
    </template>

    <!-- ══════════════════════════════════════════ -->
    <!-- PASSEN TAB                                      -->
    <!-- ══════════════════════════════════════════ -->
    <template v-if="activeTab === 'passen'">
      <!-- Section header -->
      <div class="section-header">
        <div class="section-title-row">
          <h2 class="section-title">
            <Icons icon="program" :size="18" color="var(--sg-accent)" />
            Passen-Vorlagen
          </h2>
          <span class="badge badge-blue">{{ savedGlobalPassen.length }}</span>
        </div>
        <button
          class="btn btn--primary"
          data-testid="new-passe-btn"
          @click="openPasseCreate"
        >
          <Icons icon="plus" :size="14" />
          Neue Passe
        </button>
      </div>

      <!-- Flat list in 2-column grid -->
      <div v-if="savedGlobalPassen.length > 0" class="range-groups">
        <div
          v-for="p in savedGlobalPassen"
          :key="p.id"
          class="range-group"
        >
          <div class="serie-list">
            <div
              class="serie-row passe-row"
              @click="openPasseEdit(p)"
            >
              <div class="serie-info">
                <span class="serie-name">{{ p.name }}</span>
                <span class="serie-meta">{{ passeThrows(p) }} Würfe · {{ p.serien?.length ?? 0 }} Serien</span>
              </div>
              <div class="serie-actions" @click.stop>
                <button
                  class="icon-btn"
                  title="Bearbeiten"
                  @click="openPasseEdit(p)"
                >
                  <Icons icon="edit" :size="13" color="var(--sg-text-muted)" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Empty state -->
      <div v-else class="empty-state">
        <Icons icon="program" :size="40" color="rgba(0,0,0,0.08)" />
        <p class="empty-title">Noch keine Passen-Vorlagen</p>
        <p class="empty-hint">
          Klicke auf „Neue Passe", um eine Passen-Vorlage zu erstellen.
        </p>
      </div>
    </template>

    <!-- Drawer -->
    <SerieDrawer
      :open="drawerOpen"
      :mode="drawerMode"
      :serie="drawerSerie"
      @saved="drawerOpen = false"
      @deleted="drawerOpen = false"
      @close="drawerOpen = false"
    />
    <GlobalPasseDrawer
      :open="passeDrawerOpen"
      :mode="passeDrawerMode"
      :passe="passeDrawerPasse"
      @saved="passeDrawerOpen = false"
      @deleted="passeDrawerOpen = false"
      @close="passeDrawerOpen = false"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watchEffect } from 'vue';
import { useUrlTab } from '@/composables/useUrlTab.js';
import { usePasseStore } from '@/stores/passeStore.js';
import { useRangeStore } from '@/stores/rangeStore.js';
import Icons from '@/components/Icons.vue';
import SerieDrawer from '@/components/SerieDrawer.vue';
import GlobalPasseDrawer from '@/components/GlobalPasseDrawer.vue';
import { modeDotStyle } from '@/constants/stepModes.js';

const passeStore = usePasseStore();
const rangeStore = useRangeStore();

onMounted(async () => {
  await rangeStore.initialize();
});

// ── Tabs ─────────────────────────────────────────────────────────────────────
// Active tab — synced to URL query param ?tab=xxx
const { activeTab, setTab } = useUrlTab('serien', ['serien', 'passen']);

// ── Serien data ───────────────────────────────────────────────────────────────
const rangeSerien = computed(() =>
  passeStore.savedSerien.filter((s) => s.ownership === 'range')
);

const rangeGroups = computed(() => {
  const map = new Map();
  for (const s of rangeSerien.value) {
    const key = s.rangeId ?? '__none__';
    if (!map.has(key)) {
      map.set(key, { rangeId: s.rangeId, rangeName: s.rangeName, serien: [] });
    }
    map.get(key).serien.push(s);
  }
  return Array.from(map.values());
});

// ── Passen data ───────────────────────────────────────────────────────────────
const savedGlobalPassen = computed(() => passeStore.savedGlobalPassen ?? []);

// ── Group expand/collapse ─────────────────────────────────────────────────────
const expandedGroups = ref(new Set());

const toggleGroup = (key) => {
  const next = new Set(expandedGroups.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  expandedGroups.value = next;
};

// Auto-expand all groups whenever the list changes
watchEffect(() => {
  for (const g of rangeGroups.value) {
    const key = g.rangeId ?? '__none__';
    if (!expandedGroups.value.has(key)) {
      expandedGroups.value = new Set([...expandedGroups.value, key]);
    }
  }
});

// ── Drawer state ──────────────────────────────────────────────────────────────
const drawerOpen = ref(false);
const drawerMode = ref('create');
const drawerSerie = ref(null);

const openCreate = () => {
  drawerSerie.value = null;
  drawerMode.value = 'create';
  drawerOpen.value = true;
};

const openEdit = (serie) => {
  drawerSerie.value = serie;
  drawerMode.value = 'edit';
  drawerOpen.value = true;
};

// ── Passe drawer state ────────────────────────────────────────────────────────
const passeDrawerOpen = ref(false);
const passeDrawerMode = ref('create');
const passeDrawerPasse = ref(null);

const openPasseCreate = () => {
  passeDrawerPasse.value = null;
  passeDrawerMode.value = 'create';
  passeDrawerOpen.value = true;
};

const openPasseEdit = (passe) => {
  passeDrawerPasse.value = passe;
  passeDrawerMode.value = 'edit';
  passeDrawerOpen.value = true;
};

// ── Helpers ───────────────────────────────────────────────────────────────────
const stepCount = (steps) => {
  let count = 0;
  for (const s of steps) {
    if (s.type === 'solo' || s.type === 'raffale') count += 1;
    else if (s.type === 'pair' || s.type === 'a_schuss') count += 2;
  }
  return count;
};

// Total Würfe across all serien of a passe.
const passeThrows = (p) =>
  (p.serien ?? []).reduce((sum, s) => sum + stepCount(s.steps ?? []), 0);

defineExpose({ drawerOpen, drawerMode, drawerSerie, passeDrawerOpen, passeDrawerMode, passeDrawerPasse });
</script>

<style scoped>
.passen-admin {
  padding: 24px;
  max-width: 900px;
}

/* ── Header ─────────────────────────────────────────────────────────────── */
.view-header {
  margin-bottom: 20px;
}

.view-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--sg-brand);
  margin: 0 0 4px;
}

.view-subtitle {
  font-size: 13.5px;
  color: var(--sg-text-muted);
  margin: 0;
}

/* ── Tabs ───────────────────────────────────────────────────────────────── */
.tab-row {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
  border-bottom: 1px solid var(--sg-border);
  padding-bottom: 0;
}

.tab-btn {
  padding: 10px 20px;
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

/* ── Section header ─────────────────────────────────────────────────────── */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
}

.section-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--sg-brand);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.badge {
  font-size: 11px;
  font-weight: 700;
  border-radius: 20px;
  padding: 2px 8px;
}

.badge-blue {
  background: var(--sg-accent-tint);
  color: var(--sg-color-info-text);
}

/* ── Range groups ───────────────────────────────────────────────────────── */
.range-groups {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

@media (min-width: 1024px) {
  .range-groups {
    grid-template-columns: repeat(2, 1fr);
    align-items: start;
  }
}

.range-group {
  background: var(--sg-bg-card);
  border-radius: 12px;
  box-shadow: var(--sg-shadow-sm);
  overflow: hidden;
}

.range-group-header {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: none;
  border: none;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;
}

.range-group-header:hover { background: var(--sg-bg-panel); }

.group-chevron {
  flex-shrink: 0;
  transition: transform 0.2s;
}

.group-chevron.rotated { transform: rotate(90deg); }

.range-group-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--sg-text-muted);
  flex: 1;
}

.range-group-count {
  font-size: 11px;
  color: var(--sg-text-faint);
  background: var(--sg-bg-panel);
  border-radius: 20px;
  padding: 1px 7px;
}

/* ── Serie rows ─────────────────────────────────────────────────────────── */
.serie-list {
  border-top: 1px solid var(--sg-border);
  display: flex;
  flex-direction: column;
}

.serie-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--sg-border);
  cursor: pointer;
  transition: background 0.12s;
}

.serie-row:last-child { border-bottom: none; }
.serie-row:hover { background: var(--sg-bg-page); }

.serie-info {
  flex: 1;
  min-width: 0;
}

.serie-name {
  display: block;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--sg-brand);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.serie-meta {
  display: block;
  font-size: 11.5px;
  color: var(--sg-text-faint);
  margin-top: 1px;
}

.serie-dots {
  display: flex;
  gap: 3px;
  align-items: center;
  flex-shrink: 0;
}

.step-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}


.step-dot-more {
  font-size: 10px;
  color: var(--sg-text-faint);
  margin-left: 2px;
}

.serie-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.icon-btn {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}

.icon-btn:hover {
  background: var(--sg-bg-panel);
  border-color: var(--sg-border);
}

/* ── Empty state ────────────────────────────────────────────────────────── */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 48px 16px;
  text-align: center;
  background: var(--sg-bg-card);
  border-radius: 12px;
  box-shadow: var(--sg-shadow-sm);
}

.empty-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--sg-text-faint);
  margin: 0;
}

.empty-hint {
  font-size: 13px;
  color: var(--sg-border-input);
  margin: 0;
  max-width: 340px;
  line-height: 1.5;
}

/* ── Buttons ────────────────────────────────────────────────────────────── */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid transparent;
}

.btn--primary {
  background: var(--sg-brand);
  color: #fff;
  border-color: var(--sg-brand);
}

.btn--primary:hover { background: var(--sg-brand); }
</style>
