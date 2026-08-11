-- 财报隐藏配置表：存在记录即隐藏该门店/月份的财报
CREATE TABLE report_visibility_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id    VARCHAR(50) NOT NULL COMMENT '门店ID（store_info.store_id）',
    stat_month  VARCHAR(7)  NOT NULL COMMENT '月份 yyyy-MM',
    created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_store_month (store_id, stat_month)
) COMMENT '财报隐藏配置（有记录=隐藏）';
