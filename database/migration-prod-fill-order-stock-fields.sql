-- =====================================================================
-- 生产库：订货单位/订货单价/库存单位 三字段同步
-- 数据源：store_inventory_test（测试库已跑过 xinfo 同步，2026-08-27 验证通过）
-- 行为与同步代码一致：按 qm_code 关联，接口有值则覆盖；半成品（order_unit 空）不动；
-- 存量规则 base_unit/inventory_units/unit_price 不更新
-- 执行：连 store_inventory 库按顺序执行 ①→⑤
-- =====================================================================

-- ① 加列（若已存在报 Duplicate column，可忽略该错误）
ALTER TABLE material_inventory_rule
    ADD COLUMN order_unit VARCHAR(50) NULL COMMENT '订货单位（xinfo usageUnit 同步）' AFTER purchase_unit,
    ADD COLUMN order_price DECIMAL(12,4) NULL COMMENT '订货单价（元，xinfo standardCostPrice 同步）' AFTER order_unit;

-- ② 预览：将被更新的行数（执行 ③ 前确认）
SELECT COUNT(*) AS will_update
FROM material_inventory_rule r
JOIN material m ON m.material_id = r.material_id AND m.del_flag = 0
JOIN store_inventory_test.material t ON t.qm_code = m.qm_code AND t.del_flag = 0
JOIN store_inventory_test.material_inventory_rule tr
    ON tr.material_id = t.material_id AND tr.del_flag = 0
WHERE r.del_flag = 0
  AND tr.order_unit IS NOT NULL AND tr.order_unit <> '';

-- ③ 执行更新（跨库：测试库 → 生产库）
UPDATE material_inventory_rule r
JOIN material m ON m.material_id = r.material_id AND m.del_flag = 0
JOIN store_inventory_test.material t ON t.qm_code = m.qm_code AND t.del_flag = 0
JOIN store_inventory_test.material_inventory_rule tr
    ON tr.material_id = t.material_id AND tr.del_flag = 0
SET r.order_unit = tr.order_unit,
    r.order_price = tr.order_price,
    r.stock_unit = tr.stock_unit
WHERE r.del_flag = 0
  AND tr.order_unit IS NOT NULL AND tr.order_unit <> '';

-- ④ 验证：生产库填充统计
SELECT COUNT(*) AS total,
       SUM(order_unit  IS NOT NULL AND order_unit  <> '') AS has_order_unit,
       SUM(order_price IS NOT NULL)                        AS has_order_price,
       SUM(stock_unit  IS NOT NULL AND stock_unit  <> '')  AS has_stock_unit
FROM material_inventory_rule WHERE del_flag = 0;

-- ⑤ 未匹配清单：生产有效物料但测试库无对应同步数据（确认是否遗漏，应为空或人工判断）
SELECT m.material_id, m.material_name, m.qm_code
FROM material_inventory_rule r
JOIN material m ON m.material_id = r.material_id AND m.del_flag = 0
LEFT JOIN store_inventory_test.material t
    ON t.qm_code = m.qm_code AND t.del_flag = 0
WHERE r.del_flag = 0
  AND (r.order_unit IS NULL OR r.order_unit = '')
  AND t.material_id IS NULL;
