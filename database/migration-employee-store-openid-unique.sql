-- employee 表：同门店同 openid 唯一，防止并发重复插入
-- 执行前请先备份；若存在重复数据，先保留 id 最小的一条

DELETE e1 FROM employee e1
INNER JOIN employee e2
    ON e1.store_id = e2.store_id
   AND e1.openid = e2.openid
   AND e1.openid IS NOT NULL
   AND e1.openid != ''
WHERE e1.id > e2.id;

ALTER TABLE employee
    ADD UNIQUE KEY uk_employee_store_openid (store_id, openid);
