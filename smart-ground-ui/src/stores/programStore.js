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
  // ── Capture state (Erfassen-Modus) ───────────────────────────────────────
  const recording = ref({});
  const programMode = ref(false);
  const pairPending = ref(null);
  const activeAblaufIndex = ref(0);
  const editingId = ref(null);
  const editingAblauf = ref([]);

  // ── Persisted state ───────────────────────────────────────────────────────
  // Alle Abläufe: user-eigene (ownership:'user') + platzweite (ownership:'range')
  const savedAblaeufe = ref([]);
  const savedPrograms = ref([]);
  const pendingProgramId = ref(null);

  // ── Storage key helpers ───────────────────────────────────────────────────
  // Platzweite Abläufe — global, nicht benutzerspezifisch
  const RANGE_ABLAUF_PREFIX = '_sg_range_ablauf_';

  const getProgramPrefix = () => {
    const authStore = useAuthStore();
    return `${authStore.userName ?? 'anonymous'}_program_`;
  };

  const getAblaufPrefix = () => {
    const authStore = useAuthStore();
    return `${authStore.userName ?? 'anonymous'}_ablauf_`;
  };

  const nextProgramKey = () => {
    const prefix = getProgramPrefix();
    const existing = Object.keys(localStorage)
      .filter((k) => k.startsWith(prefix))
      .map((k) => parseInt(k.slice(prefix.length), 10))
      .filter((n) => !isNaN(n));
    return `${prefix}${existing.length > 0 ? Math.max(...existing) + 1 : 1}`;
  };

  const nextAblaufKey = () => {
    const prefix = getAblaufPrefix();
    const existing = Object.keys(localStorage)
      .filter((k) => k.startsWith(prefix))
      .map((k) => parseInt(k.slice(prefix.length), 10))
      .filter((n) => !isNaN(n));
    return `${prefix}${existing.length > 0 ? Math.max(...existing) + 1 : 1}`;
  };

  const nextRangeAblaufKey = () => {
    const existing = Object.keys(localStorage)
      .filter((k) => k.startsWith(RANGE_ABLAUF_PREFIX))
      .map((k) => parseInt(k.slice(RANGE_ABLAUF_PREFIX.length), 10))
      .filter((n) => !isNaN(n));
    return `${RANGE_ABLAUF_PREFIX}${existing.length > 0 ? Math.max(...existing) + 1 : 1}`;
  };

  // ── Load from localStorage ────────────────────────────────────────────────
  const loadProgramsFromStorage = () => {
    const prefix = getProgramPrefix();
    savedPrograms.value = Object.keys(localStorage)
      .filter((k) => k.startsWith(prefix))
      .map((key) => {
        try {
          const data = JSON.parse(localStorage.getItem(key));
          // Backward compat: old format had top-level steps array
          if (data.steps && !data.ablaeufe && !data.segments) {
            data.ablaeufe = [{ id: 's1', alias: null, steps: data.steps }];
          }
          // Backward compat: old format used 'segments' key
          if (!data.ablaeufe && data.segments) {
            data.ablaeufe = data.segments;
          }
          return {
            id: key,
            name: data.programName,
            ablaeufe: (data.ablaeufe ?? []).map((abl) => ({
              ...abl,
              rangeId: abl.rangeId ?? null,
              rangeName: abl.rangeName ?? null,
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

  const loadAblaeufeFromStorage = () => {
    const parseAblauf = (key, defaultOwnership) => {
      try {
        const data = JSON.parse(localStorage.getItem(key));
        return {
          id: key,
          name: data.ablaufName ?? data.segmentName,   // backward compat
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

    const userPrefix = getAblaufPrefix();
    const userAblaeufe = Object.keys(localStorage)
      .filter((k) => k.startsWith(userPrefix))
      .map((k) => parseAblauf(k, 'user'))
      .filter(Boolean);

    const rangeAblaeufe = Object.keys(localStorage)
      .filter((k) => k.startsWith(RANGE_ABLAUF_PREFIX))
      .map((k) => parseAblauf(k, 'range'))
      .filter(Boolean);

    savedAblaeufe.value = [...userAblaeufe, ...rangeAblaeufe]
      .sort((a, b) => a.createdAt - b.createdAt);
  };

  loadProgramsFromStorage();
  loadAblaeufeFromStorage();

  // ── Capture lifecycle ─────────────────────────────────────────────────────
  const resetCapture = () => {
    programMode.value = false;
    pairPending.value = null;
    activeAblaufIndex.value = 0;
  };

  const startCapture = () => {
    const shooterRemoteStore = useShooterRemoteStore();
    if (!shooterRemoteStore.isReserved) return;
    programMode.value = true;
    activeAblaufIndex.value = 0;
    editingAblauf.value = [{ id: generateUUID(), alias: null, steps: [] }];
    editingId.value = null;
  };

  const cancelCapture = () => {
    programMode.value = false;
    pairPending.value = null;
    editingAblauf.value = [];
    activeAblaufIndex.value = 0;
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
      const segs = [...editingAblauf.value];
      segs[0].steps = [...segs[0].steps, step];
      editingAblauf.value = segs;
    } else if (shooterRemoteStore.mode === 'raffale') {
      recording.value = { ...recording.value, [positionId]: true };
      setTimeout(() => {
        const r = { ...recording.value };
        delete r[positionId];
        recording.value = r;
      }, 500);
      const step = { id: Date.now(), type: 'raffale', alias, positionId, letter };
      const segs = [...editingAblauf.value];
      segs[0].steps = [...segs[0].steps, step];
      editingAblauf.value = segs;
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
        const segs = [...editingAblauf.value];
        segs[0].steps = [...segs[0].steps, step];
        editingAblauf.value = segs;
        pairPending.value = null;
        shooterRemoteStore.setMode('solo');
      }
    }
  };

  const removeStep = (ablaufIndex, stepId) => {
    const segs = [...editingAblauf.value];
    segs[ablaufIndex] = {
      ...segs[ablaufIndex],
      steps: segs[ablaufIndex].steps.filter((s) => s.id !== stepId),
    };
    editingAblauf.value = segs;
  };

  // ── Ablauf persistence ────────────────────────────────────────────────────
  /**
   * Speichert den aktuellen Erfassungs-Ablauf.
   * ownership: 'user' (privat) | 'range' (platzweit sichtbar, nur Admin/Owner)
   */
  const saveAblauf = (ablaufName, rangeId = null, rangeName = null, ownership = 'user') => {
    const steps = editingAblauf.value[0]?.steps ?? [];
    if (steps.length === 0) return;
    const name = ablaufName?.trim() || `Ablauf ${savedAblaeufe.value.length + 1}`;
    const key = ownership === 'range' ? nextRangeAblaufKey() : nextAblaufKey();
    const createdAt = Date.now();
    const data = { ablaufName: name, rangeId, rangeName, steps, createdAt, ownership };
    localStorage.setItem(key, JSON.stringify(data));
    savedAblaeufe.value = [
      ...savedAblaeufe.value,
      { id: key, name, rangeId, rangeName, steps: [...steps], createdAt, ownership },
    ];
    cancelCapture();
  };

  const deleteAblauf = (ablaufId) => {
    localStorage.removeItem(ablaufId);
    savedAblaeufe.value = savedAblaeufe.value.filter((s) => s.id !== ablaufId);
  };

  const renameAblauf = (ablaufId, newName) => {
    const abl = savedAblaeufe.value.find((s) => s.id === ablaufId);
    if (!abl) return;
    abl.name = newName;
    try {
      const stored = JSON.parse(localStorage.getItem(ablaufId));
      if (stored) {
        stored.ablaufName = newName;
        localStorage.setItem(ablaufId, JSON.stringify(stored));
      }
    } catch { /* ignorieren */ }
  };

  // ── Program persistence ───────────────────────────────────────────────────
  /**
   * Erstellt ein Programm aus einer Liste von Ablauf-Objekten.
   * selectedAblaeufe: Array von { id, name, rangeId, rangeName, steps }
   */
  const createProgram = (programName, selectedAblaeufe) => {
    if (selectedAblaeufe.length === 0) return;
    const name = programName?.trim() || `Programm ${savedPrograms.value.length + 1}`;
    const key = nextProgramKey();
    // Abläufe werden vollständig eingebettet (Snapshot für Playback)
    const ablaeufe = selectedAblaeufe.map((abl) => ({
      id: abl.id,
      alias: abl.name,
      rangeId: abl.rangeId,
      rangeName: abl.rangeName,
      steps: [...abl.steps],
    }));
    localStorage.setItem(key, JSON.stringify({ programName: name, ablaeufe }));
    savedPrograms.value = [...savedPrograms.value, { id: key, name, ablaeufe }];
  };

  const deleteProgram = (programId) => {
    localStorage.removeItem(programId);
    savedPrograms.value = savedPrograms.value.filter((p) => p.id !== programId);
    if (pendingProgramId.value === programId) pendingProgramId.value = null;
  };

  const renameProgram = (programId, newName) => {
    const prog = savedPrograms.value.find((p) => p.id === programId);
    if (!prog) return;
    prog.name = newName;
    try {
      const stored = JSON.parse(localStorage.getItem(programId));
      if (stored) {
        stored.programName = newName;
        localStorage.setItem(programId, JSON.stringify(stored));
      }
    } catch { /* ignorieren */ }
  };

  // ── Pending program (für "Starten" aus Programm-Verwaltung) ───────────────
  const setPendingProgram = (programId) => {
    pendingProgramId.value = programId;
  };

  const clearPendingProgram = () => {
    pendingProgramId.value = null;
  };

  // ── Legacy: saveProgram ───────────────────────────────────────────────────
  /** @deprecated Wird durch saveAblauf ersetzt. */
  const saveProgram = (programName = null) => {
    if (editingAblauf.value.every((abl) => abl.steps.length === 0)) return;
    const name = programName || `Programm ${savedPrograms.value.length + 1}`;
    const key = nextProgramKey();
    localStorage.setItem(key, JSON.stringify({ programName: name, ablaeufe: [...editingAblauf.value] }));
    savedPrograms.value = [
      ...savedPrograms.value,
      { id: key, name, ablaeufe: [...editingAblauf.value] },
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
          return { id: key, name: data.trainingName, programmes: data.programmes ?? [] };
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

  const createTraining = (trainingName, selectedProgrammes) => {
    if (selectedProgrammes.length === 0) return;
    const name = trainingName?.trim() || `Training ${savedTrainings.value.length + 1}`;
    const key = nextTrainingKey();
    const programmes = selectedProgrammes.map((prog) => ({
      id: prog.id,
      name: prog.name,
      ablaeufe: prog.ablaeufe.map((abl) => ({ ...abl, steps: [...(abl.steps ?? [])] })),
    }));
    localStorage.setItem(key, JSON.stringify({ trainingName: name, programmes }));
    savedTrainings.value = [...savedTrainings.value, { id: key, name, programmes }];
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

  // ── Ablauf retrieval by category ────────────────────────────────────────────
  /**
   * Gets user-created Abläufe (ownership: 'user')
   */
  const getUserAblaeufe = () => {
    return savedAblaeufe.value.filter((abl) => abl.ownership === 'user');
  };

  /**
   * Gets globally published Abläufe (ownership: 'range')
   */
  const getGlobalAblaeufe = () => {
    return savedAblaeufe.value.filter((abl) => abl.ownership === 'range');
  };

  /**
   * Gets Abläufe assigned to a specific range (for Trainings/Wettkämpfe)
   */
  const getAblaeufeForRange = (rangeId) => {
    return savedAblaeufe.value.filter((abl) => abl.rangeId === rangeId);
  };

  /**
   * Gets user Abläufe for a specific range
   */
  const getUserAblaeufeForRange = (rangeId) => {
    return getUserAblaeufe().filter((abl) => abl.rangeId === rangeId);
  };

  return {
    // Capture state
    recording,
    programMode,
    pairPending,
    activeAblaufIndex,
    editingId,
    editingAblauf,
    // Persisted state
    savedAblaeufe,
    savedPrograms,
    pendingProgramId,
    savedTrainings,
    // Capture lifecycle
    resetCapture,
    startCapture,
    cancelCapture,
    // Step recording
    addStep,
    removeStep,
    // Ablauf actions
    saveAblauf,
    deleteAblauf,
    renameAblauf,
    loadAblaeufeFromStorage,
    // Ablauf retrieval
    getUserAblaeufe,
    getGlobalAblaeufe,
    getAblaeufeForRange,
    getUserAblaeufeForRange,
    // Program actions
    createProgram,
    deleteProgram,
    renameProgram,
    setPendingProgram,
    clearPendingProgram,
    loadProgramsFromStorage,
    // Training actions
    loadTrainingsFromStorage,
    createTraining,
    deleteTraining,
    renameTraining,
    // Legacy
    saveProgram,
  };
});
