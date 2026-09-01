import { request } from '@/utils/request'
import { BASE_URL } from '@/utils/constants'

export interface ExpenseType {
  typeId: string
  name: string
  firstTypeId?: string
  firstTypeName?: string
  description?: string
  status?: string
}

export interface ExpenseRecord {
  expenseId: string
  storeId: string
  storeName: string
  typeId: string
  typeName: string
  firstTypeId?: string
  firstTypeName?: string
  amount: number
  occurredDate: string
  handlerName: string
  voucherUrl?: string
  remark?: string
  createdAt?: string
  /** 明细概要（列表页展示）：首条明细名称 + 条数 */
  firstItemName?: string
  itemCount?: number
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
  items?: { materialId?: string; materialName: string; parentCategory?: string; category?: string; weight: number; unitPrice: number }[]
  amountItems?: { name: string; amount: number }[]
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
  items?: { materialId?: string; materialName: string; parentCategory?: string; category?: string; weight: number; unitPrice: number }[]
  amountItems?: { name: string; amount: number }[]
}) {
  return request<ExpenseRecord>({ url: `/expenses/${expenseId}`, method: 'PUT', data })
}

export function deleteExpense(expenseId: string) {
  return request({ url: `/expenses/${expenseId}`, method: 'DELETE' })
}

/** 支出明细统一返回体：自购食材=物料（materialId/qty/unitPrice 全填），其他类型=名称+金额 */
export interface ExpenseItem {
  materialId?: string
  name: string
  parentCategory?: string
  category?: string
  unit?: string
  qty?: number
  unitPrice?: number
  amount?: number
  sortNo?: number
}

export function fetchExpenseItems(expenseId: string) {
  return request<ExpenseItem[]>({ url: `/expenses/${expenseId}/items` })
}

export function uploadVoucher(filePath: string) {
  return new Promise<{url: string}>((resolve, reject) => {
    // 获取基础 URL（去掉 /api/mp 尾缀）
    const baseUrl = BASE_URL.replace(/\/api\/mp\/?$/, '')
    uni.uploadFile({
      url: `${baseUrl}/api/mp/upload/voucher`,
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
