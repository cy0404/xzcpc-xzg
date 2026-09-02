-- ============================================================
-- 8 月盘点模板：补录 5 条半成品品项（测试库验证 SQL）
-- 用途：Excel 月盘品项中 5 条半成品（接口 ENABLED 但库中不存在）
--       —— 半成品同步开关关闭（semi-enabled=false），不插新，
--          仅此 5 条为 Excel 盘点必需，手动补录
-- 执行方式：在 store_inventory_test 库执行
-- 数据来源：xinfo 半成品接口实时数据（2026-08-29）
-- 映射口径：parent_category=食材成本、category=半成品（与同步代码一致）
--           unit_price=qimaiPrice（元/kg）、order_price=cost、order_unit=unit
-- 规格全为 "kg"（纯单位规格），无换算行，仅建基础规则
-- ============================================================

-- 1) 补录物料（5 条，del_flag=0 启用；INSERT IGNORE 幂等，重复执行不报错）
INSERT IGNORE INTO material (material_id, qm_code, parent_category, category, material_name, spec, created_at, updated_at, del_flag, qr_code, loss_visible)
VALUES
('cmpdox8ysm7wnmjvy62i195s', 'WP0943', '食材成本', '半成品', '调制奶基底',   'kg', NOW(), NOW(), 0, NULL, NULL),
('cmpdoxazk0ij33nc8l88c592', 'WP0959', '食材成本', '半成品', '预制芭乐汁',   'kg', NOW(), NOW(), 0, NULL, NULL),
('cmpdoxpm8ab0xnfvfxyf2fw7', 'WP0968', '食材成本', '半成品', '泰国青柚（半成品）', 'kg', NOW(), NOW(), 0, NULL, NULL),
('cmpdox3cvjfos7q38k1oweg7', 'WP0969', '食材成本', '半成品', '预制柚子汁',   'kg', NOW(), NOW(), 0, NULL, NULL),
('cmpdox9l5khpymz2uslw6fim', 'WP0970', '食材成本', '半成品', '青柚粒',       'kg', NOW(), NOW(), 0, NULL, NULL);

-- 2) 补录盘点规则（rule_id 先置每行唯一占位符，再按 id 生成 MR + 8 位编码，与同步代码一致）
INSERT INTO material_inventory_rule
    (rule_id, material_id, base_unit, inventory_units, stock_unit, unit_price,
     purchase_price, purchase_unit, order_unit, order_price, created_at, updated_at, del_flag)
VALUES
('TMP_WP0943', 'cmpdox8ysm7wnmjvy62i195s', 'kg', 'kg', NULL, 22.7000, NULL, NULL, 'kg', 6.1715, NOW(), NOW(), 0),
('TMP_WP0959', 'cmpdoxazk0ij33nc8l88c592', 'kg', 'kg', NULL, 28.8000, NULL, NULL, 'kg', 49.9323, NOW(), NOW(), 0),
('TMP_WP0968', 'cmpdoxpm8ab0xnfvfxyf2fw7', 'kg', 'kg', NULL, 24.1800, NULL, NULL, 'kg', 18.1293, NOW(), NOW(), 0),
('TMP_WP0969', 'cmpdox3cvjfos7q38k1oweg7', 'kg', 'kg', NULL, 25.0000, NULL, NULL, 'kg', 9.5277, NOW(), NOW(), 0),
('TMP_WP0970', 'cmpdox9l5khpymz2uslw6fim', 'kg', 'kg', NULL, 20.0000, NULL, NULL, 'kg', 11.0010, NOW(), NOW(), 0);

UPDATE material_inventory_rule SET rule_id = CONCAT('MR', LPAD(id, 8, '0')) WHERE rule_id IN ('TMP_WP0943','TMP_WP0959','TMP_WP0968','TMP_WP0969','TMP_WP0970');

-- 3) 核对（应 5 行，rule 5 行，rule_id 与现有 MR 编码不冲突）
SELECT qm_code, material_name, spec, del_flag FROM material WHERE qm_code IN ('WP0943','WP0959','WP0968','WP0969','WP0970');
SELECT m.qm_code, r.rule_id, r.base_unit, r.inventory_units, r.unit_price, r.order_price
FROM material_inventory_rule r JOIN material m ON m.material_id = r.material_id
WHERE r.del_flag = 0 AND m.qm_code IN ('WP0943','WP0959','WP0968','WP0969','WP0970');
