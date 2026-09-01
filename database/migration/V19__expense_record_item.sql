-- V19: 支出记录多明细支持 — expense_record_item 明细表
-- 所有支出类型一次登记最多 10 项：主表 expense_record 保持一单一行（金额 amount=Σ明细），
-- 明细下沉到子表（其他类型为「名称+金额」，自购食材不走此表、走 self_purchase_material_item）

-- 1. 支出记录明细表（一行一个明细）
CREATE TABLE IF NOT EXISTS expense_record_item (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  expense_id  VARCHAR(32)   NOT NULL COMMENT '关联 expense_record.expense_id',
  item_name   VARCHAR(200)  NOT NULL COMMENT '明细名称，自由文本',
  amount      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '明细金额',
  sort_no     INT DEFAULT 0 COMMENT '展示顺序',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag    INT DEFAULT 0 COMMENT '删除标记 0正常 1删除',
  INDEX idx_expense_id (expense_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支出记录明细（一单多行，一单最多10行）';

-- 2. 存量数据无需迁移：历史 expense_record 均无明细，金额直接在主表 amount
