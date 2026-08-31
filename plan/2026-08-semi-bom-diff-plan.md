# 半成品成本卡同步 + 差异计算"半成品→原料"切换规划

> 日期：2026-08-29
> 状态：已实现（2026-08-31 测试库端到端验证通过，待生产上线）
> 范围：新建 3 张表同步 xinfo 半成品成本卡（BOM/配方）；盘点差异计算时，把日常报损与盘点中的半成品按 BOM 爆炸成原料后参与计算

---

## 一、背景与目标

### 1.1 业务问题

当前半成品（如"珍珠奶茶茶底"）已作为物料同步进 `material` 表（`category='半成品'`，`material_id` = xinfo 24 位雪花 ID，`qm_code` = WPF 编码），门店可以：

- **报损**：`loss_report` 按 `material_id` 记半成品报损（`loss_type='daily'` 日常报损）
- **盘点**：模板可含半成品物料，门店逐项录入数量，汇总进 `task_material_summary`

但盘点差异计算的输入数据源（PG 采购 / 订货 / 消耗）**全部是原料粒度**（按 `qm_code` = WP 编码匹配）：

```
理论剩余 = 上月剩余 + 采购(PG) + 订货(PG) + 调货净值 + 还货净值 - 报损 + 自购 - 消耗(PG)
差异 = 盘点数(adjusted_qty) - 理论剩余
```

半成品没有采购、没有消耗记录 → 其理论剩余算不出（消耗为 0 直接跳过），半成品的报损/库存全部游离在差异体系之外。**同一批原料既存在于原料库存里，又存在于半成品库存里，两边对不上账。**

### 1.2 目标

1. 新建表同步 xinfo 半成品成本卡（半成品主表 + 配方版本 + 配方行），接口见 `C:\Users\xiemg\Downloads\原料、半成品产品.md`
2. 差异计算时，**按 id** 把日常报损中的半成品、盘点中的半成品，通过 BOM 递归爆炸成原料数量，合并进对应原料的报损 / 盘点数 / 上月剩余，半成品自身不再单独出差异行（切换语义）

### 1.3 范围界定

| 数据源 | 是否切换 | 说明 |
|---|---|---|
| 日常报损（`loss_type='daily'`） | ✅ | 本次核心，用户明确要求 |
| 盘点（`task_material_summary` / `task_zone_material`） | ✅ | 本次核心 |
| 消耗（PG `dwd.store_item_sales`） | ✅ | **用户确认：消耗数据里有半成品编码**（半成品编码全部 WP 开头，与原料编码前缀无法区分）。现状 `qmToMid` 已含半成品 → 半成品消耗被算在自己头上；半成品行消失后，其消耗必须爆炸并入原料，否则原料理论剩余虚高 |
| 上月盘点剩余（`prevAdjustedMap`） | ✅ | 一致性必需：上月原料盘点数已含半成品折算，本月上月剩余必须同样口径 |
| 到货报损（`loss_type='arrival'`） | ❌ | 用户未要求，算法同一函数可扩展；列为后续项 |
| 调货 / 还货 / 自购 | ❌ | 一般只走原料，暂不动 |
| 采购 / 订货（PG `dwd.purchase` / `dwd.purchase_order`） | ❌ | **PG 实测 purchase 表无任何半成品行**（86 code + cm id 均 0 行）→ 采购/订货无需改动（见 1.5 / 6.6） |

### 1.4 数据现状（2026-08-29 实测接口）

| 事实 | 影响 |
|---|---|
| 86 个半成品，编码**全部 WP 开头**（非文档所述 WPF），与原料编码前缀无法区分 | PG 消耗/采购中半成品只能靠 `material.qm_code` 映射识别，无法凭编码 |
| 半成品接口 id 全部 cm 开头（`cmq28f*` 占 68/86，其余 `cmpdox*`），是独立的物料 id 体系 | **PG 实测证伪"cm id 出现在数据中"的假设**：`dwd.store_item_sales` 402,445 行 item_code **100% WP 开头**，cm 前缀行数 **0**；`dwd.purchase` 8,104 行同样 100% WP。→ 消耗/采购匹配**单通道 qm_code 即可**，无需双通道 |
| **20 个编码在原料列表与半成品列表同时存在**（同名同码，如 WP0566 葡萄果肉、WP0595 草莓粒、WP0630 凤梨预制汁、WP0857 预制芭乐汁），且**两个接口的 id 完全不同**（原料 id=`cmpdox*`，半成品 id=`cmq28f*`） | 双身份物料在 `material` 表只有一条记录（material_id=原料 id，半成品 id 未落库），`material.category` 为原料分类 → **半成品判定必须用 `qm_code` ↔ `semi_product.code` 关联；`material.material_id` 与 `semi_product.semi_id` 对不上** |
| 6 个半成品无配方（WP0584 花瓣、WP0714 玫瑰汁、WP0729 饼干碎、WP0755 鲜橙预制汁、WP0758 橄榄混合汁、WP0911 荔枝净果） | 无法爆炸 → 保留自身行（兜底）。**PG 实测：无配方半成品消耗量不小**——近 2 月消耗 TOP1 是 WP0758 橄榄混合汁（4,673.74 kg），WP0911 荔枝净果（691.24）、WP0729 饼干碎也有消耗 → 无配方 fallback 必须打日志，便于后续补配方后重算 |
| 部分配方行 `itemType=SEMI_FINISHED`（WP0539 里糖珍珠、WP0567 葡萄混合汁、WP0943 调制奶基底、WP0959 预制芭乐汁、WP0969 预制柚子汁、WP0970 青柚粒等） | 递归爆炸是必须的，非理论场景 |
| **PG 实测：半成品消耗数据真实存在且逐月增长**——近 6 月按统计月：6月 423 行、7月 2756 行、8月 15015 行；近 2 月实际有消耗的半成品仅 7 个（橄榄混合汁/新鲜芭乐/调制椰奶/荔枝净果去杆/泰国青柚/羽衣甘蓝叶/饼干碎），集中在少量编码 | 消耗爆炸影响面集中在少数半成品，验证阶段可逐编码核对 |
| **PG 实测证伪"进货型半成品有采购记录"**：`dwd.purchase` 全表按 item_name 搜牛油果泥/珍珠/茶汤/果肉/预制均 **0 行**，86 个半成品 code 也 0 行 → **purchase 表不覆盖半成品采购**，采购探测判定进货型不可用 |
| **牛油果泥案例澄清（用户纠正，2026-08-31）**：WP0531 牛油果泥半成品**有配方**——WP0352 冷冻牛油果泥(24包/件) 1020.91g + 冰蔗糖浆 238.43g（损耗率 0.94%）；飞书卡片/补发出库单/供应商 288/件 处理的"牛油果泥"是**原料 WP0352**（250g/包×24包/件，规格吻合），不是 WP0531。**消耗表实测：WP0531 半成品 0 行、WP0352 原料 6月 4816 kg / 7月 437 kg / 8月 8192 kg**——门店消耗记在原料口径 | **WP0531 是自制型半成品，应爆炸并入 WP0352 + 冰蔗糖浆**（消耗侧 WP0352 已有大量记录 → 爆炸后盘点/报损/上月剩余并入 WP0352，口径闭合）。此前"牛油果泥=进货型=预置黑名单"的设计是混淆两个"牛油果泥"的误判，**推翻**。结论：86 个半成品无一是进货型，有配方的全是自制型 |

### 1.5 爆炸适用性（配方驱动，无进货型）

**PG 实测 + 牛油果泥案例澄清后，不再区分"进货型 vs 自制型"**——xinfo 分类本身已隐含：原料（452 个）= 进货的，半成品（86 个）= 店内加工的：

| 类别 | 特征 | 处置 |
|---|---|---|
| 有 ACTIVE 配方（80 个） | 配方行全部指向原料（MATERIAL），个别含子半成品（SEMI_FINISHED 递归）；店内加工 | ✅ 爆炸：盘点/日常报损/消耗/上月剩余全部爆炸并入配方原料，自身行消失 |
| 无配方（6 个：WP0584 花瓣、WP0714 玫瑰汁、WP0729 饼干碎、WP0755 鲜橙预制汁、WP0758 橄榄混合汁、WP0911 荔枝净果） | 可能是进货型成品或上游配方未录 | ⚠️ 无法爆炸 → fallback 保留自身行 + warn 日志（后续补配方后可重算） |

**判定规则（简单化）**：
1. 有 ACTIVE 配方 → 爆炸；无配方 → 保留自身行
2. 黑名单 `semi_no_explode_codes` 降级为**逃生舱**（默认空）：仅当未来出现"有配方但确认是进货成品"的异常半成品时人工加入
3. 双身份物料（既原料又半成品）按同一规则：有配方 → 爆炸并入原料
4. 判定时机：`semi_formula_sync` 每次同步后，黑名单内但无配方的 → 打告警日志（防配置写错编码）

---

## 二、现状梳理

### 2.1 相关代码位置

| 组件 | 位置 | 说明 |
|---|---|---|
| 半成品同步 | `template/.../service/impl/MaterialSyncServiceImpl.java` | 半成品已并入物料同步：`material_id`=xinfo id、`qm_code`=编码、`category='半成品'`、`parent_category='食材成本'`，规则表 base_unit=接口 unit（如 kg）；**配方/BOM 数据未落库**（DTO 注释明确"formulaVersions 不落库"） |
| xinfo 客户端 | `template/.../client/XInfoApiClient.java` | 已有 `fetchMaterials()` / `fetchSemiFinishedProducts()`，X-API-Key 认证，一次返回全量 |
| 半成品 DTO | `template/.../client/dto/XInfoSemiFinishedProduct.java` | `formulaVersions` 被 `ignoreUnknown` 忽略，需扩展 |
| 差异计算 | `task/.../service/impl/DifferenceCalcServiceImpl.java` | `doCalculate()` 单任务 / `doAutoCalc()` 批量；报损查询 `batchQueryLossByDate()`（新路径）+ `queryLoss()`（旧路径）；上月剩余 `loadPrevAdjustedQty()`；盘点数来自 `task_material_summary.adjusted_qty` |
| 报损表 | `loss_report` | `material_id` 直接可关联半成品，`base_qty`（基础单位数量）为爆炸输入 |
| 物料同步定时 | `template/.../job/MaterialSyncJob.java` | 每日 3:00，`app.sync.enabled` 门控 + GET_LOCK 串行 |

### 2.2 关键数据流（现状）

```
xinfo 半成品接口(仅基础字段)
   └→ material 表 category='半成品'  +  material_inventory_rule(base_unit=kg)

报损(loss_report.material_id=半成品) ──→ 差异计算的 lossMap  key=半成品id|门店   ❌ 原料对不上
盘点(task_material_summary)           ──→ 差异计算的 actual  key=半成品id      ❌ 理论=0 被跳过
```

### 2.3 目标数据流

```
xinfo 半成品接口(含 formulaVersions 完整配方)
   └→ 新表 semi_product / semi_formula_version / semi_formula_item
        └→ 爆炸服务：半成品数量 × (1/净出量) × 配方行用量 × (1+损耗率) ──递归──→ 原料数量(基础单位)
              ├─ 日常报损 → 并入原料 lossMap
              ├─ 盘点数   → 并入原料 actual
              └─ 上月剩余 → 并入原料 lastMonth
```

---

## 三、表设计（迁移脚本 `database/migration-semi-formula-tables.sql`）

3 张新表，风格对齐现有 `material` / `material_inventory_rule`（`del_flag` 逻辑删除、`updated_at` 自动更新）：

```sql
-- ============================================================
-- 半成品成本卡（xinfo BOM）同步表
-- ============================================================

-- 3.1 半成品主表（一个半成品一行，冗余生效配方汇总字段便于直接查询）
CREATE TABLE semi_product (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  semi_id               VARCHAR(50)  NOT NULL COMMENT 'xinfo 半成品ID（24位雪花ID，与 material.material_id 对应）',
  code                  VARCHAR(50)  NOT NULL COMMENT '半成品编码（WPF开头，与 material.qm_code 对应）',
  name                  VARCHAR(200) NOT NULL COMMENT '半成品名称',
  specification         VARCHAR(100) DEFAULT '' COMMENT '规格',
  unit                  VARCHAR(50)  DEFAULT NULL COMMENT '库存单位（kg等）',
  net_output_quantity   DECIMAL(18,4) DEFAULT NULL COMMENT '生效配方净出量',
  net_output_unit       VARCHAR(20)   DEFAULT NULL COMMENT '净出量单位（g/kg）',
  yield_rate            DECIMAL(6,4)  DEFAULT NULL COMMENT '生效配方得率',
  cost                  DECIMAL(12,4) DEFAULT NULL COMMENT '生效配方总成本',
  status                VARCHAR(20)  DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  synced_at             DATETIME     DEFAULT NULL COMMENT '最近同步时间',
  created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag              INT          DEFAULT 0,
  UNIQUE KEY uk_semi_id (semi_id),
  KEY idx_code (code)
) COMMENT '半成品成本卡主表（xinfo同步）';

-- 3.2 配方版本表（一个半成品可多版本，仅 ACTIVE 用于爆炸）
CREATE TABLE semi_formula_version (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  version_id          BIGINT        NOT NULL COMMENT 'xinfo 配方版本ID',
  semi_id             VARCHAR(50)   NOT NULL COMMENT '所属半成品ID（冗余）',
  version_name        VARCHAR(50)   DEFAULT NULL COMMENT '版本名（v1/v2…）',
  status              VARCHAR(20)   DEFAULT NULL COMMENT 'DRAFT/ACTIVE/DISABLED',
  total_cost          DECIMAL(12,4) DEFAULT NULL COMMENT '版本总成本',
  net_output_quantity DECIMAL(18,4) DEFAULT NULL COMMENT '净出量（配方产出量）',
  net_output_unit     VARCHAR(20)   DEFAULT NULL COMMENT '净出量单位',
  yield_rate          DECIMAL(6,4)  DEFAULT NULL COMMENT '得率',
  synced_at           DATETIME      DEFAULT NULL,
  created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag            INT           DEFAULT 0,
  UNIQUE KEY uk_version_id (version_id),
  KEY idx_semi (semi_id)
) COMMENT '半成品配方版本（xinfo同步）';

-- 3.3 配方行表（BOM 明细，itemType 可为 MATERIAL 或 SEMI_FINISHED → 递归爆炸）
CREATE TABLE semi_formula_item (
  id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
  version_id              BIGINT        NOT NULL COMMENT '所属配方版本ID',
  semi_id                 VARCHAR(50)   NOT NULL COMMENT '所属半成品ID（冗余，便于直接取生效配方行）',
  item_id                 BIGINT        DEFAULT NULL COMMENT 'xinfo 配方行ID',
  item_type               VARCHAR(20)   NOT NULL COMMENT 'MATERIAL原料 / SEMI_FINISHED半成品',
  material_id             VARCHAR(50)   DEFAULT NULL COMMENT '原料ID（itemType=MATERIAL）',
  semi_finished_product_id VARCHAR(50)  DEFAULT NULL COMMENT '半成品ID（itemType=SEMI_FINISHED）',
  item_name               VARCHAR(200)  DEFAULT NULL COMMENT '用料名称',
  quantity                DECIMAL(18,4) DEFAULT NULL COMMENT '用量（配方净出量对应的用量）',
  unit                    VARCHAR(50)   DEFAULT NULL COMMENT '用量单位',
  loss_rate               DECIMAL(6,4)  DEFAULT NULL COMMENT '损耗率（0.05=5%）',
  sort_order              INT           DEFAULT NULL,
  synced_at               DATETIME      DEFAULT NULL,
  created_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag                INT           DEFAULT 0,
  KEY idx_version (version_id),
  KEY idx_semi (semi_id)
) COMMENT '半成品配方行（BOM明细，xinfo同步）';

-- 3.4 功能开关（差异计算爆炸门控，默认关，验证后开）
INSERT INTO sys_config (config_key, config_value, description)
VALUES ('semi_bom_explode_enabled', '0', '盘点差异计算半成品BOM爆炸开关 1开 0关')
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- 3.5 不爆炸半成品黑名单（逃生舱，默认空；仅当未来出现"有配方但确认是进货成品"的异常半成品时人工填入）
INSERT INTO sys_config (config_key, config_value, description)
VALUES ('semi_no_explode_codes', '', '不参与BOM爆炸的半成品编码，逗号分隔（逃生舱，默认空）')
ON DUPLICATE KEY UPDATE description = VALUES(description);
```

> 注：`sys_config` 仅插入不覆盖已有值；开关默认 0（关），部署验证后手动置 1；黑名单默认空（牛油果泥 WP0531 案例澄清后无需预置——它是自制型，应爆炸）。

---

## 四、同步设计

### 4.1 接口确认（读自 `原料、半成品产品.md`）

- `GET /api/semi-finished-products`（不传 size 一次返回全部，当前实测 86 条），每项**含完整 `formulaVersions` 数组**（文档明确：列表处为完整配方版本对象，含 items 配方行）→ **一次调用即可拿到全部 BOM 数据**，无需逐 id 拉详情
- 配方行 `itemType`：`MATERIAL`（带 `materialId`）/ `SEMI_FINISHED`（带 `semiFinishedProductId`，需递归）
- 认证：X-API-Key 机器码（现有 `xinfo.api.key` 配置即可）

### 4.2 DTO 扩展（`template/.../client/dto/`）

- `XInfoSemiFinishedProduct` 增加 `List<XInfoFormulaVersion> formulaVersions`
- 新增 `XInfoFormulaVersion`（id, versionName, status, totalCost, netOutputQuantity, netOutputUnit, yieldRate, items）
- 新增 `XInfoFormulaItem`（id, itemType, materialId, semiFinishedProductId, itemName, quantity, unit, lossRate, sortOrder）
- 全部 `@JsonIgnoreProperties(ignoreUnknown = true)`，沿用现有风格

### 4.3 同步服务 `SemiFormulaSyncService`（template 模块，与 MaterialSyncService 同层）

```
sync()：
1. GET_LOCK('semi_formula_xinfo_sync') 串行（server / mp-server 双端互斥，模式同物料同步）
2. 拉全量半成品（含配方）→ 空响应抛异常中止（防误删）
3. 逐半成品：
   - upsert semi_product（按 semi_id，覆盖全字段）
   - formulaVersions 为空 → 保留库中旧版本（防接口暂时性缺数据误删 BOM），warn 日志
   - formulaVersions 非空 → 删除该半成品旧 version+item（逻辑删除），按接口重插
4. 不在接口中的半成品：不动（不删），同步只增改不删
```

要点：

- **只增改不删**：半成品停用（DISABLED）时保留历史 BOM，差异计算只取 ACTIVE 版本，天然忽略停用
- 逻辑删除而非物理删除（与项目全局约定一致）
- 幂等可重跑；一轮同步失败不产生部分状态（先删后插在事务内）

### 4.4 定时任务 `SemiFormulaSyncJob`（template 模块 job 包）

- 与 `MaterialSyncJob` 同模式：`@Scheduled(cron = "0 0 3 * * *")`，`app.sync.enabled` 门控，失败 log.error
- 两个 job 同点触发各自拉接口（GET_LOCK 不同名，可并行，互不影响）
- **手动触发**：新增总部端 controller `POST /api/admin/semi-formula/sync`（同步物料已有类似做法，便于上线后手动补跑验证），需 admin 权限

---

## 五、BOM 爆炸算法（核心）

### 5.1 服务 `SemiFormulaExplodeService`（template 模块；task 已依赖 template）

```java
/**
 * 半成品数量 → 原料数量（基础单位）
 * @return Map<原料 material_id, 数量(原料基础单位)>
 */
Map<String, BigDecimal> explode(String semiId, BigDecimal semiQty, String semiUnit);
```

### 5.2 算法步骤

```
1. 取该半成品 ACTIVE 配方版本（status='ACTIVE'），无 ACTIVE → 返回 null（调用方走兜底）
2. 半成品数量换算到净出量单位：semiQty(netOutputUnit) = semiQty(semiUnit) × 质量换算系数
   质量换算表：kg=1000g, g=1, 斤=500g, 两=50g, mg=0.001g（不足两单位可加）
   semiUnit 与 netOutputUnit 无量纲/无法换算 → 返回 null（兜底）
3. 单配方产出量系数 factor = 1 / netOutputQuantity（netOutputQuantity 为 null/0 → null 兜底）
4. 逐配方行：
   rawQty = semiQty(netUnit) × factor × item.quantity × (1 + lossRate)   [单位=item.unit]
   - itemType=MATERIAL：
        rawQty(item.unit) → 原料基础单位（复用 material_conversion_rule 换算链，
        逻辑同 DifferenceCalcServiceImpl.convertToBaseUnit，抽成公共方法）
        → 合并进结果 Map<原料id, 数量>
   - itemType=SEMI_FINISHED：递归 explode(该半成品id, rawQty, item.unit)
         ⚠️ 递归环保护：visited 集合，遇环 → warn 日志 + 跳过该行
5. 返回聚合后的 Map
```

### 5.3 性能设计（批量场景）

差异计算是批量任务（`doAutoCalc` 一次算几十个任务），爆炸不能逐行查库：

- 新增 `loadActiveFormulas()`：一次查库加载**全部**半成品 ACTIVE 配方版本 + 配方行 → `Map<semiId, ActiveFormula>`（含 items、netOutputQuantity、netOutputUnit）
- `doCalculate` / `doAutoCalc` 各调一次，爆炸过程纯内存递归，无 N+1
- BOM 数据量小（86 个半成品 × 几行配方），内存开销可忽略
- 可选：Caffeine 缓存 ACTIVE 配方（TTL 10min，参照 `templateZoneMaterials`），同步成功后清缓存

### 5.4 损耗率口径

`lossRate` 计入爆炸（×(1+lossRate)）——爆炸表达的是"做出这么多半成品实际消耗了多少原料"，BOM 中的损耗率是生产损耗，应计入。此项可在配置中留常量开关（默认计入）。

---

## 六、差异计算接入（`task/.../DifferenceCalcServiceImpl.java`）

所有改动都在数据源加载阶段，`computeDiff` 公式不动。

### 6.1 半成品判定（关联键 = qm_code，不是 material_id）

**`material.material_id` 与半成品 id 对不上**（实测 + 存量 id 体系），判定链路必须经过 qm_code：

```
material_id（报损/盘点/消耗的 key）
   → material 表查 qm_code（WP 编码，存量物料 qm_code = 接口 code，可靠）
   → semi_product.code 命中 = 半成品
```

- 主判定：**`qm_code` ↔ `semi_product.code`**（86 个半成品全部能命中，含 20 个双身份物料）
- 辅助判定：`material_id` ↔ `semi_product.semi_id`（纯半成品成立；双身份物料不成立，仅兜底）
- `material.category='半成品'` **不可用**（双身份物料的 category 是原料分类，会漏）
- 爆炸输入 qty 的单位：取 `material_inventory_rule.base_unit`（报损 base_qty 已换算到该单位）
- 双身份物料经此判定后，是否爆炸看配方驱动规则（有 ACTIVE 配方 → 爆炸，见 6.6）

### 6.2 盘点数（actual）爆炸

`doCalculate()` 中，构造原料口径的 actual 集合：

```
explodedActualMap: Map<原料id, 数量>
for summaries + zeroMatMap 里的每个物料：
  mid 是半成品（semi_product 表存在）且有 ACTIVE 配方且不在黑名单？
    是：qty = adjusted_qty(或 totalQty)；爆炸 → 并入 explodedActualMap 的原料项
        半成品自身标记"已切换"，不生成差异行
    否（无配方/黑名单内）：并入 explodedActualMap 自身
computeDiff 遍历 explodedActualMap（不再直接遍历 summaries）
```

### 6.3 日常报损爆炸

`batchQueryLossByDate()`（新路径）与 `queryLoss()`（旧路径）同步修改：

```
daily 分支（loss_type='daily' 且 status='completed'）：
  mid 是半成品且有 ACTIVE 配方且不在黑名单？
    是：explode(qty=base_qty, unit=半成品基础单位) → 合并进 原料id|storeId
        半成品自身不再 merge（切换）
    否：原逻辑
到货报损分支：不动（本次范围外）
```

### 6.4 消耗爆炸（用户确认的必需项）

`batchQueryPgConsumptionByDate()`（单任务路径）与 `batchQueryPgConsumption()`（批量路径）同步修改，三处变化：

1. **查询范围扩展**：`item_code IN (...)` 集合 = 任务物料 qm_codes ∪ **全部半成品 qm_codes**（PG 实测消耗表 100% WP 编码，cm id 行数为 0，无需加 cm id）——现状只查任务盘点的物料，若门店模板不含某半成品，其消耗行根本没被查出，原料消耗会缺一块
2. **单通道匹配**：PG `item_code` → mid 映射按 `qm_code`（WP 编码）建索引即可（PG 实测全表无 cm 前缀行；如未来数据形态变化，IN 集合加 cm id 也兼容）
3. **爆炸**：查询结果中 mid 是半成品且有 ACTIVE 配方且不在黑名单 → `explode(qty, pgUnit)` → 并入原料 `consumption`；半成品自身不再 merge

```
consumption(原料) = PG 原料行(WP编码) + Σ 有配方半成品行(WP编码)爆炸值
```

### 6.5 上月盘点剩余爆炸

`loadPrevAdjustedQty()`：上一任务 summary 中半成品的 `adjusted_qty` 同样爆炸并入原料——**保证上月原料盘点数（已含半成品折算）与本月上月剩余口径一致**，链不断。

### 6.6 爆炸适用性判定（配方驱动）

- **PG 实测 purchase 无半成品行 + 牛油果泥案例澄清后，取消"进货型"概念**：有 ACTIVE 配方 = 自制型 = 爆炸；无配方 = fallback 保留自身行
- 黑名单 `semi_no_explode_codes`（sys_config，逗号分隔 WP 编码）**默认空**，仅逃生舱（未来若出现"有配方但确认进货"的异常半成品才人工加入）
- 每次计算读取一次黑名单，命中且**有配方** → 该半成品不爆炸（走原口径）；命中但无配方 → 告警日志（配置疑似写错）
- 半成品无 ACTIVE 配方 / 单位无法换算 / 净出量为 0 / 递归环 → 兜底保留自身行（见 6.7）

### 6.7 开关与兜底

- 整个爆炸逻辑外层包 `semi_bom_explode_enabled`（sys_config）判断：关 → 走 100% 原逻辑
- 兜底（返回 null 的情况：无 ACTIVE 配方 / 单位无法换算 / 净出量为 0 / 递归环）：
  - 报损：保留半成品原行（原口径）
  - 盘点：保留半成品原行（原口径，理论=0 会被现有逻辑跳过）
  - 消耗：保留半成品原行（原口径）
  - 全部打 warn 日志（含半成品 id/名称/原因），便于上线期核对

### 6.8 展示增强（可选）

`inventory_difference` 加 `semi_explode_detail` JSON 列：记录该原料差异行中"来自哪些半成品、各折算多少"，差异明细页展示（如"含茶底折算 +3kg"）。帮助督导理解差异来源。若不加，差异行纯原料口径也自洽。

### 6.9 代码归属

| 新增/改动 | 位置 |
|---|---|
| DTO ×3 | `template/.../client/dto/` |
| `SemiFormulaSyncService` + Impl | `template/.../service/` |
| `SemiFormulaSyncJob` | `template/.../job/` |
| 同步 controller（手动触发） | `server/.../controller/`（总部端） |
| `SemiFormulaExplodeService` + Impl | `template/.../service/`（含质量换算表、`loadActiveFormulas`） |
| 爆炸接入 | `task/.../DifferenceCalcServiceImpl.java`（actual / loss×2路径 / 消耗×2路径 / prevAdjusted，共 5 处 + 黑名单判定读取） |
| 迁移脚本 | `database/migration-semi-formula-tables.sql` |

---

## 七、实施步骤

| 阶段 | 内容 | 产出 |
|---|---|---|
| 1 | 写迁移脚本（3 表 + 开关 + 黑名单），交用户执行（**不自动连库**，遵循项目约定） | `database/migration-semi-formula-tables.sql` ✅ |
| 2 | DTO 扩展 + `SemiFormulaSyncService` + Job + 手动触发接口 | 同步能力 ✅ |
| 3 | 手动触发同步，核对数据：条数 vs 接口（86）、抽样半成品 BOM 行数、ACTIVE 版本唯一性、6 个无配方半成品清单 | 数据就绪 ✅ |
| 4 | `SemiFormulaExplodeService` + 单元测试（单层爆炸 / 递归爆炸 / 单位换算 / 损耗率 / 环保护 / 兜底） | 爆炸核心 ✅（20 用例全过） |
| 5 | 接入 `DifferenceCalcServiceImpl`（5 处）+ 黑名单判定 + 开关 | 计算改造 ✅ |
| 6 | 测试库验证（见下）+ 确认 6 个无配方半成品是否需补配方 + 置开关 1 上线 | 验证通过，待上线 |

**2026-08-31 实测发现并修复的关键问题**：
- `explode()` 双身份兜底：调用方（盘点/报损/上月剩余）传的是 **material_id**，而快照按 semi_id 键控——两套 id 不同值（cm 前缀同体系不同号），修复前端到端**永远爆炸不了**。已在 `SemiFormulaExplodeServiceImpl.explode()` 增加 material_id → qm_code → semi 兜底（单测 `explodeWithMaterialId` 覆盖）
- 迁移脚本 `semi_formula_item` 漏了 `qimai_product_code` 列（实体/同步代码有）→ 已补（`ALTER` 测试库 + 更新脚本）
- 测试库 schema 漂移：`material` 等 4 表缺 `image_url`（生产有，`migration-add-material-image.sql`）→ 测试库补列

## 八、验证方案

1. **同步验证**：手动触发后 SQL 比对 `semi_product` 条数 = 接口 86 条；抽 2 个半成品比对配方行数量/用量与接口一致；确认每个半成品 ACTIVE 版本唯一；确认 6 个无配方半成品被识别
2. **爆炸单测**：构造已知 BOM（如 10g 茶底 = 珍珠 5g + 茶叶 2g + 糖 3g，损耗率 5%），断言 1kg 茶底爆炸结果 = 各原料 ×100×1.05（含 4 位小数精度）
3. **计算验证（测试库）**：
   - 选一个已提交任务，重算差异
   - 断言：自制型半成品自己的差异行消失；原料行 actual 增加 = 半成品盘点数爆炸值；原料行 loss 增加 = 半成品日常报损爆炸值；**原料行 consumption 增加 = 半成品消耗爆炸值**（选有半成品销售的窗口核对）
   - **牛油果泥重点核对（最佳案例）**：WP0531 半成品爆炸 → WP0352 冷冻牛油果泥 + 冰蔗糖浆（1020.91g / 净出量）；WP0352 原料行 actual/loss/prevAdjusted 增加，consumption 本就有 WP0352 直接消耗（8月 8192 kg），应闭合；WP0531 自身差异行消失
   - 无配方半成品（橄榄混合汁 WP0758 等）行保留、数值不变（回归）
   - 用 `semi_explode_detail` 或日志核对逐项来源
4. **灰度**：开关先 0，功能部署无回归；数据核对无误后置 1，再重算当月未算任务
5. **回归**：无半成品数据的门店任务，爆炸逻辑零影响（原料走原路径）

**2026-08-31 测试库实测结果（task 505，2026-07，店 cmpwa91ya010f3pq343o6pqxk）**：

| 验证点 | 结果 |
|---|---|
| 同步核对 | semi_product 86（54 ENABLED/32 DISABLED，80 有净出量+得率）；版本 80 ACTIVE（与半成品一一对应）；配方行 195（185 MATERIAL + 10 SEMI_FINISHED 递归层）；97 个去重 qimai_product_code；WP0531 净出 1259.34kg/得率 99.06/成本 65.8954，配方 2 行（WP0338 238.43g + WP0352 1020.91g，损耗率 0.0094）；6 个无 ACTIVE 配方 |
| 单元测试 | 20 用例全过：WP0531 单层（100kg→WP0338 0.0191kg+WP0352 0.0818kg，独立计算精确吻合）、递归 55kg、环保护、12 层深度保护、黑名单、无配方/净出量/单位换算兜底、material_id 双身份通道、isExplodable/explodableQmCodes |
| 重算行数 | **68 → 67**：消失行 = WP0549 鲜芒果叶（窗口内消耗 0.16kg，爆炸并入原料） |
| WP0352 行 | actual 37000 → **37001.6366**（+1.6366 = 2000g÷1259.34kg×1020.91g×1.0094 **精确吻合**）；lastMonth 38000 → 38002.0973 |
| WP0338 行 | actual 102800 → 102800.4221（WP0531 贡献 0.3822 + 其他含 WP0338 配方半成品）；lastMonth 26158 → 26158.5217 |
| WP0531 | 无自身差异行（无 PG 消耗本就被跳过，爆炸后亦不生成）✓ |
| 无配方保留 | WP0758 红李果汁保留自身行（消耗 8380，actual 0）✓ |
| 爆炸失败兜底 | **WP0688 速冻椰浆**保留自身行：配方行单位全为 **ml**，质量换算表（kg/g/斤/两/mg）不含 ml → 无法换算 → 兜底防算错 ✓（已知边界，见风险表） |
| 残留核对 | 差异行中无可爆炸半成品残留（仅兜底的 WP0688）✓ |
| 开关 | 验证后已复位 0（测试库） |

**遗留确认（上线前）**：6 个无配方半成品（橄榄混合汁 WP0758 消耗 TOP1 等）是进货成品还是配方未录？若后者需上游补配方。WP0688 类 ml 单位配方是否支持（需密度假设或人工校准，默认不扩）。

## 九、风险与注意

| 风险 | 应对 |
|---|---|
| 接口 id 体系会随上游重建变更（8/29 记忆：新旧两套并存） | 同步按 `semi_id` upsert + 保留 code；物料同步已按 qm_code 匹配，爆炸用 `material_id`（与 loss_report / summary 的引用一致），id 重建时由物料同步的 id 映射兜底，需复核 |
| BOM 数据频繁变动 | 每日 3 点全量刷新；差异计算读取的是计算当刻的 BOM（历史任务重算会用新 BOM，属可接受口径，需与用户确认是否接受） |
| 半成品单位（kg）与净出量单位（g）不一致 | 质量换算表覆盖 kg/g/斤/两/mg；未覆盖 → 兜底原逻辑 + 告警 |
| 双计风险 | 切换语义：半成品行不再出差异，只并入原料行；若门店同时盘了原料和半成品，两者物理库存确实同时存在，原料 actual = 原料盘点 + 半成品折算，**不重复**（半成品折算的是"半成品形态的原料"） |
| 20 个双身份物料（原料/半成品同名同码） | 消耗查询扩展后，PG 中同码行统一映射到同一条 material → 消耗=原料行+爆炸行，物理口径一致；配方驱动决定其是否爆炸 |
| "牛油果泥混淆"类误判（把进货原料当成自制半成品或反之） | 8/31 澄清后已消除：半成品判定按 qm_code↔semi_product.code，爆炸适用性按配方驱动；飞书卡片/出库单的对象是原料 WP0352（非半成品），二者互不影响。上线核对时注意同名义物料 |
| 无配方半成品消耗量大（实测橄榄混合汁 WP0758 消耗 TOP1、荔枝净果 WP0911、饼干碎 WP0729 均有消耗） | 无配方 → 保留自身行 + warn 日志（6.7）；若这些半成品实际是自制型，上线后原料消耗会缺一块，需人工给 xinfo 补配方后重算——验证阶段重点核对这 3 个 |
| **配方行单位 ml/L 不在质量换算表**（实测 WP0688 速冻椰浆 3 行全 ml → 爆炸失败兜底保留自身行） | 兜底防算错 ✓；ml↔g 需密度假设（水≈1 但不通用），默认不扩，等待上游配方单位规范化；影响面=单位含 ml 的配方半成品，上线前可 `SELECT DISTINCT unit FROM semi_formula_item` 核对清单 |
| 到货报损未覆盖 | 明确告知用户本次范围，算法留扩展点 |

## 十、待确认事项

1. ~~进货型清单~~ **已澄清（8/31 用户纠正）**：牛油果泥 WP0531 是自制型（配方=冷冻牛油果泥 WP0352+冰蔗糖浆），应爆炸；86 个半成品无进货型，黑名单默认空。剩余待确认：**6 个无配方半成品**（橄榄混合汁 WP0758 消耗 TOP1 等）是进货成品还是配方未录？若是后者，请上游补配方后爆炸口径才完整
2. ~~消耗双通道~~ **已实测**：消耗表 100% WP 编码（402,445 行无 cm 前缀），半成品消耗集中在 7 个编码，无需双通道；已落到计划 6.4 单通道匹配
3. **到货报损的半成品**是否也纳入切换？（默认否，仅日常报损；接口/算法支持直接扩展）
4. **BOM 变更对历史任务**：重算历史任务用当前 BOM 口径，是否接受？（默认接受，与"快照机制"业务惯例不同，此处理解为换算口径而非任务快照）
5. **展示增强**：`semi_explode_detail` JSON 列是否本期做？（默认做，便于验证与督导理解）
6. **爆炸结果精度**：保留 4 位小数（与全项目数量精度一致）
