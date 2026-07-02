import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ShooterPlayPage from '../shooter/ShooterPlayPage.vue'
import { usePlaySessionStore } from '@/stores/playSessionStore.js'
import { StepState, StepType } from '@/constants/playEnums.js'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
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
