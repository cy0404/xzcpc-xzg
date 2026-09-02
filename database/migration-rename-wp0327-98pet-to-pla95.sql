-- ============================================================
-- WP0327 物料改名：98PET杯+盖 → 95PET杯+盖（2026-08-31）
-- material_id = cmpdj8kzh01vh3pmiw3qbsvee（qm_code=WP0327）
--
-- 任务快照是隔离存储的，改名不会自动渗透到已提交/进行中任务，
-- 故同步更新全部快照表，保证门店端和管理端显示一致：
--   ① task_zone_material      任务分区物料快照（含已录数据，只改名不动数量）
--   ② task_material_summary   已提交任务汇总快照（6 家改回进行中后重新提交时
--                             会从 tzm 重建，此处更新是兜底）
--   ③ template_zone_material  模板快照（保证之后新建任务也是新名）
--   ④ material                主数据（⚠ 会被每日 xinfo 同步覆盖，需源头同步改名）
-- ⚠ 若上游（xinfo/企迈）商品名未改，①-③ 会在下次同步后与 ④ 不一致
-- ============================================================

UPDATE task_zone_material
SET material_name = '95PET杯+盖'
WHERE material_id = 'cmpdj8kzh01vh3pmiw3qbsvee' AND del_flag = 0;

UPDATE task_material_summary
SET material_name = '95PET杯+盖'
WHERE material_id = 'cmpdj8kzh01vh3pmiw3qbsvee' AND del_flag = 0;

UPDATE template_zone_material
SET material_name = '95PET杯+盖'
WHERE material_id = 'cmpdj8kzh01vh3pmiw3qbsvee' AND del_flag = 0;

UPDATE material
SET material_name = '95PET杯+盖'
WHERE material_id = 'cmpdj8kzh01vh3pmiw3qbsvee';

-- 核对：改名行数（应 191 + 4 已提交 summary + 模板快照若干 + 1）
SELECT 'task_zone_material' AS tbl, COUNT(*) AS cnt FROM task_zone_material WHERE material_id = 'cmpdj8kzh01vh3pmiw3qbsvee' AND del_flag = 0
UNION ALL SELECT 'task_material_summary', COUNT(*) FROM task_material_summary WHERE material_id = 'cmpdj8kzh01vh3pmiw3qbsvee' AND del_flag = 0
UNION ALL SELECT 'template_zone_material', COUNT(*) FROM template_zone_material WHERE material_id = 'cmpdj8kzh01vh3pmiw3qbsvee' AND del_flag = 0
UNION ALL SELECT 'material', COUNT(*) FROM material WHERE material_id = 'cmpdj8kzh01vh3pmiw3qbsvee';
