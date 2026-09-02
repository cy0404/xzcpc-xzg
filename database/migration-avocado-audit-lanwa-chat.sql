-- ============================================================
-- 蓝蛙牛油果泥审核卡片 → 发蓝蛙外部群
-- 执行方式：手动执行（生产库 store_inventory）
-- 说明：loss_notify_card_config 的 feishu_user_id 以 chat_ 开头即视为群 chat_id，
--       系统将把该卡片发到群里（配置驱动，无需改代码）。
--       此配置只影响"蓝蛙厂家"的牛油果泥审核（卡片 E 蓝蛙部分 + 牛油果泥加急蓝蛙单）；
--       hass 等其余厂家仍发原 avocado_audit 收件人（个人）。
-- ============================================================

INSERT INTO loss_notify_card_config (category, card_type, feishu_user_id, remark, status)
SELECT '其他类', 'avocado_audit_lanwa', 'chat_oc_229997d31fbbe40b675c30568ee15e3a', '蓝蛙牛油果泥审核卡片 → 蓝蛙外部群', 1
WHERE NOT EXISTS (
  SELECT 1 FROM loss_notify_card_config
  WHERE category='其他类' AND card_type='avocado_audit_lanwa'
);

-- 校验：
-- SELECT category, card_type, feishu_user_id, remark, status
-- FROM loss_notify_card_config WHERE card_type IN ('avocado_audit', 'avocado_audit_lanwa');
