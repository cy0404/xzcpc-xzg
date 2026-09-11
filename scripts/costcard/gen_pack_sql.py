# -*- coding: utf-8 -*-
"""
生成「成本卡包材/耗材明细」迁移 SQL（只生成脚本，不连库执行）
=================================================================
来源：C:\\Users\\xiemg\\Desktop\\1\\直营门店云边乌龙sop2026.8.29.xlsx
      工作表「产品出品包装标准」（表头：外卖、堂食封口后统一加盖 除使用半球盖、pet杯）
规则：98连体盖 → 95（95PET杯+盖 WP0327，两者盘点单价同为 0.50）
      98PET杯+盖 是迭代品、不能计入消耗 → 卡里一律指 95（PACK 行本身也不产消耗）
      封口膜计入；胖胖杯与胖胖盖为一体只记一行；堂食/外卖二选一时取「外卖」
输出：database/migration-cost-card-pack-20260911.sql
"""
import sys, io, os, datetime
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "database", "migration-cost-card-pack-20260911.sql")

# ---------- 包材/耗材主数据（key → 物料名/qm/material_id/单位/盘点单价）----------
# 连体盖用 95 还是 98：98PET杯+盖（MN7KNEIU81）是 2026-09-04 建的手工物料，
# 无企迈编码、定位「迭代品类」新品，盘点单价与 95 完全相同（0.50）→ 两者成本等价。
# 用户在本次需求中要求「98连体盖改成95」，故 USE_95 = True（指向 WP0327，有企迈编码）。
# 若要改回指向 98：USE_95 = False 即可——成本完全不变，只有 material_id/qm_code 变。
# 注意 98PET杯+盖 无 qm_code，JOIN 走 MID 里的 material_id。
USE_95 = True

M = {
    "PET95":  ("95PET杯+盖", "WP0327", "个", 0.5000),
    "PET98":  ("98PET杯+盖", "", "个", 0.5000),   # 无企迈编码，mid 见 MID
    "ZSB500": ("注塑杯(500)", "WP0314", "个", 0.4400),
    "NPZ":    ("牛皮纸杯", "WP0302", "个", 0.4400),
    "FK":     ("封口膜", "WP0310", "张", 0.0500),
    "YTG":    ("白色一体盖", "WP0675", "个", 0.2200),
    "BQG":    ("半球杯盖", "WP0312", "个", 0.1500),
    "PP500":  ("PP500粗吸管", "WP0319", "根", 0.0750),
    "PP700C": ("PP700粗吸管", "WP0313", "支", 0.1000),
    "PP700X": ("PP700细吸管（新）", "WP0869", "根", 0.0500),
    "PLA3":   ("（新）PLA三孔鲜奶吸管", "WP0768", "根", 0.0750),
    "PPB":    ("胖胖瓶", "WP0651", "个", 1.2000),
    # SOP 的「木勺子」= 小料勺 WP0304（一级分类「包材成本/吸管类」，随杯出；
    # 其余带「勺」的物料都在「辅材成本/器具、设备类」，是工具不是耗材）
    "SPOON":  ("小料勺", "WP0304", "个", 0.1900),
}
# 无企迈编码的手工物料：key → material_id（JOIN 时按 material_id 命中）
MID = {"PET98": "MN7KNEIU81"}
# 连体盖用哪支物料（成本等价，仅指向不同）
LID = "PET95" if USE_95 else "PET98"
# SOP 写「封口+白色一体盖」，封口膜 WP0310 是真实存在的耗材（0.05/张）。
# 但原 Excel 的整包金额**没算封口膜**（0.735 = 牛皮纸杯0.44+一体盖0.22+吸管0.075）。
# True  = 按 SOP 计入 → 26 张卡变贵（22 张 +0.05、白毫茉莉 3 张 +0.03、奶昔/牛油果 2 张 +0.095）
# False = 跟随原 Excel 口径 → 除奶昔/牛油果(+0.045)外基本与原值持平
INCLUDE_FILM = True


def _cup(*keys):
    return [(k, lbl) for k, lbl in keys if not (k == "FK" and not INCLUDE_FILM)]


# 杯+盖组合（label = SOP 原文出处）
CUP_PET        = [(LID, "98连体盖(改95)" if USE_95 else "98连体盖")]
CUP_PAPER      = _cup(("NPZ", "双层牛皮纸杯"), ("FK", "封口"), ("YTG", "白色一体盖"))
CUP_INJ        = _cup(("ZSB500", "500注塑杯"), ("FK", "封口"), ("YTG", "白色一体盖"))
CUP_FAT        = [("PPB", "胖胖杯(含盖一体)")]
CUP_PAPER_DINE = [("NPZ", "双层牛皮纸杯"), ("BQG", "半球盖")]
CUP_INJ_DINE   = [("ZSB500", "500注塑杯"), ("BQG", "半球盖")]

# ---------- SOP → 成本卡映射 ----------
COLD = ["normal_ice", "less_ice", "least_ice", "no_ice", "ice", "default"]
HOT = ["warm", "hot", "room_temp"]
ALL = COLD + HOT

# (cost_product.product_name, 适用 temp_key, 杯盖组合, 吸管key)
SPEC = [
    # 块1
    ("手炒黑糖珍珠", ["least_ice", "no_ice", "normal_ice"], CUP_PET, "PP500"),
    ("手炒黑糖珍珠", ["warm"], CUP_PAPER, "PP500"),
    ("鲜奶黑糖啵啵", ["least_ice", "normal_ice"], CUP_PET, "PP500"),
    ("鲜奶黑糖啵啵", ["room_temp", "warm"], CUP_PAPER, "PP500"),
    ("手作米布鲜奶茶", ALL, CUP_PAPER, "PP500"),
    ("酸奶紫米露", ALL, CUP_PAPER, "PP500"),
    ("纸杯白毫茉莉", ALL, CUP_PAPER, "PP700X"),
    # 块2 舂柠檬家族：pet杯 + 700细 + 98连体盖
    ("树番茄舂柠檬", ALL, CUP_PET, "PP700X"),
    ("树番茄话梅·德宏", ALL, CUP_PET, "PP700X"),
    ("茉莉舂柠檬", ALL, CUP_PET, "PP700X"),
    ("滇橄榄舂柠檬", ALL, CUP_PET, "PP700X"),
    ("滇红舂柠檬", ALL, CUP_PET, "PP700X"),
    ("青柚滇橄榄", ALL, CUP_PET, "PP700X"),
    # 块3
    ("牛油果酸奶巴旦木", ALL, CUP_INJ, "PP500"),
    ("茉莉奶白", ALL, CUP_PAPER, "PLA3"),
    ("新10月黄油红糖(等普洱)", ALL, CUP_PAPER, "PLA3"),
    ("山野滇红(加雪顶)", ALL, CUP_PAPER, "PLA3", ["SPOON"]),   # 三孔细吸管+木勺子
    ("山野滇红(去雪顶)", ALL, CUP_PAPER, "PLA3", ["SPOON"]),   # 三孔细吸管+木勺子
    ("杨枝甘露双倍果肉", ALL, CUP_INJ, "PP500"),
    # 块4
    ("牛油果燕麦椰椰", ALL, CUP_PET, "PP500"),
    ("牛油果碧根果", ALL, CUP_FAT, "PP500"),
    ("厚牛油果酸奶巴旦木(奶昔)", ALL, CUP_INJ, "PP500"),
    ("双倍牛油果蔓越莓", ALL, CUP_FAT, "PP500"),
    ("酸角话梅·版纳", ALL, CUP_PET, "PP700X"),
    # 块5
    ("清甜芭乐冰茶", ALL, CUP_PET, "PP700X"),
    ("清甜芭乐果奶", ALL, CUP_PET, "PLA3"),
    ("玫瑰奶白酒", ALL, CUP_PET, "PP500"),
    ("玫瑰奶白酒-热(无糖版)", ALL, CUP_PAPER, "PP500"),
    # 块6
    ("青柚与葡萄柚", ALL, CUP_PET, "PP500"),
    ("青柚乌龙冰茶", ALL, CUP_PET, "PLA3"),
]

# 堂食口径备选（只影响这三张卡）
DINE_ALT = [
    ("牛油果酸奶巴旦木", ALL, CUP_INJ_DINE, "PP700C"),
    ("山野滇红(加雪顶)", ALL, CUP_PAPER_DINE, "PLA3", ["SPOON"]),
    ("山野滇红(去雪顶)", ALL, CUP_PAPER_DINE, "PLA3", ["SPOON"]),
]

# SOP 未覆盖的成本卡（保持原「包材」整包行不动）
NOT_COVERED = ["人参果杏子", "卡美罗小黄姜", "时令荔枝·冰茶", "时令荔枝·冰酿", "杏福人参",
               "杏福人参果奶", "杨枝甘露(凯特芒版)", "杨枝甘露(新)双倍果肉", "焦糖玫瑰滇红",
               "牛油果杏福人参果", "胭脂果与葡萄·冰茶", "胭脂果与葡萄·酸奶冰浆", "释迦果·芭乐冰茶",
               "释迦果·芭乐奶酪", "释迦果·芭乐酸奶昔", "青芒南姜特调(特调)", "青芒李子冰茶"]


def rows_of(cup, straw, extras=()):
    """→ [(qm, mid, 物料名, 单位, 数量, 单价, SOP出处)]，顺序：杯/盖/封口 → 吸管 → 附加耗材。
    mid 非空表示该物料无企迈编码（手工物料），JOIN 走 material_id。"""
    def one(key, lbl):
        n, q, u, p = M[key]
        return (q, MID.get(key, ""), n, u, 1, p, lbl)
    out = [one(k, lbl) for k, lbl in cup]
    out.append(one(straw, "吸管"))
    out.extend(one(k, "耗材") for k in extras)
    return out


def select_rows(spec, with_ord=True):
    """每卡每行一条 SELECT，UNION ALL 成派生表 s。
    tkeys 必须是**单个**字符串字面量（逗号分隔无引号），供 FIND_IN_SET 用；
    早期版本拼成 'a','b' 会变成多列 → UNION ALL 列数不一致直接报 1222。
    spec 行 = (产品名, temp_key 列表, 杯盖组合, 吸管key[, 附加耗材key列表])
    """
    out = []
    for row in spec:
        pname, tks, cup, straw = row[:4]
        extras = row[4] if len(row) > 4 else ()
        tks_s = ",".join(tks)
        for idx, (qm, mid, mname, unit, qty, price, label) in enumerate(rows_of(cup, straw, extras), 1):
            tail = ", %d AS ord" % idx if with_ord else ""
            out.append("SELECT '%s' AS pname, '%s' AS tkeys, '%s' AS qm, '%s' AS mid, "
                       "'%s' AS mname, '%s' AS unit, %s AS qty, %s AS price%s, '%s' AS src"
                       % (pname, tks_s, qm, mid, mname, unit, qty, price, tail, label))
    return out


# 物料命中条件：有企迈编码按 qm_code，手工物料按 material_id
MAT_JOIN = ("JOIN material     m ON m.del_flag = 0\n"
            "                       AND ((s.qm <> '' AND m.qm_code = s.qm)"
            " OR (s.mid <> '' AND m.material_id = s.mid))")


def main():
    ts = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    products = sorted({s[0] for s in SPEC})
    L = []
    A = L.append
    A("-- " + "=" * 94)
    A("-- 成本卡包材/耗材明细（来源：直营门店云边乌龙sop2026.8.29.xlsx「产品出品包装标准」）")
    A("-- 生成时间：%s   生成器：scripts/costcard/gen_pack_sql.py" % ts)
    A("--")
    A("-- 【本脚本做什么】")
    A("--   把成本卡里原来那 1 行整包「包材」（只有金额、无物料）替换成 SOP 拆出来的逐项包材/耗材行，")
    A("--   item_type='PACK'。PACK 行只计入成本卡成本，不产生物料消耗（消耗计算跳过 PACK）。")
    A("--")
    A("-- 【口径决定 · 已确认】")
    A("--   1) 98连体盖 → 95：pet 杯产品一律用 95PET杯+盖 WP0327（盘点单价 0.50，与 98 同价）")
    A("--      原因（需求方确认）：98PET杯+盖 已是迭代品，不能计入消耗；卡里一律指 95。")
    A("--      本脚本全部行都是 item_type='PACK'，而消耗计算对 PACK 直接跳过")
    A("--      （ConsumeCalcServiceImpl 判 'PACK'.equals(itemType) → continue），")
    A("--      故 95/98 都不会进入 store_material_consume_daily，已只读核验：")
    A("--        · store_material_consume_daily 无任何「杯/盖/吸管/膜」物料")
    A("--        · cost_card_item / cost_addon 均无引用 MN7KNEIU81")
    A("--   2) 封口膜 %s（需求方确认「算的」）" % ("【已计入】" if INCLUDE_FILM else "【未计入】"))
    A("--      SOP 写「封口+白色一体盖」，原整包金额没算封口膜")
    A("--      （原 0.735 = 牛皮纸杯0.44 + 白色一体盖0.22 + 吸管0.075）")
    A("--      → 本脚本使 %d 张卡比原整包贵：22 张 +0.0500、纸杯白毫茉莉 3 张 +0.0300，"
      % (26 if INCLUDE_FILM else 2))
    A("--        厚牛油果酸奶巴旦木(奶昔)/牛油果酸奶巴旦木 2 张 +0.0950")
    A("--      改口径：把 gen_pack_sql.py 的 INCLUDE_FILM 置 False 重跑即可")
    A("--   3) 胖胖杯：SOP 写「胖胖杯+胖胖盖」，需求方确认「胖胖盖和胖胖杯是一体的」→ 只记 1 行，")
    A("--      用「胖胖瓶 WP0651」代（原整包 1.28 = 胖胖瓶1.20 + 500粗0.075 也是这么算的）")
    A("--   4) 堂食/外卖二选一 → 取「外卖」（表头明示外卖统一口径；外卖为出货主渠道）")
    A("--      受影响的卡：牛油果酸奶巴旦木（吸管 500粗 vs 堂食 700粗）、山野滇红(加/去雪顶)")
    A("--      堂食口径的等价 SELECT 见文件末尾「堂食口径备选」，替换 3) 里对应行即可")
    A("--")
    A("-- 【未覆盖】SOP 表里没有的 %d 个产品（季节/下架品），保持原整包「包材」行不动：" % len(NOT_COVERED))
    for i in range(0, len(NOT_COVERED), 6):
        A("--     " + "、".join(NOT_COVERED[i:i + 6]))
    A("--")
    A("--   5) 木勺子：需求方确认「木勺子是小料勺」→ 用「小料勺 WP0304」（0.19/个，")
    A("--      一级分类「包材成本/吸管类」，是随杯耗材；其余带「勺」的物料都在")
    A("--      「辅材成本/器具、设备类」，是工具，不计入包材）。")
    A("--      受影响：山野滇红(加雪顶)/山野滇红(去雪顶) 共 5 张卡，各 +0.1900")
    A("--")
    A("-- 【无剩余缺口】SOP 列出的包材/耗材均有对应物料主数据。")
    A("--")
    A("-- 【执行顺序】先跑 0) 的 dry-run 核对行数 → 1) 备份 → 2) 删除 → 3) 插入 → 4) 核对")
    A("-- 【执行前务必】python scripts/costcard/check_sql_syntax.py  把脚本逐条 PREPARE 解析一遍")
    A("-- 【回滚】见文件末尾「回滚」段")
    A("--")
    A("-- !! 2026-09-11 已在本脚本上出过一次生产事故，务必先读：")
    A("--   初版 2) DELETE 写成 `WHERE item_type='PACK' AND 第1组 OR 第2组 …`，")
    A("--   AND 优先级高于 OR ⇒ 第 2 组起不受 item_type 约束，")
    A("--   **删掉了 54 张卡的 297 行 RAW/SEMI 配方行**。")
    A("--   已修正为 `WHERE i.item_type='PACK' AND ( … )`；")
    A("--   被删的行由 database/restore-cost-card-raw-semi-20260911.sql 回放修复。")
    A("--   教训：多组 OR 条件必须整体加括号；交付前必须过 check_sql_syntax.py。")
    A("-- " + "=" * 94)
    A("")
    A("SET NAMES utf8mb4;")
    A("")

    prod_list = []
    for i in range(0, len(products), 4):
        pre = "  " if i == 0 else "   OR "
        prod_list.append(pre + "p.product_name IN (" + ", ".join("'%s'" % n for n in products[i:i + 4]) + ")")
    prods_block = "\n".join(prod_list)

    # 0) dry-run
    A("-- ---------- 0) 试算：预期插入行数（只读，先跑这条） ----------")
    A("-- 预期 = 各卡 (杯+盖项数 + 1根吸管) 之和")
    A("-- SELECT COUNT(*) AS will_insert FROM (")
    A("-- " + ("\n-- UNION ALL\n-- ".join(select_rows(SPEC, with_ord=False)).replace("\n", "\n")))
    A("-- ) s")
    A("-- JOIN cost_product p ON p.product_name = s.pname")
    A("-- JOIN cost_card    k ON k.product_id = p.id AND FIND_IN_SET(k.temp_key, s.tkeys) > 0")
    A("-- " + MAT_JOIN.replace("\n", "\n-- ") + ";")
    A("")

    # 1) 备份
    A("-- ---------- 1) 备份将被替换的整包行（回滚用） ----------")
    A("DROP TABLE IF EXISTS cost_card_item_pack_bak_20260911;")
    A("CREATE TABLE cost_card_item_pack_bak_20260911 AS")
    A("SELECT i.* FROM cost_card_item i")
    A("JOIN cost_card k    ON k.id = i.card_id")
    A("JOIN cost_product p ON p.id = k.product_id")
    A("WHERE i.item_type = 'PACK' AND (")
    A(prods_block)
    A(");")
    A("")
    A("-- ---------- 2) 删除将重做的整包「包材」行（仅限下列产品，其余产品不动） ----------")
    A("DELETE i FROM cost_card_item i")
    A("JOIN cost_card k    ON k.id = i.card_id")
    A("JOIN cost_product p ON p.id = k.product_id")
    A("-- 必须整体加括号：AND 优先级高于 OR，若写成 AND 第1组 OR 第2组…")
    A("-- 则第 2 组起的 OR 分支不受 item_type='PACK' 约束，会连 RAW/SEMI 配方行一起删掉")
    A("WHERE i.item_type = 'PACK' AND (")
    A(prods_block)
    A(");")
    A("")

    # 3) 插入
    A("-- ---------- 3) 插入逐项包材/耗材行 ----------")
    A("-- source 列保留 SOP 出处（双层牛皮纸杯/封口/白色一体盖/98连体盖(改95)/吸管/胖胖杯）")
    A("-- 单价为成本卡快照（= 物料盘点单价），可用 4.3 的 SQL 核对漂移")
    A("-- 注：不写 loss_rate（NOT NULL DEFAULT 0，显式传 NULL 会报 1048）；")
    A("--     created_at/updated_at 走 DEFAULT CURRENT_TIMESTAMP")
    A("INSERT INTO cost_card_item")
    A("  (card_id, item_type, excel_item_name, material_name, material_id, qm_code,")
    A("   quantity, unit, unit_price, amount, sort_no)")
    A("SELECT k.id, 'PACK', CONCAT(s.mname, '｜', s.src), s.mname, m.material_id, m.qm_code,")
    A("       s.qty, s.unit, s.price, ROUND(s.qty * s.price, 4),")
    A("       COALESCE((SELECT MAX(x.sort_no) FROM cost_card_item x")
    A("                 WHERE x.card_id = k.id AND x.item_type <> 'PACK'), 0) + s.ord")
    A("FROM (")
    A("  " + "\n  UNION ALL\n  ".join(select_rows(SPEC)))
    A(") s")
    A("JOIN cost_product p ON p.product_name = s.pname")
    A("JOIN cost_card    k ON k.product_id = p.id AND FIND_IN_SET(k.temp_key, s.tkeys) > 0")
    A(MAT_JOIN)
    A(";")
    A("")

    # 4) 核对
    A("-- " + "=" * 94)
    A("-- 4) 核对（执行后跑）")
    A("-- " + "=" * 94)
    A("-- 4.1 新增行数（应等于 0) 的 will_insert）")
    A("-- SELECT COUNT(*) AS inserted FROM cost_card_item WHERE item_type='PACK' AND material_id IS NOT NULL;")
    A("")
    A("-- 4.2 逐卡包材成本：新明细 vs 原整包金额（delta 即口径差）")
    A("-- SELECT p.product_name, k.temp_key,")
    A("--        ROUND(SUM(i.amount),4) AS pack_cost,")
    A("--        (SELECT ROUND(b.amount,4) FROM cost_card_item_pack_bak_20260911 b WHERE b.card_id=k.id LIMIT 1) AS old_lump,")
    A("--        ROUND(SUM(i.amount) - (SELECT b.amount FROM cost_card_item_pack_bak_20260911 b WHERE b.card_id=k.id LIMIT 1), 4) AS delta")
    A("-- FROM cost_card_item i")
    A("-- JOIN cost_card k    ON k.id = i.card_id")
    A("-- JOIN cost_product p ON p.id = k.product_id")
    A("-- WHERE i.item_type='PACK' AND i.material_id IS NOT NULL")
    A("-- GROUP BY p.product_name, k.temp_key ORDER BY delta DESC;")
    A("")
    A("-- 4.3 单价漂移核对：卡里快照单价 vs 现盘点单价（应返回空集）")
    A("-- SELECT p.product_name, k.temp_key, i.material_name, i.unit_price AS card_price, r.unit_price AS rule_price")
    A("-- FROM cost_card_item i")
    A("-- JOIN cost_card k    ON k.id = i.card_id")
    A("-- JOIN cost_product p ON p.id = k.product_id")
    A("-- LEFT JOIN material_inventory_rule r ON r.material_id = i.material_id AND r.del_flag = 0")
    A("-- WHERE i.item_type='PACK' AND i.material_id IS NOT NULL AND i.unit_price <> r.unit_price;")
    A("")
    A("-- 4.4 残留整包行（应只剩 SOP 未覆盖的 %d 个产品）" % len(NOT_COVERED))
    A("-- SELECT p.product_name, k.temp_key, i.amount FROM cost_card_item i")
    A("-- JOIN cost_card k    ON k.id = i.card_id")
    A("-- JOIN cost_product p ON p.id = k.product_id")
    A("-- WHERE i.item_type='PACK' AND i.material_id IS NULL ORDER BY p.product_name;")
    A("")
    A("-- " + "=" * 94)
    A("-- 回滚")
    A("-- " + "=" * 94)
    A("-- DELETE i FROM cost_card_item i")
    A("-- JOIN cost_card k    ON k.id = i.card_id")
    A("-- JOIN cost_product p ON p.id = k.product_id")
    A("-- WHERE i.item_type='PACK' AND i.material_id IS NOT NULL AND (")
    A("-- " + prods_block.replace("\n", "\n-- "))   # 回滚段同样整体注释，勿漏 -- 前缀
    A("-- );")
    A("-- INSERT INTO cost_card_item (card_id, item_type, excel_item_name, material_name, material_id,")
    A("--   qm_code, semi_code, quantity, unit, loss_rate, unit_price, amount, sort_no, created_at, updated_at)")
    A("-- SELECT card_id, item_type, excel_item_name, material_name, material_id,")
    A("--   qm_code, semi_code, quantity, unit, loss_rate, unit_price, amount, sort_no, created_at, updated_at")
    A("-- FROM cost_card_item_pack_bak_20260911;")
    A("")
    A("-- " + "=" * 94)
    A("-- 堂食口径备选：把 3) 里这三张卡的 SELECT 行替换成下面这段（其余不变）")
    A("--   差异：牛油果酸奶巴旦木 吸管 500粗→700粗；山野滇红 封口+一体盖→半球盖")
    A("-- " + "=" * 94)
    for ln in select_rows(DINE_ALT):
        A("-- " + ln)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with io.open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(L) + "\n")
    print("写入", OUT)
    print("覆盖产品 %d 个；未覆盖 %d 个" % (len(products), len(NOT_COVERED)))
    print("映射条目 %d 条 → 真实行数/卡数用脚本里 0) 的 dry-run 核对"
          % sum(len(rows_of(*r[2:])) for r in SPEC))


if __name__ == "__main__":
    main()
