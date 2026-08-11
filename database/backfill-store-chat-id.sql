-- ============================================================
-- 回填 store_info.chat_id
-- 数据源：task_platform.store_chat_mapping（按 store_name 匹配）
-- 目标：  store_inventory.store_info.chat_id
-- 说明：两库需在同一 MySQL 实例；执行账号需对两个库都有权限
-- 假设：store_chat_mapping 含列 store_name、chat_id（列名不同请替换）
-- ============================================================

-- ⚠️ 门店名两边不一致（例：映射表"昆明西山南亚风情店" vs store_info"象子茶铺茶南亚风情店"）
--    → 精确 JOIN 匹配不上，先用下面 A) 两条查询列出全部门店名对照，
--      把结果贴回，据此生成精确 CASE WHEN 回填（见文件末尾模板）。

-- A-1. store_info 全部门店名
SELECT store_id, store_name
FROM store_inventory.store_info
WHERE del_flag = 0
ORDER BY store_name;

-- A-2. 映射表全部门店名 + chat_id（先确认列名，不是 chat_id 就替换）
SELECT store_name, chat_id
FROM task_platform.store_chat_mapping
ORDER BY store_name;

-- ============================================================
-- 【方案一】名字若能对上（或你已改成一致）——按 store_name 精确回填
-- ============================================================

-- 0. 先确认映射表结构，核对 chat_id 的真实列名
--    （若列名不是 chat_id，请把下方所有 scm.chat_id 改成实际列名）
SHOW COLUMNS FROM task_platform.store_chat_mapping;

-- 1. 预检：将要写入的匹配结果（执行 UPDATE 前 dry-run 看一眼）
SELECT si.store_id, si.store_name AS inv_store_name,
       scm.store_name AS map_store_name, scm.chat_id
FROM store_inventory.store_info si
JOIN task_platform.store_chat_mapping scm
     ON si.store_name = scm.store_name
WHERE si.del_flag = 0
ORDER BY si.store_name;

-- 1b. 预检：store_name 对不上、无法回填的门店（需人工核对/改名）
SELECT si.store_id, si.store_name
FROM store_inventory.store_info si
LEFT JOIN task_platform.store_chat_mapping scm
     ON si.store_name = scm.store_name
WHERE si.del_flag = 0 AND scm.store_name IS NULL
ORDER BY si.store_name;

-- 1c. 预检：映射表内 store_name 是否有重复（重复会导致回填取值不确定）
SELECT store_name, COUNT(*) AS cnt
FROM task_platform.store_chat_mapping
GROUP BY store_name HAVING COUNT(*) > 1;

-- 2. 回填（确认上面预检无误后再执行）
--    仅覆盖能匹配到的门店；未匹配的保持原值
UPDATE store_inventory.store_info si
JOIN task_platform.store_chat_mapping scm
     ON si.store_name = scm.store_name
SET si.chat_id = scm.chat_id
WHERE si.del_flag = 0;

-- 3. 校验：查看回填结果（chat_id 为空的排在前面便于检查遗漏）
SELECT store_id, store_name, chat_id
FROM store_inventory.store_info
WHERE del_flag = 0
ORDER BY (chat_id IS NULL OR chat_id = '') DESC, store_name;

-- ============================================================
-- 【方案二】名字对不上——精确 CASE WHEN 回填（推荐，最可靠）
-- 把 A-1 / A-2 两条查询结果贴给我，我按 store_id → chat_id 生成下面这段。
-- 也可自己按下表逐行填（左边=store_info.store_id，右边=映射表 chat_id）：
-- ============================================================

-- UPDATE store_inventory.store_info
-- SET chat_id = CASE store_id
--     WHEN '门店ID_1' THEN 'chatid_1'   -- 象子茶铺茶南亚风情店  ← 昆明西山南亚风情店
--     WHEN '门店ID_2' THEN 'chatid_2'   -- ...
--     ELSE chat_id
-- END
-- WHERE del_flag = 0
--   AND store_id IN ('门店ID_1','门店ID_2' /* ,... */);

-- （可选）方案二·模糊预览：去掉 store_info 的品牌前缀后按位置后缀匹配映射表
-- 仅供参考，务必人工核对结果再决定是否采用；品牌前缀按实际替换
-- SELECT si.store_id, si.store_name, scm.store_name AS map_name, scm.chat_id
-- FROM store_inventory.store_info si
-- JOIN task_platform.store_chat_mapping scm
--   ON scm.store_name LIKE CONCAT('%', REPLACE(si.store_name, '象子茶铺茶', ''))
-- WHERE si.del_flag = 0;

