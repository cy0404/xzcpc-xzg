import { request } from '@/utils/request'

export interface SmartOrder {
  id: number
  bizCode: string
  storeId: string
  storeName: string
  weekStartDate: string
  weekLabel: string
  status: string
  itemCount: number
  totalQty: number
  suggestAmount: number
  deadline: string | null
  generatedAt: string
  confirmedBy: string | null
  confirmedAt: string | null
  qmaiDeclareNo: string | null
  submitError: string | null
  syncAttempts: number
  createdAt: string
  updatedAt: string
}

export interface SmartOrderItem {
  id: number
  orderId: number
  materialId: number | null
  materialName: string
  spec: string
  category: string
  qmCode: string
  stockUnit: string
  baseUnit: string
  unitPrice: number | null
  currentInventory: number | null
  dailyUse: number | null
  cycleDays: number | null
  safetyDays: number | null
  suggestQty: number
  confirmedQty: number | null
  supportDays: number | null
  reason: string
  sortNo: number
}

export interface SmartOrderDetail {
  order: SmartOrder
  items: SmartOrderItem[]
}

export interface SmartOrderPageResp {
  records: SmartOrder[]
  total: number
  current: number
  size: number
}

export interface SmartOrderOverview {
  pending: number
  syncing: number
  success: number
  submitFailed: number
}

export interface StorePendingItem {
  storeId: string
  storeName: string
  pending: number
}

export function fetchSmartOrderList(params?: {
  pageNum?: number
  pageSize?: number
  all?: boolean
}) {
  return request<SmartOrderPageResp>({ url: '/smart-order/list', data: params })
}

export function fetchSmartOrderDetail(id: number) {
  return request<SmartOrderDetail>({ url: `/smart-order/${id}` })
}

export function confirmSmartOrder(id: number, items: { itemId: number; qty: number }[]) {
  return request<SmartOrder>({ url: `/smart-order/${id}/confirm`, method: 'PUT', data: { items } })
}

export function fetchSmartOrderOverview(all?: boolean) {
  return request<SmartOrderOverview | StorePendingItem[]>({
    url: '/smart-order/overview',
    data: all ? { all: true } : undefined,
  })
}

export function fetchSmartOrderOverviewTotal() {
  return request<{ pending: number }>({ url: '/smart-order/overview-total' })
}

export function generateSmartOrder() {
  return request<{ generated: number; skipped: number; failedStores: string[] }>({
    url: '/smart-order/generate',
    method: 'POST',
  })
}
