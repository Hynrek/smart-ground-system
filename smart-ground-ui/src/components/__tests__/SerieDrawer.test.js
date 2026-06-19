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

  it('resets stepMode to solo after pair is completed', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'create', serie: null },
      global: { stubs: { Icons: true } },
      attachTo: document.body,
    })
    await wrapper.vm.$nextTick()
    // Select range first to get position buttons
    const select = getDrawerSelect()
    expect(select).toBeTruthy()
    if (select) {
      select.value = 'r1'
      select.dispatchEvent(new Event('change', { bubbles: true }))
    }
    await wrapper.vm.$nextTick()

    // Switch to pair mode
    const typeBtns = getDrawerElement()?.querySelectorAll('.type-btn')
    const pairBtn = Array.from(typeBtns ?? []).find(b => b.textContent === 'Pair')
    expect(pairBtn).toBeTruthy()
    pairBtn?.click()
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.stepMode).toBe('pair')

    // Click first position (sets pairPending)
    const posBtns = getDrawerElement()?.querySelectorAll('[data-testid="pos-btn"]')
    expect(posBtns?.length).toBeGreaterThanOrEqual(2)
    posBtns?.[0]?.click()
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.pairPending).not.toBeNull()

    // Click second position (completes pair, resets to solo)
    posBtns?.[1]?.click()
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.stepMode).toBe('solo')
    expect(wrapper.vm.pairPending).toBeNull()
    const stepRows = getDrawerElement()?.querySelectorAll('.step-row')
    expect(stepRows?.length).toBe(1)
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

describe('SerieDrawer — edit mode', () => {
  const mockSerie = {
    id: '_sg_range_serie_1',
    name: 'Test Serie',
    rangeId: 'r1',
    rangeName: 'Platz 1',
    steps: [
      { id: 1, type: 'solo', alias: 'Werfer 1', positionId: 'pos-a', letter: 'A' },
    ],
    ownership: 'range',
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  it('pre-fills name input in edit mode', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'edit', serie: mockSerie },
      global: { stubs: { Icons: true } },
      attachTo: document.body,
    })
    await wrapper.vm.$nextTick()
    expect(getDrawerInput()?.value).toBe('Test Serie')
  })

  it('shows range as read-only label instead of picker in edit mode', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'edit', serie: mockSerie },
      global: { stubs: { Icons: true } },
      attachTo: document.body,
    })
    await wrapper.vm.$nextTick()
    expect(getDrawerSelect()).toBeFalsy()
    const readOnlyField = getDrawerElement()?.querySelector('.field-readonly')
    expect(readOnlyField?.textContent).toBe('Platz 1')
  })

  it('pre-populates step sequence with existing steps', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'edit', serie: mockSerie },
      global: { stubs: { Icons: true } },
      attachTo: document.body,
    })
    await wrapper.vm.$nextTick()
    const stepRows = getDrawerElement()?.querySelectorAll('.step-row')
    expect(stepRows?.length).toBe(1)
  })

  it('shows delete button in edit mode', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'edit', serie: mockSerie },
      global: { stubs: { Icons: true } },
      attachTo: document.body,
    })
    await wrapper.vm.$nextTick()
    const deleteBtn = getDrawerElement()?.querySelector('[data-testid="delete-btn"]')
    expect(deleteBtn).toBeTruthy()
  })

  it('does not show delete button in create mode', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'create', serie: null },
      global: { stubs: { Icons: true } },
      attachTo: document.body,
    })
    await wrapper.vm.$nextTick()
    const deleteBtn = getDrawerElement()?.querySelector('[data-testid="delete-btn"]')
    expect(deleteBtn).toBeFalsy()
  })

  it('calls updateSerie and emits saved+close when save is clicked in edit mode', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'edit', serie: mockSerie },
      global: { stubs: { Icons: true } },
      attachTo: document.body,
    })
    await wrapper.vm.$nextTick()
    const saveBtn = getSaveBtn()
    expect(saveBtn?.disabled).toBeFalsy()
    saveBtn?.click()
    await wrapper.vm.$nextTick()
    expect(mockPasseStore.updateSerie).toHaveBeenCalledWith(
      '_sg_range_serie_1',
      'Test Serie',
      expect.arrayContaining([expect.objectContaining({ type: 'solo' })]),
    )
    expect(wrapper.emitted('saved')).toBeTruthy()
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('shows confirm UI after delete button clicked', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'edit', serie: mockSerie },
      global: { stubs: { Icons: true } },
      attachTo: document.body,
    })
    await wrapper.vm.$nextTick()
    const deleteBtn = getDrawerElement()?.querySelector('[data-testid="delete-btn"]')
    deleteBtn?.click()
    await wrapper.vm.$nextTick()
    const confirmText = getDrawerElement()?.querySelector('.delete-confirm-text')
    expect(confirmText).toBeTruthy()
  })

  it('calls deleteSerie and emits deleted+close when confirmed', async () => {
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'edit', serie: mockSerie },
      global: { stubs: { Icons: true } },
      attachTo: document.body,
    })
    await wrapper.vm.$nextTick()
    const deleteBtn = getDrawerElement()?.querySelector('[data-testid="delete-btn"]')
    deleteBtn?.click()
    await wrapper.vm.$nextTick()
    const dangerBtn = getDrawerElement()?.querySelector('.btn--danger')
    dangerBtn?.click()
    await wrapper.vm.$nextTick()
    expect(mockPasseStore.deleteSerie).toHaveBeenCalledWith('_sg_range_serie_1')
    expect(wrapper.emitted('deleted')).toBeTruthy()
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('renders a placeholder for a step whose position was deleted (null letter)', async () => {
    const serie = {
      id: '_sg_range_serie_1',
      name: 'Solo',
      rangeId: 'r1',
      rangeName: 'Platz 1',
      steps: [{ id: 1, type: 'solo', positionId: 'pos-a', alias: null, letter: null }],
      ownership: 'range',
    }
    const wrapper = mount(SerieDrawer, {
      props: { open: true, mode: 'edit', serie },
      global: { stubs: { Icons: true } },
      attachTo: document.body,
    })
    await wrapper.vm.$nextTick()
    const label = getDrawerElement()?.querySelector('.step-label')
    expect(label?.textContent).toContain('—')
    expect(label?.textContent).not.toContain('null')
  })
})
