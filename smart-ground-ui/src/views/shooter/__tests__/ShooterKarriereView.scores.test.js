import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ShooterKarriereView from '../ShooterKarriereView.vue'
import * as scoreApi from '@/services/scoreApi.js'

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal()),
  useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
}))
vi.mock('@/services/userApi.js', () => ({
  fetchMyQrToken: vi.fn(),
  rotateMyQrToken: vi.fn(),
  resolveUserByQr: vi.fn(),
}))
vi.mock('@/services/scoreApi.js', () => ({
  fetchMyScores: vi.fn(),
  fetchMyScoreSummary: vi.fn(),
  fetchLeaderboard: vi.fn(),
  fetchMyPassen: vi.fn(),
  fetchMyWettkaempfe: vi.fn(),
}))

const mountView = () =>
  mount(ShooterKarriereView, {
    global: { stubs: { Icons: true } },
  })

const clickTab = async (wrapper, label) => {
  const tab = wrapper.findAll('.tab-btn').find((b) => b.text() === label)
  await tab.trigger('click')
  await flushPromises()
}

const clickScoreGroup = async (wrapper, testid) => {
  const tab = wrapper.find(`[data-testid="${testid}"]`)
  await tab.trigger('click')
  await flushPromises()
}

const emptySummary = { contexts: [], passen: [], wettkaempfe: [] }

const soloStep = (overrides = {}) => ({
  playerId: 'u1',
  serieIndex: 0,
  stepIndex: 0,
  state: 'done',
  pointValue: 1,
  noBirds: 0,
  pointsEarned: 1,
  type: 'solo',
  letter: 'A',
  ...overrides,
})

describe('ShooterKarriereView scores tab', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    scoreApi.fetchMyPassen.mockResolvedValue([])
    scoreApi.fetchMyWettkaempfe.mockResolvedValue([])
  })

  it('shows stats header and serie cards from the score store', async () => {
    scoreApi.fetchMyScoreSummary.mockResolvedValue({
      contexts: [
        { context: 'TRAINING', serieCount: 2, totalPoints: 14, maxPoints: 20, averagePercent: 70, bestPercent: 80 },
        { context: 'COMPETITION', serieCount: 0, totalPoints: 0, maxPoints: 0, averagePercent: 0, bestPercent: null },
      ],
    })
    scoreApi.fetchMyScores.mockResolvedValue({
      content: [{
        id: 'a', context: 'TRAINING', serieAlias: 'Serie 1', parentName: 'Passe X',
        rangeName: 'Platz 1', totalPoints: 8, maxPoints: 10, completedAt: '2026-07-01T10:00:00Z',
        stepStates: [soloStep()],
      }],
      meta: null,
    })
    const wrapper = mountView()
    await flushPromises()

    await clickTab(wrapper, 'Ergebnisse')

    expect(wrapper.text()).toContain('Serie 1')
    expect(wrapper.text()).toContain('8/10')
    expect(wrapper.text()).toContain('70')
  })

  it('shows an empty state without scores', async () => {
    scoreApi.fetchMyScoreSummary.mockResolvedValue(emptySummary)
    scoreApi.fetchMyScores.mockResolvedValue({ content: [], meta: null })
    const wrapper = mountView()
    await flushPromises()

    await clickTab(wrapper, 'Ergebnisse')

    expect(wrapper.text()).toContain('Noch keine Ergebnisse')
  })

  it('Serien tab lists only standalone SERIE rows and expands to clay breakdown', async () => {
    scoreApi.fetchMyScoreSummary.mockResolvedValue(emptySummary)
    scoreApi.fetchMyScores.mockResolvedValue({
      content: [{
        id: 's1', context: 'TRAINING', serieAlias: 'A', totalPoints: 8, maxPoints: 10,
        rangeName: 'Platz 1', completedAt: '2026-07-01T10:00:00Z',
        stepStates: [soloStep()],
      }],
      meta: null,
    })
    const wrapper = mountView()
    await flushPromises()

    await clickTab(wrapper, 'Ergebnisse')
    await clickScoreGroup(wrapper, 'score-group-serien')

    const cards = wrapper.findAllComponents({ name: 'SerieScoreCard' })
    expect(cards).toHaveLength(1)
    expect(wrapper.text()).toContain('A')
    expect(wrapper.text()).toContain('8/10')

    expect(wrapper.findComponent({ name: 'StepScorecard' }).exists()).toBe(false)

    const toggle = wrapper.find('[data-testid="serie-score-toggle"]')
    await toggle.trigger('click')

    expect(wrapper.findComponent({ name: 'StepScorecard' }).exists()).toBe(true)
  })

  it('Passen tab shows groups and drills into child serien', async () => {
    scoreApi.fetchMyScoreSummary.mockResolvedValue(emptySummary)
    scoreApi.fetchMyScores.mockResolvedValue({ content: [], meta: null })
    scoreApi.fetchMyPassen.mockResolvedValue([{
      key: 'p1', label: 'Passe X', serieCount: 1, totalPoints: 14, maxPoints: 20,
      lastCompletedAt: '2026-07-01T10:00:00Z',
      serien: [{
        id: 's1', serieAlias: 'A', totalPoints: 14, maxPoints: 20,
        rangeName: 'Platz 1', completedAt: '2026-07-01T10:00:00Z',
        stepStates: [soloStep()],
      }],
    }])
    const wrapper = mountView()
    await flushPromises()

    await clickTab(wrapper, 'Ergebnisse')
    await clickScoreGroup(wrapper, 'score-group-passen')

    expect(wrapper.text()).toContain('Passe X')
    expect(wrapper.text()).toContain('14/20')
    expect(wrapper.findComponent({ name: 'SerieScoreCard' }).exists()).toBe(false)

    const groupToggle = wrapper.find('[data-testid="passe-group-toggle"]')
    await groupToggle.trigger('click')

    const cards = wrapper.findAllComponents({ name: 'SerieScoreCard' })
    expect(cards).toHaveLength(1)
    expect(wrapper.text()).toContain('A')
  })
})
