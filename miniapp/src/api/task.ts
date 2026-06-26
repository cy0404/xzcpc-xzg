import { request } from '@/utils/request'

export function fetchTaskList() {
  return request({ url: '/tasks' })
}

export function fetchTaskDetail(id: number) {
  return request({ url: `/tasks/${id}` })
}

export function submitTask(id: number) {
  return request({ url: `/tasks/${id}/submit`, method: 'POST' })
}

export function fetchTaskSummary(id: number) {
  return request({ url: `/tasks/${id}/summary` })
}

export function fetchTaskResult(id: number) {
  return request({ url: `/tasks/${id}/result` })
}

export function searchTaskMaterial(taskId: number, keyword: string) {
  return request({ url: `/tasks/${taskId}/materials/search`, data: { keyword }, showLoading: false })
}

export function fetchUnenteredMaterials(taskId: number) {
  return request({ url: `/tasks/${taskId}/unentered-materials`, showLoading: false })
}
