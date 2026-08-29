-- ============================================================
-- 修复：员工表 mock 脏数据导致"去收货"报「入库单不存在」
-- 库：store_inventory_test（测试环境 162.14.122.80）
-- 背景：
--   employee id=127（openid=oK2o33VPGXkBpwW5Cu2JmuWnO2Xk，store_id='test1001'，role=老板）
--   是早期 mock 测试数据，store_id='test1001' 不是有效门店编码。
--   小程序登录时 findStoresByOpenid 按 id 升序取第一条回填 session.store_id，
--   导致该用户每次登录后 session.store_id 都被设为 'test1001'，
--   而入库单详情接口校验 order.store_id.equals(session.store_id) 失败 → 500「入库单不存在」。
-- 处理：删除该 mock 记录（已确认无任何业务引用）。
--   修复后登录自动匹配到 id=700（cmpz23uu201ta3pq33tchfhdo）。
-- ============================================================

-- 先确认目标记录（应返回 1 行，name 为乱码/老板，store_id='test1001'）
SELECT id, name, store_id, role, status, openid
FROM employee
WHERE id = 127;

-- 删除 mock 数据
DELETE FROM employee WHERE id = 127;

-- 验证：该 openid 下不再有 test1001，最新门店列表 id 最小的应为 cmpz23uu201ta3pq33tchfhdo
SELECT id, name, store_id, role, status
FROM employee
WHERE openid = 'oK2o33VPGXkBpwW5Cu2JmuWnO2Xk'
  AND status = '在职'
ORDER BY id ASC
LIMIT 5;
