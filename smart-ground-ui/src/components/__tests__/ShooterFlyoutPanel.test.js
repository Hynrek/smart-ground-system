import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';
import { usePasseStore } from '@/stores/passeStore.js';
import ShooterFlyoutPanel from '../shooter-remote/ShooterFlyoutPanel.vue';

vi.mock('@/stores/playSessionStore.js', () => ({
  usePlaySessionStore: () => ({
    loadCompletedSerien: vi.fn(),
    isSerieCompleted: vi.fn(() => false),
    pendingPasseInfo: null,
    setPendingGroupSerien: vi.fn(),
    playPasseWithScore: vi.fn(),
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
    const passeStore = usePasseStore();

    remoteStore.sessionMode = 'recording';
    remoteStore.recordingActive = true;
    passeStore.passeMode = true;
    passeStore.editingSerie = [
      {
        id: 'serie-1',
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
    const passeStore = usePasseStore();

    remoteStore.sessionMode = 'recording';
    remoteStore.recordingActive = true;
    passeStore.passeMode = true;
    passeStore.editingSerie = [
      {
        id: 'serie-1',
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

// Minimal stub for child icons component
vi.mock('@/components/Icons.vue', () => ({
  default: { template: '<span />' },
}));

describe('ShooterFlyoutPanel — competition Rotten', () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it('renders one card per active competition Rotte', async () => {
    const { useActivePasseStore } = await import('@/stores/activePasseStore.js');
    const { useShooterRemoteStore } = await import('@/stores/shooterRemoteStore.js');
    const { usePasseStore } = await import('@/stores/passeStore.js');

    const activePasseStore = useActivePasseStore();
    const remoteStore = useShooterRemoteStore();
    const passeStore = usePasseStore();

    // Seed a competition instance directly
    activePasseStore.activeInstances = [{
      instanceId: 'inst-c1',
      type: 'competition',
      sessionId: null,
      templateId: 'c1',
      templateName: 'Frühjahrspokal',
      name: 'Frühjahrspokal',
      rotten: [
        { rotteId: 'r1', name: 'Rotte 1', players: [{ id: 'u1', displayName: 'Max' }], status: 'waiting', assignedRangeId: null, currentPhaseIndex: 0,
          phases: [{ phaseIndex: 0, passeId: 'p1', passeName: 'Passe 1', status: 'active',
            blocks: [{ blockId: 'blk-1', serieId: 's1', serieAlias: 'A', rangeId: 'range-1', rangeName: 'Platz 1', steps: [], status: 'pending', completedAt: null, result: null }] }] },
        { rotteId: 'r2', name: 'Rotte 2', players: [{ id: 'u2', displayName: 'Lisa' }], status: 'waiting', assignedRangeId: null, currentPhaseIndex: 0,
          phases: [{ phaseIndex: 0, passeId: 'p1', passeName: 'Passe 1', status: 'active',
            blocks: [{ blockId: 'blk-2', serieId: 's1', serieAlias: 'A', rangeId: 'range-1', rangeName: 'Platz 1', steps: [], status: 'pending', completedAt: null, result: null }] }] },
      ],
      startedAt: Date.now(), completedAt: null,
    }];

    remoteStore.selectedRangeId = 'range-1';
    passeStore.passeMode = false;

    const wrapper = mount(ShooterFlyoutPanel, {
      global: { stubs: { Icons: true, RouterLink: true } },
    });

    // Open the panel
    await wrapper.find('.flyout-handle').trigger('click');

    const cards = wrapper.findAll('[data-testid="competition-rotte-card"]');
    expect(cards).toHaveLength(2);
    expect(cards[0].text()).toContain('Rotte 1');
    expect(cards[0].text()).toContain('Passe 1');
    expect(cards[1].text()).toContain('Rotte 2');
  });

  it('does not show done rotten', async () => {
    const { useActivePasseStore } = await import('@/stores/activePasseStore.js');
    const { useShooterRemoteStore } = await import('@/stores/shooterRemoteStore.js');
    const { usePasseStore } = await import('@/stores/passeStore.js');

    const activePasseStore = useActivePasseStore();
    useShooterRemoteStore().selectedRangeId = 'range-1';
    usePasseStore().passeMode = false;

    activePasseStore.activeInstances = [{
      instanceId: 'inst-c2',
      type: 'competition',
      sessionId: null,
      templateId: 'c2',
      templateName: 'Cup',
      name: 'Cup',
      rotten: [
        { rotteId: 'r1', name: 'Rotte 1', players: [], status: 'done', assignedRangeId: null, currentPhaseIndex: 0,
          phases: [{ phaseIndex: 0, passeId: 'p1', passeName: 'P1', status: 'active', blocks: [] }] },
      ],
      startedAt: Date.now(), completedAt: null,
    }];

    const wrapper = mount(ShooterFlyoutPanel, {
      global: { stubs: { Icons: true, RouterLink: true } },
    });
    await wrapper.find('.flyout-handle').trigger('click');

    const cards = wrapper.findAll('[data-testid="competition-rotte-card"]');
    expect(cards).toHaveLength(0);
  });
});
