-- ============================================================
-- 扫码问题反馈 — 数据库迁移
-- 新增表：issue_feedback（顾客扫码反馈记录）
-- 用途：微信扫码打开 H5 表单页收集门店反馈（类型单选 + 门店 + 内容 + 图片），
--       数据入 xzcpc_db，总部后台查看/导出。
-- 反馈类型选项存 sys_config.feedback_type_options（逗号分隔），改选项只需 UPDATE 无需改代码。
-- 执行方式：手动在 xzcpc_db 执行
-- ============================================================

CREATE TABLE IF NOT EXISTS issue_feedback (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    feedback_type VARCHAR(32)  NOT NULL COMMENT '反馈类型（选项见 sys_config.feedback_type_options）',
    store_id      VARCHAR(64)  DEFAULT NULL COMMENT '门店ID（store_info.id）',
    store_name    VARCHAR(128) DEFAULT NULL COMMENT '门店名称',
    content       TEXT         NOT NULL COMMENT '反馈内容',
    images        TEXT         DEFAULT NULL COMMENT '图片URL，逗号分隔，最多3张',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag      INT          DEFAULT 0 COMMENT '删除标记',
    version       INT          DEFAULT 0 COMMENT '乐观锁',
    KEY idx_store (store_id),
    KEY idx_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '扫码问题反馈记录';

-- 反馈类型默认选项（可配置：改这个值即改 H5 表单的单选选项）
INSERT INTO sys_config (config_key, config_value, description)
VALUES ('feedback_type_options', '门店服务,饮品品质,其他', '扫码反馈类型选项（逗号分隔）')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

SELECT 'migration-add-feedback-record.sql executed successfully' AS status;
