-- 迁移：task_zone_material 增加 unit_inputs 字段
-- 用于保存多单位录入时的各单位输入拆分（JSON格式）

ALTER TABLE task_zone_material
    ADD COLUMN unit_inputs TEXT DEFAULT NULL COMMENT '各单位输入拆分JSON，如{"箱":"1","瓶":"2"}' AFTER conversion_snapshot;
