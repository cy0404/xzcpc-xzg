CREATE TABLE IF NOT EXISTS material (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  material_id VARCHAR(32) NOT NULL COMMENT '物料业务ID',
  qm_code VARCHAR(64) DEFAULT NULL COMMENT '品相编码',
  parent_category VARCHAR(64) DEFAULT NULL COMMENT '父级分类',
  category VARCHAR(64) DEFAULT NULL COMMENT '物料分类',
  material_name VARCHAR(128) NOT NULL COMMENT '物料名称',
  spec VARCHAR(128) DEFAULT NULL COMMENT '规格',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  UNIQUE KEY uk_material_id (material_id),
  KEY idx_material_name (material_name),
  KEY idx_material_category (category),
  KEY idx_material_del_flag (del_flag)
) COMMENT='物料主数据本地同步表';

CREATE TABLE IF NOT EXISTS material_inventory_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  rule_id VARCHAR(32) NOT NULL COMMENT '盘点规则业务ID',
  material_id VARCHAR(32) NOT NULL COMMENT '物料业务ID',
  base_unit VARCHAR(32) NOT NULL COMMENT '基础盘点单位',
  inventory_units VARCHAR(255) NOT NULL COMMENT '可盘点单位，逗号分隔',
  unit_price DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '基础单位盘点单价',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  UNIQUE KEY uk_material_inventory_rule_id (rule_id),
  UNIQUE KEY uk_material_inventory_rule_material (material_id, del_flag),
  KEY idx_material_inventory_rule_base_unit (base_unit),
  KEY idx_material_inventory_rule_del_flag (del_flag)
) COMMENT='物料盘点规则主表';

CREATE TABLE IF NOT EXISTS material_conversion_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  rule_id VARCHAR(32) NOT NULL COMMENT '盘点规则业务ID',
  conversion_type VARCHAR(16) NOT NULL COMMENT '换算类型：unit普通单位换算，weight称重换算',
  from_quantity DECIMAL(18,6) NOT NULL COMMENT '左侧数量',
  from_unit VARCHAR(32) NOT NULL COMMENT '左侧单位，普通换算为盘点单位，称重换算为重量单位',
  to_quantity DECIMAL(18,6) NOT NULL COMMENT '右侧数量',
  to_unit VARCHAR(32) NOT NULL COMMENT '右侧单位，普通换算为盘点单位，称重换算为基础单位',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
  KEY idx_material_conversion_rule_rule (rule_id, del_flag),
  KEY idx_material_conversion_rule_type (conversion_type)
) COMMENT='物料换算规则表';
