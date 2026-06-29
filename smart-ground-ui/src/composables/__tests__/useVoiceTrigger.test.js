/* global navigator */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useVoiceTrigger } from '../useVoiceTrigger'

// --- Shared mock setup ---

let mockGetByteFrequencyData
let mockTrackStop
let mockCtxClose
let mockAnalyser
let mockSource
let mockCtx
let mockStream

const setupMocks = (frequencyData = new Uint8Array(128).fill(0)) => {
  mockGetByteFrequencyData = vi.fn().mockImplementation((arr) => arr.set(frequencyData))
  mockTrackStop = vi.fn()
  mockCtxClose = vi.fn()
  mockAnalyser = {
    fftSize: 256,
    frequencyBinCount: 128,
    connect: vi.fn(),
    getByteFrequencyData: mockGetByteFrequencyData,
  }
  mockSource = { connect: vi.fn() }
  mockCtx = {
    createMediaStreamSource: vi.fn().mockReturnValue(mockSource),
    createAnalyser: vi.fn().mockReturnValue(mockAnalyser),
    close: mockCtxClose,
  }
  mockStream = { getTracks: () => [{ stop: mockTrackStop }] }

  vi.stubGlobal('AudioContext', vi.fn(function () { return mockCtx }))
  vi.stubGlobal('navigator', {
    mediaDevices: {
      getUserMedia: vi.fn().mockResolvedValue(mockStream),
    },
  })
}

describe('useVoiceTrigger', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it('startListening requests microphone access', async () => {
    setupMocks()
    const { startListening } = useVoiceTrigger({ rufPeak: 70, rufDauer: 120, rufTotzeit: 0 })
    await startListening(vi.fn())
    expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({ audio: true })
  })

  it('stopListening stops the mic track and closes the AudioContext', async () => {
    setupMocks()
    const { startListening, stopListening } = useVoiceTrigger({ rufPeak: 70, rufDauer: 120, rufTotzeit: 0 })
    await startListening(vi.fn())
    stopListening()
    expect(mockTrackStop).toHaveBeenCalled()
    expect(mockCtxClose).toHaveBeenCalled()
  })

  it('micLevel is 0 when analyser returns silence', async () => {
    setupMocks(new Uint8Array(128).fill(0))
    const { startListening, micLevel } = useVoiceTrigger({ rufPeak: 70, rufDauer: 120, rufTotzeit: 0 })
    await startListening(vi.fn())
    expect(micLevel.value).toBe(0)
  })

  it('micLevel reflects analyser amplitude', async () => {
    // Fill with 128 → RMS = 128 → normalized = 100
    setupMocks(new Uint8Array(128).fill(128))
    const { startListening, micLevel } = useVoiceTrigger({ rufPeak: 70, rufDauer: 120, rufTotzeit: 0 })
    await startListening(vi.fn())
    expect(micLevel.value).toBeGreaterThan(0)
  })

  it('does NOT call onTrigger when peak is not sustained long enough', async () => {
    // Level will exceed peak threshold
    setupMocks(new Uint8Array(128).fill(200))
    const onTrigger = vi.fn()
    const { startListening } = useVoiceTrigger({ rufPeak: 10, rufDauer: 500, rufTotzeit: 0 })
    await startListening(onTrigger)
    // Advance time less than rufDauer
    vi.advanceTimersByTime(200)
    expect(onTrigger).not.toHaveBeenCalled()
  })

  it('calls onTrigger when peak is sustained for rufDauer', async () => {
    setupMocks(new Uint8Array(128).fill(200))
    const onTrigger = vi.fn()
    const { startListening } = useVoiceTrigger({ rufPeak: 10, rufDauer: 50, rufTotzeit: 0 })
    await startListening(onTrigger)
    vi.advanceTimersByTime(100)
    // setInterval tick fires after rufDauer has elapsed, so trigger should have been called
    expect(onTrigger).toHaveBeenCalledTimes(1)
  })

  it('does not call onTrigger before Totzeit has elapsed', async () => {
    setupMocks(new Uint8Array(128).fill(200))
    const onTrigger = vi.fn()
    const { startListening } = useVoiceTrigger({ rufPeak: 10, rufDauer: 50, rufTotzeit: 2000 })
    startListening(onTrigger) // do not await — Totzeit hasn't elapsed
    vi.advanceTimersByTime(500)
    expect(onTrigger).not.toHaveBeenCalled()
    expect(navigator.mediaDevices.getUserMedia).not.toHaveBeenCalled()
  })

  it('sets micDenied when getUserMedia is rejected', async () => {
    setupMocks()
    navigator.mediaDevices.getUserMedia = vi.fn().mockRejectedValue(new Error('NotAllowedError'))
    const { startListening, micDenied } = useVoiceTrigger({ rufPeak: 70, rufDauer: 120, rufTotzeit: 0 })
    await startListening(vi.fn())
    expect(micDenied.value).toBe(true)
  })

  it('stopListening during Totzeit prevents getUserMedia from being called', async () => {
    setupMocks()
    const onTrigger = vi.fn()
    const { startListening, stopListening } = useVoiceTrigger({ rufPeak: 70, rufDauer: 120, rufTotzeit: 2000 })
    startListening(onTrigger)
    stopListening()
    vi.advanceTimersByTime(3000) // Totzeit would have elapsed
    expect(navigator.mediaDevices.getUserMedia).not.toHaveBeenCalled()
  })
})
