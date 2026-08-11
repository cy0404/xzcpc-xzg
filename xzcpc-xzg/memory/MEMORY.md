# xzcpc-xzg 核心知识库

> 每次会话自动加载。详细内容见各专题文件。

## 📋 速查卡

| 项 | 值 |
|----|-----|
| **项目** | 盘点工具 1.0 / 象掌柜 (xzcpc-xzg) |
| **用途** | 门店月度盘点管理系统：总部配模板→下发任务→门店小程序录入→自动汇总 |
| **技术栈** | Java 17 / Spring Boot 3.2.5 / MyBatis-Plus 3.5.5 / MySQL 8.0 |
| **前端** | Vue 3 + Ant Design Vue 4（总部）/ uni-app Vue 3（小程序） |
| **端口** | 总部端 4026 / 小程序端 30261 |
| **模块数** | 8 个 Maven 模块 / ~210 Java 文件 / ~125 API |
| **数据库** | `store_inventory` / schema.sql 建表 / 30 个迁移脚本 |

## 🆕 近期变更

### 2026-08-03~08-10 飞书报损通知重构

**核心改动**：报损卡片从群聊改为按角色发个人，卡片按审核/补发维度拆分，群聊仅保留每日统计。

- **DB 变更**：
  - `V13__loss_notify_card_config.sql` — 旧表 `material_loss_notify_config` 替换为竖表 `loss_notify_card_config`，按 `category + card_type + feishu_user_id + status` 配置收件人
  - `V14__loss_report_log_attachment.sql` — `loss_report_log` 加 `attachment_url TEXT` 字段
  - `sys_config` 新增 `feishu_avocado_material_id`
- **新建工具类**：`common/.../FeishuMessageService.java` — 飞书 token、发送（`sendToUser`/`sendToChat`）、配置查询、分类分组。支持多人
- **重写 DailySummaryJob**：每日9:30发卡片给个人，卡片维度：

| 分类 | 卡片 | card_type | 颜色 | 频率 |
|------|------|-----------|------|------|
| 水果蔬菜 | A 审核 | pending | 黄 | 每天 |
| 其他类-外包装 | B1a 审核 | damage_audit | 黄 | 每天 |
| 其他类-非外包装 | B2a 审核 | other_audit | 蓝 | 每天 |
| 其他类-全部 | B2b 补发 | other_resend | 绿 | 每天 |
| 牛油果泥 | E 审核 | avocado_audit | 蓝 | 每天 |
| 全部 | F 群统计 | — | 蓝 | 每天 |

卡片标题区分审核/补发：`到货验收报损 · 审核 · XXX` / `到货验收报损 · 补发`。水果蔬菜不按原因拆分。加急卡片店长审批通过后才发。

- **H5 页面**（`loss-daily-confirm.html`）：
  - 审核 Tab + 补发 Tab（按门店分组折叠）+ 牛油果泥 3 Tab（未审核/已审核，未审核下含未下载/已下载子Tab）
  - 确认/拒绝弹窗：备注输入 + 附件上传（图片/视频，代理到 mp 端）
  - 视频下载 ZIP（按 report IDs，文件名含物料名+数量+日期）+ 同步下载 Excel
  - 牛油果泥统计表（按门店汇总）+ 导出 Excel（按 Tab 区分已下载/未下载）
  - 全选只勾可见物料、搜索支持门店/物料/企迈单号、已通过/已拒绝/已收货/未收到货标签
  - 页面可见时自动刷新、时间戳防缓存
- **小程序端**：详情页流程时间线展示操作附件（图片预览/视频播放）、列表展示最新进度
- **其他修复**：
  - `MaterialServiceImpl` 缓存名冲突、`InventoryReportController` SQL 分页、`MpTaskServiceImpl` 物理删除
  - `GlobalExceptionHandler` ClientAbortException→WARN、Admin 端视频支持
  - `application-prod.yml` 加 multipart 50MB + tomcat max-post 50MB
- **流程**：提交→待审核(pending)→审核确认(registered)→补发(confirmed_resend)→门店收货(received/not_received)

### 2026-07-28~08-01 盘点差异处理模块
- **计算引擎**：`DifferenceCalcService/Impl` — 8 数据源差异计算，时间窗口：上次 `submitted_at` → 本次 `submitted_at`
- **公式**：理论剩余 = 上月剩余 + 采购(PG) + 订货(PG) + 调货净值 + 还货净值 - 报损 + 自购 - 消耗(PG)
- **PG 数据源**：`stat_date` 按天查，NULL unit 自动补全，`PgDataSourceConfig` 双数据源
- **单位换算**：所有数据源统一 `convertToBaseUnit` 换算到盘点基础单位
- **零盘点物料**：`not_entered`/`zero_entered` 也参与计算
- **DB 变更**：`V9__diff_redesign.sql` — 重建 `inventory_difference`（8来源+计算），`task_material_summary` 加 `original_qty`/`adjusted_qty`，`transfer_order_item` 加 `material_id`，`loss_report` 加 `completed_at`，统一 collation
- **新增表**：`difference_modify_log` — 每次 `adjusted_qty` 修改日志；`difference_process_log` FK 改为 `ON DELETE SET NULL`
- **阈值**：`sys_config.diff_threshold_rate`，前端可调，自动重算 `is_large`
- **定时任务**：`DiffCalcJob` 每月1号10:00 串行计算，`calcLock` 防并发
- **API**：`GET diff-tasks` / `POST diff-tasks/{id}/calculate` / `POST diff-tasks/batch-calculate` / `PUT differences/{id}/adjust` / `GET|PUT diff-config`
- **前端**：`DifferenceList.vue`（任务级列表+筛选+阈值）+ `DifferenceDetail.vue`（精简6列+详情弹窗+录入明细+行内修改）
- **权限**：`AdminLoginInterceptor` JWT 角色改为实时查 DB，督导只看自己门店
- **分页修复**：`MyBatisPlusConfig` 加 `PaginationInnerInterceptor` 修复 total=0
- **盘点列表**：自动选中最近月份 + 指标卡片 + 统计报表 Tab（督导完成度柱状图）

### 2026-07-28
- **外部查询接口新增**：`InventoryReportController` 新增 2 个 `/api/reports` 端点（Bearer Token 鉴权）
  - `GET /api/reports/loss-report` — 到货报损单：`loss_type='arrival'` + status IN ('registered','confirmed_resend','received','not_received')，返回14字段
  - `GET /api/reports/transfer-order` — 调货单：`status='completed'`，每条 item 拆 2 行（调出_0/调入_1），返回12字段
- **新依赖注入**：`LossReportMapper`、`LossReportLogMapper`、`TransferOrderMapper`、`TransferOrderItemMapper`、`TransferReturnRecordMapper`
- **文档**：[docs/reports-api.md](../docs/reports-api.md)

### 2026-07-27
- **报损分类差异化**：`material_loss_notify_config` 分组→水果蔬菜类/其他类不同流程；新增 `registered`（已登记）状态
- **H5 确认页 Tab 布局**：水果蔬菜2Tab(待审核/已登记)、其他类3Tab(待审核/待发货/已发货)；拒绝必填原因
- **加急报损**：小程序新增"是否加急"；`loss_report.urgent` 字段；server 端 `send-urgent-card` 汇总发红色飞书卡片
- **月度发券**：`LossReportMonthlyVoucherJob` 每月3号10:00；`loss-monthly-voucher.html`
- **小程序6Tab**：全部/待审核/待收货/待确认/已登记/已拒绝
- **性能**：daily-summary SQL 限制近30天；批量查配置替代逐条查库
- **新 API**：`issue-voucher`、`batch-issue-voucher`、`monthly-registered`、`send-urgent-card`、`trigger-monthly-voucher`
- **新文件**：`LossReportMonthlyVoucherJob.java`、`loss-monthly-voucher.html`、`V8__loss_report_urgent.sql`
- **LossReport 实体**：新增 `urgent(Integer)`、`isFruitVeg(Boolean transient)`
- **LossReportServiceImpl**：新增 `resolveFruitVeg()` 批量分类判定；加急后调 server 发卡

### 2026-07-21~23
- **P2 督导拜访模块上线**：总部端台账+创建表单，小程序端确认/异议/任务执行
- **新表**：`supervisor_visit`、`supervisor_visit_action`、`supervisor_store_access`
- **新增 mp 文件**：`SupervisorVisit`、`SupervisorVisitAction`、`SupervisorStoreAccess`（Entity/Mapper）+ `SupervisorVisitService/Impl`（~650行）+ `MpSupervisorVisitController`
- **新增 server 文件**：`SupervisorVisitManageController`（12个端点）
- **权限**：`supervisor_store_access` 表 openId→门店映射，非 `headquarters_admin` 只看自己有权限的门店
- **状态机**：draft→pending_confirm→in_progress→completed（含 objection 异议分支）
- **逾期**：动态判断 tracking_time < 今天，逾期仍可提交，总端保留逾期痕迹
- **确认人**：`confirmManagerOpenid/Name` + `confirmOwnerOpenid/Name`，回填编辑
- **迁移 SQL**：`migration-add-supervisor-visit.sql`、`migration-add-supervisor-store-access.sql`、`migration-add-supervisor-visit-confirm-openid.sql`
- **统一 collation**：admin_permission、store_info 统一为 utf8mb4_0900_ai_ci
- **物料管理换算筛选**：换算类型下拉从"已维护/未维护"→"单位换算/称重换算"，后端 `matchConversionType` 新增 `unit`/`weight`
- **飞书到货 H5 卡片**：每条门店报损新增报损时间（`occurred_date`）；字段换行；未收到货反馈红底

### 2026-07-13
- **报损状态补全**：前端下拉/颜色/标签覆盖全部 8 种（`pending_approval`/`completed`/`received`/`not_received`）
- **调货台账列表**：去掉数量列，状态下拉精简为 3 种（待确认/已完成/已拒绝）
- **侧边栏恢复**：问题处理/物流信息/督导拜访重新显示

### 2026-07-11
- **调货台账 admin 端**：列对齐设计稿（去方向/数量/原因，新增物品/发起人/更新时间），详情抽屉去方向字段
- **发起人显示姓名**：`TransferOrder.createdByName` 瞬态字段，`pageAll()` 批量查 `employee` 表 `openid→name`
- **导出 Excel**：`TransferManageController.export()` Apache POI 生成 xlsx
- **Entity+SQL 注释补全**：`TransferOrder` 全部 22 字段、`TransferOrderItem` 全部 12 字段加中文注释
- **SQL 建表补全**：`transfer_order` 新增 `handoff`/`creator_store_id`，`transfer_order_item` 新增 `base_unit`/`base_qty`/`input_unit`/`input_qty`/`unit_price`
- **报损列表状态补全**：新增 `pending_approval`（待店长审批）
- **性能优化**：`store_info` 添加 `(del_flag, updated_at)` 复合索引，`getAllStores()` 全表扫描 508ms→个位数
- **菜单**：临时隐藏问题处理/物流信息/督导拜访（7/13 已恢复）

### 2026-07-10
- **调货物料搜索优化**：去掉 `.orderByAsc(sortNo)`，IN 查询 1315ms→几十ms
- **全部门店接口冲突修复**：`MpStoreController` 路径 `/api/mp/stores`→`/api/mp/stores-all`，不再和 `MpAuthController` 冲突
- **调货概览增加已拒绝**：`overview()` 新增 `rejected` 计数，排除列表移除 "rejected"
- **概览删除待交接**：`confirmed` 从概览统计中移除（自动确认后该状态不再出现）
- **创建人门店追踪**：`transfer_order` 新增 `creator_store_id`，创建时写入当前门店 ID
- **交接方式**：`transfer_order` 新增 `handoff` 字段（门店自取/第三方物流）
- **调货物料明细增强**：`transfer_order_item` 新增 `base_unit`/`base_qty`/`input_unit`/`input_qty`/`unit_price`
- **单位换算优化**：物料搜索仅返回 unit 类型单位（不含称重），返回 `unitInfos`（含换算提示"1箱=24个"）和 `unitPrices`
- **报损多单位换算**：到货验收支持 `inputUnit`→`baseUnit` 换算（`computeConversionFactor`）
- **容器别名**：`container_config` 新增 `alias` 字段

### 2026-07-09
- **P0 调货管理模块上线**：店间直调 7 状态流转 + HQ 只读台账 + Excel 导出
- **调货 API**：`MpTransferController`（CRUD + 全门店查询 + 物料搜索 + 概览统计）、`TransferManageController`（总部台账 + 导出）
- **调货物料搜索**：按 `inventory_units` 返回可选单位，仅 unit 类型换算（不含称重），自动换算单价（`unitPrices`），返回换算提示（`unitInfos`，1箱=24个）
- **新表**：`transfer_order`（含 `creator_store_id`、`handoff`、`total_qty`）、`transfer_order_item`（含 `base_unit`、`base_qty`、`input_unit`、`input_qty`、`unit_price`）
- **确认自动发货**：调出方确认后自动 `confirmed→pending_ship`，写入发货人/时间
- **任务列表支持全门店**：`MpTaskController.list(?all=true)` 跨名下所有门店查询
- **人员列表全门店**：`MpStaffController.list(?all=true)` + `listAllStoresStaff()`
- **报损/调货全门店统计**：`overviewByStores(openid)` 聚合名下各门店待处理数
- **门店切换同步**：工具页/首页切换门店时调用 `switchStore` 同步后端 session

### 2026-07-11/13
- **过期任务移入历史**：`MpTaskServiceImpl.list()` 过期未提交任务从 `current` 移入 `history`，`DeadlineGuardAspect` 继续拦截过期写操作
- **员工审批待处理事项**：新增 `GET /api/mp/staff/applications/overview?all=true`，`MpStaffService.overviewByStores()`，首页待处理事项增加员工审批卡片
- **调货待处理仅保留关键状态**：`overviewByStores` 和 `overview()` 仅统计 `pending_receive`（确认收货）+ `pending_ship`（确认可调出），移除 `pending_confirm`/`confirmed`
- **报损待处理仅保留关键状态**：`overviewByStores` 仅统计 `pending_approval`（待审核）+ `confirmed_resend`（待补发），移除 `pending`
- **门店搜索接口公开**：`MpWebMvcConfig` 排除 `/api/mp/stores-all`，登录前/切换本地环境时不再 403

### 2026-07-10/11
- **报损字段重命名**：`loss_report` 表 `unit`→`input_unit`、`loss_qty`→`input_qty`，新增 `base_unit`/`base_qty`/`qimai_order_no`/`reject_reason`
- **到货多单位换算**：`MaterialConversionRule` BFS换算链，录入 `input_unit`+`input_qty` → 自动计算 `base_qty`
- **报损审批流程**：店员提交→`pending_approval`→店长通过/拒绝；日常直接 `completed`，到货发飞书
- **飞书每日汇总**：`LossReportDailySummaryJob` 每天9:30汇总前一天pending到货报损，按 `material_loss_notify_config` 分类发卡片
- **飞书 H5 确认页**：`LossReportH5Controller` + `loss-daily-confirm.html`（每日确认页），`LossReportPageController`（server端H5页面）
- **操作日志**：`loss_report_log` 表，所有状态变更记录（submit/approve/confirm/reject/receive/not_receive）
- **新状态**：`completed`（已录入）、`received`（已收货）、`not_received`（未收到货）
- **新表**：`loss_report_log`、`material_loss_notify_config`、`sys_config`
- **角色判定优化**：`determineRole()` 直接查 employee.role，移除 owner_registration 依赖
- **mp 飞书代码清理**：删除 `FeishuMessageService/Impl`、`FeishuSummaryService/Impl`、`LossReportEventListener`等，飞书逻辑全移 server

### 2026-07-13
- **调货概览修复**：`overview()` `pendingConfirm` 字段名修正（原拼写错误 `pendingReceive`），补充 `pending_confirm` 状态计数
- **全门店汇总去重**：新增 `overviewTotal()` 单次 SQL 聚合全门店（`fromStoreId IN(...) OR toStoreId IN(...)`），避免逐门店累加导致双倍计数
- **首页待处理过滤**：`overviewByStores()` 按操作方过滤 — 待确认只统计 fromStore，待收货只统计 toStore
- **报损可见标记**：`material` 新增 `loss_visible`，白名单物料默认展示，搜关键词时查全表
- **物料搜索优化**：搜索框初始折叠，点击展开，关键词下方显示结果，假滚动条提示可滑动
- **报损单位+审批**：`loss_report` 字段重命名(`unit`→`input_unit`)，新增 `base_unit`/`base_qty`/`qimai_order_no`/`reject_reason`，到货多单位换算
- **报损操作日志**：`loss_report_log` 表，所有状态变更记录
- **飞书通知体系**：`sys_config`+`material_loss_notify_config` 表，`LossReportDailySummaryJob` 每日汇总
- **新状态**：`completed`（已录入）、`received`（已收货）、`not_received`（未收到货）、`pending_approval`

### 2026-07-10/11
- **到货弹窗**：标题"到货验收报损"，橙色副标题"仅限企迈平台发货的到货验收报损"
- **H5页面**：桌面端40%宽度居中，移动端100%/430px；状态标签区分已确认(绿)/已拒绝(红)
- **飞书卡片**：applink包一层强制内置浏览器打开，颜色按分类（水果黄/其他蓝），去除明细条
- **企迈单号**：到货必填，卡片和H5均展示
- **P0 门店报损模块上线**：小程序端列表+新建+详情，总部端报损台账+详情抽屉
- **报损 API**：`MpLossReportController`（CRUD+物料搜索+容器列表）、`LossReportManageController`（总部台账+导出）
- **报损管理增强**：总部端筛选新增门店（`mendianmingcheng`）+日期范围（`a-range-picker`），详情抽屉优化（报损信息卡片+计算公式+备注+附件），导出 Excel（Apache POI 5.2.5，`fetch`+Blob 下载）
- **物料换算双区域**：规则换算（unit）和称重换算（weight）分区展示，排序箭头，`weightUnit` 写入 `inventory_units`
- **角色权限**：`LoginUser` 新增 `role` 字段，JWT 携带 role，`determineRole()` 老板>店长>员工
- **扫码盘点**：`MpInventoryController` 扫码识别物料 + 条码补充申请
- **差异处理**：`DifferenceService` 差异生成+阈值过滤+状态机（pending→adjusted/closed/converted）
- **Admin 侧边栏重构**：分组结构（门店运营/协同流转/经营支撑/系统）
- **BizCodeUtil 改为随机数**
- **P0 新表**：`loss_report`、`container_config`、`barcode_supplement`、`inventory_difference` 等 11 张

### 2026-07-06/07
- **自购食材物料采购**：小程序支出登记新增自购食材类型，物料搜索按父级分类过滤，仅存 `self_purchase_material` 表
- **API 新增**：`GET /api/mp/materials/by-category`（物料分类搜索+缓存）、`GET /api/mp/expenses/{id}/material`（物料明细查询）
- **自购物料表变更**：新增 `store_miniapp_no`、`material_id`、`purchase_date`、`handler_name`、`voucher_url`、`remark`，删除 `received_qty`
- **Admin 侧边栏固定**：`App.vue` 中 `a-layout-sider` 设为 `position: fixed`，内容滚动不受影响

## 🗂️ 记忆索引

| 文件 | 读我当需要... |
|------|--------------|
| [decisions.md](decisions.md) | 🧠 理解为什么这样设计（快照隔离、多模块、多单位换算…） |
| [patterns.md](patterns.md) | 📐 写代码时查命名约定、分层规范、JSON 格式 |
| [feedback.md](feedback.md) | 📝 了解用户纠正过什么、有哪些特殊偏好 |
| [contacts.md](contacts.md) | 📇 找模块关键类、外部系统接口、数据库连接信息 |
| [testing.md](testing.md) | 🧪 写测试时查命名规范、怎么 mock、边界值怎么测 |
| `YYYY-MM-DD.md` | 📅 回顾近期讨论和临时结论（30 天自动衰减） |

## 🧭 模块速查

```
common (31)  →  R<T> / 异常 / 缓存 / 日志AOP / ThreadLocal上下文
template (25) → 物料CRUD + 盘点规则 + 模板分区
task (22)    → 月盘任务 + 门店 + 快照同步 + 事件监听
expense (23) → 支出类型CRUD + 支出记录 + 自购成本 + 统计看板
people (15)  → 员工 + 老板绑定 + 门店联系人
server (21)  → 飞书登录 / JWT / 多角色权限 / 启动器 :4026
mp (81)      → 小程序全部API（13个控制器）
mp-server (6)→ 小程序启动器 :30261
```

依赖链：`mp-server → mp → task → template → common`

## ⚙️ 关键设计模式

- **快照隔离**：任务创建时复制模板数据，模板变更不影响已有任务
- **事件驱动同步**：模板/物料变更 → Spring Event → 异步重建未开始任务快照
- **多单位换算**：`material_conversion_rule` 表支持 unit（箱→瓶）和 weight（kg→g）
- **逻辑删除 + 乐观锁**：所有核心表 `del_flag` + `version`
- **ThreadLocal 上下文**：`AdminContextHolder`（总部）/ `UserContextHolder`（小程序）

## 🔗 相关文档

- 项目上下文：[CLAUDE.md](../CLAUDE.md)
- 数据库：[schema.sql](../../database/schema.sql) / [CHANGELOG.md](../../database/CHANGELOG.md)
- 总部前端：[admin/](../../admin/)
- 小程序前端：[miniapp/](../../miniapp/)

## 2026-07-14/15 更新

### 支出类型排序
- `expense_type` 表加 `sort_no INT DEFAULT NULL`，越小越靠前，NULL 排最后
- 小程序端 `MpExpenseServiceImpl.listTypes()`：`ORDER BY sort_no IS NULL, sort_no ASC, id ASC`
- 总部端 `ExpenseTypeServiceImpl.list()`：同上
- 新建类型自动 `sortNo = MAX(sort_no) + 1`
- SQL：`database/migrate-expense-type-add-sort.sql`

## 2026-07-17 更新

### 物料表索引优化
- material 表加 `(del_flag, material_name)` 联合索引
- SQL：`database/migration-material-add-name-index.sql`
- 慢查询 `ORDER BY material_name LIMIT 100` 从 ~670ms 降到 <50ms

### 问题处理模块上线
- mp 模块新增 `MpIssueController` + `IssueCallbackController`（~16个端点）
- server 模块新增 `IssueManageController`（总部只读台账）
- 状态直接使用 task_platform 原始值

### 调货还货/还钱模块
- 新表 `transfer_return_record`，`TransferReturnService/Impl`，`MpTransferReturnController`
- 归还状态用 `handoff='returned'`(不改 `status`)
- `doReturn`: 还货校验数量上限,还钱金额不限
- `confirmReturn`: 仅调出门店可确认
- 详情接口返回 `returnRecords`

### 问题处理日志
- 点"上报新问题"即记录"上报问题"(`POST /issue/report-click`)
- Server 端补 xiangmu 配置+图片 URL 前缀处理

### 支出统计看板优化
- `ExpenseController.dashboard()` 支持 `startDate`/`endDate` 参数
- `ExpenseDashboardResp.StoreRanking` 新增 `count`/`pctAmount`/`pctCount`
- 新增 `buildMonthlyTrend()` 按月汇总
- `buildStoreRanking` 去 `.limit(8)`，返回全部门店

### Issue 实体精简 + 回复回写（2026-07-22）
- **删除 Issue 实体字段**（只写不读）：`contactName`、`contactPhone`、`images`、`processResult`、`acceptedBy`、`acceptedAt`、`submittedBy`、`processedAt`、`resolvedAt`
- **字段映射**：`replyText` 通过 `@TableField("acceptance_remark")` 映射到列 `acceptance_remark`（列名不变，无 SQL 迁移，注释改为"解决原因"）
- `IssueCreateReq` 同步精简，去 `images`/`contactName`/`contactPhone`
- `replyExternal()` 成功后将 `replyText` 回写到 `issue.replyText`（即 `acceptance_remark` 列）
- 同步修复 `P0ModuleTests.java`
