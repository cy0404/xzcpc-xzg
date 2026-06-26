-- 物料录入时间 & 分区保存时间
ALTER TABLE task_zone_material ADD COLUMN entered_at DATETIME DEFAULT NULL COMMENT '录入时间';
ALTER TABLE task_zone ADD COLUMN saved_at DATETIME DEFAULT NULL COMMENT '分区保存时间';
