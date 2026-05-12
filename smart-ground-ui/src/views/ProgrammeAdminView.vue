<template>
  <div class="programme-admin">
    <!-- Header -->
    <div class="view-header">
      <div>
        <h1 class="view-title">Programme & Abläufe</h1>
        <p class="view-subtitle">Verwalten Sie platzweite Abläufe für alle Schützen</p>
      </div>
    </div>

    <!-- Stats -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon--blue">
          <Icons icon="target" :size="16" color="#4fc3f7" />
        </div>
        <div>
          <div class="stat-value">{{ rangeAblaeufe.length }}</div>
          <div class="stat-label">Platz-Abläufe</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--green">
          <Icons icon="ranges" :size="16" color="#48bb78" />
        </div>
        <div>
          <div class="stat-value">{{ rangeGroups.length }}</div>
          <div class="stat-label">Plätze mit Abläufen</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--orange">
          <Icons icon="user" :size="16" color="#ed8936" />
        </div>
        <div>
          <div class="stat-value">{{ userAblaeufe.length }}</div>
          <div class="stat-label">Schützen-Abläufe (lokal)</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--purple">
          <Icons icon="program" :size="16" color="#9c27b0" />
        </div>
        <div>
          <div class="stat-value">{{ programStore.savedPrograms.length }}</div>
          <div class="stat-label">Schützen-Programme (lokal)</div>
        </div>
      </div>
    </div>

    <!-- ════════════════════════════════════════════ -->
    <!-- BLOCK: PLATZ-ABLÄUFE                         -->
    <!-- ════════════════════════════════════════════ -->
    <div class="section-card">
      <div class="section-header">
        <div class="section-title-row">
          <h2 class="section-title">
            <Icons icon="target" :size="18" color="#4fc3f7" />
            Platz-Abläufe
          </h2>
          <span class="badge badge-blue">{{ rangeAblaeufe.length }}</span>
        </div>
        <p class="section-desc">
          Platz-Abläufe sind für alle Schützen auf dem jeweiligen Platz sichtbar.
          Erstelle einen Ablauf im Erfassen-Modus (Remote → Platz wählen → Modus E) und
          aktiviere beim Speichern <strong>„Für alle auf diesem Platz"</strong>.
        </p>
      </div>

      <!-- Grouped by range -->
      <div v-if="rangeGroups.length > 0" class="range-groups">
        <div v-for="group in rangeGroups" :key="group.rangeId ?? '__none__'" class="range-group">
          <div class="range-group-header">
            <Icons icon="ranges" :size="13" color="#4fc3f7" />
            <span class="range-group-name">{{ group.rangeName ?? 'Kein Platz' }}</span>
            <span class="range-group-count">{{ group.ablaeufe.length }}</span>
          </div>
          <div class="ablauf-list">
            <div
              v-for="abl in group.ablaeufe"
              :key="abl.id"
              class="ablauf-row"
            >
              <div class="ablauf-info">
                <span class="ablauf-name">{{ abl.name }}</span>
                <span class="ablauf-meta">{{ stepCount(abl.steps) }} Würfe · {{ abl.steps.length }} Schritte</span>
              </div>
              <div class="ablauf-steps-preview">
                <span
                  v-for="(step, i) in abl.steps.slice(0, 10)"
                  :key="step.id ?? i"
                  class="step-dot"
                  :class="`dot-${step.type}`"
                  :title="stepTypeLabel(step.type)"
                />
                <span v-if="abl.steps.length > 10" class="step-dot-more">
                  +{{ abl.steps.length - 10 }}
                </span>
              </div>
              <div class="ablauf-actions">
                <button
                  class="icon-btn icon-btn--danger"
                  title="Ablauf löschen"
                  @click="confirmDelete(abl)"
                >
                  <Icons icon="trash" :size="14" color="#e53e3e" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="empty-state">
        <Icons icon="target" :size="36" color="rgba(0,0,0,0.1)" />
        <p class="empty-title">Keine Platz-Abläufe vorhanden</p>
        <p class="empty-hint">
          Zeichne einen Ablauf im Erfassen-Modus auf und wähle beim Speichern
          „Für alle auf diesem Platz".
        </p>
      </div>
    </div>

    <!-- ════════════════════════════════════════════ -->
    <!-- BLOCK: SCHÜTZEN-DATEN (Info)                 -->
    <!-- ════════════════════════════════════════════ -->
    <div class="section-card section-card--info">
      <div class="section-header">
        <div class="section-title-row">
          <h2 class="section-title">
            <Icons icon="user" :size="18" color="#ed8936" />
            Schützen-Daten
          </h2>
        </div>
        <p class="section-desc">
          Schützen-eigene Abläufe und Programme werden lokal auf dem Gerät des jeweiligen
          Schützen gespeichert. Diese Daten sind hier nicht einsehbar. Sobald eine
          Backend-Synchronisierung verfügbar ist, können Abläufe zentral verwaltet werden.
        </p>
      </div>
      <div class="info-tiles">
        <div class="info-tile">
          <Icons icon="user" :size="20" color="#ed8936" />
          <div class="info-tile-text">
            <span class="info-tile-label">Schützen-Abläufe</span>
            <span class="info-tile-desc">Privat, nur auf dem eigenen Gerät</span>
          </div>
        </div>
        <div class="info-tile">
          <Icons icon="program" :size="20" color="#9c27b0" />
          <div class="info-tile-text">
            <span class="info-tile-label">Schützen-Programme</span>
            <span class="info-tile-desc">Aus eigenen + Platz-Abläufen zusammengestellt</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Delete confirmation dialog -->
    <div v-if="deletingAblauf" class="dialog-backdrop" @click.self="deletingAblauf = null">
      <div class="dialog">
        <h3 class="dialog-title">Ablauf löschen?</h3>
        <p class="dialog-body">
          <strong>{{ deletingAblauf.name }}</strong> wird für alle Schützen auf diesem Platz entfernt.
        </p>
        <div class="dialog-actions">
          <button class="btn btn--ghost" @click="deletingAblauf = null">Abbrechen</button>
          <button class="btn btn--danger" @click="executeDelete">Löschen</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useProgramStore } from '@/stores/programStore.js';
import Icons from '@/components/Icons.vue';

const programStore = useProgramStore();

// ── Data views ────────────────────────────────────────────────────────────────
const rangeAblaeufe = computed(() =>
  programStore.savedAblaeufe.filter((a) => a.ownership === 'range'),
);

const userAblaeufe = computed(() =>
  programStore.savedAblaeufe.filter((a) => a.ownership !== 'range'),
);

// Platz-Abläufe nach Platz gruppiert
const rangeGroups = computed(() => {
  const map = new Map();
  for (const abl of rangeAblaeufe.value) {
    const key = abl.rangeId ?? '__none__';
    if (!map.has(key)) {
      map.set(key, { rangeId: abl.rangeId, rangeName: abl.rangeName, ablaeufe: [] });
    }
    map.get(key).ablaeufe.push(abl);
  }
  return Array.from(map.values());
});

// ── Delete ────────────────────────────────────────────────────────────────────
const deletingAblauf = ref(null);

const confirmDelete = (abl) => {
  deletingAblauf.value = abl;
};

const executeDelete = () => {
  if (deletingAblauf.value) {
    programStore.deleteAblauf(deletingAblauf.value.id);
    deletingAblauf.value = null;
  }
};

// ── Helpers ───────────────────────────────────────────────────────────────────
const stepCount = (steps) => {
  let count = 0;
  for (const s of steps) {
    if (s.type === 'solo') count += 1;
    else if (s.type === 'pair' || s.type === 'a_schuss') count += 2;
    else if (s.type === 'raffale') count += 2;
  }
  return count;
};

const stepTypeLabel = (type) => {
  const map = { solo: 'Solo', pair: 'Pair', a_schuss: 'a.Schuss', raffale: 'Raffale' };
  return map[type] ?? type;
};
</script>

<style scoped>
.programme-admin {
  padding: 24px;
  max-width: 900px;
}

/* ── Header ─────────────────────────────────────────────────────────────── */
.view-header {
  margin-bottom: 24px;
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

/* ── Stats ──────────────────────────────────────────────────────────────── */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 24px;
}

.stat-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.07);
}

.stat-icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon--blue   { background: rgba(79, 195, 247, 0.12); }
.stat-icon--green  { background: rgba(72, 187, 120, 0.12); }
.stat-icon--orange { background: rgba(237, 137, 54, 0.12); }
.stat-icon--purple { background: rgba(156, 39, 176, 0.12); }

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #1a1a2e;
  line-height: 1.1;
}

.stat-label {
  font-size: 11.5px;
  color: #718096;
  margin-top: 2px;
}

/* ── Section cards ──────────────────────────────────────────────────────── */
.section-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  margin-bottom: 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.07);
}

.section-card--info {
  border: 1px solid rgba(237, 137, 54, 0.2);
  background: rgba(237, 137, 54, 0.03);
}

.section-header {
  margin-bottom: 20px;
}

.section-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
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

.section-desc {
  font-size: 13px;
  color: #718096;
  margin: 0;
  line-height: 1.5;
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
  gap: 16px;
}

.range-group-header {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 8px;
}

.range-group-name {
  font-size: 12.5px;
  font-weight: 600;
  color: #4a5568;
}

.range-group-count {
  font-size: 11px;
  color: #a0aec0;
  background: #f7fafc;
  border-radius: 20px;
  padding: 1px 7px;
}

/* ── Ablauf rows ────────────────────────────────────────────────────────── */
.ablauf-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ablauf-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: #f7f8fc;
  border-radius: 8px;
  border: 1px solid rgba(0,0,0,0.05);
  transition: border-color 0.15s;
}

.ablauf-row:hover {
  border-color: rgba(79, 195, 247, 0.3);
}

.ablauf-info {
  flex: 1;
  min-width: 0;
}

.ablauf-name {
  display: block;
  font-size: 13.5px;
  font-weight: 600;
  color: #1a1a2e;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ablauf-meta {
  display: block;
  font-size: 11.5px;
  color: #a0aec0;
  margin-top: 1px;
}

.ablauf-steps-preview {
  display: flex;
  gap: 3px;
  flex-wrap: wrap;
  align-items: center;
}

.step-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}

.dot-solo     { background: #4fc3f7; }
.dot-pair     { background: #48bb78; }
.dot-a_schuss { background: #f6ad55; }
.dot-raffale  { background: #fc8181; }

.step-dot-more {
  font-size: 10px;
  color: #a0aec0;
  margin-left: 2px;
}

.ablauf-actions {
  display: flex;
  gap: 6px;
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

.icon-btn--danger:hover {
  background: rgba(229, 62, 62, 0.08);
  border-color: rgba(229, 62, 62, 0.25);
}

/* ── Empty state ────────────────────────────────────────────────────────── */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 16px;
  text-align: center;
}

.empty-title {
  font-size: 14px;
  font-weight: 600;
  color: #a0aec0;
  margin: 0;
}

.empty-hint {
  font-size: 12.5px;
  color: #cbd5e0;
  margin: 0;
  max-width: 360px;
  line-height: 1.5;
}

/* ── Info tiles ─────────────────────────────────────────────────────────── */
.info-tiles {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.info-tile {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: rgba(237, 137, 54, 0.06);
  border-radius: 8px;
}

.info-tile-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.info-tile-label {
  font-size: 13px;
  font-weight: 600;
  color: #4a5568;
}

.info-tile-desc {
  font-size: 12px;
  color: #a0aec0;
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

.btn--ghost {
  background: transparent;
  border-color: #e2e8f0;
  color: #4a5568;
}

.btn--ghost:hover { background: #f7fafc; }

.btn--danger {
  background: rgba(229, 62, 62, 0.1);
  border-color: rgba(229, 62, 62, 0.3);
  color: #e53e3e;
}

.btn--danger:hover { background: rgba(229, 62, 62, 0.18); }

/* ── Delete dialog ──────────────────────────────────────────────────────── */
.dialog-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.dialog {
  background: #fff;
  border-radius: 14px;
  padding: 24px;
  width: 340px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.18);
}

.dialog-title {
  font-size: 16px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 10px;
}

.dialog-body {
  font-size: 13.5px;
  color: #4a5568;
  margin: 0 0 20px;
  line-height: 1.5;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
