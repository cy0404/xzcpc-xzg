-- ============================================================
-- transfer_order.handoff 字段改为还货状态
-- 旧值：门店自取|第三方物流 → 已不用，统一清空
-- 新值：pending_return(待还)|returned(已还)
-- ============================================================

-- 1. 清空旧数据，completed 的默认设为待还
UPDATE transfer_order SET handoff = 'pending_return' WHERE status = 'completed';
UPDATE transfer_order SET handoff = NULL WHERE status != 'completed';

-- 2. 更新注释
ALTER TABLE transfer_order
    MODIFY COLUMN handoff VARCHAR(30) DEFAULT 'pending_return'
        COMMENT '还货状态：pending_return(待还)|returned(已还)';

SELECT 'migration-handoff-to-return-status.sql executed successfully' AS status;
