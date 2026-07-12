import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { useCompetitionProgress } from '@/composables/useCompetitionProgress.js'

const makeInstance = () => ({
  instanceId: 'inst-1',
  type: 'competition',
  rotten: [
    {
      rotteId: 'r1',
      name: 'Rotte A',
      currentPhaseIndex: 0,
      phases: [
        {
          phaseIndex: 0,
          passeName: 'Passe 1',
          status: 'active',
          blocks: [
            { blockId: 'b1', serieAlias: 'Serie 1', status: 'pending', result: null },
            { blockId: 'b2', serieAlias: 'Serie 2', status: 'pending', result: null },
          ],
        },
        {
          phaseIndex: 1,
          passeName: 'Passe 2',
          status: 'pending',
          blocks: [
            { blockId: 'b3', serieAlias: 'Serie 1', status: 'pending', result: null },
          ],
        },
      ],
    },
    {
      rotteId: 'r2',
      name: 'Rotte B',
      currentPhaseIndex: 0,
      phases: [
        {
          phaseIndex: 0,
          passeName: 'Passe 1',
          status: 'active',
          blocks: [
            { blockId: 'b4', serieAlias: 'Serie 1', status: 'in_progress', result: null },
            { blockId: 'b5', serieAlias: 'Serie 2', status: 'pending', result: null },
          ],
        },
        {
          phaseIndex: 1,
          passeName: 'Passe 2',
          status: 'pending',
          blocks: [
            { blockId: 'b6', serieAlias: 'Serie 1', status: 'pending', result: null },
          ],
        },
      ],
    },
  ],
})

describe('useCompetitionProgress', () => {
  it('returns empty arrays when instance is null', () => {
    const instance = ref(null)
    const { passenProgress, serieCards, leaderboard } = useCompetitionProgress(instance)
    expect(passenProgress.value).toEqual([])
    expect(serieCards.value).toEqual([])
    expect(leaderboard.value).toEqual([])
  })

  it('activePhaseIndex defaults to 0 when instance is null', () => {
    const instance = ref(null)
    const { activePhaseIndex } = useCompetitionProgress(instance)
    expect(activePhaseIndex.value).toBe(0)
  })

  it('passenProgress: active phase gets status aktiv', () => {
    const instance = ref(makeInstance())
    const { passenProgress } = useCompetitionProgress(instance)
    expect(passenProgress.value[0]).toMatchObject({ passeName: 'Passe 1', status: 'aktiv' })
  })

  it('passenProgress: unreached phase gets status offen', () => {
    const instance = ref(makeInstance())
    const { passenProgress } = useCompetitionProgress(instance)
    expect(passenProgress.value[1]).toMatchObject({ passeName: 'Passe 2', status: 'offen' })
  })

  it('passenProgress: phase is fertig when all rotten have phases[i].status === done', () => {
    const inst = makeInstance()
    inst.rotten[0].phases[0].status = 'done'
    inst.rotten[0].currentPhaseIndex = 1
    inst.rotten[1].phases[0].status = 'done'
    inst.rotten[1].currentPhaseIndex = 1
    const instance = ref(inst)
    const { passenProgress } = useCompetitionProgress(instance)
    expect(passenProgress.value[0].status).toBe('fertig')
    expect(passenProgress.value[1].status).toBe('aktiv')
  })

  it('activePhaseIndex is the lowest phase not fully done across all rotten', () => {
    const inst = makeInstance()
    inst.rotten[0].phases[0].status = 'done'
    inst.rotten[0].currentPhaseIndex = 1
    inst.rotten[1].phases[0].status = 'done'
    inst.rotten[1].currentPhaseIndex = 1
    const instance = ref(inst)
    const { activePhaseIndex } = useCompetitionProgress(instance)
    expect(activePhaseIndex.value).toBe(1)
  })

  it('uses the instance releasedPasseIndex as the active phase (admin gate)', () => {
    const inst = makeInstance()
    // Both Rotten finished Passe 1, but the admin has not released Passe 2 yet.
    inst.releasedPasseIndex = 0
    inst.rotten[0].phases[0].status = 'done'
    inst.rotten[1].phases[0].status = 'done'
    const instance = ref(inst)
    const { activePhaseIndex, passenProgress } = useCompetitionProgress(instance)
    expect(activePhaseIndex.value).toBe(0)
    // Passe 1 is done-by-all → fertig; the gate keeps it as the active index but
    // the next passe stays offen until released.
    expect(passenProgress.value[1].status).toBe('offen')
  })

  it('serieCards: one card per block in the active phase, with a row per rotte', () => {
    const instance = ref(makeInstance())
    const { serieCards } = useCompetitionProgress(instance)
    expect(serieCards.value).toHaveLength(2)
    expect(serieCards.value[0].serieAlias).toBe('Serie 1')
    expect(serieCards.value[0].rotteRows).toHaveLength(2)
    expect(serieCards.value[0].rotteRows[0]).toMatchObject({ rotteName: 'Rotte A', status: 'pending' })
    expect(serieCards.value[0].rotteRows[1]).toMatchObject({ rotteName: 'Rotte B', status: 'in_progress' })
  })

  it('leaderboard is empty when no blocks are done', () => {
    const instance = ref(makeInstance())
    const { leaderboard } = useCompetitionProgress(instance)
    expect(leaderboard.value).toEqual([])
  })

  it('leaderboard aggregates playerResults from all done blocks', () => {
    const inst = makeInstance()
    inst.rotten[0].phases[0].blocks[0].status = 'done'
    inst.rotten[0].phases[0].blocks[0].result = {
      playerResults: [
        { playerId: 'p1', displayName: 'Alice', totalPoints: 10, maxPoints: 12 },
        { playerId: 'p2', displayName: 'Bob', totalPoints: 8, maxPoints: 12 },
      ],
    }
    inst.rotten[1].phases[0].blocks[0].status = 'done'
    inst.rotten[1].phases[0].blocks[0].result = {
      playerResults: [
        { playerId: 'p1', displayName: 'Alice', totalPoints: 6, maxPoints: 12 },
        { playerId: 'p2', displayName: 'Bob', totalPoints: 9, maxPoints: 12 },
      ],
    }
    const instance = ref(inst)
    const { leaderboard } = useCompetitionProgress(instance)
    expect(leaderboard.value).toHaveLength(2)
    expect(leaderboard.value[0]).toMatchObject({ displayName: 'Bob', totalPoints: 17, maxPoints: 24 })
    expect(leaderboard.value[1]).toMatchObject({ displayName: 'Alice', totalPoints: 16, maxPoints: 24 })
  })

  it('leaderboard is sorted descending by totalPoints', () => {
    const inst = makeInstance()
    inst.rotten[0].phases[0].blocks[0].status = 'done'
    inst.rotten[0].phases[0].blocks[0].result = {
      playerResults: [
        { playerId: 'p1', displayName: 'Alice', totalPoints: 5, maxPoints: 10 },
        { playerId: 'p2', displayName: 'Bob', totalPoints: 9, maxPoints: 10 },
      ],
    }
    const instance = ref(inst)
    const { leaderboard } = useCompetitionProgress(instance)
    expect(leaderboard.value[0].displayName).toBe('Bob')
    expect(leaderboard.value[1].displayName).toBe('Alice')
  })
})
