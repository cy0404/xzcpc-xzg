-- ============================================================
-- 修复：长水机场店/卫星厅 督导双挂（罗正彩 manual 行残留）
--
-- 现象：统计报表「督导完成度统计」董建明只有 19（应 21），罗正彩却有 3
-- 根因：
--   1) 长水机场店（store_code=347）+ 长水机场卫星厅（353）
--      存在两条活跃 ssa 行：罗正彩 manual（历史手工维护，已过时）
--      + 董建明 auto（接口当前确认，正确）
--   2) 督导同步收敛只软删 auto 行（防误删手工数据的保护），
--      这 2 条罗正彩 manual 行是历史遗留错误数据，同步永远不碰
--   3) 统计报表按 store_id → ssa 活跃行取第一条 admin_name 分组，
--      双挂时取到罗正彩 → 这 2 个任务记到罗正彩名下
--
-- 执行：在 store_inventory（生产库）执行
-- 验证：SELECT ... WHERE store_id IN (2家) AND del_flag=0
--       应只剩董建明 auto 行；统计报表董建明恢复 21
-- ============================================================

-- 软删罗正彩在长水机场店/卫星厅的 manual 行（历史错误数据，接口已确认归董建明）
UPDATE supervisor_store_access
SET del_flag = 1
WHERE open_id = 'ou_dc3311f282ba7b28ce74bfccddb96aa1'
  AND store_id IN ('cmpnw039p02f33pk3o3a4hwqg',   -- 长水机场店 (347)
                   'cmpwcx3oo015k3pq3bivxc6e0')   -- 长水机场卫星厅 (353)
  AND source = 'manual'
  AND del_flag = 0;

-- ============================================================
-- 执行后验证（应返回 2 行，全部为董建明）：
--   SELECT si.store_code, si.store_name, s.admin_name, s.source
--   FROM supervisor_store_access s JOIN store_info si ON s.store_id = si.store_id
--   WHERE s.store_id IN ('cmpnw039p02f33pk3o3a4hwqg','cmpwcx3oo015k3pq3bivxc6e0')
--     AND s.del_flag = 0;
-- ============================================================
