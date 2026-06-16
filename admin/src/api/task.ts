import api from './index'

// 分页查询任务列表，支持按门店/状态/关键词(全局搜索)/模板名称/月份筛选
export function getTasks(params: {
  storeId?: string
  status?: string
  keyword?: string
  templateName?: string
  taskMonth?: string
  pageNum?: number
  pageSize?: number
}) {
  return api.get('/tasks', { params })
}

// 创建月盘任务（从模板生成快照）
export function createTask(data: any) {
  return api.post('/tasks', data)
}

// 获取任务详情
export function getTaskDetail(id: number) {
  return api.get(`/tasks/${id}`)
}

// 获取任务盘点汇总结果
export function getTaskResult(id: number) {
  return api.get(`/tasks/${id}/result`)
}

// 删除任务（仅未开始状态可删）
export function deleteTask(id: number) {
  return api.delete(`/tasks/${id}`)
}

// 获取模板预览（含分区和物料，用于创建任务页预览）
export function getTemplatePreview(id: number) {
  return api.get(`/templates/${id}/preview`)
}
