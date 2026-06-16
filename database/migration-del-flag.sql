-- 盘点工具 1.0 — 逻辑删除迁移脚本
-- 全部表增加 del_flag 字段（0=正常，1=已删除）

USE store_Inventory;

DROP PROCEDURE IF EXISTS add_del_flag_if_not_exists;
DELIMITER $$
CREATE PROCEDURE add_del_flag_if_not_exists(IN tbl VARCHAR(100))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = tbl
          AND COLUMN_NAME = 'del_flag'
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', tbl, ' ADD COLUMN del_flag INT DEFAULT 0 COMMENT ''删除标记 0正常 1删除''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_del_flag_if_not_exists('template');
CALL add_del_flag_if_not_exists('template_zone');
CALL add_del_flag_if_not_exists('template_zone_material');
CALL add_del_flag_if_not_exists('task');
CALL add_del_flag_if_not_exists('task_zone');
CALL add_del_flag_if_not_exists('task_zone_material');
CALL add_del_flag_if_not_exists('task_material_summary');
CALL add_del_flag_if_not_exists('store_manager_session');

DROP PROCEDURE add_del_flag_if_not_exists;
