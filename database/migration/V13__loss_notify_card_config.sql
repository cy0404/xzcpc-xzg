-- V13: 报损飞书通知竖表重构 + 旧表替换

-- 1. 新建竖表
CREATE TABLE IF NOT EXISTS loss_notify_card_config (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  category        VARCHAR(200) NOT NULL COMMENT '物料分类(逗号分隔)，其他类为兜底',
  card_type       VARCHAR(50) NOT NULL COMMENT '卡片类型: pending/damage/other/audit/resend/avocado_audit/avocado_resend',
  feishu_user_id  VARCHAR(200) NOT NULL COMMENT '收件人飞书open_id',
  remark          VARCHAR(200) DEFAULT NULL COMMENT '备注（收件人姓名/用途等）',
  status          TINYINT DEFAULT 1 COMMENT '1启用 0停用',
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报损飞书卡片通知配置（竖表）';

-- 2. 迁移旧数据（仅当旧表存在时）
INSERT INTO loss_notify_card_config (category, card_type, feishu_user_id)
  SELECT category, 'pending', feishu_user_id
  FROM material_loss_notify_config
  WHERE status = 1 AND feishu_user_id IS NOT NULL AND feishu_user_id != ''
  AND NOT EXISTS (SELECT 1 FROM loss_notify_card_config WHERE card_type='pending');

-- 3. 删旧表
DROP TABLE IF EXISTS material_loss_notify_config;

-- 4. 新增牛油果泥 material_id 配置
INSERT INTO sys_config (config_key, config_value, description)
VALUES ('feishu_avocado_material_id', '', '牛油果泥的 material_id')
ON DUPLICATE KEY UPDATE config_key = config_key;
