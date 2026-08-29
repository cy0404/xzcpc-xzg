-- ============================================================
-- 把生产库 report_visibility_config 的数据同步到测试库
-- 两库在同一台 MySQL：store_inventory（生产）→ store_inventory_test（测试）
-- 用法：在数据库客户端（Navicat 等）中执行本脚本，账号需有跨库权限
-- ============================================================

-- 1. 确保测试库存在这张表（不存在才创建，已存在跳过）
CREATE TABLE IF NOT EXISTS store_inventory_test.report_visibility_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id    VARCHAR(50) NOT NULL COMMENT '门店ID（store_info.store_id）',
    stat_month  VARCHAR(7)  NOT NULL COMMENT '月份 yyyy-MM',
    created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_store_month (store_id, stat_month)
) COMMENT '财报隐藏配置（有记录=隐藏）';

-- 2. 把生产库数据复制到测试库
--    重复的 (store_id, stat_month) 会被 UNIQUE KEY 自动跳过，本脚本可重复执行
INSERT IGNORE INTO store_inventory_test.report_visibility_config (store_id, stat_month, created_at)
SELECT store_id, stat_month, created_at
FROM store_inventory.report_visibility_config;

-- 3. 验证：两边数量（应相等，除非测试库原本有多余行）
SELECT 'prod' AS env, COUNT(*) AS cnt FROM store_inventory.report_visibility_config
UNION ALL
SELECT 'test', COUNT(*) FROM store_inventory_test.report_visibility_config;

-- 4. 查看测试库里的明细
SELECT store_id, stat_month, created_at
FROM store_inventory_test.report_visibility_config
ORDER BY stat_month DESC, store_id;

-- ============================================================
-- 【可选】如果想让测试库与生产库完全一致（清掉测试库多余的行），
-- 把第 2 步的 INSERT IGNORE 换成下面两行：
--
-- DELETE FROM store_inventory_test.report_visibility_config;
-- INSERT INTO store_inventory_test.report_visibility_config (store_id, stat_month, created_at)
-- SELECT store_id, stat_month, created_at FROM store_inventory.report_visibility_config;
-- ============================================================
