import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { sendDeviceCommand } from '@/services/deviceApi.js';

export const useShooterRemoteStore = defineStore('shooterRemote', () => {
  const selectedRangeId = ref(null);
  const reservedByMe = ref(false);
  const selectedGroupId = ref(null);

  const mode = ref('solo'); // 'solo' | 'pair' | 'a.schuss' | 'raffale'
  const recording = ref({}); // Record<deviceId, boolean>

  const sessionMode = ref('throwing'); // 'throwing' | 'recording'
  const recordingActive = ref(false); // Is recording currently running
  const recordingPaused = ref(false); // Was recording paused when switching to throwing mode

  const programMode = ref(false);
  const pairPending = ref(null); // { id, alias } | null
  const ablauf = ref([]);
  const savedPrograms = ref([]);
  const editingId = ref(null);

  const playProg = ref(null);
  const playCurrentStep = ref(0);
  const playPartialStep = ref(null); // 'first' or 'second' for a.schuss
  const playRaffaleStarted = ref(false);
  const playScoreMode = ref(false); // Enable score tracking
  const playScore = ref({ hits: 0, misses: 0, steps: [] }); // Track each step's result
  const playLastDeviceStep = ref(null); // For "No Bird" retry

  const isReserved = computed(() => reservedByMe.value);

  const playProgress = computed(() => {
    if (!playProg.value) return 0;
    return ((playCurrentStep.value + 1) / playProg.value.length) * 100;
  });

  // Called from ShooterRangeSelectView when user taps a range card
  const reservePlatz = (rangeId) => {
    selectedRangeId.value = rangeId;
    reservedByMe.value = true;
    selectedGroupId.value = null;
    programMode.value = false;
    ablauf.value = [];
    pairPending.value = null;
    playProg.value = null;
    sessionMode.value = 'throwing';
    recordingActive.value = false;
    recordingPaused.value = false;
  };

  // Called from ShooterRemoteView on mount — auto-reserves if navigated directly
  const ensureReserved = (rangeId) => {
    if (selectedRangeId.value !== rangeId || !reservedByMe.value) {
      reservePlatz(rangeId);
    }
  };

  const releasePlatz = () => {
    reservedByMe.value = false;
    programMode.value = false;
    pairPending.value = null;
    playProg.value = null;
  };

  const setGroupId = (groupId) => {
    selectedGroupId.value = groupId;
    pairPending.value = null;
  };

  const setMode = (newMode) => {
    mode.value = newMode;
    pairPending.value = null;
  };

  const addStep = (deviceId, deviceData) => {
    const alias = deviceData.alias ?? 'Gerät';

    if (mode.value === 'solo') {
      recording.value = { ...recording.value, [deviceId]: true };
      setTimeout(() => {
        const r = { ...recording.value };
        delete r[deviceId];
        recording.value = r;
      }, 500);
      ablauf.value = [...ablauf.value, { id: Date.now(), type: 'solo', alias, deviceId }];
    } else if (mode.value === 'raffale') {
      // Raffale: one device triggered twice with 2 sec delay
      recording.value = { ...recording.value, [deviceId]: true };
      setTimeout(() => {
        const r = { ...recording.value };
        delete r[deviceId];
        recording.value = r;
      }, 500);
      ablauf.value = [...ablauf.value, { id: Date.now(), type: 'raffale', alias, deviceId }];
      // Switch back to solo after raffale
      mode.value = 'solo';
    } else if (mode.value === 'pair' || mode.value === 'a.schuss') {
      // Pair / a.Schuss: two devices together
      if (!pairPending.value) {
        pairPending.value = { id: deviceId, alias };
      } else if (pairPending.value.id === deviceId) {
        pairPending.value = null;
      } else {
        const pendingId = pairPending.value.id;
        const pendingAlias = pairPending.value.alias;
        recording.value = { ...recording.value, [deviceId]: true, [pendingId]: true };
        setTimeout(() => {
          const r = { ...recording.value };
          delete r[deviceId];
          delete r[pendingId];
          recording.value = r;
        }, 500);
        const stepType = mode.value === 'a.schuss' ? 'a.schuss' : 'pair';
        ablauf.value = [
          ...ablauf.value,
          { id: Date.now(), type: stepType, alias1: pendingAlias, alias2: alias, deviceId1: pendingId, deviceId2: deviceId },
        ];
        pairPending.value = null;
        // Switch back to solo mode after pair/a.schuss
        mode.value = 'solo';
      }
    }
  };

  const removeStep = (stepId) => {
    ablauf.value = ablauf.value.filter((s) => s.id !== stepId);
  };

  const clearAblauf = () => {
    ablauf.value = [];
    pairPending.value = null;
  };

  const startCapture = () => {
    if (!isReserved.value) return;
    programMode.value = true;
    ablauf.value = [];
    editingId.value = null;
  };

  const cancelCapture = () => {
    programMode.value = false;
    ablauf.value = [];
    pairPending.value = null;
  };

  const saveProgram = (programName = null) => {
    if (ablauf.value.length === 0) return;
    const name = programName || `Programm ${savedPrograms.value.length + 1}`;
    if (editingId.value !== null) {
      const idx = savedPrograms.value.findIndex((p) => p.id === editingId.value);
      if (idx !== -1) savedPrograms.value[idx].steps = [...ablauf.value];
    } else {
      savedPrograms.value = [...savedPrograms.value, { id: Date.now(), name, steps: [...ablauf.value] }];
    }
    ablauf.value = [];
    pairPending.value = null;
    editingId.value = null;
    programMode.value = false;
  };

  const editProgram = (programId) => {
    const prog = savedPrograms.value.find((p) => p.id === programId);
    if (!prog) return;
    editingId.value = programId;
    ablauf.value = [...prog.steps];
    programMode.value = true;
  };

  const deleteProgram = (programId) => {
    savedPrograms.value = savedPrograms.value.filter((p) => p.id !== programId);
  };

  const playProgram = (programId) => {
    const prog = savedPrograms.value.find((p) => p.id === programId);
    if (!prog) return;
    playProg.value = prog.steps;
    playCurrentStep.value = 0;
    playScoreMode.value = false;
  };

  const playProgramWithScore = (programId) => {
    const prog = savedPrograms.value.find((p) => p.id === programId);
    if (!prog) return;
    playProg.value = prog.steps;
    playCurrentStep.value = 0;
    playScoreMode.value = true;
    playScore.value = { hits: 0, misses: 0, steps: [] };
    playLastDeviceStep.value = null;
  };

  const advancePlayStep = async () => {
    if (!playProg.value) return;

    const currentStep = playProg.value[playCurrentStep.value];
    if (!currentStep) return;

    try {
      if (currentStep.type === 'solo') {
        await sendDeviceCommand(currentStep.deviceId);
        if (playScoreMode.value) {
          playLastDeviceStep.value = playCurrentStep.value;
          // Don't auto-advance in score mode, wait for hit/miss buttons
        } else {
          moveToNextStep();
        }
      } else if (currentStep.type === 'pair') {
        await Promise.all([
          sendDeviceCommand(currentStep.deviceId1),
          sendDeviceCommand(currentStep.deviceId2),
        ]);
        if (playScoreMode.value) {
          playLastDeviceStep.value = playCurrentStep.value;
        } else {
          moveToNextStep();
        }
      } else if (currentStep.type === 'a.schuss') {
        // a.schuss requires two clicks
        if (playPartialStep.value === null) {
          // First click: send command to first device
          await sendDeviceCommand(currentStep.deviceId1);
          playPartialStep.value = 'first';
        } else if (playPartialStep.value === 'first') {
          // Second click: send command to second device
          await sendDeviceCommand(currentStep.deviceId2);
          playPartialStep.value = null;
          if (playScoreMode.value) {
            playLastDeviceStep.value = playCurrentStep.value;
          } else {
            moveToNextStep();
          }
        }
      } else if (currentStep.type === 'raffale') {
        // Raffale: timer starts on first click, auto-advances after delay
        if (!playRaffaleStarted.value) {
          await sendDeviceCommand(currentStep.deviceId);
          playRaffaleStarted.value = true;
          if (playScoreMode.value) {
            playLastDeviceStep.value = playCurrentStep.value;
          }
          // Auto-advance after 1 second delay (called from component)
        }
      }
    } catch (err) {
      console.error('Failed to send device command during playback:', err);
    }
  };

  const completeRaffaleStep = async () => {
    const currentStep = playProg.value[playCurrentStep.value];
    if (currentStep && currentStep.type === 'raffale') {
      try {
        await sendDeviceCommand(currentStep.deviceId);
      } catch (err) {
        console.error('Failed to send second raffale command:', err);
      }
      playRaffaleStarted.value = false;
      if (!playScoreMode.value) {
        moveToNextStep();
      }
      // In score mode, wait for hit/miss buttons
    }
  };

  const moveToNextStep = () => {
    if (playCurrentStep.value < playProg.value.length - 1) {
      playCurrentStep.value += 1;
    } else {
      closePlayback();
    }
  };

  const recordHit = () => {
    if (!playScoreMode.value) return;
    playScore.value.hits += 1;
    playScore.value.steps.push({ step: playCurrentStep.value, result: 'hit' });
    playLastDeviceStep.value = null;
    moveToNextStep();
  };

  const recordMiss = () => {
    if (!playScoreMode.value) return;
    playScore.value.misses += 1;
    playScore.value.steps.push({ step: playCurrentStep.value, result: 'miss' });
    playLastDeviceStep.value = null;
    moveToNextStep();
  };

  const handleNoBird = () => {
    if (!playScoreMode.value || !playLastDeviceStep.value) return;
    // Go back to last device step and reset partial step
    playCurrentStep.value = playLastDeviceStep.value;
    playPartialStep.value = null;
  };

  const closePlayback = () => {
    playProg.value = null;
    playCurrentStep.value = 0;
    playPartialStep.value = null;
    playRaffaleStarted.value = false;
    playScoreMode.value = false;
    playScore.value = { hits: 0, misses: 0, steps: [] };
    playLastDeviceStep.value = null;
  };

  const setSessionMode = (newMode) => {
    if (newMode === 'throwing') {
      // Switching to throwing mode — pause recording if active
      if (recordingActive.value && programMode.value) {
        recordingPaused.value = true;
        recordingActive.value = false;
      }
    } else if (newMode === 'recording') {
      // Switching to recording mode — resume recording if it was paused
      if (recordingPaused.value) {
        recordingActive.value = true;
        recordingPaused.value = false;
        programMode.value = true;
      } else {
        // Start fresh recording session
        recordingActive.value = true;
        programMode.value = true;
        ablauf.value = [];
        pairPending.value = null;
      }
    }
    sessionMode.value = newMode;
  };

  return {
    selectedRangeId, reservedByMe, selectedGroupId,
    mode, recording,
    sessionMode, recordingActive, recordingPaused,
    programMode, pairPending, ablauf, savedPrograms, editingId,
    playProg, playCurrentStep, playPartialStep, playRaffaleStarted,
    playScoreMode, playScore, playLastDeviceStep,
    isReserved, playProgress,
    reservePlatz, ensureReserved, releasePlatz, setGroupId,
    setMode, setSessionMode, addStep, removeStep, clearAblauf,
    startCapture, cancelCapture, saveProgram, editProgram, deleteProgram,
    playProgram, playProgramWithScore, advancePlayStep, completeRaffaleStep,
    recordHit, recordMiss, handleNoBird, closePlayback,
  };
});
