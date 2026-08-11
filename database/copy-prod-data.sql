-- ============================================================
-- 从生产库拷贝基础数据到测试库（prod 和 test 表结构已对齐）
-- 执行: mysql -h 162.14.122.80 -P 3306 -u store_inventory -pXzcpc@2026 < copy-prod-data.sql
-- ============================================================

SET SESSION sql_mode = 'NO_ENGINE_SUBSTITUTION';

-- 1. 门店信息
INSERT INTO store_inventory_test.store_info SELECT * FROM store_inventory.store_info;

-- 2. 物料主数据
INSERT INTO store_inventory_test.material SELECT * FROM store_inventory.material;

-- 3. 物料盘点规则
INSERT INTO store_inventory_test.material_inventory_rule SELECT * FROM store_inventory.material_inventory_rule;

-- 4. 物料换算关系
INSERT INTO store_inventory_test.material_conversion_rule SELECT * FROM store_inventory.material_conversion_rule;

-- 5. 模板
INSERT INTO store_inventory_test.template SELECT * FROM store_inventory.template;

-- 6. 模板分区
INSERT INTO store_inventory_test.template_zone SELECT * FROM store_inventory.template_zone;

-- 7. 模板分区物料
INSERT INTO store_inventory_test.template_zone_material SELECT * FROM store_inventory.template_zone_material;

-- 8. 支出类型
INSERT INTO store_inventory_test.expense_type SELECT * FROM store_inventory.expense_type;

-- 9. 支出项目
INSERT INTO store_inventory_test.expense_item SELECT * FROM store_inventory.expense_item;

-- 10. 总部管理员权限
INSERT INTO store_inventory_test.admin_permission SELECT * FROM store_inventory.admin_permission;

SELECT '基础数据拷贝完成' AS status;
