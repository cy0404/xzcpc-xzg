"""
诊断：为什么线上同步 matchedSupervisors=0 / unmatchedByCode=189
对比 接口(userId/code) vs 线上库 admin_permission.user_id / store_info.store_code
"""
import json
import urllib.request
import pymysql

DB_CONFIG = {
    'host': '162.14.122.80',
    'port': 3306,
    'user': 'store_inventory',
    'password': 'Xzcpc@2026',
    'database': 'store_inventory',
    'charset': 'utf8mb4',
}
API_URL = 'http://162.14.122.80:18088/api/external/stores'
API_KEY = 'xk-gJ2hK7nP0qT5wX9zC4mV8bN1sA6fR'

# 1. 拉接口
req = urllib.request.Request(API_URL, headers={'X-API-Key': API_KEY})
with urllib.request.urlopen(req, timeout=20) as resp:
    body = json.loads(resp.read().decode('utf-8'))
items = body.get('items', [])
print(f"接口门店数: {len(items)}")

print("\n=== 接口首条原始数据 ===")
print(json.dumps(items[0], ensure_ascii=False, indent=2)[:1500])

api_codes = set()
api_user_ids = {}
api_names = {}
for it in items:
    code = str(it.get('code') or '').strip()
    if code:
        api_codes.add(code)
    sup = it.get('supervisor') or {}
    if sup.get('userId'):
        api_user_ids[sup['userId']] = sup.get('displayName')
        api_names.setdefault(sup['userId'], sup.get('displayName'))

print(f"接口有code: {len(api_codes)}")
print(f"接口督导userId: {len(api_user_ids)}")
for uid, name in api_user_ids.items():
    print(f"    {uid}  {name}")

# 2. 查库
conn = pymysql.connect(**DB_CONFIG)
cursor = conn.cursor(pymysql.cursors.DictCursor)

cursor.execute("""
    SELECT id, name, open_id, user_id, del_flag
    FROM admin_permission ORDER BY id
""")
aps = cursor.fetchall()
print(f"\nadmin_permission 总数: {len(aps)}")
print(f"  user_id 非空: {sum(1 for a in aps if a['user_id'])}")
print(f"  open_id 非空: {sum(1 for a in aps if a['open_id'])}")
db_uid_map = {}
for a in aps:
    if a['user_id']:
        db_uid_map[a['user_id']] = a
print(f"  前 20 条: id/name/open_id/user_id")
for a in aps[:20]:
    print(f"    {a['id']} {a['name']:<6} {a['open_id'] or '-':<40} user_id={a['user_id'] or '-'}")

# 3. 督导匹配
print("\n=== 督导 userId 匹配 ===")
for uid, name in api_user_ids.items():
    hit = db_uid_map.get(uid)
    print(f"  {uid} {name:<6} → {'✓ ' + hit['name'] if hit else '✗ 库中无此 user_id'}")

# 4. 门店 code 匹配
cursor.execute("""
    SELECT store_id, store_name, store_code, xiaochengxuid, cangkuid, supervisor_name, del_flag
    FROM store_info ORDER BY id
""")
stores = cursor.fetchall()
print(f"\nstore_info 总数: {len(stores)}")
print(f"  del_flag=0: {sum(1 for s in stores if s['del_flag'] == 0)}")
print(f"  store_code 非空: {sum(1 for s in stores if s['store_code'])}")
print("\n=== store_info 前 10 条 ===")
for s in stores[:10]:
    print(f"  store_id={s['store_id']} code={s['store_code']} name={s['store_name'][:15]} 小程序号={s['xiaochengxuid']} 仓库={s['cangkuid']} 督导={s['supervisor_name']}")

# 督导 del_flag 检查
cursor.execute("SELECT name, user_id, del_flag FROM admin_permission WHERE user_id IS NOT NULL AND user_id != ''")
print("\n=== 有 user_id 的督导 del_flag ===")
for r in cursor.fetchall():
    print(f"  {r['name']:<8} user_id={r['user_id']} del_flag={r['del_flag']}")

db_code_set = {s['store_code'] for s in stores if s['store_code']}
inter = api_codes & db_code_set
print(f"\n接口code 与 store_info.store_code 交集: {len(inter)} / 接口 {len(api_codes)}")
if len(inter) != len(api_codes):
    print("  接口有、库没有的 code 样例（前10）:")
    for c in sorted(api_codes - db_code_set)[:10]:
        print(f"    {c}")
    print("  库有、接口没有的 code 样例（前5）:")
    for c in sorted(db_code_set - api_codes)[:5]:
        print(f"    {c}")
# 接口 id ↔ 本地 store_code（用户确认的对应关系）
api_ids = {str(it.get('id') or '').strip() for it in items if it.get('id')}
inter_code = api_ids & db_code_set
print(f"\n接口id 与 store_info.store_code 交集: {len(inter_code)} / 接口 {len(api_ids)}")
if api_ids - db_code_set:
    print("  接口有、库没有的 id（=新门店候选，前10）:")
    for c in sorted(api_ids - db_code_set)[:10]:
        print(f"    {c}")

# warehouseId 维度对一下（MDCK 开头，格式一致）
cursor.execute("SELECT store_id, warehouse_id FROM store_info WHERE del_flag = 0")
wh_rows = cursor.fetchall()
db_wh_set = {r['warehouse_id'] for r in wh_rows if r['warehouse_id']}
api_wh_set = {str(it.get('warehouseId') or '').strip() for it in items if it.get('warehouseId')}
inter_wh = api_wh_set & db_wh_set
print(f"\n接口warehouseId 与 store_info.warehouse_id 交集: {len(inter_wh)} / 接口 {len(api_wh_set)} / 库 {len(db_wh_set)}")
if len(inter_wh) != len(api_wh_set):
    print("  接口有、库没有的 warehouseId（前10）:")
    for w in sorted(api_wh_set - db_wh_set)[:10]:
        print(f"    {w}")

cursor.close()
conn.close()
