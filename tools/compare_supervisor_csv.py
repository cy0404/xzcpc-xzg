"""
对比 CSV（_督导_门店_区域映射.csv）与数据库 supervisor_store_access 的差异。
用法：python compare_supervisor_csv.py
输出：compare_result.sql（可直接执行的修正脚本） + 终端差异摘要
"""
import csv
import os

CSV_PATH = r"C:\Users\xiemg\Downloads\_督导_门店_区域映射.csv"
OUT_SQL = os.path.join(os.path.dirname(__file__), "..", "database", "compare_result.sql")

def main():
    # 1. 读取 CSV（GBK 编码）
    rows = []
    with open(CSV_PATH, "r", encoding="gbk") as f:
        reader = csv.DictReader(f)
        for r in reader:
            name = r["store_name"].strip()
            rid = r["record_id"].strip()
            sup = r["supervisor_name"].strip()
            region = r["region_name"].strip()
            if rid:  # 跳过空行
                rows.append({"store_name": name, "record_id": rid, "supervisor": sup, "region": region})

    print(f"CSV 共 {len(rows)} 条记录")

    # 2. 统计
    by_sup = {}
    for r in rows:
        sup = r["supervisor"] if r["supervisor"] else "(空)"
        by_sup.setdefault(sup, []).append(r)

    print("\n=== CSV 督导分布 ===")
    for sup, items in sorted(by_sup.items(), key=lambda x: -len(x[1])):
        print(f"  {sup}: {len(items)} 家")

    empty = [r for r in rows if not r["supervisor"]]
    if empty:
        print(f"\n⚠ 无督导门店: {len(empty)} 家")
        for r in empty:
            print(f"  {r['store_name']} | {r['record_id']} | 区域: {r['region']}")

    # 3. 生成 SQL：更新 store_info.supervisor_name
    sql_lines = []
    sql_lines.append("-- ============================================================")
    sql_lines.append("-- 自动生成：根据 _督导_门店_区域映射.csv 更新 store_info.supervisor_name")
    sql_lines.append(f"-- 生成时间：2026-08-11 | CSV 共 {len(rows)} 条")
    sql_lines.append("-- ============================================================")
    sql_lines.append("")

    for sup, items in sorted(by_sup.items(), key=lambda x: -len(x[1])):
        if sup == "(空)":
            continue
        sql_lines.append(f"-- 【{sup}】{len(items)} 家")
        for r in items:
            sql_lines.append(
                f"UPDATE store_info SET supervisor_name = '{sup}' "
                f"WHERE store_id = '{r['record_id']}';  -- {r['store_name']}（{r['region']}）"
            )
        sql_lines.append("")

    # 长水机场到达厅 - 无督导
    if empty:
        sql_lines.append("-- ⚠ 以下门店 CSV 中无督导，supervisor_name 设为 NULL")
        for r in empty:
            sql_lines.append(
                f"UPDATE store_info SET supervisor_name = NULL "
                f"WHERE store_id = '{r['record_id']}';  -- {r['store_name']}（{r['region']}）"
            )
        sql_lines.append("")

    # 4. 生成 supervisor_store_access 同步脚本（基于更新后的 store_info.supervisor_name）
    sql_lines.append("-- ============================================================")
    sql_lines.append("-- 重建 supervisor_store_access（基于 store_info.supervisor_name）")
    sql_lines.append("-- ============================================================")
    sql_lines.append("")
    sql_lines.append("-- 4.1 标记 CSV 中不存在的旧映射为删除")
    sql_lines.append("UPDATE supervisor_store_access SET del_flag = 1")
    sql_lines.append("WHERE store_id NOT IN (")
    for i, r in enumerate(rows):
        comma = "," if i < len(rows) - 1 else ""
        sql_lines.append(f"    '{r['record_id']}'{comma}")
    sql_lines.append(");")
    sql_lines.append("")

    sql_lines.append("-- 4.2 插入/更新映射（以 store_info.supervisor_name 为准）")
    sql_lines.append("INSERT INTO supervisor_store_access (open_id, admin_name, store_id, store_name)")
    sql_lines.append("SELECT ap.open_id, ap.name, s.store_id, s.store_name")
    sql_lines.append("FROM store_info s")
    sql_lines.append("JOIN admin_permission ap ON ap.name = s.supervisor_name AND ap.del_flag = 0")
    sql_lines.append("WHERE s.del_flag = 0 AND s.supervisor_name IS NOT NULL")
    sql_lines.append("  AND s.store_id IN (")
    for i, r in enumerate(rows):
        if r["supervisor"]:  # 只包含有督导的
            comma = "," if i < len(rows) - 1 else ""
            sql_lines.append(f"    '{r['record_id']}'{comma}")
    sql_lines.append("  )")
    sql_lines.append("ON DUPLICATE KEY UPDATE admin_name = VALUES(admin_name), store_name = VALUES(store_name), del_flag = 0;")
    sql_lines.append("")

    # 5. 诊断查询
    sql_lines.append("-- ============================================================")
    sql_lines.append("-- 诊断：执行后验证")
    sql_lines.append("-- ============================================================")
    sql_lines.append("")
    sql_lines.append("-- 5.1 各督导门店数（应与 CSV 一致）")
    sql_lines.append("SELECT s.supervisor_name AS 督导, COUNT(*) AS 门店数")
    sql_lines.append("FROM store_info s")
    sql_lines.append("WHERE s.del_flag = 0 AND s.supervisor_name IS NOT NULL")
    sql_lines.append("GROUP BY s.supervisor_name ORDER BY 门店数 DESC;")
    sql_lines.append("")
    sql_lines.append("-- 5.2 supervisor_store_access 各督导门店数")
    sql_lines.append("SELECT admin_name AS 督导, COUNT(*) AS 门店数")
    sql_lines.append("FROM supervisor_store_access WHERE del_flag = 0")
    sql_lines.append("GROUP BY admin_name ORDER BY 门店数 DESC;")
    sql_lines.append("")
    sql_lines.append("-- 5.3 CSV 有但 store_info 找不到的 record_id（如有结果需排查）")
    sql_lines.append("-- 请在数据库中执行以下检查（将 CSV 的 166 个 record_id 替换下面的占位）：")
    rid_list = "','".join(r['record_id'] for r in rows)
    sql_lines.append(f"-- WHERE store_id IN ('{rid_list}')")

    # 写入文件
    with open(OUT_SQL, "w", encoding="utf-8") as f:
        f.write("\n".join(sql_lines))

    print(f"\n✅ SQL 脚本已生成：{OUT_SQL}")
    print(f"   共 {len(sql_lines)} 行")

if __name__ == "__main__":
    main()
