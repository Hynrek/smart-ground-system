import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import QrScanModal from '../shooter/QrScanModal.vue'
import * as userApi from '@/services/userApi.js'
import { buildCheckinPayload } from '@/constants/qr.js'

vi.mock('qr-scanner', () => {
  class FakeScanner {
    constructor(video, onDecode) {
      this.onDecode = onDecode
      FakeScanner.instances.push(this)
    }
    start = vi.fn().mockResolvedValue(undefined)
    destroy = vi.fn()
  }
  FakeScanner.instances = []
  return { default: FakeScanner }
})

vi.mock('@/services/userApi.js', () => ({
  fetchMyQrToken: vi.fn(),
  rotateMyQrToken: vi.fn(),
  resolveUserByQr: vi.fn(),
  fetchMyPlayResults: vi.fn(),
}))

import QrScanner from 'qr-scanner'

const mountModal = () => mount(QrScanModal)

const emitScan = async (wrapper, payload) => {
  const scanner = QrScanner.instances.at(-1)
  await scanner.onDecode({ data: payload })
  await flushPromises()
}

describe('QrScanModal', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    QrScanner.instances.length = 0
  })

  it('starts the camera scanner on mount and destroys it on unmount', async () => {
    const wrapper = mountModal()
    await flushPromises()
    const scanner = QrScanner.instances.at(-1)
    expect(scanner.start).toHaveBeenCalled()

    wrapper.unmount()
    expect(scanner.destroy).toHaveBeenCalled()
  })

  it('ignores QR codes without the check-in prefix', async () => {
    const wrapper = mountModal()
    await flushPromises()

    await emitScan(wrapper, 'https://example.com/some-random-qr')

    expect(userApi.resolveUserByQr).not.toHaveBeenCalled()
    expect(wrapper.emitted('resolved')).toBeUndefined()
  })

  it('resolves a valid payload and emits the user', async () => {
    userApi.resolveUserByQr.mockResolvedValue({ userId: 'u1', displayName: 'Anna Muster' })
    const wrapper = mountModal()
    await flushPromises()

    await emitScan(wrapper, buildCheckinPayload('tok-1'))

    expect(userApi.resolveUserByQr).toHaveBeenCalledWith('tok-1')
    expect(wrapper.emitted('resolved')[0][0]).toEqual({ userId: 'u1', displayName: 'Anna Muster' })
  })

  it('shows "Code ungültig" on 404 and keeps scanning', async () => {
    userApi.resolveUserByQr.mockRejectedValue(Object.assign(new Error('nope'), { status: 404 }))
    const wrapper = mountModal()
    await flushPromises()

    await emitScan(wrapper, buildCheckinPayload('rotated'))

    expect(wrapper.text()).toContain('Code ungültig')
    expect(wrapper.emitted('resolved')).toBeUndefined()
  })

  it('does not re-resolve the same token twice in a row', async () => {
    userApi.resolveUserByQr.mockResolvedValue({ userId: 'u1', displayName: 'Anna' })
    const wrapper = mountModal()
    await flushPromises()

    await emitScan(wrapper, buildCheckinPayload('tok-1'))
    await emitScan(wrapper, buildCheckinPayload('tok-1'))

    expect(userApi.resolveUserByQr).toHaveBeenCalledTimes(1)
  })

  it('emits close when cancel is clicked', async () => {
    const wrapper = mountModal()
    await flushPromises()

    await wrapper.get('.qr-scan-cancel').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
