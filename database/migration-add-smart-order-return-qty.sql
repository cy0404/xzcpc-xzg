-- ============================================================
-- 智能订货 P2-B：smart_order_item 增加 还货 修正字段
-- 调货存在"还货"环节（transfer_return_record）：收货方将货品还给发货方（实物回流，反向调货）。
-- 仅处理 return_type='goods'（还货品）；return_type='money'（还钱）实物未动，不影响订货。
-- 执行：mysql -uxxxx -p xzcpc_db < migration-add-smart-order-return-qty.sql
-- ============================================================

ALTER TABLE smart_order_item
  ADD COLUMN return_qty DECIMAL(12,4) DEFAULT NULL COMMENT '还货净值修正(基础单位, 近4周周均, 仅还货品goods, 净还出为正; 还钱不影响实物不计)';
