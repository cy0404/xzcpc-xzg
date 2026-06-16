-- 门店信息本地存储表（替代 Spring Cache）
-- 数据来源：外部门店 API 定时同步 + qr_code 字段本地手动维护

CREATE TABLE IF NOT EXISTS store_info (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    store_id VARCHAR(64) NOT NULL COMMENT '外部 API 门店 ID',
    store_name VARCHAR(128) NOT NULL COMMENT '门店名称',
    store_code VARCHAR(64) DEFAULT NULL COMMENT '门店编码',
    xiaochengxuid VARCHAR(64) DEFAULT NULL COMMENT '小程序号',
    cangkuid VARCHAR(64) DEFAULT NULL COMMENT '仓库 ID（来自外部 API）',
    qr_code VARCHAR(512) DEFAULT NULL COMMENT '门店二维码（本地维护）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-正常 1-删除',
    UNIQUE KEY uk_store_id (store_id),
    KEY idx_store_name (store_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店信息本地表';
