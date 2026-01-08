<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Alert, AlertFilter } from '@/types'
import dayjs from 'dayjs'

// Mock alerts data
const mockAlerts: Alert[] = [
  {
    alertId: 'ALT-001',
    alertType: 'HIGH_ERROR_RATE',
    severity: 'HIGH',
    title: '錯誤率上升',
    message: 'ATM通道錯誤率上升至2.5%，超過閾值1%',
    source: 'fep-transaction',
    transactionId: 'TXN100001',
    acknowledged: false,
    createdAt: new Date(Date.now() - 15 * 60000).toISOString(),
  },
  {
    alertId: 'ALT-002',
    alertType: 'CONNECTION_LOST',
    severity: 'CRITICAL',
    title: '連線中斷',
    message: 'FISC備援線路連線中斷',
    source: 'fep-communication',
    acknowledged: false,
    createdAt: new Date(Date.now() - 30 * 60000).toISOString(),
  },
  {
    alertId: 'ALT-003',
    alertType: 'PERFORMANCE_DEGRADATION',
    severity: 'MEDIUM',
    title: '效能下降',
    message: '平均回應時間上升至300ms，超過閾值200ms',
    source: 'fep-transaction',
    acknowledged: true,
    acknowledgedBy: 'admin',
    acknowledgedAt: new Date(Date.now() - 5 * 60000).toISOString(),
    createdAt: new Date(Date.now() - 60 * 60000).toISOString(),
  },
  {
    alertId: 'ALT-004',
    alertType: 'FRAUD_DETECTED',
    severity: 'HIGH',
    title: '疑似詐騙交易',
    message: '偵測到異常交易模式，卡號 4111****1234，金額 50,000',
    source: 'fep-security',
    transactionId: 'TXN100005',
    acknowledged: false,
    createdAt: new Date(Date.now() - 45 * 60000).toISOString(),
  },
  {
    alertId: 'ALT-005',
    alertType: 'BLACKLIST_MATCH',
    severity: 'HIGH',
    title: '黑名單卡片',
    message: '交易使用黑名單卡片 4111****5678',
    source: 'fep-security',
    transactionId: 'TXN100008',
    acknowledged: true,
    acknowledgedBy: 'operator1',
    acknowledgedAt: new Date(Date.now() - 20 * 60000).toISOString(),
    createdAt: new Date(Date.now() - 90 * 60000).toISOString(),
    resolvedAt: new Date(Date.now() - 10 * 60000).toISOString(),
  },
  {
    alertId: 'ALT-006',
    alertType: 'LIMIT_EXCEEDED',
    severity: 'LOW',
    title: '交易超限',
    message: '終端 ATM10001 今日交易量超過 5000 筆',
    source: 'fep-transaction',
    acknowledged: false,
    createdAt: new Date(Date.now() - 120 * 60000).toISOString(),
  },
]

const loading = ref(false)
const alerts = ref<Alert[]>([...mockAlerts])
const total = ref(mockAlerts.length)

// Filter
const filterForm = reactive<AlertFilter>({
  alertType: undefined,
  severity: undefined,
  acknowledged: undefined,
})

const activeTab = ref('all')

// Options
const alertTypes = [
  { value: 'SYSTEM_ERROR', label: '系統錯誤' },
  { value: 'CONNECTION_LOST', label: '連線中斷' },
  { value: 'HIGH_ERROR_RATE', label: '錯誤率過高' },
  { value: 'FRAUD_DETECTED', label: '詐騙偵測' },
  { value: 'BLACKLIST_MATCH', label: '黑名單比對' },
  { value: 'LIMIT_EXCEEDED', label: '限額超限' },
  { value: 'PERFORMANCE_DEGRADATION', label: '效能下降' },
]

const severityOptions = [
  { value: 'CRITICAL', label: '嚴重', type: 'danger' },
  { value: 'HIGH', label: '高', type: 'warning' },
  { value: 'MEDIUM', label: '中', type: 'info' },
  { value: 'LOW', label: '低', type: 'success' },
]

// Computed
const filteredAlerts = computed(() => {
  let result = [...alerts.value]

  if (activeTab.value === 'unacknowledged') {
    result = result.filter((a) => !a.acknowledged)
  } else if (activeTab.value === 'acknowledged') {
    result = result.filter((a) => a.acknowledged)
  }

  if (filterForm.alertType) {
    result = result.filter((a) => a.alertType === filterForm.alertType)
  }

  if (filterForm.severity) {
    result = result.filter((a) => a.severity === filterForm.severity)
  }

  return result.sort((a, b) => {
    // Sort by severity first
    const severityOrder = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 }
    if (severityOrder[a.severity] !== severityOrder[b.severity]) {
      return severityOrder[a.severity] - severityOrder[b.severity]
    }
    // Then by time
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  })
})

const unacknowledgedCount = computed(() => alerts.value.filter((a) => !a.acknowledged).length)

// Methods
function getSeverityType(severity: string): string {
  const option = severityOptions.find((o) => o.value === severity)
  return option?.type || ''
}

function getSeverityLabel(severity: string): string {
  const option = severityOptions.find((o) => o.value === severity)
  return option?.label || severity
}

function getAlertTypeLabel(type: string): string {
  const option = alertTypes.find((o) => o.value === type)
  return option?.label || type
}

function formatTime(time: string): string {
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

async function handleAcknowledge(alert: Alert) {
  try {
    await ElMessageBox.confirm(`確定要確認告警 ${alert.alertId} 嗎？`, '確認告警', {
      confirmButtonText: '確定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    // Update alert
    const index = alerts.value.findIndex((a) => a.alertId === alert.alertId)
    if (index !== -1) {
      alerts.value[index] = {
        ...alerts.value[index],
        acknowledged: true,
        acknowledgedBy: 'admin',
        acknowledgedAt: new Date().toISOString(),
      }
    }

    ElMessage.success('告警已確認')
  } catch (error) {
    // User cancelled
  }
}

async function handleAcknowledgeAll() {
  const unacknowledged = alerts.value.filter((a) => !a.acknowledged)
  if (unacknowledged.length === 0) {
    ElMessage.info('沒有待確認的告警')
    return
  }

  try {
    await ElMessageBox.confirm(`確定要確認所有 ${unacknowledged.length} 筆待處理告警嗎？`, '確認所有告警', {
      confirmButtonText: '確定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    alerts.value = alerts.value.map((a) =>
      a.acknowledged
        ? a
        : {
            ...a,
            acknowledged: true,
            acknowledgedBy: 'admin',
            acknowledgedAt: new Date().toISOString(),
          }
    )

    ElMessage.success('所有告警已確認')
  } catch (error) {
    // User cancelled
  }
}

function handleReset() {
  filterForm.alertType = undefined
  filterForm.severity = undefined
}

onMounted(() => {
  // Initial data load would happen here
})
</script>

<template>
  <div class="alerts-page">
    <!-- Page Header -->
    <div class="page-header">
      <h2>
        告警管理
        <el-badge v-if="unacknowledgedCount > 0" :value="unacknowledgedCount" class="badge" />
      </h2>
      <el-button type="primary" :icon="Check" @click="handleAcknowledgeAll" :disabled="unacknowledgedCount === 0">
        全部確認
      </el-button>
    </div>

    <!-- Tabs & Filter -->
    <div class="filter-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane name="all">
          <template #label>
            <span>全部 ({{ alerts.length }})</span>
          </template>
        </el-tab-pane>
        <el-tab-pane name="unacknowledged">
          <template #label>
            <span>
              待確認
              <el-badge v-if="unacknowledgedCount > 0" :value="unacknowledgedCount" type="danger" />
            </span>
          </template>
        </el-tab-pane>
        <el-tab-pane name="acknowledged" label="已確認" />
      </el-tabs>

      <el-form :model="filterForm" inline class="filter-form">
        <el-form-item label="告警類型">
          <el-select v-model="filterForm.alertType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="item in alertTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="嚴重程度">
          <el-select v-model="filterForm.severity" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="item in severityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Alert List -->
    <div class="alert-list">
      <el-empty v-if="filteredAlerts.length === 0" description="沒有符合條件的告警" />

      <div v-else class="alert-items">
        <div
          v-for="alert in filteredAlerts"
          :key="alert.alertId"
          class="alert-item"
          :class="[alert.severity.toLowerCase(), { acknowledged: alert.acknowledged }]"
        >
          <div class="alert-severity">
            <el-tag :type="getSeverityType(alert.severity)" size="small" effect="dark">
              {{ getSeverityLabel(alert.severity) }}
            </el-tag>
          </div>

          <div class="alert-content">
            <div class="alert-header">
              <span class="alert-id">{{ alert.alertId }}</span>
              <span class="alert-type">{{ getAlertTypeLabel(alert.alertType) }}</span>
              <span class="alert-title">{{ alert.title }}</span>
            </div>
            <div class="alert-message">{{ alert.message }}</div>
            <div class="alert-meta">
              <span class="meta-item">
                <el-icon><Connection /></el-icon>
                {{ alert.source }}
              </span>
              <span v-if="alert.transactionId" class="meta-item">
                <el-icon><Tickets /></el-icon>
                {{ alert.transactionId }}
              </span>
              <span class="meta-item">
                <el-icon><Clock /></el-icon>
                {{ formatTime(alert.createdAt) }}
              </span>
              <span v-if="alert.acknowledged" class="meta-item acknowledged">
                <el-icon><CircleCheck /></el-icon>
                {{ alert.acknowledgedBy }} 於 {{ formatTime(alert.acknowledgedAt!) }} 確認
              </span>
            </div>
          </div>

          <div class="alert-actions">
            <el-button
              v-if="!alert.acknowledged"
              type="primary"
              size="small"
              @click="handleAcknowledge(alert)"
            >
              確認
            </el-button>
            <el-button size="small">詳情</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.alerts-page {
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
      display: flex;
      align-items: center;
      gap: 8px;

      .badge {
        margin-left: 8px;
      }
    }
  }

  .filter-card {
    background: #fff;
    border-radius: 8px;
    padding: 0 20px 20px;
    margin-bottom: 20px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);

    .filter-form {
      margin-top: 16px;
    }
  }

  .alert-list {
    .alert-items {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .alert-item {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      padding: 16px;
      background: #fff;
      border-radius: 8px;
      box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
      border-left: 4px solid #909399;
      transition: all 0.3s;

      &.critical {
        border-left-color: #f56c6c;
        background: #fef0f0;
      }

      &.high {
        border-left-color: #e6a23c;
        background: #fdf6ec;
      }

      &.medium {
        border-left-color: #409eff;
        background: #ecf5ff;
      }

      &.low {
        border-left-color: #67c23a;
        background: #f0f9eb;
      }

      &.acknowledged {
        opacity: 0.7;
        background: #f5f7fa;
      }

      .alert-severity {
        flex-shrink: 0;
      }

      .alert-content {
        flex: 1;
        min-width: 0;

        .alert-header {
          display: flex;
          align-items: center;
          gap: 8px;
          margin-bottom: 8px;
          flex-wrap: wrap;

          .alert-id {
            font-weight: 600;
            color: #303133;
          }

          .alert-type {
            font-size: 12px;
            color: #909399;
            padding: 2px 8px;
            background: #f5f7fa;
            border-radius: 4px;
          }

          .alert-title {
            font-weight: 600;
            color: #303133;
          }
        }

        .alert-message {
          font-size: 14px;
          color: #606266;
          margin-bottom: 8px;
        }

        .alert-meta {
          display: flex;
          gap: 16px;
          font-size: 12px;
          color: #909399;
          flex-wrap: wrap;

          .meta-item {
            display: flex;
            align-items: center;
            gap: 4px;

            &.acknowledged {
              color: #67c23a;
            }
          }
        }
      }

      .alert-actions {
        display: flex;
        gap: 8px;
        flex-shrink: 0;
      }
    }
  }
}
</style>
