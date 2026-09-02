-- ============================================================
-- 生产库补录 8 月模板缺失的 16 条物料（含规则+换算行）
-- 依据：8 月盘点模板快照引用的 material_id 在生产库 material 表不存在
--       物料字段取 xinfo 接口实时值；规则/换算照搬测试库（口径已对齐）
-- 注意：青柚粒 WP0970 测试库单价 0.0020 疑为笔误（应为 0.02 元/g），
--       本脚本按 0.02 生成，请同步修正测试库
-- 幂等：material 靠 uk_material_id 唯一索引 INSERT IGNORE；
--       规则/换算行 NOT EXISTS 防重
-- 执行方式：在 store_inventory（生产库）执行
-- ============================================================

-- 1) 补录物料（16 条）
INSERT IGNORE INTO material (material_id, qm_code, parent_category, category, material_name, spec, created_at, updated_at, del_flag, qr_code, loss_visible)
VALUES
('cmpdox4nkgn2d6h9egr1r4tj', 'WP0973', '耗材物料', '周边贴纸类', '刺绣书签', '20个/包', NOW(), NOW(), 0, NULL, NULL),
('cmpdox9l5khpymz2uslw6fim', 'WP0970', '食材成本', '半成品', '青柚粒', 'kg', NOW(), NOW(), 0, NULL, NULL),
('cmpdox3cvjfos7q38k1oweg7', 'WP0969', '食材成本', '半成品', '预制柚子汁', 'kg', NOW(), NOW(), 0, NULL, NULL),
('cmpdoxpm8ab0xnfvfxyf2fw7', 'WP0968', '食材成本', '半成品', '泰国青柚（半成品）', 'kg', NOW(), NOW(), 0, NULL, NULL),
('cmpdoxe6vrsy4wwetu0u7xu0', 'WP0967', '自购食材物料', '水果蔬菜类', '泰国青柚（原材料）', 'kg', NOW(), NOW(), 0, NULL, NULL),
('cmpdoxtdbjjwtep4r4trwi2g', 'WP0966', '耗材物料', '周边贴纸类', '外卖安心贴', '1000张/卷*4卷/件', NOW(), NOW(), 0, NULL, NULL),
('cmpdoxybxk7tf1ltu2v9n5yb', 'WP0964', '食材物料', '冷冻类', '速冻黄金葡萄柚汁', '950g/瓶*6瓶/件', NOW(), NOW(), 0, NULL, NULL),
('未匹配', 'WP0963', '耗材物料', '周边贴纸类', '青柚贴纸', '200张/捆*124捆/件', NOW(), NOW(), 0, NULL, NULL),
('cmpdoxqwsxowim0890a7r2vt', 'WP0961', '耗材物料', '周边贴纸类', '芭乐贴纸', '200个/捆*124捆/件', NOW(), NOW(), 0, NULL, NULL),
('cmpdox1scibdn119j1uovzw4', 'WP0960', '食材物料', '冷冻类', '黑糖珍珠', '1kg/包*20包/件​', NOW(), NOW(), 0, NULL, NULL),
('cmpdoxdb4ejt26wyimfaxiyh', 'WP0947', '辅材物料', '清洁类', '强力通渠粉', '1kg/瓶*6瓶/件', NOW(), NOW(), 0, NULL, NULL),
('cmpdox8ysm7wnmjvy62i195s', 'WP0943', '食材成本', '半成品', '调制奶基底', 'kg', NOW(), NOW(), 0, NULL, NULL),
('cmsa28vnr0bo83pq3p2jmrlo7', 'WP0940', '耗材物料', '周边贴纸类', '8周年-风车', '500个/件', NOW(), NOW(), 0, NULL, NULL),
('cmsa28vo30bod3pq38hirebvl', 'WP0939', '耗材物料', '周边贴纸类', '8周年-笔记本套盒', '50套/件', NOW(), NOW(), 0, NULL, NULL),
('cmsa28vcl0bkg3pq30bub65v4', 'WP0938', '耗材物料', '周边贴纸类', '8周年-保温杯', '40个/件', NOW(), NOW(), 0, NULL, NULL),
('cmsa28vgd0blu3pq3bh9vez6m', 'WP0919', '食材物料', '茶叶类', '普洱茶', '50g/包*100包/件', NOW(), NOW(), 0, NULL, NULL);

-- 2) 补录盘点规则（16 条，rule_id 沿用测试库 MR 编码）
--    单价口径：base=g 的品项存元/g（与全库一致）；青柚粒按 0.02（修正测试库笔误）
--    幂等：INSERT IGNORE，uk_material_inventory_rule_id（rule_id 唯一）拦截重复
INSERT IGNORE INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, stock_unit, unit_price, purchase_price, purchase_unit, order_unit, order_price, created_at, updated_at, del_flag)
VALUES
('MR00001348', 'cmpdox4nkgn2d6h9egr1r4tj', '个', '个,包', '捆', 3.0000, 60.0000, '捆', '捆', 60.0000, NOW(), NOW(), 0),
('MR00001420', 'cmpdox9l5khpymz2uslw6fim', 'g', 'g,kg', NULL, 0.0200, NULL, NULL, 'kg', 11.0010, NOW(), NOW(), 0),
('MR00001419', 'cmpdox3cvjfos7q38k1oweg7', 'g', 'g,kg', NULL, 0.0250, NULL, NULL, 'kg', 9.5277, NOW(), NOW(), 0),
('MR00001418', 'cmpdoxpm8ab0xnfvfxyf2fw7', 'g', 'g,kg', NULL, 0.0242, NULL, NULL, 'kg', 18.1293, NOW(), NOW(), 0),
('MR00001347', 'cmpdoxe6vrsy4wwetu0u7xu0', 'g', 'g,kg', 'kg', 0.0220, 22.0000, 'kg', 'kg', 22.0000, NOW(), NOW(), 0),
('MR00001346', 'cmpdoxtdbjjwtep4r4trwi2g', '张', '张,卷,件', '件', 0.0220, 88.0000, '件', '件', 88.0000, NOW(), NOW(), 0),
('MR00001344', 'cmpdoxybxk7tf1ltu2v9n5yb', 'g', 'g,瓶,件', '瓶', 0.0368, 35.0000, '瓶', '瓶', 35.0000, NOW(), NOW(), 0),
('MR00001343', '未匹配', '张', '张,捆,件', '捆', 0.1000, 20.0000, '捆', '捆', 20.0000, NOW(), NOW(), 0),
('MR00001341', 'cmpdoxqwsxowim0890a7r2vt', '个', '个,捆,件', '捆', 0.1000, 20.0000, '捆', '捆', 20.0000, NOW(), NOW(), 0),
('MR00001340', 'cmpdox1scibdn119j1uovzw4', 'g', 'g,包,kg,件', '包', 0.0140, 14.0000, '包', '包', 14.0000, NOW(), NOW(), 0),
('MR00001333', 'cmpdoxdb4ejt26wyimfaxiyh', 'g', 'g,瓶,kg,件', '瓶', 0.0850, 85.0000, '瓶', '瓶', 85.0000, NOW(), NOW(), 0),
('MR00001416', 'cmpdox8ysm7wnmjvy62i195s', 'g', 'g,kg', NULL, 0.0227, NULL, NULL, 'kg', 6.1715, NOW(), NOW(), 0),
('MR00001328', 'cmsa28vnr0bo83pq3p2jmrlo7', '个', '个,件', '个', 0.8000, 0.8000, '个', '个', 0.8000, NOW(), NOW(), 0),
('MR00001327', 'cmsa28vo30bod3pq38hirebvl', '套', '套,件', '套', 16.8000, 16.8000, '套', '套', 16.8000, NOW(), NOW(), 0),
('MR00001326', 'cmsa28vcl0bkg3pq30bub65v4', '个', '个,件', '个', 30.0000, 30.0000, '个', '个', 30.0000, NOW(), NOW(), 0),
('MR00001317', 'cmsa28vgd0blu3pq3bh9vez6m', 'g', 'g,包,件', '件', 0.1500, 750.0000, '件', '件', 750.0000, NOW(), NOW(), 0);

-- 3) 补录换算行（沿用测试库方向；UNION ALL 写法避免行构造器；NOT EXISTS 防重）
INSERT INTO material_conversion_rule (rule_id, conversion_type, from_quantity, from_unit, to_quantity, to_unit, sort_no, del_flag)
SELECT v.rule_id, v.conversion_type, v.from_quantity, v.from_unit, v.to_quantity, v.to_unit, v.sort_no, 0 FROM (
SELECT 'MR00001348' AS rule_id, 'unit' AS conversion_type, 1.000000 AS from_quantity, '包' AS from_unit, 20.000000 AS to_quantity, '个' AS to_unit, 1 AS sort_no
UNION ALL
SELECT 'MR00001420', 'unit', 1.000000, 'kg', 1000.000000, 'g', 1
UNION ALL
SELECT 'MR00001419', 'unit', 1.000000, 'kg', 1000.000000, 'g', 1
UNION ALL
SELECT 'MR00001418', 'unit', 1.000000, 'kg', 1000.000000, 'g', 1
UNION ALL
SELECT 'MR00001347', 'unit', 1.000000, 'kg', 1000.000000, 'g', 1
UNION ALL
SELECT 'MR00001346', 'unit', 1.000000, '卷', 1000.000000, '张', 1
UNION ALL
SELECT 'MR00001346', 'unit', 1.000000, '件', 4.000000, '卷', 2
UNION ALL
SELECT 'MR00001346', 'unit', 1.000000, '件', 4000.000000, '张', 3
UNION ALL
SELECT 'MR00001344', 'unit', 1.000000, '瓶', 950.000000, 'g', 1
UNION ALL
SELECT 'MR00001344', 'unit', 1.000000, '件', 6.000000, '瓶', 2
UNION ALL
SELECT 'MR00001344', 'unit', 1.000000, '件', 5700.000000, 'g', 3
UNION ALL
SELECT 'MR00001343', 'unit', 1.000000, '捆', 200.000000, '张', 1
UNION ALL
SELECT 'MR00001343', 'unit', 1.000000, '件', 124.000000, '捆', 2
UNION ALL
SELECT 'MR00001343', 'unit', 1.000000, '件', 24800.000000, '张', 3
UNION ALL
SELECT 'MR00001341', 'unit', 1.000000, '捆', 200.000000, '个', 1
UNION ALL
SELECT 'MR00001341', 'unit', 1.000000, '件', 124.000000, '捆', 2
UNION ALL
SELECT 'MR00001341', 'unit', 1.000000, '件', 24800.000000, '个', 3
UNION ALL
SELECT 'MR00001340', 'unit', 1.000000, '包', 1.000000, 'kg', 1
UNION ALL
SELECT 'MR00001340', 'unit', 1.000000, '件', 20.000000, '包', 2
UNION ALL
SELECT 'MR00001340', 'unit', 1.000000, '件', 20.000000, 'kg', 3
UNION ALL
SELECT 'MR00001340', 'unit', 1.000000, 'kg', 1000.000000, 'g', 4
UNION ALL
SELECT 'MR00001333', 'unit', 1.000000, '瓶', 1.000000, 'kg', 1
UNION ALL
SELECT 'MR00001333', 'unit', 1.000000, '件', 6.000000, '瓶', 2
UNION ALL
SELECT 'MR00001333', 'unit', 1.000000, '件', 6.000000, 'kg', 3
UNION ALL
SELECT 'MR00001333', 'unit', 1.000000, 'kg', 1000.000000, 'g', 4
UNION ALL
SELECT 'MR00001416', 'unit', 1.000000, 'kg', 1000.000000, 'g', 1
UNION ALL
SELECT 'MR00001328', 'unit', 1.000000, '件', 500.000000, '个', 1
UNION ALL
SELECT 'MR00001327', 'unit', 1.000000, '件', 50.000000, '套', 1
UNION ALL
SELECT 'MR00001326', 'unit', 1.000000, '件', 40.000000, '个', 1
UNION ALL
SELECT 'MR00001317', 'unit', 1.000000, '包', 50.000000, 'g', 1
UNION ALL
SELECT 'MR00001317', 'unit', 1.000000, '件', 100.000000, '包', 2
UNION ALL
SELECT 'MR00001317', 'unit', 1.000000, '件', 5000.000000, 'g', 3
) v
WHERE NOT EXISTS (SELECT 1 FROM material_conversion_rule c2 WHERE c2.rule_id = v.rule_id AND c2.from_unit = v.from_unit AND c2.to_unit = v.to_unit AND c2.del_flag = 0);

-- 4) 核对
-- 4.1 物料 16 条入库
SELECT qm_code, material_name, spec, parent_category, category, del_flag FROM material WHERE qm_code IN ('WP0973','WP0970','WP0969','WP0968','WP0967','WP0966','WP0964','WP0963','WP0961','WP0960','WP0947','WP0943','WP0940','WP0939','WP0938','WP0919');
-- 4.2 规则 16 条（应全部有行）
SELECT m.qm_code, r.rule_id, r.base_unit, r.inventory_units, r.unit_price FROM material_inventory_rule r JOIN material m ON m.material_id = r.material_id WHERE r.del_flag = 0 AND m.qm_code IN ('WP0973','WP0970','WP0969','WP0968','WP0967','WP0966','WP0964','WP0963','WP0961','WP0960','WP0947','WP0943','WP0940','WP0939','WP0938','WP0919');
-- 4.3 建模板后跑：快照仍引不到物料的品项（执行建模板 SQL 后运行，应无结果）
-- SELECT tzm.material_id, tzm.material_name FROM template_zone_material tzm LEFT JOIN material m ON m.material_id = tzm.material_id WHERE tzm.zone_id = @zone_id AND tzm.del_flag = 0 AND m.material_id IS NULL;
