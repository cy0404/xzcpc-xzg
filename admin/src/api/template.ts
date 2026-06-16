import api from './index'

// 分页查询模板列表
export function getTemplates(params: { keyword?: string; pageNum?: number; pageSize?: number }) {
  return api.get('/templates', { params })
}

// 新增模板
export function addTemplate(data: any) {
  return api.post('/templates', data)
}

// 编辑模板名称
export function updateTemplate(id: number, data: any) {
  return api.put(`/templates/${id}`, data)
}

// 删除模板
export function deleteTemplate(id: number) {
  return api.delete(`/templates/${id}`)
}

// 设置模板状态（1启用 0停用 2草稿）
export function setTemplateStatus(id: number, status: number) {
  return api.patch(`/templates/${id}/status`, { status })
}

// 获取模板详情
export function getTemplateDetail(id: number) {
  return api.get(`/templates/${id}`)
}

// 获取模板下的分区列表
export function getTemplateZones(id: number) {
  return api.get(`/templates/${id}/zones`)
}

// 为模板新增分区
export function addTemplateZone(id: number, data: any) {
  return api.post(`/templates/${id}/zones`, data)
}

// 编辑分区名称等信息
export function updateTemplateZone(templateId: number, zoneId: number, data: any) {
  return api.put(`/templates/${templateId}/zones/${zoneId}`, data)
}

// 删除分区
export function deleteTemplateZone(templateId: number, zoneId: number) {
  return api.delete(`/templates/${templateId}/zones/${zoneId}`)
}

// 为分区添加物料
export function addZoneMaterial(templateId: number, zoneId: number, data: any) {
  return api.post(`/templates/${templateId}/zones/${zoneId}/materials`, data)
}

// 从分区移除物料
export function deleteZoneMaterial(templateId: number, zoneId: number, materialId: number) {
  return api.delete(`/templates/${templateId}/zones/${zoneId}/materials/${materialId}`)
}

// 更新分区排序
export function updateZoneSort(templateId: number, data: any) {
  return api.put(`/templates/${templateId}/zones/sort`, data)
}

// 更新分区内物料的排序
export function updateZoneMaterialSort(templateId: number, zoneId: number, data: any) {
  return api.put(`/templates/${templateId}/zones/${zoneId}/materials/sort`, data)
}
