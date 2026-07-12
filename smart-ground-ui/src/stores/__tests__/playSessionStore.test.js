import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePlaySessionStore } from '../playSessionStore'
import { StepType, PartialStep } from '@/constants/playEnums.js'

describe('playSessionStore — releaseIdsForStep', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('returns the single position id for a solo step', () => {
    const store = usePlaySessionStore()
    const step = { type: StepType.SOLO, positionId: 'pos-a' }
    expect(store.releaseIdsForStep(step, null)).toEqual(['pos-a'])
  })

  it('returns both position ids for a pair step', () => {
    const store = usePlaySessionStore()
    const step = { type: StepType.PAIR, positionId1: 'pos-a', positionId2: 'pos-b' }
    expect(store.releaseIdsForStep(step, null)).toEqual(['pos-a', 'pos-b'])
  })

  it('returns the first id for an a_schuss step before the first tap', () => {
    const store = usePlaySessionStore()
    const step = { type: StepType.A_SCHUSS, positionId1: 'pos-a', positionId2: 'pos-b' }
    expect(store.releaseIdsForStep(step, null)).toEqual(['pos-a'])
  })

  it('returns the second id for an a_schuss step after the first tap', () => {
    const store = usePlaySessionStore()
    const step = { type: StepType.A_SCHUSS, positionId1: 'pos-a', positionId2: 'pos-b' }
    expect(store.releaseIdsForStep(step, PartialStep.FIRST)).toEqual(['pos-b'])
  })

  it('returns the single position id for a raffale step', () => {
    const store = usePlaySessionStore()
    const step = { type: StepType.RAFFALE, positionId: 'pos-a' }
    expect(store.releaseIdsForStep(step, null)).toEqual(['pos-a'])
  })

  it('supports the API posId shape as well as positionId', () => {
    const store = usePlaySessionStore()
    const step = { type: StepType.PAIR, posId1: 'pos-a', posId2: 'pos-b' }
    expect(store.releaseIdsForStep(step, null)).toEqual(['pos-a', 'pos-b'])
  })

  it('returns an empty array for a null step', () => {
    const store = usePlaySessionStore()
    expect(store.releaseIdsForStep(null, null)).toEqual([])
  })
})
