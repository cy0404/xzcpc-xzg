-- ============================================================
-- task_zone_material 加 append_records 字段
-- 存储累加录入记录 JSON 数组，每条格式：
-- {"input":"1箱 + 3包 + 50个","total":"350","unit":"个","time":"2026-07-02 14:30:00"}
-- 日期：2026-07-02
-- ============================================================

USE store_inventory;

ALTER TABLE task_zone_material
    ADD COLUMN append_records TEXT DEFAULT NULL COMMENT '累加记录JSON数组' AFTER unit_inputs;
