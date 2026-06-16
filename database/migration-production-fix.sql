-- 盘点工具 1.0 — FK 切换补齐脚本（幂等，可重复执行）
USE store_Inventory;

DROP PROCEDURE IF EXISTS exec_if_no_error;
DELIMITER $$
CREATE PROCEDURE exec_if_no_error(IN stmt TEXT)
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;
    SET @s = stmt;
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
END$$
DELIMITER ;

-- ============================================================
-- 更新 FK 值（幂等：old = new 则不变）
-- ============================================================
UPDATE IGNORE template_zone tz
SET tz.template_id = (SELECT t.id FROM template t WHERE t.template_id = tz.template_id);

UPDATE IGNORE template_zone_material tzm
SET tzm.zone_id = (SELECT tz.id FROM template_zone tz WHERE tz.zone_id = tzm.zone_id);

UPDATE IGNORE task_zone tz
SET tz.task_id = (SELECT t.id FROM task t WHERE t.task_id = tz.task_id);

UPDATE IGNORE task_zone_material tzm
SET tzm.task_id = (SELECT t.id FROM task t WHERE t.task_id = tzm.task_id);

UPDATE IGNORE task_zone_material tzm
SET tzm.task_zone_id = (SELECT tz.id FROM task_zone tz WHERE tz.task_zone_id = tzm.task_zone_id);

UPDATE IGNORE task_material_summary tms
SET tms.task_id = (SELECT t.id FROM task t WHERE t.task_id = tms.task_id);

-- ============================================================
-- 重建 FK（已存在则忽略报错）
-- ============================================================
CALL exec_if_no_error('ALTER TABLE template_zone ADD FOREIGN KEY (template_id) REFERENCES template(id)');
CALL exec_if_no_error('ALTER TABLE template_zone_material ADD FOREIGN KEY (zone_id) REFERENCES template_zone(id)');
CALL exec_if_no_error('ALTER TABLE task_zone ADD FOREIGN KEY (task_id) REFERENCES task(id)');
CALL exec_if_no_error('ALTER TABLE task_zone_material ADD FOREIGN KEY (task_id) REFERENCES task(id)');
CALL exec_if_no_error('ALTER TABLE task_zone_material ADD FOREIGN KEY (task_zone_id) REFERENCES task_zone(id)');
CALL exec_if_no_error('ALTER TABLE task_material_summary ADD FOREIGN KEY (task_id) REFERENCES task(id)');

-- ============================================================
-- 重建唯一键（已存在则忽略报错）
-- ============================================================
CALL exec_if_no_error('ALTER TABLE template_zone_material ADD UNIQUE KEY uk_zone_material (zone_id, material_id)');
CALL exec_if_no_error('ALTER TABLE task_zone_material ADD UNIQUE KEY uk_task_zone_material (task_zone_id, material_id)');
CALL exec_if_no_error('ALTER TABLE task_material_summary ADD UNIQUE KEY uk_task_material (task_id, material_id)');

-- ============================================================
-- 旧主键同步为 id
-- ============================================================
UPDATE template              SET template_id = id WHERE template_id = 0;
UPDATE template_zone         SET zone_id = id WHERE zone_id = 0;
UPDATE task                  SET task_id = id WHERE task_id = 0;
UPDATE task_zone             SET task_zone_id = id WHERE task_zone_id = 0;
UPDATE task_zone_material    SET task_zone_material_id = id WHERE task_zone_material_id = 0;

DROP PROCEDURE exec_if_no_error;

-- ============================================================
-- 验证
-- ============================================================
SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = 'store_Inventory'
ORDER BY TABLE_NAME;
