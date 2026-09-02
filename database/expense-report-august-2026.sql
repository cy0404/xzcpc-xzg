-- ============================================================
-- 8 月财报（2026-08-01 ~ 2026-08-31）
-- 口径对齐管理端 ExpenseDashboard：occurred_date 范围 + del_flag=0
-- 纯查询，可安全执行
-- ============================================================

-- ① 总览：总支出 / 记录数 / 门店数 / 平均门店支出 / TOP 门店 / 凭证率
SELECT
  COUNT(*)                                                        AS 记录数,
  SUM(amount)                                                     AS 总支出,
  COUNT(DISTINCT store_id)                                        AS 门店数,
  ROUND(SUM(amount) / COUNT(DISTINCT store_id), 2)                AS 平均门店支出,
  (SELECT store_name FROM expense_record
   WHERE occurred_date BETWEEN '2026-08-01' AND '2026-08-31' AND del_flag = 0
   GROUP BY store_name ORDER BY SUM(amount) DESC LIMIT 1)         AS 支出最高门店,
  (SELECT ROUND(SUM(amount), 2) FROM expense_record
   WHERE occurred_date BETWEEN '2026-08-01' AND '2026-08-31' AND del_flag = 0
   GROUP BY store_name ORDER BY SUM(amount) DESC LIMIT 1)         AS 最高门店金额,
  CONCAT(ROUND(SUM(voucher_url IS NOT NULL AND voucher_url <> '') * 100.0 / COUNT(*), 0), '%') AS 凭证率
FROM expense_record
WHERE occurred_date BETWEEN '2026-08-01' AND '2026-08-31' AND del_flag = 0;

-- ② 按门店汇总（支出排名）
SELECT store_name          AS 门店,
       COUNT(*)            AS 单数,
       SUM(amount)         AS 支出金额,
       ROUND(SUM(amount) * 100.0 / SUM(SUM(amount)) OVER (), 1) AS 占比,
       SUM(voucher_url IS NOT NULL AND voucher_url <> '') AS 有凭证单数
FROM expense_record
WHERE occurred_date BETWEEN '2026-08-01' AND '2026-08-31' AND del_flag = 0
GROUP BY store_name
ORDER BY 支出金额 DESC;

-- ③ 按一级类型汇总
SELECT first_type_name     AS 一级类型,
       COUNT(*)            AS 单数,
       SUM(amount)         AS 支出金额,
       ROUND(SUM(amount) * 100.0 / SUM(SUM(amount)) OVER (), 1) AS 占比
FROM expense_record
WHERE occurred_date BETWEEN '2026-08-01' AND '2026-08-31' AND del_flag = 0
GROUP BY first_type_name
ORDER BY 支出金额 DESC;

-- ④ 按二级类型汇总（类型分布，对齐 dashboard TypeDistribution）
SELECT type_name           AS 二级类型,
       first_type_name     AS 一级类型,
       COUNT(*)            AS 单数,
       SUM(amount)         AS 支出金额,
       ROUND(SUM(amount) * 100.0 / SUM(SUM(amount)) OVER (), 1) AS 占比
FROM expense_record
WHERE occurred_date BETWEEN '2026-08-01' AND '2026-08-31' AND del_flag = 0
GROUP BY type_name, first_type_name
ORDER BY 支出金额 DESC;

-- ⑤ 按项目汇总 TOP 20
SELECT item_name           AS 项目,
       type_name           AS 类型,
       COUNT(*)            AS 单数,
       SUM(amount)         AS 支出金额
FROM expense_record
WHERE occurred_date BETWEEN '2026-08-01' AND '2026-08-31' AND del_flag = 0
GROUP BY item_name, type_name
ORDER BY 支出金额 DESC
LIMIT 20;

-- ⑥ 门店 × 一级类型 透视（行=门店，列=一级类型）
SELECT store_name AS 门店,
       SUM(CASE WHEN first_type_name = '人工成本' THEN amount ELSE 0 END)     AS 人工成本,
       SUM(CASE WHEN first_type_name = '租金成本' THEN amount ELSE 0 END)     AS 租金成本,
       SUM(CASE WHEN first_type_name = '营运成本' THEN amount ELSE 0 END)     AS 营运成本,
       SUM(CASE WHEN first_type_name = '能耗成本' THEN amount ELSE 0 END)     AS 能耗成本,
       SUM(amount) AS 合计
FROM expense_record
WHERE occurred_date BETWEEN '2026-08-01' AND '2026-08-31' AND del_flag = 0
GROUP BY store_name
ORDER BY 合计 DESC;
