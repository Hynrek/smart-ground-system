import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePlaySessionStore } from '../playSessionStore'
import { StepType } from '@/constants/playEnums.js'

const players = () => [
  { id: 'A', displayName: 'A' },
  { id: 'B', displayName: 'B' },
  { id: 'C', displayName: 'C' },
  { id: 'D', displayName: 'D' },
]
const oneSoloProg = () => [{ steps: [{ type: StepType.SOLO, positionId: 'p' }] }]

describe('playSessionStore — starter wrap order', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('starts at the marked starter and wraps through the rotation', async () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien(oneSoloProg())
    // starterIndex 2 = C
    await store.startGroupPlay(players(), null, null, null, null, null, null, null, 2)

    expect(store.currentPlayer.id).toBe('C')
    expect(store.nextPlayer.id).toBe('D')
    store.advanceToNextPlayer()
    expect(store.currentPlayer.id).toBe('D')
    expect(store.nextPlayer.id).toBe('A')
    store.advanceToNextPlayer()
    expect(store.currentPlayer.id).toBe('A')
    expect(store.nextPlayer.id).toBe('B')
    store.advanceToNextPlayer()
    expect(store.currentPlayer.id).toBe('B')
    expect(store.nextPlayer).toBe(null)
  })

  it('defaults to index 0 (identity order) when no starter is given', async () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien(oneSoloProg())
    await store.startGroupPlay(players())
    expect(store.currentPlayer.id).toBe('A')
    expect(store.nextPlayer.id).toBe('B')
  })
})
