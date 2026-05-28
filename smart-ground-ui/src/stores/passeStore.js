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

export const usePasseStore = defineStore('passe', () => {
  // ── Capture state (Erfassen-Modus) ───────────────────────────────────────
  const recording = ref({});
  const passeMode = ref(false);
  const pairPending = ref(null);
  const activeSerieIndex = ref(0);
  const editingId = ref(null);
  const editingSerie = ref([]);

  // ── Persisted state ───────────────────────────────────────────────────────
  // Alle Serien: user-eigene (ownership:'user') + platzweite (ownership:'range')
  const savedSerien = ref([]);
  const savedPassen = ref([]);
  const pendingPasseId = ref(null);
  const savedGlobalPassen = ref([]);

  // ── Storage key helpers ───────────────────────────────────────────────────
  // Platzweite Serien — global, nicht benutzerspezifisch
  const RANGE_SERIE_PREFIX = '_sg_range_serie_';
  const GLOBAL_PASSE_PREFIX = '_sg_global_passe_';

  const getPassePrefix = () => {
    const authStore = useAuthStore();
    return `${authStore.userName ?? 'anonymous'}_passe_`;
  };

  const getSeriePrefix = () => {
    const authStore = useAuthStore();
    return `${authStore.userName ?? 'anonymous'}_serie_`;
  };

  const nextPasseKey = () => {
    const prefix = getPassePrefix();
    const existing = Object.keys(localStorage)
      .filter((k) => k.startsWith(prefix))
      .map((k) => parseInt(k.slice(prefix.length), 10))
      .filter((n) => !isNaN(n));
    return `${prefix}${existing.length > 0 ? Math.max(...existing) + 1 : 1}`;
  };

  const nextSerieKey = () => {
    const prefix = getSeriePrefix();
    const existing = Object.keys(localStorage)
      .filter((k) => k.startsWith(prefix))
      .map((k) => parseInt(k.slice(prefix.length), 10))
      .filter((n) => !isNaN(n));
    return `${prefix}${existing.length > 0 ? Math.max(...existing) + 1 : 1}`;
  };

  const nextRangeSerieKey = () => {
    const existing = Object.keys(localStorage)
      .filter((k) => k.startsWith(RANGE_SERIE_PREFIX))
      .map((k) => parseInt(k.slice(RANGE_SERIE_PREFIX.length), 10))
      .filter((n) => !isNaN(n));
    return `${RANGE_SERIE_PREFIX}${existing.length > 0 ? Math.max(...existing) + 1 : 1}`;
  };

  const nextGlobalPasseKey = () => {
    const existing = Object.keys(localStorage)
      .filter((k) => k.startsWith(GLOBAL_PASSE_PREFIX))
      .map((k) => parseInt(k.slice(GLOBAL_PASSE_PREFIX.length), 10))
      .filter((n) => !isNaN(n));
    return `${GLOBAL_PASSE_PREFIX}${existing.length > 0 ? Math.max(...existing) + 1 : 1}`;
  };

  // ── Load from localStorage ────────────────────────────────────────────────
  const loadPassenFromStorage = () => {
    const prefix = getPassePrefix();
    savedPassen.value = Object.keys(localStorage)
      .filter((k) => k.startsWith(prefix))
      .map((key) => {
        try {
          const data = JSON.parse(localStorage.getItem(key));
          // Backward compat: old format had top-level steps array
          if (data.steps && !data.serien && !data.segments) {
            data.serien = [{ id: 's1', alias: null, steps: data.steps }];
          }
          // Backward compat: old format used 'segments' or 'ablaeufe' key
          if (!data.serien && data.segments) {
            data.serien = data.segments;
          }
          if (!data.serien && data.ablaeufe) {
            data.serien = data.ablaeufe;
          }
          return {
            id: key,
            name: data.passeName ?? data.programName,
            serien: (data.serien ?? []).map((s) => ({
              ...s,
              rangeId: s.rangeId ?? null,
              rangeName: s.rangeName ?? null,
            })),
          };
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
  };

  const loadSerienFromStorage = () => {
    const parseSerie = (key, defaultOwnership) => {
      try {
        const data = JSON.parse(localStorage.getItem(key));
        return {
          id: key,
          name: data.serieName ?? data.ablaufName ?? data.segmentName,   // backward compat
          rangeId: data.rangeId ?? null,
          rangeName: data.rangeName ?? null,
          steps: data.steps ?? [],
          createdAt: data.createdAt ?? 0,
          ownership: data.ownership ?? defaultOwnership,
        };
      } catch {
        return null;
      }
    };

    const userPrefix = getSeriePrefix();
    const userSerien = Object.keys(localStorage)
      .filter((k) => k.startsWith(userPrefix))
      .map((k) => parseSerie(k, 'user'))
      .filter(Boolean);

    const rangeSerien = Object.keys(localStorage)
      .filter((k) => k.startsWith(RANGE_SERIE_PREFIX))
      .map((k) => parseSerie(k, 'range'))
      .filter(Boolean);

    savedSerien.value = [...userSerien, ...rangeSerien]
      .sort((a, b) => a.createdAt - b.createdAt);
  };

  const loadGlobalPassenFromStorage = () => {
    savedGlobalPassen.value = Object.keys(localStorage)
      .filter((k) => k.startsWith(GLOBAL_PASSE_PREFIX))
      .map((key) => {
        try {
          const data = JSON.parse(localStorage.getItem(key));
          return {
            id: key,
            name: data.passeName,
            serien: data.serien ?? [],
            createdAt: data.createdAt ?? 0,
            ownership: 'global',
          };
        } catch {
          return null;
        }
      })
      .filter(Boolean)
      .sort((a, b) => a.createdAt - b.createdAt);
  };

  loadPassenFromStorage();
  loadSerienFromStorage();
  loadGlobalPassenFromStorage();

  // ── Capture lifecycle ─────────────────────────────────────────────────────
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

  // ── Step recording ────────────────────────────────────────────────────────
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

  // ── Serie persistence ────────────────────────────────────────────────────
  /**
   * Speichert die aktuelle Erfassungs-Serie.
   * ownership: 'user' (privat) | 'range' (platzweit sichtbar, nur Admin/Owner)
   */
  const saveSerie = (serieName, rangeId = null, rangeName = null, ownership = 'user') => {
    const steps = editingSerie.value[0]?.steps ?? [];
    if (steps.length === 0) return;
    const name = serieName?.trim() || `Serie ${savedSerien.value.length + 1}`;
    const key = ownership === 'range' ? nextRangeSerieKey() : nextSerieKey();
    const createdAt = Date.now();
    const data = { serieName: name, rangeId, rangeName, steps, createdAt, ownership };
    localStorage.setItem(key, JSON.stringify(data));
    savedSerien.value = [
      ...savedSerien.value,
      { id: key, name, rangeId, rangeName, steps: [...steps], createdAt, ownership },
    ];
    cancelCapture();
  };

  const deleteSerie = (serieId) => {
    localStorage.removeItem(serieId);
    savedSerien.value = savedSerien.value.filter((s) => s.id !== serieId);
  };

  const renameSerie = (serieId, newName) => {
    const serie = savedSerien.value.find((s) => s.id === serieId);
    if (!serie) return;
    serie.name = newName;
    try {
      const stored = JSON.parse(localStorage.getItem(serieId));
      if (stored) {
        stored.serieName = newName;
        localStorage.setItem(serieId, JSON.stringify(stored));
      }
    } catch { /* ignorieren */ }
  };

  const createRangeSerie = (name, rangeId, rangeName, steps) => {
    if (!steps || steps.length === 0) return;
    const trimmedName = name?.trim() || `Serie ${savedSerien.value.length + 1}`;
    const key = nextRangeSerieKey();
    const createdAt = Date.now();
    const data = { serieName: trimmedName, rangeId, rangeName, steps, createdAt, ownership: 'range' };
    localStorage.setItem(key, JSON.stringify(data));
    savedSerien.value = [
      ...savedSerien.value,
      { id: key, name: trimmedName, rangeId, rangeName, steps: [...steps], createdAt, ownership: 'range' },
    ];
  };

  const updateSerie = (serieId, newName, newSteps) => {
    const exists = savedSerien.value.some((s) => s.id === serieId);
    if (!exists) return;
    savedSerien.value = savedSerien.value.map((s) =>
      s.id === serieId ? { ...s, name: newName, steps: [...(newSteps ?? [])] } : s
    );
    try {
      const stored = JSON.parse(localStorage.getItem(serieId));
      if (stored) {
        stored.serieName = newName;
        stored.steps = newSteps ?? [];
        localStorage.setItem(serieId, JSON.stringify(stored));
      }
    } catch { /* ignorieren */ }
  };

  const createGlobalPasse = (name, selectedSerien) => {
    if (!selectedSerien || selectedSerien.length === 0) return;
    const trimmedName = name?.trim() || `Passe ${savedGlobalPassen.value.length + 1}`;
    const key = nextGlobalPasseKey();
    const createdAt = Date.now();
    const serien = selectedSerien.map((s) => ({
      id: s.id,
      alias: s.name,
      rangeId: s.rangeId,
      rangeName: s.rangeName,
      steps: [...(s.steps ?? [])],
    }));
    localStorage.setItem(key, JSON.stringify({ passeName: trimmedName, serien, createdAt, ownership: 'global' }));
    savedGlobalPassen.value = [
      ...savedGlobalPassen.value,
      { id: key, name: trimmedName, serien, createdAt, ownership: 'global' },
    ];
  };

  const updateGlobalPasse = (id, newName, newSerien) => {
    const exists = savedGlobalPassen.value.some((p) => p.id === id);
    if (!exists) return;
    const serien = (newSerien ?? []).map((s) => ({
      id: s.id,
      alias: s.name,
      rangeId: s.rangeId,
      rangeName: s.rangeName,
      steps: [...(s.steps ?? [])],
    }));
    savedGlobalPassen.value = savedGlobalPassen.value.map((p) =>
      p.id === id ? { ...p, name: newName, serien } : p
    );
    try {
      const stored = JSON.parse(localStorage.getItem(id));
      if (stored) {
        stored.passeName = newName;
        stored.serien = serien;
        localStorage.setItem(id, JSON.stringify(stored));
      }
    } catch { /* ignorieren */ }
  };

  const deleteGlobalPasse = (id) => {
    localStorage.removeItem(id);
    savedGlobalPassen.value = savedGlobalPassen.value.filter((p) => p.id !== id);
  };

  // ── Passe persistence ───────────────────────────────────────────────────────
  /**
   * Erstellt eine Passe aus einer Liste von Serie-Objekten.
   * selectedSerien: Array von { id, name, rangeId, rangeName, steps }
   */
  const createPasse = (passeName, selectedSerien) => {
    if (selectedSerien.length === 0) return;
    const name = passeName?.trim() || `Passe ${savedPassen.value.length + 1}`;
    const key = nextPasseKey();
    // Serien werden vollständig eingebettet (Snapshot für Playback)
    const serien = selectedSerien.map((s) => ({
      id: s.id,
      alias: s.name,
      rangeId: s.rangeId,
      rangeName: s.rangeName,
      steps: [...s.steps],
    }));
    localStorage.setItem(key, JSON.stringify({ passeName: name, serien }));
    savedPassen.value = [...savedPassen.value, { id: key, name, serien }];
  };

  const deletePasse = (passeId) => {
    localStorage.removeItem(passeId);
    savedPassen.value = savedPassen.value.filter((p) => p.id !== passeId);
    if (pendingPasseId.value === passeId) pendingPasseId.value = null;
  };

  const renamePasse = (passeId, newName) => {
    const passe = savedPassen.value.find((p) => p.id === passeId);
    if (!passe) return;
    passe.name = newName;
    try {
      const stored = JSON.parse(localStorage.getItem(passeId));
      if (stored) {
        stored.passeName = newName;
        localStorage.setItem(passeId, JSON.stringify(stored));
      }
    } catch { /* ignorieren */ }
  };

  // ── Pending passe (für "Starten" aus Passen-Verwaltung) ───────────────
  const setPendingPasse = (passeId) => {
    pendingPasseId.value = passeId;
  };

  const clearPendingPasse = () => {
    pendingPasseId.value = null;
  };

  // ── Legacy: savePasse ───────────────────────────────────────────────────────
  /** @deprecated Wird durch saveSerie ersetzt. */
  const savePasse = (passeName = null) => {
    if (editingSerie.value.every((s) => s.steps.length === 0)) return;
    const name = passeName || `Passe ${savedPassen.value.length + 1}`;
    const key = nextPasseKey();
    localStorage.setItem(key, JSON.stringify({ passeName: name, serien: [...editingSerie.value] }));
    savedPassen.value = [
      ...savedPassen.value,
      { id: key, name, serien: [...editingSerie.value] },
    ];
    cancelCapture();
  };

  // ── Training template persistence ─────────────────────────────────────────
  const savedTrainings = ref([]);

  const getTrainingPrefix = () => {
    const authStore = useAuthStore();
    return `${authStore.userName ?? 'anonymous'}_training_`;
  };

  const nextTrainingKey = () => {
    const prefix = getTrainingPrefix();
    const existing = Object.keys(localStorage)
      .filter((k) => k.startsWith(prefix))
      .map((k) => parseInt(k.slice(prefix.length), 10))
      .filter((n) => !isNaN(n));
    return `${prefix}${existing.length > 0 ? Math.max(...existing) + 1 : 1}`;
  };

  const loadTrainingsFromStorage = () => {
    const prefix = getTrainingPrefix();
    savedTrainings.value = Object.keys(localStorage)
      .filter((k) => k.startsWith(prefix))
      .map((key) => {
        try {
          const data = JSON.parse(localStorage.getItem(key));
          return { id: key, name: data.trainingName, passen: data.passen ?? data.programmes ?? [] };
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
  };

  const createTraining = (trainingName, selectedPassen) => {
    if (selectedPassen.length === 0) return;
    const name = trainingName?.trim() || `Training ${savedTrainings.value.length + 1}`;
    const key = nextTrainingKey();
    const passen = selectedPassen.map((passe) => ({
      id: passe.id,
      name: passe.name,
      serien: passe.serien.map((s) => ({ ...s, steps: [...(s.steps ?? [])] })),
    }));
    localStorage.setItem(key, JSON.stringify({ trainingName: name, passen }));
    savedTrainings.value = [...savedTrainings.value, { id: key, name, passen }];
  };

  const deleteTraining = (trainingId) => {
    localStorage.removeItem(trainingId);
    savedTrainings.value = savedTrainings.value.filter((t) => t.id !== trainingId);
  };

  const renameTraining = (trainingId, newName) => {
    const training = savedTrainings.value.find((t) => t.id === trainingId);
    if (!training) return;
    training.name = newName;
    try {
      const stored = JSON.parse(localStorage.getItem(trainingId));
      if (stored) {
        stored.trainingName = newName;
        localStorage.setItem(trainingId, JSON.stringify(stored));
      }
    } catch { /* ignorieren */ }
  };

  loadTrainingsFromStorage();

  // ── Serie retrieval by category ────────────────────────────────────────────
  /**
   * Gets user-created Serien (ownership: 'user')
   */
  const getUserSerien = () => {
    return savedSerien.value.filter((s) => s.ownership === 'user');
  };

  /**
   * Gets globally published Serien (ownership: 'range')
   */
  const getGlobalSerien = () => {
    return savedSerien.value.filter((s) => s.ownership === 'range');
  };

  /**
   * Gets Serien assigned to a specific range (for Trainings/Wettkämpfe)
   */
  const getSerienForRange = (rangeId) => {
    return savedSerien.value.filter((s) => s.rangeId === rangeId);
  };

  /**
   * Gets user Serien for a specific range
   */
  const getUserSerienForRange = (rangeId) => {
    return getUserSerien().filter((s) => s.rangeId === rangeId);
  };

  return {
    // Capture state
    recording,
    passeMode,
    pairPending,
    activeSerieIndex,
    editingId,
    editingSerie,
    // Persisted state
    savedSerien,
    savedPassen,
    pendingPasseId,
    savedTrainings,
    savedGlobalPassen,
    // Capture lifecycle
    resetCapture,
    startCapture,
    cancelCapture,
    // Step recording
    addStep,
    removeStep,
    // Serie actions
    saveSerie,
    deleteSerie,
    renameSerie,
    createRangeSerie,
    updateSerie,
    loadSerienFromStorage,
    // Serie retrieval
    getUserSerien,
    getGlobalSerien,
    getSerienForRange,
    getUserSerienForRange,
    // Passe actions
    createPasse,
    deletePasse,
    renamePasse,
    setPendingPasse,
    clearPendingPasse,
    loadPassenFromStorage,
    // Global Passe actions
    createGlobalPasse,
    updateGlobalPasse,
    deleteGlobalPasse,
    loadGlobalPassenFromStorage,
    // Training actions
    loadTrainingsFromStorage,
    createTraining,
    deleteTraining,
    renameTraining,
    // Legacy
    savePasse,
  };
});
