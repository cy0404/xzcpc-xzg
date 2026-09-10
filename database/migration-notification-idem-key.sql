-- 订阅消息入队幂等键：防止双实例并发跑 job 时重复入队（先查后写在并发下无效）
-- 键格式：{eventType}:{storeId}:{openid}:{业务日期}（如 LOSS_APPROVAL_REMIND:xxx:openid:2026-09-10）
-- 执行：mysql -u<user> -p <db> < migration-notification-idem-key.sql

ALTER TABLE notification_log
    ADD COLUMN idem_key VARCHAR(191) DEFAULT NULL COMMENT '入队幂等键（同日同店同人同事件唯一，防并发重复入队）' AFTER source_id;

-- 唯一索引（NULL 不参与唯一约束，历史行与无幂等键的行不受影响）
ALTER TABLE notification_log
    ADD UNIQUE KEY uk_idem_key (idem_key);
