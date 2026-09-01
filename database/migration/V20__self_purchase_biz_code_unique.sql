-- V20: self_purchase_material.biz_code 唯一索引（防业务号撞号）
-- 背景：SPM 业务号原为「SPM + 分钟 + 随机3位」，同一分钟内 1/1000 概率撞号；
--      后端已改为「SPM + 分钟 + 自增主键」派生（先 TMP_ 插入拿 id 再回填），此脚本给 biz_code 加唯一索引做兜底。
-- 执行顺序：先升级后端代码，再执行本脚本。
-- 若第 1 步有查询结果，必须先执行第 2 步修复，否则第 3 步会因重复值失败。

-- 1. 检查历史撞号（应返回 0 行；有结果则执行第 2 步）
SELECT biz_code, COUNT(*) AS c, GROUP_CONCAT(id) AS ids
FROM self_purchase_material
GROUP BY biz_code
HAVING c > 1;

-- 2. 修复撞号（仅当第 1 步有结果时执行）：
--    同一 biz_code 保留 id 最大的记录，其余记录（含其明细行）业务号改为「原号-id」，
--    保证每单唯一且明细仍挂在原单下（明细表先改，此时主表还是旧号可关联）
UPDATE self_purchase_material_item i
JOIN self_purchase_material m ON i.biz_code = m.biz_code
LEFT JOIN (SELECT biz_code, MAX(id) AS keep_id FROM self_purchase_material GROUP BY biz_code) k
       ON m.biz_code = k.biz_code
SET i.biz_code = CONCAT(i.biz_code, '-', m.id)
WHERE k.keep_id IS NOT NULL AND m.id <> k.keep_id;

UPDATE self_purchase_material m
LEFT JOIN (SELECT biz_code, MAX(id) AS keep_id FROM self_purchase_material GROUP BY biz_code) k
       ON m.biz_code = k.biz_code
SET m.biz_code = CONCAT(m.biz_code, '-', m.id)
WHERE k.keep_id IS NOT NULL AND m.id <> k.keep_id;

-- 3. 加唯一索引（无撞号数据时可直接执行）
ALTER TABLE self_purchase_material ADD UNIQUE KEY uk_biz_code (biz_code);
