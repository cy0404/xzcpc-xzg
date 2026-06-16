-- ============================================================
-- 盘点工具 1.0 — 乐观锁迁移
-- 核心业务表添加 version 字段
-- ============================================================

USE store_Inventory;

-- 总部端
ALTER TABLE template ADD COLUMN version INT DEFAULT 0 COMMENT '乐观锁版本号';
ALTER TABLE template_zone ADD COLUMN version INT DEFAULT 0 COMMENT '乐观锁版本号';
ALTER TABLE template_zone_material ADD COLUMN version INT DEFAULT 0 COMMENT '乐观锁版本号';

-- 门店默认清单
ALTER TABLE store_zone_material ADD COLUMN version INT DEFAULT 0 COMMENT '乐观锁版本号';

-- 月盘任务
ALTER TABLE task ADD COLUMN version INT DEFAULT 0 COMMENT '乐观锁版本号';
ALTER TABLE task_zone ADD COLUMN version INT DEFAULT 0 COMMENT '乐观锁版本号';
ALTER TABLE task_zone_material ADD COLUMN version INT DEFAULT 0 COMMENT '乐观锁版本号';
