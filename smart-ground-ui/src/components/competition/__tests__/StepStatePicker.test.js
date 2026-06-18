import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StepStatePicker from '../StepStatePicker.vue'

describe('StepStatePicker', () => {
  it('single-target step offers Treffer + one Fehler', () => {
    const w = mount(StepStatePicker, { props: { type: 'solo' } })
    const labels = w.findAll('.picker-btn').map(b => b.text())
    expect(labels).toContain('Treffer')
    expect(labels.filter(l => l.includes('Fehler'))).toHaveLength(1)
  })

  it('double-target step offers Treffer + A/B/Beide and emits the chosen state', async () => {
    const w = mount(StepStatePicker, { props: { type: 'pair' } })
    expect(w.findAll('.picker-btn').length).toBe(4)
    await w.findAll('.picker-btn').find(b => b.text() === 'Treffer').trigger('click')
    expect(w.emitted('pick')[0][0]).toBe('done')
  })
})
