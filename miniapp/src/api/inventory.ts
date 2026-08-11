import { request } from '@/utils/request'

/** 扫码识别物料 */
export function scanBarcode(barcode: string, taskId?: number, zoneId?: number) {
  return request<any>({
    url: `/inventory/scan/${encodeURIComponent(barcode)}`,
    data: { taskId, zoneId },
    showLoading: false,
    silent: true,
  })
}

/** 条码补充申请 */
export function supplementBarcode(barcode: string, materialName?: string, remark?: string) {
  return request({
    url: '/inventory/barcode/supplement',
    method: 'POST',
    data: { barcode, materialName, remark },
  })
}
