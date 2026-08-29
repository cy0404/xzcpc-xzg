# 象掌柜 · 微信小程序端 项目总览

> 门店盘点操作端，面向店长和店员。un-app + Vue 3 + TypeScript + Pinia + wot-design-uni。

---

## 一、项目概况

| 维度 | 详情 |
|------|------|
| **项目名称** | 盘点工具 / 象掌柜 (xzcpc-miniapp) |
| **AppID** | `wx3081dfa30167bb6e` |
| **目标用户** | 门店店长、门店员工、老板 |
| **核心场景** | 接收月盘任务 → 按分区逐项录入物料盘点数量 → 跨分区汇总 → 提交 → 查看结果 |
| **技术栈** | uni-app 3.0 (Vue 3 Composition API + `<script setup>`) + TypeScript + Vite 5 + Pinia 2 |
| **UI 组件库** | wot-design-uni (easycom 自动注册，前缀 `wd-*`) + @dcloudio/uni-ui |
| **构建目标** | 微信小程序 (mp-weixin) |
| **对应后端** | `mp-server` 模块（端口 30261，context-path `/storeInventory`） |

---

## 二、目录结构

```
miniapp/
├── package.json              # 依赖与脚本
├── tsconfig.json             # TypeScript 配置（strict，路径别名 @/ → src/）
├── vite.config.ts            # Vite 构建（@dcloudio/vite-plugin-uni），dev 端口 3000
├── index.html                # 入口 HTML
├── manifest.json             # uni-app 清单（AppID、微信插件、权限）
├── pages.json                # 路由 + Tab 栏 + 全局样式
├── CHANGELOG.md              # 变更记录
├── test-api.js               # API 连通性测试
├── test-flow.js              # 全流程集成测试（12 步）
├── test-httpbin.js           # 外部网络诊断
└── src/
    ├── main.ts               # 入口：createSSRApp + Pinia
    ├── App.vue               # 根组件（onLaunch checkLogin + 全局样式 + wot 主题覆盖）
    ├── env.d.ts              # 环境类型声明
    ├── manifest.json
    ├── pages.json
    ├── api/                  # 8 个 API 模块（~54 个端点）
    │   ├── auth.ts           #   认证（微信登录/门店绑定/老板注册） 11 端点
    │   ├── task.ts           #   任务（列表/详情/汇总/结果/提交） 7 端点
    │   ├── zone.ts           #   分区（物料/批量保存/增删排序） 7 端点
    │   ├── material.ts       #   物料（候选搜索/扫码/增删排序） 5 端点
    │   ├── expense.ts        #   支出（CRUD + 上传凭证） 8 端点
    │   ├── staff.ts          #   员工（登记/审批/离职/编辑） 12 端点
    │   ├── workHours.ts      #   工时（CRUD） 4 端点
    │   └── store.ts          #   门店搜索 1 端点
    ├── components/           # 11 个公共组件
    │   ├── BrandDialog.vue           # 自定义弹窗（图标+标题+提示区）
    │   ├── ConfirmDialog.vue         # wd-message-box 薄封装
    │   ├── DrawerNumericKeyboard.vue # 底部抽屉数字键盘（4×4 网格）
    │   ├── EmptyState.vue            # 空状态占位（empty/error/network/search）
    │   ├── MaterialIcon.vue          # 物料图标（emoji + 绿色背景）
    │   ├── ProgressBar.vue           # 百分比进度条（CSS 动画）
    │   ├── RefreshFab.vue            # 浮动/内联刷新按钮
    │   ├── Skeleton.vue              # 骨架屏加载
    │   ├── StatCard.vue              # 统计卡片（标签+数值）
    │   ├── StatusBadge.vue           # 彩色状态标签
    │   └── StoreSwitcher.vue         # 门店切换器底部弹窗
    ├── composables/
    │   └── useVoiceSearch.ts # 语音搜索（WechatSI 插件封装）
    ├── pages/                # 26 个页面
    │   ├── home/index/               # 首页 Tab（仪表盘）
    │   ├── tools/index/              # 工具 Tab
    │   ├── business/index/           # 经营 Tab
    │   ├── profile/index/            # 我的 Tab
    │   ├── login/                    # 登录（微信授权 + 手机号绑定）
    │   ├── task/                     # 盘点任务（5 页）
    │   │   ├── list/                 #   任务列表
    │   │   ├── detail/               #   任务详情
    │   │   ├── zone-entry/           #   核心盘点录入页 ★
    │   │   ├── summary/              #   盘点汇总
    │   │   └── result/               #   盘点结果（只读）
    │   ├── expense/                  # 支出管理（3 页）
    │   │   ├── list/                 #   支出列表
    │   │   ├── form/                 #   新增/编辑支出
    │   │   └── detail/               #   支出详情
    │   ├── staff/                    # 人员管理（8 页）
    │   │   ├── list/                 #   员工列表
    │   │   ├── detail/               #   员工详情
    │   │   ├── edit/                 #   编辑员工
    │   │   ├── register/             #   员工登记
    │   │   ├── register-success/     #   登记成功
    │   │   ├── approval/             #   审批列表
    │   │   ├── approval-detail/      #   审批详情
    │   │   └── approval-result/      #   审批结果
    │   ├── work-hours/               # 工时录入（1 页）
    │   ├── inventory/scan/           # 扫码盘点
    │   ├── bind/                     # 老板绑定（3 页）
    │   │   ├── owner-register        #   注册页
    │   │   ├── owner-register-qr/    #   二维码页
    │   │   └── owner-register-status #   注册状态
    │   ├── owner-home/               # 老板首页/门店总览
    │   ├── placeholder/              # 功能建设中占位
    │   └── agreement/                # 协议（2 页）
    │       ├── service               #   服务条款
    │       └── privacy               #   隐私政策
    ├── store/
    │   ├── user.ts           # 用户 Store（token/门店/员工/角色）
    │   └── task.ts           # 任务 Store（当前任务+历史任务）
    ├── styles/
    │   └── theme.scss        # 设计系统（品牌色 #2F8F57，工具类）
    └── utils/
        ├── constants.ts      # BASE_URL、错误码、状态映射
        ├── formatter.ts      # 日期/进度/金额/物料名格式化
        └── request.ts        # HTTP 封装（自动 token/加载/错误处理/401 拦截）
```

---

## 三、页面导航结构

### Tab 栏（底部 4 个标签）

| Tab | 路径 | 用途 |
|-----|------|------|
| 🏠 首页 | `pages/home/index/index` | 店长仪表盘（待办任务/统计数据） |
| 🛠️ 工具 | `pages/tools/index/index` | 工具集合入口 |
| 📊 经营 | `pages/business/index/index` | 经营数据查看 |
| 👤 我的 | `pages/profile/index/index` | 个人中心 |

Tab 栏配色：未选中 `#98A19C`，选中 `#2F8F57`（品牌绿），背景白色。

### 核心用户流程

```
微信授权登录 → 手机号绑定 → 选择/绑定门店
    ↓
首页仪表盘 → 查看月盘任务列表
    ↓
进入任务详情 → 逐分区录入盘点数量 ★（核心页 zone-entry）
    ├── 搜索/扫码添加物料
    ├── 数字键盘输入数量（支持多单位）
    ├── 保存分区
    └── 调整分区物料
    ↓
查看盘点汇总 → 确认提交
    ↓
查看盘点结果（只读）
```

---

## 四、API 层详情

**BASE_URL**：`http://119.45.162.160:30261/storeInventory/api/mp`（备案完成后切回 HTTPS 域名）
**鉴权**：`Authorization: Bearer <JWT token>`
**请求封装**：`src/utils/request.ts` — 自动注入 token、全局 loading、401 自动跳登录、网络异常 toast

### 端点统计（共 ~54 个）

| API 模块 | 文件 | 端点数 | 职责 |
|----------|------|--------|------|
| auth.ts | 认证 | 11 | 微信登录、门店绑定/切换、老板绑定流程、我的门店 |
| task.ts | 任务 | 7 | 任务列表、详情、提交、汇总、结果、物料搜索、未录入物料 |
| zone.ts | 分区 | 7 | 分区物料列表、批量保存、单条保存、增删分区、排序、重命名 |
| material.ts | 物料 | 5 | 候选搜索、扫码查询、添加/移除/排序分区物料 |
| expense.ts | 支出 | 10 | 支出类型查询、支出 CRUD、凭证上传、物料明细查询 |
| staff.ts | 员工 | 12 | 员工列表/详情、登记/审批、编辑、离职、公开查询 |
| workHours.ts | 工时 | 4 | 工时 CRUD |
| store.ts | 门店 | 1 | 关键词搜索门店 |
| **合计** | | **~54** | |

---

## 五、核心组件一览

| 组件 | 用途 | 关键 Props/Events |
|------|------|-------------------|
| `DrawerNumericKeyboard` | 底部抽屉数字键盘 | `modelValue`, `confirm`, 4×4 网格含小数点+退格 |
| `BrandDialog` | 自定义确认弹窗 | `icon`(info/warning/danger), `showTip`, `confirmType` |
| `StoreSwitcher` | 门店切换底部弹窗 | `open()` 方法, `@switched` 事件 |
| `ProgressBar` | 任务进度条 | `current`, `total`, CSS 过渡动画 |
| `StatusBadge` | 彩色状态标签 | `type`(success/progress/danger/warning/gray) |
| `StatCard` | 仪表盘统计卡片 | `label`, `value`, `valueColor` |
| `EmptyState` | 空状态占位 | `type`(empty/error/network/search) |
| `Skeleton` | 骨架屏 | `rows` 数量 |
| `MaterialIcon` | 物料图标 | `name`, `size` |
| `RefreshFab` | 刷新按钮 | `loading`, `inline` 模式 |
| `ConfirmDialog` | 简单确认框 | wd-message-box 封装 |

---

## 六、状态管理（Pinia Store）

### userStore (`src/store/user.ts`)

| 状态 | 类型 | 说明 |
|------|------|------|
| `token` | string | JWT token |
| `storeId` / `storeName` | string | 当前门店 |
| `employeeId` / `employeeName` | string | 当前员工 |
| `role` | string | 角色。**canonical 值是英文码 `owner`/`store_manager`/`staff`**（`/auth/me` 经 `determineRole()` 输出）。前端判角色请用英文码（可加中文兜底），勿只比中文——`work-hours` 曾因只比 `'老板'/'店长'` 导致真店长/老板被误拦（2026-07-13 已修） |
| `permissions` | string[] | 权限列表 |
| `storeCount` | number | 管理门店数 |
| `bound` | boolean | 是否已绑定门店 |

**关键计算属性**：`isLoggedIn` = `!!token && bound`

**关键动作**：`checkLogin()` 恢复登录态、`wxLogin()` 微信登录流程、`bindStore()` 门店绑定、`logout()` 清除所有状态、`fetchMe()` 异步验证

### taskStore (`src/store/task.ts`)

| 状态 | 类型 | 说明 |
|------|------|------|
| `currentTasks` | any[] | 当前活跃任务 |
| `historyTasks` | any[] | 已完成/归档任务 |
| `loading` | boolean | 加载状态 |

**关键动作**：`fetchTaskList()` 获取并分离为当前/历史任务

---

## 七、认证流程

```
App.vue onLaunch
  └→ userStore.checkLogin()
       └→ 从本地存储恢复 token + userInfo
       └→ 异步调用 GET /auth/me 验证（401 则清除）

登录页（两步骤）：
  Step 1：uni.login() 获取微信 code
          POST /auth/wx/login → 返回 bound 字段
          如果 bound=true → 直接进入首页
          如果 bound=false → 进入 Step 2
  Step 2：输入手机号
          POST /auth/wx/bind-phone → 成功后进入首页
```

---

## 八、设计系统

| 设计令牌 | 值 | 用途 |
|----------|-----|------|
| 品牌主色 | `#2F8F57` | 按钮、进度条、Tab 选中、标签 |
| 页面背景 | `#F7F8F6` | 全局背景 |
| 卡片背景 | `#FFFFFF` | 卡片、列表项 |
| 文字主色 | `#1F2421` | 标题、正文 |
| 文字次要 | `#66706A` | 辅助信息 |
| 文字三级 | `#98A19C` | 占位符、禁用态 |
| 警告色 | `#E58A2D` | 逾期、提醒 |
| 危险色 | `#E05A47` | 删除、错误 |

预置工具类：`.card`、`.card-outline`、`.tag-*`、`.flex-*`、`.action-bar`、`.tap-active`、`.brand-card`、`.theme-btn-primary`

---

## 九、语音搜索

`src/composables/useVoiceSearch.ts` 封装微信 WechatSI 插件 (`wx069ba97219f66d99` v0.3.7)：

- 处理隐私协议（`getPrivacySetting` / `requirePrivacyAuthorize`）
- 处理麦克风权限（`scope.record`，含引导跳转设置）
- 录音状态管理（录制/加载/识别/冷却）
- 上滑取消手势
- 6 秒加载超时保护
- 冷却锁防快速连续触发

---

## 十、关键业务规则

1. **单店长单任务**：同一手机号同时只能有一个未提交的盘点任务
2. **快照隔离**：任务创建时完整复制模板数据，模板后续变更不影响已创建任务（但未开始任务会通过事件机制自动同步）
3. **分区三态**：未开始 → 进行中 → 已完成（所有物料录入后 `isComplete=true`）
4. **部分保存**：分区保存不要求所有物料录入完毕，仅保存已填写数据
5. **提交即锁定**：任务提交后变为只读，不可再次修改或提交
6. **多单位录入**：物料支持多种单位（个/箱/斤等），换算关系由后端维护

---

## 十一、支出登记优化（2026-07-06）

### 自购食材物料采购

- 选择"自购食材"支出类型时，展示物料明细区域（物料名称搜索 + 重量 + 单价）
- 物料搜索按父级分类 `parentCategory = '自购食材成本'` 筛选，支持关键词模糊匹配
- 输入重量和单价后自动计算总价 → 回填支出金额
- 物料名称支持"其他"选项（不在物料库中的物料）
- 自购食材支出仅存 `self_purchase_material` 表，不写 `expense_record`
- 支出列表和详情页自动合并两张表查询，自购食材类展示物料信息
- 后端新增 `GET /api/mp/materials/by-category` 接口，带 Caffeine 缓存（5分钟）

### API 端点新增

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/mp/materials/by-category?parentCategory=&keyword= | 按父级分类搜索物料（缓存5分钟） |
| GET | /api/mp/expenses/{expenseId}/material | 查询支出关联的物料明细 |

### UI 优化

- 支出类型选择弹窗改为两列网格，按钮内展示描述文字（橙色）
- 所有 `›` 箭头改为品牌绿色 `#2F8F57`
- 物料明细区域白色卡片样式，标签在上、输入框在下（`#FFFFFF` 背景）
- 物料名称/重量/单价必填项红色 `*` 标识
- 表单页按设计稿重构：卡片式布局、凭证上传虚线区域、毛玻璃底部栏

### 隐私授权

- `App.vue` 新增 `wx.onNeedPrivacyAuthorization` 监听，支持上传凭证时正常调用相册
- `manifest.json` → `__usePrivacyCheck__: true`，配合后台已发布的隐私指引

### 自购物料表变更

`self_purchase_material` 新增字段：`store_miniapp_no`、`material_id`、`purchase_date`、`handler_name`、`voucher_url`、`remark`
删除字段：`received_qty`
迁移脚本：`database/migrate_self_purchase_material.sql`、`database/migrate_expense_to_self_purchase.sql`

---

## 十二、物料盘点页大改版（2026-07-08）

### 页面重构
- 搜索栏、提示条、筛选标签（全部/待盘/已盘，默认"全部"）对齐设计系统
- 底部栏改为"结束盘点"+"语音搜索"双按钮
- 多单位抽屉保留原有逻辑，新增称重单位卡片（橙色背景）
- 任务列表点击卡片直接进入物料盘点点（不调详情接口）

### 累加记录
- 数据库 `task_zone_material.append_records` 存储每次录入的独立记录
- 编辑抽屉累加模式下每次录入不合并，展示累加记录列表
- "总数" = 已有累计 + 本次录入

### 语音搜索增强
- 智能解析：提取"物料名 + 数量 + 单位"（如"牛油果100g"）
- 精准命中 1 个 → 自动开门回填；模糊匹配 → 提示点击回填
- 支持和待盘/已盘/搜索结果交互
- 中文数字转换（一→1，十→10，百→100）

### 其他
- 允许录入 0，关闭抽屉不自动保存
- 数量 > 100000 弹窗确认
- 抽屉滑动防穿透
- 汇总页去除分区指标，展示多单位详情（`1杯 · 1件 · 1个`）
- 详见 `memory/dateMemory/2026-07-08.md`

---

## 十四、调货管理模块优化（2026-07-10）

### 品项选购优化
- **底部抽屉录入**：点击物料弹出底部抽屉，2×2 单位网格卡片，选中绿底绿边，换算提示 "1箱=24个"
- **购物车抽屉**：点击 🛒 弹出已选物料列表，每行名称+数量+金额+删除✕，支持逐行删除
- **回填数量**：重新打开已录入物料时自动填入上次数量
- **底部金额**：品项选购和发起调货底部栏均展示总金额

### 发起调货优化
- **调入地默认当前门店**：表单初始化时填入当前门店
- **提交校验**：调出地或调入地必须有一个是当前门店
- **物料删除**：多单位物料点 ✕ 弹出抽屉逐行选择删除，底部"全部删除"选项
- **物料金额展示**：列表展示物料名+数量单位+金额

### 调货列表优化
- **概览面板**：4 列（待确认/待收货/已完成/已拒绝），已拒绝红色
- **删除待交接**：自动确认后该状态不再展示
- **卡片标题简化**：方向标签+纯物料名，副标题展示物流方向
- **门店切换**：选全部门店进入展示全部门店调货单+StoreSwitcher

### 首页优化
- **待处理事项**：全部门店按门店分组，副标题带门店名，点击自动切换门店+跳转
- **报损/调货卡片**：仅有待处理数据时展示
- **隐藏入口**：暂隐藏问题处理/物流信息/智能订货/上报问题

### 工具页优化
- **隐藏入口**：暂隐藏问题处理/物流信息/智能订货

---

## 十六、调货+报损优化（2026-07-13）

### 调货概览修复
- **待确认计数**：修复 `pendingReceive` 拼写→`pendingConfirm`，补充 `pending_confirm` 状态计数
- **全门店去重**：新增 `overview-total` 接口，单次 SQL 聚合避免逐门店累加双倍计数
- **首页待处理过滤**：只看需要本店操作的（待确认=fromStore，待收货=toStore）

### 报损增强
- **默认物料**：新增 `loss_visible` 白名单，打开表单自动加载，搜关键词查全表
- **搜索折叠**：搜索框初始折叠，点击展开
- **到货多单位**：支持 `input_unit`→`base_unit` 换算

---

## 十七、调货管理模块上线（2026-07-09）

### 新增模块

| 模块 | 说明 |
|------|------|
| `store/transfer.ts` | 调货物料 Pinia Store（多单位支持，单价换算） |
| `api/transfer.ts` | 调货 API（CRUD + 全门店列表 + 物料搜索 + 概览） |
| `pages/transfer/list/` | 调货列表页（概览卡片+方向标签+操作按钮+切换门店） |
| `pages/transfer/detail/` | 调货详情页（物料清单+流程记录+底部操作） |
| `pages/transfer/form/` | 发起调货（双门店选择+搜索+物料+备注） |
| `pages/transfer/item-select/` | 品项选购（搜索+底部抽屉录入+多单位+数字键盘） |

### 品项选购

- **底部抽屉录入**：点击物料弹出抽屉，2×2 单位网格，选中绿底绿边
- **多单位支持**：从 `inventory_units` 读取可选单位，仅 unit 类型（不含称重）
- **换算提示**：每个单位显示 "1箱=24个" 格式的换算关系
- **实时合计**：底部汇总条显示换算后基准单位总量
- **数字键盘**：复用 `DrawerNumericKeyboard` 组件，"确认录入"按钮
- **购物车抽屉**：点击 🛒 弹出已选物料列表，支持逐行删除
- **回填数量**：重新打开已录入物料时自动填入上次数量

### 卡片标题

- 简洁格式：方向标签 + 物料名 + 物流方向（调出：A → 调入：B）
- 标题：`牛油果等3种`，副标题：`调出：南屏街店 → 调入：科技园店`

### 门店切换与权限

- **全部门店模式**：首页/工具页选"全部门店"进入，查看类工具传 `?all=true`
- **单门店模式**：默认展示当前门店，无切换按钮
- **执行类工具**：全部门店时弹出抽屉选门店，单门店直接进入
- **发起调货权限**：所有角色均可发起（已移除店员限制）

### 首页/工具页重构

- **范围卡片**：横向滑动 chips（全部门店 + 名下各门店），单门店隐藏
- **权限差异化**：老板展示经营概况，店员隐藏工资工时
- **待处理事项**：动态展示盘点/调货/报损待办，全部门店按门店分组

### 新增页面

`pages/transfer/list/`、`pages/transfer/detail/`、`pages/transfer/form/`、`pages/transfer/item-select/`

### 新增 API

`fetchTransferList`、`fetchTransferDetail`、`createTransfer`、`confirmTransfer`、`shipTransfer`、`receiveTransfer`、`cancelTransfer`、`rejectTransfer`、`fetchTransferOverview`、`searchTransferMaterials`

---

## 十四、构建与脚本

```bash
# 安装依赖
npm install

# 开发模式（编译到 dist/dev/mp-weixin/）
npm run dev:mp-weixin

# 生产构建（编译到 dist/build/mp-weixin/）
npm run build:mp-weixin
```

**开发服务器**：端口 3000，`/api` 代理至 `https://www.xzcpc-9pd.top`

---

## 十三、当前规模

| 维度 | 数量 |
|------|------|
| 页面 | 26 个（含 4 个 Tab 页） |
| 公共组件 | 11 个 |
| API 模块 | 8 个 |
| API 端点 | ~56 个 |
| Pinia Store | 2 个 |
| 组合式函数 | 1 个 |
| 工具函数模块 | 3 个 |
| 测试脚本 | 3 个 |

---

## 十六、2026-07-11/13 更新

### 待处理事项增强

- **员工审批卡片**：首页待处理事项新增 👥 员工审批，`fetchStaffOverview(all?)` 调 `/staff/applications/overview`，全部门店/单门店双模式支持
- **报损待处理仅保留关键状态**：仅 `pending_approval`（待审核）+ `confirmed_resend`（待补发）计入待处理，后端 `LossReportServiceImpl.overviewByStores` 同步
- **调货待处理仅保留关键状态**：仅 `pending_receive`（确认收货）+ `pending_ship`（确认可调出）计入待处理，后端 `TransferOrderServiceImpl.overview/overviewByStores` 同步

### 任务列表优化

- **过期任务移入历史**：后台 `MpTaskServiceImpl.list()` 过期任务归入 `history`，前端历史卡片红字"已过期"+ 灰底半透明 + 点击 toast "任务已过期，无法查看"
- **已提交任务不变**：绿字"查看结果 ›"，点击正常跳转

### 物料盘点页 bug 修复

- **清除搜索**：✕ 按钮退出 `locateMode`，清空 `focusMaterialId`/`extResults`/`voiceHintText`
- **确认录入二次弹窗**：`confirmDrawer` 设 `skipNextMatch` 标记，watch 检测跳过 matchOne，防止保存后状态变更重复弹窗
- **语音搜索 single match 逻辑**：`totalMatch` 计数合并待盘+已盘，总数唯一才自动弹窗，支持已盘单单位物料累加模式预填

### 首页经营数据

- **今日销售**：硬编码 `¥126,430`/`¥18,260` 改为 `--`，待真实数据接入

### 前端 BASE_URL

- `constants.ts` 和 `vite.config.ts` 支持切换本地 IP / 生产域名

---

## 十四、相关文档

- 主项目 CLAUDE.md：`../CLAUDE.md`（根目录项目总览）
- 变更记录：`../CHANGELOG.md`
- 后端架构：`../xzcpc-xzg/`（Maven 多模块 Java 项目）
- 数据库设计：`../database/schema.sql`
- 开发计划：`../plan/`
- **P0 日报**：详见 [xzcpc-xzg/memory/P0-plan.md](../xzcpc-xzg/memory/P0-plan.md)、[dateMemory/2026-07-08.md](dateMemory/2026-07-08.md)

---

## 十五、门店报损模块（P0 A2，2026-07-07/08）

### 新增页面与 API

| 页面 | 路径 | 说明 |
|------|------|------|
| 报损列表 | `pages/loss-report/list/index` | 统计面板 + 日常/到货两个入口 + 记录列表 + 底部新建弹窗 |
| 报损详情 | `pages/loss-report/detail/index` | 信息卡 + 处理结果 + 计算公式 + 图片备注 |
| 扫码盘点 | `pages/inventory/scan/index` | 调起微信扫码 → 后端识别物料 → 展示结果 |
| API | `api/loss-report.ts` | 报损 CRUD + 容器列表 + 物料搜索 + 图片上传 |
| API | `api/inventory.ts` | 扫码识别 + 条码补充 |

### 报损核心业务

- **权限**：仅店长/老板可登记，staff 返回 403
- **日常报损**：提交即完成，状态 `closed` = "已记录"
- **到货验收**：状态流转 `pending` → `confirmed_resend` / `rejected`
- **半成品去皮**：选中半成品物料 → 容器胶囊按钮选择 → 自动算净重
- **物料搜索**：搜全 `material` 表，展示名称+规格+分类+单位
- **单位**：日常用 `base_unit`，到货用 `stock_unit`
- **图片上传**：最多 4 张，先本地预览再异步上传
- **登记人**：后端取自 `LoginUser.employeeName`
- **业务编码**：`LOSS + 时间戳 + 随机数`

### 工具页重构

- 分组布局：查看类工具（可跨门店）+ 执行类工具（需先选门店）
- 新增"门店报损"入口（执行类工具）
- 容器卡片展示（图片 + 名称 + 皮重）

### 数据库

| 表 | 说明 |
|----|------|
| `loss_report` | 报损记录（含 biz_code、reason、voucher_url、handler_name） |
| `container_config` | 容器配置（id=1去皮, 2~5对应1~4号容器，皮重12g/25g/50g/80g） |
| `barcode_supplement` | 条码补充申请 |

### 后端

| 文件 | 说明 |
|------|------|
| `MpLossReportController` | 报损 CRUD + 物料搜索 + 容器列表 |
| `LossReportService/Impl` | 净重计算 + 权限 + 自动生成 bizCode |
| `MpInventoryController/Impl` | 扫码识别 + 条码补充 |
| `LossReportManageController` | 总部报损台账 |
| `LoginUser` | 新增 `role` + `isStoreManagerOrOwner()` / `isStaffOnly()` |
| `MpAuthServiceImpl` | `determineRole()` 老板>店长>员工 |
| `BizCodeUtil` | 改为随机数防碰撞 |

### 15 → 当前规模

| 维度 | 旧 | 新 |
|------|------|-----|
| 页面 | 26 | 29 |
| API 模块 | 8 | 10 |
| API 端点 | ~56 | ~65 |

---

## 十六、门店报损优化（2026-07-10/11）

### 日常报损 UI 优化
- 默认选"不锈钢份数盒1/6*100mm"容器
- 容器详情卡片移到标题下方，净重公式移到含容器重量输入框下方
- 净重行内展示：`[输入框] g 净重：450g`
- 现场照片必上传（红色* + toast 校验）

### 到货验收报损优化
- 企迈单号必填（前端红色* + 后端校验）
- 支持多单位选择：`material_conversion_rule` unit 类型换算链
- `loss_report` 表 `unit`→`input_unit`、`loss_qty`→`input_qty`，新增 `base_unit`/`base_qty`
- 单位胶囊按钮，默认 `stock_unit`，展示换算提示（1箱=24个）

### 容器别名
- `container_config` 新增 `alias` 字段，胶囊展示别名（`alias || name`）

### 物料搜索
- 搜索结果不展示单位
- 搜索框初始折叠 → 点击展开 → 输入关键词下方显示结果
- 假滚动条提示可滑动

---

## 十七、到货验收报损飞书通知 + H5（2026-07-11）

### 飞书每日汇总
- 每天 9:30 定时汇总前一天 `status=pending` 的到货报损
- 按 `material_loss_notify_config` 分类分组，不同颜色卡片
- 卡片仅含汇总信息 + @对应人 + H5链接
- H5 每日确认页：待确认 + 已处理，缩略图+lightbox，气泡弹窗确认/拒绝
- 飞书逻辑全部在 server 端（`LossReportDailySummaryJob` + `LossReportH5Controller`）

### 新表/配置
| 表 | 说明 |
|----|------|
| `loss_report_log` | 操作日志（submit/approve/confirm/reject/receive/not_receive） |
| `material_loss_notify_config` | 物料分类→飞书user_id，category 逗号分隔，status=1 启用 |
| `sys_config` | `feishu_loss_chat_id` 群ID |

---

## 十八、报损审批流程（2026-07-11）

### 状态流
| 角色 | 日常报损 | 到货验收 |
|------|---------|---------|
| 店员 | pending_approval → 店长通过 → completed | pending_approval → 店长通过 → pending（飞书）→ confirmed_resend → received |
| 店长/老板 | completed（直接完成） | pending（发飞书）→ confirmed_resend → received |

新状态：`received`（已收货）、`not_received`（未收到货）、`completed`（已录入）
展示：completed→已录入, closed→已关闭, received→已收货

### 审批操作
- 详情页底部固定通过/拒绝按钮（仅店长/老板可见）
- 到货 `confirmed_resend` 时底部已收货/未收到货+备注
- 处理流程时间线 + 门店反馈展示

### 角色判定优化
- `MpAuthServiceImpl.determineRole()` 改为直接查 employee 表 role 字段
- 移除 owner_registration 依赖
- 重装绑定时物理删除旧 employee 记录再插入

---

## 十九、全局地址切换（2026-07-11）
- 小程序 API：`https://www.xzcpc-9pd.top/storeInventory/api/mp`
- 图片上传 URL 从 `app.public-url` 推导
- 飞书 H5 卡片链接通过 applink 强制飞书内置浏览器打开

---

## 二十、老板绑定 & 新店入店讨论（2026-07-09/11/13）

### 老板绑定确认问题
- **背景**：老板扫码绑定时有乱绑风险，总部需和门店确认老板信息
- **限制**：微信小程序无法获取用户昵称（2021年后 `getUserProfile` 废弃，`getUserInfo` 返回"微信用户"）
- **确认方案**：导出员工表 → 手机号尾号 + 姓名脱敏 → 店长核对
- **openid 不可用于人工确认**：是一串乱码，用户不知道自己的 openid
- 当前代码 `userStore.wxLogin('')` 的 `wxNickname` 永远为空

### 新店入店校验方案
- **问题**：新店无老板信息（`owner_name`/`owner_phone`），无法走手机号+姓名自动匹配
- **可选方案**：
  - A. 短信验证码 — 需接入短信服务商，有费用
  - B. 微信手机号授权 `getPhoneNumber` — 零费用、微信担保实名、体验最好（需小程序已通过企业认证）
- **推荐 B**：绑定码 + 微信手机号授权，无需预存数据

### 报损状态补全（2026-07-13）
- 状态从 4 种扩展为 8 种：`pending_approval`（店员提交待店长批）/ `pending`（待厂家）/ `confirmed_resend`（已确认补发）/ `received`（已收货）/ `not_received`（未收到货）/ `completed`（已录入）/ `rejected`（已拒绝）/ `closed`（已关闭）
- `completed` 和 `pending_approval` 是后期新增，Entity 注释未体现
- 审批流程：店员→pending_approval→店长通过→completed（日常）或 pending（到货）
- `received`/`not_received`：厂家确认补发后门店反馈

---

## 二十一、飞书到货验收 H5 卡片优化（2026-07-21）
- 每条门店报损卡片新增报损时间（`occurred_date`，格式 MM-dd）
- 字段改为换行展示：企迈单号 → 原因 → 备注 → 报损时间
- "门店未收到货"反馈背景色从绿色改为红色（`.feedback-red`）
- 后端 SQL 新增 `r.occurred_date` 列，Java 映射 `occurredDate`

---

## 二十二、物料管理换算筛选优化（2026-07-21）
- 总部端 Admin 物料管理页面：换算类型下拉从"已维护/未维护"简化为"单位换算/称重换算"
- 后端 `MaterialRuleServiceImpl.matchConversionType` 新增 `unit`（仅规则换算）和 `weight`（仅称重换算）过滤
- 选择"称重换算"即可精确筛选有称重换算关系的物料

## 二十一、支出类型排序 + 自购食材提示 + 报损优化（2026-07-14/15）

### 支出类型排序（后端）
- `expense_type` 表新增 `sort_no INT DEFAULT NULL`，越小越靠前，NULL 排最后
- 小程序端 `MpExpenseServiceImpl.listTypes()` 排序从 `ORDER BY id` 改为 `sort_no IS NULL, sort_no ASC, id ASC`（10分钟缓存）
- 总部端 `ExpenseTypeServiceImpl.list()` 排序从 `ORDER BY updated_at DESC` 改为按 `sort_no`
- 新建支出类型自动分配 `sortNo = MAX(sort_no) + 1`，排到末尾
- SQL 迁移脚本：`database/migrate-expense-type-add-sort.sql`；统计脚本：`database/stat-expense-type-usage.sql`
- 前端小程序无需改动（`v-for` 直接渲染后端返回顺序）

### 自购食材提示
- 支出登记页选中"自购食材"时，在金额卡片和支出信息间展示 ⚠️ 浅橙提示条："请填写自行购买的物料，非企迈采购"
- 仅 `isSelfPurchase=true` 时可见，切换类型自动隐藏

### 自购食材"其他"物料分类修正
- 选"其他"物料时，`parentCategory` 从 `''` 改为 `'自购食材成本'`，`category` 从 `''` 改为 `'自购食材'`（仅前端提交逻辑）

### 报损列表优化
- **操作按钮**：卡片底部按状态展示操作按钮（取代"查看报损 ›"）
  - `pending_approval` + 店长/老板：**拒绝(红·左)** + **通过(绿·右)** 胶囊按钮
  - `confirmed_resend` + 所有人：**未收到货(红·左)** + **已收货(绿·右)** 胶囊按钮
  - 其他状态保持"查看报损 ›"箭头
  - 按钮 `border-radius:999rpx` 与调货管理风格统一，`@click.stop` 阻止冒泡不触发卡片跳转
- **权限**：审批仅店长/老板可见；收货所有人可见
- 导入 `approveLoss/rejectApproval/receiveLoss/notReceiveLoss` API，操作后自动刷新列表

### 日常报损新增优化
- 打开日常报损弹窗时含容器重量默认回填 `'0'`（到货验收不受影响）
- 公式"净重 0g = 含容器重量 0g - 容器 xxg"自动展开
- 含容器重量输入框占行宽 2/3，净重文字占 1/3 右对齐

### 容器图片与描述优化
- 容器详情卡片点击图片 → 自定义全屏预览层（黑底 + 右上角 88rpx 白边框圆形 ✕ 按钮 + `safe-area-inset-top`）
- 弃用 `uni.previewImage`（无法自定义关闭按钮）
- 去除容器描述中的档位标识（大容器/标准容器/小容器/不扣容器重量），仅保留"容器名 · 约XXg"

### 提交校验
- 默认0仅为展示公式，用户不改实际重量直接提交仍被"净重必须大于0"拦截

---

## 二十二、首页/工具页门店选中记忆 + Tab 切换优化（2026-07-15）

### 门店选中跨页面共享
- `userStore` 新增 `selectedScope` 字段（会话级，杀进程重置为 `'all'`）
- 首页与工具页共享同一选中门店：任一边切换，另一边 `onShow` 自动同步
- 带容错：若已存门店不在当前门店列表，回退到 `'all'`
- `logout()` 时重置为 `'all'`

### Tab 切换 loading 闪烁修复
- ~~首页 `onShow` 增加 `dataReady` 标记直接 return~~（2026-07-21 已移除：导致从人员管理页返回时数据不刷新）
- 优化：`switchStore` API 加 `showLoading:false`，减少重复 loading 闪烁
- 全部门店模式 4 个 API 并行化（`Promise.all` 替换串行 await）

### 老板首页清理
- 删除 `pages/owner-home/` 目录 + `pages.json` 路由注册（死代码，无任何入口引用）

---

## 二十三、报损页面大改版 + 地址切换优化（2026-07-17）

### 报损底部栏
- 日常报损/到货验收报损从页面中部移到吸底固定栏（毛玻璃 `backdrop-filter`）
- 改为纯色胶囊按钮（`border-radius:999rpx`）：绿色日常 + 橙色到货
- 按钮去副标题，副标题文字移到抽屉内展示
- 按钮顺序：到货验收报损(橙·左) → 日常报损(绿·右)

### 抽屉优化
- 日常报损：灰色居中副标题"过期、破损、制作损耗"
- 到货验收报损：⚠️ 橙色警告条居中一行"仅限企迈平台发的货支持到货验收报损，收货后请立即报损"

### 报损原因 + 容器选择
- 原因从 `<picker>` 改为胶囊按钮 + 横向滚动 + 右侧渐变提示
- 容器选择同样改为横向滚动 + 渐变提示
- 默认容器改为"去皮"（alias匹配→name匹配→tareWeight=0兜底）
- 净重计算显示真实值（去除 Math.max 截断）

### 图片上传
- 点击已上传缩略图 → 自定义全屏预览（复用容器图片预览层）

### 地址切换
- `constants.ts` 和 `vite.config.ts` 新增 `IS_PROD` 开关变量
- 切换只需改一行 `IS_PROD = true/false`

### 数据库
- `migration-material-add-name-index.sql` — material 加 `(del_flag, material_name)` 联合索引

### 问题处理首页集成
- 单门店+全部门店视图均展示"问题处理"待处理卡片
- 后端已有 `/issue/overview-stores` 接口按门店返回待验收计数

---

## 二十四、调货还货/还钱模块（2026-07-21）

### 新页面
- `pages/transfer/return/index` — 还货/还钱操作页
- 路由注册: `pages.json`

### 入口
- 列表页 + 详情页: `completed` + 调入方 → "还货"; 调出门店 → "还货记录"(审查模式)
- `handoff === 'returned'` 时隐藏所有操作按钮

### 操作模式
- 每项物料三选一胶囊: [仅还货][仅还钱][还货+还钱]
- 仅还钱: 填金额,自动反推 `returnQty = amount / unitPrice`
- 还货+还钱: 各自独立,还钱不校验数量上限
- 顶部"全部还货/还钱"预填不提交

### 归还卡片
- 归还记录内嵌物料卡片,展示时间+经手人+数量/金额
- `✓ 已还清` 标签靠右居中
- 提交后 `loadDetail()` 刷新,不跳走

### 详情 Tab
- 已归还单双 Tab: "调拨详情"|"还货详情"
- 流程记录补充"物料已全部归还"步骤
- 调拨详情去归还数量显示

### 后端
- 新表 `transfer_return_record`
- `doReturn`: 还货校验数量,还钱金额不限
- `confirmReturn`: 设 `handoff='returned'`(不改 `status`)
- 剩余 = 调货总数 - 还货已还 - (还钱总金额 ÷ 单价)

### Bug 修复
- 首页/工具页 `switchStore` 补 `chatId`(问题上报不带门店)
- 去 `dataReady` 跳过(人员管理回首页卡片不刷新)
- 还货数量保留 2 位小数
- 首页问题处理卡片改紫色

### 问题处理日志
- 点"上报新问题"即记录"上报问题"(`POST /issue/report-click`,静默)
- 完整链路:上报问题 → 同步外部 → 外部回调

---

## 二十六、督导拜访 P2（2026-07-21~23）

### 新增页面
| 页面 | 路径 | 说明 |
|------|------|------|
| 拜访单详情 | `pages/supervisor-visit/detail/index` | 经营数据+沟通记录+行动计划+确认/异议操作 |
| 任务详情 | `pages/supervisor-visit/action-detail/index` | 状态卡片+任务内容+退回说明+反馈+审核结果+拜访记录链接 |

### 新增 API
| 模块 | 文件 | 说明 |
|------|------|------|
| 督导拜访 | `api/supervisor-visit.ts` | 7个端点：confirm/object/complete/overview |

### 首页集成
- 待确认拜访单卡片（仅被选确认人可见）
- 拜访任务逐条展示（按角色过滤，逾期淡黄色卡片）
- 点击直接跳详情或任务页

### 权限
- 确认/异议/提交反馈：`isStoreManagerOrOwner()`
- 待确认卡片：按 `confirmManagerOpenid/confirmOwnerOpenid` 过滤
- 任务卡片：按 `responsibleRole`（店长见店长任务，老板见老板任务）

### 任务逾期
- 动态判断：`pending + tracking_time < 今天` → 显示为 overdue
- 逾期仍可提交反馈
- 提交后若 `submitted_at > tracking_time` → 总端保留逾期痕迹

### 图片上传
- 选图后上传到 `/api/mp/upload/voucher`，存真实 URL

## 二十五、问题处理"未解决"弹窗 + Issue 实体精简（2026-07-22）

### 未解决原因弹窗
- 新增 `components/IssueRejectSheet.vue`：底部弹窗，标题"未解决原因"，输入框 label"解决原因"，支持图片/视频上传
- 列表页 `pages/issue/list/index.vue` 和详情页 `pages/issue/detail/index.vue` 的「未解决」按钮均改为打开弹窗
- 确认后流程：① `storeConfirm(id, 'reject')` → ② `replyIssue(id, replyText, mediaUrls)`（后端回写到 `acceptance_remark` 字段）
- 「已解决」按钮保持系统确认框不变
- 弹窗按钮固定底部，各占 50% 宽度

### Issue 实体精简
- **删除字段**（只写不读）：`contactName`、`contactPhone`、`images`、`processResult`、`acceptedBy`、`acceptedAt`、`submittedBy`、`processedAt`、`resolvedAt`
- **字段映射**：Java 实体 `replyText` 通过 `@TableField("acceptance_remark")` 映射到 DB 列 `acceptance_remark`（列名不变，无 SQL 迁移）
- 前端 Issue 接口同步精简，`replyText` 对应后端 `acceptance_remark` 列
- 详情页去除"现场图片"和"验收备注"展示，`replyText` 以"解决原因"标签展示
- 后端 `replyExternal()` 成功后将 `replyText` 回写到 `issue.replyText`（即 `acceptance_remark` 列）
- 同步修复 `P0ModuleTests.java` 测试

### Bug 修复
- 首页 `goPendingItem` 两处切换门店补 `userStore.chatId = data?.chatId || ''`，修复全部门店→待处理卡片→问题列表→上报新问题 chatId 丢失

### 下拉刷新
- 门店人员 `pages/staff/list/index.vue` 新增下拉刷新
- 门店报损 `pages/loss-report/list/index.vue` 新增下拉刷新
- 均在 `pages.json` 开启 `enablePullDownRefresh: true`

## 二十七、报损模块大改版（2026-07-27）

### 报损列表 6 Tab
- 顶部横向可滑动 Tab：全部 / 待审核 / 待收货 / 待确认 / 已登记 / 已拒绝
- `tabRecords` computed 按 activeTab 过滤 records
- 概览面板：今日记录 / 待审核(pending_approval) / 待收货(confirmed_resend)

### 到货验收报损新增加急
- 表单新增「是否加急」胶囊（是/否，默认否，选"是"红色背景）
- 提交时传 `urgent: 1/0`
- 仅其他类支持加急，水果蔬菜类无效

### 分类差异化状态文案
- 水果蔬菜类 `confirmed_resend` → "已发券"（按钮"已收到/未收到"）
- 其他类 `confirmed_resend` → "已确认补发"（按钮"已收货/未收到货"）
- 其他类 `registered` → nextStep"已发券，等待厂家发货"
- 水果蔬菜类 `registered` → nextStep"已登记，等待厂家发券"
- 判定依据：`LossReport.isFruitVeg` 透传字段（`LossReportServiceImpl.resolveFruitVeg()` 批量填充）

### 拒绝原因展示
- 详情页 `report.rejectReason` 红色展示

### 地址切换
- `utils/constants.ts` IS_PROD 控制本地/生产切换
- 本地：`192.168.0.4:30261`，生产：`www.xzcpc-9pd.top`

## 二十八、物料盘点页搜索清除 Tab 重置修复（2026-07-28）

### Bug 现象
- 搜索不存在的物料后点击搜索框 ✕ 清除，页面为空，没有回到「全部」Tab

### 根因
- `zone-entry/index.vue` watch 中 `!kw` 分支仅清理 `extResults` 等状态，未重置 `showPending`/`showDone` 为 `true`
- 搜索无结果时两个 Tab 均被设为 `false`，清除搜索后未恢复

### 修复
- `!kw` 分支增加 `showPending.value = true; showDone.value = true;`
- 覆盖：点击 ✕ / 手动删光 / 语音搜索清空 → 均回到「全部」Tab
- 详见 [dateMemory/2026-07-28.md](dateMemory/2026-07-28.md)

---

## 十七、日常报损多物料改造（2026-08-06）

### 概述
日常报损从「一单一物料」改为「一单多物料」，到货验收报损保持不变。

### 数据库
| 表 | 说明 |
|----|------|
| `loss_report_item` | **新建**明细表，存物料/重量/容器/净重/金额 |
| `loss_report` | `material_name` 改 nullable，新增 `item_count`（0=扁平/到货，>0=多物料），`voucher_url` 扩至 4000 |

- 迁移脚本：`database/migration/V14__loss_report_item.sql`（建表+改主表）
- 数据迁移：`database/migration/V15__migrate_daily_loss_to_items.sql`（存量日常报损→明细表）
- `item_count` 区分新旧：0 走扁平字段，>0 走明细表

### 后端新增/修改
| 文件 | 操作 | 说明 |
|------|------|------|
| `mp/entity/LossReportItem.java` | 新建 | 明细实体 |
| `mp/mapper/LossReportItemMapper.java` | 新建 | Mapper |
| `mp/dto/DailyLossCreateReq.java` | 新建 | 请求 DTO（含 `@Valid List<ItemReq> items`） |
| `mp/entity/LossReport.java` | 修改 | +items(非DB) +itemNames(非DB) +itemCount(DB) |
| `mp/service/LossReportService.java` | 修改 | +createDaily +updateDaily +deleteDaily |
| `mp/service/impl/LossReportServiceImpl.java` | 修改 | +createDaily(事务创建主+子) +updateDaily(删旧明细+重建) +deleteDaily(软删除+明细) +getItemsBatch +enrichMultiItemReports |
| `mp/controller/MpLossReportController.java` | 修改 | +POST /daily +PUT /{id} +DELETE /{id} +POST /{id}/remove-voucher |
| `server/.../LossReportManageController.java` | 修改 | 导出列适配多物料（itemNames/共N种/逐项公式） |

### 前端新增/修改
| 文件 | 操作 | 说明 |
|------|------|------|
| `pages/loss-report/form-daily/index.vue` | **新建** | 多物料表单页。标题/物料列表/容器胶囊/重量输入/净重计算/原因/备注/图片上传(20张)/底部金额栏。支持新增和编辑回填 |
| `pages/loss-report/list/index.vue` | 修改 | 底部「日常报损」按钮改为导航到 form-daily。卡片适配 itemNames 和 itemCount |
| `pages/loss-report/detail/index.vue` | 修改 | 顶部基础信息卡。多物料明细清单。底部固定编辑/删除栏。编辑跳转 form-daily 回填。软删除 |
| `pages.json` | 修改 | 注册 form-daily 路由 |
| `api/loss-report.ts` | 修改 | +createDailyLoss +updateDailyLoss +deleteDailyLoss +removeLossVoucher |

### 表单页设计
- 单页滚动布局，标题「新增报损」/「编辑报损」
- 物料卡片：名称+规格 + 右对齐净重+金额 + ✕删除，点击可编辑
- 添加物料：搜索底部抽屉 → 点击物料 → 底部编辑抽屉（容器胶囊选图+重量输入+净重/公式/金额）→ 确认加入列表
- 容器：横向文本胶囊，选中展示图片+名称+皮重，图片点击放大
- 报损原因：横向滚动胶囊（过期/破损/制作损耗/其他）
- 图片：最多20张，160rpx 方块网格
- 底部固定栏：报损金额(橙色) + 共N种物料 + 确认按钮(绿色圆角胶囊)
- 单价显示最大4位小数去尾零

### 编辑流程
- 详情页底部固定「编辑」「删除」按钮（仅日常报损）
- 编辑 → 跳转 form-daily?editId=xxx → 回填物料/原因/备注/图片
- 支持完整编辑（增删物料、换容器、改重量、增删图片）
- 保存调 updateDailyLoss，返回后 onShow 自动刷新
- 存量单物料未迁移也能正常编辑（fallback 从扁平字段回填）

### 详情页改动
- 顶部新增基础信息卡（报损类型/原因/登记人/创建时间/更新时间）
- 多物料展示明细清单，去掉计算公式
- 凭证与备注间距加大
- 图片仅查看模式，编辑时在表单页操作

### 后端 API 新增
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/mp/loss-report/daily | 新建多物料日常报损 |
| PUT | /api/mp/loss-report/{id} | 修改日常报损（删旧明细+重建） |
| DELETE | /api/mp/loss-report/{id} | 软删除日常报损+明细 |
| POST | /api/mp/loss-report/{id}/remove-voucher | 移除单个凭证图片 |

### 兼容性
- 到货验收零改动（POST / 扁平路径不变）
- 存量日常报损 item_count=0 走原路径，编辑时自动迁移至明细表
- 旧版小程序仍可调 POST / 单物料接口
- resolveFruitVeg 对 null materialId 安全跳过

---

## 二十九、后端改动（2026-08-11，无前端代码变更）

今天改动全在后端，小程序无变更，仅供参考：

- **牛油果泥单位换算修复**：仅 unit="件" 时 ×24→"包"，"包"不变
- **自动收货**：`LossReportAutoReceiveJob`，补发4天后自动收货
- 详见 [dateMemory/2026-08-11.md](dateMemory/2026-08-11.md)

---

## 三十、H5 牛油果报损更新（2026-08-13，无小程序端代码变更）

改动全部在 H5 页面（xzcpc-xzg/upload/h5/），小程序 src 无变更：

- **厂家选择**：牛油果泥必选厂家（hass/蓝蛙，图片卡片），拼接进 `remark`（格式 `厂家：XXX\n备注`），无新字段
- **隐藏加急**：牛油果泥不显示加急选项，自动重置为否
- **上传 4→10**：提示/校验/按钮显隐三处；`voucher_url` VARCHAR(4000) 够用
- **指引模块**：新增水印（image12）+喷码（image13）示例图，当天剪开拍销毁视频、每天报损勿积压
- **修复页面跳动**：搜索抽屉关闭后恢复滚动位置
- **数据库**：生产库缺 `qmai_store_id` 列需执行 `database/migration-add-qmai-store-id.sql`
- 详见 [dateMemory/2026-08-13.md](dateMemory/2026-08-13.md) 及 [docs/牛油果到货报损更新说明.md](../../docs/牛油果到货报损更新说明.md)

---

## 三十一、智能订货模块（2026-08-14，P1 上线）

> ⚠️ **页面形式（用户指令）**：不做原生 uni-app 页，改用 **H5 + web-view**（参考入库管理 inbound-list/inbound-detail 模式）。原生页面已删除，此节 2026-08-14 晚重写。

### 页面 = H5（`xzcpc-xzg/upload/h5/`，改后传服务器无需打包；static/h5 有 jar 副本）
| 页面 | 文件 | 说明 |
|------|------|------|
| 订货单列表 | `smart-order-list.html` | 概览面板**不可点击**（范围在首页/工具页进入前已切好）：`?token=&all=1&storeName=`；all 模式卡片带门店名+绿色门店 chip（GET /list?all=true + /overview-total），单店模式全部单据+状态 chip（/list + /overview）；`2026-W34`→`2026 年第 34 周`；去确认/重新确认/查看详情› 相对链接跳 detail；pageshow 刷新 + 下拉刷新（复用 inbound 阻尼模式） |
| 订货单明细 | `smart-order-detail.html` | 顶部返回头（history.back）→ 周概览 3 指标（建议数量实时联动步进器）→ 状态卡 4 态（pending 橙⏳含 deadline HH:mm / syncing 橙🔄 / success 绿✅含企迈单号 / submit_failed 红⚠️含 submitError+尝试次数）→ 明细表手写步进器（仅 pending/submit_failed 可编辑，锁定态显示确认数量）→ 底部固定栏（合计数量 + 确认订货/重新提交）→ 自定义确认弹窗 → PUT /{id}/confirm **提交全部明细**（后端语义：未提交明细视为 0 不订）→ toast「已提交，等待企迈同步结果」→ 刷新 |

- H5 自包含原生 JS（无外部依赖）：`API=origin+path.split('/upload/')[0]+'/api/mp'`、token 走 URL 参数、`Authorization: Bearer`、xhr 封装（code 401 登录过期 / code!==200 toast message）、showToast/confirm 弹窗、esc 转义

### miniapp 侧改动
- `pages/common/webview/index.vue`：支持 `title` 参数动态 setNavigationBarTitle（不传保持默认「入库管理」）
- **工具页查看类首项**「智能订货」📋（desc 动态 `每周建议订货单 · N 张待确认`，onShow 拉 overview-total，staff 不请求）：卡片 `h5: true` → `goSmartOrder()`——all 模式 H5 带 `&all=1`；单店模式先 `await switchStore(scope)` 保证 token 已切门店再打开
- **首页待处理事项**：all 模式按门店循环 🛒（storeId 携带，goPendingItem 先切门店再跳转）；单店模式 pending+submitFailed>0 单条；条目 `h5: true` → goPendingItem 末尾 `buildSmartOrderUrl()`（切换后用最新 token 构建 webview URL + title 参数）；数据请求按 `isManagerOrOwner` 门控（request 层会 toast，员工调会被 403 弹错）
- 删除原生 `pages/smart-order/` 两页 + pages.json 两条目；`api/smart-order.ts` 保留（工具/首页待确认数仍用 overview 接口）

### 状态卡 4 态
- pending 橙「订货单待确认」+生成时间/截止时间；syncing 橙「正在同步企迈」；success 绿「已生成企迈订货单」+企迈单号（全 0 确认则「确认完成·已跳过企迈下单」）；submit_failed 红「提交失败」+错误信息+尝试次数+「重新提交」按钮

### 后端要点（详见 xzcpc-xzg/memory）
- 每周一 6:00 定时生成建议单（也可 POST /generate 手动）；库存差法算日均消耗；**真实对接企迈创建报货单**；确认两阶段状态机 pending→syncing→success/submit_failed
- ⚠️ 数据源偏差：`store_zone_material` 是空表，物料池+库存取最近已提交任务的 task_material_summary
- **warehouseNo = `store_info.cangkuid`**（既有列，外部同步自动填，用户纠错"企迈仓库id是cangkuid"，不加新列）；为空时自愈——最近 60 天报货单 storeWarehouseNo 回写 cangkuid
- DB V17（smart_order/smart_order_item/5 条 sys_config，**无 store_info 改列**）；⚠️ 线上已执行过旧版 V17（含 ALTER 加 qmai_warehouse_no），表/配置一致无需处理，仅需跑 `database/migration-drop-qmai-warehouse-no.sql` 删多余列；仓库编码一般无需运维操作

### 范围规则
- 工具页查看类入口按当前 scope 决定 all 模式/单店模式；首页待办携带 storeId 先切换再跳转；H5 列表页进入后不可再切范围（概览面板非点击）
- 详见 [dateMemory/2026-08-14.md](dateMemory/2026-08-14.md)
