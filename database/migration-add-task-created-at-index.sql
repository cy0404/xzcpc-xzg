-- 迁移：task 表 created_at 加索引，优化 ORDER BY created_at DESC 分页查询
ALTER TABLE task ADD INDEX idx_task_created_at (created_at);
