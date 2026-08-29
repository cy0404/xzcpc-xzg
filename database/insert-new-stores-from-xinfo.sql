-- ============================================================
-- 新增门店：把 xinfo 新门店接口中有、本地 store_info 缺失的门店补录
-- 数据源：GET http://162.14.122.80:18088/api/external/stores（机器码认证）
-- 判定键：store_code = xinfo 门店 ID（数字）
--   1) 有 feishuChatId 的 185 家均已按 chat_id 在本地找到（179 唯一更新 + 6 家群重复不处理）
--   2) 仅 1 家无群 id 且本地不存在 → 新增：内蒙店 (id=331)
-- 新增行 store_id 为随机生成的企迈格式 cm id（用户确认：不匹配企迈，自行定义）
-- 生成日期：2026-08-21
-- ============================================================

-- 1) 预检：确认内蒙店本地确实不存在（应返回 0 行）
SELECT store_id, store_name, store_code, chat_id
FROM store_inventory.store_info
WHERE del_flag = 0
  AND (store_code = '331'
       OR store_name LIKE '%内蒙%'
       OR chat_id IS NULL AND store_name LIKE '%内蒙%');

-- 2) 预检：列出 xinfo 接口中的内蒙店原样数据（对照用）
--    id=331 code=S331A9F4C1157 name=象子茶铺茶(内蒙店) storeType=FRANCHISE status=ENABLED
--    province=内蒙古自治区 city=鄂尔多斯市 feishuChatId=null（无飞书群）

-- 3) 新增内蒙店（带防重守卫，可重复执行）
INSERT INTO store_inventory.store_info
    (store_id, store_name, store_code, chat_id, xiaochengxuid, cangkuid,
     qr_code, owner_name, owner_phone, owner_openid, supervisor_name,
     qmai_store_id, warehouse_id, weekly_inventory_day, weekly_paused, del_flag)
SELECT
    'cmq7o7ygw6yke3f7jjm9zs24p',  -- store_id: 随机生成 cm 格式（企迈格式），本地唯一
    '象子茶铺茶内蒙店',            -- store_name: 取自 xinfo "象子茶铺茶(内蒙店)"，去括号对齐本地命名
    '331',                          -- store_code: xinfo 门店 ID
    NULL,                           -- chat_id: xinfo 无 feishuChatId
    NULL,                           -- xiaochengxuid: xinfo 无 miniProgramId
    NULL,                           -- cangkuid: xinfo 无 warehouseId
    NULL, NULL, NULL, NULL,         -- qr_code / owner_name / owner_phone / owner_openid（本地维护）
    NULL,                           -- supervisor_name: xinfo 未分配督导
    NULL, NULL,                     -- qmai_store_id / warehouse_id（企迈控制台本地维护，未知）
    NULL, 0, 0                      -- weekly_inventory_day / weekly_paused / del_flag
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM store_inventory.store_info
    WHERE del_flag = 0
      AND (store_code = '331'
           OR store_id = 'cmq7o7ygw6yke3f7jjm9zs24p')
);

-- 4) 校验：应返回 1 行新增记录
SELECT id, store_id, store_name, store_code, chat_id, del_flag, created_at
FROM store_inventory.store_info
WHERE store_code = '331' OR store_id = 'cmq7o7ygw6yke3f7jjm9zs24p';
