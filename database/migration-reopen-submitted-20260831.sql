-- ============================================================
-- 已提交 8 月任务改回「进行中」（2026-08-31）
-- 背景：换算链修正后，已提交任务的 tzm 旧值需门店重新提交生效
-- 涉及 6 家：676 测试门店 / 795 龙陵盛和雅苑 / 811 翰林大观 /
--           816 施甸交通路 / 748 普洱创基 / 738 漾濞
-- 改回内容（对齐 MpTaskServiceImpl.submit 提交时改动的字段）：
--   status: submitted → in_progress
--   submitted_at / submitted_by / total_amount → NULL（重新提交时重算）
-- ⚠ 执行顺序：先执行 tzm 修正 SQL（migration-fix-entered-conversions-submitted*.sql）
--   再执行本脚本，然后让门店重新提交
-- ⚠ 重新提交必须早于 deadline（2026-09-01 05:00），逾期提交会被
--   submit() 自动置为 overdue 并拒绝；来不及请顺延（见文末可选语句）
-- ============================================================

UPDATE task
SET status = 'in_progress',
    submitted_at = NULL,
    submitted_by = NULL,
    total_amount = NULL
WHERE id IN (676, 795, 811, 816, 748, 738)
  AND del_flag = 0;

-- 核对：应全部变为 in_progress，submitted_at 为 NULL
SELECT id, store_name, status, submitted_at, total_amount
FROM task WHERE id IN (676, 795, 811, 816, 748, 738) AND del_flag = 0;

-- [可选] 若需顺延截止时间（重新提交晚于 09-01 05:00 时启用）：
-- UPDATE task SET deadline = '2026-09-01 12:00:00'
-- WHERE id IN (676, 795, 811, 816, 748, 738) AND del_flag = 0;
