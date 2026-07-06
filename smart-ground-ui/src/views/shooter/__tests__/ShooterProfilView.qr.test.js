import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ShooterProfilView from '../ShooterProfilView.vue'
import { useAuthStore } from '@/stores/authStore.js'
import * as userApi from '@/services/userApi.js'
import QRCode from 'qrcode'
import { buildCheckinPayload } from '@/constants/qr.js'

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal()),
  useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
}))
vi.mock('qrcode', () => ({
  default: { toCanvas: vi.fn().mockResolvedValue(undefined) },
}))
vi.mock('@/services/userApi.js', () => ({
  fetchMyQrToken: vi.fn(),
  rotateMyQrToken: vi.fn(),
  resolveUserByQr: vi.fn(),
}))
vi.mock('@/services/scoreApi.js', () => ({
  fetchMyScores: vi.fn().mockResolvedValue({ content: [], meta: null }),
  fetchMyScoreSummary: vi.fn().mockResolvedValue(null),
  fetchLeaderboard: vi.fn(),
}))

const mountView = () => {
  const auth = useAuthStore()
  auth.profile = { vorname: 'Anna', nachname: 'Muster', username: 'anna' }
  return mount(ShooterProfilView, {
    global: { stubs: { Icons: true } },
  })
}

const clickTab = async (wrapper, label) => {
  const tab = wrapper.findAll('.tab-btn').find((b) => b.text() === label)
  await tab.trigger('click')
  await flushPromises()
}

describe('ShooterProfilView QR tab', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads the token and renders the QR code when the tab is opened', async () => {
    userApi.fetchMyQrToken.mockResolvedValue({ qrToken: 'tok-1' })
    const wrapper = mountView()

    await clickTab(wrapper, 'QR-Code')

    expect(userApi.fetchMyQrToken).toHaveBeenCalled()
    expect(QRCode.toCanvas).toHaveBeenCalledWith(
      expect.anything(),
      buildCheckinPayload('tok-1'),
      expect.anything(),
    )
  })

  it('rotates the token and re-renders', async () => {
    userApi.fetchMyQrToken.mockResolvedValue({ qrToken: 'tok-1' })
    userApi.rotateMyQrToken.mockResolvedValue({ qrToken: 'tok-2' })
    const wrapper = mountView()
    await clickTab(wrapper, 'QR-Code')

    await wrapper.get('.qr-rotate-btn').trigger('click')
    await flushPromises()

    expect(userApi.rotateMyQrToken).toHaveBeenCalled()
    expect(QRCode.toCanvas).toHaveBeenLastCalledWith(
      expect.anything(),
      buildCheckinPayload('tok-2'),
      expect.anything(),
    )
  })

})
