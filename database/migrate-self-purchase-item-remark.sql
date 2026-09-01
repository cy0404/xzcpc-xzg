-- =============================================================
-- 自购食材明细级「说明」：self_purchase_material_item 加 remark 列
-- 需求：支出说明从类型组级下放到每个物料明细项（H5 表单）
-- 执行：测试环境 / 生产环境 各执行一次（幂等保护）
-- =============================================================

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'self_purchase_material_item' AND COLUMN_NAME = 'remark'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE self_purchase_material_item ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT ''物料说明''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
