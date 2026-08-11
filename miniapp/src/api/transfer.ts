import { request } from '@/utils/request'

export interface TransferOrder {
  id: number
  bizCode: string
  fromStoreId: string
  fromStoreName: string
  toStoreId: string
  toStoreName: string
  status: string
  totalQty: number
  handoff: string
  remark: string
  creatorStoreId: string
  createdBy: string
  confirmedBy: string
  shippedBy: string
  receivedBy: string
  confirmedAt: string
  shippedAt: string
  receivedAt: string
  completedAt: string
  cancelledAt: string
  createdAt: string
  updatedAt: string
}

export interface TransferOrderItem {
  id: number
  transferId: number
  materialName: string
  spec: string
  unit: string
  transferQty: number
  baseUnit: string
  baseQty: number
  inputUnit: string
  inputQty: number
  unitPrice: number
  remark: string
}

export interface TransferDetail {
  order: TransferOrder
  items: TransferOrderItem[]
}

export interface TransferPageResp {
  records: TransferOrder[]
  total: number
  current: number
  size: number
  itemsMap?: Record<string, TransferOrderItem[]>
}

export interface TransferOverview {
  pendingConfirm: number
  pendingShip: number
  completed: number
  rejected: number
}

export function fetchTransferList(params?: {
  status?: string
  pageNum?: number
  pageSize?: number
  all?: boolean
}) {
  return request<TransferPageResp>({ url: '/transfer/list', data: params })
}

export function fetchTransferDetail(id: number) {
  return request<TransferDetail>({ url: `/transfer/${id}` })
}

export function fetchTransferOverview(all?: boolean) {
  return request<TransferOverview | any[]>({ url: '/transfer/overview', data: all ? { all: true } : undefined })
}

export function fetchTransferOverviewTotal() {
  return request<TransferOverview>({ url: '/transfer/overview-total' })
}

export interface StorePendingItem {
  storeId: string
  storeName: string
  pending: number
}

export function createTransfer(data: {
  fromStoreId: string
  fromStoreName?: string
  toStoreId: string
  toStoreName?: string
  remark?: string
  items: { materialName: string; spec?: string; unit: string; transferQty: number; remark?: string }[]
}) {
  return request<TransferOrder>({ url: '/transfer', method: 'POST', data })
}

export function confirmTransfer(id: number) {
  return request<TransferOrder>({ url: `/transfer/${id}/confirm`, method: 'PUT' })
}

export function shipTransfer(id: number) {
  return request<TransferOrder>({ url: `/transfer/${id}/ship`, method: 'PUT' })
}

export function receiveTransfer(id: number) {
  return request<TransferOrder>({ url: `/transfer/${id}/receive`, method: 'PUT' })
}

export function cancelTransfer(id: number) {
  return request<TransferOrder>({ url: `/transfer/${id}/cancel`, method: 'PUT' })
}

export function rejectTransfer(id: number) {
  return request<TransferOrder>({ url: `/transfer/${id}/reject`, method: 'PUT' })
}

export interface MaterialSearchItem {
  materialId: string
  materialName: string
  qmCode: string
  spec: string
  category: string
  baseUnit: string
  unitPrice: number | null
  units: string[]
  unitPrices: Record<string, number>
  unitInfos: { unit: string; hint: string }[]
}

export interface MaterialSearchResult {
  list: MaterialSearchItem[]
}

export function doReturn(transferId: number, data: { action: string; items?: Array<{ itemId: number; returnType: string; returnQty: number; returnAmount: number }>; remark?: string }) {
  return request({ url: `/transfers/${transferId}/return`, method: 'POST', data })
}

export function confirmReturn(transferId: number) {
  return request({ url: `/transfers/${transferId}/return-confirm`, method: 'POST' })
}

export function searchTransferMaterials(keyword?: string) {
  return request<MaterialSearchResult>({
    url: '/transfer/materials/search',
    data: { keyword: keyword || '' },
    showLoading: false,
  })
}
