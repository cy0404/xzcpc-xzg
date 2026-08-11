-- ============================================================
-- task_zone_material 表确保有 task_id 索引
-- 若 migration-add-indexes.sql 已执行则跳过，否则创建
-- ============================================================

-- 先检查索引是否存在
-- SELECT * FROM information_schema.statistics WHERE table_schema = 'store_inventory' AND table_name = 'task_zone_material' AND index_name = 'idx_tzm_task_id';

-- 不存在则创建（忽略已存在的错误）
CREATE INDEX IF NOT EXISTS idx_tzm_task_id ON task_zone_material(task_id);

SELECT 'migration-add-tzm-task-index.sql executed successfully' AS status;
