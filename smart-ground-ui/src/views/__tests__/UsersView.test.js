import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/stores/userStore.js', () => ({
  useUserStore: () => ({
    users: [
      { id: 'u1', fullName: 'Hans Müller', email: 'hans@test.ch', status: 'ACTIVE' },
      { id: 'u2', fullName: 'Anna Schmidt', email: 'anna@test.ch', status: 'ACTIVE' },
    ],
    selectedUser: null,
    userRolesMap: { u1: [{ roleName: 'SHOOTER' }], u2: [{ roleName: 'ADMIN' }] },
    isLoading: false,
    error: null,
    availableRoles: [{ name: 'SHOOTER' }, { name: 'ADMIN' }],
    loadUsers: vi.fn().mockResolvedValue(undefined),
    loadAvailableRoles: vi.fn().mockResolvedValue(undefined),
    selectUser: vi.fn(),
    deleteUser: vi.fn(),
    toggleRole: vi.fn(),
  }),
}))

vi.mock('@/components/UserFormModal.vue', () => ({
  default: { template: '<div />' },
}))

describe('UsersView', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('renders all users in the list', async () => {
    const { default: UsersView } = await import('../UsersView.vue')
    const wrapper = mount(UsersView)
    expect(wrapper.text()).toContain('Hans Müller')
    expect(wrapper.text()).toContain('Anna Schmidt')
  })

  it('renders role badges in the list', async () => {
    const { default: UsersView } = await import('../UsersView.vue')
    const wrapper = mount(UsersView)
    expect(wrapper.text()).toContain('Schütze')
    expect(wrapper.text()).toContain('Admin')
  })

  it('filters list by search term', async () => {
    const { default: UsersView } = await import('../UsersView.vue')
    const wrapper = mount(UsersView)
    await wrapper.find('[data-testid="search-input"]').setValue('anna')
    expect(wrapper.text()).not.toContain('Hans Müller')
    expect(wrapper.text()).toContain('Anna Schmidt')
  })

  it('shows empty state when no user is selected', async () => {
    const { default: UsersView } = await import('../UsersView.vue')
    const wrapper = mount(UsersView)
    expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(true)
  })

  it('opens create modal when + Neu is clicked', async () => {
    const { default: UsersView } = await import('../UsersView.vue')
    const wrapper = mount(UsersView)
    await wrapper.find('[data-testid="btn-create"]').trigger('click')
    expect(wrapper.find('[data-testid="user-form-modal"]').exists()).toBe(true)
  })
})
