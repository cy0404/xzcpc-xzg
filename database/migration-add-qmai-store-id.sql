-- ============================================================
-- V??: store_info 增加企迈门店ID字段
-- 原因：生产数据库缺少 qmai_store_id 列，导致总部端报错
--       Unknown column 'qmai_store_id' in 'field list'
-- ============================================================

ALTER TABLE store_info ADD COLUMN qmai_store_id BIGINT DEFAULT NULL COMMENT '企迈门店ID' AFTER cangkuid;

-- 注意：数据填充需要运行根目录 tmp_qmai_store_sync.sql 中的 UPDATE 语句
