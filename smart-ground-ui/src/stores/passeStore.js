import { defineStore } from 'pinia';
import { ref } from 'vue';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import * as ablaufApi from '@/services/ablaufApi.js';
import * as programmeApi from '@/services/programmeApi.js';
import * as trainingApi from '@/services/trainingApi.js';

const generateUUID = () =>
  typeof globalThis !== 'undefined' && globalThis.crypto?.randomUUID
    ? globalThis.crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
      });

// ── Step mapping: UI step ↔ backend Step schema ──────────────────────────────
// positionId (UUID) stored in posId; resolves directly when sending position commands

function toApiStep(step) {
  const base = { id: String(step.id), type: step.type };
  if (step.type === 'solo' || step.type === 'raffale') {
    return { ...base, posId: step.positionId ?? null, alias: step.alias ?? null, letter: step.letter ?? null };
  }
  return {
    ...base,
    posId1: step.positionId1 ?? null,
    posId2: step.positionId2 ?? null,
    alias1: step.alias1 ?? null,
    alias2: step.alias2 ?? null,
    letter1: step.letter1 ?? null,
    letter2: step.letter2 ?? null,
  };
}

function fromApiStep(step) {
  const base = { id: step.id, type: step.type };
  if (step.type === 'solo' || step.type === 'raffale') {
    return { ...base, positionId: step.posId ?? null, alias: step.alias ?? null, letter: step.letter ?? null };
  }
  return {
    ...base,
    positionId1: step.posId1 ?? null,
    positionId2: step.posId2 ?? null,
    alias1: step.alias1 ?? null,
    alias2: step.alias2 ?? null,
    letter1: step.letter1 ?? null,
    letter2: step.letter2 ?? null,
  };
}

function toUiSerie(ablauf) {
  return {
    id: ablauf.id,
    name: ablauf.name,
    rangeId: ablauf.rangeId ?? null,
    rangeName: ablauf.rangeName ?? null,
    steps: (ablauf.steps ?? []).map(fromApiStep),
    ownership: ablauf.ownership ?? 'user',
    createdAt: ablauf.createdAt ?? null,
    ownerUsername: ablauf.ownerUsername ?? null,
  };
}

function toUiPasse(programme) {
  return {
    id: programme.id,
    name: programme.name,
    serien: (programme.ablaeufe ?? []).map((a) => ({
      id: a.id,
      alias: a.alias,
      rangeId: a.rangeId ?? null,
      rangeName: a.rangeName ?? null,
      steps: (a.steps ?? []).map(fromApiStep),
    })),
    ownerUsername: programme.ownerUsername ?? null,
  };
}

function toUiTraining(training) {
  return {
    id: training.id,
    name: training.name,
    passen: (training.programmes ?? []).map((prog) => ({
      id: prog.id,
      name: prog.name,
      serien: (prog.ablaeufe ?? []).map((a) => ({
        id: a.id,
        alias: a.alias,
        rangeId: a.rangeId ?? null,
        rangeName: a.rangeName ?? null,
        steps: (a.steps ?? []).map(fromApiStep),
      })),
    })),
    ownerUsername: training.ownerUsername ?? null,
  };
}

export const usePasseStore = defineStore('passe', () => {
  // ── Capture state (in-memory only, no persistence) ────────────────────────
  const recording = ref({});
  const passeMode = ref(false);
  const pairPending = ref(null);
  const activeSerieIndex = ref(0);
  const editingId = ref(null);
  const editingSerie = ref([]);

  // ── Persisted state (backed by backend API) ────────────────────────────────
  const savedSerien = ref([]);
  const savedPassen = ref([]);
  const savedGlobalPassen = ref([]);
  const savedTrainings = ref([]);
  const pendingPasseId = ref(null);

  // ── Load from backend API ──────────────────────────────────────────────────

  const loadSerienFromStorage = async () => {
    try {
      const ablaeufe = await ablaufApi.fetchAblaeufe();
      savedSerien.value = ablaeufe.map(toUiSerie);
    } catch (e) {
      console.error('Failed to load Abläufe from API:', e);
    }
  };

  const loadPassenFromStorage = async () => {
    try {
      const programmes = await programmeApi.fetchProgrammes();
      savedPassen.value = programmes.map(toUiPasse);
      savedGlobalPassen.value = [];
    } catch (e) {
      console.error('Failed to load Programmes from API:', e);
    }
  };

  const loadGlobalPassenFromStorage = async () => {
    // Global passen are unified with savedPassen (both are Programmes on the backend).
    // No-op kept for API compatibility with existing callers.
  };

  const loadTrainingsFromStorage = async () => {
    try {
      const trainings = await trainingApi.fetchTrainings();
      savedTrainings.value = trainings.map(toUiTraining);
    } catch (e) {
      console.error('Failed to load Trainings from API:', e);
    }
  };

  // ── Capture lifecycle ──────────────────────────────────────────────────────

  const resetCapture = () => {
    passeMode.value = false;
    pairPending.value = null;
    activeSerieIndex.value = 0;
  };

  const startCapture = () => {
    const shooterRemoteStore = useShooterRemoteStore();
    if (!shooterRemoteStore.isReserved) return;
    passeMode.value = true;
    activeSerieIndex.value = 0;
    editingSerie.value = [{ id: generateUUID(), alias: null, steps: [] }];
    editingId.value = null;
  };

  const cancelCapture = () => {
    passeMode.value = false;
    pairPending.value = null;
    editingSerie.value = [];
    activeSerieIndex.value = 0;
    editingId.value = null;
  };

  // ── Step recording (in-memory) ────────────────────────────────────────────

  const addStep = (positionId, position, positionLabel) => {
    const alias = position.device?.alias ?? position.label;
    const letter = positionLabel;
    const shooterRemoteStore = useShooterRemoteStore();

    if (shooterRemoteStore.mode === 'solo') {
      recording.value = { ...recording.value, [positionId]: true };
      setTimeout(() => {
        const r = { ...recording.value };
        delete r[positionId];
        recording.value = r;
      }, 500);
      const step = { id: Date.now(), type: 'solo', alias, positionId, letter };
      const segs = [...editingSerie.value];
      segs[0].steps = [...segs[0].steps, step];
      editingSerie.value = segs;
    } else if (shooterRemoteStore.mode === 'raffale') {
      recording.value = { ...recording.value, [positionId]: true };
      setTimeout(() => {
        const r = { ...recording.value };
        delete r[positionId];
        recording.value = r;
      }, 500);
      const step = { id: Date.now(), type: 'raffale', alias, positionId, letter };
      const segs = [...editingSerie.value];
      segs[0].steps = [...segs[0].steps, step];
      editingSerie.value = segs;
      shooterRemoteStore.setMode('solo');
    } else if (shooterRemoteStore.mode === 'pair' || shooterRemoteStore.mode === 'a_schuss') {
      if (!pairPending.value) {
        pairPending.value = { id: positionId, alias, letter };
      } else if (pairPending.value.id === positionId) {
        pairPending.value = null;
      } else {
        const pendingId = pairPending.value.id;
        const pendingAlias = pairPending.value.alias;
        recording.value = { ...recording.value, [positionId]: true, [pendingId]: true };
        setTimeout(() => {
          const r = { ...recording.value };
          delete r[positionId];
          delete r[pendingId];
          recording.value = r;
        }, 500);
        const stepType = shooterRemoteStore.mode === 'a_schuss' ? 'a_schuss' : 'pair';
        const step = {
          id: Date.now(),
          type: stepType,
          alias1: pendingAlias,
          alias2: alias,
          positionId1: pendingId,
          positionId2: positionId,
        };
        const segs = [...editingSerie.value];
        segs[0].steps = [...segs[0].steps, step];
        editingSerie.value = segs;
        pairPending.value = null;
        shooterRemoteStore.setMode('solo');
      }
    }
  };

  const removeStep = (serieIndex, stepId) => {
    const segs = [...editingSerie.value];
    segs[serieIndex] = {
      ...segs[serieIndex],
      steps: segs[serieIndex].steps.filter((s) => s.id !== stepId),
    };
    editingSerie.value = segs;
  };

  // ── Serie (Ablauf) persistence ────────────────────────────────────────────

  const saveSerie = async (serieName, rangeId = null, rangeName = null, ownership = 'user') => {
    const steps = editingSerie.value[0]?.steps ?? [];
    if (steps.length === 0) return;
    const name = serieName?.trim() || `Serie ${savedSerien.value.length + 1}`;
    const apiSteps = steps.map(toApiStep);
    try {
      const created = await ablaufApi.createAblauf(name, apiSteps, rangeId, ownership);
      savedSerien.value = [...savedSerien.value, toUiSerie({ ...created, rangeName })];
      cancelCapture();
    } catch (e) {
      console.error('Failed to save Serie:', e);
      throw e;
    }
  };

  const deleteSerie = async (serieId) => {
    try {
      await ablaufApi.deleteAblauf(serieId);
      savedSerien.value = savedSerien.value.filter((s) => s.id !== serieId);
    } catch (e) {
      console.error('Failed to delete Serie:', e);
      throw e;
    }
  };

  const renameSerie = async (serieId, newName) => {
    const serie = savedSerien.value.find((s) => s.id === serieId);
    if (!serie) return;
    try {
      await ablaufApi.updateAblauf(serieId, newName, serie.rangeId ?? null);
      serie.name = newName;
    } catch (e) {
      console.error('Failed to rename Serie:', e);
      throw e;
    }
  };

  const createRangeSerie = async (name, rangeId, rangeName, steps) => {
    if (!steps || steps.length === 0) return;
    const trimmedName = name?.trim() || `Serie ${savedSerien.value.length + 1}`;
    const apiSteps = steps.map(toApiStep);
    try {
      const created = await ablaufApi.createAblauf(trimmedName, apiSteps, rangeId, 'range');
      savedSerien.value = [...savedSerien.value, toUiSerie({ ...created, rangeName })];
    } catch (e) {
      console.error('Failed to create range Serie:', e);
      throw e;
    }
  };

  const updateSerie = async (serieId, newName, newSteps) => {
    const serie = savedSerien.value.find((s) => s.id === serieId);
    if (!serie) return;
    // Backend PUT only accepts name/rangeId — replace via delete+create to persist steps
    try {
      await ablaufApi.deleteAblauf(serieId);
      const apiSteps = (newSteps ?? []).map(toApiStep);
      const created = await ablaufApi.createAblauf(newName, apiSteps, serie.rangeId ?? null, serie.ownership ?? 'user');
      savedSerien.value = savedSerien.value.map((s) =>
        s.id === serieId ? toUiSerie({ ...created, rangeName: serie.rangeName }) : s,
      );
      // Ablauf ID changed — reload Passen to repair any Programme references
      console.warn('[passeStore] updateSerie: Ablauf ID changed from', serieId, 'to', created.id, '— reloading Passen.');
      loadPassenFromStorage().catch(console.error);
    } catch (e) {
      console.error('Failed to update Serie:', e);
      throw e;
    }
  };

  // ── Passe (Programme) persistence ─────────────────────────────────────────

  const createPasse = async (passeName, selectedSerien) => {
    if (selectedSerien.length === 0) return;
    const name = passeName?.trim() || `Passe ${savedPassen.value.length + 1}`;
    const ablaufIds = selectedSerien.map((s) => s.id);
    try {
      const created = await programmeApi.createProgramme(name, ablaufIds);
      savedPassen.value = [...savedPassen.value, toUiPasse(created)];
    } catch (e) {
      console.error('Failed to create Passe:', e);
      throw e;
    }
  };

  const deletePasse = async (passeId) => {
    try {
      await programmeApi.deleteProgramme(passeId);
      savedPassen.value = savedPassen.value.filter((p) => p.id !== passeId);
      if (pendingPasseId.value === passeId) pendingPasseId.value = null;
    } catch (e) {
      console.error('Failed to delete Passe:', e);
      throw e;
    }
  };

  const renamePasse = async (passeId, newName) => {
    try {
      await programmeApi.updateProgramme(passeId, newName);
      savedPassen.value = savedPassen.value.map((p) =>
        p.id === passeId ? { ...p, name: newName } : p,
      );
    } catch (e) {
      console.error('Failed to rename Passe:', e);
      throw e;
    }
  };

  // Global passen merged into savedPassen — these are aliases for backward compat
  const createGlobalPasse = (name, selectedSerien) => createPasse(name, selectedSerien);
  const updateGlobalPasse = async (id, newName, _selectedSerien) => {
    // Note: backend PUT /api/programmes/{id} only supports renaming.
    // Updating Ablauf membership requires a backend API extension.
    if (_selectedSerien !== undefined) {
      console.warn('updateGlobalPasse: updating Ablauf membership is not yet supported by the backend API. Only the name will be saved.');
    }
    return renamePasse(id, newName);
  };
  const deleteGlobalPasse = (id) => deletePasse(id);

  // ── Training persistence ───────────────────────────────────────────────────

  const createTraining = async (trainingName, selectedPassen, _options = {}) => {
    if (selectedPassen.length === 0) return;
    const name = trainingName?.trim() || `Training ${savedTrainings.value.length + 1}`;
    const programmeIds = selectedPassen.map((p) => p.id);
    try {
      const created = await trainingApi.createTraining(name, programmeIds);
      savedTrainings.value = [...savedTrainings.value, toUiTraining(created)];
    } catch (e) {
      console.error('Failed to create Training:', e);
      throw e;
    }
  };

  const createCompetition = (name, selectedPassen, rottCountHint = null) => {
    return createTraining(name, selectedPassen, { type: 'competition', rottCountHint });
  };

  const deleteTraining = async (trainingId) => {
    try {
      await trainingApi.deleteTraining(trainingId);
      savedTrainings.value = savedTrainings.value.filter((t) => t.id !== trainingId);
    } catch (e) {
      console.error('Failed to delete Training:', e);
      throw e;
    }
  };

  const renameTraining = async (trainingId, newName) => {
    try {
      await trainingApi.updateTraining(trainingId, newName);
      savedTrainings.value = savedTrainings.value.map((t) =>
        t.id === trainingId ? { ...t, name: newName } : t,
      );
    } catch (e) {
      console.error('Failed to rename Training:', e);
      throw e;
    }
  };

  // ── Pending passe ──────────────────────────────────────────────────────────

  const setPendingPasse = (passeId) => { pendingPasseId.value = passeId; };
  const clearPendingPasse = () => { pendingPasseId.value = null; };

  // ── Serie retrieval helpers ────────────────────────────────────────────────

  const getUserSerien = () => savedSerien.value.filter((s) => s.ownership === 'user');
  const getGlobalSerien = () => savedSerien.value.filter((s) => s.ownership === 'range');
  const getSerienForRange = (rangeId) => savedSerien.value.filter((s) => s.rangeId === rangeId);
  const getUserSerienForRange = (rangeId) => getUserSerien().filter((s) => s.rangeId === rangeId);

  // ── Legacy ─────────────────────────────────────────────────────────────────

  /** @deprecated use saveSerie */
  const savePasse = async (passeName = null) => {
    if (editingSerie.value.every((s) => s.steps.length === 0)) return;
    await saveSerie(passeName);
  };

  return {
    // Capture state
    recording, passeMode, pairPending, activeSerieIndex, editingId, editingSerie,
    // Persisted state
    savedSerien, savedPassen, pendingPasseId, savedTrainings, savedGlobalPassen,
    // Capture lifecycle
    resetCapture, startCapture, cancelCapture,
    // Step recording
    addStep, removeStep,
    // Serie actions
    saveSerie, deleteSerie, renameSerie, createRangeSerie, updateSerie,
    loadSerienFromStorage,
    // Serie retrieval
    getUserSerien, getGlobalSerien, getSerienForRange, getUserSerienForRange,
    // Passe actions
    createPasse, deletePasse, renamePasse, setPendingPasse, clearPendingPasse,
    loadPassenFromStorage,
    // Global Passe actions (aliases)
    createGlobalPasse, updateGlobalPasse, deleteGlobalPasse, loadGlobalPassenFromStorage,
    // Training actions
    loadTrainingsFromStorage, createTraining, createCompetition, deleteTraining, renameTraining,
    // Legacy
    savePasse,
  };
});
