-- ============================================================
-- 修复：督导马迎峰显示名精简（"【 实习督导】马迎峰15508893667" → "马迎峰"）
--
-- 同步 upsert 会用 admin_permission.name 覆盖
--   ssa.admin_name / store_info.supervisor_name，
-- 所以只需改 admin_permission，但每日 2 点自动同步前
-- 报表/统计仍显示长名 → 这里一次性把三处都改掉，立即生效。
--
-- 执行：在 store_inventory（生产库）执行
-- 验证：SELECT name FROM admin_permission WHERE id=53;  → 马迎峰
--       SELECT supervisor_name, COUNT(*) FROM store_info
--       WHERE supervisor_name='马迎峰' GROUP BY supervisor_name;  → 22
-- ============================================================

-- ---------- 1) admin_permission 显示名精简 ----------
UPDATE admin_permission
SET name = '马迎峰'
WHERE id = 53
  AND name LIKE '【 实习督导】马迎峰%';

-- ---------- 2) ssa 活跃行显示名同步（立即生效，不等自动同步） ----------
UPDATE supervisor_store_access
SET admin_name = '马迎峰'
WHERE del_flag = 0
  AND admin_name LIKE '【 实习督导】马迎峰%';

-- ---------- 3) store_info.supervisor_name 同步 ----------
UPDATE store_info
SET supervisor_name = '马迎峰'
WHERE del_flag = 0
  AND supervisor_name LIKE '【 实习督导】马迎峰%';
