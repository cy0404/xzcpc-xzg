-- ============================================================
-- 盘点工具 1.0 — 统一主键 + 业务编码迁移
-- 改造后：每表 id INT AUTO_INCREMENT PK + biz_code VARCHAR(50) + 旧主键保留
-- ⚠️ 执行前必须全库备份！
-- ============================================================

USE store_Inventory;

-- ============ 阶段 1：先切所有引用旧主键的 FK ============

-- 1a. store_zone_material.material_id → material.material_id
--     material.material_id 从 INT PK 变为普通 INT 列，FK 需切到 material.id
ALTER TABLE store_zone_material ADD COLUMN new_material_id INT DEFAULT NULL AFTER material_id;
ALTER TABLE store_zone_material DROP FOREIGN KEY store_zone_material_ibfk_1;

-- 1b. template_zone_material.zone_id → template_zone.zone_id
ALTER TABLE template_zone_material ADD COLUMN new_zone_id INT DEFAULT NULL AFTER zone_id;
ALTER TABLE template_zone_material DROP FOREIGN KEY template_zone_material_ibfk_1;
ALTER TABLE template_zone_material DROP KEY uk_zone_material;

-- 1c. template_zone.template_id → template.template_id
ALTER TABLE template_zone ADD COLUMN new_template_id INT DEFAULT NULL AFTER template_id;
ALTER TABLE template_zone DROP FOREIGN KEY template_zone_ibfk_1;

-- 1d. task_zone.task_id → task.task_id
ALTER TABLE task_zone ADD COLUMN new_task_id INT DEFAULT NULL AFTER task_id;
ALTER TABLE task_zone DROP FOREIGN KEY task_zone_ibfk_1;

-- 1e. task_zone_material 两个 FK
ALTER TABLE task_zone_material ADD COLUMN new_task_id INT DEFAULT NULL AFTER task_id;
ALTER TABLE task_zone_material ADD COLUMN new_task_zone_id INT DEFAULT NULL AFTER task_zone_id;
ALTER TABLE task_zone_material DROP FOREIGN KEY task_zone_material_ibfk_1;
ALTER TABLE task_zone_material DROP FOREIGN KEY task_zone_material_ibfk_2;
ALTER TABLE task_zone_material DROP KEY uk_task_zone_material;

-- 1f. task_material_summary.task_id → task.task_id
ALTER TABLE task_material_summary ADD COLUMN new_task_id INT DEFAULT NULL AFTER task_id;
ALTER TABLE task_material_summary DROP FOREIGN KEY task_material_summary_ibfk_1;
ALTER TABLE task_material_summary DROP KEY uk_task_material;

-- ============ 阶段 2：给父表加 id + biz_code ============

-- 2a. material
ALTER TABLE material DROP PRIMARY KEY, MODIFY material_id INT NOT NULL COMMENT '物料ID（旧主键）';
ALTER TABLE material ADD COLUMN biz_code VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码' AFTER material_id;
ALTER TABLE material ADD COLUMN id INT AUTO_INCREMENT PRIMARY KEY FIRST;
UPDATE material SET biz_code = CONCAT('MAT', LPAD(id, 8, '0'));

-- 2b. template
ALTER TABLE template DROP PRIMARY KEY, MODIFY template_id INT NOT NULL COMMENT '模板ID（旧主键）';
ALTER TABLE template ADD COLUMN biz_code VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码' AFTER template_id;
ALTER TABLE template ADD COLUMN id INT AUTO_INCREMENT PRIMARY KEY FIRST;
UPDATE template SET biz_code = CONCAT('TPL', LPAD(id, 8, '0'));

-- 2c. task
ALTER TABLE task DROP PRIMARY KEY, MODIFY task_id INT NOT NULL COMMENT '任务ID（旧主键）';
ALTER TABLE task ADD COLUMN biz_code VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码' AFTER task_id;
ALTER TABLE task ADD COLUMN id INT AUTO_INCREMENT PRIMARY KEY FIRST;
UPDATE task SET biz_code = CONCAT('TASK', LPAD(id, 8, '0'));

-- ============ 阶段 3：给子表加 id + biz_code，切 FK 到父表新 id ============

-- 3a. template_zone
ALTER TABLE template_zone DROP PRIMARY KEY, MODIFY zone_id INT NOT NULL COMMENT '分区ID（旧主键）';
ALTER TABLE template_zone ADD COLUMN biz_code VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码' AFTER zone_id;
ALTER TABLE template_zone ADD COLUMN id INT AUTO_INCREMENT PRIMARY KEY FIRST;
UPDATE template_zone tz SET new_template_id = (SELECT id FROM template t WHERE t.template_id = tz.template_id);
ALTER TABLE template_zone DROP COLUMN template_id;
ALTER TABLE template_zone CHANGE new_template_id template_id INT NOT NULL COMMENT '关联模板ID';
ALTER TABLE template_zone ADD FOREIGN KEY (template_id) REFERENCES template(id);
UPDATE template_zone SET biz_code = CONCAT('TZ', LPAD(id, 8, '0'));

-- 3b. template_zone_material
ALTER TABLE template_zone_material ADD COLUMN biz_code VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码' AFTER id;
UPDATE template_zone_material tzm SET new_zone_id = (SELECT id FROM template_zone tz WHERE tz.zone_id = tzm.zone_id);
ALTER TABLE template_zone_material DROP COLUMN zone_id;
ALTER TABLE template_zone_material CHANGE new_zone_id zone_id INT NOT NULL COMMENT '关联分区ID';
ALTER TABLE template_zone_material ADD FOREIGN KEY (zone_id) REFERENCES template_zone(id);
ALTER TABLE template_zone_material ADD UNIQUE KEY uk_zone_material (zone_id, material_id);
UPDATE template_zone_material SET biz_code = CONCAT('TZM', LPAD(id, 8, '0'));

-- 3c. store_zone_material
ALTER TABLE store_zone_material ADD COLUMN biz_code VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码' AFTER id;
UPDATE store_zone_material szm SET new_material_id = (SELECT id FROM material m WHERE m.material_id = szm.material_id);
ALTER TABLE store_zone_material DROP COLUMN material_id;
ALTER TABLE store_zone_material CHANGE new_material_id material_id INT NOT NULL COMMENT '关联物料ID';
ALTER TABLE store_zone_material ADD FOREIGN KEY (material_id) REFERENCES material(id);
ALTER TABLE store_zone_material DROP KEY uk_store_zone_material;
ALTER TABLE store_zone_material ADD UNIQUE KEY uk_store_zone_material (store_id, zone_name, material_id);
UPDATE store_zone_material SET biz_code = CONCAT('SZM', LPAD(id, 8, '0'));

-- 3d. task_zone
ALTER TABLE task_zone DROP PRIMARY KEY, MODIFY task_zone_id INT NOT NULL COMMENT '分区ID（旧主键）';
ALTER TABLE task_zone ADD COLUMN biz_code VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码' AFTER task_zone_id;
ALTER TABLE task_zone ADD COLUMN id INT AUTO_INCREMENT PRIMARY KEY FIRST;
UPDATE task_zone tz SET new_task_id = (SELECT id FROM task t WHERE t.task_id = tz.task_id);
ALTER TABLE task_zone DROP COLUMN task_id;
ALTER TABLE task_zone CHANGE new_task_id task_id INT NOT NULL COMMENT '关联任务ID';
ALTER TABLE task_zone ADD FOREIGN KEY (task_id) REFERENCES task(id);
UPDATE task_zone SET biz_code = CONCAT('TKZ', LPAD(id, 8, '0'));

-- 3e. task_zone_material
ALTER TABLE task_zone_material DROP PRIMARY KEY, MODIFY task_zone_material_id INT NOT NULL COMMENT '快照ID（旧主键）';
ALTER TABLE task_zone_material ADD COLUMN biz_code VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码' AFTER task_zone_material_id;
ALTER TABLE task_zone_material ADD COLUMN id INT AUTO_INCREMENT PRIMARY KEY FIRST;
UPDATE task_zone_material tzm SET new_task_id = (SELECT id FROM task WHERE task_id = tzm.task_id);
UPDATE task_zone_material tzm SET new_task_zone_id = (SELECT id FROM task_zone WHERE task_zone_id = tzm.task_zone_id);
ALTER TABLE task_zone_material DROP COLUMN task_id;
ALTER TABLE task_zone_material DROP COLUMN task_zone_id;
ALTER TABLE task_zone_material CHANGE new_task_id task_id INT NOT NULL COMMENT '关联任务ID';
ALTER TABLE task_zone_material CHANGE new_task_zone_id task_zone_id INT NOT NULL COMMENT '关联分区ID';
ALTER TABLE task_zone_material ADD FOREIGN KEY (task_id) REFERENCES task(id);
ALTER TABLE task_zone_material ADD FOREIGN KEY (task_zone_id) REFERENCES task_zone(id);
ALTER TABLE task_zone_material ADD UNIQUE KEY uk_task_zone_material (task_zone_id, material_id);
UPDATE task_zone_material SET biz_code = CONCAT('TZM', LPAD(id, 8, '0'));

-- 3f. task_material_summary
ALTER TABLE task_material_summary ADD COLUMN biz_code VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码' AFTER id;
UPDATE task_material_summary tms SET new_task_id = (SELECT id FROM task WHERE task_id = tms.task_id);
ALTER TABLE task_material_summary DROP COLUMN task_id;
ALTER TABLE task_material_summary CHANGE new_task_id task_id INT NOT NULL COMMENT '关联任务ID';
ALTER TABLE task_material_summary ADD FOREIGN KEY (task_id) REFERENCES task(id);
ALTER TABLE task_material_summary ADD UNIQUE KEY uk_task_material (task_id, material_id);
UPDATE task_material_summary SET biz_code = CONCAT('TMS', LPAD(id, 8, '0'));

-- 3g. store_manager_session
ALTER TABLE store_manager_session ADD COLUMN biz_code VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务编码' AFTER id;
UPDATE store_manager_session SET biz_code = CONCAT('SMS', LPAD(id, 8, '0'));

-- ============================================================
-- 验证
-- ============================================================
SELECT 'migration done' AS status;
