-- ============================================================
-- 生产库 store_info 备份（apply 前执行）
-- apply 会：更新 169 家 supervisor_name + xinfo 字段、插入 8 家新店
-- 用法：在生产库（store_inventory）执行
-- ============================================================

-- 1) 建备份表（结构 + 数据）
DROP TABLE IF EXISTS store_info_bak_20260826;
CREATE TABLE store_info_bak_20260826 AS
SELECT * FROM store_info;

-- 2) 校验备份条数（应与原表一致）
SELECT COUNT(*) AS bak_count FROM store_info_bak_20260826;

-- ============================================================
-- 如需要恢复（apply 后回滚用）：
-- DELETE FROM store_info;
-- INSERT INTO store_info SELECT * FROM store_info_bak_20260826;
-- ============================================================
