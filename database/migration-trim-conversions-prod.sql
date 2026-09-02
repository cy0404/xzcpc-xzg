-- ============================================================
-- 换算关系精简（生产库 store_inventory）
-- 原则：只保留"盘点单位 → 基础单位"的直接换算行，删除中间链
--       （to_unit ≠ base_unit 的行），录入时每个盘点单位一步换算到 base
-- 处理：1) 逻辑删除 7 个物料共 11 条中间链（del_flag=1 可回滚）
--       2) 补 4 条缺失直接行（NOT EXISTS 幂等）
-- 不动：WP0486（base=卷，现有行全部 to=卷 已是直接行）、
--       WP0922（盘点=g,包 无中间链）、WP0971（已是最简）
-- 提示：WP0486 base_unit 测试库=条/生产库=卷，口径不一致，另行确认
-- ============================================================

-- 1) 删除中间链（逻辑删除，幂等）
UPDATE material_conversion_rule c
JOIN (
  SELECT 'MR00001317' AS rule_id, '件' AS from_unit, '包' AS to_unit
  UNION ALL SELECT 'MR00001333', '瓶', 'kg'
  UNION ALL SELECT 'MR00001333', '件', '瓶'
  UNION ALL SELECT 'MR00001333', '件', 'kg'
  UNION ALL SELECT 'MR00001340', '包', 'kg'
  UNION ALL SELECT 'MR00001340', '件', '包'
  UNION ALL SELECT 'MR00001340', '件', 'kg'
  UNION ALL SELECT 'MR00001341', '件', '捆'
  UNION ALL SELECT 'MR00001343', '件', '捆'
  UNION ALL SELECT 'MR00001344', '件', '瓶'
  UNION ALL SELECT 'MR00001346', '件', '卷'
) t ON t.rule_id = c.rule_id AND t.from_unit = c.from_unit AND t.to_unit = c.to_unit
SET c.del_flag = 1
WHERE c.del_flag = 0;

-- 2) 补缺失直接行（幂等：NOT EXISTS 防重）
INSERT INTO material_conversion_rule (rule_id, conversion_type, from_quantity, from_unit, to_quantity, to_unit, sort_no, del_flag)
SELECT v.rule_id, v.conversion_type, v.from_quantity, v.from_unit, v.to_quantity, v.to_unit, v.sort_no, 0 FROM (
  SELECT 'MR00001333' AS rule_id, 'unit' AS conversion_type, 1.0 AS from_quantity, '瓶' AS from_unit, 1000.0 AS to_quantity, 'g' AS to_unit, 1 AS sort_no
  UNION ALL SELECT 'MR00001333', 'unit', 1.0, '件', 6000.0, 'g', 2
  UNION ALL SELECT 'MR00001340', 'unit', 1.0, '包', 1000.0, 'g', 1
  UNION ALL SELECT 'MR00001340', 'unit', 1.0, '件', 20000.0, 'g', 2
) v
WHERE NOT EXISTS (SELECT 1 FROM material_conversion_rule c2
  WHERE c2.rule_id = v.rule_id AND c2.from_unit = v.from_unit AND c2.to_unit = v.to_unit AND c2.del_flag = 0);

-- 3) 核对：7 个物料应无 to_unit != base_unit 的有效行（应无结果）
SELECT m.qm_code, m.material_name, r.base_unit, c.from_unit, c.to_unit, c.from_quantity, c.to_quantity
FROM material m
JOIN material_inventory_rule r ON r.material_id = m.material_id COLLATE utf8mb4_unicode_ci AND r.del_flag = 0
JOIN material_conversion_rule c ON c.rule_id = r.rule_id AND c.del_flag = 0
WHERE m.qm_code IN ('WP0919','WP0947','WP0960','WP0961','WP0963','WP0964','WP0966')
  AND c.to_unit != r.base_unit;

-- 4) 核对：每物料有效换算行（应为 盘点单位数-1）
SELECT m.qm_code, r.base_unit, r.inventory_units,
       GROUP_CONCAT(CONCAT(c.from_unit,'→',c.to_unit,'(',c.to_quantity,')') ORDER BY c.sort_no SEPARATOR ', ') AS convs
FROM material m
JOIN material_inventory_rule r ON r.material_id = m.material_id COLLATE utf8mb4_unicode_ci AND r.del_flag = 0
JOIN material_conversion_rule c ON c.rule_id = r.rule_id AND c.del_flag = 0
WHERE m.qm_code IN ('WP0919','WP0947','WP0960','WP0961','WP0963','WP0964','WP0966')
GROUP BY m.qm_code, r.base_unit, r.inventory_units
ORDER BY m.qm_code;
