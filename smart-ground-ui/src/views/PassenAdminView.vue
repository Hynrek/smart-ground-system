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
        @click="activeTab = 'serien'"
      >
        Serien
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'passen' }"
        @click="activeTab = 'passen'"
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
            <Icons icon="target" :size="18" color="#4fc3f7" />
            Platz-Serien
          </h2>
          <span class="badge badge-blue">{{ rangeSerien.length }}</span>
        </div>
        <button
          class="btn btn--primary"
          data-testid="new-serie-btn"
          @click="openCreate"
        >
          <Icons icon="plus" :size="14" color="#fff" />
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
              color="#a0aec0"
              class="group-chevron"
              :class="{ rotated: expandedGroups.has(group.rangeId ?? '__none__') }"
            />
            <Icons icon="ranges" :size="13" color="#4fc3f7" />
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
                  :class="`dot-${step.type}`"
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
                  <Icons icon="edit" :size="13" color="#718096" />
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
            <Icons icon="program" :size="18" color="#4fc3f7" />
            Passen-Vorlagen
          </h2>
          <span class="badge badge-blue">{{ savedGlobalPassen.length }}</span>
        </div>
        <button
          class="btn btn--primary"
          data-testid="new-passe-btn"
          @click="openPasseCreate"
        >
          <Icons icon="plus" :size="14" color="#fff" />
          Neue Passe
        </button>
      </div>

      <!-- Grouped list -->
      <div v-if="passeGroups.length > 0" class="range-groups">
        <div
          v-for="group in passeGroups"
          :key="group.rangeId ?? '__none__'"
          class="range-group"
        >
          <!-- Group header -->
          <button
            class="range-group-header"
            @click="togglePasseGroup(group.rangeId ?? '__none__')"
          >
            <Icons
              icon="chevronRight"
              :size="13"
              color="#a0aec0"
              class="group-chevron"
              :class="{ rotated: expandedPasseGroups.has(group.rangeId ?? '__none__') }"
            />
            <Icons icon="ranges" :size="13" color="#4fc3f7" />
            <span class="range-group-name">{{ group.rangeName ?? 'Kein Platz' }}</span>
            <span class="range-group-count">{{ group.passen.length }}</span>
          </button>

          <!-- Passe rows -->
          <div
            v-if="expandedPasseGroups.has(group.rangeId ?? '__none__')"
            class="serie-list"
          >
            <div
              v-for="p in group.passen"
              :key="p.id"
              class="serie-row passe-row"
              @click="openPasseEdit(p)"
            >
              <div class="serie-info">
                <span class="serie-name">{{ p.name }}</span>
                <span class="serie-meta">{{ p.serien?.length ?? 0 }} Serien</span>
              </div>
              <div class="serie-actions" @click.stop>
                <button
                  class="icon-btn"
                  title="Bearbeiten"
                  @click="openPasseEdit(p)"
                >
                  <Icons icon="edit" :size="13" color="#718096" />
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
import { usePasseStore } from '@/stores/passeStore.js';
import { useRangeStore } from '@/stores/rangeStore.js';
import Icons from '@/components/Icons.vue';
import SerieDrawer from '@/components/SerieDrawer.vue';
import GlobalPasseDrawer from '@/components/GlobalPasseDrawer.vue';

const passeStore = usePasseStore();
const rangeStore = useRangeStore();

onMounted(async () => {
  await rangeStore.initialize();
});

// ── Tabs ─────────────────────────────────────────────────────────────────────
const activeTab = ref('serien');

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

// Passen werden nach dem Platz der ersten Serie gruppiert (Best-effort-Anzeige).
// Passen mit Serien von mehreren Plätzen erscheinen unter dem Platz der ersten Serie.
const passeGroups = computed(() => {
  const map = new Map();
  for (const p of savedGlobalPassen.value) {
    const firstSerie = p.serien?.[0];
    const rangeId = firstSerie?.rangeId ?? '__none__';
    const rangeName = firstSerie?.rangeName ?? null;
    if (!map.has(rangeId)) {
      map.set(rangeId, { rangeId, rangeName, passen: [] });
    }
    map.get(rangeId).passen.push(p);
  }
  return Array.from(map.values());
});

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

// ── Passe group expand/collapse ───────────────────────────────────────────────
const expandedPasseGroups = ref(new Set());

const togglePasseGroup = (key) => {
  const next = new Set(expandedPasseGroups.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  expandedPasseGroups.value = next;
};

watchEffect(() => {
  for (const g of passeGroups.value) {
    const key = g.rangeId ?? '__none__';
    if (!expandedPasseGroups.value.has(key)) {
      expandedPasseGroups.value = new Set([...expandedPasseGroups.value, key]);
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
  color: #1a1a2e;
  margin: 0 0 4px;
}

.view-subtitle {
  font-size: 13.5px;
  color: #718096;
  margin: 0;
}

/* ── Tabs ───────────────────────────────────────────────────────────────── */
.tab-row {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
  border-bottom: 1px solid #e2e8f0;
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
  color: #a0aec0;
  cursor: pointer;
  margin-bottom: -1px;
  transition: color 0.15s, border-color 0.15s;
}

.tab-btn.active {
  color: #1a1a2e;
  border-bottom-color: #4fc3f7;
}

.tab-btn:hover:not(.active) { color: #4a5568; }

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
  color: #1a1a2e;
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
  background: rgba(79, 195, 247, 0.12);
  color: #0288d1;
}

/* ── Range groups ───────────────────────────────────────────────────────── */
.range-groups {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.range-group {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.07);
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

.range-group-header:hover { background: #f7fafc; }

.group-chevron {
  flex-shrink: 0;
  transition: transform 0.2s;
}

.group-chevron.rotated { transform: rotate(90deg); }

.range-group-name {
  font-size: 13px;
  font-weight: 600;
  color: #4a5568;
  flex: 1;
}

.range-group-count {
  font-size: 11px;
  color: #a0aec0;
  background: #f7fafc;
  border-radius: 20px;
  padding: 1px 7px;
}

/* ── Serie rows ─────────────────────────────────────────────────────────── */
.serie-list {
  border-top: 1px solid #f0f4f8;
  display: flex;
  flex-direction: column;
}

.serie-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid #f0f4f8;
  cursor: pointer;
  transition: background 0.12s;
}

.serie-row:last-child { border-bottom: none; }
.serie-row:hover { background: #f7f8fc; }

.serie-info {
  flex: 1;
  min-width: 0;
}

.serie-name {
  display: block;
  font-size: 13.5px;
  font-weight: 600;
  color: #1a1a2e;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.serie-meta {
  display: block;
  font-size: 11.5px;
  color: #a0aec0;
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

.dot-solo     { background: #4fc3f7; }
.dot-pair     { background: #48bb78; }
.dot-a_schuss { background: #f6ad55; }
.dot-raffale  { background: #a855f7; }

.step-dot-more {
  font-size: 10px;
  color: #a0aec0;
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
  background: #f0f4f8;
  border-color: #e2e8f0;
}

/* ── Empty state ────────────────────────────────────────────────────────── */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 48px 16px;
  text-align: center;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.07);
}

.empty-title {
  font-size: 14px;
  font-weight: 600;
  color: #a0aec0;
  margin: 0;
}

.empty-hint {
  font-size: 13px;
  color: #cbd5e0;
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
  background: #1a1a2e;
  color: #fff;
  border-color: #1a1a2e;
}

.btn--primary:hover { background: #0f0f1a; }
</style>
