import { defineStore } from 'pinia'
import { ref } from 'vue'

const generateUUID = () =>
  typeof globalThis !== 'undefined' && globalThis.crypto?.randomUUID
    ? globalThis.crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
      })

const ACTIVE_KEY = 'sg_active_program_instances'
const COMPLETED_KEY = 'sg_completed_program_instances'

export const useActiveProgramStore = defineStore('activeProgram', () => {
  const activeInstances = ref([])
  const completedInstances = ref([])

  const _saveActive = () =>
    localStorage.setItem(ACTIVE_KEY, JSON.stringify(activeInstances.value))

  const _saveCompleted = () =>
    localStorage.setItem(COMPLETED_KEY, JSON.stringify(completedInstances.value))

  const loadFromStorage = () => {
    try {
      const a = localStorage.getItem(ACTIVE_KEY)
      if (a) activeInstances.value = JSON.parse(a)
      const c = localStorage.getItem(COMPLETED_KEY)
      if (c) completedInstances.value = JSON.parse(c)
    } catch { /* ignore malformed data */ }
  }

  const startProgram = (template, players) => {
    const instance = {
      instanceId: generateUUID(),
      type: 'programm',
      templateId: template.id,
      templateName: template.name,
      players: [...players],
      startedAt: Date.now(),
      blocks: template.ablaeufe.map((ablauf) => ({
        blockId: generateUUID(),
        ablaufId: ablauf.id,
        ablaufAlias: ablauf.name ?? ablauf.alias ?? ablauf.id,
        rangeId: ablauf.rangeId ?? null,
        rangeName: ablauf.rangeName ?? null,
        steps: ablauf.steps ?? [],
        status: 'pending',
        completedAt: null,
        result: null,
      })),
    }
    activeInstances.value.push(instance)
    _saveActive()
    return instance
  }

  const startTraining = (template, players) => {
    const instance = {
      instanceId: generateUUID(),
      type: 'training',
      templateId: template.id,
      templateName: template.name,
      players: [...players],
      startedAt: Date.now(),
      currentPhaseIndex: 0,
      phases: template.programmes.map((prog, phaseIndex) => ({
        phaseIndex,
        programmeId: prog.id,
        programmeName: prog.name,
        status: phaseIndex === 0 ? 'active' : 'pending',
        blocks: prog.ablaeufe.map((ablauf) => ({
          blockId: generateUUID(),
          ablaufId: ablauf.id,
          ablaufAlias: ablauf.name ?? ablauf.alias ?? ablauf.id,
          rangeId: ablauf.rangeId ?? null,
          rangeName: ablauf.rangeName ?? null,
          steps: ablauf.steps ?? [],
          status: 'pending',
          completedAt: null,
          result: null,
        })),
      })),
    }
    activeInstances.value.push(instance)
    _saveActive()
    return instance
  }

  const getBlocksForRange = (rangeId) => {
    const result = []
    for (const inst of activeInstances.value) {
      if (inst.type === 'training') {
        const phase = inst.phases[inst.currentPhaseIndex]
        if (!phase) continue
        for (const block of phase.blocks) {
          if (block.rangeId === rangeId && block.status !== 'done') {
            result.push({
              ...block,
              instanceId: inst.instanceId,
              templateName: inst.templateName,
              programmeName: phase.programmeName,
              players: inst.players,
              instanceType: 'training',
            })
          }
        }
      } else {
        for (const block of inst.blocks) {
          if (block.rangeId === rangeId && block.status !== 'done') {
            result.push({
              ...block,
              instanceId: inst.instanceId,
              templateName: inst.templateName,
              players: inst.players,
              instanceType: 'programm',
            })
          }
        }
      }
    }
    return result
  }

  const markBlockInProgress = (instanceId, blockId) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    const block = inst?.blocks.find((b) => b.blockId === blockId)
    if (block && block.status === 'pending') {
      block.status = 'in_progress'
      _saveActive()
    }
  }

  const markBlockDone = (instanceId, blockId, playerResults) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    if (!inst) return

    if (inst.type === 'training') {
      const phase = inst.phases[inst.currentPhaseIndex]
      if (!phase) return
      const block = phase.blocks.find((b) => b.blockId === blockId)
      if (!block) return
      block.status = 'done'
      block.completedAt = Date.now()
      block.result = { playerResults }

      if (phase.blocks.every((b) => b.status === 'done')) {
        phase.status = 'done'
        const nextIndex = inst.currentPhaseIndex + 1
        if (nextIndex < inst.phases.length) {
          inst.currentPhaseIndex = nextIndex
          inst.phases[nextIndex].status = 'active'
        } else {
          completedInstances.value.push({ ...inst, completedAt: Date.now() })
          activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== instanceId)
          _saveCompleted()
        }
      }
    } else {
      const block = inst.blocks.find((b) => b.blockId === blockId)
      if (!block) return
      block.status = 'done'
      block.completedAt = Date.now()
      block.result = { playerResults }

      if (inst.blocks.every((b) => b.status === 'done')) {
        completedInstances.value.push({ ...inst, completedAt: Date.now() })
        activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== instanceId)
        _saveCompleted()
      }
    }
    _saveActive()
  }

  const stopInstance = (instanceId) => {
    activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== instanceId)
    _saveActive()
  }

  loadFromStorage()

  return {
    activeInstances,
    completedInstances,
    startProgram,
    startTraining,
    getBlocksForRange,
    markBlockInProgress,
    markBlockDone,
    stopInstance,
  }
})
