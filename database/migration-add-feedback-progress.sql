-- ============================================================
-- 扫码问题反馈 — 顾客查处理进度 + 门店联系
-- 给 issue_feedback 加字段：
--   phone         手机号全号（提交必填，查询进度的凭证，门店/后台可联系顾客回访）
--   status        处理状态：pending待处理 / processing处理中 / done已处理 / closed已关闭
--   process_note  处理说明（后台填写，顾客可见）
--   processing_at 开始处理时间（时间线用）
--   processed_at  处理完成时间（时间线用）
-- 顾客查询：输入完整手机号，列出该手机号全部反馈（无需选门店），点开看详情/进度；
-- 后台列表/详情展示全号，供门店联系回访。
-- 说明：历史数据没有手机号，phone 为 NULL，查不了进度（可接受）；
--       老记录 status 自动归为 pending。
-- 执行方式：手动在 xzcpc_db 执行
-- ============================================================

ALTER TABLE issue_feedback
    ADD COLUMN phone         VARCHAR(11)  DEFAULT NULL COMMENT '手机号全号（提交必填，查询进度凭证，门店联系回访用；历史数据为空）' AFTER images,
    ADD COLUMN status        VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '处理状态：pending待处理/processing处理中/done已处理/closed已关闭' AFTER phone,
    ADD COLUMN process_note  VARCHAR(500) DEFAULT NULL COMMENT '处理说明（后台填写，顾客可查）' AFTER status,
    ADD COLUMN processing_at DATETIME     DEFAULT NULL COMMENT '开始处理时间' AFTER process_note,
    ADD COLUMN processed_at  DATETIME     DEFAULT NULL COMMENT '处理完成时间' AFTER processing_at,
    ADD KEY idx_phone (phone);

SELECT 'migration-add-feedback-progress.sql executed successfully' AS status;
