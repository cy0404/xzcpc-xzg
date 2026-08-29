-- ============================================================
-- 门店订货周期表 + 周盘按订货周期下发（2026-08-28）
-- 业务链路：订货日前一天自动生成周盘任务 → 周盘提交后自动生成智能订货单
-- 执行库：store_inventory（正式）/ store_inventory_test（测试）均需执行（在对应库下执行即可，脚本无 USE）
-- ============================================================

-- 1) 门店订货周期表（替代 store_info.weekly_inventory_day / weekly_paused）
--    order_days: 订货日 1-7 逗号分隔（1=周一 … 7=周日），空/无记录 = 不参与周盘
CREATE TABLE IF NOT EXISTS store_order_cycle (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  store_id VARCHAR(64) NOT NULL COMMENT '门店ID',
  order_days VARCHAR(20) NOT NULL COMMENT '订货日(1-7逗号分隔,1=周一)',
  paused TINYINT DEFAULT 0 COMMENT '暂停周盘 0参与 1暂停',
  version INT DEFAULT 0 COMMENT '乐观锁',
  del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店订货周期配置';

-- 2) 迁移：旧「周盘点日」→ 订货日 = 盘点日次日（盘点日不变 → 周盘节奏不变；周日盘 → 周一订）
--    store_id 取 store_info.store_id（外部门店ID，与 task.store_id 同源）
--    weekly_inventory_day=7(周日盘点) → order_days='1'(周一订货)；其余 = 盘点日+1
INSERT INTO store_order_cycle (store_id, order_days, paused)
SELECT store_id,
       IF(weekly_inventory_day = 7, '1', CAST(weekly_inventory_day + 1 AS CHAR)),
       IFNULL(weekly_paused, 0)
FROM store_info
WHERE weekly_inventory_day IS NOT NULL AND del_flag = 0;

-- 3) 智能订货单增加来源周盘任务关联（支持每周多个订货日各生成一张建议单）
ALTER TABLE smart_order
  ADD COLUMN task_id BIGINT NULL COMMENT '来源周盘任务ID' AFTER week_label;

-- 幂等约束：同店同一周盘任务只生成一张建议单
ALTER TABLE smart_order
  ADD UNIQUE KEY uk_store_task (store_id, task_id);

-- 4) 校验（执行后应能看到迁移行数）
-- SELECT COUNT(*) FROM store_order_cycle;
