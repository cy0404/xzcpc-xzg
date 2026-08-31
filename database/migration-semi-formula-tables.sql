-- ============================================================
-- 半成品成本卡（BOM 配方）同步表 + 差异计算爆炸开关
-- 执行方式：手动执行（项目约定数据库变更不自动执行）
--          mysql -uxzcpc -p < database/migration-semi-formula-tables.sql
--
-- 背景：把日常报损 / 盘点 / 消耗 / 上月剩余中的半成品按 BOM 爆炸成原料，
--       解决"同一批物理库存、原料与半成品两种记账形态"的口径差。
--
-- 关键业务事实（2026-08-29~31 实测，勿改）：
--   1. 86 个半成品编码全部 WP 开头（与原料编码前缀无法区分，非文档所述 WPF）
--   2. 半成品判定/关联键 = qm_code ↔ semi_product.code；
--      material.material_id 与 semi_product.semi_id 不匹配（双身份物料 id 不同 + 旧 id 体系）
--   3. 爆炸适用性 = 配方驱动：有 ACTIVE 配方 → 爆炸；无配方 → 保留自身行
--      （PG 实测 purchase 无半成品行；牛油果泥 WP0531 是自制型，应爆炸并入 WP0352）
--   4. PG 消耗/采购表 item_code 100% WP 编码（无 cm id），单通道匹配
-- ============================================================

-- 3.1 半成品主表（一个半成品一行，冗余生效配方汇总字段便于直接查询）
CREATE TABLE IF NOT EXISTS semi_product (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  semi_id             VARCHAR(50)  NOT NULL COMMENT 'xinfo 半成品ID（cm 开头雪花ID；与 material.material_id 不对应，关联靠 code↔qm_code）',
  code                VARCHAR(50)  NOT NULL COMMENT '半成品编码（WP 开头，与 material.qm_code 对应，全链路关联键）',
  name                VARCHAR(200) NOT NULL COMMENT '半成品名称',
  specification       VARCHAR(100) DEFAULT '' COMMENT '规格',
  unit                VARCHAR(50)  DEFAULT NULL COMMENT '库存单位（kg等，与 material_inventory_rule.base_unit 一致）',
  net_output_quantity DECIMAL(18,4) DEFAULT NULL COMMENT '生效配方净出量',
  net_output_unit     VARCHAR(20)   DEFAULT NULL COMMENT '净出量单位（g/kg）',
  yield_rate          DECIMAL(7,4)  DEFAULT NULL COMMENT '生效配方得率（接口最大 100.0，DECIMAL(6,4) 整数位不够）',
  cost                DECIMAL(12,4) DEFAULT NULL COMMENT '生效配方总成本',
  status              VARCHAR(20)  DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  synced_at           DATETIME     DEFAULT NULL COMMENT '最近同步时间',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag            INT          DEFAULT 0,
  UNIQUE KEY uk_semi_id (semi_id),
  KEY idx_code (code)
) COMMENT '半成品成本卡主表（xinfo同步）';

-- 3.2 配方版本表（一个半成品可多版本，仅 ACTIVE 用于爆炸）
-- 注：接口版本层无 netOutputQuantity/netOutputUnit/yieldRate（2026-08-31 实测），
--     净出量/得率以 semi_product 主表生效配方汇总为准。
CREATE TABLE IF NOT EXISTS semi_formula_version (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  version_id          BIGINT        NOT NULL COMMENT 'xinfo 配方版本ID',
  semi_id             VARCHAR(50)   NOT NULL COMMENT '所属半成品ID（冗余）',
  version_name        VARCHAR(50)   DEFAULT NULL COMMENT '版本名（v1/v2…）',
  status              VARCHAR(20)   DEFAULT NULL COMMENT 'DRAFT/ACTIVE/DISABLED',
  total_cost          DECIMAL(12,4) DEFAULT NULL COMMENT '版本总成本',
  synced_at           DATETIME      DEFAULT NULL,
  created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag            INT           DEFAULT 0,
  UNIQUE KEY uk_version_id (version_id),
  KEY idx_semi (semi_id)
) COMMENT '半成品配方版本（xinfo同步）';

-- 3.3 配方行表（BOM 明细，itemType 可为 MATERIAL 或 SEMI_FINISHED → 递归爆炸）
CREATE TABLE IF NOT EXISTS semi_formula_item (
  id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
  version_id              BIGINT        NOT NULL COMMENT '所属配方版本ID',
  semi_id                 VARCHAR(50)   NOT NULL COMMENT '所属半成品ID（冗余，便于直接取生效配方行）',
  item_id                 BIGINT        DEFAULT NULL COMMENT 'xinfo 配方行ID',
  item_type               VARCHAR(20)   NOT NULL COMMENT 'MATERIAL原料 / SEMI_FINISHED半成品',
  material_id             VARCHAR(50)   DEFAULT NULL COMMENT '原料ID（itemType=MATERIAL；cm 开头，与 material.material_id 对应）',
  qimai_product_code      VARCHAR(50)   DEFAULT NULL COMMENT '企迈物料编码（WP 开头；爆炸目标匹配优先此列↔qm_code）',
  semi_finished_product_id VARCHAR(50)  DEFAULT NULL COMMENT '半成品ID（itemType=SEMI_FINISHED；cm 开头，与 semi_product.semi_id 对应）',
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

-- 3.4 功能开关（差异计算爆炸门控，默认关，验证后置 1）
INSERT INTO sys_config (config_key, config_value, description)
VALUES ('semi_bom_explode_enabled', '0', '盘点差异计算半成品BOM爆炸开关 1开 0关')
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- 3.5 不爆炸半成品黑名单（逃生舱，默认空；仅当未来出现"有配方但确认是进货成品"的异常半成品时人工填入）
INSERT INTO sys_config (config_key, config_value, description)
VALUES ('semi_no_explode_codes', '', '不参与BOM爆炸的半成品编码，逗号分隔（逃生舱，默认空）')
ON DUPLICATE KEY UPDATE description = VALUES(description);
