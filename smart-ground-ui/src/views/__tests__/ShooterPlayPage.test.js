import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ShooterPlayPage from '../shooter/ShooterPlayPage.vue'
import { usePlaySessionStore } from '@/stores/playSessionStore.js'
import { StepState, StepType } from '@/constants/playEnums.js'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: {} }),
}))
vi.mock('@/stores/passeStore.js', () => ({
  usePasseStore: () => ({ savedPassen: [] }),
}))
vi.mock('@/services/rangePositionApi.js', () => ({
  sendPositionCommand: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('@/services/userApi.js', () => ({
  fetchMyQrToken: vi.fn(),
  rotateMyQrToken: vi.fn(),
  resolveUserByQr: vi.fn(),
  fetchMyPlayResults: vi.fn(),
}))

const seedPlay = (store, step, state) => {
  store.sessionPlayers = [{ id: 'p1', displayName: 'Alice' }]
  store.roundOrder = [0]
  store.currentPlayerIndex = 0
  store.playProg = [{ steps: [step] }]
  store.currentSerieIndex = 0
  store.currentStepIndex = 1 // advanced past the (single) step → it is "last fired"
  store.playScore = {
    totalPoints: state === StepState.DONE ? step.pointValue : 0,
    stepStates: [
      {
        playerId: 'p1', serieIndex: 0, stepIndex: 0,
        state, pointValue: step.pointValue,
        pointsEarned: state === StepState.DONE ? step.pointValue : 0,
        noBirds: 0, corrected: false, originalState: null,
      },
    ],
  }
  store.playLastDeviceStep = { serieIdx: 0, stepIdx: 0 }
}

const mountPage = () =>
  mount(ShooterPlayPage, {
    props: { rangeId: 'r1' },
    global: { stubs: { Icons: true } },
  })

const soloStep = { type: StepType.SOLO, letter: 'A', alias: 'LED 1', pointValue: 1 }
const pairStep = { type: StepType.PAIR, letter1: 'A', letter2: 'B', pointValue: 2 }

describe('ShooterPlayPage action bar', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('renders Fail, Treffer and No Bird buttons', () => {
    const store = usePlaySessionStore()
    seedPlay(store, pairStep, StepState.DONE)
    const wrapper = mountPage()
    const labels = wrapper.findAll('.action-bar .btn-label').map((n) => n.text())
    expect(labels).toEqual(['Fail', 'Treffer', 'No Bird'])
  })

  it('disables Treffer when the last step is a full hit', () => {
    const store = usePlaySessionStore()
    seedPlay(store, pairStep, StepState.DONE)
    const wrapper = mountPage()
    const treffer = wrapper.get('.btn-hit-action')
    expect(treffer.attributes('disabled')).toBeDefined()
  })

  it('enables Treffer after an accidental fail and reverts on click', async () => {
    const store = usePlaySessionStore()
    seedPlay(store, pairStep, StepState.FAILED_BOTH)
    const wrapper = mountPage()
    const treffer = wrapper.get('.btn-hit-action')
    expect(treffer.attributes('disabled')).toBeUndefined()

    await treffer.trigger('click')

    expect(store.playScore.stepStates[0].state).toBe(StepState.DONE)
    expect(store.playScore.stepStates[0].pointsEarned).toBe(2)
  })

  it('opens the fail flyout for a double', async () => {
    const store = usePlaySessionStore()
    seedPlay(store, pairStep, StepState.DONE)
    const wrapper = mountPage()
    expect(wrapper.find('.fail-sheet').exists()).toBe(false)

    await wrapper.findAll('.action-btn')[0].trigger('click')

    expect(wrapper.find('.fail-sheet').exists()).toBe(true)
    const cells = wrapper.findAll('.fail-cell .fail-cell-label').map((n) => n.text())
    expect(cells).toEqual(['A', 'B', 'A + B'])
  })

  it('fails a solo step immediately without opening the flyout', async () => {
    const store = usePlaySessionStore()
    seedPlay(store, soloStep, StepState.DONE)
    const failSpy = vi.spyOn(store, 'failStep')
    const wrapper = mountPage()

    await wrapper.findAll('.action-btn')[0].trigger('click')

    expect(wrapper.find('.fail-sheet').exists()).toBe(false)
    expect(failSpy).toHaveBeenCalledWith('a')
  })
})

describe('ShooterPlayPage group setup — reorder & starter', () => {
  beforeEach(() => setActivePinia(createPinia()))

  const openSetup = (store) => {
    store.setPendingGroupSerien([{ steps: [soloStep] }])
    return mountPage()
  }

  it('marks the first shooter as starter by default and passes starterIndex 0', async () => {
    const store = usePlaySessionStore()
    const startSpy = vi.spyOn(store, 'startGroupPlay').mockResolvedValue(undefined)
    const wrapper = openSetup(store)
    await wrapper.get('.add-player-btn').trigger('click') // 2 shooters now
    await wrapper.get('.modal-actions .btn-primary').trigger('click')
    const starterIndex = startSpy.mock.calls[0].at(-1)
    expect(starterIndex).toBe(0)
  })

  it('moves a shooter up and keeps the starter marker on the person', async () => {
    const store = usePlaySessionStore()
    const startSpy = vi.spyOn(store, 'startGroupPlay').mockResolvedValue(undefined)
    const wrapper = openSetup(store)
    await wrapper.get('.add-player-btn').trigger('click') // Schütze 1, Schütze 2

    // Mark the second shooter as starter
    await wrapper.findAll('.player-star-btn')[1].trigger('click')
    // Move the second shooter up to row 1
    await wrapper.findAll('.player-move-up')[1].trigger('click')
    await wrapper.get('.modal-actions .btn-primary').trigger('click')

    const args = startSpy.mock.calls[0]
    const passedPlayers = args[0]
    const starterIndex = args.at(-1)
    expect(passedPlayers[0].displayName).toBe('Schütze 2')
    expect(starterIndex).toBe(0)
  })
})

describe('ShooterPlayPage — Serie Anschauen preview', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shows the Serie anschauen button in group setup', () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien([{ steps: [soloStep] }])
    const wrapper = mountPage()
    const labels = wrapper.findAll('.modal-actions .btn').map((n) => n.text())
    expect(labels).toContain('Serie anschauen')
  })

  it('renders the preview screen and Überspringen calls skipPreviewStep', async () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien([{ steps: [soloStep, pairStep] }])
    store.startPreview()
    const skipSpy = vi.spyOn(store, 'skipPreviewStep')
    const wrapper = mountPage()
    expect(wrapper.find('.preview-page').exists()).toBe(true)
    await wrapper.get('.btn-skip').trigger('click')
    expect(skipSpy).toHaveBeenCalled()
  })

  it('gates the first shooter at an unseen step and does not release on tap', async () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien([{ steps: [soloStep] }])
    store.startPreview()
    store.stopPreview() // engaged, frontier 0

    store.showGroupSetup = false // startGroupPlay normally clears this; we bypass it
    store.playProg = [{ steps: [soloStep] }]
    store.sessionPlayers = [{ id: 'A', displayName: 'A' }, { id: 'B', displayName: 'B' }]
    store.roundOrder = [0, 1]
    store.currentPlayerIndex = 0
    store.currentSerieIndex = 0
    store.currentStepIndex = 0

    const advanceSpy = vi.spyOn(store, 'advancePlayStep')
    const wrapper = mountPage()
    expect(wrapper.find('.preview-gate-overlay').exists()).toBe(true)
    await wrapper.get('.step-card').trigger('click')
    expect(advanceSpy).not.toHaveBeenCalled()
  })

  it('cancels the pending preview raffale timeout when preview is stopped', async () => {
    vi.useFakeTimers()
    try {
      const store = usePlaySessionStore()
      store.setPendingGroupSerien([{ steps: [pairStep] }])
      store.startPreview()
      const completeSpy = vi.spyOn(store, 'completePreviewRaffaleStep')
      mountPage()

      store.previewRaffaleStarted = true
      await vi.advanceTimersByTimeAsync(500) // timer pending, not yet fired

      store.stopPreview() // shooter hits Stop mid-flight
      await vi.advanceTimersByTimeAsync(1000) // let the original 1s deadline pass

      expect(completeSpy).not.toHaveBeenCalled()
    } finally {
      vi.useRealTimers()
    }
  })

  it('cancels the pending preview raffale timeout on unmount', async () => {
    vi.useFakeTimers()
    try {
      const store = usePlaySessionStore()
      store.setPendingGroupSerien([{ steps: [pairStep] }])
      store.startPreview()
      const completeSpy = vi.spyOn(store, 'completePreviewRaffaleStep')
      const wrapper = mountPage()

      store.previewRaffaleStarted = true
      await vi.advanceTimersByTimeAsync(500) // timer pending, not yet fired

      wrapper.unmount()
      await vi.advanceTimersByTimeAsync(1000) // let the original 1s deadline pass

      expect(completeSpy).not.toHaveBeenCalled()
    } finally {
      vi.useRealTimers()
    }
  })
})
