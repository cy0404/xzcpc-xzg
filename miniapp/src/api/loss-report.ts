import { request } from '@/utils/request'
import { BASE_URL } from '@/utils/constants'

export function getLossOverview(all?: boolean) {
  return request<any[]>({ url: '/loss-report/overview', data: all ? { all: true } : undefined, showLoading: false, silent: true })
}

export function getLossList(params: { lossType?: string; pageNum?: number; pageSize?: number }) {
  return request<any>({ url: '/loss-report/list', data: params, showLoading: false })
}

export function getApprovalList(params?: { pageNum?: number; pageSize?: number }) {
  return request<any>({ url: '/loss-report/approval-list', data: params, showLoading: false })
}

export function approveLoss(id: number) {
  return request({ url: `/loss-report/${id}/approve`, method: 'POST' })
}

export function rejectApproval(id: number) {
  return request({ url: `/loss-report/${id}/reject-approval`, method: 'POST' })
}

/** 店长批量审批：action=approve|reject，返回 { success, skipped } */
export function batchApproveLoss(ids: number[], action: 'approve' | 'reject') {
  return request<any>({ url: '/loss-report/batch-approve', method: 'POST', data: { ids, action } })
}

export function getLossLogs(id: number) {
  return request<any[]>({ url: `/loss-report/${id}/logs`, showLoading: false })
}

export function receiveLoss(id: number, remark?: string) {
  return request({ url: `/loss-report/${id}/receive`, method: 'POST', data: { remark: remark || '' } })
}

export function notReceiveLoss(id: number, remark?: string) {
  return request({ url: `/loss-report/${id}/not-receive`, method: 'POST', data: { remark: remark || '' } })
}

export function closeLoss(id: number) {
  return request({ url: `/loss-report/${id}/close`, method: 'POST' })
}

export function appendLossVoucher(id: number, url: string) {
  return request({ url: `/loss-report/${id}/append-voucher`, method: 'POST', data: { url } })
}

export function removeLossVoucher(id: number, url: string) {
  return request({ url: `/loss-report/${id}/remove-voucher`, method: 'POST', data: { url } })
}

export function getLossStandard(materialId: string) {
  return request<any>({ url: `/loss-report/standard/${materialId}`, showLoading: false, silent: true })
}

export function getLossDetail(id: number) {
  return request<any>({ url: `/loss-report/${id}`, showLoading: false })
}

export function createLoss(data: any) {
  return request({ url: '/loss-report', method: 'POST', data })
}

/** 日常多物料报损 */
export function createDailyLoss(data: {
  reason: string; remark?: string; voucherUrl: string;
  items: {
    materialId: string; materialName: string; spec?: string;
    lossObject?: string; inputUnit?: string; inputQty?: number;
    grossWeight?: number; containerId?: number; unitPrice?: number;
  }[]
}) {
  return request<any>({ url: '/loss-report/daily', method: 'POST', data })
}

/** 修改日常多物料报损 */
export function updateDailyLoss(id: number, data: {
  reason: string; remark?: string; voucherUrl: string;
  items: {
    materialId: string; materialName: string; spec?: string;
    lossObject?: string; inputUnit?: string; inputQty?: number;
    grossWeight?: number; containerId?: number; unitPrice?: number;
  }[]
}) {
  return request<any>({ url: `/loss-report/${id}`, method: 'PUT', data })
}

/** 删除日常报损 */
export function deleteDailyLoss(id: number) {
  return request({ url: `/loss-report/${id}`, method: 'DELETE' })
}

export function getContainers() {
  return request<any[]>({ url: '/loss-report/containers', showLoading: false, silent: true })
}

export function searchMaterials(keyword: string, lossObject?: string) {
  return request<any>({ url: '/loss-report/materials/search', data: { keyword, lossObject }, showLoading: false, silent: true })
}

const CHUNK_SIZE = 5 * 1024 * 1024 // 5MB
const MAX_DIRECT_SIZE = 10 * 1024 * 1024 // 10MB 以下直接上传

function directUpload(filePath: string, onProgress?: (pct: number) => void, storeName?: string): Promise<any> {
  return new Promise<any>((resolve, reject) => {
    const baseUrl = BASE_URL.replace(/\/api\/mp\/?$/, '')
    const task = uni.uploadFile({
      url: `${baseUrl}/api/mp/upload/voucher`,
      filePath,
      name: 'file',
      formData: storeName ? { storeName } : {},
      header: { Authorization: `Bearer ${uni.getStorageSync('token')}` },
      success: (res: any) => {
        try { const d = JSON.parse(res.data); if (d.code === 200) resolve(d.data); else reject(d) }
        catch { reject(res) }
      },
      fail: reject,
    })
    if (onProgress) {
      task.onProgressUpdate((e: any) => {
        if (e.totalBytesExpectedToSend > 0) {
          onProgress(Math.round(e.totalBytesSent / e.totalBytesExpectedToSend * 100))
        }
      })
    }
  })
}

function chunkedUpload(filePath: string, fileSize: number, onProgress?: (pct: number) => void, storeName?: string): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const baseUrl = BASE_URL.replace(/\/api\/mp\/?$/, '')
    const token = uni.getStorageSync('token')
    const totalChunks = Math.ceil(fileSize / CHUNK_SIZE)
    const ext = filePath.substring(filePath.lastIndexOf('.')).toLowerCase() || '.mp4'
    const fileName = `video_${Date.now()}${ext}`

    // Step 1: 初始化分片上传
    uni.request({
      url: `${baseUrl}/api/mp/upload/chunk/init`,
      method: 'POST',
      header: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: { fileName, totalChunks },
      success: (initRes: any) => {
        if (initRes.statusCode !== 200 || initRes.data?.code !== 200) {
          reject(new Error('初始化上传失败')); return
        }
        const uploadId = initRes.data.data.uploadId
        uploadChunks(0)

        function uploadChunks(index: number) {
          if (index >= totalChunks) {
            onProgress && onProgress(99)
            // 全部分片上传完毕，调用合并
            uni.request({
              url: `${baseUrl}/api/mp/upload/chunk/${uploadId}/complete`,
              method: 'POST',
              header: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
              data: { fileName, totalChunks, storeName: storeName || '' },
              success: (compRes: any) => {
                if (compRes.statusCode === 200 && compRes.data?.code === 200) {
                  onProgress && onProgress(100)
                  resolve(compRes.data.data.url)
                } else {
                  reject(new Error('合并文件失败'))
                }
              },
              fail: reject,
            })
            return
          }
          onProgress && onProgress(Math.round(index / totalChunks * 100))

          // 分片上传
          uni.uploadFile({
            url: `${baseUrl}/api/mp/upload/chunk/${uploadId}/${index}`,
            filePath,
            name: 'file',
            header: { Authorization: `Bearer ${token}` },
            success: (chunkRes: any) => {
              try {
                const d = JSON.parse(chunkRes.data)
                if (d.code === 200) { uploadChunks(index + 1) }
                else { reject(new Error(`分片 ${index} 上传失败`)) }
              } catch { reject(chunkRes) }
            },
            fail: reject,
          })
        }
      },
      fail: reject,
    })
  })
}

/** 上传报损图片 */
export function uploadLossImage(filePath: string, storeName?: string): Promise<string> {
  return directUpload(filePath, undefined, storeName).then((data: any) => data.url || data)
}

/** 上传报损视频。返回 { url, thumb }。onProgress 返回 0-100 进度百分比 */
export function uploadLossVideo(filePath: string, fileSize: number, onProgress?: (pct: number) => void, storeName?: string): Promise<any> {
  if (fileSize <= MAX_DIRECT_SIZE) {
    return directUpload(filePath, onProgress, storeName)
  }
  return chunkedUpload(filePath, fileSize, onProgress, storeName)
}
