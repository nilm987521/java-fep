<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { SettlementFile, ClearingRecord } from '@/types'
import dayjs from 'dayjs'

// State
const activeTab = ref('files')
const loading = ref(false)

// Mock settlement files
const settlementFiles = ref<SettlementFile[]>([
  {
    fileId: 'SF-20260107-001',
    fileName: 'FISC_SETTLE_20260107_001.dat',
    fileType: 'DAILY_SETTLEMENT',
    settlementDate: '2026-01-07',
    totalRecords: 12580,
    totalAmount: 1258000000,
    status: 'COMPLETED',
    processedAt: new Date().toISOString(),
  },
  {
    fileId: 'SF-20260107-002',
    fileName: 'FISC_ATM_20260107_001.dat',
    fileType: 'ATM_TRANSACTION',
    settlementDate: '2026-01-07',
    totalRecords: 8542,
    totalAmount: 854200000,
    status: 'COMPLETED',
    processedAt: new Date().toISOString(),
  },
  {
    fileId: 'SF-20260106-001',
    fileName: 'FISC_SETTLE_20260106_001.dat',
    fileType: 'DAILY_SETTLEMENT',
    settlementDate: '2026-01-06',
    totalRecords: 11250,
    totalAmount: 1125000000,
    status: 'COMPLETED',
    processedAt: new Date(Date.now() - 86400000).toISOString(),
  },
])

// Mock clearing records
const clearingRecords = ref<ClearingRecord[]>([
  {
    clearingId: 'CLR-20260107-001',
    counterpartyBank: '0050000 - 台北富邦銀行',
    settlementDate: '2026-01-07',
    debitCount: 2580,
    debitAmount: 258000000,
    creditCount: 1852,
    creditAmount: 185200000,
    netAmount: -72800000,
    status: 'CONFIRMED',
  },
  {
    clearingId: 'CLR-20260107-002',
    counterpartyBank: '0060000 - 國泰世華銀行',
    settlementDate: '2026-01-07',
    debitCount: 1820,
    debitAmount: 182000000,
    creditCount: 2140,
    creditAmount: 214000000,
    netAmount: 32000000,
    status: 'CALCULATED',
  },
  {
    clearingId: 'CLR-20260107-003',
    counterpartyBank: '0070000 - 中國信託銀行',
    settlementDate: '2026-01-07',
    debitCount: 1560,
    debitAmount: 156000000,
    creditCount: 1420,
    creditAmount: 142000000,
    netAmount: -14000000,
    status: 'SETTLED',
  },
])

// Filter
const dateRange = ref<[string, string] | null>(null)

// File type map
const fileTypeMap: Record<string, string> = {
  DAILY_SETTLEMENT: '日清算檔',
  ATM_TRANSACTION: 'ATM交易檔',
  POS_TRANSACTION: 'POS交易檔',
  BILL_PAYMENT: '代繳費用檔',
}

// Status map
const fileStatusMap: Record<string, { label: string; type: string }> = {
  PENDING: { label: '待處理', type: '' },
  PROCESSING: { label: '處理中', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  ERROR: { label: '錯誤', type: 'danger' },
}

const clearingStatusMap: Record<string, { label: string; type: string }> = {
  CALCULATED: { label: '已計算', type: 'info' },
  CONFIRMED: { label: '已確認', type: 'warning' },
  SUBMITTED: { label: '已送出', type: '' },
  SETTLED: { label: '已清算', type: 'success' },
}

// Methods
function formatAmount(amount: number): string {
  return amount.toLocaleString()
}

function formatDate(date: string): string {
  return dayjs(date).format('YYYY-MM-DD')
}

function formatTime(time: string): string {
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

function handleUpload() {
  ElMessage.info('上傳功能開發中')
}

function handleConfirm(record: ClearingRecord) {
  const index = clearingRecords.value.findIndex((r) => r.clearingId === record.clearingId)
  if (index !== -1) {
    clearingRecords.value[index].status = 'CONFIRMED'
    ElMessage.success('清算記錄已確認')
  }
}

function handleSubmit(record: ClearingRecord) {
  const index = clearingRecords.value.findIndex((r) => r.clearingId === record.clearingId)
  if (index !== -1) {
    clearingRecords.value[index].status = 'SUBMITTED'
    ElMessage.success('清算記錄已送出')
  }
}

onMounted(() => {
  // Load data
})
</script>

<template>
  <div class="settlement-page">
    <!-- Page Header -->
    <div class="page-header">
      <h2>清算對帳</h2>
      <el-button type="primary" :icon="Upload" @click="handleUpload">上傳檔案</el-button>
    </div>

    <!-- Tabs -->
    <el-tabs v-model="activeTab">
      <el-tab-pane label="清算檔案" name="files">
        <!-- Filter -->
        <div class="filter-section">
          <el-form inline>
            <el-form-item label="清算日期">
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
              <el-button type="primary" :icon="Search">查詢</el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- File Table -->
        <el-table :data="settlementFiles" stripe border v-loading="loading">
          <el-table-column prop="fileId" label="檔案編號" width="180" />
          <el-table-column prop="fileName" label="檔案名稱" min-width="220" />
          <el-table-column prop="fileType" label="檔案類型" width="120">
            <template #default="{ row }">
              {{ fileTypeMap[row.fileType] || row.fileType }}
            </template>
          </el-table-column>
          <el-table-column prop="settlementDate" label="清算日期" width="120">
            <template #default="{ row }">
              {{ formatDate(row.settlementDate) }}
            </template>
          </el-table-column>
          <el-table-column prop="totalRecords" label="筆數" width="100" align="right">
            <template #default="{ row }">
              {{ formatAmount(row.totalRecords) }}
            </template>
          </el-table-column>
          <el-table-column prop="totalAmount" label="金額" width="140" align="right">
            <template #default="{ row }">
              {{ formatAmount(row.totalAmount) }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="狀態" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="fileStatusMap[row.status]?.type" size="small">
                {{ fileStatusMap[row.status]?.label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="processedAt" label="處理時間" width="180">
            <template #default="{ row }">
              {{ row.processedAt ? formatTime(row.processedAt) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center">
            <template #default>
              <el-button type="primary" link size="small">詳情</el-button>
              <el-button type="primary" link size="small">下載</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="清算記錄" name="clearing">
        <!-- Summary Cards -->
        <el-row :gutter="20" class="summary-row">
          <el-col :xs="12" :sm="6">
            <div class="summary-card">
              <div class="value negative">-72,800,000</div>
              <div class="label">淨應付金額</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="6">
            <div class="summary-card">
              <div class="value positive">32,000,000</div>
              <div class="label">淨應收金額</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="6">
            <div class="summary-card">
              <div class="value">-54,800,000</div>
              <div class="label">淨清算金額</div>
            </div>
          </el-col>
          <el-col :xs="12" :sm="6">
            <div class="summary-card">
              <div class="value">3</div>
              <div class="label">清算記錄數</div>
            </div>
          </el-col>
        </el-row>

        <!-- Clearing Table -->
        <el-table :data="clearingRecords" stripe border v-loading="loading">
          <el-table-column prop="clearingId" label="清算編號" width="180" />
          <el-table-column prop="counterpartyBank" label="對手行" min-width="200" />
          <el-table-column prop="settlementDate" label="清算日期" width="120">
            <template #default="{ row }">
              {{ formatDate(row.settlementDate) }}
            </template>
          </el-table-column>
          <el-table-column label="借方" align="center">
            <el-table-column prop="debitCount" label="筆數" width="80" align="right">
              <template #default="{ row }">
                {{ formatAmount(row.debitCount) }}
              </template>
            </el-table-column>
            <el-table-column prop="debitAmount" label="金額" width="120" align="right">
              <template #default="{ row }">
                {{ formatAmount(row.debitAmount) }}
              </template>
            </el-table-column>
          </el-table-column>
          <el-table-column label="貸方" align="center">
            <el-table-column prop="creditCount" label="筆數" width="80" align="right">
              <template #default="{ row }">
                {{ formatAmount(row.creditCount) }}
              </template>
            </el-table-column>
            <el-table-column prop="creditAmount" label="金額" width="120" align="right">
              <template #default="{ row }">
                {{ formatAmount(row.creditAmount) }}
              </template>
            </el-table-column>
          </el-table-column>
          <el-table-column prop="netAmount" label="淨額" width="120" align="right">
            <template #default="{ row }">
              <span :class="row.netAmount >= 0 ? 'positive' : 'negative'">
                {{ formatAmount(row.netAmount) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="狀態" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="clearingStatusMap[row.status]?.type" size="small">
                {{ clearingStatusMap[row.status]?.label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="center">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'CALCULATED'"
                type="primary"
                size="small"
                @click="handleConfirm(row)"
              >
                確認
              </el-button>
              <el-button
                v-if="row.status === 'CONFIRMED'"
                type="success"
                size="small"
                @click="handleSubmit(row)"
              >
                送出
              </el-button>
              <el-button type="primary" link size="small">詳情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="差異帳" name="discrepancy">
        <el-empty description="沒有差異帳記錄" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.settlement-page {
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

  .el-tabs {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
  }

  .filter-section {
    margin-bottom: 20px;
  }

  .summary-row {
    margin-bottom: 20px;

    .summary-card {
      background: #f5f7fa;
      border-radius: 8px;
      padding: 16px;
      text-align: center;

      .value {
        font-size: 24px;
        font-weight: 700;
        color: #303133;

        &.positive {
          color: #67c23a;
        }

        &.negative {
          color: #f56c6c;
        }
      }

      .label {
        font-size: 14px;
        color: #909399;
        margin-top: 4px;
      }
    }
  }

  .positive {
    color: #67c23a;
  }

  .negative {
    color: #f56c6c;
  }
}
</style>
