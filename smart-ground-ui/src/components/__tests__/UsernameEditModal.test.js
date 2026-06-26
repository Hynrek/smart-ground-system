import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import UsernameEditModal from '../UsernameEditModal.vue'
import { useAuthStore } from '@/stores/authStore'

describe('UsernameEditModal', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('blocks an invalid handle client-side', async () => {
    const store = useAuthStore()
    store.profile = { id: 'u1', username: 'old' }
    store.updateOwnUsername = vi.fn()

    const wrapper = mount(UsernameEditModal)
    await wrapper.find('[data-testid="edit-username"]').setValue('ab')
    await wrapper.find('[data-testid="save"]').trigger('click')

    expect(store.updateOwnUsername).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="username-error"]').exists()).toBe(true)
  })

  it('saves a valid handle and emits saved', async () => {
    const store = useAuthStore()
    store.profile = { id: 'u1', username: 'old' }
    store.updateOwnUsername = vi.fn().mockResolvedValue()

    const wrapper = mount(UsernameEditModal)
    await wrapper.find('[data-testid="edit-username"]').setValue('newhandle')
    await wrapper.find('[data-testid="save"]').trigger('click')

    expect(store.updateOwnUsername).toHaveBeenCalledWith('newhandle')
    expect(wrapper.emitted('saved')).toBeTruthy()
  })
})
