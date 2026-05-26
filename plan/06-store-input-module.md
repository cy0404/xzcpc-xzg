# 06 门店任务与分区录入模块

## 1. 目标

实现小程序端店长查看任务、查看任务详情、按分区录入数量、保存分区数据、查询汇总预览的能力。

本模块不处理最终提交事务，提交逻辑见 `07-submit-summary-module.md`。

物料和门店来自基础数据库管理系统同步缓存。本模块只引用已生成的任务快照，不直接维护物料或门店主数据。

## 2. 涉及表

```text
inventory_task
task_zone
task_zone_material
material
store
user_account
```

字段以 `01-database-design.md` 为准。

## 3. API

### 3.1 查询门店任务列表

```text
GET /api/store/inventory-tasks
```

查询范围：

- 仅当前店长所属门店。

返回字段：

```text
id
taskName
taskMonth
deadline
status
zoneCount
materialCount
enteredCount
notEnteredCount
submittedAt
```

### 3.2 查询门店任务详情

```text
GET /api/store/inventory-tasks/{id}
```

返回内容：

- 任务基础信息。
- 总体进度。
- 分区列表。
- 分区进度。
- 底部按钮状态所需字段。

### 3.3 查询分区物料

```text
GET /api/store/inventory-tasks/{id}/zones/{zoneId}
```

查询参数：

```text
keyword    按物料名称或规格搜索当前分区物料，可选
```

返回字段：

```text
taskZoneMaterialId
materialId
materialName
spec
unit
inputQty
remark
inputStatus
sortNo
```

### 3.4 保存分区录入

```text
PUT /api/store/inventory-tasks/{id}/zones/{zoneId}/inputs
```

请求体：

```json
{
  "items": [
    {
      "taskZoneMaterialId": 1,
      "inputQty": 5000,
      "remark": "无"
    },
    {
      "taskZoneMaterialId": 2,
      "inputQty": 0,
      "remark": "过期报损"
    },
    {
      "taskZoneMaterialId": 3,
      "inputQty": null,
      "remark": ""
    }
  ]
}
```

### 3.5 查询汇总预览

```text
GET /api/store/inventory-tasks/{id}/summary
```

返回内容：

- 分区总数。
- 已完成分区数。
- 未完成分区数。
- 物料总数。
- 已录入物料数。
- 未录入物料数。
- 按物料汇总的预览列表。
- 是否允许提交。

## 4. 录入规则

- 数量允许整数。
- 数量允许小数。
- 数量允许 0。
- 数量不允许负数。
- 空值表示未录入。
- 备注非必填。
- 单位不可编辑。

录入状态计算：

```text
input_qty is null -> not_entered
input_qty = 0     -> zero_entered
input_qty > 0     -> entered
```

特别注意：

- `0` 不能被当成空。
- 前端传空字符串时，后端应统一转换或校验为 `null`。
- 保存接口允许局部仍未录入。
- 提交接口才要求全部录入。

## 5. 任务状态更新

保存分区录入后：

- 如果任务状态是 `not_started`，且已有任意物料录入，则更新为 `in_progress`。
- 如果任务状态是 `submitted`，禁止保存。
- 任务状态不在本模块更新为 `submitted`。

## 6. 当前分区搜索

搜索范围：

- 当前任务。
- 当前分区。
- 当前分区物料列表。

匹配字段：

- 物料名称。
- 规格。

搜索规则：

- 实时过滤。
- 清空搜索后恢复完整列表。
- 搜索结果中允许直接录入和保存。
- 当前分区无结果时，小程序显示兜底入口。

兜底能力可以在后续增强：

- 搜索本次任务全部物料。
- 从总部物料库添加到当前分区。

这里的“总部物料库”指基础数据库管理系统同步到盘点工具本地的 `material` 缓存，不是盘点工具自建物料库。

## 7. 后端建议类

```text
StoreInventoryTaskController
StoreTaskService
StoreInputService
TaskProgressService
TaskSummaryPreviewService

StoreTaskListResponse
StoreTaskDetailResponse
ZoneInputResponse
ZoneInputSaveRequest
SummaryPreviewResponse
```

## 8. 校验

- 店长只能访问自己门店的任务。
- 任务不存在返回 `TASK_NOT_FOUND`。
- 分区不存在返回 `TASK_ZONE_NOT_FOUND`。
- 已提交任务保存返回 `TASK_SUBMITTED`。
- 数量为负返回 `INPUT_QTY_NEGATIVE`。
- 数量格式非法返回 `INPUT_QTY_INVALID`。
- 请求中的 `taskZoneMaterialId` 必须属于当前任务和当前分区。

## 9. 不做范围

- 不在本模块提交任务。
- 不在本模块生成最终汇总表。
- 不在本模块更新门店默认清单。
- 不在本模块维护模板。
- 不在本模块新增、编辑物料主数据。

## 10. 验收标准

- 店长可以查看本门店任务列表。
- 店长可以查看任务详情和分区进度。
- 店长可以进入分区盘点页。
- 店长可以保存整数、小数、0。
- 空值保存后状态为 `not_entered`。
- 0 保存后状态为 `zero_entered`。
- 大于 0 保存后状态为 `entered`。
- 已提交任务不可保存。
- 当前分区搜索按名称和规格生效。
