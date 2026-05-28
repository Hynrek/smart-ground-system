import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import SerieDrawer from '../SerieDrawer.vue'

const mockRangeStore = {
  ranges: [
    { id: 'r1', name: 'Platz 1' },
    { id: 'r2', name: 'Platz 2' },
  ],
  positions: {
    r1: [
      { id: 'pos-a', label: 'A', device: { alias: 'Werfer 1' } },
      { id: 'pos-b', label: 'B', device: { alias: 'Werfer 2' } },
    ],
  },
  loadPositions: vi.fn().mockResolvedValue(undefined),
}

const mockPasseStore = {
  createRangeSerie: vi.fn(),
  updateSerie: vi.fn(),
  deleteSerie: vi.fn(),
}

vi.mock('@/stores/rangeStore.js', () => ({
  useRangeStore: () => mockRangeStore,
}))

vi.mock('@/stores/passeStore.js', () => ({
  usePasseStore: () => mockPasseStore,
}))

// Helper to find elements in teleported DOM
const getDrawerElement = () => document.querySelector('[data-testid="serie-drawer"]')
const getDrawerInput = () => getDrawerElement()?.querySelector('input[type="text"]')
const getDrawerSelect = () => getDrawerElement()?.querySelector('select')
const getSaveBtn = () => getDrawerElement()?.querySelector('[data-testid="save-btn"]')
const getCancelBtn = () => getDrawerElement()?.querySelector('[data-testid="cancel-btn"]')
const getBackdrop = () => document.querySelector('.drawer-backdrop')

describe('SerieDrawer — create mode', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    // Clear the DOM between tests
    document.body.innerHTML = ''
  })

  it('renders name input and range picker when open in create mode', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'create', serie: null },
      global: { stubs: { Icons: true } },
      attachTo: document.body,
    })
    await wrapper.vm.$nextTick()
    expect(getDrawerElement()).toBeTruthy()
    expect(getDrawerInput()).toBeTruthy()
    expect(getDrawerSelect()).toBeTruthy()
  })

  it('does not render when open is false', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: false, mode: 'create', serie: null },
      global: { stubs: { Icons: true } },
      attachTo: document.body,
    })
    await wrapper.vm.$nextTick()
    expect(getDrawerElement()).toBeFalsy()
  })

  it('save button is disabled when name is empty', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'create', serie: null },
      global: { stubs: { Icons: true } },
      attachTo: document.body,
    })
    await wrapper.vm.$nextTick()
    expect(getSaveBtn()?.disabled).toBe(true)
  })

  it('emits close when backdrop is clicked', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'create', serie: null },
      global: { stubs: { Icons: true } },
      attachTo: document.body,
    })
    await wrapper.vm.$nextTick()
    const backdrop = getBackdrop()
    expect(backdrop).toBeTruthy()
    backdrop?.click()
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('emits close when Abbrechen is clicked', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'create', serie: null },
      global: { stubs: { Icons: true } },
      attachTo: document.body,
    })
    await wrapper.vm.$nextTick()
    const cancelBtn = getCancelBtn()
    expect(cancelBtn).toBeTruthy()
    cancelBtn?.click()
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('calls loadPositions and shows position buttons after range is selected', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'create', serie: null },
      global: { stubs: { Icons: true } },
      attachTo: document.body,
    })
    await wrapper.vm.$nextTick()
    const select = getDrawerSelect()
    expect(select).toBeTruthy()
    if (select) {
      select.value = 'r1'
      select.dispatchEvent(new Event('change', { bubbles: true }))
    }
    await wrapper.vm.$nextTick()
    expect(mockRangeStore.loadPositions).toHaveBeenCalledWith('r1')
    await wrapper.vm.$nextTick()
    const posButtons = getDrawerElement()?.querySelectorAll('[data-testid="pos-btn"]')
    expect(posButtons?.length).toBeGreaterThan(0)
  })
})
