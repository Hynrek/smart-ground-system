import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import PassenAdminView from '../PassenAdminView.vue'

const routes = [{ path: '/', component: { template: '<div />' } }]

function makeRouter() {
  return createRouter({ history: createMemoryHistory(), routes })
}

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
  savedGlobalPassen: [
    {
      id: '_sg_global_passe_1',
      name: 'Olympisch Vorlage',
      serien: [{ id: '_sg_range_serie_1', alias: 'Olympisch', rangeId: 'r1', rangeName: 'Platz 1', steps: [] }],
      ownership: 'global',
    },
    {
      id: '_sg_global_passe_2',
      name: 'Skeet Vorlage',
      serien: [{ id: '_sg_range_serie_2', alias: 'Skeet', rangeId: 'r2', rangeName: 'Platz 2', steps: [] }],
      ownership: 'global',
    },
  ],
  createRangeSerie: vi.fn(),
  updateSerie: vi.fn(),
  deleteSerie: vi.fn(),
  createGlobalPasse: vi.fn(),
  updateGlobalPasse: vi.fn(),
  deleteGlobalPasse: vi.fn(),
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
vi.mock('@/components/GlobalPasseDrawer.vue', () => ({
  default: { template: '<div data-testid="global-passe-drawer" />', props: ['open', 'mode', 'passe'], emits: ['saved', 'deleted', 'close'] },
}))

describe('PassenAdminView — Serien tab', () => {
  let router

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    router = makeRouter()
  })

  it('renders Serien and Passen tab buttons', () => {
    const wrapper = mount(PassenAdminView, { global: { plugins: [router] } })
    const tabs = wrapper.findAll('.tab-btn')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].text()).toContain('Serien')
    expect(tabs[1].text()).toContain('Passen')
  })

  it('shows Platz-Serien header with count badge on Serien tab', () => {
    const wrapper = mount(PassenAdminView, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('Platz-Serien')
    expect(wrapper.text()).toContain('2')
  })

  it('groups Serien by range', () => {
    const wrapper = mount(PassenAdminView, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('Platz 1')
    expect(wrapper.text()).toContain('Platz 2')
  })

  it('shows Serien names within their groups', () => {
    const wrapper = mount(PassenAdminView, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('Olympisch')
    expect(wrapper.text()).toContain('Skeet')
  })

  it('opens SerieDrawer in create mode when Neue Serie button is clicked', async () => {
    const wrapper = mount(PassenAdminView, { global: { plugins: [router] } })
    await wrapper.find('[data-testid="new-serie-btn"]').trigger('click')
    expect(wrapper.find('[data-testid="serie-drawer"]').exists()).toBe(true)
    expect(wrapper.vm.drawerOpen).toBe(true)
    expect(wrapper.vm.drawerMode).toBe('create')
  })

  it('shows empty state when no Platz-Serien exist', () => {
    const original = mockPasseStore.savedSerien
    mockPasseStore.savedSerien = []
    try {
      const wrapper = mount(PassenAdminView, { global: { plugins: [router] } })
      expect(wrapper.text()).toContain('Noch keine Platz-Serien')
    } finally {
      mockPasseStore.savedSerien = original
    }
  })

  it('opens SerieDrawer in edit mode when a serie row is clicked', async () => {
    const wrapper = mount(PassenAdminView, { global: { plugins: [router] } })
    await wrapper.vm.$nextTick()
    await wrapper.find('.serie-row').trigger('click')
    expect(wrapper.vm.drawerOpen).toBe(true)
    expect(wrapper.vm.drawerMode).toBe('edit')
    expect(wrapper.vm.drawerSerie).toEqual(mockPasseStore.savedSerien[0])
  })
})

describe('PassenAdminView — Passen tab', () => {
  let router

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    router = makeRouter()
  })

  const switchToPassen = async (wrapper) => {
    await wrapper.findAll('.tab-btn')[1].trigger('click')
    await flushPromises()
    await wrapper.vm.$nextTick()
  }

  it('shows Passen-Vorlagen header with count badge', async () => {
    const wrapper = mount(PassenAdminView, { global: { plugins: [router] } })
    await switchToPassen(wrapper)
    expect(wrapper.text()).toContain('Passen-Vorlagen')
    expect(wrapper.text()).toContain('2')
  })

  it('groups Passen by range', async () => {
    const wrapper = mount(PassenAdminView, { global: { plugins: [router] } })
    await switchToPassen(wrapper)
    expect(wrapper.text()).toContain('Platz 1')
    expect(wrapper.text()).toContain('Platz 2')
  })

  it('shows Passen names in list', async () => {
    const wrapper = mount(PassenAdminView, { global: { plugins: [router] } })
    await switchToPassen(wrapper)
    expect(wrapper.text()).toContain('Olympisch Vorlage')
    expect(wrapper.text()).toContain('Skeet Vorlage')
  })

  it('opens GlobalPasseDrawer in create mode when Neue Passe button is clicked', async () => {
    const wrapper = mount(PassenAdminView, { global: { plugins: [router] } })
    await switchToPassen(wrapper)
    await wrapper.find('[data-testid="new-passe-btn"]').trigger('click')
    expect(wrapper.vm.passeDrawerOpen).toBe(true)
    expect(wrapper.vm.passeDrawerMode).toBe('create')
  })

  it('opens GlobalPasseDrawer in edit mode when a passe row is clicked', async () => {
    const wrapper = mount(PassenAdminView, { global: { plugins: [router] } })
    await switchToPassen(wrapper)
    await wrapper.find('.passe-row').trigger('click')
    expect(wrapper.vm.passeDrawerOpen).toBe(true)
    expect(wrapper.vm.passeDrawerMode).toBe('edit')
    expect(wrapper.vm.passeDrawerPasse).toEqual(mockPasseStore.savedGlobalPassen[0])
  })

  it('shows empty state when no Passen-Vorlagen exist', async () => {
    const original = mockPasseStore.savedGlobalPassen
    mockPasseStore.savedGlobalPassen = []
    try {
      const wrapper = mount(PassenAdminView, { global: { plugins: [router] } })
      await switchToPassen(wrapper)
      expect(wrapper.text()).toContain('Noch keine Passen-Vorlagen')
    } finally {
      mockPasseStore.savedGlobalPassen = original
    }
  })
})
