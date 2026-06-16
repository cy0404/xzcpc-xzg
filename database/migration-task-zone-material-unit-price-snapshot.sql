-- 迁移：task_zone_material 增加单价快照字段
-- 保存物料录入时的盘点单价，不受后续规则变更影响

ALTER TABLE task_zone_material
    ADD COLUMN unit_price_snapshot DECIMAL(10,2) DEFAULT NULL COMMENT '盘点单价快照（录入时的单价，不受后续规则变更影响）' AFTER conversion_snapshot;
