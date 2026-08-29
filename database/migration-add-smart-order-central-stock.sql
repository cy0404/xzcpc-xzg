-- ============================================================
-- 智能订货：总仓实时库存比对（9.2.13 warehouse-product/list）
-- 详情页展示每个物料的总仓可用库存，下单数量不得超过可用库存。
-- 总仓 6 个（对照企迈控制台「仓库管理」，已逐个实测 OpenAPI 验证有效）：
--   PSCK000023 新螺蛳湾总仓 / PSCK000070 鲜奶仓 / PSCK000071 水果仓
--   PSCK000149 冷冻仓 / PSCK000191 水果仓2 / PSCK000214 水果总仓
-- 注意：warehouseNoList 单次最多 5 个仓，代码已按 5 个一批循环查询并合并，
--       多仓可用库存按品项累加。
-- 执行：mysql -uxxxx -p xzcpc_db < migration-add-smart-order-central-stock.sql
-- ============================================================

INSERT INTO sys_config (config_key, config_value, description)
VALUES ('smart_order_central_warehouse_no', 'PSCK000023,PSCK000070,PSCK000071,PSCK000149,PSCK000191,PSCK000214', '智能订货总仓仓库编码（企迈 warehouseNo，逗号分隔，9.2.13 实时库存查询用；为空则跳过库存比对）')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), description = VALUES(description);
