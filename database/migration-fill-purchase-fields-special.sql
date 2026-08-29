-- =====================================================================
-- 补录特殊物料采购字段（migration-fill-purchase-fields.sql 的补充）
-- 背景：第一批 424 条 CASE 补录后 still_missing=38，其中：
--   * 36 条半成品（紫米/牛油果泥/茶汤等）：半成品接口无 purchaseUnit/purchasePrice 字段，
--     不补，等接口加字段后由同步自动补录
--   * WP0551 南非橙：原料/半成品两个接口均不存在（接口侧已下架），无数据可补
--   * WP0752 外卖安心贴：接口已改码为 WP0966（原料接口 ENABLED，purchasePrice=88.0，
--     purchaseUnit=包），库里 qm_code 仍是旧编码 WP0752，本脚本按旧编码匹配补录
-- 说明：只补 material_inventory_rule 表采购字段，不动 material 表主数据
--      （qm_code 是否同步改成 WP0966 待业务确认，扫码/报表可能依赖旧码）
-- =====================================================================

UPDATE material_inventory_rule r
JOIN material m ON m.material_id = r.material_id
SET r.purchase_price = 88.0,
    r.purchase_unit = '包'
WHERE m.qm_code = 'WP0752'
  AND m.del_flag = 0
  AND r.del_flag = 0;

-- 验证：执行后应返回 0 行
-- SELECT r.material_id, m.material_name, m.qm_code, r.purchase_price, r.purchase_unit
-- FROM material_inventory_rule r
-- JOIN material m ON m.material_id = r.material_id
-- WHERE m.del_flag = 0 AND r.del_flag = 0
--   AND (r.purchase_price IS NULL OR r.purchase_unit IS NULL);
