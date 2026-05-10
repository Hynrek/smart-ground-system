import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { useProgramStore } from '@/stores/programStore.js';
import { sendDeviceCommand } from '@/services/deviceApi.js';

export const usePlaySessionStore = defineStore('playSession', () => {
  const programStore = useProgramStore();

  // Playback state
  const playProg = ref(null); // Segment[] | null  (all segments of the program)
  const currentSegmentIndex = ref(0);
  const currentStepIndex = ref(0); // renamed from playCurrentStep
  const playPartialStep = ref(null); // 'first' | 'second' for a.schuss
  const playRaffaleStarted = ref(false);
  const playScoreMode = ref(false);
  const playScore = ref({ totalPoints: 0, stepStates: [] });
  const playLastDeviceStep = ref(null); // { segmentIdx, stepIdx } | null — for NoBird

  // Multi-player state
  const sessionPlayers = ref([]); // SessionPlayer[] — ordered roster for this session
  const currentPlayerIndex = ref(0); // index in roundOrder[] for the current turn
  const roundOrder = ref([]); // player indices in play order this round
  const roundStartIndex = ref(0); // which player starts each segment (rotates)

  // Computed: current segment and step
  const currentSegment = computed(() => playProg.value?.[currentSegmentIndex.value] ?? null);

  const currentStep = computed(() => currentSegment.value?.steps[currentStepIndex.value] ?? null);

  // Multi-player computeds
  const isMultiPlayer = computed(() => sessionPlayers.value.length > 1);

  const currentPlayer = computed(() =>
    sessionPlayers.value[roundOrder.value[currentPlayerIndex.value]] ?? null
  );

  const isLastPlayerInRound = computed(() =>
    currentPlayerIndex.value === roundOrder.value.length - 1
  );

  // Overall completion across all segments
  const completionPct = computed(() => {
    if (!playProg.value) return 0;
    const total = playProg.value.reduce((sum, seg) => sum + seg.steps.length, 0);
    if (total === 0) return 0;
    const done = playScore.value.stepStates.filter((s) => s.state !== 'pending').length;
    return Math.round((done / total) * 100);
  });

  // Per-segment completion
  const segmentCompletionPct = computed(() => {
    if (!currentSegment.value) return 0;
    const segTotal = currentSegment.value.steps.length;
    if (segTotal === 0) return 0;
    const segDone = playScore.value.stepStates.filter(
      (s) => s.segmentIndex === currentSegmentIndex.value && s.state !== 'pending'
    ).length;
    return Math.round((segDone / segTotal) * 100);
  });

  // Progress through program
  const playProgress = computed(() => {
    if (!playProg.value) return 0;
    const totalSteps = playProg.value.reduce((sum, seg) => sum + seg.steps.length, 0);
    const flatIndex =
      playProg.value.slice(0, currentSegmentIndex.value).reduce((sum, seg) => sum + seg.steps.length, 0) +
      currentStepIndex.value;
    return ((flatIndex + 1) / totalSteps) * 100;
  });

  const getPointValueForStep = (step) => {
    if (step.type === 'solo') return 1;
    if (step.type === 'pair' || step.type === 'a.schuss') return 2;
    if (step.type === 'raffale') return 2;
    return 1;
  };

  const findStepState = (segIdx, stepIdx) => {
    const playerId = currentPlayer.value?.id;
    return (
      playScore.value.stepStates.find(
        (s) => s.playerId === playerId && s.segmentIndex === segIdx && s.stepIndex === stepIdx
      ) ?? null
    );
  };

  const setupPlayers = (players) => {
    sessionPlayers.value = players;
    roundStartIndex.value = 0;
    currentPlayerIndex.value = 0;
    buildRoundOrder();
  };

  const buildRoundOrder = () => {
    const count = sessionPlayers.value.length;
    if (count === 0) {
      roundOrder.value = [];
      return;
    }
    const order = [];
    for (let i = 0; i < count; i++) {
      order.push((roundStartIndex.value + i) % count);
    }
    roundOrder.value = order;
  };

  const playProgramWithScore = (programId, players = []) => {
    const prog = programStore.savedPrograms.find((p) => p.id === programId);
    if (!prog) return;

    if (players.length > 0) {
      setupPlayers(players);
    } else {
      setupPlayers([{ id: 'solo', type: 'user', userId: null, displayName: 'Spieler' }]);
    }

    playProg.value = prog.segments;
    currentSegmentIndex.value = 0;
    currentStepIndex.value = 0;
    playScoreMode.value = true;
    playPartialStep.value = null;
    playRaffaleStarted.value = false;
    playLastDeviceStep.value = null;

    const stepStates = [];
    sessionPlayers.value.forEach((player) => {
      prog.segments.forEach((seg, segIdx) => {
        seg.steps.forEach((step, stepIdx) => {
          stepStates.push({
            playerId: player.id,
            segmentIndex: segIdx,
            stepIndex: stepIdx,
            state: 'pending',
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
    const currentStepVal = currentStep.value;
    if (!currentStepVal) return;

    try {
      if (currentStepVal.type === 'solo') {
        await sendDeviceCommand(currentStepVal.deviceId);
        markStepDone();
      } else if (currentStepVal.type === 'pair') {
        await Promise.all([
          sendDeviceCommand(currentStepVal.deviceId1),
          sendDeviceCommand(currentStepVal.deviceId2),
        ]);
        markStepDone();
      } else if (currentStepVal.type === 'a.schuss') {
        // a.schuss requires two clicks
        if (playPartialStep.value === null) {
          // First click: send command to first device
          await sendDeviceCommand(currentStepVal.deviceId1);
          playPartialStep.value = 'first';
        } else if (playPartialStep.value === 'first') {
          // Second click: send command to second device
          await sendDeviceCommand(currentStepVal.deviceId2);
          playPartialStep.value = null;
          markStepDone();
        }
      } else if (currentStepVal.type === 'raffale') {
        // Raffale: timer starts on first click, auto-advances after delay
        if (!playRaffaleStarted.value) {
          await sendDeviceCommand(currentStepVal.deviceId);
          playRaffaleStarted.value = true;
        }
      }
    } catch (err) {
      console.error('Failed to send device command during playback:', err);
    }
  };

  const completeRaffaleStep = async () => {
    const currentStepVal = currentStep.value;
    if (currentStepVal && currentStepVal.type === 'raffale') {
      try {
        await sendDeviceCommand(currentStepVal.deviceId);
      } catch (err) {
        console.error('Failed to send second raffale command:', err);
      }
      playRaffaleStarted.value = false;
      markStepDone();
    }
  };

  const markStepDone = () => {
    if (!playScore.value.stepStates) return;
    const state = findStepState(currentSegmentIndex.value, currentStepIndex.value);
    if (state && state.state === 'pending') {
      state.state = 'done';
      state.pointsEarned = state.pointValue;
      playScore.value.totalPoints += state.pointValue;
      playLastDeviceStep.value = { segmentIdx: currentSegmentIndex.value, stepIdx: currentStepIndex.value };
    }
    moveToNextStep();
  };

  const moveToNextStep = () => {
    const seg = playProg.value[currentSegmentIndex.value];
    if (currentStepIndex.value < seg.steps.length - 1) {
      currentStepIndex.value += 1;
      playPartialStep.value = null;
    } else if (currentSegmentIndex.value < playProg.value.length - 1) {
      // Advance to next segment
      currentSegmentIndex.value += 1;
      currentStepIndex.value = 0;
      playPartialStep.value = null;
      playLastDeviceStep.value = null;
    } else {
      // All segments done
      playLastDeviceStep.value = null;
    }
  };

  const getPointDeduction = (state) => {
    if (state === 'done') return 0;
    if (state === 'failed-both') return 2;
    if (state === 'failed-a' || state === 'failed-b') return 1;
    return 0;
  };

  const updateFailState = (stepState, newFailType, stepType) => {
    const oldDeduction = getPointDeduction(stepState.state);

    if (stepType === 'solo') {
      stepState.state = 'failed-both';
    } else {
      stepState.state = 'failed-' + newFailType;
    }

    const newDeduction = getPointDeduction(stepState.state);
    const pointDifference = newDeduction - oldDeduction;
    playScore.value.totalPoints -= pointDifference;
  };

  const failStep = (failType) => {
    if (!playScore.value.stepStates || !playLastDeviceStep.value) return;
    const { segmentIdx, stepIdx } = playLastDeviceStep.value;
    const lastStepState = findStepState(segmentIdx, stepIdx);
    const lastStep = playProg.value[segmentIdx].steps[stepIdx];

    if (!lastStepState || !lastStep) return;
    updateFailState(lastStepState, failType, lastStep.type);
  };

  const retryStep = () => {
    if (!playScore.value.stepStates || !playLastDeviceStep.value) return;
    const { segmentIdx, stepIdx } = playLastDeviceStep.value;
    const state = findStepState(segmentIdx, stepIdx);
    if (state && state.state === 'done') {
      state.noBirds += 1; // increment count
      state.state = 'pending';
      playScore.value.totalPoints -= state.pointValue;
      state.pointsEarned = 0;
      currentSegmentIndex.value = segmentIdx;
      currentStepIndex.value = stepIdx;
      playPartialStep.value = null;
    }
  };

  const closePlayback = () => {
    playProg.value = null;
    currentSegmentIndex.value = 0;
    currentStepIndex.value = 0;
    playPartialStep.value = null;
    playRaffaleStarted.value = false;
    playScoreMode.value = false;
    playScore.value = { totalPoints: 0, stepStates: [] };
    playLastDeviceStep.value = null;
  };

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

  const playerScores = computed(() => {
    return sessionPlayers.value.map((player) => {
      const states = playScore.value.stepStates.filter((s) => s.playerId === player.id);
      const total = states.reduce((sum, s) => sum + s.pointsEarned, 0);
      const max = states.reduce((sum, s) => sum + s.pointValue, 0);
      const done = states.filter((s) => s.state !== 'pending').length;
      const completionPct = states.length > 0 ? Math.round((done / states.length) * 100) : 0;
      return { player, totalPoints: total, maxPoints: max, completionPct };
    });
  });

  const playerScoresSorted = computed(() =>
    [...playerScores.value].sort((a, b) => b.totalPoints - a.totalPoints)
  );

  return {
    playProg,
    currentSegmentIndex,
    currentStepIndex,
    playPartialStep,
    playRaffaleStarted,
    playScoreMode,
    playScore,
    playLastDeviceStep,
    sessionPlayers,
    currentPlayerIndex,
    roundOrder,
    roundStartIndex,
    currentSegment,
    currentStep,
    currentPlayer,
    isMultiPlayer,
    isLastPlayerInRound,
    completionPct,
    segmentCompletionPct,
    playProgress,
    playerScores,
    playerScoresSorted,
    playProgramWithScore,
    advancePlayStep,
    completeRaffaleStep,
    markStepDone,
    failStep,
    retryStep,
    closePlayback,
    getPointValueForStep,
    setupPlayers,
    buildRoundOrder,
    endPlayerTurn,
    setRoundStartOverride,
  };
});
