/* global URLSearchParams */
import { apiFetch } from './apiClient.js'

export const createSession = (name, passen, groups) =>
  apiFetch('/sessions', {
    method: 'POST',
    body: JSON.stringify({ type: 'COMPETITION', name, passen, groups }),
  })

export const listSessions = (type, status) => {
  const p = new URLSearchParams()
  if (type)   p.set('type', type)
  if (status) p.set('status', status)
  const q = p.toString() ? `?${p.toString()}` : ''
  return apiFetch(`/sessions${q}`)
}

export const getSession = (id) => apiFetch(`/sessions/${id}`)

export const patchStatus = (id, status) =>
  apiFetch(`/sessions/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })

export const deleteSession = (id) =>
  apiFetch(`/sessions/${id}`, { method: 'DELETE' })

export const createGroup = (sessionId, name, members = []) =>
  apiFetch(`/sessions/${sessionId}/groups`, {
    method: 'POST',
    body: JSON.stringify({ name, members }),
  })

export const updateGroup = (sessionId, groupId, name) =>
  apiFetch(`/sessions/${sessionId}/groups/${groupId}`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  })

export const deleteGroup = (sessionId, groupId) =>
  apiFetch(`/sessions/${sessionId}/groups/${groupId}`, { method: 'DELETE' })

export const addMember = (sessionId, groupId, member) =>
  apiFetch(`/sessions/${sessionId}/groups/${groupId}/members`, {
    method: 'POST',
    body: JSON.stringify(member),
  })

export const removeMember = (sessionId, groupId, memberId) =>
  apiFetch(`/sessions/${sessionId}/groups/${groupId}/members/${memberId}`, {
    method: 'DELETE',
  })

export const patchMember = (sessionId, groupId, memberId, paid) =>
  apiFetch(`/sessions/${sessionId}/groups/${groupId}/members/${memberId}`, {
    method: 'PATCH',
    body: JSON.stringify({ paid }),
  })

export const completeSerie = (sessionId, groupId, serieId, passeIndex, playInstanceId, results) =>
  apiFetch(`/sessions/${sessionId}/groups/${groupId}/serien/${serieId}/complete`, {
    method: 'POST',
    body: JSON.stringify({ passeIndex, playInstanceId, results }),
  })

export const getProgress = (sessionId) =>
  apiFetch(`/sessions/${sessionId}/progress`)

export const addPasse = (sessionId, passeId) =>
  apiFetch(`/sessions/${sessionId}/passen`, {
    method: 'POST',
    body: JSON.stringify({ passeId }),
  })

export const removePasse = (sessionId, passeId) =>
  apiFetch(`/sessions/${sessionId}/passen/${passeId}`, { method: 'DELETE' })
