-- ============================================================
-- 迁移：未验收问题提醒卡片发送日志表
-- 用途：每天 9:00 定时任务发送「未验收问题提醒」卡片后记录 message_id，
--       供 GET /api/public/issue/reminder-read?date=xxx 查询已读回执
--       （飞书 GET /im/v1/messages/{message_id}/read_users，仅机器人自身 7 天内消息有效）
-- 执行方式：手动在 xzcpc_db 执行
-- ============================================================

CREATE TABLE IF NOT EXISTS issue_reminder_send_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    send_date   DATE         NOT NULL COMMENT '发送日期（yyyy-MM-dd）',
    chat_id     VARCHAR(128) NOT NULL COMMENT '门店飞书群 chat_id',
    store_name  VARCHAR(64)  DEFAULT NULL COMMENT '门店名称（取该群第一条问题的门店）',
    message_id  VARCHAR(64)  NOT NULL COMMENT '飞书消息 message_id（查已读回执用）',
    issue_count INT          DEFAULT 0 COMMENT '卡片内问题条数',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    KEY idx_send_date (send_date),
    KEY idx_message_id (message_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '未验收问题提醒卡片发送记录（用于查询已读回执）';
