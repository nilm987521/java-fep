<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
} from 'echarts/components'
import type { TransactionStats } from '@/types'

use([
  CanvasRenderer,
  LineChart,
  BarChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
])

interface Props {
  data: TransactionStats[]
}

const props = defineProps<Props>()

const option = computed(() => ({
  tooltip: {
    trigger: 'axis',
    axisPointer: {
      type: 'cross',
      crossStyle: {
        color: '#999',
      },
    },
  },
  legend: {
    data: ['交易筆數', '成功筆數', '平均回應時間'],
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
    data: props.data.map((item) => item.time),
    axisPointer: {
      type: 'shadow',
    },
  },
  yAxis: [
    {
      type: 'value',
      name: '交易筆數',
      axisLabel: {
        formatter: '{value}',
      },
    },
    {
      type: 'value',
      name: '回應時間(ms)',
      axisLabel: {
        formatter: '{value} ms',
      },
    },
  ],
  series: [
    {
      name: '交易筆數',
      type: 'bar',
      data: props.data.map((item) => item.count),
      itemStyle: {
        color: '#409eff',
        borderRadius: [4, 4, 0, 0],
      },
      barMaxWidth: 30,
    },
    {
      name: '成功筆數',
      type: 'bar',
      data: props.data.map((item) => item.successCount),
      itemStyle: {
        color: '#67c23a',
        borderRadius: [4, 4, 0, 0],
      },
      barMaxWidth: 30,
    },
    {
      name: '平均回應時間',
      type: 'line',
      yAxisIndex: 1,
      data: props.data.map((item) => item.avgResponseTime),
      smooth: true,
      lineStyle: {
        color: '#e6a23c',
        width: 2,
      },
      itemStyle: {
        color: '#e6a23c',
      },
    },
  ],
}))
</script>

<template>
  <div class="chart-container">
    <VChart :option="option" autoresize />
  </div>
</template>

<style scoped>
.chart-container {
  width: 100%;
  height: 300px;
}
</style>
