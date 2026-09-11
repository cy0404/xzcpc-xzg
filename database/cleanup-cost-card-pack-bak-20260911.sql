-- ==============================================================================================
-- 清理包材迁移的临时备份表
-- ==============================================================================================
--
-- 【删什么】cost_card_item_pack_bak_20260911
--   migration-cost-card-pack-20260911.sql 在 1) 段建的备份，装的是被替换掉的
--   61 行整包「包材」行（每卡 1 行，只有金额、无物料）。
--
-- 【为什么可以删】原始整包金额在别处完整留存，不依赖这张表：
--   database/migration-cost-card-import-20260909.sql 里每张卡的 'PACK','包材' 行
--   就是同一批数据，金额逐一对得上（已核对）：
--       0.5500×20 / 0.5750×3 / 0.5800×4 / 0.5950×2
--       0.6900×2  / 0.7300×6 / 0.7350×22 / 1.2800×2   = 61 行
--   需要回滚包材明细时，从 import 脚本按卡名回放即可。
--
-- 【前置确认】包材迁移已核对通过（2026-09-11）：
--   PACK 明细 212 行 = 179 挂物料（新拆的逐项包材）+ 33 整包（SOP 未覆盖的 17 个产品）
--   与生成器意图逐行一致。
--
-- 【回滚】本脚本不可逆。若执行后仍想要这份备份，用 import 脚本按卡名重新导出即可。
-- ==============================================================================================

-- ---------- 0) 删前确认：表在、且只有 PACK 行 ----------
-- SELECT COUNT(*) AS rows_total, COUNT(DISTINCT card_id) AS cards,
--        SUM(item_type <> 'PACK') AS non_pack_rows
-- FROM cost_card_item_pack_bak_20260911;
--   期望：rows_total = 61，cards = 61，non_pack_rows = 0

-- ---------- 1) 删除 ----------
DROP TABLE IF EXISTS cost_card_item_pack_bak_20260911;

-- ---------- 2) 核对：应返回空结果 ----------
-- SHOW TABLES LIKE 'cost_card_item_pack_bak%';
-- SELECT COUNT(*) AS remaining FROM information_schema.tables
-- WHERE table_schema = DATABASE() AND table_name = 'cost_card_item_pack_bak_20260911';
