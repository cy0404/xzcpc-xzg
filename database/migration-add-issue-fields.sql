-- ============================================================
-- B1 问题处理：issue 表补列
-- 原型/PRD 需要但建表时缺失的字段：子类型、处理结果、门店验收
-- 状态新增取值 pending_acceptance（待验收），无需 DDL，仅注释更新
-- ============================================================

ALTER TABLE issue
    ADD COLUMN sub_type          VARCHAR(50)   DEFAULT NULL COMMENT '子类型（如设备问题下的制冰机/净水器）' AFTER issue_type,
    ADD COLUMN process_result    VARCHAR(1000) DEFAULT NULL COMMENT '处理结果/最新进度（象目经理同步回来）' AFTER status,
    ADD COLUMN acceptance_remark VARCHAR(500)  DEFAULT NULL COMMENT '门店验收备注' AFTER process_result,
    ADD COLUMN accepted_by       VARCHAR(100)  DEFAULT NULL COMMENT '验收人 openid' AFTER acceptance_remark,
    ADD COLUMN accepted_at       DATETIME      DEFAULT NULL COMMENT '验收时间' AFTER accepted_by;

-- status 取值扩展为：pending|processing|pending_acceptance|resolved|closed
ALTER TABLE issue
    MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'pending'
        COMMENT 'pending|processing|pending_acceptance|resolved|closed';

SELECT 'migration-add-issue-fields.sql executed successfully' AS status;
