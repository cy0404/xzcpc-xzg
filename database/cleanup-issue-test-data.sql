-- ============================================================
-- 清理测试库的未验收问题提醒测试数据，只保留日志群测试数据
-- 数据库：store_inventory（测试库；如实际库名不同请替换）
-- 保留依据：日志群测试门店 chat_id = oc_ea177cd1cd4c074677c53785db6fd1b7
--           （即 insert_issues_from_xiangmu.sql 中 store_name='日志群测试' 的两条）
-- 说明：硬删除（含 del_flag=1 的历史测试行），测试库无需保留
-- 执行顺序：0 → 1 预检确认 → 2/3 删除 → 4 校验
-- ============================================================

USE store_inventory;

-- ==================== 0. 预检：总数 ====================

-- 当前 issue 总行数（含逻辑删除的测试行）
SELECT COUNT(*) AS issue_total FROM issue;

-- 将保留的行数（日志群测试门店）
SELECT COUNT(*) AS issue_keep
FROM issue i
JOIN store_info s ON i.store_id = s.store_id AND s.del_flag = 0
WHERE s.chat_id = 'oc_ea177cd1cd4c074677c53785db6fd1b7';

-- ==================== 1. 预检：将被删除的明细 ====================

SELECT i.id, i.biz_code, i.store_name, i.title, i.status
FROM issue i
LEFT JOIN store_info s ON i.store_id = s.store_id AND s.del_flag = 0
WHERE NOT EXISTS (
    SELECT 1 FROM store_info s2
    WHERE i.store_id = s2.store_id AND s2.chat_id = 'oc_ea177cd1cd4c074677c53785db6fd1b7'
)
ORDER BY i.id
LIMIT 100;

-- ==================== 2. 删除 issue 测试数据（保留日志群测试） ====================

DELETE i FROM issue i
WHERE NOT EXISTS (
    SELECT 1 FROM store_info s
    WHERE i.store_id = s.store_id AND s.chat_id = 'oc_ea177cd1cd4c074677c53785db6fd1b7'
);

-- ==================== 3. 清理发送日志（保留日志群测试的发送记录） ====================
-- 注意：若还没执行过迁移建表脚本（migration-add-issue-reminder-send-log.sql），
--       本步会报"表不存在"，忽略即可

DELETE FROM issue_reminder_send_log
WHERE chat_id != 'oc_ea177cd1cd4c074677c53785db6fd1b7';

-- ==================== 4. 校验：剩余数据 ====================

SELECT i.id, i.biz_code, i.store_name, i.title, i.status, s.chat_id
FROM issue i
LEFT JOIN store_info s ON i.store_id = s.store_id AND s.del_flag = 0
ORDER BY i.id;

SELECT * FROM issue_reminder_send_log ORDER BY id DESC;

-- ============================================================
-- （可选）把日志群测试的问题置为「待验收」，方便触发提醒卡片测试：
-- UPDATE issue i
-- JOIN store_info s ON i.store_id = s.store_id
-- SET i.status = 'pending_acceptance', i.updated_at = NOW()
-- WHERE s.chat_id = 'oc_ea177cd1cd4c074677c53785db6fd1b7';
-- ============================================================
