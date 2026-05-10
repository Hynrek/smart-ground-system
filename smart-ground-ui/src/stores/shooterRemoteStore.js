import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { useProgramStore } from '@/stores/programStore.js';

export const useShooterRemoteStore = defineStore('shooterRemote', () => {
  const selectedRangeId = ref(null);
  const reservedByMe = ref(false);
  const selectedGroupId = ref(null);

  const mode = ref('solo');
  const recording = ref({});

  const sessionMode = ref('throwing');
  const recordingActive = ref(false);
  const recordingPaused = ref(false);
  const throwPairPending = ref(null);

  const isReserved = computed(() => reservedByMe.value);

  const reservePlatz = (rangeId) => {
    const programStore = useProgramStore();
    selectedRangeId.value = rangeId;
    reservedByMe.value = true;
    selectedGroupId.value = null;
    sessionMode.value = 'throwing';
    recordingActive.value = false;
    recordingPaused.value = false;
    programStore.resetCapture();
  };

  const ensureReserved = (rangeId) => {
    if (selectedRangeId.value !== rangeId || !reservedByMe.value) {
      reservePlatz(rangeId);
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
    const programStore = useProgramStore();
    if (newMode === 'throwing') {
      if (recordingActive.value && programStore.programMode) {
        recordingPaused.value = true;
        recordingActive.value = false;
      }
    } else if (newMode === 'recording') {
      if (recordingPaused.value) {
        recordingActive.value = true;
        recordingPaused.value = false;
        programStore.programMode = true;
      } else {
        recordingActive.value = true;
        programStore.startCapture();
      }
    }
    sessionMode.value = newMode;
  };

  return {
    selectedRangeId,
    reservedByMe,
    selectedGroupId,
    mode,
    recording,
    sessionMode,
    recordingActive,
    recordingPaused,
    throwPairPending,
    isReserved,
    reservePlatz,
    ensureReserved,
    releasePlatz,
    setGroupId,
    setMode,
    setSessionMode,
  };
});
