// src/components/__tests__/ShooterPlayPageNextShooter.test.js
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ShooterPlayPage from '@/views/shooter/ShooterPlayPage.vue'
import { usePlaySessionStore } from '@/stores/playSessionStore.js'
import { StepType } from '@/constants/playEnums.js'

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
vi.mock('@/components/shooter/ScoreTable.vue', () => ({
  default: {
    name: 'ScoreTable',
    props: ['stepStates', 'program', 'players', 'editable'],
    emits: ['correct-step'],
    template: '<div class="score-table-stub" />',
  },
}))

const mockProgram = [
  { steps: [{ type: StepType.SOLO, letter: 'A', positionId: 'pos-1' }] },
]
const mockPlayers = [
  { id: 'p1', displayName: 'Alice' },
  { id: 'p2', displayName: 'Max Mustermann' },
]

const makeRouter = () =>
  createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }],
  })

// Mounts ShooterPlayPage in the group-setup modal with a staged serie, ready to
// trigger beginGroupPlay via the "Starten" button. `players` controls multi/single.
const mountAtGroupSetup = async (players = mockPlayers) => {
  const router = makeRouter()
  await router.push('/remote/r1/play')
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = usePlaySessionStore()

  store.showGroupSetup = true
  store.pendingGroupSerien = mockProgram
  store.pendingPasseInfo = {
    serie: mockProgram[0],
    players: players.map((p) => ({ id: p.id, displayName: p.displayName })),
  }

  const wrapper = mount(ShooterPlayPage, {
    props: { rangeId: 'r1' },
    global: { plugins: [router, pinia] },
  })
  await nextTick()
  // Trigger the real beginGroupPlay flow via the modal's "Starten" button.
  await wrapper.find('.gsm-btn--primary').trigger('click')
  await nextTick()
  return { wrapper, store }
}

// Mounts ShooterPlayPage in an active multi-player session, at program end
// (isAtProgramEnd = true, nextPlayer set to p2), so the Getroffen card is visible.
const mountAtProgramEnd = async () => {
  const router = makeRouter()
  await router.push('/remote/r1/play')
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = usePlaySessionStore()

  store.playProg = mockProgram
  store.showGroupSetup = false
  store.playComplete = false
  store.sessionPlayers = mockPlayers          // length > 1 → isMultiPlayer = true
  store.roundOrder = [0, 1]                   // needed for currentPlayer computed
  store.currentPlayerIndex = 0
  store.currentSerieIndex = 0
  store.currentStepIndex = 1                  // past end of 1-step program → isAtProgramEnd = true
  store.playScore = { totalPoints: 0, stepStates: [] }
  store.advanceToNextPlayer = vi.fn()
  store.confirmComplete = vi.fn()
  store.closePlayback = vi.fn()

  const wrapper = mount(ShooterPlayPage, {
    props: { rangeId: 'r1' },
    global: { plugins: [router, pinia] },
  })
  await nextTick()
  return { wrapper, store }
}

describe('ShooterPlayPage — Nächster Schütze overlay', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('overlay is not visible during normal play', async () => {
    const { wrapper } = await mountAtProgramEnd()
    expect(wrapper.find('.next-shooter-overlay').exists()).toBe(false)
  })

  it('tapping the Getroffen card in multi-player shows the overlay instead of advancing', async () => {
    const { wrapper, store } = await mountAtProgramEnd()
    await wrapper.find('.getroffen-card').trigger('click')
    await nextTick()
    expect(wrapper.find('.next-shooter-overlay').exists()).toBe(true)
    expect(store.advanceToNextPlayer).not.toHaveBeenCalled()
  })

  it('overlay displays the next shooter name', async () => {
    const { wrapper } = await mountAtProgramEnd()
    await wrapper.find('.getroffen-card').trigger('click')
    await nextTick()
    expect(wrapper.find('.next-shooter-overlay').text()).toContain('Max Mustermann')
  })

  it('tapping Starten calls advanceToNextPlayer and hides the overlay', async () => {
    const { wrapper, store } = await mountAtProgramEnd()
    await wrapper.find('.getroffen-card').trigger('click')
    await nextTick()
    await wrapper.find('.next-shooter-start-btn').trigger('click')
    await nextTick()
    expect(store.advanceToNextPlayer).toHaveBeenCalledOnce()
    expect(wrapper.find('.next-shooter-overlay').exists()).toBe(false)
  })

  it('shows the ready overlay for the FIRST shooter on group start', async () => {
    const { wrapper, store } = await mountAtGroupSetup()
    const advanceSpy = vi.spyOn(store, 'advanceToNextPlayer')

    expect(wrapper.find('.next-shooter-overlay').exists()).toBe(true)
    // Overlay names the first (current) shooter, not the next one.
    expect(wrapper.find('.next-shooter-name').text()).toContain('Alice')

    await wrapper.find('.next-shooter-start-btn').trigger('click')
    await nextTick()

    expect(wrapper.find('.next-shooter-overlay').exists()).toBe(false)
    // The first shooter must not be skipped.
    expect(advanceSpy).not.toHaveBeenCalled()
    expect(store.currentPlayer?.displayName).toBe('Alice')
  })

  it('does NOT show the ready overlay on start for a single-player session', async () => {
    const { wrapper } = await mountAtGroupSetup([mockPlayers[0]])
    expect(wrapper.find('.next-shooter-overlay').exists()).toBe(false)
  })
})
