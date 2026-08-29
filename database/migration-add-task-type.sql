-- ============================================================
-- 周盘功能：任务类型 + 周标签 + 模板类型 + 门店周盘点日
-- 执行环境：生产 store_Inventory + 测试库
-- ============================================================

-- 1. task 表加任务类型与周标签
ALTER TABLE task
    ADD COLUMN task_type VARCHAR(20) NOT NULL DEFAULT 'monthly' COMMENT '任务类型: monthly月盘|weekly周盘' AFTER task_month,
    ADD COLUMN task_week  VARCHAR(20) DEFAULT NULL COMMENT '盘点周 YYYY-Www(仅周盘)' AFTER task_type,
    ADD INDEX idx_task_type (task_type);

-- 2. template 表加模板类型
ALTER TABLE template
    ADD COLUMN template_type VARCHAR(20) NOT NULL DEFAULT 'monthly' COMMENT '模板类型: monthly月盘|weekly周盘' AFTER status;

-- 3. store_info 表加周盘点日配置（本地维护）
ALTER TABLE store_info
    ADD COLUMN weekly_inventory_day TINYINT DEFAULT NULL COMMENT '周盘点日(1周一-7周日, 仅周盘用, NULL=不参与周盘)' AFTER warehouse_id;

-- 存量数据无需回填（DEFAULT 'monthly' / NULL 自动生效，全部归为月盘）
