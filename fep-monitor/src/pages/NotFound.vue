<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goBack() {
  router.back()
}

function goHome() {
  router.push('/')
}
</script>

<template>
  <div class="not-found-page">
    <div class="content">
      <div class="error-code">404</div>
      <h1>頁面不存在</h1>
      <p>抱歉，您要找的頁面不存在或已被移除。</p>
      <div class="actions">
        <el-button type="primary" @click="goHome">返回首頁</el-button>
        <el-button @click="goBack">返回上一頁</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.not-found-page {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;

  .content {
    text-align: center;

    .error-code {
      font-size: 120px;
      font-weight: 700;
      color: #409eff;
      line-height: 1;
      margin-bottom: 20px;
    }

    h1 {
      font-size: 24px;
      font-weight: 600;
      color: #303133;
      margin: 0 0 12px;
    }

    p {
      font-size: 16px;
      color: #909399;
      margin: 0 0 32px;
    }

    .actions {
      display: flex;
      gap: 16px;
      justify-content: center;
    }
  }
}
</style>
