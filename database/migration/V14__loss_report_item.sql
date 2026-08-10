-- V14: 日常报损多物料支持 — loss_report_item 明细表
-- 到货验收保持主表扁平结构不变；日常报损新单走明细表

-- 1. 报损明细表
CREATE TABLE IF NOT EXISTS loss_report_item (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  report_id        BIGINT NOT NULL COMMENT '关联 loss_report.id',
  loss_object      VARCHAR(30) NOT NULL DEFAULT 'semi_finished' COMMENT 'finished|semi_finished',
  material_id      VARCHAR(50) DEFAULT NULL COMMENT '物料ID',
  material_name    VARCHAR(200) NOT NULL COMMENT '物料名称',
  spec             VARCHAR(100) DEFAULT '' COMMENT '规格',
  input_unit       VARCHAR(50) DEFAULT NULL COMMENT '填报单位',
  input_qty        DECIMAL(12,4) DEFAULT NULL COMMENT '填报数量（成品报损用）',
  unit_price       DECIMAL(10,2) DEFAULT NULL COMMENT '单价',
  total_amount     DECIMAL(10,2) DEFAULT NULL COMMENT '金额（数量×单价）',
  base_unit        VARCHAR(50) DEFAULT NULL COMMENT '基础单位',
  base_qty         DECIMAL(12,4) DEFAULT NULL COMMENT '基础单位数量',
  gross_weight     DECIMAL(10,2) DEFAULT NULL COMMENT '含容器总重(克)',
  container_id     BIGINT DEFAULT NULL COMMENT '容器ID',
  container_name   VARCHAR(100) DEFAULT NULL COMMENT '容器名称快照',
  container_weight DECIMAL(10,2) DEFAULT NULL COMMENT '容器皮重快照(克)',
  net_weight       DECIMAL(10,2) DEFAULT NULL COMMENT '净重(克)',
  sort_no          INT DEFAULT 0 COMMENT '展示顺序',
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag         INT DEFAULT 0 COMMENT '删除标记',
  INDEX idx_report (report_id),
  INDEX idx_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报损明细（日常报损多物料用）';

-- 2. 主表：material_name 允许为空（多物料报损不写主表）
ALTER TABLE loss_report
  MODIFY COLUMN material_name VARCHAR(200) DEFAULT NULL COMMENT '物料名称（到货/旧数据；多物料报损为空）';

-- 3. 主表：item_count 区分新旧数据（0=扁平单物料/到货；>0=多物料走明细表）
ALTER TABLE loss_report
  ADD COLUMN item_count INT DEFAULT 0 COMMENT '明细条数' AFTER version;

-- 4. 凭证列扩容：20 张图 comma-join URL 可超 500 字符
ALTER TABLE loss_report
  MODIFY COLUMN voucher_url VARCHAR(4000) DEFAULT NULL COMMENT '凭证图片URL，逗号分隔';
