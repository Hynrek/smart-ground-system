import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as guestApi from '@/services/guestApi.js'

export const useGuestStore = defineStore('guests', () => {
  const guests = ref([])
  const isLoading = ref(false)
  const error = ref(null)

  const loadGuests = async () => {
    isLoading.value = true
    error.value = null
    try {
      guests.value = await guestApi.fetchGuests()
    } catch (e) {
      error.value = e.message
    } finally {
      isLoading.value = false
    }
  }

  const addGuest = async (displayName) => {
    const guest = await guestApi.createGuest(displayName.trim())
    guests.value = [...guests.value, guest]
    return guest
  }

  const removeGuest = async (id) => {
    await guestApi.deleteGuest(id)
    guests.value = guests.value.filter((g) => g.id !== id)
  }

  const updateGuest = async (id, displayName) => {
    const updated = await guestApi.updateGuest(id, displayName.trim())
    guests.value = guests.value.map((g) => (g.id === id ? updated : g))
  }

  return { guests, isLoading, error, loadGuests, addGuest, removeGuest, updateGuest }
})
