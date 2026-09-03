// 切换环境：true=生产 false=本地 'test'=测试域名
const ENV: 'prod' | 'local' | 'test' = 'local'
const LOCAL_URL = 'http://192.168.0.4:30261/storeInventory/api/mp'
const TEST_URL = 'https://www.xzcpc-9pd.top/test/storeInventory/api/mp'
const PROD_URL = 'https://www.xzcpc-9pd.top/storeInventory/api/mp'

function getBaseUrl() {
  if (ENV === 'local') return LOCAL_URL
  if (ENV === 'test') return TEST_URL
  return PROD_URL
}

const BASE_URL = getBaseUrl()
const IS_PROD = ENV === 'prod'

// H5 页面基础路径（测试环境 URL 含 /test 前缀，H5 用同域 HTTPS 也可直接取 /test/storeInventory）
const H5_BASE = getBaseUrl().replace(/\/api\/mp\/?$/, '')

const CODE_MAP: Record<number, string> = {
  401: '未登录或登录已过期',
  4031: '您还没有被指派盘点任务，请联系总部',
  4032: '任务已提交，不可修改',
  4033: '任务已过截止时间',
  4040: '任务不存在',
}

export { BASE_URL, H5_BASE, IS_PROD, CODE_MAP }

// 订阅消息模板 ID（一次性订阅，7 个业务场景共用同一模板）：
// 到 mp.weixin.qq.com → 功能 → 订阅消息 申请后填入（后端 mp-server application.yml 同步一份 NOTIFY_SUBSCRIBE_TEMPLATE_ID）；
// 为空 = 未申请/开发期，前端跳过 requestSubscribeMessage，不打扰用户
export const SUBSCRIBE_TEMPLATE_ID = ''

export const TASK_STATUS_MAP: Record<string, string> = {
  not_started: '未开始',
  in_progress: '进行中',
  submitted: '已提交',
}

export const TASK_STATUS_COLOR: Record<string, string> = {
  not_started: '#909399',
  in_progress: '#1989fa',
  submitted: '#07c160',
}

export const TASK_TYPE_MAP: Record<string, string> = {
  monthly: '月盘',
  weekly: '周盘',
}

export const TASK_TYPE_COLOR: Record<string, string> = {
  monthly: '#909399',
  weekly: '#d48806',
}
