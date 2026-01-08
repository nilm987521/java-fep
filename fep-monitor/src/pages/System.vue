<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { GaugeChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent } from 'echarts/components'
import type { SystemStatus, ConnectionStatus } from '@/types'

use([CanvasRenderer, GaugeChart, LineChart, TitleComponent, TooltipComponent, GridComponent])

// Mock data
const systemStatuses = ref<SystemStatus[]>([
  {
    serviceName: 'fep-gateway',
    status: 'UP',
    uptime: 99.99,
    lastHeartbeat: new Date().toISOString(),
    cpuUsage: 35,
    memoryUsage: 52,
    activeConnections: 256,
    errorRate: 0.01,
  },
  {
    serviceName: 'fep-transaction',
    status: 'UP',
    uptime: 99.98,
    lastHeartbeat: new Date().toISOString(),
    cpuUsage: 68,
    memoryUsage: 75,
    activeConnections: 128,
    errorRate: 0.02,
  },
  {
    serviceName: 'fep-communication',
    status: 'UP',
    uptime: 99.97,
    lastHeartbeat: new Date().toISOString(),
    cpuUsage: 42,
    memoryUsage: 55,
    activeConnections: 24,
    errorRate: 0.01,
  },
  {
    serviceName: 'fep-security',
    status: 'UP',
    uptime: 100,
    lastHeartbeat: new Date().toISOString(),
    cpuUsage: 22,
    memoryUsage: 40,
    activeConnections: 64,
    errorRate: 0,
  },
  {
    serviceName: 'fep-settlement',
    status: 'UP',
    uptime: 99.95,
    lastHeartbeat: new Date().toISOString(),
    cpuUsage: 15,
    memoryUsage: 35,
    activeConnections: 8,
    errorRate: 0,
  },
])

const connectionStatuses = ref<ConnectionStatus[]>([
  {
    connectionId: 'FISC-PRIMARY',
    connectionType: 'FISC',
    target: '財金公司主線',
    status: 'CONNECTED',
    lastActivity: new Date().toISOString(),
    sentMessages: 125847,
    receivedMessages: 125832,
    errorCount: 15,
  },
  {
    connectionId: 'FISC-BACKUP',
    connectionType: 'FISC',
    target: '財金公司備線',
    status: 'CONNECTED',
    lastActivity: new Date(Date.now() - 30000).toISOString(),
    sentMessages: 0,
    receivedMessages: 0,
    errorCount: 0,
  },
  {
    connectionId: 'CBS-MQ',
    connectionType: 'CBS',
    target: '核心銀行系統',
    status: 'CONNECTED',
    lastActivity: new Date().toISOString(),
    sentMessages: 98542,
    receivedMessages: 98540,
    errorCount: 2,
  },
  {
    connectionId: 'HSM-01',
    connectionType: 'HSM',
    target: 'HSM 主設備',
    status: 'CONNECTED',
    lastActivity: new Date().toISOString(),
    sentMessages: 251694,
    receivedMessages: 251694,
    errorCount: 0,
  },
  {
    connectionId: 'DB-PRIMARY',
    connectionType: 'DATABASE',
    target: 'Oracle RAC Primary',
    status: 'CONNECTED',
    lastActivity: new Date().toISOString(),
    sentMessages: 0,
    receivedMessages: 0,
    errorCount: 0,
  },
])

const loading = ref(false)
const autoRefresh = ref(true)
let refreshInterval: ReturnType<typeof setInterval> | null = null

// CPU/Memory history data for charts
const cpuHistory = ref<number[]>(Array(60).fill(0).map(() => Math.floor(Math.random() * 30) + 30))
const memoryHistory = ref<number[]>(Array(60).fill(0).map(() => Math.floor(Math.random() * 20) + 50))
const tpsHistory = ref<number[]>(Array(60).fill(0).map(() => Math.floor(Math.random() * 200) + 200))

const overallCpu = ref(45)
const overallMemory = ref(62)
const currentTps = ref(287)

function getGaugeOption(value: number, title: string, color: string) {
  return {
    series: [
      {
        type: 'gauge',
        startAngle: 180,
        endAngle: 0,
        min: 0,
        max: 100,
        splitNumber: 5,
        radius: '100%',
        center: ['50%', '75%'],
        axisLine: {
          lineStyle: {
            width: 15,
            color: [
              [0.6, '#67c23a'],
              [0.8, '#e6a23c'],
              [1, '#f56c6c'],
            ],
          },
        },
        pointer: {
          length: '60%',
          width: 6,
        },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        title: {
          offsetCenter: [0, '20%'],
          fontSize: 14,
          color: '#606266',
        },
        detail: {
          fontSize: 24,
          offsetCenter: [0, '-20%'],
          valueAnimation: true,
          formatter: '{value}%',
          color: color,
        },
        data: [{ value, name: title }],
      },
    ],
  }
}

function getLineOption(data: number[], title: string, color: string) {
  return {
    tooltip: {
      trigger: 'axis',
    },
    grid: {
      left: '5%',
      right: '5%',
      top: '10%',
      bottom: '10%',
    },
    xAxis: {
      type: 'category',
      show: false,
      data: data.map((_, i) => i),
    },
    yAxis: {
      type: 'value',
      show: false,
      min: 0,
      max: title === 'TPS' ? 500 : 100,
    },
    series: [
      {
        data: data,
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { color, width: 2 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: color + '40' },
              { offset: 1, color: color + '00' },
            ],
          },
        },
      },
    ],
  }
}

function getStatusType(status: string): 'success' | 'danger' | 'warning' {
  switch (status) {
    case 'UP':
    case 'CONNECTED':
      return 'success'
    case 'DOWN':
    case 'DISCONNECTED':
      return 'danger'
    default:
      return 'warning'
  }
}

function getStatusText(status: string): string {
  switch (status) {
    case 'UP':
      return '正常'
    case 'DOWN':
      return '停止'
    case 'DEGRADED':
      return '降級'
    case 'CONNECTED':
      return '已連線'
    case 'DISCONNECTED':
      return '已斷線'
    case 'RECONNECTING':
      return '重連中'
    default:
      return status
  }
}

function formatNumber(num: number): string {
  return num.toLocaleString()
}

function refreshData() {
  // Simulate data refresh
  overallCpu.value = Math.floor(Math.random() * 20) + 40
  overallMemory.value = Math.floor(Math.random() * 15) + 55
  currentTps.value = Math.floor(Math.random() * 100) + 250

  cpuHistory.value = [...cpuHistory.value.slice(1), overallCpu.value]
  memoryHistory.value = [...memoryHistory.value.slice(1), overallMemory.value]
  tpsHistory.value = [...tpsHistory.value.slice(1), currentTps.value]
}

function handleRefresh() {
  refreshData()
  ElMessage.success('資料已更新')
}

onMounted(() => {
  refreshInterval = setInterval(() => {
    if (autoRefresh.value) {
      refreshData()
    }
  }, 5000)
})

onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
  }
})
</script>

<template>
  <div class="system-page">
    <!-- Page Header -->
    <div class="page-header">
      <h2>系統監控</h2>
      <div class="header-actions">
        <el-switch v-model="autoRefresh" active-text="自動更新" style="margin-right: 16px" />
        <el-button type="primary" :icon="Refresh" @click="handleRefresh">重新整理</el-button>
      </div>
    </div>

    <!-- Overall Metrics -->
    <el-row :gutter="20">
      <el-col :xs="24" :sm="8">
        <div class="metric-card">
          <div class="metric-header">
            <span class="title">CPU 使用率</span>
            <el-tag :type="overallCpu > 80 ? 'danger' : overallCpu > 60 ? 'warning' : 'success'" size="small">
              {{ overallCpu > 80 ? '高' : overallCpu > 60 ? '中' : '正常' }}
            </el-tag>
          </div>
          <div class="gauge-container">
            <VChart :option="getGaugeOption(overallCpu, 'CPU', overallCpu > 80 ? '#f56c6c' : '#67c23a')" autoresize />
          </div>
          <div class="chart-mini">
            <VChart :option="getLineOption(cpuHistory, 'CPU', '#409eff')" autoresize />
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="8">
        <div class="metric-card">
          <div class="metric-header">
            <span class="title">記憶體使用率</span>
            <el-tag :type="overallMemory > 80 ? 'danger' : overallMemory > 60 ? 'warning' : 'success'" size="small">
              {{ overallMemory > 80 ? '高' : overallMemory > 60 ? '中' : '正常' }}
            </el-tag>
          </div>
          <div class="gauge-container">
            <VChart :option="getGaugeOption(overallMemory, '記憶體', overallMemory > 80 ? '#f56c6c' : '#67c23a')" autoresize />
          </div>
          <div class="chart-mini">
            <VChart :option="getLineOption(memoryHistory, 'Memory', '#67c23a')" autoresize />
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="8">
        <div class="metric-card">
          <div class="metric-header">
            <span class="title">當前 TPS</span>
            <el-tag type="success" size="small">正常</el-tag>
          </div>
          <div class="tps-value">{{ currentTps }}</div>
          <div class="tps-label">筆/秒</div>
          <div class="chart-mini">
            <VChart :option="getLineOption(tpsHistory, 'TPS', '#e6a23c')" autoresize />
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- Service Status -->
    <div class="section-card">
      <div class="section-header">
        <span class="title">服務狀態</span>
        <el-tag type="success" size="small">{{ systemStatuses.filter(s => s.status === 'UP').length }}/{{ systemStatuses.length }} 正常</el-tag>
      </div>

      <el-table :data="systemStatuses" stripe border>
        <el-table-column prop="serviceName" label="服務名稱" width="180" />
        <el-table-column prop="status" label="狀態" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="dark">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cpuUsage" label="CPU" width="150">
          <template #default="{ row }">
            <el-progress
              :percentage="row.cpuUsage"
              :status="row.cpuUsage > 80 ? 'exception' : row.cpuUsage > 60 ? 'warning' : 'success'"
              :stroke-width="10"
            />
          </template>
        </el-table-column>
        <el-table-column prop="memoryUsage" label="記憶體" width="150">
          <template #default="{ row }">
            <el-progress
              :percentage="row.memoryUsage"
              :status="row.memoryUsage > 80 ? 'exception' : row.memoryUsage > 60 ? 'warning' : 'success'"
              :stroke-width="10"
            />
          </template>
        </el-table-column>
        <el-table-column prop="activeConnections" label="連線數" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.activeConnections) }}
          </template>
        </el-table-column>
        <el-table-column prop="uptime" label="可用率" width="100" align="right">
          <template #default="{ row }">
            {{ row.uptime.toFixed(2) }}%
          </template>
        </el-table-column>
        <el-table-column prop="errorRate" label="錯誤率" width="100" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.errorRate > 0 }">
              {{ (row.errorRate * 100).toFixed(2) }}%
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default>
            <el-button type="primary" link size="small">詳情</el-button>
            <el-button type="primary" link size="small">日誌</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Connection Status -->
    <div class="section-card">
      <div class="section-header">
        <span class="title">連線狀態</span>
        <el-tag type="success" size="small">{{ connectionStatuses.filter(c => c.status === 'CONNECTED').length }}/{{ connectionStatuses.length }} 已連線</el-tag>
      </div>

      <el-table :data="connectionStatuses" stripe border>
        <el-table-column prop="connectionId" label="連線 ID" width="150" />
        <el-table-column prop="connectionType" label="類型" width="100" />
        <el-table-column prop="target" label="目標" width="180" />
        <el-table-column prop="status" label="狀態" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="dark">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sentMessages" label="已發送" width="120" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.sentMessages) }}
          </template>
        </el-table-column>
        <el-table-column prop="receivedMessages" label="已接收" width="120" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.receivedMessages) }}
          </template>
        </el-table-column>
        <el-table-column prop="errorCount" label="錯誤數" width="100" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.errorCount > 0 }">
              {{ formatNumber(row.errorCount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default>
            <el-button type="primary" link size="small">重連</el-button>
            <el-button type="primary" link size="small">測試</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped lang="scss">
.system-page {
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
    }
  }

  .metric-card {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    margin-bottom: 20px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);

    .metric-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;

      .title {
        font-size: 14px;
        font-weight: 600;
        color: #606266;
      }
    }

    .gauge-container {
      height: 150px;
    }

    .chart-mini {
      height: 60px;
      margin-top: 10px;
    }

    .tps-value {
      font-size: 48px;
      font-weight: 700;
      color: #303133;
      text-align: center;
      margin-top: 20px;
    }

    .tps-label {
      text-align: center;
      color: #909399;
      font-size: 14px;
    }
  }

  .section-card {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    margin-bottom: 20px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);

    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
      padding-bottom: 12px;
      border-bottom: 1px solid #ebeef5;

      .title {
        font-size: 16px;
        font-weight: 600;
        color: #303133;
      }
    }
  }

  .text-danger {
    color: #f56c6c;
  }
}
</style>
