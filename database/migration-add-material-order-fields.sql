-- =====================================================================
-- material_inventory_rule 表新增订货单位/订货单价字段（xinfo 同步用）
-- 对应接口字段：order_unit <- usageUnit（使用单位，440/440 有值）、
--             order_price <- standardCostPrice（标准成本价，440/440 有值）
-- 执行后跑同步或执行 migration-fill-order-fields.sql 补存量
-- =====================================================================

ALTER TABLE material_inventory_rule
    ADD COLUMN order_unit VARCHAR(50) NULL COMMENT '订货单位（xinfo usageUnit 同步）' AFTER purchase_unit,
    ADD COLUMN order_price DECIMAL(12,4) NULL COMMENT '订货单价（元，xinfo standardCostPrice 同步）' AFTER order_unit;
