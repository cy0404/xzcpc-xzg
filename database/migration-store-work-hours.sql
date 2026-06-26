-- 门店月度工时录入
CREATE TABLE IF NOT EXISTS store_work_hours (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id     VARCHAR(32)   NOT NULL COMMENT '业务ID',
    store_id      VARCHAR(50)   NOT NULL COMMENT '门店ID',
    store_name    VARCHAR(200)  DEFAULT NULL COMMENT '门店名称',
    `record_time`  VARCHAR(7)    NOT NULL COMMENT '年月 YYYY-MM',
    hours         DECIMAL(10,2) NOT NULL COMMENT '工时（小时）',
    employee_id   VARCHAR(32)   NOT NULL COMMENT '录入人ID',
    employee_name VARCHAR(100)  NOT NULL COMMENT '录入人姓名',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '录入时间',
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag      INT           DEFAULT 0 COMMENT '删除标记',
    INDEX idx_store_id (store_id),
    INDEX idx_record_time (`record_time`),
    INDEX idx_store_month (store_id, `record_time`)
) COMMENT '门店月度工时';
