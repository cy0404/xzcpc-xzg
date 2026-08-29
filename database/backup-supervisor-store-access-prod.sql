-- ============================================================
-- 生产库 supervisor_store_access 备份（apply 前执行）
-- 用法：在生产库（store_inventory）执行
-- ============================================================

-- 1) 建备份表（结构 + 数据）
DROP TABLE IF EXISTS supervisor_store_access_bak_20260826;
CREATE TABLE supervisor_store_access_bak_20260826 AS
SELECT * FROM supervisor_store_access;

-- 2) 校验备份条数（应与原表一致：196）
SELECT COUNT(*) AS bak_count FROM supervisor_store_access_bak_20260826;

-- ============================================================
-- 如需要恢复（apply 后回滚用）：
-- DELETE FROM supervisor_store_access;
-- INSERT INTO supervisor_store_access
-- SELECT * FROM supervisor_store_access_bak_20260826;
-- ============================================================
