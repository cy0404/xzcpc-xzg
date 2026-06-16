-- 迁移：task 表增加门店快照字段
-- 创建任务时从外部门店 API 获取并固化，后续查询不再依赖外部 API

ALTER TABLE task
    ADD COLUMN store_name      VARCHAR(200) DEFAULT NULL COMMENT '门店名称快照' AFTER store_id,
    ADD COLUMN store_code      VARCHAR(50)  DEFAULT NULL COMMENT '门店编码快照' AFTER store_name,
    ADD COLUMN xiaochengxuid   VARCHAR(100) DEFAULT NULL COMMENT '小程序UID快照' AFTER store_code;

-- 存量数据回填：从 store_manager_session 表取已绑定的门店名称
-- 注意：两个表字符集可能不同，JOIN 时需统一 collation
UPDATE task t
    LEFT JOIN store_manager_session s
        ON t.store_id COLLATE utf8mb4_unicode_ci = s.store_id COLLATE utf8mb4_unicode_ci
        AND s.store_name IS NOT NULL
SET t.store_name = COALESCE(t.store_name, s.store_name)
WHERE t.store_name IS NULL;
