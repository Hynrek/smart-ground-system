import { apiFetch } from './apiClient.js'

export async function fetchAblaeufe(params) {
  const query = params ? '?' + new URLSearchParams(params).toString() : ''
  return apiFetch(`/ablaeufe${query}`, undefined)
}

export async function getAblauf(id) {
  return apiFetch(`/ablaeufe/${id}`)
}

export async function createAblauf(name, steps, rangeId = null, ownership = 'user') {
  return apiFetch('/ablaeufe', {
    method: 'POST',
    body: JSON.stringify({ name, steps, rangeId, ownership }),
  })
}

export async function updateAblauf(id, name, rangeId = null) {
  return apiFetch(`/ablaeufe/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name, rangeId }),
  })
}

export async function updateAblaufOwnership(id, ownership) {
  return apiFetch(`/ablaeufe/${id}/ownership`, {
    method: 'PATCH',
    body: JSON.stringify({ ownership }),
  })
}

export async function deleteAblauf(id) {
  return apiFetch(`/ablaeufe/${id}`, { method: 'DELETE' })
}
