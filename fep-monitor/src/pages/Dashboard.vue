<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useDashboardStore } from '@/stores/dashboard'
import StatsCard from '@/components/dashboard/StatsCard.vue'
import TransactionChart from '@/components/dashboard/TransactionChart.vue'
import ChannelPieChart from '@/components/dashboard/ChannelPieChart.vue'
import SystemStatusCard from '@/components/dashboard/SystemStatusCard.vue'
import AlertList from '@/components/dashboard/AlertList.vue'
import ErrorRateChart from '@/components/dashboard/ErrorRateChart.vue'

const dashboardStore = useDashboardStore()
let refreshInterval: ReturnType<typeof setInterval> | null = null

const autoRefresh = ref(true)
const refreshSeconds = ref(30)

onMounted(() => {
  dashboardStore.fetchDashboardData()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})

function startAutoRefresh() {
  if (refreshInterval) return
  refreshInterval = setInterval(() => {
    if (autoRefresh.value) {
      dashboardStore.refreshStats()
    }
  }, refreshSeconds.value * 1000)
}

function stopAutoRefresh() {
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
}

function handleRefresh() {
  dashboardStore.fetchDashboardData()
}

function formatAmount(amount: number): string {
  if (amount >= 100000000) {
    return (amount / 100000000).toFixed(2) + ' 億'
  } else if (amount >= 10000) {
    return (amount / 10000).toFixed(2) + ' 萬'
  }
  return amount.toLocaleString()
}
</script>

<template>
  <div class="dashboard-page">
    <!-- Header -->
    <div class="page-header">
      <h2>監控儀表板</h2>
      <div class="header-actions">
        <el-switch
          v-model="autoRefresh"
          active-text="自動更新"
          inactive-text=""
          style="margin-right: 16px"
        />
        <el-button type="primary" :icon="Refresh" @click="handleRefresh" :loading="dashboardStore.isLoading">
          重新整理
        </el-button>
        <span v-if="dashboardStore.lastUpdated" class="last-updated">
          最後更新: {{ new Date(dashboardStore.lastUpdated).toLocaleTimeString() }}
        </span>
      </div>
    </div>

    <!-- Stats Cards Row -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6">
        <StatsCard
          title="今日交易筆數"
          :value="dashboardStore.stats.totalTransactions.toLocaleString()"
          icon="Tickets"
          color="#409eff"
          :trend="{ value: 5.2, isUp: true }"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <StatsCard
          title="交易成功率"
          :value="dashboardStore.stats.successRate.toFixed(2) + '%'"
          icon="CircleCheck"
          color="#67c23a"
          :trend="{ value: 0.15, isUp: true }"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <StatsCard
          title="平均回應時間"
          :value="dashboardStore.stats.averageResponseTime + ' ms'"
          icon="Timer"
          color="#e6a23c"
          :trend="{ value: 12, isUp: false }"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <StatsCard
          title="當前 TPS"
          :value="dashboardStore.stats.currentTps.toString()"
          :subValue="'峰值: ' + dashboardStore.stats.peakTps"
          icon="Odometer"
          color="#909399"
        />
      </el-col>
    </el-row>

    <!-- Second Stats Row -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6">
        <StatsCard
          title="交易總金額"
          :value="formatAmount(dashboardStore.stats.totalAmount)"
          icon="Money"
          color="#409eff"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <StatsCard
          title="待處理告警"
          :value="dashboardStore.stats.activeAlerts.toString()"
          icon="Bell"
          :color="dashboardStore.stats.activeAlerts > 0 ? '#f56c6c' : '#67c23a'"
          :highlight="dashboardStore.stats.activeAlerts > 0"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <StatsCard
          title="系統健康度"
          :value="dashboardStore.stats.systemHealth === 'HEALTHY' ? '正常' :
                  dashboardStore.stats.systemHealth === 'DEGRADED' ? '降級' : '異常'"
          icon="Monitor"
          :color="dashboardStore.stats.systemHealth === 'HEALTHY' ? '#67c23a' :
                  dashboardStore.stats.systemHealth === 'DEGRADED' ? '#e6a23c' : '#f56c6c'"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <StatsCard
          title="線上服務數"
          :value="dashboardStore.systemStatuses.filter(s => s.status === 'UP').length + '/' + dashboardStore.systemStatuses.length"
          icon="Connection"
          color="#67c23a"
        />
      </el-col>
    </el-row>

    <!-- Charts Row -->
    <el-row :gutter="20">
      <el-col :xs="24" :lg="16">
        <div class="dashboard-card">
          <div class="card-header">
            <span class="title">交易趨勢 (24小時)</span>
          </div>
          <TransactionChart :data="dashboardStore.transactionStats" />
        </div>
      </el-col>
      <el-col :xs="24" :lg="8">
        <div class="dashboard-card">
          <div class="card-header">
            <span class="title">通道分佈</span>
          </div>
          <ChannelPieChart :data="dashboardStore.channelStats" />
        </div>
      </el-col>
    </el-row>

    <!-- Second Charts Row -->
    <el-row :gutter="20">
      <el-col :xs="24" :lg="12">
        <div class="dashboard-card">
          <div class="card-header">
            <span class="title">錯誤代碼分析</span>
          </div>
          <ErrorRateChart :data="dashboardStore.errorStats" />
        </div>
      </el-col>
      <el-col :xs="24" :lg="12">
        <div class="dashboard-card">
          <div class="card-header">
            <span class="title">系統狀態</span>
            <el-tag type="success" size="small">全部正常</el-tag>
          </div>
          <SystemStatusCard :data="dashboardStore.systemStatuses" />
        </div>
      </el-col>
    </el-row>

    <!-- Alerts Section -->
    <el-row :gutter="20">
      <el-col :span="24">
        <div class="dashboard-card">
          <div class="card-header">
            <span class="title">最新告警</span>
            <el-button type="primary" link @click="$router.push('/alerts')">
              查看全部 <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
          <AlertList :data="dashboardStore.recentAlerts" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.dashboard-page {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 600;
      color: #303133;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 16px;

      .last-updated {
        font-size: 12px;
        color: #909399;
      }
    }
  }

  .stats-row {
    margin-bottom: 20px;
  }
}
</style>
