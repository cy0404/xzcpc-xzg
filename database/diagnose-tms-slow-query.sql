-- ============================================================
-- task_material_summary 慢查询诊断脚本（全只读，可放心在生产库执行）
-- 对应慢 SQL：SELECT ... FROM task_material_summary
--            WHERE del_flag=0 AND task_id IN (?,?,... 约100个)
-- 来源：/api/reports/inventory-result、/api/reports/qimai-inventory
--       或差异批量计算 DifferenceCalcServiceImpl
-- 适用：GUI 工具（Navicat/DBeaver）逐段选中执行；mysql 命令行直接 source 亦可
-- ============================================================

USE store_inventory;  -- 按实际库名调整

-- ① 表结构和索引：确认 uk_task_material(task_id, material_id) 是否存在
SHOW CREATE TABLE task_material_summary;
SHOW INDEX FROM task_material_summary;

-- ② 数据量与分布：总行数 / 逻辑删除行数 / 任务数 / 每任务平均物料数
SELECT
    COUNT(*)                        AS total_rows,
    SUM(del_flag = 1)               AS deleted_rows,
    COUNT(DISTINCT task_id)         AS distinct_tasks,
    ROUND(COUNT(*) / NULLIF(COUNT(DISTINCT task_id), 0), 1) AS avg_mats_per_task
FROM task_material_summary;

-- ③a 取 20 个已提交任务的 id 作为样本
--    （慢日志里是 ~100 个，20 个已足够看出走不走索引）
SELECT GROUP_CONCAT(id) AS sample_task_ids
FROM (SELECT id FROM task WHERE status = 'submitted' ORDER BY id DESC LIMIT 20) t;

-- ③b 把 ③a 的结果粘到下面的 IN (...) 里执行
--    关键看 type（应为 range/ref）和 key（应为 uk_task_material 或 idx_tms_task_del_cover）
--    若 type=ALL 说明没走索引或统计信息失真
EXPLAIN
SELECT id, task_id, material_id, material_name, spec, base_unit,
       total_qty, original_qty, adjusted_qty, zone_count, unit_breakdown, del_flag
FROM task_material_summary
WHERE del_flag = 0 AND task_id IN (/* 粘贴 sample_task_ids */);

-- ④ 统计信息刷新（可选：仅更新优化器统计，不锁表、不改数据）
--    若 ③b 显示 type=ALL 而索引明明存在，执行下面这句后重跑 ③b
-- ANALYZE TABLE task_material_summary;

-- ⑤ 慢查询阈值与服务器状态
SHOW VARIABLES LIKE 'long_query_time';
SHOW VARIABLES LIKE 'innodb_buffer_pool_size';

-- ⑥ 是否有 DDL / 长事务正在阻塞（慢日志 Lock_time > 0 时重点看）
SELECT ID, USER, HOST, DB, COMMAND, TIME, STATE, LEFT(INFO, 200) AS SQL_TEXT
FROM information_schema.PROCESSLIST
WHERE COMMAND != 'Sleep'
ORDER BY TIME DESC;
