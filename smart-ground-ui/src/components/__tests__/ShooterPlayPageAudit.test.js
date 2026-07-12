import { describe, it, expect, vi } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ShooterPlayPage from '@/views/shooter/ShooterPlayPage.vue'
import { usePlaySessionStore } from '@/stores/playSessionStore.js'
import { StepState, StepType } from '@/constants/playEnums.js'

vi.mock('@/stores/passeStore.js', () => ({
  usePasseStore: () => ({
    savedPassen: [{ id: 'passe-1', name: 'Test', serien: [{ steps: [] }] }],
  }),
}))
vi.mock('@/services/rangePositionApi.js', () => ({
  sendPositionCommand: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('@/components/Icons.vue', () => ({
  default: { name: 'Icons', props: ['icon', 'size', 'color'], template: '<span />' },
}))
// Stub ScoreTable so we can emit correct-step directly from the component instance
vi.mock('@/components/shooter/ScoreTable.vue', () => ({
  default: {
    name: 'ScoreTable',
    props: ['stepStates', 'program', 'players', 'editable'],
    emits: ['correct-step'],
    template: '<div class="score-table-stub" />',
  },
}))

const mockProgram = [{ steps: [{ type: StepType.SOLO, letter: 'A', positionId: 'pos-1' }] }]
const mockPlayers = [{ id: 'p1', displayName: 'Alice' }]
const mockStepStates = [
  { playerId: 'p1', serieIndex: 0, stepIndex: 0, state: StepState.DONE,
    pointValue: 1, pointsEarned: 1, noBirds: 0, corrected: false, originalState: null },
]

const makeRouter = () =>
  createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }],
  })

// Mounts ShooterPlayPage showing the final score screen in competition mode.
// pendingPasseInfo with instanceType 'competition' sets _blockContext (_isCompetitionMode = true)
// during script setup. After mount we force playComplete = true to show the final screen.
const mountFinalScreen = async () => {
  const router = makeRouter()
  await router.push('/remote/r1/play')
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = usePlaySessionStore()

  // Pre-set playProg so the redirect guard at top of setup does NOT fire
  store.playProg = mockProgram

  // pendingPasseInfo sets _blockContext (instanceType: 'competition') during setup
  store.pendingPasseInfo = {
    passeId: 'passe-1',
    rangeId: 'r1',
    instanceId: 'inst-1',
    blockId: 'blk-1',
    rotteId: null,
    instanceType: 'competition',
    rotteName: 'Rotte 1',
    serieName: 'Serie A',
    serie: { steps: [] },
    players: mockPlayers,
  }

  const wrapper = mount(ShooterPlayPage, {
    props: { rangeId: 'r1' },
    global: { plugins: [router, pinia] },
  })

  // Script setup has now consumed pendingPasseInfo; force the final screen state
  store.showGroupSetup = false
  store.playComplete = true
  store.sessionPlayers = mockPlayers
  store.playScore = { totalPoints: 1, stepStates: mockStepStates }
  store.playerConfirmations = new Map([['p1', false]])
  await nextTick()

  return { wrapper, store }
}

describe('ShooterPlayPage — audit checkboxes', () => {
  it('audit section is visible in competition mode on final screen', async () => {
    const { wrapper } = await mountFinalScreen()
    expect(wrapper.find('.audit-section').exists()).toBe(true)
  })

  it('Beenden button is disabled when playerConfirmations has an unconfirmed player', async () => {
    const { wrapper } = await mountFinalScreen()
    // store.allPlayersConfirmed is false (p1 not confirmed) so Beenden should be disabled
    const beenden = wrapper.findAll('button').find((b) => b.text() === 'Beenden')
    expect(beenden.attributes('disabled')).toBeDefined()
  })

  it('ticking a checkbox calls store.confirmPlayer with the player id', async () => {
    const { wrapper, store } = await mountFinalScreen()
    store.confirmPlayer = vi.fn()
    const checkbox = wrapper.find('.audit-checkbox')
    // Simulate checking the checkbox
    await checkbox.setValue(true)
    expect(store.confirmPlayer).toHaveBeenCalledWith('p1')
  })

  it('unticking a checkbox calls store.unconfirmPlayer with the player id', async () => {
    const { wrapper, store } = await mountFinalScreen()
    // Start with confirmed
    store.playerConfirmations = new Map([['p1', true]])
    store.unconfirmPlayer = vi.fn()
    await nextTick()
    const checkbox = wrapper.find('.audit-checkbox')
    await checkbox.setValue(false)
    expect(store.unconfirmPlayer).toHaveBeenCalledWith('p1')
  })
})

describe('ShooterPlayPage — correction picker', () => {
  it('correction picker is hidden by default on final screen', async () => {
    const { wrapper } = await mountFinalScreen()
    expect(wrapper.find('.correction-overlay').exists()).toBe(false)
  })

  it('picker appears after ScoreTable emits correct-step', async () => {
    const { wrapper } = await mountFinalScreen()
    const scoreTable = wrapper.findComponent({ name: 'ScoreTable' })
    await scoreTable.vm.$emit('correct-step', {
      playerId: 'p1', serieIndex: 0, stepIndex: 0, currentState: StepState.DONE,
    })
    await nextTick()
    expect(wrapper.find('.correction-overlay').exists()).toBe(true)
  })

  it('clicking the backdrop dismisses picker and does not call correctStep', async () => {
    const { wrapper, store } = await mountFinalScreen()
    store.correctStep = vi.fn()
    const scoreTable = wrapper.findComponent({ name: 'ScoreTable' })
    await scoreTable.vm.$emit('correct-step', {
      playerId: 'p1', serieIndex: 0, stepIndex: 0, currentState: StepState.DONE,
    })
    await nextTick()
    await wrapper.find('.correction-overlay').trigger('click')
    await nextTick()
    expect(wrapper.find('.correction-overlay').exists()).toBe(false)
    expect(store.correctStep).not.toHaveBeenCalled()
  })

  it('clicking a picker button calls store.correctStep and closes the picker', async () => {
    const { wrapper, store } = await mountFinalScreen()
    store.correctStep = vi.fn()
    const scoreTable = wrapper.findComponent({ name: 'ScoreTable' })
    await scoreTable.vm.$emit('correct-step', {
      playerId: 'p1', serieIndex: 0, stepIndex: 0, currentState: StepState.FAILED_BOTH,
    })
    await nextTick()
    await wrapper.find('.picker-btn.btn-getroffen').trigger('click')
    await nextTick()
    expect(store.correctStep).toHaveBeenCalledWith('p1', 0, 0, StepState.DONE)
    expect(wrapper.find('.correction-overlay').exists()).toBe(false)
  })
})
