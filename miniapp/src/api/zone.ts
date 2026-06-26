import { request } from '@/utils/request'

export function fetchZoneMaterials(taskId: number, zoneId: number, options?: { showLoading?: boolean }) {
  return request({
    url: `/tasks/${taskId}/zones/${zoneId}/materials`,
    showLoading: options?.showLoading ?? true,
  })
}

export function saveZone(taskId: number, zoneId: number, items: any[]) {
  return request({ url: `/tasks/${taskId}/zones/${zoneId}/save`, method: 'POST', data: { items } })
}

export function itemSave(taskId: number, zoneId: number, data: any) {
  return request({ url: `/tasks/${taskId}/zones/${zoneId}/item-save`, method: 'POST', data })
}

export function addZone(taskId: number, zoneName: string) {
  return request({ url: `/tasks/${taskId}/zones`, method: 'POST', data: { zoneName } })
}

export function deleteZone(taskId: number, zoneId: number) {
  return request({ url: `/tasks/${taskId}/zones/${zoneId}`, method: 'DELETE' })
}

export function sortZones(taskId: number, ids: number[]) {
  return request({ url: `/tasks/${taskId}/zones/sort`, method: 'PUT', data: { ids } })
}

export function updateZoneName(taskId: number, zoneId: number, zoneName: string) {
  return request({ url: `/tasks/${taskId}/zones/${zoneId}/name`, method: 'PUT', data: { zoneName } })
}
