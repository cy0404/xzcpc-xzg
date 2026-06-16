import api from './index'

// 获取门店列表（用于创建任务时选择门店范围）
export function getStores(params?: { keyword?: string }) {
  return api.get('/stores', { params })
}
