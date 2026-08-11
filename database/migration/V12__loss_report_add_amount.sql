-- 报损增加单价/金额字段
ALTER TABLE loss_report
  ADD COLUMN unit_price   DECIMAL(10,2) DEFAULT NULL COMMENT '单价' AFTER input_qty,
  ADD COLUMN total_amount DECIMAL(10,2) DEFAULT NULL COMMENT '金额（数量×单价）' AFTER unit_price;
