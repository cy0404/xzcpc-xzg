-- 盘点工具 1.0 — 小程序端「提交人微信昵称」迁移脚本
-- 变更：task.submitted_by 保存 openid，store_manager_session 保存微信昵称用于展示

USE store_Inventory;

DROP PROCEDURE IF EXISTS add_wx_nickname_if_not_exists;
DELIMITER $$
CREATE PROCEDURE add_wx_nickname_if_not_exists()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'store_manager_session'
          AND COLUMN_NAME = 'wx_nickname'
    ) THEN
        ALTER TABLE store_manager_session
            ADD COLUMN wx_nickname VARCHAR(100) DEFAULT NULL COMMENT '微信昵称' AFTER session_key;
    END IF;
END$$
DELIMITER ;
CALL add_wx_nickname_if_not_exists();
DROP PROCEDURE add_wx_nickname_if_not_exists;
