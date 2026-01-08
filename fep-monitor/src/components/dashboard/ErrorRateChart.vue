<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent } from 'echarts/components'
import type { ErrorStats } from '@/types'

use([CanvasRenderer, BarChart, TitleComponent, TooltipComponent, GridComponent])

interface Props {
  data: ErrorStats[]
}

const props = defineProps<Props>()

const option = computed(() => ({
  tooltip: {
    trigger: 'axis',
    axisPointer: {
      type: 'shadow',
    },
    formatter: (params: any) => {
      const item = props.data[params[0].dataIndex]
      return `
        <div style="font-weight: bold">錯誤碼: ${item.errorCode}</div>
        <div>${item.errorMessage}</div>
        <div>次數: ${item.count}</div>
        <div>佔比: ${item.percentage}%</div>
      `
    },
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '3%',
    containLabel: true,
  },
  xAxis: {
    type: 'value',
    axisLabel: {
      formatter: '{value}%',
    },
  },
  yAxis: {
    type: 'category',
    data: props.data.map((item) => item.errorCode).reverse(),
    axisLabel: {
      color: '#606266',
    },
  },
  series: [
    {
      name: '錯誤佔比',
      type: 'bar',
      data: props.data.map((item) => item.percentage).reverse(),
      itemStyle: {
        color: (params: any) => {
          const colors = ['#f56c6c', '#e6a23c', '#409eff', '#67c23a', '#909399', '#c0c4cc']
          return colors[params.dataIndex % colors.length]
        },
        borderRadius: [0, 4, 4, 0],
      },
      label: {
        show: true,
        position: 'right',
        formatter: '{c}%',
        color: '#606266',
      },
      barMaxWidth: 24,
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
