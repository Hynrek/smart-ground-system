import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, it, expect, vi } from 'vitest'
import SoloSerieStartModal from '@/components/shooter-remote/SoloSerieStartModal.vue'

vi.mock('@/stores/authStore.js', () => ({
  useAuthStore: () => ({ profile: { id: 'u1', vorname: 'Max', nachname: 'M' } }),
}))

describe('SoloSerieStartModal', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shows the current user and record unchecked by default', () => {
    const w = mount(SoloSerieStartModal)
    expect(w.text()).toContain('Max')
    expect(w.find('[data-testid="record-toggle"]').element.checked).toBe(false)
  })

  it('emits confirm with current user and record flag', async () => {
    const w = mount(SoloSerieStartModal)
    await w.find('[data-testid="record-toggle"]').setValue(true)
    await w.find('[data-testid="confirm"]').trigger('click')
    expect(w.emitted('confirm')[0][0]).toMatchObject({ record: true, shooter: { userId: 'u1' } })
  })
})
