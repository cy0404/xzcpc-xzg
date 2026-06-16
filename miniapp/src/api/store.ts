import { request } from '@/utils/request'

export function fetchStores(keyword = '') {
  return request({ url: '/stores', data: { keyword } })
}
