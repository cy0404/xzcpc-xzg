import { request } from '@/utils/request'
import { BASE_URL } from '@/utils/constants'

export interface ExpenseType {
  typeId: string
  name: string
  description?: string
  status?: string
}

export interface ExpenseRecord {
  expenseId: string
  storeId: string
  storeName: string
  typeId: string
  typeName: string
  amount: number
  occurredDate: string
  handlerName: string
  voucherUrl?: string
  remark?: string
  createdAt?: string
}

export interface ExpensePageResp {
  records: ExpenseRecord[]
  total: number
  current: number
  size: number
}

export function fetchExpenseTypes() {
  return request<ExpenseType[]>({ url: '/expense-types' })
}

export function fetchExpenseItems() {
  return request<any[]>({ url: '/expense-items' })
}

export function fetchExpenses(params?: {
  typeId?: string
  startDate?: string
  endDate?: string
  pageNum?: number
  pageSize?: number
}) {
  return request<ExpensePageResp>({ url: '/expenses', data: params })
}

export function fetchExpenseDetail(expenseId: string) {
  return request<ExpenseRecord>({ url: `/expenses/${expenseId}` })
}

export function createExpense(data: {
  typeId: string
  amount: number
  occurredDate: string
  handlerName: string
  voucherUrl?: string
  remark?: string
}) {
  return request<ExpenseRecord>({ url: '/expenses', method: 'POST', data })
}

export function updateExpense(expenseId: string, data: {
  typeId: string
  amount: number
  occurredDate: string
  handlerName: string
  voucherUrl?: string
  remark?: string
}) {
  return request<ExpenseRecord>({ url: `/expenses/${expenseId}`, method: 'PUT', data })
}

export function deleteExpense(expenseId: string) {
  return request({ url: `/expenses/${expenseId}`, method: 'DELETE' })
}

export function uploadVoucher(filePath: string) {
  return new Promise<{url: string}>((resolve, reject) => {
    uni.uploadFile({
      url: BASE_URL.replace(/\/api\/mp$/, '') + '/api/mp/upload/voucher',
      filePath,
      name: 'file',
      header: { 'Authorization': `Bearer ${uni.getStorageSync('token')}` },
      success: (res: any) => {
        try { const d = JSON.parse(res.data); if (d.code === 200) resolve(d.data); else reject(d) }
        catch { reject(res) }
      },
      fail: reject
    })
  })
}
