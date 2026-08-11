-- ============================================================
-- 重置 6 月 not_started 任务：删除旧任务 → 重建
-- 目标库：store_inventory
-- 日期：2026-06-30
-- ============================================================
-- 注意：因外键约束，删除顺序必须是 子表 → 父表
--       task_zone_material → task_zone → task
--       MyBatis-Plus @TableLogic 用 UPDATE del_flag=1，
--       这里直接硬删，效果相同但需手动处理 FK 顺序。
-- ============================================================

USE store_inventory;

-- 先查一遍受影响范围
SELECT t.id, t.store_name, t.task_name, t.status
FROM task t
WHERE t.task_month = '2026-06' AND t.status = 'not_started' AND t.del_flag = 0;

-- ============================================================
-- 执行删除（按 FK 顺序）
-- ============================================================

-- 1. 删除分区物料快照
DELETE FROM task_zone_material
WHERE task_id IN (
    SELECT id FROM (
        SELECT t.id FROM task t
        WHERE t.task_month = '2026-06' AND t.status = 'not_started' AND t.del_flag = 0
    ) AS tmp
);

-- 2. 删除分区快照
DELETE FROM task_zone
WHERE task_id IN (
    SELECT id FROM (
        SELECT t.id FROM task t
        WHERE t.task_month = '2026-06' AND t.status = 'not_started' AND t.del_flag = 0
    ) AS tmp
);

-- 3. 删除任务
DELETE FROM task
WHERE id IN (
    SELECT id FROM (
        SELECT t.id FROM task t
        WHERE t.task_month = '2026-06' AND t.status = 'not_started' AND t.del_flag = 0
    ) AS tmp
);

-- ============================================================
-- 验证
-- ============================================================
-- 确认 6 月已无 not_started 任务
SELECT COUNT(*) AS '剩余 not_started' FROM task
WHERE task_month = '2026-06' AND status = 'not_started' AND del_flag = 0;
