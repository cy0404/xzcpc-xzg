-- ============================================================
-- 8 月模板：删除停用的外卖安心贴快照（WP0752 卷装）
-- 背景：模板中有两条「外卖安心贴」——WP0752（卷装，停用 del_flag=1）与
--       WP0966（张装，正常），保留张装，删除停用卷装（sort_no=136）
-- 执行方式：在 store_inventory_test 库执行（逻辑删除，可回滚：del_flag 改回 0）
-- ============================================================

-- 删除模板快照（del_flag=1 逻辑删除，与系统删除行为一致）
UPDATE template_zone_material tzm
JOIN template_zone tz ON tz.id = tzm.zone_id
JOIN template t ON t.id = tz.template_id
SET tzm.del_flag = 1
WHERE t.template_name = '8月盘点模板'
  AND tzm.material_id = 'cmpdj8ieo00x13pmixxbninbr'
  AND tzm.del_flag = 0;

-- 核对：应剩 181 条快照，且仅剩 WP0966 张装一条外卖安心贴
SELECT COUNT(*) AS item_cnt FROM template_zone_material tzm
JOIN template_zone tz ON tz.id = tzm.zone_id
JOIN template t ON t.id = tz.template_id
WHERE t.template_name = '8月盘点模板' AND tzm.del_flag = 0;

SELECT tzm.material_name, tzm.spec, tzm.sort_no
FROM template_zone_material tzm
JOIN template_zone tz ON tz.id = tzm.zone_id
JOIN template t ON t.id = tz.template_id
WHERE t.template_name = '8月盘点模板' AND tzm.del_flag = 0 AND tzm.material_name = '外卖安心贴';
