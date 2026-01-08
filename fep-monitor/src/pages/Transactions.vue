<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { transactionApi } from '@/api/transaction'
import type { Transaction, TransactionFilter, PaginationParams } from '@/types'
import dayjs from 'dayjs'

const router = useRouter()

// State
const loading = ref(false)
const transactions = ref<Transaction[]>([])
const total = ref(0)
const showAdvancedFilter = ref(false)

// Pagination
const pagination = reactive<PaginationParams>({
  page: 1,
  pageSize: 20,
})

// Filter form
const filterForm = reactive<TransactionFilter>({
  transactionId: '',
  transactionType: undefined,
  status: undefined,
  channel: undefined,
  startDate: '',
  endDate: '',
  rrn: '',
  stan: '',
  cardNumber: '',
  terminalId: '',
})

// Date range shortcut
const dateRange = ref<[Date, Date] | null>(null)

// Transaction type options
const transactionTypes = [
  { value: 'WITHDRAWAL', label: '跨行提款' },
  { value: 'TRANSFER', label: '跨行轉帳' },
  { value: 'BALANCE_INQUIRY', label: '餘額查詢' },
  { value: 'BILL_PAYMENT', label: '代繳費用' },
  { value: 'DEPOSIT', label: '跨行存款' },
]

// Status options
const statusOptions = [
  { value: 'APPROVED', label: '成功', type: 'success' },
  { value: 'DECLINED', label: '拒絕', type: 'warning' },
  { value: 'TIMEOUT', label: '逾時', type: 'danger' },
  { value: 'ERROR', label: '錯誤', type: 'danger' },
  { value: 'REVERSED', label: '已沖正', type: 'info' },
  { value: 'PENDING', label: '處理中', type: '' },
]

// Channel options
const channelOptions = [
  { value: 'ATM', label: 'ATM' },
  { value: 'POS', label: 'POS' },
  { value: 'INTERNET_BANKING', label: '網路銀行' },
  { value: 'MOBILE_BANKING', label: '行動銀行' },
]

// Methods
async function fetchTransactions() {
  loading.value = true
  try {
    const filter: TransactionFilter = { ...filterForm }

    // Handle date range
    if (dateRange.value) {
      filter.startDate = dayjs(dateRange.value[0]).format('YYYY-MM-DD')
      filter.endDate = dayjs(dateRange.value[1]).format('YYYY-MM-DD')
    }

    // Remove empty values
    Object.keys(filter).forEach((key) => {
      const k = key as keyof TransactionFilter
      if (!filter[k]) {
        delete filter[k]
      }
    })

    const result = await transactionApi.getTransactions(pagination, filter)
    transactions.value = result.data
    total.value = result.total
  } catch (error) {
    ElMessage.error('查詢交易失敗')
    console.error(error)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchTransactions()
}

function handleReset() {
  Object.assign(filterForm, {
    transactionId: '',
    transactionType: undefined,
    status: undefined,
    channel: undefined,
    startDate: '',
    endDate: '',
    rrn: '',
    stan: '',
    cardNumber: '',
    terminalId: '',
  })
  dateRange.value = null
  pagination.page = 1
  fetchTransactions()
}

function handlePageChange(page: number) {
  pagination.page = page
  fetchTransactions()
}

function handleSizeChange(size: number) {
  pagination.pageSize = size
  pagination.page = 1
  fetchTransactions()
}

function viewDetail(row: Transaction) {
  router.push(`/transactions/${row.transactionId}`)
}

function getStatusType(status: string): string {
  const option = statusOptions.find((o) => o.value === status)
  return option?.type || ''
}

function getStatusLabel(status: string): string {
  const option = statusOptions.find((o) => o.value === status)
  return option?.label || status
}

function getChannelLabel(channel: string): string {
  const option = channelOptions.find((o) => o.value === channel)
  return option?.label || channel
}

function getTypeLabel(type: string): string {
  const option = transactionTypes.find((o) => o.value === type)
  return option?.label || type
}

function formatAmount(amount: number): string {
  return amount.toLocaleString()
}

function formatTime(time: string): string {
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

async function handleExport() {
  try {
    ElMessage.info('正在匯出...')
    // Export functionality would be implemented here
    ElMessage.success('匯出成功')
  } catch (error) {
    ElMessage.error('匯出失敗')
  }
}

onMounted(() => {
  fetchTransactions()
})
</script>

<template>
  <div class="transactions-page">
    <!-- Page Header -->
    <div class="page-header">
      <h2>交易查詢</h2>
      <el-button type="primary" :icon="Download" @click="handleExport">匯出</el-button>
    </div>

    <!-- Filter Form -->
    <div class="filter-card">
      <el-form :model="filterForm" inline label-width="80px">
        <el-form-item label="交易編號">
          <el-input
            v-model="filterForm.transactionId"
            placeholder="請輸入交易編號"
            clearable
            style="width: 180px"
          />
        </el-form-item>

        <el-form-item label="交易類型">
          <el-select
            v-model="filterForm.transactionType"
            placeholder="全部"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="item in transactionTypes"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="交易狀態">
          <el-select
            v-model="filterForm.status"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="通道">
          <el-select
            v-model="filterForm.channel"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="item in channelOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="交易日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            start-placeholder="開始日期"
            end-placeholder="結束日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查詢</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="primary" link @click="showAdvancedFilter = !showAdvancedFilter">
            {{ showAdvancedFilter ? '收起' : '進階' }}
            <el-icon>
              <ArrowUp v-if="showAdvancedFilter" />
              <ArrowDown v-else />
            </el-icon>
          </el-button>
        </el-form-item>
      </el-form>

      <!-- Advanced Filter -->
      <el-collapse-transition>
        <el-form v-show="showAdvancedFilter" :model="filterForm" inline label-width="80px" class="advanced-filter">
          <el-form-item label="RRN">
            <el-input v-model="filterForm.rrn" placeholder="請輸入 RRN" clearable style="width: 180px" />
          </el-form-item>
          <el-form-item label="STAN">
            <el-input v-model="filterForm.stan" placeholder="請輸入 STAN" clearable style="width: 180px" />
          </el-form-item>
          <el-form-item label="卡號">
            <el-input v-model="filterForm.cardNumber" placeholder="請輸入卡號後4碼" clearable style="width: 180px" />
          </el-form-item>
          <el-form-item label="終端代號">
            <el-input v-model="filterForm.terminalId" placeholder="請輸入終端代號" clearable style="width: 180px" />
          </el-form-item>
        </el-form>
      </el-collapse-transition>
    </div>

    <!-- Data Table -->
    <div class="table-card">
      <el-table
        v-loading="loading"
        :data="transactions"
        stripe
        border
        style="width: 100%"
        @row-click="viewDetail"
      >
        <el-table-column prop="transactionId" label="交易編號" width="140" fixed />
        <el-table-column prop="transactionType" label="交易類型" width="100">
          <template #default="{ row }">
            {{ getTypeLabel(row.transactionType) }}
          </template>
        </el-table-column>
        <el-table-column prop="channel" label="通道" width="100">
          <template #default="{ row }">
            {{ getChannelLabel(row.channel) }}
          </template>
        </el-table-column>
        <el-table-column prop="cardNumber" label="卡號" width="140" />
        <el-table-column prop="amount" label="金額" width="120" align="right">
          <template #default="{ row }">
            {{ formatAmount(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="狀態" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="responseCode" label="回應碼" width="80" align="center" />
        <el-table-column prop="rrn" label="RRN" width="140" />
        <el-table-column prop="stan" label="STAN" width="100" />
        <el-table-column prop="terminalId" label="終端代號" width="100" />
        <el-table-column prop="processingTimeMs" label="處理時間" width="100" align="right">
          <template #default="{ row }">
            {{ row.processingTimeMs }} ms
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="交易時間" width="180">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="viewDetail(row)">
              詳情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.transactions-page {
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

    .el-form-item {
      margin-bottom: 12px;
    }

    .advanced-filter {
      padding-top: 12px;
      border-top: 1px dashed #ebeef5;
      margin-top: 12px;
    }
  }

  .table-card {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);

    .el-table {
      cursor: pointer;
    }

    .pagination-wrapper {
      display: flex;
      justify-content: flex-end;
      margin-top: 20px;
    }
  }
}
</style>
