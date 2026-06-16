-- 迁移：task 表和 expense_record 表增加 warehouse_code 字段
-- 从外部门店 API 获取 cangkuid 并固化，创建任务/支出时存入

ALTER TABLE task
    ADD COLUMN warehouse_code VARCHAR(50) DEFAULT NULL COMMENT '仓库编码快照' AFTER xiaochengxuid;

ALTER TABLE expense_record
    ADD COLUMN warehouse_code VARCHAR(50) DEFAULT NULL COMMENT '仓库编码快照' AFTER store_name;
