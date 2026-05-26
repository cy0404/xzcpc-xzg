# 05 月盘任务与快照模块

## 1. 目标

实现总部创建月盘任务，并在创建时生成独立任务快照。

任务快照是系统数据一致性的核心，必须保证：

- 创建任务后，后续模板变化不影响该任务。
- 创建任务后，后续门店默认清单变化不影响该任务。
- 创建任务后，后续基础数据库管理系统中的物料或门店变化不影响该任务。
- 任务中的分区和物料使用快照数据。

## 2. 涉及表

```text
store
inventory_template
template_zone
template_zone_material
store_zone_material
inventory_task
task_zone
task_zone_material
material
```

字段以 `01-database-design.md` 为准。

说明：

- `store` 和 `material` 是从基础数据库管理系统同步到盘点工具的本地缓存。
- 月盘任务引用本地缓存中的门店和物料 ID。
- 任务分区物料快照必须保存物料名称、规格、单位，避免基础系统后续变化影响历史任务。

## 3. API

### 3.1 查询任务列表

```text
GET /api/admin/inventory-tasks
```

查询参数：

```text
taskMonth
storeId
status
templateName
page
pageSize
```

返回字段：

```text
id
taskName
storeId
storeName
taskMonth
templateId
templateName
deadline
status
zoneCount
materialCount
submittedAt
createdAt
```

### 3.2 创建月盘任务

```text
POST /api/admin/inventory-tasks
```

请求体：

```json
{
  "taskName": "2026年4月月盘",
  "storeId": 1,
  "taskMonth": "2026-04",
  "templateId": 1,
  "deadline": "2026-04-30 23:00:00"
}
```

### 3.3 查询任务详情

```text
GET /api/admin/inventory-tasks/{id}
```

返回内容：

- 任务基础信息。
- 任务进度。
- 分区列表。
- 每个分区的物料数量和录入进度。

### 3.4 查询任务结果

```text
GET /api/admin/inventory-tasks/{id}/result
```

说明：

- 若任务未提交，可返回当前录入明细，也可提示未提交。
- 若任务已提交，返回最终结果。

## 4. 创建任务流程

创建任务必须使用事务。

流程：

1. 校验门店存在。
2. 校验门店状态为可用。
3. 校验模板存在且状态可用。
4. 创建 `inventory_task`。
5. 读取模板分区。
6. 读取门店默认分区物料清单。
7. 生成任务分区快照 `task_zone`。
8. 生成任务分区物料快照 `task_zone_material`。
9. 保存物料名称、规格、单位快照。
10. 初始化录入数量为 `null`。
11. 初始化录入状态为 `not_entered`。
12. 初始化任务状态为 `not_started`。

## 5. 快照生成规则

数据来源：

- 总部标准模板。
- 门店默认分区物料清单。
- 基础数据库管理系统同步缓存中的物料和门店。

建议首期规则：

1. 以模板分区作为基础分区结构。
2. 如果门店默认清单中存在相同分区名称，则该分区物料优先来自门店默认清单。
3. 如果门店默认清单没有该分区，则使用模板分区物料。
4. 如果门店默认清单存在模板没有的分区，首期可选择追加为任务分区。
5. 同一任务分区内不得重复生成同一物料。
6. 生成物料快照时，从本地物料缓存读取名称、规格、单位。

需要在实现前明确一条原则：

- 任务一旦创建，`task_zone` 和 `task_zone_material` 就是该任务自己的历史数据。

## 6. 进度计算

任务总物料数：

```text
count(task_zone_material where task_id = ?)
```

已录入数：

```text
count(input_status in ('entered', 'zero_entered'))
```

未录入数：

```text
count(input_status = 'not_entered')
```

分区状态：

```text
已录入数 = 0 -> not_started
已录入数 > 0 且小于总数 -> in_progress
已录入数 = 总数 -> completed
```

## 7. 后端建议类

```text
AdminInventoryTaskController
InventoryTaskService
TaskSnapshotService
TaskProgressService

InventoryTaskEntity
TaskZoneEntity
TaskZoneMaterialEntity

InventoryTaskCreateRequest
InventoryTaskQueryRequest
InventoryTaskListResponse
InventoryTaskDetailResponse
TaskZoneProgressResponse
```

## 8. 校验

- 门店不存在返回 `STORE_NOT_FOUND`。
- 门店停用返回 `STORE_DISABLED`。
- 模板不存在返回 `TEMPLATE_NOT_FOUND`。
- 模板停用返回 `TEMPLATE_DISABLED`。
- 截止时间不能为空。
- 盘点月份格式建议为 `yyyy-MM`。
- 同一门店同一月份是否允许多个任务，需要产品确认；首期建议不允许重复。

## 9. 不做范围

- 不在本模块处理门店录入。
- 不在本模块处理提交汇总。
- 不在本模块更新门店默认清单。

## 10. 验收标准

- 总部可以创建月盘任务。
- 创建任务后生成任务分区快照。
- 创建任务后生成任务分区物料快照。
- 快照包含物料名称、规格、单位。
- 修改模板后，已创建任务不变化。
- 修改门店默认清单后，已创建任务不变化。
- 任务初始进度正确。
