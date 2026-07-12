// src/views/__tests__/PasseManagementView.revalidate.test.js
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createRouter, createMemoryHistory } from 'vue-router';
import PasseManagementView from '@/views/shooter/PasseManagementView.vue';

// Capture the loader + options the component actually passes to useRevalidate,
// so we can invoke the real wiring and assert on the real mocked store methods.
const captured = { loader: null, options: null };
vi.mock('@/composables/useRevalidate.js', () => ({
  useRevalidate: (loader, options) => {
    captured.loader = loader;
    captured.options = options;
    return { start() {}, stop() {} };
  },
}));

const loadSerienFromStorage = vi.fn().mockResolvedValue();
const loadPassenFromStorage = vi.fn().mockResolvedValue();

vi.mock('@/stores/passeStore.js', () => ({
  usePasseStore: () => ({
    savedPassen: [],
    savedSerien: [],
    loadSerienFromStorage,
    loadPassenFromStorage,
    deletePasse: vi.fn(),
    deleteSerie: vi.fn(),
    renameSerie: vi.fn(),
    renamePasse: vi.fn(),
    createPasse: vi.fn(),
  }),
}));

vi.mock('@/stores/authStore.js', () => ({
  useAuthStore: () => ({
    hasPermission: vi.fn().mockReturnValue(false),
  }),
}));

vi.mock('@/stores/playSessionStore.js', () => ({
  usePlaySessionStore: () => ({
    activeSessions: [],
    resumeSession: vi.fn(),
    saveSessions: vi.fn(),
  }),
}));

vi.mock('@/stores/activePasseStore.js', () => ({
  useActivePasseStore: () => ({
    activeInstances: [],
    completedInstances: [],
    startPasse: vi.fn(),
    stopInstance: vi.fn(),
  }),
}));

vi.mock('@/components/Icons.vue', () => ({
  default: { name: 'Icons', props: ['icon', 'size', 'color'], template: '<span />' },
}));

const makeRouter = () =>
  createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }],
  });

describe('PasseManagementView revalidation wiring', () => {
  beforeEach(() => {
    captured.loader = null;
    captured.options = null;
    vi.clearAllMocks();
  });

  it('mounts and registers a loader that refreshes Serien and Passen via the real passeStore', async () => {
    const router = makeRouter();
    await router.push('/passen');

    mount(PasseManagementView, {
      global: { plugins: [router] },
    });

    expect(captured.loader).toBeTypeOf('function');

    await captured.loader();

    expect(loadSerienFromStorage).toHaveBeenCalledOnce();
    expect(loadPassenFromStorage).toHaveBeenCalledOnce();
  });

  it('registers the loader with a 10s polling interval', async () => {
    const router = makeRouter();
    await router.push('/passen');

    mount(PasseManagementView, {
      global: { plugins: [router] },
    });

    expect(captured.options).toEqual({ interval: 10000 });
  });
});
