import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { usePasseStore } from '@/stores/passeStore.js';
import { sendPositionCommand } from '@/services/rangePositionApi.js';
import { StepState, StepType, PartialStep, FailType } from '@/constants/playEnums.js';

// Helper to generate UUID
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

export { StepState, StepType, PartialStep, FailType };

export const usePlaySessionStore = defineStore('playSession', () => {
  const passeStore = usePasseStore();

  // ── Playback state ──────────────────────────────────────────────────────────
  const playProg = ref(null);           // Segment[] | null
  const currentSerieIndex = ref(0);
  const currentStepIndex = ref(0);
  const playPartialStep = ref(null);    // PartialStep value | null  (a.schuss two-tap)
  const playRaffaleStarted = ref(false);
  const playScoreMode = ref(false);
  const playScore = ref({ totalPoints: 0, stepStates: [] });
  const playLastDeviceStep = ref(null); // { serieIdx, stepIdx } of last fired step
  const playComplete = ref(false);      // true once the user confirms program end
  const activeBlockContext = ref(null); // { instanceId, blockId, rotteId? } | null

  // ── Multi-player state ──────────────────────────────────────────────────────
  const sessionPlayers = ref([]);
  const currentPlayerIndex = ref(0);
  const roundOrder = ref([]);
  const roundStartIndex = ref(0);
  const completedPlayerCount = ref(0);

  // ── Audit confirmation state ─────────────────────────────────────────────────
  const playerConfirmations = ref(new Map()); // playerId → boolean

  // ── Group setup state ────────────────────────────────────────────────────────
  const pendingGroupSerien = ref(null);
  const showGroupSetup = ref(false);

  // ── Pending passe (from Passe Management View) ──────────────────────────
  const pendingPasseInfo = ref(null); // { passeId, rangeId }
  const _pendingPasseId = ref(null);  // Internal only — persists passeId through session init

  // ── Active sessions (persistent across page reloads) ────────────────────────
  const activeSessions = ref([]);
  const currentSessionId = ref(null);
  const currentRangeId = ref(null);
  const SESSIONS_KEY = 'sg_active_sessions';

  // ── Completion tracking ──────────────────────────────────────────────────────
  // Persistent storage of completed Serie IDs (for Training/Wettkampf sessions)
  const completedSerien = ref(new Set());
  const COMPLETED_SERIE_KEY = 'sg_completed_serien';

  // ── Core computed ───────────────────────────────────────────────────────────
  const currentSerie = computed(() => playProg.value?.[currentSerieIndex.value] ?? null);

  const currentStep = computed(() => currentSerie.value?.steps[currentStepIndex.value] ?? null);

  // True once the last step's index has advanced past the end of the last segment.
  // At this point currentStep is null and the Fertig card should appear.
  const isAtProgramEnd = computed(() =>
    !!(playProg.value &&
       currentSerie.value &&
       currentSerieIndex.value === playProg.value.length - 1 &&
       currentStepIndex.value >= currentSerie.value.steps.length)
  );

  // ── Multi-player computed ───────────────────────────────────────────────────
  const isMultiPlayer = computed(() => sessionPlayers.value.length > 1);

  const currentPlayer = computed(() =>
    sessionPlayers.value[roundOrder.value[currentPlayerIndex.value]] ?? null
  );

  const isLastPlayerInRound = computed(() =>
    currentPlayerIndex.value === roundOrder.value.length - 1
  );

  const nextPlayer = computed(() => {
    if (!isMultiPlayer.value) return null;
    const nextIdx = currentPlayerIndex.value + 1;
    if (nextIdx >= sessionPlayers.value.length) return null;
    return sessionPlayers.value[nextIdx] ?? null;
  });

  // ── Score / progress computed ───────────────────────────────────────────────
  const completionPct = computed(() => {
    if (!playProg.value) return 0;
    const total = playProg.value.reduce((sum, seg) => sum + seg.steps.length, 0);
    if (total === 0) return 0;
    const done = playScore.value.stepStates.filter((s) => s.state !== StepState.PENDING).length;
    return Math.round((done / total) * 100);
  });

  const serieCompletionPct = computed(() => {
    if (!currentSerie.value) return 0;
    const segTotal = currentSerie.value.steps.length;
    if (segTotal === 0) return 0;
    const segDone = playScore.value.stepStates.filter(
      (s) => s.serieIndex === currentSerieIndex.value && s.state !== StepState.PENDING
    ).length;
    return Math.round((segDone / segTotal) * 100);
  });

  const playProgress = computed(() => {
    if (!playProg.value) return 0;
    const totalSteps = playProg.value.reduce((sum, seg) => sum + seg.steps.length, 0);
    const flatIndex =
      playProg.value.slice(0, currentSerieIndex.value).reduce((sum, seg) => sum + seg.steps.length, 0) +
      currentStepIndex.value;
    return ((flatIndex + 1) / totalSteps) * 100;
  });

  // No Bird is only valid when the last fired step is still in DONE state (not yet failed/retried).
  const canRetry = computed(() => {
    if (!playLastDeviceStep.value || playComplete.value) return false;
    const { serieIdx, stepIdx } = playLastDeviceStep.value;
    const state = findStepState(serieIdx, stepIdx);
    return state?.state === StepState.DONE;
  });

  const playerScores = computed(() =>
    sessionPlayers.value.map((player) => {
      const states = playScore.value.stepStates.filter((s) => s.playerId === player.id);
      const total = states.reduce((sum, s) => sum + s.pointsEarned, 0);
      const max = states.reduce((sum, s) => sum + s.pointValue, 0);
      const done = states.filter((s) => s.state !== StepState.PENDING).length;
      const pct = states.length > 0 ? Math.round((done / states.length) * 100) : 0;
      return { player, totalPoints: total, maxPoints: max, completionPct: pct };
    })
  );

  const playerScoresSorted = computed(() =>
    [...playerScores.value].sort((a, b) => b.totalPoints - a.totalPoints)
  );

  // ── Helpers ─────────────────────────────────────────────────────────────────
  const getPointValueForStep = (step) => {
    if (step.type === StepType.SOLO) return 1;
    if (step.type === StepType.PAIR || step.type === StepType.A_SCHUSS) return 2;
    if (step.type === StepType.RAFFALE) return 2;
    return 1;
  };

  const findStepState = (segIdx, stepIdx) => {
    const playerId = currentPlayer.value?.id;
    return (
      playScore.value.stepStates.find(
        (s) => s.playerId === playerId && s.serieIndex === segIdx && s.stepIndex === stepIdx
      ) ?? null
    );
  };

  const getPointDeduction = (state) => {
    if (state === StepState.FAILED_BOTH) return 2;
    if (state === StepState.FAILED_A || state === StepState.FAILED_B) return 1;
    return 0;
  };

  const buildPlayerResults = () => {
    return sessionPlayers.value.map((player) => {
      const states = playScore.value.stepStates.filter((s) => s.playerId === player.id);
      const totalPoints = states.reduce((sum, s) => sum + s.pointsEarned, 0);
      const maxPoints = states.reduce((sum, s) => sum + s.pointValue, 0);
      return {
        playerId: player.id,
        displayName: player.displayName,
        totalPoints,
        maxPoints,
        stepStates: states.map((s) => ({ ...s })),
      };
    });
  };

  // ── Player setup ─────────────────────────────────────────────────────────────
  const setupPlayers = (players) => {
    sessionPlayers.value = players;
    roundStartIndex.value = 0;
    currentPlayerIndex.value = 0;
    buildRoundOrder();
  };

  const buildRoundOrder = () => {
    const count = sessionPlayers.value.length;
    if (count === 0) { roundOrder.value = []; return; }
    roundOrder.value = Array.from({ length: count }, (_, i) => (roundStartIndex.value + i) % count);
  };

  // Load pending passe and prepare for group setup
  const loadPendingPasse = () => {
    if (!pendingPasseInfo.value) return null;
    const { passeId } = pendingPasseInfo.value;
    _pendingPasseId.value = passeId;  // Persist for startGroupPlay
    const prog = passeStore.savedPassen.find((p) => p.id === passeId);
    if (!prog) return null;

    // Stage the serien for group setup
    pendingGroupSerien.value = prog.serien;
    showGroupSetup.value = true;

    return prog;
  };

  const clearPendingPasse = () => {
    pendingPasseInfo.value = null;
  };

  // ── Session persistence ──────────────────────────────────────────────────────
  const loadActiveSessions = () => {
    const stored = localStorage.getItem(SESSIONS_KEY);
    if (stored) {
      try {
        activeSessions.value = JSON.parse(stored);
      } catch {
        activeSessions.value = [];
      }
    }
  };

  const saveSessions = () => {
    localStorage.setItem(SESSIONS_KEY, JSON.stringify(activeSessions.value));
  };

  const stopSession = (sessionId) => {
    activeSessions.value = activeSessions.value.filter((s) => s.sessionId !== sessionId);
    if (currentSessionId.value === sessionId) {
      currentSessionId.value = null;
      closePlayback();
    }
    saveSessions();
  };

  const resumeSession = (sessionId) => {
    const session = activeSessions.value.find((s) => s.sessionId === sessionId);
    if (!session) return false;

    const prog = passeStore.savedPassen.find((p) => p.id === session.passeId);
    if (!prog) return false;

    currentSessionId.value = sessionId;
    currentRangeId.value = session.rangeId ?? null;
    playProg.value = prog.serien;
    sessionPlayers.value = session.players;
    currentPlayerIndex.value = 0;
    currentSerieIndex.value = 0;
    currentStepIndex.value = 0;
    playScoreMode.value = true;
    playPartialStep.value = null;
    playRaffaleStarted.value = false;
    playLastDeviceStep.value = null;
    playComplete.value = false;

    // Initialize playScore with stepStates for all players
    const stepStates = [];
    prog.serien.forEach((seg, segIdx) => {
      seg.steps.forEach((step, stepIdx) => {
        session.players.forEach((player) => {
          stepStates.push({
            playerId: player.id,
            serieIndex: segIdx,
            stepIndex: stepIdx,
            state: StepState.PENDING,
            pointValue: getPointValueForStep(step),
            noBirds: 0,
            pointsEarned: 0,
            corrected: false,
            originalState: null,
          });
        });
      });
    });
    playScore.value = { totalPoints: 0, stepStates };
    showGroupSetup.value = false;

    return true;
  };

  // ── Playback actions ─────────────────────────────────────────────────────────
  const playPasseWithScore = (passeId, players = [], rangeId = null) => {
    const prog = passeStore.savedPassen.find((p) => p.id === passeId);
    if (!prog) return;

    currentRangeId.value = rangeId;
    setupPlayers(
      players.length > 0
        ? players
        : [{ id: 'solo', type: 'user', userId: null, displayName: 'Schütze' }]
    );

    playProg.value = prog.serien;
    currentSerieIndex.value = 0;
    currentStepIndex.value = 0;
    playScoreMode.value = true;
    playPartialStep.value = null;
    playRaffaleStarted.value = false;
    playLastDeviceStep.value = null;
    playComplete.value = false;

    const stepStates = [];
    sessionPlayers.value.forEach((player) => {
      prog.serien.forEach((seg, segIdx) => {
        seg.steps.forEach((step, stepIdx) => {
          stepStates.push({
            playerId: player.id,
            serieIndex: segIdx,
            stepIndex: stepIdx,
            state: StepState.PENDING,
            pointValue: getPointValueForStep(step),
            noBirds: 0,
            pointsEarned: 0,
            corrected: false,
            originalState: null,
          });
        });
      });
    });
    playScore.value = { totalPoints: 0, stepStates };
  };

  // Resolve position ID supporting both UI format (positionId) and API format (posId).
  const _posId  = (step) => step.positionId  ?? step.posId  ?? null;
  const _posId1 = (step) => step.positionId1 ?? step.posId1 ?? null;
  const _posId2 = (step) => step.positionId2 ?? step.posId2 ?? null;

  const advancePlayStep = async () => {
    if (!playProg.value) return;
    const step = currentStep.value;
    if (!step) return;
    const rangeId = currentRangeId.value;

    try {
      if (step.type === StepType.SOLO) {
        await sendPositionCommand(rangeId, _posId(step));
        markStepDone();
      } else if (step.type === StepType.PAIR) {
        await Promise.all([
          sendPositionCommand(rangeId, _posId1(step)),
          sendPositionCommand(rangeId, _posId2(step)),
        ]);
        markStepDone();
      } else if (step.type === StepType.A_SCHUSS) {
        if (playPartialStep.value === null) {
          await sendPositionCommand(rangeId, _posId1(step));
          playPartialStep.value = PartialStep.FIRST;
        } else if (playPartialStep.value === PartialStep.FIRST) {
          await sendPositionCommand(rangeId, _posId2(step));
          playPartialStep.value = null;
          markStepDone();
        }
      } else if (step.type === StepType.RAFFALE) {
        if (!playRaffaleStarted.value) {
          await sendPositionCommand(rangeId, _posId(step));
          playRaffaleStarted.value = true;
        }
      }
    } catch (err) {
      console.error('Failed to send position command during playback:', err);
    }
  };

  const completeRaffaleStep = async () => {
    const step = currentStep.value;
    if (step?.type !== StepType.RAFFALE) return;
    try {
      await sendPositionCommand(currentRangeId.value, _posId(step));
    } catch (err) {
      console.error('Failed to send second raffale command:', err);
    }
    playRaffaleStarted.value = false;
    markStepDone();
  };

  const markStepDone = () => {
    const state = findStepState(currentSerieIndex.value, currentStepIndex.value);
    if (state?.state === StepState.PENDING) {
      state.state = StepState.DONE;
      state.pointsEarned = state.pointValue;
      playScore.value.totalPoints += state.pointValue;
      playLastDeviceStep.value = {
        serieIdx: currentSerieIndex.value,
        stepIdx: currentStepIndex.value,
      };
    }
    moveToNextStep();
  };

  const moveToNextStep = () => {
    const seg = playProg.value[currentSerieIndex.value];

    if (currentStepIndex.value < seg.steps.length - 1) {
      // More steps in this segment
      currentStepIndex.value += 1;
      playPartialStep.value = null;
    } else if (currentSerieIndex.value < playProg.value.length - 1) {
      // Advance to the first step of the next segment.
      // playLastDeviceStep is intentionally kept so fail buttons remain usable
      // for the last step of the completed segment.
      currentSerieIndex.value += 1;
      currentStepIndex.value = 0;
      playPartialStep.value = null;
    } else {
      // Last step of the last segment: push index past the end so currentStep
      // becomes null and isAtProgramEnd becomes true.
      // playLastDeviceStep is kept — the user can still fail this last step.
      currentStepIndex.value = seg.steps.length;
    }
  };

  const updateFailState = (stepState, failType, stepType) => {
    const oldDeduction = getPointDeduction(stepState.state);

    if (stepType === StepType.SOLO) {
      stepState.state = StepState.FAILED_BOTH;
    } else if (failType === FailType.A) {
      stepState.state = StepState.FAILED_A;
    } else if (failType === FailType.B) {
      stepState.state = StepState.FAILED_B;
    } else {
      stepState.state = StepState.FAILED_BOTH;
    }

    const rawDeduction = getPointDeduction(stepState.state) - oldDeduction;
    const actualDeduction = Math.min(rawDeduction, stepState.pointValue);
    playScore.value.totalPoints -= actualDeduction;
    stepState.pointsEarned = Math.max(0, stepState.pointsEarned - actualDeduction);
  };

  const failStep = (failType) => {
    if (!playLastDeviceStep.value) return;
    const { serieIdx, stepIdx } = playLastDeviceStep.value;
    const stepState = findStepState(serieIdx, stepIdx);
    const step = playProg.value[serieIdx]?.steps[stepIdx];
    if (!stepState || !step) return;
    updateFailState(stepState, failType, step.type);
  };

  const retryStep = () => {
    if (!playLastDeviceStep.value) return;
    const { serieIdx, stepIdx } = playLastDeviceStep.value;
    const state = findStepState(serieIdx, stepIdx);
    if (state?.state !== StepState.DONE) return;
    state.noBirds += 1;
    state.state = StepState.PENDING;
    playScore.value.totalPoints -= state.pointValue;
    state.pointsEarned = 0;
    currentSerieIndex.value = serieIdx;
    currentStepIndex.value = stepIdx;
    playPartialStep.value = null;
    playLastDeviceStep.value = null;
  };

  // Uses direct find (not findStepState) because corrections can target any player, not just currentPlayer
  const correctStep = (playerId, serieIdx, stepIdx, newState) => {
    if (newState === StepState.PENDING) return   // PENDING is not a valid correction target
    const stepState = playScore.value.stepStates.find(
      (s) => s.playerId === playerId && s.serieIndex === serieIdx && s.stepIndex === stepIdx
    )
    if (!stepState) return
    if (!stepState.corrected) {
      stepState.originalState = stepState.state
    }
    stepState.corrected = true
    playScore.value.totalPoints -= stepState.pointsEarned
    stepState.state = newState
    stepState.pointsEarned = Math.max(0, stepState.pointValue - getPointDeduction(newState))
    playScore.value.totalPoints += stepState.pointsEarned
  }

  const confirmPlayer = (playerId) => {
    const map = new Map(playerConfirmations.value)
    map.set(playerId, true)
    playerConfirmations.value = map
  }

  const unconfirmPlayer = (playerId) => {
    const map = new Map(playerConfirmations.value)
    map.set(playerId, false)
    playerConfirmations.value = map
  }

  const allPlayersConfirmed = computed(() =>
    sessionPlayers.value.length > 0 &&
    sessionPlayers.value.every((p) => playerConfirmations.value.get(p.id) === true)
  )

  // Called by the UI when the user confirms the program is finished
  // (clicking Fertig or using a fail button at program end).
  const confirmComplete = async () => {
    playComplete.value = true;
    // Notify the correct store when a block instance completes
    if (activeBlockContext.value) {
      const ctx = activeBlockContext.value
      const results = buildPlayerResults()
      if (ctx.instanceType === 'competition') {
        const { useCompetitionEventStore } = await import('@/stores/competitionEventStore.js')
        useCompetitionEventStore().markBlockDone(ctx.instanceId, ctx.blockId, results, ctx.rotteId ?? null)
      } else {
        const { useActivePasseStore } = await import('@/stores/activePasseStore.js')
        useActivePasseStore().markBlockDone(ctx.instanceId, ctx.blockId, results)
      }
      activeBlockContext.value = null
    }
    // Remove legacy session from active sessions
    if (currentSessionId.value) {
      activeSessions.value = activeSessions.value.filter((s) => s.sessionId !== currentSessionId.value);
      currentSessionId.value = null;
      saveSessions();
    }
  };

  const closePlayback = () => {
    playProg.value = null;
    currentSerieIndex.value = 0;
    currentStepIndex.value = 0;
    playPartialStep.value = null;
    playRaffaleStarted.value = false;
    playScoreMode.value = false;
    playScore.value = { totalPoints: 0, stepStates: [] };
    playLastDeviceStep.value = null;
    playComplete.value = false;
    sessionPlayers.value = [];
    currentPlayerIndex.value = 0;
    completedPlayerCount.value = 0;
    showGroupSetup.value = false;
    pendingGroupSerien.value = null;
    currentRangeId.value = null;
    playerConfirmations.value = new Map();
  };

  const setPendingGroupSerien = (serien) => {
    pendingGroupSerien.value = serien;
    showGroupSetup.value = true;
  };

  const cancelGroupSetup = () => {
    showGroupSetup.value = false;
    pendingGroupSerien.value = null;
  };

  const startGroupPlay = async (players, rangeId = null, rangeName = null, instanceId = null, blockId = null, rotteId = null, instanceType = null) => {
    if (!pendingGroupSerien.value) return;
    const serien = pendingGroupSerien.value;

    currentRangeId.value = rangeId;
    setupPlayers(players);
    playerConfirmations.value = new Map(players.map((p) => [p.id, false]));
    completedPlayerCount.value = 0;

    playProg.value = serien;
    currentSerieIndex.value = 0;
    currentStepIndex.value = 0;
    playScoreMode.value = true;
    playPartialStep.value = null;
    playRaffaleStarted.value = false;
    playLastDeviceStep.value = null;
    playComplete.value = false;

    const stepStates = [];
    players.forEach((player) => {
      serien.forEach((seg, segIdx) => {
        seg.steps.forEach((step, stepIdx) => {
          stepStates.push({
            playerId: player.id,
            serieIndex: segIdx,
            stepIndex: stepIdx,
            state: StepState.PENDING,
            pointValue: getPointValueForStep(step),
            noBirds: 0,
            pointsEarned: 0,
            corrected: false,
            originalState: null,
          });
        });
      });
    });
    playScore.value = { totalPoints: 0, stepStates };

    // Create active session if rangeId is provided (legacy, not used for block instances)
    if (rangeId && !instanceId) {
      const sessionId = generateUUID();
      currentSessionId.value = sessionId;
      const passeId = _pendingPasseId.value;
      const passeName = passeStore.savedPassen.find((p) => p.id === passeId)?.name || 'Passe';

      const session = {
        sessionId,
        passeId,
        passeName,
        rangeId,
        rangeName: rangeName || `Platz ${rangeId}`,
        players: [...players],
        startedAt: Date.now(),
        completionPct: 0,
        status: 'active',
      };

      activeSessions.value.push(session);
      saveSessions();
      _pendingPasseId.value = null;  // Clear after session is created
    }

    // Set block context when playing from an active passe/competition instance
    if (instanceId && blockId) {
      activeBlockContext.value = { instanceId, blockId, rotteId: rotteId ?? null, instanceType: instanceType ?? null }
      if (instanceType === 'competition') {
        const { useCompetitionEventStore } = await import('@/stores/competitionEventStore.js')
        useCompetitionEventStore().markBlockInProgress(instanceId, blockId, rotteId ?? null)
      } else {
        const { useActivePasseStore } = await import('@/stores/activePasseStore.js')
        useActivePasseStore().markBlockInProgress(instanceId, blockId)
      }
    } else {
      activeBlockContext.value = null
    }

    showGroupSetup.value = false;
    pendingGroupSerien.value = null;
  };

  const advanceToNextPlayer = async () => {
    completedPlayerCount.value += 1;
    if (completedPlayerCount.value >= sessionPlayers.value.length) {
      await confirmComplete();
    } else {
      currentPlayerIndex.value += 1;
      currentSerieIndex.value = 0;
      currentStepIndex.value = 0;
      playPartialStep.value = null;
      playRaffaleStarted.value = false;
      playLastDeviceStep.value = null;
    }
  };

  // ── Multi-player actions ─────────────────────────────────────────────────────
  const endPlayerTurn = () => {
    if (currentPlayerIndex.value < roundOrder.value.length - 1) {
      currentPlayerIndex.value += 1;
      currentStepIndex.value = 0;
      playPartialStep.value = null;
      playRaffaleStarted.value = false;
      playLastDeviceStep.value = null;
    } else {
      currentPlayerIndex.value = 0;
      roundStartIndex.value = (roundStartIndex.value + 1) % sessionPlayers.value.length;
      buildRoundOrder();
    }
  };

  const setRoundStartOverride = (playerIndex) => {
    roundStartIndex.value = playerIndex;
  };

  // ── Completion tracking actions ──────────────────────────────────────────────
  const loadCompletedSerien = () => {
    try {
      const stored = localStorage.getItem(COMPLETED_SERIE_KEY);
      if (stored) {
        completedSerien.value = new Set(JSON.parse(stored));
      }
    } catch (e) {
      console.error('Failed to load completed serien:', e);
    }
  };

  const saveCompletedSerien = () => {
    try {
      localStorage.setItem(COMPLETED_SERIE_KEY, JSON.stringify([...completedSerien.value]));
    } catch (e) {
      console.error('Failed to save completed serien:', e);
    }
  };

  const markSerieComplete = (serieId) => {
    if (serieId) {
      completedSerien.value.add(serieId);
      saveCompletedSerien();
    }
  };

  const isSerieCompleted = (serieId) => {
    return serieId ? completedSerien.value.has(serieId) : false;
  };

  // Initialize: load persisted sessions and completed serien
  loadActiveSessions();
  loadCompletedSerien();

  return {
    // State
    playerConfirmations,
    playProg,
    currentSerieIndex,
    currentStepIndex,
    playPartialStep,
    playRaffaleStarted,
    playScoreMode,
    playScore,
    playLastDeviceStep,
    playComplete,
    activeBlockContext,
    sessionPlayers,
    currentPlayerIndex,
    roundOrder,
    roundStartIndex,
    completedSerien,
    completedPlayerCount,
    pendingGroupSerien,
    showGroupSetup,
    pendingPasseInfo,
    activeSessions,
    currentSessionId,
    currentRangeId,
    // Computed
    currentSerie,
    currentStep,
    isAtProgramEnd,
    currentPlayer,
    isMultiPlayer,
    isLastPlayerInRound,
    nextPlayer,
    completionPct,
    serieCompletionPct,
    playProgress,
    canRetry,
    playerScores,
    playerScoresSorted,
    allPlayersConfirmed,
    // Actions
    playPasseWithScore,
    advancePlayStep,
    completeRaffaleStep,
    markStepDone,
    failStep,
    retryStep,
    correctStep,
    confirmPlayer,
    unconfirmPlayer,
    confirmComplete,
    closePlayback,
    buildPlayerResults,
    getPointValueForStep,
    setupPlayers,
    buildRoundOrder,
    endPlayerTurn,
    setRoundStartOverride,
    setPendingGroupSerien,
    cancelGroupSetup,
    startGroupPlay,
    advanceToNextPlayer,
    loadCompletedSerien,
    saveCompletedSerien,
    markSerieComplete,
    isSerieCompleted,
    loadPendingPasse,
    clearPendingPasse,
    loadActiveSessions,
    saveSessions,
    stopSession,
    resumeSession,
  };
});
