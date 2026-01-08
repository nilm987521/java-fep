// Type definitions for FEP Monitor

// Transaction types
export interface Transaction {
  transactionId: string
  transactionType: TransactionType
  amount: number
  currency: string
  status: TransactionStatus
  responseCode: string
  cardNumber: string
  terminalId: string
  merchantId?: string
  issuingBank: string
  acquiringBank: string
  rrn: string
  stan: string
  channel: Channel
  createdAt: string
  completedAt?: string
  processingTimeMs: number
}

export type TransactionType =
  | 'WITHDRAWAL'
  | 'TRANSFER'
  | 'BALANCE_INQUIRY'
  | 'BILL_PAYMENT'
  | 'DEPOSIT'
  | 'PIN_CHANGE'

export type TransactionStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'DECLINED'
  | 'TIMEOUT'
  | 'ERROR'
  | 'REVERSED'

export type Channel = 'ATM' | 'POS' | 'INTERNET_BANKING' | 'MOBILE_BANKING'

// Alert types
export interface Alert {
  alertId: string
  alertType: AlertType
  severity: Severity
  title: string
  message: string
  source: string
  transactionId?: string
  acknowledged: boolean
  acknowledgedBy?: string
  acknowledgedAt?: string
  createdAt: string
  resolvedAt?: string
}

export type AlertType =
  | 'SYSTEM_ERROR'
  | 'CONNECTION_LOST'
  | 'HIGH_ERROR_RATE'
  | 'FRAUD_DETECTED'
  | 'BLACKLIST_MATCH'
  | 'LIMIT_EXCEEDED'
  | 'PERFORMANCE_DEGRADATION'

export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

// System status
export interface SystemStatus {
  serviceName: string
  status: 'UP' | 'DOWN' | 'DEGRADED'
  uptime: number
  lastHeartbeat: string
  cpuUsage: number
  memoryUsage: number
  activeConnections: number
  errorRate: number
}

// Connection status
export interface ConnectionStatus {
  connectionId: string
  connectionType: 'FISC' | 'CBS' | 'HSM' | 'DATABASE'
  target: string
  status: 'CONNECTED' | 'DISCONNECTED' | 'RECONNECTING'
  lastActivity: string
  sentMessages: number
  receivedMessages: number
  errorCount: number
}

// Dashboard statistics
export interface DashboardStats {
  totalTransactions: number
  successRate: number
  averageResponseTime: number
  currentTps: number
  peakTps: number
  totalAmount: number
  activeAlerts: number
  systemHealth: 'HEALTHY' | 'DEGRADED' | 'CRITICAL'
}

// Transaction stats by time
export interface TransactionStats {
  time: string
  count: number
  successCount: number
  failCount: number
  avgResponseTime: number
  amount: number
}

// Channel stats
export interface ChannelStats {
  channel: Channel
  count: number
  amount: number
  successRate: number
}

// Error stats
export interface ErrorStats {
  errorCode: string
  errorMessage: string
  count: number
  percentage: number
}

// Settlement types
export interface SettlementFile {
  fileId: string
  fileName: string
  fileType: string
  settlementDate: string
  totalRecords: number
  totalAmount: number
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'ERROR'
  processedAt?: string
}

export interface ClearingRecord {
  clearingId: string
  counterpartyBank: string
  settlementDate: string
  debitCount: number
  debitAmount: number
  creditCount: number
  creditAmount: number
  netAmount: number
  status: 'CALCULATED' | 'CONFIRMED' | 'SUBMITTED' | 'SETTLED'
}

// Report types
export interface Report {
  reportId: string
  reportType: string
  reportDate: string
  generatedAt: string
  totalRecords: number
  matchedRecords: number
  discrepancyRecords: number
  matchRate: number
}

// Pagination
export interface PaginationParams {
  page: number
  pageSize: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface PaginatedResult<T> {
  data: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

// API Response
export interface ApiResponse<T> {
  success: boolean
  data?: T
  message?: string
  errorCode?: string
}

// Query filters
export interface TransactionFilter {
  transactionId?: string
  transactionType?: TransactionType
  status?: TransactionStatus
  channel?: Channel
  startDate?: string
  endDate?: string
  minAmount?: number
  maxAmount?: number
  cardNumber?: string
  terminalId?: string
  rrn?: string
  stan?: string
}

export interface AlertFilter {
  alertType?: AlertType
  severity?: Severity
  acknowledged?: boolean
  startDate?: string
  endDate?: string
}
