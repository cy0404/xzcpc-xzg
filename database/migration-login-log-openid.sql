-- 迁移：login_log 表增加 openid 字段
ALTER TABLE login_log ADD COLUMN openid VARCHAR(128) DEFAULT NULL COMMENT '微信 openid' AFTER user_id;
