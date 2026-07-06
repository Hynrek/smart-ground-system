import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ShooterProfilView from '../ShooterProfilView.vue'
import { useAuthStore } from '@/stores/authStore.js'
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
}))

const mountView = () => {
  const auth = useAuthStore()
  auth.profile = { vorname: 'Anna', nachname: 'Muster', username: 'anna' }
  return mount(ShooterProfilView, {
    global: { stubs: { Icons: true } },
  })
}

const clickTab = async (wrapper, label) => {
  const tab = wrapper.findAll('.tab-btn').find((b) => b.text() === label)
  await tab.trigger('click')
  await flushPromises()
}

describe('ShooterProfilView scores tab', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('shows stats header and serie rows from the score store', async () => {
    scoreApi.fetchMyScoreSummary.mockResolvedValue({
      contexts: [
        { context: 'TRAINING', serieCount: 2, totalPoints: 14, maxPoints: 20, averagePercent: 70, bestPercent: 80 },
        { context: 'COMPETITION', serieCount: 0, totalPoints: 0, maxPoints: 0, averagePercent: 0, bestPercent: null },
      ],
      passen: [{ key: 'p1', label: 'Passe X', context: 'TRAINING', serieCount: 2, totalPoints: 14, maxPoints: 20, lastCompletedAt: '2026-07-01T10:00:00Z' }],
      wettkaempfe: [],
    })
    scoreApi.fetchMyScores.mockResolvedValue({
      content: [{
        id: 'a', context: 'TRAINING', serieAlias: 'Serie 1', parentName: 'Passe X',
        rangeName: 'Platz 1', totalPoints: 8, maxPoints: 10, completedAt: '2026-07-01T10:00:00Z',
      }],
      meta: null,
    })
    const wrapper = mountView()

    await clickTab(wrapper, 'Ergebnisse')

    expect(wrapper.text()).toContain('Serie 1')
    expect(wrapper.text()).toContain('8/10')
    expect(wrapper.text()).toContain('70')
  })

  it('switches to grouped Passen view', async () => {
    scoreApi.fetchMyScoreSummary.mockResolvedValue({
      contexts: [],
      passen: [{ key: 'p1', label: 'Passe X', context: 'TRAINING', serieCount: 2, totalPoints: 14, maxPoints: 20, lastCompletedAt: '2026-07-01T10:00:00Z' }],
      wettkaempfe: [],
    })
    scoreApi.fetchMyScores.mockResolvedValue({ content: [], meta: null })
    const wrapper = mountView()

    await clickTab(wrapper, 'Ergebnisse')
    const passenTab = wrapper.find('[data-testid="score-group-passen"]')
    await passenTab.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Passe X')
    expect(wrapper.text()).toContain('14/20')
  })

  it('shows an empty state without scores', async () => {
    scoreApi.fetchMyScoreSummary.mockResolvedValue({ contexts: [], passen: [], wettkaempfe: [] })
    scoreApi.fetchMyScores.mockResolvedValue({ content: [], meta: null })
    const wrapper = mountView()

    await clickTab(wrapper, 'Ergebnisse')

    expect(wrapper.text()).toContain('Noch keine Ergebnisse')
  })
})
