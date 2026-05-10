import { defineStore } from 'pinia';
import { ref } from 'vue';

const generateUUID = () => {
  if (typeof globalThis !== 'undefined' && globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};

export const useGuestStore = defineStore('guests', () => {
  const STORAGE_KEY = 'smart-ground:guests';
  const guests = ref([]);

  const loadGuests = () => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      guests.value = raw ? JSON.parse(raw) : [];
    } catch {
      guests.value = [];
    }
  };

  const persist = () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(guests.value));
  };

  const addGuest = (displayName) => {
    const guest = {
      id: generateUUID(),
      displayName: displayName.trim(),
      createdAt: new Date().toISOString(),
    };
    guests.value = [...guests.value, guest];
    persist();
    return guest;
  };

  const removeGuest = (id) => {
    guests.value = guests.value.filter((g) => g.id !== id);
    persist();
  };

  const updateGuest = (id, displayName) => {
    guests.value = guests.value.map((g) =>
      g.id === id ? { ...g, displayName: displayName.trim() } : g
    );
    persist();
  };

  loadGuests();

  return { guests, loadGuests, addGuest, removeGuest, updateGuest };
});
