-- ============================================================
-- 测试库 xinfo 基础字段 → 同步到生产库（自动生成）
-- 字段：xinfo_store_name / province / city / district / address
-- 匹配键：store_info.id（两个库 id 一致，之前 store_code 同步已对齐）
-- 用法：在 162.14.122.80:3306 任一连接执行（两库同实例，可直接跨库 JOIN）
-- 注意：生产库这 5 个字段当前全为空（0 条），本脚本为全量覆盖；若生产已有值请先备份
-- ============================================================

UPDATE store_inventory.store_info p
INNER JOIN store_inventory_test.store_info t ON p.id = t.id
SET p.xinfo_store_name = t.xinfo_store_name,
    p.province        = t.province,
    p.city            = t.city,
    p.district        = t.district,
    p.address         = t.address;

-- ============================================================
-- 校验（执行后应看到 xinfo_store_name 非空 ≈ 185）
-- SELECT COUNT(*) FROM store_inventory.store_info
--  WHERE xinfo_store_name IS NOT NULL AND xinfo_store_name != '';
-- ============================================================
