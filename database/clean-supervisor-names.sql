-- ============================================================
-- 清理 admin_permission 中带手机号/实习字样的督导名
-- 背景：同步逻辑写库用 admin_permission.name（本地名），但该表历史数据
--       混入了企迈格式名字（带手机号/岗位字样），导致督导名不干净
-- 处理：仅清理 2 位督导（其他 7 位已是干净名）
-- 执行后：重新触发督导同步（apply=true），auto 行自动覆盖为干净名
-- 用法：在生产库（store_inventory）执行
-- ============================================================

-- 1) 清理前确认
SELECT id, name, user_id FROM admin_permission WHERE user_id IN ('bd157g8d', '114d9b68');

-- 2) 更新为干净名（飞书真实姓名）
UPDATE admin_permission SET name = '马迎峰' WHERE user_id = 'bd157g8d';
UPDATE admin_permission SET name = '唐敏'   WHERE user_id = '114d9b68';

-- 3) 清理后确认
SELECT id, name, user_id FROM admin_permission WHERE user_id IN ('bd157g8d', '114d9b68');

-- ============================================================
-- 改完后重新触发同步覆盖门店侧督导名：
--   curl -X POST "http://<生产域名>/api/public/supervisor-sync/trigger?apply=true"
-- 同步幂等：auto 行直接覆盖更新，无需先清数据
-- ============================================================
