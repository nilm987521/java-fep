<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <el-config-provider :locale="zhTw">
    <RouterView />
  </el-config-provider>
</template>

<script lang="ts">
import zhTw from 'element-plus/es/locale/lang/zh-tw'
export default {
  data() {
    return { zhTw }
  }
}
</script>

<style>
#app {
  width: 100%;
  height: 100vh;
}
</style>
