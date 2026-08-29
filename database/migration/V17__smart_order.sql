-- ============================================================
-- V17: 智能订货 —— 建议订货单 + 明细 + 配置
-- ============================================================
-- 说明：企迈仓库编码直接复用 store_info 已有列 cangkuid（外部 API 同步），
--      无需新增列；确认订货时为空则按最近 60 天报货单自愈回写 cangkuid。

-- 1. 建议订货单
CREATE TABLE smart_order (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  biz_code        VARCHAR(50)   NOT NULL COMMENT '业务编码',
  store_id        VARCHAR(50)   NOT NULL COMMENT '门店ID',
  store_name      VARCHAR(200)  DEFAULT NULL COMMENT '门店名称快照',
  week_start_date DATE          NOT NULL COMMENT '订货周周一(ISO周)',
  week_label      VARCHAR(20)   NOT NULL COMMENT '周标签,如 2026-W34',
  status          VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT 'pending待确认|syncing同步中|success同步成功|submit_failed提交失败',
  item_count      INT           NOT NULL DEFAULT 0 COMMENT '建议品项数',
  total_qty       DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '建议数量合计(订货单位)',
  suggest_amount  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '建议金额合计(元)',
  deadline        DATETIME      DEFAULT NULL COMMENT '截止时间(仅展示,不强制)',
  generated_at    DATETIME      DEFAULT NULL COMMENT '生成时间',
  confirmed_by    VARCHAR(100)  DEFAULT NULL COMMENT '确认人openid',
  confirmed_at    DATETIME      DEFAULT NULL COMMENT '确认时间',
  qmai_declare_no VARCHAR(100)  DEFAULT NULL COMMENT '企迈报货单号',
  submit_error    VARCHAR(1000) DEFAULT NULL COMMENT '企迈提交错误信息',
  sync_attempts   INT           NOT NULL DEFAULT 0 COMMENT '提交尝试次数',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        INT           DEFAULT 0 COMMENT '删除标记',
  version         INT           DEFAULT 0 COMMENT '乐观锁',
  UNIQUE KEY uk_store_week (store_id, week_start_date),
  INDEX idx_status (status),
  INDEX idx_biz_code (biz_code)
) COMMENT '智能订货建议单' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3. 建议订货单明细（生成时快照物料信息）
CREATE TABLE smart_order_item (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id          BIGINT        NOT NULL COMMENT '关联订货单ID',
  material_id       BIGINT        DEFAULT NULL COMMENT '物料ID(关联material.id)',
  material_name     VARCHAR(200)  NOT NULL COMMENT '物料名称快照',
  spec              VARCHAR(100)  DEFAULT '' COMMENT '规格快照',
  category          VARCHAR(100)  DEFAULT NULL COMMENT '分类快照',
  qm_code           VARCHAR(100)  DEFAULT NULL COMMENT '企迈编码快照(productCode)',
  stock_unit        VARCHAR(50)   NOT NULL COMMENT '订货单位(库存单位)',
  base_unit         VARCHAR(50)   DEFAULT NULL COMMENT '基础盘点单位',
  unit_price        DECIMAL(10,2) DEFAULT NULL COMMENT '单价快照(元)',
  current_inventory DECIMAL(18,4) DEFAULT NULL COMMENT '当前库存(基础单位)',
  daily_use         DECIMAL(18,4) DEFAULT NULL COMMENT '日均消耗估算(基础单位/天)',
  cycle_days        INT           DEFAULT NULL COMMENT '配送周期天数',
  safety_days       INT           DEFAULT NULL COMMENT '安全天数',
  suggest_qty       DECIMAL(12,4) NOT NULL COMMENT '建议数量(订货单位)',
  confirmed_qty     DECIMAL(12,4) DEFAULT NULL COMMENT '确认数量(订货单位)',
  support_days      DECIMAL(10,2) DEFAULT NULL COMMENT '当前库存可支撑天数',
  reason            VARCHAR(500)  DEFAULT NULL COMMENT '建议依据',
  sort_no           INT           DEFAULT NULL COMMENT '排序号',
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag          INT           DEFAULT 0 COMMENT '删除标记',
  version           INT           DEFAULT 0 COMMENT '乐观锁',
  FOREIGN KEY (order_id) REFERENCES smart_order(id),
  INDEX idx_order (order_id)
) COMMENT '智能订货建议单明细' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 4. 配置（一期总部统一配置，后续配送周期/安全天数改为企迈按店拉取）
INSERT INTO sys_config (config_key, config_value, description)
VALUES ('smart_order_delivery_cycle_days', '7', '智能订货配送周期天数(后续改为企迈按店)'),
       ('smart_order_safety_days', '3', '智能订货安全天数(后续改为企迈按店)'),
       ('smart_order_default_daily_use', '0.5', '智能订货默认日均消耗(基础单位/天,无两次盘点数据时兜底)'),
       ('smart_order_online_pay', '0', '智能订货企迈支付方式 0线下 1线上'),
       ('smart_order_deadline_time', '18:00:00', '智能订货截止时间(仅展示不强制)')
ON DUPLICATE KEY UPDATE config_key = config_key;

SELECT 'V17__smart_order.sql executed successfully' AS status;
