-- ============================================================
-- B1 问题处理：store_info 增加外部问题表单系统门店标识
-- chat_id 用于拼接外部上报页 URL：store-issue-form.html?chat_id=<chat_id>
-- 本地手工维护，不随外部门店同步覆盖
-- ============================================================

ALTER TABLE store_info
    ADD COLUMN chat_id VARCHAR(128) DEFAULT NULL COMMENT '外部问题表单系统门店标识（chat_id），用于上报页URL映射' AFTER owner_openid;

SELECT 'migration-add-store-chat-id.sql executed successfully' AS status;
