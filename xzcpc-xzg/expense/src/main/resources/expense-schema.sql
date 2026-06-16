CREATE TABLE IF NOT EXISTS expense_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  type_id VARCHAR(32) NOT NULL COMMENT '支出类型业务ID',
  name VARCHAR(64) NOT NULL COMMENT '类型名称',
  description VARCHAR(255) DEFAULT NULL COMMENT '适用说明',
  status VARCHAR(20) NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled/disabled',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  UNIQUE KEY uk_expense_type_id (type_id),
  UNIQUE KEY uk_expense_type_name_del (name, del_flag),
  KEY idx_expense_type_status (status),
  KEY idx_expense_type_del_flag (del_flag)
) COMMENT='支出类型表';

CREATE TABLE IF NOT EXISTS expense_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  expense_id VARCHAR(32) NOT NULL COMMENT '支出记录业务ID',
  store_id VARCHAR(32) NOT NULL COMMENT '门店ID',
  store_miniapp_no VARCHAR(64) DEFAULT NULL COMMENT '门店小程序号',
  store_name VARCHAR(128) NOT NULL COMMENT '门店名称快照',
  warehouse_code VARCHAR(50) DEFAULT NULL COMMENT '仓库编码快照',
  type_id VARCHAR(32) NOT NULL COMMENT '支出类型业务ID',
  type_name VARCHAR(64) NOT NULL COMMENT '支出类型名称快照',
  amount DECIMAL(12,2) NOT NULL COMMENT '金额',
  occurred_date DATE NOT NULL COMMENT '产生日期',
  handler_name VARCHAR(64) NOT NULL COMMENT '经手人',
  voucher_url VARCHAR(512) DEFAULT NULL COMMENT '凭证图片地址',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  UNIQUE KEY uk_expense_id (expense_id),
  KEY idx_store_date (store_id, occurred_date),
  KEY idx_store_miniapp_no (store_miniapp_no),
  KEY idx_type_date (type_id, occurred_date),
  KEY idx_occurred_date (occurred_date),
  KEY idx_expense_record_del_flag (del_flag)
) COMMENT='支出记录表';

CREATE TABLE IF NOT EXISTS expense_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  item_id VARCHAR(32) NOT NULL COMMENT '支出项目业务ID',
  type_id VARCHAR(32) NOT NULL COMMENT '关联支出类型ID',
  name VARCHAR(100) NOT NULL COMMENT '项目名称',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  INDEX idx_expense_item_type (type_id)
) COMMENT='支出项目表（二级分类）';

-- 如果 expense_record 表已存在，执行以下语句补充项目字段：
-- ALTER TABLE expense_record ADD COLUMN item_id VARCHAR(32) DEFAULT NULL AFTER type_name;
-- ALTER TABLE expense_record ADD COLUMN item_name VARCHAR(100) DEFAULT NULL AFTER item_id;

-- 父级分类字段（数据库预设，不可在总端管理）：
-- ALTER TABLE expense_type ADD COLUMN first_type_id VARCHAR(32) DEFAULT NULL AFTER name;
-- ALTER TABLE expense_type ADD COLUMN first_type_name VARCHAR(64) DEFAULT NULL AFTER first_type_id;
-- ALTER TABLE expense_record ADD COLUMN first_type_id VARCHAR(32) DEFAULT NULL AFTER warehouse_code;
-- ALTER TABLE expense_record ADD COLUMN first_type_name VARCHAR(64) DEFAULT NULL AFTER first_type_id;
