-- 迁移：operation_log 表 user_id 改为 VARCHAR，存 openid
ALTER TABLE operation_log MODIFY COLUMN user_id VARCHAR(128) DEFAULT NULL COMMENT '操作人ID（小程序端为微信 openid，总部端为飞书 open_id）';
