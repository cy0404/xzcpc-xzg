-- ============================================================
-- 7 张历史换算错误出库单 → 标记 failed（H5 已补发 tab 显示红行 + 重新补发按钮）
-- 生成时间：2026-08-29
-- 前置：先重启 4026 服务（让 convertQty 修复 + 换算失败标记生效），再执行本脚本
-- 执行后：在 H5 已补发 tab 点「重新补发」，会用正确数量重新建企迈出库单
-- ============================================================

UPDATE outbound_order o
JOIN loss_report r ON r.outbound_order_id = o.id
SET o.status = 'failed',
    o.error = '历史换算错误单：报损数量按库存单位错误建单（倍数错），已标记失败，请在已补发里点重新补发'
WHERE r.id IN (9704, 11446, 14981, 15003, 15144, 16496, 16624, 18186)
  AND o.status = 'success';

-- 验证（应输出 7 行 status=failed，对应 7 张错误单）
-- SELECT o.external_no, o.outbound_no, o.status, o.error, GROUP_CONCAT(r.id) AS loss_ids
-- FROM outbound_order o
-- JOIN loss_report r ON r.outbound_order_id = o.id
-- WHERE r.id IN (9704, 11446, 14981, 15003, 15144, 16496, 16624, 18186)
-- GROUP BY o.id;
