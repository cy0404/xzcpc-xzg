import { request } from '@/utils/request'

export interface WorkHoursRecord {
  recordId: string
  storeId: string
  storeName: string
  recordTime: string
  hours: number
  employeeId: string
  employeeName: string
  createdAt: string
  updatedAt: string
}

export function fetchWorkHours() {
  return request<WorkHoursRecord[]>({ url: '/work-hours' })
}

export function createWorkHours(data: { recordTime: string; hours: number }) {
  return request({ url: '/work-hours', method: 'POST', data })
}

export function updateWorkHours(recordId: string, data: { recordTime: string; hours: number }) {
  return request({ url: `/work-hours/${recordId}`, method: 'PUT', data })
}

export function deleteWorkHours(recordId: string) {
  return request({ url: `/work-hours/${recordId}`, method: 'DELETE' })
}
