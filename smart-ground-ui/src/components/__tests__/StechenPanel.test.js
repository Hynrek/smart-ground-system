import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import StechenPanel from '@/components/competition/StechenPanel.vue'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { usePasseStore } from '@/stores/passeStore.js'

vi.mock('@/components/Icons.vue', () => ({ default: { template: '<span />' } }))

const STUBS = { Icons: true, Badge: { template: '<span><slot /></span>' }, Button: { template: '<button><slot /></button>' } }

const tiedBlock = () => ({
  tiePosition: 1,
  sharedScore: 24,
  resolved: false,
  players: [
    { playerId: 'p1', displayName: 'Anna' },
    { playerId: 'p2', displayName: 'Ben' },
  ],
  rounds: [],
})

const setup = async () => {
  setActivePinia(createPinia())
  const store = useCompetitionEventStore()
  store.tiesBySession = { s1: { sessionId: 's1', tiedBlocks: [tiedBlock()] } }
  vi.spyOn(store, 'startStechen').mockResolvedValue({ id: 'tb1' })

  const passeStore = usePasseStore()
  passeStore.savedSerien = [
    { id: 'se-pub', name: 'Stech-Serie', ownership: 'range', published: true },
    { id: 'se-unpub', name: 'Entwurf', ownership: 'range', published: false },
    { id: 'se-user', name: 'Privat', ownership: 'user', published: true },
  ]
  vi.spyOn(passeStore, 'loadSerienFromStorage').mockResolvedValue()

  const wrapper = mount(StechenPanel, {
    props: { sessionId: 's1' },
    global: { plugins: [], stubs: STUBS },
  })
  await flushPromises()
  return { wrapper, store }
}

describe('StechenPanel — Serie picker', () => {
  beforeEach(() => vi.clearAllMocks())

  it('offers only published range Serien in the start modal', async () => {
    const { wrapper } = await setup()
    await wrapper.find('.tie-block-header button').trigger('click') // "Stechen starten"
    await flushPromises()
    const options = wrapper.findAll('.modal-select option').map(o => o.text())
    expect(options).toContain('Stech-Serie')
    expect(options).not.toContain('Entwurf')
    expect(options).not.toContain('Privat')
  })

  it('starts a Stechen with a templateType-free Serie payload', async () => {
    const { wrapper, store } = await setup()
    await wrapper.find('.tie-block-header button').trigger('click')
    await flushPromises()
    await wrapper.find('.modal-select').setValue('se-pub')
    const startBtn = wrapper.findAll('.modal-actions button').at(-1)
    await startBtn.trigger('click')
    await flushPromises()
    expect(store.startStechen).toHaveBeenCalledWith('s1', {
      playerIds: ['p1', 'p2'],
      templateId: 'se-pub',
      tiePosition: 1,
    })
  })
})
