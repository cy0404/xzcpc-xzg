-- ============================================================
-- material_inventory_rule 表新增：采购单价、采购单位（xinfo 同步用）
-- 执行：生产库 + 测试库各一次（若已执行报 Duplicate column，忽略即可）
-- ============================================================

ALTER TABLE material_inventory_rule
    ADD COLUMN purchase_price DECIMAL(12,4) DEFAULT NULL COMMENT '采购单价（元，按采购单位，xinfo purchasePrice 同步）' AFTER unit_price,
    ADD COLUMN purchase_unit  VARCHAR(50)   DEFAULT NULL COMMENT '采购单位（xinfo purchaseUnit 同步）' AFTER purchase_price;

-- 验证
-- SELECT material_id, base_unit, unit_price, purchase_price, purchase_unit FROM material_inventory_rule WHERE del_flag = 0 LIMIT 10;
