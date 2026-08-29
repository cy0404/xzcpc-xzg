-- ============================================================
-- 企迈控制台登录 Cookie 配置（qmai_console_cookie）
--
-- 用途：控制台 API 登录态（qm_seller_token）过期后，直接改库值即可，
--       无需重启服务（QmaiConsoleClient 带 60 秒缓存，最多 1 分钟生效）。
-- 回退：sys_config 中无此 key 或值为空时，回退使用环境变量 QMAI_CONSOLE_COOKIE。
--
-- 注意：测试库(store_inventory_test)和生产库(store_inventory)都要执行。
-- ============================================================

INSERT INTO sys_config (config_key, config_value, description)
SELECT 'qmai_console_cookie', '',
       '企迈控制台登录Cookie(qm_seller_token值)，过期后改此值即可，空值回退环境变量'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'qmai_console_cookie');

-- 更新 Cookie 示例（浏览器登录 inapi.qmai.cn 后从 Cookie 中复制 qm_seller_token 的值）：
-- UPDATE sys_config SET config_value = '你的新qm_seller_token值' WHERE config_key = 'qmai_console_cookie';
