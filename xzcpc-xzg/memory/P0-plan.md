---
updated: 2026-07-07
importance: 5
---

# 象掌柜 v2.0 — P0 开发规划

> 基于 v2.0 PRD（`projectDesign/`），结合当前 xzcpc-xzg 代码现状制定。

---

## 一、目标与范围

### 目标
在盘点工具 v1.0 基础上，完成 P0 主版本升级：门店现场执行底座（盘点/报损/调货/物流）+ 协同触达（问题处理/企微通知）。

### P0 包含

| 编号 | 模块 | 类型 | 说明 |
|------|------|------|------|
| A1 | 盘点优化 | 改造现有 | 盲盘、扫码、称重去皮、差异处理、模板推荐、企迈同步 |
| A2 | 门店报损 | 全新 | 日常报损 + 到货验收报损 + 半成品去皮 + 厂家飞书H5确认 |
| A3 | 调货管理 | 全新 | 店间直调 7 状态流转 + HQ 只读台账 |
| A4 | 物流信息 | 全新 | HQ 录入运单号 + 门店查看轨迹 |
| B1 | 问题处理 | 全新 | 门店上报 + 象目经理同步 + HQ 台账 |
| B2 | 企微通知 | 新基础设施 | 6 类事件异步通知店长企微个人 |

### P0 不包含

- 经营概览页 / 首页大改（P2）
- 督导拜访（P2）
- 智能订货 / 新店筹备（P1）
- 通用任务下发 / 门店报表 / 员工打卡（8 月）

---

## 二、当前项目现状速览

### 后端模块

```
mp-server (:30261) → mp (81 files, 13 controllers)
server (:4026)     → task → template → common
                     expense → common
                     people → common + task
```

### 数据库（25 张表）

| 域 | 表 |
|----|-----|
| 物料 | material, material_inventory_rule, material_conversion_rule |
| 模板 | template, template_zone, template_zone_material |
| 任务 | task, task_zone, task_zone_material, task_material_summary, store_zone_material |
| 支出 | expense_type, expense_item, expense_record, self_purchase_material |
| 人员 | employee, employee_registration_application, owner_registration, owner_bind_application |
| 门店 | store_info, store_contact, store_manager_session |
| 权限 | admin_permission |
| 工时 | store_work_hours |
| 日志 | operation_log, login_log |

### 已有前端

| 端 | 框架 | 页面数 |
|----|------|--------|
| admin（总部） | Vue 3 + Ant Design Vue 4 | 15 页 |
| miniapp（门店） | uni-app Vue 3 | 30+ 页 |

---

## 三、角色分权设计（P0 最小方案）

### 当前问题

`LoginUser` 无角色字段，所有小程序登录用户一视同仁。但 P0 报损和问题上报要求"仅店长"。

### 方案

在 `LoginUser` 中加一个 `role` 字段，登录时从 `owner_registration` 表判断：

```java
// LoginUser 新增
private String role;  // store_manager | owner | staff
```

| 角色 | 判定来源 | P0 权限 |
|------|---------|---------|
| `store_manager` | `store_manager_session.store_id` 匹配 | 全部操作（报损登记、问题上报、盘点执行、调货发起） |
| `owner` | `owner_registration` 表存在绑定记录 | 可查看所有内容，单店态继承店长执行能力 |
| `staff` | 员工表匹配 | 仅盘点执行（数量录入） |

### 涉及改动

| 文件 | 改动 |
|------|------|
| `mp/.../context/LoginUser.java` | 加 `role` 字段 |
| `mp/.../MpAuthController.java` | 登录时查 `owner_registration` 判定角色 |
| `mp/.../interceptor/MpLoginInterceptor.java` | 无需改动，JWT 中带 role |
| 各 P0 Controller | `if (!"store_manager".equals(role))` 校验 |

---

## 四、数据库改动

### 4.1 新增 11 张表

| # | 表名 | 所属模块 | P0 阶段 |
|---|------|---------|---------|
| 1 | `barcode_supplement` | 盘点-扫码 | A1 |
| 2 | `container_config` | 盘点-称重 | A1 |
| 3 | `inventory_difference` | 盘点-差异 | A1 |
| 4 | `difference_process_log` | 盘点-差异 | A1 |
| 5 | `inventory_template_recommendation` | 盘点-模板 | A1 |
| 6 | `loss_report` | 报损 | A2 |
| 7 | `transfer_order` | 调货 | A3 |
| 8 | `transfer_order_item` | 调货 | A3 |
| 9 | `logistics_record` | 物流 | A4 |
| 10 | `issue` | 问题 | B1 |
| 11 | `notification_log` | 通知 | B2 |

### 4.2 修改 2 张现有表

| 表 | 改动 | 原因 |
|----|------|------|
| `task` | 状态值支持 `pending_submit`、`overdue` | 盘点流程细化 |
| `store_manager_session` | 加 `role` 字段 `VARCHAR(20)` | 登录时缓存角色 |

---

## 五、API 改动清单

### A1 盘点优化（改造现有 mp + server 端）

| 方法 | 路径 | 说明 | 类型 |
|------|------|------|------|
| GET | `/api/mp/inventory/scan/{barcode}` | 扫码识别物料 | 新增 |
| POST | `/api/mp/inventory/barcode/supplement` | 条码补充申请 | 新增 |
| POST | `/api/mp/inventory/weigh` | 称重录入（含皮重扣除） | 新增 |
| GET | `/api/mp/inventory/containers` | 获取容器列表 | 新增 |
| POST | `/api/mp/inventory/task/{taskId}/zone` | 门店新增分区 | 新增 |
| DELETE | `/api/mp/inventory/task/{taskId}/zone/{zoneId}` | 门店删除分区 | 新增 |
| PUT | `/api/mp/inventory/task/{taskId}/zones/sort` | 分区分区排序 | 新增 |
| GET | `/api/admin/inventory/differences` | 差异项列表 | 新增 |
| POST | `/api/admin/inventory/differences/{id}/adjust` | 确认调整 | 新增 |
| POST | `/api/admin/inventory/differences/{id}/close` | 标记无需调整 | 新增 |
| POST | `/api/admin/inventory/differences/{id}/convert` | 转问题单 | 新增 |
| POST | `/api/admin/inventory/sync/qimai/{taskId}` | 触发企迈同步 | 新增 |
| POST | `/api/admin/inventory/containers` | 新增容器 | 新增 |
| PUT | `/api/admin/inventory/containers/{id}` | 编辑容器 | 新增 |

### A2 门店报损

| 方法 | 路径 | 说明 | 类型 |
|------|------|------|------|
| POST | `/api/mp/loss-report` | 新建报损 | 新增 |
| GET | `/api/mp/loss-report/list` | 报损列表 | 新增 |
| GET | `/api/mp/loss-report/{id}` | 报损详情 | 新增 |
| GET | `/api/admin/loss-report/list` | 全门店报损列表 | 新增 |
| GET | `/api/admin/containers` | 容器配置列表 | 新增 |
| POST | `/api/admin/containers` | 新建容器 | 新增 |
| PUT | `/api/admin/containers/{id}` | 编辑容器 | 新增 |

### A3 调货管理

| 方法 | 路径 | 说明 | 类型 |
|------|------|------|------|
| POST | `/api/mp/transfer` | 发起调货 | 新增 |
| PUT | `/api/mp/transfer/{id}/confirm` | 确认调货 | 新增 |
| PUT | `/api/mp/transfer/{id}/ship` | 发货/交接 | 新增 |
| PUT | `/api/mp/transfer/{id}/receive` | 收货确认 | 新增 |
| PUT | `/api/mp/transfer/{id}/cancel` | 取消调货 | 新增 |
| PUT | `/api/mp/transfer/{id}/reject` | 拒绝调货 | 新增 |
| GET | `/api/mp/transfer/list` | 调货列表 | 新增 |
| GET | `/api/mp/transfer/{id}` | 调货详情 | 新增 |
| GET | `/api/admin/transfer/list` | 总部只读台账 | 新增 |

### A4 物流信息

| 方法 | 路径 | 说明 | 类型 |
|------|------|------|------|
| GET | `/api/mp/logistics/list` | 门店物流列表 | 新增 |
| GET | `/api/mp/logistics/{id}` | 物流轨迹详情 | 新增 |
| POST | `/api/admin/logistics` | 总部录入运单号 | 新增 |
| GET | `/api/admin/logistics/list` | 总部物流列表 | 新增 |

### B1 问题处理

| 方法 | 路径 | 说明 | 类型 |
|------|------|------|------|
| POST | `/api/mp/issues` | 门店提交问题 | 新增 |
| GET | `/api/mp/issues/list` | 门店问题列表 | 新增 |
| GET | `/api/mp/issues/{id}` | 门店问题详情 | 新增 |
| GET | `/api/admin/issues/list` | 总部问题台账 | 新增 |
| GET | `/api/admin/issues/{id}` | 总部问题详情 | 新增 |

### B2 企微通知（内部）

| 方式 | 说明 |
|------|------|
| Spring Event 监听 | 监听各模块状态变更，异步触发企微发送 |
| `NotificationLogService` | 记录发送状态、失败重试 |

---

## 六、前端改动

### admin（总部端）

| 页面 | 改动 | P0 阶段 |
|------|------|---------|
| 盘点管理 | 新增"差异处理"Tab + "物料管理"增强 | A1 |
| 报损管理 | 全新页面：记录列表 + 容器配置 | A2 |
| 调货台账 | 全新页面：只读列表 + 详情 | A3 |
| 物流管理 | 全新页面：运单录入 + 列表 | A4 |
| 问题台账 | 全新页面：列表 + 详情（含象目经理单号） | B1 |

### miniapp（门店端）

| 页面 | 改动 | P0 阶段 |
|------|------|---------|
| 盘点执行 | 新增扫码页（全屏摄像头）+ 称重去皮抽屉 | A1 |
| 盘点任务详情 | 新增分区管理（添加/删除/排序） | A1 |
| 盘点任务列表 | 新增"待提交"状态 + 逾期展示 | A1 |
| 报损 | 全新模块：列表 + 新建（日常/到货验收）+ 详情 + 去皮计算 | A2 |
| 调货 | 全新模块：列表 + 发起 + 确认/拒绝 + 发货/收货 | A3 |
| 物流 | 全新模块：列表 + 轨迹详情 | A4 |
| 问题 | 全新模块：列表 + 新建 + 详情 + 待验收 | B1 |
| 首页 | **不做大改动**，保持现有结构 | — |

---

## 七、执行顺序

```
Week 1              Week 2-3              Week 4-5
──────────────      ──────────────        ──────────────
A1 盘点优化          A2 门店报损            B1 问题处理
├─ 状态扩展         ├─ 数据库建表           ├─ 数据库建表
├─ 盲盘保障         ├─ mp端 3 页            ├─ mp端 3 页
├─ 扫码API          ├─ admin端 2 页         ├─ admin端 2 页
├─ 容器去皮         ├─ 飞书H5确认页         ├─ 象目经理同步
├─ 差异处理                          B2 企微通知
├─ 模板推荐         A3 调货管理              ├─ 通知服务
└─ 企迈同步         ├─ 数据库建表           ├─ 6类事件监听
                    ├─ mp端 4 页            └─ 通知日志
       ↓            └─ admin端 1 页
   LoginUser.role ──────────────────────────┘
                    A4 物流信息
                    ├─ 数据库建表
                    ├─ mp端 2 页
                    └─ admin端 2 页
```

### 为什么这个顺序

1. **盘点先做**：A1 是已有代码改造，团队最熟悉，能最快验证 v2.0 方向。同时差异处理的结果可以转问题单，为 B1 铺路。
2. **报损紧跟**：A2 是 P0 中门店最高频的新功能，且和盘点的物料库强关联。
3. **调货+物流并行**：A3/A4 相对独立，可以和 A2 并行开发。
4. **问题+通知收尾**：B1 依赖 A1（差异转问题），B2 依赖所有模块的状态机稳定后接入。

---

## 八、风险评估

| 风险 | 影响 | 应对 |
|------|------|------|
| 盲盘逻辑遗漏 | 门店端意外暴露账面库存 | 代码 Review 重点检查 DTO 字段，写单元测试断言返回字段白名单 |
| 差异处理数据量大 | 提交时全量比对慢 | 异步生成差异，先返回提交成功，差异结果通过企微通知 |
| 飞书 H5 厂家确认 | 外部页面开发周期长 | 一期可用"总部代为确认"作为降级方案 |
| 企微通知对接 | 企微 API 限频或有变动 | 异步队列 + 失败重试 + 人工兜底 |
| 小程序首页不改动 | 老板多店用户体验割裂 | P0 阶段老板暂时也是单店登录（按 store_id），P2 再统一 |
