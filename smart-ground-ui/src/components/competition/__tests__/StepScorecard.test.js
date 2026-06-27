import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StepScorecard from '../StepScorecard.vue'

const serien = [
  { key: '0:se1', passeIndex: 0, rangeName: 'Stand 1', serieName: 'Morgen', sortIndex: 0, steps: [
    { stepIndex: 0, type: 'solo', letter: 'A', letter1: null, letter2: null, state: 'done' },
    { stepIndex: 1, type: 'pair', letter: null, letter1: 'B', letter2: 'D', state: 'failed-a' },
  ] },
  { key: '1:se2', passeIndex: 1, rangeName: null, serieName: 'Abend', sortIndex: 1, steps: [
    { stepIndex: 0, type: 'pair', letter: null, letter1: 'E', letter2: 'F', state: 'pending' },
  ] },
]

describe('StepScorecard', () => {
  it('renders a header per Serie as "RangeName – SerieName" (or just name)', () => {
    const wrapper = mount(StepScorecard, { props: { serien } })
    const headers = wrapper.findAll('.serie-label').map(n => n.text())
    expect(headers[0]).toBe('Stand 1 – Morgen')
    expect(headers[1]).toBe('Abend')
  })

  it('renders a solid chip for a solo step, colored by state', () => {
    const wrapper = mount(StepScorecard, { props: { serien } })
    const solo = wrapper.findAll('.step-chip')[0]
    expect(solo.classes()).toContain('is-done')
    expect(solo.text()).toContain('A')
    expect(solo.find('.half').exists()).toBe(false)
  })

  it('renders a split chip for a doublette, coloring each half by target', () => {
    const wrapper = mount(StepScorecard, { props: { serien } })
    // second chip = pair B/D, failed-a → left (B) miss, right (D) hit
    const split = wrapper.findAll('.step-chip')[1]
    const halves = split.findAll('.half')
    expect(halves).toHaveLength(2)
    expect(halves[0].text()).toBe('B')
    expect(halves[0].classes()).toContain('half--fail')
    expect(halves[1].text()).toBe('D')
    expect(halves[1].classes()).toContain('half--done')
  })

  it('shows the mode notation glyph on split chips, matching the Shooter Play view', () => {
    const typed = [
      { key: '0:t', passeIndex: 0, rangeName: null, serieName: 'Typen', sortIndex: 0, steps: [
        { stepIndex: 0, type: 'pair', letter: null, letter1: 'A', letter2: 'B', state: 'pending' },
        { stepIndex: 1, type: 'a_schuss', letter: null, letter1: 'C', letter2: 'D', state: 'pending' },
        { stepIndex: 2, type: 'raffale', letter: 'E', letter1: null, letter2: null, state: 'pending' },
      ] },
    ]
    const wrapper = mount(StepScorecard, { props: { serien: typed } })
    const [pair, aschuss, raffale] = wrapper.findAll('.step-chip')

    // pair "A + B", glyph tinted in the Pair mode hue
    expect(pair.find('.step-chip__op').text()).toBe('+')
    expect(pair.find('.step-chip__op').attributes('style')).toContain('--mode-glyph: #378ADD')
    expect(pair.findAll('.half').map(h => h.text())).toEqual(['A', 'B'])

    // a.Schuss "C → D"
    expect(aschuss.find('.step-chip__op').text()).toBe('→')
    expect(aschuss.findAll('.half').map(h => h.text())).toEqual(['C', 'D'])

    // raffale "E ×2" — one trap, repeat token instead of a connector / duplicate letter
    expect(raffale.find('.step-chip__op').exists()).toBe(false)
    expect(raffale.findAll('.half').map(h => h.text())).toEqual(['E', '×2'])
  })

  it('renders pending halves grey', () => {
    const wrapper = mount(StepScorecard, { props: { serien } })
    const pendingChip = wrapper.findAll('.step-chip')[2] // E/F pending
    const halves = pendingChip.findAll('.half')
    expect(halves[0].classes()).toContain('half--pending')
    expect(halves[1].classes()).toContain('half--pending')
  })

  it('gives every chip a non-empty aria-label', () => {
    const wrapper = mount(StepScorecard, { props: { serien } })
    for (const chip of wrapper.findAll('.step-chip')) {
      expect(chip.attributes('aria-label')).toBeTruthy()
    }
  })

  it('renders nothing when there are no Serien', () => {
    const wrapper = mount(StepScorecard, { props: { serien: [] } })
    expect(wrapper.find('.step-chip').exists()).toBe(false)
  })

  it('editable mode emits correct-step with the step identity on chip click', async () => {
    const wrapper = mount(StepScorecard, { props: { serien, editable: true } })
    await wrapper.findAll('.step-chip')[0].trigger('click')
    expect(wrapper.emitted('correct-step')).toBeTruthy()
    expect(wrapper.emitted('correct-step')[0][0]).toMatchObject({ stepIndex: 0, currentState: 'done' })
  })

  it('non-editable chips do not emit on click', async () => {
    const wrapper = mount(StepScorecard, { props: { serien } })
    await wrapper.findAll('.step-chip')[0].trigger('click')
    expect(wrapper.emitted('correct-step')).toBeFalsy()
  })
})
