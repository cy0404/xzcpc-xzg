-- 迁移：优化 template_zone_material 查询性能
-- GET /api/templates/{id}/zones/{zoneId}/materials 慢查询优化
-- 查询：SELECT ... WHERE zone_id = ? AND del_flag = 0 ORDER BY sort_no ASC
-- 原索引 uk_zone_material (zone_id, material_id) 无法覆盖 del_flag 和 sort_no 导致 filesort
-- 新索引覆盖 WHERE + ORDER BY 全链路，避免回表和排序

ALTER TABLE template_zone_material
    ADD INDEX idx_zone_del_sort (zone_id, del_flag, sort_no);
