SELECT
    t.type_id,
    t.name    AS type_name,
    t.status  AS status,
    t.sort_no AS cur_sort,
    (
        SELECT COUNT(*) FROM expense_record er
        WHERE er.del_flag = 0
          AND er.type_id = t.type_id
          AND DATE_FORMAT(er.occurred_date, '%Y-%m') = DATE_FORMAT(CURDATE(), '%Y-%m')
    )
    + CASE WHEN t.name = '自购食材' THEN (
        SELECT COUNT(*) FROM self_purchase_material spm
        WHERE spm.del_flag = 0
          AND spm.purchase_month = DATE_FORMAT(CURDATE(), '%Y-%m')
      ) ELSE 0 END AS month_usage,
    (
        SELECT IFNULL(SUM(er.amount), 0) FROM expense_record er
        WHERE er.del_flag = 0
          AND er.type_id = t.type_id
          AND DATE_FORMAT(er.occurred_date, '%Y-%m') = DATE_FORMAT(CURDATE(), '%Y-%m')
    )
    + CASE WHEN t.name = '自购食材' THEN (
        SELECT IFNULL(SUM(spm.total_amount), 0) FROM self_purchase_material spm
        WHERE spm.del_flag = 0
          AND spm.purchase_month = DATE_FORMAT(CURDATE(), '%Y-%m')
      ) ELSE 0 END AS month_amount
FROM expense_type t
WHERE t.del_flag = 0
ORDER BY month_usage DESC, t.sort_no ASC, t.id ASC;
