# -*- coding: utf-8 -*-
"""
P0 数据探针（只读）：dwd.order_item 语义定案，为成本卡消耗计算提供依据
======================================================================
目标（输出 tmp_probe_report.txt 供人工确认）：
  1. 数据覆盖：日期范围 / 各月抽样日行数（量级）/ 渠道分布 / 门店数
  2. 退款行：refund_type 取值、quantity 符号（负数或正数需手工冲减）、示例行
  3. order_status 取值与占比（定白名单）
  4. item_type / product_flag / is_combo / combo_name 分布
  5. 费用/杂项行特征：quantity=0 或 名称含 配送/包装/餐具/优惠/打包 的样例与占比
  6. 门店键形态：store_id / store_code distinct 样例；与 dim.store / dim.store_id_mapping 命中率
  7. preparation 高频词（前 40）
  8. quantity 分布 / sales_amount、cost_amount 覆盖度
  9. product_lib_name vs product_name_clean 差异样例
10. ETL 滞后：max(order_date) vs now

用法：python probe_order_item.py [days_back=10]
连接：企迈 PG tea_chain（119.45.162.160:5433）——只读 SELECT，不写库
"""
import sys
import io
import os
import time
import datetime as dt
import psycopg2

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

PG_HOST = "119.45.162.160"
PG_PORT = 5433
PG_DB = "tea_chain"

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tmp")
os.makedirs(OUT_DIR, exist_ok=True)
OUT = open(os.path.join(OUT_DIR, "tmp_probe_report.txt"), "w", encoding="utf-8")

def p(*args):
    print(*args, file=OUT, flush=True)
    print(*args, flush=True)

def q(pc, sql, timeout_s=100):
    """单查询执行，超时/失败不中断整体"""
    try:
        pc.execute(f"SET LOCAL statement_timeout = {timeout_s * 1000}")
        pc.execute(sql)
        return pc.fetchall()
    except Exception as e:
        p(f"  [SKIP] SQL 超时/失败: {type(e).__name__}: {e}")
        p(f"         SQL: {sql[:200]}")
        return None

def main():
    days_back = int(sys.argv[1]) if len(sys.argv) > 1 else 10
    today = dt.date.today()
    w_from = today - dt.timedelta(days=days_back)
    p(f"== probe_time={today} window_from={w_from} (近 {days_back} 天) ==")
    p(f"== 说明：* 单项失败会 SKIP 标注，不影响其余探测 ==")

    from _conn import PG
    pg = psycopg2.connect(**PG, connect_timeout=25)
    pc = pg.cursor()

    # 1) 范围与量级 -----------------------------------------------------
    p("\n## 1. 数据范围与量级")
    r = q(pc, "SELECT MIN(order_date), MAX(order_date), COUNT(*) FROM dwd.order_item", 150)
    if r: p("全表: min/max/rows =", r[0])
    p("各月抽样日行数（order_date IN 指定日）:")
    for d in ["2026-05-06", "2026-06-06", "2026-07-06", "2026-08-06", "2026-09-06"]:
        r = q(pc, f"SELECT COUNT(*), COUNT(DISTINCT store_code), COUNT(DISTINCT product_lib_name) FROM dwd.order_item WHERE order_date='{d}'", 100)
        if r: p(f"  {d}: rows={r[0][0]} stores={r[0][1]} products={r[0][2]}")
    # 窗口量级
    r = q(pc, f"SELECT COUNT(*), COUNT(DISTINCT store_code), COUNT(DISTINCT product_lib_name), "
              f"COUNT(DISTINCT order_date) FROM dwd.order_item WHERE order_date >= '{w_from}'::date", 150)
    if r: p(f"窗口({w_from}~today): rows={r[0][0]} stores={r[0][1]} products={r[0][2]} days={r[0][3]}")

    # 2) 渠道分布（窗口内）
    p("\n## 2. 渠道分布")
    r = q(pc, f"SELECT channel, COUNT(*), SUM(quantity) FROM dwd.order_item WHERE order_date >= '{w_from}'::date GROUP BY 1 ORDER BY 2 DESC", 100)
    if r:
        for row in r: p("  ", row)

    # 3) 退款语义 -------------------------------------------------------
    p("\n## 3. 退款行（refund_type + quantity 符号）")
    r = q(pc, f"SELECT refund_type, COUNT(*), SUM(quantity), COUNT(*) FILTER (WHERE quantity < 0) neg_cnt "
              f"FROM dwd.order_item WHERE order_date >= '{w_from}'::date AND refund_type IS NOT NULL GROUP BY 1", 100)
    if r:
        for row in r: p("  ", row)
    else:
        r = q(pc, f"SELECT COUNT(*), COUNT(refund_no), COUNT(*) FILTER (WHERE quantity < 0) FROM dwd.order_item "
                  f"WHERE order_date >= '{w_from}'::date", 100)
        if r: p("  全窗口: rows/refund_no 非空/负quantity行 =", r[0], "（refund_type 无值，退款可能走 refund_no/负 quantity）")
    r = q(pc, f"SELECT order_date, channel, product_lib_name, quantity, refund_type, refund_no, order_status "
              f"FROM dwd.order_item WHERE order_date >= '{w_from}'::date AND (refund_type IS NOT NULL OR quantity < 0) "
              f"ORDER BY order_date DESC LIMIT 15", 100)
    if r:
        p("  退款/负量示例（15 行）:")
        for row in r: p("   ", row)

    # 4) order_status / item_type / product_flag / combo
    p("\n## 4. 状态/类型分布")
    r = q(pc, f"SELECT order_status, COUNT(*) FROM dwd.order_item WHERE order_date >= '{w_from}'::date GROUP BY 1 ORDER BY 2 DESC", 100)
    if r:
        for row in r: p("  order_status:", row)
    r = q(pc, f"SELECT item_type, COUNT(*) FROM dwd.order_item WHERE order_date >= '{w_from}'::date GROUP BY 1 ORDER BY 2 DESC", 100)
    if r:
        for row in r: p("  item_type:", row)
    r = q(pc, f"SELECT product_flag, COUNT(*) FROM dwd.order_item WHERE order_date >= '{w_from}'::date GROUP BY 1 ORDER BY 2 DESC LIMIT 15", 100)
    if r:
        for row in r: p("  product_flag:", row)
    r = q(pc, f"SELECT is_combo, COUNT(*), COUNT(DISTINCT combo_name) FROM dwd.order_item "
              f"WHERE order_date >= '{w_from}'::date GROUP BY 1", 100)
    if r:
        for row in r: p("  is_combo:", row)

    # 5) 费用/杂项行 -----------------------------------------------
    p("\n## 5. 费用/杂项行特征（窗口内 quantity=0 或名称命中特征词）")
    r = q(pc, f"SELECT product_lib_name, COUNT(*), SUM(quantity) FROM dwd.order_item "
              f"WHERE order_date >= '{w_from}'::date AND quantity = 0 GROUP BY 1 ORDER BY 2 DESC LIMIT 15", 100)
    if r:
        p("  quantity=0 的行（多为配送费/包装费）:")
        for row in r: p("   ", row)
    r = q(pc, f"SELECT product_lib_name, COUNT(*) FROM dwd.order_item WHERE order_date >= '{w_from}'::date "
              f"AND (product_lib_name ~ '配送|包装|打包|餐具|优惠|满减|纸巾|杯套|购物袋|服务费') GROUP BY 1 ORDER BY 2 DESC LIMIT 15", 120)
    if r:
        p("  名称含费用特征词的明细:")
        for row in r: p("   ", row)
    r = q(pc, f"SELECT COUNT(*), COUNT(*) FILTER (WHERE product_lib_name ~ '配送|包装|打包|优惠|满减') FROM dwd.order_item "
              f"WHERE order_date >= '{w_from}'::date", 120)
    if r: p("  窗口总数 vs 名称特征词命中数:", r[0])

    # 6) 门店键形态与映射命中 -------------------------------
    p("\n## 6. 门店键形态")
    r = q(pc, f"SELECT store_id, store_code, COUNT(*) FROM dwd.order_item "
              f"WHERE order_date >= '{w_from}'::date GROUP BY 1, 2 ORDER BY 3 DESC LIMIT 15", 100)
    if r:
        p("  窗口 top 组合:")
        for row in r: p("   ", row)
    # 命中 dim.store
    r = q(pc, f"SELECT COUNT(DISTINCT t.store_code) total_sc, COUNT(DISTINCT CASE WHEN s.store_code IS NOT NULL THEN t.store_code END) hit_sc, "
              f"COUNT(DISTINCT t.store_id) total_sid, COUNT(DISTINCT CASE WHEN s.record_id = t.store_id OR s.store_code = t.store_code THEN t.store_id END) hit_sid "
              f"FROM (SELECT store_id, store_code FROM dwd.order_item WHERE order_date >= '{w_from}'::date GROUP BY 1,2) t "
              f"LEFT JOIN dim.store s ON s.store_code = t.store_code", 100)
    if r: p("  store_code→dim.store 命中率（窗口 distinct 门店）:", r[0])
    r = q(pc, f"SELECT COUNT(*) FROM dwd.order_item o JOIN dim.store s ON s.store_code = o.store_code "
              f"WHERE o.order_date >= '{w_from}'::date", 150)
    if r: p("  行级 join dim.store(on store_code) 命中:", r[0][0])
    # dim.store record_id/record_code 形态样例
    r = q(pc, "SELECT store_code, record_id, record_code, store_id FROM dim.store WHERE is_active ORDER BY open_date DESC LIMIT 8", 60)
    if r:
        p("  dim.store 样例(store_code/record_id/record_code/store_id):")
        for row in r: p("   ", row)
    r = q(pc, "SELECT COUNT(DISTINCT store_code), COUNT(*) FROM dim.store", 60)
    if r: p("  dim.store 总行数/去重 store_code:", r[0])
    r = q(pc, "SELECT source_system, COUNT(*), COUNT(store_code) FROM dim.store_id_mapping GROUP BY 1", 60)
    if r:
        p("  dim.store_id_mapping 按 source_system:")
        for row in r: p("   ", row)

    # 7) preparation 高频词 --------------------------------------
    p("\n## 7. preparation 高频组合（前 40，含 NULL）")
    r = q(pc, f"SELECT COALESCE(preparation, '(NULL)'), COUNT(*) FROM dwd.order_item "
              f"WHERE order_date >= '{w_from}'::date GROUP BY 1 ORDER BY 2 DESC LIMIT 40", 120)
    if r:
        for row in r: p("  ", row)

    # 8) quantity 与金额覆盖 -------------------------------------
    p("\n## 8. quantity / 金额覆盖")
    r = q(pc, f"SELECT MIN(quantity), MAX(quantity), PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY quantity) p90, "
              f"COUNT(*) FILTER (WHERE quantity > 5) gt5 FROM dwd.order_item WHERE order_date >= '{w_from}'::date", 100)
    if r: p("  quantity min/max/p90/ >5 行数:", r[0])
    r = q(pc, f"SELECT COUNT(*), COUNT(sales_amount), COUNT(cost_amount), COUNT(unit_price), COUNT(cup_size), COUNT(topping) "
              f"FROM dwd.order_item WHERE order_date >= '{w_from}'::date", 100)
    if r: p("  覆盖(rows/sales_amount/cost_amount/unit_price/cup_size/topping):", r[0])

    # 9) product_lib_name vs product_name_clean 差异
    p("\n## 9. lib_name vs clean 差异样例")
    r = q(pc, f"SELECT product_lib_name, product_name_clean, COUNT(*) FROM dwd.order_item "
              f"WHERE order_date >= '{w_from}'::date AND product_name_clean IS NOT NULL "
              f"AND product_name_clean <> product_lib_name GROUP BY 1, 2 ORDER BY 3 DESC LIMIT 12", 100)
    if r:
        for row in r: p("  ", row)
    r = q(pc, f"SELECT COUNT(DISTINCT product_lib_name), COUNT(DISTINCT product_name_clean) FROM dwd.order_item "
              f"WHERE order_date >= '{w_from}'::date", 100)
    if r: p("  窗口 distinct lib_name / clean:", r[0])

    # 10) ETL 滞后
    p("\n## 10. ETL 滞后")
    r = q(pc, "SELECT MAX(order_date) FROM dwd.order_item", 60)
    if r: p(f"  max(order_date)={r[0][0]} today={today} 滞后={ (today - r[0][0]).days } 天")
    r = q(pc, "SELECT MAX(etl_time), MAX(ods_updated_at) FROM dwd.order_item", 100)
    if r: p("  max(etl_time)/ods_updated_at:", r[0])

    pg.close()
    OUT.close()
    p("\n== 完成：报告见 tmp/tmp_probe_report.txt ==")

if __name__ == "__main__":
    main()
