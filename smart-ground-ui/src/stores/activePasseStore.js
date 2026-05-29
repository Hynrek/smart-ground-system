import { defineStore } from 'pinia'
import { ref } from 'vue'

const generateUUID = () =>
  typeof globalThis !== 'undefined' && globalThis.crypto?.randomUUID
    ? globalThis.crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
      })

const ACTIVE_KEY = 'sg_active_passe_instances'
const COMPLETED_KEY = 'sg_completed_passe_instances'

export const useActivePasseStore = defineStore('activePasse', () => {
  const activeInstances = ref([])
  const completedInstances = ref([])

  const _saveActive = () =>
    localStorage.setItem(ACTIVE_KEY, JSON.stringify(activeInstances.value))

  const _saveCompleted = () =>
    localStorage.setItem(COMPLETED_KEY, JSON.stringify(completedInstances.value))

  const _completeBlock = (block, playerResults) => {
    block.status = 'done'
    block.completedAt = Date.now()
    block.result = { playerResults }
  }

  const loadFromStorage = () => {
    try {
      const a = localStorage.getItem(ACTIVE_KEY)
      if (a) activeInstances.value = JSON.parse(a)
      const c = localStorage.getItem(COMPLETED_KEY)
      if (c) completedInstances.value = JSON.parse(c)
    } catch { /* ignore malformed data */ }
  }

  const startPasse = (template, players) => {
    const instance = {
      instanceId: generateUUID(),
      type: 'passe',
      templateId: template.id,
      templateName: template.name,
      players: [...players],
      startedAt: Date.now(),
      blocks: template.serien.map((serie) => ({
        blockId: generateUUID(),
        serieId: serie.id,
        serieAlias: serie.name ?? serie.alias ?? serie.id,
        rangeId: serie.rangeId ?? null,
        rangeName: serie.rangeName ?? null,
        steps: serie.steps ?? [],
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
      phases: template.passen.map((passe, phaseIndex) => ({
        phaseIndex,
        passeId: passe.id,
        passeName: passe.name,
        status: phaseIndex === 0 ? 'active' : 'pending',
        blocks: passe.serien.map((serie) => ({
          blockId: generateUUID(),
          serieId: serie.id,
          serieAlias: serie.name ?? serie.alias ?? serie.id,
          rangeId: serie.rangeId ?? null,
          rangeName: serie.rangeName ?? null,
          steps: serie.steps ?? [],
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
              passeName: phase.passeName,
              players: inst.players,
              instanceType: 'training',
            })
          }
        }
      } else if (inst.type === 'competition') {
        for (const rotte of inst.rotten) {
          if (rotte.assignedRangeId !== rangeId || rotte.status !== 'active') continue
          const phase = rotte.phases[rotte.currentPhaseIndex]
          if (!phase) continue
          for (const block of phase.blocks) {
            if (block.rangeId === rangeId && block.status !== 'done') {
              result.push({
                ...block,
                instanceId: inst.instanceId,
                templateName: inst.templateName,
                passeName: phase.passeName,
                players: rotte.players,
                instanceType: 'competition',
                rotteName: rotte.name,
                rotteId: rotte.rotteId,
              })
            }
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
              instanceType: 'passe',
            })
          }
        }
      }
    }
    return result
  }

  const getActiveCompetitionRotten = () => {
    const result = []
    for (const inst of activeInstances.value) {
      if (inst.type !== 'competition') continue
      for (const rotte of inst.rotten) {
        if (rotte.status === 'done') continue
        const phase = rotte.phases[rotte.currentPhaseIndex]
        if (!phase) continue
        result.push({
          instanceId: inst.instanceId,
          instanceName: inst.templateName,
          rotteId: rotte.rotteId,
          rotteName: rotte.name,
          passeName: phase.passeName,
          phaseIndex: rotte.currentPhaseIndex,
          players: rotte.players,
          blocks: phase.blocks,
        })
      }
    }
    return result
  }

  const markBlockInProgress = (instanceId, blockId, rotteId) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    if (!inst) return
    let block
    if (rotteId && inst.type === 'competition') {
      const rotte = inst.rotten.find((r) => r.rotteId === rotteId)
      const phase = rotte?.phases[rotte.currentPhaseIndex]
      block = phase?.blocks.find((b) => b.blockId === blockId)
    } else if (inst.type === 'training') {
      const phase = inst.phases[inst.currentPhaseIndex]
      block = phase?.blocks.find((b) => b.blockId === blockId)
    } else {
      block = inst.blocks.find((b) => b.blockId === blockId)
    }
    if (block && block.status === 'pending') {
      block.status = 'in_progress'
      _saveActive()
    }
  }

  const markBlockDone = (instanceId, blockId, playerResults, rotteId) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    if (!inst) return

    if (rotteId && inst.type === 'competition') {
      const rotte = inst.rotten.find((r) => r.rotteId === rotteId)
      if (!rotte) return
      const phase = rotte.phases[rotte.currentPhaseIndex]
      if (!phase) return
      const block = phase.blocks.find((b) => b.blockId === blockId)
      if (!block) return
      _completeBlock(block, playerResults)

      if (phase.blocks.every((b) => b.status === 'done')) {
        phase.status = 'done'
        const nextIndex = rotte.currentPhaseIndex + 1
        if (nextIndex < rotte.phases.length) {
          rotte.currentPhaseIndex = nextIndex
          rotte.phases[nextIndex].status = 'active'
        } else {
          rotte.status = 'done'
          if (inst.rotten.every((r) => r.status === 'done')) {
            completedInstances.value.push({ ...inst, completedAt: Date.now() })
            activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== instanceId)
            _saveCompleted()
          }
        }
      }
    } else if (inst.type === 'training') {
      const phase = inst.phases[inst.currentPhaseIndex]
      if (!phase) return
      const block = phase.blocks.find((b) => b.blockId === blockId)
      if (!block) return
      _completeBlock(block, playerResults)

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
      _completeBlock(block, playerResults)

      if (inst.blocks.every((b) => b.status === 'done')) {
        completedInstances.value.push({ ...inst, completedAt: Date.now() })
        activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== instanceId)
        _saveCompleted()
      }
    }
    _saveActive()
  }

  const startCompetition = (template, rotten) => {
    const buildPhases = (passen) =>
      passen.map((passe, phaseIndex) => ({
        phaseIndex,
        passeId: passe.id,
        passeName: passe.name,
        status: phaseIndex === 0 ? 'active' : 'pending',
        blocks: passe.serien.map((serie) => ({
          blockId: generateUUID(),
          serieId: serie.id,
          serieAlias: serie.name ?? serie.alias ?? serie.id,
          rangeId: serie.rangeId ?? null,
          rangeName: serie.rangeName ?? null,
          steps: serie.steps ?? [],
          status: 'pending',
          completedAt: null,
          result: null,
        })),
      }))

    const instance = {
      instanceId: generateUUID(),
      type: 'competition',
      templateId: template.id,
      templateName: template.name,
      name: template.name,
      passen: template.passen,
      rotten: rotten.map((r) => ({
        rotteId: r.rotteId,
        name: r.name,
        players: [...r.players],
        status: 'waiting',
        assignedRangeId: null,
        currentPhaseIndex: 0,
        phases: buildPhases(template.passen),
      })),
      startedAt: Date.now(),
      completedAt: null,
    }
    activeInstances.value.push(instance)
    _saveActive()
    return instance
  }

  const assignRotteToRange = (instanceId, rotteId, rangeId) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    if (!inst || inst.type !== 'competition') return
    const rotte = inst.rotten.find((r) => r.rotteId === rotteId)
    if (!rotte) return
    rotte.assignedRangeId = rangeId
    rotte.status = 'active'
    _saveActive()
  }

  const unassignRotte = (instanceId, rotteId) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    if (!inst || inst.type !== 'competition') return
    const rotte = inst.rotten.find((r) => r.rotteId === rotteId)
    if (!rotte) return
    rotte.assignedRangeId = null
    rotte.status = 'paused'
    _saveActive()
  }

  const stopInstance = (instanceId) => {
    activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== instanceId)
    _saveActive()
  }

  const stopCompetition = (instanceId) => stopInstance(instanceId)

  loadFromStorage()

  return {
    activeInstances,
    completedInstances,
    startPasse,
    startTraining,
    startCompetition,
    getBlocksForRange,
    getActiveCompetitionRotten,
    markBlockInProgress,
    markBlockDone,
    stopInstance,
    stopCompetition,
    assignRotteToRange,
    unassignRotte,
  }
})
