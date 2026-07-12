import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ScoreTable from '@/components/shooter/ScoreTable.vue'
import { StepState, StepType } from '@/constants/playEnums.js'

const mockProgram = [
  { steps: [{ type: StepType.SOLO, letter: 'A', positionId: 'pos-1' }] },
]

const baseStepState = {
  playerId: 'p1',
  serieIndex: 0,
  stepIndex: 0,
  state: StepState.DONE,
  pointValue: 1,
  pointsEarned: 1,
  noBirds: 0,
  corrected: false,
  originalState: null,
}

const mockPlayers = [{ id: 'p1', displayName: 'Alice' }]

describe('ScoreTable', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('does not emit correct-step on row click when editable is false', async () => {
    const wrapper = mount(ScoreTable, {
      props: {
        stepStates: [baseStepState],
        program: mockProgram,
        players: mockPlayers,
        editable: false,
      },
    })

    await wrapper.find('.step-row').trigger('click')

    expect(wrapper.emitted('correct-step')).toBeFalsy()
  })

  it('emits correct-step with full payload on row click when editable is true', async () => {
    const wrapper = mount(ScoreTable, {
      props: {
        stepStates: [baseStepState],
        program: mockProgram,
        players: mockPlayers,
        editable: true,
      },
    })

    await wrapper.find('.step-row').trigger('click')

    expect(wrapper.emitted('correct-step')).toBeTruthy()
    expect(wrapper.emitted('correct-step')[0][0]).toEqual({
      playerId: 'p1',
      serieIndex: 0,
      stepIndex: 0,
      currentState: StepState.DONE,
    })
  })

  it('shows corrected-badge when step is corrected and editable is true', () => {
    const correctedState = { ...baseStepState, corrected: true }
    const wrapper = mount(ScoreTable, {
      props: {
        stepStates: [correctedState],
        program: mockProgram,
        players: mockPlayers,
        editable: true,
      },
    })

    expect(wrapper.find('.corrected-badge').text()).toBe('✏')
  })

  it('does not show corrected badge content when step is not corrected', () => {
    const wrapper = mount(ScoreTable, {
      props: {
        stepStates: [baseStepState],
        program: mockProgram,
        players: mockPlayers,
        editable: true,
      },
    })

    expect(wrapper.find('.corrected-badge').text()).toBe('')
  })
})
