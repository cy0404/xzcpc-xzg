-- ============================================================
-- 盘点工具 1.0 — 生产库迁移脚本
-- ⚠️ 已备份后再执行！
-- ============================================================

USE store_Inventory;

-- ============================================================
-- 阶段 1：所有表加 del_flag / biz_code / version
-- ============================================================
DROP PROCEDURE IF EXISTS add_col_if_missing;
DELIMITER $$
CREATE PROCEDURE add_col_if_missing(IN tbl VARCHAR(100), IN col VARCHAR(100), IN def VARCHAR(200))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=tbl AND COLUMN_NAME=col) THEN
        SET @s = CONCAT('ALTER TABLE ',tbl,' ADD COLUMN ',col,' ',def);
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
END$$
DELIMITER ;

CALL add_col_if_missing('template',              'del_flag', "INT DEFAULT 0 COMMENT '删除标记'");
CALL add_col_if_missing('template_zone',         'del_flag', "INT DEFAULT 0 COMMENT '删除标记'");
CALL add_col_if_missing('template_zone_material','del_flag', "INT DEFAULT 0 COMMENT '删除标记'");
CALL add_col_if_missing('task',                  'del_flag', "INT DEFAULT 0 COMMENT '删除标记'");
CALL add_col_if_missing('task_zone',             'del_flag', "INT DEFAULT 0 COMMENT '删除标记'");
CALL add_col_if_missing('task_zone_material',    'del_flag', "INT DEFAULT 0 COMMENT '删除标记'");
CALL add_col_if_missing('task_material_summary', 'del_flag', "INT DEFAULT 0 COMMENT '删除标记'");
CALL add_col_if_missing('store_manager_session', 'del_flag', "INT DEFAULT 0 COMMENT '删除标记'");

CALL add_col_if_missing('template',              'biz_code', "VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码'");
CALL add_col_if_missing('template_zone',         'biz_code', "VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码'");
CALL add_col_if_missing('template_zone_material','biz_code', "VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码'");
CALL add_col_if_missing('task',                  'biz_code', "VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码'");
CALL add_col_if_missing('task_zone',             'biz_code', "VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码'");
CALL add_col_if_missing('task_zone_material',    'biz_code', "VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码'");
CALL add_col_if_missing('task_material_summary', 'biz_code', "VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码'");
CALL add_col_if_missing('store_manager_session', 'biz_code', "VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码'");

CALL add_col_if_missing('template',              'version', "INT DEFAULT 0 COMMENT '乐观锁'");
CALL add_col_if_missing('template_zone',         'version', "INT DEFAULT 0 COMMENT '乐观锁'");
CALL add_col_if_missing('template_zone_material','version', "INT DEFAULT 0 COMMENT '乐观锁'");
CALL add_col_if_missing('task',                  'version', "INT DEFAULT 0 COMMENT '乐观锁'");
CALL add_col_if_missing('task_zone',             'version', "INT DEFAULT 0 COMMENT '乐观锁'");
CALL add_col_if_missing('task_zone_material',    'version', "INT DEFAULT 0 COMMENT '乐观锁'");
CALL add_col_if_missing('task_material_summary', 'version', "INT DEFAULT 0 COMMENT '乐观锁'");

DROP PROCEDURE add_col_if_missing;

-- ============================================================
-- 阶段 2：先删所有 FK（避免修改父表时被锁）
-- ============================================================
ALTER TABLE template_zone           DROP FOREIGN KEY template_zone_ibfk_1;
ALTER TABLE template_zone_material  DROP FOREIGN KEY template_zone_material_ibfk_1;
ALTER TABLE task_zone               DROP FOREIGN KEY task_zone_ibfk_1;
ALTER TABLE task_zone_material      DROP FOREIGN KEY task_zone_material_ibfk_1;
ALTER TABLE task_zone_material      DROP FOREIGN KEY task_zone_material_ibfk_2;
ALTER TABLE task_material_summary   DROP FOREIGN KEY task_material_summary_ibfk_1;

-- ============================================================
-- 阶段 3：无 id 的表加 id PK（FK 已删，不会被阻塞）
-- ============================================================

-- 3a. template
ALTER TABLE template MODIFY template_id INT NOT NULL COMMENT '模板ID（旧主键）';
ALTER TABLE template ADD COLUMN id INT AUTO_INCREMENT UNIQUE FIRST;
UPDATE template SET biz_code = CONCAT('TPL', LPAD(id,8,'0')) WHERE biz_code = '';
ALTER TABLE template DROP PRIMARY KEY, ADD PRIMARY KEY (id);

-- 3b. template_zone
ALTER TABLE template_zone MODIFY zone_id INT NOT NULL COMMENT '分区ID（旧主键）';
ALTER TABLE template_zone ADD COLUMN id INT AUTO_INCREMENT UNIQUE FIRST;
UPDATE template_zone SET biz_code = CONCAT('TZ', LPAD(id,8,'0')) WHERE biz_code = '';
ALTER TABLE template_zone DROP PRIMARY KEY, ADD PRIMARY KEY (id);

-- 3c. task
ALTER TABLE task MODIFY task_id INT NOT NULL COMMENT '任务ID（旧主键）';
ALTER TABLE task ADD COLUMN id INT AUTO_INCREMENT UNIQUE FIRST;
UPDATE task SET biz_code = CONCAT('TASK', LPAD(id,8,'0')) WHERE biz_code = '';
ALTER TABLE task DROP PRIMARY KEY, ADD PRIMARY KEY (id);

-- 3d. task_zone
ALTER TABLE task_zone MODIFY task_zone_id INT NOT NULL COMMENT '分区ID（旧主键）';
ALTER TABLE task_zone ADD COLUMN id INT AUTO_INCREMENT UNIQUE FIRST;
UPDATE task_zone SET biz_code = CONCAT('TKZ', LPAD(id,8,'0')) WHERE biz_code = '';
ALTER TABLE task_zone DROP PRIMARY KEY, ADD PRIMARY KEY (id);

-- 3e. task_zone_material
ALTER TABLE task_zone_material MODIFY task_zone_material_id INT NOT NULL COMMENT '快照ID（旧主键）';
ALTER TABLE task_zone_material ADD COLUMN id INT AUTO_INCREMENT UNIQUE FIRST;
UPDATE task_zone_material SET biz_code = CONCAT('TZM', LPAD(id,8,'0')) WHERE biz_code = '';
ALTER TABLE task_zone_material DROP PRIMARY KEY, ADD PRIMARY KEY (id);

-- 已有 id 的表补 biz_code
UPDATE template_zone_material SET biz_code = CONCAT('TZM', LPAD(id,8,'0')) WHERE biz_code = '';
UPDATE task_material_summary  SET biz_code = CONCAT('TMS', LPAD(id,8,'0')) WHERE biz_code = '';
UPDATE store_manager_session  SET biz_code = CONCAT('SMS', LPAD(id,8,'0')) WHERE biz_code = '';

-- ============================================================
-- 阶段 4：切 FK 到新主键 id 并重建
-- ============================================================

-- 4a. template_zone: template_id → template.id
ALTER TABLE template_zone ADD COLUMN new_template_id INT AFTER template_id;
UPDATE template_zone tz SET new_template_id = (
    SELECT t.id FROM template t WHERE t.template_id = tz.template_id
);
ALTER TABLE template_zone DROP COLUMN template_id;
ALTER TABLE template_zone CHANGE new_template_id template_id INT NOT NULL COMMENT '关联模板ID';
ALTER TABLE template_zone ADD FOREIGN KEY (template_id) REFERENCES template(id);

-- 4b. template_zone_material: zone_id → template_zone.id
ALTER TABLE template_zone_material ADD COLUMN new_zone_id INT AFTER zone_id;
UPDATE template_zone_material tzm SET new_zone_id = (
    SELECT tz.id FROM template_zone tz WHERE tz.zone_id = tzm.zone_id
);
ALTER TABLE template_zone_material DROP KEY uk_zone_material;
ALTER TABLE template_zone_material DROP COLUMN zone_id;
ALTER TABLE template_zone_material CHANGE new_zone_id zone_id INT NOT NULL COMMENT '关联分区ID';
ALTER TABLE template_zone_material ADD FOREIGN KEY (zone_id) REFERENCES template_zone(id);
ALTER TABLE template_zone_material ADD UNIQUE KEY uk_zone_material (zone_id, material_id);

-- 4c. task_zone: task_id → task.id
ALTER TABLE task_zone ADD COLUMN new_task_id INT AFTER task_id;
UPDATE task_zone tz SET new_task_id = (
    SELECT t.id FROM task t WHERE t.task_id = tz.task_id
);
ALTER TABLE task_zone DROP COLUMN task_id;
ALTER TABLE task_zone CHANGE new_task_id task_id INT NOT NULL COMMENT '关联任务ID';
ALTER TABLE task_zone ADD FOREIGN KEY (task_id) REFERENCES task(id);

-- 4d. task_zone_material: task_id → task.id
ALTER TABLE task_zone_material ADD COLUMN new_task_id INT AFTER task_id;
UPDATE task_zone_material tzm SET new_task_id = (
    SELECT t.id FROM task t WHERE t.task_id = tzm.task_id
);
ALTER TABLE task_zone_material DROP COLUMN task_id;
ALTER TABLE task_zone_material CHANGE new_task_id task_id INT NOT NULL COMMENT '关联任务ID';
ALTER TABLE task_zone_material ADD FOREIGN KEY (task_id) REFERENCES task(id);

-- 4e. task_zone_material: task_zone_id → task_zone.id
ALTER TABLE task_zone_material ADD COLUMN new_task_zone_id INT AFTER task_zone_id;
UPDATE task_zone_material tzm SET new_task_zone_id = (
    SELECT tz.id FROM task_zone tz WHERE tz.task_zone_id = tzm.task_zone_id
);
ALTER TABLE task_zone_material DROP COLUMN task_zone_id;
ALTER TABLE task_zone_material CHANGE new_task_zone_id task_zone_id INT NOT NULL COMMENT '关联分区ID';
ALTER TABLE task_zone_material ADD FOREIGN KEY (task_zone_id) REFERENCES task_zone(id);
ALTER TABLE task_zone_material DROP KEY uk_task_zone_material;
ALTER TABLE task_zone_material ADD UNIQUE KEY uk_task_zone_material (task_zone_id, material_id);

-- 4f. task_material_summary: task_id → task.id
ALTER TABLE task_material_summary ADD COLUMN new_task_id INT AFTER task_id;
UPDATE task_material_summary tms SET new_task_id = (
    SELECT t.id FROM task t WHERE t.task_id = tms.task_id
);
ALTER TABLE task_material_summary DROP COLUMN task_id;
ALTER TABLE task_material_summary CHANGE new_task_id task_id INT NOT NULL COMMENT '关联任务ID';
ALTER TABLE task_material_summary ADD FOREIGN KEY (task_id) REFERENCES task(id);
ALTER TABLE task_material_summary DROP KEY uk_task_material;
ALTER TABLE task_material_summary ADD UNIQUE KEY uk_task_material (task_id, material_id);

-- ============================================================
-- 阶段 5：旧主键同步为 id（前端过渡兼容）
-- ============================================================
UPDATE template              SET template_id = id WHERE template_id IS NULL OR template_id = 0;
UPDATE template_zone         SET zone_id = id WHERE zone_id IS NULL OR zone_id = 0;
UPDATE task                  SET task_id = id WHERE task_id IS NULL OR task_id = 0;
UPDATE task_zone             SET task_zone_id = id WHERE task_zone_id IS NULL OR task_zone_id = 0;
UPDATE task_zone_material    SET task_zone_material_id = id WHERE task_zone_material_id IS NULL OR task_zone_material_id = 0;

-- ============================================================
-- 验证
-- ============================================================
SELECT 'Migration completed' AS status;
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_KEY
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'store_Inventory'
AND COLUMN_NAME IN ('id','biz_code','del_flag','version')
ORDER BY TABLE_NAME, ORDINAL_POSITION;
