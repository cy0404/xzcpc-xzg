# -*- coding: utf-8 -*-
"""按 material_conversion_rule 换算链计算 8 条记录的正确出库数量（与 ConversionFactorUtil 双向 BFS 同逻辑）"""
import pymysql
from collections import deque

conn = pymysql.connect(host="162.14.122.80", port=3306, user="store_inventory",
                       password="Xzcpc@2026", database="store_inventory", charset="utf8mb4")
cur = conn.cursor()

cur.execute("""
SELECT r.id, m.material_name, r.input_qty, r.input_unit, ir.stock_unit, ir.purchase_unit
FROM loss_report r
LEFT JOIN material m ON r.material_id = m.material_id
LEFT JOIN material_inventory_rule ir ON ir.material_id = r.material_id AND ir.del_flag = 0
WHERE r.id IN (9704, 11446, 14981, 15003, 15144, 15575, 16496, 16624, 18186)
""")
rows = cur.fetchall()

def load_rules(material_id):
    cur.execute("""
        SELECT c.conversion_type, c.from_quantity, c.from_unit, c.to_quantity, c.to_unit
        FROM material_conversion_rule c
        JOIN material_inventory_rule ir ON ir.rule_id = c.rule_id
        WHERE ir.material_id = %s AND ir.del_flag = 0 AND c.del_flag = 0
        ORDER BY c.sort_no
    """, (material_id,))
    return cur.fetchall()

def factor(from_unit, to_unit, rules):
    """双向 BFS：1 from_unit = ? to_unit"""
    if from_unit == to_unit:
        return 1.0
    adj = {}
    for ct, fq, fu, tq, tu in rules:
        if ct != 'unit':
            continue
        if fu and tu and fq and tq:
            adj.setdefault(fu, []).append((tu, float(tq) / float(fq)))
            adj.setdefault(tu, []).append((fu, float(fq) / float(tq)))
    q = deque([(from_unit, 1.0)])
    seen = {from_unit}
    while q:
        u, f = q.popleft()
        for v, ratio in adj.get(u, []):
            nf = f * ratio
            if v == to_unit:
                return nf
            if v not in seen:
                seen.add(v)
                q.append((v, nf))
    return None

cur.execute("SELECT r.id, r.material_id FROM loss_report r WHERE r.id IN (9704, 11446, 14981, 15003, 15144, 15575, 16496, 16624, 18186)")
mid_map = dict(cur.fetchall())

for rid, name, qty, iunit, sunit, punit in rows:
    rules = load_rules(mid_map[rid])
    target = sunit or punit
    f = factor(iunit, target, rules)
    correct = float(qty) * f if f else None
    rules_txt = "; ".join("%s %s→%s %s" % (r[1], r[2], r[4], r[3]) for r in rules)
    print("id=%s | %s | 报损 %s%s | %s→%s | factor=%s | 正确出库=%s | 规则: %s"
          % (rid, name, qty, iunit, iunit, target, f, correct, rules_txt))
conn.close()
