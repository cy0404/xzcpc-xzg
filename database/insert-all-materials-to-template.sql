-- ============================================================
-- 将全部 del_flag=0 的物料加入「盘点模板无分区」的「所有物料」分区
-- 已存在的跳过，不重复插入
-- ============================================================

USE store_inventory;

-- 插入（inventory_unit 从 material_inventory_rule.base_unit 取值）
INSERT INTO template_zone_material (biz_code, zone_id, material_id, material_name, spec, inventory_unit, sort_no, del_flag, version)
SELECT
    '' AS biz_code,
    46,
    m.material_id,
    m.material_name,
    m.spec,
    COALESCE(r.base_unit, ''),
    0,
    0,
    0
FROM material m
LEFT JOIN material_inventory_rule r ON r.material_id = m.material_id AND r.del_flag = 0
WHERE m.del_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM template_zone_material tzm
      WHERE tzm.zone_id = 46
        AND tzm.material_id = m.material_id
        AND tzm.del_flag = 0
  );

-- 验证
SELECT COUNT(*) AS zone_material_count
FROM template_zone_material
WHERE zone_id = 46 AND del_flag = 0;
