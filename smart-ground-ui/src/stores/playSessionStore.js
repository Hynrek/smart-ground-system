import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { useProgramStore } from '@/stores/programStore.js';
import { sendDeviceCommand } from '@/services/deviceApi.js';
import { StepState, StepType, PartialStep, FailType } from '@/constants/playEnums.js';

export { StepState, StepType, PartialStep, FailType };

export const usePlaySessionStore = defineStore('playSession', () => {
  const programStore = useProgramStore();

  // ── Playback state ──────────────────────────────────────────────────────────
  const playProg = ref(null);           // Segment[] | null
  const currentAblaufIndex = ref(0);
  const currentStepIndex = ref(0);
  const playPartialStep = ref(null);    // PartialStep value | null  (a.schuss two-tap)
  const playRaffaleStarted = ref(false);
  const playScoreMode = ref(false);
  const playScore = ref({ totalPoints: 0, stepStates: [] });
  const playLastDeviceStep = ref(null); // { ablaufIdx, stepIdx } of last fired step
  const playComplete = ref(false);      // true once the user confirms program end

  // ── Multi-player state ──────────────────────────────────────────────────────
  const sessionPlayers = ref([]);
  const currentPlayerIndex = ref(0);
  const roundOrder = ref([]);
  const roundStartIndex = ref(0);

  // ── Completion tracking ──────────────────────────────────────────────────────
  // Persistent storage of completed Ablauf IDs (for Training/Wettkampf sessions)
  const completedAblaeufe = ref(new Set());
  const COMPLETED_ABLAUF_KEY = 'sg_completed_ablaeufe';

  // ── Core computed ───────────────────────────────────────────────────────────
  const currentAblauf = computed(() => playProg.value?.[currentAblaufIndex.value] ?? null);

  const currentStep = computed(() => currentAblauf.value?.steps[currentStepIndex.value] ?? null);

  // True once the last step's index has advanced past the end of the last segment.
  // At this point currentStep is null and the Fertig card should appear.
  const isAtProgramEnd = computed(() =>
    !!(playProg.value &&
       currentAblauf.value &&
       currentAblaufIndex.value === playProg.value.length - 1 &&
       currentStepIndex.value >= currentAblauf.value.steps.length)
  );

  // ── Multi-player computed ───────────────────────────────────────────────────
  const isMultiPlayer = computed(() => sessionPlayers.value.length > 1);

  const currentPlayer = computed(() =>
    sessionPlayers.value[roundOrder.value[currentPlayerIndex.value]] ?? null
  );

  const isLastPlayerInRound = computed(() =>
    currentPlayerIndex.value === roundOrder.value.length - 1
  );

  // ── Score / progress computed ───────────────────────────────────────────────
  const completionPct = computed(() => {
    if (!playProg.value) return 0;
    const total = playProg.value.reduce((sum, seg) => sum + seg.steps.length, 0);
    if (total === 0) return 0;
    const done = playScore.value.stepStates.filter((s) => s.state !== StepState.PENDING).length;
    return Math.round((done / total) * 100);
  });

  const ablaufCompletionPct = computed(() => {
    if (!currentAblauf.value) return 0;
    const segTotal = currentAblauf.value.steps.length;
    if (segTotal === 0) return 0;
    const segDone = playScore.value.stepStates.filter(
      (s) => s.ablaufIndex === currentAblaufIndex.value && s.state !== StepState.PENDING
    ).length;
    return Math.round((segDone / segTotal) * 100);
  });

  const playProgress = computed(() => {
    if (!playProg.value) return 0;
    const totalSteps = playProg.value.reduce((sum, seg) => sum + seg.steps.length, 0);
    const flatIndex =
      playProg.value.slice(0, currentAblaufIndex.value).reduce((sum, seg) => sum + seg.steps.length, 0) +
      currentStepIndex.value;
    return ((flatIndex + 1) / totalSteps) * 100;
  });

  // No Bird is only valid when the last fired step is still in DONE state (not yet failed/retried).
  const canRetry = computed(() => {
    if (!playLastDeviceStep.value || playComplete.value) return false;
    const { ablaufIdx, stepIdx } = playLastDeviceStep.value;
    const state = findStepState(ablaufIdx, stepIdx);
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
        (s) => s.playerId === playerId && s.ablaufIndex === segIdx && s.stepIndex === stepIdx
      ) ?? null
    );
  };

  const getPointDeduction = (state) => {
    if (state === StepState.FAILED_BOTH) return 2;
    if (state === StepState.FAILED_A || state === StepState.FAILED_B) return 1;
    return 0;
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

  // ── Playback actions ─────────────────────────────────────────────────────────
  const playProgramWithScore = (programId, players = []) => {
    const prog = programStore.savedPrograms.find((p) => p.id === programId);
    if (!prog) return;

    setupPlayers(
      players.length > 0
        ? players
        : [{ id: 'solo', type: 'user', userId: null, displayName: 'Spieler' }]
    );

    playProg.value = prog.ablaeufe;
    currentAblaufIndex.value = 0;
    currentStepIndex.value = 0;
    playScoreMode.value = true;
    playPartialStep.value = null;
    playRaffaleStarted.value = false;
    playLastDeviceStep.value = null;
    playComplete.value = false;

    const stepStates = [];
    sessionPlayers.value.forEach((player) => {
      prog.ablaeufe.forEach((seg, segIdx) => {
        seg.steps.forEach((step, stepIdx) => {
          stepStates.push({
            playerId: player.id,
            ablaufIndex: segIdx,
            stepIndex: stepIdx,
            state: StepState.PENDING,
            pointValue: getPointValueForStep(step),
            noBirds: 0,
            pointsEarned: 0,
          });
        });
      });
    });
    playScore.value = { totalPoints: 0, stepStates };
  };

  const advancePlayStep = async () => {
    if (!playProg.value) return;
    const step = currentStep.value;
    if (!step) return;

    try {
      if (step.type === StepType.SOLO) {
        await sendDeviceCommand(step.deviceId);
        markStepDone();
      } else if (step.type === StepType.PAIR) {
        await Promise.all([
          sendDeviceCommand(step.deviceId1),
          sendDeviceCommand(step.deviceId2),
        ]);
        markStepDone();
      } else if (step.type === StepType.A_SCHUSS) {
        if (playPartialStep.value === null) {
          await sendDeviceCommand(step.deviceId1);
          playPartialStep.value = PartialStep.FIRST;
        } else if (playPartialStep.value === PartialStep.FIRST) {
          await sendDeviceCommand(step.deviceId2);
          playPartialStep.value = null;
          markStepDone();
        }
      } else if (step.type === StepType.RAFFALE) {
        if (!playRaffaleStarted.value) {
          await sendDeviceCommand(step.deviceId);
          playRaffaleStarted.value = true;
        }
      }
    } catch (err) {
      console.error('Failed to send device command during playback:', err);
    }
  };

  const completeRaffaleStep = async () => {
    const step = currentStep.value;
    if (step?.type !== StepType.RAFFALE) return;
    try {
      await sendDeviceCommand(step.deviceId);
    } catch (err) {
      console.error('Failed to send second raffale command:', err);
    }
    playRaffaleStarted.value = false;
    markStepDone();
  };

  const markStepDone = () => {
    const state = findStepState(currentAblaufIndex.value, currentStepIndex.value);
    if (state?.state === StepState.PENDING) {
      state.state = StepState.DONE;
      state.pointsEarned = state.pointValue;
      playScore.value.totalPoints += state.pointValue;
      playLastDeviceStep.value = {
        ablaufIdx: currentAblaufIndex.value,
        stepIdx: currentStepIndex.value,
      };
    }
    moveToNextStep();
  };

  const moveToNextStep = () => {
    const seg = playProg.value[currentAblaufIndex.value];

    if (currentStepIndex.value < seg.steps.length - 1) {
      // More steps in this segment
      currentStepIndex.value += 1;
      playPartialStep.value = null;
    } else if (currentAblaufIndex.value < playProg.value.length - 1) {
      // Advance to the first step of the next segment.
      // playLastDeviceStep is intentionally kept so fail buttons remain usable
      // for the last step of the completed segment.
      currentAblaufIndex.value += 1;
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

    playScore.value.totalPoints -= getPointDeduction(stepState.state) - oldDeduction;
  };

  const failStep = (failType) => {
    if (!playLastDeviceStep.value) return;
    const { ablaufIdx, stepIdx } = playLastDeviceStep.value;
    const stepState = findStepState(ablaufIdx, stepIdx);
    const step = playProg.value[ablaufIdx]?.steps[stepIdx];
    if (!stepState || !step) return;
    updateFailState(stepState, failType, step.type);
  };

  const retryStep = () => {
    if (!playLastDeviceStep.value) return;
    const { ablaufIdx, stepIdx } = playLastDeviceStep.value;
    const state = findStepState(ablaufIdx, stepIdx);
    if (state?.state !== StepState.DONE) return;
    state.noBirds += 1;
    state.state = StepState.PENDING;
    playScore.value.totalPoints -= state.pointValue;
    state.pointsEarned = 0;
    currentAblaufIndex.value = ablaufIdx;
    currentStepIndex.value = stepIdx;
    playPartialStep.value = null;
    playLastDeviceStep.value = null;
  };

  // Called by the UI when the user confirms the program is finished
  // (clicking Fertig or using a fail button at program end).
  const confirmComplete = () => {
    playComplete.value = true;
  };

  const closePlayback = () => {
    playProg.value = null;
    currentAblaufIndex.value = 0;
    currentStepIndex.value = 0;
    playPartialStep.value = null;
    playRaffaleStarted.value = false;
    playScoreMode.value = false;
    playScore.value = { totalPoints: 0, stepStates: [] };
    playLastDeviceStep.value = null;
    playComplete.value = false;
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
  const loadCompletedAblaeufe = () => {
    try {
      const stored = localStorage.getItem(COMPLETED_ABLAUF_KEY);
      if (stored) {
        completedAblaeufe.value = new Set(JSON.parse(stored));
      }
    } catch (e) {
      console.error('Failed to load completed ablaeufe:', e);
    }
  };

  const saveCompletedAblaeufe = () => {
    try {
      localStorage.setItem(COMPLETED_ABLAUF_KEY, JSON.stringify([...completedAblaeufe.value]));
    } catch (e) {
      console.error('Failed to save completed ablaeufe:', e);
    }
  };

  const markAblaufComplete = (ablaufId) => {
    if (ablaufId) {
      completedAblaeufe.value.add(ablaufId);
      saveCompletedAblaeufe();
    }
  };

  const isAblaufCompleted = (ablaufId) => {
    return ablaufId ? completedAblaeufe.value.has(ablaufId) : false;
  };

  return {
    // State
    playProg,
    currentAblaufIndex,
    currentStepIndex,
    playPartialStep,
    playRaffaleStarted,
    playScoreMode,
    playScore,
    playLastDeviceStep,
    playComplete,
    sessionPlayers,
    currentPlayerIndex,
    roundOrder,
    roundStartIndex,
    completedAblaeufe,
    // Computed
    currentAblauf,
    currentStep,
    isAtProgramEnd,
    currentPlayer,
    isMultiPlayer,
    isLastPlayerInRound,
    completionPct,
    ablaufCompletionPct,
    playProgress,
    canRetry,
    playerScores,
    playerScoresSorted,
    // Actions
    playProgramWithScore,
    advancePlayStep,
    completeRaffaleStep,
    markStepDone,
    failStep,
    retryStep,
    confirmComplete,
    closePlayback,
    getPointValueForStep,
    setupPlayers,
    buildRoundOrder,
    endPlayerTurn,
    setRoundStartOverride,
    loadCompletedAblaeufe,
    saveCompletedAblaeufe,
    markAblaufComplete,
    isAblaufCompleted,
  };
});
