# -*- coding: utf-8 -*-
"""
物料理论消耗报表（门店销售 × 成本卡 → store_material_consume_daily）
====================================================================
用途：把落表的消耗按物料/门店/日 三个视角导出 CSV，供人工核"准不准"。
数据口径：base_qty（折物料基础单位，下游同口径）；raw_qty 为配方原量(g/ml)。

用法：
  python report_consume.py 2026-09-01 2026-09-08
输出（scripts/costcard/output/ 下）：
  consume_by_material_<from>_<to>.csv   物料汇总：总量/日均/覆盖门店/覆盖天数/折算状态
  consume_by_store_material_<...>.csv   门店×物料（单店核对手算用）
  consume_by_day_material_<...>.csv     日×物料（Top 物料趋势）
控制台：Top 物料、匹配率、折算状态分布
"""
import sys
import io
import os
import csv
import datetime
import pymysql

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

from _conn import MY as CONN   # 连接参数见 _conn.py（含密码，不入库）
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "output")


def q(cur, sql, args=None):
    cur.execute(sql, args or ())
    return cur.fetchall()


def main():
    if len(sys.argv) < 3:
        print("用法: python report_consume.py <from yyyy-MM-dd> <to yyyy-MM-dd>")
        return
    d_from, d_to = sys.argv[1], sys.argv[2]
    if not os.path.isdir(OUT):
        os.makedirs(OUT)
    tag = "%s_%s" % (d_from.replace("-", ""), d_to.replace("-", ""))
    conn = pymysql.connect(**CONN)
    cur = conn.cursor()

    days = (datetime.date.fromisoformat(d_to) - datetime.date.fromisoformat(d_from)).days + 1

    # ---------- 0. 概览 ----------
    print("=" * 78)
    print("窗口 %s ~ %s（%d 天）" % (d_from, d_to, days))
    rows = q(cur, """SELECT COUNT(DISTINCT stat_date), COUNT(DISTINCT store_id),
                            COUNT(*), ROUND(SUM(qty),0)
                     FROM store_product_sales_daily WHERE stat_date BETWEEN %s AND %s""",
             (d_from, d_to))
    ds, nst, nrow, cups = rows[0]
    print("销售：%s 天 / %s 店 / %s 行 / 净杯数 %s" % (ds, nst, nrow, cups))

    # 杯数覆盖率（已算消耗的杯数占比）
    rows = q(cur, """SELECT matched_status, COUNT(*), ROUND(SUM(qty),0)
                     FROM store_product_sales_daily WHERE stat_date BETWEEN %s AND %s
                     GROUP BY matched_status""", (d_from, d_to))
    tot = sum(float(r[2] or 0) for r in rows)
    for st, cnt, qq in rows:
        label = {1: "已匹配卡", 0: "无产品卡", None: "未计算"}.get(st, str(st))
        print("  销售匹配 %-8s %6s 行 %10s 杯 (%5.1f%%)" %
              (label, cnt, qq, (float(qq or 0) / tot * 100) if tot else 0))

    rows = q(cur, """SELECT reason, COUNT(*), ROUND(SUM(qty),0) FROM cost_sales_unmatched
                     WHERE stat_date BETWEEN %s AND %s GROUP BY reason""", (d_from, d_to))
    print("未匹配归因：")
    for r in rows:
        print("  %-20s %6s 行 %10s 杯" % r)

    rows = q(cur, """SELECT convert_status, COUNT(*), ROUND(SUM(base_qty),1)
                     FROM store_material_consume_daily WHERE stat_date BETWEEN %s AND %s
                     GROUP BY convert_status""", (d_from, d_to))
    print("消耗折算状态：")
    for r in rows:
        print("  %-18s %6s 行 %18s" % r)

    # ---------- 1. 按物料汇总（同一物料 ml/g 双单位合并；base_qty 已折基础单位） ----------
    mat = q(cur, """
        SELECT c.material_id, MAX(c.material_name),
               COALESCE(MAX(c.base_unit), MAX(c.unit)) AS bunit,
               ROUND(SUM(c.base_qty),2)  AS base_total,
               ROUND(SUM(c.raw_qty),2)   AS raw_total,
               COUNT(DISTINCT c.store_id) AS stores,
               COUNT(DISTINCT c.stat_date) AS days,
               SUM(c.cup_qty) AS cups,
               GROUP_CONCAT(DISTINCT c.unit) AS src_units,
               GROUP_CONCAT(DISTINCT c.convert_status) AS sts
        FROM store_material_consume_daily c
        WHERE c.stat_date BETWEEN %s AND %s
        GROUP BY c.material_id
        ORDER BY base_total DESC""", (d_from, d_to))
    f1 = os.path.join(OUT, "consume_by_material_%s.csv" % tag)
    with io.open(f1, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["物料ID", "物料名", "基础单位", "消耗总量(base)", "日均", "配方原量(g/ml)",
                    "覆盖门店数", "覆盖天数", "对应杯数", "每百杯用量", "配方单位", "折算状态"])
        for mid, name, bunit, bt, rt, st, dy, cups_, units, sts in mat:
            bt = float(bt or 0)
            per100 = round(bt / float(cups_) * 100, 3) if cups_ else ""
            w.writerow([mid, name, bunit, bt, round(bt / days, 2), rt, st, dy, cups_, per100, units, sts])

    print("\n物料行数 %d（按 base_qty 降序），写入 %s" % (len(mat), os.path.basename(f1)))
    print("\nTop 25 物料（按消耗总量）：")
    print("  %-24s %-6s %14s %12s %6s %6s" % ("物料", "单位", "消耗总量", "日均", "门店", "天"))
    for mid, name, bunit, bt, rt, st, dy, cups_, units, sts in mat[:25]:
        print("  %-24s %-6s %14s %12s %6s %6s" %
              ((name or mid)[:22], bunit, bt, round(float(bt) / days, 1) if bt else "", st, dy))

    # 无卡产品（占比监控：这些杯数的消耗完全没算）
    um = q(cur, """SELECT product_name, SUM(qty) cq, COUNT(DISTINCT store_id)
                   FROM cost_sales_unmatched WHERE stat_date BETWEEN %s AND %s
                   GROUP BY product_name ORDER BY cq DESC LIMIT 25""", (d_from, d_to))
    print("\n无卡产品 Top 25（净杯数，未计入消耗）：")
    for name, cq, st in um:
        print("  %-30s %10s 杯  %3s 店" % (name[:28], cq, st))

    # ---------- 2. 门店×物料 ----------
    sm = q(cur, """
        SELECT c.store_id, s.store_name, c.material_id, MAX(c.material_name),
               COALESCE(MAX(c.base_unit), MAX(c.unit)),
               ROUND(SUM(c.base_qty),2), ROUND(SUM(c.raw_qty),2), COUNT(DISTINCT c.stat_date),
               SUM(c.cup_qty), GROUP_CONCAT(DISTINCT c.unit),
               GROUP_CONCAT(DISTINCT c.convert_status)
        FROM store_material_consume_daily c
        LEFT JOIN store_info s ON s.store_id = c.store_id
        WHERE c.stat_date BETWEEN %s AND %s
        GROUP BY c.store_id, s.store_name, c.material_id
        ORDER BY c.store_id, SUM(c.base_qty) DESC""", (d_from, d_to))
    f2 = os.path.join(OUT, "consume_by_store_material_%s.csv" % tag)
    with io.open(f2, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["门店ID", "门店名", "物料ID", "物料名", "基础单位", "消耗总量(base)",
                    "配方原量(g/ml)", "覆盖天数", "杯数", "配方单位", "折算状态"])
        w.writerows(sm)
    print("门店×物料 %d 行，写入 %s" % (len(sm), os.path.basename(f2)))

    # ---------- 3. 日×物料（Top 80 物料） ----------
    top_ids = [r[0] for r in mat[:80]]
    if top_ids:
        ph = ",".join(["%s"] * len(top_ids))
        dm = q(cur, """
            SELECT c.stat_date, c.material_id, MAX(c.material_name), c.unit,
                   ROUND(SUM(c.base_qty),2)
            FROM store_material_consume_daily c
            WHERE c.stat_date BETWEEN %s AND %s AND c.material_id IN (%s)
            GROUP BY c.stat_date, c.material_id, c.unit
            ORDER BY c.stat_date, SUM(c.base_qty) DESC""" % ("%s", "%s", ph),
            (d_from, d_to) + tuple(top_ids))
        f3 = os.path.join(OUT, "consume_by_day_material_%s.csv" % tag)
        with io.open(f3, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.writer(f)
            w.writerow(["日期", "物料ID", "物料名", "单位", "消耗量"])
            w.writerows(dm)
        print("日×物料 %d 行，写入 %s" % (len(dm), os.path.basename(f3)))

    conn.close()
    print("=" * 78)


if __name__ == "__main__":
    main()
