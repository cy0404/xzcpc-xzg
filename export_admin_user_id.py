"""
导出生产库 admin_permission.user_id → 生成测试库可执行的同步 SQL。
用法：python export_admin_user_id.py
输出：database/sync-admin-permission-user-id-to-test.sql（仅更新 user_id 字段）
"""
import pymysql

DB_CONFIG = {
    'host': '162.14.122.80',
    'port': 3306,
    'user': 'store_inventory',
    'password': 'Xzcpc@2026',
    'database': 'store_inventory',
    'charset': 'utf8mb4',
}
TABLE = 'admin_permission'
OUT_SQL = 'database/sync-admin-permission-user-id-to-test.sql'

conn = pymysql.connect(**DB_CONFIG)
cursor = conn.cursor()

# 只导出 user_id 非空的行（id + user_id）
cursor.execute(
    f"SELECT id, user_id FROM {TABLE} "
    "WHERE user_id IS NOT NULL AND user_id != '' ORDER BY id")
rows = cursor.fetchall()


def esc(v):
    if v is None:
        return 'NULL'
    if isinstance(v, (int, float)):
        return str(v)
    s = str(v)
    return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'"


lines = []
lines.append("-- ============================================================")
lines.append(f"-- 生产库 {TABLE}.user_id 同步到测试库（自动生成）")
lines.append(f"-- 导出时间：2026-08-26 | 共 {len(rows)} 条（仅 user_id 非空的行）")
lines.append("-- 用法：直接在测试库执行；仅更新 user_id，其他字段不受影响")
lines.append("-- 用 UPDATE 而非 INSERT：避免 1364（字段 NOT NULL 无默认值）")
lines.append("-- ============================================================")
lines.append("")
for rid, uid in rows:
    lines.append(f"UPDATE {TABLE} SET `user_id` = {esc(uid)} WHERE `id` = {rid};")
lines.append("")
lines.append("-- 说明：按主键 id 逐行更新；id 在测试库中不存在的行不会报错，仅跳过")

with open(OUT_SQL, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')

print(f"已生成 {OUT_SQL}，共 {len(rows)} 条记录（仅 id + user_id）")
cursor.close()
conn.close()
