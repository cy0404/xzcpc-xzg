-- 盘点工具 1.0 — 小程序端增量迁移脚本
-- 适用于已经按 schema.sql 初始化过、且有业务数据的环境
-- 全新环境请直接跑 schema.sql

USE store_Inventory;

-- 1. task_zone 表加分区保存标记（店长点过「保存本分区」后置 1）
ALTER TABLE task_zone
    ADD COLUMN zone_saved TINYINT NOT NULL DEFAULT 0 COMMENT '0未保存 1已保存（店长点过保存本分区）';

-- 3. 小程序店长会话表
CREATE TABLE IF NOT EXISTS store_manager_session (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    openid          VARCHAR(100)  NOT NULL COMMENT '微信 openid',
    unionid         VARCHAR(100)  DEFAULT NULL COMMENT '微信 unionid（可选）',
    session_key     VARCHAR(200)  DEFAULT NULL COMMENT '微信 session_key（用于解密手机号）',
    wx_nickname     VARCHAR(100)  DEFAULT NULL COMMENT '微信昵称',
    manager_phone   VARCHAR(20)   DEFAULT NULL COMMENT '已绑定的店长手机号',
    token           VARCHAR(500)  DEFAULT NULL COMMENT '当前有效的 JWT',
    last_login_at   DATETIME      DEFAULT NULL COMMENT '最近登录时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_openid (openid),
    INDEX idx_manager_phone (manager_phone)
) COMMENT '小程序店长会话';

-- 4. 移除已废弃的 manager_phone 字段（登录不再校验手机号）
DROP PROCEDURE IF EXISTS drop_col_if_exists;
DELIMITER $$
CREATE PROCEDURE drop_col_if_exists()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'store_manager_session'
          AND COLUMN_NAME = 'manager_phone'
    ) THEN
        ALTER TABLE store_manager_session DROP COLUMN manager_phone;
    END IF;
END$$
DELIMITER ;
CALL drop_col_if_exists();
DROP PROCEDURE drop_col_if_exists;
