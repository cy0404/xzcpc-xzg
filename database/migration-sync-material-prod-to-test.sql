-- ============================================================
-- 物料主数据：生产库 → 测试库 全量镜像（material 三表）
-- 用途：把生产环境的物料信息同步到 store_inventory_test，便于本地/测试验证
-- 执行方式：mysql 客户端或 SQL 工具在任意库执行（跨库语句，无 USE 依赖）
--
-- 注意：
--   1. 测试库三表将被生产数据【全量替换】（先删后插），测试库本地人工
--      新增/修改的物料数据会丢失——生产为权威源，如需保留请先备份
--   2. 测试库 template_zone_material / task_zone_material 等快照表的
--      material_id 引用可能因物料被替换/删除而悬空，属预期（物料以生产为准）
--   3. 两库表结构一致（2026-08-29 已核对 12/14/9 列），SELECT * 直接复制
--   4. 执行前建议先跑一次生产 xinfo 同步（POST /api/materials/sync），
--      确保生产物料是最新状态
-- ============================================================

-- 1) 清空测试库三表（按依赖顺序：换算行 → 规则 → 物料）
DELETE FROM store_inventory_test.material_conversion_rule;
DELETE FROM store_inventory_test.material_inventory_rule;
DELETE FROM store_inventory_test.material;

-- 2) 从生产库全量复制（保留原主键 id / 业务键 material_id / rule_id）
INSERT INTO store_inventory_test.material
SELECT * FROM store_inventory.material;

INSERT INTO store_inventory_test.material_inventory_rule
SELECT * FROM store_inventory.material_inventory_rule;

INSERT INTO store_inventory_test.material_conversion_rule
SELECT * FROM store_inventory.material_conversion_rule;

-- 3) 修正测试库自增游标（生产最大 id 可能大于测试库当前 AUTO_INCREMENT，
--    否则后续手工新增物料会主键冲突）
SET @max_material_id = (SELECT IFNULL(MAX(id), 0) + 1 FROM store_inventory_test.material);
SET @max_rule_id    = (SELECT IFNULL(MAX(id), 0) + 1 FROM store_inventory_test.material_inventory_rule);
SET @max_conv_id    = (SELECT IFNULL(MAX(id), 0) + 1 FROM store_inventory_test.material_conversion_rule);
SET @sql1 = CONCAT('ALTER TABLE store_inventory_test.material AUTO_INCREMENT = ', @max_material_id);
SET @sql2 = CONCAT('ALTER TABLE store_inventory_test.material_inventory_rule AUTO_INCREMENT = ', @max_rule_id);
SET @sql3 = CONCAT('ALTER TABLE store_inventory_test.material_conversion_rule AUTO_INCREMENT = ', @max_conv_id);
PREPARE stmt1 FROM @sql1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;
PREPARE stmt2 FROM @sql2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;
PREPARE stmt3 FROM @sql3; EXECUTE stmt3; DEALLOCATE PREPARE stmt3;

-- 4) 核对（应分别等于生产行数：material 465 / rule 222 / conversion 444）
SELECT 'material' AS tbl, COUNT(*) AS cnt FROM store_inventory_test.material
UNION ALL SELECT 'material_inventory_rule', COUNT(*) FROM store_inventory_test.material_inventory_rule
UNION ALL SELECT 'material_conversion_rule', COUNT(*) FROM store_inventory_test.material_conversion_rule;
