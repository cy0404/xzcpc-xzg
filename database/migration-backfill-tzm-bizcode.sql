-- ============================================================
-- 迁移：template_zone_material 表 biz_code 回填
-- 格式：TZM + YYYYMMDDHHmm + 4位序号
-- 示例：TZM2026061613110001
-- ============================================================

USE store_inventory;

-- 使用用户变量生成递增序号
SET @row_number = 0;

UPDATE template_zone_material
SET biz_code = CONCAT(
    'TZM',
    DATE_FORMAT(NOW(), '%Y%m%d%H%i'),
    LPAD(@row_number := @row_number + 1, 4, '0')
)
WHERE biz_code = '';

-- 验证
SELECT COUNT(*) AS remaining_empty
FROM template_zone_material
WHERE biz_code = '';

SELECT 'template_zone_material biz_code backfill completed' AS status;
