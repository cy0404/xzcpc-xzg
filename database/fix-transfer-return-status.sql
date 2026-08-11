-- ============================================================
-- 修复历史调货单：所有物料已全部归还但状态仍为 completed 的改为 returned
-- 问题：之前 getReturnedQty 只统计 goods 类型，还钱记录没被计入
-- ============================================================

UPDATE transfer_order o
SET o.status = 'returned', o.completed_at = NOW()
WHERE o.status = 'completed'
  AND NOT EXISTS (
    -- 存在未完全归还的物料
    SELECT 1 FROM transfer_order_item i
    WHERE i.transfer_id = o.id
      AND i.del_flag = 0
      AND i.transfer_qty > (
        SELECT COALESCE(SUM(r.return_qty), 0)
        FROM transfer_return_record r
        WHERE r.item_id = i.id
      )
  )
  AND EXISTS (
    -- 确保至少有一条归还记录（避免把没还过的也改了）
    SELECT 1 FROM transfer_return_record r
    WHERE r.transfer_id = o.id
  );

SELECT CONCAT('已将 ', ROW_COUNT(), ' 条调货单状态改为 returned') AS result;
