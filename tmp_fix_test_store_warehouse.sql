-- ============================================================
-- 修复：智能订货确认报错 160098「单据属性值错误」的根因排查脚本
-- 背景：测试库测试门店 cangkuid = MDCK111118 为种子脚本造的假值，
--       企迈不认 → createDeclareOrder 被拒。
-- 用法：在 测试库 store_inventory_test 执行。
-- ============================================================

-- 1) 看当前测试门店的 cangkuid（确认现状）
SELECT store_id, store_name, cangkuid, qmai_store_id
FROM store_info
WHERE del_flag = 0 AND store_name LIKE '%测试%';

-- 2) 【可选】从生产库拷一个真实仓库编码过来。
--    生产库执行下面这条，拿到真实 cangkuid 后替换下方 UPDATE 的值：
--    SELECT store_id, store_name, cangkuid FROM store_inventory.store_info
--    WHERE del_flag = 0 AND cangkuid IS NOT NULL AND cangkuid <> '' LIMIT 20;
--    注意：前提是测试环境的企迈 openapi 凭证和生产是同一套（仓库编码才有效）

-- 3) 把测试门店 cangkuid 换成真实仓库编码（把 MDCK000094 换成第 2 步查到的值）
UPDATE store_info
SET cangkuid = 'MDCK000094', updated_at = NOW()
WHERE del_flag = 0 AND store_name LIKE '%测试%';

-- 4) 复查
SELECT store_id, store_name, cangkuid
FROM store_info
WHERE del_flag = 0 AND store_name LIKE '%测试%';
