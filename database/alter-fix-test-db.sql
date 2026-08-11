-- ============================================================
-- 修正测试库表结构，对齐生产库
-- 执行: mysql -h 162.14.122.80 -P 3306 -u store_inventory -pXzcpc@2026 < alter-fix-test-db.sql
-- ============================================================

USE store_inventory_test;

-- 1. template_zone: zone_id 去自增 → 加 id → 拷值 → id 设自增主键
ALTER TABLE template_zone
    MODIFY zone_id INT NOT NULL;            -- 去掉 AUTO_INCREMENT
ALTER TABLE template_zone
    ADD COLUMN id INT FIRST;                -- 新增普通 id 列
UPDATE template_zone SET id = zone_id;      -- 拷值
ALTER TABLE template_zone
    DROP PRIMARY KEY,                       -- 去掉 zone_id 主键
    MODIFY id INT AUTO_INCREMENT PRIMARY KEY FIRST;  -- id 设为自增主键

-- 2. template_zone_material: 修正外键指向 template_zone.id
ALTER TABLE template_zone_material
    DROP FOREIGN KEY template_zone_material_ibfk_1;
ALTER TABLE template_zone_material
    ADD FOREIGN KEY (zone_id) REFERENCES template_zone(id);

-- 3. operation_log: 补充 source 字段
ALTER TABLE operation_log
    ADD COLUMN source VARCHAR(50) DEFAULT NULL COMMENT '来源：mp小程序/admin总部'
    AFTER user_id;

SELECT '测试库结构修正完成' AS status;
