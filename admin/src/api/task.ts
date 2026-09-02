import api from './index'

// 获取最近有任务的月份
export function getLatestMonth() {
  return api.get('/tasks/latest-month')
}

// 分页查询任务列表，支持按门店/状态/关键词(全局搜索)/模板名称/月份/任务类型筛选
export function getTasks(params: {
  storeId?: string
  storeIds?: string
  status?: string
  keyword?: string
  templateName?: string
  supervisorName?: string
  taskMonth?: string
  taskType?: string
  pageNum?: number
  pageSize?: number
}) {
  return api.get('/tasks', { params })
}

// 创建盘点任务（月盘/周盘，从模板生成快照）
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

// 更新任务基本信息（仅未开始/进行中可改）
export function updateTask(id: number, data: any) {
  return api.put(`/tasks/${id}`, data)
}

// 批量更新物料数量（已提交任务）
export function updateMaterials(id: number, materials: any[]) {
  return api.put(`/tasks/${id}/materials`, materials)
}

// 删除物料（已提交任务）
export function deleteMaterial(taskId: number, materialId: number) {
  return api.delete(`/tasks/${taskId}/materials/${materialId}`)
}

// 汇总编辑：改物料总量（已提交任务）
export function setMaterialTotal(taskId: number, materialId: string, totalQty: number) {
  return api.put(`/tasks/${taskId}/materials/${materialId}/total`, { totalQty })
}

// 获取模板预览（含分区和物料，用于创建任务页预览）
export function getTemplatePreview(id: number) {
  return api.get(`/templates/${id}/preview`)
}

// ===== 盘点差异处理 =====

/** 盘点差异任务列表（按任务分组） */
export function getDiffTasks(params: {
  pageNum?: number; pageSize?: number
  storeIds?: string; supervisorName?: string
}) {
  return api.get('/admin/inventory/diff-tasks', { params })
}

/** 某任务的差异明细 */
export function getDiffTaskDetail(taskId: number) {
  return api.get(`/admin/inventory/diff-tasks/${taskId}`)
}

/** 触发差异计算 */
export function triggerDiffCalc(taskId: number) {
  return api.post(`/admin/inventory/diff-tasks/${taskId}/calculate`)
}

/** 修改 adjusted_qty 并重算差异（未计算差异行只改数量，不重算） */
export function modifyAdjustedQty(id: number, adjustedQty: number) {
  return api.put(`/admin/inventory/differences/${id}/adjust`, { adjustedQty })
}

/** 获取差异阈值配置 */
export function getDiffConfig() {
  return api.get('/admin/inventory/diff-config')
}

/** 更新差异阈值 */
export function updateDiffConfig(thresholdRate: number) {
  return api.put('/admin/inventory/diff-config', { thresholdRate })
}

/** 按物料维度聚合差异（跨任务/跨门店） */
export function getDiffMaterials(params: {
  taskMonth?: string
  storeIds?: string
  supervisorName?: string
}) {
  return api.get('/admin/inventory/diff-materials', { params })
}
