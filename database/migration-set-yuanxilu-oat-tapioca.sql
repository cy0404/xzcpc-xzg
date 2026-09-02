-- =============================================================
-- 园西路门店 8月月盘：燕麦龙珠（半成品）盘点数量改为 13000g
-- 适用：生产库 store_inventory（用户手动执行，勿自动运行）
-- 说明：改的是任务快照录入数量 task_zone_material.input_qty（基础单位 g）
--       改完后需在任务提交后重算差异才会生效（recalculate?taskMonth=2026-08）
-- =============================================================

-- ① 预览：园西路 8月月盘任务里的燕麦龙珠（半成品 WP0648）当前录入值
--    注意：物料名有多个历史变体（燕麦龙珠 / 燕麦龙珠（本成品）/ 燕麦龙珠（半成品）），
--    但"燕麦龙珠（原材料）"是原料 WP0631，不能动，故 NOT LIKE '%原材料%' 排除
SELECT tzm.id          AS 明细id,
       t.id            AS 任务id,
       t.store_name    AS 门店,
       tz.zone_name    AS 分区,
       tzm.material_id AS 物料id,
       m.qm_code       AS 编码,
       tzm.material_name AS 物料名,
       tzm.inventory_unit AS 盘点单位,
       tzm.input_qty   AS 当前录入数量,
       tzm.base_unit_snapshot AS 基础单位快照,
       tzm.conversion_snapshot AS 折算快照,
       tzm.input_original_qty AS 原始录入数,
       tzm.input_original_unit AS 原始录入单位,
       tzm.input_mode   AS 录入模式,
       tzm.base_qty     AS 基础数量,
       tzm.unit_inputs AS 多单位录入,
       tzm.input_status AS 录入状态
FROM task_zone_material tzm
JOIN task t ON t.id = tzm.task_id
JOIN task_zone tz ON tz.id = tzm.task_zone_id AND tz.del_flag = 0
LEFT JOIN material m ON m.material_id COLLATE utf8mb4_unicode_ci = tzm.material_id
WHERE t.task_month = '2026-08'
  AND t.task_type = 'monthly'
  AND t.del_flag = 0
  AND t.store_name LIKE '%园西路%'
  AND tzm.material_name LIKE '%燕麦龙珠%'
  AND tzm.material_name NOT LIKE '%原材料%'
  AND tzm.del_flag = 0;

-- ② 执行修改：统一改为 13000g（13kg）
--    店长原录 kg=13000 → 系统折算 base_qty=13,000,000g（13吨，错误）；
--    目标 13000g：input_qty/base_qty/input_original_qty 全置 13000（基础单位 g），
--    unit_inputs 改为 {"g":"13000"} 使页面显示"13000g"而非"13000kg"
UPDATE task_zone_material tzm
JOIN task t ON t.id = tzm.task_id
SET tzm.input_qty = 13000,
    tzm.base_qty = 13000,
    tzm.input_original_qty = 13000,
    tzm.input_original_unit = 'g',
    tzm.unit_inputs = '{"g":"13000"}'
WHERE t.task_month = '2026-08'
  AND t.task_type = 'monthly'
  AND t.del_flag = 0
  AND t.store_name LIKE '%园西路%'
  AND tzm.material_name LIKE '%燕麦龙珠%'
  AND tzm.material_name NOT LIKE '%原材料%'
  AND tzm.del_flag = 0;

-- ③ 核对：执行后应显示 input_qty = base_qty = 13000、unit_inputs = {"g":"13000"}（且编码为 WP0648，无 WP0631 原材料行）
SELECT tzm.id, t.store_name, tz.zone_name, m.qm_code AS 编码,
       tzm.material_name, tzm.input_qty, tzm.base_qty,
       tzm.input_original_qty, tzm.input_original_unit, tzm.unit_inputs, tzm.input_status
FROM task_zone_material tzm
JOIN task t ON t.id = tzm.task_id
JOIN task_zone tz ON tz.id = tzm.task_zone_id AND tz.del_flag = 0
LEFT JOIN material m ON m.material_id COLLATE utf8mb4_unicode_ci = tzm.material_id
WHERE t.task_month = '2026-08'
  AND t.task_type = 'monthly'
  AND t.del_flag = 0
  AND t.store_name LIKE '%园西路%'
  AND tzm.material_name LIKE '%燕麦龙珠%'
  AND tzm.material_name NOT LIKE '%原材料%'
  AND tzm.del_flag = 0;
