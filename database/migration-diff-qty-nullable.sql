-- ============================================================
-- 差异表 diff_qty 改为可空：消耗为0的盘点物料也生成差异行（可在明细页
-- 修改数量、查看录入明细），但差异值不计算 —— diff_qty 置 NULL 表示
-- "未计算差异"，前端显示 "--"，不参与大差异/物料聚合统计。
-- 执行方式：手动执行（项目约定数据库变更不自动执行）
--          mysql -uxzcpc -p store_inventory < database/migration-diff-qty-nullable.sql
-- ============================================================

-- ① 先确认当前列定义（若精度与此处不同，按实际精度改 NULL 属性）
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_difference'
  AND COLUMN_NAME IN ('diff_qty', 'diff_rate');

-- ② 改为可空（diff_rate 原定义 decimal(12,6) 已可空，无需改）
ALTER TABLE inventory_difference
  MODIFY COLUMN diff_qty DECIMAL(12,4) NULL COMMENT '差异数量（NULL=未计算差异，如消耗为0的物料）';

-- ③ 核对
SHOW COLUMNS FROM inventory_difference WHERE Field IN ('diff_qty', 'diff_rate');
