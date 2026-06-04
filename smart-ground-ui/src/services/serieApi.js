/* global URLSearchParams */
import { apiFetch } from './apiClient.js'

export async function fetchSerien(params) {
  const query = params ? '?' + new URLSearchParams(params).toString() : ''
  return apiFetch(`/serien${query}`)
}

export async function getSerie(id) {
  return apiFetch(`/serien/${id}`)
}

export async function createSerie(name, steps, rangeId = null, ownership = 'user') {
  return apiFetch('/serien', {
    method: 'POST',
    body: JSON.stringify({ name, steps, rangeId, ownership }),
  })
}

export async function updateSerie(id, name, rangeId = null) {
  return apiFetch(`/serien/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name, rangeId }),
  })
}

export async function updateSerieOwnership(id, ownership) {
  return apiFetch(`/serien/${id}/ownership`, {
    method: 'PATCH',
    body: JSON.stringify({ ownership }),
  })
}

export async function patchSeriePublished(id, published) {
  return apiFetch(`/serien/${id}/published`, {
    method: 'PATCH',
    body: JSON.stringify({ published }),
  })
}

export async function deleteSerie(id) {
  return apiFetch(`/serien/${id}`, { method: 'DELETE' })
}
