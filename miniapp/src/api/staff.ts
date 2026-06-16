import { request } from '@/utils/request'
import { BASE_URL } from '@/utils/constants'

export function fetchStaffList(status = '') {
  return request({ url: '/staff', data: { status } })
}

export function fetchMyStaffProfile() {
  return request({ url: '/staff/me' })
}

export function fetchStaffDetail(employeeId: string) {
  return request({ url: `/staff/${employeeId}` })
}

export function updateStaff(employeeId: string, data: any) {
  return request({ url: `/staff/${employeeId}`, method: 'PUT', data })
}

export function submitStaffRegistration(data: any) {
  return request({ url: '/staff/registrations', method: 'POST', data })
}

export function fetchStaffApplications(status = 'pending') {
  return request({ url: '/staff/applications', data: { status } })
}

export function fetchStaffApplicationDetail(applicationId: string) {
  return request({ url: `/staff/applications/${applicationId}` })
}

export function approveStaffApplication(applicationId: string, data: any) {
  return request({ url: `/staff/applications/${applicationId}/approval`, method: 'POST', data })
}

export function resignStaff(employeeId: string, data: any) {
  return request({ url: `/staff/${employeeId}/resign`, method: 'PUT', data })
}

/** 公开接口：查询登记申请状态，无需登录 */
export function checkApplicationStatus(applicationId: string) {
  return publicRequest({ url: '/public/application/' + applicationId })
}

/** 公开接口：根据 code + storeId 检查是否有已有申请 */
export function checkExistingApplication(code: string, storeId: string) {
  return publicRequest({ url: '/public/check-application', method: 'POST', data: { code, storeId } })
}

/** 公开接口请求（无需 token，不拦截 401） */
function publicRequest(options: { url: string; method?: string; data?: any }) {
  const { url, method = 'GET', data } = options
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      method,
      data,
      header: { 'Content-Type': 'application/json' },
      success: (res: any) => {
        if (res.statusCode === 200 && res.data?.code === 200) resolve(res.data.data)
        else resolve(null)
      },
      fail: () => resolve(null),
    })
  })
}
