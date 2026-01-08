<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const isCollapsed = ref(false)
const activeMenu = computed(() => route.path)

const menuItems = [
  { path: '/dashboard', title: '監控儀表板', icon: 'Monitor' },
  { path: '/transactions', title: '交易查詢', icon: 'List' },
  { path: '/system', title: '系統監控', icon: 'DataLine' },
  { path: '/alerts', title: '告警管理', icon: 'Bell' },
  { path: '/settlement', title: '清算對帳', icon: 'Document' },
  { path: '/reports', title: '報表統計', icon: 'DataAnalysis' },
  { path: '/settings', title: '系統設定', icon: 'Setting' },
]

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}

const handleMenuSelect = (path: string) => {
  router.push(path)
}
</script>

<template>
  <el-container class="main-layout">
    <!-- Sidebar -->
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="sidebar">
      <div class="logo" @click="router.push('/')">
        <el-icon :size="28"><Monitor /></el-icon>
        <span v-show="!isCollapsed" class="logo-text">FEP Monitor</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapsed"
        background-color="#001529"
        text-color="#ffffffa6"
        active-text-color="#ffffff"
        @select="handleMenuSelect"
      >
        <el-menu-item
          v-for="item in menuItems"
          :key="item.path"
          :index="item.path"
        >
          <el-icon>
            <component :is="item.icon" />
          </el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- Main content -->
    <el-container>
      <!-- Header -->
      <el-header class="header">
        <div class="header-left">
          <el-icon
            class="collapse-btn"
            :size="20"
            @click="toggleCollapse"
          >
            <Fold v-if="!isCollapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首頁</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <!-- System status indicator -->
          <el-tooltip content="系統狀態: 正常" placement="bottom">
            <div class="status-indicator online">
              <span class="dot"></span>
              <span>在線</span>
            </div>
          </el-tooltip>

          <!-- Notifications -->
          <el-badge :value="3" class="notification-badge">
            <el-button :icon="Bell" circle />
          </el-badge>

          <!-- User dropdown -->
          <el-dropdown @command="handleLogout">
            <div class="user-info">
              <el-avatar :size="32" icon="UserFilled" />
              <span class="username">{{ userStore.username || 'Admin' }}</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>個人設定</el-dropdown-item>
                <el-dropdown-item divided command="logout">登出</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- Main content area -->
      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped lang="scss">
.main-layout {
  height: 100vh;
}

.sidebar {
  background-color: #001529;
  transition: width 0.3s;
  overflow: hidden;

  .logo {
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    cursor: pointer;
    color: #fff;
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);

    .logo-text {
      font-size: 18px;
      font-weight: 600;
      white-space: nowrap;
    }
  }

  .el-menu {
    border: none;

    .el-menu-item {
      &:hover {
        background-color: rgba(255, 255, 255, 0.05);
      }

      &.is-active {
        background-color: #1890ff;
      }
    }
  }
}

.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  z-index: 10;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .collapse-btn {
      cursor: pointer;
      color: #303133;
      transition: color 0.2s;

      &:hover {
        color: #409eff;
      }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 20px;

    .status-indicator {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 14px;

      .dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        animation: pulse 2s infinite;
      }

      &.online {
        color: #67c23a;
        .dot { background-color: #67c23a; }
      }

      &.offline {
        color: #f56c6c;
        .dot { background-color: #f56c6c; }
      }
    }

    .notification-badge {
      .el-button {
        border: none;
      }
    }

    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;

      .username {
        color: #303133;
        font-size: 14px;
      }
    }
  }
}

.main-content {
  background-color: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
</style>
