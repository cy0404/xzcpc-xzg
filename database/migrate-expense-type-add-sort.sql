-- ============================================================
-- 支出类型（expense_type）增加排序字段 sort_no
-- 用途：支出登记时的支出项展示顺序，由 sort_no 升序控制（越小越靠前，NULL 排最后）
-- 说明：手动执行；执行后请按需修改各行 sort_no 值来调整支出项顺序
-- 生效范围：总部管理列表立即生效；小程序端有 10 分钟类型缓存，最多 10 分钟后刷新
-- ============================================================

-- 1. 新增排序字段
ALTER TABLE expense_type
    ADD COLUMN sort_no INT DEFAULT NULL COMMENT '排序号，越小越靠前，NULL 排最后' AFTER status;

-- 2. 初始化现有数据：按当前 id 顺序编号为 1,2,3...（仅未删除的记录），方便后续手动微调
SET @rn := 0;
UPDATE expense_type
SET sort_no = (@rn := @rn + 1)
WHERE del_flag = 0
ORDER BY id;
