import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as scoreApi from '@/services/scoreApi.js'

// Personal score history/summary and cross-user leaderboard
export const useScoreStore = defineStore('score', () => {
  const scores = ref([])
  const scoresMeta = ref(null)
  const summary = ref(null)
  const leaderboard = ref(null)
  const passen = ref([])
  const wettkaempfe = ref([])
  const isLoading = ref(false)
  const error = ref(null)

  const run = async (fn) => {
    isLoading.value = true
    error.value = null
    try {
      await fn()
    } catch (e) {
      error.value = e.message
    } finally {
      isLoading.value = false
    }
  }

  // params is forwarded as-is to fetchMyScores (e.g. { context, kind, page, ... })
  const loadScores = (params = {}) => run(async () => {
    const page = await scoreApi.fetchMyScores(params)
    scores.value = page.content ?? []
    scoresMeta.value = page.meta ?? null
  })

  const loadSummary = () => run(async () => {
    summary.value = await scoreApi.fetchMyScoreSummary()
  })

  const loadLeaderboard = (params = {}) => run(async () => {
    leaderboard.value = await scoreApi.fetchLeaderboard(params)
  })

  const loadPassen = () => run(async () => {
    passen.value = await scoreApi.fetchMyPassen()
  })

  const loadWettkaempfe = () => run(async () => {
    wettkaempfe.value = await scoreApi.fetchMyWettkaempfe()
  })

  return {
    scores,
    scoresMeta,
    summary,
    leaderboard,
    passen,
    wettkaempfe,
    isLoading,
    error,
    loadScores,
    loadSummary,
    loadLeaderboard,
    loadPassen,
    loadWettkaempfe,
  }
})
