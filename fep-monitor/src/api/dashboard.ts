import { request } from './request'
import type {
  DashboardStats,
  TransactionStats,
  ChannelStats,
  ErrorStats,
  SystemStatus,
  Alert,
} from '@/types'

// Mock data for development
const mockStats: DashboardStats = {
  totalTransactions: 125847,
  successRate: 99.72,
  averageResponseTime: 156,
  currentTps: 287,
  peakTps: 1523,
  totalAmount: 89563214500,
  activeAlerts: 3,
  systemHealth: 'HEALTHY',
}

const mockTransactionStats: TransactionStats[] = Array.from({ length: 24 }, (_, i) => ({
  time: `${i.toString().padStart(2, '0')}:00`,
  count: Math.floor(Math.random() * 5000) + 2000,
  successCount: Math.floor(Math.random() * 4800) + 1900,
  failCount: Math.floor(Math.random() * 200) + 50,
  avgResponseTime: Math.floor(Math.random() * 100) + 100,
  amount: Math.floor(Math.random() * 10000000) + 5000000,
}))

const mockChannelStats: ChannelStats[] = [
  { channel: 'ATM', count: 45230, amount: 3521000000, successRate: 99.85 },
  { channel: 'POS', count: 38125, amount: 1852000000, successRate: 99.62 },
  { channel: 'INTERNET_BANKING', count: 28540, amount: 2815000000, successRate: 99.78 },
  { channel: 'MOBILE_BANKING', count: 13952, amount: 1375000000, successRate: 99.91 },
]

const mockErrorStats: ErrorStats[] = [
  { errorCode: '05', errorMessage: '不予承兌', count: 125, percentage: 28.5 },
  { errorCode: '51', errorMessage: '餘額不足', count: 98, percentage: 22.3 },
  { errorCode: '14', errorMessage: '無效卡號', count: 76, percentage: 17.3 },
  { errorCode: '54', errorMessage: '卡片已過期', count: 62, percentage: 14.1 },
  { errorCode: '91', errorMessage: '發卡行無法連線', count: 45, percentage: 10.2 },
  { errorCode: '其他', errorMessage: '其他錯誤', count: 33, percentage: 7.6 },
]

const mockSystemStatuses: SystemStatus[] = [
  {
    serviceName: 'fep-transaction',
    status: 'UP',
    uptime: 99.99,
    lastHeartbeat: new Date().toISOString(),
    cpuUsage: 45,
    memoryUsage: 62,
    activeConnections: 128,
    errorRate: 0.02,
  },
  {
    serviceName: 'fep-communication',
    status: 'UP',
    uptime: 99.98,
    lastHeartbeat: new Date().toISOString(),
    cpuUsage: 38,
    memoryUsage: 55,
    activeConnections: 24,
    errorRate: 0.01,
  },
  {
    serviceName: 'fep-security',
    status: 'UP',
    uptime: 100,
    lastHeartbeat: new Date().toISOString(),
    cpuUsage: 22,
    memoryUsage: 40,
    activeConnections: 64,
    errorRate: 0,
  },
  {
    serviceName: 'fep-settlement',
    status: 'UP',
    uptime: 99.95,
    lastHeartbeat: new Date().toISOString(),
    cpuUsage: 15,
    memoryUsage: 35,
    activeConnections: 8,
    errorRate: 0,
  },
]

const mockAlerts: Alert[] = [
  {
    alertId: 'ALT-001',
    alertType: 'HIGH_ERROR_RATE',
    severity: 'HIGH',
    title: '錯誤率上升',
    message: 'ATM通道錯誤率上升至2.5%，超過閾值1%',
    source: 'fep-transaction',
    acknowledged: false,
    createdAt: new Date(Date.now() - 15 * 60000).toISOString(),
  },
  {
    alertId: 'ALT-002',
    alertType: 'CONNECTION_LOST',
    severity: 'CRITICAL',
    title: '連線中斷',
    message: 'FISC備援線路連線中斷',
    source: 'fep-communication',
    acknowledged: false,
    createdAt: new Date(Date.now() - 30 * 60000).toISOString(),
  },
  {
    alertId: 'ALT-003',
    alertType: 'PERFORMANCE_DEGRADATION',
    severity: 'MEDIUM',
    title: '效能下降',
    message: '平均回應時間上升至300ms，超過閾值200ms',
    source: 'fep-transaction',
    acknowledged: true,
    acknowledgedBy: 'admin',
    acknowledgedAt: new Date(Date.now() - 5 * 60000).toISOString(),
    createdAt: new Date(Date.now() - 60 * 60000).toISOString(),
  },
]

// Use mock data in development, real API in production
const useMock = import.meta.env.DEV

export const dashboardApi = {
  async getStats(): Promise<DashboardStats> {
    if (useMock) {
      return Promise.resolve({ ...mockStats })
    }
    return request.get<DashboardStats>('/dashboard/stats')
  },

  async getTransactionStats(hours: number = 24): Promise<TransactionStats[]> {
    if (useMock) {
      return Promise.resolve([...mockTransactionStats])
    }
    return request.get<TransactionStats[]>('/dashboard/transaction-stats', {
      params: { hours },
    })
  },

  async getChannelStats(): Promise<ChannelStats[]> {
    if (useMock) {
      return Promise.resolve([...mockChannelStats])
    }
    return request.get<ChannelStats[]>('/dashboard/channel-stats')
  },

  async getErrorStats(): Promise<ErrorStats[]> {
    if (useMock) {
      return Promise.resolve([...mockErrorStats])
    }
    return request.get<ErrorStats[]>('/dashboard/error-stats')
  },

  async getSystemStatus(): Promise<SystemStatus[]> {
    if (useMock) {
      return Promise.resolve([...mockSystemStatuses])
    }
    return request.get<SystemStatus[]>('/dashboard/system-status')
  },

  async getRecentAlerts(limit: number = 10): Promise<Alert[]> {
    if (useMock) {
      return Promise.resolve([...mockAlerts])
    }
    return request.get<Alert[]>('/dashboard/alerts', {
      params: { limit },
    })
  },
}
