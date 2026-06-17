import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as playInstanceApi from '@/services/playInstanceApi.js'

export const useActivePasseStore = defineStore('activePasse', () => {
  const activeInstances    = ref([])
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
    try {
      const instance = await playInstanceApi.startPasseInstance(template.id, players)
      activeInstances.value.push(instance)
      return instance
    } catch (e) {
      console.error('Failed to start Passe instance:', e)
      throw e
    }
  }

  const startTraining = async (template, players) => {
    try {
      const instance = await playInstanceApi.startTrainingInstance(template.id, players)
      activeInstances.value.push(instance)
      return instance
    } catch (e) {
      console.error('Failed to start Training instance:', e)
      throw e
    }
  }

  // ── Block lifecycle ───────────────────────────────────────────────────────

  const markBlockInProgress = async (instanceId, blockId) => {
    try {
      const updated = await playInstanceApi.startBlock(instanceId, blockId)
      _mergeInstance(updated)
    } catch (e) {
      console.error('Failed to mark block in progress:', e)
    }
  }

  const markBlockDone = async (instanceId, blockId, playerResults) => {
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
    try {
      await playInstanceApi.stopPlayInstance(instanceId)
    } catch (e) {
      console.error('Failed to stop play instance:', e)
    }
    activeInstances.value = activeInstances.value.filter((i) => i.instanceId !== instanceId)
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
      } else {
        // Passe instance — blocks at top level
        for (const block of (inst.blocks ?? [])) {
          if (block.rangeId === rangeId && block.status !== 'done') {
            result.push({ ...block, instanceId: inst.instanceId, templateName: inst.templateName, players: inst.players, instanceType: 'passe' })
          }
        }
      }
    }
    return result
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  function _mergeInstance(updated) {
    const idx = activeInstances.value.findIndex((i) => i.instanceId === updated.instanceId)
    if (idx === -1) {
      console.warn('[activePasseStore] _mergeInstance: no matching instance for', updated.instanceId)
      return
    }
    activeInstances.value[idx] = updated
  }

  return {
    activeInstances,
    completedInstances,
    loadFromStorage,
    startPasse,
    startTraining,
    getBlocksForRange,
    markBlockInProgress,
    markBlockDone,
    stopInstance,
  }
})
