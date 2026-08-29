-- 牛油果泥审核：确认报损登记时支持修改数量
-- 方案：修改后的数量直接写回 input_qty（input_unit 同时改为换算后的单位，如"包"），
--       下游补发/群日报/导出等所有逻辑继续读 input_qty，无需任何改动；
--       只新增一列 orig_qty 记录修改前的原始数量（按包），NULL = 未修改。
-- 注意：如已执行过旧版脚本（confirm_qty / confirm_unit 两列），请先执行下面两行：
--   ALTER TABLE loss_report DROP COLUMN confirm_unit;
--   ALTER TABLE loss_report DROP COLUMN confirm_qty;
ALTER TABLE loss_report
  ADD COLUMN orig_qty DECIMAL(12,2) NULL COMMENT '确认登记修改前的原始数量（牛油果泥按包），NULL=未修改' AFTER input_qty;
