-- self_purchase_material 表变更
-- 1. 新增字段
ALTER TABLE self_purchase_material ADD COLUMN store_miniapp_no VARCHAR(100) DEFAULT NULL COMMENT '小程序ID';
ALTER TABLE self_purchase_material ADD COLUMN material_id VARCHAR(50) DEFAULT NULL COMMENT '物料ID';
ALTER TABLE self_purchase_material ADD COLUMN purchase_date DATE DEFAULT NULL COMMENT '采购日期';
ALTER TABLE self_purchase_material ADD COLUMN handler_name VARCHAR(50) DEFAULT NULL COMMENT '经手人';
ALTER TABLE self_purchase_material ADD COLUMN voucher_url VARCHAR(500) DEFAULT NULL COMMENT '凭证图片URL';
ALTER TABLE self_purchase_material ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT '说明';
-- 2. 删除 received_qty 字段
ALTER TABLE self_purchase_material DROP COLUMN received_qty;
