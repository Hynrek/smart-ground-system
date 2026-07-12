import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as userApi from '@/services/userApi.js'

// Own QR check-in token (profile page) and QR resolve for group setup
export const useProfileStore = defineStore('profile', () => {
  const qrToken = ref(null)
  const isLoading = ref(false)
  const error = ref(null)

  const loadQrToken = async () => {
    isLoading.value = true
    error.value = null
    try {
      qrToken.value = (await userApi.fetchMyQrToken()).qrToken
    } catch (e) {
      error.value = e.message
    } finally {
      isLoading.value = false
    }
  }

  const rotateQrToken = async () => {
    isLoading.value = true
    error.value = null
    try {
      qrToken.value = (await userApi.rotateMyQrToken()).qrToken
    } catch (e) {
      error.value = e.message
    } finally {
      isLoading.value = false
    }
  }

  // Resolve a scanned token. Errors (incl. e.status === 404) propagate to the
  // caller so the scan modal can show inline feedback and keep scanning.
  const resolveCheckinToken = async (token) => userApi.resolveUserByQr(token)

  return { qrToken, isLoading, error, loadQrToken, rotateQrToken, resolveCheckinToken }
})
