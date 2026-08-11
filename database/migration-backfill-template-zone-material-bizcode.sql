-- template_zone_material 表 biz_code 回填
-- 格式：TZM + yyyyMMddHHmm + 4位序号
SET @row = 0;
UPDATE template_zone_material
SET biz_code = CONCAT('TZM', DATE_FORMAT(NOW(), '%Y%m%d%H%i'), LPAD(@row := @row + 1, 4, '0'))
WHERE biz_code = '';
