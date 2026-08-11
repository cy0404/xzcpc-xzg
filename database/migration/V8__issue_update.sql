-- ============================================================
-- V8: issue 表补全字段 + 状态/紧急程度调整
-- ============================================================

USE store_inventory;

-- 1. 新增缺失列（已有则跳过）
ALTER TABLE issue ADD COLUMN IF NOT EXISTS sub_type VARCHAR(50) DEFAULT NULL COMMENT '子类型（如设备问题下的制冰机/净水器）' AFTER issue_type;
ALTER TABLE issue ADD COLUMN IF NOT EXISTS process_result VARCHAR(1000) DEFAULT NULL COMMENT '处理结果/最新进度（象目经理同步回来）' AFTER status;
ALTER TABLE issue ADD COLUMN IF NOT EXISTS acceptance_remark VARCHAR(500) DEFAULT NULL COMMENT '门店验收备注' AFTER process_result;
ALTER TABLE issue ADD COLUMN IF NOT EXISTS accepted_by VARCHAR(100) DEFAULT NULL COMMENT '验收人 openid' AFTER acceptance_remark;
ALTER TABLE issue ADD COLUMN IF NOT EXISTS accepted_at DATETIME DEFAULT NULL COMMENT '验收时间' AFTER accepted_by;

-- 2. 缺失索引
ALTER TABLE issue ADD INDEX IF NOT EXISTS idx_urgency (urgency);
ALTER TABLE issue ADD INDEX IF NOT EXISTS idx_biz_code (biz_code);

-- 3. 修改字段类型/注释
ALTER TABLE issue MODIFY urgency VARCHAR(50) NOT NULL COMMENT '紧急程度（MD原文）：严重-影响营业或有客诉|一般-影响效率和体验|轻微-期望优化';
ALTER TABLE issue MODIFY description VARCHAR(2000) DEFAULT '' COMMENT '问题描述';
ALTER TABLE issue MODIFY contact_name VARCHAR(50) DEFAULT '' COMMENT '联系人';
ALTER TABLE issue MODIFY contact_phone VARCHAR(20) DEFAULT '' COMMENT '联系电话';
ALTER TABLE issue MODIFY status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS|pending|processing|pending_acceptance|resolved|closed';

SELECT 'V8__issue_update.sql executed successfully' AS status;
