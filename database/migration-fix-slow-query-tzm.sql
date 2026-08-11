-- ============================================================
-- 慢 SQL 修复：task_zone_material / task_zone 复合索引优化
-- 目标库：store_inventory
-- 日期：2026-06-30
-- ============================================================
-- 问题：WHERE del_flag=0 AND task_id = ? 耗时 2060ms
-- 原因：单列索引 (task_id) 无法覆盖 del_flag 过滤，
--       MySQL 需逐行回表检查 del_flag，数据量大时退化严重。
-- 修复：替换为复合索引 (task_id, del_flag)，一条索引搞定
--       @TableLogic 自动 + taskId 等值查询，index dive 直达。
-- ============================================================

USE store_inventory;

-- 通用：如果索引存在则删除（兼容 MySQL 8.0.29 以下版本）
DROP PROCEDURE IF EXISTS drop_index_if_exists;
DELIMITER $$
CREATE PROCEDURE drop_index_if_exists(IN tbl VARCHAR(100), IN idx VARCHAR(100))
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = tbl
          AND INDEX_NAME = idx
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', tbl, ' DROP INDEX ', idx);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- 1. task_zone_material：删旧单列索引 → 建复合索引
CALL drop_index_if_exists('task_zone_material', 'idx_tzm_task_id');
CREATE INDEX idx_tzm_task_del ON task_zone_material(task_id, del_flag);

-- 2. task_zone：同样模式一并优化
CALL drop_index_if_exists('task_zone', 'idx_tz_task_id');
CREATE INDEX idx_tz_task_del ON task_zone(task_id, del_flag);

DROP PROCEDURE drop_index_if_exists;

-- ============================================================
-- 验证：执行以下 EXPLAIN，确认 type=ref, key=新索引名
-- ============================================================
-- EXPLAIN SELECT * FROM task_zone_material WHERE del_flag=0 AND task_id = 1;
-- EXPLAIN SELECT * FROM task_zone WHERE del_flag=0 AND task_id = 1;
