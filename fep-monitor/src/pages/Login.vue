<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
  remember: false,
})

const rules: FormRules = {
  username: [
    { required: true, message: '請輸入使用者名稱', trigger: 'blur' },
    { min: 3, max: 20, message: '使用者名稱長度為 3 到 20 個字元', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '請輸入密碼', trigger: 'blur' },
    { min: 6, max: 20, message: '密碼長度為 6 到 20 個字元', trigger: 'blur' },
  ],
}

async function handleLogin() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      await userStore.login({
        username: loginForm.username,
        password: loginForm.password,
      })
      ElMessage.success('登入成功')
      router.push('/')
    } catch (error) {
      ElMessage.error('登入失敗，請檢查帳號密碼')
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-header">
        <div class="logo">
          <el-icon :size="48"><Monitor /></el-icon>
        </div>
        <h1>FEP Monitor</h1>
        <p>金融電子支付前端處理器監控系統</p>
      </div>

      <el-form
        ref="formRef"
        :model="loginForm"
        :rules="rules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="使用者名稱"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密碼"
            :prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <div class="form-options">
            <el-checkbox v-model="loginForm.remember">記住我</el-checkbox>
            <el-button type="primary" link>忘記密碼?</el-button>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            size="large"
            :loading="loading"
            class="login-button"
          >
            登入
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <p>版本 1.0.0 | 2026 FEP System</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);

  .login-container {
    width: 400px;
    padding: 40px;
    background: #fff;
    border-radius: 16px;
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);

    .login-header {
      text-align: center;
      margin-bottom: 32px;

      .logo {
        width: 80px;
        height: 80px;
        margin: 0 auto 16px;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        border-radius: 16px;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #fff;
      }

      h1 {
        font-size: 24px;
        font-weight: 600;
        color: #303133;
        margin: 0 0 8px;
      }

      p {
        font-size: 14px;
        color: #909399;
        margin: 0;
      }
    }

    .login-form {
      .form-options {
        width: 100%;
        display: flex;
        justify-content: space-between;
        align-items: center;
      }

      .login-button {
        width: 100%;
      }
    }

    .login-footer {
      margin-top: 24px;
      text-align: center;

      p {
        font-size: 12px;
        color: #c0c4cc;
        margin: 0;
      }
    }
  }
}
</style>
