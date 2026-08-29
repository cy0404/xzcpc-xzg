-- 盘点金额字段：提交任务时自动计算 sum(baseQty × 单价)
ALTER TABLE task ADD COLUMN total_amount DECIMAL(12,2) DEFAULT NULL COMMENT '盘点金额' AFTER submitted_at;
