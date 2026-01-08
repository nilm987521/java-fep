<script setup lang="ts">
import { computed } from 'vue'
import type { Alert } from '@/types'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-tw'

dayjs.extend(relativeTime)
dayjs.locale('zh-tw')

interface Props {
  data: Alert[]
}

const props = defineProps<Props>()

const sortedAlerts = computed(() => {
  return [...props.data].sort((a, b) => {
    // Unacknowledged first
    if (a.acknowledged !== b.acknowledged) {
      return a.acknowledged ? 1 : -1
    }
    // Then by severity
    const severityOrder = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 }
    if (severityOrder[a.severity] !== severityOrder[b.severity]) {
      return severityOrder[a.severity] - severityOrder[b.severity]
    }
    // Then by time
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  })
})

function getSeverityType(severity: string): 'danger' | 'warning' | 'info' | 'success' {
  switch (severity) {
    case 'CRITICAL':
      return 'danger'
    case 'HIGH':
      return 'warning'
    case 'MEDIUM':
      return 'info'
    case 'LOW':
      return 'success'
    default:
      return 'info'
  }
}

function getSeverityText(severity: string): string {
  switch (severity) {
    case 'CRITICAL':
      return '嚴重'
    case 'HIGH':
      return '高'
    case 'MEDIUM':
      return '中'
    case 'LOW':
      return '低'
    default:
      return '未知'
  }
}

function getAlertTypeIcon(type: string): string {
  switch (type) {
    case 'SYSTEM_ERROR':
      return 'Warning'
    case 'CONNECTION_LOST':
      return 'Link'
    case 'HIGH_ERROR_RATE':
      return 'CircleClose'
    case 'FRAUD_DETECTED':
      return 'Shield'
    case 'BLACKLIST_MATCH':
      return 'List'
    case 'LIMIT_EXCEEDED':
      return 'Tickets'
    case 'PERFORMANCE_DEGRADATION':
      return 'Odometer'
    default:
      return 'Bell'
  }
}

function formatTime(time: string): string {
  return dayjs(time).fromNow()
}
</script>

<template>
  <div class="alert-list">
    <el-empty v-if="sortedAlerts.length === 0" description="目前沒有告警" />

    <div v-else class="alert-items">
      <div
        v-for="alert in sortedAlerts"
        :key="alert.alertId"
        class="alert-item"
        :class="{ acknowledged: alert.acknowledged }"
      >
        <div class="alert-icon" :class="getSeverityType(alert.severity)">
          <el-icon :size="20">
            <component :is="getAlertTypeIcon(alert.alertType)" />
          </el-icon>
        </div>

        <div class="alert-content">
          <div class="alert-header">
            <span class="alert-title">{{ alert.title }}</span>
            <el-tag :type="getSeverityType(alert.severity)" size="small">
              {{ getSeverityText(alert.severity) }}
            </el-tag>
          </div>
          <div class="alert-message">{{ alert.message }}</div>
          <div class="alert-meta">
            <span class="meta-item">
              <el-icon><Connection /></el-icon>
              {{ alert.source }}
            </span>
            <span class="meta-item">
              <el-icon><Clock /></el-icon>
              {{ formatTime(alert.createdAt) }}
            </span>
            <span v-if="alert.acknowledged" class="meta-item acknowledged">
              <el-icon><CircleCheck /></el-icon>
              已確認 ({{ alert.acknowledgedBy }})
            </span>
          </div>
        </div>

        <div class="alert-actions">
          <el-button
            v-if="!alert.acknowledged"
            type="primary"
            size="small"
            plain
          >
            確認
          </el-button>
          <el-button size="small">詳情</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.alert-list {
  .alert-items {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .alert-item {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 12px;
    background: #fef0f0;
    border-radius: 8px;
    border-left: 4px solid #f56c6c;
    transition: all 0.3s;

    &.acknowledged {
      background: #f5f7fa;
      border-left-color: #909399;
      opacity: 0.8;
    }

    .alert-icon {
      width: 40px;
      height: 40px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      &.danger {
        background: #f56c6c20;
        color: #f56c6c;
      }

      &.warning {
        background: #e6a23c20;
        color: #e6a23c;
      }

      &.info {
        background: #909399;
        color: #909399;
      }

      &.success {
        background: #67c23a20;
        color: #67c23a;
      }
    }

    .alert-content {
      flex: 1;
      min-width: 0;

      .alert-header {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 4px;

        .alert-title {
          font-weight: 600;
          color: #303133;
        }
      }

      .alert-message {
        font-size: 13px;
        color: #606266;
        margin-bottom: 8px;
      }

      .alert-meta {
        display: flex;
        gap: 16px;
        font-size: 12px;
        color: #909399;

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
</style>
