import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/pages/Dashboard.vue'),
        meta: { title: '監控儀表板', icon: 'Monitor' },
      },
      {
        path: 'transactions',
        name: 'Transactions',
        component: () => import('@/pages/Transactions.vue'),
        meta: { title: '交易查詢', icon: 'List' },
      },
      {
        path: 'transactions/:id',
        name: 'TransactionDetail',
        component: () => import('@/pages/TransactionDetail.vue'),
        meta: { title: '交易明細', hidden: true },
      },
      {
        path: 'system',
        name: 'System',
        component: () => import('@/pages/System.vue'),
        meta: { title: '系統監控', icon: 'DataLine' },
      },
      {
        path: 'alerts',
        name: 'Alerts',
        component: () => import('@/pages/Alerts.vue'),
        meta: { title: '告警管理', icon: 'Bell' },
      },
      {
        path: 'settlement',
        name: 'Settlement',
        component: () => import('@/pages/Settlement.vue'),
        meta: { title: '清算對帳', icon: 'Document' },
      },
      {
        path: 'reports',
        name: 'Reports',
        component: () => import('@/pages/Reports.vue'),
        meta: { title: '報表統計', icon: 'DataAnalysis' },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/pages/Settings.vue'),
        meta: { title: '系統設定', icon: 'Setting' },
      },
    ],
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/pages/Login.vue'),
    meta: { title: '登入' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/pages/NotFound.vue'),
    meta: { title: '頁面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || 'FEP Monitor'} - FEP 監控管理系統`
  next()
})

export default router
