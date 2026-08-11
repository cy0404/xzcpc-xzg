import { request } from '@/utils/request'
import { BASE_URL } from '@/utils/constants'

export interface Issue {
  id: number
  bizCode: string
  storeId: string
  storeName: string
  title: string
  issueType: string
  subType: string
  urgency: string          // urgent | normal | low
  description: string
  xiangmuId: string
  syncStatus: string
  status: string           // pending | processing | pending_acceptance | resolved | closed
  recordsData: string
  replyText: string        // 解决原因
  processedBy: string
  createdAt: string
  updatedAt: string
}

export interface IssuePageResp {
  records: Issue[]
  total: number
  current: number
  size: number
}

export interface IssueOverview {
  processing: number
  pendingAcceptance: number
  resolved: number
  all: number
}

export function fetchIssueList(params?: { status?: string; pageNum?: number; pageSize?: number; all?: boolean }) {
  return request<IssuePageResp>({ url: '/issue/list', data: params, showLoading: false })
}

export function reportIssueClick() {
  return request({ url: '/issue/report-click', method: 'POST', showLoading: false, silent: true })
}

export function fetchIssueOverview(all?: boolean) {
  return request<IssueOverview>({ url: '/issue/overview', data: all ? { all: true } : undefined, showLoading: false, silent: true })
}

export function fetchIssueOverviewStores() {
  return request<any[]>({ url: '/issue/overview-stores', showLoading: false, silent: true })
}

export function fetchIssueDetail(id: number) {
  return request<Issue>({ url: `/issue/${id}`, showLoading: false })
}

export function createIssue(data: {
  title: string
  issueType: string
  subType?: string
  urgency: string
  description: string
  images?: string
  contactName: string
  contactPhone: string
}) {
  return request<Issue>({ url: '/issue', method: 'POST', data })
}

export function acceptIssue(id: number, remark?: string) {
  return request<Issue>({ url: `/issue/${id}/accept`, method: 'POST', data: { remark: remark || '' } })
}

/** 同步外部表单提交结果 */
export function syncExternalIssue(data: { id: number; issueNo?: string; status?: string }) {
  return request<Issue>({ url: '/issue/sync-external', method: 'POST', data })
}

/** 按外部 ID 查 issue */
export function fetchIssueByExternalId(externalId: number) {
  return request<Issue>({ url: `/issue/by-external/${externalId}`, showLoading: false })
}

/** 代理查询外部处理记录 */
export function fetchIssueRecords(id: number) {
  return request<any>({ url: `/issue/${id}/records`, showLoading: false })
}

/** 门店确认（已解决/未解决） */
export function storeConfirm(id: number, action: 'accept' | 'reject') {
  return request<any>({ url: `/issue/${id}/store-confirm?action=${action}`, method: 'POST' })
}

/** 代理门店回复 */
export function replyIssue(id: number, replyText: string, mediaUrls?: string) {
  return request<any>({ url: `/issue/${id}/reply`, method: 'POST', data: { replyText, mediaUrls: mediaUrls || '' } })
}

/** 代理上传文件到 task_platform */
export function uploadIssueMedia(filePaths: string[], chatId?: string) {
  return new Promise<any>((resolve, reject) => {
    const baseUrl = (BASE_URL as string).replace(/\/api\/mp\/?$/, '')
    uni.uploadFile({
      url: `${baseUrl}/api/mp/issue/upload-media`,
      filePath: filePaths[0],
      name: 'files',
      formData: { chatId: chatId || '' },
      header: { Authorization: `Bearer ${uni.getStorageSync('token')}` },
      success: (res: any) => {
        try { const d = JSON.parse(res.data); if (d.code === 200) resolve(d.data); else reject(d) }
        catch { reject(res) }
      },
      fail: reject,
    })
  })
}

/** 上传现场图片，复用通用凭证上传接口，返回图片 URL */
export function uploadIssueImage(filePath: string) {
  return new Promise<string>((resolve, reject) => {
    const baseUrl = BASE_URL.replace(/\/api\/mp\/?$/, '')
    uni.uploadFile({
      url: `${baseUrl}/api/mp/upload/voucher`,
      filePath,
      name: 'file',
      header: { Authorization: `Bearer ${uni.getStorageSync('token')}` },
      success: (res: any) => {
        try { const d = JSON.parse(res.data); if (d.code === 200) resolve(d.data.url); else reject(d) }
        catch { reject(res) }
      },
      fail: reject,
    })
  })
}
