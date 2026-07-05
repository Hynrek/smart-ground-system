import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

// Mock useVoiceTrigger so we can drive the mic trigger synchronously.
const mocks = vi.hoisted(() => {
  const captured = { onTrigger: null }
  return {
    captured,
    startListening: vi.fn((cb) => { captured.onTrigger = cb }),
    stopListening: vi.fn(() => { captured.onTrigger = null }),
    micLevel: { value: 0 },
    wouldTrigger: { value: false },
    micDenied: { value: false },
  }
})

vi.mock('@/composables/useVoiceTrigger.js', () => ({
  useVoiceTrigger: () => ({
    startListening: mocks.startListening,
    stopListening: mocks.stopListening,
    micLevel: mocks.micLevel,
    wouldTrigger: mocks.wouldTrigger,
    micDenied: mocks.micDenied,
  }),
}))

import { useTriggerGating } from '../useTriggerGating'

const makeStore = (over = {}) => ({
  sessionMode: 'throwing',
  delaySeconds: 3,
  rufPeak: 70,
  rufDauer: 120,
  rufTotzeit: 1000,
  ...over,
})

describe('useTriggerGating', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mocks.startListening.mockClear()
    mocks.stopListening.mockClear()
    mocks.captured.onTrigger = null
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('throwing mode fires immediately and stays idle', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'throwing' }))
    const onFire = vi.fn()
    gating.arm(['p1'], onFire)
    expect(onFire).toHaveBeenCalledTimes(1)
    expect(gating.phase.value).toBe('idle')
    expect(gating.armedIds.value).toEqual([])
  })

  it('delayed mode does not fire before the countdown elapses', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'delayed', delaySeconds: 3 }))
    const onFire = vi.fn()
    gating.arm(['p1'], onFire)
    expect(gating.phase.value).toBe('counting')
    expect(gating.armedIds.value).toEqual(['p1'])
    vi.advanceTimersByTime(2000)
    expect(onFire).not.toHaveBeenCalled()
  })

  it('delayed mode fires after the countdown elapses and resets to idle', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'delayed', delaySeconds: 3 }))
    const onFire = vi.fn()
    gating.arm(['p1'], onFire)
    vi.advanceTimersByTime(3000)
    expect(onFire).toHaveBeenCalledTimes(1)
    expect(gating.phase.value).toBe('idle')
    expect(gating.armedIds.value).toEqual([])
  })

  it('re-arming an already-armed id cancels the pending fire', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'delayed', delaySeconds: 3 }))
    const onFire = vi.fn()
    gating.arm(['p1'], onFire)
    gating.arm(['p1'], onFire) // second tap on the same id aborts
    expect(gating.phase.value).toBe('idle')
    vi.advanceTimersByTime(3000)
    expect(onFire).not.toHaveBeenCalled()
  })

  it('delayed mode ignores a new arm while an episode is already running', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'delayed', delaySeconds: 3 }))
    const first = vi.fn()
    const second = vi.fn()
    gating.arm(['p1'], first)
    gating.arm(['p2'], second) // different id, episode busy → ignored
    expect(gating.armedIds.value).toEqual(['p1'])
    vi.advanceTimersByTime(3000)
    expect(first).toHaveBeenCalledTimes(1)
    expect(second).not.toHaveBeenCalled()
  })

  it('rufausloesung with totzeit=0 starts listening immediately', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'rufausloesung', rufTotzeit: 0 }))
    const onFire = vi.fn()
    gating.arm(['p1'], onFire)
    expect(gating.phase.value).toBe('listening')
    expect(mocks.startListening).toHaveBeenCalledTimes(1)
    expect(onFire).not.toHaveBeenCalled()
    // Simulate the mic trigger
    mocks.captured.onTrigger()
    expect(onFire).toHaveBeenCalledTimes(1)
    expect(gating.phase.value).toBe('idle')
  })

  it('rufausloesung with totzeit>0 waits in totzeit before listening', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'rufausloesung', rufTotzeit: 1000 }))
    const onFire = vi.fn()
    gating.arm(['p1'], onFire)
    expect(gating.phase.value).toBe('totzeit')
    expect(mocks.startListening).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1000)
    expect(gating.phase.value).toBe('listening')
    expect(mocks.startListening).toHaveBeenCalledTimes(1)
  })

  it('cancel() stops the mic and resets state', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'rufausloesung', rufTotzeit: 0 }))
    gating.arm(['p1'], vi.fn())
    gating.cancel()
    expect(mocks.stopListening).toHaveBeenCalled()
    expect(gating.phase.value).toBe('idle')
    expect(gating.armedIds.value).toEqual([])
  })

  it('isArmed reflects the armed ids', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'delayed', delaySeconds: 3 }))
    gating.arm(['p1', 'p2'], vi.fn())
    expect(gating.isArmed('p1')).toBe(true)
    expect(gating.isArmed('p2')).toBe(true)
    expect(gating.isArmed('p3')).toBe(false)
  })

  it('countdownLabel and remainingMs track the countdown', () => {
    const gating = useTriggerGating(makeStore({ sessionMode: 'delayed', delaySeconds: 3 }))
    gating.arm(['p1'], vi.fn())
    expect(gating.totalMs.value).toBe(3000)
    vi.advanceTimersByTime(1000)
    expect(gating.remainingMs.value).toBeLessThanOrEqual(2000)
    expect(gating.countdownLabel.value).toMatch(/^\d+s$/)
  })
})
