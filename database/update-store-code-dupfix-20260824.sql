-- ============================================================
-- 补充更新：原 179 家群ID更新后遗留的 5 家门店（群重复/缺失）
-- 数据源：GET http://162.14.122.80:18088/api/external/stores（机器码认证）
-- 匹配键：本地 store_info.store_id（企迈 cm id，唯一稳定）
-- 对应关系判定（2026-08-24 最新接口数据）：
--   249 华宁店  ← 本地华宁店        （群 oc_89e5 恢复唯一，chatName 完全一致）
--   360 盘溪店  ← 本地玉溪华宁盘溪镇店（群 oc_3320a33c 为新拆出的独立群，chatName=玉溪华宁店）
--   296 广南店  ← 本地广南店        （ENABLED，名称完全一致）
--   219 广南店暂停营业 ← 本地广南店暂停营业（DISABLED，名称完全一致）
--   350 镇康店  ← 本地镇康南伞店    （ENABLED 试营业新档；306 老档已 DISABLED，用户确认跟启用状态）
-- 注：新增门店（内蒙店 id=331）见 insert-new-stores-from-xinfo.sql，需一并执行
-- 生成日期：2026-08-24
-- ============================================================

-- 1) 执行前备份（本次 5 家原值，如失败可回滚）
CREATE TABLE IF NOT EXISTS store_inventory.store_info_backup_20260824_dupfix AS
SELECT id, store_id, store_name, store_code, chat_id
FROM store_inventory.store_info
WHERE del_flag = 0
  AND store_id IN ('cmpaocufu00j63pmbaoig7krk',   -- 华宁店
                   'cmr8kz5hv06rx3pq3kgw7ebx6',   -- 玉溪华宁盘溪镇店
                   'cmpaocuy400ob3pmb6mzv6ppu',   -- 广南店
                   'cmrj0npl707i23pq3pnpuvc0c',   -- 广南店暂停营业
                   'cmp3hkhxa01yt3ps1buu08pso');  -- 镇康南伞店

-- 2) 预检：5 家当前值
SELECT store_id, store_name, store_code AS old_store_code, chat_id
FROM store_inventory.store_info
WHERE del_flag = 0
  AND store_id IN ('cmpaocufu00j63pmbaoig7krk',
                   'cmr8kz5hv06rx3pq3kgw7ebx6',
                   'cmpaocuy400ob3pmb6mzv6ppu',
                   'cmrj0npl707i23pq3pnpuvc0c',
                   'cmp3hkhxa01yt3ps1buu08pso')
ORDER BY store_name;

-- 3) 更新 store_code（均按 store_id 精确定位，可重复执行）
UPDATE store_inventory.store_info SET store_code = '249' WHERE del_flag = 0 AND store_id = 'cmpaocufu00j63pmbaoig7krk'; -- 华宁店
UPDATE store_inventory.store_info SET store_code = '360' WHERE del_flag = 0 AND store_id = 'cmr8kz5hv06rx3pq3kgw7ebx6'; -- 玉溪华宁盘溪镇店
UPDATE store_inventory.store_info SET store_code = '296' WHERE del_flag = 0 AND store_id = 'cmpaocuy400ob3pmb6mzv6ppu'; -- 广南店
UPDATE store_inventory.store_info SET store_code = '219' WHERE del_flag = 0 AND store_id = 'cmrj0npl707i23pq3pnpuvc0c'; -- 广南店暂停营业
UPDATE store_inventory.store_info SET store_code = '350' WHERE del_flag = 0 AND store_id = 'cmp3hkhxa01yt3ps1buu08pso'; -- 镇康南伞店

-- 4) 回填 chat_id（盘溪/广南暂停原本无群，按 xinfo 归属补上）
UPDATE store_inventory.store_info
SET chat_id = 'oc_3320a33c19b8da869cc840962187a032'  -- xinfo 360 盘溪店独立群
WHERE del_flag = 0 AND store_id = 'cmr8kz5hv06rx3pq3kgw7ebx6';

UPDATE store_inventory.store_info
SET chat_id = 'oc_3af29202a5f0f4b3ab71e7597ba75d24'  -- xinfo 219 广南店暂停营业（与 296 共享群，xinfo 侧亦如此）
WHERE del_flag = 0 AND store_id = 'cmrj0npl707i23pq3pnpuvc0c';

-- 5) 校验：5 家更新后值（store_code 应为 249/360/296/219/350）
SELECT store_id, store_name, store_code, chat_id
FROM store_inventory.store_info
WHERE del_flag = 0
  AND store_id IN ('cmpaocufu00j63pmbaoig7krk',
                   'cmr8kz5hv06rx3pq3kgw7ebx6',
                   'cmpaocuy400ob3pmb6mzv6ppu',
                   'cmrj0npl707i23pq3pnpuvc0c',
                   'cmp3hkhxa01yt3ps1buu08pso')
ORDER BY store_name;
