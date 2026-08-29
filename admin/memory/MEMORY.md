# 象掌柜总部管理后台 — 前端项目总结

## 项目概述

- **应用名称**：象掌柜总部（xzcpc-admin）
- **用途**：总部管理后台，供总部管理员配置盘点模板/物料规则，下发月盘任务，查看盘点结果，管理支出、人员及门店绑定
- **技术栈**：Vue 3 + Ant Design Vue 4 + Vite + TypeScript + Axios
- **构建方式**：Vite 构建产物输出至 `src/main/resources/static/`，由 Spring Boot 单体 fat jar 提供服务
- **开发端口**：Vite dev server → `30260`，API 代理 → `localhost:4026`
- **UI 库**：Ant Design Vue 4.x（`ant-design-vue` v4.2.3）
- **路由模式**：Hash 模式（`createWebHashHistory`）

---

## 目录结构

```
admin/
├── index.html                    # HTML 入口，标题 "象掌柜总部"
├── package.json                  # 依赖与脚本
├── vite.config.ts                # Vite 配置（端口 30260，代理 /api → localhost:4026）
└── src/
    ├── main.ts                   # Vue 应用入口，注册 Ant Design Vue + Router + 全局样式
    ├── App.vue                   # 根组件：可折叠侧边栏 + <router-view /> 内容区
    ├── styles/global.css         # 全局样式：CSS 变量（--primary: #0D7A3D）、侧边栏、表格、滚动条
    ├── utils/auth.ts             # 认证工具：token/用户 localStorage 管理、角色判断、开发模拟登录
    ├── router/index.ts           # 路由配置 + 飞书 OAuth 导航守卫 + 角色权限守卫
    ├── api/
    │   ├── index.ts              # Axios 实例：baseURL /api，token 拦截器，401 处理
    │   ├── task.ts               # 盘点任务 CRUD API
    │   ├── template.ts           # 盘点模板 + 分区 + 物料绑定 API
    │   ├── material.ts           # 物料分类 API
    │   ├── materialRule.ts       # 物料盘点规则 API（单位、换算、单价）
    │   ├── expense.ts            # 支出记录 + 支出类型 + 看板 API
    │   ├── people.ts             # 员工列表 + 详情 + 看板 API
    │   ├── permission.ts         # 权限管理 API
    │   ├── store.ts              # 门店列表 API
    │   └── ownerRegistration.ts  # 老板/店长注册绑定 API
    ├── components/
    │   ├── PageModuleTabs.vue    # 任务/模板/物料模块标签导航
    │   ├── ExpenseModuleTabs.vue # 支出模块标签导航
    │   └── PeopleModuleTabs.vue  # 人员模块标签导航
    └── views/
        ├── NoPermission.vue      # 无权限占位页
        ├── task/
        │   ├── TaskList.vue      # 盘点任务列表（筛选、分页、创建、删除）
        │   ├── TaskCreate.vue    # 创建月盘任务（选择门店/模板/月份）
        │   └── TaskResult.vue    # 盘点结果详情（按分区/物料查看汇总）
        ├── template/
        │   └── TemplateList.vue  # 模板管理（CRUD、分区/物料拖拽排序、启用/停用）
        ├── material/
        │   └── MaterialManagement.vue  # 物料盘点规则管理（单位换算、单价编辑）
        ├── expense/
        │   ├── ExpenseList.vue         # 支出明细列表
        │   ├── ExpenseDashboard.vue    # 支出统计看板
        │   └── ExpenseTypeManagement.vue  # 支出类型管理
        ├── people/
        │   ├── EmployeeList.vue        # 员工列表
        │   ├── EmployeeDashboard.vue   # 员工统计看板
        │   ├── EmployeeDetail.vue      # 员工详情（基本信息、权限、时间线）
        │   └── OwnerRegistrationList.vue  # 门店绑定记录（手动绑定老板/店长）
        └── settings/
            ├── SystemSettings.vue      # 系统设置（权限管理 + 飞书用户 + 角色授权）
            └── PlaceholderPage.vue     # 功能开发中占位页
```

---

## 路由与权限体系

### 路由表

| 路径 | 视图 | 角色要求 |
|---|---|---|
| `/` | 重定向至 `defaultRoute()` | — |
| `/no-permission` | NoPermission.vue | 公开 |
| `/tasks` | task/TaskList.vue | headquarters\_admin, operation\_admin |
| `/tasks/create` | task/TaskCreate.vue | headquarters\_admin, operation\_admin |
| `/tasks/:id/result` | task/TaskResult.vue | headquarters\_admin, operation\_admin |
| `/templates` | template/TemplateList.vue | headquarters\_admin, operation\_admin |
| `/materials` | material/MaterialManagement.vue | headquarters\_admin, operation\_admin |
| `/expense` | expense/ExpenseList.vue | headquarters\_admin, finance\_admin |
| `/expense/dashboard` | expense/ExpenseDashboard.vue | headquarters\_admin, finance\_admin |
| `/expense/types` | expense/ExpenseTypeManagement.vue | headquarters\_admin, finance\_admin |
| `/people` | people/EmployeeList.vue | headquarters\_admin, hr\_admin |
| `/people/dashboard` | people/EmployeeDashboard.vue | headquarters\_admin, hr\_admin |
| `/people/bindings` | people/OwnerRegistrationList.vue | headquarters\_admin, hr\_admin |
| `/people/:id` | people/EmployeeDetail.vue | headquarters\_admin, hr\_admin |
| `/settings` | settings/SystemSettings.vue | headquarters\_admin |

### 四种管理员角色

| 角色标识 | 名称 | 默认首页 |
|---|---|---|
| `headquarters_admin` | 总部管理员 | `/tasks` |
| `operation_admin` | 运营负责人 | `/tasks` |
| `finance_admin` | 财务负责人 | `/expense` |
| `hr_admin` | 人事负责人 | `/people` |

### 认证流程

1. **飞书 OAuth**：生产环境通过飞书授权码换取 JWT token
2. **开发回退**：本地/内网环境自动调用 `/api/auth/dev/login` 模拟管理员登录
3. **Token 存储**：`localStorage` 键名 `admin_token`、`admin_user`
4. **全局导航守卫**：
   - 处理飞书 `?code=xxx` 参数，调用 `/api/auth/feishu/login` 换取 token
   - 检查 `meta.roles` 权限，不匹配则重定向至 `/no-permission`
   - 无 token 时触发飞书登录重定向

---

## 功能模块详解

### 1. 盘点任务模块（`/tasks`、`/tasks/create`、`/tasks/:id/result`）

- **TaskList.vue**：任务列表，支持门店多选、任务状态筛选、月份筛选、分页展示，表格横向滚动，操作列固定右侧。支持以下操作：
  - **查看**：跳转结果页，点击任务名称也可进入
  - **延期**（未开始/进行中）：点击弹出 Popover 日期选择器（精确到秒），确认后更新截止时间
  - **编辑**（已提交）：跳转 TaskResult 编辑模式，可修改物料总量
  - **删除**（未开始）：二次确认后硬删除该任务
- **TaskCreate.vue**：创建月盘任务，选择门店、模板、月份，右侧预览分区和物料。提交时调用 `POST /api/tasks`。
- **TaskResult.vue**：门店盘点详情，仅展示物料汇总视图（已移除分区视图）。
  - 已提交任务支持"编辑模式"：总量可修改、保存后自动清除多单位明细并重算汇总。
  - 物料汇总支持物料名称搜索。
  - 编辑入口：列表页点击"编辑"按钮，或详情页直接点击"编辑"按钮。

### 2. 盘点模板模块（`/templates`）

- **TemplateList.vue**：核心管理视图（大型 SPA 风格），左侧模板列表（支持名称搜索、状态筛选），右侧模板编辑器。功能包括：
  - 模板 CRUD（名称、启用/停用/草稿状态）
  - 分区管理：添加/编辑/删除分区，拖拽排序
  - 物料管理：向分区添加/移除物料，分区内物料拖拽排序
  - 物料不可在同一分区重复添加，同一物料可配置在多个分区
- **TemplateDetail.vue**：已废弃，全部功能整合至 TemplateList.vue

### 3. 物料规则模块（`/materials`）

- **MaterialManagement.vue**：管理物料的盘点规则，包括：
  - 按父分类/子分类筛选物料
  - 设置盘点单位（`inventory_unit`）、换算关系、参考单价
  - 获取基础单位列表（`GET /material-rules/base-units`）

### 4. 支出管理模块（`/expense`、`/expense/dashboard`、`/expense/types`）

- **ExpenseList.vue**：支出明细列表，双 Tab 切换「普通支出」和「自购成本」。
  - 普通支出：查询 `expense_record` 表，按门店多选、支出类型、日期范围、经手人筛选，分页展示，支持查看凭证。列：门店名称、支出项目/类型（firstTypeName + typeName 标签）、说明（remark）、金额、产生日期、经手人、凭证。
  - 自购成本：查询 `self_purchase_material` 表（API: `/api/self-purchase-materials`），按门店多选、日期范围、经手人筛选。列：门店名称、支出项目/类型（materialName + "自购成本"标签）、重量（purchaseQty+unit）、单价（unitPrice）、总价（totalAmount）、说明（remark）、产生日期（purchaseDate）、经手人、凭证。
  - `bodyCell` 使用 `v-if/v-else-if/v-else` 链式互斥渲染，末尾 `v-else` 兜底 `record[column.dataIndex]`。
  - 表格卡片加 `:key="activeTab"` 强制 Tab 切换时重建组件，避免列定义与旧数据冲突。
- **ExpenseDashboard.vue**：支出统计看板，含指标卡、条状图、环形图、趋势图。
- **ExpenseTypeManagement.vue**：支出分类管理（`expense_item` 已弃用，改为直接管理 `expense_type` 表）。
  - 数据源：`GET /api/expense-types`，CRUD：`POST/PUT/DELETE /api/expense-types`。
  - 列：项目名称（name）、所属分类（firstTypeName 蓝色标签）、适用示例（description）、状态、最近更新、操作。
  - 新增/编辑表单：「所属分类」使用 `a-auto-complete` 下拉建议（从已有数据提取去重 firstTypeName），支持自由输入新分类。
- **新增表 `self_purchase_material`**：自购物料采购表，字段含 store_id、store_name、material_name、unit、purchase_qty、unit_price、total_amount、purchase_month、purchase_date、handler_name、voucher_url、remark、parent_category、category 等。后端实体/Mapper/Service/Controller 位于 expense 模块。

### 5. 人员管理模块（`/people`、`/people/dashboard`、`/people/bindings`、`/people/:id`）

- **EmployeeList.vue**：员工列表，支持按姓名/手机号/门店筛选，分页展示。
- **EmployeeDashboard.vue**：员工统计看板，含指标卡、角色分布图、入职趋势图。
- **EmployeeDetail.vue**：员工详情页，展示基本信息、权限角色、操作时间线、历史记录。
- **OwnerRegistrationList.vue**：门店绑定记录管理，支持手动绑定老板/店长、编辑绑定信息（关联 `ownerRegistration.ts` API）。

### 6. 系统设置模块（`/settings`）

- **SystemSettings.vue**：权限管理页面，包含权限总览、飞书用户列表、角色授权（总部管理员专属）。

---

## 全局组件

| 组件 | 用途 |
|---|---|
| `PageModuleTabs.vue` | 任务模块标签导航（盘点任务 / 盘点模板 / 物料管理），含全局任务搜索框 |
| `ExpenseModuleTabs.vue` | 支出模块标签导航（支出明细 / 支出统计 / 支出类型） |
| `PeopleModuleTabs.vue` | 人员模块标签导航（员工列表 / 员工统计 / 门店绑定记录） |

三个标签导航组件统一风格：浅绿色 primary 色 `#0D7A3D`、激活下划线、右侧搜索框 + 头像。

---

## 主题与样式

- **主色调**：`#0D7A3D`（绿色），通过 CSS 变量 `--primary` 定义
- **UI 库**：Ant Design Vue 4，含大量自定义覆盖样式
- **CSS 变量**：定义在 `styles/global.css`，涵盖侧边栏宽度/背景、表格斑马纹、滚动条样式
- **侧边栏**：可折叠，深色背景，四项菜单根据角色条件渲染

---

## API 层设计

- **统一 Axios 实例**（`api/index.ts`）：baseURL `/api`，自动附加 `Authorization: Bearer <token>`
- **响应拦截器**：业务 code 非 200 则 reject；401 触发飞书登录重定向或开发模式自动重登
- **API 文件组织**：按业务模块拆分（task / template / material / materialRule / expense / people / permission / store / ownerRegistration）
- **自购成本 API**：`GET /api/self-purchase-materials`（分页，参数 storeIds/startDate/endDate/handlerName），返回 self_purchase_material 表数据，用于支出明细列表的自购成本 Tab

---

## 开发工作流

```bash
# 前端开发（在 admin/ 目录下）
npm run dev          # 启动 Vite dev server → http://localhost:30260
npm run build        # 生产构建 → dist/

# 后端开发
cd ../server && mvn spring-boot:run   # 启动 Spring Boot → localhost:4026
```

- 前端 dev server 将 `/api/*` 代理到后端 `localhost:4026`
- 开发环境自动模拟登录，无需飞书 OAuth 流程
- 部分视图（支出列表、员工列表、看板）包含硬编码 fallback 数据，确保后端未就绪时也能预览 UI

---

## 注意事项

1. **TemplateDetail.vue 已废弃**，所有模板管理功能均在 TemplateList.vue 中实现
2. **四个角色权限**贯穿路由守卫和侧边栏菜单可见性，修改时需同步更新
3. **开发/生产认证流程不同**：开发环境走 `/api/auth/dev/login`，生产环境走飞书 OAuth
4. **Vite 代理端口**：前端 30260 → 后端 4026，与 Spring Boot 默认 8080 不同（生产 fat jar 用 8080）
5. **Hash 路由**：使用 `createWebHashHistory`，所有 URL 带 `#/` 前缀
6. **TaskResult.vue 已移除分区视图**，所有盘点结果仅展示物料汇总视图
7. **延期功能**：TaskList.vue 中未开始/进行中任务可通过 Popover 日期选择器修改截止时间（精确到秒），已提交任务不可延期但可编辑物料数量
8. **多门店筛选**：TaskList.vue 和 ExpenseList.vue 门店均改为多选，后端 `storeId` 参数兼容逗号分隔
9. **物料汇总编辑**：已提交任务在 TaskResult 编辑模式下可修改物料总量，保存后多单位清除并重算 `task_material_summary`
10. **后端新增接口**：`PUT /api/tasks/{id}`（更新基本信息）、`PUT /api/tasks/{id}/materials`（批量更新物料）、`PUT /api/tasks/{id}/materials/{materialId}/total`（汇总编辑）、`DELETE /api/tasks/{id}/materials/{materialId}`（删除物料）
11. **支出明细双 Tab**：ExpenseList.vue 新增「普通支出」和「自购成本」两个 Tab。自购成本查询 `self_purchase_material` 表（`GET /api/self-purchase-materials`），列拆分为重量、单价、总价、说明。Tab 切换加 `:key` 强制重建表格组件。
12. **bodyCell 插槽规范**：所有页面的 `bodyCell` 模板必须使用 `v-if/v-else-if/v-else` 互斥链，末尾 `v-else` 兜底 `record[column.dataIndex]`，否则未匹配列会留空（Ant Design Vue 4 不会回退到 dataIndex 默认渲染）。
13. **expense_item 已弃用**：ExpenseTypeManagement.vue 改为直接管理 `expense_type` 表，CRUD 端点 `/api/expense-types`（POST/PUT/DELETE），新增表单中「所属分类」使用 `a-auto-complete` 下拉选择+自由输入。
14. **self_purchase_material 表**：自购物料采购表，位于 expense 模块。实体：SelfPurchaseMaterial.java（`@TableLogic` delFlag），分页接口 `GET /api/self-purchase-materials`。迁移脚本 `migrate_expense_to_self_purchase.sql` 将 expense_record 中 `first_type_name='自购成本'` 的记录迁入。
15. **报表接口更新**：`/api/reports/self-purchase-cost` 数据源从 ExpenseRecord 切到 SelfPurchaseMaterial，月份筛选改用 `purchase_month` 直接 IN 匹配，输出字段新增 purchaseDate、totalAmount、handlerName、remark、voucherUrl 等。
16. **侧边栏固定（2026-07-07）**：`App.vue` 中 `a-layout-sider` 设为 `position: fixed`，右侧 `a-layout` 通过 `marginLeft` 自适应，内容滚动时侧边栏保持固定。
17. **报损管理模块（2026-07-08）**：
    - 新增 `views/loss/LossList.vue` 报损台账页：门店/类型/状态/日期筛选 + 表格 + 详情抽屉
    - 详情抽屉：状态标签 + 物料名称+数量 + 业务编码 + 报损信息卡片 + 计算公式 + 备注 + 附件
    - 筛选条件：门店下拉（`show-search` + `filter-option` 本地搜索）、报损类型、状态、日期范围（`a-range-picker`）
    - 门店数据源 `StoreInfo` 字段为 `mendianmingcheng`/`id`，需用 `field-names` 映射
    - 导出 Excel：`GET /api/admin/loss-report/export`，Apache POI 生成 xlsx，前端 `fetch` + Blob 下载
    - 报表服务 `LossReportManageController`、`LossReportService`、`LossReportMapper`
18. **物料管理优化（2026-07-08）**：换算关系分"规则换算"（unit）和"称重换算"（weight）双区域，各支持排序箭头、称重换算 `weightUnit` 写入 `inventory_units`
19. **侧边栏重构**：分组结构（门店运营/协同流转/经营支撑/系统），新增问题处理、调货台账、物流信息、督导拜访占位入口
20. **调货台账模块（2026-07-11）**：
    - `views/transfer/TransferList.vue`：表格列对齐设计稿（调货编号/调出门店/调入门店/物品/状态/发起人/更新时间/操作），去方向、去数量
    - 筛选条件：调出门店、调入门店、状态（3种：待确认/已完成/已拒绝）、关键词、日期范围
    - 详情抽屉：台账说明 + 调货信息（去方向）+ 物料明细 + 流程记录
    - 发起人显示员工姓名（`createdByName`），非 openid
    - 导出 Excel：`GET /api/admin/transfer/export`，`fetch` + Blob 下载
    - API 模块：`api/transfer.ts`（`getTransferOrders` / `getTransferOrderDetail` / `exportTransferOrders`）
    - 路由器已配置：`/transfer` — `headquarters_admin + operation_admin`
21. **报损管理状态补全（2026-07-13）**：
    - 状态下拉从 4 种扩展为 8 种：待店长审批/待厂家确认/已确认补发/已收到货/未收到货/已录入/已拒绝/已关闭
    - `statusColor` + `statusLabel` 函数补全所有状态映射
22. **调货台账列表优化（2026-07-13）**：去掉数量列，状态下拉精简为待确认/已完成/已拒绝 3 种
23. **侧边栏菜单（2026-07-13）**：问题处理/物流信息/督导拜访解除隐藏，正常显示
24. **总部端多项优化（2026-07-17）**：
    - **问题台账**:去问题编号/上报人/象目单号列，门店+操作列固定；紧急程度半透明圆角标签；搜索区4列布局；详情两卡片合并
    - **支出统计**:日期范围选择器替换分段控件；门店对比金额/次数双维度+排序；饼图动态真实数据；月度趋势接后端
    - **调货台账**:流程去"已发货/已交接"；物料明细展示金额+总金额
    - **报损管理**:附件图片可点击放大；修复到货报损数量显示
    - **员工统计**:门店分布可滚动
    - **后端**:ExpenseDashboard 支持日期范围+次数维度+月度趋势；material 加索引
25. **物料管理换算筛选（2026-07-21）**：换算类型下拉从"已维护/未维护"→"单位换算/称重换算"，后端 `matchConversionType` 新增 `unit`/`weight`
26. **督导拜访 P2（2026-07-21~23）**：
    - 新增 `views/supervisor/SupervisorVisitList.vue`（台账列表+详情抽屉+审核）+ `SupervisorVisitForm.vue`（创建/编辑表单）
    - 新增 `api/supervisor.ts`（12个端点）
    - 路由 `/supervisor`、`/supervisor/create`、`/supervisor/:id/edit` — 角色 `headquarters_admin, operation_admin`
    - 侧边栏经营支撑组新增督导拜访入口
    - 概览统计卡片 + 进度条 + 按状态区分操作按钮 + 确认人姓名+角色标签
    - 门店/督导下拉按 `supervisor_store_access` 权限过滤
    - 抽屉底部固定 + 审核/退回补充/修改
26. **飞书到货 H5 卡片（2026-07-21）**：每条报损新增报损时间；字段换行排列（企迈单号/原因/备注/报损时间）；未收到货反馈红底
27. **报损分类差异化（2026-07-27）**：按 `material_loss_notify_config` 分水果蔬菜类和其他类两种流程；新增 `registered`（已登记）状态；H5 确认页改为 Tab 布局（水果蔬菜2Tab/其他类3Tab）；拒绝操作必须填原因；其他类 registered 状态增加"确认发货"按钮
28. **加急报损（2026-07-27）**：小程序到货验收表单新增"是否加急"胶囊；DB 新增 `loss_report.urgent` 字段；提交后 server 端发红色飞书卡片到群
29. **月度发券（2026-07-27）**：新增 `LossReportMonthlyVoucherJob`（每月3号10:00）；新增 `loss-monthly-voucher.html`；统计上月1号~今天的 registered 报损
30. **LossList.vue（2026-07-27）**：新增 registered 状态筛选/颜色(blue)/标签
31. **小程序报损页（2026-07-27）**：6 Tab（全部/待审核/待收货/待确认/已登记/已拒绝）；详情页展示拒绝原因；分类差异化状态文案
32. **性能优化（2026-07-27）**：daily-summary SQL 限制近30天；批量查配置表替代逐条查库
33. **盘点差异处理（2026-07-28~08-01）**：
    - **计算引擎重构**：时间窗口从 `task_month` 改为 `submitted_at`（上次提交时间→本次提交时间）
    - **PG 数据源**：`stat_month` → `stat_date`，按天范围查询；NULL unit 自动补全
    - **8 数据源**：上月剩余 + 采购(PG) + 订货(PG) + 调货净值 + 还货净值 - 报损 + 自购 - 消耗(PG)
    - **单位换算**：PG/调货/还货/自购统一调用 `convertToBaseUnit` 换算到盘点基础单位
    - **零盘点物料**：`not_entered`/`zero_entered` 的物料也参与差异计算（`actual_qty=0`）
    - **DB 变更**：`V9__diff_redesign.sql` 重建 `inventory_difference` 表（含 8 来源字段+计算结果），`task_material_summary` 加 `original_qty`/`adjusted_qty`，`transfer_order_item` 加 `material_id`，`loss_report` 加 `completed_at`，统一 collation 为 `utf8mb4_0900_ai_ci`
    - **修改日志**：`difference_modify_log` 记录每次 `adjusted_qty` 修改（初始值/旧值/新值/操作人）
    - **阈值配置**：`sys_config` 加 `diff_threshold_rate`，前端可动态调整，自动重算 `is_large`
    - **定时任务**：`DiffCalcJob` 每月1号10:00自动串行计算上月差异，`calcLock` 防并发
    - **批量接口**：`POST /api/admin/inventory/diff-tasks/batch-calculate` 一键计算所有未算任务
    - **双数据源**：`server` 和 `mp-server` 均配置 `PgDataSourceConfig`（MySQL `@Primary` + PG `pgJdbcTemplate`）
    - **前端**：`DifferenceList.vue` 任务级列表（门店/督导/盘点月份/已盘物料数/有差异物料数）+ 阈值设置；`DifferenceDetail.vue` 差异明细表（精简6列+详情弹窗+录入明细+行内修改）；`PageModuleTabs` 加"盘点处理"Tab
    - **权限**：`AdminLoginInterceptor` JWT 角色改为实时查 DB，督导只能查看自己门店
    - **分页修复**：`MyBatisPlusConfig` 加 `PaginationInnerInterceptor` 修复分页 total=0
    - **盘点列表页**：自动选中最近月份 + 指标卡片（未开始/进行中/已提交）+ 统计报表 Tab（督导完成度柱状图）
    - **人员管理**：`OwnerRegistrationList.vue` 门店列固定 + 删除重复列
34. **问题台账 source 字段（2026-08-07）**：
    - **回调接口**：`POST /api/mp/public/issue/callback` 新增 `source` 字段，标识问题来源渠道
    - **枚举值**：`wxapp`/`MINI_PROGRAM`/`WXAPP`(小程序)、`FEISHU_GROUP`(飞书群H5)、`HQ`(总部上报)、空(旧数据)
    - **存储策略**：数据库存原始值不做转换，总部端展示时映射中文标签
    - **DB 变更**：`V16__issue_add_source.sql` — `issue` 表新增 `source VARCHAR(30)` 列 + 索引
    - **后端**：`IssueCallbackController` 解析 `source` → `IssueServiceImpl.syncByCallback` 写入；`IssueManageController.list` + `pageAll` 新增 `source` 筛选
    - **前端**：`IssueList.vue` 列表新增"来源"列（绿色小程序/蓝色飞书群/橙色总部）、筛选区新增来源下拉、详情新增来源行
    - **API 文档**：`docs/task_platform-callback-api.md` 补充 `source` 字段说明及映射关系
35. **后端改动（2026-08-11，无前端代码变更）**：
    - 牛油果泥单位换算修复：仅 unit="件" 时 ×24→"包"，"包"不变
    - 自动收货：`LossReportAutoReceiveJob`，补发后 4 天门店未收货自动确认
    - 详见 [dateMemory/2026-08-11.md](dateMemory/2026-08-11.md)
