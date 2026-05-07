import { apiFetch } from './apiClient.js'

export async function fetchUsers() {
  return apiFetch('/users')
}

export async function createUser(username, password, role) {
  return apiFetch('/users', {
    method: 'POST',
    body: JSON.stringify({ username, password, role })
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

export async function getCurrentUser() {
  return apiFetch('/users/me')
}

export async function changePassword(oldPassword, newPassword) {
  return apiFetch('/users/me/password', {
    method: 'POST',
    body: JSON.stringify({ oldPassword, newPassword })
  })
}
