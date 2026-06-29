import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import * as serieApi from '@/services/serieApi.js';
import * as passeApi from '@/services/passeApi.js';

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
    published: ablauf.published ?? false,
  };
}

function toUiPasse(passe) {
  return {
    id: passe.id,
    name: passe.name,
    serien: (passe.serien ?? []).map((s) => ({
      id: s.id,
      alias: s.alias,
      rangeId: s.rangeId ?? null,
      rangeName: s.rangeName ?? null,
      steps: (s.steps ?? []).map(fromApiStep),
    })),
    ownerUsername: passe.ownerUsername ?? null,
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
  const savedGlobalPassen = computed(() => savedPassen.value);
  const pendingPasseId = ref(null);

  // ── Load from backend API ──────────────────────────────────────────────────

  const loadSerienFromStorage = async () => {
    try {
      const serien = await serieApi.fetchSerien();
      savedSerien.value = serien.map(toUiSerie);
    } catch (e) {
      console.error('Failed to load Serien from API:', e);
    }
  };

  const loadPassenFromStorage = async () => {
    try {
      const passen = await passeApi.fetchPassen();
      savedPassen.value = passen.map(toUiPasse);
    } catch (e) {
      console.error('Failed to load Passen from API:', e);
    }
  };

  const loadGlobalPassenFromStorage = async () => {
    // Global passen are unified with savedPassen (both are Programmes on the backend).
    // No-op kept for API compatibility with existing callers.
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

  // Drop the current capture/edit but stay in recording mode: returns to the
  // empty init state (fresh draft serie, no steps) so the operator can keep
  // capturing without leaving the recording session.
  const clearEditingSteps = () => {
    pairPending.value = null;
    editingId.value = null;
    activeSerieIndex.value = 0;
    editingSerie.value = [{ id: generateUUID(), alias: null, steps: [] }];
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
        const pendingLetter = pairPending.value.letter;
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
          letter1: pendingLetter,
          letter2: letter,
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
      const created = await serieApi.createSerie(name, apiSteps, rangeId, ownership);
      savedSerien.value = [...savedSerien.value, toUiSerie({ ...created, rangeName })];
      cancelCapture();
    } catch (e) {
      console.error('Failed to save Serie:', e);
      throw e;
    }
  };

  const deleteSerie = async (serieId) => {
    try {
      await serieApi.deleteSerie(serieId);
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
      await serieApi.updateSerie(serieId, newName, serie.rangeId ?? null);
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
      const created = await serieApi.createSerie(trimmedName, apiSteps, rangeId, 'range');
      savedSerien.value = [...savedSerien.value, toUiSerie({ ...created, rangeName })];
    } catch (e) {
      console.error('Failed to create range Serie:', e);
      throw e;
    }
  };

  const updateSerie = async (serieId, newName, newSteps) => {
    const serie = savedSerien.value.find((s) => s.id === serieId);
    if (!serie) return;
    // Backend PUT accepts steps -> in-place edit keeps the stable Serie ID,
    // so referencing Passen never need repair.
    try {
      const apiSteps = (newSteps ?? []).map(toApiStep);
      const updated = await serieApi.updateSerie(serieId, newName, serie.rangeId ?? null, apiSteps);
      savedSerien.value = savedSerien.value.map((s) =>
        s.id === serieId ? toUiSerie({ ...updated, rangeName: serie.rangeName }) : s,
      );
    } catch (e) {
      console.error('Failed to update Serie:', e);
      throw e;
    }
  };

  const setSeriePublished = async (serieId, published) => {
    const serie = savedSerien.value.find((s) => s.id === serieId);
    if (!serie) return;
    serie.published = published;
    try {
      await serieApi.patchSeriePublished(serieId, published);
    } catch (e) {
      serie.published = !published;
      throw e;
    }
  };

  // ── Passe (Programme) persistence ─────────────────────────────────────────

  const createPasse = async (passeName, selectedSerien) => {
    if (selectedSerien.length === 0) return;
    const name = passeName?.trim() || `Passe ${savedPassen.value.length + 1}`;
    const serieIds = selectedSerien.map((s) => s.id);
    try {
      const created = await passeApi.createPasse(name, serieIds);
      savedPassen.value = [...savedPassen.value, toUiPasse(created)];
    } catch (e) {
      console.error('Failed to create Passe:', e);
      throw e;
    }
  };

  const deletePasse = async (passeId) => {
    try {
      await passeApi.deletePasse(passeId);
      savedPassen.value = savedPassen.value.filter((p) => p.id !== passeId);
      if (pendingPasseId.value === passeId) pendingPasseId.value = null;
    } catch (e) {
      console.error('Failed to delete Passe:', e);
      throw e;
    }
  };

  const renamePasse = async (passeId, newName) => {
    try {
      await passeApi.updatePasse(passeId, newName);
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
    // Note: backend PUT /api/passen/{id} only supports renaming.
    // Updating Serie membership requires a backend API extension.
    if (_selectedSerien !== undefined) {
      console.warn('updateGlobalPasse: updating Serie membership is not yet supported by the backend API. Only the name will be saved.');
    }
    return renamePasse(id, newName);
  };
  const deleteGlobalPasse = (id) => deletePasse(id);

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
    savedSerien, savedPassen, pendingPasseId, savedGlobalPassen,
    // Capture lifecycle
    resetCapture, startCapture, cancelCapture, clearEditingSteps,
    // Step recording
    addStep, removeStep,
    // Serie actions
    saveSerie, deleteSerie, renameSerie, createRangeSerie, updateSerie, setSeriePublished,
    loadSerienFromStorage,
    // Serie retrieval
    getUserSerien, getGlobalSerien, getSerienForRange, getUserSerienForRange,
    // Passe actions
    createPasse, deletePasse, renamePasse, setPendingPasse, clearPendingPasse,
    loadPassenFromStorage,
    // Global Passe actions (aliases)
    createGlobalPasse, updateGlobalPasse, deleteGlobalPasse, loadGlobalPassenFromStorage,
    // Legacy
    savePasse,
  };
});
