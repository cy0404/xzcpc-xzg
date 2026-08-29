-- ============================================================
-- 客诉处理功能：小程序店长/老板处理客诉（凭证仅内部可见）
-- 前置依赖：先执行 migration-add-feedback-progress.sql（phone/status/process_note 等字段）
-- 说明：process_note 处理说明改为仅内部可见（H5 查询页不再展示）；
--       evidence 处理凭证（图片/视频 URL，逗号分隔）仅店长/老板小程序与 admin 后台可见；
--       processed_by / processed_name 记录处理人。
-- ============================================================

ALTER TABLE issue_feedback
    ADD COLUMN evidence       VARCHAR(1000) DEFAULT NULL COMMENT '处理凭证URL（逗号分隔，仅内部可见，顾客不可见）' AFTER processed_at,
    ADD COLUMN processed_by   VARCHAR(64)   DEFAULT NULL COMMENT '处理人openid（小程序店长/老板）' AFTER evidence,
    ADD COLUMN processed_name VARCHAR(32)   DEFAULT NULL COMMENT '处理人姓名' AFTER processed_by;

SELECT 'migration-add-complaint-evidence.sql executed successfully' AS status;
