# 09 原生微信小程序计划

## 1. 目标

实现门店店长在小程序中完成月盘任务：

- 查看任务。
- 进入任务详情。
- 按分区录入物料数量。
- 调整当前任务分区与物料。
- 查看汇总预览。
- 提交任务。
- 查看盘点结果。

注意：物料和门店来自基础数据库管理系统。小程序端只能从已同步的物料库中选择物料加入当前任务分区，不能新建物料主数据。

## 2. 页面结构

```text
pages/task-list/index              # 月盘任务列表
pages/task-detail/index            # 任务详情
pages/zone-input/index             # 分区盘点
pages/zone-adjust/index            # 调整分区与物料
pages/add-zone/index               # 添加分区
pages/add-material/index           # 添加物料
pages/task-material-search/index   # 任务内物料搜索
pages/summary/index                # 汇总预览
pages/result/index                 # 盘点结果
```

## 3. API 封装

建议：

```text
utils/request.js
api/task.js
api/input.js
api/material.js
```

首期可在请求头中模拟：

```text
X-User-Id
X-Role: store_manager
X-Store-Id
```

## 4. 月盘任务列表页

页面：`pages/task-list/index`

展示：

- 门店名称。
- 当前任务。
- 历史任务。
- 任务名称。
- 月份。
- 截止时间。
- 状态。
- 当前进度。

操作：

- 开始盘点。
- 继续盘点。
- 查看结果。

接口：

```text
GET /api/store/inventory-tasks
```

## 5. 任务详情页

页面：`pages/task-detail/index`

展示：

- 任务基础信息。
- 总体录入进度。
- 分区数量。
- 物料总数。
- 已录入。
- 未录入。
- 分区列表。

操作：

- 调整分区与物料。
- 查看盘点汇总。
- 开始/继续录入分区。
- 查看记录。
- 底部主按钮根据状态变化。

接口：

```text
GET /api/store/inventory-tasks/{id}
```

## 6. 分区盘点页

页面：`pages/zone-input/index`

展示：

- 分区名称。
- 当前分区进度。
- 搜索框。
- 物料列表。
- 数量输入框。
- 备注入口。

操作：

- 输入数量。
- 保存本分区。
- 返回任务详情。

接口：

```text
GET /api/store/inventory-tasks/{id}/zones/{zoneId}
PUT /api/store/inventory-tasks/{id}/zones/{zoneId}/inputs
```

输入规则：

- 允许整数。
- 允许小数。
- 允许 0。
- 不允许负数。
- 空值表示未录入。
- 备注非必填。
- 单位不可编辑。

特别注意：

- 小程序端输入框的空字符串，在提交给后端前应转为 `null`。
- 字符串 `"0"` 应转为数字 `0`，不能转成 `null`。
- 负数应在前端提示，也必须由后端再次校验。

## 7. 搜索兜底

当前分区搜索：

- 默认搜索当前分区物料。
- 按物料名称和规格模糊匹配。
- 实时过滤。
- 清空搜索恢复完整列表。

无结果时展示：

- 当前分区未找到相关物料。
- 入口 1：搜索本次任务全部物料。
- 入口 2：从物料库添加到当前分区。

物料库说明：

- 物料库数据来自盘点工具本地 `material` 同步缓存。
- 最终源头是基础数据库管理系统。
- 小程序端不提供新建物料入口。

增强页面：

```text
pages/task-material-search/index
pages/add-material/index
```

## 8. 调整分区与物料页

页面：`pages/zone-adjust/index`

功能：

- 新增分区。
- 删除空分区。
- 给分区添加物料。
- 删除分区内未录入物料。
- 上移/下移物料。
- 将未录入物料移动到其他分区。

限制：

- 不支持拖拽排序。
- 已录入物料不可移动。
- 已录入物料不可删除。
- 已提交任务不可调整。
- 添加物料时只能选择已同步且启用的物料。

该能力可以在主流程跑通后实现。

## 9. 汇总预览页

页面：`pages/summary/index`

展示：

- 分区总数。
- 已完成分区数。
- 未完成分区数。
- 物料总数。
- 已录入物料数。
- 未录入物料数。
- 物料汇总表。

操作：

- 返回继续录入。
- 提交任务。

规则：

- 存在未录入时，提交按钮不可用。
- 全部录完时，允许提交。

接口：

```text
GET  /api/store/inventory-tasks/{id}/summary
POST /api/store/inventory-tasks/{id}/submit
```

## 10. 盘点结果页

页面：`pages/result/index`

展示：

- 任务名称。
- 月份。
- 提交时间。
- 提交人。
- 按分区查看。
- 按物料汇总查看。

接口：

```text
GET /api/store/inventory-tasks/{id}/result
```

## 11. UI 要求

- 移动端优先。
- 单列布局。
- 输入控件足够大。
- 底部主操作按钮固定。
- 状态文案清晰。
- 已提交任务全部只读。
- 不在页面上展示冗长说明文字。

## 12. 实现顺序

```text
1. 请求封装和模拟登录头
2. 任务列表页
3. 任务详情页
4. 分区盘点页
5. 保存分区录入
6. 汇总预览页
7. 提交任务
8. 盘点结果页
9. 当前分区搜索
10. 搜索兜底
11. 调整分区与物料
```

## 13. 验收标准

- 店长能查看本门店任务。
- 店长能进入任务详情。
- 店长能按分区录入。
- 空值和 0 显示正确。
- 未全部录入不能提交。
- 全部录入后可以提交。
- 提交后任务只读。
- 可查看按分区和按物料汇总结果。
