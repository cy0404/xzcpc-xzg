import api from './index'

// 获取门店列表（传 supervisorName 时只返回该督导名下门店，用于门店-督导联动筛选）
export function getStores(params?: { keyword?: string; supervisorName?: string }) {
  return api.get('/stores', { params })
}

// 批量更新门店订货周期配置（{storeId: {orderDays: "1,4"|null, paused: 0|1}}，
// orderDays 为 1-7 逗号分隔的订货日，null 表示清空不参与周盘）
export function updateOrderCycle(data: Record<string, { orderDays: string | null; paused: number }>) {
  return api.put('/stores/order-cycle', data)
}
