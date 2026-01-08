<script setup lang="ts">
interface Trend {
  value: number
  isUp: boolean
}

interface Props {
  title: string
  value: string
  subValue?: string
  icon: string
  color?: string
  highlight?: boolean
  trend?: Trend
}

const props = withDefaults(defineProps<Props>(), {
  color: '#409eff',
  highlight: false,
})
</script>

<template>
  <div class="stats-card" :class="{ highlight: props.highlight }">
    <div class="card-content">
      <div class="stats-info">
        <div class="stats-value">{{ props.value }}</div>
        <div class="stats-title">{{ props.title }}</div>
        <div v-if="props.subValue" class="stats-sub">{{ props.subValue }}</div>
        <div v-if="props.trend" class="stats-trend" :class="props.trend.isUp ? 'up' : 'down'">
          <el-icon>
            <CaretTop v-if="props.trend.isUp" />
            <CaretBottom v-else />
          </el-icon>
          <span>{{ props.trend.value }}%</span>
        </div>
      </div>
      <div class="stats-icon" :style="{ backgroundColor: props.color + '15', color: props.color }">
        <el-icon :size="28">
          <component :is="props.icon" />
        </el-icon>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.stats-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
  margin-bottom: 20px;
  transition: all 0.3s;

  &:hover {
    box-shadow: 0 4px 16px 0 rgba(0, 0, 0, 0.1);
  }

  &.highlight {
    border-left: 4px solid #f56c6c;
    animation: pulse-highlight 2s infinite;
  }

  .card-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .stats-info {
    .stats-value {
      font-size: 28px;
      font-weight: 700;
      color: #303133;
      line-height: 1.2;
    }

    .stats-title {
      font-size: 14px;
      color: #909399;
      margin-top: 8px;
    }

    .stats-sub {
      font-size: 12px;
      color: #c0c4cc;
      margin-top: 4px;
    }

    .stats-trend {
      display: flex;
      align-items: center;
      gap: 4px;
      margin-top: 8px;
      font-size: 12px;

      &.up {
        color: #67c23a;
      }

      &.down {
        color: #f56c6c;
      }
    }
  }

  .stats-icon {
    width: 56px;
    height: 56px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

@keyframes pulse-highlight {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.8; }
}
</style>
