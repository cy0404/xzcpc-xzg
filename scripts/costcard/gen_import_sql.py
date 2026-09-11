# -*- coding: utf-8 -*-
"""
P2-c 生成成本卡导入 SQL（database/migration-cost-card-import-*.sql）
====================================================================
输入 output/{cards.json, material_map.json, semis.json}
产出 output/import_stat.txt + database/migration-cost-card-import-<date>.sql

规则：
  - cost_product 每基础产品一行；cost_card 每 Excel 卡一行（含 temp_words/is_default/
    售价）；cost_card_item 每配方行（RAW/SEMI/PACK/SKIP_WATER 不入行；UNMATCHED 行
    落库但 material 字段空、备注 UNMATCHED，供报表追踪）。
  - 默认卡：单卡产品；多卡产品优先 含"正常冰" 卡，否则 sort 最前。
  - item 金额：行内 amount 存在则用；否则 SEMI 行用半成品每g价(semis.json j_gml)×qty；
    MATERIAL 行按 cost_material_price 换算（下方物料单价表）。算不出留 NULL。
  - cost_material_price 种子：半成品成本表 G 列 1kg价 → 元/g（只 MATERIAL 叶/自购原料）。
  - 全量重建：脚本先 DELETE 5 张导入表再 INSERT（幂等可重放）；人工页面维护的
    MANUAL 行会被清掉 —— 重放前需人工确认（二期可做按 source=MANUAL 保护）。
用法：python gen_import_sql.py
"""
import sys
import io
import os
import json
import datetime
from collections import defaultdict

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "output")
DB = os.path.join(HERE, "..", "..", "database")

TEMP_KEY = {"正常冰": "normal_ice", "少冰": "less_ice", "少少冰": "least_ice",
            "去冰": "no_ice", "常温": "room_temp", "热": "hot", "温热": "warm",
            "冰": "ice"}


def sql_str(v):
    if v is None:
        return "NULL"
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def main():
    cards = json.load(io.open(os.path.join(OUT, "cards.json"), encoding="utf-8"))
    mp = json.load(io.open(os.path.join(OUT, "material_map.json"), encoding="utf-8"))
    semis = json.load(io.open(os.path.join(OUT, "semis.json"), encoding="utf-8"))

    # ---- 0. 去重：同名卡按配方指纹去重（Excel 存在复制粘贴冗余，
    #            如 山野滇红(去雪顶)少冰 出现两张配方完全相同的卡）----
    def fp(c):
        return tuple(sorted((r["excel_item_name"], r["quantity"]) for r in c["rows"]))

    seen = {}
    keep = []
    dup_dropped = 0
    for c in cards:
        k = c["card_name_full"]
        f = fp(c)
        if k in seen:
            if seen[k] == f:
                dup_dropped += 1
                continue  # 完全相同 → 丢弃后出现的冗余卡
        else:
            seen[k] = f
        keep.append(c)
    if dup_dropped:
        print(f"同名重复卡丢弃 {dup_dropped} 张（配方完全相同，保留首张）")
    cards = keep

    # ---- 1. 基础产品分组 ----
    prods = defaultdict(list)  # base_product -> cards
    for c in cards:
        prods[c["base_product"]].append(c)
    prod_names = sorted(prods.keys())

    # 默认卡：每产品
    def def_card(lst):
        norm_ice = [c for c in lst if "正常冰" in c.get("temp_words", [])]
        if norm_ice:
            return norm_ice[0]
        return sorted(lst, key=lambda c: c["_loc"])[0]

    # ---- 2. 物料单价种子（Sheet2 G 列 元/kg → 元/g）；仅作 cost_material_price ----
    mat_prices = {}
    for s in semis:
        for m in s.get("materials", []):
            g = m.get("g_kg_price")
            nm = m["name"]
            if g and nm and nm not in mat_prices:
                mat_prices[nm] = g / 1000.0
    # 原料行 MATERRIAL 的直接价格取 master qm_code? 保持：只放 sheet2 出现过的
    # （半成品表记录了单价；未在 sheet2 的原料（巴旦木等）Excel D 列行内已给系数）——
    # 追加 Excel 行 amount/qty 推算价到 material price 兜底
    for c in cards:
        for r in c["rows"]:
            if r["quantity"] and r["amount"]:
                nm = r["norm_name"]
                if nm and nm not in mat_prices:
                    mat_prices[nm] = r["amount"] / r["quantity"]

    # ---- 3. 组装行 ----
    lines = []
    lines.append("-- 成本卡导入（生成于 %s，由 scripts/costcard/parse_cost_excel.py + norm_matcher.py 产物生成）"
                 % datetime.date.today().isoformat())
    lines.append("-- 执行方式：手动执行；可重复（DELETE+INSERT 幂等）；重放会清掉成本域人工维护数据，执行前确认")
    lines.append("-- 映射疑问见 scripts/costcard/output/norm_report.txt；未映射原料行为 UNMATCHED（消耗计算跳过，报表可见）\n")

    def item_type(kind):
        return {"SEMI": "SEMI", "MATERIAL": "RAW", "PACK": "PACK"}.get(kind, "RAW")

    # 卡明细 rows 引用 mat map 找映射（norm_name 已归一）
    card_rows = []
    semi_rows = []
    stat = defaultdict(int)
    stat_unmatched_rows = []
    semi_prices = {s["semi_name"]: s.get("j_gml") for s in semis}
    price_by_qm = {}  # qm_code → 元/g·ml（第 4 节落 cost_material_price）

    for pi, pname in enumerate(prod_names):
        clist = prods[pname]
        default_full = def_card(clist)["card_name_full"]
        for c in clist:
            # 温度词
            tw = c.get("temp_words") or []
            temp_key = TEMP_KEY.get(tw[0], "default") if tw else "default"
            is_default = 1 if c["card_name_full"] == default_full else 0
            card_rows.append((pname, c, temp_key, is_default))

    # ---- 4. 生成 SQL ----
    out = []
    out.append("DELETE FROM cost_card_item; DELETE FROM cost_card; DELETE FROM cost_product;")
    out.append("DELETE FROM cost_product_alias; DELETE FROM cost_material_price;\n")

    # products
    out.append("-- 1) 产品（%d 个基础产品）" % len(prod_names))
    for pname in prod_names:
        out.append("INSERT INTO cost_product (product_name, category, source, status) VALUES (%s, NULL, 'IMPORT', 1);"
                   % sql_str(pname))
    out.append("")
    # cards（用 product_name 反查 id）
    out.append("-- 2) 变体卡（%d 张）" % len(card_rows))
    for pname, c, temp_key, is_default in card_rows:
        tw = c.get("temp_words") or []
        price = c.get("price")
        out.append("INSERT INTO cost_card (product_id, card_name, card_name_full, temp_key, temp_words, is_default, price, status)"
                   " SELECT id, %s, %s, %s, %s, %d, %s, 1 FROM cost_product WHERE product_name = %s;"
                   % (sql_str(c["card_name_full"]), sql_str(c["card_name_full"]),
                      sql_str(temp_key), sql_str(",".join(tw)), is_default,
                      "NULL" if price is None else str(price), sql_str(pname)))
    out.append("")
    # items
    out.append("-- 3) 配方行（原料/半成品/包材；SKIP_WATER 不落；UNMATCHED 落库留痕）")
    total_items = 0
    def norm2(s):
        import re as _re
        s = str(s)
        s = s.replace("（半成品）", "").replace("(半成品)", "").replace(" ", "").replace("　", "")
        s = s.replace("（", "(").replace("）", ")")
        s = _re.sub(r"\((g|ml|kg|克|毫升)\)?$", "", s)
        return _re.sub(r"(ml|g|ML|mL)$", "", s)

    for pname, c, temp_key, is_default in card_rows:
        for idx, r in enumerate(c["rows"]):
            nm = norm2(r["norm_name"] or r["excel_item_name"])
            ent = mp.get(nm) or {}
            kind = ent.get("kind")
            if kind == "SKIP_WATER":
                stat["skip_water"] += 1
                continue
            total_items += 1
            if kind == "PACK":
                it = "PACK"
                mid = qm = semi = None
                name = nm
            elif kind in ("SEMI", "MATERIAL"):
                it = "SEMI" if kind == "SEMI" else "RAW"
                mid = ent.get("material_id")
                qm = ent.get("qm_code")
                semi = ent.get("semi_code")
                name = ent.get("master_name") or nm
            else:  # UNMATCHED
                it = "RAW"
                mid = qm = semi = None
                name = nm
                stat["unmatched_items"] += 1
                stat_unmatched_rows.append((pname, r["excel_item_name"]))
            # 行成本
            amount = r.get("amount")
            qty = r.get("quantity")
            unit = r.get("unit")
            unit_price = None
            if amount is None and qty and kind == "SEMI" and semi_prices.get(ent.get("master_name")):
                unit_price = semi_prices.get(ent.get("master_name"))
                amount = round(unit_price * qty, 4)
            if amount is None and qty and kind == "MATERIAL":
                if mat_prices.get(nm):
                    unit_price = mat_prices[nm]
                    amount = round(unit_price * qty, 4)
            if amount is not None and unit_price is None and qty:
                unit_price = amount / qty
            # 单价种子（按 qm_code 收集，供第 4 节落 cost_material_price）
            if kind == "MATERIAL" and qm and unit_price is not None and qm not in price_by_qm:
                price_by_qm[qm] = unit_price
            if kind in ("SEMI", "MATERIAL"):
                stat["mapped_items"] += 1
            elif kind == "PACK":
                stat["pack_items"] += 1
            out.append("INSERT INTO cost_card_item (card_id, item_type, excel_item_name, material_name,"
                       " material_id, qm_code, semi_code, quantity, unit, unit_price, amount, sort_no)"
                       " SELECT id, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %d FROM cost_card WHERE card_name_full = %s;"
                       % (sql_str(it), sql_str(r["excel_item_name"]), sql_str(name),
                          sql_str(mid), sql_str(qm), sql_str(semi),
                          "NULL" if qty is None else repr(qty), sql_str(unit),
                          "NULL" if unit_price is None else repr(round(unit_price, 8)),
                          "NULL" if amount is None else repr(amount), idx, sql_str(c["card_name_full"])))
    out.append("")
    # prices
    out.append("-- 4) 物料标准单价种子（元/单位；按 qm_code 精确匹配物料，避免名称括号/点号差异静默漏插）")
    for qmc, ppu in sorted(price_by_qm.items()):
        out.append("INSERT INTO cost_material_price (material_id, material_name, qm_code, unit, price_per_unit, source)"
                   " SELECT material_id, material_name, qm_code, %s, %s, 'IMPORT' FROM material"
                   " WHERE qm_code = %s AND del_flag=0 LIMIT 1;"
                   % (sql_str("g"), repr(round(ppu, 8)), sql_str(qmc)))
    out.append("")

    fname = "migration-cost-card-import-%s.sql" % datetime.date.today().isoformat().replace("-", "")
    with io.open(os.path.join(DB, fname), "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")

    # ---- 5. 统计报告 ----
    rep = []
    rep.append("产品=%d 卡=%d 配方行=%d（SKIP_WATER 不计）" %
               (len(prod_names), len(card_rows), total_items))
    rep.append("行级统计: %s" % dict(stat))
    rep.append("UNMATCHED 行（%d）: 见下（消耗计算跳过，报表可见）" % len(stat_unmatched_rows))
    for p, n in stat_unmatched_rows:
        rep.append("   [%s] %s" % (p, n))
    rep.append("MATERIAL 价格种子=%d" % len(mat_prices))
    rep.append("SQL 文件: database/%s" % fname)
    txt = "\n".join(rep)
    print(txt)
    with io.open(os.path.join(OUT, "import_stat.txt"), "w", encoding="utf-8") as f:
        f.write(txt + "\n")


if __name__ == "__main__":
    main()
