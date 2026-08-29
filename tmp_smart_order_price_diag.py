# -*- coding: utf-8 -*-
"""只读诊断：测试库种子订货单的单价/单位/换算规则现状
看 stock_unit 与 base_unit 之间有没有 unit 换算规则、方向如何、单价是否按库存单位换算
"""
import pymysql

DB = dict(host="162.14.122.80", port=3306, user="store_inventory",
          password="Xzcpc@2026", database="store_inventory_test")

conn = pymysql.connect(**DB, charset="utf8mb4")
cur = conn.cursor(pymysql.cursors.DictCursor)

cur.execute("""
SELECT i.id, i.material_name, i.stock_unit, i.base_unit, i.unit_price, i.suggest_qty, i.qm_code
  FROM smart_order_item i
  JOIN smart_order o ON o.id = i.order_id
 WHERE o.store_id = (SELECT store_id FROM store_info
                      WHERE store_name LIKE '%%测试门店%%' AND del_flag = 0 ORDER BY id LIMIT 1)
   AND i.del_flag = 0
 ORDER BY i.id
""")
items = cur.fetchall()
print("===== 明细（库存单位/基础单位/单价）=====")
for it in items:
    print(f"id={it['id']} {it['material_name']} | stock={it['stock_unit']} base={it['base_unit']} "
          f"price={it['unit_price']} qty={it['suggest_qty']} qm={it['qm_code']}")

if not items:
    print("（无明细行 —— 种子可能还没跑或已被清理）")
    conn.close()
    raise SystemExit

# 这些物料对应的盘点规则 + 换算规则
mat_names = [it["material_name"] for it in items]
fmt = ",".join(["%s"] * len(mat_names))
cur.execute(f"""
SELECT m.material_name, r.rule_id, r.base_unit, r.stock_unit, r.unit_price,
       cr.id AS cr_id, cr.conversion_type, cr.from_quantity, cr.from_unit, cr.to_quantity, cr.to_unit
  FROM material m
  LEFT JOIN material_inventory_rule r ON r.material_id = m.material_id
  LEFT JOIN material_conversion_rule cr ON cr.rule_id = r.rule_id AND cr.del_flag = 0
 WHERE m.material_name IN ({fmt})
 ORDER BY m.material_name, cr.id
""", mat_names)
print("\n===== 规则/换算链（换算方向与类型）=====")
for row in cur.fetchall():
    print(f"{row['material_name']} | rule_id={row['rule_id']} base={row['base_unit']} stock={row['stock_unit']} "
          f"rule_price={row['unit_price']} | cr#{row['cr_id']} {row['conversion_type']}: "
          f"{row['from_quantity']}{row['from_unit']} = {row['to_quantity']}{row['to_unit']}")

conn.close()
