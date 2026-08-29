"""
匹配企迈门店 ↔ 本地 store_info
1. 导出本地 store_info 数据
2. 编码精确匹配: store_info.store_code ↔ Qmai code
3. 名称模糊匹配: 剩余未匹配的做名称相似度匹配
4. 输出匹配结果文件 + SQL 脚本
"""
import json
import re
import pymysql
from difflib import SequenceMatcher

# ============ 数据库连接 ============
DB_CONFIG = {
    'host': '162.14.122.80',
    'port': 3306,
    'user': 'store_inventory',
    'password': 'Xzcpc@2026',
    'database': 'store_inventory',
    'charset': 'utf8mb4',
}

# ============ 1. 加载企迈门店 ============
with open('tmp_qmai_all_stores.json', 'r', encoding='utf-8') as f:
    qmai_stores = json.load(f)

print(f"企迈门店总数: {len(qmai_stores)}")

# 建立编码索引: code -> qmai store
qmai_by_code = {}
qmai_no_code = []
for s in qmai_stores:
    code = s.get('code', '').strip()
    if code:
        qmai_by_code[code] = s
    else:
        qmai_no_code.append(s)

print(f"  有编码: {len(qmai_by_code)} 家")
print(f"  无编码: {len(qmai_no_code)} 家")

# ============ 2. 查询本地门店 ============
conn = pymysql.connect(**DB_CONFIG)
cursor = conn.cursor()
cursor.execute("""
    SELECT id, store_id, store_name, store_code, xiaochengxuid, cangkuid, owner_name
    FROM store_info
    WHERE del_flag = 0
    ORDER BY id
""")
local_stores = [dict(zip([col[0] for col in cursor.description], row)) for row in cursor.fetchall()]
cursor.close()
conn.close()

print(f"\n本地门店总数: {len(local_stores)}")

# ============ 3. 编码精确匹配 ============
matched = []       # (local_store, qmai_store, match_type)
unmatched_local = []
local_code_set = set()

for ls in local_stores:
    code = (ls.get('store_code') or '').strip()
    ls['_codes'] = set()
    if code:
        ls['_codes'].add(code)
        local_code_set.add(code)

    qmai = qmai_by_code.get(code) if code else None
    if qmai:
        matched.append((ls, qmai, 'code'))
    else:
        unmatched_local.append(ls)

print(f"\n编码精确匹配: {len(matched)} 家")

# ============ 4. 名称模糊匹配 ============
def clean_name(name):
    """清洗门店名称，去除通用修饰以便比较"""
    if not name:
        return ''
    # 去除常见前缀后缀
    name = name.strip()
    name = name.replace('象子茶铺茶', '')
    name = name.replace('象子茶铺', '')
    name = name.replace('（', '(').replace('）', ')')
    return name.strip()

def name_similarity(a, b):
    """计算名称相似度"""
    ca = clean_name(a)
    cb = clean_name(b)
    return SequenceMatcher(None, ca, cb).ratio()

# 剩余未匹配的企迈门店
used_qmai_ids = {m[1]['id'] for m in matched}
remaining_qmai = [s for s in qmai_stores if s['id'] not in used_qmai_ids]

print(f"剩余未匹配本地门店: {len(unmatched_local)}")
print(f"剩余未匹配企迈门店: {len(remaining_qmai)}")

name_matched = []
still_unmatched_local = []

for ls in unmatched_local:
    local_name = ls.get('store_name', '')
    best_score = 0
    best_qmai = None

    for qs in remaining_qmai:
        score = name_similarity(local_name, qs.get('name', ''))
        if score > best_score:
            best_score = score
            best_qmai = qs

    if best_score >= 0.55 and best_qmai:
        name_matched.append((ls, best_qmai, f'name({best_score:.2f})'))
        remaining_qmai = [s for s in remaining_qmai if s['id'] != best_qmai['id']]
    else:
        still_unmatched_local.append(ls)

print(f"名称模糊匹配: {len(name_matched)} 家 (阈值>=0.55)")

# ============ 5. 输出结果 ============
all_matched = matched + name_matched

print(f"\n=== 匹配汇总 ===")
print(f"编码匹配: {len(matched)}")
print(f"名称匹配: {len(name_matched)}")
print(f"总匹配:   {len(all_matched)}")
print(f"本地未匹配: {len(still_unmatched_local)}")
print(f"企迈未匹配: {len(remaining_qmai)}")

# 输出详细匹配结果 JSON
match_result = []
for ls, qs, mtype in all_matched:
    match_result.append({
        'local_id': ls['id'],
        'local_store_id': ls['store_id'],
        'local_name': ls['store_name'],
        'local_code': ls.get('store_code') or '',
        'local_cangkuid': ls.get('cangkuid') or '',
        'qmai_id': qs['id'],
        'qmai_code': qs.get('code', ''),
        'qmai_name': qs['name'],
        'qmai_status': '正式营业' if qs.get('managerStatus') == 1 else ('待开业' if qs.get('managerStatus') == 0 else str(qs.get('managerStatus'))),
        'match_type': mtype,
    })

with open('tmp_store_match_result.json', 'w', encoding='utf-8') as f:
    json.dump(match_result, f, ensure_ascii=False, indent=2)

# 输出未匹配的
unmatched_export = {
    'local': [{'id': s['id'], 'store_id': s['store_id'], 'name': s['store_name'],
               'code': s.get('store_code') or '', 'cangkuid': s.get('cangkuid') or ''}
              for s in still_unmatched_local],
    'qmai': [{'id': s['id'], 'code': s.get('code', ''), 'name': s['name'],
              'status': '正式营业' if s.get('managerStatus') == 1 else '待开业'}
             for s in remaining_qmai],
}
with open('tmp_store_unmatched.json', 'w', encoding='utf-8') as f:
    json.dump(unmatched_export, f, ensure_ascii=False, indent=2)

# ============ 6. 生成 SQL ============
sql_lines = []
sql_lines.append("-- ============================================================")
sql_lines.append("-- 企迈门店ID 写入 store_info 表")
sql_lines.append("-- 生成时间: 2026-08-11")
sql_lines.append("-- 匹配逻辑: store_code ↔ Qmai code 精确匹配 + 名称模糊匹配")
sql_lines.append("-- ============================================================")
sql_lines.append("")
sql_lines.append("-- Step 1: 添加 qmai_store_id 字段（如果不存在）")
sql_lines.append("ALTER TABLE store_info ADD COLUMN qmai_store_id BIGINT DEFAULT NULL COMMENT '企迈门店ID' AFTER cangkuid;")
sql_lines.append("")

sql_lines.append("-- Step 2: 更新企迈门店ID")
sql_lines.append("-- 编码精确匹配部分")
code_matched = [m for m in all_matched if m[2] == 'code']
for ls, qs, mtype in code_matched:
    sql_lines.append(f"UPDATE store_info SET qmai_store_id = {qs['id']} WHERE id = {ls['id']};  -- {ls['store_name']} → {qs['name']} (code: {qs.get('code','')})")

sql_lines.append("")
sql_lines.append("-- 名称模糊匹配部分（请人工核实）")
for ls, qs, mtype in name_matched:
    sql_lines.append(f"UPDATE store_info SET qmai_store_id = {qs['id']} WHERE id = {ls['id']};  -- {ls['store_name']} → {qs['name']} (相似度: {mtype})")

with open('tmp_qmai_store_sync.sql', 'w', encoding='utf-8') as f:
    f.write('\n'.join(sql_lines) + '\n')

# ============ 7. 打印预览 ============
print("\n" + "=" * 80)
print("匹配结果预览（前20条）:")
print(f"{'本地ID':<6} {'本地编码':<8} {'本地名称':<24} {'企迈ID':<8} {'企迈编码':<8} {'企迈名称':<24} {'匹配':<12}")
print("-" * 80)
for r in match_result[:20]:
    print(f"{r['local_id']:<6} {r['local_code']:<8} {r['local_name']:<24} {r['qmai_id']:<8} {r['qmai_code']:<8} {r['qmai_name']:<24} {r['match_type']:<12}")

if len(match_result) > 20:
    print(f"... 共 {len(match_result)} 条，完整结果见 tmp_store_match_result.json")

print(f"\n未匹配本地门店 ({len(still_unmatched_local)}):")
for s in still_unmatched_local:
    print(f"  ID={s['id']}  {s['store_name']}  code={s.get('store_code') or '(无)'}")

print(f"\n未匹配企迈门店 ({len(remaining_qmai)}):")
for s in remaining_qmai:
    status = '正式营业' if s.get('managerStatus') == 1 else '待开业'
    print(f"  ID={s['id']}  {s.get('code','(无)')}  {s['name']}  [{status}]")

print(f"\n输出文件:")
print(f"  tmp_store_match_result.json  - 匹配结果")
print(f"  tmp_store_unmatched.json     - 未匹配项")
print(f"  tmp_qmai_store_sync.sql      - SQL 更新脚本")
