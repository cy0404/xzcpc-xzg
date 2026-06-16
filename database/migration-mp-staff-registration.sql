-- 迁移：小程序端门店员工登记与审批
-- 复用 people 模块 employee 表作为员工主数据，新增登记申请表承载员工填写和负责人审批流程

ALTER TABLE employee
    ADD COLUMN openid VARCHAR(64) DEFAULT NULL COMMENT '绑定微信 openid' AFTER mobile,
    ADD COLUMN emergency_contact_name VARCHAR(64) DEFAULT NULL COMMENT '紧急联系人姓名' AFTER leave_date,
    ADD COLUMN emergency_contact_phone VARCHAR(32) DEFAULT NULL COMMENT '紧急联系人电话' AFTER emergency_contact_name,
    ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT '备注' AFTER emergency_contact_phone,
    ADD INDEX idx_employee_openid (openid);

CREATE TABLE IF NOT EXISTS employee_registration_application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    application_id VARCHAR(32) NOT NULL COMMENT '登记申请业务ID',
    openid VARCHAR(64) DEFAULT NULL COMMENT '申请人微信 openid',
    store_id VARCHAR(32) NOT NULL COMMENT '申请门店ID',
    store_name VARCHAR(128) NOT NULL COMMENT '申请门店名称快照',
    name VARCHAR(64) NOT NULL COMMENT '姓名',
    mobile VARCHAR(32) NOT NULL COMMENT '手机号',
    gender VARCHAR(16) DEFAULT NULL COMMENT '性别',
    birthday DATE DEFAULT NULL COMMENT '出生日期',
    expected_role VARCHAR(64) NOT NULL COMMENT '期望岗位',
    employment_type VARCHAR(32) NOT NULL COMMENT '用工类型',
    entry_date DATE NOT NULL COMMENT '入职日期',
    emergency_contact_name VARCHAR(64) DEFAULT NULL COMMENT '紧急联系人姓名',
    emergency_contact_phone VARCHAR(32) DEFAULT NULL COMMENT '紧急联系人电话',
    remark VARCHAR(500) DEFAULT NULL COMMENT '补充说明',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending待审批 approved已通过 rejected已驳回',
    reject_reason VARCHAR(500) DEFAULT NULL COMMENT '驳回原因',
    approver_openid VARCHAR(64) DEFAULT NULL COMMENT '审批人 openid',
    approved_at DATETIME DEFAULT NULL COMMENT '审批时间',
    employee_id VARCHAR(32) DEFAULT NULL COMMENT '审批通过后生成的员工业务ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
    UNIQUE KEY uk_application_id (application_id),
    KEY idx_staff_app_store_status (store_id, status),
    KEY idx_staff_app_openid (openid),
    KEY idx_staff_app_mobile (mobile),
    KEY idx_staff_app_del_flag (del_flag)
) COMMENT='员工登记申请表';
