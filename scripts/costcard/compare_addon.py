# -*- coding: utf-8 -*-
"""
加料前后对比：baseline_no_addon/consume_by_material_*.csv vs 当前 output 同名文件
用法：python compare_addon.py 20260901_20260907
输出：每物料 加料前/加料后/增量/增幅%，按增量降序
"""
import sys
import io
import os
import csv

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "output")


def load(path):
    d = {}
    if not os.path.exists(path):
        return d
    for r in list(csv.reader(io.open(path, encoding="utf-8-sig")))[1:]:
        d[r[1]] = (float(r[3]) if r[3] else 0.0, r[2])
    return d


def main():
    tag = sys.argv[1] if len(sys.argv) > 1 else "20260901_20260907"
    f = "consume_by_material_%s.csv" % tag
    before = load(os.path.join(OUT, "baseline_no_addon", f))
    after = load(os.path.join(OUT, f))
    if not before or not after:
        print("缺文件：baseline_no_addon/%s 或 %s（先跑 report_consume.py）" % (f, f))
        return
    rows = []
    for name in set(before) | set(after):
        b = before.get(name, (0.0, ""))[0]
        a = after.get(name, (0.0, ""))[0]
        unit = (after.get(name) or before.get(name))[1]
        rows.append((name, unit, b, a, a - b, (a - b) / b * 100 if b else None))
    rows.sort(key=lambda r: -r[4])
    print("%-24s %-4s %14s %14s %12s %9s" % ("物料", "单位", "加料前(7天)", "加料后(7天)", "增量", "增幅"))
    print("-" * 88)
    for name, unit, b, a, d, p in rows:
        if abs(d) < 0.5 and p is None:
            continue
        print("%-24s %-4s %14.0f %14.0f %12.0f %8s" %
              (name[:22], unit, b, a, d, ("%.1f%%" % p) if p is not None else "新增"))
    print("-" * 88)
    tb = sum(r[2] for r in rows)
    ta = sum(r[3] for r in rows)
    print("物料数 %d → %d；总量的绝对量已含单位差异，仅供参考" % (
        len(before), len(after)))


if __name__ == "__main__":
    main()
