-- ============================================================
-- V16: issue 表新增 source 字段 —— 问题来源渠道
-- ============================================================

USE store_inventory;

-- 新增 source 列
ALTER TABLE issue ADD COLUMN source VARCHAR(30) DEFAULT NULL COMMENT '来源渠道：MINI_PROGRAM(小程序)|FEISHU_GROUP(飞书群H5)|HQ(总部上报)|null(其他旧数据)' AFTER status;

-- 索引
ALTER TABLE issue ADD INDEX idx_source (source);

SELECT 'V16__issue_add_source.sql executed successfully' AS status;
