<template>
  <div
    class="shooter-remote"
    :class="[
      `mode--${store.mode}`,
      {
        'session--erfassen':      store.sessionMode === 'recording',
        'session--verzoegert':    store.sessionMode === 'delayed',
        'session--rufausloesung': store.sessionMode === 'rufausloesung',
      },
    ]"
  >
    <!-- Header: [← Back + range name (tablet)] [Lock | Notfall] [Mode Badge] -->
    <div class="page-header">
      <!-- Left: back + range name on tablet -->
      <div class="header-left">
        <button v-if="!auth.profile?.assignedRangeId" class="back-btn" @click="goBack">
          <Icons icon="chevronLeft" :size="20" color="rgba(255,255,255,0.8)" />
        </button>
        <span v-if="range?.name" class="range-name">{{ range.name }}</span>
        <span v-if="rotteName" class="rotte-badge">{{ rotteName }}</span>
      </div>

      <!-- Center: [Verzögerung] Lock + Notfall (truly centered) -->
      <div class="header-center">
        <!-- Rufauslösung: mic-config button -->
        <button
          v-if="store.sessionMode === 'rufausloesung'"
          class="ruf-btn"
          title="Empfindlichkeit einstellen"
          @click="rufModalOpen = true"
        >
          <Icons icon="mic" :size="14" />
          <span>{{ store.rufPeak }}</span>
        </button>
        <!-- Verzögert: delay-timer button (left of lock, in view-mode color) -->
        <button
          v-if="store.sessionMode === 'delayed'"
          class="delay-btn"
          :class="{ 'is-counting': queuedIds.length > 0 }"
          title="Verzögerung einstellen"
          @click="delayModalOpen = true"
        >
          <Icons icon="clock" :size="14" />
          <span>{{ store.delaySeconds }}s</span>
        </button>
        <button class="icon-btn" title="Stand reservieren (Mock)">
          <Icons icon="lock" :size="15" />
        </button>
        <button
          class="emergency-btn"
          :class="{ 'is-locked': isLocked }"
          :title="isLocked ? 'Notfallsperrung aktiv – Klick zum Freigeben' : 'Klick zum Notfall Stop'"
          @click="toggleBlock"
        >
          <Icons :icon="isLocked ? 'unlock' : 'alert'" :size="15" />
          <span>{{ isLocked ? 'Freigeben' : 'Notfall' }}</span>
        </button>
      </div>

      <!-- Right: Mode badge — main indicator, opens mode flyout -->
      <div class="header-right">
        <button
          class="mode-badge-btn"
          :class="modeBadgeClass"
          :aria-expanded="modeDrawerOpen"
          @click="modeDrawerOpen = !modeDrawerOpen"
        >
          <span class="mode-dot" />
          <span class="mode-label">{{ modeBadgeLabel }}</span>
        </button>
      </div>
    </div>

    <!-- Mode flyout (slides down from header) -->
    <Transition name="mode-flyout">
      <div v-if="modeDrawerOpen" class="mode-flyout">
        <button
          class="mode-option"
          :class="{ 'is-active': store.sessionMode === 'throwing' }"
          @click="setSessionMode('throwing')"
        >
          <span class="option-dot option-dot--normal" />
          <span class="option-name">Schiessen</span>
          <span v-if="store.sessionMode === 'throwing'" class="option-tag option-tag--normal">Aktiv</span>
        </button>
        <button
          class="mode-option"
          :class="{ 'is-active': store.sessionMode === 'recording' }"
          @click="setSessionMode('recording')"
        >
          <span class="option-dot option-dot--erfassen" />
          <span class="option-name">Erfassen</span>
          <span v-if="store.sessionMode === 'recording'" class="option-tag option-tag--erfassen">Aktiv</span>
        </button>
        <button
          class="mode-option"
          :class="{ 'is-active': store.sessionMode === 'delayed' }"
          @click="setSessionMode('delayed')"
        >
          <span class="option-dot option-dot--verzoegert" />
          <span class="option-name">Verzögert</span>
          <span v-if="store.sessionMode === 'delayed'" class="option-tag option-tag--verzoegert">Aktiv</span>
        </button>
        <button
          class="mode-option"
          :class="{ 'is-active': store.sessionMode === 'rufausloesung' }"
          @click="setSessionMode('rufausloesung')"
        >
          <span class="option-dot option-dot--rufausloesung" />
          <span class="option-name">Rufauslösung</span>
          <span v-if="store.sessionMode === 'rufausloesung'" class="option-tag option-tag--rufausloesung">Aktiv</span>
        </button>
      </div>
    </Transition>
    <div v-if="modeDrawerOpen" class="mode-flyout-backdrop" @click="modeDrawerOpen = false" />

    <!-- Device section -->
    <div class="device-section" :class="{ 'is-recording': isRecordingActive }">

      <div v-if="positionsLoading" class="state-center">
        <p class="state-text">Lade Positionen…</p>
      </div>

      <div v-else-if="rangePositions.length === 0" class="state-center">
        <Icons icon="bolt" :size="44" color="rgba(255,255,255,0.1)" />
        <p class="state-text">Keine Positionen konfiguriert</p>
        <p class="state-hint">Bitte einen Administrator kontaktieren.</p>
      </div>

      <div v-else class="device-grid" :class="`device-grid--${store.mode}`">
        <button
          v-for="(position, idx) in rangePositions"
          :key="position.id"
          class="device-btn"
          :class="positionBtnClass(position)"
          :disabled="isPositionDisabled(position)"
          @click="handlePositionTap(position)"
        >
          <div class="btn-glow" />
          <div class="btn-icon-wrap">
            <!-- Verzögert: countdown ring + remaining seconds over the queued position -->
            <div v-if="isQueued(position)" class="btn-countdown-ring" :style="countdownRingStyle" />
            <span v-if="isQueued(position)" class="btn-countdown-num">{{ countdownLabel }}</span>
            <div
              v-else-if="rufArmedIds.includes(position.id) && rufPhase === 'totzeit'"
              class="btn-countdown-ring"
              :style="rufCountdownRingStyle"
            />
            <span
              v-else-if="rufArmedIds.includes(position.id) && rufPhase === 'totzeit'"
              class="btn-countdown-num btn-countdown-num--ruf"
            >{{ rufCountdownLabel }}</span>
            <span v-else class="btn-letter" :style="{ color: iconColor(position) }">
              {{ position.label }}
            </span>
            <span class="btn-position-num">{{ idx + 1 }}</span>
          </div>
          <span class="btn-status-chip" :class="chipClass(position)">{{ chipLabel(position) }}</span>
        </button>
      </div>
    </div>

    <!-- Mode selector (fixed bottom) -->
    <div class="solo-pair-bar">
      <div class="solo-pair-toggle">
        <button
          class="toggle-btn"
          :class="{ active: store.mode === 'solo' }"
          @click="onModeButtonTap('solo')"
        >
          Solo
        </button>
        <button
          class="toggle-btn"
          :class="{ active: store.mode === 'pair' }"
          @click="onModeButtonTap('pair')"
        >
          Pair
        </button>
        <!-- Extra options in Erfassungs mode -->
        <template v-if="store.sessionMode === 'recording'">
          <button
            class="toggle-btn"
            :class="{ active: store.mode === 'a_schuss' }"
            @click="store.setMode('a_schuss')"
          >
            a. Schuss
          </button>
          <button
            class="toggle-btn"
            :class="{ active: store.mode === 'raffale' }"
            @click="store.setMode('raffale')"
          >
            Raffale
          </button>
        </template>
      </div>
    </div>

    <!-- Flyout panel -->
    <ShooterFlyoutPanel />

    <!-- Verzögert: delay-config modal (slider 1–10 s) -->
    <Transition name="delay-modal">
      <div v-if="delayModalOpen" class="delay-modal-backdrop" @click.self="delayModalOpen = false">
        <div class="delay-modal" role="dialog" aria-modal="true" aria-labelledby="delay-modal-title">
          <div class="delay-modal-head">
            <h2 id="delay-modal-title" class="delay-modal-title">Verzögerung</h2>
            <button class="delay-modal-close" title="Schliessen" @click="delayModalOpen = false">
              <Icons icon="x" :size="14" />
            </button>
          </div>
          <p class="delay-modal-hint">Zeit zwischen Tastendruck und Auslösung des Werfers.</p>
          <div class="delay-value">{{ draftDelay }}<span class="delay-value-unit">s</span></div>
          <input
            v-model.number="draftDelay"
            class="delay-slider"
            type="range"
            min="1"
            max="10"
            step="1"
            aria-label="Verzögerung in Sekunden"
          />
          <div class="delay-scale">
            <span>1s</span>
            <span>10s</span>
          </div>
          <button class="delay-save-btn" @click="saveDelay">Speichern</button>
        </div>
      </div>
    </Transition>

    <!-- Rufauslösung: config modal -->
    <Transition name="delay-modal">
      <div v-if="rufModalOpen" class="delay-modal-backdrop" @click.self="rufModalOpen = false">
        <div class="ruf-modal" role="dialog" aria-modal="true" aria-labelledby="ruf-modal-title">
          <div class="delay-modal-head">
            <h2 id="ruf-modal-title" class="delay-modal-title">Rufauslösung</h2>
            <button class="delay-modal-close" title="Schliessen" @click="rufModalOpen = false">
              <Icons icon="x" :size="14" />
            </button>
          </div>

          <!-- Empfindlichkeit (Peak) -->
          <div class="ruf-slider-group">
            <div class="ruf-slider-header">
              <span class="ruf-slider-label">Empfindlichkeit</span>
              <span class="ruf-slider-value">{{ draftRufPeak }}</span>
            </div>
            <input
              v-model.number="draftRufPeak"
              class="ruf-slider"
              type="range"
              min="0"
              max="100"
              step="1"
              aria-label="Empfindlichkeit (Peak)"
            />
            <div class="ruf-slider-scale"><span>Leise</span><span>Laut</span></div>
          </div>

          <!-- Haltedauer -->
          <div class="ruf-slider-group">
            <div class="ruf-slider-header">
              <span class="ruf-slider-label">Haltedauer</span>
              <span class="ruf-slider-value">{{ draftRufDauer }} ms</span>
            </div>
            <input
              v-model.number="draftRufDauer"
              class="ruf-slider"
              type="range"
              min="50"
              max="500"
              step="10"
              aria-label="Haltedauer in Millisekunden"
            />
            <div class="ruf-slider-scale"><span>50 ms</span><span>500 ms</span></div>
          </div>

          <!-- Totzeit -->
          <div class="ruf-slider-group">
            <div class="ruf-slider-header">
              <span class="ruf-slider-label">Totzeit</span>
              <span class="ruf-slider-value">{{ draftRufTotzeit.toFixed(1) }} s</span>
            </div>
            <input
              v-model.number="draftRufTotzeit"
              class="ruf-slider"
              type="range"
              min="0"
              max="8"
              step="0.5"
              aria-label="Totzeit in Sekunden"
            />
            <div class="ruf-slider-scale"><span>0 s</span><span>8 s</span></div>
          </div>

          <!-- Live level bar -->
          <div class="ruf-level-wrap" aria-label="Mikrofon-Pegel">
            <div v-if="micDenied" class="ruf-denied">
              Mikrofon-Zugriff verweigert — bitte in den Browser-Einstellungen freigeben.
            </div>
            <template v-else>
              <div class="ruf-level-bar-track">
                <div
                  class="ruf-level-bar-fill"
                  :class="{ 'ruf-level--trigger': wouldTrigger }"
                  :style="{ width: `${micLevel}%` }"
                />
                <div class="ruf-level-threshold" :style="{ left: `${draftRufPeak}%` }" />
              </div>
              <div class="ruf-level-hint">{{ wouldTrigger ? 'Würde auslösen' : 'Kein Auslösen' }}</div>
            </template>
          </div>

          <button class="ruf-save-btn" @click="saveRuf">Speichern</button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useRangeStore } from '@/stores/rangeStore.js';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import { useActivePasseStore } from '@/stores/activePasseStore.js';
import { usePasseStore } from '@/stores/passeStore.js';
import { sendPositionCommand } from '@/services/rangePositionApi.js';
import { useAuthStore } from '@/stores/authStore.js';
import { fetchRange } from '@/services/rangeApi.js';
import Icons from '@/components/Icons.vue';
import ShooterFlyoutPanel from '@/components/shooter-remote/ShooterFlyoutPanel.vue';
import { useVoiceTrigger } from '@/composables/useVoiceTrigger.js';

const props = defineProps({ rangeId: { type: String, required: true } });

const router = useRouter();
const route = useRoute();
const rangeStore = useRangeStore();
const store = useShooterRemoteStore();
const auth = useAuthStore();
const passeStore = usePasseStore();
const activePasseStore = useActivePasseStore();

const { startListening, stopListening, micLevel, wouldTrigger, micDenied } = useVoiceTrigger(store);

// Rufauslösung: arming state
const rufArmedIds  = ref([]);   // position ids currently armed
const rufPhase     = ref(null); // 'totzeit' | 'listening' | 'waiting-pair' | null

// Totzeit countdown (for the ring animation — mirrors the delay countdown)
const rufTotzeitTotalMs     = ref(0);
const rufTotzeitRemainingMs = ref(0);
let rufTotzeitInterval = null;
let rufTotzeitTimeout  = null;

const clearRufTimers = () => {
  if (rufTotzeitInterval) { clearInterval(rufTotzeitInterval); rufTotzeitInterval = null; }
  if (rufTotzeitTimeout)  { clearTimeout(rufTotzeitTimeout);  rufTotzeitTimeout  = null; }
};

const cancelRuf = () => {
  clearRufTimers();
  stopListening();
  rufArmedIds.value = [];
  rufPhase.value    = null;
  rufTotzeitRemainingMs.value = 0;
  rufTotzeitTotalMs.value     = 0;
};

const handleRufTap = (position) => {
  // Re-tapping an armed position aborts
  if (rufArmedIds.value.includes(position.id)) {
    cancelRuf();
    return;
  }

  if (store.mode === 'pair') {
    if (rufArmedIds.value.length === 0) {
      rufArmedIds.value = [position.id];
      rufPhase.value    = 'waiting-pair';
      return;
    }
    const ids = [...rufArmedIds.value, position.id];
    armPositions(ids);
    return;
  }

  // Solo
  armPositions([position.id]);
};

const armPositions = (ids) => {
  rufArmedIds.value = ids;
  const totzeitMs   = store.rufTotzeit;

  if (totzeitMs > 0) {
    rufPhase.value              = 'totzeit';
    rufTotzeitTotalMs.value     = totzeitMs;
    rufTotzeitRemainingMs.value = totzeitMs;
    const startedAt = Date.now();

    rufTotzeitInterval = setInterval(() => {
      rufTotzeitRemainingMs.value = Math.max(0, totzeitMs - (Date.now() - startedAt));
    }, 50);

    rufTotzeitTimeout = setTimeout(() => {
      clearRufTimers();
      rufTotzeitRemainingMs.value = 0;
      rufTotzeitTotalMs.value     = 0;
      beginListening(ids);
    }, totzeitMs);
  } else {
    beginListening(ids);
  }
};

const beginListening = (ids) => {
  rufPhase.value = 'listening';
  startListening(() => {
    if (rufPhase.value !== 'listening') return;
    if (ids.length === 1) {
      fireSinglePosition(ids[0]);
    } else {
      firePairPositions(ids[0], ids[1]);
    }
    rufArmedIds.value = [];
    rufPhase.value    = null;
  });
};

const onModeButtonTap = (newMode) => {
  if (store.sessionMode === 'rufausloesung' && rufArmedIds.value.length > 0) {
    cancelRuf();
  }
  store.setMode(newMode);
};

// Optional competition context query params
const rotteId = computed(() => route.query.rotteId ?? null);
const instanceId = computed(() => route.query.instanceId ?? null);

// Derive the rotte's display name from the active instance when context is present
const rotteName = computed(() => {
  if (!rotteId.value || !instanceId.value) return null;
  const inst = activePasseStore.activeInstances.find((i) => i.instanceId === instanceId.value);
  return inst?.rotten?.find((r) => r.rotteId === rotteId.value)?.name ?? null;
});

const positionsLoading = ref(false);

onMounted(async () => {
  // Redirect if this range is assigned to a different user
  const rangeData = await fetchRange(props.rangeId);
  if (rangeData.assignedUserId && rangeData.assignedUserId !== auth.profile?.id) {
    router.replace('/remote');
    return;
  }

  store.ensureReserved(props.rangeId);
  store.setMode('solo');
  // Store competition context so ShooterFlyoutPanel can access it
  store.setCompetitionContext(instanceId.value, rotteId.value);
  // Only fetch from API if positions are not already cached for this range
  if (!rangeStore.positions[props.rangeId]) {
    positionsLoading.value = true;
    try {
      await rangeStore.loadPositions(props.rangeId);
    } finally {
      positionsLoading.value = false;
    }
  }
});

onUnmounted(() => {
  store.releasePlatz();
  store.setCompetitionContext(null, null);
  cancelQueue();
  cancelRuf();
});

watch(() => store.sessionMode, () => {
  store.setMode('solo');
  modeDrawerOpen.value = false;
  cancelQueue();
  cancelRuf();
});

watch(() => store.mode, () => {
  store.throwPairPending = null;
});

// ── Mode flyout ────────────────────────────────────
const modeDrawerOpen = ref(false);

const setSessionMode = (mode) => {
  store.setSessionMode(mode);
  modeDrawerOpen.value = false;
};

const isRecordingActive = computed(
  () => store.sessionMode === 'recording' && store.recordingActive && passeStore.passeMode,
);

// ── Mode badge ─────────────────────────────────────
const modeBadgeLabel = computed(() => {
  if (store.sessionMode === 'recording')     return 'Erfassen';
  if (store.sessionMode === 'delayed')       return 'Verzögert';
  if (store.sessionMode === 'rufausloesung') return 'Rufauslösung';
  return 'Schiessen';
})
const modeBadgeClass = computed(() => ({
  'mode-badge--recording':     store.sessionMode === 'recording',
  'mode-badge--delayed':       store.sessionMode === 'delayed',
  'mode-badge--rufausloesung': store.sessionMode === 'rufausloesung',
}))

// ── Range & positions ──────────────────────────────
const range = computed(() => rangeStore.ranges.find((r) => r.id === props.rangeId));
const isLocked = computed(() => range.value?.locked ?? false);
const rangePositions = computed(() => rangeStore.positions[props.rangeId] ?? []);

// ── Navigation & lock ──────────────────────────────
const goBack = () => {
  store.releasePlatz();
  router.push('/remote');
};

const toggleBlock = async () => {
  if (!range.value) return;
  await rangeStore.setLocked(range.value.id, !isLocked.value);
};

// ── Fire state ─────────────────────────────────────
const firingIds = ref(new Set());
const firedIds = ref(new Set());
const errorIds = ref(new Set());

// ── Verzögert (delayed mode) ───────────────────────
// Delay-config modal.
const delayModalOpen = ref(false);
const draftDelay = ref(store.delaySeconds);

watch(delayModalOpen, (open) => {
  if (open) draftDelay.value = store.delaySeconds;
});

const saveDelay = () => {
  store.setDelaySeconds(draftDelay.value);
  delayModalOpen.value = false;
};

// Rufauslösung: config modal state
const rufModalOpen    = ref(false);
const draftRufPeak    = ref(store.rufPeak);
const draftRufDauer   = ref(store.rufDauer);
const draftRufTotzeit = ref(store.rufTotzeit / 1000); // display in seconds

watch(rufModalOpen, async (open) => {
  if (open) {
    draftRufPeak.value    = store.rufPeak;
    draftRufDauer.value   = store.rufDauer;
    draftRufTotzeit.value = store.rufTotzeit / 1000;
    await startListening(() => { /* preview only — no fire */ }, { totzeit: 0 });
  } else {
    stopListening();
  }
});

const saveRuf = () => {
  store.setRufPeak(draftRufPeak.value);
  store.setRufDauer(draftRufDauer.value);
  store.setRufTotzeit(Math.round(draftRufTotzeit.value * 1000));
  rufModalOpen.value = false;
};

// Command queue — only ONE command may be queued at a time. While the countdown
// runs, every other button is locked; the queued button(s) stay tappable to abort.
const queuedIds = ref([]);
const queueTotalMs = ref(0);
const queueRemainingMs = ref(0);
let queueTimeout = null;
let queueInterval = null;

const clearQueueTimers = () => {
  if (queueTimeout) { clearTimeout(queueTimeout); queueTimeout = null; }
  if (queueInterval) { clearInterval(queueInterval); queueInterval = null; }
};

const cancelQueue = () => {
  clearQueueTimers();
  queuedIds.value = [];
  queueRemainingMs.value = 0;
  queueTotalMs.value = 0;
};

const scheduleDelayedFire = (ids, fireFn) => {
  if (queuedIds.value.length) return; // one command at a time
  const totalMs = store.delaySeconds * 1000;
  const startedAt = Date.now();
  queuedIds.value = ids;
  queueTotalMs.value = totalMs;
  queueRemainingMs.value = totalMs;
  queueInterval = setInterval(() => {
    queueRemainingMs.value = Math.max(0, totalMs - (Date.now() - startedAt));
  }, 50);
  queueTimeout = setTimeout(async () => {
    clearQueueTimers();
    queuedIds.value = [];
    queueRemainingMs.value = 0;
    queueTotalMs.value = 0;
    await fireFn();
  }, totalMs);
};

const isQueued = (position) => queuedIds.value.includes(position.id);
const countdownLabel = computed(() => `${Math.ceil(queueRemainingMs.value / 1000)}s`);
const countdownRingStyle = computed(() => {
  const pct = queueTotalMs.value ? (queueRemainingMs.value / queueTotalMs.value) * 360 : 0;
  return {
    background: `conic-gradient(var(--delay-color, #EF9F27) ${pct}deg, rgba(255,255,255,0.12) ${pct}deg)`,
  };
});

const handleDelayedTap = (position) => {
  // A command is already queued: tapping the queued position aborts it; the
  // others are locked (isPositionDisabled) so they never reach here.
  if (queuedIds.value.length) {
    if (isQueued(position)) cancelQueue();
    return;
  }
  if (store.mode === 'pair' || store.mode === 'a_schuss') {
    if (!store.throwPairPending) {
      store.throwPairPending = { id: position.id, alias: position.device?.alias ?? position.label };
    } else if (store.throwPairPending.id === position.id) {
      store.throwPairPending = null;
    } else {
      const pendingId = store.throwPairPending.id;
      store.throwPairPending = null;
      scheduleDelayedFire([pendingId, position.id], () => firePairPositions(pendingId, position.id));
    }
    return;
  }
  scheduleDelayedFire([position.id], () => fireSinglePosition(position.id));
};

// Abort a pending countdown whenever delayed mode is left or the range is locked.
watch(isLocked, (locked) => {
  if (locked) {
    cancelQueue();
    cancelRuf();
  }
});

const handlePositionTap = async (position) => {
  if (isPositionDisabled(position)) return;

  if (store.sessionMode === 'rufausloesung') {
    handleRufTap(position);
    return;
  }

  if (store.sessionMode === 'delayed') {
    handleDelayedTap(position);
    return;
  }

  if (store.sessionMode === 'recording' && passeStore.passeMode) {
    passeStore.addStep(position.id, position, position.label);
    return;
  }

  if ((store.mode === 'pair' || store.mode === 'a_schuss') && !passeStore.passeMode) {
    if (!store.throwPairPending) {
      store.throwPairPending = { id: position.id, alias: position.device?.alias ?? position.label };
    } else if (store.throwPairPending.id === position.id) {
      store.throwPairPending = null;
    } else {
      const pendingId = store.throwPairPending.id;
      store.throwPairPending = null;
      await firePairPositions(pendingId, position.id);
      return;
    }
    return;
  }

  if (store.mode === 'raffale' && !passeStore.passeMode) {
    await fireRaffalePosition(position.id);
    return;
  }

  await fireSinglePosition(position.id);
};

const fireSinglePosition = async (positionId) => {
  if (firingIds.value.has(positionId)) return;
  firingIds.value = new Set([...firingIds.value, positionId]);
  firedIds.value.delete(positionId);
  errorIds.value.delete(positionId);
  try {
    await sendPositionCommand(props.rangeId, positionId);
    firedIds.value = new Set([...firedIds.value, positionId]);
    setTimeout(() => {
      firedIds.value = new Set([...firedIds.value].filter((id) => id !== positionId));
    }, 900);
  } catch {
    errorIds.value = new Set([...errorIds.value, positionId]);
    setTimeout(() => {
      errorIds.value = new Set([...errorIds.value].filter((id) => id !== positionId));
    }, 1500);
  } finally {
    firingIds.value = new Set([...firingIds.value].filter((id) => id !== positionId));
  }
};

const firePairPositions = async (posId1, posId2) => {
  if (firingIds.value.has(posId1) || firingIds.value.has(posId2)) return;
  firingIds.value = new Set([...firingIds.value, posId1, posId2]);
  firedIds.value.delete(posId1);
  firedIds.value.delete(posId2);
  errorIds.value.delete(posId1);
  errorIds.value.delete(posId2);
  try {
    await Promise.all([
      sendPositionCommand(props.rangeId, posId1),
      sendPositionCommand(props.rangeId, posId2),
    ]);
    firedIds.value = new Set([...firedIds.value, posId1, posId2]);
    setTimeout(() => {
      firedIds.value = new Set([...firedIds.value].filter((id) => id !== posId1 && id !== posId2));
    }, 900);
  } catch {
    errorIds.value = new Set([...errorIds.value, posId1, posId2]);
    setTimeout(() => {
      errorIds.value = new Set([...errorIds.value].filter((id) => id !== posId1 && id !== posId2));
    }, 1500);
  } finally {
    firingIds.value = new Set([...firingIds.value].filter((id) => id !== posId1 && id !== posId2));
  }
};

const fireRaffalePosition = async (positionId) => {
  if (firingIds.value.has(positionId)) return;
  firingIds.value = new Set([...firingIds.value, positionId]);
  firedIds.value.delete(positionId);
  errorIds.value.delete(positionId);
  try {
    await sendPositionCommand(props.rangeId, positionId);
    await new Promise(resolve => setTimeout(resolve, 2000));
    await sendPositionCommand(props.rangeId, positionId);
    firedIds.value = new Set([...firedIds.value, positionId]);
    setTimeout(() => {
      firedIds.value = new Set([...firedIds.value].filter((id) => id !== positionId));
    }, 900);
  } catch {
    errorIds.value = new Set([...errorIds.value, positionId]);
    setTimeout(() => {
      errorIds.value = new Set([...errorIds.value].filter((id) => id !== positionId));
    }, 1500);
  } finally {
    firingIds.value = new Set([...firingIds.value].filter((id) => id !== positionId));
  }
};

// ── Button helpers ─────────────────────────────────
const isPositionDisabled = (position) => {
  if (!position.device) return true;
  if (position.device.blocked || position.device.healthy === false) return true;
  if (isLocked.value) return true;
  if (!store.reservedByMe) return true;
  // Verzögert: while a command is queued, lock every button except the queued
  // one(s) — those stay tappable so the shooter can abort the countdown.
  if (queuedIds.value.length) return !isQueued(position);
  if (rufArmedIds.value.length > 0 && rufPhase.value !== 'waiting-pair') {
    return !rufArmedIds.value.includes(position.id);
  }
  if (firingIds.value.has(position.id)) return true;
  return false;
};

const isThrowPairPending = (position) =>
  !passeStore.passeMode && store.throwPairPending?.id === position.id;

const positionBtnClass = (position) => ({
  'device-btn--firing':      firingIds.value.has(position.id),
  'device-btn--fired':       firedIds.value.has(position.id),
  'device-btn--error':       errorIds.value.has(position.id),
  'device-btn--blocked':     (position.device?.blocked ?? false) || isLocked.value,
  'device-btn--no-device':   !position.device,
  'device-btn--recording':   !!passeStore.recording[position.id],
  'device-btn--pair-pending':
    isThrowPairPending(position) ||
    (passeStore.passeMode && passeStore.pairPending?.id === position.id),
  'device-btn--queued':     isQueued(position),
  'device-btn--ruf-armed':  rufArmedIds.value.includes(position.id),
  'device-btn--inactive':    !store.reservedByMe && !isLocked.value,
});

const iconColor = (position) => {
  if (!position.device) return 'rgba(255,255,255,0.15)';
  if (position.device.blocked || isLocked.value) return 'rgba(252,129,129,0.5)';
  if (!store.reservedByMe) return 'rgba(255,255,255,0.25)';
  return 'rgba(255,255,255,0.95)';
};

const chipClass = (position) => {
  if (!position.device) return 'chip--no-device';
  if (position.device.blocked || isLocked.value) return 'chip--blocked';
  if (!store.reservedByMe) return 'chip--free';
  if (isQueued(position)) return 'chip--queued';
  if (queuedIds.value.length) return 'chip--waiting';
  if (rufArmedIds.value.includes(position.id) && rufPhase.value === 'listening') return 'chip--listening';
  if (rufArmedIds.value.includes(position.id) && rufPhase.value === 'waiting-pair') return 'chip--pending';
  // totzeit + listening fallback: ring/pulse provides phase-specific feedback
  if (rufArmedIds.value.includes(position.id)) return 'chip--waiting';
  if (rufArmedIds.value.length > 0 && !rufArmedIds.value.includes(position.id)) return 'chip--waiting';
  if (errorIds.value.has(position.id)) return 'chip--error';
  if (firedIds.value.has(position.id)) return 'chip--fired';
  if (passeStore.recording[position.id]) return 'chip--recording';
  if (isThrowPairPending(position)) return 'chip--pending';
  if (passeStore.passeMode && passeStore.pairPending?.id === position.id) return 'chip--pending';
  return 'chip--ready';
};

const rufCountdownLabel = computed(() =>
  `${Math.ceil(rufTotzeitRemainingMs.value / 1000)}s`
);
const rufCountdownRingStyle = computed(() => {
  const pct = rufTotzeitTotalMs.value
    ? (rufTotzeitRemainingMs.value / rufTotzeitTotalMs.value) * 360
    : 0;
  return {
    background: `conic-gradient(var(--ruf-color, #56C8D8) ${pct}deg, rgba(255,255,255,0.12) ${pct}deg)`,
  };
});

const chipLabel = (position) => {
  if (!position.device) return 'Kein Gerät';
  if (position.device.blocked) return 'Gesperrt';
  if (isLocked.value) return 'Notfall';
  if (!store.reservedByMe) return 'Frei';
  if (isQueued(position)) return 'Abbrechen';
  if (queuedIds.value.length) return 'Warten';
  if (rufArmedIds.value.includes(position.id) && rufPhase.value === 'listening') return 'Lauscht';
  if (rufArmedIds.value.includes(position.id) && rufPhase.value === 'waiting-pair') return 'Gewählt';
  // totzeit + listening fallback: ring/pulse provides phase-specific feedback
  if (rufArmedIds.value.includes(position.id)) return 'Warten';
  if (rufArmedIds.value.length > 0 && !rufArmedIds.value.includes(position.id)) return 'Warten';
  if (errorIds.value.has(position.id)) return 'Fehler';
  if (firedIds.value.has(position.id)) return 'Ausgelöst';
  if (passeStore.recording[position.id]) return 'Erfasst';
  if (isThrowPairPending(position)) return 'Gewählt';
  if (passeStore.passeMode && passeStore.pairPending?.id === position.id) return 'Gewählt';
  return 'Bereit';
};
</script>

<style scoped>
/* ── Throw-type color tokens ─────────────────────── */
.mode--solo     { --throw-color: #1D9E75; --throw-tint: rgba(29, 158, 117, 0.18); --throw-glow: rgba(29, 158, 117, 0.22); }
.mode--pair     { --throw-color: #378ADD; --throw-tint: rgba(55, 138, 221, 0.18); --throw-glow: rgba(55, 138, 221, 0.22); }
.mode--a_schuss { --throw-color: #EF9F27; --throw-tint: rgba(239, 159, 39, 0.18);  --throw-glow: rgba(239, 159, 39, 0.22);  }
.mode--raffale  { --throw-color: #7F77DD; --throw-tint: rgba(127, 119, 221, 0.18); --throw-glow: rgba(127, 119, 221, 0.22); }

/* Verzögert identity colour (amber), shared by header button, badge, countdown. */
.shooter-remote { --delay-color: #EF9F27; --delay-text: #FAC775; --ruf-color: #56C8D8; --ruf-text: #7AD8E4; }

.shooter-remote {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: env(safe-area-inset-top, 0) 0 calc(env(safe-area-inset-bottom, 0px) + 80px);
  min-height: 0;
}

/* ── Header ──────────────────────────────────────── */
.page-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: linear-gradient(180deg, rgba(255,255,255,0.02) 0%, transparent 100%);
}

/* True 3-zone centering */
.header-left {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.header-center {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

/* Back button */
.back-btn {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  width: 36px;
  height: 36px;
  transition: all 0.15s;
}

.back-btn:hover { background: rgba(255, 255, 255, 0.1); }
.back-btn:active { transform: scale(0.95); }

/* Range name — tablet only */
.range-name {
  display: none;
  font-size: 15px;
  font-weight: 700;
  color: #ffffff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

@media (min-width: 600px) {
  .range-name { display: block; }
}

/* Rotte badge */
.rotte-badge {
  flex-shrink: 0;
  background: var(--sg-accent-tint);
  color: var(--sg-accent);
  border-radius: 12px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

/* Generic icon button (Lock mock) */
.icon-btn {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.4);
  border-radius: 10px;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
}

.icon-btn:hover { background: rgba(255, 255, 255, 0.1); color: rgba(255, 255, 255, 0.7); }
.icon-btn:active { transform: scale(0.95); }

/* Emergency button — always shows text */
.emergency-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0 10px;
  height: 36px;
  border-radius: 10px;
  font-family: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
  background: rgba(252, 129, 129, 0.08);
  border: 1.5px solid rgba(252, 129, 129, 0.3);
  color: #fc8181;
}

.emergency-btn:hover { background: rgba(252, 129, 129, 0.14); }
.emergency-btn:active { transform: scale(0.95); }

.emergency-btn.is-locked {
  background: rgba(72, 187, 120, 0.08);
  border-color: rgba(72, 187, 120, 0.3);
  color: var(--sg-color-success);
}

/* Verzögert — delay-timer button (left of lock, view-mode amber) */
.delay-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0 10px;
  height: 36px;
  border-radius: 10px;
  font-family: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
  background: rgba(239, 159, 39, 0.1);
  border: 1.5px solid rgba(239, 159, 39, 0.35);
  color: var(--delay-text);
}

.delay-btn:hover { background: rgba(239, 159, 39, 0.16); }
.delay-btn:active { transform: scale(0.95); }
.delay-btn.is-counting { animation: delay-btn-pulse 1s ease-in-out infinite; }

@keyframes delay-btn-pulse {
  0%, 100% { border-color: rgba(239, 159, 39, 0.35); }
  50% { border-color: rgba(239, 159, 39, 0.9); }
}

/* Mode badge button — main session-mode indicator */
.mode-badge-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 12px;
  height: 36px;
  border-radius: 20px;
  /* Schiessen (default): green tint */
  border: 1.5px solid rgba(72, 187, 120, 0.35);
  background: rgba(72, 187, 120, 0.08);
  color: #48BB78;
  font-family: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.mode-badge-btn:hover { background: rgba(72, 187, 120, 0.13); }
.mode-badge-btn:active { transform: scale(0.95); }

.mode-badge-btn.mode-badge--recording {
  border-color: rgba(252, 129, 129, 0.45);
  background: rgba(252, 129, 129, 0.12);
  color: #fc8181;
}

.mode-badge-btn.mode-badge--delayed {
  border-color: rgba(239, 159, 39, 0.45);
  background: rgba(239, 159, 39, 0.12);
  color: var(--delay-text);
}

.mode-badge--delayed .mode-dot { background: var(--delay-color); }

.mode-badge-btn.mode-badge--rufausloesung {
  border-color: rgba(86, 200, 216, 0.45);
  background: rgba(86, 200, 216, 0.12);
  color: var(--ruf-text);
}

.mode-badge--rufausloesung .mode-dot {
  background: var(--ruf-color);
  animation: mode-dot-pulse 1s ease-in-out infinite;
}

.mode-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  /* Schiessen: solid green */
  background: #48BB78;
}

.mode-badge--recording .mode-dot {
  background: #fc8181;
  animation: mode-dot-pulse 1s ease-in-out infinite;
}

@keyframes mode-dot-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.35; transform: scale(0.65); }
}

/* ── Mode flyout ─────────────────────────────────── */
.mode-flyout {
  position: relative;
  z-index: 50;
  background: rgba(14, 14, 22, 0.98);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(20px);
  padding: 6px 10px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mode-flyout-backdrop {
  position: fixed;
  inset: 0;
  z-index: 40;
}

.mode-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 12px;
  border: none;
  background: transparent;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  transition: background 0.15s;
  text-align: left;
  width: 100%;
}

.mode-option:hover:not(:disabled) { background: rgba(255, 255, 255, 0.05); }
.mode-option.is-active { color: #fff; background: rgba(255, 255, 255, 0.06); }
.mode-option--soon { opacity: 0.4; cursor: not-allowed; }

.option-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.option-dot--normal    { background: #48BB78; }
.option-dot--erfassen  { background: #fc8181; }
.option-dot--verzoegert { background: #FAC775; }
.option-dot--rufausloesung { background: #56C8D8; }

.mode-option.is-active .option-dot--erfassen,
.mode-option.is-active .option-dot--verzoegert {
  animation: mode-dot-pulse 1s ease-in-out infinite;
}

.option-name { flex: 1; }

.option-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.45);
}

.option-tag--normal {
  background: rgba(72, 187, 120, 0.15);
  color: #48BB78;
}

.option-tag--erfassen {
  background: rgba(252, 129, 129, 0.15);
  color: #fc8181;
}

.option-tag--verzoegert {
  background: rgba(239, 159, 39, 0.15);
  color: var(--delay-text);
}

.option-tag--rufausloesung {
  background: rgba(86, 200, 216, 0.15);
  color: var(--ruf-text);
}

.option-tag--soon {
  background: rgba(250, 199, 117, 0.12);
  color: #FAC775;
}

/* Flyout enter/leave animation */
.mode-flyout-enter-active { transition: opacity 0.15s ease, transform 0.15s ease; }
.mode-flyout-leave-active { transition: opacity 0.1s ease, transform 0.1s ease; }
.mode-flyout-enter-from,
.mode-flyout-leave-to   { opacity: 0; transform: translateY(-6px); }

/* ── Device section ──────────────────────────────── */
.device-section {
  flex: 1;
  padding: 0 16px;
  min-height: 0;
}

/* ── States ──────────────────────────────────────── */
.state-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 48px 24px;
}

.state-text {
  font-size: 15px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.3);
  margin: 0;
}

.state-hint {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.18);
  margin: 0;
  text-align: center;
}

/* ── Device grid ─────────────────────────────────── */
.device-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

/* ── Card borders + glow follow session/view mode ───────── */
/* Saturated border plus a coloured outer glow so the buttons stay legible on a
   tablet in direct sunlight, where the previous faint borders washed out. */
/* Schiessen (default): green */
.device-btn:not(:disabled) {
  border-color: rgba(72, 187, 120, 0.7);
  box-shadow: 0 0 12px rgba(72, 187, 120, 0.3), inset 0 0 0 1px rgba(72, 187, 120, 0.18);
}
/* Erfassen: red */
.session--erfassen .device-btn:not(:disabled) {
  border-color: rgba(252, 129, 129, 0.75);
  box-shadow: 0 0 12px rgba(252, 129, 129, 0.32), inset 0 0 0 1px rgba(252, 129, 129, 0.2);
}
/* Verzögert: amber */
.session--verzoegert .device-btn:not(:disabled) {
  border-color: rgba(239, 159, 39, 0.75);
  box-shadow: 0 0 12px rgba(239, 159, 39, 0.32), inset 0 0 0 1px rgba(239, 159, 39, 0.2);
}
/* Rufauslösung: cyan glow on device buttons */
.session--rufausloesung .device-btn:not(:disabled) {
  border-color: rgba(86, 200, 216, 0.75);
  box-shadow: 0 0 12px rgba(86, 200, 216, 0.32), inset 0 0 0 1px rgba(86, 200, 216, 0.2);
}

/* ── Bottom bar active tab follows throw-type color ─ */
.mode--solo .toggle-btn.active     { background: rgba(29, 158, 117, 0.15); color: #5DCAA5; }
.mode--pair .toggle-btn.active     { background: rgba(55, 138, 221, 0.15); color: #85B7EB; }
.mode--a_schuss .toggle-btn.active { background: rgba(239, 159, 39, 0.15); color: #FAC775; }
.mode--raffale .toggle-btn.active  { background: rgba(127, 119, 221, 0.15); color: #AFA9EC; }

/* Push device grid left when recording shrunk panel is open */
.device-section.is-recording {
  padding-right: 60px;
  transition: padding-right 0.2s ease;
}

/* ── Device button ───────────────────────────────── */
.device-btn {
  position: relative;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.09);
  border: 2px solid rgba(255, 255, 255, 0.1);
  border-radius: 18px;
  padding: 12px 10px 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s, border-color 0.15s, transform 0.1s;
  -webkit-tap-highlight-color: transparent;
}

.device-btn:not(:disabled):hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: color-mix(in srgb, var(--sg-accent) 30%, transparent);
}

.device-btn:not(:disabled):active {
  transform: scale(0.93);
}

.device-btn:disabled { cursor: not-allowed; }

/* States */
.device-btn--inactive {
  opacity: 0.45;
}

.device-btn--pair-pending {
  background: var(--throw-tint) !important;
  border-color: color-mix(in srgb, var(--throw-color) 50%, transparent) !important;
}

.device-btn--fired {
  background: var(--throw-tint) !important;
  border-color: color-mix(in srgb, var(--throw-color) 40%, transparent) !important;
  animation: fired-pulse 0.35s ease-out;
}

.device-btn--firing {
  background: var(--throw-tint) !important;
  border-color: color-mix(in srgb, var(--throw-color) 40%, transparent) !important;
}

.device-btn--error {
  background: rgba(252, 129, 129, 0.1) !important;
  border-color: rgba(252, 129, 129, 0.4) !important;
}

.device-btn--blocked {
  opacity: 0.45;
}

.device-btn--recording {
  background: transparent !important;
  border-color: color-mix(in srgb, var(--throw-color) 55%, transparent) !important;
}

/* Verzögert: the queued (counting-down) button — amber, stays tappable to abort */
.device-btn--queued {
  background: rgba(239, 159, 39, 0.12) !important;
  border-color: rgba(239, 159, 39, 0.6) !important;
  opacity: 1 !important;
}

.device-btn--ruf-armed {
  background: rgba(86, 200, 216, 0.12) !important;
  border-color: rgba(86, 200, 216, 0.6) !important;
  opacity: 1 !important;
}

.btn-countdown-num--ruf { color: var(--ruf-text); }

@keyframes fired-pulse {
  0% { transform: scale(1); }
  40% { transform: scale(0.95); }
  100% { transform: scale(1); }
}

/* Glow */
.btn-glow {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: radial-gradient(circle at 50% 40%, var(--throw-glow) 0%, transparent 70%);
  opacity: 0;
  transition: opacity 0.3s;
  pointer-events: none;
}

.device-btn--firing .btn-glow,
.device-btn--fired .btn-glow {
  opacity: 1;
}

/* Icon wrap */
.btn-icon-wrap {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.07);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
  position: relative;
}

.btn-letter {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.5px;
}

/* Verzögert: circular countdown ring filling the icon-wrap (set inline via
   conic-gradient), with a punched-out centre so it reads as a ring. */
.btn-countdown-ring {
  position: absolute;
  inset: -4px;
  border-radius: 50%;
  -webkit-mask: radial-gradient(circle, transparent 56%, #000 58%);
  mask: radial-gradient(circle, transparent 56%, #000 58%);
  transition: background 0.05s linear;
  pointer-events: none;
}

.btn-countdown-num {
  font-size: 22px;
  font-weight: 800;
  color: var(--delay-text);
  font-variant-numeric: tabular-nums;
  z-index: 1;
}

.btn-position-num {
  position: absolute;
  bottom: 2px;
  right: 2px;
  font-size: 9px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.25);
  line-height: 1;
  pointer-events: none;
}

.device-btn--firing .btn-icon-wrap {
  background: var(--throw-tint);
  animation: icon-pulse 0.7s ease-in-out infinite alternate;
}

.device-btn--fired .btn-icon-wrap { background: var(--throw-tint); }
.device-btn--error .btn-icon-wrap { background: rgba(252, 129, 129, 0.15); }
.device-btn--pair-pending .btn-icon-wrap { background: var(--throw-tint); }
.device-btn--pair-pending { border-color: color-mix(in srgb, var(--throw-color) 50%, transparent) !important; }

@keyframes icon-pulse {
  from { transform: scale(0.94); }
  to { transform: scale(1.06); }
}

/* Status chip */
.btn-status-chip {
  font-size: 11px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 20px;
}

.chip--ready { background: rgba(72,187,120,0.15); color: var(--sg-color-success); }
.chip--fired { background: rgba(72,187,120,0.2); color: var(--sg-color-success); }
.chip--recording { background: var(--sg-accent-tint); color: var(--sg-accent); }
.chip--pending { background: color-mix(in srgb, var(--throw-color) 18%, transparent); color: var(--throw-color); }
.chip--queued { background: rgba(239, 159, 39, 0.18); color: var(--delay-text); }
.chip--waiting { background: rgba(255, 255, 255, 0.06); color: rgba(250, 199, 117, 0.55); }
.chip--listening {
  background: rgba(86, 200, 216, 0.18);
  color: var(--ruf-text);
  animation: mode-dot-pulse 1s ease-in-out infinite;
}
.chip--error { background: rgba(252,129,129,0.15); color: var(--sg-color-danger-bg); }
.chip--blocked { background: rgba(252,129,129,0.12); color: rgba(252,129,129,0.7); }
.chip--free { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.35); }

/* ── Solo/Pair bar ────────────────────────────────── */
.solo-pair-bar {
  position: fixed;
  bottom: calc(env(safe-area-inset-bottom, 0px) + 16px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 20;
  width: min(90%, 320px);
}

.solo-pair-toggle {
  background: rgba(18, 18, 28, 0.9);
  border: 1.5px solid rgba(255, 255, 255, 0.12);
  border-radius: 14px;
  display: flex;
  overflow: hidden;
  backdrop-filter: blur(16px);
  box-shadow: var(--sg-shadow-md);
}

.toggle-btn {
  flex: 1;
  padding: 13px 0;
  background: transparent;
  border: none;
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.3);
  cursor: pointer;
  transition: all 0.18s;
}

.toggle-btn.active {
  background: var(--sg-accent-tint);
  color: var(--sg-accent);
}

.toggle-btn:not(.active):hover {
  background: rgba(255, 255, 255, 0.04);
  color: rgba(255, 255, 255, 0.5);
}

/* ── Tablet+ ─────────────────────────────────────── */
@media (min-width: 600px) {
  .device-section {
    max-width: 560px;
    margin: 0 auto;
    width: 100%;
    padding: 0 24px;
  }

  .device-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

.chip--no-device { background: rgba(255,255,255,0.05); color: rgba(255,255,255,0.2); }
.device-btn--no-device { opacity: 0.3; }

/* ── Verzögert: delay-config modal ───────────────── */
.delay-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.delay-modal {
  width: min(100%, 340px);
  background: rgba(20, 20, 30, 0.98);
  border: 1px solid rgba(239, 159, 39, 0.3);
  border-radius: 20px;
  padding: 20px;
  box-shadow: var(--sg-shadow-md);
}

.delay-modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.delay-modal-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #fff;
}

.delay-modal-close {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.6);
  border-radius: 8px;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
}

.delay-modal-close:hover { background: rgba(255, 255, 255, 0.1); color: #fff; }

.delay-modal-hint {
  margin: 0 0 16px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.45);
  line-height: 1.4;
}

.delay-value {
  text-align: center;
  font-size: 48px;
  font-weight: 800;
  color: var(--delay-text);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.delay-value-unit {
  font-size: 22px;
  font-weight: 700;
  margin-left: 2px;
  color: rgba(250, 199, 117, 0.6);
}

.delay-slider {
  width: 100%;
  margin: 18px 0 6px;
  accent-color: var(--delay-color);
  cursor: pointer;
}

.delay-scale {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.3);
  margin-bottom: 18px;
}

.delay-save-btn {
  width: 100%;
  padding: 13px 0;
  border: none;
  border-radius: 12px;
  background: rgba(239, 159, 39, 0.18);
  border: 1.5px solid rgba(239, 159, 39, 0.45);
  color: var(--delay-text);
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
}

.delay-save-btn:hover { background: rgba(239, 159, 39, 0.26); }
.delay-save-btn:active { transform: scale(0.97); }

/* Rufauslösung — mic header button */
.ruf-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0 10px;
  height: 36px;
  border-radius: 10px;
  font-family: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
  background: rgba(86, 200, 216, 0.10);
  border: 1.5px solid rgba(86, 200, 216, 0.35);
  color: var(--ruf-text);
}

.ruf-btn:hover  { background: rgba(86, 200, 216, 0.16); }
.ruf-btn:active { transform: scale(0.95); }

/* Rufauslösung config modal */
.ruf-modal {
  width: min(100%, 360px);
  background: rgba(20, 20, 30, 0.98);
  border: 1px solid rgba(86, 200, 216, 0.3);
  border-radius: 20px;
  padding: 20px;
  box-shadow: var(--sg-shadow-md);
}

.ruf-slider-group {
  margin-bottom: 18px;
}

.ruf-slider-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.ruf-slider-label {
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.7);
}

.ruf-slider-value {
  font-size: 13px;
  font-weight: 700;
  color: var(--ruf-text);
  font-variant-numeric: tabular-nums;
}

.ruf-slider {
  width: 100%;
  accent-color: var(--ruf-color);
  cursor: pointer;
}

.ruf-slider-scale {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.3);
  margin-top: 2px;
}

/* Live level bar */
.ruf-level-wrap {
  margin-bottom: 18px;
}

.ruf-level-bar-track {
  position: relative;
  height: 10px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  overflow: visible;
}

.ruf-level-bar-fill {
  height: 100%;
  background: var(--ruf-color);
  border-radius: 6px;
  transition: width 0.05s linear;
}

.ruf-level-bar-fill.ruf-level--trigger {
  background: #48BB78;
  animation: mode-dot-pulse 0.4s ease-out;
}

.ruf-level-threshold {
  position: absolute;
  top: -3px;
  bottom: -3px;
  width: 2px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 2px;
  transform: translateX(-50%);
}

.ruf-level-hint {
  margin-top: 5px;
  font-size: 11px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.35);
  text-align: center;
}

.ruf-denied {
  font-size: 12px;
  color: #fc8181;
  text-align: center;
  padding: 10px 0;
}

.ruf-save-btn {
  width: 100%;
  padding: 13px 0;
  border-radius: 12px;
  background: rgba(86, 200, 216, 0.18);
  border: 1.5px solid rgba(86, 200, 216, 0.45);
  color: var(--ruf-text);
  font-family: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
}

.ruf-save-btn:hover  { background: rgba(86, 200, 216, 0.26); }
.ruf-save-btn:active { transform: scale(0.97); }

.delay-modal-enter-active,
.delay-modal-leave-active { transition: opacity 0.15s ease; }
.delay-modal-enter-from,
.delay-modal-leave-to { opacity: 0; }
.delay-modal-enter-active .delay-modal { transition: transform 0.15s ease; }
.delay-modal-enter-from .delay-modal { transform: scale(0.95); }
</style>
