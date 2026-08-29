-- ============================================================
-- store_info 增加 warehouse_id 列
-- 原因：生产数据库缺少 warehouse_id 列，导致总部端报错
--       Unknown column 'warehouse_id' in 'field list'（GET /api/stores）
-- 执行环境：生产库 store_inventory（162.14.122.80:3306）
-- ============================================================

ALTER TABLE store_info ADD COLUMN warehouse_id VARCHAR(50) DEFAULT NULL COMMENT '企迈控制台仓库ID（本地维护，用于查询入库单）' AFTER qmai_store_id;
