-- 迁移：task_zone_material 增加盘点结果快照字段
-- 用于固化小程序录入时的原始数量、换算规则和基础单位数量

ALTER TABLE task_zone_material
    ADD COLUMN input_mode          VARCHAR(20)    DEFAULT NULL COMMENT '录入模式：unit按单位录入，weight称重录入' AFTER input_status,
    ADD COLUMN input_original_qty  DECIMAL(18,4)  DEFAULT NULL COMMENT '用户原始录入数量' AFTER input_mode,
    ADD COLUMN input_original_unit VARCHAR(50)    DEFAULT NULL COMMENT '用户原始录入单位；称重时为重量单位' AFTER input_original_qty,
    ADD COLUMN base_unit_snapshot  VARCHAR(50)    DEFAULT NULL COMMENT '基础盘点单位快照' AFTER input_original_unit,
    ADD COLUMN rule_id_snapshot    VARCHAR(50)    DEFAULT NULL COMMENT '物料盘点规则ID快照' AFTER base_unit_snapshot,
    ADD COLUMN base_qty            DECIMAL(18,4)  DEFAULT NULL COMMENT '折算后的基础单位数量' AFTER rule_id_snapshot,
    ADD COLUMN conversion_snapshot TEXT           DEFAULT NULL COMMENT '本次使用的换算规则JSON快照' AFTER base_qty;

-- 存量录入数据兼容：把旧 input_qty 视为基础单位数量
UPDATE task_zone_material
SET base_qty = input_qty
WHERE base_qty IS NULL
  AND input_qty IS NOT NULL;
