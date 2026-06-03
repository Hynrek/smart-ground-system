// src/stores/activePasseStore.js
import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as playInstanceApi from '@/services/playInstanceApi.js'

const generateUUID = () =>
  typeof globalThis !== 'undefined' && globalThis.crypto?.randomUUID
    ? globalThis.crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
      })

export const useActivePasseStore = defineStore('activePasse', () => {
  const activeInstances = ref([])
  const completedInstances = ref([])

  // ── Instance loading ──────────────────────────────────────────────────────

  const loadFromStorage = async () => {
    try {
      const instances = await playInstanceApi.listPlayInstances({ status: 'active' })
      activeInstances.value = instances
    } catch (e) {
      console.error('Failed to load active play instances:', e)
      activeInstances.value = []
    }
  }

  // ── Start instances ───────────────────────────────────────────────────────

  const startPasse = async (template, players) => {
    const instance = await playInstanceApi.startProgrammeInstance(template.id, players)
    activeInstances.value.push(instance)
    return instance
  }

  const startTraining = async (template, players) => {
    const instance = await playInstanceApi.startTrainingInstance(template.id, players)
    activeInstances.value.push(instance)
    return instance
  }

  // Competition instances remain in-memory only (no backend endpoint for rotten-based competitions)
  const startCompetition = (template, rotten) => {
    const buildPhases = (passen) =>
      passen.map((passe, phaseIndex) => ({
        phaseIndex,
        passeId: passe.id,
        passeName: passe.name,
        status: phaseIndex === 0 ? 'active' : 'pending',
        blocks: (passe.serien ?? []).map((serie) => ({
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
    return instance
  }

  // ── Block lifecycle ───────────────────────────────────────────────────────

  const markBlockInProgress = async (instanceId, blockId, rotteId) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    if (!inst) return

    if (inst.type === 'competition') {
      // Competition blocks are managed in-memory only
      const rotte = inst.rotten?.find((r) => r.rotteId === rotteId)
      const phase = rotte?.phases[rotte.currentPhaseIndex]
      const block = phase?.blocks.find((b) => b.blockId === blockId)
      if (block && block.status === 'pending') block.status = 'in_progress'
      return
    }

    try {
      const updated = await playInstanceApi.startBlock(instanceId, blockId)
      _mergeInstance(updated)
    } catch (e) {
      console.error('Failed to mark block in progress:', e)
    }
  }

  const markBlockDone = async (instanceId, blockId, playerResults, rotteId) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    if (!inst) return

    if (inst.type === 'competition') {
      // Competition blocks are managed in-memory only
      _completeCompetitionBlock(inst, blockId, playerResults, rotteId)
      return
    }

    try {
      const updated = await playInstanceApi.completeBlock(instanceId, blockId, playerResults)
      _mergeInstance(updated)
      if (updated.status === 'completed') {
        completedInstances.value.push(updated)
        activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== instanceId)
      }
    } catch (e) {
      console.error('Failed to complete block:', e)
    }
  }

  const stopInstance = async (instanceId) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    if (inst?.type === 'competition') {
      activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== instanceId)
      return
    }
    try {
      await playInstanceApi.stopPlayInstance(instanceId)
    } catch (e) {
      console.error('Failed to stop play instance:', e)
    }
    activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== instanceId)
  }

  const stopCompetition = (instanceId) => stopInstance(instanceId)

  // ── Competition-specific (in-memory only) ─────────────────────────────────

  const assignRotteToRange = (instanceId, rotteId, rangeId) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    if (!inst || inst.type !== 'competition') return
    const rotte = inst.rotten.find((r) => r.rotteId === rotteId)
    if (!rotte) return
    rotte.assignedRangeId = rangeId
    rotte.status = 'active'
  }

  const unassignRotte = (instanceId, rotteId) => {
    const inst = activeInstances.value.find((i) => i.instanceId === instanceId)
    if (!inst || inst.type !== 'competition') return
    const rotte = inst.rotten.find((r) => r.rotteId === rotteId)
    if (!rotte) return
    rotte.assignedRangeId = null
    rotte.status = 'paused'
  }

  // ── Queries ───────────────────────────────────────────────────────────────

  const getBlocksForRange = (rangeId) => {
    const result = []
    for (const inst of activeInstances.value) {
      if (inst.type === 'training') {
        const activePhase = inst.phases?.find((p) => p.status === 'active')
        for (const block of (activePhase?.blocks ?? [])) {
          if (block.rangeId === rangeId && block.status !== 'done') {
            result.push({ ...block, instanceId: inst.instanceId, templateName: inst.templateName, players: inst.players, instanceType: 'training' })
          }
        }
      } else if (inst.type === 'competition') {
        for (const rotte of (inst.rotten ?? [])) {
          if (rotte.assignedRangeId !== rangeId || rotte.status !== 'active') continue
          const phase = rotte.phases[rotte.currentPhaseIndex]
          if (!phase) continue
          for (const block of phase.blocks) {
            if (block.rangeId === rangeId && block.status !== 'done') {
              result.push({ ...block, instanceId: inst.instanceId, templateName: inst.templateName, players: rotte.players, instanceType: 'competition', rotteName: rotte.name, rotteId: rotte.rotteId })
            }
          }
        }
      } else {
        // Programme instance — blocks are at the top level
        for (const block of (inst.blocks ?? [])) {
          if (block.rangeId === rangeId && block.status !== 'done') {
            result.push({ ...block, instanceId: inst.instanceId, templateName: inst.templateName, players: inst.players, instanceType: 'passe' })
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
      for (const rotte of (inst.rotten ?? [])) {
        if (rotte.status === 'done') continue
        const phase = rotte.phases[rotte.currentPhaseIndex]
        if (!phase) continue
        result.push({ instanceId: inst.instanceId, instanceName: inst.templateName, rotteId: rotte.rotteId, rotteName: rotte.name, passeName: phase.passeName, players: rotte.players, blocks: phase.blocks })
      }
    }
    return result
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  function _mergeInstance(updated) {
    const idx = activeInstances.value.findIndex((i) => i.instanceId === updated.instanceId)
    if (idx > -1) activeInstances.value[idx] = updated
  }

  function _completeCompetitionBlock(inst, blockId, playerResults, rotteId) {
    const rotte = inst.rotten?.find((r) => r.rotteId === rotteId)
    if (!rotte) return
    const phase = rotte.phases[rotte.currentPhaseIndex]
    if (!phase) return
    const block = phase.blocks.find((b) => b.blockId === blockId)
    if (!block) return
    block.status = 'done'
    block.completedAt = Date.now()
    block.result = { playerResults }

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
          activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== inst.instanceId)
        }
      }
    }
  }

  return {
    activeInstances,
    completedInstances,
    loadFromStorage,
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
