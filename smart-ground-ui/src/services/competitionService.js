import { BASE_URL } from './apiClient.js';

/**
 * API-Service für Wettkampf-Verwaltung.
 */
export const competitionService = {
  // ── Templates ──

  /**
   * Listet alle verfügbaren Wettkampf-Vorlagen auf.
   * @param {Object} params - { type?: string, page?: number, size?: number }
   * @returns {Promise<Page<CompetitionTemplateResponse>>}
   */
  async listTemplates(params = {}) {
    const url = new URL(`${BASE_URL}/session-templates`);
    if (params.type) url.searchParams.append('type', params.type);
    if (params.page !== undefined) url.searchParams.append('page', params.page);
    if (params.size !== undefined) url.searchParams.append('size', params.size);

    const response = await fetch(url);
    if (!response.ok) throw new Error(`Failed to list templates: ${response.status}`);
    return response.json();
  },

  /**
   * Erstellt eine neue Wettkampf-Vorlage.
   * @param {CreateCompetitionTemplateRequest} templateData
   * @returns {Promise<CompetitionTemplateResponse>}
   */
  async createTemplate(templateData) {
    const response = await fetch(`${BASE_URL}/session-templates`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(templateData),
    });
    if (!response.ok) throw new Error(`Failed to create template: ${response.status}`);
    return response.json();
  },

  /**
   * Aktualisiert eine Wettkampf-Vorlage.
   * @param {UUID} templateId
   * @param {CreateCompetitionTemplateRequest} templateData
   * @returns {Promise<CompetitionTemplateResponse>}
   */
  async updateTemplate(templateId, templateData) {
    const response = await fetch(`${BASE_URL}/session-templates/${templateId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(templateData),
    });
    if (!response.ok) throw new Error(`Failed to update template: ${response.status}`);
    return response.json();
  },

  /**
   * Löscht eine Wettkampf-Vorlage.
   * @param {UUID} templateId
   * @returns {Promise<void>}
   */
  async deleteTemplate(templateId) {
    const response = await fetch(`${BASE_URL}/session-templates/${templateId}`, {
      method: 'DELETE',
    });
    if (!response.ok) throw new Error(`Failed to delete template: ${response.status}`);
  },

  // ── Sessions ──

  /**
   * Erstellt eine neue Wettkampf-Sitzung aus einer Vorlage.
   * @param {CreateSessionRequest} request
   * @returns {Promise<SessionResponse>}
   */
  async createSession(request) {
    const response = await fetch(`${BASE_URL}/sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
    if (!response.ok) throw new Error(`Failed to create session: ${response.status}`);
    return response.json();
  },

  /**
   * Aktualisiert den Status einer Sitzung.
   * @param {UUID} sessionId
   * @param {string} newStatus - "ACTIVE", "PAUSED", "COMPLETED", "ABANDONED"
   * @returns {Promise<SessionResponse>}
   */
  async updateSessionStatus(sessionId, newStatus) {
    const response = await fetch(`${BASE_URL}/sessions/${sessionId}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: newStatus }),
    });
    if (!response.ok) throw new Error(`Failed to update session status: ${response.status}`);
    return response.json();
  },

  // ── Group Registration ──

  /**
   * Registriert eine Gruppe an einem Bereich zum Spielen.
   * @param {UUID} sessionId
   * @param {UUID} groupId
   * @param {UUID} rangeId
   * @returns {Promise<void>}
   */
  async registerGroupAtRange(sessionId, groupId, rangeId) {
    const response = await fetch(`${BASE_URL}/sessions/${sessionId}/groups/${groupId}/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ rangeId }),
    });
    if (!response.ok) throw new Error(`Failed to register group: ${response.status}`);
  },

  /**
   * Entfernt eine Gruppe von einem Bereich.
   * @param {UUID} sessionId
   * @param {UUID} groupId
   * @returns {Promise<void>}
   */
  async unregisterGroupFromRange(sessionId, groupId) {
    const response = await fetch(`${BASE_URL}/sessions/${sessionId}/groups/${groupId}/register`, {
      method: 'DELETE',
    });
    if (!response.ok) throw new Error(`Failed to unregister group: ${response.status}`);
  },

  /**
   * Lädt die Gruppen für einen bestimmten Bereich (aktiv + Warteschlange).
   * @param {UUID} sessionId
   * @param {UUID} rangeId
   * @returns {Promise<{ active: ShooterGroup[], queue: ShooterGroup[] }>}
   */
  async getGroupsAtRange(sessionId, rangeId) {
    const response = await fetch(`${BASE_URL}/sessions/${sessionId}/range/${rangeId}/groups`);
    if (!response.ok) throw new Error(`Failed to get groups at range: ${response.status}`);
    return response.json();
  },

  // ── Leaderboards ──

  /**
   * Lädt das aktuelle Leaderboard für eine Sitzung.
   * @param {UUID} sessionId
   * @returns {Promise<SessionLeaderboardResponse>}
   */
  async getSessionLeaderboard(sessionId) {
    const response = await fetch(`${BASE_URL}/sessions/${sessionId}/leaderboard`);
    if (!response.ok) throw new Error(`Failed to get leaderboard: ${response.status}`);
    return response.json();
  },

  /**
   * Exportiert das Leaderboard einer Sitzung.
   * @param {UUID} sessionId
   * @param {string} format - "json" | "csv"
   * @returns {Promise<Blob>}
   */
  async exportLeaderboard(sessionId, format = 'json') {
    const response = await fetch(
      `${BASE_URL}/sessions/${sessionId}/leaderboard/export?format=${format}`
    );
    if (!response.ok) throw new Error(`Failed to export leaderboard: ${response.status}`);
    return response.blob();
  },

  /**
   * Lädt die Top-Spieler nach Gesamtpunkten.
   * @param {Object} params - { page?: number, size?: number }
   * @returns {Promise<Page<CareerStatsResponse>>}
   */
  async getTopPlayers(params = {}) {
    const url = new URL(`${BASE_URL}/career-stats/top-players`);
    if (params.page !== undefined) url.searchParams.append('page', params.page);
    if (params.size !== undefined) url.searchParams.append('size', params.size);

    const response = await fetch(url);
    if (!response.ok) throw new Error(`Failed to get top players: ${response.status}`);
    return response.json();
  },

  /**
   * Lädt die Top-Spieler nach Siegen.
   * @param {Object} params - { page?: number, size?: number }
   * @returns {Promise<Page<CareerStatsResponse>>}
   */
  async getTopPlayersByWins(params = {}) {
    const url = new URL(`${BASE_URL}/career-stats/top-players/wins`);
    if (params.page !== undefined) url.searchParams.append('page', params.page);
    if (params.size !== undefined) url.searchParams.append('size', params.size);

    const response = await fetch(url);
    if (!response.ok) throw new Error(`Failed to get top players by wins: ${response.status}`);
    return response.json();
  },

  /**
   * Lädt die Karriere-Statistiken für einen Spieler.
   * @param {UUID} userId
   * @returns {Promise<CareerStatsResponse>}
   */
  async getCareerStats(userId) {
    const response = await fetch(`${BASE_URL}/career-stats/${userId}`);
    if (!response.ok) throw new Error(`Failed to get career stats: ${response.status}`);
    return response.json();
  },
};
