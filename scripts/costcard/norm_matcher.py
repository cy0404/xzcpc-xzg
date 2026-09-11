# -*- coding: utf-8 -*-
"""
P2-b 名称归一匹配：Excel 原料/半成品行 → material 主数据 / semi_product
=======================================================================
输入：output/cards.json（parse_cost_excel 产物）+ 根目录 xinfo 快照
     （xinfo_materials_live.json=452 物料, xinfo_semis_live.json=86 半成品）
产出（output/）：
  material_map.json  归一后原料名 → {kind: MATERIAL/SEMI/UNMATCHED/PACK/SKIP,
                                    material_id, qm_code, semi_code, semi_name,
                                    match: exact|alias|fuzzy|manual|none, master_name}
  norm_report.txt    汇总 + 未匹配/低置信清单（供人工审查）

匹配策略（三级）：
  1. 别名/纠错表（业务观察沉淀：预支→预制、基地→基底、去单位/去后缀等）
  2. 精确：归一化后名 == semi.name（去（半成品）后缀） 或 material.name
     （半成品优先：牛油果泥=自制半成品而非采购原料）
  3. 包含/相似度：名内包含或互为子串（≥2 字）→ fuzzy 标注待人工确认
  未命中 → UNMATCHED（导入照常落卡行但不参与消耗/成本，报表可见）
特殊规则：
  - 包材/杯等 → PACK（只计成本）
  - 净水/开水/热水/冰块/沸水 → SKIP_WATER（免费辅料，无消耗无成本）
  - 名称同 code 命中多候选 → 半成品优先 + status ENABLED 优先 + 短名优先
用法：python norm_matcher.py
"""
import sys
import io
import os
import json
import re
from difflib import SequenceMatcher

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "output")
ROOT = os.path.join(HERE, "..", "..")

PACK_HINT = ("包材", "杯", "吸管", "盖", "勺", "袋", "贴纸", "标签")
WATER_HINT = ("净水", "开水", "热水", "冰块", "沸水", "纯净水", "水")

# 纠错/别名表（key=Excel 归一写法 → 目标标准名（须为主数据/半成品真实名））
# 依据：Excel 内嵌成本系数与主数据单价互证（0.41/30g≈乍甸酸奶 0.0139/g 等）
ALIAS = {
    # 茶底系列（金芽滇红茶汤 WP0556 = 山野滇红茶汤 WP0556 改版同 code，爆炸目标一致）
    "山野滇红": "金芽滇红茶汤", "滇红茶": "金芽滇红茶汤",
    "滇红茶汤": "金芽滇红茶汤", "滇红": "金芽滇红茶汤",
    "兰韵观音": "兰韵清露茶汤", "兰韵茶汤": "兰韵清露茶汤",
    "茉莉茶汤": "云豪茉莉茶汤",  # Excel 泛指茉莉茶汤
    "茉莉茶": "云豪茉莉茶汤", "茉莉": "云豪茉莉茶汤",  # 180-280ml 实为泡好茶汤量
    "预制青柚汁": "预制柚子汁",  # 青柚=柚子叫法；≠预制青芒汁
    "预制南汁": "预制南姜汁", "预制南姜": "预制南姜汁",
    # 乳品/糖浆类
    "酸奶": "乍甸酸奶",  # 0.41元/30g≈乍甸 0.0139/g；酸奶酱另名精确命中
    "鲜奶": "悦鲜活鲜奶",  # Excel 0.0163/ml≈悦鲜活；排除 PLA吸管 干扰
    "调制椰乳": "调制椰奶", "调制椰奶": "调制椰奶",
    "调制奶基地": "调制奶基底", "奶基地": "调制奶基底",
    "咸法酪": "咸法干酪乳", "咸法干酪": "咸法干酪乳",
    "淡奶油": "安佳淡奶油",
    "冰蔗糖": "冰蔗糖浆(新)",
    "黑糖珍珠": "珍珠",
    # 坚果/果干/粒（0.08/0.12 系数对应）
    "巴旦木": "剥壳巴旦木1公斤", "碧根果": "碧根果仁", "蔓越莓": "蔓越莓干",
    "速冻西柚粒": "速冻白西柚果粒",
    # 备制类
    "香水柠檬片": "柠檬片", "香水柠檬": "柠檬片", "香柠檬": "柠檬片",
    "柠檬叶一片": "柠檬叶",
    "树番茄浆": "冷冻树番茄",  # 0.045/g 同冷冻树番茄（店内打浆）
    "预制荔枝汁": "荔枝预制汁", "预支荔枝汁": "荔枝预制汁",
    # 其他（价格互证）
    "话梅": "洱宝话梅", "米酒": "甜白酒", "玫瑰花": "花瓣（原材料）",
    "水果糖浆": "水果糖浆.", "米布": "冷冻牛奶米布",
}
# 强制挂 UNMATCHED（避免 fuzzy 猜错；宁缺勿错，报表可见待补）
# 芒果预制：无 xinfo 半成品/主数据对应（Excel 自备芒果块），挂账不误配鲜芒果
FORCE_UNMATCHED = {"预制小黄姜汁", "小黄姜", "青玉观音", "芒果预制"}


def norm(s):
    """轻量归一：半角化、去空白、（半成品）后缀、纯单位括注清理"""
    s = str(s)
    s = s.replace("（半成品）", "").replace("(半成品)", "")
    s = s.replace(" ", "").replace("　", "")
    s = s.replace("（", "(").replace("）", ")")
    s = s.replace("浆叶", "叶")
    # 尾部单位括注去掉（名字已由 parse 剥离单位，这里兜底）
    s = re.sub(r"\((g|ml|kg|克|毫升|L|l)\)?$", "", s)
    s = re.sub(r"(ml|g|ML|mL)$", "", s)
    return s


def load_json(fn):
    with io.open(os.path.join(ROOT, fn), encoding="utf-8") as f:
        return json.load(f)


def build_master():
    """直连生产库（store_inventory）取 material + semi_product 真值。
    快照 json 已过时（semi 93 vs 86），DB 为唯一准。material 半成品行可能 del_flag=1
    （旧名镜像），收录全状态，pick 时优先 del_flag=0+category 非半成品。
    返回 mat_by/semi_by：归一名 → [{...}]；MATERIAL 键含 material_id/qm_code/category/del_flag，
    SEMI 键含 semi_id/code/name/net_output_quantity/net_output_unit/cost/has_active"""
    import pymysql
    from _conn import MY
    conn = pymysql.connect(**MY, connect_timeout=10)
    mat_by, semi_by = {}, {}
    cur = conn.cursor()
    cur.execute("SELECT material_id, qm_code, material_name, category, del_flag "
                "FROM material WHERE qm_code IS NOT NULL AND qm_code <> ''")
    for mid, code, name, cat, delf in cur.fetchall():
        nm = norm(name or "")
        if nm:
            mat_by.setdefault(nm, []).append(
                {"material_id": mid, "qm_code": code, "name": name,
                 "category": cat, "del_flag": delf})
    try:  # manual_override 列随 migration-cost-card.sql 落地后才存在
        cur.execute("SELECT semi_id, code, name, net_output_quantity, net_output_unit, "
                    "cost, status, manual_override FROM semi_product")
        rows = cur.fetchall()
        manual_ok = True
    except pymysql.err.OperationalError:
        cur.execute("SELECT semi_id, code, name, net_output_quantity, net_output_unit, "
                    "cost, status, 0 FROM semi_product")
        rows = cur.fetchall()
        manual_ok = False
    for sid, code, name, noq, nou, cost, st, manual in rows:
        nm = norm(name or "")
        if nm:
            rec = {"semi_id": sid, "code": code, "name": name,
                   "net_output_quantity": noq, "net_output_unit": nou,
                   "cost": cost, "status": st, "manual_override": manual if manual_ok else 0}
            semi_by.setdefault(nm, []).append(rec)
    # 有 ACTIVE 版本 → 可爆炸
    cur.execute("SELECT semi_id FROM semi_formula_version WHERE status='ACTIVE' AND del_flag=0")
    active = {r[0] for r in cur.fetchall()}
    for lst in semi_by.values():
        for s in lst:
            s["has_active"] = s["semi_id"] in active
    conn.close()
    return mat_by, semi_by


def pick_semi(cands):
    """多候选：优先 ENABLED + has_active（有 ACTIVE 配方可炸）"""
    def score(s):
        return (s.get("status") == "ENABLED", bool(s.get("has_active")),
                s.get("net_output_quantity") is not None,
                -len(s.get("name", "")))
    return sorted(cands, key=score, reverse=True)[0]


def pick_mat(cands):
    """优先 del_flag=0 且 category 非'半成品'（半成品镜像行由 semi 表管）；再短名"""
    def score(m):
        return (m.get("del_flag") == 0, m.get("category") != "半成品",
                -len(m.get("name", "")))
    return sorted(cands, key=score, reverse=True)[0]


def main():
    cards = json.load(io.open(os.path.join(OUT, "cards.json"), encoding="utf-8"))
    mat_by, semi_by = build_master()
    # 待选集合（fuzzy 用）：物料只留 del_flag=0；半成品全收
    semi_items = [s for lst in semi_by.values() for s in lst]
    mat_items = [m for lst in mat_by.values() for m in lst if m.get("del_flag") == 0]

    distinct = {}
    for c in cards:
        for r in c["rows"]:
            key = r["norm_name"] or r["excel_item_name"]
            key = norm(key)
            distinct.setdefault(key, {"excel_examples": [], "row_type": None})
            ex = r["excel_item_name"]
            if ex not in distinct[key]["excel_examples"]:
                distinct[key]["excel_examples"].append(ex)
            if distinct[key]["row_type"] is None:
                distinct[key]["row_type"] = "PACK" if any(p in ex for p in PACK_HINT) else \
                                           ("WATER" if key in WATER_HINT else "MAT")

    def norm_name_manual(key):
        # 别名表/纠错：一次映射到主数据真实名（别名值按 norm 对齐）
        if key in FORCE_UNMATCHED:
            return None
        tgt = ALIAS.get(key)
        if tgt:
            return norm(tgt)
        return key

    result = {}
    unmatched = []
    low_conf = []
    for key, meta in distinct.items():
        if meta["row_type"] == "PACK":
            result[key] = {"kind": "PACK", "match": "exact"}
            continue
        if meta["row_type"] == "WATER":
            result[key] = {"kind": "SKIP_WATER", "match": "exact"}
            continue
        orig_key = key
        target = norm_name_manual(key)
        rec = {"kind": None, "match": None, "excel_examples": meta["excel_examples"]}
        if target is None:
            result[orig_key] = {"kind": "UNMATCHED", "match": "none",
                                "excel_examples": meta["excel_examples"], "forced": True}
            unmatched.append((orig_key, meta["excel_examples"]))
            continue
        if target != key:
            rec["alias_to"] = target
            key = target
        # 1) 半成品精确
        if key in semi_by:
            s = pick_semi(semi_by[key])
            rec.update(kind="SEMI", match="exact", semi_code=s.get("code"),
                       semi_id=s.get("semi_id"), master_name=s.get("name"))
            result[orig_key] = rec
            continue
        # 2) 物料精确
        if key in mat_by:
            m = pick_mat(mat_by[key])
            rec.update(kind="MATERIAL", match="exact", material_id=m.get("material_id"),
                       qm_code=m.get("qm_code"), master_name=m.get("name"))
            result[orig_key] = rec
            continue
        # 3) fuzzy：包含（双方长串互含，短侧≥2 字）
        cands = []
        for s in semi_items + mat_items:
            nm = norm(s.get("name", ""))
            if not nm:
                continue
            if (len(key) >= 2 and (key in nm or nm in key)) or \
               SequenceMatcher(None, key, nm).ratio() > 0.72:
                cands.append((nm, s))
        # 去重按名
        best = {}
        for nm, s in cands:
            best.setdefault(nm, s)
        cands = sorted(best.items(),
                       key=lambda x: max(len(key), len(x[0])) - abs(len(key) - len(x[0])),
                       reverse=True)[:3]
        if cands:
            nm, s = cands[0]
            kind = "SEMI" if s in semi_items else "MATERIAL"
            rec.update(kind=kind, match="fuzzy", master_name=nm)
            if kind == "SEMI":
                # DB 加载的半成品行字段：semi_id/code
                rec.update(semi_code=s.get("code"), semi_id=s.get("semi_id"))
            else:
                # DB 加载的物料行字段：material_id/qm_code（勿用 json 快照的 id/code）
                rec.update(material_id=s.get("material_id"), qm_code=s.get("qm_code"))
            rec["candidates"] = [c[0] for c in cands]
            result[orig_key] = rec
            low_conf.append((orig_key, rec))
            continue
        result[orig_key] = {"kind": "UNMATCHED", "match": "none",
                            "excel_examples": meta["excel_examples"]}
        unmatched.append((orig_key, meta["excel_examples"]))

    with io.open(os.path.join(OUT, "material_map.json"), "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)

    # 报告
    from collections import Counter
    kinds = Counter(v["kind"] for v in result.values())
    report = []
    report.append(f"原料归一总数={len(result)} kind={dict(kinds)}")
    report.append(f"行级覆盖（去重后）: SEMI+MATERIAL+SKIP+PACK vs UNMATCHED")
    report.append("\n## UNMATCHED（需人工补别名/映射，未映射行不参与消耗与成本）")
    for k, exs in sorted(unmatched):
        report.append(f"  {k}  例: {exs[:3]}")
    report.append("\n## LOW CONF（fuzzy 命中，需人工核对）")
    for k, rec in low_conf:
        report.append(f"  {k} → {rec['kind']} {rec.get('master_name')} (candidates={rec.get('candidates')})")
    report.append("\n## SEMI 命中清单（将按半成品配方爆炸）")
    for k, rec in sorted(result.items()):
        if rec.get("kind") == "SEMI":
            report.append(f"  {k} → {rec.get('semi_code')} {rec.get('master_name')} [{rec.get('match')}]")
    report.append("\n## MATERIAL 命中清单")
    for k, rec in sorted(result.items()):
        if rec.get("kind") == "MATERIAL":
            report.append(f"  {k} → {rec.get('qm_code')} {rec.get('master_name')} [{rec.get('match')}]")
    txt = "\n".join(report)
    with io.open(os.path.join(OUT, "norm_report.txt"), "w", encoding="utf-8") as f:
        f.write(txt)
    print(txt[:4000])


if __name__ == "__main__":
    main()
