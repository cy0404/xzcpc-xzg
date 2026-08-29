# -*- coding: utf-8 -*-
"""8/7 周盘理论剩余计算 — 库存紧张场景（理论剩余×30%，模拟临期缺货）
口径与 SmartOrderServiceImpl 完全一致，仅把库存缩到 30% 用于验证 suggest>0 出单链路"""
import pymysql, psycopg2
from collections import defaultdict, deque

MYSQL = dict(host="162.14.122.80", port=3306, user="store_inventory", password="Xzcpc@2026",
             database="store_inventory_test", charset="utf8mb4", autocommit=True)
PG = dict(host="119.45.162.160", port=5433, dbname="tea_chain", user="postgres", password="xzcp123456")

START, END = "2026-08-01", "2026-08-07"  # 前闭后开 6 天（与 backtest daysBetween 对齐）
TIGHT_RATIO = 0.3  # 库存紧张系数：理论剩余 × 0.3

conn = pymysql.connect(**MYSQL)
cur = conn.cursor(pymysql.cursors.DictCursor)

# 1. 门店
cur.execute("SELECT id, store_id, store_name, cangkuid FROM store_info WHERE del_flag=0 AND cangkuid='MDCK000082'")
stores = cur.fetchall()
store = stores[0]
STORE_ID = store["store_id"]

# 2. 594 盘点汇总（7/31 期末库存）
cur.execute("SELECT material_id, material_name, total_qty FROM task_material_summary WHERE task_id=594")
summaries = {r["material_id"]: r for r in cur.fetchall()}
print("594 summary count:", len(summaries))

# 3. 物料主数据（qm_code, category）+ 盘点规则（base_unit）+ 换算链
mids = list(summaries.keys())
in_q = ",".join(["%s"] * len(mids))
cur.execute(f"SELECT material_id, material_name, qm_code, category FROM material WHERE material_id IN ({in_q})", mids)
materials = {r["material_id"]: r for r in cur.fetchall()}
cur.execute(f"SELECT material_id, rule_id, base_unit FROM material_inventory_rule WHERE del_flag=0 AND material_id IN ({in_q})", mids)
rules = {r["material_id"]: r for r in cur.fetchall()}
rule_ids = list({r["rule_id"] for r in rules.values() if r["rule_id"]})
conv = defaultdict(list)
if rule_ids:
    rq = ",".join(["%s"] * len(rule_ids))
    cur.execute(f"SELECT rule_id, conversion_type, from_quantity, from_unit, to_quantity, to_unit FROM material_conversion_rule WHERE del_flag=0 AND rule_id IN ({rq})", rule_ids)
    for r in cur.fetchall():
        conv[r["rule_id"]].append(r)

# 4. PG 销量 + 到货（qm_code 维度）
pg = psycopg2.connect(**PG)
pcur = pg.cursor()
qm_codes = [m["qm_code"] for m in materials.values() if m["qm_code"]]
qin = ",".join(["%s"] * len(qm_codes))
pcur.execute(
    "SELECT item_code, COALESCE(SUM(sales_quantity - COALESCE(return_quantity,0)),0) AS qty, "
    "COUNT(DISTINCT stat_date) AS days, COALESCE(unit, MAX(unit) OVER (PARTITION BY item_code)) AS unit "
    "FROM dwd.store_item_sales WHERE warehouse_code=%s AND stat_date>=%s::date AND stat_date<%s::date "
    f"AND item_code IN ({qin}) GROUP BY item_code, unit",
    ["MDCK000082", START, END, *qm_codes])
pg_sales = defaultdict(lambda: {"qty": 0.0, "days": 0})
for r in pcur.fetchall():
    k = r[0]
    pg_sales[k]["qty"] += float(r[1] or 0)
    pg_sales[k]["days"] += int(r[2] or 0)
    pg_sales[k]["unit"] = r[3]
# 入库 = PG purchase_order（order_time 在窗口、已送达的 shipping_quantity）
pcur.execute(
    "SELECT item_code, COALESCE(SUM(shipping_quantity),0) AS qty, "
    "COALESCE(unit, MAX(unit) OVER (PARTITION BY item_code)) AS unit "
    "FROM dwd.purchase_order WHERE warehouse_code=%s AND order_status='已送达' "
    "AND order_time>=%s::date AND order_time<%s::timestamp "
    f"AND item_code IN ({qin}) GROUP BY item_code, unit",
    ["MDCK000082", START, END, *qm_codes])
pg_purchase = defaultdict(lambda: {"qty": 0.0})
for r in pcur.fetchall():
    pg_purchase[r[0]]["qty"] += float(r[1] or 0)
    pg_purchase[r[0]]["unit"] = r[2]
pcur.close(); pg.close()

# 补充：inbound_order 采购类（type=3，直采到货；8/7 08:35 在盘点时刻前计入）
cur.execute("""SELECT it.product_code, it.product_unit, COALESCE(SUM(it.product_num),0) AS qty
               FROM inbound_order i JOIN inbound_order_item it ON it.inbound_order_id=i.id
               WHERE i.warehouse_code='MDCK000082' AND i.inbound_type=3
               AND i.inbound_at>='2026-08-01 00:00:00' AND i.inbound_at<'2026-08-07 12:00:00'
               AND i.del_flag=0 AND it.del_flag=0 GROUP BY it.product_code, it.product_unit""")
for r in cur.fetchall():
    pc = r["product_code"]
    if pc and pc in qm_codes:
        if pc not in pg_purchase:
            pg_purchase[pc] = {"qty": 0.0, "unit": r["product_unit"]}
        pg_purchase[pc]["qty"] += float(r["qty"] or 0)

# 5. MySQL 报损/调货/还货/自购
def q(sql, args=None):
    cur.execute(sql, args or ())
    return cur.fetchall()

loss = defaultdict(float)
for r in q("""SELECT material_id,
              COALESCE(SUM(CASE WHEN orig_qty IS NOT NULL AND orig_qty > 0 THEN input_qty
              WHEN base_qty IS NOT NULL AND base_qty > 0 THEN base_qty ELSE input_qty END),0) AS qty
              FROM loss_report WHERE store_id=%s AND del_flag=0 AND occurred_date>=%s AND occurred_date<%s
              AND ((loss_type='daily' AND status='completed')
                   OR (loss_type='arrival' AND status IN ('registered','confirmed_resend','received','not_received','completed')))
              GROUP BY material_id""", (STORE_ID, START, END)):
    if r["material_id"]: loss[r["material_id"]] += float(r["qty"] or 0)

transfer = defaultdict(float)  # 净调出为正（调出+，调入−）
for r in q("""SELECT oi.material_id, oi.transfer_qty, oi.base_qty, o.from_store_id, o.to_store_id
              FROM transfer_order_item oi JOIN transfer_order o ON oi.transfer_id=o.id
              WHERE (o.from_store_id=%s OR o.to_store_id=%s) AND o.status IN ('completed','returned')
              AND o.received_at>=%s AND o.received_at<%s AND oi.del_flag=0 AND o.del_flag=0""",
           (STORE_ID, STORE_ID, START, END)):
    mid = r["material_id"]
    if not mid: continue
    raw = float(r["transfer_qty"] or 0)
    if raw == 0: continue
    qty = float(r["base_qty"] or 0)
    if qty <= 0: qty = raw
    if r["from_store_id"] == STORE_ID: transfer[mid] += qty   # 调出
    if r["to_store_id"] == STORE_ID: transfer[mid] -= qty     # 调入

ret = defaultdict(float)  # 净还出为正（还出+，收回−）
for r in q("""SELECT oi.material_id, oi.unit, rr.return_qty, o.from_store_id, o.to_store_id
              FROM transfer_return_record rr
              JOIN transfer_order_item oi ON rr.item_id=oi.id AND oi.del_flag=0
              JOIN transfer_order o ON rr.transfer_id=o.id AND o.del_flag=0
              WHERE (o.from_store_id=%s OR o.to_store_id=%s) AND rr.return_type='goods'
              AND rr.created_at>=%s AND rr.created_at<%s""",
           (STORE_ID, STORE_ID, START, END)):
    mid = r["material_id"]
    if not mid: continue
    raw = float(r["return_qty"] or 0)
    if raw == 0: continue
    if r["from_store_id"] == STORE_ID: ret[mid] -= raw  # 收回 → 库存+
    if r["to_store_id"] == STORE_ID: ret[mid] += raw    # 还出 → 库存−

sp = defaultdict(float)
for r in q("""SELECT material_id, unit, COALESCE(SUM(purchase_qty),0) AS qty FROM self_purchase_material
              WHERE store_id=%s AND del_flag=0 AND purchase_date>=%s AND purchase_date<%s GROUP BY material_id, unit""",
           (STORE_ID, START, END)):
    if r["material_id"]: sp[r["material_id"]] += float(r["qty"] or 0)

# 6. 换算 BFS（复刻 ConversionFactorUtil.computeConversionFactor）
def factor(mid, unit):
    if not unit: return 1.0
    rule = rules.get(mid)
    base = (rule or {}).get("base_unit")
    if not base or base == unit: return 1.0
    graph = defaultdict(list)
    for r in conv.get((rule or {}).get("rule_id") or "", []):
        if r["conversion_type"] != "unit": continue
        ft = float(r["to_quantity"]) / float(r["from_quantity"])
        tf = float(r["from_quantity"]) / float(r["to_quantity"])
        graph[r["from_unit"]].append((r["to_unit"], ft))
        graph[r["to_unit"]].append((r["from_unit"], tf))
    if unit not in graph: return 1.0
    dq, seen = deque([(unit, 1.0)]), {unit}
    while dq:
        u, f = dq.popleft()
        if u == base: return f
        for nxt, ratio in graph.get(u, []):
            if nxt not in seen:
                seen.add(nxt); dq.append((nxt, f * ratio))
    return 1.0

def to_base(mid, unit, qty):
    return qty * factor(mid, unit)

# 7. 汇总输出
cur.execute(f"SELECT material_id, spec FROM material WHERE material_id IN ({in_q})", mids)
specs = {r["material_id"]: (r["spec"] or "") for r in cur.fetchall()}
rows = []
for mid, s in summaries.items():
    m = materials.get(mid)
    if not m: continue
    cat = m["category"] or ""
    excluded = "半成品" in cat or "淘汰" in cat
    end_qty = float(s["total_qty"] or 0)
    qm = m["qm_code"] or ""
    ps = pg_sales.get(qm)
    sales = to_base(mid, (ps or {}).get("unit"), (ps or {}).get("qty", 0.0)) if ps else 0.0
    pur = to_base(mid, (pg_purchase.get(qm) or {}).get("unit"), (pg_purchase.get(qm) or {}).get("qty", 0.0)) if pg_purchase.get(qm) else 0.0
    lossv = to_base(mid, None, loss.get(mid, 0.0))
    trv = to_base(mid, None, transfer.get(mid, 0.0))
    rtv = to_base(mid, None, ret.get(mid, 0.0))
    spv = to_base(mid, None, sp.get(mid, 0.0))
    theo = end_qty + pur + spv - sales - lossv - trv - rtv
    tight = theo * TIGHT_RATIO  # 库存紧张场景：真实剩余 × 30%
    negative = tight < 0
    if negative: tight = 0.0
    rows.append((mid, s["material_name"], cat, specs.get(mid, ""), end_qty, pur, sales, lossv, trv, rtv, spv, tight, excluded, negative))

rows.sort(key=lambda x: (x[12], x[0]))
print(f"\n{'material_id':<14}{'名称':<18}{'期末':>9}{'入库':>8}{'销量':>9}{'报损':>8}{'调净':>8}{'还净':>8}{'自购':>8}{'紧张剩余':>10}")
for r in rows:
    flag = "  [半成品/淘汰]" if r[12] else ("  [负→0]" if r[13] else "")
    print(f"{r[0]:<14}{r[1]:<18}{r[4]:>9.2f}{r[5]:>8.2f}{r[6]:>9.2f}{r[7]:>8.2f}{r[8]:>8.2f}{r[9]:>8.2f}{r[10]:>8.2f}{r[11]:>10.2f}{flag}")
print("\n总物料:", len(rows), " 半成品/淘汰:", sum(1 for r in rows if r[12]), " 负值截断:", sum(1 for r in rows if r[13]))

# 8. 生成 SQL 脚本（task + task_material_summary）
import time
ts = time.strftime("%Y%m%d%H%M%S")
biz = f"TASK{ts}8027"
store_name = store["store_name"]
store_code = store.get("store_code") or ""
xcx = store.get("xiaochengxuid") or ""
wh = store.get("warehouse_code") or store["cangkuid"]
lines = []
lines.append("-- 8/7 周盘测试任务（库存紧张场景：理论剩余×30%，2026-08-21 生成）")
lines.append("-- 库: store_inventory_test  门店: 象子茶铺茶怒江东岸人民路店 (MDCK000082)")
lines.append("-- 口径: 理论剩余 = 594期末(7/31) + 8/1~8/7入库 − 6天消耗(PG销量+报损+调货+还货−自购)，再×0.3")
lines.append(f"INSERT INTO task (store_id, store_name, store_code, xiaochengxuid, warehouse_code, template_id, task_name, task_month, task_type, task_week, deadline, status, created_by, submitted_by, submitted_at, biz_code, version)")
lines.append(f"VALUES ('{STORE_ID}', '{store_name}', '{store_code}', '{xcx}', '{wh}', NULL, '8/7周盘测试任务(紧张)', '2026-08', 'weekly', '2026-W32', '2026-08-07 12:00:00', 'submitted', 'system', '陈燕', '2026-08-07 12:00:00', '{biz}', 0);")
lines.append(f"SET @task_id = LAST_INSERT_ID();")
lines.append(f"INSERT INTO task_material_summary (task_id, material_id, material_name, spec, base_unit, total_qty, original_qty, adjusted_qty, zone_count, unit_breakdown, del_flag, biz_code, version) VALUES")
vals = []
for r in rows:
    mid, name, cat, spec, end_qty, pur, sales, lossv, trv, rtv, spv, tight, excluded, negative = r
    rule = rules.get(mid) or {}
    base = rule.get("base_unit") or "g"
    vals.append(f"(@task_id, '{mid}', '{name}', '{spec}', '{base}', {tight:.2f}, {tight:.2f}, {tight:.2f}, 0, NULL, 0, '', 0)")
lines.append(",\n".join(vals) + ";")
lines.append("-- 执行后请回传任务ID（SELECT LAST_INSERT_ID() 或查最新 TASK id），用于 backtest")
sql_path = r"C:\Users\xiemg\Documents\test\inventory-tool\tmp_weekly_task_0807_tight.sql"
with open(sql_path, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print("\nSQL 已生成:", sql_path, " 任务biz:", biz)
cur.close(); conn.close()
