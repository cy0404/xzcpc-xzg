-- ============================================================
-- 周盘功能：门店周盘暂停开关
-- 执行环境：生产 store_Inventory + 测试库
-- ============================================================

ALTER TABLE store_info
    ADD COLUMN weekly_paused TINYINT NOT NULL DEFAULT 0 COMMENT '周盘暂停: 0参与 1暂停(暂停后自动生成跳过该店)' AFTER weekly_inventory_day;
