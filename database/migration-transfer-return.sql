-- ============================================================
-- 调货还货/还钱功能：新建归还记录表
-- 归还状态/数量/金额通过 transfer_return_record 聚合查询，不在 item 表冗余
-- ============================================================

-- 1. 归还记录表
CREATE TABLE IF NOT EXISTS transfer_return_record (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id        VARCHAR(50)   NOT NULL COMMENT '业务ID(TRR+时间戳)',
    transfer_id      BIGINT        NOT NULL COMMENT '关联 transfer_order.id',
    item_id          BIGINT        NOT NULL COMMENT '关联 transfer_order_item.id',
    return_type      VARCHAR(20)   NOT NULL COMMENT '归还类型: goods|money',
    return_qty       DECIMAL(10,2) DEFAULT 0    COMMENT '归还数量',
    return_amount    DECIMAL(10,2) DEFAULT 0    COMMENT '归还金额',
    unit_price       DECIMAL(10,2) DEFAULT NULL COMMENT '单价快照',
    remark           VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    handler_name     VARCHAR(100)  DEFAULT NULL COMMENT '经手人',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_transfer_id (transfer_id),
    INDEX idx_item_id (item_id)
) COMMENT '调货归还记录';

SELECT 'migration-transfer-return.sql executed successfully' AS status;
