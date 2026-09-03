-- ============================================================
-- 智能订货：安全库存 3 天 → 2 天
-- 背景：店长反馈 3 天偏多（覆盖周期 = 配送周期 + 安全天数 = 7+3=10 天偏保守），
--       改为 2 天后覆盖周期 9 天，按需微降建议量。
-- 执行：mysql -uxxxx -p xzcpc_db < migration-smart-order-safety-2d.sql
-- 影响：仅 sys_config 一条配置；生成引擎与回测每次实时读取（代码默认值同为 3，
--       已存在配置则无需改代码），新生成的建议单 safety_days 字段自动为 2。
-- ============================================================

INSERT INTO sys_config (config_key, config_value, description)
VALUES ('smart_order_safety_days', '2', '智能订货安全天数(后续改为企迈按店)')
ON DUPLICATE KEY UPDATE config_value = '2';

-- 执行后校验：应返回 config_value = '2'
-- SELECT config_key, config_value FROM sys_config WHERE config_key = 'smart_order_safety_days';
