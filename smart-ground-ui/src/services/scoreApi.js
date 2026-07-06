import { apiFetch } from './apiClient.js'

// Builds "?a=1&b=2" from an object, skipping null/undefined/empty values
function toQuery(params = {}) {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, value)
  }
  const s = search.toString()
  return s ? `?${s}` : ''
}

export async function fetchMyScores(params) {
  return apiFetch(`/users/me/scores${toQuery(params)}`)
}

export async function fetchMyScoreSummary() {
  return apiFetch('/users/me/scores/summary')
}

export async function fetchLeaderboard(params) {
  return apiFetch(`/scores/leaderboard${toQuery(params)}`)
}
