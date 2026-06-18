-- 物料规则同步 SQL — 来源: 物料信息.xls
-- 注意: 换算规则中的中文单位名可能因编码问题不准确，请审核后执行

START TRANSACTION;

-- 甜白酒（新版） (qm=WP0917)
SET @mat_WP0917 = (SELECT material_id FROM material WHERE qm_code = 'WP0917');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0917', @mat_WP0917, 'g', 'g,瓶,件', 0.014667)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,件', unit_price = 0.014667;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0917);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 750, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0917;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 9000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0917;

-- 荔枝净果（去杆） (qm=WP0911)
SET @mat_WP0911 = (SELECT material_id FROM material WHERE qm_code = 'WP0911');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0911', @mat_WP0911, 'g', 'g,kg', 0.020500)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.020500;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0911);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0911;

-- 荔枝果肉 (qm=WP0909)
SET @mat_WP0909 = (SELECT material_id FROM material WHERE qm_code = 'WP0909');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0909', @mat_WP0909, 'g', 'g,kg', 0.024700)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.024700;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0909);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0909;

-- 荔枝预制汁 (qm=WP0908)
SET @mat_WP0908 = (SELECT material_id FROM material WHERE qm_code = 'WP0908');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0908', @mat_WP0908, 'g', 'g,kg', 0.021100)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.021100;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0908);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0908;

-- 荔枝杯套 (qm=WP0903)
SET @mat_WP0903 = (SELECT material_id FROM material WHERE qm_code = 'WP0903');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0903', @mat_WP0903, '个', '个,捆,g', 0.240000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,捆,g', unit_price = 0.240000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0903);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 50, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0903;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '个', 4.8, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0903;

-- HPP冷冻荔枝原汁 (qm=WP0902)
SET @mat_WP0902 = (SELECT material_id FROM material WHERE qm_code = 'WP0902');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0902', @mat_WP0902, 'g', 'g,瓶,件', 0.042100)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,件', unit_price = 0.042100;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0902);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 950, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0902;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 11400, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0902;

-- 青芒香片 (qm=WP0893)
SET @mat_WP0893 = (SELECT material_id FROM material WHERE qm_code = 'WP0893');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0893', @mat_WP0893, '个', '个', 3.000000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个', unit_price = 3.000000;

-- 预制南姜汁 (qm=WP0888)
SET @mat_WP0888 = (SELECT material_id FROM material WHERE qm_code = 'WP0888');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0888', @mat_WP0888, 'g', 'g,kg', 0.002347)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.002347;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0888);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0888;

-- 预制青芒汁 (qm=WP0887)
SET @mat_WP0887 = (SELECT material_id FROM material WHERE qm_code = 'WP0887');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0887', @mat_WP0887, 'g', 'g,kg', 0.020900)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.020900;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0887);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0887;

-- 南姜 (qm=WP0877)
SET @mat_WP0877 = (SELECT material_id FROM material WHERE qm_code = 'WP0877');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0877', @mat_WP0877, 'g', 'g,kg', 0.014000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.014000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0877);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0877;

-- 金桔柠檬 (qm=WP0876)
SET @mat_WP0876 = (SELECT material_id FROM material WHERE qm_code = 'WP0876');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0876', @mat_WP0876, 'g', 'g,kg', 0.009500)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.009500;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0876);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0876;

-- 柠檬叶 (qm=WP0875)
SET @mat_WP0875 = (SELECT material_id FROM material WHERE qm_code = 'WP0875');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0875', @mat_WP0875, 'g', 'g,kg', 0.037000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.037000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0875);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0875;

-- 青脆李 (qm=WP0874)
SET @mat_WP0874 = (SELECT material_id FROM material WHERE qm_code = 'WP0874');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0874', @mat_WP0874, 'g', 'g,kg', 0.011000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.011000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0874);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0874;

-- 速冻青芒果汁 (qm=WP0873)
SET @mat_WP0873 = (SELECT material_id FROM material WHERE qm_code = 'WP0873');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0873', @mat_WP0873, 'g', 'g,瓶,ml,件', 0.045000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,ml,件', unit_price = 0.045000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0873);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 1000, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0873;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 6000, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0873;

-- 青芒杯套 (qm=WP0871)
SET @mat_WP0871 = (SELECT material_id FROM material WHERE qm_code = 'WP0871');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0871', @mat_WP0871, '张', '张,捆,个,g', 0.240000)
ON DUPLICATE KEY UPDATE base_unit = '张', inventory_units = '张,捆,个,g', unit_price = 0.240000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0871);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 50, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0871;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '个', 4.8, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0871;

-- PP700细吸管（新） (qm=WP0869)
SET @mat_WP0869 = (SELECT material_id FROM material WHERE qm_code = 'WP0869');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0869', @mat_WP0869, '根', '根,包,件,g', 0.050000)
ON DUPLICATE KEY UPDATE base_unit = '根', inventory_units = '根,包,件,g', unit_price = 0.050000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0869);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 200, '根', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0869;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 4000, '根', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0869;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '根', 1.4, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0869;

-- 芭乐释迦杯套 (qm=WP0864)
SET @mat_WP0864 = (SELECT material_id FROM material WHERE qm_code = 'WP0864');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0864', @mat_WP0864, '张', '张,捆,个,g', 0.240000)
ON DUPLICATE KEY UPDATE base_unit = '张', inventory_units = '张,捆,个,g', unit_price = 0.240000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0864);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 50, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0864;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '个', 4.8, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0864;

-- 棉蒸布 (qm=WP0684)
SET @mat_WP0684 = (SELECT material_id FROM material WHERE qm_code = 'WP0684');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0684', @mat_WP0684, '张', '张,张一包', 6.000000)
ON DUPLICATE KEY UPDATE base_unit = '张', inventory_units = '张,张一包', unit_price = 6.000000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0684);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 10, '张一包', 50, '张', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0684;

-- 新鲜荔枝 (qm=WP0679)
SET @mat_WP0679 = (SELECT material_id FROM material WHERE qm_code = 'WP0679');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0679', @mat_WP0679, 'g', 'g,kg', 0.018000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.018000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0679);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0679;

-- 速冻芒果浆 (qm=WP0350)
SET @mat_WP0350 = (SELECT material_id FROM material WHERE qm_code = 'WP0350');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0350', @mat_WP0350, 'g', 'g,瓶,ml,件', 0.022000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,ml,件', unit_price = 0.022000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0350);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 950, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0350;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 11400, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0350;

-- 普洱茶（新版） (qm=WP0868)
SET @mat_WP0868 = (SELECT material_id FROM material WHERE qm_code = 'WP0868');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0868', @mat_WP0868, 'g', 'g,包,份', 0.150000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,份', unit_price = 0.150000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0868);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 50, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0868;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '份', 500, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0868;

-- 速冻红芭乐果浆 (qm=WP0854)
SET @mat_WP0854 = (SELECT material_id FROM material WHERE qm_code = 'WP0854');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0854', @mat_WP0854, 'g', 'g,瓶,件', 0.036800)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,件', unit_price = 0.036800;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0854);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 950, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0854;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 11400, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0854;

-- 话梅饮料浓浆 (qm=WP0850)
SET @mat_WP0850 = (SELECT material_id FROM material WHERE qm_code = 'WP0850');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0850', @mat_WP0850, 'g', 'g,包,件', 0.040000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.040000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0850);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 500, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0850;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 10000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0850;

-- 冷冻胭脂果汁 (qm=WP0783)
SET @mat_WP0783 = (SELECT material_id FROM material WHERE qm_code = 'WP0783');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0783', @mat_WP0783, 'ml', 'ml,包,件', 0.042100)
ON DUPLICATE KEY UPDATE base_unit = 'ml', inventory_units = 'ml,包,件', unit_price = 0.042100;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0783);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 950, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0783;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 11400, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0783;

-- 速冻白西柚果粒 (qm=WP0687)
SET @mat_WP0687 = (SELECT material_id FROM material WHERE qm_code = 'WP0687');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0687', @mat_WP0687, 'g', 'g,包,件', 0.040000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.040000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0687);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 500, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0687;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 10000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0687;

-- 花瓣（原材料） (qm=WP0583)
SET @mat_WP0583 = (SELECT material_id FROM material WHERE qm_code = 'WP0583');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0583', @mat_WP0583, 'g', 'g,包,件', 0.240000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.240000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0583);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 50, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0583;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 5000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0583;

-- 糯米小丸子 (qm=WP0573)
SET @mat_WP0573 = (SELECT material_id FROM material WHERE qm_code = 'WP0573');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0573', @mat_WP0573, 'g', 'g,包', 0.018000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包', unit_price = 0.018000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0573);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0573;

-- 冷冻滇橄榄原汁 (qm=WP0358)
SET @mat_WP0358 = (SELECT material_id FROM material WHERE qm_code = 'WP0358');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0358', @mat_WP0358, 'g', 'g,瓶,ml,件', 0.035000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,ml,件', unit_price = 0.035000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0358);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 1000, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0358;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 6000, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0358;

-- 冷冻树番茄 (qm=WP0356)
SET @mat_WP0356 = (SELECT material_id FROM material WHERE qm_code = 'WP0356');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0356', @mat_WP0356, 'g', 'g,瓶,件', 0.045000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,件', unit_price = 0.045000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0356);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0356;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 12000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0356;

-- 兰韵清露茶 (qm=WP0345)
SET @mat_WP0345 = (SELECT material_id FROM material WHERE qm_code = 'WP0345');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0345', @mat_WP0345, 'g', 'g,包,份,件', 0.160000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,份,件', unit_price = 0.160000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0345);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 50, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0345;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '份', 500, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0345;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 5000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0345;

-- 青玉观音茶 (qm=WP0343)
SET @mat_WP0343 = (SELECT material_id FROM material WHERE qm_code = 'WP0343');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0343', @mat_WP0343, 'g', 'g,包,件', 0.160000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.160000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0343);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 500, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0343;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 15000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0343;

-- 云豪茉莉茶 (qm=WP0342)
SET @mat_WP0342 = (SELECT material_id FROM material WHERE qm_code = 'WP0342');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0342', @mat_WP0342, 'g', 'g,包,件', 0.200000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.200000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0342);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 100, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0342;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 5000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0342;

-- 山野滇红茶 (qm=WP0341)
SET @mat_WP0341 = (SELECT material_id FROM material WHERE qm_code = 'WP0341');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0341', @mat_WP0341, 'g', 'g,包,件', 0.176000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.176000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0341);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 500, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0341;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 15000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0341;

-- 普洱茶（旧版） (qm=WP0340)
SET @mat_WP0340 = (SELECT material_id FROM material WHERE qm_code = 'WP0340');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0340', @mat_WP0340, 'g', 'g,包,件', 0.130000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.130000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0340);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 500, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0340;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 12500, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0340;

-- 黑糖糖浆 (qm=WP0339)
SET @mat_WP0339 = (SELECT material_id FROM material WHERE qm_code = 'WP0339');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0339', @mat_WP0339, 'g', 'g,瓶,件', 0.038000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,件', unit_price = 0.038000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0339);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 2000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0339;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 20000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0339;

-- 冰蔗糖浆（新） (qm=WP0338)
SET @mat_WP0338 = (SELECT material_id FROM material WHERE qm_code = 'WP0338');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0338', @mat_WP0338, 'g', 'g,瓶,件', 0.011250)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,件', unit_price = 0.011250;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0338);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 6000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0338;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 24000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0338;

-- 水果糖浆. (qm=WP0337)
SET @mat_WP0337 = (SELECT material_id FROM material WHERE qm_code = 'WP0337');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0337', @mat_WP0337, 'g', 'g,瓶,件', 0.025000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,件', unit_price = 0.025000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0337);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0337;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 12000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0337;

-- 幼砂糖- (qm=WP0336)
SET @mat_WP0336 = (SELECT material_id FROM material WHERE qm_code = 'WP0336');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0336', @mat_WP0336, 'g', 'g,件', 0.011666)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,件', unit_price = 0.011666;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0336);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 30000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0336;

-- 新黑糖粉 (qm=WP0334)
SET @mat_WP0334 = (SELECT material_id FROM material WHERE qm_code = 'WP0334');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0334', @mat_WP0334, 'g', 'g,包', 0.030000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包', unit_price = 0.030000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0334);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0334;

-- 香草糖浆1 (qm=WP0333)
SET @mat_WP0333 = (SELECT material_id FROM material WHERE qm_code = 'WP0333');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0333', @mat_WP0333, 'g', 'g,瓶,件', 0.025000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,件', unit_price = 0.025000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0333);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 1200, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0333;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 18000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0333;

-- 释迦果（半成品） (qm=WP0860)
SET @mat_WP0860 = (SELECT material_id FROM material WHERE qm_code = 'WP0860');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0860', @mat_WP0860, 'g', 'g,kg', 0.020700)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.020700;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0860);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0860;

-- 新鲜芭乐 (qm=WP0859)
SET @mat_WP0859 = (SELECT material_id FROM material WHERE qm_code = 'WP0859');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0859', @mat_WP0859, 'g', 'g,kg', 0.017200)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.017200;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0859);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0859;

-- 释迦芭乐果肉 (qm=WP0858)
SET @mat_WP0858 = (SELECT material_id FROM material WHERE qm_code = 'WP0858');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0858', @mat_WP0858, 'g', 'g,kg', 0.026200)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.026200;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0858);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0858;

-- 预制芭乐汁 (qm=WP0857)
SET @mat_WP0857 = (SELECT material_id FROM material WHERE qm_code = 'WP0857');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0857', @mat_WP0857, 'g', 'g,kg', 0.024000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.024000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0857);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0857;

-- 牛油果奶油奶酪 (qm=WP0851)
SET @mat_WP0851 = (SELECT material_id FROM material WHERE qm_code = 'WP0851');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0851', @mat_WP0851, 'g', 'g,kg', 0.046500)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.046500;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0851);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0851;

-- 秘制葡萄果肉 (qm=WP0788)
SET @mat_WP0788 = (SELECT material_id FROM material WHERE qm_code = 'WP0788');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0788', @mat_WP0788, 'g', 'g,kg', 0.021700)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.021700;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0788);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0788;

-- 芝士奶盖 (qm=WP0787)
SET @mat_WP0787 = (SELECT material_id FROM material WHERE qm_code = 'WP0787');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0787', @mat_WP0787, 'g', 'g,kg', 0.032600)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.032600;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0787);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0787;

-- 预制胭脂果葡萄浆 (qm=WP0786)
SET @mat_WP0786 = (SELECT material_id FROM material WHERE qm_code = 'WP0786');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0786', @mat_WP0786, 'g', 'g,kg', 0.022300)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.022300;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0786);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0786;

-- 橄榄混合汁 (qm=WP0758)
SET @mat_WP0758 = (SELECT material_id FROM material WHERE qm_code = 'WP0758');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0758', @mat_WP0758, 'g', 'g,kg', 0.031500)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.031500;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0758);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0758;

-- 鲜橙预制汁 (qm=WP0755)
SET @mat_WP0755 = (SELECT material_id FROM material WHERE qm_code = 'WP0755');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0755', @mat_WP0755, 'g', 'g,kg', 0.030400)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.030400;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0755);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0755;

-- 玫瑰汁 (qm=WP0728)
SET @mat_WP0728 = (SELECT material_id FROM material WHERE qm_code = 'WP0728');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0728', @mat_WP0728, 'g', 'g,kg', 0.022500)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.022500;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0728);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0728;

-- 芒果粒 (qm=WP0689)
SET @mat_WP0689 = (SELECT material_id FROM material WHERE qm_code = 'WP0689');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0689', @mat_WP0689, 'g', 'g,kg', 0.010900)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.010900;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0689);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0689;

-- 咸法干酪乳 (qm=WP0642)
SET @mat_WP0642 = (SELECT material_id FROM material WHERE qm_code = 'WP0642');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0642', @mat_WP0642, 'ml', 'ml,瓶,件', 0.042000)
ON DUPLICATE KEY UPDATE base_unit = 'ml', inventory_units = 'ml,瓶,件', unit_price = 0.042000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0642);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 1000, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0642;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 12000, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0642;

-- 厚椰乳 (qm=WP0639)
SET @mat_WP0639 = (SELECT material_id FROM material WHERE qm_code = 'WP0639');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0639', @mat_WP0639, 'ml', 'ml,瓶,件', 0.015000)
ON DUPLICATE KEY UPDATE base_unit = 'ml', inventory_units = 'ml,瓶,件', unit_price = 0.015000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0639);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 1000, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0639;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 12000, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0639;

-- 悦鲜活鲜奶 (qm=WP0384)
SET @mat_WP0384 = (SELECT material_id FROM material WHERE qm_code = 'WP0384');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0384', @mat_WP0384, 'ml', 'ml,瓶,件', 0.016300)
ON DUPLICATE KEY UPDATE base_unit = 'ml', inventory_units = 'ml,瓶,件', unit_price = 0.016300;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0384);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 950, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0384;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 11400, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0384;

-- 乍甸酸奶 (qm=WP0383)
SET @mat_WP0383 = (SELECT material_id FROM material WHERE qm_code = 'WP0383');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0383', @mat_WP0383, 'g', 'g,包,份', 0.013800)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,份', unit_price = 0.013800;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0383);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 180, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0383;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '份', 3600, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0383;

-- 安佳淡奶油 (qm=WP0381)
SET @mat_WP0381 = (SELECT material_id FROM material WHERE qm_code = 'WP0381');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0381', @mat_WP0381, 'ml', 'ml,瓶,件', 0.041500)
ON DUPLICATE KEY UPDATE base_unit = 'ml', inventory_units = 'ml,瓶,件', unit_price = 0.041500;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0381);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 1000, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0381;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 12000, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0381;

-- 冰勃朗 (qm=WP0378)
SET @mat_WP0378 = (SELECT material_id FROM material WHERE qm_code = 'WP0378');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0378', @mat_WP0378, 'g', 'g,瓶,件', 0.021500)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,件', unit_price = 0.021500;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0378);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0378;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 12000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0378;

-- 冷冻牛奶米布 (qm=WP0354)
SET @mat_WP0354 = (SELECT material_id FROM material WHERE qm_code = 'WP0354');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0354', @mat_WP0354, 'g', 'g,包,件', 0.024000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.024000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0354);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 500, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0354;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 10000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0354;

-- 冷冻牛油果泥(24包/件) (qm=WP0352)
SET @mat_WP0352 = (SELECT material_id FROM material WHERE qm_code = 'WP0352');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0352', @mat_WP0352, 'g', 'g,包,件', 0.061330)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.061330;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0352);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 250, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0352;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 6000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0352;

-- 调制椰奶 (qm=WP0688)
SET @mat_WP0688 = (SELECT material_id FROM material WHERE qm_code = 'WP0688');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0688', @mat_WP0688, 'g', 'g,kg', 0.019500)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.019500;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0688);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0688;

-- 燕麦龙珠（本成品） (qm=WP0648)
SET @mat_WP0648 = (SELECT material_id FROM material WHERE qm_code = 'WP0648');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0648', @mat_WP0648, 'g', 'g,kg', 0.037800)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.037800;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0648);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0648;

-- 苦瓜（半成品） (qm=WP0644)
SET @mat_WP0644 = (SELECT material_id FROM material WHERE qm_code = 'WP0644');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0644', @mat_WP0644, 'g', 'g,kg', 0.010100)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.010100;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0644);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0644;

-- 奇亚籽（半成品） (qm=WP0643)
SET @mat_WP0643 = (SELECT material_id FROM material WHERE qm_code = 'WP0643');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0643', @mat_WP0643, 'g', 'g,kg', 0.004400)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.004400;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0643);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0643;

-- 新鲜牛油果 (qm=WP0641)
SET @mat_WP0641 = (SELECT material_id FROM material WHERE qm_code = 'WP0641');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0641', @mat_WP0641, 'g', 'g,kg', 0.049820)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.049820;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0641);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0641;

-- 火龙果汁 (qm=WP0585)
SET @mat_WP0585 = (SELECT material_id FROM material WHERE qm_code = 'WP0585');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0585', @mat_WP0585, 'g', 'g,kg', 0.006800)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.006800;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0585);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0585;

-- 花瓣（半成品） (qm=WP0584)
SET @mat_WP0584 = (SELECT material_id FROM material WHERE qm_code = 'WP0584');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0584', @mat_WP0584, 'g', 'g,kg', 0.012000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.012000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0584);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0584;

-- 木薯淀粉 (qm=WP0580)
SET @mat_WP0580 = (SELECT material_id FROM material WHERE qm_code = 'WP0580');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0580', @mat_WP0580, 'g', 'g,kg', 0.001100)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.001100;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0580);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0580;

-- 小丸子 (qm=WP0574)
SET @mat_WP0574 = (SELECT material_id FROM material WHERE qm_code = 'WP0574');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0574', @mat_WP0574, 'g', 'g,kg', 0.013300)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.013300;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0574);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0574;

-- 雪顶 (qm=WP0572)
SET @mat_WP0572 = (SELECT material_id FROM material WHERE qm_code = 'WP0572');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0572', @mat_WP0572, 'g', 'g,kg', 0.049100)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.049100;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0572);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0572;

-- 橙子块 (qm=WP0571)
SET @mat_WP0571 = (SELECT material_id FROM material WHERE qm_code = 'WP0571');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0571', @mat_WP0571, 'g', 'g,kg', 0.016200)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.016200;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0571);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0571;

-- 葡萄整果 (qm=WP0565)
SET @mat_WP0565 = (SELECT material_id FROM material WHERE qm_code = 'WP0565');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0565', @mat_WP0565, 'g', 'g,kg', 0.014600)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.014600;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0565);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0565;

-- 普洱茶茶汤 (qm=WP0557)
SET @mat_WP0557 = (SELECT material_id FROM material WHERE qm_code = 'WP0557');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0557', @mat_WP0557, 'g', 'g,kg', 0.003800)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.003800;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0557);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0557;

-- 山野滇红茶汤 (qm=WP0556)
SET @mat_WP0556 = (SELECT material_id FROM material WHERE qm_code = 'WP0556');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0556', @mat_WP0556, 'g', 'g,kg', 0.004400)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.004400;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0556);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0556;

-- 青玉观音茶汤 (qm=WP0555)
SET @mat_WP0555 = (SELECT material_id FROM material WHERE qm_code = 'WP0555');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0555', @mat_WP0555, 'g', 'g,kg', 0.004000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.004000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0555);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0555;

-- 云豪茉莉茶汤 (qm=WP0553)
SET @mat_WP0553 = (SELECT material_id FROM material WHERE qm_code = 'WP0553');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0553', @mat_WP0553, 'g', 'g,kg', 0.004800)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.004800;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0553);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0553;

-- 兰韵清露茶汤 (qm=WP0552)
SET @mat_WP0552 = (SELECT material_id FROM material WHERE qm_code = 'WP0552');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0552', @mat_WP0552, 'g', 'g,kg', 0.004400)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.004400;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0552);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0552;

-- 南非橙 (qm=WP0551)
SET @mat_WP0551 = (SELECT material_id FROM material WHERE qm_code = 'WP0551');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0551', @mat_WP0551, 'g', 'g,kg', 0.014400)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.014400;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0551);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0551;

-- 羽衣甘蓝叶（半成品） (qm=WP0549)
SET @mat_WP0549 = (SELECT material_id FROM material WHERE qm_code = 'WP0549');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0549', @mat_WP0549, 'g', 'g,kg', 0.015770)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.015770;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0549);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0549;

-- 芒果酱 (qm=WP0540)
SET @mat_WP0540 = (SELECT material_id FROM material WHERE qm_code = 'WP0540');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0540', @mat_WP0540, 'g', 'g,kg', 0.016200)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.016200;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0540);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0540;

-- 酸角果泥果酱 (qm=WP0733)
SET @mat_WP0733 = (SELECT material_id FROM material WHERE qm_code = 'WP0733');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0733', @mat_WP0733, 'g', 'g,瓶,件', 0.045000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,件', unit_price = 0.045000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0733);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0733;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 12000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0733;

-- 洱宝话梅 (qm=WP0698)
SET @mat_WP0698 = (SELECT material_id FROM material WHERE qm_code = 'WP0698');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0698', @mat_WP0698, 'g', 'g,包,件', 0.043750)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.043750;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0698);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 160, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0698;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 6400, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0698;

-- 奇亚籽（原材料） (qm=WP0645)
SET @mat_WP0645 = (SELECT material_id FROM material WHERE qm_code = 'WP0645');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0645', @mat_WP0645, 'g', 'g,包', 0.058000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包', unit_price = 0.058000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0645);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0645;

-- 燕麦龙珠（原材料） (qm=WP0631)
SET @mat_WP0631 = (SELECT material_id FROM material WHERE qm_code = 'WP0631');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0631', @mat_WP0631, 'g', 'g,瓶,件', 0.024500)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,件', unit_price = 0.024500;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0631);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 850, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0631;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 10200, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0631;

-- 甜白酒（旧版） (qm=WP0578)
SET @mat_WP0578 = (SELECT material_id FROM material WHERE qm_code = 'WP0578');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0578', @mat_WP0578, 'g', 'g,瓶,件', 0.014660)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,件', unit_price = 0.014660;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0578);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 750, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0578;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 4500, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0578;

-- 玫瑰酱 (qm=WP0577)
SET @mat_WP0577 = (SELECT material_id FROM material WHERE qm_code = 'WP0577');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0577', @mat_WP0577, 'g', 'g,瓶,件', 0.032000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,件', unit_price = 0.032000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0577);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0577;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 12000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0577;

-- 黄油红糖 (qm=WP0537)
SET @mat_WP0537 = (SELECT material_id FROM material WHERE qm_code = 'WP0537');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0537', @mat_WP0537, 'g', 'g,kg', 0.041160)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.041160;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0537);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0537;

-- 珍珠 (qm=WP0536)
SET @mat_WP0536 = (SELECT material_id FROM material WHERE qm_code = 'WP0536');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0536', @mat_WP0536, 'g', 'g,kg', 0.013210)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.013210;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0536);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0536;

-- 寒天冻 (qm=WP0535)
SET @mat_WP0535 = (SELECT material_id FROM material WHERE qm_code = 'WP0535');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0535', @mat_WP0535, 'g', 'g,kg', 0.003500)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.003500;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0535);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0535;

-- 柠檬片 (qm=WP0534)
SET @mat_WP0534 = (SELECT material_id FROM material WHERE qm_code = 'WP0534');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0534', @mat_WP0534, 'g', 'g,kg', 0.017550)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.017550;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0534);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0534;

-- 酸奶酱 (qm=WP0533)
SET @mat_WP0533 = (SELECT material_id FROM material WHERE qm_code = 'WP0533');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0533', @mat_WP0533, 'g', 'g,kg', 0.014200)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.014200;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0533);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0533;

-- 牛油果泥 (qm=WP0531)
SET @mat_WP0531 = (SELECT material_id FROM material WHERE qm_code = 'WP0531');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0531', @mat_WP0531, 'g', 'g,kg', 0.053700)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.053700;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0531);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0531;

-- 紫米（半成品） (qm=WP0530)
SET @mat_WP0530 = (SELECT material_id FROM material WHERE qm_code = 'WP0530');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0530', @mat_WP0530, 'g', 'g,kg', 0.017400)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.017400;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0530);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0530;

-- 氧化亚氮气弹 (qm=WP0382)
SET @mat_WP0382 = (SELECT material_id FROM material WHERE qm_code = 'WP0382');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0382', @mat_WP0382, '支', '支,盒,件', 3.500000)
ON DUPLICATE KEY UPDATE base_unit = '支', inventory_units = '支,盒,件', unit_price = 3.500000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0382);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '盒', 10, '支', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0382;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 100, '支', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0382;

-- 酸奶粉 (qm=WP0374)
SET @mat_WP0374 = (SELECT material_id FROM material WHERE qm_code = 'WP0374');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0374', @mat_WP0374, 'g', 'g,包,件', 0.035000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.035000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0374);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0374;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 20000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0374;

-- 紫米（原材料） (qm=WP0373)
SET @mat_WP0373 = (SELECT material_id FROM material WHERE qm_code = 'WP0373');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0373', @mat_WP0373, 'g', 'g,包,件', 0.009500)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.009500;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0373);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 500, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0373;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 12000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0373;

-- 炒米 (qm=WP0372)
SET @mat_WP0372 = (SELECT material_id FROM material WHERE qm_code = 'WP0372');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0372', @mat_WP0372, 'g', 'g,瓶,件', 0.017000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶,件', unit_price = 0.017000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0372);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 500, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0372;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 12500, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0372;

-- 维生素C (qm=WP0368)
SET @mat_WP0368 = (SELECT material_id FROM material WHERE qm_code = 'WP0368');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0368', @mat_WP0368, 'g', 'g,包', 0.004000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包', unit_price = 0.004000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0368);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0368;

-- 珍珠粉圆 (qm=WP0366)
SET @mat_WP0366 = (SELECT material_id FROM material WHERE qm_code = 'WP0366');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0366', @mat_WP0366, 'g', 'g,包,件', 0.013210)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.013210;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0366);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0366;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 16000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0366;

-- 水晶冻粉 (qm=WP0365)
SET @mat_WP0365 = (SELECT material_id FROM material WHERE qm_code = 'WP0365');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0365', @mat_WP0365, 'g', 'g,包,件', 0.035000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.035000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0365);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0365;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 12000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0365;

-- 纸杯（顾客喝水用） (qm=WP0852)
SET @mat_WP0852 = (SELECT material_id FROM material WHERE qm_code = 'WP0852');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0852', @mat_WP0852, '个', '个,提,件', 0.200000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,提,件', unit_price = 0.200000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0852);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '提', 25, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0852;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 500, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0852;

-- 白色一体盖 (qm=WP0675)
SET @mat_WP0675 = (SELECT material_id FROM material WHERE qm_code = 'WP0675');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0675', @mat_WP0675, '个', '个', 0.220000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个', unit_price = 0.220000;

-- 针织袋(通版） (qm=WP0661)
SET @mat_WP0661 = (SELECT material_id FROM material WHERE qm_code = 'WP0661');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0661', @mat_WP0661, '个', '个', 5.000000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个', unit_price = 5.000000;

-- 胖胖瓶 (qm=WP0651)
SET @mat_WP0651 = (SELECT material_id FROM material WHERE qm_code = 'WP0651');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0651', @mat_WP0651, '个', '个,件', 1.200000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,件', unit_price = 1.200000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0651);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 100, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0651;

-- 剥壳巴旦木1公斤 (qm=WP0364)
SET @mat_WP0364 = (SELECT material_id FROM material WHERE qm_code = 'WP0364');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0364', @mat_WP0364, 'g', 'g,件', 0.080000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,件', unit_price = 0.080000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0364);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 10000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0364;

-- 大麦若叶粉 (qm=WP0363)
SET @mat_WP0363 = (SELECT material_id FROM material WHERE qm_code = 'WP0363');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0363', @mat_WP0363, 'g', 'g,包,件', 0.080000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.080000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0363);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 250, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0363;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 6000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0363;

-- 云南红糖 (qm=WP0362)
SET @mat_WP0362 = (SELECT material_id FROM material WHERE qm_code = 'WP0362');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0362', @mat_WP0362, 'g', 'g,包,件', 0.025000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.025000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0362);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0362;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 10000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0362;

-- 碧根果仁 (qm=WP0361)
SET @mat_WP0361 = (SELECT material_id FROM material WHERE qm_code = 'WP0361');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0361', @mat_WP0361, 'g', 'g,包,件', 0.120000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.120000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0361);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 250, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0361;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 10000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0361;

-- 泰国木薯淀粉 (qm=WP0360)
SET @mat_WP0360 = (SELECT material_id FROM material WHERE qm_code = 'WP0360');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0360', @mat_WP0360, 'g', 'g,包,件', 0.004000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.004000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0360);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 500, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0360;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 15000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0360;

-- 安佳黄油 (qm=WP0346)
SET @mat_WP0346 = (SELECT material_id FROM material WHERE qm_code = 'WP0346');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0346', @mat_WP0346, 'g', 'g,盒,件', 0.079200)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,盒,件', unit_price = 0.079200;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0346);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '盒', 227, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0346;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 9080, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0346;

-- 无纺布单杯袋 (qm=WP0330)
SET @mat_WP0330 = (SELECT material_id FROM material WHERE qm_code = 'WP0330');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0330', @mat_WP0330, '个', '个,件', 0.600000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,件', unit_price = 0.600000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0330);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 600, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0330;

-- 350ml玻璃瓶 (qm=WP0329)
SET @mat_WP0329 = (SELECT material_id FROM material WHERE qm_code = 'WP0329');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0329', @mat_WP0329, '个', '个,件', 2.500000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,件', unit_price = 2.500000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0329);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 60, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0329;

-- 98PET杯+盖 (qm=WP0327)
SET @mat_WP0327 = (SELECT material_id FROM material WHERE qm_code = 'WP0327');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0327', @mat_WP0327, '个', '个,件', 0.500000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,件', unit_price = 0.500000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0327);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 1000, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0327;

-- 无纺布双杯袋 (qm=WP0325)
SET @mat_WP0325 = (SELECT material_id FROM material WHERE qm_code = 'WP0325');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0325', @mat_WP0325, '个', '个,件', 0.712500)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,件', unit_price = 0.712500;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0325);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 400, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0325;

-- 纸质小料杯 (qm=WP0320)
SET @mat_WP0320 = (SELECT material_id FROM material WHERE qm_code = 'WP0320');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0320', @mat_WP0320, '个', '个,件', 0.200000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,件', unit_price = 0.200000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0320);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 1000, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0320;

-- PLA单个打包袋 (qm=WP0316)
SET @mat_WP0316 = (SELECT material_id FROM material WHERE qm_code = 'WP0316');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0316', @mat_WP0316, '个', '个,捆,件', 0.240000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,捆,件', unit_price = 0.240000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0316);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 100, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0316;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 2000, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0316;

-- 注塑杯(500) (qm=WP0314)
SET @mat_WP0314 = (SELECT material_id FROM material WHERE qm_code = 'WP0314');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0314', @mat_WP0314, '个', '个,件', 0.440000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,件', unit_price = 0.440000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0314);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 500, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0314;

-- 半球杯盖 (qm=WP0312)
SET @mat_WP0312 = (SELECT material_id FROM material WHERE qm_code = 'WP0312');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0312', @mat_WP0312, '个', '个,件', 0.150000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,件', unit_price = 0.150000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0312);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 1000, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0312;

-- 牛皮纸杯 (qm=WP0302)
SET @mat_WP0302 = (SELECT material_id FROM material WHERE qm_code = 'WP0302');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0302', @mat_WP0302, '个', '个,件', 0.440000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,件', unit_price = 0.440000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0302);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 500, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0302;

-- 试饮杯 (qm=WP0301)
SET @mat_WP0301 = (SELECT material_id FROM material WHERE qm_code = 'WP0301');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0301', @mat_WP0301, '个', '个,捆,件', 0.100000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,捆,件', unit_price = 0.100000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0301);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 50, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0301;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 2000, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0301;

-- （新）PLA三孔鲜奶吸管 (qm=WP0768)
SET @mat_WP0768 = (SELECT material_id FROM material WHERE qm_code = 'WP0768');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0768', @mat_WP0768, '根', '根,包,件,g', 0.075000)
ON DUPLICATE KEY UPDATE base_unit = '根', inventory_units = '根,包,件,g', unit_price = 0.075000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0768);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 200, '根', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0768;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 5000, '根', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0768;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '根', 1.8, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0768;

-- 沙发清洁剂 (qm=WP0751)
SET @mat_WP0751 = (SELECT material_id FROM material WHERE qm_code = 'WP0751');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0751', @mat_WP0751, 'ml', 'ml,瓶', 0.030000)
ON DUPLICATE KEY UPDATE base_unit = 'ml', inventory_units = 'ml,瓶', unit_price = 0.030000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0751);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 500, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0751;

-- 椰子油去污膏 (qm=WP0750)
SET @mat_WP0750 = (SELECT material_id FROM material WHERE qm_code = 'WP0750');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0750', @mat_WP0750, 'g', 'g,瓶', 0.040000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶', unit_price = 0.040000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0750);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 450, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0750;

-- 含氟消毒片 (qm=WP0674)
SET @mat_WP0674 = (SELECT material_id FROM material WHERE qm_code = 'WP0674');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0674', @mat_WP0674, '片', '片,瓶', 0.520000)
ON DUPLICATE KEY UPDATE base_unit = '片', inventory_units = '片,瓶', unit_price = 0.520000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0674);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 100, '片', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0674;

-- 餐具浸泡去渍粉 (qm=WP0673)
SET @mat_WP0673 = (SELECT material_id FROM material WHERE qm_code = 'WP0673');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0673', @mat_WP0673, 'g', 'g,瓶', 0.055000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶', unit_price = 0.055000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0673);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0673;

-- 一次性胶手套(L码) (qm=WP0603)
SET @mat_WP0603 = (SELECT material_id FROM material WHERE qm_code = 'WP0603');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0603', @mat_WP0603, '副', '副,包,件', 0.700000)
ON DUPLICATE KEY UPDATE base_unit = '副', inventory_units = '副,包,件', unit_price = 0.700000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0603);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 50, '副', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0603;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 500, '副', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0603;

-- 一次性胶手套(M码) (qm=WP0602)
SET @mat_WP0602 = (SELECT material_id FROM material WHERE qm_code = 'WP0602');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0602', @mat_WP0602, '副', '副,包,件', 0.700000)
ON DUPLICATE KEY UPDATE base_unit = '副', inventory_units = '副,包,件', unit_price = 0.700000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0602);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 50, '副', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0602;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 500, '副', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0602;

-- 柠檬除胶剂 (qm=WP0601)
SET @mat_WP0601 = (SELECT material_id FROM material WHERE qm_code = 'WP0601');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0601', @mat_WP0601, 'g', 'g,瓶', 0.045000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,瓶', unit_price = 0.045000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0601);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0601;

-- 双头铲刀 (qm=WP0600)
SET @mat_WP0600 = (SELECT material_id FROM material WHERE qm_code = 'WP0600');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0600', @mat_WP0600, '把', '把', 3.000000)
ON DUPLICATE KEY UPDATE base_unit = '把', inventory_units = '把', unit_price = 3.000000;

-- 保鲜膜 (qm=WP0430)
SET @mat_WP0430 = (SELECT material_id FROM material WHERE qm_code = 'WP0430');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0430', @mat_WP0430, '卷', '卷', 40.000000)
ON DUPLICATE KEY UPDATE base_unit = '卷', inventory_units = '卷', unit_price = 40.000000;

-- PP500粗吸管 (qm=WP0319)
SET @mat_WP0319 = (SELECT material_id FROM material WHERE qm_code = 'WP0319');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0319', @mat_WP0319, '根', '根,包,件,g', 0.075000)
ON DUPLICATE KEY UPDATE base_unit = '根', inventory_units = '根,包,件,g', unit_price = 0.075000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0319);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 100, '根', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0319;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 2000, '根', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0319;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '根', 2.2, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0319;

-- PP700细吸管-（旧） (qm=WP0318)
SET @mat_WP0318 = (SELECT material_id FROM material WHERE qm_code = 'WP0318');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0318', @mat_WP0318, '支', '支,包,件,根', 0.050000)
ON DUPLICATE KEY UPDATE base_unit = '支', inventory_units = '支,包,件,根', unit_price = 0.050000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0318);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 100, '支', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0318;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 2000, '根', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0318;

-- PP700粗吸管 (qm=WP0313)
SET @mat_WP0313 = (SELECT material_id FROM material WHERE qm_code = 'WP0313');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0313', @mat_WP0313, '支', '支,包,件,根,g', 0.050000)
ON DUPLICATE KEY UPDATE base_unit = '支', inventory_units = '支,包,件,根,g', unit_price = 0.050000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0313);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 200, '支', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0313;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 4000, '根', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0313;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '根', 2.8, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0313;

-- PLA双个打包袋 (qm=WP0311)
SET @mat_WP0311 = (SELECT material_id FROM material WHERE qm_code = 'WP0311');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0311', @mat_WP0311, '个', '个,捆,件', 0.360000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,捆,件', unit_price = 0.360000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0311);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 100, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0311;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 2000, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0311;

-- 封口膜 (qm=WP0310)
SET @mat_WP0310 = (SELECT material_id FROM material WHERE qm_code = 'WP0310');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0310', @mat_WP0310, '张', '张,卷', 0.050000)
ON DUPLICATE KEY UPDATE base_unit = '张', inventory_units = '张,卷', unit_price = 0.050000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0310);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '卷', 2000, '张', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0310;

-- 新版小黑勺 (qm=WP0309)
SET @mat_WP0309 = (SELECT material_id FROM material WHERE qm_code = 'WP0309');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0309', @mat_WP0309, '根', '根,包,件', 0.180000)
ON DUPLICATE KEY UPDATE base_unit = '根', inventory_units = '根,包,件', unit_price = 0.180000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0309);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 100, '根', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0309;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 500, '根', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0309;

-- 四杯托(纸托) (qm=WP0306)
SET @mat_WP0306 = (SELECT material_id FROM material WHERE qm_code = 'WP0306');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0306', @mat_WP0306, '个', '个,件', 0.516600)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,件', unit_price = 0.516600;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0306);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 300, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0306;

-- 无纺布四杯袋 (qm=WP0305)
SET @mat_WP0305 = (SELECT material_id FROM material WHERE qm_code = 'WP0305');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0305', @mat_WP0305, '个', '个,件', 1.000000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,件', unit_price = 1.000000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0305);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 400, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0305;

-- 小木勺子(奶油专用) (qm=WP0304)
SET @mat_WP0304 = (SELECT material_id FROM material WHERE qm_code = 'WP0304');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0304', @mat_WP0304, '个', '个,包,件,g', 0.190000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,包,件,g', unit_price = 0.190000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0304);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 100, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0304;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 5000, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0304;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '个', 2.2, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0304;

-- 红心芭乐 (qm=WP0856)
SET @mat_WP0856 = (SELECT material_id FROM material WHERE qm_code = 'WP0856');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0856', @mat_WP0856, 'g', 'g', 0.014000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g', unit_price = 0.014000;

-- 台面毛巾(灰色) (qm=WP0525)
SET @mat_WP0525 = (SELECT material_id FROM material WHERE qm_code = 'WP0525');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0525', @mat_WP0525, '条', '条', 3.000000)
ON DUPLICATE KEY UPDATE base_unit = '条', inventory_units = '条', unit_price = 3.000000;

-- 地面毛巾(酒红色) (qm=WP0524)
SET @mat_WP0524 = (SELECT material_id FROM material WHERE qm_code = 'WP0524');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0524', @mat_WP0524, '条', '条', 3.000000)
ON DUPLICATE KEY UPDATE base_unit = '条', inventory_units = '条', unit_price = 3.000000;

-- T33口感调节活性炭 (qm=WP0523)
SET @mat_WP0523 = (SELECT material_id FROM material WHERE qm_code = 'WP0523');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0523', @mat_WP0523, '根', '根', 35.000000)
ON DUPLICATE KEY UPDATE base_unit = '根', inventory_units = '根', unit_price = 35.000000;

-- 除油喷壶 (qm=WP0516)
SET @mat_WP0516 = (SELECT material_id FROM material WHERE qm_code = 'WP0516');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0516', @mat_WP0516, '个', '个', 45.000000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个', unit_price = 45.000000;

-- 纯水机GR0膜 (qm=WP0510)
SET @mat_WP0510 = (SELECT material_id FROM material WHERE qm_code = 'WP0510');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0510', @mat_WP0510, '根', '根', 550.000000)
ON DUPLICATE KEY UPDATE base_unit = '根', inventory_units = '根', unit_price = 550.000000;

-- 纯水机压缩活性炭 (qm=WP0509)
SET @mat_WP0509 = (SELECT material_id FROM material WHERE qm_code = 'WP0509');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0509', @mat_WP0509, '根', '根', 36.000000)
ON DUPLICATE KEY UPDATE base_unit = '根', inventory_units = '根', unit_price = 36.000000;

-- 纯水机PP棉 (qm=WP0508)
SET @mat_WP0508 = (SELECT material_id FROM material WHERE qm_code = 'WP0508');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0508', @mat_WP0508, '根', '根', 8.000000)
ON DUPLICATE KEY UPDATE base_unit = '根', inventory_units = '根', unit_price = 8.000000;

-- 定制口罩 (qm=WP0505)
SET @mat_WP0505 = (SELECT material_id FROM material WHERE qm_code = 'WP0505');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0505', @mat_WP0505, '个', '个,捆,件', 0.320000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,捆,件', unit_price = 0.320000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0505);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 50, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0505;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 2500, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0505;

-- 强效清洁剂 (qm=WP0502)
SET @mat_WP0502 = (SELECT material_id FROM material WHERE qm_code = 'WP0502');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0502', @mat_WP0502, 'ml', 'ml,瓶', 0.105700)
ON DUPLICATE KEY UPDATE base_unit = 'ml', inventory_units = 'ml,瓶', unit_price = 0.105700;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0502);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 473, 'ml', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0502;

-- 清洁棉 (qm=WP0500)
SET @mat_WP0500 = (SELECT material_id FROM material WHERE qm_code = 'WP0500');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0500', @mat_WP0500, '盒', '盒,包', 1.500000)
ON DUPLICATE KEY UPDATE base_unit = '盒', inventory_units = '盒,包', unit_price = 1.500000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0500);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 100, '盒', 1, '包', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0500;

-- 蓝色毛巾(蓝色) (qm=WP0497)
SET @mat_WP0497 = (SELECT material_id FROM material WHERE qm_code = 'WP0497');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0497', @mat_WP0497, '条', '条', 3.000000)
ON DUPLICATE KEY UPDATE base_unit = '条', inventory_units = '条', unit_price = 3.000000;

-- 出品毛巾(木色) (qm=WP0495)
SET @mat_WP0495 = (SELECT material_id FROM material WHERE qm_code = 'WP0495');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0495', @mat_WP0495, '条', '条', 3.000000)
ON DUPLICATE KEY UPDATE base_unit = '条', inventory_units = '条', unit_price = 3.000000;

-- 垃圾袋 (qm=WP0494)
SET @mat_WP0494 = (SELECT material_id FROM material WHERE qm_code = 'WP0494');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0494', @mat_WP0494, '包', '包,袋', 20.000000)
ON DUPLICATE KEY UPDATE base_unit = '包', inventory_units = '包,袋', unit_price = 20.000000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0494);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '袋', 20, '包', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0494;

-- 酸性清洗粉 (qm=WP0492)
SET @mat_WP0492 = (SELECT material_id FROM material WHERE qm_code = 'WP0492');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0492', @mat_WP0492, 'g', 'g,包,件', 0.035000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,包,件', unit_price = 0.035000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0492);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0492;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 1000, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0492;

-- 一次性防尘帽 (qm=WP0491)
SET @mat_WP0491 = (SELECT material_id FROM material WHERE qm_code = 'WP0491');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0491', @mat_WP0491, '个', '个,包,件', 0.060000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,包,件', unit_price = 0.060000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0491);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 100, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0491;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 1000, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0491;

-- 定制纸巾 (qm=WP0490)
SET @mat_WP0490 = (SELECT material_id FROM material WHERE qm_code = 'WP0490');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0490', @mat_WP0490, '包', '包,件', 2.833300)
ON DUPLICATE KEY UPDATE base_unit = '包', inventory_units = '包,件', unit_price = 2.833300;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0490);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 60, '包', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0490;

-- 玻璃清洁剂 (qm=WP0488)
SET @mat_WP0488 = (SELECT material_id FROM material WHERE qm_code = 'WP0488');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0488', @mat_WP0488, 'ml', 'ml,瓶,g', 0.012000)
ON DUPLICATE KEY UPDATE base_unit = 'ml', inventory_units = 'ml,瓶,g', unit_price = 0.012000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0488);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '瓶', 500, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0488;

-- 一次性胶手套(S码) (qm=WP0487)
SET @mat_WP0487 = (SELECT material_id FROM material WHERE qm_code = 'WP0487');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0487', @mat_WP0487, '副', '副,包,件', 0.700000)
ON DUPLICATE KEY UPDATE base_unit = '副', inventory_units = '副,包,件', unit_price = 0.700000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0487);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 50, '副', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0487;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 500, '副', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0487;

-- 释迦果（原材料） (qm=WP0855)
SET @mat_WP0855 = (SELECT material_id FROM material WHERE qm_code = 'WP0855');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0855', @mat_WP0855, 'g', 'g,kg', 0.020000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.020000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0855);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0855;

-- 云南杯套（新） (qm=WP0778)
SET @mat_WP0778 = (SELECT material_id FROM material WHERE qm_code = 'WP0778');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0778', @mat_WP0778, '张', '张,捆,件,g', 0.160000)
ON DUPLICATE KEY UPDATE base_unit = '张', inventory_units = '张,捆,件,g', unit_price = 0.160000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0778);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 50, '张', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0778;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 1000, '张', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0778;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '张', 4.8, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0778;

-- 圆边防漏溢纸 (qm=WP0764)
SET @mat_WP0764 = (SELECT material_id FROM material WHERE qm_code = 'WP0764');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0764', @mat_WP0764, '张', '张,包', 0.020000)
ON DUPLICATE KEY UPDATE base_unit = '张', inventory_units = '张,包', unit_price = 0.020000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0764);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '包', 500, '张', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0764;

-- 外卖安心贴 (qm=WP0752)
SET @mat_WP0752 = (SELECT material_id FROM material WHERE qm_code = 'WP0752');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0752', @mat_WP0752, '卷', '卷', 22.000000)
ON DUPLICATE KEY UPDATE base_unit = '卷', inventory_units = '卷', unit_price = 22.000000;

-- 云南风物杯套 (qm=WP0735)
SET @mat_WP0735 = (SELECT material_id FROM material WHERE qm_code = 'WP0735');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0735', @mat_WP0735, '张', '张,捆,件,g', 0.160000)
ON DUPLICATE KEY UPDATE base_unit = '张', inventory_units = '张,捆,件,g', unit_price = 0.160000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0735);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 50, '张', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0735;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 1000, '张', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0735;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '张', 4.8, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0735;

-- 贴纸（新店用） (qm=WP0720)
SET @mat_WP0720 = (SELECT material_id FROM material WHERE qm_code = 'WP0720');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0720', @mat_WP0720, '张', '张,捆', 1.200000)
ON DUPLICATE KEY UPDATE base_unit = '张', inventory_units = '张,捆', unit_price = 1.200000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0720);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 50, '张', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0720;

-- 牛油果限定公仔 (qm=WP0693)
SET @mat_WP0693 = (SELECT material_id FROM material WHERE qm_code = 'WP0693');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0693', @mat_WP0693, '个', '个,袋', 4.000000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,袋', unit_price = 4.000000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0693);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '袋', 100, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0693;

-- 牛油果杯套 (qm=WP0676)
SET @mat_WP0676 = (SELECT material_id FROM material WHERE qm_code = 'WP0676');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0676', @mat_WP0676, '张', '张,捆,件,g', 0.160000)
ON DUPLICATE KEY UPDATE base_unit = '张', inventory_units = '张,捆,件,g', unit_price = 0.160000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0676);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 50, '张', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0676;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 1000, '张', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0676;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '张', 4.8, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0676;

-- 苦瓜（原材料） (qm=WP0647)
SET @mat_WP0647 = (SELECT material_id FROM material WHERE qm_code = 'WP0647');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0647', @mat_WP0647, 'g', 'g,kg', 0.010000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.010000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0647);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0647;

-- 牛油果鲜果 (qm=WP0646)
SET @mat_WP0646 = (SELECT material_id FROM material WHERE qm_code = 'WP0646');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0646', @mat_WP0646, 'g', 'g,kg', 0.030000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.030000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0646);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0646;

-- 大象冰箱贴（亚克力） (qm=WP0608)
SET @mat_WP0608 = (SELECT material_id FROM material WHERE qm_code = 'WP0608');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0608', @mat_WP0608, '个', '个', 3.000000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个', unit_price = 3.000000;

-- 玫瑰杯套 (qm=WP0579)
SET @mat_WP0579 = (SELECT material_id FROM material WHERE qm_code = 'WP0579');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0579', @mat_WP0579, '个', '个,捆,件,g', 0.360000)
ON DUPLICATE KEY UPDATE base_unit = '个', inventory_units = '个,捆,件,g', unit_price = 0.360000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0579);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 25, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0579;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 2000, '个', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0579;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '个', 6.6, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0579;

-- 火龙果 (qm=WP0575)
SET @mat_WP0575 = (SELECT material_id FROM material WHERE qm_code = 'WP0575');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0575', @mat_WP0575, 'g', 'g,kg', 0.010000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.010000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0575);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0575;

-- 葡萄 (qm=WP0564)
SET @mat_WP0564 = (SELECT material_id FROM material WHERE qm_code = 'WP0564');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0564', @mat_WP0564, 'g', 'g,kg', 0.016000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.016000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0564);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0564;

-- 芒果 (qm=WP0562)
SET @mat_WP0562 = (SELECT material_id FROM material WHERE qm_code = 'WP0562');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0562', @mat_WP0562, 'g', 'g,kg', 0.010500)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.010500;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0562);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0562;

-- 柠檬 (qm=WP0561)
SET @mat_WP0561 = (SELECT material_id FROM material WHERE qm_code = 'WP0561');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0561', @mat_WP0561, 'g', 'g,kg', 0.015000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.015000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0561);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0561;

-- 橄榄鲜果 (qm=WP0559)
SET @mat_WP0559 = (SELECT material_id FROM material WHERE qm_code = 'WP0559');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0559', @mat_WP0559, 'g', 'g,kg', 0.014000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.014000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0559);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0559;

-- 橙子 (qm=WP0558)
SET @mat_WP0558 = (SELECT material_id FROM material WHERE qm_code = 'WP0558');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0558', @mat_WP0558, 'g', 'g,kg', 0.008500)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.008500;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0558);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0558;

-- 羽衣甘蓝（原料） (qm=WP0548)
SET @mat_WP0548 = (SELECT material_id FROM material WHERE qm_code = 'WP0548');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0548', @mat_WP0548, 'g', 'g,kg', 0.010000)
ON DUPLICATE KEY UPDATE base_unit = 'g', inventory_units = 'g,kg', unit_price = 0.010000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0548);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1000, 'g', 1, 'kg', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0548;

-- 80*40小票打印纸(新店使用) (qm=WP0526)
SET @mat_WP0526 = (SELECT material_id FROM material WHERE qm_code = 'WP0526');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0526', @mat_WP0526, '卷', '卷,件', 1.500000)
ON DUPLICATE KEY UPDATE base_unit = '卷', inventory_units = '卷,件', unit_price = 1.500000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0526);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 200, '卷', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0526;

-- 防水效期贴 (qm=WP0499)
SET @mat_WP0499 = (SELECT material_id FROM material WHERE qm_code = 'WP0499');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0499', @mat_WP0499, '张', '张,卷', 0.036000)
ON DUPLICATE KEY UPDATE base_unit = '张', inventory_units = '张,卷', unit_price = 0.036000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0499);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '卷', 500, '张', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0499;

-- logo标签纸 (qm=WP0486)
SET @mat_WP0486 = (SELECT material_id FROM material WHERE qm_code = 'WP0486');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0486', @mat_WP0486, '卷', '卷,条,件', 5.000000)
ON DUPLICATE KEY UPDATE base_unit = '卷', inventory_units = '卷,条,件', unit_price = 5.000000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0486);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '条', 5, '卷', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0486;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '件', 100, '卷', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0486;

-- 普洱贴纸 (qm=WP0399)
SET @mat_WP0399 = (SELECT material_id FROM material WHERE qm_code = 'WP0399');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0399', @mat_WP0399, '张', '张,捆,g', 0.100000)
ON DUPLICATE KEY UPDATE base_unit = '张', inventory_units = '张,捆,g', unit_price = 0.100000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0399);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 100, '张', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0399;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '张', 1.2, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0399;

-- 牛油果贴纸 (qm=WP0396)
SET @mat_WP0396 = (SELECT material_id FROM material WHERE qm_code = 'WP0396');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0396', @mat_WP0396, '张', '张,捆,g', 0.100000)
ON DUPLICATE KEY UPDATE base_unit = '张', inventory_units = '张,捆,g', unit_price = 0.100000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0396);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 100, '张', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0396;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '张', 2, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0396;

-- 滇橄榄贴纸 (qm=WP0395)
SET @mat_WP0395 = (SELECT material_id FROM material WHERE qm_code = 'WP0395');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0395', @mat_WP0395, '张', '张,捆,g', 0.200000)
ON DUPLICATE KEY UPDATE base_unit = '张', inventory_units = '张,捆,g', unit_price = 0.200000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0395);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 100, '张', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0395;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '张', 2.4, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0395;

-- 滇红贴纸 (qm=WP0388)
SET @mat_WP0388 = (SELECT material_id FROM material WHERE qm_code = 'WP0388');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0388', @mat_WP0388, '张', '张,捆,g', 0.100000)
ON DUPLICATE KEY UPDATE base_unit = '张', inventory_units = '张,捆,g', unit_price = 0.100000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0388);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 100, '张', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0388;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '张', 1.2, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0388;

-- 柠檬茶杯套 (qm=WP0332)
SET @mat_WP0332 = (SELECT material_id FROM material WHERE qm_code = 'WP0332');

INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price)
VALUES ('R_WP0332', @mat_WP0332, '张', '张,捆,g', 0.240000)
ON DUPLICATE KEY UPDATE base_unit = '张', inventory_units = '张,捆,g', unit_price = 0.240000;

DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = @mat_WP0332);
INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '捆', 50, '张', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0332;

INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)
SELECT rule_id, 1, '张', 4.8, 'g', 'unit'
FROM material_inventory_rule WHERE material_id = @mat_WP0332;

-- ========== 同步未提交任务快照 ==========
UPDATE task_zone_material tzm
JOIN task t ON t.id = tzm.task_id
JOIN material_inventory_rule r ON r.material_id COLLATE utf8mb4_unicode_ci = tzm.material_id
SET tzm.base_unit_snapshot = r.base_unit, tzm.unit_price_snapshot = r.unit_price
WHERE t.status IN ('not_started', 'in_progress');

COMMIT;