<script setup lang="ts">
import type { SystemStatus } from '@/types'

interface Props {
  data: SystemStatus[]
}

const props = defineProps<Props>()

function getStatusType(status: string): 'success' | 'danger' | 'warning' {
  switch (status) {
    case 'UP':
      return 'success'
    case 'DOWN':
      return 'danger'
    case 'DEGRADED':
      return 'warning'
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
    default:
      return '未知'
  }
}

function getProgressStatus(value: number): 'success' | 'warning' | 'exception' {
  if (value < 60) return 'success'
  if (value < 80) return 'warning'
  return 'exception'
}

function formatUptime(uptime: number): string {
  return uptime.toFixed(2) + '%'
}
</script>

<template>
  <div class="system-status-list">
    <div v-for="item in props.data" :key="item.serviceName" class="status-item">
      <div class="item-header">
        <span class="service-name">{{ item.serviceName }}</span>
        <el-tag :type="getStatusType(item.status)" size="small" effect="dark">
          {{ getStatusText(item.status) }}
        </el-tag>
      </div>

      <div class="item-metrics">
        <div class="metric">
          <span class="metric-label">CPU</span>
          <el-progress
            :percentage="item.cpuUsage"
            :status="getProgressStatus(item.cpuUsage)"
            :stroke-width="6"
            :show-text="false"
          />
          <span class="metric-value">{{ item.cpuUsage }}%</span>
        </div>

        <div class="metric">
          <span class="metric-label">記憶體</span>
          <el-progress
            :percentage="item.memoryUsage"
            :status="getProgressStatus(item.memoryUsage)"
            :stroke-width="6"
            :show-text="false"
          />
          <span class="metric-value">{{ item.memoryUsage }}%</span>
        </div>
      </div>

      <div class="item-footer">
        <span class="footer-item">
          <el-icon><Connection /></el-icon>
          {{ item.activeConnections }} 連線
        </span>
        <span class="footer-item">
          <el-icon><Timer /></el-icon>
          {{ formatUptime(item.uptime) }} 可用率
        </span>
        <span class="footer-item" :class="{ error: item.errorRate > 0 }">
          <el-icon><Warning /></el-icon>
          {{ (item.errorRate * 100).toFixed(2) }}% 錯誤
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.system-status-list {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .status-item {
    padding: 12px;
    background: #f5f7fa;
    border-radius: 8px;

    .item-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;

      .service-name {
        font-weight: 600;
        color: #303133;
      }
    }

    .item-metrics {
      display: flex;
      gap: 20px;
      margin-bottom: 12px;

      .metric {
        flex: 1;
        display: flex;
        align-items: center;
        gap: 8px;

        .metric-label {
          font-size: 12px;
          color: #909399;
          width: 40px;
        }

        .el-progress {
          flex: 1;
        }

        .metric-value {
          font-size: 12px;
          color: #606266;
          width: 40px;
          text-align: right;
        }
      }
    }

    .item-footer {
      display: flex;
      gap: 16px;
      font-size: 12px;
      color: #909399;

      .footer-item {
        display: flex;
        align-items: center;
        gap: 4px;

        &.error {
          color: #f56c6c;
        }
      }
    }
  }
}
</style>
