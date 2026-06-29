import { describe, it, expect, beforeEach, afterEach } from 'vitest'
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

describe('useShooterRemoteStore — Ruf settings', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('has correct default values', () => {
    const store = useShooterRemoteStore()
    expect(store.rufPeak).toBe(70)
    expect(store.rufDauer).toBe(120)
    expect(store.rufTotzeit).toBe(1000)
  })

  it('setRufPeak clamps to 0–100', () => {
    const store = useShooterRemoteStore()
    store.setRufPeak(150)
    expect(store.rufPeak).toBe(100)
    store.setRufPeak(-10)
    expect(store.rufPeak).toBe(0)
    store.setRufPeak(60)
    expect(store.rufPeak).toBe(60)
  })

  it('setRufDauer clamps to 50–500', () => {
    const store = useShooterRemoteStore()
    store.setRufDauer(10)
    expect(store.rufDauer).toBe(50)
    store.setRufDauer(999)
    expect(store.rufDauer).toBe(500)
    store.setRufDauer(200)
    expect(store.rufDauer).toBe(200)
  })

  it('setRufTotzeit clamps to 0–8000', () => {
    const store = useShooterRemoteStore()
    store.setRufTotzeit(-100)
    expect(store.rufTotzeit).toBe(0)
    store.setRufTotzeit(99999)
    expect(store.rufTotzeit).toBe(8000)
    store.setRufTotzeit(2000)
    expect(store.rufTotzeit).toBe(2000)
  })

  it('setRufPeak persists to localStorage', () => {
    const store = useShooterRemoteStore()
    store.setRufPeak(55)
    expect(localStorage.getItem('sg_ruf_peak')).toBe('55')
  })

  it('setRufDauer persists to localStorage', () => {
    const store = useShooterRemoteStore()
    store.setRufDauer(300)
    expect(localStorage.getItem('sg_ruf_dauer')).toBe('300')
  })

  it('setRufTotzeit persists to localStorage', () => {
    const store = useShooterRemoteStore()
    store.setRufTotzeit(3000)
    expect(localStorage.getItem('sg_ruf_totzeit')).toBe('3000')
  })

  it('loads rufPeak from localStorage on init', () => {
    localStorage.setItem('sg_ruf_peak', '42')
    const store = useShooterRemoteStore()
    expect(store.rufPeak).toBe(42)
  })

  it('loads rufDauer from localStorage on init', () => {
    localStorage.setItem('sg_ruf_dauer', '250')
    const store = useShooterRemoteStore()
    expect(store.rufDauer).toBe(250)
  })

  it('loads rufTotzeit from localStorage on init', () => {
    localStorage.setItem('sg_ruf_totzeit', '5000')
    const store = useShooterRemoteStore()
    expect(store.rufTotzeit).toBe(5000)
  })
})
