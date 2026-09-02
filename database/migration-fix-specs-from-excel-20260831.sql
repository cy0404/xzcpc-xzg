-- ============================================================
-- 月盘品项_合并规格.xlsx 规格对齐（2026-08-31）
-- 口径：以 Excel 月盘清单为准，统一生产 material.spec
-- 23 条 = 11 条写法统一 + 6 条口径不同(⚠需确认) + 5 条生产缺失 + 1 条生产查不到
-- 已剔除（生产=xinfo源，Excel 系旧值/笔误，不改）：
--   WP0486 logo标签纸（生产=xinfo=5卷/条*20/件，Excel 6条/件 误）
--   WP0304 小料勺（生产=xinfo=100个/包*50包/件=5000个/件，Excel 10包/件 误；
--          另：企迈已改名 小木勺子(奶油专用)→小料勺，生产同步未开仍为旧名，见第 3 部分）
-- 幂等：按 qm_code 匹配，重复执行结果不变
-- 用法：核对 ⚠ 项 → 确认后整文件执行（生产 + 测试库）
-- 执行后：模板/任务快照 spec 已同步（第 2 部分）；盘点规则/换算规则不受影响（spec 仅展示用）
-- ============================================================

-- ------------------------------------------------------------
-- 1) material.spec 更新（22 条；WP0959 见文末，生产查不到；WP0486/WP0304 已剔除不改）
--    A 组 11 条仅写法统一、每件总量不变；⚠ 项已按 Excel 生成，改前请先核对
-- ------------------------------------------------------------
UPDATE material
SET spec = CASE qm_code
      -- ---- A 组：写法统一（总量一致，11 条）----
      WHEN 'WP0302' THEN '25个/捆*20捆/件'   -- 原 500个/件
      WHEN 'WP0312' THEN '50个/捆*20捆/件'   -- 原 1000个/件
      WHEN 'WP0313' THEN '100支/包*20包/件'  -- 原 100支/20包/件
      WHEN 'WP0314' THEN '25个/捆*20捆/件'   -- 原 500个/件
      WHEN 'WP0327' THEN '50个/捆*20捆/件'   -- 原 1000个/件
      WHEN 'WP0675' THEN '50个/捆*20捆/件'   -- 原 1000个/件
      WHEN 'WP0487' THEN '50副/包*10包/件S码' -- 原 50副/包*10包/S码
      WHEN 'WP0602' THEN '50副/包*10包/件M码' -- 原 50副/包*10包/M码
      WHEN 'WP0603' THEN '50副/包*10包/件L码' -- 原 50副/包*10包/L码
      WHEN 'WP0646' THEN '1000g/1kg'          -- 原 1000g/kg
      WHEN 'WP0647' THEN '1000g/1kg'          -- 原 1000g/kg
      -- ---- C 组：单层/口径不同（⚠ 需确认）----
      WHEN 'WP0492' THEN '6包/份*8份/件'      -- ⚠ Excel 按包/份/件；生产原 1000g/包
      WHEN 'WP0500' THEN '20盒/包'            -- ⚠ Excel=20盒/包；生产原 100盒/包
      WHEN 'WP0584' THEN '50g/包'             -- ⚠ Excel=50g/包；生产原 1000g/kg
      WHEN 'WP0601' THEN '500ml/瓶*6瓶/箱'    -- ⚠ Excel 按瓶/箱；生产原 1kg/瓶
      WHEN 'WP0674' THEN '200片/瓶*6瓶/件'    -- ⚠ Excel=200片/瓶；生产原 100片/瓶
      WHEN 'WP0852' THEN '25个/条*20条/件'    -- ⚠ Excel 按条；生产原 25个/提（缺件规格）
      -- ---- D 组：生产规格缺失/过简，按 Excel 补全（5 条）----
      WHEN 'WP0332' THEN '50张/捆*20捆/件'    -- 原 50张/捆
      WHEN 'WP0430' THEN '6卷/件'             -- 原 卷
      WHEN 'WP0494' THEN '50个/包*20包/件'    -- 原 包
      WHEN 'WP0550' THEN '1000g/kg'           -- 原 空
      WHEN 'WP0972' THEN '1000g/kg'           -- 原 kg
      ELSE spec
    END
WHERE qm_code IN ('WP0302','WP0312','WP0313','WP0314','WP0327','WP0675',
                  'WP0487','WP0602','WP0603','WP0646','WP0647',
                  'WP0492','WP0500','WP0584','WP0601','WP0674','WP0852',
                  'WP0332','WP0430','WP0494','WP0550','WP0972')
  AND del_flag = 0;

-- 核对（应返回 22 行，新规格值）：
-- SELECT qm_code, spec FROM material WHERE qm_code IN ('WP0645','WP0302','WP0332') AND del_flag = 0;

-- ------------------------------------------------------------
-- 2) 模板/任务/汇总快照 spec 回填（仅本次 25 个物料）
--    collation 不一致，JOIN 显式 COLLATE 防 1267
-- ------------------------------------------------------------
UPDATE template_zone_material tzm
JOIN material m ON m.material_id = tzm.material_id COLLATE utf8mb4_unicode_ci
SET tzm.spec = m.spec
WHERE tzm.del_flag = 0 AND m.del_flag = 0
  AND m.qm_code IN ('WP0302','WP0312','WP0313','WP0314','WP0327','WP0675',
                    'WP0487','WP0602','WP0603','WP0646','WP0647',
                    'WP0492','WP0500','WP0584','WP0601','WP0674','WP0852',
                    'WP0332','WP0430','WP0494','WP0550','WP0972');

UPDATE task_zone_material tzm
JOIN material m ON m.material_id = tzm.material_id COLLATE utf8mb4_unicode_ci
SET tzm.spec = m.spec
WHERE tzm.del_flag = 0 AND m.del_flag = 0
  AND m.qm_code IN ('WP0302','WP0312','WP0313','WP0314','WP0327','WP0675',
                    'WP0487','WP0602','WP0603','WP0646','WP0647',
                    'WP0492','WP0500','WP0584','WP0601','WP0674','WP0852',
                    'WP0332','WP0430','WP0494','WP0550','WP0972');

UPDATE task_material_summary sm
JOIN material m ON m.material_id = sm.material_id COLLATE utf8mb4_unicode_ci
SET sm.spec = m.spec
WHERE sm.del_flag = 0 AND m.del_flag = 0
  AND m.qm_code IN ('WP0302','WP0312','WP0313','WP0314','WP0327','WP0675',
                    'WP0487','WP0602','WP0603','WP0646','WP0647',
                    'WP0492','WP0500','WP0584','WP0601','WP0674','WP0852',
                    'WP0332','WP0430','WP0494','WP0550','WP0972');

-- 核对：SELECT COUNT(*) FROM template_zone_material WHERE del_flag=0 AND spec IN (...);

-- ------------------------------------------------------------
-- 3) WP0304 名称同步：企迈已改名 小木勺子(奶油专用) → 小料勺
--    生产同步未开，名称停留在旧值；快照表（模板/任务）名称需在后台
--    重新保存模板/模板变更事件触发后才会刷新，本脚本只改主表
-- ------------------------------------------------------------
UPDATE material SET material_name = '小料勺' WHERE qm_code = 'WP0304' AND del_flag = 0;

-- 核对：SELECT qm_code, material_name FROM material WHERE qm_code='WP0304';

-- ------------------------------------------------------------
-- 4) WP0959 预制芭乐汁：Excel 有、生产 material 表无此编码
--    8 月模板刚移除了它（migration-august-template-remove-wp0959.sql）
--    ⚠ 二选一，需业务确认：
--    a) 若月盘还要用 → 在后台物料管理新增（或确认 xinfo 是否仍产出该编码）
--    b) 若已下架 → Excel 清单这行忽略，无需任何 SQL
-- ------------------------------------------------------------
