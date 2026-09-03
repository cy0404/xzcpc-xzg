-- ============================================================
-- 订阅消息通知系统 迁移脚本（2026-09）
-- 执行前备份；脚本幂等可重复执行
-- ============================================================

-- ① 订阅消息授权额度表（用户授权 +1，发送 -1，无上限累积）
CREATE TABLE IF NOT EXISTS notification_quota (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid      VARCHAR(64)  NOT NULL COMMENT '微信 openid',
    quota       INT          NOT NULL DEFAULT 0 COMMENT '剩余订阅消息额度（授权+1 发送-1）',
    updated_at  DATETIME     DEFAULT NULL COMMENT '最近变动时间',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_openid (openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小程序订阅消息授权额度';

-- ② notification_log 增加"订阅消息点击跳转页"（该表复用为发送队列，加 page_path 列即可，无需新表）
ALTER TABLE notification_log
    ADD COLUMN page_path VARCHAR(255) DEFAULT NULL COMMENT '订阅消息点击跳转小程序页面（含参数）' AFTER content;

-- ③ issue 增加提交人 openid（场景5：状态推进到 待联系/待验收 时通知提交人；存量数据无 openid，历史单不通知）
ALTER TABLE issue
    ADD COLUMN submitter_openid VARCHAR(64) DEFAULT NULL COMMENT '问题提交人 openid（订阅消息通知用）';
