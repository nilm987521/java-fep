import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  DashboardStats,
  TransactionStats,
  ChannelStats,
  ErrorStats,
  SystemStatus,
  Alert,
} from '@/types'
import { dashboardApi } from '@/api/dashboard'

export const useDashboardStore = defineStore('dashboard', () => {
  // State
  const stats = ref<DashboardStats>({
    totalTransactions: 0,
    successRate: 0,
    averageResponseTime: 0,
    currentTps: 0,
    peakTps: 0,
    totalAmount: 0,
    activeAlerts: 0,
    systemHealth: 'HEALTHY',
  })

  const transactionStats = ref<TransactionStats[]>([])
  const channelStats = ref<ChannelStats[]>([])
  const errorStats = ref<ErrorStats[]>([])
  const systemStatuses = ref<SystemStatus[]>([])
  const recentAlerts = ref<Alert[]>([])
  const isLoading = ref(false)
  const lastUpdated = ref<Date | null>(null)

  // Getters
  const healthStatus = computed(() => stats.value.systemHealth)
  const hasActiveAlerts = computed(() => stats.value.activeAlerts > 0)

  // Actions
  async function fetchDashboardData() {
    isLoading.value = true
    try {
      const [
        statsData,
        txnStatsData,
        channelData,
        errorData,
        systemData,
        alertData,
      ] = await Promise.all([
        dashboardApi.getStats(),
        dashboardApi.getTransactionStats(),
        dashboardApi.getChannelStats(),
        dashboardApi.getErrorStats(),
        dashboardApi.getSystemStatus(),
        dashboardApi.getRecentAlerts(),
      ])

      stats.value = statsData
      transactionStats.value = txnStatsData
      channelStats.value = channelData
      errorStats.value = errorData
      systemStatuses.value = systemData
      recentAlerts.value = alertData
      lastUpdated.value = new Date()
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error)
    } finally {
      isLoading.value = false
    }
  }

  async function refreshStats() {
    try {
      stats.value = await dashboardApi.getStats()
      lastUpdated.value = new Date()
    } catch (error) {
      console.error('Failed to refresh stats:', error)
    }
  }

  return {
    // State
    stats,
    transactionStats,
    channelStats,
    errorStats,
    systemStatuses,
    recentAlerts,
    isLoading,
    lastUpdated,
    // Getters
    healthStatus,
    hasActiveAlerts,
    // Actions
    fetchDashboardData,
    refreshStats,
  }
})
