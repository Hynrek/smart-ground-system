<template>
  <div class="passen-view">
    <!-- Top bar -->
    <div class="top-bar">
      <button class="back-btn" @click="router.push('/home')">
        <Icons icon="chevronLeft" :size="18" color="rgba(255,255,255,0.7)" />
      </button>
      <h1 class="page-title">Passen</h1>
      <div class="top-bar-spacer" />
    </div>

    <!-- Tab toggle -->
    <div class="tab-toggle">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'passen' }"
        @click="setTab('passen')"
      >
        Passen
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'active' }"
        @click="setTab('active')"
      >
        Aktive Passen
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'completed' }"
        @click="setTab('completed')"
      >
        Abgeschlossen
      </button>
    </div>

    <!-- Scrollable content -->
    <div class="content">

      <!-- ═══════════════════════════════════════════ -->
      <!-- TAB 1: PASSEN MANAGEMENT                    -->
      <!-- ═══════════════════════════════════════════ -->
      <template v-if="activeTab === 'passen'">

      <!-- ═══════════════════════════════════════════ -->
      <!-- BLOCK 1: PASSEN                             -->
      <!-- ═══════════════════════════════════════════ -->
      <section class="block">
        <div class="block-header">
          <div class="block-title-row">
            <span class="block-title">Passen</span>
            <span class="block-badge">{{ passeStore.savedPassen.length }}</span>
          </div>
          <p class="block-desc">
            Zusammenstellung aus 1–n Serien. Kann direkt auf einem Platz gestartet werden.
          </p>
        </div>

        <!-- Passen list -->
        <div v-if="passeStore.savedPassen.length > 0" class="program-list">
          <div
            v-for="passe in passeStore.savedPassen"
            :key="passe.id"
            class="program-card"
            :class="{ expanded: expandedPasseId === passe.id }"
          >
            <div class="prog-main" @click="renamingPasseId !== passe.id ? toggleExpand(passe.id) : null">
              <div class="prog-info">
                <template v-if="renamingPasseId === passe.id">
                  <input
                    ref="passeRenameInputRef"
                    v-model="passeRenameValue"
                    class="rename-input"
                    type="text"
                    maxlength="50"
                    @keyup.enter="confirmRenamePasse(passe.id)"
                    @keyup.escape="renamingPasseId = null"
                    @click.stop
                  />
                </template>
                <template v-else>
                  <span class="prog-name">{{ passe.name }}</span>
                  <div class="prog-meta-row">
                    <span class="prog-meta-item">{{ passe.serien.length }} Serien</span>
                    <span class="prog-meta-dot">·</span>
                    <span class="prog-meta-item">{{ totalThrows(passe.serien) }} Würfe</span>
                  </div>
                </template>
              </div>
              <div v-if="renamingPasseId === passe.id" class="prog-actions-inline" @click.stop>
                <button
                  class="icon-btn icon-btn--confirm"
                  title="Umbenennen bestätigen"
                  @click="confirmRenamePasse(passe.id)"
                >
                  <Icons icon="check" :size="13" />
                </button>
                <button
                  class="icon-btn"
                  title="Abbrechen"
                  @click="renamingPasseId = null"
                >
                  <Icons icon="x" :size="13" color="rgba(255,255,255,0.4)" />
                </button>
              </div>
              <div v-else class="prog-actions-inline" @click.stop>
                <button
                  class="icon-btn"
                  title="Umbenennen"
                  @click="startRenamePasse(passe)"
                >
                  <Icons icon="edit" :size="13" color="rgba(255,255,255,0.4)" />
                </button>
              </div>
              <Icons
                v-if="renamingPasseId !== passe.id"
                icon="chevronRight"
                :size="14"
                color="rgba(255,255,255,0.3)"
                class="expand-icon"
                :class="{ rotated: expandedPasseId === passe.id }"
              />
            </div>

            <!-- Expanded detail -->
            <div v-if="expandedPasseId === passe.id" class="prog-detail">
              <!-- Serien grouped by range -->
              <div
                v-for="rg in passeRangeGroups(passe)"
                :key="rg.rangeId ?? '__none__'"
                class="prog-range-group"
              >
                <div class="prog-range-label">
                  <Icons icon="ranges" :size="10" color="rgba(255,255,255,0.3)" />
                  <span>{{ rg.rangeName ?? 'Kein Platz' }}</span>
                </div>
                <div class="prog-seg-chips">
                  <span v-for="seg in rg.serien" :key="seg.id" class="prog-seg-chip">
                    {{ seg.alias ?? seg.id }}
                    <span class="chip-throws">{{ stepCount(seg.steps) }} W</span>
                  </span>
                </div>
              </div>

              <!-- Actions -->
              <div class="prog-actions">
                <button class="action-btn action-btn--start" @click.stop="openStartModal(passe)">
                  <Icons icon="play" :size="14" color="var(--sg-accent)" />
                  Starten
                </button>
                <button class="action-btn action-btn--danger" @click.stop="passeStore.deletePasse(passe.id)">
                  <Icons icon="trash" :size="13" color="rgba(252,129,129,0.7)" />
                  Löschen
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty state -->
        <div v-if="passeStore.savedPassen.length === 0 && !creatingPasse" class="empty-block">
          <Icons icon="program" :size="36" color="rgba(255,255,255,0.07)" />
          <p>Noch keine Passen</p>
          <p class="empty-hint">Kombiniere Serien zu einer Passe</p>
        </div>

        <!-- Create passe flow -->
        <div v-if="creatingPasse" class="create-program-panel">
          <div class="create-panel-header">
            <span class="create-panel-title">Neue Passe</span>
          </div>

          <!-- Name input -->
          <div class="create-field">
            <label class="create-label">Name</label>
            <input
              v-model="newPasseName"
              class="create-input"
              type="text"
              placeholder="z.B. Training Woche 1"
              maxlength="50"
            />
          </div>

          <!-- Serie picker hint -->
          <div class="create-field">
            <label class="create-label">
              Serien auswählen
              <span class="create-label-count">{{ selectedSerieIds.size }} gewählt</span>
            </label>
            <p v-if="passeStore.savedSerien.length === 0" class="create-no-serien">
              Noch keine Serien vorhanden. Zeichne zuerst Serien im Erfassen-Modus auf.
            </p>
            <p v-else class="create-hint">Tippe auf Serien oben, um sie auszuwählen.</p>
          </div>

          <!-- Selected segments preview -->
          <div v-if="selectedSerieIds.size > 0" class="create-preview">
            <div
              v-for="rg in selectedRangeGroups"
              :key="rg.rangeId ?? '__none__'"
              class="prog-range-group"
            >
              <div class="prog-range-label">
                <Icons icon="ranges" :size="10" color="rgba(255,255,255,0.3)" />
                <span>{{ rg.rangeName ?? 'Kein Platz' }}</span>
              </div>
              <div class="prog-seg-chips">
                <span
                  v-for="seg in rg.serien"
                  :key="seg.id"
                  class="prog-seg-chip prog-seg-chip--selected"
                >
                  {{ seg.name }}
                  <span class="chip-throws">{{ stepCount(seg.steps) }} W</span>
                  <button class="chip-remove" @click="toggleSerieSelection(seg.id)">
                    <Icons icon="x" :size="9" color="rgba(255,255,255,0.5)" />
                  </button>
                </span>
              </div>
            </div>
          </div>

          <!-- Create actions -->
          <div class="create-actions">
            <button class="action-btn action-btn--cancel" @click="cancelCreatePasse">
              Abbrechen
            </button>
            <button
              class="action-btn action-btn--create"
              :disabled="selectedSerieIds.size === 0"
              @click="confirmCreatePasse"
            >
              <Icons icon="check" :size="13" />
              Erstellen
            </button>
          </div>
        </div>

        <!-- New passe button -->
        <button
          v-if="!creatingPasse"
          class="new-program-btn"
          :disabled="passeStore.savedSerien.length === 0"
          @click="startCreatePasse"
        >
          <Icons icon="plus" :size="16" color="var(--sg-accent-subtle)" />
          Neue Passe
        </button>
      </section>

      <!-- ═══════════════════════════════════════════ -->
      <!-- BLOCK 2: SERIEN                             -->
      <!-- ═══════════════════════════════════════════ -->
      <section class="block">
        <div class="block-header">
          <div class="block-title-row">
            <span class="block-title">Serien</span>
            <span class="block-badge">{{ passeStore.savedSerien.length }}</span>
          </div>
          <p class="block-desc">
            Im Erfassen-Modus aufgezeichnete Bewegungsabfolgen. Können zu Passen zusammengestellt werden.
          </p>
        </div>

        <!-- User segments, grouped by range -->
        <template v-if="passeStore.savedSerien.length > 0">
          <div v-for="group in serieGroups" :key="group.rangeId ?? '__no_range__'" class="range-group">
            <button
              class="range-group-header"
              @click="toggleRangeGroup(group.rangeId ?? '__no_range__')"
            >
              <Icons
                icon="chevronRight"
                :size="12"
                color="rgba(255,255,255,0.3)"
                class="range-group-chevron"
                :class="{ rotated: expandedRangeGroups.has(group.rangeId ?? '__no_range__') }"
              />
              <Icons icon="ranges" :size="11" color="rgba(255,255,255,0.3)" />
              <span>{{ group.rangeName ?? 'Kein Platz zugeordnet' }}</span>
            </button>
            <div
              v-if="expandedRangeGroups.has(group.rangeId ?? '__no_range__')"
              class="segment-list">
              <div
                v-for="seg in group.serien"
                :key="seg.id"
                class="segment-card"
                :class="{ 'is-selected': selectedSerieIds.has(seg.id) }"
                @click="creatingPasse ? toggleSerieSelection(seg.id) : null"
              >
                <div class="seg-main">
                  <div class="seg-info">
                    <template v-if="renamingSerieId === seg.id">
                      <input
                        ref="renameInputRef"
                        v-model="renameValue"
                        class="rename-input"
                        type="text"
                        maxlength="40"
                        @keyup.enter="confirmRename(seg.id)"
                        @keyup.escape="renamingSerieId = null"
                        @click.stop
                      />
                    </template>
                    <template v-else>
                      <span class="seg-name">{{ seg.name }}</span>
                      <span v-if="seg.ownership === 'range'" class="ownership-chip">Platz</span>
                    </template>
                    <span class="seg-meta">{{ stepCount(seg.steps) }} Würfe</span>
                  </div>
                  <!-- Eigene Serien: umbenennen + löschen. Platz-Serien: nur löschen für Admin -->
                  <div v-if="!creatingPasse" class="seg-actions">
                    <button
                      v-if="seg.ownership !== 'range' && renamingSerieId === seg.id"
                      class="icon-btn icon-btn--confirm"
                      title="Umbenennen bestätigen"
                      @click.stop="confirmRename(seg.id)"
                    >
                      <Icons icon="check" :size="13" />
                    </button>
                    <button
                      v-else-if="seg.ownership !== 'range'"
                      class="icon-btn"
                      title="Umbenennen"
                      @click.stop="startRename(seg)"
                    >
                      <Icons icon="edit" :size="13" color="rgba(255,255,255,0.4)" />
                    </button>
                    <button
                      v-if="seg.ownership !== 'range' || authStore.hasPermission('MANAGE_RANGES')"
                      class="icon-btn icon-btn--danger"
                      title="Löschen"
                      @click.stop="passeStore.deleteSerie(seg.id)"
                    >
                      <Icons icon="trash" :size="13" color="rgba(252,129,129,0.6)" />
                    </button>
                  </div>
                  <div v-else class="seg-check">
                    <div class="check-circle" :class="{ active: selectedSerieIds.has(seg.id) }">
                      <Icons v-if="selectedSerieIds.has(seg.id)" icon="check" :size="11" />
                    </div>
                  </div>
                </div>
                <div class="seg-steps-preview">
                  <span
                    v-for="step in seg.steps.slice(0, 8)"
                    :key="step.id"
                    class="step-dot"
                    :style="modeDotStyle(step.type)"
                    :title="stepTypeLabel(step.type)"
                  />
                  <span v-if="seg.steps.length > 8" class="step-dot-more">
                    +{{ seg.steps.length - 8 }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- Empty state -->
        <div v-if="passeStore.savedSerien.length === 0" class="empty-block">
          <Icons icon="program" :size="36" color="rgba(255,255,255,0.07)" />
          <p>Noch keine Serien</p>
          <p class="empty-hint">Zeichne Serien im Erfassen-Modus auf (Remote → Platz wählen → Modus E)</p>
        </div>
      </section>

      </template>

      <!-- ═══════════════════════════════════════════ -->
      <!-- TAB 2: AKTIVE PASSEN                        -->
      <!-- ═══════════════════════════════════════════ -->
      <template v-if="activeTab === 'active'">
      <section class="block">
        <div class="block-header">
          <div class="block-title-row">
            <span class="block-title">Aktive Passen</span>
            <span class="block-badge">{{ passenActiveInstances.length }}</span>
          </div>
          <p class="block-desc">
            Gestartete Passen. Blöcke erscheinen auf den zugehörigen Plätzen.
          </p>
        </div>

        <div v-if="passenActiveInstances.length > 0" class="active-sessions-list">
          <div
            v-for="inst in passenActiveInstances"
            :key="inst.instanceId"
            class="session-card"
          >
            <div class="session-main">
              <div class="session-info">
                <span class="session-program">{{ inst.templateName }}</span>
                <span class="session-range">
                  {{ inst.players.map(p => p.displayName).join(', ') }}
                  · {{ inst.blocks.filter(b => b.status === 'done').length }}/{{ inst.blocks.length }} Blöcke
                </span>
              </div>
              <button class="icon-btn" title="Stoppen" @click="stopInstanceConfirm(inst)">
                <Icons icon="x" :size="13" color="rgba(252,129,129,0.6)" />
              </button>
            </div>

            <div class="block-status-list">
              <div
                v-for="block in inst.blocks"
                :key="block.blockId"
                class="block-status-row"
              >
                <span class="block-status-dot" :class="`dot-${block.status}`" />
                <span class="block-status-range">{{ block.rangeName ?? 'Kein Platz' }}</span>
                <span class="block-status-name">{{ block.serieAlias }}</span>
              </div>
            </div>

            <div class="progress-bar-container">
              <div class="progress-bar">
                <div
                  class="progress-fill"
                  :style="{ width: (inst.blocks.filter(b => b.status === 'done').length / inst.blocks.length * 100) + '%' }"
                />
              </div>
              <span class="progress-label">
                {{ inst.blocks.filter(b => b.status === 'done').length }}/{{ inst.blocks.length }}
              </span>
            </div>
          </div>
        </div>

        <div v-if="passenActiveInstances.length === 0" class="empty-block">
          <Icons icon="program" :size="36" color="rgba(255,255,255,0.07)" />
          <p>Keine aktiven Passen</p>
          <p class="empty-hint">Starte eine Passe über den Passen-Tab.</p>
        </div>
      </section>
      </template>

      <!-- ═══════════════════════════════════════════ -->
      <!-- TAB 3: ABGESCHLOSSENE PASSEN                -->
      <!-- ═══════════════════════════════════════════ -->
      <template v-if="activeTab === 'completed'">
        <section class="block">
          <div class="block-header">
            <div class="block-title-row">
              <span class="block-title">Abgeschlossene Passen</span>
              <span class="block-badge">{{ passenCompletedInstances.length }}</span>
            </div>
            <p class="block-desc">Fertig gespielte Passen. Basis für zukünftige Statistiken.</p>
          </div>

          <div v-if="passenCompletedInstances.length > 0" class="active-sessions-list">
            <div
              v-for="inst in passenCompletedInstances"
              :key="inst.instanceId"
              class="session-card completed-card"
              @click="toggleCompletedCard(inst.instanceId)"
            >
              <!-- Header row -->
              <div class="session-main">
                <span class="session-program">{{ inst.templateName }}</span>
                <span class="total-wuerfe">{{ new Date(inst.completedAt).toLocaleDateString('de-CH') }}</span>
              </div>

              <!-- Per-player summary chips (always visible) -->
              <div class="player-summaries">
                <div
                  v-for="ps in getPlayerSummaries(inst)"
                  :key="ps.player.id"
                  class="player-summary-chip"
                >
                  <span class="ps-name">{{ ps.player.displayName }}</span>
                  <span class="ps-pts">{{ ps.totalPts }}/{{ ps.maxPts }}</span>
                  <span class="ps-pct">{{ ps.hitPct }}%</span>
                </div>
              </div>

              <!-- Expandable detail: scores grouped by player -->
              <div v-if="expandedCards.has(inst.instanceId)" class="completed-detail" @click.stop>
                <div
                  v-for="ps in getPlayerSummaries(inst)"
                  :key="ps.player.id"
                  class="player-detail-section"
                >
                  <div class="player-detail-header">
                    <span class="pd-name">{{ ps.player.displayName }}</span>
                    <span class="pd-total">{{ ps.totalPts }}/{{ ps.maxPts }}</span>
                  </div>
                  <div
                    v-for="{ block, pr } in getBlocksForPlayer(inst, ps.player.id)"
                    :key="block.blockId"
                    class="completed-score-row"
                  >
                    <span class="completed-score-block">
                      <span class="completed-score-range">{{ block.rangeName }}</span>
                      {{ block.serieAlias }}
                    </span>
                    <span class="completed-score-pts">{{ pr.totalPoints }}/{{ pr.maxPoints }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="passenCompletedInstances.length === 0" class="empty-block">
            <Icons icon="program" :size="36" color="rgba(255,255,255,0.07)" />
            <p>Noch keine abgeschlossenen Passen</p>
          </div>
        </section>
      </template>

    </div>

    <!-- ── Inline Passe Start Modal ──────────────────── -->
    <div v-if="startingPasse" class="start-modal-overlay" @click.self="cancelStartPasse">
      <div class="start-modal">
        <h3 class="start-modal-title">{{ startingPasse.name }} starten</h3>

        <div class="start-modal-players">
          <div
            v-for="(player, i) in startModalPlayers"
            :key="player.id"
            class="start-player-row"
          >
            <span class="start-player-num">{{ i + 1 }}:</span>
            <input
              v-model="player.displayName"
              class="start-player-input"
              type="text"
              :placeholder="`Schütze ${i + 1}`"
              maxlength="30"
            />
            <button
              v-if="startModalPlayers.length > 1"
              class="icon-btn icon-btn--danger"
              @click="removeStartModalPlayer(i)"
            >
              <Icons icon="x" :size="11" color="var(--sg-color-danger-bg)" />
            </button>
          </div>
        </div>

        <button class="add-player-btn" @click="addStartModalPlayer">
          + Schütze hinzufügen
        </button>

        <div class="start-modal-actions">
          <button class="action-btn action-btn--cancel" @click="cancelStartPasse">Abbrechen</button>
          <button
            class="action-btn action-btn--start"
            :disabled="startModalPlayers.length === 0"
            @click="confirmStartPasse"
          >
            <Icons icon="play" :size="14" color="var(--sg-accent)" />
            Starten
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { usePasseStore } from '@/stores/passeStore.js';
import { useAuthStore } from '@/stores/authStore.js';
import { usePlaySessionStore } from '@/stores/playSessionStore.js';
import { useActivePasseStore } from '@/stores/activePasseStore.js';
import Icons from '@/components/Icons.vue';
import { stepModeLabel, modeDotStyle } from '@/constants/stepModes.js';
import { useUrlTab } from '@/composables/useUrlTab.js';
import { useRevalidate } from '@/composables/useRevalidate.js';

const router = useRouter();
const passeStore = usePasseStore();
const authStore = useAuthStore();
const playSessionStore = usePlaySessionStore();
const activePasseStore = useActivePasseStore();

// Keep Serien/Passen fresh against admin edits while this view is open
useRevalidate(() => {
  passeStore.loadSerienFromStorage();
  passeStore.loadPassenFromStorage();
}, { interval: 10000 });

// Filter out training instances — this view only shows passe instances
const passenActiveInstances = computed(() =>
  activePasseStore.activeInstances.filter((i) => i.type !== 'training')
);
const passenCompletedInstances = computed(() =>
  activePasseStore.completedInstances.filter((i) => i.type !== 'training')
);

// Active passen tab — synced to URL query param ?tab=xxx
const { activeTab, setTab } = useUrlTab('passen', ['passen', 'active', 'completed']);

// ── Inline passe start modal ────────────────────────────────────────────
const startingPasse = ref(null);
const startModalPlayers = ref([]);
let _nextStartPlayerId = 1;

const openStartModal = (passe) => {
  startingPasse.value = passe;
  _nextStartPlayerId = 1;
  startModalPlayers.value = [{ id: `sp-${_nextStartPlayerId++}`, displayName: 'Schütze 1', type: 'guest' }];
};

const addStartModalPlayer = () => {
  const n = startModalPlayers.value.length + 1;
  startModalPlayers.value.push({ id: `sp-${_nextStartPlayerId++}`, displayName: `Schütze ${n}`, type: 'guest' });
};

const removeStartModalPlayer = (index) => {
  startModalPlayers.value.splice(index, 1);
};

const confirmStartPasse = () => {
  if (!startingPasse.value || startModalPlayers.value.length === 0) return;
  activePasseStore.startPasse(startingPasse.value, startModalPlayers.value);
  startingPasse.value = null;
  startModalPlayers.value = [];
  setTab('active', { replace: true });
};

const cancelStartPasse = () => {
  startingPasse.value = null;
  startModalPlayers.value = [];
};

// ── Active session rename logic ────────────────────────────────────────────
const renamingSessionId = ref(null);
const sessionRenameValue = ref('');
const sessionRenameInputRef = ref(null);

// ── Serie grouping ───────────────────────────────────────────────────────
const serieGroups = computed(() => {
  const map = new Map();
  for (const seg of passeStore.savedSerien) {
    const key = seg.rangeId ?? '__no_range__';
    if (!map.has(key)) {
      map.set(key, { rangeId: seg.rangeId, rangeName: seg.rangeName, serien: [] });
    }
    map.get(key).serien.push(seg);
  }
  return Array.from(map.values());
});

// ── Rename logic ───────────────────────────────────────────────────────────
const renamingSerieId = ref(null);
const renameValue = ref('');
const renameInputRef = ref(null);

const startRename = (seg) => {
  renamingSerieId.value = seg.id;
  renameValue.value = seg.name;
  nextTick(() => renameInputRef.value?.[0]?.focus?.() ?? renameInputRef.value?.focus?.());
};

const confirmRename = (segId) => {
  if (renameValue.value.trim()) {
    passeStore.renameSerie(segId, renameValue.value.trim());
  }
  renamingSerieId.value = null;
};

// ── Range group expand/collapse ────────────────────────────────────────────
const expandedRangeGroups = ref(new Set());

const toggleRangeGroup = (rangeId) => {
  const next = new Set(expandedRangeGroups.value);
  if (next.has(rangeId)) next.delete(rangeId);
  else next.add(rangeId);
  expandedRangeGroups.value = next;
};

// ── Expand passe cards ─────────────────────────────────────────────────────
const expandedPasseId = ref(null);
const renamingPasseId = ref(null);
const passeRenameValue = ref('');
const passeRenameInputRef = ref(null);

const toggleExpand = (passeId) => {
  expandedPasseId.value = expandedPasseId.value === passeId ? null : passeId;
};

const startRenamePasse = (passe) => {
  renamingPasseId.value = passe.id;
  passeRenameValue.value = passe.name;
  nextTick(() => passeRenameInputRef.value?.[0]?.focus?.() ?? passeRenameInputRef.value?.focus?.());
};

const confirmRenamePasse = (passeId) => {
  if (passeRenameValue.value.trim()) {
    passeStore.renamePasse(passeId, passeRenameValue.value.trim());
  }
  renamingPasseId.value = null;
};

const passeRangeGroups = (passe) => {
  const map = new Map();
  for (const seg of passe.serien) {
    const key = seg.rangeId ?? '__none__';
    if (!map.has(key)) {
      map.set(key, { rangeId: seg.rangeId, rangeName: seg.rangeName, serien: [] });
    }
    map.get(key).serien.push(seg);
  }
  return Array.from(map.values());
};

// ── Create passe flow ──────────────────────────────────────────────────────
const creatingPasse = ref(false);
const newPasseName = ref('');
const selectedSerieIds = ref(new Set());

const startCreatePasse = () => {
  creatingPasse.value = true;
  selectedSerieIds.value = new Set();
  newPasseName.value = '';
};

const cancelCreatePasse = () => {
  creatingPasse.value = false;
  selectedSerieIds.value = new Set();
};

const toggleSerieSelection = (segId) => {
  const next = new Set(selectedSerieIds.value);
  if (next.has(segId)) next.delete(segId);
  else next.add(segId);
  selectedSerieIds.value = next;
};

// Alle Serien (user + range) die ausgewählt sind
const selectedSerien = computed(() =>
  passeStore.savedSerien.filter((s) => selectedSerieIds.value.has(s.id)),
);

const selectedRangeGroups = computed(() => {
  const map = new Map();
  for (const seg of selectedSerien.value) {
    const key = seg.rangeId ?? '__none__';
    if (!map.has(key)) {
      map.set(key, { rangeId: seg.rangeId, rangeName: seg.rangeName, serien: [] });
    }
    map.get(key).serien.push(seg);
  }
  return Array.from(map.values());
});

const confirmCreatePasse = () => {
  if (selectedSerieIds.value.size === 0) return;
  passeStore.createPasse(newPasseName.value, selectedSerien.value);
  creatingPasse.value = false;
  selectedSerieIds.value = new Set();
  newPasseName.value = '';
};

// ── Helpers ────────────────────────────────────────────────────────────────
const stepCount = (steps) => {
  let count = 0;
  for (const s of steps) {
    if (s.type === 'solo') count += 1;
    else if (s.type === 'pair' || s.type === 'a_schuss') count += 2;
    else if (s.type === 'raffale') count += 2;
  }
  return count;
};

const totalThrows = (serien) => serien.reduce((sum, s) => sum + stepCount(s.steps), 0);

const stepTypeLabel = (type) => stepModeLabel(type);

// ── Active programs actions ───────────────────────────────────────
const resumeSession = (session) => {
  if (playSessionStore.resumeSession(session.sessionId)) {
    router.push(`/remote/${session.rangeId}/play`);
  }
};

// ── Completed card expand/collapse ───────────────────────────────────────────
const expandedCards = ref(new Set());
const toggleCompletedCard = (instanceId) => {
  const s = new Set(expandedCards.value);
  if (s.has(instanceId)) s.delete(instanceId);
  else s.add(instanceId);
  expandedCards.value = s;
};

const getPlayerSummaries = (inst) => inst.players.map((player) => {
  let totalPts = 0, maxPts = 0;
  for (const block of inst.blocks) {
    const pr = block.result?.playerResults?.find((r) => r.playerId === player.id);
    if (!pr) continue;
    totalPts += pr.totalPoints;
    maxPts += pr.maxPoints;
  }
  return { player, totalPts, maxPts, hitPct: maxPts > 0 ? Math.round(totalPts / maxPts * 100) : 0 };
});

const getBlocksForPlayer = (inst, playerId) => inst.blocks
  .map((block) => ({ block, pr: block.result?.playerResults?.find((r) => r.playerId === playerId) }))
  .filter(({ pr }) => pr != null);

const stopInstanceConfirm = (inst) => {
  if (confirm(`Möchtest du „${inst.templateName}" wirklich abbrechen?`)) {
    activePasseStore.stopInstance(inst.instanceId);
  }
};

const startRenameSession = (session) => {
  renamingSessionId.value = session.sessionId;
  sessionRenameValue.value = session.passeName;
  nextTick(() => sessionRenameInputRef.value?.focus());
};

const confirmRenameSession = (sessionId) => {
  const session = playSessionStore.activeSessions.find((s) => s.sessionId === sessionId);
  if (session && sessionRenameValue.value.trim()) {
    session.passeName = sessionRenameValue.value.trim();
    playSessionStore.saveSessions();
  }
  renamingSessionId.value = null;
};
</script>

<style scoped>
.passen-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--sg-brand);
  color: var(--sg-text-primary);
}

/* ── Top bar ─────────────────────────────────────── */
.top-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.back-btn {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--sg-border);
  border-radius: 10px;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s;
  flex-shrink: 0;
}

.back-btn:hover { background: rgba(255, 255, 255, 0.1); }

.page-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--sg-text-primary);
  margin: 0;
  letter-spacing: -0.3px;
}

.top-bar-spacer { flex: 1; }

/* ── Content ─────────────────────────────────────── */
.content {
  flex: 1;
  overflow-y: auto;
  padding: 0 0 40px;
}

/* ── Block ───────────────────────────────────────── */
.block {
  padding: 20px 16px 0;
}

.block + .block {
  margin-top: 32px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  padding-top: 24px;
}

.block-header {
  margin-bottom: 16px;
}

.block-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.block-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--sg-text-primary);
  letter-spacing: -0.3px;
}

.block-badge {
  font-size: 11px;
  font-weight: 700;
  color: var(--sg-text-faint);
  background: rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  padding: 2px 8px;
}

.block-desc {
  font-size: 12px;
  color: var(--sg-text-disabled);
  margin: 0;
  line-height: 1.5;
}

/* ── Range group ─────────────────────────────────── */
.range-group {
  margin-bottom: 14px;
}

.range-group-label {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 600;
  color: var(--sg-text-disabled);
  text-transform: uppercase;
  letter-spacing: 0.6px;
  margin-bottom: 8px;
}

.range-group-count {
  font-size: 10px;
  font-weight: 700;
  color: var(--sg-text-disabled);
  background: rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  padding: 1px 6px;
}

.ownership-chip {
  font-size: 10px;
  font-weight: 700;
  color: var(--sg-accent-subtle);
  background: var(--sg-accent-tint);
  border: 1px solid color-mix(in srgb, var(--sg-accent) 20%, transparent);
  border-radius: 20px;
  padding: 1px 6px;
  flex-shrink: 0;
}

/* ── Range group header ──────────────────────────── */
.range-group-header {
  background: none;
  border: none;
  padding: 0;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 600;
  color: var(--sg-text-disabled);
  text-transform: uppercase;
  letter-spacing: 0.6px;
  cursor: pointer;
  transition: color 0.15s;
}

.range-group-header:hover {
  color: var(--sg-text-faint);
}

.range-group-chevron {
  transition: transform 0.2s;
}

.range-group-chevron.rotated {
  transform: rotate(90deg);
}

/* ── Serie card ─────────────────────────────────── */
.segment-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.segment-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--sg-border);
  border-radius: 14px;
  padding: 12px 14px;
  transition: border-color 0.15s, background 0.15s;
}

.segment-card.is-selected {
  background: var(--sg-accent-subtle);
  border-color: color-mix(in srgb, var(--sg-accent) 40%, transparent);
}

.seg-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.seg-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.seg-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--sg-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.seg-meta {
  font-size: 11px;
  color: var(--sg-text-disabled);
}

.seg-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.icon-btn {
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  transition: background 0.15s;
}

.icon-btn:hover { background: rgba(255, 255, 255, 0.07); }
.icon-btn--danger:hover { background: rgba(252, 129, 129, 0.1); }
.icon-btn--confirm:hover { background: rgba(72, 187, 120, 0.1); }

.seg-check { flex-shrink: 0; }

.check-circle {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 2px solid var(--sg-border-input);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.check-circle.active {
  background: var(--sg-accent-subtle);
  border-color: var(--sg-accent);
}

.seg-steps-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 10px;
}

.step-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
}


.step-dot-more {
  font-size: 10px;
  color: var(--sg-text-disabled);
  align-self: center;
}

.rename-input {
  width: 100%;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid color-mix(in srgb, var(--sg-accent) 40%, transparent);
  border-radius: 8px;
  color: var(--sg-text-primary);
  font-size: 14px;
  font-family: inherit;
  padding: 4px 8px;
  outline: none;
}

/* ── Empty states ────────────────────────────────── */
.empty-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 16px;
  text-align: center;
}

.empty-block p {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.2);
  margin: 0;
}

.empty-hint {
  font-size: 12px !important;
  color: rgba(255, 255, 255, 0.12) !important;
  line-height: 1.5;
}

/* ── Program card ────────────────────────────────── */
.program-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 14px;
}

.program-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--sg-border);
  border-radius: 16px;
  overflow: hidden;
  transition: border-color 0.15s;
}

.program-card.expanded {
  border-color: color-mix(in srgb, var(--sg-accent) 25%, transparent);
}

.prog-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 16px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.prog-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.prog-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--sg-text-primary);
}

.prog-meta-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.prog-meta-item {
  font-size: 12px;
  color: var(--sg-text-disabled);
}

.prog-meta-dot {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.15);
}

.prog-actions-inline {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.expand-icon {
  flex-shrink: 0;
  transition: transform 0.2s;
}

.expand-icon.rotated {
  transform: rotate(90deg);
}

/* ── Program detail (expanded) ───────────────────── */
.prog-detail {
  padding: 0 16px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.prog-range-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 12px;
}

.prog-range-label {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
  font-weight: 600;
  color: var(--sg-text-disabled);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.prog-seg-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.prog-seg-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid var(--sg-border);
  border-radius: 20px;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--sg-text-muted);
}

.prog-seg-chip--selected {
  background: var(--sg-accent-tint);
  border-color: color-mix(in srgb, var(--sg-accent) 30%, transparent);
  color: var(--sg-accent);
}

.chip-throws {
  font-size: 10px;
  color: var(--sg-text-disabled);
}

.prog-seg-chip--selected .chip-throws {
  color: color-mix(in srgb, var(--sg-accent) 50%, transparent);
}

.chip-remove {
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 0;
  opacity: 0.6;
  transition: opacity 0.15s;
}

.chip-remove:hover { opacity: 1; }

.prog-actions {
  display: flex;
  gap: 8px;
}

/* ── Action buttons ──────────────────────────────── */
.action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s, opacity 0.15s;
  border: 1px solid transparent;
}

.action-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.action-btn--start {
  background: var(--sg-accent-tint);
  border-color: color-mix(in srgb, var(--sg-accent) 30%, transparent);
  color: var(--sg-accent);
}

.action-btn--start:hover:not(:disabled) {
  background: color-mix(in srgb, var(--sg-accent) 20%, transparent);
}

.action-btn--danger {
  background: rgba(252, 129, 129, 0.08);
  border-color: rgba(252, 129, 129, 0.2);
  color: rgba(252, 129, 129, 0.7);
  flex: none;
  padding: 10px 14px;
}

.action-btn--danger:hover {
  background: rgba(252, 129, 129, 0.14);
}

.action-btn--cancel {
  background: transparent;
  border-color: var(--sg-border);
  color: var(--sg-text-faint);
}

.action-btn--cancel:hover { background: rgba(255, 255, 255, 0.05); }

.action-btn--create {
  background: color-mix(in srgb, var(--sg-accent) 20%, transparent);
  border-color: color-mix(in srgb, var(--sg-accent) 40%, transparent);
  color: var(--sg-accent);
}

.action-btn--create:hover:not(:disabled) {
  background: color-mix(in srgb, var(--sg-accent) 28%, transparent);
}

/* ── New passe button ────────────────────────────── */
.new-program-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 13px;
  background: transparent;
  border: 1.5px dashed color-mix(in srgb, var(--sg-accent) 25%, transparent);
  border-radius: 14px;
  color: color-mix(in srgb, var(--sg-accent) 60%, transparent);
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
  margin-top: 4px;
}

.new-program-btn:hover:not(:disabled) {
  background: var(--sg-accent-subtle);
  border-color: color-mix(in srgb, var(--sg-accent) 40%, transparent);
  color: var(--sg-accent-subtle);
}

.new-program-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

/* ── Create passe panel ──────────────────────────── */
.create-program-panel {
  background: var(--sg-accent-subtle);
  border: 1px solid color-mix(in srgb, var(--sg-accent) 20%, transparent);
  border-radius: 16px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 4px;
}

.create-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.create-panel-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--sg-accent-subtle);
}

.create-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.create-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--sg-text-disabled);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.create-label-count {
  background: var(--sg-accent-tint);
  color: var(--sg-accent-subtle);
  border-radius: 12px;
  padding: 2px 8px;
  font-size: 10px;
  font-weight: 700;
}

.create-input {
  width: 100%;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--sg-border);
  border-radius: 10px;
  color: var(--sg-text-primary);
  font-size: 14px;
  font-family: inherit;
  padding: 10px 12px;
  outline: none;
  transition: border-color 0.15s;
}

.create-input:focus {
  border-color: color-mix(in srgb, var(--sg-accent) 30%, transparent);
}

.create-no-serien,
.create-hint {
  font-size: 12px;
  color: var(--sg-text-disabled);
  margin: 0;
  line-height: 1.5;
}

.create-preview {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  background: var(--sg-accent-subtle);
  border: 1px solid var(--sg-accent-tint);
  border-radius: 12px;
}

.create-actions {
  display: flex;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--sg-accent-tint);
}

/* ── Tab toggle ──────────────────────────────── */
.tab-toggle {
  display: flex;
  gap: 8px;
  padding: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.tab-btn {
  flex: 1;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--sg-border);
  border-radius: 10px;
  color: var(--sg-text-faint);
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
}

.tab-btn.active {
  background: var(--sg-accent-tint);
  border-color: color-mix(in srgb, var(--sg-accent) 40%, transparent);
  color: var(--sg-accent);
}

.tab-btn:hover:not(.active) {
  background: rgba(255, 255, 255, 0.07);
  color: var(--sg-text-muted);
}

/* ── Active sessions list ───────────────────── */
.active-sessions-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 14px;
}

.session-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--sg-border);
  border-radius: 16px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: border-color 0.15s;
}

.session-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.session-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.session-program {
  font-size: 15px;
  font-weight: 600;
  color: var(--sg-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-range {
  font-size: 12px;
  color: var(--sg-text-disabled);
}

.progress-bar-container {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, color-mix(in srgb, var(--sg-accent) 60%, transparent), var(--sg-accent-subtle));
  width: 0;
  transition: width 0.3s ease;
}

.progress-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--sg-accent-subtle);
  min-width: 35px;
  text-align: right;
}

/* ── Start modal ──────────────────────────────────── */
.start-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: 24px;
}

.start-modal {
  background: rgba(24, 24, 40, 0.98);
  border: 1.5px solid var(--sg-border);
  border-radius: 20px;
  padding: 24px;
  width: 100%;
  max-width: 340px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.start-modal-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--sg-text-primary);
  margin: 0;
  text-align: center;
}

.start-modal-players {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.start-player-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.start-player-num {
  font-size: 13px;
  font-weight: 700;
  color: color-mix(in srgb, var(--sg-accent) 70%, transparent);
  min-width: 20px;
}

.start-player-input {
  flex: 1;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--sg-border);
  border-radius: 8px;
  color: var(--sg-text-primary);
  font-size: 13px;
  font-family: inherit;
  padding: 8px 10px;
  outline: none;
}

.start-player-input:focus {
  border-color: color-mix(in srgb, var(--sg-accent) 30%, transparent);
}

.add-player-btn {
  background: transparent;
  border: 1px dashed var(--sg-border-input);
  border-radius: 8px;
  color: var(--sg-text-faint);
  font-size: 12px;
  font-family: inherit;
  padding: 8px;
  cursor: pointer;
  transition: all 0.15s;
}

.add-player-btn:hover {
  background: rgba(255, 255, 255, 0.04);
  color: var(--sg-text-muted);
}

.start-modal-actions {
  display: flex;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

/* ── Block status list ───────────────────────────── */
.block-status-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 4px 0;
}

.block-status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.block-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.block-status-dot.dot-pending { background: rgba(255, 255, 255, 0.2); }
.block-status-dot.dot-in_progress { background: rgba(246, 173, 85, 0.8); }
.block-status-dot.dot-done { background: rgba(72, 187, 120, 0.7); }

.block-status-range {
  color: var(--sg-text-disabled);
  min-width: 60px;
}

.block-status-name {
  color: var(--sg-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── Completed card ──────────────────────────────── */
.completed-card {
  cursor: pointer;
  user-select: none;
}

.total-wuerfe {
  font-size: 12px;
  font-weight: 600;
  color: var(--sg-text-disabled);
  white-space: nowrap;
  flex-shrink: 0;
}

.player-summaries {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-top: 6px;
}

.player-summary-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.ps-name {
  color: var(--sg-text-muted);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ps-pts {
  font-weight: 700;
  color: var(--sg-accent-subtle);
}

.ps-pct {
  font-size: 11px;
  color: var(--sg-text-disabled);
  min-width: 36px;
  text-align: right;
}

.completed-detail {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.player-detail-section {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.player-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 4px;
  margin-bottom: 2px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.pd-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--sg-text-muted);
}

.pd-total {
  font-size: 12px;
  font-weight: 700;
  color: var(--sg-accent-subtle);
}

.completed-score-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  padding: 2px 0;
}

.completed-score-block {
  flex: 1;
  color: var(--sg-text-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.completed-score-range {
  color: var(--sg-text-disabled);
  margin-right: 4px;
}

.completed-score-pts {
  font-weight: 600;
  color: var(--sg-text-faint);
  white-space: nowrap;
}
</style>
