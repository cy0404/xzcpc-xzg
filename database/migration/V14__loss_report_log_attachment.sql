-- V14: loss_report_log 增加附件字段
ALTER TABLE loss_report_log ADD COLUMN attachment_url TEXT DEFAULT NULL COMMENT '操作附件(逗号分隔的图片/视频URL)' AFTER remark;
