import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import PassenAdminView from '../PassenAdminView.vue'

const mockPasseStore = {
  savedSerien: [
    {
      id: '_sg_range_serie_1',
      name: 'Olympisch',
      rangeId: 'r1',
      rangeName: 'Platz 1',
      steps: [{ id: 1, type: 'solo', alias: 'W1', positionId: 'pos-a', letter: 'A' }],
      ownership: 'range',
    },
    {
      id: '_sg_range_serie_2',
      name: 'Skeet',
      rangeId: 'r2',
      rangeName: 'Platz 2',
      steps: [{ id: 2, type: 'pair', alias1: 'W1', alias2: 'W2', positionId1: 'p1', positionId2: 'p2', letter1: 'A', letter2: 'B' }],
      ownership: 'range',
    },
  ],
  savedPassen: [],
  createRangeSerie: vi.fn(),
  updateSerie: vi.fn(),
  deleteSerie: vi.fn(),
}

const mockRangeStore = {
  ranges: [{ id: 'r1', name: 'Platz 1' }, { id: 'r2', name: 'Platz 2' }],
  positions: {},
  loadPositions: vi.fn().mockResolvedValue(undefined),
  initialize: vi.fn().mockResolvedValue(undefined),
}

vi.mock('@/stores/passeStore.js', () => ({ usePasseStore: () => mockPasseStore }))
vi.mock('@/stores/rangeStore.js', () => ({ useRangeStore: () => mockRangeStore }))
vi.mock('@/components/SerieDrawer.vue', () => ({
  default: { template: '<div data-testid="serie-drawer" />', props: ['open', 'mode', 'serie'], emits: ['saved', 'deleted', 'close'] },
}))

describe('PassenAdminView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders Serien and Passen tab buttons', () => {
    const wrapper = mount(PassenAdminView)
    const tabs = wrapper.findAll('.tab-btn')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].text()).toContain('Serien')
    expect(tabs[1].text()).toContain('Passen')
  })

  it('shows Platz-Serien header with count badge on Serien tab', () => {
    const wrapper = mount(PassenAdminView)
    expect(wrapper.text()).toContain('Platz-Serien')
    expect(wrapper.text()).toContain('2') // total count
  })

  it('groups Serien by range', () => {
    const wrapper = mount(PassenAdminView)
    expect(wrapper.text()).toContain('Platz 1')
    expect(wrapper.text()).toContain('Platz 2')
  })

  it('shows Serien names within their groups', () => {
    const wrapper = mount(PassenAdminView)
    expect(wrapper.text()).toContain('Olympisch')
    expect(wrapper.text()).toContain('Skeet')
  })

  it('opens drawer in create mode when Neue Serie button is clicked', async () => {
    const wrapper = mount(PassenAdminView)
    await wrapper.find('[data-testid="new-serie-btn"]').trigger('click')
    expect(wrapper.find('[data-testid="serie-drawer"]').exists()).toBe(true)
    expect(wrapper.vm.drawerOpen).toBe(true)
    expect(wrapper.vm.drawerMode).toBe('create')
  })

  it('shows Passen stub when Passen tab is clicked', async () => {
    const wrapper = mount(PassenAdminView)
    const passenTab = wrapper.findAll('.tab-btn')[1]
    await passenTab.trigger('click')
    expect(wrapper.text()).toContain('Passen-Verwaltung')
  })

  it('shows empty state when no Platz-Serien exist', () => {
    const original = mockPasseStore.savedSerien
    mockPasseStore.savedSerien = []
    try {
      const wrapper = mount(PassenAdminView)
      expect(wrapper.text()).toContain('Noch keine Platz-Serien')
    } finally {
      mockPasseStore.savedSerien = original
    }
  })

  it('opens drawer in edit mode when a serie row is clicked', async () => {
    const wrapper = mount(PassenAdminView)
    await wrapper.vm.$nextTick()
    await wrapper.find('.serie-row').trigger('click')
    expect(wrapper.vm.drawerOpen).toBe(true)
    expect(wrapper.vm.drawerMode).toBe('edit')
    expect(wrapper.vm.drawerSerie).toEqual(mockPasseStore.savedSerien[0])
  })
})
