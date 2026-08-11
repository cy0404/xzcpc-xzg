-- Issue 实体精简后，以下列不再由代码写入，需确保有默认值避免 INSERT 报错
-- 执行前建议先查一次确认线上各列的默认值情况

ALTER TABLE issue MODIFY COLUMN contact_name    VARCHAR(50)   DEFAULT ''  COMMENT '联系人';
ALTER TABLE issue MODIFY COLUMN contact_phone   VARCHAR(20)   DEFAULT ''  COMMENT '联系电话';
ALTER TABLE issue MODIFY COLUMN images          TEXT          DEFAULT NULL COMMENT '图片URL（逗号分隔）';
ALTER TABLE issue MODIFY COLUMN process_result   VARCHAR(1000) DEFAULT NULL COMMENT '处理结果/最新进度';
ALTER TABLE issue MODIFY COLUMN accepted_by      VARCHAR(100)  DEFAULT NULL COMMENT '验收人 openid';
ALTER TABLE issue MODIFY COLUMN accepted_at      DATETIME      DEFAULT NULL COMMENT '验收时间';
ALTER TABLE issue MODIFY COLUMN submitted_by     VARCHAR(100)  DEFAULT NULL COMMENT '提交人 openid';
ALTER TABLE issue MODIFY COLUMN processed_at     DATETIME      DEFAULT NULL COMMENT '处理时间';
ALTER TABLE issue MODIFY COLUMN resolved_at      DATETIME      DEFAULT NULL COMMENT '解决时间';
