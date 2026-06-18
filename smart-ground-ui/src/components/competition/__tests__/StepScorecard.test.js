import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StepScorecard from '../StepScorecard.vue'

const passen = [
  { passeIndex: 0, label: 'Passe 1', steps: [
    { stepIndex: 0, state: 'done', pointsEarned: 2, pointValue: 2 },
    { stepIndex: 1, state: 'failed-a', pointsEarned: 1, pointValue: 2 },
  ] },
  { passeIndex: 1, label: 'Passe 2', steps: [
    { stepIndex: 0, state: 'failed-both', pointsEarned: 0, pointValue: 2 },
  ] },
]

describe('StepScorecard', () => {
  it('renders a labelled group per Passe', () => {
    const wrapper = mount(StepScorecard, { props: { passen } })
    const labels = wrapper.findAll('.passe-label').map(n => n.text())
    expect(labels).toEqual(['Passe 1', 'Passe 2'])
  })

  it('renders one chip per step with a state class and points', () => {
    const wrapper = mount(StepScorecard, { props: { passen } })
    const chips = wrapper.findAll('.step-chip')
    expect(chips).toHaveLength(3)
    expect(chips[0].classes()).toContain('is-done')
    expect(chips[0].text()).toContain('2/2')
    expect(chips[1].classes()).toContain('is-fail')
    expect(chips[1].text()).toContain('1/2')
  })

  it('renders nothing when there are no Passen', () => {
    const wrapper = mount(StepScorecard, { props: { passen: [] } })
    expect(wrapper.find('.step-chip').exists()).toBe(false)
  })
})
