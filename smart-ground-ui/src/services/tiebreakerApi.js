import { apiFetch } from './apiClient.js'

// Stechen (competition tiebreaker) endpoints. Available while a session is PRE_COMPLETE.

export const getTies = (sessionId) =>
  apiFetch(`/sessions/${sessionId}/ties`)

export const listTiebreakers = (sessionId) =>
  apiFetch(`/sessions/${sessionId}/tiebreakers`)

export const startTiebreaker = (sessionId, { playerIds, templateType, templateId, tiePosition }) =>
  apiFetch(`/sessions/${sessionId}/tiebreakers`, {
    method: 'POST',
    body: JSON.stringify({ playerIds, templateType, templateId, tiePosition }),
  })

export const submitTiebreakerResults = (sessionId, tiebreakerId, results) =>
  apiFetch(`/sessions/${sessionId}/tiebreakers/${tiebreakerId}/results`, {
    method: 'POST',
    body: JSON.stringify({ results }),
  })
