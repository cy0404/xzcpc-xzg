-- ============================================================
-- B1 问题处理：issue 表增加处理记录 JSON 字段
-- task_platform 有新处理记录时回调，我们存 JSON，不代理查询
-- ============================================================

ALTER TABLE issue
    ADD COLUMN records_data TEXT DEFAULT NULL COMMENT '处理记录JSON（records+replies+solutionPhotos）' AFTER process_result;

SELECT 'migration-add-issue-records.sql executed successfully' AS status;
