-- ============================================================
-- 慢 SQL 修复：operation_log / login_log 分页查询优化
-- 目标库：store_inventory
-- 日期：2026-07-02
-- ============================================================
-- 问题：/api/logs/operation?page=1&size=20 耗时 15s
-- 原因：分页 = COUNT(*) + ORDER BY created_at + LIMIT，
--       COUNT(*) 在 InnoDB 大表上需扫描索引，单列索引
--       且无覆盖索引时退化严重。
-- 修复：created_at 降序索引 + 组合筛选索引用
-- ============================================================

USE store_inventory;

-- 通用：如果索引存在则删除
DROP PROCEDURE IF EXISTS drop_index_if_exists;
DELIMITER $$
CREATE PROCEDURE drop_index_if_exists(IN tbl VARCHAR(100), IN idx VARCHAR(100))
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = tbl
          AND INDEX_NAME = idx
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', tbl, ' DROP INDEX ', idx);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ============================================================
-- 1. operation_log：重建为复合索引
-- ============================================================

-- 删旧单列索引
CALL drop_index_if_exists('operation_log', 'idx_username');
CALL drop_index_if_exists('operation_log', 'idx_module');
CALL drop_index_if_exists('operation_log', 'idx_created_at');

-- 建复合索引：覆盖最常用查询
--   无筛选 → ORDER BY created_at → idx_op_created 直接走
--   有筛选 → (username, module, operation) 任一可用
CREATE INDEX idx_op_created ON operation_log(created_at);
CREATE INDEX idx_op_username_module ON operation_log(username, module);
CREATE INDEX idx_op_operation ON operation_log(operation);

-- ============================================================
-- 2. login_log：同样的索引策略
-- ============================================================

CALL drop_index_if_exists('login_log', 'idx_username');
CALL drop_index_if_exists('login_log', 'idx_login_type');
CALL drop_index_if_exists('login_log', 'idx_created_at');

CREATE INDEX idx_ll_created ON login_log(created_at);
CREATE INDEX idx_ll_username_type ON login_log(username, login_type);

DROP PROCEDURE drop_index_if_exists;

-- ============================================================
-- 验证
-- ============================================================
-- EXPLAIN SELECT * FROM operation_log ORDER BY created_at DESC LIMIT 0, 20;
-- 预期：type=index, key=idx_op_created, rows 仅扫描 20 行
-- EXPLAIN SELECT COUNT(*) FROM operation_log;
-- 预期：key=idx_op_created（MySQL 自动选最小索引做 COUNT）
