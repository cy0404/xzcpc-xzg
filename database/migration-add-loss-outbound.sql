-- ============================================================
-- 补发联动企迈出库单：outbound_order 出库单记录表 + loss_report 外键 + sys_config 仓库编码配置
-- 执行：生产库 + 测试库各一次（若已执行报 Duplicate column / Duplicate entry，忽略即可）
-- 前置：企迈 9.2.4 创建出库单接口权限已开通（应用接口权限申请）
-- ============================================================

-- 1. 出库单记录表（一仓一单；一次批量补发可能多张单，每单关联多条 loss_report）
CREATE TABLE IF NOT EXISTS outbound_order (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '出库单记录ID',
    external_no   VARCHAR(64)  NOT NULL COMMENT '三方出库单号（幂等，如 BF123-1）',
    warehouse_no  VARCHAR(32)  DEFAULT NULL COMMENT '出库仓库编码（冷冻仓/鲜奶仓/总仓）',
    outbound_no   VARCHAR(64)  DEFAULT NULL COMMENT '企迈出库单号（9.2.4 创建成功回填）',
    status        VARCHAR(16)  DEFAULT 'none' COMMENT 'none=未建单 success=已建单 failed=建单失败',
    error         VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_external_no (external_no)
) COMMENT='补发出库单记录（联动企迈9.2.4）';

-- 2. loss_report 加外键列（一单关联多条记录）
ALTER TABLE loss_report
    ADD COLUMN outbound_order_id BIGINT DEFAULT NULL COMMENT '出库单记录ID（outbound_order.id）' AFTER orig_qty;

-- 3. sys_config 加三个出库仓库编码（补发按物料归属选仓）
INSERT INTO sys_config (config_key, config_value, description) VALUES
    ('loss_outbound_wh_frozen',    'PSCK000149', '补发出库-冷冻仓编码（冷冻类物料+安佳淡奶油/酸奶/咸法干酪乳）'),
    ('loss_outbound_wh_fresh_milk','PSCK000070', '补发出库-鲜奶仓编码（鲜奶类物料）'),
    ('loss_outbound_wh_central',   'PSCK000023', '补发出库-新螺蛳湾总仓编码（其余物料）')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

-- 验证
-- SELECT o.id, o.external_no, o.warehouse_no, o.outbound_no, o.status, o.error, r.id AS report_id
--   FROM outbound_order o LEFT JOIN loss_report r ON r.outbound_order_id = o.id LIMIT 10;
-- SELECT config_key, config_value FROM sys_config WHERE config_key LIKE 'loss_outbound_%';
