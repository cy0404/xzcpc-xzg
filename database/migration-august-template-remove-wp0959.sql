-- ============================================================
-- 8 月模板：删除 WP0959 预制芭乐汁（新品）快照
-- 背景：模板中有两条同名「预制芭乐汁」——WP0857（老品 sort=28）与
--       WP0959（新品 sort=172，0.0288 元/g），删除新品 WP0959，
--       保留老品 WP0857（0.024 元/g）
-- 执行方式：在 store_inventory_test 库执行（逻辑删除，可回滚：del_flag 改回 0）
-- 说明：仅删模板快照，物料/规则不动（WP0959 规则 MR00001417 仍在库中）
-- ============================================================

-- 删除模板快照（del_flag=1 逻辑删除，与系统删除行为一致）
UPDATE template_zone_material
SET del_flag = 1
WHERE zone_id = 48
  AND material_id = 'cmpdoxazk0ij33nc8l88c592'
  AND del_flag = 0;

-- 核对：应剩 180 条快照，且「预制芭乐汁」仅剩 WP0857 老品
SELECT COUNT(*) AS item_cnt FROM template_zone_material
WHERE zone_id = 48 AND del_flag = 0;

SELECT tzm.material_id, tzm.material_name, tzm.spec, tzm.sort_no
FROM template_zone_material tzm
WHERE tzm.zone_id = 48 AND tzm.del_flag = 0 AND tzm.material_name = '预制芭乐汁';
