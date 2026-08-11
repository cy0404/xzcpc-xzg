-- V3: 到货验收报损 → 支持多单位选择
-- 将 unit/loss_qty 重命名为 input_unit/input_qty，新增 base_unit/base_qty

ALTER TABLE loss_report
  CHANGE COLUMN unit input_unit VARCHAR(50) DEFAULT NULL COMMENT '填报单位',
  CHANGE COLUMN loss_qty input_qty DECIMAL(12,4) DEFAULT NULL COMMENT '填报数量',
  ADD COLUMN base_unit VARCHAR(50) DEFAULT NULL COMMENT '最小单位' AFTER input_qty,
  ADD COLUMN base_qty DECIMAL(12,4) DEFAULT NULL COMMENT '换算到最小单位的数量' AFTER base_unit;
