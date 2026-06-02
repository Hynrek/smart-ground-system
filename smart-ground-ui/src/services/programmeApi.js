import { apiFetch } from './apiClient.js'

export async function fetchProgrammes() {
  return apiFetch('/programmes')
}

export async function getProgramme(id) {
  return apiFetch(`/programmes/${id}`)
}

export async function createProgramme(name, ablaufIds) {
  return apiFetch('/programmes', {
    method: 'POST',
    body: JSON.stringify({ name, ablaufIds }),
  })
}

export async function updateProgramme(id, name) {
  return apiFetch(`/programmes/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  })
}

export async function deleteProgramme(id) {
  return apiFetch(`/programmes/${id}`, { method: 'DELETE' })
}
