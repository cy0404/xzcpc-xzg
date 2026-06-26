import { request } from '@/utils/request'

export function fetchCandidates(taskId: number, zoneId: number, keyword = '') {
  return request({
    url: `/tasks/${taskId}/zones/${zoneId}/candidates`,
    data: { keyword },
    showLoading: false,
  })
}

export function addMaterial(taskId: number, zoneId: number, materialId: string) {
  return request({
    url: `/tasks/${taskId}/zones/${zoneId}/materials`,
    method: 'POST',
    data: { materialId },
  })
}

export function removeMaterial(taskId: number, zoneId: number, materialId: string) {
  return request({
    url: `/tasks/${taskId}/zones/${zoneId}/materials/${materialId}`,
    method: 'DELETE',
  })
}

export function sortMaterials(taskId: number, zoneId: number, ids: number[]) {
  return request({
    url: `/tasks/${taskId}/zones/${zoneId}/materials/sort`,
    method: 'PUT',
    data: { ids },
  })
}

export function scanMaterial(taskId: number, zoneId: number, qmCode: string) {
  return request({
    url: `/tasks/${taskId}/zones/${zoneId}/materials/scan`,
    data: { qmCode },
    silent: true,
  })
}
