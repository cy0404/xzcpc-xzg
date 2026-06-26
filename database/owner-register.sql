-- 门店联系人信息表（总部录入，用于匹配老板身份）
CREATE TABLE IF NOT EXISTS store_contact (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id      VARCHAR(50)  NOT NULL COMMENT '门店ID',
    store_name    VARCHAR(200) NOT NULL COMMENT '门店名称',
    contact_name  VARCHAR(50)  NOT NULL COMMENT '联系人姓名',
    contact_phone VARCHAR(20)  NOT NULL COMMENT '联系人手机号',
    del_flag      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '0=正常 1=已删除',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_store_id (store_id),
    INDEX idx_phone (contact_phone)
) COMMENT '门店联系人信息';

-- 老板扫码登记表
CREATE TABLE IF NOT EXISTS owner_registration (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid        VARCHAR(128) NOT NULL COMMENT '微信openid',
    bind_code     VARCHAR(64)  DEFAULT NULL COMMENT '二维码绑定码',
    name          VARCHAR(50)  NOT NULL COMMENT '老板姓名',
    phone         VARCHAR(20)  NOT NULL COMMENT '手机号',
    role          VARCHAR(20)  DEFAULT NULL COMMENT '角色：店长/老板',
    store_id      VARCHAR(50)  NOT NULL COMMENT '门店ID',
    store_name    VARCHAR(200) NOT NULL COMMENT '门店名称',
    status        VARCHAR(20)  NOT NULL DEFAULT '未关联' COMMENT '已绑定/未关联',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_openid (openid),
    INDEX idx_store (store_id)
) COMMENT '老板扫码登记表';
