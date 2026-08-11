-- 调货单增加交接方式
ALTER TABLE transfer_order ADD COLUMN handoff VARCHAR(30) DEFAULT '门店自取' COMMENT '交接方式：门店自取|第三方物流';

-- 调货明细增加单价
ALTER TABLE transfer_order_item ADD COLUMN unit_price DECIMAL(10,2) DEFAULT NULL COMMENT '单价（快照）';
