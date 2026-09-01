-- V18: 自购食材支出多物料支持 — self_purchase_material_item 明细表
-- 自购食材一次登记最多 10 个物料：主表保持一单一行（总额 total_amount），物料明细下沉到子表

-- 1. 自购食材明细表（一行一个物料）
CREATE TABLE IF NOT EXISTS self_purchase_material_item (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  biz_code         VARCHAR(50) NOT NULL COMMENT '关联 self_purchase_material.biz_code',
  material_id      VARCHAR(50) DEFAULT NULL COMMENT '物料ID',
  material_name    VARCHAR(200) NOT NULL COMMENT '物料名称，自由文本',
  parent_category  VARCHAR(100) DEFAULT NULL COMMENT '父级分类',
  category         VARCHAR(100) DEFAULT NULL COMMENT '分类',
  unit             VARCHAR(20) NOT NULL DEFAULT 'kg' COMMENT '单位',
  purchase_qty     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '采购数量',
  unit_price       DECIMAL(10,2) DEFAULT NULL COMMENT '单价',
  total_amount     DECIMAL(10,2) DEFAULT NULL COMMENT '小计（数量×单价）',
  sort_no          INT DEFAULT 0 COMMENT '展示顺序',
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag         INT DEFAULT 0 COMMENT '删除标记 0正常 1删除',
  INDEX idx_biz_code (biz_code),
  INDEX idx_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自购食材明细（一次登记多物料用，一单最多10行）';

-- 2. 存量数据迁移：老数据一单一行即一个物料，搬入子表
INSERT INTO self_purchase_material_item (biz_code, material_id, material_name, parent_category, category, unit, purchase_qty, unit_price, total_amount, sort_no)
SELECT biz_code, material_id, material_name, parent_category, category, COALESCE(NULLIF(unit, ''), 'kg'), purchase_qty, unit_price, total_amount, 0
FROM self_purchase_material
WHERE del_flag = 0;

-- 3. 主表：物料列放宽可空并清空（物料明细统一走子表，主表只留 total_amount 合计）
ALTER TABLE self_purchase_material
  MODIFY COLUMN material_name VARCHAR(200) DEFAULT NULL COMMENT '物料名称（旧数据；多物料新单为空，明细在子表）';

UPDATE self_purchase_material
SET material_id = NULL, material_name = NULL, parent_category = NULL, category = NULL,
    unit = NULL, purchase_qty = 0, unit_price = NULL
WHERE del_flag = 0;
