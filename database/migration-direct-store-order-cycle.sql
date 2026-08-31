-- ============================================================
-- 16家直营门店订货周期填充（数据来源：企迈控制台 require-control 订货控制规则）
-- 执行时机：生产库执行（幂等，可重复执行）
-- 说明：order_days = 每周订货星期（1=周一 … 7=周日），盘点日 = 订货日-1
-- 未命中规则的门店（弥勒金辰/弥勒印象街/福保）不配置 = 不参与周盘，
-- 待确认后在下方按需补充。
-- ============================================================

INSERT INTO store_order_cycle (store_id, order_days, paused) VALUES
-- 高山#翠湖 规则：周一、周三、周五
('cmpaoctij00az3pmbfl6c71mz', '1,3,5', 0),   -- 翠湖
('cmpaoctqy00cx3pmb43nouafn', '1,3,5', 0),   -- 高山铺
-- 3号线【南线】模板：周三、周六
('cmpaoctko00be3pmbvpeiogtc', '3,6', 0),     -- 福德
('cmpaoctm800bt3pmbdo8azaqf', '3,6', 0),     -- 南亚风情
('cmpaocu5500ge3pmb70lbe792', '3,6', 0),     -- 滇池名门
-- 4号线【西线】模板：周三、周日
('cmpaoctmr00by3pmb20hdtxl5', '3,7', 0),     -- 翰林大观
('cmpaoctrs00d23pmb0nygzz46', '3,7', 0),     -- 仕林街
('cmpaoctv800e13pmbw3w2ahxu', '3,7', 0),     -- 新亚洲
('cmpaoctvq00e63pmbobapy64w', '3,7', 0),     -- 呈贡七彩云南第壹城
('cmpwa91ya010f3pq343o6pqxk', '3,7', 0),     -- 昆明建工新城
-- 1号线【北线】模板：周一、周四
('cmpaocud700ih3pmbe6opynv0', '1,4', 0),    -- 新迎新城
('cmpaocun900l93pmbczh07a1b', '1,4', 0),     -- 万宏国际
-- 2号线【东线】模板：周二、周五
('cmpwa93tr010v3pq3hzddyxs2', '2,5', 0)      -- 南屏街世纪广场
ON DUPLICATE KEY UPDATE order_days = VALUES(order_days), paused = VALUES(paused);

-- 核对：已配置订货周期的直营店（应返回 13 行）
-- SELECT c.store_id, s.store_name, c.order_days FROM store_order_cycle c
--   JOIN store_info s ON s.store_id = c.store_id ORDER BY s.store_name;
