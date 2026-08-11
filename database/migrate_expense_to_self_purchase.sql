-- ============================================================
-- 迁移 expense_record 自购成本 → self_purchase_material
-- 条件：first_type_name = '自购成本'，6月1日至今
-- 日期：2026-07-06
-- ============================================================

-- 1. 先查数量，确认范围
SELECT COUNT(*) AS cnt
FROM expense_record
WHERE first_type_name = '自购成本'
  AND del_flag = 0
  AND occurred_date >= '2026-06-01';

-- 2. 备份（安全第一）
CREATE TABLE expense_record_self_purchase_bak_20260601 AS
SELECT * FROM expense_record
WHERE first_type_name = '自购成本'
  AND del_flag = 0
  AND occurred_date >= '2026-06-01';

-- 3. 迁移
INSERT INTO self_purchase_material (
    biz_code, store_id, store_name, store_miniapp_no,
    parent_category, category,
    material_name, unit, purchase_qty, unit_price, total_amount,
    purchase_month, purchase_date,
    handler_name, voucher_url, remark,
    del_flag
)
SELECT
    CONCAT('SPM_MIG_', expense_id)   AS biz_code,
    store_id,
    store_name,
    store_miniapp_no,
    first_type_name                  AS parent_category,
    type_name                        AS category,
    ''                               AS material_name,
    ''                               AS unit,
    1                                AS purchase_qty,
    amount                           AS unit_price,
    amount                           AS total_amount,
    DATE_FORMAT(occurred_date, '%Y-%m') AS purchase_month,
    occurred_date                    AS purchase_date,
    handler_name,
    voucher_url,
    remark,
    0
FROM expense_record
WHERE first_type_name = '自购成本'
  AND del_flag = 0
  AND occurred_date >= '2026-06-01';

-- 4. 迁移后验证
SELECT COUNT(*) AS migrated_cnt FROM self_purchase_material WHERE biz_code LIKE 'SPM_MIG_%';

-- 5. 确认无误后，软删原表记录（手动执行）
-- UPDATE expense_record SET del_flag = 1
-- WHERE first_type_name = '自购成本'
--   AND del_flag = 0
--   AND occurred_date >= '2026-06-01';
