ALTER TABLE transfer_order_item ADD COLUMN base_unit VARCHAR(50) DEFAULT NULL COMMENT '最小盘点单位';
ALTER TABLE transfer_order_item ADD COLUMN base_qty DECIMAL(12,4) DEFAULT NULL COMMENT '最小单位数量';
ALTER TABLE transfer_order_item ADD COLUMN input_unit VARCHAR(50) DEFAULT NULL COMMENT '录入单位';
ALTER TABLE transfer_order_item ADD COLUMN input_qty DECIMAL(12,4) DEFAULT NULL COMMENT '录入数量';
