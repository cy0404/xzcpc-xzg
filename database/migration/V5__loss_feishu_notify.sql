-- V5: 到货验收报损飞书通知 + 企迈单号 + 拒绝原因

-- loss_report 加企迈单号 + 拒绝原因
ALTER TABLE loss_report
  ADD COLUMN qimai_order_no VARCHAR(100) DEFAULT NULL COMMENT '企迈单号' AFTER base_qty,
  ADD COLUMN reject_reason VARCHAR(500) DEFAULT NULL COMMENT '拒绝原因' AFTER status;

-- 系统配置表
CREATE TABLE IF NOT EXISTS sys_config (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  config_key   VARCHAR(100) NOT NULL,
  config_value VARCHAR(1000) DEFAULT '',
  description  VARCHAR(300) DEFAULT NULL,
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_key (config_key)
) COMMENT='系统配置';

-- 物料报损通知配置表
CREATE TABLE IF NOT EXISTS material_loss_notify_config (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  category        VARCHAR(100) NOT NULL COMMENT '物料分类',
  feishu_user_id  VARCHAR(100) NOT NULL COMMENT '飞书user_id，@的人',
  chat_id         VARCHAR(100) DEFAULT NULL COMMENT '群chat_id，为空则用系统默认',
  status          TINYINT DEFAULT 1 COMMENT '1启用 0停用',
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_category (category)
) COMMENT='物料报损飞书通知配置';

-- 初始配置
INSERT INTO sys_config (config_key, config_value, description)
VALUES ('feishu_loss_chat_id', '', '到货报损飞书通知群chat_id')
ON DUPLICATE KEY UPDATE config_key = config_key;
