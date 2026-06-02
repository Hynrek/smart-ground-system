import { apiFetch } from './apiClient.js'

export async function fetchTrainings() {
  return apiFetch('/trainings')
}

export async function getTraining(id) {
  return apiFetch(`/trainings/${id}`)
}

export async function createTraining(name, programmeIds) {
  return apiFetch('/trainings', {
    method: 'POST',
    body: JSON.stringify({ name, programmeIds }),
  })
}

export async function updateTraining(id, name) {
  return apiFetch(`/trainings/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  })
}

export async function deleteTraining(id) {
  return apiFetch(`/trainings/${id}`, { method: 'DELETE' })
}
