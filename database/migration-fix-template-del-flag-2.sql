-- ============================================================
-- 8 月盘点模板引用的 6 条物料 del_flag=2 → 全部恢复为 0
-- 背景：生产库 8 月盘点模板 180 条快照中，6 条物料在 material 表
--       del_flag=2（6 月 xinfo 切换时遗留的"彻底删除"标记），
--       模板快照均在用（del_flag=0），需恢复正常状态
-- 处理：6 条全部恢复 del_flag=0（含 350ml玻璃瓶 WP0329，按业务确认）
-- 执行方式：在 store_inventory（生产库）执行
-- ============================================================

-- 1) 6 条全部恢复 del_flag=0
UPDATE material SET del_flag = 0, updated_at = NOW()
WHERE material_id IN (
  'cmpdj8ipk011c3pmia5qhfvpa',  -- WP0664 香茅
  'cmpdj8ihh00y53pmihx6zd0nm',  -- WP0720 贴纸（新店用）
  'cmq28fdbb04ix3pq3h136xymv',  -- WP0550 冷冻橄榄原汁
  'cmq28fb9r03zd3pq3uvwj5o3q',  -- WP0855 释迦果（原材料）
  'cmq28fb2603xe3pq3cgu4lnxp',  -- WP0875 柠檬叶
  'cmq28fex404x53pq3swm6zuha'   -- WP0329 350ml玻璃瓶
);

-- 2) 核对：模板中应无 del_flag=2 的物料（应无结果）
SELECT m.qm_code, m.material_name, m.del_flag
FROM template_zone_material tzm
JOIN template_zone tz ON tz.id = tzm.zone_id
JOIN template t ON t.id = tz.template_id
LEFT JOIN material m ON m.material_id = tzm.material_id COLLATE utf8mb4_unicode_ci
WHERE t.template_name = '8月盘点模板' AND tzm.del_flag = 0 AND m.del_flag = 2;

-- 3) 核对：6 条物料最终状态（应全部为 0）
SELECT m.qm_code, m.material_name, m.del_flag
FROM material m
WHERE m.material_id COLLATE utf8mb4_unicode_ci IN (
  'cmpdj8ipk011c3pmia5qhfvpa','cmpdj8ihh00y53pmihx6zd0nm',
  'cmq28fdbb04ix3pq3h136xymv','cmq28fb9r03zd3pq3uvwj5o3q',
  'cmq28fb2603xe3pq3cgu4lnxp','cmq28fex404x53pq3swm6zuha');
