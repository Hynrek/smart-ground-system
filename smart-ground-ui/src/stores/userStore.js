import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as userApi from '../services/userApi.js'

export const useUserStore = defineStore('user', () => {
  const users = ref([])
  const selectedUser = ref(null)
  const userRolesMap = ref({})
  const currentUser = ref(null)
  const isLoading = ref(false)
  const error = ref(null)
  const availableRoles = ref([])

  async function loadUsers() {
    isLoading.value = true
    error.value = null
    try {
      const data = await userApi.fetchUsers()
      const list = data.content ?? data
      users.value = list

      const entries = await Promise.all(
        list.map(async (u) => {
          const roles = await userApi.fetchUserRoles(u.id).catch(() => [])
          return [u.id, roles]
        })
      )
      userRolesMap.value = Object.fromEntries(entries)
    } catch (err) {
      error.value = err.message || 'Failed to load users'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  function selectUser(user) {
    selectedUser.value = user
  }

  async function createUser(userData, role = 'SHOOTER') {
    isLoading.value = true
    error.value = null
    try {
      const created = await userApi.createUser(userData)
      await userApi.assignRole(created.id, role)
      await loadUsers()
    } catch (err) {
      error.value = err.message || 'Failed to create user'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function updateUser(userId, data) {
    isLoading.value = true
    error.value = null
    try {
      await userApi.updateUser(userId, data)
      await loadUsers()
    } catch (err) {
      error.value = err.message || 'Failed to update user'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function deleteUser(userId) {
    isLoading.value = true
    error.value = null
    try {
      await userApi.deleteUser(userId)
      if (selectedUser.value?.id === userId) {
        selectedUser.value = null
      }
      await loadUsers()
    } catch (err) {
      error.value = err.message || 'Failed to delete user'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function getCurrentUser() {
    isLoading.value = true
    error.value = null
    try {
      const data = await userApi.getCurrentUser()
      currentUser.value = data
      return data
    } catch (err) {
      error.value = err.message || 'Failed to load user profile'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function toggleRole(userId, roleName) {
    isLoading.value = true
    error.value = null
    try {
      const current = userRolesMap.value[userId] ?? []
      const hasRole = current.some((r) => r.roleName === roleName)
      if (hasRole) {
        await userApi.revokeRole(userId, roleName)
      } else {
        await userApi.assignRole(userId, roleName)
      }
      userRolesMap.value[userId] = await userApi.fetchUserRoles(userId)
    } catch (err) {
      error.value = err.message || 'Failed to update role'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function changePassword(oldPassword, newPassword) {
    isLoading.value = true
    error.value = null
    try {
      await userApi.changePassword(oldPassword, newPassword)
    } catch (err) {
      error.value = err.message || 'Failed to change password'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function loadAvailableRoles() {
    isLoading.value = true
    error.value = null
    try {
      availableRoles.value = await userApi.fetchAvailableRoles()
    } catch (err) {
      error.value = err.message || 'Failed to load roles'
    } finally {
      isLoading.value = false
    }
  }

  return {
    users,
    selectedUser,
    userRolesMap,
    currentUser,
    isLoading,
    error,
    availableRoles,
    loadUsers,
    selectUser,
    createUser,
    updateUser,
    deleteUser,
    toggleRole,
    getCurrentUser,
    changePassword,
    loadAvailableRoles,
  }
})
