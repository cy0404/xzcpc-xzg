# 04 盘点模板模块

## 1. 目标

实现总部维护盘点模板、模板分区、模板分区物料关系和排序能力。

本模块只处理标准模板，不创建月盘任务，不生成任务快照。

模板中可选择的物料来自基础数据库管理系统同步到盘点工具本地的 `material` 缓存表。盘点工具不在本模块新增或编辑物料主数据。

## 2. 涉及表

```text
inventory_template
template_zone
template_zone_material
material
```

字段以 `01-database-design.md` 为准。

## 3. 业务规则

- 总部可以创建盘点模板。
- 模板下可以创建多个分区。
- 每个分区下可以添加多个物料。
- 同一模板分区内不可重复添加同一物料。
- 同一物料可以出现在同一模板的多个分区。
- 分区和物料都需要维护 `sort_no`。
- 停用物料不可被添加到模板分区。
- 物料名称、规格、单位来自本地同步缓存，最终源头是基础数据库管理系统。
- 模板只保存物料 ID 和排序，不复制物料主数据。
- 修改模板不影响已创建任务快照。

## 4. API

### 4.1 查询模板列表

```text
GET /api/admin/templates
```

查询参数：

```text
keyword
status
page
pageSize
```

返回字段：

```text
id
templateName
status
zoneCount
materialCount
createdAt
updatedAt
```

### 4.2 创建模板

```text
POST /api/admin/templates
```

请求体：

```json
{
  "templateName": "标准茶饮店月盘模板"
}
```

默认状态：

```text
draft
```

### 4.3 查询模板详情

```text
GET /api/admin/templates/{id}
```

返回结构应包含：

- 模板基础信息。
- 分区列表。
- 每个分区下的物料列表。

### 4.4 编辑模板

```text
PUT /api/admin/templates/{id}
```

### 4.5 更新模板状态

```text
PATCH /api/admin/templates/{id}/status
```

状态：

```text
draft
enabled
disabled
```

### 4.6 新增模板分区

```text
POST /api/admin/templates/{id}/zones
```

请求体：

```json
{
  "zoneName": "前台冷藏区"
}
```

### 4.7 编辑模板分区

```text
PUT /api/admin/templates/{id}/zones/{zoneId}
```

### 4.8 删除模板分区

```text
DELETE /api/admin/templates/{id}/zones/{zoneId}
```

说明：

- 删除分区时一并删除该分区下模板物料关系。
- 不影响已创建任务快照。

### 4.9 添加物料到模板分区

```text
POST /api/admin/template-zones/{zoneId}/materials
```

请求体：

```json
{
  "materialId": 1
}
```

添加前校验：

- 物料必须存在于本地 `material` 缓存表。
- 物料状态必须为 `enabled`。
- 不允许在同一分区重复添加同一物料。

### 4.10 删除模板分区物料

```text
DELETE /api/admin/template-zone-materials/{id}
```

### 4.11 调整模板分区物料顺序

```text
PUT /api/admin/template-zones/{zoneId}/materials/sort
```

请求体：

```json
{
  "items": [
    { "templateZoneMaterialId": 10, "sortNo": 1 },
    { "templateZoneMaterialId": 11, "sortNo": 2 }
  ]
}
```

## 5. 后端建议类

```text
InventoryTemplateController
InventoryTemplateService
TemplateZoneService
TemplateZoneMaterialService

InventoryTemplateEntity
TemplateZoneEntity
TemplateZoneMaterialEntity

TemplateCreateRequest
TemplateUpdateRequest
TemplateStatusUpdateRequest
TemplateZoneCreateRequest
TemplateZoneUpdateRequest
TemplateZoneMaterialAddRequest
SortUpdateRequest
TemplateDetailResponse
```

## 6. 校验

- 模板名称不能为空。
- 分区名称不能为空。
- 同一模板内建议不允许重名分区。
- 添加物料时校验物料存在且启用。
- 不允许在盘点工具中临时创建物料。
- 同一分区重复添加物料返回 `TEMPLATE_ZONE_MATERIAL_DUPLICATED`。
- 模板不存在返回 `TEMPLATE_NOT_FOUND`。
- 分区不存在返回 `TEMPLATE_ZONE_NOT_FOUND`。

## 7. 不做范围

- 不在本模块创建月盘任务。
- 不在本模块新增、编辑、启停物料主数据。
- 不在本模块处理门店默认清单。
- 不在本模块处理任务快照。
- 不在本模块处理门店录入。

## 8. 验收标准

- 总部可以创建模板。
- 总部可以添加分区。
- 总部可以给分区添加物料。
- 总部可以删除分区物料。
- 总部可以调整分区物料顺序。
- 同一分区重复添加同一物料会失败。
- 修改模板后，不影响已创建任务的设计原则被保留。
