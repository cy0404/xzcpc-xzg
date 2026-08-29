-- ============================================================
-- 智能订货：企迈仓库编码 = store_info.cangkuid（复用既有列，无需运维填充）
-- ============================================================
-- 说明：
-- 1. cangkuid 是 store_info 既有列（仓库ID，来自企迈外部 API），
--    门店/物料定时同步任务会自动填充，绝大多数门店无需任何手工操作。
-- 2. 创建企迈报货单时 warehouseNo 取该列；确认订货时若为空，系统会从
--    该门店最近 60 天的报货单自动取 storeWarehouseNo 并回写（自愈）。
-- 3. 仅当某门店 cangkuid 为空且从未产生过报货单记录时，才需要手工补填。

-- 查询哪些门店还没有仓库编码（供核对；为空不代表有问题，可能只是未同步/无报货单）：
SELECT id, store_id, store_name, qmai_store_id, cangkuid
  FROM store_info
 WHERE del_flag = 0 AND (cangkuid IS NULL OR cangkuid = '');

-- 确需手工补填时（编码以企迈门店后台「门店档案」为准）：
-- UPDATE store_info SET cangkuid = 'WAREHOUSE_CODE_EXAMPLE' WHERE store_id = 'STORE_ID_EXAMPLE';
