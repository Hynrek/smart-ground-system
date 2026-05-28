import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { bracketService } from '@/services/bracketService.js';

export const useBracketStore = defineStore('bracket', () => {
  // ── State ──
  const currentSession = ref(null);           // SessionID for current bracket
  const bracketType = ref(null);              // SINGLE_ELIMINATION | DOUBLE_ELIMINATION
  const bracketPhase = ref(null);             // SETUP, SEEDING, IN_PROGRESS, FINALS, COMPLETED
  const seededPlayers = ref([]);              // [{ playerId, seed, displayName }]
  const matchesByRound = ref({});             // { 1: [matches], 2: [matches], ... }
  const bracketMetadata = ref({});            // totalRounds, totalByes, etc.
  const selectedMatch = ref(null);            // Currently selected match for UI highlight
  const isLoading = ref(false);
  const error = ref(null);
  const champion = ref(null);                 // Tournament winner

  // ── Computed ──

  /**
   * Nächstes Match, das keine Ergebnisse hat.
   */
  const nextUnplayedMatch = computed(() => {
    for (const round in matchesByRound.value) {
      for (const match of matchesByRound.value[round]) {
        if (!match.isBye && !match.winnerId && match.contestant1Id && match.contestant2Id) {
          return match;
        }
      }
    }
    return null;
  });

  /**
   * Fortschritt pro Runde (% abgeschlossener Matches).
   */
  const roundProgress = computed(() => {
    const progress = {};
    for (const round in matchesByRound.value) {
      const matches = matchesByRound.value[round];
      const nonByeMatches = matches.filter(m => !m.isBye).length;
      const completedMatches = matches.filter(m => m.winnerId).length;
      progress[round] = nonByeMatches > 0 ? Math.round((completedMatches / nonByeMatches) * 100) : 100;
    }
    return progress;
  });

  /**
   * Gesamter Turnier-Fortschritt (%).
   */
  const bracketProgress = computed(() => {
    let totalMatches = 0;
    let completedMatches = 0;
    for (const round in matchesByRound.value) {
      const matches = matchesByRound.value[round];
      totalMatches += matches.filter(m => !m.isBye).length;
      completedMatches += matches.filter(m => m.winnerId).length;
    }
    return totalMatches > 0 ? Math.round((completedMatches / totalMatches) * 100) : 0;
  });

  /**
   * Prüft, ob das Turnier abgeschlossen ist.
   */
  const isComplete = computed(() => bracketPhase.value === 'COMPLETED');

  /**
   * Gibt die aktuelle Runde zurück (wo Matches gespielt werden).
   */
  const currentRound = computed(() => {
    for (const round in matchesByRound.value) {
      const matches = matchesByRound.value[round];
      if (matches.some(m => !m.isBye && !m.winnerId)) {
        return parseInt(round);
      }
    }
    return null;
  });

  // ── Actions ──

  /**
   * Lädt das Bracket aus dem Backend.
   */
  const initializeBracket = async (sessionId, config) => {
    try {
      isLoading.value = true;
      error.value = null;

      // POST initialize bracket
      await bracketService.initializeBracket(sessionId, config);

      // GET bracket state
      await loadBracketState(sessionId);

      currentSession.value = sessionId;
    } catch (err) {
      error.value = `Failed to initialize bracket: ${err.message}`;
      console.error(err);
    } finally {
      isLoading.value = false;
    }
  };

  /**
   * Lädt den aktuellen Bracket-State.
   */
  const loadBracketState = async (sessionId) => {
    try {
      const response = await bracketService.getBracketState(sessionId);

      bracketType.value = response.type;
      seededPlayers.value = response.seededPlayers || [];
      bracketMetadata.value = {
        totalByes: response.totalByes,
        roundCount: response.roundCount,
      };

      // TODO: Parse matches into matchesByRound structure from backend
      // For now, initialize empty structure
      matchesByRound.value = {};
    } catch (err) {
      error.value = `Failed to load bracket state: ${err.message}`;
    }
  };

  /**
   * Zeichnet ein Match-Ergebnis auf.
   */
  const recordMatchWinner = async (matchNumber, winnerId, score1, score2) => {
    if (!currentSession.value) {
      error.value = 'No active bracket session';
      return;
    }

    try {
      await bracketService.recordMatchWinner(currentSession.value, matchNumber, {
        winnerId,
        score1,
        score2,
      });

      // Match-Update kommt via WebSocket - keine lokale Mutation nötig
    } catch (err) {
      error.value = `Failed to record match winner: ${err.message}`;
      console.error(err);
    }
  };

  /**
   * Bestätigt das Seeding.
   */
  const confirmSeeding = async () => {
    if (!currentSession.value) {
      error.value = 'No active bracket session';
      return;
    }

    try {
      await bracketService.confirmSeeding(currentSession.value);
      // Phase-Update kommt via WebSocket
    } catch (err) {
      error.value = `Failed to confirm seeding: ${err.message}`;
    }
  };

  /**
   * Startet die Bracket-Spielphase.
   */
  const startPlay = async () => {
    if (!currentSession.value) {
      error.value = 'No active bracket session';
      return;
    }

    try {
      await bracketService.startBracketPlay(currentSession.value);
      // Phase-Update kommt via WebSocket
    } catch (err) {
      error.value = `Failed to start bracket play: ${err.message}`;
    }
  };

  /**
   * Wählt ein Match zur UI-Hervorhebung aus.
   */
  const selectMatch = (matchNumber) => {
    for (const round in matchesByRound.value) {
      const match = matchesByRound.value[round].find(m => m.matchNumber === matchNumber);
      if (match) {
        selectedMatch.value = match;
        return;
      }
    }
  };

  // ── WebSocket Handlers ──

  /**
   * Callback für Bracket-State-Updates.
   */
  const onBracketStateUpdate = (data) => {
    bracketType.value = data.type;
    seededPlayers.value = data.seededPlayers || [];
    bracketMetadata.value = {
      totalByes: data.totalByes,
      roundCount: data.roundCount,
    };
  };

  /**
   * Callback für Match-Ergebnis-Updates.
   */
  const onMatchResultUpdate = (data) => {
    const { matchNumber, winnerId, score1, score2 } = data;

    // Finde und update den Match
    for (const round in matchesByRound.value) {
      const match = matchesByRound.value[round].find(m => m.matchNumber === matchNumber);
      if (match) {
        match.winnerId = winnerId;
        match.score1 = score1;
        match.score2 = score2;
        break;
      }
    }

    // Refresh next unplayed match
    selectedMatch.value = nextUnplayedMatch.value;
  };

  /**
   * Callback für Phase-Änderungen.
   */
  const onPhaseChange = (data) => {
    const { newPhase } = data;
    bracketPhase.value = newPhase;
  };

  /**
   * Callback für Runden-Abschluss.
   */
  const onRoundCompletion = (_data) => {
    // UI-Hinweis: Runde abgeschlossen, nächste Runde verfügbar
    // Automatisch zum nächsten Match springen
    setTimeout(() => {
      selectedMatch.value = nextUnplayedMatch.value;
    }, 500);
  };

  /**
   * Callback für Turnier-Abschluss.
   */
  const onBracketCompleted = (data) => {
    champion.value = {
      id: data.championId,
      name: data.championName,
    };
    bracketPhase.value = 'COMPLETED';
  };

  /**
   * Bereinigt den Bracket-State.
   */
  const clearBracket = () => {
    currentSession.value = null;
    bracketType.value = null;
    bracketPhase.value = null;
    seededPlayers.value = [];
    matchesByRound.value = {};
    bracketMetadata.value = {};
    selectedMatch.value = null;
    champion.value = null;
    error.value = null;
  };

  return {
    // State
    currentSession,
    bracketType,
    bracketPhase,
    seededPlayers,
    matchesByRound,
    bracketMetadata,
    selectedMatch,
    champion,
    isLoading,
    error,

    // Computed
    nextUnplayedMatch,
    roundProgress,
    bracketProgress,
    isComplete,
    currentRound,

    // Actions
    initializeBracket,
    loadBracketState,
    recordMatchWinner,
    confirmSeeding,
    startPlay,
    selectMatch,
    onBracketStateUpdate,
    onMatchResultUpdate,
    onPhaseChange,
    onRoundCompletion,
    onBracketCompleted,
    clearBracket,
  };
});
