-- ============================================================
-- 周盘任务截止时间调整：盘点日 23:59:59 → 订货日当天 05:00
-- 背景：周盘任务改为订货日前一天 9:00 生成，截止 = 订货日当天凌晨 5 点。
-- 存量未提交周盘任务的 deadline 需同步顺延，否则与新规则判重键不一致，
-- 且门店端看到的截止时间仍是旧值。
-- 转换：旧 deadline(盘点日 23:59:59) → (盘点日+1天=订货日) 05:00:00
-- 幂等：TIME(deadline)='23:59:59' 只命中旧格式，可重复执行。
-- ============================================================

UPDATE task
SET deadline = DATE(DATE_ADD(deadline, INTERVAL 1 DAY)) + INTERVAL 5 HOUR
WHERE task_type = 'weekly'
  AND status IN ('not_started', 'in_progress', 'overdue')
  AND TIME(deadline) = '23:59:59';

-- 核对：应无未提交周盘任务再带 23:59:59 截止
-- SELECT id, task_name, deadline FROM task
--  WHERE task_type='weekly' AND status IN ('not_started','in_progress','overdue')
--    AND TIME(deadline) = '23:59:59';
