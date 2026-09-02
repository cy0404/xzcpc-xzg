-- ============================================================
-- 修复：督导唐敏显示名精简（"唐敏 实习督导 15555446828" → "唐敏"）
--
-- 同张坤玉/马迎峰：三处一起改，立即生效，不等凌晨 2 点自动同步。
--
-- 执行：在 store_inventory（生产库）执行
-- 验证：SELECT name FROM admin_permission WHERE id=57;  → 唐敏
--       SELECT supervisor_name, COUNT(*) FROM store_info
--       WHERE supervisor_name='唐敏' GROUP BY supervisor_name;  → 17
-- ============================================================

-- ---------- 1) admin_permission 显示名精简 ----------
UPDATE admin_permission
SET name = '唐敏'
WHERE id = 57
  AND name LIKE '唐敏 实习督导%';

-- ---------- 2) ssa 活跃行显示名同步（立即生效） ----------
UPDATE supervisor_store_access
SET admin_name = '唐敏'
WHERE del_flag = 0
  AND admin_name LIKE '唐敏 实习督导%';

-- ---------- 3) store_info.supervisor_name 同步 ----------
UPDATE store_info
SET supervisor_name = '唐敏'
WHERE del_flag = 0
  AND supervisor_name LIKE '唐敏 实习督导%';
