/* global navigator, AudioContext, clearTimeout, clearInterval, setInterval */
import { ref } from 'vue'

// Tick interval in ms — short enough for responsive detection, testable via fake timers
const TICK_MS = 16

export function useVoiceTrigger(settings) {
  const micLevel     = ref(0)
  const wouldTrigger = ref(false)
  const micDenied    = ref(false)

  let stream       = null
  let audioCtx     = null
  let analyser     = null
  let tickHandle   = null
  let peakStart    = null
  let triggered    = false
  let totzeitTimer = null

  const stopListening = () => {
    if (totzeitTimer) { clearTimeout(totzeitTimer); totzeitTimer = null }
    if (tickHandle)   { clearInterval(tickHandle); tickHandle = null }
    if (stream)       { stream.getTracks().forEach((t) => t.stop()); stream = null }
    if (audioCtx)     { audioCtx.close(); audioCtx = null }
    analyser           = null
    peakStart          = null
    triggered          = false
    micLevel.value     = 0
    wouldTrigger.value = false
  }

  const startAnalysis = (onTrigger, threshold, dauer, preview) => {
    const dataArray = new Uint8Array(analyser.frequencyBinCount)

    const tick = () => {
      if (!analyser) return

      analyser.getByteFrequencyData(dataArray)
      const sumSq = dataArray.reduce((s, v) => s + v * v, 0)
      const rms   = Math.sqrt(sumSq / dataArray.length)
      const level = Math.min(100, Math.round((rms / 128) * 100))
      micLevel.value = level

      if (level >= threshold) {
        if (!peakStart) peakStart = Date.now()
        const held = Date.now() - peakStart
        wouldTrigger.value = held >= dauer
        if (!triggered && held >= dauer) {
          triggered = true
          onTrigger()
          if (preview) {
            // Preview mode: keep listening so the meter can be tested repeatedly
            // instead of cutting the mic like a real trigger would.
            peakStart = null
            triggered = false
          } else {
            stopListening()
          }
          return
        }
      } else {
        peakStart = null
        wouldTrigger.value = false
      }
    }

    // Use setInterval so the loop is controlled by fake timers in tests
    tick() // run immediately on start so micLevel is set synchronously
    tickHandle = setInterval(tick, TICK_MS)
  }

  const startListening = async (onTrigger, overrides = {}) => {
    // Guard against re-entry during an active Totzeit wait
    if (totzeitTimer) { clearTimeout(totzeitTimer); totzeitTimer = null }

    const totzeit = overrides.totzeit !== undefined
      ? overrides.totzeit
      : ('rufTotzeit' in settings ? settings.rufTotzeit : (settings?.value?.rufTotzeit ?? 1000))

    const begin = async () => {
      triggered = false
      micDenied.value = false
      try {
        const threshold = 'rufPeak' in settings ? settings.rufPeak : (settings?.value?.rufPeak ?? 70)
        const dauer     = 'rufDauer' in settings ? settings.rufDauer : (settings?.value?.rufDauer ?? 120)
        stream   = await navigator.mediaDevices.getUserMedia({ audio: true })
        audioCtx = new AudioContext()
        analyser = audioCtx.createAnalyser()
        analyser.fftSize = 256
        const source = audioCtx.createMediaStreamSource(stream)
        source.connect(analyser)
        startAnalysis(onTrigger, threshold, dauer, overrides.preview)
      } catch {
        micDenied.value = true
      }
    }

    if (totzeit > 0) {
      totzeitTimer = setTimeout(begin, totzeit)
    } else {
      await begin()
    }
  }

  return { startListening, stopListening, micLevel, wouldTrigger, micDenied }
}
