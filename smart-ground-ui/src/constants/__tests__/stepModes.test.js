import { describe, it, expect } from 'vitest'
import { stepNotation, stepAriaLabel } from '@/constants/stepModes.js'

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
