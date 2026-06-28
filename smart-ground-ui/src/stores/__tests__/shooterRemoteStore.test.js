import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useShooterRemoteStore } from '../shooterRemoteStore'

describe('useShooterRemoteStore — Verzögert (delayed) mode', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('defaults delaySeconds to 3', () => {
    const store = useShooterRemoteStore()
    expect(store.delaySeconds).toBe(3)
  })

  it('clamps delaySeconds into the 1–10 range and persists it', () => {
    const store = useShooterRemoteStore()

    store.setDelaySeconds(7)
    expect(store.delaySeconds).toBe(7)
    expect(localStorage.getItem('sg_remote_delay')).toBe('7')

    store.setDelaySeconds(0)
    expect(store.delaySeconds).toBe(1)

    store.setDelaySeconds(99)
    expect(store.delaySeconds).toBe(10)
  })

  it('rounds fractional input', () => {
    const store = useShooterRemoteStore()
    store.setDelaySeconds(4.6)
    expect(store.delaySeconds).toBe(5)
  })

  it('restores a persisted delay on init', () => {
    localStorage.setItem('sg_remote_delay', '8')
    const store = useShooterRemoteStore()
    expect(store.delaySeconds).toBe(8)
  })

  it('switches into delayed session mode without starting a capture', () => {
    const store = useShooterRemoteStore()
    store.reservePlatz('range-1')

    store.setSessionMode('delayed')

    expect(store.sessionMode).toBe('delayed')
    expect(store.recordingActive).toBe(false)
  })
})
