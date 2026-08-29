# -*- coding: utf-8 -*-
"""只读检查：inbound_order 表在两个库的存在情况/结构/行数（不执行任何变更）"""
import pymysql

HOST = dict(host="162.14.122.80", port=3306, user="store_inventory",
            password="Xzcpc@2026")

for db in ("store_inventory", "store_inventory_test"):
    try:
        conn = pymysql.connect(**HOST, database=db, charset="utf8mb4")
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM information_schema.TABLES "
                    "WHERE TABLE_SCHEMA = '" + db + "' "
                    "AND TABLE_NAME IN ('inbound_order','inbound_order_item')")
        n = cur.fetchone()[0]
        print("库 %s: inbound_* 表存在 %d/2" % (db, n))
        if n:
            cur.execute("SELECT TABLE_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION) "
                        "FROM information_schema.COLUMNS "
                        "WHERE TABLE_SCHEMA = '" + db + "' "
                        "AND TABLE_NAME IN ('inbound_order','inbound_order_item') "
                        "GROUP BY TABLE_NAME")
            for t, cols in cur.fetchall():
                print("  %s 列: %s" % (t, cols))
            try:
                cur.execute("SELECT COUNT(*) FROM inbound_order")
                print("  inbound_order 行数: %d" % cur.fetchone()[0])
            except Exception as e:
                print("  行数查询失败: %s" % e)
            try:
                cur.execute("SELECT COUNT(*) FROM inbound_order_item")
                print("  inbound_order_item 行数: %d" % cur.fetchone()[0])
            except Exception as e:
                print("  行数查询失败: %s" % e)
        conn.close()
    except Exception as e:
        print("库 %s 连接/查询失败: %s" % (db, e))
