-- =============================================================
-- 8月月盘任务延期：未提交门店截止时间统一延至 2026-09-01 18:00
-- 适用：生产库 store_inventory（用户手动执行，勿自动运行）
-- 说明：仅更新 monthly 月盘任务；weekly 周盘 deadline 由周盘点日推导，不在此范围
-- =============================================================

-- ① 预览受影响记录（应只有未提交的 8 月月盘任务）
SELECT id, store_name, task_month, task_type, status, deadline
FROM task
WHERE task_month = '2026-08'
  AND task_type = 'monthly'
  AND status IN ('not_started', 'in_progress')
  AND del_flag = 0;

-- ② 执行延期
UPDATE task
SET deadline = '2026-09-01 18:00:00'
WHERE task_month = '2026-08'
  AND task_type = 'monthly'
  AND status IN ('not_started', 'in_progress')
  AND del_flag = 0;

-- ③ 核对：受影响行数应等于 ① 的行数
SELECT id, store_name, task_month, status, deadline
FROM task
WHERE task_month = '2026-08'
  AND task_type = 'monthly'
  AND status IN ('not_started', 'in_progress')
  AND del_flag = 0;
