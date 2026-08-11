-- 给已有测试库的 operation_log 补充 source 字段
ALTER TABLE store_inventory_test.operation_log
    ADD COLUMN source VARCHAR(50) DEFAULT NULL COMMENT '来源：mp小程序/admin总部'
    AFTER user_id;
