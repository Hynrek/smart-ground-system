import { defineStore } from 'pinia';
import { ref } from 'vue';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import { useAuthStore } from '@/stores/authStore.js';

const generateUUID = () => {
  if (typeof globalThis !== 'undefined' && globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};

export const useProgramStore = defineStore('program', () => {
  const recording = ref({});
  const programMode = ref(false);
  const pairPending = ref(null);
  const activeSegmentIndex = ref(0);
  const editingId = ref(null);
  const savedPrograms = ref([]);
  const editingSegments = ref([]);

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
          if (data.steps && !data.segments) {
            data.segments = [{ id: 's1', alias: null, steps: data.steps }];
          }
          return { id: key, name: data.programName, segments: data.segments };
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

  const resetCapture = () => {
    programMode.value = false;
    pairPending.value = null;
    activeSegmentIndex.value = 0;
  };

  const startCapture = () => {
    const shooterRemoteStore = useShooterRemoteStore();
    if (!shooterRemoteStore.isReserved) return;
    programMode.value = true;
    activeSegmentIndex.value = 0;
    editingSegments.value = [{ id: generateUUID(), alias: null, steps: [] }];
    editingId.value = null;
  };

  const cancelCapture = () => {
    programMode.value = false;
    pairPending.value = null;
    editingSegments.value = [];
    activeSegmentIndex.value = 0;
    editingId.value = null;
  };

  const addSegment = () => {
    const newSeg = { id: generateUUID(), alias: null, steps: [] };
    editingSegments.value = [...editingSegments.value, newSeg];
    activeSegmentIndex.value = editingSegments.value.length - 1;
  };

  const setActiveSegment = (index) => {
    activeSegmentIndex.value = index;
    pairPending.value = null;
  };

  const setSegmentAlias = (index, alias) => {
    const segs = [...editingSegments.value];
    segs[index] = { ...segs[index], alias: alias || null };
    editingSegments.value = segs;
  };

  const removeSegment = (index) => {
    if (editingSegments.value.length <= 1) return;
    editingSegments.value = editingSegments.value.filter((_, i) => i !== index);
    activeSegmentIndex.value = Math.min(activeSegmentIndex.value, editingSegments.value.length - 1);
  };

  const addStep = (deviceId, deviceData) => {
    const alias = deviceData.alias ?? 'Gerät';
    const shooterRemoteStore = useShooterRemoteStore();

    if (shooterRemoteStore.mode === 'solo') {
      recording.value = { ...recording.value, [deviceId]: true };
      setTimeout(() => {
        const r = { ...recording.value };
        delete r[deviceId];
        recording.value = r;
      }, 500);
      const step = { id: Date.now(), type: 'solo', alias, deviceId };
      const segs = [...editingSegments.value];
      segs[activeSegmentIndex.value].steps = [...segs[activeSegmentIndex.value].steps, step];
      editingSegments.value = segs;
    } else if (shooterRemoteStore.mode === 'raffale') {
      recording.value = { ...recording.value, [deviceId]: true };
      setTimeout(() => {
        const r = { ...recording.value };
        delete r[deviceId];
        recording.value = r;
      }, 500);
      const step = { id: Date.now(), type: 'raffale', alias, deviceId };
      const segs = [...editingSegments.value];
      segs[activeSegmentIndex.value].steps = [...segs[activeSegmentIndex.value].steps, step];
      editingSegments.value = segs;
      shooterRemoteStore.setMode('solo');
    } else if (shooterRemoteStore.mode === 'pair' || shooterRemoteStore.mode === 'a.schuss') {
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
        const stepType = shooterRemoteStore.mode === 'a.schuss' ? 'a.schuss' : 'pair';
        const step = {
          id: Date.now(),
          type: stepType,
          alias1: pendingAlias,
          alias2: alias,
          deviceId1: pendingId,
          deviceId2: deviceId,
        };
        const segs = [...editingSegments.value];
        segs[activeSegmentIndex.value].steps = [...segs[activeSegmentIndex.value].steps, step];
        editingSegments.value = segs;
        pairPending.value = null;
        shooterRemoteStore.setMode('solo');
      }
    }
  };

  const removeStep = (segmentIndex, stepId) => {
    const segs = [...editingSegments.value];
    segs[segmentIndex] = {
      ...segs[segmentIndex],
      steps: segs[segmentIndex].steps.filter((s) => s.id !== stepId),
    };
    editingSegments.value = segs;
  };

  const saveProgram = (programName = null) => {
    if (editingSegments.value.every((seg) => seg.steps.length === 0)) return;
    const name = programName || `Programm ${savedPrograms.value.length + 1}`;
    if (editingId.value !== null) {
      const idx = savedPrograms.value.findIndex((p) => p.id === editingId.value);
      if (idx !== -1) {
        savedPrograms.value[idx].segments = [...editingSegments.value];
        localStorage.setItem(
          editingId.value,
          JSON.stringify({
            programName: savedPrograms.value[idx].name,
            segments: [...editingSegments.value],
          })
        );
      }
    } else {
      const key = nextProgramKey();
      localStorage.setItem(
        key,
        JSON.stringify({ programName: name, segments: [...editingSegments.value] })
      );
      savedPrograms.value = [
        ...savedPrograms.value,
        { id: key, name, segments: [...editingSegments.value] },
      ];
    }
    cancelCapture();
  };

  const editProgram = (programId) => {
    const prog = savedPrograms.value.find((p) => p.id === programId);
    if (!prog) return;
    editingId.value = programId;
    editingSegments.value = prog.segments.map((seg) => ({ ...seg, steps: [...seg.steps] }));
    activeSegmentIndex.value = 0;
    programMode.value = true;
  };

  const deleteProgram = (programId) => {
    localStorage.removeItem(programId);
    savedPrograms.value = savedPrograms.value.filter((p) => p.id !== programId);
  };

  return {
    recording,
    programMode,
    pairPending,
    activeSegmentIndex,
    editingId,
    savedPrograms,
    editingSegments,
    resetCapture,
    startCapture,
    cancelCapture,
    addSegment,
    setActiveSegment,
    setSegmentAlias,
    removeSegment,
    addStep,
    removeStep,
    saveProgram,
    editProgram,
    deleteProgram,
    loadProgramsFromStorage,
  };
});
