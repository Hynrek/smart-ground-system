import { getActivePinia } from 'pinia';

let _initialized = false;

export function isAppDataInitialized() {
  return _initialized;
}

// One-time initialization of API-backed stores, run on first authenticated
// navigation. Idempotent. Dynamic imports here avoid a static import cycle
// with authStore.js (which imports resetAppData below) — they do not
// produce separate lazy chunks, since these stores are also imported
// statically elsewhere in the app.
export async function initializeAppData() {
  if (_initialized) return;
  _initialized = true;

  const { useAppStore } = await import('@/stores/appStore.js');
  const { useGuestStore } = await import('@/stores/guestStore.js');
  const { usePasseStore } = await import('@/stores/passeStore.js');
  const { useActivePasseStore } = await import('@/stores/activePasseStore.js');
  const { useCompetitionEventStore } = await import('@/stores/competitionEventStore.js');

  const appStore = useAppStore();
  const guestStore = useGuestStore();
  const passeStore = usePasseStore();
  const activePasseStore = useActivePasseStore();
  const competitionEventStore = useCompetitionEventStore();

  appStore.initializeStore().catch(console.error);
  guestStore.loadGuests().catch(console.error);
  passeStore.loadSerienFromStorage().catch(console.error);
  passeStore.loadPassenFromStorage().catch(console.error);
  activePasseStore.loadFromStorage().catch(console.error);
  competitionEventStore.loadEvents().catch(console.error);
}

// Wipe every instantiated store except auth, stopping any polling first, then
// re-arm initialization so the next login refetches. auth owns its own lifecycle.
export function resetAppData() {
  const pinia = getActivePinia();
  if (pinia) {
    pinia._s.forEach((store) => {
      if (store.$id === 'auth') return;
      store.stopAllPolling?.();
      store.$reset?.();
    });
  }
  _initialized = false;
}
