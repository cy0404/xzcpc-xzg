-- ============================================================
-- 智能订货拆单（每周一盘 × 双订货日分批发货）——plan/2026-09-smart-order-split-plan.md 改动 A/G
-- 执行库：store_inventory（正式）/ store_inventory_test（测试）均需执行（在对应库下执行即可，脚本无 USE）
-- ============================================================

-- 1) 建议单增加订货日/批次
--    order_day: 批1=门店第一个订货日（order_days 首值）；批2=order_days 第二值（试点店 '3,7' → 周日 7），
--               单值店无第二值才按 +4 推导 (orderDay+3)%7+1
--    存量单据（W34 等旧数据）两列为 NULL，不影响新逻辑（幂等按 task_id 过滤，旧行 task_id 为空）
ALTER TABLE smart_order
  ADD COLUMN order_day INT DEFAULT NULL COMMENT '订货日(1-7,1=周一): 批1=首订货日 批2=推导+4' AFTER week_label;

ALTER TABLE smart_order
  ADD COLUMN batch_no INT DEFAULT NULL COMMENT '批次 1首批(实盘库存) 2次批(估算库存)' AFTER order_day;

-- 2) 幂等约束放开：同店同周可两张单（批1 周三 / 批2 周日），以 order_day 区分
--    （旧 uk_store_week (store,week) 与 uk_store_task (store,task) 都会挡住批2，需删除）
ALTER TABLE smart_order DROP INDEX uk_store_week;
ALTER TABLE smart_order DROP INDEX uk_store_task;
ALTER TABLE smart_order
  ADD UNIQUE KEY uk_store_week_batch (store_id, week_start_date, order_day);

-- 3) 安全天数 3 → 1（拆单后配送快 + 补货间隔 3~4 天；代码层已支持，纯配置）
UPDATE sys_config SET config_value = '1', description = '智能订货安全天数(拆单后1天)'
WHERE config_key = 'smart_order_safety_days';

-- 4) 校验（执行后应看到 uk_store_week_batch 与两列）
-- SHOW INDEX FROM smart_order;
-- SELECT order_day, batch_no, COUNT(*) FROM smart_order GROUP BY order_day, batch_no;
-- SELECT config_key, config_value FROM sys_config WHERE config_key = 'smart_order_safety_days';
