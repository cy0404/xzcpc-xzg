-- ============================================================
-- 恢复 8/27 18:12 批次牛油果泥报损单为「未下载」状态（供重新下载）
-- 执行方式：手动执行（生产库 store_inventory，勿在测试库执行）
-- 影响范围：19 单 × 2 条 download 日志 = 38 条（8/27 全天仅此一批下载日志）
-- 说明：H5 审核页的「已下载」标记由 loss_report_log.action='download' 判断，
--       删除后该 19 单恢复为未下载，可重新下载视频 ZIP
-- ============================================================

DELETE l FROM loss_report_log l
JOIN loss_report r ON l.report_id = r.id
WHERE l.action = 'download'
  AND r.material_id = 'cmpdj8ktc01t93pmib2i7tyrh'
  AND l.created_at >= '2026-08-27 18:11:00'
  AND l.created_at < '2026-08-27 18:13:00';

-- 执行后校验：应返回 0 行（19 单均恢复未下载）
-- SELECT COUNT(*) FROM loss_report_log
-- WHERE action = 'download'
--   AND created_at >= '2026-08-27 18:11:00'
--   AND created_at < '2026-08-27 18:13:00';
