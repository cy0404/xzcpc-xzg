-- ============================================================
-- 诊断：为什么回填 SQL 没有更新到门店
-- 请按顺序跑，把结果告诉我
-- ============================================================

-- 1. 检查 chat_id 列是否存在
SHOW COLUMNS FROM store_inventory.store_info LIKE 'chat_id';

-- 2. 当前 chat_id 为空的门店数（回填前应该很多）
SELECT COUNT(*) AS empty_chat_id_count
FROM store_inventory.store_info
WHERE del_flag = 0 AND (chat_id IS NULL OR chat_id = '');

-- 3. 回填 SQL 里的 store_id 在数据库里是否能匹配到
--    (你执行的 SQL 里 WHERE store_id IN (...)，这里取个例测试)
SELECT store_id, store_name, chat_id
FROM store_inventory.store_info
WHERE del_flag = 0
  AND store_id IN ('cmpaocun900l93pmbczh07a1b', 'cmpaocu2b00fu3pmbwf04mo09', 'cmpaoctm800bt3pmbdo8azaqf')
ORDER BY store_id;

-- 4. 反向查：数据库有但 Excel 里可能没有的 store_id（首字母排序前十）
--    如果上一步匹配到了但更新失败，说明是 SQL 执行问题；
--    如果上一步返回空，说明 store_id 对不上——数据库里的 store_id 是别的值
SELECT store_id, store_name
FROM store_inventory.store_info
WHERE del_flag = 0
ORDER BY store_id
LIMIT 10;
