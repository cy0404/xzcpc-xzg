-- ============================================================
-- 清理：旧版 V17 曾给 store_info 加 qmai_warehouse_no 列
-- ============================================================
-- 现状：企迈仓库编码统一用 store_info.cangkuid（外部 API 同步自动填），
--       该列已无任何代码引用，建议删除。
-- 用法：先跑第 1 步核对——有输出说明线上存在此列，继续执行 2/3 步；
--       无输出说明线上没有此列，后面不用跑（smart_order 两张表与
--       sys_config 5 条配置均为最终版，无需任何处理）。

-- 1. 核对列是否存在
SELECT COLUMN_NAME FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'store_info'
   AND COLUMN_NAME = 'qmai_warehouse_no';

-- 2. 若曾按旧版 tmp_smart_order_warehouse_no.sql 手工填过编码，
--    先迁入 cangkuid（仅 cangkuid 为空时迁移，外部同步数据优先）
UPDATE store_info SET cangkuid = qmai_warehouse_no
 WHERE (cangkuid IS NULL OR cangkuid = '')
   AND qmai_warehouse_no IS NOT NULL AND qmai_warehouse_no <> '';

-- 3. 删列
ALTER TABLE store_info DROP COLUMN qmai_warehouse_no;

-- 4. 复核（应无输出）：
-- SELECT COLUMN_NAME FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'store_info' AND COLUMN_NAME = 'qmai_warehouse_no';
