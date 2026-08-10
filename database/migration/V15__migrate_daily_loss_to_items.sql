-- V15: 存量日常报损数据迁移 → loss_report_item 明细表
-- 每条历史日常报损生成一条明细记录，到货验收不动

INSERT INTO loss_report_item (
  report_id, loss_object, material_id, material_name, spec,
  input_unit, input_qty, unit_price, total_amount,
  base_unit, base_qty,
  gross_weight, container_id, container_name, container_weight, net_weight,
  sort_no, created_at, updated_at
)
SELECT
  id, COALESCE(loss_object, 'semi_finished'), material_id, material_name, spec,
  input_unit, input_qty, unit_price, total_amount,
  base_unit, base_qty,
  gross_weight, container_id, container_name, container_weight, net_weight,
  0, created_at, updated_at
FROM loss_report
WHERE loss_type = 'daily'
  AND item_count = 0
  AND material_name IS NOT NULL;

-- 标记已迁移
UPDATE loss_report
SET item_count = 1
WHERE loss_type = 'daily'
  AND item_count = 0
  AND material_name IS NOT NULL;
