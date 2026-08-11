import request from './index'

/** 操作日志 */
export function fetchOperationLogs(params: {
  page: number
  size: number
  username?: string
  module?: string
  operation?: string
  source?: string
}) {
  return request({ url: '/logs/operation', params })
}

/** 登录日志 */
export function fetchLoginLogs(params: {
  page: number
  size: number
  username?: string
  loginType?: string
}) {
  return request({ url: '/logs/login', params })
}
