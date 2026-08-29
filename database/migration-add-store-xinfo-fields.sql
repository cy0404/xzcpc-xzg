-- ============================================================
-- store_info 增加小象（xinfo）基础门店字段
-- 数据来源：新门店接口 GET /api/external/stores（X-API-Key 认证）
-- 说明：不动现有 store_name（企迈同步来源），小象名称单独存新列
-- ============================================================

ALTER TABLE store_info
    ADD COLUMN xinfo_store_name VARCHAR(200) DEFAULT NULL
    COMMENT '小象基础门店名称（xinfo接口name）' AFTER store_name;

ALTER TABLE store_info
    ADD COLUMN province VARCHAR(50) DEFAULT NULL
    COMMENT '省（xinfo接口province）' AFTER xinfo_store_name;

ALTER TABLE store_info
    ADD COLUMN city VARCHAR(50) DEFAULT NULL
    COMMENT '地市（xinfo接口city）' AFTER province;

ALTER TABLE store_info
    ADD COLUMN district VARCHAR(50) DEFAULT NULL
    COMMENT '县/区（xinfo接口district）' AFTER city;

ALTER TABLE store_info
    ADD COLUMN address VARCHAR(255) DEFAULT NULL
    COMMENT '详细地址（xinfo接口address）' AFTER district;

-- 校验
SHOW COLUMNS FROM store_info WHERE Field IN ('xinfo_store_name', 'province', 'city', 'district', 'address');
