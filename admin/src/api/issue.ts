import api from './index'

export function getIssueList(params?: {
  storeId?: string
  supervisorName?: string
  issueType?: string
  urgency?: string
  status?: string
  source?: string
  keyword?: string
  startDate?: string
  endDate?: string
  pageNum?: number
  pageSize?: number
}) {
  return api.get('/admin/issue/list', { params })
}

export function getIssueDetail(id: number, records?: boolean) {
  if (records) {
    return api.get(`/admin/issue/${id}/records`)
  }
  return api.get(`/admin/issue/${id}`)
}
