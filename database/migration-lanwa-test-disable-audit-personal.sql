-- ============================================================
-- 【本地测试临时用】停用测试库 avocado_audit（"其他厂家"牛油果泥审核）个人收件人
-- 执行库：store_inventory_test（测试库，勿在生产执行）
-- 目的：本地触发 trigger-avocado 测试蓝蛙群发卡时，
--       hass 等"其他厂家"的卡片 E 不会误发个人（id=15 停用后该部分跳过）
-- 测试完恢复：UPDATE loss_notify_card_config SET status=1 WHERE id=15;
-- ============================================================

UPDATE loss_notify_card_config SET status=0 WHERE id=15;

-- 校验（应看到 id=15 status=0，id=21 蓝蛙群 status=1）：
-- SELECT id, card_type, feishu_user_id, remark, status
-- FROM loss_notify_card_config WHERE id IN (15, 21);
