<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { transactionApi } from '@/api/transaction'
import type { Transaction } from '@/types'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const transaction = ref<Transaction | null>(null)

const statusMap: Record<string, { label: string; type: string }> = {
  APPROVED: { label: '成功', type: 'success' },
  DECLINED: { label: '拒絕', type: 'warning' },
  TIMEOUT: { label: '逾時', type: 'danger' },
  ERROR: { label: '錯誤', type: 'danger' },
  REVERSED: { label: '已沖正', type: 'info' },
  PENDING: { label: '處理中', type: '' },
}

const typeMap: Record<string, string> = {
  WITHDRAWAL: '跨行提款',
  TRANSFER: '跨行轉帳',
  BALANCE_INQUIRY: '餘額查詢',
  BILL_PAYMENT: '代繳費用',
  DEPOSIT: '跨行存款',
}

const channelMap: Record<string, string> = {
  ATM: 'ATM',
  POS: 'POS',
  INTERNET_BANKING: '網路銀行',
  MOBILE_BANKING: '行動銀行',
}

async function fetchTransaction() {
  loading.value = true
  try {
    const id = route.params.id as string
    transaction.value = await transactionApi.getTransaction(id)
  } catch (error) {
    ElMessage.error('載入交易詳情失敗')
    router.push('/transactions')
  } finally {
    loading.value = false
  }
}

async function handleReverse() {
  if (!transaction.value) return

  try {
    await ElMessageBox.confirm(
      `確定要沖正交易 ${transaction.value.transactionId} 嗎？此操作無法復原。`,
      '確認沖正',
      {
        confirmButtonText: '確定沖正',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )

    await transactionApi.reverseTransaction(transaction.value.transactionId, '手動沖正')
    ElMessage.success('沖正成功')
    fetchTransaction()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('沖正失敗')
    }
  }
}

function formatTime(time: string | undefined): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss.SSS')
}

function formatAmount(amount: number): string {
  return amount.toLocaleString()
}

onMounted(() => {
  fetchTransaction()
})
</script>

<template>
  <div class="transaction-detail-page">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" @click="router.push('/transactions')">返回</el-button>
        <h2>交易詳情</h2>
      </div>
      <div class="header-right">
        <el-button
          v-if="transaction && transaction.status === 'APPROVED'"
          type="danger"
          :icon="RefreshRight"
          @click="handleReverse"
        >
          沖正
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="content">
      <template v-if="transaction">
        <!-- Basic Info -->
        <div class="info-card">
          <div class="card-header">
            <span class="title">基本資訊</span>
            <el-tag :type="statusMap[transaction.status]?.type" size="large">
              {{ statusMap[transaction.status]?.label }}
            </el-tag>
          </div>

          <el-descriptions :column="3" border>
            <el-descriptions-item label="交易編號">
              {{ transaction.transactionId }}
            </el-descriptions-item>
            <el-descriptions-item label="交易類型">
              {{ typeMap[transaction.transactionType] || transaction.transactionType }}
            </el-descriptions-item>
            <el-descriptions-item label="通道">
              {{ channelMap[transaction.channel] || transaction.channel }}
            </el-descriptions-item>
            <el-descriptions-item label="金額">
              <span class="amount">{{ formatAmount(transaction.amount) }} {{ transaction.currency }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="回應碼">
              <el-tag :type="transaction.responseCode === '00' ? 'success' : 'danger'" size="small">
                {{ transaction.responseCode }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="處理時間">
              {{ transaction.processingTimeMs }} ms
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- Card & Terminal Info -->
        <div class="info-card">
          <div class="card-header">
            <span class="title">卡片與終端資訊</span>
          </div>

          <el-descriptions :column="3" border>
            <el-descriptions-item label="卡號">
              {{ transaction.cardNumber }}
            </el-descriptions-item>
            <el-descriptions-item label="發卡行代碼">
              {{ transaction.issuingBank }}
            </el-descriptions-item>
            <el-descriptions-item label="收單行代碼">
              {{ transaction.acquiringBank }}
            </el-descriptions-item>
            <el-descriptions-item label="終端代號">
              {{ transaction.terminalId }}
            </el-descriptions-item>
            <el-descriptions-item label="商戶代號">
              {{ transaction.merchantId || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="RRN">
              {{ transaction.rrn }}
            </el-descriptions-item>
            <el-descriptions-item label="STAN">
              {{ transaction.stan }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- Time Info -->
        <div class="info-card">
          <div class="card-header">
            <span class="title">時間資訊</span>
          </div>

          <el-descriptions :column="2" border>
            <el-descriptions-item label="交易發起時間">
              {{ formatTime(transaction.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="交易完成時間">
              {{ formatTime(transaction.completedAt) }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- Processing Timeline -->
        <div class="info-card">
          <div class="card-header">
            <span class="title">處理流程</span>
          </div>

          <el-timeline>
            <el-timeline-item timestamp="接收請求" placement="top" type="primary">
              交易請求已接收
            </el-timeline-item>
            <el-timeline-item timestamp="驗證通過" placement="top" type="primary">
              交易參數驗證通過
            </el-timeline-item>
            <el-timeline-item timestamp="風控檢核" placement="top" type="primary">
              風控規則檢核通過
            </el-timeline-item>
            <el-timeline-item timestamp="發送財金" placement="top" type="primary">
              已發送至財金公司
            </el-timeline-item>
            <el-timeline-item
              timestamp="交易完成"
              placement="top"
              :type="transaction.status === 'APPROVED' ? 'success' : 'danger'"
            >
              交易{{ statusMap[transaction.status]?.label }}
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.transaction-detail-page {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    .header-left {
      display: flex;
      align-items: center;
      gap: 16px;

      h2 {
        margin: 0;
        font-size: 20px;
        font-weight: 600;
        color: #303133;
      }
    }
  }

  .content {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .info-card {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
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

    .amount {
      font-size: 18px;
      font-weight: 600;
      color: #409eff;
    }
  }
}
</style>
