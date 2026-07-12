import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { usePasseStore } from '@/stores/passeStore.js';

// Generic clamp+load helpers for persisted numeric settings.
const clampRuf = (v, min, max, def) => {
  const n = Math.round(Number(v));
  if (Number.isNaN(n)) return def;
  return Math.min(max, Math.max(min, n));
};

const loadRuf = (key, min, max, def) => {
  try {
    const raw = localStorage.getItem(key);
    return raw == null ? def : clampRuf(raw, min, max, def);
  } catch {
    return def;
  }
};

// Verzögert (delayed) session mode — persisted delay before a command is released.
const DELAY = { key: 'sg_remote_delay', min: 1, max: 10, def: 3 };

// Rufauslösung settings — persisted mic trigger configuration.
const RUF_PEAK    = { key: 'sg_ruf_peak',    min: 0,  max: 100,  def: 70   };
const RUF_DAUER   = { key: 'sg_ruf_dauer',   min: 50, max: 500,  def: 120  };
const RUF_TOTZEIT = { key: 'sg_ruf_totzeit', min: 0,  max: 8000, def: 1000 };

export const useShooterRemoteStore = defineStore('shooterRemote', () => {
  const selectedRangeId = ref(null);
  const reservedByMe = ref(false);
  const selectedGroupId = ref(null);

  // Optional competition context set when navigating from CompetitionLiveView
  // { instanceId: string, rotteId: string } | null
  const competitionContext = ref(null);

  const mode = ref('solo');
  const recording = ref({});

  const sessionMode = ref('throwing');
  const recordingActive = ref(false);
  const recordingPaused = ref(false);
  const throwPairPending = ref(null);

  // Verzögert: saveable delay (seconds) applied before a command fires.
  const delaySeconds = ref(loadRuf(DELAY.key, DELAY.min, DELAY.max, DELAY.def));

  // Rufauslösung settings
  const rufPeak    = ref(loadRuf(RUF_PEAK.key,    RUF_PEAK.min,    RUF_PEAK.max,    RUF_PEAK.def));
  const rufDauer   = ref(loadRuf(RUF_DAUER.key,   RUF_DAUER.min,   RUF_DAUER.max,   RUF_DAUER.def));
  const rufTotzeit = ref(loadRuf(RUF_TOTZEIT.key, RUF_TOTZEIT.min, RUF_TOTZEIT.max, RUF_TOTZEIT.def));

  const persistRuf = (key, value) => {
    try { localStorage.setItem(key, String(value)); } catch { /* ignore */ }
  };

  const setDelaySeconds = (n) => {
    delaySeconds.value = clampRuf(n, DELAY.min, DELAY.max, DELAY.def);
    persistRuf(DELAY.key, delaySeconds.value);
  };

  const setRufPeak = (v) => {
    rufPeak.value = clampRuf(v, RUF_PEAK.min, RUF_PEAK.max, RUF_PEAK.def);
    persistRuf(RUF_PEAK.key, rufPeak.value);
  };

  const setRufDauer = (v) => {
    rufDauer.value = clampRuf(v, RUF_DAUER.min, RUF_DAUER.max, RUF_DAUER.def);
    persistRuf(RUF_DAUER.key, rufDauer.value);
  };

  const setRufTotzeit = (v) => {
    rufTotzeit.value = clampRuf(v, RUF_TOTZEIT.min, RUF_TOTZEIT.max, RUF_TOTZEIT.def);
    persistRuf(RUF_TOTZEIT.key, rufTotzeit.value);
  };

  const isReserved = computed(() => reservedByMe.value);

  const reservePlatz = (rangeId) => {
    const passeStore = usePasseStore();
    selectedRangeId.value = rangeId;
    reservedByMe.value = true;
    selectedGroupId.value = null;
    sessionMode.value = 'throwing';
    recordingActive.value = false;
    recordingPaused.value = false;
    passeStore.resetCapture();
  };

  const ensureReserved = (rangeId) => {
    if (selectedRangeId.value !== rangeId || !reservedByMe.value) {
      reservePlatz(rangeId);
    }
  };

  const setCompetitionContext = (instanceId, rotteId) => {
    if (instanceId && rotteId) {
      competitionContext.value = { instanceId, rotteId };
    } else {
      competitionContext.value = null;
    }
  };

  const releasePlatz = () => {
    reservedByMe.value = false;
  };

  const setGroupId = (groupId) => {
    selectedGroupId.value = groupId;
    throwPairPending.value = null;
  };

  const setMode = (newMode) => {
    mode.value = newMode;
    throwPairPending.value = null;
  };

  // Enter recording mode for an in-progress edit. Unlike setSessionMode('recording'),
  // this never calls startCapture(), so an already-loaded editingSerie (the serie
  // being edited) is preserved rather than reset to an empty draft.
  const enterRecordingForEdit = () => {
    recordingActive.value = true;
    recordingPaused.value = false;
    sessionMode.value = 'recording';
  };

  const setSessionMode = (newMode) => {
    const passeStore = usePasseStore();
    if (newMode === 'recording') {
      if (recordingPaused.value) {
        recordingActive.value = true;
        recordingPaused.value = false;
        passeStore.passeMode = true;
      } else {
        recordingActive.value = true;
        passeStore.startCapture();
      }
    } else {
      // 'throwing' or 'delayed' — pause any active recording, keeping its state
      // so re-entering 'recording' resumes the capture.
      if (recordingActive.value && passeStore.passeMode) {
        recordingPaused.value = true;
        recordingActive.value = false;
      }
    }
    sessionMode.value = newMode;
  };

  return {
    selectedRangeId,
    reservedByMe,
    selectedGroupId,
    competitionContext,
    mode,
    recording,
    sessionMode,
    recordingActive,
    recordingPaused,
    throwPairPending,
    delaySeconds,
    setDelaySeconds,
    rufPeak,
    rufDauer,
    rufTotzeit,
    setRufPeak,
    setRufDauer,
    setRufTotzeit,
    isReserved,
    reservePlatz,
    ensureReserved,
    releasePlatz,
    setGroupId,
    setMode,
    setSessionMode,
    enterRecordingForEdit,
    setCompetitionContext,
  };
});
