<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import dayjs from 'dayjs'

use([CanvasRenderer, LineChart, BarChart, PieChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

// State
const activeTab = ref('daily')
const loading = ref(false)
const dateRange = ref<[string, string]>([
  dayjs().subtract(7, 'day').format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD'),
])

// Report types
const reportTypes = [
  { value: 'DAILY', label: '日報表' },
  { value: 'MONTHLY', label: '月報表' },
  { value: 'RECONCILIATION', label: '對帳報表' },
  { value: 'CLEARING', label: '清算報表' },
  { value: 'ERROR_ANALYSIS', label: '錯誤分析' },
]

// Mock daily data
const dailyData = ref([
  { date: '2026-01-01', transactions: 12580, amount: 1258000000, successRate: 99.72, avgResponseTime: 156 },
  { date: '2026-01-02', transactions: 11250, amount: 1125000000, successRate: 99.68, avgResponseTime: 162 },
  { date: '2026-01-03', transactions: 13420, amount: 1342000000, successRate: 99.81, avgResponseTime: 148 },
  { date: '2026-01-04', transactions: 14850, amount: 1485000000, successRate: 99.75, avgResponseTime: 152 },
  { date: '2026-01-05', transactions: 12890, amount: 1289000000, successRate: 99.62, avgResponseTime: 168 },
  { date: '2026-01-06', transactions: 11580, amount: 1158000000, successRate: 99.78, avgResponseTime: 145 },
  { date: '2026-01-07', transactions: 10250, amount: 1025000000, successRate: 99.85, avgResponseTime: 138 },
])

// Chart options
const transactionChartOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
  },
  legend: {
    data: ['交易筆數', '交易金額(萬)'],
    bottom: 0,
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '15%',
    containLabel: true,
  },
  xAxis: {
    type: 'category',
    data: dailyData.value.map((d) => d.date),
  },
  yAxis: [
    {
      type: 'value',
      name: '筆數',
    },
    {
      type: 'value',
      name: '金額(萬)',
    },
  ],
  series: [
    {
      name: '交易筆數',
      type: 'bar',
      data: dailyData.value.map((d) => d.transactions),
      itemStyle: { color: '#409eff' },
    },
    {
      name: '交易金額(萬)',
      type: 'line',
      yAxisIndex: 1,
      data: dailyData.value.map((d) => Math.round(d.amount / 10000)),
      itemStyle: { color: '#67c23a' },
    },
  ],
}))

const successRateChartOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
    formatter: (params: any) => {
      const data = params[0]
      return `${data.name}<br/>成功率: ${data.value}%`
    },
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '3%',
    containLabel: true,
  },
  xAxis: {
    type: 'category',
    data: dailyData.value.map((d) => d.date),
  },
  yAxis: {
    type: 'value',
    min: 99,
    max: 100,
    axisLabel: {
      formatter: '{value}%',
    },
  },
  series: [
    {
      type: 'line',
      data: dailyData.value.map((d) => d.successRate),
      smooth: true,
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: '#67c23a40' },
            { offset: 1, color: '#67c23a00' },
          ],
        },
      },
      lineStyle: { color: '#67c23a' },
      itemStyle: { color: '#67c23a' },
    },
  ],
}))

const channelPieOption = computed(() => ({
  tooltip: {
    trigger: 'item',
  },
  legend: {
    orient: 'vertical',
    right: '5%',
    top: 'center',
  },
  series: [
    {
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['35%', '50%'],
      data: [
        { value: 45230, name: 'ATM', itemStyle: { color: '#409eff' } },
        { value: 38125, name: 'POS', itemStyle: { color: '#67c23a' } },
        { value: 28540, name: '網路銀行', itemStyle: { color: '#e6a23c' } },
        { value: 13952, name: '行動銀行', itemStyle: { color: '#f56c6c' } },
      ],
    },
  ],
}))

// Methods
function handleQuery() {
  loading.value = true
  setTimeout(() => {
    loading.value = false
    ElMessage.success('報表已更新')
  }, 500)
}

function handleExport(format: string) {
  ElMessage.info(`正在匯出 ${format} 格式...`)
}

function formatAmount(amount: number): string {
  if (amount >= 100000000) {
    return (amount / 100000000).toFixed(2) + ' 億'
  } else if (amount >= 10000) {
    return (amount / 10000).toFixed(0) + ' 萬'
  }
  return amount.toLocaleString()
}
</script>

<template>
  <div class="reports-page">
    <!-- Page Header -->
    <div class="page-header">
      <h2>報表統計</h2>
      <div class="header-actions">
        <el-dropdown trigger="click" @command="handleExport">
          <el-button type="primary">
            匯出報表 <el-icon><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="excel">Excel (.xlsx)</el-dropdown-item>
              <el-dropdown-item command="csv">CSV (.csv)</el-dropdown-item>
              <el-dropdown-item command="pdf">PDF (.pdf)</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- Filter -->
    <div class="filter-card">
      <el-form inline>
        <el-form-item label="報表類型">
          <el-select v-model="activeTab" style="width: 140px">
            <el-option v-for="item in reportTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期區間">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            start-placeholder="開始日期"
            end-placeholder="結束日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery" :loading="loading">查詢</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Summary Cards -->
    <el-row :gutter="20" class="summary-row">
      <el-col :xs="12" :sm="6">
        <div class="summary-card">
          <div class="value primary">86,820</div>
          <div class="label">總交易筆數</div>
          <div class="trend up">
            <el-icon><CaretTop /></el-icon> 5.2%
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="summary-card">
          <div class="value success">86.82 億</div>
          <div class="label">總交易金額</div>
          <div class="trend up">
            <el-icon><CaretTop /></el-icon> 3.8%
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="summary-card">
          <div class="value warning">99.74%</div>
          <div class="label">平均成功率</div>
          <div class="trend up">
            <el-icon><CaretTop /></el-icon> 0.02%
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="summary-card">
          <div class="value">153 ms</div>
          <div class="label">平均回應時間</div>
          <div class="trend down">
            <el-icon><CaretBottom /></el-icon> 8 ms
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- Charts -->
    <el-row :gutter="20">
      <el-col :xs="24" :lg="16">
        <div class="chart-card">
          <div class="card-header">
            <span class="title">每日交易統計</span>
          </div>
          <div class="chart-container">
            <VChart :option="transactionChartOption" autoresize />
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :lg="8">
        <div class="chart-card">
          <div class="card-header">
            <span class="title">通道分佈</span>
          </div>
          <div class="chart-container">
            <VChart :option="channelPieOption" autoresize />
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="24">
        <div class="chart-card">
          <div class="card-header">
            <span class="title">成功率趨勢</span>
          </div>
          <div class="chart-container small">
            <VChart :option="successRateChartOption" autoresize />
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- Data Table -->
    <div class="table-card">
      <div class="card-header">
        <span class="title">每日明細</span>
      </div>
      <el-table :data="dailyData" stripe border>
        <el-table-column prop="date" label="日期" width="120" />
        <el-table-column prop="transactions" label="交易筆數" width="120" align="right">
          <template #default="{ row }">
            {{ row.transactions.toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="交易金額" width="140" align="right">
          <template #default="{ row }">
            {{ formatAmount(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column prop="successRate" label="成功率" width="100" align="right">
          <template #default="{ row }">
            <el-tag type="success" size="small">{{ row.successRate }}%</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="avgResponseTime" label="平均回應時間" width="140" align="right">
          <template #default="{ row }">
            {{ row.avgResponseTime }} ms
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default>
            <el-button type="primary" link size="small">詳情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped lang="scss">
.reports-page {
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
  }

  .filter-card {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    margin-bottom: 20px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
  }

  .summary-row {
    margin-bottom: 20px;

    .summary-card {
      background: #fff;
      border-radius: 8px;
      padding: 20px;
      box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
      text-align: center;

      .value {
        font-size: 28px;
        font-weight: 700;
        color: #303133;

        &.primary { color: #409eff; }
        &.success { color: #67c23a; }
        &.warning { color: #e6a23c; }
        &.danger { color: #f56c6c; }
      }

      .label {
        font-size: 14px;
        color: #909399;
        margin-top: 4px;
      }

      .trend {
        font-size: 12px;
        margin-top: 8px;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 4px;

        &.up { color: #67c23a; }
        &.down { color: #f56c6c; }
      }
    }
  }

  .chart-card,
  .table-card {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    margin-bottom: 20px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);

    .card-header {
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

    .chart-container {
      height: 300px;

      &.small {
        height: 200px;
      }
    }
  }
}
</style>
