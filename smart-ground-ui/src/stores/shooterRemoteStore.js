import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { sendDeviceCommand } from '@/services/deviceApi.js';
import { useAuthStore } from '@/stores/authStore.js';

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

  const getProgramPrefix = () => {
    const authStore = useAuthStore();
    const userName = authStore.userName ?? 'anonymous';
    return `${userName}_program_`;
  };

  const nextProgramKey = () => {
    const prefix = getProgramPrefix();
    const existing = Object.keys(localStorage)
      .filter((k) => k.startsWith(prefix))
      .map((k) => parseInt(k.slice(prefix.length), 10))
      .filter((n) => !isNaN(n));
    const nextNum = existing.length > 0 ? Math.max(...existing) + 1 : 1;
    return `${prefix}${nextNum}`;
  };

  const loadProgramsFromStorage = () => {
    const prefix = getProgramPrefix();
    const programs = Object.keys(localStorage)
      .filter((k) => k.startsWith(prefix))
      .map((key) => {
        try {
          const data = JSON.parse(localStorage.getItem(key));
          return { id: key, name: data.programName, steps: data.steps };
        } catch {
          return null;
        }
      })
      .filter(Boolean)
      .sort((a, b) => {
        const numA = parseInt(a.id.slice(prefix.length), 10);
        const numB = parseInt(b.id.slice(prefix.length), 10);
        return numA - numB;
      });
    savedPrograms.value = programs;
  };

  loadProgramsFromStorage();

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
      if (idx !== -1) {
        savedPrograms.value[idx].steps = [...ablauf.value];
        localStorage.setItem(editingId.value, JSON.stringify({ programName: savedPrograms.value[idx].name, steps: [...ablauf.value] }));
      }
    } else {
      const key = nextProgramKey();
      localStorage.setItem(key, JSON.stringify({ programName: name, steps: [...ablauf.value] }));
      savedPrograms.value = [...savedPrograms.value, { id: key, name, steps: [...ablauf.value] }];
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
    localStorage.removeItem(programId);
    savedPrograms.value = savedPrograms.value.filter((p) => p.id !== programId);
  };

  const playProgramWithScore = (programId) => {
    const prog = savedPrograms.value.find((p) => p.id === programId);
    if (!prog) return;
    playProg.value = prog.steps;
    playCurrentStep.value = 0;
    playScoreMode.value = true;
    // Initialize score with step states
    const stepStates = prog.steps.map((_, idx) => ({
      stepIndex: idx,
      state: 'pending', // 'pending' | 'done' | 'failed'
      pointValue: getPointValueForStep(prog.steps[idx]),
    }));
    playScore.value = { totalPoints: 0, stepStates };
    playLastDeviceStep.value = null;
  };

  const createPlaySession = (programId) => {
    const prog = savedPrograms.value.find((p) => p.id === programId);
    if (!prog) return null;
    return programId;
  };

  const loadPlaySession = (sessionId) => {
    const sessionData = localStorage.getItem(sessionId);
    if (!sessionData) return null;
    try {
      return JSON.parse(sessionData);
    } catch (e) {
      console.error('Failed to parse play session:', e);
      return null;
    }
  };

  const clearPlaySession = () => {};

  const getPointValueForStep = (step) => {
    if (step.type === 'solo') return 1;
    if (step.type === 'pair' || step.type === 'a.schuss') return 2;
    if (step.type === 'raffale') return 2;
    return 1;
  };

  const advancePlayStep = async () => {
    if (!playProg.value) return;
    const currentStep = playProg.value[playCurrentStep.value];
    if (!currentStep) return;

    try {
      if (currentStep.type === 'solo') {
        await sendDeviceCommand(currentStep.deviceId);
        markStepDone();
      } else if (currentStep.type === 'pair') {
        await Promise.all([
          sendDeviceCommand(currentStep.deviceId1),
          sendDeviceCommand(currentStep.deviceId2),
        ]);
        markStepDone();
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
          markStepDone();
        }
      } else if (currentStep.type === 'raffale') {
        // Raffale: timer starts on first click, auto-advances after delay
        if (!playRaffaleStarted.value) {
          await sendDeviceCommand(currentStep.deviceId);
          playRaffaleStarted.value = true;
        }
      }
    } catch (err) {
      console.error('Failed to send device command during playback:', err);
    }
  };

  const markStepDone = () => {
    if (!playScore.value.stepStates) return;
    const state = playScore.value.stepStates[playCurrentStep.value];
    if (state && state.state === 'pending') {
      state.state = 'done';
      playScore.value.totalPoints += state.pointValue;
      playLastDeviceStep.value = playCurrentStep.value;
    }
    moveToNextStep();
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
      markStepDone();
    }
  };

  const failStep = (failType) => {
    if (!playScore.value.stepStates || playLastDeviceStep.value === null) return;

    const lastStepState = playScore.value.stepStates[playLastDeviceStep.value];
    const lastStep = playProg.value[playLastDeviceStep.value];

    if (lastStepState.state === 'done') {
      const deduction = failType === 'both' ? 2 : 1;
      playScore.value.totalPoints -= deduction;

      if (lastStep.type === 'solo') {
        lastStepState.state = 'failed-both';
      } else {
        lastStepState.state = 'failed-' + failType;
      }
      moveToNextStep();
    }
  };

  const retryStep = () => {
    if (!playScore.value.stepStates || playLastDeviceStep.value === null) return;
    const state = playScore.value.stepStates[playLastDeviceStep.value];
    if (state && state.state === 'done') {
      state.state = 'pending';
      playScore.value.totalPoints -= state.pointValue;
      playCurrentStep.value = playLastDeviceStep.value;
      playPartialStep.value = null;
    }
  };

  const moveToNextStep = () => {
    if (playCurrentStep.value < playProg.value.length - 1) {
      playCurrentStep.value += 1;
      playPartialStep.value = null;
    } else {
      playLastDeviceStep.value = null;
    }
  };

  const closePlayback = () => {
    playProg.value = null;
    playCurrentStep.value = 0;
    playPartialStep.value = null;
    playRaffaleStarted.value = false;
    playScoreMode.value = false;
    playScore.value = { totalPoints: 0, stepStates: [] };
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
    playProgramWithScore, advancePlayStep, completeRaffaleStep,
    markStepDone, failStep, retryStep, closePlayback, getPointValueForStep,
    createPlaySession, loadPlaySession, clearPlaySession,
  };
});
