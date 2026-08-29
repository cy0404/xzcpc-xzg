# -*- coding: utf-8 -*-
"""查询 7 张历史错误出库单对应 loss_report + outbound_order 明细（只读）"""
import pymysql

conn = pymysql.connect(host="162.14.122.80", port=3306, user="store_inventory",
                       password="Xzcpc@2026", database="store_inventory", charset="utf8mb4")
cur = conn.cursor()

# 7 条错误单：牛油果泥 4 条 + 珍珠粉圆/厚椰乳/普洱茶 各1条，全部关联了 outbound_order
cur.execute("""
SELECT r.id, r.store_name, m.material_name, r.input_qty, r.input_unit,
       ir.stock_unit, ir.purchase_unit, ir.purchase_price,
       o.external_no, o.outbound_no, o.warehouse_no, o.status, o.error
FROM loss_report r
LEFT JOIN material m ON r.material_id = m.material_id
LEFT JOIN material_inventory_rule ir ON ir.material_id = r.material_id AND ir.del_flag = 0
LEFT JOIN outbound_order o ON r.outbound_order_id = o.id
WHERE r.outbound_order_id IS NOT NULL
  AND (m.material_name LIKE '%牛油果泥%' OR m.material_name LIKE '%珍珠粉圆%'
       OR m.material_name LIKE '%厚椰乳%' OR m.material_name LIKE '%普洱茶%')
ORDER BY r.id
""")
rows = cur.fetchall()
print("命中条数:", len(rows))
for r in rows:
    print("id=%s | %s | %s | input=%s%s | stock=%s purchase=%s price=%s | ex=%s | outbound_no=%s | wh=%s | status=%s | %s" % r)
conn.close()
