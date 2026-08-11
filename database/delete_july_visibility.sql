-- 删除 2026年7月 财报隐藏配置（白名单恢复）
-- 执行后7月所有门店的财报将重新可见

DELETE FROM report_visibility_config WHERE stat_month = '2026-07';

-- 确认数量
SELECT COUNT(*) AS remaining FROM report_visibility_config WHERE stat_month = '2026-07';
