-- ============================================================
-- 5 条新半成品补换算行（测试库验证 SQL）
-- 背景：8 月模板这 5 条半成品 Excel 业务口径按 g 盘点（换算列 1000g=1kg），
--       规则 base_unit=kg（接口 unit），无换算行则 g 录入无法换算汇总。
--       方向与现有 45 条半成品换算行一致：1kg = 1000g（换算系统 BFS 双向可用）
-- 幂等：若重复执行，INSERT IGNORE + 唯一约束（rule_id+conversion_type+from_unit+to_unit）拦截
-- ============================================================

INSERT INTO material_conversion_rule
    (rule_id, conversion_type, from_quantity, from_unit, to_quantity, to_unit, sort_no, del_flag)
SELECT r.rule_id, 'unit', 1.0, 'kg', 1000.0, 'g', 1, 0
FROM material_inventory_rule r
WHERE r.rule_id IN ('MR00001416','MR00001417','MR00001418','MR00001419','MR00001420')
  AND NOT EXISTS (SELECT 1 FROM material_conversion_rule c
                  WHERE c.rule_id = r.rule_id AND c.del_flag = 0 AND c.from_unit = 'kg' AND c.to_unit = 'g');

-- 核对（应 5 行，全部 rule 各 1 条 1kg=1000g）
SELECT c.rule_id, m.qm_code, m.material_name, c.from_quantity, c.from_unit, c.to_quantity, c.to_unit
FROM material_conversion_rule c
JOIN material_inventory_rule r ON r.rule_id = c.rule_id
JOIN material m ON m.material_id = r.material_id
WHERE c.rule_id IN ('MR00001416','MR00001417','MR00001418','MR00001419','MR00001420') AND c.del_flag = 0;
