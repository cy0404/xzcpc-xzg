-- ============================================================
-- 将 test 库 material.del_flag 同步到生产库
-- test: store_inventory_test
-- prod: store_inventory
-- 匹配规则: qm_code 相同
-- 生成时间: 2026-07-30
-- ============================================================

-- Step 1: 预览变更（先看影响哪些行）
SELECT '【将更新】' AS 操作,
       prod.qm_code AS 品项编码,
       prod.material_name AS 物料名称,
       prod.del_flag AS 当前del_flag,
       test.del_flag AS 目标del_flag
FROM store_inventory.material prod
INNER JOIN store_inventory_test.material test ON prod.qm_code = test.qm_code
WHERE prod.del_flag <> test.del_flag;

-- Step 2: 执行同步（确认 Step 1 无误后再跑）
-- UPDATE store_inventory.material prod
-- INNER JOIN store_inventory_test.material test ON prod.qm_code = test.qm_code
-- SET prod.del_flag = test.del_flag
-- WHERE prod.del_flag <> test.del_flag;

-- SELECT ROW_COUNT() AS 同步行数;

-- Step 3: 验证（执行 UPDATE 后跑这个确认结果）
-- SELECT '生产库' AS 来源, del_flag, COUNT(*) AS 数量
-- FROM store_inventory.material
-- GROUP BY del_flag;
--
-- SELECT '测试库' AS 来源, del_flag, COUNT(*) AS 数量
-- FROM store_inventory_test.material
-- GROUP BY del_flag;
