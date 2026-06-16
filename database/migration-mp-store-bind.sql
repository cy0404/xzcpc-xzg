-- 盘点工具 1.0 — 小程序端「门店绑定登录」迁移脚本
-- 变更：登录不再绑定手机号，改为登录后选择门店并绑定 store_id

USE store_Inventory;

-- 给 store_manager_session 增加 store_id / store_name 字段
ALTER TABLE store_manager_session
    ADD COLUMN store_id    VARCHAR(50)  DEFAULT NULL COMMENT '已绑定的门店ID' AFTER session_key,
    ADD COLUMN store_name  VARCHAR(200) DEFAULT NULL COMMENT '已绑定的门店名称（冗余便于展示）' AFTER store_id,
    ADD INDEX idx_store_id (store_id);
