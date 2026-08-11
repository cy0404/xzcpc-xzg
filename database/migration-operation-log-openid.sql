-- 迁移：operation_log 表增加 openid 字段
ALTER TABLE operation_log ADD COLUMN openid VARCHAR(128) DEFAULT NULL COMMENT '微信 openid' AFTER user_id;
