# 智能订货功能规划（v2）

**版本**：v0.3（评审稿）
**日期**：2026-08-18
**状态**：待评审
**背景**：智能订货 P1（建议订货单 + 企迈报货单同步）**已实现**；本规划聚焦 P2：**按门店订货周期的周盘任务** + **预测引擎升级**（PG 同期销量/订货量 + 损耗/调货）。

---

## 1. 目标

```
按每个门店的订货周期生成周盘任务 → 周盘结果作为期末库存 →
建议订货量 = 预测需求(同期+趋势) + 损耗修正 ± 调货修正 − 库存 − 在途 + 安全库存 →
店长确认 → 同步企迈报货单（P1 已有链路）
```

## 2. P1 现状盘点（已实现，不要重复做）

### 2.1 已实现清单

| 组件 | 位置 | 说明 |
|------|------|------|
| 建议订货单表 | `smart_order` + `smart_order_item`（V17__smart_order.sql） | 单店单周唯一 `uk_store_week`，明细快照物料/单位/单价 |
| 生成 job | `SmartOrderGenerateJob`（每周一 06:00 全店生成，幂等） | 手动接口 `POST /api/mp/smart-order/generate` |
| 生成算法 | `SmartOrderServiceImpl.generateForStore()` | 库存差法（见 2.2） |
| 确认 + 企迈同步 | `confirm()` → QmaiClient 创建报货单 | 状态机 pending→syncing→success/submit_failed，`qm_declare_no`/`sync_attempts`/`submit_error`，仓库编码 cangkuid + 60 天自愈 |
| 手动增删物料 | `addItem`/`deleteItem`（upsert） | 店长可补订漏项 |
| 门店端页面 | H5 ×3：`smart-order-list/detail/add-item.html`（线上 `xzcpc-xzg/upload/h5/`） | 小程序首页/工具页入口 + 待确认 badge |
| 权限 | 店长/老板可用，员工禁止；`all=true` 跨店视图 | — |

### 2.2 一期算法（库存差法）

```
① demand = dailyUse × (cycleDays 7 + safetyDays 3)   ← cycle/safety 来自 sys_config
② base   = max(0, demand − 当前库存)
③ 折算到订货单位，向上取整（仓库按整件发货）
④ 只入 base > 0 且有企迈编码的物料；半成品/淘汰类过滤
```

- **物料池 + 当前库存** = 最近一次已提交盘点任务的 `task_material_summary`
  → 代码注释明确写了：**「周盘上线后替换 loadCurrentInventory 相关取数」**
- **日均消耗** = 库存差法 `(前次盘点 + 期间企迈报货入库 − 当前库存) ÷ 间隔天数`
  → 不足两次盘点或企迈拉取失败时用 `smart_order_default_daily_use`（0.5）兜底

### 2.3 与需求的差距（P2 要做的）

| # | 需求 | 现状 | 差距 |
|---|------|------|------|
| 1 | 按门店订货周期周盘 | 周盘字段有，**生成 job 无** | 新增周盘生成 job |
| 2 | PG 同期销量预测 | 库存差法，**未用 PG 销量** | 预测引擎升级 |
| 3 | PG 订货量（在途） | **无在途概念** | 新增在途减项 |
| 4 | 损耗修正 | **未接 loss_report** | 新增损耗修正 |
| 5 | 调货修正 | **未接 transfer_order** | 新增调货修正 |
| 6 | 按门店周期生成 | job 固定周一全店，`weekly_inventory_day` 未用 | 生成时机改造 |

## 3. 数据源清单（已验证）

| 数据 | 位置 | 关键字段 | 用途 |
|------|------|---------|------|
| 销量 | PG `dwd.store_item_sales` | `sales_quantity - return_quantity`、`stat_date`、`item_category`/`material_category` | 预测（半成品过滤，未来 BOM 换算） |
| 订货 | PG `dwd.purchase_order` | `order_status`、`review_quantity`、`shipping_quantity`、`order_time` | 在途 = Σ(review − shipping) 未完成单 |
| 采购到货 | PG `dwd.purchase` | `purchase_quantity` | 差异模块复用；预测口径可选校准 |
| 报损 | MySQL `loss_report` | 物料、数量、日期 | 损耗修正 |
| 调货 | MySQL `transfer_order(+item/return)` | 调出/调入 | 调货修正 |
| 期末库存 | 周盘 `task_material_summary` | 提交后汇总 | 引擎核心输入（替换现有取数） |
| 物料映射 | `material.qm_code ↔ PG item_code` | 单位换算 `convertToBaseUnit` | 复用差异模块 |

## 4. 决策记录（已确认 + 新增）

| # | 决策点 | 结论 |
|---|--------|------|
| 1 | 输出形态 | **生成订货单**，店长确认后同步企迈报货单（P1 已实现，沿用） |
| 2 | 预测口径 | 同期 + 趋势修正：去年同周 × (近4周 ÷ 去年同4周)，clamp [0.5, 2.0] |
| 3 | 周盘范围 | 单独 `template_type=weekly` 模板，只放可订货原材料 |
| 4 | 周盘生成 | 系统自动 + 总部干预（门店暂停、任务可停用） |
| 5 | 在途口径 | **差值法**：Σ累计订货(`purchase_order.order_quantity`) − Σ累计到货(`purchase.purchase_quantity`)，**不依赖 order_status**（差异计算未使用该字段，可靠性未知；差值法与差异模块口径一致可对账） |
| 6 | 半成品 | 销量表混半成品，未来接外部接口 BOM 换算；当前按分类过滤 + `MaterialDemandProvider` 抽象 |
| 7 | 安全库存 | 沿用现有 `safetyDays × dailyUse` 语义（默认 3 天），参数在 sys_config |
| 8 | 周盘未提交 | 订货单**跳过**生成（先盘后订），周盘提交事件补触发 |
| 9 | weekly 模板 | **全局一份**（取启用的 weekly 模板） |
| 10 | 总部后台 | 订货单管理页**本期做**（P2-C 排入） |

## 5. P2 设计

### 5.1 周盘任务生成（新增 job，server 或 mp-server）

```
每日 02:00 扫描：
  1. store_info.weekly_inventory_day IS NOT NULL AND weekly_paused = 0 的门店
  2. 若今天 = 该店盘点日 − 1 天 → 生成周盘任务
     - task_type='weekly'，task_week=ISO 周，deadline=盘点日 24:00
     - 快照自启用的 weekly 模板（模板绑定见 8.2）
  3. 幂等：同店同 task_week 未提交任务已存在 → 跳过
```

总部干预：门店级 `weekly_paused` 暂停；任务级沿用现有任务管理页。

### 5.2 订货单生成时机改造（改 `SmartOrderGenerateJob`）

- 由「每周一全店生成」改为「**每日扫描，按门店盘点日生成**」：盘点日当天生成本周订货单
- **周盘未提交 → 跳过**（先盘后订，不兜底旧数据）
- 周盘提交 → Spring Event 补触发生成（复用 @Async + 防抖模式），保证盘点日晚于 job 时段也能出单

### 5.3 预测引擎升级（改 `generateForStore`）

```
① 需求预测（替代库存差法）：
   predicted_demand = 去年同周销量 × 趋势系数 ÷ 7 × (cycleDays + safetyDays)
   趋势系数 = 近4周日均 ÷ 去年同4周日均，clamp [0.5, 2.0]
   无去年数据 → 降级近 4 周均值；仍无 → 库存差法 → 最后默认兜底（保留现有兜底链）
② 损耗修正：+ 近4周 loss_report 平均（按物料×门店，折算基础单位）
③ 调货修正：± 近4周平均(调出 − 调入)（净调出 → 多订）
④ 在途减项：− Σ累计订货 + Σ累计到货（差值法，全历史按门店×物料，不依赖 order_status）
⑤ 安全库存：safetyDays × 日均消耗（沿用现有语义）
建议 = max(0, 预测 + 损耗 ± 调货 − 当前库存 − 在途 + 安全库存)
```

- **物料池 + 当前库存**：本周周盘 `task_material_summary`；周盘未提交 → 跳过生成（决策 #8）
- 半成品过滤沿用 P1（`notLike category 半成品/淘汰`）+ 销量侧按 `item_category` 过滤
- 销量数据源抽象 `MaterialDemandProvider` 接口：`SalesDemandProvider`（现）/ `BomDemandProvider`（未来，外部接口确定后接入）

### 5.4 明细因子落表（改 `smart_order_item`）

**评估结论（待确认项 #4）**：加字段。理由：核心预测输入（去年同周销量、趋势系数）落表后，店长能看懂"建议怎么来的"，总部可追溯审计，未来校准模型有据可查；成本仅一个 ALTER + 生成时赋值。`reason` 文本同步展示。

```sql
ALTER TABLE smart_order_item
  ADD COLUMN last_year_qty  DECIMAL(12,4) DEFAULT NULL COMMENT '去年同周销量快照(基础单位)',
  ADD COLUMN trend_factor   DECIMAL(6,3) DEFAULT NULL COMMENT '趋势系数',
  ADD COLUMN loss_qty       DECIMAL(12,4) DEFAULT NULL COMMENT '损耗修正(基础单位)',
  ADD COLUMN transfer_qty   DECIMAL(12,4) DEFAULT NULL COMMENT '调货净值修正(基础单位)',
  ADD COLUMN in_transit_qty DECIMAL(12,4) DEFAULT NULL COMMENT '在途量(基础单位)';
```

- `daily_use` 字段语义复用：升级后为"预测日均消耗"（去年同期×趋势÷7 或降级值）
- `support_days` 语义不变：当前库存可支撑天数 = current / daily_use
- `reason` 示例：`去年同期 120，趋势系数 1.15，损耗 +8，调货 −5，在途 −30`

## 6. API 设计

### 6.1 小程序（已有，微调）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/mp/smart-order/generate` | 手动生成（保留） |
| GET | `/api/mp/smart-order/list?all=` | 列表（保留，展示新增因子字段） |
| PUT | `/api/mp/smart-order/{id}/confirm` | 确认 → 企迈报货（保留，不动） |

### 6.2 总部后台（新增）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/smart-orders?storeId=&week=&status=` | 全部门店订货单列表（状态跟踪） |
| GET | `/api/admin/smart-orders/{id}` | 单据详情 |
| GET/PUT | 门店订货周期配置 | 复用门店管理，补 weekly_inventory_day / weekly_paused |
| POST | `/api/admin/smart-orders/calculate` | 手动重算（按门店+周） |

## 7. 实施阶段

| 阶段 | 内容 | 依赖 |
|------|------|------|
| P2-A | 周盘闭环：weekly 模板管理（模板页加类型）→ 周盘生成 job → 小程序任务列表周盘标签 → 后台周期配置 | 无 |
| P2-B | 引擎升级：PG 销量预测 + 在途/损耗/调货修正 + 因子字段 + 生成时机改造 | order_status 取值确认（8.1） |
| P2-C | 总部后台订货单管理页 | P2-B |
| P3 | BOM 换算接入（外部接口确定后） | 外部接口 |

## 8. 待确认事项

已全部确认（决策表 #1~#10）。剩余风险：差值法在途依赖 `purchase_order` 是"下单即记"（非已完成量），首轮上线后与企迈报货单对账验证；若偏差大，再评估 status 或报货单明细口径。
