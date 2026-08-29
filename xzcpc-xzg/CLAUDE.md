# 盘点工具 1.0 — 项目上下文

## 项目基本信息

- **项目名称**：盘点工具 1.0（xzcpc-xzg）
- **用途**：总部配置物料和标准分区模板，下发月盘任务，店长在小程序端按分区录入物料数量，系统自动跨分区汇总，提交后更新门店默认分区物料清单。
- **技术栈**：Java 17 + Spring Boot 3.2 + MyBatis-Plus 3.5 / Vue 3 + Ant Design Vue 4 + Vite / MySQL 8.0
- **数据库**：库名 store_Inventory，119.45.162.160:3306，store_inventory/Xzcpc@2026
- **打包方式**：前后端一个项目，Vite 构建产物放 src/main/resources/static/，Maven 单体 fat jar
  - Vite `outDir` 配置在 `admin/vite.config.ts`，指向 `../server/src/main/resources/static/`
  - Spring Boot Maven Plugin 将 static/ 一同打进 fat jar，`java -jar` 启动后直接提供前端页面
- **开发代理**：Vite dev server → localhost:8080 代理 /api/*
- **端口**：总部端 8080，小程序端 8081
- **部署方式**：
  ```bash
  # 小程序端服务器
  cd mp-server && mvn clean package -DskipTests
  java -jar target/mp-server-1.0.0.jar   # → localhost:8081

  # 总部端服务器
  cd server && mvn clean package -DskipTests
  java -jar target/server-1.0.0.jar      # → localhost:8080
  ```
- **MySQL 启动**：非系统服务，需手动 `mysqld --standalone --console`
- **建表 SQL**：`database/schema.sql`
- **PRD**：`PRD/盘点工具 1.0 PRD.docx`
- **设计稿**：`design/后端页面/`（总部端）、`design/小程序端/`（门店端）

## 编码规范

### 命名规范
- 类名：PascalCase 大驼峰，如 `MaterialController`、`TemplateService`
- 方法/变量：camelCase 小驼峰，如 `findByName`、`materialList`
- 包名：全小写，如 `com.xzcpc.material.controller`
- 数据库表/字段：snake_case，如 `material_id`、`template_zone`
- Vue 组件：PascalCase，如 `MaterialList.vue`、`TemplateDetail.vue`

### 格式要求
- 缩进：Java 4 空格，Vue/JS 2 空格
- 编码：UTF-8
- 行尾：LF

### 代码风格
- Controller 只做参数解析和结果返回，业务逻辑放 Service
- 统一返回体使用 `com.xzcpc.common.response.R`
- 数据库实体使用 MyBatis-Plus 注解
- API 路径前缀 `/api/`，资源名用复数

## 项目结构

```
inventory-tool/xzcpc-xzg/
├── pom.xml                                # 父 POM
├── common/                                # 公共模块（配置、异常、统一返回 R）
├── material/                              # 物料模块
│   └── controller / service / mapper / entity
├── template/                              # 模板模块（依赖 material）
│   └── controller / service / mapper / entity (Template, TemplateZone, TemplateZoneMaterial)
├── task/                                  # 任务模块（依赖 template）
│   └── controller / service / mapper / entity (Task, TaskZone, TaskZoneMaterial, TaskMaterialSummary)
├── server/                                # 启动模块（聚合所有模块 + application.yml）
│   └── InventoryApplication.java
└── admin/                                 # Vue 前端（Ant Design Vue）
    └── src/ api/ views/ router/ components/
```

**模块依赖链**：server → task → template → material → common

## 盘点差异处理（新增模块，2026-08）

### 架构概览
- **后端**：`DifferenceCalcService` / `DifferenceCalcServiceImpl` — 核心计算引擎
- **前端**：`DifferenceList.vue`（任务级列表） + `DifferenceDetail.vue`（差异明细+修改）
- **数据库**：`inventory_difference`（差异项） + `difference_modify_log`（修改日志） + `difference_process_log`（操作日志）
- **PG 数据源**：`PgDataSourceConfig` 配置双数据源，`pgJdbcTemplate` 查企迈 PG 库
- **定时任务**：`DiffCalcJob` 每月1号10:00自动串行计算

### 核心计算
```
理论剩余 = 上月剩余 + 采购(PG) + 订货(PG) + 调货净值 + 还货净值 - 报损 + 自购 - 消耗(PG)
差异 = 本月盘点数(adjusted_qty) - 理论剩余
差异率 = |差异| / |理论剩余|
```
- 时间窗口：上次 `submitted_at` → 本次 `submitted_at`（非按月）
- PG 用 `stat_date` 按天，NULL unit 自动补全
- 所有数据源统一换算到盘点基础单位

### 关键 API
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/inventory/diff-tasks` | 差异任务列表（门店/督导筛选） |
| GET | `/api/admin/inventory/diff-tasks/{taskId}` | 任务差异明细 |
| POST | `/api/admin/inventory/diff-tasks/{taskId}/calculate` | 触发单个任务计算 |
| POST | `/api/admin/inventory/diff-tasks/batch-calculate` | 批量计算所有未算任务 |
| PUT | `/api/admin/inventory/differences/{id}/adjust` | 修改 adjusted_qty 并重算 |
| GET | `/api/admin/inventory/diff-config` | 获取差异阈值 |
| PUT | `/api/admin/inventory/diff-config` | 更新阈值 |

## 常用开发命令

```bash
# 启动 MySQL
mysqld --standalone --console

# 后端启动
cd server && mvn spring-boot:run

# 前端开发
cd admin && npm run dev

# 整体打包（两步：先前端后后端）
cd admin && npm run build          # 步骤1：前端构建 → server/src/main/resources/static/
cd .. && mvn clean package -DskipTests  # 步骤2：Maven 打包，含前端静态文件 → server/target/server-1.0.0.jar
# 若 Maven Wrapper 损坏，可用：
# cd .. && C:\Users\xiemg\apache-maven-3.9.6\bin\mvn clean package -DskipTests

# 仅后端打包（前端未改动时跳过 npm build）
mvn clean package -DskipTests

# 运行测试
mvn test

# 数据库初始化
mysql -uroot -pxzcpc2026 < database/schema.sql
```

## 重要业务规则

### 物料
- 名称不能为空、不能重复、有长度限制
- 新增字段 `inventory_unit`（盘点单位），与 `unit`（最小规格单位）区分
- 被模板引用过的物料不可删除
- 启停用 Switch 控制状态

### 模板
- 模板名称不能重复、不能为空、长度限制
- 分区名称同一模板内唯一，不能为空，不可超长
- sort_no 可空，空值排在已有序号后面
- 同一分区内不可重复添加同一物料，同一物料可配置在多个分区
- 分区可以删除，分区物料可拖拽排序

### 任务
- 创建任务时从模板生成快照，后续模板变更不影响已有任务
- 任务状态：not_started → in_progress → submitted
- 已提交任务不可编辑、不可重复提交
- 所有分区所有物料均录入后才可提交（值可为 0，不可为空）
- 提交后更新门店默认分区物料清单，数量为 0 的移除

### 创建任务页面
- 单门店创建，独立页面
- 进页面默认选中第一个模板
- 右侧展示选中模板的分区和物料预览

## 数据模型关键变更

相比原始 schema.sql：
- `material` 加 `inventory_unit VARCHAR(50)`
- `template_zone` 加 `sort_no INT DEFAULT NULL`
- `template_zone_material` 加 `sort_no INT DEFAULT NULL`
- `store_zone_material` 加 `sort_no INT DEFAULT NULL`
- `task_zone` 加 `sort_no INT DEFAULT NULL`
- `task_zone_material` 加 `sort_no INT DEFAULT NULL`

## API 概要

### 物料 /api/materials
- GET 分页列表（名称搜索），POST 新增，PUT 编辑，PATCH 启停，DELETE 删除（被引用则拒绝），GET /all 全部

### 模板 /api/templates
- GET 分页列表，POST 新增，PUT 编辑，PATCH 启停，GET /{id} 详情
- POST /{id}/zones 新增分区，PUT 编辑，DELETE 删除
- POST /{id}/zones/{zoneId}/materials 分派物料，DELETE 移除
- PUT /{id}/zones/sort 分区排序，PUT /{id}/zones/{zoneId}/materials/sort 物料排序

### 任务 /api/tasks
- GET 分页列表（按门店/状态/模板名称筛选），POST 创建（生成快照）
- GET /{id} 详情，GET /{id}/result 结果
- GET /api/templates/{id}/preview 模板预览

## 前端拖拽排序实现

拖拽排序集中在 [TemplateList.vue](admin/src/views/template/TemplateList.vue)，使用原生 HTML5 Drag and Drop API，无第三方拖拽库。

### 核心模式：live reorder on dragover

分区和物料拖拽采用相同模式——在 `dragover` 事件中即时重排，而非等 `drop`：

1. **`dragstart`** — 记录拖拽源索引（分区是一维 idx，物料是二维 zoneIdx + matIdx），设置 `effectAllowed = 'move'`
2. **`dragover`** — clone 当前排序后的列表，splice 移除被拖拽项并插入到悬停位置，重分配 `sortNo`，回写响应式数组，更新拖拽源索引到新位置，设置悬停高亮索引
3. **`dragleave`** — 仅清除悬停高亮索引（视觉反馈）
4. **`drop`** — 只做状态清理 + `markDirty()`，重排已在 dragover 完成
5. **`dragend`** — 安全网清理所有拖拽状态

### 分区拖拽（一维）

| 状态 | 类型 | 用途 |
|------|------|------|
| `dragZoneIdx` | `number \| null` | 被拖拽分区索引 |
| `dragOverZoneIdx` | `number \| null` | 当前悬停分区索引（绿色边框 CSS class `zone-card--dragover`） |

模板迭代 `sortedZones`（computed，按 `sortNo` 排序），可直接操作 `zones.value`。

### 物料拖拽（二维）

| 状态 | 类型 | 用途 |
|------|------|------|
| `dragMatZoneIdx` | `number \| null` | 被拖拽物料所在分区索引 |
| `dragMatIdx` | `number \| null` | 被拖拽物料在分区内的索引 |
| `dragOverMatZoneIdx` | `number \| null` | 悬停分区索引 |
| `dragOverMatIdx` | `number \| null` | 悬停物料索引（顶部绿色线 CSS class `material-item--dragover`） |

物料拖拽目前**禁止跨分区移动**：`onMatDragOver` 中 `dragMatZoneIdx !== zoneIdx` 时直接 return，`onMatDrop` 中跨分区时清状态不标记 dirty。

### 排序保存

保存时机：用户点击"保存"按钮时，脏标记 `dirty` 为 true 才触发。调用两个 API：

- `PUT /api/templates/{id}/zones/sort` — body 为 `zoneIds` 数组（当前排序后的 zoneId 顺序）
- `PUT /api/templates/{id}/zones/{zoneId}/materials/sort` — body 为 `materialIds` 数组（每个分区内排序后的 materialId 顺序）

### CSS 视觉反馈

- 被拖拽项：半透明（opacity 0.4 分区 / 0.7 物料）
- 悬停目标：绿色边框（分区）或顶部绿色线（物料）
- 拖拽手柄：`HolderOutlined` 图标，鼠标悬停变绿色

---

## 到货验收报损 · 飞书通知系统

### 架构概览

| 组件 | 文件 | 说明 |
|------|------|------|
| 消息服务 | `common/.../feishu/FeishuMessageService.java` | Token 管理、卡片发送（sendToUser/sendToChat）、配置查询 |
| 每日汇总 | `server/.../job/LossReportDailySummaryJob.java` | 每天 9:30 发送卡片 A/B/E/G/F |
| 每月发券 | `server/.../job/LossReportMonthlyVoucherJob.java` | 月末发券汇总 |
| 自动收货 | `server/.../job/LossReportAutoReceiveJob.java` | 每天凌晨 3 点，补发超 4 天自动收货 |
| H5 页面 | `server/.../controller/LossReportH5Controller.java` | 审核/补发确认 + 视频下载 + Excel 导出 |
| 管理后台 | `server/.../controller/LossReportManageController.java` | 总部端报损管理 API |
| 小程序端 | `mp/.../controller/MpLossController.java` 等 | 门店报损登记/审批 |

### 配置表 `loss_notify_card_config`

| 字段 | 说明 |
|------|------|
| `category` | 分类分组 key（如"水果蔬菜""其他类"），或逗号分隔的子分类列表 |
| `card_type` | `pending` / `damage_audit` / `other_audit` / `other_resend` / `avocado_audit` / `avocado_resend` 等 |
| `feishu_user_id` | 收件人 open_id，逗号分隔多人 |
| `status` | 1=启用 0=停用 |

### 每日卡片发送清单（`doSend()`）

| 卡片 | 内容 | 收件人（card_type） | 发送方式 |
|------|------|---------------------|----------|
| A | 水果蔬菜待审核 | `pending` | 个人 |
| B1a | 外包装破损审核 | `damage_audit` | 个人 |
| B1b | 其他原因审核 | `other_audit` | 个人 |
| B2 | 其他原因补发（含外包装，不含牛油果泥） | `other_resend` | 个人 |
| E | 牛油果泥审核 | `avocado_audit` | 个人 |
| G | 牛油果泥补发 | `avocado_resend` | 个人 |
| F | 群统计日报 | — | 群（`feishu_loss_chat_id`） |

> 牛油果泥独立走卡片 E（审核）/G（补发），卡片 B 系列排除牛油果泥；审核按原因分卡（外包装 / 其他）；补发合并为一张。

### 报损状态流转

```
pending（待审核）
  → registered（审核通过，待补发）
      → confirmed_resend（厂家已补发，待门店收货）
          → received（门店已收货）或 not_received（门店未收到）
          → 4 天后自动 → received（LossReportAutoReceiveJob）
  → rejected（审核拒绝）
```

### 牛油果泥特殊处理

- 牛油果泥（material_id 从 sys_config `feishu_avocado_material_id` 读取）独立走卡片 E（审核）和 G（补发）
- 卡片 B 系列（审核+补发）中排除牛油果泥（avoFilter，`sendOtherAuditOrResend`）
- 卡片 G 链接带 `materialId=牛油果泥ID`，H5 补发页据此拆分"牛油果泥补发"和"其他类补发"两张卡片
- **单位换算**：`convertAvocadoQty(材料ID, 数量, 单位)` 和 `convertAvocadoUnit(材料ID, 单位)`
  - 仅当 `input_unit = "件"` 时：数量 ×24，单位改为 "包"
  - `input_unit = "包"` 时：保持不变
  - 非牛油果泥材料：原值返回，不受影响
- H5 页面牛油果泥统计表同理："件"的加总 ×24 再汇总
- H5 补发页按入口区分：卡片 G 链接带 `materialId=牛油果泥ID`，只展示牛油果泥卡片（按门店合计包数分档「24包及以上 / 24包以下」，统计表 tab 与待补发列表联动，默认 24包及以上）；卡片 B2 链接不带 materialId，只展示其他类卡片；蔬菜水果不进补发页（走月度发券）；搜索框支持多门店/物料关键字过滤，搜索词存 sessionStorage（按日期 `sgSearch_<date>`），切换待补发/已补发 tab（页面刷新）后 `restoreSearch()` 自动恢复，两个 tab 均生效
- H5 审核页「下载视频(ZIP)」拆分下载：前端先请求 `/api/public/loss-report/download-videos-plan`（只统计大小 + 打 download 日志），视频总大小超过 990MB（`ZIP_SPLIT_SIZE`）时拆分为多个分包，文件名加 `-partNofM` 后缀；前端按 `plan.parts` 每隔 3 秒依次下载 `download-videos?ids=..&supplier=..&part=N`（`part=0` 保持旧的单包行为，分包文件与单包 ZIP 均缓存在 `upload/zips/`）
- **确认登记修改数量**：牛油果泥审核「确认报损登记」弹窗显示原始数量（只读）+ 确认数量（可改，默认预填）；确认后修改值**直接写回 `input_qty`**（`input_unit` 改为"包"），下游补发/群日报/导出/台账全部沿用原逻辑无需改动；新增单列 `orig_qty` 记录修改前原始数量（按包，NULL=未修改，迁移脚本 `database/migration-add-confirm-qty.sql`）；卡片 h3 红色标注「数量修改：原始X包」（dailySummary 返回 `origQtyStr`）；日志记「数量修改：原始X包 → 确认Y包」；与原始一致不写 orig_qty 也不改 input_qty
- **视频缩略图（首屏加载优化）**：上传视频时服务端已生成 `<原名>_thumb.jpg`（ffmpeg，`thumbUrl()` 规则）；审核页卡片 `<video>` 加 `poster` 指向缩略图 + 新增 `thumbOf()`——iOS 微信 WKWebView 无视 `preload="none"`，进页面会为每个 `<video>` 拉视频数据导致首屏很慢，加 poster 后只拉小图；到货登记页（upload/h5/loss-arrival.html）编辑已有报损单时旧视频也用 `thumbOf()` 显示缩略图（新上传视频本来就有）
- **视频 faststart（点击即播）**：上传入口（MpUploadController.uploadVoucher / completeChunkUpload）存盘后对 mp4/mov 做 `-c copy -movflags +faststart` 重排（`faststartRemux`，失败保留原文件）——手机原片 moov 在文件尾，iOS 点击播放要拉完整文件，重排后能边看边下载；存量视频用户拍板不处理，`upload/faststart-all.sh` 脚本保留备用

### 手动触发接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/public/loss-report/trigger-summary` | 触发每日汇总（全部卡片） |
| POST | `/api/public/loss-report/trigger-avocado` | 仅重发牛油果泥审核卡片 E |
| GET | `/api/public/loss-report/trigger-avocado-urgent` | 触发牛油果泥加急卡片 |
| POST | `/api/public/loss-report/trigger-monthly-voucher` | 触发月度发券汇总 |
| POST | `/api/public/loss-report/trigger-weekly-warning` | 触发每周门店操作预警 |
| POST | `/api/public/issue/trigger-acceptance-reminder` | 触发未验收问题提醒：无参=完整逻辑（测试群/全部门店群）；`?chatId=群ID`=单群（只发指定群） |

### IssueAcceptanceReminderJob（未验收问题提醒）

- **文件**：`server/.../job/IssueAcceptanceReminderJob.java`
- **定时**：每天 9:00（cron `0 0 9 * * ?`）
- **数据源**：`issue` 表 `status='pending_acceptance'` 的问题，JOIN `store_info` 取门店群
- **测试/门店模式切换（运行时）**：`sys_config.feishu_issue_reminder_test_chat_id` 非空 = 测试模式（固定发该测试群，按钮**无** chatId，H5 全量）；**为空 = 门店模式**（按 `store_info.chat_id` 分组发，按钮带 `?chatId=`，H5 只显示本店问题；**无待验收问题的群不发卡片**）。改库即生效，无需重启（yml 兜底 `issue-reminder-test-chat-id`）
- **卡片**：黄色 header「⚠️ 未验收问题提醒（N 条）」+ 表格三列（问题标题 lark_md 链接直达 H5 / 类型 / 提交时间，无序号列）+ 底部按钮「👀 查看详情并验收」跳 `/h5/issue-accept.html`（**jar 内 static/，非 /upload/**）
- **H5 页面**：`static/h5/issue-accept.html` — URL 参数 `chatId` 过滤 + 20 秒自动轮询（指纹 diff，无变化不重渲染）+ 已解决/未解决弹窗按钮复位（防卡「提交中」）
- **记录**：每次发送写 `issue_reminder_send_log`（chat_id/store_name/message_id/issue_count），供已读回执查询（`GET /api/public/issue/reminder-read?date=`）

### LossReportAutoReceiveJob

- cron: `0 0 3 * * ?`（每天凌晨 3 点）
- 查找 `status='confirmed_resend'` 且 `confirmed_at <= NOW() - 4天` 的记录
- 更新 `status='received'`，写入 log（`action='auto_receive'`）

### WeeklyStoreWarningJob（门店操作预警）

- **文件**：`server/.../job/WeeklyStoreWarningJob.java`
- **触发**：每周一 8:00（`@Scheduled(cron = "0 0 8 ? * MON")`，当前已注释关闭）
- **手动测试**：`POST /api/public/loss-report/trigger-weekly-warning`
- **数据源**：`expense_record`（支出）+ `loss_report`（报损），按 `occurred_date` 查上周范围
- **逻辑**：活跃门店 - (上周有支出的 ∪ 上周有报损的) = 预警门店，按 `store_info.supervisor_name` 分组
- **发送**：
  - 督导个人：`fms.sendToUser()`，仅发自己名下预警门店
  - 督导群：`fms.sendToChat()`，汇总全部督导预警情况，群 chat_id 配置在 `sys_config.feishu_supervisor_group_chat_id`
- **卡片**：橙色 header，标题"门店操作预警通知--日期范围"，markdown 展示督导名（粗体）+ 家数 + 门店列表

---

## 分层记忆系统

项目使用分层记忆架构，详见 `memory/README.md`：

- **长期记忆**：`memory/MEMORY.md`（总览）+ `memory/decisions.md`（决策）+ `memory/patterns.md`（模式）+ `memory/feedback.md`（反馈）+ `memory/contacts.md`（联系人）
- **短期记忆**：`memory/YYYY-MM-DD.md`（每日会话日志，30 天衰减）
- **边界值**：重要性 ≥4 分写长期记忆，2-3 分写日志，<2 分丢弃
- **触发词**：用户说"记住"/"别忘了"/"永久保存"/"这是重点"时强制写入

**每次会话前**：检查 `memory/MEMORY.md` 索引和所有专项文件是否有更新。
