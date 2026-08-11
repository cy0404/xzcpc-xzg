-- ============================================================
-- V9: 盘点差异处理 — 完整迁移脚本
-- ============================================================

-- ============================================================
-- 一、统一 collation 为 utf8mb4_0900_ai_ci
-- ============================================================
ALTER TABLE loss_report CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE material CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE material_conversion_rule CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE material_loss_notify_config CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ============================================================
-- 二、loss_report 加字段
-- ============================================================
ALTER TABLE loss_report
  ADD COLUMN completed_at DATETIME DEFAULT NULL COMMENT '完成时间（用于差异计算加减对冲）' AFTER confirmed_at;

ALTER TABLE loss_report
  ADD COLUMN unit_price DECIMAL(10,2) DEFAULT NULL COMMENT '单价' AFTER input_qty,
  ADD COLUMN total_amount DECIMAL(12,2) DEFAULT NULL COMMENT '金额' AFTER unit_price;

-- ============================================================
-- 三、task_material_summary 加原始值和调整值
-- ============================================================
ALTER TABLE task_material_summary
  ADD COLUMN original_qty  DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '原始盘点数量（任务提交时快照）' AFTER total_qty,
  ADD COLUMN adjusted_qty DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '调整后数量（差异处理可修改）' AFTER original_qty;

UPDATE task_material_summary SET original_qty = total_qty, adjusted_qty = total_qty
WHERE original_qty = 0 AND adjusted_qty = 0;

-- ============================================================
-- 四、transfer_order_item 加 material_id
-- ============================================================
ALTER TABLE transfer_order_item
  ADD COLUMN material_id VARCHAR(50) DEFAULT NULL COMMENT '物料ID（关联material表）' AFTER material_name;

-- 回填：通过 material_name 精确匹配
UPDATE transfer_order_item oi
JOIN material m ON oi.material_name = m.material_name AND m.del_flag = 0
SET oi.material_id = m.material_id
WHERE oi.material_id IS NULL;

CREATE INDEX idx_transfer_item_material ON transfer_order_item(material_id);

-- ============================================================
-- 五、重建 inventory_difference 表
-- ============================================================

-- 删除旧外键
ALTER TABLE difference_process_log DROP FOREIGN KEY difference_process_log_ibfk_1;
ALTER TABLE difference_process_log DROP FOREIGN KEY fk_diff_log_diff;

-- 改 diff_id 允许 NULL（ON DELETE SET NULL）
ALTER TABLE difference_process_log MODIFY diff_id BIGINT NULL;

DROP TABLE IF EXISTS inventory_difference;

CREATE TABLE inventory_difference (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id           INT           NOT NULL COMMENT '关联任务ID',
    material_id       VARCHAR(50)   NOT NULL COMMENT '物料ID',
    material_name     VARCHAR(200)  NOT NULL COMMENT '物料名称',
    spec              VARCHAR(100)  DEFAULT '' COMMENT '规格',
    unit              VARCHAR(50)   DEFAULT NULL COMMENT '盘点单位',
    -- 上月盘点剩余
    last_month_qty    DECIMAL(12,4) DEFAULT 0 COMMENT '上月盘点剩余',
    -- MySQL 数据来源
    transfer_net_qty  DECIMAL(12,4) DEFAULT 0 COMMENT '调货净值（调入-调出）',
    return_qty        DECIMAL(12,4) DEFAULT 0 COMMENT '还货净值',
    loss_qty          DECIMAL(12,4) DEFAULT 0 COMMENT '报损数量',
    self_purchase_qty DECIMAL(12,4) DEFAULT 0 COMMENT '自购食材数量',
    -- PostgreSQL 数据来源
    purchase_qty      DECIMAL(12,4) DEFAULT 0 COMMENT '采购数量',
    order_qty         DECIMAL(12,4) DEFAULT 0 COMMENT '订货数量',
    consumption_qty   DECIMAL(12,4) DEFAULT 0 COMMENT '消耗数量',
    -- 计算结果
    theoretical_qty   DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '理论剩余',
    actual_qty        DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '本月盘点数',
    diff_qty          DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '差异数量',
    diff_rate         DECIMAL(12,6) DEFAULT 0 COMMENT '差异率',
    is_large          TINYINT       DEFAULT 0 COMMENT '是否大差异',
    original_is_large TINYINT       DEFAULT 0 COMMENT '初始是否大差异（不随修改变化）',
    -- 处理字段
    status            VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT 'pending|adjusted|closed',
    handler           VARCHAR(100)  DEFAULT NULL COMMENT '处理人',
    handled_at        DATETIME      DEFAULT NULL COMMENT '处理时间',
    remark            VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    -- 通用字段
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag          INT           DEFAULT 0,
    version           INT           DEFAULT 0,
    FOREIGN KEY (task_id) REFERENCES task(id),
    UNIQUE KEY uk_task_material (task_id, material_id),
    INDEX idx_task (task_id),
    INDEX idx_status (status),
    INDEX idx_is_large (is_large)
) COMMENT '盘点差异项（含计算明细）' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 恢复外键（ON DELETE SET NULL，删差异不删日志）
ALTER TABLE difference_process_log
  ADD CONSTRAINT fk_diff_log_diff FOREIGN KEY (diff_id) REFERENCES inventory_difference(id) ON DELETE SET NULL;

-- ============================================================
-- 六、差异修改日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS difference_modify_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    diff_id       BIGINT        NOT NULL COMMENT '关联差异项ID',
    task_id       INT           NOT NULL COMMENT '任务ID',
    store_id      VARCHAR(50)   DEFAULT NULL COMMENT '门店ID',
    store_name    VARCHAR(200)  DEFAULT NULL COMMENT '门店名称',
    material_id   VARCHAR(50)   DEFAULT NULL COMMENT '物料ID',
    material_name VARCHAR(200)  DEFAULT NULL COMMENT '物料名称',
    initial_qty   DECIMAL(12,4) DEFAULT NULL COMMENT '任务提交时的初始值(original_qty)',
    old_qty       DECIMAL(12,4) DEFAULT NULL COMMENT '修改前的值',
    new_qty       DECIMAL(12,4) DEFAULT NULL COMMENT '修改后的值',
    operator      VARCHAR(100)  DEFAULT NULL COMMENT '操作人',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_diff_id (diff_id),
    INDEX idx_task_id (task_id),
    FOREIGN KEY (diff_id) REFERENCES inventory_difference(id) ON DELETE CASCADE
) COMMENT '差异修改日志' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 七、sys_config 添加差异阈值
-- ============================================================
INSERT INTO sys_config (config_key, config_value, description)
VALUES ('diff_threshold_rate', '0.5', '盘点差异阈值（小数格式，0.5=50%），差异率超过此值标记为大差异')
ON DUPLICATE KEY UPDATE config_key = config_key;
