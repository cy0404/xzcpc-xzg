USE xzcpc_db;
CREATE TABLE IF NOT EXISTS owner_bind_pending (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  store_id   VARCHAR(50)  NOT NULL COMMENT '门店ID',
  openid     VARCHAR(100) NOT NULL COMMENT '老板微信openid',
  name       VARCHAR(50)  DEFAULT NULL COMMENT '老板姓名',
  mobile     VARCHAR(20)  DEFAULT NULL COMMENT '老板手机号',
  status     VARCHAR(20)  DEFAULT 'pending' COMMENT 'pending=待审核 completed=已完成',
  created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_store_id (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='老板绑定待处理表';
