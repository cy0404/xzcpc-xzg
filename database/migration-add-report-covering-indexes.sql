-- ============================================================
-- 报表接口慢查询优化 — 覆盖索引
-- 背景：/api/reports/inventory-result?pageSize=100 时，
--       SELECT ... FROM task_material_summary WHERE del_flag=0 AND task_id IN (约100个)
--       每行都要回表取 del_flag/total_qty 等列，随机 IO 多导致进慢查询日志
-- 方案：加覆盖索引 + 代码侧收窄查询列，让这两条查询变成纯索引扫描，不回表
-- 目标库：store_inventory
-- 日期：2026-08-17
-- 说明：一次性执行即可；DROP 部分用过程做了存在性判断，若 CREATE 报"索引已存在"说明已执行过，忽略即可
--       ADD/DROP INDEX 均为 INPLACE 在线操作，不阻塞业务读写，建议低峰期执行
-- ============================================================

USE store_inventory;

-- 通用：索引存在才删除
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

-- 1. 任务汇总表：覆盖 report 接口的汇总查询（task_id + del_flag + 代码所需列）
CREATE INDEX idx_tms_task_del_cover ON task_material_summary (task_id, del_flag, material_id, total_qty, adjusted_qty);

-- 2. 任务分区物料表：覆盖快照单位/单价查询（task_id + del_flag + 代码所需列）
--    新索引以 task_id 开头，完全替代旧的 idx_tzm_task_del（2026-06-30 慢SQL修复所建）
--    和测试库的 idx_tzm_task_id，删除避免重复维护
CREATE INDEX idx_tzm_task_del_cover ON task_zone_material (task_id, del_flag, material_id, base_unit_snapshot, unit_price_snapshot);
CALL drop_index_if_exists('task_zone_material', 'idx_tzm_task_del');
CALL drop_index_if_exists('task_zone_material', 'idx_tzm_task_id');

-- 3. task 表：支撑改写后的步骤3查询（status + task_month + store_id IN）
CREATE INDEX idx_task_status_month_store ON task (status, task_month, store_id);

DROP PROCEDURE drop_index_if_exists;

-- 4. 刷新优化器统计信息（表频繁批量增删后统计易失真，导致走全表扫描）
ANALYZE TABLE task_material_summary;
ANALYZE TABLE task_zone_material;
ANALYZE TABLE task;
