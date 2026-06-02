import { apiFetch } from './apiClient.js'

export async function fetchGuests() {
  return apiFetch('/guests')
}

export async function createGuest(displayName) {
  return apiFetch('/guests', {
    method: 'POST',
    body: JSON.stringify({ displayName }),
  })
}

export async function updateGuest(id, displayName) {
  return apiFetch(`/guests/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ displayName }),
  })
}

export async function deleteGuest(id) {
  return apiFetch(`/guests/${id}`, { method: 'DELETE' })
}
