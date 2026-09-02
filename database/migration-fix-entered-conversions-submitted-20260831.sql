-- ============================================================
-- 已提交 8 月任务补充修正（2026-08-31）
-- 涉及：测试门店(676)、龙陵盛和雅苑(795)、翰林大观(811)、施甸交通路(816)
-- 修正内容：
--   ① task_zone_material.input_qty / base_qty（按 unit_inputs × 当前链）
--   ② task_material_summary.total_qty/original_qty/adjusted_qty + unit_breakdown
-- 差异：inventory_difference 尚未计算（0 行），无需重算；
--       修正后 9/1 差异计算将基于新值。
-- ⚠ 柠檬除胶剂(WP0601) 瓶 行：等实物规格确认后另行处理
-- ============================================================

-- ① task_zone_material 修正
UPDATE task_zone_material
SET input_qty = 1000.0, base_qty = 1000.0
WHERE id = 121316;
-- task=676 测试门店 小料勺 {"个":"","件":"1"} 5000.0 → 1000.0
UPDATE task_zone_material
SET input_qty = 500.0, base_qty = 500.0
WHERE id = 146500;
-- task=816 象子茶铺茶施甸交通路店 新黑糖粉 {"g":"","包":"1"} 1000.0 → 500.0
UPDATE task_zone_material
SET input_qty = 575.0, base_qty = 575.0
WHERE id = 145600;
-- task=811 象子茶铺茶翰林大观店 新黑糖粉 {"g":"","包":"1.15"} 1150.0 → 575.0
UPDATE task_zone_material
SET input_qty = 500.0, base_qty = 500.0
WHERE id = 142720;
-- task=795 象子茶铺茶龙陵盛和雅苑店 新黑糖粉 {"g":"","包":"1"} 1000.0 → 500.0
UPDATE task_zone_material
SET input_qty = 2300.0, base_qty = 2300.0
WHERE id = 121300;
-- task=676 测试门店 新黑糖粉 {"g":"300","包":"4"} 4300.0 → 2300.0
UPDATE task_zone_material
SET input_qty = 65.0, base_qty = 65.0
WHERE id = 145649;
-- task=811 象子茶铺茶翰林大观店 奇亚籽（原材料） {"g":"","包":"0.26"} 260.0 → 65.0
UPDATE task_zone_material
SET input_qty = 250.0, base_qty = 250.0
WHERE id = 142769;
-- task=795 象子茶铺茶龙陵盛和雅苑店 奇亚籽（原材料） {"g":"","包":"1"} 1000.0 → 250.0
UPDATE task_zone_material
SET input_qty = 500.0, base_qty = 500.0
WHERE id = 121349;
-- task=676 测试门店 奇亚籽（原材料） {"g":"","包":"2"} 2000.0 → 500.0

-- ② task_material_summary 修正
UPDATE task_material_summary
SET total_qty = 1000.0, original_qty = 1000.0, adjusted_qty = 1000.0,
    unit_breakdown = CONCAT('[{"unit":"', base_unit, '","qty":', 1000.0, ',"isWeight":false}]')
WHERE task_id = 676 AND material_id = (
  SELECT material_id FROM material WHERE qm_code = 'WP0304' AND del_flag = 0 LIMIT 1);
-- task=676 WP0304 → 1000.0
UPDATE task_material_summary
SET total_qty = 500.0, original_qty = 500.0, adjusted_qty = 500.0,
    unit_breakdown = CONCAT('[{"unit":"', base_unit, '","qty":', 500.0, ',"isWeight":false}]')
WHERE task_id = 816 AND material_id = (
  SELECT material_id FROM material WHERE qm_code = 'WP0334' AND del_flag = 0 LIMIT 1);
-- task=816 WP0334 → 500.0
UPDATE task_material_summary
SET total_qty = 575.0, original_qty = 575.0, adjusted_qty = 575.0,
    unit_breakdown = CONCAT('[{"unit":"', base_unit, '","qty":', 575.0, ',"isWeight":false}]')
WHERE task_id = 811 AND material_id = (
  SELECT material_id FROM material WHERE qm_code = 'WP0334' AND del_flag = 0 LIMIT 1);
-- task=811 WP0334 → 575.0
UPDATE task_material_summary
SET total_qty = 500.0, original_qty = 500.0, adjusted_qty = 500.0,
    unit_breakdown = CONCAT('[{"unit":"', base_unit, '","qty":', 500.0, ',"isWeight":false}]')
WHERE task_id = 795 AND material_id = (
  SELECT material_id FROM material WHERE qm_code = 'WP0334' AND del_flag = 0 LIMIT 1);
-- task=795 WP0334 → 500.0
UPDATE task_material_summary
SET total_qty = 2300.0, original_qty = 2300.0, adjusted_qty = 2300.0,
    unit_breakdown = CONCAT('[{"unit":"', base_unit, '","qty":', 2300.0, ',"isWeight":false}]')
WHERE task_id = 676 AND material_id = (
  SELECT material_id FROM material WHERE qm_code = 'WP0334' AND del_flag = 0 LIMIT 1);
-- task=676 WP0334 → 2300.0
UPDATE task_material_summary
SET total_qty = 65.0, original_qty = 65.0, adjusted_qty = 65.0,
    unit_breakdown = CONCAT('[{"unit":"', base_unit, '","qty":', 65.0, ',"isWeight":false}]')
WHERE task_id = 811 AND material_id = (
  SELECT material_id FROM material WHERE qm_code = 'WP0645' AND del_flag = 0 LIMIT 1);
-- task=811 WP0645 → 65.0
UPDATE task_material_summary
SET total_qty = 250.0, original_qty = 250.0, adjusted_qty = 250.0,
    unit_breakdown = CONCAT('[{"unit":"', base_unit, '","qty":', 250.0, ',"isWeight":false}]')
WHERE task_id = 795 AND material_id = (
  SELECT material_id FROM material WHERE qm_code = 'WP0645' AND del_flag = 0 LIMIT 1);
-- task=795 WP0645 → 250.0
UPDATE task_material_summary
SET total_qty = 500.0, original_qty = 500.0, adjusted_qty = 500.0,
    unit_breakdown = CONCAT('[{"unit":"', base_unit, '","qty":', 500.0, ',"isWeight":false}]')
WHERE task_id = 676 AND material_id = (
  SELECT material_id FROM material WHERE qm_code = 'WP0645' AND del_flag = 0 LIMIT 1);
-- task=676 WP0645 → 500.0
