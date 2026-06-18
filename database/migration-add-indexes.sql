-- 盘点工具性能优化 — 添加缺失索引
-- 执行前请确认目标库为 store_inventory
-- 如索引已存在会报错，跳过该行继续执行即可

USE store_inventory;

-- 1. employee 表：openid 和 store_id 查询频繁
--    使用场景：登录 /auth/me、员工列表、离职拦截
CREATE INDEX idx_employee_openid_status ON employee(openid, status);
CREATE INDEX idx_employee_store_id ON employee(store_id);

-- 2. material 表：material_id 精确查询
--    使用场景：getByMaterialId、getMaterialInfoById
CREATE INDEX idx_material_material_id ON material(material_id);

-- 3. material_inventory_rule 表：material_id IN 批量查询
--    使用场景：fillInventoryUnit（每次查物料列表都调）
CREATE INDEX idx_rule_material_id ON material_inventory_rule(material_id);

-- 4. task 表：按门店筛选任务列表
--    使用场景：GET /tasks、MpLoginInterceptor 校验归属
CREATE INDEX idx_task_store_id ON task(store_id);

-- 5. task_zone_material 表：按 task_id 查所有分区物料
--    使用场景：任务详情、分区物料列表、批量保存
CREATE INDEX idx_tzm_task_id ON task_zone_material(task_id);
