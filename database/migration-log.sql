-- ============================================================
-- 盘点工具 1.0 — 操作日志和登录日志表
-- ============================================================

USE store_inventory;

-- 操作日志
CREATE TABLE IF NOT EXISTS operation_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT        DEFAULT NULL COMMENT '操作人ID',
    username     VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '操作人标识（storeId/openid/admin）',
    module       VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '模块：物料/模板/任务',
    operation    VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '操作：新增/编辑/删除/启停/提交',
    description  VARCHAR(500)  DEFAULT '' COMMENT '操作描述',
    request_ip   VARCHAR(50)   DEFAULT '' COMMENT '请求IP',
    status       TINYINT       NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    error_msg    VARCHAR(1000) DEFAULT '' COMMENT '失败时的错误信息',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_username (username),
    INDEX idx_module (module),
    INDEX idx_created_at (created_at)
) COMMENT '操作日志';

-- 登录日志
CREATE TABLE IF NOT EXISTS login_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT        DEFAULT NULL COMMENT '用户ID',
    username     VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '用户名/微信昵称',
    login_type   VARCHAR(30)   NOT NULL DEFAULT '' COMMENT '登录类型：login/logout/bind_store',
    status       TINYINT       NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    fail_reason  VARCHAR(200)  DEFAULT '' COMMENT '失败原因',
    request_ip   VARCHAR(50)   DEFAULT '' COMMENT '请求IP',
    user_agent   VARCHAR(500)  DEFAULT '' COMMENT '客户端UA',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_username (username),
    INDEX idx_login_type (login_type),
    INDEX idx_created_at (created_at)
) COMMENT '登录日志';
