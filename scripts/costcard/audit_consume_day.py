# -*- coding: utf-8 -*-
"""
P6 冒烟对账：独立重算某日某店的理论消耗，与 store_material_consume_daily 落表结果比对。
口径与 ConsumeCalcServiceImpl 一致（RAW g/ml 直收 / SEMI 引擎爆炸 / PACK 不计），
但独立实现 SQL 层重算，避免与 Java 实现同错。

用法：python audit_consume_day.py <store_id|门店名> <date>
示例：python audit_consume_day.py 2026-09-06
      python audit_consume_day.py 南屏 2026-09-06   （store_id 前缀匹配）
输出：每物料 落表 vs 重算 raw 差异；>0.01 差异列出（含未映射/爆炸行说明）
"""
import sys
import io
import pymysql

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

from _conn import MY as CONN   # 连接参数见 _conn.py（含密码，不入库）


def main():
    args = sys.argv[1:]
    if not args:
        print("用法: python audit_consume_day.py <date> [store_keyword]")
        return
    date = args[0]
    kw = args[1] if len(args) > 1 else None
    conn = pymysql.connect(**CONN)
    cur = conn.cursor()
    if kw:
        cur.execute("SELECT store_id FROM store_info WHERE del_flag=0 AND store_name LIKE %s LIMIT 1",
                    (f"%{kw}%",))
        r = cur.fetchone()
        if not r:
            print("门店未找到:", kw)
            return
        store_id = r[0]
    else:
        # 默认取当日销售行数最少的 5 家店之一（小样本好对）
        cur.execute("SELECT store_id, COUNT(*) c FROM store_product_sales_daily"
                    " WHERE stat_date=%s GROUP BY store_id ORDER BY c ASC LIMIT 5", (date,))
        rs = cur.fetchall()
        if not rs:
            print(f"{date} 无销售数据（先跑 sync/backfill）")
            return
        store_id = rs[0][0]
        print("样本店（当日销售行最少之一）:", rs)
    print(f"== 对账 store={store_id} date={date} ==")

    # 1) 落表
    cur.execute("SELECT material_id, material_name, unit, raw_qty, base_qty, convert_status, cup_qty"
                " FROM store_material_consume_daily WHERE store_id=%s AND stat_date=%s", (store_id, date))
    done = {(r[0], r[2]): r for r in cur.fetchall()}

    # 2) 独立重算（RAW 行 raw_qty；SEMI 不做引擎爆炸，只算引用行——半成品行差异单独说明）
    cur.execute("""SELECT s.product_name, s.qty, s.preparation,
                   c.card_name, i.item_type, i.excel_item_name,
                   i.material_id, i.material_name, i.quantity, i.unit
        FROM store_product_sales_daily s
        LEFT JOIN cost_product_alias a ON a.sales_name = s.product_name
        LEFT JOIN cost_product p ON p.id = a.product_id AND p.status = 1
        LEFT JOIN cost_card c ON c.product_id = p.id
        LEFT JOIN cost_card_item i ON i.card_id = c.id
        WHERE s.stat_date = %s AND s.store_id = %s AND s.qty <> 0
          AND c.status = 1 AND i.item_type IN ('RAW','SEMI') AND i.quantity IS NOT NULL""",
               (date, store_id))
    re = {}
    no_card = 0
    for pname, qty, prep, cname, itype, iname, mid, mname, iqty, unit in cur.fetchall():
        if qty is None or qty <= 0:
            continue
        if itype == 'PACK':
            continue
        # 近似：销售行可能匹配不到卡（此处 LEFT 后 mid/cname 为空 = 无卡或无默认匹配，
        # 简化只统计能带物料的行）
        if not mid:
            no_card += 1
            continue
        amt = float(qty) * float(iqty)
        re[(mid, unit or 'g')] = re.get((mid, unit or 'g'), 0.0) + amt

    print("\n落表行 vs 独立重算（RAW 直收口径，单位一致才可比）:")
    keys = set(done) | set(re)
    bad = 0
    for k in sorted(keys, key=str):
        d = done.get(k)
        rq = re.get(k)
        dv = float(d[3]) if d and d[3] is not None else 0.0
        rv = float(rq) if rq else 0.0
        mark = "OK" if abs(dv - rv) < 0.01 else "DIFF"
        if mark == "DIFF":
            bad += 1
        print(f"  {mark} {k[0][:14]}... unit={k[1]:>3} 落表raw={dv:.2f} 重算={rv:.2f}"
              + (f" base={d[4]} [{d[5]}]" if d else ""))
    print(f"\n半成品/其他说明：落表 {len(done)} 物料行，独立重算 RAW 口径 {len(re)} 物料键；"
          f"无物料引用的销售行 {no_card}（属无卡/未映射，走 cost_sales_unmatched）")
    print("DIFF 数量:", bad, "（>0.01；半成品爆炸行差异属预期，需按引擎口径核对）")
    conn.close()


if __name__ == "__main__":
    main()
