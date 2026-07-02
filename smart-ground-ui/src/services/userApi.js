import { apiFetch } from './apiClient.js'

export async function fetchUsers() {
  return apiFetch('/users')
}

export async function createUser(userData) {
  return apiFetch('/users', {
    method: 'POST',
    body: JSON.stringify(userData)
  })
}

export async function updateUserRole(userId, role) {
  return apiFetch(`/users/${userId}/role`, {
    method: 'PATCH',
    body: JSON.stringify({ role })
  })
}

export async function deleteUser(userId) {
  return apiFetch(`/users/${userId}`, {
    method: 'DELETE'
  })
}

export async function updateUser(userId, data) {
  return apiFetch(`/users/${userId}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

export async function assignRole(userId, roleName) {
  return apiFetch(`/users/${userId}/roles`, {
    method: 'POST',
    body: JSON.stringify({ roleName }),
  })
}

export async function fetchUserRoles(userId) {
  return apiFetch(`/users/${userId}/roles`)
}

export async function revokeRole(userId, roleName) {
  return apiFetch(`/users/${userId}/roles/${roleName}`, {
    method: 'DELETE',
  })
}

export async function fetchAvailableRoles() {
  return apiFetch('/roles')
}

export async function fetchMyQrToken() {
  return apiFetch('/users/me/qr')
}

export async function rotateMyQrToken() {
  return apiFetch('/users/me/qr/rotate', { method: 'POST' })
}

export async function resolveUserByQr(token) {
  return apiFetch(`/users/by-qr/${encodeURIComponent(token)}`)
}

export async function fetchMyPlayResults() {
  return apiFetch('/users/me/play-results')
}
