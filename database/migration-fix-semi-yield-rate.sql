-- ============================================================
-- 修复 semi_product.yield_rate 精度溢出（Out of range value）
-- 原因：生产表还是 DECIMAL(6,4)（整数位 2 位，装不下 100.0 得率），
--       接口返回的最大得率为 100.0，需 DECIMAL(7,4)（整数位 3 位）。
-- 执行方式：手动执行（生产库 store_inventory）
--          mysql -uxzcpc -p store_inventory < database/migration-fix-semi-yield-rate.sql
-- 执行后：重新调 GET /api/admin/semi-formula/sync 即可（幂等 upsert）
-- ============================================================

-- ① 确认当前定义（应为 decimal(6,4)）
SHOW COLUMNS FROM semi_product WHERE Field = 'yield_rate';

-- ② 修复精度
ALTER TABLE semi_product
  MODIFY COLUMN yield_rate DECIMAL(7,4) DEFAULT NULL COMMENT '生效配方得率（接口最大 100.0）';

-- ③ 核对（应变为 decimal(7,4)）
SHOW COLUMNS FROM semi_product WHERE Field = 'yield_rate';
