import { apiFetch } from './apiClient.js'

export async function fetchPassen() {
  return apiFetch('/passen')
}

export async function getPasse(id) {
  return apiFetch(`/passen/${id}`)
}

export async function createPasse(name, serieIds) {
  return apiFetch('/passen', {
    method: 'POST',
    body: JSON.stringify({ name, serieIds }),
  })
}

export async function updatePasse(id, name) {
  return apiFetch(`/passen/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  })
}

export async function deletePasse(id) {
  return apiFetch(`/passen/${id}`, { method: 'DELETE' })
}
