import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const mockCreate = vi.fn().mockResolvedValue(undefined)
const mockUpdate = vi.fn().mockResolvedValue(undefined)

vi.mock('@/stores/userStore.js', () => ({
  useUserStore: () => ({
    createUser: mockCreate,
    updateUser: mockUpdate,
    isLoading: false,
    error: null,
  }),
}))

async function importModal() {
  const { default: UserFormModal } = await import('../UserFormModal.vue')
  return UserFormModal
}

describe('UserFormModal', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders Basisdaten tab active by default', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, { props: { mode: 'create' } })
    expect(wrapper.find('[data-testid="tab-base"]').classes()).toContain('active')
    expect(wrapper.find('[data-testid="tab-extended"]').classes()).not.toContain('active')
  })

  it('switches to Erweitert tab on click', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, { props: { mode: 'create' } })
    await wrapper.find('[data-testid="tab-extended"]').trigger('click')
    expect(wrapper.find('[data-testid="tab-extended"]').classes()).toContain('active')
    expect(wrapper.find('[data-testid="tab-base"]').classes()).not.toContain('active')
  })

  it('shows validation errors when required fields empty on submit', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, { props: { mode: 'create' } })
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    expect(wrapper.find('[data-testid="error-vorname"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="error-email"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="error-password"]').exists()).toBe(true)
  })

  it('switches back to Basisdaten tab when validation fails while on Erweitert', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, { props: { mode: 'create' } })
    await wrapper.find('[data-testid="tab-extended"]').trigger('click')
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    expect(wrapper.find('[data-testid="tab-base"]').classes()).toContain('active')
  })

  it('emits close when Abbrechen is clicked', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, { props: { mode: 'create' } })
    await wrapper.find('[data-testid="cancel-btn"]').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('calls createUser and emits saved after valid create submit', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, { props: { mode: 'create' } })
    await wrapper.find('[data-testid="input-vorname"]').setValue('Hans')
    await wrapper.find('[data-testid="input-nachname"]').setValue('Müller')
    await wrapper.find('[data-testid="input-email"]').setValue('hans@test.ch')
    await wrapper.find('[data-testid="input-password"]').setValue('secret123')
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    await wrapper.vm.$nextTick()
    expect(mockCreate).toHaveBeenCalledOnce()
    expect(wrapper.emitted('saved')).toBeTruthy()
  })

  it('does not show password field in edit mode', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, {
      props: { mode: 'edit', initialUser: { id: 'u1', vorname: 'Anna', nachname: 'Schmidt', email: 'anna@test.ch' } },
    })
    expect(wrapper.find('[data-testid="input-password"]').exists()).toBe(false)
  })

  it('does not show role dropdown in edit mode', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, {
      props: { mode: 'edit', initialUser: { id: 'u1', vorname: 'Anna', nachname: 'Schmidt', email: 'anna@test.ch' } },
    })
    expect(wrapper.find('[data-testid="input-role"]').exists()).toBe(false)
  })

  it('pre-populates form with initialUser data in edit mode', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, {
      props: {
        mode: 'edit',
        initialUser: { id: 'u1', vorname: 'Anna', nachname: 'Schmidt', email: 'anna@test.ch', telefonnummer: '+41 79 111' },
      },
    })
    expect(wrapper.find('[data-testid="input-vorname"]').element.value).toBe('Anna')
    expect(wrapper.find('[data-testid="input-nachname"]').element.value).toBe('Schmidt')
  })

  it('calls updateUser and emits saved after valid edit submit', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, {
      props: { mode: 'edit', initialUser: { id: 'u1', vorname: 'Anna', nachname: 'Schmidt', email: 'anna@test.ch' } },
    })
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    await wrapper.vm.$nextTick()
    expect(mockUpdate).toHaveBeenCalledWith('u1', expect.objectContaining({ vorname: 'Anna', nachname: 'Schmidt' }))
    expect(wrapper.emitted('saved')).toBeTruthy()
  })
})
