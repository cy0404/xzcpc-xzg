# 督导拜访（P2）— 功能与测试文档

> 最后更新：2026-07-23

## 一、概述

督导带电脑到店，在总部端 PC 创建拜访单，基于上月经营数据、历史拜访记录和现场沟通填写拜访记录与行动计划。店长或加盟商老板在小程序端确认或提出异议，确认后行动计划拆分为门店待处理任务，门店执行反馈，督导审核闭环。

---

## 二、数据库表

### 2.1 新增表

| 表名 | 说明 |
|------|------|
| `supervisor_visit` | 督导拜访单 |
| `supervisor_visit_action` | 行动计划（确认后成为门店任务） |
| `supervisor_store_access` | 督导门店访问权限映射（openId → 门店 1:N） |

### 2.2 修改表

| 表名 | 改动 |
|------|------|
| `store_info` | 新增 `supervisor_name` 字段（已存在） |
| `admin_permission` | 统一 collation 为 utf8mb4_0900_ai_ci |
| `store_info` | 统一 collation 为 utf8mb4_0900_ai_ci |

---

## 三、权限体系

### 3.1 总部端

| 角色 | 权限 |
|------|------|
| `headquarters_admin` | 查看全部门店拜访单、全部督导选项 |
| `operation_admin`（督导） | 只看 `supervisor_store_access` 中映射的门店，督导下拉只能选自己 |
| `operation_admin`（督导领导） | 在 `supervisor_store_access` 中映射全部门店即可看全部 |

### 3.2 小程序端

| 操作 | 店长 | 老板 | 店员 |
|------|:--:|:--:|:--:|
| 查看拜访单详情 | ✅ | ✅ | ✅ |
| 待确认卡片 | 仅被选为确认人时 | 仅被选为确认人时 | ❌ |
| 确认/异议 | 被选为确认人时 | 被选为确认人时 | ❌ |
| 拜访任务卡片 | 任务角色=店长时 | 任务角色=老板时 | ❌ |
| 提交完成反馈 | 同上 | 同上 | ❌ |

---

## 四、状态机

### 4.1 拜访单状态

```
draft → pending_confirm → in_progress → completed
              ↓                ↑
          objection ──────────┘ (督导修改后重新提交)
```

| 状态 | 说明 |
|------|------|
| `draft` | 草稿，可编辑、提交 |
| `pending_confirm` | 待确认，可退回补充 |
| `objection` | 异议待处理，可修改 |
| `in_progress` | 跟进中，任务已生成 |
| `completed` | 全部任务审核通过 |

### 4.2 任务状态

```
pending → pending_review → completed
   ↓            ↓
overdue     returned → (重新提交) → pending_review
```

逾期判断：`tracking_time < 今天` 且 `status = pending` 时动态标记为 overdue。逾期仍可提交反馈。

逾期痕迹：提交后若 `submitted_at > tracking_time`，总端展示"（逾期提交）"。

---

## 五、功能清单

### 5.1 总部端

| 功能 | 说明 |
|------|------|
| 台账列表 | 筛选（门店/督导/状态/确认状态/逾期/日期/关键词）+ 表格（拜访单双行+进度条+状态标签）+ 抽屉详情 |
| 创建拜访单 | 三列基础信息 + 经营数据（主表单区）+ 沟通记录 + 行动计划（弹窗新增/编辑）+ 右侧面板（门店评级+历史记录） |
| 编辑拜访单 | 草稿/待确认/异议状态可编辑，固定底栏（取消/保存草稿/提交确认/重新提交） |
| 退回补充 | pending_confirm 状态可撤回修改后重新提交 |
| 异议处理 | 查看异议说明 → 修改 → 重新提交 |
| 督导审核 | 审核通过/退回补充（可调整追踪时间），审核后表格自动刷新进度 |
| 权限过滤 | 门店下拉/督导下拉按 supervisor_store_access 映射过滤 |
| 逾期展示 | 表格进度含动态逾期+退回计数，抽屉标签红色 |

### 5.2 小程序端

| 功能 | 说明 |
|------|------|
| 首页待办 | 待确认拜访单（按确认人 openid 匹配）+ 拜访任务逐条展示（按角色过滤，逾期黄色卡片） |
| 拜访单详情 | 经营数据+沟通记录+行动计划+历史记录（只展示）
| 确认/异议 | 底部 50/50 按钮，确认需二次确认，异议需填写说明（必填） |
| 任务详情 | 按原型设计：状态卡片+面板+任务内容+退回说明+反馈区+审核结果+拜访记录链接+底部提交 |
| 图片上传 | 选图后上传到 `/api/mp/upload/voucher`，存真实 URL |
| 权限 | 仅被选确认人可见待确认卡片，仅匹配角色可见任务卡片 |

---

## 六、API 清单

### 6.1 总部端

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/supervisor-visit` | 台账分页 |
| POST | 同上 | 创建 |
| PUT | `/{id}` | 编辑 |
| GET | `/{id}` | 详情 |
| POST | `/{id}/submit` | 提交确认 |
| POST | `/{id}/handle-objection` | 处理异议 |
| GET | `/store-data/{storeId}` | 门店经营数据 |
| GET | `/supervisor-stores` | 按权限过滤的门店列表 |
| GET | `/supervisor-options` | 按权限过滤的督导列表 |
| GET | `/store-employees/{storeId}` | 门店在职员工 |
| POST | `/actions/{actionId}/approve` | 审核通过 |
| POST | `/actions/{actionId}/reject` | 审核退回 |

### 6.2 小程序端

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/mp/supervisor-visit/pending` | 待确认列表 |
| GET | `/overview` | 首页概览（待确认数+任务列表+首条ID） |
| GET | `/{id}` | 详情（校验门店归属+确认人匹配） |
| POST | `/{id}/confirm` | 确认（需店长/老板角色） |
| POST | `/{id}/object` | 异议（需店长/老板角色） |
| GET | `/actions` | 我的任务列表 |
| GET | `/actions/{actionId}` | 任务详情（含动态逾期判断） |
| POST | `/actions/{actionId}/complete` | 提交完成反馈（需店长/老板角色） |

---

## 七、迁移脚本

| 文件 | 说明 |
|------|------|
| `migration-add-supervisor-visit.sql` | 创建拜访单+行动计划表 |
| `migration-add-supervisor-store-access.sql` | 创建权限映射表 + collation 统一 + 初始化 |
| `migration-add-supervisor-visit-confirm-openid.sql` | 确认人 openid/name 字段 |
