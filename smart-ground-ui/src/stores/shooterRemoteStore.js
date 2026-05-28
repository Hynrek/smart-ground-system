import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { usePasseStore } from '@/stores/passeStore.js';

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
    if (newMode === 'throwing') {
      if (recordingActive.value && passeStore.passeMode) {
        recordingPaused.value = true;
        recordingActive.value = false;
      }
    } else if (newMode === 'recording') {
      if (recordingPaused.value) {
        recordingActive.value = true;
        recordingPaused.value = false;
        passeStore.passeMode = true;
      } else {
        recordingActive.value = true;
        passeStore.startCapture();
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
