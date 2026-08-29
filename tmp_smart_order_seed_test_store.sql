-- ============================================================
-- 测试门店种子数据（仅生成一张待确认建议单）
-- ============================================================
-- 目标库：测试环境 store_inventory_test（162.14.122.80:3306）
-- 前置：该库已执行 database/migration/V17__smart_order.sql
--       （若报 "Table 'smart_order' doesn't exist" 就是 V17 没跑）
--
-- ⚠️ 注意：
-- 1. 明细数据源三级降级（测试门店无盘点数据时会自动用 B/C）：
--    A. 最近一次已提交盘点任务的 task_material_summary（与真实生成同源）
--    B. 该门店默认分区清单 store_zone_material（status=1）
--    C. 物料主数据 material（有企迈编码且已配置盘点规则的前 10 条）
--    无论哪级，建议数量都是"测试值"（A 库存×25%最低1；B/C 固定 3），
--    不是真实算法结果。真实生成 = 每周一 6 点定时任务，
--    或手动 POST /api/mp/smart-order/generate（全门店，需登录 token）。
-- 2. 在 H5 里点「确认订货」会【真实调企迈】创建报货单（测试门店也会真下单），
--    只想看页面就别点确认。
-- 3. 确认时需要 cangkuid（诊断第 1 步可查）；为空则后端尝试用近 60 天报货单自愈，
--    仍无 → 报「该门店尚未配置企迈仓库编码」。
-- 4. 幂等：本周已有单据（任意状态）则跳过，重复执行无副作用。
--    想删掉重新来：用文件末尾的清理语句。
-- 5. 数量为整数（库存单位），单价按库存单位换算；换算系数只支持单级
--    unit 换算链（多级链以真实生成为准）。
-- ============================================================

-- ---------- 0. 诊断（先看这几组结果） ----------
-- 0.1 测试门店行：store_id（外部API门店ID）会被用为 smart_order.store_id；
--     cangkuid 为空时确认订货会失败（除非近60天企迈报货单可自愈）
SELECT id, store_id, store_name, cangkuid, qmai_store_id
  FROM store_info
 WHERE store_name LIKE '%测试门店%' AND del_flag = 0
 ORDER BY id LIMIT 5;

-- 0.1b 若上面 cangkuid / qmai_store_id 为空，从【生产库 store_inventory】查测试门店的值并回填：
-- 生产库查询：SELECT id, store_id, store_name, cangkuid, qmai_store_id FROM store_info WHERE store_name LIKE '%测试门店%';
-- UPDATE store_info SET cangkuid = '<生产库查到的cangkuid>' WHERE store_name LIKE '%测试门店%' AND del_flag = 0;
-- UPDATE store_info SET qmai_store_id = <生产库查到的qmai_store_id> WHERE store_name LIKE '%测试门店%' AND del_flag = 0;

-- 0.2 数据源诊断（看哪一级降级源有数据）
SELECT 'A-任务汇总(已提交)' AS source,
       (SELECT COUNT(*) FROM task_material_summary s
         JOIN task t ON t.id = s.task_id AND t.del_flag = 0
        WHERE t.store_id = si.store_id AND t.status = 'submitted') AS cnt
  FROM store_info si WHERE si.store_name LIKE '%测试门店%' AND si.del_flag = 0
UNION ALL
SELECT 'B-门店默认分区清单',
       (SELECT COUNT(*) FROM store_zone_material z
        WHERE z.store_id = si.store_id AND z.status = 1 AND z.del_flag = 0)
  FROM store_info si WHERE si.store_name LIKE '%测试门店%' AND si.del_flag = 0
UNION ALL
SELECT 'C-物料主数据(有企迈编码)',
       (SELECT COUNT(*) FROM material m
        WHERE m.qm_code IS NOT NULL AND m.qm_code <> '' AND m.del_flag = 0)
  FROM store_info si WHERE si.store_name LIKE '%测试门店%' AND si.del_flag = 0;

-- 0.3 最近已提交盘点任务
SELECT t.id, t.task_month, t.store_name, t.submitted_at
  FROM task t
 WHERE t.store_id = (SELECT store_id FROM store_info
                      WHERE store_name LIKE '%测试门店%' AND del_flag = 0 ORDER BY id LIMIT 1)
   AND t.status = 'submitted' AND t.del_flag = 0
 ORDER BY t.submitted_at DESC LIMIT 3;

-- ---------- 1. 定位门店（用外部 store_id，全系统业务语义一致）+ 本周一 + 最近已提交任务 ----------
SET @storeId := (SELECT store_id FROM store_info
                  WHERE store_name LIKE '%测试门店%' AND del_flag = 0
                  ORDER BY id LIMIT 1);
SET @weekStart := DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY);  -- 本周一
SET @latestTask := (SELECT t.id FROM task t
                     WHERE t.store_id = @storeId AND t.status = 'submitted' AND t.del_flag = 0
                     ORDER BY t.submitted_at DESC LIMIT 1);

-- ---------- 2. 建议单主表（已有本周单据则跳过） ----------
INSERT INTO smart_order
  (biz_code, store_id, store_name, week_start_date, week_label,
   status, item_count, total_qty, suggest_amount, deadline, generated_at, sync_attempts)
SELECT CONCAT('SMO', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'), LPAD(FLOOR(RAND() * 10000), 4, '0')),
       @storeId, si.store_name, @weekStart,
       CONCAT(YEAR(@weekStart), '-W', LPAD(WEEK(@weekStart, 3), 2, '0')),
       'pending', 0, 0, 0,
       CONCAT(CURDATE(), ' 18:00:00'), NOW(), 0
  FROM store_info si
 WHERE si.store_id = @storeId
   AND NOT EXISTS (SELECT 1 FROM smart_order o
                    WHERE o.store_id = @storeId AND o.week_start_date = @weekStart);

-- ---------- 3. 明细（三级降级；已有明细的单据整体跳过，重复执行安全） ----------
INSERT INTO smart_order_item
  (order_id, material_id, material_name, spec, category, qm_code,
   stock_unit, base_unit, unit_price, current_inventory, daily_use,
   cycle_days, safety_days, suggest_qty, support_days, reason, sort_no)
-- A. 最近一次已提交任务的汇总物料（与真实生成同源）
SELECT o.id, m.id, s.material_name, COALESCE(s.spec, ''), m.category, m.qm_code,
       COALESCE(NULLIF(r.stock_unit, ''), COALESCE(s.base_unit, '')),
       COALESCE(s.base_unit, ''),
       ROUND(r.unit_price * COALESCE((
           SELECT cr.to_quantity / cr.from_quantity
             FROM material_conversion_rule cr
            WHERE cr.rule_id = r.rule_id AND cr.conversion_type = 'unit'
              AND cr.from_unit = r.stock_unit AND cr.to_unit = r.base_unit
              AND cr.del_flag = 0 LIMIT 1), 1), 2),
       COALESCE(s.total_qty, 0), 0.5, 7, 3,
       GREATEST(1, CEIL(COALESCE(s.total_qty, 0) * 0.25 / COALESCE((
           SELECT cr.to_quantity / cr.from_quantity
             FROM material_conversion_rule cr
            WHERE cr.rule_id = r.rule_id AND cr.conversion_type = 'unit'
              AND cr.from_unit = r.stock_unit AND cr.to_unit = r.base_unit
              AND cr.del_flag = 0 LIMIT 1), 1))),
       ROUND(COALESCE(s.total_qty, 0) / 0.5, 1),
       '按最近盘点库存与日均消耗估算',
       ROW_NUMBER() OVER (ORDER BY s.material_name)
  FROM task_material_summary s
  JOIN task t ON t.id = s.task_id AND t.del_flag = 0
  JOIN smart_order o ON o.store_id = @storeId AND o.week_start_date = @weekStart
  JOIN material m ON m.material_id = s.material_id
  LEFT JOIN material_inventory_rule r ON r.material_id = s.material_id
 WHERE s.task_id = @latestTask
   AND m.qm_code IS NOT NULL AND m.qm_code <> ''
   AND NOT EXISTS (SELECT 1 FROM smart_order_item x
                    WHERE x.order_id = o.id AND x.del_flag = 0)
UNION ALL
-- B. 无盘点数据时：门店默认分区清单
SELECT o.id, m.id, m.material_name, m.spec, m.category, m.qm_code,
       COALESCE(NULLIF(r.stock_unit, ''), r.base_unit, ''),
       COALESCE(r.base_unit, ''),
       ROUND(r.unit_price * COALESCE((
           SELECT cr.to_quantity / cr.from_quantity
             FROM material_conversion_rule cr
            WHERE cr.rule_id = r.rule_id AND cr.conversion_type = 'unit'
              AND cr.from_unit = r.stock_unit AND cr.to_unit = r.base_unit
              AND cr.del_flag = 0 LIMIT 1), 1), 2),
       NULL, 0.5, 7, 3, 3, NULL,
       '按门店默认分区清单估算',
       ROW_NUMBER() OVER (ORDER BY z.sort_no, m.material_name)
  FROM store_zone_material z
  JOIN smart_order o ON o.store_id = @storeId AND o.week_start_date = @weekStart
  JOIN material m ON m.id = z.material_id
  JOIN material_inventory_rule r ON r.material_id = m.material_id
 WHERE z.store_id = @storeId AND z.status = 1 AND z.del_flag = 0
   AND m.qm_code IS NOT NULL AND m.qm_code <> ''
   AND @latestTask IS NULL
   AND NOT EXISTS (SELECT 1 FROM smart_order_item x
                    WHERE x.order_id = o.id AND x.del_flag = 0)
UNION ALL
-- C. 清单也没有时：物料主数据前 10 条（有企迈编码）
(SELECT o.id, m.id, m.material_name, m.spec, m.category, m.qm_code,
       COALESCE(NULLIF(r.stock_unit, ''), r.base_unit, ''),
       COALESCE(r.base_unit, ''),
       ROUND(r.unit_price * COALESCE((
           SELECT cr.to_quantity / cr.from_quantity
             FROM material_conversion_rule cr
            WHERE cr.rule_id = r.rule_id AND cr.conversion_type = 'unit'
              AND cr.from_unit = r.stock_unit AND cr.to_unit = r.base_unit
              AND cr.del_flag = 0 LIMIT 1), 1), 2),
       NULL, 0.5, 7, 3, 3, NULL,
       '按物料主数据估算',
       ROW_NUMBER() OVER (ORDER BY m.material_name)
  FROM material m
  JOIN smart_order o ON o.store_id = @storeId AND o.week_start_date = @weekStart
  JOIN material_inventory_rule r ON r.material_id = m.material_id
 WHERE m.qm_code IS NOT NULL AND m.qm_code <> '' AND m.del_flag = 0
   AND @latestTask IS NULL
   AND NOT EXISTS (SELECT 1 FROM store_zone_material z
                    WHERE z.store_id = @storeId AND z.status = 1 AND z.del_flag = 0)
   AND NOT EXISTS (SELECT 1 FROM smart_order_item x
                    WHERE x.order_id = o.id AND x.del_flag = 0)
 ORDER BY m.material_name
 LIMIT 10);

-- ---------- 4. 回填汇总 ----------
UPDATE smart_order o
   SET item_count = (SELECT COUNT(*) FROM smart_order_item i WHERE i.order_id = o.id AND i.del_flag = 0),
       total_qty  = (SELECT COALESCE(SUM(i.suggest_qty), 0) FROM smart_order_item i WHERE i.order_id = o.id AND i.del_flag = 0),
       suggest_amount = (SELECT COALESCE(SUM(i.suggest_qty * COALESCE(i.unit_price, 0)), 0)
                           FROM smart_order_item i WHERE i.order_id = o.id AND i.del_flag = 0)
 WHERE o.store_id = @storeId AND o.week_start_date = @weekStart;

-- ---------- 5. 验证（应能看到 1 张 pending 单 + 明细行） ----------
SELECT o.id, o.store_id, o.store_name, o.week_label, o.status,
       o.item_count, o.total_qty, o.suggest_amount, o.deadline
  FROM smart_order o
 WHERE o.store_id = @storeId AND o.week_start_date = @weekStart;

SELECT i.id, i.material_name, i.spec, i.stock_unit, i.suggest_qty, i.unit_price, i.qm_code, i.reason
  FROM smart_order_item i
  JOIN smart_order o ON o.id = i.order_id
 WHERE o.store_id = @storeId AND o.week_start_date = @weekStart AND i.del_flag = 0
 ORDER BY i.sort_no;

-- ---------- 6. 旧种子文案清理（去掉"测试数据"前缀，幂等，只影响 reason 以"测试数据"开头的行） ----------
UPDATE smart_order_item i
  JOIN smart_order o ON o.id = i.order_id
   SET i.reason = CASE
         WHEN i.reason LIKE '测试数据：按默认日均消耗估算%' THEN '按最近盘点库存与日均消耗估算'
         WHEN i.reason LIKE '测试数据：门店默认分区清单%' THEN '按门店默认分区清单估算'
         WHEN i.reason LIKE '测试数据：物料主数据%' THEN '按物料主数据估算'
         ELSE i.reason END
 WHERE o.store_name LIKE '%测试门店%'
   AND i.del_flag = 0
   AND i.reason LIKE '测试数据%';

-- ---------- 清理（想删掉种子数据重新生成时取消注释执行） ----------
-- DELETE FROM smart_order_item
--  WHERE order_id IN (SELECT o.id FROM smart_order o
--                      WHERE o.store_id = @storeId AND o.week_start_date = @weekStart);
-- DELETE FROM smart_order
--  WHERE store_id = @storeId AND week_start_date = @weekStart;
