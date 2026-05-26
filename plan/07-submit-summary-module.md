# 07 提交、汇总与默认清单回写模块

## 1. 目标

实现盘点任务提交事务，完成任务锁定、物料汇总、提交信息写入、门店默认分区物料清单更新。

这是项目最关键、最容易出错的模块。实现时必须严格按本文档执行。

## 2. 涉及表

```text
inventory_task
task_zone
task_zone_material
task_material_summary
store_zone_material
user_account
```

字段以 `01-database-design.md` 为准。

## 3. API

### 3.1 提交任务

```text
POST /api/store/inventory-tasks/{id}/submit
```

返回：

```json
{
  "taskId": 1,
  "status": "submitted",
  "submittedAt": "2026-04-30 21:20:00"
}
```

### 3.2 查询任务结果

```text
GET /api/store/inventory-tasks/{id}/result
GET /api/admin/inventory-tasks/{id}/result
```

结果视图：

- 按分区查看。
- 按物料汇总查看。

## 4. 提交前校验

必须全部满足：

1. 当前用户是店长。
2. 当前店长属于任务门店。
3. 任务存在。
4. 任务状态不是 `submitted`。
5. 任务下存在分区。
6. 任务下所有分区物料均已录入。
7. 不存在 `input_qty is null`。
8. 数量允许为 0。
9. 备注允许为空。

未满足时不能提交。

## 5. 事务流程

提交逻辑必须使用一个数据库事务。

流程：

```text
1. 锁定任务记录或在事务内重新读取任务。
2. 校验用户权限。
3. 校验任务未提交。
4. 查询任务所有 task_zone_material。
5. 校验不存在 input_qty is null。
6. 删除该任务旧的 task_material_summary。
7. 按 material_id 汇总 input_qty。
8. 写入 task_material_summary。
9. 更新 inventory_task.status = submitted。
10. 写入 submitted_by。
11. 写入 submitted_at。
12. 更新门店默认分区物料清单。
13. 提交事务。
```

## 6. 汇总规则

汇总维度：

```text
task_id + material_id
```

汇总公式：

```text
sum(input_qty)
```

规则：

- 空值不参与汇总，但提交时不允许有空值。
- 0 参与汇总。
- 同一物料出现在多个分区时，数量求和。
- `zone_count` 记录该物料出现的分区数量。
- 汇总表保存物料名称、规格、单位快照。

示例：

```text
前台冷藏区：鲜奶 5000 ml
操作台下方：鲜奶 1800 ml
汇总结果：鲜奶 6800 ml，出现分区数 2
```

## 7. 默认清单回写规则

提交成功后，系统更新门店默认分区物料清单。

规则：

1. 遍历当前任务所有 `task_zone_material`。
2. 对每条记录，根据 `store_id + zone_name + material_id` 定位门店默认清单关系。
3. 如果 `input_qty = 0`：
   - 从 `store_zone_material` 移除该关系。
4. 如果 `input_qty > 0`：
   - 如果关系存在，保留并更新排序、状态、更新时间。
   - 如果关系不存在，新增该关系。
5. 不处理 `input_qty is null`，因为提交前已经禁止。

注意：

- 移除的是门店默认清单关系。
- 不删除当前任务记录。
- 不删除总部模板。
- 不删除基础数据库管理系统中的物料主数据。
- 不影响历史任务数据。

## 8. 并发与幂等

需要防止重复提交：

- 如果任务状态已是 `submitted`，返回 `TASK_SUBMITTED`。
- 提交时建议对任务行加锁，或使用状态条件更新。
- 同一任务重复点击提交按钮，不应重复生成汇总或重复回写默认清单。

建议实现：

```text
update inventory_task
set status = 'submitted', submitted_by = ?, submitted_at = ?
where id = ? and status <> 'submitted'
```

若更新行数为 0，则认为已提交。

## 9. 后端建议类

```text
TaskSubmitService
TaskSummaryService
StoreDefaultListService
TaskResultService

TaskSubmitResponse
TaskResultResponse
TaskResultByZoneResponse
TaskResultByMaterialResponse
```

## 10. 错误码

```text
TASK_NOT_FOUND
TASK_SUBMITTED
TASK_NOT_COMPLETED
FORBIDDEN
STORE_NOT_FOUND
```

## 11. 不做范围

- 不做审批流。
- 不做差异分析。
- 不做报损自动生成。
- 不做订货建议。
- 不做财务审核。

## 12. 验收标准

- 未全部录入时不能提交。
- 录入 0 的物料允许提交。
- 提交后任务状态变为 `submitted`。
- 提交后写入提交人和提交时间。
- 提交后生成物料汇总表。
- 同一物料跨分区汇总准确。
- 提交后任务不可编辑。
- `input_qty = 0` 的分区物料关系从门店默认清单移除。
- `input_qty > 0` 的分区物料关系保留或新增到门店默认清单。
- 历史任务明细不受默认清单变化影响。
