-- ============================================================
-- 修复生产 semi_product / semi_formula_version / semi_formula_item
-- 三表缺失的列（生产之前执行了旧版建表脚本）
-- 执行方式：手动执行（生产库 store_inventory）
-- 说明：先跑第 ① 步核对，再执行 ② 的补列语句；
--       若某条 ALTER 报 "Duplicate column name" 说明该列已存在，跳过即可。
-- 全部执行后重新调 GET /api/admin/semi-formula/sync
-- ============================================================

-- ① 核对三表当前列（对照 database/migration-semi-formula-tables.sql 最新定义）
SELECT 'semi_product' AS tbl, COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='semi_product'
UNION ALL SELECT 'semi_formula_version', COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='semi_formula_version'
UNION ALL SELECT 'semi_formula_item', COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='semi_formula_item';

-- ② semi_formula_item 补列（当前报错缺 qimai_product_code，一并补全其余新列）
ALTER TABLE semi_formula_item ADD COLUMN item_id BIGINT DEFAULT NULL COMMENT 'xinfo 配方行ID' AFTER semi_id;
ALTER TABLE semi_formula_item ADD COLUMN item_type VARCHAR(20) NOT NULL COMMENT 'MATERIAL原料 / SEMI_FINISHED半成品' AFTER item_id;
ALTER TABLE semi_formula_item ADD COLUMN material_id VARCHAR(50) DEFAULT NULL COMMENT '原料ID（cm 开头）' AFTER item_type;
ALTER TABLE semi_formula_item ADD COLUMN qimai_product_code VARCHAR(50) DEFAULT NULL COMMENT '企迈物料编码（WP 开头；爆炸目标匹配优先此列↔qm_code）' AFTER material_id;
ALTER TABLE semi_formula_item ADD COLUMN semi_finished_product_id VARCHAR(50) DEFAULT NULL COMMENT '半成品ID（itemType=SEMI_FINISHED）' AFTER qimai_product_code;
ALTER TABLE semi_formula_item ADD COLUMN item_name VARCHAR(200) DEFAULT NULL COMMENT '用料名称' AFTER semi_finished_product_id;
ALTER TABLE semi_formula_item ADD COLUMN quantity DECIMAL(18,4) DEFAULT NULL COMMENT '用量（配方净出量对应的用量）' AFTER item_name;
ALTER TABLE semi_formula_item ADD COLUMN unit VARCHAR(50) DEFAULT NULL COMMENT '用量单位' AFTER quantity;
ALTER TABLE semi_formula_item ADD COLUMN loss_rate DECIMAL(6,4) DEFAULT NULL COMMENT '损耗率（0.05=5%）' AFTER unit;
ALTER TABLE semi_formula_item ADD COLUMN sort_order INT DEFAULT NULL AFTER loss_rate;
ALTER TABLE semi_formula_item ADD COLUMN synced_at DATETIME DEFAULT NULL AFTER sort_order;

-- ③ semi_formula_version 补列
ALTER TABLE semi_formula_version ADD COLUMN version_name VARCHAR(50) DEFAULT NULL COMMENT '版本名（v1/v2…）' AFTER semi_id;
ALTER TABLE semi_formula_version ADD COLUMN status VARCHAR(20) DEFAULT NULL COMMENT 'DRAFT/ACTIVE/DISABLED' AFTER version_name;
ALTER TABLE semi_formula_version ADD COLUMN total_cost DECIMAL(12,4) DEFAULT NULL COMMENT '版本总成本' AFTER status;
ALTER TABLE semi_formula_version ADD COLUMN synced_at DATETIME DEFAULT NULL AFTER total_cost;

-- ④ semi_product 补列（yield_rate 已单独修复；其余新列一并核对）
ALTER TABLE semi_product ADD COLUMN specification VARCHAR(100) DEFAULT '' COMMENT '规格' AFTER name;
ALTER TABLE semi_product ADD COLUMN net_output_quantity DECIMAL(18,4) DEFAULT NULL COMMENT '生效配方净出量' AFTER unit;
ALTER TABLE semi_product ADD COLUMN net_output_unit VARCHAR(20) DEFAULT NULL COMMENT '净出量单位（g/kg）' AFTER net_output_quantity;
ALTER TABLE semi_product ADD COLUMN cost DECIMAL(12,4) DEFAULT NULL COMMENT '生效配方总成本' AFTER yield_rate;
ALTER TABLE semi_product ADD COLUMN status VARCHAR(20) DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED' AFTER cost;
ALTER TABLE semi_product ADD COLUMN synced_at DATETIME DEFAULT NULL AFTER status;

-- ⑤ 最终核对：三表列数应等于最新建表脚本定义
SELECT TABLE_NAME, COUNT(*) AS col_count FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('semi_product','semi_formula_version','semi_formula_item')
GROUP BY TABLE_NAME;
