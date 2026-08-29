-- ============================================================
-- 督导-门店关系对接新门店接口（xinfo）支持
-- 1. admin_permission 增加 user_id（飞书企业稳定ID，跨应用一致，作为外部接口对账键）
-- 2. supervisor_store_access 增加 source 标记（auto=接口同步 / manual=手工维护）
-- 3. sys_config 预置新接口地址，机器码需人工填入
-- ============================================================

-- 1) admin_permission 加 user_id
ALTER TABLE admin_permission
    ADD COLUMN user_id VARCHAR(64) NULL
    COMMENT '飞书企业稳定ID(工号)，跨应用一致，外部接口对账键' AFTER open_id;

ALTER TABLE admin_permission
    ADD UNIQUE INDEX uk_admin_user_id (user_id);

-- 2) supervisor_store_access 加 source 标记
--    自动同步只重建 source='auto' 的行；手工维护行（如督导领导→全部门店）永不覆盖
ALTER TABLE supervisor_store_access
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'manual'
    COMMENT 'auto=接口同步 manual=手工维护' AFTER store_name;

-- 3) sys_config 预置新门店接口配置（机器码需人工填入 supervisor_api_key）
INSERT INTO sys_config (config_key, config_value, description) VALUES
('supervisor_api_url', 'http://162.14.122.80:18088/api/external/stores', '督导关系同步-新门店接口地址'),
('supervisor_api_key', '', '督导关系同步-X-API-Key机器码(需填入)')
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- 4) 校验：查看当前 admin_permission 的 user_id 回填情况（初始化脚本执行前应为空）
SELECT id, open_id, name, user_id FROM admin_permission WHERE del_flag = 0;
