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
