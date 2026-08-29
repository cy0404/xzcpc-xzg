-- ============================================================
-- 智能订货 P2-B：smart_order_item 增加 自购食材 修正字段
-- 门店自行采购的水果/食材不进企迈订货体系，但销量预测的需求含其满足部分，
-- 故按近4周自购周均从需求中扣除（减项），避免重复订货。
-- 执行：mysql -uxxxx -p xzcpc_db < migration-add-smart-order-self-purchase.sql
-- ============================================================

ALTER TABLE smart_order_item
  ADD COLUMN self_purchase_qty DECIMAL(12,4) DEFAULT NULL COMMENT '自购食材修正(基础单位, 近4周自购周均, 减项)';
