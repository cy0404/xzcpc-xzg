import pymysql

conn = pymysql.connect(host='162.14.122.80', user='store_inventory',
                       password='Xzcpc@2026', database='store_inventory_test',
                       connect_timeout=5)
cur = conn.cursor()

print("=== 1. 填充统计 ===")
cur.execute("""
    SELECT COUNT(*) total,
           SUM(order_unit  IS NOT NULL AND order_unit  <> '') has_order_unit,
           SUM(order_price IS NOT NULL) has_order_price,
           SUM(stock_unit  IS NOT NULL AND stock_unit  <> '') has_stock_unit,
           SUM(purchase_price IS NOT NULL) has_purchase_price
    FROM material_inventory_rule WHERE del_flag = 0
""")
print(cur.fetchone())

print("\n=== 2. 抽查 5 条（order_unit/order_price/stock_unit 都有值）===")
cur.execute("""
    SELECT m.material_name, m.qm_code, r.order_unit, r.order_price, r.stock_unit, r.base_unit
    FROM material_inventory_rule r JOIN material m ON m.material_id = r.material_id
    WHERE r.del_flag = 0 AND m.del_flag = 0
      AND r.order_unit IS NOT NULL AND r.stock_unit IS NOT NULL
    LIMIT 5
""")
for r in cur.fetchall():
    print(r)

print("\n=== 3. order_unit 为空的行数（半成品等）===")
cur.execute("""
    SELECT COUNT(*) FROM material_inventory_rule r
    JOIN material m ON m.material_id = r.material_id
    WHERE r.del_flag = 0 AND m.del_flag = 0 AND (r.order_unit IS NULL OR r.order_unit = '')
""")
print(cur.fetchone())

print("\n=== 4. 订单单位与库存单位相同的占比 ===")
cur.execute("""
    SELECT SUM(order_unit = stock_unit) same_cnt, COUNT(*) total_cnt
    FROM material_inventory_rule WHERE del_flag = 0 AND order_unit IS NOT NULL AND stock_unit IS NOT NULL
""")
print(cur.fetchone())

print("\n=== 5. 半成品规则抽查（order_unit 应保持空/人工值，stock_unit 不被覆盖）===")
cur.execute("""
    SELECT m.material_name, m.qm_code, r.order_unit, r.order_price, r.stock_unit
    FROM material_inventory_rule r JOIN material m ON m.material_id = r.material_id
    WHERE r.del_flag = 0 AND m.del_flag = 0
      AND m.qm_code LIKE 'BZ%'
    LIMIT 5
""")
rows = cur.fetchall()
if rows:
    for r in rows:
        print(r)
else:
    print("(测试库无 BZ 前缀半成品，跳过)")

conn.close()
print("\nDONE")
