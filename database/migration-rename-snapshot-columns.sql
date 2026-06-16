-- 迁移：task_zone_material 表去掉 snapshot 后缀
-- 表名本身已表明是快照表，字段名无需重复标注

ALTER TABLE task_zone_material
    CHANGE material_name_snapshot material_name VARCHAR(200) NOT NULL COMMENT '物料名称',
    CHANGE spec_snapshot          spec          VARCHAR(100) NOT NULL DEFAULT '' COMMENT '规格',
    CHANGE unit_snapshot          unit          VARCHAR(50)  NOT NULL COMMENT '单位',
    CHANGE inventory_unit_snapshot inventory_unit VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '盘点单位';
