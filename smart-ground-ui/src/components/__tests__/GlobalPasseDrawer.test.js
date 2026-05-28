import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import GlobalPasseDrawer from '../GlobalPasseDrawer.vue'

const mockPasseStore = {
  savedSerien: [
    { id: '_sg_range_serie_1', name: 'Olympisch', rangeId: 'r1', rangeName: 'Platz 1', steps: [], ownership: 'range' },
    { id: '_sg_range_serie_2', name: 'Skeet', rangeId: 'r2', rangeName: 'Platz 2', steps: [], ownership: 'range' },
  ],
  createGlobalPasse: vi.fn(),
  updateGlobalPasse: vi.fn(),
  deleteGlobalPasse: vi.fn(),
}

vi.mock('@/stores/passeStore.js', () => ({ usePasseStore: () => mockPasseStore }))
vi.mock('@/components/Icons.vue', () => ({
  default: { template: '<span />', props: ['icon', 'size', 'color'] },
}))

// Helpers to access teleported DOM
const getDrawerElement = () => document.querySelector('[data-testid="global-passe-drawer"]')
const getDrawerInput = () => getDrawerElement()?.querySelector('input[type="text"]')
const getBackdrop = () => document.querySelector('.drawer-backdrop')
const getSaveBtn = () => getDrawerElement()?.querySelector('[data-testid="save-btn"]')
const getCancelBtn = () => getDrawerElement()?.querySelector('[data-testid="cancel-btn"]')
const getDeleteBtn = () => getDrawerElement()?.querySelector('[data-testid="delete-btn"]')
const getPickerItems = () => document.querySelectorAll('[data-testid="picker-item"]')
const getSelectedRows = () => document.querySelectorAll('[data-testid="selected-row"]')
const getDangerBtn = () => getDrawerElement()?.querySelector('.btn--danger')
const getDeleteConfirmText = () => getDrawerElement()?.querySelector('.delete-confirm-text')

const mountDrawer = (propsData) => {
  document.body.innerHTML = ''
  return mount(GlobalPasseDrawer, {
    props: propsData,
    attachTo: document.body,
    global: { plugins: [createPinia()], stubs: { Icons: true } },
  })
}

describe('GlobalPasseDrawer — create mode', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders name input and Serie picker when open in create mode', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'create', passe: null })
    await wrapper.vm.$nextTick()
    expect(getDrawerElement()).toBeTruthy()
    expect(getDrawerInput()).toBeTruthy()
    expect(getPickerItems().length).toBe(2)
  })

  it('Save button is disabled when name is empty', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'create', passe: null })
    await wrapper.vm.$nextTick()
    const saveBtn = getSaveBtn()
    expect(saveBtn?.disabled).toBe(true)
  })

  it('Save button is disabled when name is set but no Serie is selected', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'create', passe: null })
    await wrapper.vm.$nextTick()
    const input = getDrawerInput()
    input.value = 'My Passe'
    input.dispatchEvent(new Event('input', { bubbles: true }))
    await wrapper.vm.$nextTick()
    const saveBtn = getSaveBtn()
    expect(saveBtn?.disabled).toBe(true)
  })

  it('Save button is enabled when name is set and at least one Serie is selected', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'create', passe: null })
    await wrapper.vm.$nextTick()
    const input = getDrawerInput()
    input.value = 'My Passe'
    input.dispatchEvent(new Event('input', { bubbles: true }))
    await wrapper.vm.$nextTick()
    const pickerItems = getPickerItems()
    pickerItems[0].click()
    await wrapper.vm.$nextTick()
    const saveBtn = getSaveBtn()
    expect(saveBtn?.disabled).toBe(false)
  })

  it('clicking backdrop emits close', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'create', passe: null })
    await wrapper.vm.$nextTick()
    const backdrop = getBackdrop()
    expect(backdrop).toBeTruthy()
    backdrop?.click()
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('clicking Cancel emits close', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'create', passe: null })
    await wrapper.vm.$nextTick()
    const cancelBtn = getCancelBtn()
    expect(cancelBtn).toBeTruthy()
    cancelBtn?.click()
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('clicking Save calls createGlobalPasse and emits saved and close', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'create', passe: null })
    await wrapper.vm.$nextTick()
    const input = getDrawerInput()
    input.value = 'Neue Vorlage'
    input.dispatchEvent(new Event('input', { bubbles: true }))
    await wrapper.vm.$nextTick()
    const pickerItems = getPickerItems()
    pickerItems[0].click()
    await wrapper.vm.$nextTick()
    const saveBtn = getSaveBtn()
    saveBtn?.click()
    await wrapper.vm.$nextTick()
    expect(mockPasseStore.createGlobalPasse).toHaveBeenCalledWith(
      'Neue Vorlage',
      expect.arrayContaining([expect.objectContaining({ id: '_sg_range_serie_1' })]),
    )
    expect(wrapper.emitted('saved')).toBeTruthy()
    expect(wrapper.emitted('close')).toBeTruthy()
  })
})

describe('GlobalPasseDrawer — edit mode', () => {
  const existingPasse = {
    id: '_sg_global_passe_1',
    name: 'Olympisch Vorlage',
    serien: [
      { id: '_sg_range_serie_1', alias: 'Olympisch', rangeId: 'r1', rangeName: 'Platz 1', steps: [] },
    ],
    ownership: 'global',
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('pre-fills name field in edit mode', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'edit', passe: existingPasse })
    await wrapper.vm.$nextTick()
    const input = getDrawerInput()
    expect(input?.value).toBe('Olympisch Vorlage')
  })

  it('pre-populates selectedSerien list from passe serien', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'edit', passe: existingPasse })
    await wrapper.vm.$nextTick()
    const rows = getSelectedRows()
    expect(rows.length).toBe(1)
  })

  it('shows delete button in edit mode', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'edit', passe: existingPasse })
    await wrapper.vm.$nextTick()
    expect(getDeleteBtn()).toBeTruthy()
  })

  it('delete button is not shown in create mode', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'create', passe: null })
    await wrapper.vm.$nextTick()
    expect(getDeleteBtn()).toBeFalsy()
  })

  it('clicking Save in edit mode calls updateGlobalPasse', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'edit', passe: existingPasse })
    await wrapper.vm.$nextTick()
    const input = getDrawerInput()
    input.value = 'Geänderte Vorlage'
    input.dispatchEvent(new Event('input', { bubbles: true }))
    await wrapper.vm.$nextTick()
    const saveBtn = getSaveBtn()
    saveBtn?.click()
    await wrapper.vm.$nextTick()
    expect(mockPasseStore.updateGlobalPasse).toHaveBeenCalledWith(
      '_sg_global_passe_1',
      'Geänderte Vorlage',
      expect.any(Array),
    )
  })

  it('clicking delete button reveals confirm controls', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'edit', passe: existingPasse })
    await wrapper.vm.$nextTick()
    const deleteBtn = getDeleteBtn()
    deleteBtn?.click()
    await wrapper.vm.$nextTick()
    expect(getDeleteConfirmText()).toBeTruthy()
    expect(getDeleteBtn()).toBeFalsy()
  })

  it('confirming delete calls deleteGlobalPasse and emits deleted and close', async () => {
    const wrapper = mountDrawer({ open: true, mode: 'edit', passe: existingPasse })
    await wrapper.vm.$nextTick()
    const deleteBtn = getDeleteBtn()
    deleteBtn?.click()
    await wrapper.vm.$nextTick()
    const dangerBtn = getDangerBtn()
    dangerBtn?.click()
    await wrapper.vm.$nextTick()
    expect(mockPasseStore.deleteGlobalPasse).toHaveBeenCalledWith('_sg_global_passe_1')
    expect(wrapper.emitted('deleted')).toBeTruthy()
    expect(wrapper.emitted('close')).toBeTruthy()
  })
})
