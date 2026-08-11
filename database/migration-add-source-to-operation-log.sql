-- ============================================================
-- operation_log 加 source 字段 + 索引重建
-- 日期：2026-07-02
-- ============================================================

USE store_inventory;

-- 1. 加字段
ALTER TABLE operation_log
    ADD COLUMN source VARCHAR(10) DEFAULT '' COMMENT '来源：admin总部端 mp小程序端' AFTER user_id;

-- 2. 存量数据回填：飞书 open_id 以 ou_ 开头
UPDATE operation_log SET source = 'admin' WHERE user_id LIKE 'ou_%' AND source = '';
UPDATE operation_log SET source = 'mp'   WHERE user_id NOT LIKE 'ou_%' AND user_id != '' AND source = '';

-- 3. 重建索引（source 等值查询 + created_at 排序）
DROP PROCEDURE IF EXISTS drop_idx;
DELIMITER $$
CREATE PROCEDURE drop_idx(IN tbl VARCHAR(100), IN idx VARCHAR(100))
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', tbl, ' DROP INDEX ', idx);
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- 删掉之前不合理的索引
CALL drop_idx('operation_log', 'idx_op_username_module');
CALL drop_idx('operation_log', 'idx_op_operation');
CALL drop_idx('operation_log', 'idx_op_created');

-- 建新索引：source + created_at 覆盖最常用查询
CREATE INDEX idx_op_source_created ON operation_log(source, created_at);
CREATE INDEX idx_op_username ON operation_log(username);
CREATE INDEX idx_op_module ON operation_log(module);

DROP PROCEDURE drop_idx;

-- 验证
SELECT source, COUNT(*) FROM operation_log GROUP BY source;
