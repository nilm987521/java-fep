<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'

const activeTab = ref('general')

// General settings
const generalForm = reactive({
  refreshInterval: 30,
  alertNotification: true,
  soundAlert: false,
  darkMode: false,
  language: 'zh-TW',
})

// Alert thresholds
const alertForm = reactive({
  errorRateThreshold: 1,
  responseTimeThreshold: 200,
  tpsLowThreshold: 100,
  tpsHighThreshold: 2000,
  connectionTimeoutSec: 30,
})

// System parameters
const systemParams = ref([
  { key: 'MAX_TPS', value: '2000', description: '最大 TPS 限制' },
  { key: 'TIMEOUT_MS', value: '30000', description: '交易逾時時間(毫秒)' },
  { key: 'RETRY_COUNT', value: '3', description: '重試次數' },
  { key: 'HEARTBEAT_INTERVAL', value: '10', description: '心跳間隔(秒)' },
])

const generalRules = {
  refreshInterval: [{ required: true, message: '請輸入更新間隔', trigger: 'blur' }],
}

const alertRules = {
  errorRateThreshold: [{ required: true, message: '請輸入錯誤率閾值', trigger: 'blur' }],
  responseTimeThreshold: [{ required: true, message: '請輸入回應時間閾值', trigger: 'blur' }],
}

function handleSaveGeneral() {
  ElMessage.success('一般設定已儲存')
}

function handleSaveAlert() {
  ElMessage.success('告警設定已儲存')
}

function handleSaveParams() {
  ElMessage.success('系統參數已儲存')
}
</script>

<template>
  <div class="settings-page">
    <!-- Page Header -->
    <div class="page-header">
      <h2>系統設定</h2>
    </div>

    <el-tabs v-model="activeTab" tab-position="left">
      <!-- General Settings -->
      <el-tab-pane label="一般設定" name="general">
        <div class="settings-section">
          <h3>一般設定</h3>
          <el-form :model="generalForm" :rules="generalRules" label-width="140px">
            <el-form-item label="自動更新間隔" prop="refreshInterval">
              <el-input-number v-model="generalForm.refreshInterval" :min="5" :max="300" />
              <span class="unit">秒</span>
            </el-form-item>
            <el-form-item label="告警通知">
              <el-switch v-model="generalForm.alertNotification" />
            </el-form-item>
            <el-form-item label="聲音提醒">
              <el-switch v-model="generalForm.soundAlert" />
            </el-form-item>
            <el-form-item label="深色模式">
              <el-switch v-model="generalForm.darkMode" />
            </el-form-item>
            <el-form-item label="語言">
              <el-select v-model="generalForm.language" style="width: 200px">
                <el-option label="繁體中文" value="zh-TW" />
                <el-option label="English" value="en" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSaveGeneral">儲存設定</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- Alert Settings -->
      <el-tab-pane label="告警設定" name="alert">
        <div class="settings-section">
          <h3>告警閾值設定</h3>
          <el-form :model="alertForm" :rules="alertRules" label-width="140px">
            <el-form-item label="錯誤率閾值" prop="errorRateThreshold">
              <el-input-number v-model="alertForm.errorRateThreshold" :min="0.1" :max="10" :precision="1" :step="0.1" />
              <span class="unit">%</span>
            </el-form-item>
            <el-form-item label="回應時間閾值" prop="responseTimeThreshold">
              <el-input-number v-model="alertForm.responseTimeThreshold" :min="50" :max="5000" :step="50" />
              <span class="unit">ms</span>
            </el-form-item>
            <el-form-item label="TPS 下限">
              <el-input-number v-model="alertForm.tpsLowThreshold" :min="0" :max="1000" :step="10" />
            </el-form-item>
            <el-form-item label="TPS 上限">
              <el-input-number v-model="alertForm.tpsHighThreshold" :min="500" :max="10000" :step="100" />
            </el-form-item>
            <el-form-item label="連線逾時">
              <el-input-number v-model="alertForm.connectionTimeoutSec" :min="5" :max="120" />
              <span class="unit">秒</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSaveAlert">儲存設定</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- System Parameters -->
      <el-tab-pane label="系統參數" name="params">
        <div class="settings-section">
          <h3>系統參數</h3>
          <el-table :data="systemParams" stripe border>
            <el-table-column prop="key" label="參數名稱" width="200" />
            <el-table-column prop="value" label="參數值" width="200">
              <template #default="{ row }">
                <el-input v-model="row.value" size="small" />
              </template>
            </el-table-column>
            <el-table-column prop="description" label="說明" />
          </el-table>
          <div class="action-buttons">
            <el-button type="primary" @click="handleSaveParams">儲存參數</el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- About -->
      <el-tab-pane label="關於" name="about">
        <div class="settings-section">
          <h3>關於 FEP Monitor</h3>
          <div class="about-info">
            <p><strong>版本:</strong> 1.0.0</p>
            <p><strong>建置時間:</strong> 2026-01-07</p>
            <p><strong>技術架構:</strong> Vue 3 + Element Plus + TypeScript</p>
            <p><strong>後端服務:</strong> Spring Boot 3.x + Java 21</p>
          </div>
          <el-divider />
          <h4>系統資訊</h4>
          <div class="system-info">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="前端版本">1.0.0</el-descriptions-item>
              <el-descriptions-item label="API 版本">v1</el-descriptions-item>
              <el-descriptions-item label="後端版本">1.0.0-SNAPSHOT</el-descriptions-item>
              <el-descriptions-item label="資料庫">Oracle 19c</el-descriptions-item>
            </el-descriptions>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.settings-page {
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

  .el-tabs {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
    min-height: 600px;

    :deep(.el-tabs__content) {
      padding-left: 40px;
    }
  }

  .settings-section {
    max-width: 800px;

    h3 {
      font-size: 18px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 24px;
      padding-bottom: 12px;
      border-bottom: 1px solid #ebeef5;
    }

    h4 {
      font-size: 16px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 16px;
    }

    .unit {
      margin-left: 8px;
      color: #909399;
    }

    .action-buttons {
      margin-top: 20px;
    }

    .about-info {
      p {
        margin: 8px 0;
        color: #606266;
      }
    }

    .system-info {
      margin-top: 16px;
    }
  }
}
</style>
