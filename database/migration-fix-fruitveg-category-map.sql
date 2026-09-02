-- 修复月度发券「无水果蔬菜已登记报损」：分类映射缺少「水果蔬菜类」
-- 现象：LossReportMonthlyVoucherJob 查询 8/1~9/1 registered 报损后按 catGroupMap 过滤水果蔬菜组，
--       兜底映射 feishu_fruitveg_categories='水果蔬菜' 只含「水果蔬菜」，物料分类「水果蔬菜类」匹配不到 → 掉入其他类被过滤
-- 影响库：
--   [必须] 测试库 store_inventory_test —— 物料分类「水果蔬菜类」18 种，8 月 registered 约 35 条全被过滤
--   [可选] 生产库 store_inventory   —— 生产 29 种「水果蔬菜」正常，仅 1 种「水果蔬菜类」物料受影响（加上更完整）
-- 改完即生效（getConfig 每次查库，无需重启），可立即触发 POST /api/public/loss-report/trigger-monthly-voucher 验证

UPDATE sys_config SET config_value = '水果蔬菜,水果蔬菜类' WHERE config_key = 'feishu_fruitveg_categories';
