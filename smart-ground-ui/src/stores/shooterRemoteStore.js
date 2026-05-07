import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { sendDeviceCommand } from '@/services/deviceApi.js';

export const useShooterRemoteStore = defineStore('shooterRemote', () => {
  const selectedRangeId = ref(null);
  const reservedByMe = ref(false);
  const selectedGroupId = ref(null);

  const mode = ref('solo'); // 'solo' | 'pair'
  const recording = ref({}); // Record<deviceId, boolean>

  const programMode = ref(false);
  const pairPending = ref(null); // { id, alias } | null
  const ablauf = ref([]);
  const savedPrograms = ref([]);
  const editingId = ref(null);

  const playProg = ref(null);
  const playCurrentStep = ref(0);

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
    } else {
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
        ablauf.value = [
          ...ablauf.value,
          { id: Date.now(), type: 'pair', alias1: pendingAlias, alias2: alias, deviceId1: pendingId, deviceId2: deviceId },
        ];
        pairPending.value = null;
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
  };

  const advancePlayStep = async () => {
    if (!playProg.value) return;

    // Send command(s) for current step before advancing
    const currentStep = playProg.value[playCurrentStep.value];
    if (currentStep) {
      try {
        if (currentStep.type === 'solo') {
          // Send command for single device
          await sendDeviceCommand(currentStep.deviceId);
        } else if (currentStep.type === 'pair') {
          // Send commands for both devices in pair
          await Promise.all([
            sendDeviceCommand(currentStep.deviceId1),
            sendDeviceCommand(currentStep.deviceId2),
          ]);
        }
      } catch (err) {
        console.error('Failed to send device command during playback:', err);
      }
    }

    // Advance to next step
    if (playCurrentStep.value < playProg.value.length - 1) {
      playCurrentStep.value += 1;
    } else {
      closePlayback();
    }
  };

  const closePlayback = () => {
    playProg.value = null;
    playCurrentStep.value = 0;
  };

  return {
    selectedRangeId, reservedByMe, selectedGroupId,
    mode, recording,
    programMode, pairPending, ablauf, savedPrograms, editingId,
    playProg, playCurrentStep,
    isReserved, playProgress,
    reservePlatz, ensureReserved, releasePlatz, setGroupId,
    setMode, addStep, removeStep, clearAblauf,
    startCapture, cancelCapture, saveProgram, editProgram, deleteProgram,
    playProgram, advancePlayStep, closePlayback,
  };
});
