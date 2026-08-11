-- ============================================================
-- 自购物料采购表
-- 门店自行采购的水果+食材物料记录
-- 日期：2026-07-01
-- ============================================================

USE store_inventory;

CREATE TABLE self_purchase_material (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY       COMMENT '主键',
    biz_code         VARCHAR(50)   DEFAULT NULL              COMMENT '业务ID，如 SPM_xxx',
    store_id         VARCHAR(50)   NOT NULL                  COMMENT '门店ID，关联 store_info.store_id',
    store_name       VARCHAR(200)  DEFAULT NULL              COMMENT '门店名称快照',
    parent_category  VARCHAR(100)  NOT NULL                  COMMENT '父级分类，自由文本',
    category         VARCHAR(100)  NOT NULL                  COMMENT '分类，自由文本',
    material_code    VARCHAR(100)  DEFAULT NULL              COMMENT '物料编码，自由文本',
    material_name    VARCHAR(200)  NOT NULL                  COMMENT '物料名称，自由文本',
    unit             VARCHAR(20)   NOT NULL DEFAULT ''       COMMENT '最小单位，如斤/个/包/瓶',
    purchase_month   VARCHAR(7)    NOT NULL                  COMMENT '采购月份，如 2026-07',
    purchase_qty     DECIMAL(10,2) NOT NULL DEFAULT 0        COMMENT '采购数量',
    received_qty     DECIMAL(10,2) NOT NULL DEFAULT 0        COMMENT '领取数量（实际收到）',
    unit_price       DECIMAL(10,2) DEFAULT NULL              COMMENT '单价',
    total_amount     DECIMAL(10,2) DEFAULT NULL              COMMENT '合计',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag         INT           DEFAULT 0                 COMMENT '删除标记 0正常 1删除',
    UNIQUE KEY uk_biz_code (biz_code),
    INDEX idx_store_id (store_id),
    INDEX idx_store_month (store_id, purchase_month),
    INDEX idx_parent_category (parent_category),
    INDEX idx_category (category),
    INDEX idx_purchase_month (purchase_month)
) COMMENT '自购物料采购表，门店自行采购的水果+食材物料记录';
