import { describe, it, expect } from 'vitest'
import { stepNotation, stepAriaLabel, stepFailCells } from '@/constants/stepModes.js'
import { StepType } from '@/constants/playEnums.js'

describe('stepNotation — deleted-position placeholder', () => {
  it('renders an em dash for a missing solo letter (deleted position)', () => {
    expect(stepNotation({ type: 'solo', letter: null })).toBe('—')
    expect(stepNotation({ type: 'solo', letter: null })).not.toContain('?')
  })

  it('renders an em dash for a missing side of a pair', () => {
    expect(stepNotation({ type: 'pair', letter1: 'A', letter2: null })).toBe('A + —')
  })

  it('still renders present letters normally', () => {
    expect(stepNotation({ type: 'a_schuss', letter1: 'A', letter2: 'B' })).toBe('A → B')
    expect(stepNotation({ type: 'raffale', letter: 'C' })).toBe('C×2')
  })
})

describe('stepAriaLabel', () => {
  it('describes a normal solo step in German', () => {
    expect(stepAriaLabel({ type: 'solo', letter: 'A' })).toBe('Solo Position A')
  })

  it('names a deleted position', () => {
    expect(stepAriaLabel({ type: 'solo', letter: null })).toBe('Solo gelöschte Position')
  })

  it('describes a pair with one deleted side', () => {
    expect(stepAriaLabel({ type: 'pair', letter1: 'A', letter2: null }))
      .toBe('Pair Position A und gelöschte Position')
  })
})

describe('stepFailCells', () => {
  it('returns letter cells for a pair', () => {
    const cells = stepFailCells({ type: StepType.PAIR, letter1: 'A', letter2: 'B' })
    expect(cells).toEqual([
      { failType: 'a', label: 'A', cost: 1 },
      { failType: 'b', label: 'B', cost: 1 },
      { failType: 'both', label: 'A + B', cost: 2 },
    ])
  })

  it('returns letter cells for a.Schuss', () => {
    const cells = stepFailCells({ type: StepType.A_SCHUSS, letter1: 'A', letter2: 'C' })
    expect(cells.map((c) => c.label)).toEqual(['A', 'C', 'A + C'])
  })

  it('returns shot-indexed cells for raffale', () => {
    const cells = stepFailCells({ type: StepType.RAFFALE, letter: 'A' })
    expect(cells).toEqual([
      { failType: 'a', label: 'A1', cost: 1 },
      { failType: 'b', label: 'A2', cost: 1 },
      { failType: 'both', label: 'A×2', cost: 2 },
    ])
  })

  it('returns an empty array for a null step', () => {
    expect(stepFailCells(null)).toEqual([])
  })
})
