import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as userApi from '../services/userApi.js'

export const useUserStore = defineStore('user', () => {
  const users = ref([])
  const currentUser = ref(null)
  const isLoading = ref(false)
  const error = ref(null)

  async function loadUsers() {
    isLoading.value = true
    error.value = null
    try {
      const data = await userApi.fetchUsers()
      users.value = data.content ?? data
    } catch (err) {
      error.value = err.message || 'Failed to load users'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function createUser(userData) {
    isLoading.value = true
    error.value = null
    try {
      await userApi.createUser(userData)
      await loadUsers()
    } catch (err) {
      error.value = err.message || 'Failed to create user'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function updateUserRole(userId, role) {
    isLoading.value = true
    error.value = null
    try {
      await userApi.updateUserRole(userId, role)
      await loadUsers()
    } catch (err) {
      error.value = err.message || 'Failed to update user role'
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

  return {
    users,
    currentUser,
    isLoading,
    error,
    loadUsers,
    createUser,
    updateUserRole,
    deleteUser,
    getCurrentUser,
    changePassword
  }
})
