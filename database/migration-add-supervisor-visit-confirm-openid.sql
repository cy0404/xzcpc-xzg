-- ============================================================
-- supervisor_visit 增加确认人 openid 字段
-- 用于回填编辑时恢复选中的具体确认人
-- ============================================================
ALTER TABLE supervisor_visit
    ADD COLUMN confirm_manager_openid VARCHAR(100) DEFAULT NULL COMMENT '选中的店长确认人 openid' AFTER confirm_person_type,
    ADD COLUMN confirm_manager_name VARCHAR(100) DEFAULT NULL COMMENT '选中的店长确认人姓名' AFTER confirm_manager_openid,
    ADD COLUMN confirm_owner_openid VARCHAR(100) DEFAULT NULL COMMENT '选中的加盟商老板确认人 openid' AFTER confirm_manager_name,
    ADD COLUMN confirm_owner_name VARCHAR(100) DEFAULT NULL COMMENT '选中的加盟商老板确认人姓名' AFTER confirm_owner_openid;
