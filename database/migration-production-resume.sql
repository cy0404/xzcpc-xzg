-- 盘点工具 1.0 — 生产库迁移续跑（接上次中断处）
-- 已确认阶段 1/2/3 和 4a/4b/4e 部分完成
USE store_Inventory;

-- 检查当前状态
SELECT '当前主键' AS info;
SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='store_Inventory' AND COLUMN_KEY='PRI';

-- ============================================================
-- 处理 task_zone_material 的重复索引（上次 4e 部分成功导致 id 有双重索引）
-- ============================================================
-- 删除冗余索引 id（MySQL 自动为 UNIQUE 创建，PK 也有）
ALTER TABLE task_zone_material DROP INDEX id;

-- ============================================================
-- 阶段 4 续：未完成的 FK 切换
-- ============================================================

-- 4c. task_zone: 如果还有旧 task_id，切
ALTER TABLE task_zone ADD COLUMN new_task_id_b INT AFTER task_id;
UPDATE task_zone SET new_task_id_b = (SELECT t.id FROM task t WHERE t.task_id = task.task_id);
ALTER TABLE task_zone DROP COLUMN task_id;
ALTER TABLE task_zone CHANGE new_task_id_b task_id INT NOT NULL COMMENT '关联任务ID';
ALTER TABLE task_zone ADD FOREIGN KEY fk_task_zone_task (task_id) REFERENCES task(id);

-- 4d. task_zone_material: task_id 切
ALTER TABLE task_zone_material ADD COLUMN new_task_id_b INT AFTER task_id;
UPDATE task_zone_material SET new_task_id_b = (SELECT t.id FROM task t WHERE t.task_id = task_zone_material.task_id);
ALTER TABLE task_zone_material DROP INDEX task_id;
ALTER TABLE task_zone_material DROP COLUMN task_id;
ALTER TABLE task_zone_material CHANGE new_task_id_b task_id INT NOT NULL COMMENT '关联任务ID';
ALTER TABLE task_zone_material ADD FOREIGN KEY fk_tzm_task (task_id) REFERENCES task(id);

-- 4e. task_zone_material: task_zone_id 切（上回添加了 uk 但列可能未切）
UPDATE task_zone_material tzm
SET tzm.task_zone_id = (SELECT tz.id FROM task_zone tz WHERE tz.task_zone_id = tzm.task_zone_id)
WHERE EXISTS (SELECT 1 FROM task_zone tz WHERE tz.task_zone_id = tzm.task_zone_id AND tz.id != tzm.task_zone_id);

-- 重建 FK task_zone_id → task_zone.id（如果不存在）
ALTER TABLE task_zone_material ADD FOREIGN KEY IF NOT EXISTS fk_tzm_zone (task_zone_id) REFERENCES task_zone(id);

-- 4f. task_material_summary: task_id → task.id
ALTER TABLE task_material_summary ADD COLUMN new_task_id_b INT AFTER task_id;
UPDATE task_material_summary SET new_task_id_b = (SELECT t.id FROM task t WHERE t.task_id = task_material_summary.task_id);
ALTER TABLE task_material_summary DROP COLUMN task_id;
ALTER TABLE task_material_summary CHANGE new_task_id_b task_id INT NOT NULL COMMENT '关联任务ID';
ALTER TABLE task_material_summary ADD FOREIGN KEY fk_tms_task (task_id) REFERENCES task(id);

-- ============================================================
-- 阶段 5：旧主键同步为 id
-- ============================================================
UPDATE template              SET template_id = id WHERE template_id IS NULL OR template_id = 0;
UPDATE template_zone         SET zone_id = id WHERE zone_id IS NULL OR zone_id = 0;
UPDATE task                  SET task_id = id WHERE task_id IS NULL OR task_id = 0;
UPDATE task_zone             SET task_zone_id = id WHERE task_zone_id IS NULL OR task_zone_id = 0;
UPDATE task_zone_material    SET task_zone_material_id = id WHERE task_zone_material_id IS NULL OR task_zone_material_id = 0;

-- 验证
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_KEY
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='store_Inventory' AND COLUMN_NAME IN ('id','biz_code','del_flag','version')
ORDER BY TABLE_NAME, ORDINAL_POSITION;
