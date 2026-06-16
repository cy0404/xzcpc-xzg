-- 支持多角色：role 字段从单值改为逗号分隔多值
ALTER TABLE admin_permission
    MODIFY COLUMN role VARCHAR(500) NOT NULL DEFAULT 'normal_user'
    COMMENT '角色，逗号分隔，如 headquarters_admin,finance_admin';
