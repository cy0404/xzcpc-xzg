-- ============================================================
-- 每周三 / 每月1号 到货报损明细汇总卡片 收件人配置（周/月共用一行）
-- 执行方式：手动执行（生产库 store_inventory）
-- 说明：feishu_user_id 可配个人 open_id，逗号分隔多人；配成 chat_ 开头即发群
--       （sendToCardTargets 自动分流），上线后直接 UPDATE 该行即可，无需改代码。
-- 生产收件人 = 运营本人；测试期可临时 UPDATE 为 chat_oc_303777719e27f45606b42c41fd5c7e58（测试群）
-- ============================================================

INSERT INTO loss_notify_card_config (category, card_type, feishu_user_id, remark, status)
SELECT '其他类', 'period_loss_summary', 'ou_34c667aef057096ef1a8f3ff6d39ff76',
       '每周三/每月1号 到货报损明细汇总（不含蔬菜水果类）', 1
WHERE NOT EXISTS (
  SELECT 1 FROM loss_notify_card_config
  WHERE category='其他类' AND card_type='period_loss_summary'
);

-- 校验：
-- SELECT category, card_type, feishu_user_id, remark, status
-- FROM loss_notify_card_config WHERE card_type='period_loss_summary';
