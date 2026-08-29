-- ============================================================
-- 测试库 store_inventory_test 补表脚本（智能订货测试所需）
-- ============================================================
-- 背景：测试库 schema 滞后，缺 store_zone_material 等表（1146 报错）。
-- 本脚本全部幂等（CREATE TABLE IF NOT EXISTS / ON DUPLICATE KEY），
-- 可重复执行；已存在的表自动跳过、不会覆盖。
--
-- ⚠️ 外键说明：store_zone_material/task_material_summary 这里不建
--    指向既有表的外键（测试库 material/task 的字符集版本未知，
--    外键要求列字符集一致，建了可能报 errno 150）；业务等价即可。
-- ============================================================

-- ---------- 0. 先看现状（哪些表已存在） ----------
SELECT TABLE_NAME FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('smart_order', 'smart_order_item', 'sys_config',
                      'store_zone_material', 'task_material_summary',
                      'material', 'material_inventory_rule',
                      'material_conversion_rule', 'task')
 ORDER BY TABLE_NAME;

-- ---------- 1. 门店默认分区物料清单（来自 schema.sql，去 FK） ----------
CREATE TABLE IF NOT EXISTS store_zone_material (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
  store_id        VARCHAR(50)   NOT NULL COMMENT '门店ID',
  zone_name       VARCHAR(100)  NOT NULL COMMENT '分区名称',
  material_id     BIGINT        NOT NULL COMMENT '物料ID（关联 material.id）',
  sort_no         INT           DEFAULT NULL COMMENT '排序号',
  status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1有效 0已移除',
  source_type     VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '来源类型',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
  version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
  UNIQUE KEY uk_store_zone_material (store_id, zone_name, material_id)
) COMMENT '门店默认分区物料清单，任务提交后自动更新' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- 2. 任务物料汇总（schema.sql + V9 差异处理的 original_qty/adjusted_qty，去 FK） ----------
CREATE TABLE IF NOT EXISTS task_material_summary (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  biz_code        VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '业务编码',
  task_id         INT             NOT NULL COMMENT '关联任务ID',
  material_id     VARCHAR(50)     NOT NULL COMMENT '物料ID',
  material_name   VARCHAR(200)    DEFAULT NULL COMMENT '物料名称',
  spec            VARCHAR(100)    DEFAULT NULL COMMENT '规格',
  base_unit       VARCHAR(50)     DEFAULT NULL COMMENT '基础单位',
  total_qty       DECIMAL(10,2)   NOT NULL DEFAULT 0 COMMENT '跨分区汇总数量',
  original_qty    DECIMAL(12,4)   NOT NULL DEFAULT 0 COMMENT '原始盘点数量（任务提交时快照）',
  adjusted_qty    DECIMAL(12,4)   NOT NULL DEFAULT 0 COMMENT '调整后数量（差异处理可修改）',
  zone_count      INT             DEFAULT NULL COMMENT '涉及分区数',
  unit_breakdown  VARCHAR(500)    DEFAULT NULL COMMENT '各单位数量明细',
  del_flag        INT             DEFAULT 0 COMMENT '删除标记 0正常 1删除',
  version         INT             DEFAULT 0 COMMENT '乐观锁版本号',
  UNIQUE KEY uk_task_material (task_id, material_id)
) COMMENT '任务物料汇总' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- 3. 智能订货建议单 + 明细（V17 原样） ----------
CREATE TABLE IF NOT EXISTS smart_order (
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

CREATE TABLE IF NOT EXISTS smart_order_item (
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

-- ---------- 4. 系统配置表（若无）+ 智能订货 5 条配置（V17） ----------
CREATE TABLE IF NOT EXISTS sys_config (
  id           bigint       NOT NULL AUTO_INCREMENT,
  config_key   varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
  config_value varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '配置值',
  description  varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明',
  created_at   datetime     DEFAULT CURRENT_TIMESTAMP,
  updated_at   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_key (config_key),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置';

INSERT INTO sys_config (config_key, config_value, description)
VALUES ('smart_order_delivery_cycle_days', '7', '智能订货配送周期天数(后续改为企迈按店)'),
       ('smart_order_safety_days', '3', '智能订货安全天数(后续改为企迈按店)'),
       ('smart_order_default_daily_use', '0.5', '智能订货默认日均消耗(基础单位/天,无两次盘点数据时兜底)'),
       ('smart_order_online_pay', '0', '智能订货企迈支付方式 0线下 1线上'),
       ('smart_order_deadline_time', '18:00:00', '智能订货截止时间(仅展示不强制)')
ON DUPLICATE KEY UPDATE config_key = config_key;

SELECT '补表脚本执行完成' AS status;
