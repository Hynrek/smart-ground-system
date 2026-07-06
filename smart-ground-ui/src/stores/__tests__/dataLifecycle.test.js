import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia, defineStore } from 'pinia';
import { ref } from 'vue';
import { resettablePlugin } from '../plugins/resettable.js';
import { resetAppData, initializeAppData, isAppDataInitialized } from '../dataLifecycle.js';

// Mock every module initializeAppData dynamically imports, so it performs no I/O.
const loadGuests = vi.fn().mockResolvedValue();
const initializeStore = vi.fn().mockResolvedValue();
const loadSerienFromStorage = vi.fn().mockResolvedValue();
const loadPassenFromStorage = vi.fn().mockResolvedValue();
const loadFromStorage = vi.fn().mockResolvedValue();
const loadEvents = vi.fn().mockResolvedValue();

vi.mock('@/stores/appStore.js', () => ({ useAppStore: () => ({ initializeStore }) }));
vi.mock('@/stores/guestStore.js', () => ({ useGuestStore: () => ({ loadGuests }) }));
vi.mock('@/stores/passeStore.js', () => ({
  usePasseStore: () => ({ loadSerienFromStorage, loadPassenFromStorage }),
}));
vi.mock('@/stores/activePasseStore.js', () => ({ useActivePasseStore: () => ({ loadFromStorage }) }));
vi.mock('@/stores/competitionEventStore.js', () => ({ useCompetitionEventStore: () => ({ loadEvents }) }));

const useAuthLike = defineStore('auth', () => {
  const token = ref('user-a-token');
  return { token };
});
const useDataLike = defineStore('data', () => {
  const items = ref([]);
  return { items };
});
const stopAllPollingSpy = vi.fn();
const usePollingLike = defineStore('polling', () => {
  const items = ref([]);
  const stopAllPolling = stopAllPollingSpy;
  return { items, stopAllPolling };
});

describe('dataLifecycle', () => {
  beforeEach(() => {
    const pinia = createPinia();
    pinia._a = true; // Make pinia.use() register plugins immediately (no Vue app installed in tests)
    pinia.use(resettablePlugin);
    setActivePinia(pinia);
    vi.clearAllMocks();
  });

  it('resets non-auth stores and leaves auth untouched', () => {
    const auth = useAuthLike();
    const data = useDataLike();
    auth.token = 'still-user-a';
    data.items.push('leaked');

    resetAppData();

    expect(data.items).toEqual([]); // wiped
    expect(auth.token).toBe('still-user-a'); // auth is the login/logout owner
  });

  it('stops polling before resetting a polling store', () => {
    usePollingLike();
    resetAppData();
    expect(stopAllPollingSpy).toHaveBeenCalledOnce();
  });

  it('initializes once and re-arms after reset', async () => {
    expect(isAppDataInitialized()).toBe(false);
    await initializeAppData();
    expect(isAppDataInitialized()).toBe(true);
    expect(initializeStore).toHaveBeenCalledOnce();

    await initializeAppData(); // idempotent
    expect(initializeStore).toHaveBeenCalledOnce();

    resetAppData();
    expect(isAppDataInitialized()).toBe(false);
    await initializeAppData(); // re-arms
    expect(initializeStore).toHaveBeenCalledTimes(2);
  });
});
