-- ============================================================
-- 评价管理：issue_feedback 增加渠道字段（为接入美团/小红书等平台差评预留）
-- 执行方式：手动执行（项目约定数据库变更不自动执行）
-- 生效说明：channel 默认 'scan'（现有扫码反馈数据自动归为扫码渠道），
--           未来接入平台差评时按平台写入 meituan / xiaohongshu 等
-- ============================================================

ALTER TABLE issue_feedback
    ADD COLUMN channel VARCHAR(32) NOT NULL DEFAULT 'scan'
    COMMENT '渠道：scan=扫码反馈；meituan=美团；xiaohongshu=小红书（未来接入）'
    AFTER feedback_type;
