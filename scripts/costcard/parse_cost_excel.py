# -*- coding: utf-8 -*-
"""
P2-a Excel 成本卡解析：象子茶铺产品成本表.xlsx
================================================
解析「产品成本表」Sheet1（左右双卡布局，96 卡 663 行）→ cards.json；
解析「半成品成本表」Sheet2 → semis.json（单价初值 seed）。

产出（scripts/costcard/output/）：
  cards.json    [{product_name, card_name_full, temp_words, is_default,
                  price, cost, gross_margin, rows:[{excel_item_name, qty, unit,
                  amount, quantity_text}]}]
  semis.json    [{semi_name, materials:[{name, g_kg_price(元/kg), cost_gml}],
                  j_gml(半成品每 g/ml 价格), net_output_g, yield_rate}]
  parse_warn.txt  文本单位缺失/非常规数量等告警（交人核对）

用法：python parse_cost_excel.py <xlsx路径>
"""
import sys
import io
import os
import json
import re
import openpyxl

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "output")
os.makedirs(OUT_DIR, exist_ok=True)

# 温度词表（长词优先，key 供 cost_card.temp_key 参考；卡级 is_default 由导入再定）
TEMP_WORDS = [
    "正常冰", "少少冰", "少冰", "去冰", "常温", "温热", "热", "冰",
]
TEMP_RE = re.compile(r"[（(]([^（）()]*)[）)]")


def norm_name(s):
    """清洗卡名空白，便于日志/去重"""
    s = s.replace("\n", "").replace(" ", "").replace("　", "").replace("（", "(").replace("）", ")")
    return s.strip()


def split_temp(name):
    """把卡名拆成 (基础产品名, 温度词列表)。示例：
       '手作米布鲜奶茶（冰）'          → ('手作米布鲜奶茶', ['冰'])
       '茉莉奶白正常冰'                → ('茉莉奶白', ['正常冰'])
       '手作米布鲜奶茶（去冰/常温）'   → ('手作米布鲜奶茶', ['去冰','常温'])
       '厚牛油果酸奶巴旦木（奶昔）'    → ('厚牛油果酸奶巴旦木（奶昔）', [])  # 奶昔非温度词
       '释迦果·芭乐冰茶正常冰少冰'     → ('释迦果·芭乐冰茶', ['正常冰','少冰'])
    """
    temps = []
    def repl(m):
        inner = m.group(1).strip()
        hit = [w for w in TEMP_WORDS if inner and (w in inner or inner in w)]
        if hit:
            temps.extend(hit)
            return ""
        return m.group(0)
    base = TEMP_RE.sub(repl, name).strip()
    # 非括号温度词：循环剥除直到稳定（'正常冰少冰'/'正常冰' 一次剥一个，长词优先）
    # 规则：多字符温度词可裸剥（如 茉莉奶白正常冰 → 茉莉奶白）；单字符 冰/热
    #       仅当前面是 分隔符(/ - ·) 才剥，避免误伤 '冰茶' 的 '冰'
    changed = True
    while changed and base:
        changed = False
        for w in sorted(TEMP_WORDS, key=len, reverse=True):
            if not base.endswith(w):
                continue
            if len(w) == 1:
                if len(base) == 1 or base[-2] not in "/-·":
                    continue
            base = base[: -len(w)].rstrip(" /·-")
            temps.insert(0, w)
            changed = True
            break
    # 去重保序
    seen = []
    for t in temps:
        if t not in seen:
            seen.append(t)
    return base, seen


def unit_of(item_name):
    """从原料名剥离单位括注 → (名称主干, unit)。unit None=名称未标单位（导入告警）"""
    s = item_name.strip()
    m = re.search(r"[（(](g|ml)[）)]$", s, re.I)
    if m:
        return s[: m.start()].strip(), m.group(1).lower()
    m = re.search(r"[（(](g|ml)(?:[）)]|[)（(])", s, re.I)
    if m:
        return s[: m.start()].strip(), m.group(1).lower()
    return s, None


def parse_sheet1(ws):
    """返回 cards 列表。块定位：A 列/H 列纵向合并的名格（排除全宽家族标题与合计/原料行）"""
    merged = list(ws.merged_cells.ranges)

    def span(r, c):
        for m in merged:
            if m.min_row <= r <= m.max_row and m.min_col <= c <= m.max_col:
                return m
        return None

    def is_block_name_cell(r, c):
        v = ws.cell(row=r, column=c).value
        if not isinstance(v, str):
            return False, None
        s = v.strip().replace("\n", "")
        if not s or s in ("合计",) or s.startswith("原料"):
            return False, None
        m = span(r, c)
        if not m or m.max_row <= m.min_row:
            return False, None   # 非纵向块（家族标题全宽除外已由宽过滤）
        if m.max_col - m.min_col > 4:   # 全宽家族标题（如 A38:M38）
            return False, None
        return True, m

    # 收集所有左(L)/右(R)产品块 [列, 起行, 名]
    blocks = []
    for r in range(1, ws.max_row + 1):
        for c in (1, 8):
            ok, m = is_block_name_cell(r, c)
            if ok:
                v = ws.cell(row=r, column=c).value
                blocks.append({"col": c, "row": r, "r1": m.min_row, "r2": m.max_row,
                               "name": norm_name(v)})
    blocks.sort(key=lambda b: (b["row"], b["col"]))

    cards = []
    warn = []
    for b in blocks:
        col = b["col"]
        nc = col + 1      # 原料名列（B/I）
        qc = col + 2      # 用量列（C/J）
        cc = col + 3      # 成本列（D/K）
        rows = []
        for r in range(b["r1"] + 1, b["r2"] + 1):
            name_v = ws.cell(row=r, column=nc).value
            if name_v is None:
                continue
            nm = str(name_v).strip()
            if not nm or nm == "原料":
                continue
            qv = ws.cell(row=r, column=qc).value
            cv = ws.cell(row=r, column=cc).value
            qty = None
            qtext = None
            if isinstance(qv, (int, float)):
                qty = float(qv)
            elif isinstance(qv, str) and qv.strip():
                qtext = qv.strip()
                m = re.match(r"^\s*([\d.]+)\s*", qv)
                if m:
                    qty = float(m.group(1))
                else:
                    warn.append(f"[{b['name']}] 非常规用量文本未取数: {nm} = '{qtext}'")
            if qty is not None and (not isinstance(qv, (int, float))) and isinstance(qv, str):
                warn.append(f"[{b['name']}] 用量为文本取值(默认按g计): {nm} = '{qtext}'")
            main, unit = unit_of(nm)
            if qty is not None and unit is None:
                unit = "g"  # 名称未标单位默认 g（ml≡g 口径，液体密度偏差计入损耗）
            rows.append({
                "excel_item_name": nm,
                "norm_name": main,
                "unit": unit,
                "quantity": qty,
                "quantity_text": qtext,
                "amount": float(cv) if isinstance(cv, (int, float)) else None,
            })
        # 合计行（块尾下一行：A/H 列 '合计'）
        tr = b["r2"] + 1
        price = ws.cell(row=tr, column=col + 4).value      # E/L
        cost = ws.cell(row=tr, column=col + 3).value       # D/K
        margin = ws.cell(row=tr, column=col + 5).value     # F/M
        # 部分卡合计在 r2+1 但右块尽头为整行错位时容错：若该行无合计标记，找下 3 行内
        tag = ws.cell(row=tr, column=col).value
        if not (isinstance(tag, str) and tag.strip() == "合计"):
            warn.append(f"[{b['name']}] 合计行未在预期位置(r{tr})，price/cost 可能缺失")

        base, temps = split_temp(b["name"])
        cards.append({
            "card_name_full": b["name"],
            "base_product": base,
            "temp_words": temps,
            "price": float(price) if isinstance(price, (int, float)) else None,
            "cost": float(cost) if isinstance(cost, (int, float)) else None,
            "gross_margin": float(margin) if isinstance(margin, (int, float)) else None,
            "rows": rows,
            "_loc": f"{'L' if col == 1 else 'R'}{b['row']}",
        })
    return cards, warn


def parse_sheet2(ws):
    """半成品成本表：A 列半成品名（含重复同名不同配方），B 列原料，D 原克重，G 1kg价，J 每g/ml价"""
    semis = []
    cur = None
    for r in range(3, ws.max_row + 1):
        an = ws.cell(row=r, column=1).value
        bn = ws.cell(row=r, column=2).value
        if isinstance(an, str) and an.strip():
            if cur:
                semis.append(cur)
            cur = {"semi_name": an.strip(), "materials": []}
        if cur is None:
            continue
        if isinstance(bn, str) and bn.strip():
            g = ws.cell(row=r, column=7).value
            cur["materials"].append({
                "name": bn.strip(),
                "raw_g": ws.cell(row=r, column=4).value,
                "g_kg_price": float(g) if isinstance(g, (int, float)) else None,
            })
        j = ws.cell(row=r, column=10).value
        if isinstance(j, (int, float)) and cur.get("j_gml") is None:
            cur["j_gml"] = float(j)
        e = ws.cell(row=r, column=5).value
        if isinstance(e, (int, float)) and cur.get("net_output_g") is None:
            cur["net_output_g"] = float(e)
    if cur:
        semis.append(cur)
    return semis


def main():
    if len(sys.argv) < 2:
        print("用法: python parse_cost_excel.py <象子茶铺产品成本表.xlsx>")
        return 1
    path = sys.argv[1]
    wb = openpyxl.load_workbook(path, data_only=True)
    print("sheets:", wb.sheetnames)
    ws1 = wb["产品成本表"]
    ws2 = wb["半成品成本表"]
    cards, warn = parse_sheet1(ws1)
    semis = parse_sheet2(ws2)

    # 组报告 + 落盘
    with io.open(os.path.join(OUT_DIR, "cards.json"), "w", encoding="utf-8") as f:
        json.dump(cards, f, ensure_ascii=False, indent=1)
    with io.open(os.path.join(OUT_DIR, "semis.json"), "w", encoding="utf-8") as f:
        json.dump(semis, f, ensure_ascii=False, indent=1)
    with io.open(os.path.join(OUT_DIR, "parse_warn.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(warn))

    from collections import Counter
    names = Counter()
    for c in cards:
        names[c["base_product"]] += 1
    total_rows = sum(len(c["rows"]) for c in cards)
    unit_missing = sum(1 for c in cards for row in c["rows"]
                       if row["quantity"] is not None and unit_of(row["excel_item_name"])[1] is None)
    print(f"卡数={len(cards)} 期望 96+; 配方行合计={total_rows} 期望 663")
    print(f"基础产品数={len(names)}; 单位未标注行={unit_missing}")
    print("parse_warn 行数:", len(warn), "（见 output/parse_warn.txt）")
    # 基础产品名单 + 温度维度统计
    print("\n基础产品与变体卡数：")
    for k, v in names.most_common():
        print(f"  {v}  {k}")
    print("\nsemis 数量:", len(semis))


if __name__ == "__main__":
    main()
