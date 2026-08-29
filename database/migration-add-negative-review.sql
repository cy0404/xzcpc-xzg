-- ============================================================
-- 门店差评记录表
-- 外部系统每天推送差评数据，内部督导跟进处理
-- ============================================================

CREATE TABLE IF NOT EXISTS store_negative_review (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    external_id     VARCHAR(100)  NOT NULL COMMENT '外部系统唯一ID，用于去重（对方推送的唯一id）',
    store_id        VARCHAR(50)   NOT NULL COMMENT '门店ID',
    store_name      VARCHAR(200)  DEFAULT NULL COMMENT '门店名称（冗余快照）',
    review_platform VARCHAR(50)   NOT NULL COMMENT '评价平台（美团/大众点评/饿了么等）',
    review_score    DECIMAL(3,1)  DEFAULT NULL COMMENT '评价分数',
    review_date     DATE          NOT NULL COMMENT '评价日期',
    review_content  TEXT          DEFAULT NULL COMMENT '评价内容',
    supervisor_name VARCHAR(50)   DEFAULT NULL COMMENT '督导姓名（内部指派）',
    is_processed    TINYINT(1)    DEFAULT 0 COMMENT '是否处理：0=未处理 1=已处理',
    process_note    VARCHAR(1000) DEFAULT NULL COMMENT '处理说明',
    process_media   TEXT          DEFAULT NULL COMMENT '处理图片/视频URL（逗号分隔或JSON数组）',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记',
    version         INT           DEFAULT 0 COMMENT '乐观锁',
    UNIQUE INDEX idx_external_id (external_id),
    INDEX idx_store (store_id),
    INDEX idx_review_date (review_date),
    INDEX idx_platform (review_platform),
    INDEX idx_processed (is_processed),
    INDEX idx_supervisor (supervisor_name)
) COMMENT '门店差评记录' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SELECT 'migration-add-negative-review.sql executed successfully' AS status;
