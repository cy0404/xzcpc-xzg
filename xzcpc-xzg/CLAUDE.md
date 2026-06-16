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
