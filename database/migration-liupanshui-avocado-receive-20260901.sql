-- ============================================================
-- 六盘水牛油果泥报损单 14667：把"未收到货"操作记录改为"已收到货"
-- 执行方式：手动执行（生产库 store_inventory）
-- 说明：保留原"未收到货"那条操作记录（10:55 朱昱桦），直接改为"已收到货"；
--       删除 11:39 SQL 自动新增的"已收到货"日志（不再保留重复记录）
-- ============================================================

-- 1) 未收到货 → 已收到货（保留原记录，只改 action）
UPDATE loss_report_log SET action='receive'
WHERE report_id=14667 AND action='not_receive';

-- 2) 删除之前 SQL 自动新增的 receive 日志（避免两条"已收到货"）
DELETE FROM loss_report_log
WHERE report_id=14667 AND action='receive' AND operator='SQL手动修改';

-- 校验：14667 应只剩 1 条 receive 日志（朱昱桦）
-- SELECT id, report_id, action, operator, remark, created_at
-- FROM loss_report_log WHERE report_id=14667 ORDER BY id;
