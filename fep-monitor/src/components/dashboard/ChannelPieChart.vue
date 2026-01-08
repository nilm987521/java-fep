<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import type { ChannelStats } from '@/types'

use([CanvasRenderer, PieChart, TitleComponent, TooltipComponent, LegendComponent])

interface Props {
  data: ChannelStats[]
}

const props = defineProps<Props>()

const channelNames: Record<string, string> = {
  ATM: 'ATM',
  POS: 'POS',
  INTERNET_BANKING: '網路銀行',
  MOBILE_BANKING: '行動銀行',
}

const option = computed(() => ({
  tooltip: {
    trigger: 'item',
    formatter: (params: any) => {
      const item = props.data.find((d) => channelNames[d.channel] === params.name)
      if (!item) return ''
      return `
        <div style="font-weight: bold">${params.name}</div>
        <div>筆數: ${item.count.toLocaleString()}</div>
        <div>金額: ${(item.amount / 100000000).toFixed(2)} 億</div>
        <div>成功率: ${item.successRate}%</div>
        <div>佔比: ${params.percent}%</div>
      `
    },
  },
  legend: {
    orient: 'vertical',
    right: '5%',
    top: 'center',
    formatter: (name: string) => {
      const item = props.data.find((d) => channelNames[d.channel] === name)
      if (!item) return name
      return `${name}  ${item.count.toLocaleString()}`
    },
  },
  series: [
    {
      name: '通道分佈',
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['35%', '50%'],
      avoidLabelOverlap: false,
      itemStyle: {
        borderRadius: 8,
        borderColor: '#fff',
        borderWidth: 2,
      },
      label: {
        show: false,
      },
      emphasis: {
        label: {
          show: true,
          fontSize: 16,
          fontWeight: 'bold',
        },
      },
      data: props.data.map((item, index) => ({
        value: item.count,
        name: channelNames[item.channel] || item.channel,
        itemStyle: {
          color: ['#409eff', '#67c23a', '#e6a23c', '#f56c6c'][index % 4],
        },
      })),
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
