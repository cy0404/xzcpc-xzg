-- ============================================================
-- 智能订货（2026-09 PG 失真期改造）：smart_order_item 增加
--   店长意图对照字段（系统建议 vs 店长近期每次订货量，偏差>30% 提示确认）
-- 设计：plan/2026-09-smart-order-source-plan.md 第 3.1 节改动 F
-- 交互：店长确认时以店长修改为准（感觉纠正系统）；未修改则按系统建议下单（系统兜住感觉）
-- 执行：mysql -uxxxx -p xzcpc_db < migration-add-smart-order-needs-review.sql
-- ============================================================

ALTER TABLE smart_order_item
  ADD COLUMN order_ref_qty DECIMAL(12,4) DEFAULT NULL COMMENT '店长近期单次订货参考量(订货单位, 近90天订货节奏日均×订货周期折算)',
  ADD COLUMN needs_review INT DEFAULT 0 COMMENT '系统建议与店长近期订货偏差>30%标记 1=需店长核对确认';

-- sys_config：PG 销量预测源开关（失真期默认 0=关闭，只用库存差/订货节奏；企迈修复验收达标后置 1 切回）
INSERT INTO sys_config (config_key, config_value, description)
VALUES ('smart_order_use_pg', '0', '智能订货 PG 销量预测源开关 0=失真期关闭(只用库存差/订货节奏) 1=启用(PG 参与互证)')
ON DUPLICATE KEY UPDATE config_key = config_key;
