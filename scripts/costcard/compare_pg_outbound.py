# -*- coding: utf-8 -*-
"""
理论消耗 vs 企迈门店领料（PG dwd.store_item_sales）——带单位换算的对账
====================================================================
理论消耗：store_material_consume_daily.base_qty（销售 × 成本卡，半成品按开关爆炸）
门店领料：dwd.store_item_sales（件/包/瓶/kg…）按 material_conversion_rule 折到物料基础单位

用法：python compare_pg_outbound.py 2026-09-01 2026-09-08
输出：output/compare_pg_<from>_<to>.csv + 控制台（按理论消耗降序，标出比值异常）
说明：门店领料含损耗/报废/加量，理论值通常**低于**领料；理论显著高于领料(>2x)
      或显著偏低(<0.3x)都提示配方/单位/映射需要核对。
"""
import sys
import io
import os
import csv
from collections import defaultdict, deque

import pymysql
import psycopg2

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

from _conn import MY, PG   # 连接参数见 _conn.py（含密码，不入库）
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "output")

# 兜底质量单位（conversion_rule 未覆盖时）
BASE_G = {"g": 1.0, "kg": 1000.0, "ml": 1.0, "L": 1000.0, "l": 1000.0,
          "斤": 500.0, "两": 50.0, "mg": 0.001}


def build_converters(cur):
    """material_id → (unit → base_unit 系数)，依据 material_conversion_rule 建图后 BFS 到基础单位"""
    cur.execute("""SELECT r.rule_id, r.material_id, r.base_unit FROM material_inventory_rule r
                   WHERE r.del_flag = 0 AND r.base_unit IS NOT NULL""")
    rule_mat, base_unit = {}, {}
    for rule_id, mid, bu in cur.fetchall():
        rule_mat[rule_id] = mid
        base_unit.setdefault(mid, bu)
    edges = defaultdict(list)
    cur.execute("""SELECT rule_id, from_unit, from_quantity, to_unit, to_quantity
                   FROM material_conversion_rule WHERE del_flag = 0""")
    for rule_id, fu, fq, tu, tq in cur.fetchall():
        mid = rule_mat.get(rule_id)
        if not mid or not fq or not tq:
            continue
        # from_unit * (to_qty/from_qty) = to_unit
        edges[mid].append((fu, tu, float(tq) / float(fq)))
        edges[mid].append((tu, fu, float(fq) / float(tq)))
    # BFS：unit → base_unit 系数
    conv = {}
    for mid, bu in base_unit.items():
        # 先看基础单位自身是否质量单位
        best = {bu: 1.0}
        if bu in BASE_G:
            factor_to_g = BASE_G[bu]
            # 若基础单位是 g/ml，其它质量单位可直接折
            for u, f in BASE_G.items():
                best.setdefault(u, f / factor_to_g)
        # best[u] = 1 个 u 等于多少基础单位；边 (a→b, k) 表示 1 a = k b ⇒ F[a] = k·F[b] ⇒ F[b] = F[a]/k
        q = deque([(bu, 1.0)])
        while q:
            u, f = q.popleft()
            for fu, tu, ff in edges.get(mid, []):
                if fu != u or tu in best or not ff:
                    continue
                best[tu] = f / ff
                q.append((tu, f / ff))
        conv[mid] = best
    return conv, base_unit


def main():
    if len(sys.argv) < 3:
        print("用法: python compare_pg_outbound.py <from yyyy-MM-dd> <to yyyy-MM-dd>")
        return
    d_from, d_to = sys.argv[1], sys.argv[2]
    if not os.path.isdir(OUT):
        os.makedirs(OUT)

    my = pymysql.connect(**MY)
    cur = my.cursor()
    conv, base_unit = build_converters(cur)

    cur.execute("""
        SELECT c.material_id, MAX(c.material_name), MAX(m.qm_code),
               ROUND(SUM(c.base_qty),2)
        FROM store_material_consume_daily c
        LEFT JOIN material m ON m.material_id = c.material_id
        WHERE c.stat_date >= %s AND c.stat_date < %s
        GROUP BY c.material_id ORDER BY 4 DESC""", (d_from, d_to))
    theory = {}
    for mid, name, qm, qty in cur.fetchall():
        theory[mid] = dict(name=name, qm=qm, qty=float(qty or 0),
                           base=base_unit.get(mid))
    cur.execute("""SELECT store_id, cangkuid FROM store_info
                   WHERE del_flag = 0 AND cangkuid IS NOT NULL AND cangkuid <> ''""")
    wh = [r[1] for r in cur.fetchall()]
    my.close()

    qm_to_mid = {v["qm"]: k for k, v in theory.items() if v["qm"]}
    pg = psycopg2.connect(**PG)
    pc = pg.cursor()
    in_wh = ",".join("'%s'" % w.replace("'", "''") for w in wh)
    in_it = ",".join("'%s'" % c.replace("'", "''") for c in qm_to_mid)
    pc.execute("""
        SELECT item_code, unit, COALESCE(SUM(COALESCE(sales_quantity,0) - COALESCE(return_quantity,0)),0)
        FROM dwd.store_item_sales
        WHERE warehouse_code IN (%s) AND stat_date >= %%s::date AND stat_date < %%s::date
          AND item_code IN (%s)
        GROUP BY item_code, unit""" % (in_wh, in_it), (d_from, d_to))
    req = defaultdict(float)
    unconv = []
    for code, unit, qty in pc.fetchall():
        mid = qm_to_mid.get(code)
        if not mid:
            continue
        f = conv.get(mid, {}).get(unit)
        if f is None:
            unconv.append((theory[mid]["name"], unit, float(qty)))
            continue
        req[mid] += float(qty) * f
    pg.close()

    rows = []
    for mid, t in theory.items():
        p = req.get(mid)
        rows.append([t["name"] or mid, mid, t["base"], t["qty"], round(p, 2) if p else None,
                     round(t["qty"] / p, 2) if p else None])
    rows.sort(key=lambda r: -r[3])
    f = os.path.join(OUT, "compare_pg_%s_%s.csv" % (d_from.replace("-", ""), d_to.replace("-", "")))
    with io.open(f, "w", encoding="utf-8-sig", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["物料", "物料ID", "基础单位", "理论消耗", "门店领料(折基础单位)", "理论/领料"])
        w.writerows(rows)
    have = [r for r in rows if r[4]]
    print("窗口 %s ~ %s：物料 %d 个，其中 PG 有领料 %d 个" % (d_from, d_to, len(rows), len(have)))
    if unconv:
        print("换算缺失（未计入）:", ["%s(%s×%s)" % u for u in unconv[:6]])
    print("\n%-22s %-6s %14s %14s %8s" % ("物料", "单位", "理论消耗", "门店领料", "理论/领料"))
    print("-" * 72)
    for r in have[:30]:
        flag = ""
        if r[5] and r[5] > 2:
            flag = "  ← 理论偏高"
        elif r[5] and r[5] < 0.3:
            flag = "  ← 理论偏低"
        print("%-22s %-6s %14.1f %14.1f %8s%s" % (r[0][:20], r[2] or "", r[3], r[4], r[5], flag))
    print("-" * 72)
    print("写入", os.path.basename(f))


if __name__ == "__main__":
    main()
