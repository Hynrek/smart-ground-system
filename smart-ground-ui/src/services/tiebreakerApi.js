import { apiFetch } from './apiClient.js'

// Stechen (competition tiebreaker) endpoints. Available while a session is PRE_COMPLETE.

export const getTies = (sessionId) =>
  apiFetch(`/sessions/${sessionId}/ties`)

export const listTiebreakers = (sessionId) =>
  apiFetch(`/sessions/${sessionId}/tiebreakers`)

export const startTiebreaker = (sessionId, { playerIds, templateId, tiePosition }) =>
  apiFetch(`/sessions/${sessionId}/tiebreakers`, {
    method: 'POST',
    body: JSON.stringify({ playerIds, templateId, tiePosition }),
  })
