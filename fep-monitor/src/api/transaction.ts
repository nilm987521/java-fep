import { request } from './request'
import type { Transaction, TransactionFilter, PaginatedResult, PaginationParams } from '@/types'

// Mock transactions data
const mockTransactions: Transaction[] = Array.from({ length: 100 }, (_, i) => ({
  transactionId: `TXN${(100000 + i).toString()}`,
  transactionType: ['WITHDRAWAL', 'TRANSFER', 'BALANCE_INQUIRY', 'BILL_PAYMENT'][i % 4] as Transaction['transactionType'],
  amount: Math.floor(Math.random() * 50000) + 1000,
  currency: 'TWD',
  status: ['APPROVED', 'APPROVED', 'APPROVED', 'DECLINED', 'ERROR'][i % 5] as Transaction['status'],
  responseCode: i % 5 === 0 ? '05' : i % 5 === 4 ? '91' : '00',
  cardNumber: `4111****${(1111 + i).toString().slice(-4)}`,
  terminalId: `ATM${(10001 + (i % 50)).toString()}`,
  merchantId: i % 4 === 1 ? `MID${(20001 + (i % 30)).toString()}` : undefined,
  issuingBank: '0040000',
  acquiringBank: ['0050000', '0060000', '0070000'][i % 3],
  rrn: `RRN${(300000 + i).toString()}`,
  stan: (100000 + i).toString(),
  channel: ['ATM', 'POS', 'INTERNET_BANKING', 'MOBILE_BANKING'][i % 4] as Transaction['channel'],
  createdAt: new Date(Date.now() - i * 60000).toISOString(),
  completedAt: i % 5 !== 4 ? new Date(Date.now() - i * 60000 + 200).toISOString() : undefined,
  processingTimeMs: Math.floor(Math.random() * 200) + 100,
}))

const useMock = import.meta.env.DEV

export const transactionApi = {
  async getTransactions(
    params: PaginationParams,
    filter?: TransactionFilter
  ): Promise<PaginatedResult<Transaction>> {
    if (useMock) {
      let filtered = [...mockTransactions]

      // Apply filters
      if (filter?.transactionId) {
        filtered = filtered.filter((t) => t.transactionId.includes(filter.transactionId!))
      }
      if (filter?.transactionType) {
        filtered = filtered.filter((t) => t.transactionType === filter.transactionType)
      }
      if (filter?.status) {
        filtered = filtered.filter((t) => t.status === filter.status)
      }
      if (filter?.channel) {
        filtered = filtered.filter((t) => t.channel === filter.channel)
      }
      if (filter?.rrn) {
        filtered = filtered.filter((t) => t.rrn.includes(filter.rrn!))
      }
      if (filter?.stan) {
        filtered = filtered.filter((t) => t.stan.includes(filter.stan!))
      }

      // Pagination
      const start = (params.page - 1) * params.pageSize
      const end = start + params.pageSize
      const paged = filtered.slice(start, end)

      return Promise.resolve({
        data: paged,
        total: filtered.length,
        page: params.page,
        pageSize: params.pageSize,
        totalPages: Math.ceil(filtered.length / params.pageSize),
      })
    }

    return request.get<PaginatedResult<Transaction>>('/transactions', {
      params: { ...params, ...filter },
    })
  },

  async getTransaction(id: string): Promise<Transaction> {
    if (useMock) {
      const txn = mockTransactions.find((t) => t.transactionId === id)
      if (!txn) {
        throw new Error('Transaction not found')
      }
      return Promise.resolve(txn)
    }

    return request.get<Transaction>(`/transactions/${id}`)
  },

  async reverseTransaction(id: string, reason: string): Promise<Transaction> {
    if (useMock) {
      const txn = mockTransactions.find((t) => t.transactionId === id)
      if (!txn) {
        throw new Error('Transaction not found')
      }
      return Promise.resolve({ ...txn, status: 'REVERSED' })
    }

    return request.post<Transaction>(`/transactions/${id}/reverse`, { reason })
  },

  async exportTransactions(filter?: TransactionFilter): Promise<Blob> {
    return request.get('/transactions/export', {
      params: filter,
      responseType: 'blob',
    })
  },
}
