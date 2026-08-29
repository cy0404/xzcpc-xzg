-- ============================================================
-- 生产库 admin_permission.user_id 同步到测试库（自动生成）
-- 导出时间：2026-08-26 | 共 9 条（仅 user_id 非空的行）
-- 用法：直接在测试库执行；仅更新 user_id，其他字段不受影响
-- 用 UPDATE 而非 INSERT：避免 1364（字段 NOT NULL 无默认值）
-- ============================================================

UPDATE admin_permission SET `user_id` = 'f21119eb' WHERE `id` = 52;
UPDATE admin_permission SET `user_id` = 'bd157g8d' WHERE `id` = 53;
UPDATE admin_permission SET `user_id` = '3bca28eb' WHERE `id` = 54;
UPDATE admin_permission SET `user_id` = '5gc332be' WHERE `id` = 56;
UPDATE admin_permission SET `user_id` = '114d9b68' WHERE `id` = 57;
UPDATE admin_permission SET `user_id` = '4f7a8g11' WHERE `id` = 58;
UPDATE admin_permission SET `user_id` = 'c8d7f34e' WHERE `id` = 59;
UPDATE admin_permission SET `user_id` = 'be58c977' WHERE `id` = 60;
UPDATE admin_permission SET `user_id` = 'e4ae7cc2' WHERE `id` = 65;

-- 说明：按主键 id 逐行更新；id 在测试库中不存在的行不会报错，仅跳过
