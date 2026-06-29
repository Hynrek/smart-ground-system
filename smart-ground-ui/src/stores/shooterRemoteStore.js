import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { usePasseStore } from '@/stores/passeStore.js';

// Verzögert (delayed) session mode — persisted delay before a command is released.
const DELAY_STORAGE_KEY = 'sg_remote_delay';
const DELAY_MIN = 1;
const DELAY_MAX = 10;
const DELAY_DEFAULT = 3;

// Rufauslösung settings — persisted mic trigger configuration.
const RUF_PEAK_KEY    = 'sg_ruf_peak';
const RUF_DAUER_KEY   = 'sg_ruf_dauer';
const RUF_TOTZEIT_KEY = 'sg_ruf_totzeit';

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

const clampDelay = (n) => {
  const v = Math.round(Number(n));
  if (Number.isNaN(v)) return DELAY_DEFAULT;
  return Math.min(DELAY_MAX, Math.max(DELAY_MIN, v));
};

const loadDelay = () => {
  try {
    const raw = localStorage.getItem(DELAY_STORAGE_KEY);
    return raw == null ? DELAY_DEFAULT : clampDelay(raw);
  } catch {
    return DELAY_DEFAULT;
  }
};

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
  const delaySeconds = ref(loadDelay());

  // Rufauslösung settings
  const rufPeak    = ref(loadRuf(RUF_PEAK_KEY,    0,  100,  70));
  const rufDauer   = ref(loadRuf(RUF_DAUER_KEY,   50, 500,  120));
  const rufTotzeit = ref(loadRuf(RUF_TOTZEIT_KEY, 0,  8000, 1000));

  const persistRuf = (key, value) => {
    try { localStorage.setItem(key, String(value)); } catch { /* ignore */ }
  };

  const setRufPeak = (v) => {
    rufPeak.value = clampRuf(v, 0, 100, 70);
    persistRuf(RUF_PEAK_KEY, rufPeak.value);
  };

  const setRufDauer = (v) => {
    rufDauer.value = clampRuf(v, 50, 500, 120);
    persistRuf(RUF_DAUER_KEY, rufDauer.value);
  };

  const setRufTotzeit = (v) => {
    rufTotzeit.value = clampRuf(v, 0, 8000, 1000);
    persistRuf(RUF_TOTZEIT_KEY, rufTotzeit.value);
  };

  const setDelaySeconds = (n) => {
    delaySeconds.value = clampDelay(n);
    try {
      localStorage.setItem(DELAY_STORAGE_KEY, String(delaySeconds.value));
    } catch {
      // ignore persistence failures (private mode, quota, etc.)
    }
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
