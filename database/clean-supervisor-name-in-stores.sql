-- ============================================================
-- 门店侧督导名清理（马迎峰 22 家 + 唐敏 17 家 = 39 家）
-- 背景：admin_permission.name 已改干净，但 store_info.supervisor_name
--       和 supervisor_store_access.admin_name 仍是旧脏名（带手机号）
-- 覆盖前先确认，再执行 UPDATE，最后核对
-- 用法：在生产库（store_inventory）执行
-- ============================================================

-- 1) 覆盖前确认
SELECT COUNT(*) AS bad_admin_name FROM supervisor_store_access
  WHERE source='auto' AND admin_name IN ('【 实习督导】马迎峰15508893667', '唐敏 实习督导 15555446828');
SELECT COUNT(*) AS bad_supervisor_name FROM store_info
  WHERE supervisor_name IN ('【 实习督导】马迎峰15508893667', '唐敏 实习督导 15555446828');

-- 2) 覆盖为干净名（与 admin_permission.name 一致）
UPDATE supervisor_store_access SET admin_name = '马迎峰'
  WHERE source='auto' AND admin_name = '【 实习督导】马迎峰15508893667';
UPDATE supervisor_store_access SET admin_name = '唐敏'
  WHERE source='auto' AND admin_name = '唐敏 实习督导 15555446828';

UPDATE store_info SET supervisor_name = '马迎峰'
  WHERE supervisor_name = '【 实习督导】马迎峰15508893667';
UPDATE store_info SET supervisor_name = '唐敏'
  WHERE supervisor_name = '唐敏 实习督导 15555446828';

-- 3) 覆盖后核对（应均为 0）
SELECT COUNT(*) AS left_bad_admin_name FROM supervisor_store_access
  WHERE source='auto' AND admin_name IN ('【 实习督导】马迎峰15508893667', '唐敏 实习督导 15555446828');
SELECT COUNT(*) AS left_bad_supervisor_name FROM store_info
  WHERE supervisor_name IN ('【 实习督导】马迎峰15508893667', '唐敏 实习督导 15555446828');

-- 4) 抽查：改名后的分布
SELECT supervisor_name, COUNT(*) AS cnt FROM store_info
  WHERE supervisor_name IN ('马迎峰', '唐敏') GROUP BY supervisor_name;
