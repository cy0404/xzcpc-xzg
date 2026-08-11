-- 将所有门店的 2026年7月 财报加入隐藏黑名单
-- 对每个门店插入一条记录（已存在的会因 UNIQUE KEY 忽略）

INSERT IGNORE INTO report_visibility_config (store_id, stat_month)
SELECT store_id, '2026-07'
FROM store_info
WHERE del_flag = 0;

-- 确认插入数量
SELECT COUNT(*) AS hidden_count FROM report_visibility_config WHERE stat_month = '2026-07';
