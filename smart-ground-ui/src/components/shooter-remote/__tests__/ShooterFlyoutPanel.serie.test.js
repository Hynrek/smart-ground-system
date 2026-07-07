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

// SoloSerieStartModal (Task 6) emits 'confirm' with { record, shooter } — stub it to
// fire immediately on mount so the test can drive playSerieSolo's confirm branch.
const SoloSerieStartModalStub = {
  emits: ['confirm', 'cancel'],
  template: '<div class="solo-serie-start-modal-stub" />',
  mounted() {
    this.$emit('confirm', { record: true, shooter: { userId: 'u1', displayName: 'Max' } });
  },
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
      stubs: { Icons: true, SoloSerieStartModal: SoloSerieStartModalStub },
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
    await flushPromises();

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
        stubs: { Icons: true, SoloSerieStartModal: true },
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
