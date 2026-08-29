-- ============================================================
-- 智能订货 P2-B：smart_order_item 增加 5 个预测因子字段
-- 生成引擎升级（PG 同期销量 + 趋势系数 + 损耗/调货/在途修正）后落表，
-- 供店长查看"建议怎么来的"、总部追溯审计。
-- 执行：mysql -uxxxx -p xzcpc_db < migration-add-smart-order-factors.sql
-- ============================================================

ALTER TABLE smart_order_item
  ADD COLUMN last_year_qty  DECIMAL(12,4) DEFAULT NULL COMMENT '去年同周销量快照(基础单位)',
  ADD COLUMN trend_factor   DECIMAL(6,3)  DEFAULT NULL COMMENT '趋势系数(近4周÷去年同4周, clamp 0.5~2.0)',
  ADD COLUMN loss_qty       DECIMAL(12,4) DEFAULT NULL COMMENT '损耗修正(基础单位, 近4周报损周均)',
  ADD COLUMN transfer_qty   DECIMAL(12,4) DEFAULT NULL COMMENT '调货净值修正(基础单位, 近4周周均, 净调出为正)',
  ADD COLUMN in_transit_qty DECIMAL(12,4) DEFAULT NULL COMMENT '在途量(基础单位, 累计订货-累计到货差值法)';
