# -*- coding: utf-8 -*-
"""
只读校验 migration-cost-card-pack-20260911.sql 的效果（不写任何数据）
  1) 复刻 SQL：命中哪些卡、插入多少行、逐卡包材成本
  2) 与现有整包「包材」金额对账（delta）
  3) 校验 qm_code 是否都能在 material 主数据里找到
  4) 校验快照单价 vs 现盘点单价是否漂移
"""
import sys, os
import pymysql

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_pack_sql import M, SPEC, NOT_COVERED, rows_of  # noqa: E402  (其内部已包装 stdout)

from _conn import MY   # 连接参数见 _conn.py（含密码，不入库）
conn = pymysql.connect(**MY)
c = conn.cursor()

# 物料主数据（按 qm_code 与 material_id 双索引）
c.execute("SELECT qm_code, material_id, material_name FROM material WHERE del_flag = 0")
mat = {r[0]: (r[1], r[2]) for r in c.fetchall() if r[0]}
c.execute("SELECT material_id, material_name FROM material WHERE del_flag = 0")
mat_by_id = {r[0]: r[1] for r in c.fetchall()}
# 盘点单价
c.execute("""SELECT material_id, MAX(unit_price) FROM material_inventory_rule
             WHERE del_flag = 0 GROUP BY material_id""")
price = {r[0]: float(r[1]) for r in c.fetchall() if r[1] is not None}
# 成本卡
c.execute("""SELECT p.product_name, k.temp_key, k.id FROM cost_card k
             JOIN cost_product p ON p.id = k.product_id""")
cards = {(r[0], r[1]): r[2] for r in c.fetchall()}
# 现有 PACK 行
c.execute("""SELECT i.card_id, i.amount FROM cost_card_item i WHERE i.item_type='PACK'""")
old_pack = {}
for cid, amt in c.fetchall():
    old_pack.setdefault(cid, []).append(float(amt or 0))
# 非 PACK 最大 sort_no
c.execute("""SELECT card_id, MAX(sort_no) FROM cost_card_item
             WHERE item_type <> 'PACK' GROUP BY card_id""")
max_sort = {r[0]: (r[1] or 0) for r in c.fetchall()}

print("=" * 108)
print("【A】dry-run：命中卡数 / 插入行数")
hit_cards, ins_rows, missing_qm, drift = set(), 0, [], []
newcost = {}
no_card_prod = []
for _row in SPEC:
    pname, tkeys, cup, straw = _row[:4]
    extras = _row[4] if len(_row) > 4 else ()
    got = False
    for tk in tkeys:
        cid = cards.get((pname, tk))
        if cid is None:
            continue  # 该温度变体未建卡，SQL 从卡侧驱动不会命中，正常
        got = True
        hit_cards.add(cid)
        tot = 0.0
        for qm, mid, mname, unit, qty, price_, label in rows_of(cup, straw, extras):
            ins_rows += 1
            if mid:  # 手工物料：按 material_id 命中
                real = mat_by_id.get(mid)
                if real is None:
                    missing_qm.append((pname, tk, mid, mname))
                    continue
            else:
                if qm not in mat:
                    missing_qm.append((pname, tk, qm, mname))
                    continue
                mid, real = mat[qm]
            if real != mname:
                missing_qm.append((pname, tk, qm or mid, "%s≠%s" % (mname, real)))
            if mid in price and abs(price[mid] - price_) > 1e-9:
                drift.append((mname, price_, price[mid]))
            tot += qty * price_
        newcost[cid] = tot
    if not got:
        no_card_prod.append(pname)

print("  命中成本卡 = %d 张（%d 个产品）" % (len(hit_cards), len({s[0] for s in SPEC})))
print("  插入 PACK 行 = %d 行" % ins_rows)
print("  主数据缺失 = %d" % len(missing_qm), missing_qm[:5] if missing_qm else "")
print("  单价漂移 = %d" % len(drift), sorted(set(drift))[:5] if drift else "")
if no_card_prod:
    print("  !! 完全无卡的产品:", no_card_prod)

print()
print("=" * 108)
print("【B】逐卡包材成本：新明细 vs 原整包（按 delta 降序，只列前 30）")
rows = []
for (pname, tk), cid in sorted(cards.items()):
    if cid not in newcost:
        continue
    old = sum(old_pack.get(cid, [])) or None
    rows.append((pname, tk, newcost[cid], old, (newcost[cid] - old) if old is not None else None))
rows.sort(key=lambda r: -(r[4] if r[4] is not None else 0))
print("  %-28s %-11s %8s %8s %8s" % ("产品", "temp_key", "新明细", "原整包", "delta"))
print("  " + "-" * 70)
for r in rows[:18]:
    print("  %-28s %-11s %8.4f %8s %8s"
          % (r[0][:26], r[1], r[2], ("%.4f" % r[3]) if r[3] is not None else "-",
             ("%+.4f" % r[4]) if r[4] is not None else "-"))
print("  …")
for r in rows[-10:]:
    print("  %-28s %-11s %8.4f %8s %8s"
          % (r[0][:26], r[1], r[2], ("%.4f" % r[3]) if r[3] is not None else "-",
             ("%+.4f" % r[4]) if r[4] is not None else "-"))
print("  （共 %d 张卡，中间省略）" % len(rows))

print()
print("  分档统计：")
inc = [r for r in rows if r[4] is not None and r[4] > 1e-6]
dec = [r for r in rows if r[4] is not None and r[4] < -1e-6]
same = [r for r in rows if r[4] is not None and abs(r[4]) <= 1e-6]
non = [r for r in rows if r[4] is None]
print("    变贵 %d 张，平均 %+.4f（最高 %+.4f）"
      % (len(inc), sum(r[4] for r in inc) / len(inc) if inc else 0,
         max((r[4] for r in inc), default=0)))
print("    变便宜 %d 张，平均 %+.4f（最低 %+.4f）"
      % (len(dec), sum(r[4] for r in dec) / len(dec) if dec else 0,
         min((r[4] for r in dec), default=0)))
print("    持平 %d 张；无原值 %d 张" % (len(same), len(non)))

print()
print("=" * 108)
print("【C】未覆盖产品（保持原整包行）的现存金额")
rest = {}
for (pname, tk), cid in cards.items():
    if cid in newcost or pname not in NOT_COVERED:
        continue
    rest.setdefault(pname, []).append((tk, sum(old_pack.get(cid, []))))
for pn in NOT_COVERED:
    if pn in rest:
        print("  %-28s %s" % (pn, ", ".join("%s=%.4f" % t for t in rest[pn])))
    else:
        print("  %-28s (无卡或无 PACK 行)" % pn)

print()
print("=" * 108)
print("【E】把生成的 SQL 送进 MySQL 只读执行（不加 INSERT/DELETE），验证语法与口径")
sqlfile = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                       "database", "migration-cost-card-pack-20260911.sql")
lines = open(sqlfile, encoding="utf-8").read().splitlines()


def block(start_prefix):
    """抽 [start 行 .. JOIN material 及其续行] 的一段，剥掉注释前缀"""
    i = next(k for k, l in enumerate(lines) if l.startswith(start_prefix))
    j = next(k for k, l in enumerate(lines)
             if k > i and l.lstrip("-- ").startswith("JOIN material"))
    txt = "\n".join(lines[i:j + 2])          # +2 = JOIN 行本身 + 其续行
    return "\n".join(l[3:] if l.startswith("-- ") else l for l in txt.splitlines()).strip()


try:
    q_dry = block("-- SELECT COUNT(*) AS will_insert")
    q_ins = block("SELECT k.id, 'PACK'")
    c.execute(q_dry)
    n_dry = c.fetchone()[0]
    c.execute(q_ins)
    rows2 = c.fetchall()
    cols = [d[0] for d in c.description]
    print("  0) dry-run SELECT  → will_insert = %d" % n_dry)
    print("  3) INSERT 的 SELECT → %d 列 / %d 行" % (len(cols), len(rows2)))
    print("     目标列 11 个，对齐: %s" % ("✓" if len(cols) == 11 else "✗ 不一致"))
    print("     两处行数一致: %s" % ("✓" if n_dry == len(rows2) == ins_rows else "✗ %d/%d/%d" % (n_dry, len(rows2), ins_rows)))
    nullcells = [(i, cols[i]) for r in rows2 for i in range(len(r)) if r[i] is None]
    print("     NULL 单元（应无）: %s"
          % (sorted({c2 for _, c2 in nullcells}) or "无 ✓"))
except Exception as e:
    print("  !! MySQL 只读执行失败:", e)

print()
print("=" * 108)
print("【F】语句级语法校验：剥注释后按 ; 切分，每条用 PREPARE 解析（只解析、不执行）")


def split_statements(text):
    """剥掉 -- 行注释（不动字符串内的 --），按 ; 切分，返回 [(起始行号, 语句)]"""
    out, buf, line, start = [], [], 1, 1
    i, n, in_str = 0, len(text), False
    while i < n:
        ch = text[i]
        if in_str:
            buf.append(ch)
            if ch == "'":
                if i + 1 < n and text[i + 1] == "'":   # '' 转义
                    buf.append(text[i + 1]); i += 2; continue
                in_str = False
            if ch == "\n":
                line += 1
            i += 1
            continue
        if ch == "'":
            in_str = True; buf.append(ch); i += 1; continue
        if ch == "-" and i + 1 < n and text[i + 1] == "-":
            j = text.find("\n", i)
            j = n if j < 0 else j
            buf.append("\n" * text.count("\n", i, j))   # 保留行号
            line += text.count("\n", i, j)
            i = j
            continue
        if ch == ";":
            s = "".join(buf).strip()
            if s:
                out.append((start, s))
            buf = []
            line += 1
            start = line
            i += 1
            continue
        if ch == "\n":
            line += 1
        buf.append(ch)
        i += 1
    s = "".join(buf).strip()
    if s:
        out.append((start, s))
    return out


stmts = split_statements(open(sqlfile, encoding="utf-8").read())
print("  切出语句 %d 条：" % len(stmts))
bad = 0
for ln, st in stmts:
    head = " ".join(st.split())[:58]
    try:
        c.execute("PREPARE __chk FROM %s", (st,))
        c.execute("DEALLOCATE PREPARE __chk")
        print("    L%-4d ✓ %s" % (ln, head))
    except Exception as e:
        code = e.args[0] if e.args else "?"
        if code == 1295:      # ER_UNSUPPORTED_PS：该语句不支持 prepare，跳过
            print("    L%-4d ~ %s  (不支持 PREPARE，跳过)" % (ln, head))
            continue
        bad += 1
        print("    L%-4d ✗ %s" % (ln, head))
        print("           → %s" % e)
print("  %s" % ("全部语句语法通过 ✓" if bad == 0 else "!! %d 条语句语法错误" % bad))

print()
print("=" * 108)
print("【G】DELETE 影响面：把 2) 的 WHERE 条件换成 SELECT COUNT 按 item_type 分组（只读）")
dl = next(k for k, l in enumerate(lines) if l.startswith("DELETE i FROM"))
dend = next(k for k, l in enumerate(lines) if k > dl and l.strip() == ");")
dwhere = "\n".join(lines[dl:dend + 1])
dwhere = dwhere[dwhere.index("WHERE"):].rstrip(";")
c.execute("""SELECT i.item_type, COUNT(*) FROM cost_card_item i
             JOIN cost_card k    ON k.id = i.card_id
             JOIN cost_product p ON p.id = k.product_id
             """ + dwhere + " GROUP BY i.item_type")
res = c.fetchall()
for t, n in res:
    flag = "✓" if t == "PACK" else "!! 不该删的"
    print("    item_type=%-6s %4d 行  %s" % (t, n, flag))
nonpack = [t for t, _ in res if t != "PACK"]
print("  %s" % ("只命中 PACK 行 ✓" if not nonpack
                else "!! 会误删 %s 行，检查 AND/OR 优先级括号" % nonpack))
# EXPLAIN 估算行数
try:
    c.execute("EXPLAIN " + " ".join(dwhere.split()))
    est = c.fetchone()[0]
    print("    EXPLAIN 估算行数 = %s" % est)
except Exception as e:
    print("    EXPLAIN:", e)

print()
print("=" * 108)
print("【D】新口径下单卡包材成本一览（按成本降序）")
agg = {}
for pname, tk, new, old, d in rows:
    agg.setdefault(new, []).append("%s/%s" % (pname, tk))
for k in sorted(agg, reverse=True):
    print("  %.4f  ×%-3d  %s" % (k, len(agg[k]), "、".join(agg[k][:4]) + ("…" if len(agg[k]) > 4 else "")))
conn.close()
