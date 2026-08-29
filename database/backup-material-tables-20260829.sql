-- ============================================================
-- 生产库物料三表备份（更新物料前执行）
-- 覆盖：material（物料主数据）、material_inventory_rule（盘点规则）、
--       material_conversion_rule（换算规则）——xinfo 同步/手工更新都会动这三张表
-- 用法：在生产库（store_inventory）执行；若同时要动测试库，在 store_inventory_test 再执行一遍
-- ============================================================

-- 1) 建备份表（结构 + 数据），重复执行会覆盖同名备份表
DROP TABLE IF EXISTS material_bak_20260829;
CREATE TABLE material_bak_20260829 AS
SELECT * FROM material;

DROP TABLE IF EXISTS material_inventory_rule_bak_20260829;
CREATE TABLE material_inventory_rule_bak_20260829 AS
SELECT * FROM material_inventory_rule;

DROP TABLE IF EXISTS material_conversion_rule_bak_20260829;
CREATE TABLE material_conversion_rule_bak_20260829 AS
SELECT * FROM material_conversion_rule;

-- 2) 校验备份条数（应与原表一致，3 个结果都返回才说明备份完整）
SELECT COUNT(*) AS material_count FROM material;
SELECT COUNT(*) AS material_bak_count FROM material_bak_20260829;

SELECT COUNT(*) AS rule_count FROM material_inventory_rule;
SELECT COUNT(*) AS rule_bak_count FROM material_inventory_rule_bak_20260829;

SELECT COUNT(*) AS conv_count FROM material_conversion_rule;
SELECT COUNT(*) AS conv_bak_count FROM material_conversion_rule_bak_20260829;

-- ============================================================
-- 如需要恢复（更新后回滚用）：
-- DELETE FROM material_conversion_rule;
-- INSERT INTO material_conversion_rule SELECT * FROM material_conversion_rule_bak_20260829;
-- DELETE FROM material_inventory_rule;
-- INSERT INTO material_inventory_rule SELECT * FROM material_inventory_rule_bak_20260829;
-- DELETE FROM material;
-- INSERT INTO material SELECT * FROM material_bak_20260829;
-- 注意：恢复会覆盖自增 id 重新分配（若有外键引用请先处理）
-- ============================================================
