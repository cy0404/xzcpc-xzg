-- ============================================================
-- 园西路店 8 月任务垃圾袋多录一个 0（2026-08-31）
-- 象子茶铺茶园西路店（task=712，已提交）
-- 现状：店长录 "10袋"（append 记录 2026-08-31 14:11:48）
--   1袋=20包 → 200包；历史 6月 2袋 / 7月 7袋，应为 1袋=20包
-- 修正：10袋 → 1袋（200包 → 20包），金额同步重算
--   差额 = (200-20)包 × 20元/包 = 3600元
--   total_amount 594693.66 - 3600 = 591093.66
-- ============================================================

-- ① 任务分区物料快照：数量 + 单位输入
UPDATE task_zone_material
SET input_qty = 20.0,
    base_qty = 20.0,
    unit_inputs = '{"包":"","袋":"1"}'
WHERE id = 127739;

-- ② 已提交任务汇总快照
UPDATE task_material_summary
SET total_qty = 20.0,
    unit_breakdown = '[{"unit":"包","qty":20,"isWeight":false}]'
WHERE id = 58378;

-- ③ 任务总金额重算
UPDATE task
SET total_amount = 591093.66
WHERE id = 712 AND del_flag = 0;

-- 核对：垃圾袋行应为 1袋 20包，金额为 591093.66
SELECT input_qty AS qty, unit_inputs AS ui FROM task_zone_material WHERE id = 127739;
SELECT total_qty AS summary_qty, unit_breakdown AS breakdown FROM task_material_summary WHERE id = 58378;
SELECT total_amount AS task_amount FROM task WHERE id = 712;
