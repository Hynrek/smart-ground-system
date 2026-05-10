import { API_BASE_URL } from '@/config.js';

/**
 * API-Service für Bracket-Turniere (Single/Double Elimination).
 */
export const bracketService = {
  /**
   * Initialisiert ein neues Bracket-Turnier.
   * POST /api/sessions/{sessionId}/bracket
   */
  async initializeBracket(sessionId, config) {
    const response = await fetch(`${API_BASE_URL}/sessions/${sessionId}/bracket`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        bracketType: config.bracketType,           // SINGLE_ELIMINATION, DOUBLE_ELIMINATION
        seedingStrategy: config.seedingStrategy,   // BY_CAREER_STATS, MANUAL, BALANCED
        tiebreakers: config.tiebreakers || [],     // ["TOTAL_SCORE", "WINS", ...]
      }),
    });

    if (!response.ok) {
      throw new Error(`Failed to initialize bracket: ${response.status}`);
    }
    return response.json();
  },

  /**
   * Lädt den aktuellen Bracket-State.
   * GET /api/sessions/{sessionId}/bracket
   */
  async getBracketState(sessionId) {
    const response = await fetch(`${API_BASE_URL}/sessions/${sessionId}/bracket`);
    if (!response.ok) {
      throw new Error(`Failed to get bracket state: ${response.status}`);
    }
    return response.json();
  },

  /**
   * Holt die aktuelle Bracket-Phase.
   * GET /api/sessions/{sessionId}/bracket/phase
   */
  async getBracketPhase(sessionId) {
    const response = await fetch(`${API_BASE_URL}/sessions/${sessionId}/bracket/phase`);
    if (!response.ok) {
      throw new Error(`Failed to get bracket phase: ${response.status}`);
    }
    const text = await response.text();
    return text;
  },

  /**
   * Bestätigt das Seeding (SETUP → SEEDING).
   * PUT /api/sessions/{sessionId}/bracket/seeding
   */
  async confirmSeeding(sessionId) {
    const response = await fetch(`${API_BASE_URL}/sessions/${sessionId}/bracket/seeding`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
    });

    if (!response.ok) {
      throw new Error(`Failed to confirm seeding: ${response.status}`);
    }
    return response.json();
  },

  /**
   * Startet die Bracket-Spielphase (SEEDING → IN_PROGRESS).
   * PUT /api/sessions/{sessionId}/bracket/start
   */
  async startBracketPlay(sessionId) {
    const response = await fetch(`${API_BASE_URL}/sessions/${sessionId}/bracket/start`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
    });

    if (!response.ok) {
      throw new Error(`Failed to start bracket play: ${response.status}`);
    }
    return response.json();
  },

  /**
   * Listet alle Matches auf (optional gefiltert nach Runde).
   * GET /api/sessions/{sessionId}/bracket/matches?round={roundNumber}
   */
  async listMatches(sessionId, roundNumber = null) {
    const url = new URL(`${API_BASE_URL}/sessions/${sessionId}/bracket/matches`);
    if (roundNumber !== null) {
      url.searchParams.append('round', roundNumber);
    }

    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`Failed to list matches: ${response.status}`);
    }
    return response.json();
  },

  /**
   * Holt das nächste zu spielende Match.
   * GET /api/sessions/{sessionId}/bracket/matches/next
   */
  async getNextMatch(sessionId) {
    const response = await fetch(`${API_BASE_URL}/sessions/${sessionId}/bracket/matches/next`);
    if (response.status === 204) {
      return null; // No more matches
    }
    if (!response.ok) {
      throw new Error(`Failed to get next match: ${response.status}`);
    }
    return response.json();
  },

  /**
   * Zeichnet ein Match-Ergebnis auf.
   * POST /api/sessions/{sessionId}/bracket/matches/{matchNumber}
   */
  async recordMatchWinner(sessionId, matchNumber, result) {
    const response = await fetch(
      `${API_BASE_URL}/sessions/${sessionId}/bracket/matches/${matchNumber}`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          winnerId: result.winnerId,
          score1: result.score1,
          score2: result.score2,
        }),
      }
    );

    if (!response.ok) {
      throw new Error(`Failed to record match winner: ${response.status}`);
    }
    return response.json();
  },

  /**
   * Holt das Leaderboard eines abgeschlossenen Bracket-Turniers.
   * GET /api/sessions/{sessionId}/bracket/leaderboard
   */
  async getBracketLeaderboard(sessionId) {
    const response = await fetch(`${API_BASE_URL}/sessions/${sessionId}/bracket/leaderboard`);
    if (!response.ok) {
      throw new Error(`Failed to get bracket leaderboard: ${response.status}`);
    }
    return response.json();
  },

  /**
   * Exportiert das Bracket (JSON, PDF, Image).
   * GET /api/sessions/{sessionId}/bracket/export?format={format}
   */
  async exportBracket(sessionId, format = 'json') {
    const url = new URL(`${API_BASE_URL}/sessions/${sessionId}/bracket/export`);
    url.searchParams.append('format', format);

    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`Failed to export bracket: ${response.status}`);
    }

    if (format === 'json') {
      return response.json();
    }

    // For PDF/image: return blob for download
    return response.blob();
  },
};
