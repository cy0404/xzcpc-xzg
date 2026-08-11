# 象掌柜 2026 年 7 月 P0 交付规划

**版本**：v1.0  
**日期**：2026-07-06  
**状态**：执行中  
**目标**：7 月 31 日前按时交付 P0 能力，并为 P2 首页/经营页演进预留基础设施，避免业务模块二次返工。

---

## 1. 交付目标

### 1.1 一句话目标

在 **不重构经营页、不接入财报/小象报表** 的前提下，完成供应链与现场协同相关的 P0 能力，并 **前移「当前查看范围」基础设施**，使老板多店场景下 P0 新模块可直接复用。

### 1.2 成功标准（7/31 验收）

| # | 验收项 | 标准 |
|---|--------|------|
| 1 | 范围基础设施 | 老板可在「全部门店 / 单店」范围下使用首页、工具页；执行类操作支持选店；三个 Tab 共享范围状态 |
| 2 | 盘点优化 | 门店端状态展示完整；总部可看任务进度与差异；盘点结果可作为后续智能订货数据基础 |
| 3 | 门店报损 | 日常报损 + 到货验收报损；半成品去皮；总部只读台账 |
| 4 | 调货管理 | 双向发起；确认→交接→收货→完成全流程；总部只读台账 |
| 5 | 物流信息 | 总部录入单号；门店查看轨迹与异常态 |
| 6 | 问题处理 | 门店上报→总部台账→状态同步→门店查看结果（象目经理可先 mock） |
| 7 | 企微通知 | 6 类事件可触发通知（真发或 mock，需文档标注） |

---

## 2. 范围边界

### 2.1 纳入 P0（必须交付）

| 分组 | 模块 | 门店端 | 总部端 | 后端 |
|------|------|:------:|:------:|:----:|
| **基础层** | 当前查看范围基础设施 | ✅ | — | ✅ |
| **基础层** | 首页轻量改造（scope 两态） | ✅ | — | — |
| **基础层** | 工具页执行/查看分类 + 选店 | ✅ | — | — |
| **P0-A** | 盘点优化 | ✅ | ✅ | ✅ |
| **P0-A** | 门店报损 | ✅ | ✅ | ✅ |
| **P0-A** | 调货管理 | ✅ | ✅ | ✅ |
| **P0-A** | 物流信息 | ✅ | ✅ | ✅ |
| **P0-B** | 问题处理 | ✅ | ✅ | ✅ |
| **P0-B** | 企微通知 | 事件消费 | 记录/规则 | ✅ |

### 2.2 明确不做（本月不交付）

| 模块 | 原优先级 | 本月处理 |
|------|----------|----------|
| 统一经营概览页完整 UI（待关注、排行、组合概况） | P2 | 仅做 scope 两态，完整 IA 留 P2 |
| 经营页 + 小象报表 + **财报明细** | P2 | `business` Tab 保持占位 |
| 督导经营辅导拜访 | P2 | 不做 |
| 智能订货 + 企迈对接 | P1 | 不做（企微「订货待确认」事件预留） |
| 新店筹备任务 | P1 | 不做 |
| 厂家报损飞书 H5 真接入 | — | 状态流转先做，H5 可 mock |
| 物流第三方真 API | — | 轨迹可 mock，接口预留 |
| 象目经理真同步 | — | 本地台账 + mock 同步 |

### 2.3 与现有实现的策略

| 现状 | P0 策略 |
|------|---------|
| `home` + `owner-home` 两页 | 合并为 `home` 的 `scope=all/store` 两态；`owner-home` 废弃或保留跳转兼容 |
| `business` 占位页 | 不动 |
| 老板多店靠 `switchStore` 换 token | 查看范围与操作门店分离；列表可跨店，写入操作带 `storeId` |
| Phase 1 盘点/支出/人员 | 维护性优化为主，不重写 |

---

## 3. 架构决策

### 3.1 核心决策：范围基础设施前移到 P0 第 1 周

**原因**：报损、调货、物流、问题的列表/详情/待办都依赖「全部门店可查看、执行先选店」。若按单店 `storeId` 硬写，8 月 P2 需返工全部新模块。

**做法**：铺基础设施，不做完整 P2 首页视觉和信息架构。

### 3.2 范围模型

```text
viewScope: 'all' | 'store'

scopedStoreId?: string     // viewScope=store 时生效
actionStoreId?: string     // 单次执行操作的临时门店（不改变 viewScope）

角色默认：
  老板（storeCount > 1）  → viewScope = 'all'
  老板（storeCount = 1）  → viewScope = 'store'，隐藏范围切换器
  店长 / 员工             → viewScope = 'store'，scopedStoreId = 当前门店
```

### 3.3 工具页分类（与 PRD 一致）

**查看类**（`viewScope=all` 时可跨店列表）：

- 问题处理列表
- 物流信息列表
- 调货管理列表
- 人员列表

**执行类**（必须先 `pickStore()`）：

- 盘点执行
- 新增支出
- 新增报损
- 新增/编辑员工
- 发起调货

### 3.4 API 约定（全 P0 模块统一）

| 场景 | 请求约定 |
|------|----------|
| 跨店列表（老板） | `GET /xxx?storeIds=` 或不传 = 查权限内全部 |
| 单店列表 | `GET /xxx?storeId={scopedStoreId}` |
| 写入操作 | `POST/PUT` body 必须含 `storeId`（来自 actionStoreId 或 scopedStoreId） |
| 详情 | 返回 `storeId`、`storeName` 供详情页展示所属门店标签 |

### 3.5 外部系统 Mock 策略

| 系统 | P0 做法 | 预留 |
|------|---------|------|
| 象目经理 | 本地 `issue` 表 + `external_ticket_no` 字段；定时/mock 回写状态 | 同步接口抽象为 `IssueSyncService` |
| 物流第三方 | 总部手填单号；`logistics_track` 表存 mock 轨迹 | `LogisticsQueryService` 接口 |
| 厂家报损 H5 | 状态手改或后台 mock「已确认补发/拒绝」 | `vendor_confirm_token` 字段 |
| 企微 | `notification_log` 表记录；开发环境 mock 发送 | `WeComNotifyService` 接口 |

---

## 4. 分周排期（7/6 → 7/31）

### 第 1 周（7/6 – 7/11）：基础层 + 盘点优化

| 序号 | 任务 | 端 | 产出 |
|------|------|-----|------|
| 1.1 | 业务规则收口文档 | 文档 | 角色矩阵、盘点状态机、待办统计口径 |
| 1.2 | `scopeStore` + `useStorePicker` | 小程序 | `store/scope.ts`、`composables/useStorePicker.ts` |
| 1.3 | 首页轻量改造 | 小程序 | `home` 支持 all/store 两态；废弃 `owner-home` 跳转逻辑 |
| 1.4 | 工具页改造 | 小程序 | 执行/查看分类；选店弹层 |
| 1.5 | 新模块表结构设计 | DB | 报损/调货/物流/问题/通知 迁移脚本草稿 |
| 1.6 | 盘点优化 | 全端 | 状态枚举统一、差异查看、进度统计 |

**本周验收**：

- [ ] 老板登录后默认 `scope=all`，可切换到单店且三 Tab 保持范围
- [ ] 工具页执行类操作弹出选店
- [ ] 盘点任务状态在小程序/总部展示一致
- [ ] 数据库迁移脚本草稿评审通过

---

### 第 2 周（7/14 – 7/18）：门店报损

| 序号 | 任务 | 端 | 产出 |
|------|------|-----|------|
| 2.1 | 报损表 + 容器配置表 | DB | `loss_record`、`loss_container_config` |
| 2.2 | 报损 API | 后端 | CRUD、容器列表、状态流转 |
| 2.3 | 报损列表/新增/详情 | 小程序 | `pages/loss/*` |
| 2.4 | 报损台账 | 总部 | `views/loss/LossManagement.vue` |
| 2.5 | 工具页入口 | 小程序 | 报损加入工具页（执行类） |

**本周验收**：

- [ ] 店长可登记日常报损（含半成品去皮计算展示）
- [ ] 可登记到货验收报损并查看厂家确认状态
- [ ] 总部可按门店/类型/时间筛选查看
- [ ] 仅店长可新增（老板在单店 scope 下继承店长能力）

**参考 PRD**：`projectDesign/Mini-program-end/prd/门店报损.md`、`projectDesign/Headquarters-end/prd/门店报损.md`

---

### 第 3 周（7/21 – 7/25）：调货 + 物流

#### 调货（7/21 – 7/23）

| 序号 | 任务 | 端 | 产出 |
|------|------|-----|------|
| 3.1 | 调货表 | DB | `transfer_order`、`transfer_item` |
| 3.2 | 调货 API | 后端 | 发起/确认/拒绝/交接/收货/取消 |
| 3.3 | 调货列表/创建/详情 | 小程序 | `pages/transfer/*` |
| 3.4 | 调货台账（只读） | 总部 | `views/transfer/TransferManagement.vue` |

**状态机**：`待确认 → 已确认 → 待发货/待交接 → 待收货 → 已完成`（含 `已取消`、`已拒绝`）

#### 物流（7/23 – 7/25）

| 序号 | 任务 | 端 | 产出 |
|------|------|-----|------|
| 3.5 | 物流表 | DB | `logistics_order`、`logistics_track` |
| 3.6 | 物流 API | 后端 | 总部录入、门店列表/详情、轨迹查询 |
| 3.7 | 物流列表/详情 | 小程序 | `pages/logistics/*` |
| 3.8 | 物流管理 | 总部 | `views/logistics/LogisticsManagement.vue` |

**状态**：`待发货 / 运输中 / 派送中 / 已签收 / 异常 / 查询失败`

**本周验收**：

- [ ] 两家门店完成一次调货全流程
- [ ] 老板在 `scope=all` 下可跨店查看调货/物流列表
- [ ] 总部可录入物流单号，门店可查看轨迹

**参考 PRD**：`projectDesign/Mini-program-end/prd/调货管理.md`、`projectDesign/Mini-program-end/prd/物流信息.md`

---

### 第 4 周（7/28 – 7/31）：问题处理 + 企微通知 + 收尾

#### 问题处理（7/28 – 7/30）

| 序号 | 任务 | 端 | 产出 |
|------|------|-----|------|
| 4.1 | 问题表 | DB | `issue_report` |
| 4.2 | 问题 API | 后端 | 上报、列表、详情、状态同步（mock） |
| 4.3 | 问题上报/列表/详情 | 小程序 | `pages/issue/*` |
| 4.4 | 问题台账 | 总部 | `views/issue/IssueManagement.vue` |

**字段**：标题、类型、紧急程度、描述、图片、联系人、电话  
**状态**：`已提交 / 处理中 / 已解决 / 已关闭`

#### 企微通知（7/30 – 7/31）

| 序号 | 任务 | 端 | 产出 |
|------|------|-----|------|
| 4.5 | 通知记录表 | DB | `notification_log` |
| 4.6 | 通知服务 | 后端 | `WeComNotifyService`（mock 实现） |
| 4.7 | 事件接入 | 后端 | 6 类事件触发点埋入各模块 |

**事件清单**（见 `projectDesign/docs/specs/2026-06-25-企微通知规则.md`）：

1. 问题状态更新（处理中/已解决/已关闭）
2. 物流异常
3. 调货待确认
4. 调货待收货
5. 每周订货单待确认（本期仅预留，不实现订货模块）
6. 盘点任务通知

#### 收尾（7/31）

- [ ] 全模块联调
- [ ] P0 验收清单逐项勾选
- [ ] 更新 `database/CHANGELOG.md`
- [ ] 编写 `plan/2026-07-p0-acceptance.md`（验收记录）

---

## 5. 各端改动清单

### 5.1 小程序（miniapp）

#### 新增文件

```text
src/store/scope.ts                          # 当前查看范围
src/composables/useStorePicker.ts           # 执行前选店
src/components/ScopeSwitcher.vue            # 顶部范围切换（底部弹层）
src/components/StorePickerSheet.vue         # 执行类选店弹层

src/pages/loss/list/index.vue
src/pages/loss/form/index.vue
src/pages/loss/detail/index.vue

src/pages/transfer/list/index.vue
src/pages/transfer/create/index.vue
src/pages/transfer/detail/index.vue

src/pages/logistics/list/index.vue
src/pages/logistics/detail/index.vue

src/pages/issue/list/index.vue
src/pages/issue/report/index.vue
src/pages/issue/detail/index.vue

src/api/loss.ts
src/api/transfer.ts
src/api/logistics.ts
src/api/issue.ts
```

#### 改造文件

```text
src/pages/home/index/index.vue              # scope 两态，合并 owner-home 能力
src/pages/tools/index/index.vue             # 执行/查看分类 + 新工具入口
src/pages/task/list/index.vue               # 状态展示优化
src/pages/task/detail/index.vue             # 状态/差异展示
src/pages.json                              # 新页面路由
```

#### 废弃/降级

```text
src/pages/owner-home/index.vue              # 保留路由兼容，逻辑迁入 home
```

---

### 5.2 总部端（admin）

#### 新增文件

```text
src/views/loss/LossManagement.vue
src/views/transfer/TransferManagement.vue
src/views/logistics/LogisticsManagement.vue
src/views/issue/IssueManagement.vue
src/views/task/InventoryDifference.vue      # 盘点差异（或合并入 TaskResult）

src/api/loss.ts
src/api/transfer.ts
src/api/logistics.ts
src/api/issue.ts
```

#### 改造文件

```text
src/router/index.ts                         # 新路由 + 侧边栏菜单
src/App.vue                                 # 侧边栏新增供应链/问题菜单组
src/views/task/TaskList.vue                 # 进度展示
src/views/task/TaskResult.vue               # 差异查看
```

---

### 5.3 后端（xzcpc-xzg）

#### 建议模块划分

```text
loss/        # 报损 + 容器配置
transfer/    # 调货
logistics/   # 物流
issue/       # 问题
notify/      # 企微通知（common 或独立小模块）
```

或在 `mp` 模块下新增 controller 包，按业务分包：

```text
mp/controller/LossController.java
mp/controller/TransferController.java
mp/controller/LogisticsController.java
mp/controller/IssueController.java
server/controller/admin/...               # 总部端对应 Controller
```

#### 盘点优化（改造现有 task 模块）

- 任务进度统计 API
- 物料差异计算与查询 API
- 状态枚举与 PRD 对齐

---

### 5.4 数据库（database）

#### 预估新增表

| 表名 | 用途 |
|------|------|
| `loss_container_config` | 半成品容器名称与重量（总部维护） |
| `loss_record` | 报损记录（日常/到货验收） |
| `transfer_order` | 调货单主表 |
| `transfer_item` | 调货物品明细 |
| `logistics_order` | 物流单 |
| `logistics_track` | 物流轨迹节点 |
| `issue_report` | 问题上报 |
| `notification_log` | 企微通知发送记录 |

迁移脚本命名：`migration-p0-loss.sql`、`migration-p0-transfer.sql` 等。

---

## 6. 业务规则收口（第 1 周必须完成）

开发前输出 `plan/2026-07-p0-business-rules.md`，至少包含：

### 6.1 角色权限矩阵

| 动作 | 店长 | 副店长 | 店员 | 兼职 | 老板（单店 scope） |
|------|:----:|:------:|:----:|:----:|:----------------:|
| 新增报损 | ✅ | ❌ | ❌ | ❌ | ✅（继承店长） |
| 发起调货 | ✅ | 待定 | ❌ | ❌ | ✅ |
| 上报问题 | ✅ | ✅ | ❌ | ❌ | ✅ |
| 查看物流 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 删除支出 | ✅ | ❌ | ❌ | ❌ | ✅ |

> 副店长边界若有争议，P0 默认与店长对齐调货/问题，报损仅店长。

### 6.2 盘点状态机（统一前后端枚举）

```text
未开始 → 进行中 → 待提交 → 已提交
                    ↓
                  已逾期（超时未提交，仍可补录并标记逾期）
```

### 6.3 待办统计口径

- 仅统计当前用户有权限处理的事项
- 老板 `scope=all`：各店待办聚合；`scope=store`：仅该店
- 不把无权限事项计入数字

---

## 7. 人员分工建议

### 7.1 单人开发顺序

每个模块：**DB → 后端 API → 小程序 → 总部**，范围基础设施在第 1 周最先完成。

### 7.2 双人并行

| 角色 | 第 1–2 周 | 第 3–4 周 |
|------|----------|----------|
| A（后端为主） | scope API 约定、盘点优化、报损/调货后端 | 物流/问题/企微 |
| B（小程序为主） | scopeStore、home/tools 改造、报损页面 | 调货/物流/问题页面 |
| 共同 | 第 1 周末联调 scope；第 4 周联调验收 | |

---

## 8. 风险与应对

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| 首页 scope 改造牵涉面广 | 中 | 延期 3–5 天 | 只做两态，不做 P2 完整 IA |
| 象目经理接口未就绪 | 高 | 问题处理卡住 | `IssueSyncService` mock，不阻塞其他模块 |
| 单人开发 7 月时间紧 | 中 | 企微/物流真接入来不及 | 物流 mock 轨迹；企微 mock 发送 |
| 与 Phase 1 盘点逻辑冲突 | 低 | 回归 bug | 盘点优化限「展示+差异」，不改提交核心逻辑 |
| 老板/店长页面分权理解偏差 | 中 | 返工 | 本月保持分页思路；财报明确不做 |

---

## 9. P2 预留（本月只铺路，不实现）

以下能力 **本月只预留字段/接口/状态位**，不做 UI：

| P2 能力 | 本月预留 |
|---------|----------|
| 待关注门店规则 | 首页 all 态留占位区块 |
| 门店排行 | 不实现 |
| 经营页趋势图 | `business` 保持占位 |
| 财报明细 | 不做入口 |
| 智能订货 | 企微事件枚举预留 |
| 督导拜访 | 不做 |
| 完整 owner-home 删除 | 路由可保留兼容一个版本 |

---

## 10. 参考文档索引

| 文档 | 路径 |
|------|------|
| 7 月阶段计划 | `projectDesign/docs/planning-pool/next-stage-plan.md` |
| 企微通知规则 | `projectDesign/docs/specs/2026-06-25-企微通知规则.md` |
| 业务逻辑补充清单 | `projectDesign/Mini-program-end/prd/开发用业务逻辑补充清单.md` |
| 首页 PRD | `projectDesign/Mini-program-end/prd/首页仪表盘.md` |
| 工具页 PRD | `projectDesign/Mini-program-end/prd/工具页.md` |
| 门店报损 PRD | `projectDesign/Mini-program-end/prd/门店报损.md` |
| 调货 PRD | `projectDesign/Mini-program-end/prd/调货管理.md` |
| 物流 PRD | `projectDesign/Mini-program-end/prd/物流信息.md` |
| 问题处理 PRD | `projectDesign/Mini-program-end/prd/问题处理.md` |
| 小程序现状 | `miniapp/memory/MEMORY.md` |
| 总部现状 | `admin/memory/MEMORY.md` |
| 后端现状 | `xzcpc-xzg/memory/MEMORY.md` |
| 数据库 | `database/schema.sql` |

---

## 11. 下一步行动（7/6 起）

1. **今天**：确认本规划范围无遗漏后，创建 `plan/2026-07-p0-business-rules.md` 起草角色矩阵  
2. **D1–D2**：实现 `scopeStore` + `useStorePicker` + 首页两态  
3. **D2–D3**：输出 4 套新表 migration 草稿  
4. **D3 起**：盘点优化 + 按周推进各业务模块  

---

> 本规划随开发进展更新。范围变更须同步更新本文档第 2 节并记录原因。
