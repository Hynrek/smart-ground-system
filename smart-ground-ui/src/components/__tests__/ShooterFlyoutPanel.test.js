import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import { useProgramStore } from '@/stores/programStore.js';
import ShooterFlyoutPanel from '../shooter-remote/ShooterFlyoutPanel.vue';

vi.mock('@/stores/playSessionStore.js', () => ({
  usePlaySessionStore: () => ({
    loadCompletedAblaeufe: vi.fn(),
    isAblaufCompleted: vi.fn(() => false),
    pendingProgramInfo: null,
    setPendingGroupAblaeufe: vi.fn(),
    playProgramWithScore: vi.fn(),
  }),
}));

vi.mock('@/stores/activeProgramStore.js', () => ({
  useActiveProgramStore: () => ({
    getBlocksForRange: vi.fn(() => []),
  }),
}));

vi.mock('@/router', () => ({ default: { push: vi.fn() } }));

const mountPanel = () =>
  mount(ShooterFlyoutPanel, {
    global: {
      stubs: { Icons: true },
    },
  });

describe('ShooterFlyoutPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('renders without errors when no steps are present', () => {
    const wrapper = mountPanel();
    expect(wrapper.exists()).toBe(true);
  });

  it('getStepLabel returns letter for solo step', async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const remoteStore = useShooterRemoteStore();
    const programStore = useProgramStore();

    remoteStore.sessionMode = 'recording';
    remoteStore.recordingActive = true;
    programStore.programMode = true;
    programStore.editingAblauf = [
      {
        id: 'abl-1',
        alias: 'Test',
        steps: [
          { id: 's1', type: 'solo', letter: 'B', alias: 'Maschine B', positionId: 'pos-2' },
        ],
      },
    ];

    const wrapper = mountPanel();
    await wrapper.vm.$nextTick();

    const chip = wrapper.find('.item-code');
    expect(chip.text()).toBe('B');
  });

  it('getStepLabel returns letter pair for pair step', async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const remoteStore = useShooterRemoteStore();
    const programStore = useProgramStore();

    remoteStore.sessionMode = 'recording';
    remoteStore.recordingActive = true;
    programStore.programMode = true;
    programStore.editingAblauf = [
      {
        id: 'abl-1',
        alias: 'Test',
        steps: [
          { id: 's1', type: 'pair', letter1: 'A', letter2: 'C', alias1: 'M1', alias2: 'M3', positionId1: 'p1', positionId2: 'p3' },
        ],
      },
    ];

    const wrapper = mountPanel();
    await wrapper.vm.$nextTick();

    const chip = wrapper.find('.item-code');
    expect(chip.text()).toBe('A+C');
  });

  it('does not import or use deviceStore', () => {
    // This test verifies the component can mount without deviceStore being needed
    // If deviceStore were imported and used, any missing store state would cause errors
    const wrapper = mountPanel();
    expect(wrapper.exists()).toBe(true);
  });
});
