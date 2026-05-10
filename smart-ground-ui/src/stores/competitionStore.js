import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { competitionService } from '@/services/competitionService.js';

export const useCompetitionStore = defineStore('competition', () => {
  // ── Templates ──
  const templates = ref([]); // SessionTemplate[]
  const selectedTemplate = ref(null); // SessionTemplate | null

  // ── Current Session ──
  const currentSession = ref(null); // LiveSession | null
  const sessionLeaderboard = ref(null); // SessionLeaderboardResponse | null

  // ── Range Operator State ──
  const selectedRange = ref(null); // UUID | null
  const groupsAtRange = ref([]); // ShooterGroup[]
  const rangeQueue = ref([]); // ShooterGroup[] — waiting for range

  // ── Global Career Stats ──
  const topPlayers = ref([]); // CareerStatsResponse[]
  const topPlayersByWins = ref([]); // CareerStatsResponse[]

  // ── Loading State ──
  const isLoading = ref(false);
  const error = ref(null);

  // ── Computed ──
  const templatesByType = computed(() => {
    return templates.value.reduce((acc, t) => {
      if (!acc[t.type]) acc[t.type] = [];
      acc[t.type].push(t);
      return acc;
    }, {});
  });

  const isSessionActive = computed(() =>
    currentSession.value && currentSession.value.status === 'ACTIVE'
  );

  const isSessionCompleted = computed(() =>
    currentSession.value && currentSession.value.status === 'COMPLETED'
  );

  // ── Template Management ──

  const loadTemplates = async (type = null) => {
    try {
      isLoading.value = true;
      const page = await competitionService.listTemplates({ type, page: 0, size: 100 });
      templates.value = page.content;
    } catch (err) {
      error.value = `Failed to load templates: ${err.message}`;
    } finally {
      isLoading.value = false;
    }
  };

  const createTemplate = async (templateData) => {
    try {
      isLoading.value = true;
      const newTemplate = await competitionService.createTemplate(templateData);
      templates.value.push(newTemplate);
      return newTemplate;
    } catch (err) {
      error.value = `Failed to create template: ${err.message}`;
      throw err;
    } finally {
      isLoading.value = false;
    }
  };

  const updateTemplate = async (templateId, templateData) => {
    try {
      isLoading.value = true;
      const updated = await competitionService.updateTemplate(templateId, templateData);
      const idx = templates.value.findIndex((t) => t.id === templateId);
      if (idx >= 0) {
        templates.value[idx] = updated;
      }
      return updated;
    } catch (err) {
      error.value = `Failed to update template: ${err.message}`;
      throw err;
    } finally {
      isLoading.value = false;
    }
  };

  const deleteTemplate = async (templateId) => {
    try {
      isLoading.value = true;
      await competitionService.deleteTemplate(templateId);
      templates.value = templates.value.filter((t) => t.id !== templateId);
    } catch (err) {
      error.value = `Failed to delete template: ${err.message}`;
      throw err;
    } finally {
      isLoading.value = false;
    }
  };

  // ── Session Lifecycle ──

  const selectTemplate = (templateId) => {
    selectedTemplate.value = templates.value.find((t) => t.id === templateId);
  };

  const initSession = async (templateId, groups = []) => {
    try {
      isLoading.value = true;
      const template = templates.value.find((t) => t.id === templateId);
      if (!template) throw new Error('Template not found');

      const request = {
        type: template.type,
        templateId: template.id,
        groups: groups,
      };

      currentSession.value = await competitionService.createSession(request);
      return currentSession.value;
    } catch (err) {
      error.value = `Failed to initialize session: ${err.message}`;
      throw err;
    } finally {
      isLoading.value = false;
    }
  };

  const updateSessionStatus = async (sessionId, newStatus) => {
    try {
      const updated = await competitionService.updateSessionStatus(sessionId, newStatus);
      currentSession.value = updated;
      return updated;
    } catch (err) {
      error.value = `Failed to update session status: ${err.message}`;
      throw err;
    }
  };

  // ── Range Operator Functions ──

  const selectRange = (rangeId) => {
    selectedRange.value = rangeId;
  };

  const registerGroupAtRange = async (sessionId, groupId, rangeId) => {
    try {
      await competitionService.registerGroupAtRange(sessionId, groupId, rangeId);
      // Reload groups at this range
      await loadGroupsAtRange(sessionId, rangeId);
    } catch (err) {
      error.value = `Failed to register group: ${err.message}`;
      throw err;
    }
  };

  const unregisterGroupFromRange = async (sessionId, groupId) => {
    try {
      await competitionService.unregisterGroupFromRange(sessionId, groupId);
      // Reload groups
      if (selectedRange.value) {
        await loadGroupsAtRange(sessionId, selectedRange.value);
      }
    } catch (err) {
      error.value = `Failed to unregister group: ${err.message}`;
      throw err;
    }
  };

  const loadGroupsAtRange = async (sessionId, rangeId) => {
    try {
      const response = await competitionService.getGroupsAtRange(sessionId, rangeId);
      // Parse response into active/queue
      groupsAtRange.value = response.active || [];
      rangeQueue.value = response.queue || [];
    } catch (err) {
      error.value = `Failed to load groups at range: ${err.message}`;
    }
  };

  // ── Leaderboard Management ──

  const loadSessionLeaderboard = async (sessionId) => {
    try {
      isLoading.value = true;
      sessionLeaderboard.value = await competitionService.getSessionLeaderboard(sessionId);
    } catch (err) {
      error.value = `Failed to load leaderboard: ${err.message}`;
    } finally {
      isLoading.value = false;
    }
  };

  const loadTopPlayers = async (limit = 10) => {
    try {
      const page = await competitionService.getTopPlayers({ page: 0, size: limit });
      topPlayers.value = page.content;
    } catch (err) {
      error.value = `Failed to load top players: ${err.message}`;
    }
  };

  const loadTopPlayersByWins = async (limit = 10) => {
    try {
      const page = await competitionService.getTopPlayersByWins({ page: 0, size: limit });
      topPlayersByWins.value = page.content;
    } catch (err) {
      error.value = `Failed to load top players by wins: ${err.message}`;
    }
  };

  const exportLeaderboard = async (sessionId, format = 'json') => {
    try {
      return await competitionService.exportLeaderboard(sessionId, format);
    } catch (err) {
      error.value = `Failed to export leaderboard: ${err.message}`;
      throw err;
    }
  };

  // ── WebSocket Handlers ──

  const onLeaderboardUpdate = (leaderboardData) => {
    sessionLeaderboard.value = leaderboardData;
  };

  const onRangeUpdate = (rangeData) => {
    if (selectedRange.value === rangeData.rangeId) {
      groupsAtRange.value = rangeData.active || [];
      rangeQueue.value = rangeData.queue || [];
    }
  };

  // ── Utility ──

  const clearSession = () => {
    currentSession.value = null;
    sessionLeaderboard.value = null;
    selectedRange.value = null;
    groupsAtRange.value = [];
    rangeQueue.value = [];
  };

  const clearError = () => {
    error.value = null;
  };

  return {
    // State
    templates,
    selectedTemplate,
    currentSession,
    sessionLeaderboard,
    selectedRange,
    groupsAtRange,
    rangeQueue,
    topPlayers,
    topPlayersByWins,
    isLoading,
    error,

    // Computed
    templatesByType,
    isSessionActive,
    isSessionCompleted,

    // Methods
    loadTemplates,
    createTemplate,
    updateTemplate,
    deleteTemplate,
    selectTemplate,
    initSession,
    updateSessionStatus,
    selectRange,
    registerGroupAtRange,
    unregisterGroupFromRange,
    loadGroupsAtRange,
    loadSessionLeaderboard,
    loadTopPlayers,
    loadTopPlayersByWins,
    exportLeaderboard,
    onLeaderboardUpdate,
    onRangeUpdate,
    clearSession,
    clearError,
  };
});
