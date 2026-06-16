const BASE_URL = 'https://www.xzcpc-9pd.top/storeInventory/api/mp'

const CODE_MAP: Record<number, string> = {
  401: '未登录或登录已过期',
  4031: '您还没有被指派盘点任务，请联系总部',
  4032: '任务已提交，不可修改',
  4033: '任务已过截止时间',
  4040: '任务不存在',
}

export { BASE_URL, CODE_MAP }

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
