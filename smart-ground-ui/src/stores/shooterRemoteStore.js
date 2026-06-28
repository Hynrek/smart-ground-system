import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { usePasseStore } from '@/stores/passeStore.js';

// Verzögert (delayed) session mode — persisted delay before a command is released.
const DELAY_STORAGE_KEY = 'sg_remote_delay';
const DELAY_MIN = 1;
const DELAY_MAX = 10;
const DELAY_DEFAULT = 3;

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
    isReserved,
    reservePlatz,
    ensureReserved,
    releasePlatz,
    setGroupId,
    setMode,
    setSessionMode,
    setCompetitionContext,
  };
});
