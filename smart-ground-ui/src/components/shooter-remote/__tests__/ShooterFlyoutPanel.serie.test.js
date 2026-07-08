import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import { usePasseStore } from '@/stores/passeStore.js';
import { useActivePasseStore } from '@/stores/activePasseStore.js';
import ShooterFlyoutPanel from '../ShooterFlyoutPanel.vue';
import GroupSetupModal from '@/components/GroupSetupModal.vue';

vi.mock('@/components/Icons.vue', () => ({ default: { template: '<span />' } }));

const startGroupPlayMock = vi.fn().mockResolvedValue(undefined);
const setPendingGroupSerienMock = vi.fn();

vi.mock('@/stores/playSessionStore.js', () => ({
  usePlaySessionStore: () => ({
    loadCompletedSerien: vi.fn(),
    isSerieCompleted: vi.fn(() => false),
    pendingPasseInfo: null,
    setPendingGroupSerien: setPendingGroupSerienMock,
    startGroupPlay: startGroupPlayMock,
    playPasseWithScore: vi.fn(),
  }),
}));

// SoloSerieStartModal (Task 6) emits 'confirm' with { record, shooter } — tests
// drive that emit explicitly via confirmSoloModal below. It must NOT fire from
// mounted(): the stubbed Teleport remounts its subtree on every re-render, so a
// confirm-on-mount stub loops forever when the failure path keeps the modal open
// and updates the `error` prop (confirm → reject → error → remount → confirm…).
// Renders the `error` prop so failure-path tests can assert it's surfaced.
const SoloSerieStartModalStub = {
  props: ['error'],
  emits: ['confirm', 'cancel'],
  template: '<div class="solo-serie-start-modal-stub">{{ error }}</div>',
};

const confirmSoloModal = async (wrapper) => {
  wrapper
    .findComponent(SoloSerieStartModalStub)
    .vm.$emit('confirm', { record: true, shooter: { userId: 'u1', displayName: 'Max' } });
  await flushPromises();
};

const makeRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/remote/:rangeId/play', component: { template: '<div />' } },
  ],
});

const mountPanel = async () => {
  const router = makeRouter();
  await router.push('/');
  await router.isReady();
  const wrapper = mount(ShooterFlyoutPanel, {
    global: {
      plugins: [router],
      // The panel teleports its modals to <body> — .flyout-wrapper sets
      // pointer-events: none, which a nested modal would inherit. Stub the
      // Teleport so the modals render in place and stay queryable via wrapper.
      stubs: { Icons: true, SoloSerieStartModal: SoloSerieStartModalStub, teleport: true },
    },
  });
  return { wrapper, router };
};

const seedSerie = (passeStore, remoteStore) => {
  remoteStore.selectedRangeId = 'range-1';
  passeStore.savedSerien = [
    {
      id: 'serie-1',
      name: 'Serie A',
      rangeId: 'range-1',
      rangeName: 'Platz 1',
      ownership: 'user',
      steps: [],
    },
  ];
};

describe('ShooterFlyoutPanel — persisted solo Serie start', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    startGroupPlayMock.mockClear();
    setPendingGroupSerienMock.mockClear();
  });

  it('confirming the solo modal with record:true starts a real Serie instance for the resolved shooter', async () => {
    const { wrapper, router } = await mountPanel();
    const remoteStore = useShooterRemoteStore();
    const passeStore = usePasseStore();
    const activePasseStore = useActivePasseStore();
    seedSerie(passeStore, remoteStore);

    const startSerieSpy = vi.spyOn(activePasseStore, 'startSerie').mockResolvedValue({
      instanceId: 'inst-1',
      blocks: [{ blockId: 'blk-1' }],
    });

    await wrapper.find('.flyout-handle').trigger('click');
    await wrapper.find('.serie-header-btn').trigger('click');
    await wrapper.find('.action-play').trigger('click');
    await confirmSoloModal(wrapper);

    expect(startSerieSpy).toHaveBeenCalledWith(
      { ...passeStore.savedSerien[0], type: 'user' }, // userSerien tags entries with UI-only `type: 'user'`
      [{ id: 'u1', type: 'user', userId: 'u1', displayName: 'Max' }],
    );

    // The persisted instance's single block launches through the same
    // block-play path range Passe blocks use (setPendingGroupSerien + startGroupPlay),
    // routed to the range's play page.
    expect(setPendingGroupSerienMock).toHaveBeenCalled();
    expect(startGroupPlayMock).toHaveBeenCalledWith(
      [{ id: 'u1', type: 'user', userId: 'u1', displayName: 'Max' }],
      'range-1',
      null,
      'inst-1',
      'blk-1',
      null,
      'training',
    );
    expect(router.currentRoute.value.path).toBe('/remote/range-1/play');
  });

  it('keeps the solo modal open and shows an error when startSerie rejects, instead of silently closing', async () => {
    const { wrapper, router } = await mountPanel();
    const remoteStore = useShooterRemoteStore();
    const passeStore = usePasseStore();
    const activePasseStore = useActivePasseStore();
    seedSerie(passeStore, remoteStore);

    vi.spyOn(activePasseStore, 'startSerie').mockRejectedValue(new Error('network down'));

    await wrapper.find('.flyout-handle').trigger('click');
    await wrapper.find('.serie-header-btn').trigger('click');
    await wrapper.find('.action-play').trigger('click');
    await confirmSoloModal(wrapper);

    // Modal must still be present — must not silently disappear as if the run
    // had been recorded — and it must display the failure to the shooter.
    const modal = wrapper.find('.solo-serie-start-modal-stub');
    expect(modal.exists()).toBe(true);
    expect(modal.text()).toContain('konnte nicht gestartet werden');

    // No navigation and no downstream play-session calls on failure.
    expect(startGroupPlayMock).not.toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe('/');
  });
});

describe('ShooterFlyoutPanel — persisted group Serie start', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    startGroupPlayMock.mockClear();
    setPendingGroupSerienMock.mockClear();
  });

  it('confirming the inline group setup starts a real Serie instance for the assembled players', async () => {
    const router = makeRouter();
    await router.push('/');
    await router.isReady();
    const wrapper = mount(ShooterFlyoutPanel, {
      global: {
        plugins: [router],
        stubs: { Icons: true, SoloSerieStartModal: true, teleport: true },
      },
    });
    const remoteStore = useShooterRemoteStore();
    const passeStore = usePasseStore();
    const activePasseStore = useActivePasseStore();
    seedSerie(passeStore, remoteStore);

    const startSerieSpy = vi.spyOn(activePasseStore, 'startSerie').mockResolvedValue({
      instanceId: 'inst-2',
      blocks: [{ blockId: 'blk-2' }],
    });

    await wrapper.find('.flyout-handle').trigger('click');
    await wrapper.find('.serie-header-btn').trigger('click');
    await wrapper.find('.action-group').trigger('click');
    await wrapper.vm.$nextTick();

    // The inline GroupSetupModal seeds a single default guest player — confirm as-is.
    await wrapper.findComponent(GroupSetupModal).vm.$emit('confirm');
    await flushPromises();

    expect(startSerieSpy).toHaveBeenCalledWith(
      { ...passeStore.savedSerien[0], type: 'user' },
      [{ id: 'gs-1', type: 'guest', userId: null, displayName: 'Schütze 1' }],
    );
    expect(startGroupPlayMock).toHaveBeenCalledWith(
      [{ id: 'gs-1', type: 'guest', userId: null, displayName: 'Schütze 1' }],
      'range-1',
      null,
      'inst-2',
      'blk-2',
      null,
      'training',
    );
    expect(router.currentRoute.value.path).toBe('/remote/range-1/play');
  });
});
