-- ============================================================
-- 物料表 material 加索引：优化 ORDER BY material_name 排序查询
-- 慢SQL：SELECT ... FROM material WHERE del_flag=0 ORDER BY material_name ASC LIMIT 100
-- 现状：~670ms（全表扫描 + filesort）
-- 预期：加索引后降到 <50ms（覆盖索引排序）
-- ============================================================

ALTER TABLE material
    ADD INDEX idx_material_del_name (del_flag, material_name);

SELECT 'migration-material-add-name-index.sql executed successfully' AS status;
