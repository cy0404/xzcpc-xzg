-- 测试库 issue 表补充 source 列（生产库已有，migration/V16__issue_add_source.sql）
-- 背景：验收接口（acceptFromFeishu → IssueMapper.selectById）SELECT 含 source 列，测试库缺失导致 SQL 报错：
--       Unknown column 'source' in 'field list' → H5 验收 500
-- 执行：在测试库 store_inventory_test 中执行（建议先 SELECT * FROM issue LIMIT 1 确认无 source 列）
ALTER TABLE issue
    ADD COLUMN source VARCHAR(30) DEFAULT NULL
        COMMENT '来源渠道：MINI_PROGRAM(小程序)|FEISHU_GROUP(飞书群H5)|HQ(总部上报)|null(其他旧数据)'
        AFTER status;

ALTER TABLE issue ADD INDEX idx_source (source);
