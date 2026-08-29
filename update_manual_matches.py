import json

# 加载现有结果
matched = json.load(open("tmp_store_match_result.json", "r", encoding="utf-8"))
unmatched = json.load(open("tmp_store_unmatched.json", "r", encoding="utf-8"))

# 手动配对: (local_name_keyword, qmai_name_keyword)
manual_pairs = [
    ("测试门店", "测试门店"),
    ("象子茶铺茶广南店", "广南万象金街"),
    ("晋城区店", "晋城州街店"),
    ("毕节招商花园店", "毕节店"),
    ("长水机场店", "长水机场店"),
]

new_matched = []
for local_kw, qmai_kw in manual_pairs:
    local = None
    for l in unmatched["local"]:
        if local_kw in l["name"]:
            local = l
            break
    qmai = None
    for q in unmatched["qmai"]:
        if qmai_kw in q["name"]:
            qmai = q
            break
    if local and qmai:
        print(f"Match: {local['name']} -> {qmai['name']} (Qmai ID={qmai['id']})")
        new_matched.append({
            "local_id": local["id"],
            "local_store_id": local["store_id"],
            "local_name": local["name"],
            "local_code": local["code"],
            "local_cangkuid": local["cangkuid"],
            "qmai_id": qmai["id"],
            "qmai_code": qmai["code"],
            "qmai_name": qmai["name"],
            "qmai_status": qmai["status"],
            "match_type": "manual",
        })
        unmatched["local"] = [l for l in unmatched["local"] if l["id"] != local["id"]]
        unmatched["qmai"] = [q for q in unmatched["qmai"] if q["id"] != qmai["id"]]
    else:
        print(f"NOT FOUND: local={local_kw}({'OK' if local else 'MISS'}), qmai={qmai_kw}({'OK' if qmai else 'MISS'})")

# 合并
all_matched = matched + new_matched
with open("tmp_store_match_result.json", "w", encoding="utf-8") as f:
    json.dump(all_matched, f, ensure_ascii=False, indent=2)
with open("tmp_store_unmatched.json", "w", encoding="utf-8") as f:
    json.dump(unmatched, f, ensure_ascii=False, indent=2)

print(f"\nTotal: matched {len(all_matched)}, local unmatched {len(unmatched['local'])}, qmai unmatched {len(unmatched['qmai'])}")
print(f"Local unmatched: {[l['name'] for l in unmatched['local']]}")
print(f"Qmai unmatched: {[q['name'] for q in unmatched['qmai']]}")

# 重新生成SQL
code_cnt = sum(1 for m in all_matched if m["match_type"] == "code")
name_cnt = sum(1 for m in all_matched if m["match_type"].startswith("name"))
manual_cnt = sum(1 for m in all_matched if m["match_type"] == "manual")

sql = []
sql.append("-- ============================================================")
sql.append("-- Qmai store ID sync to store_info")
sql.append("-- Code match: " + str(code_cnt) + ", Name match: " + str(name_cnt) + ", Manual: " + str(manual_cnt) + ", Total: " + str(len(all_matched)))
sql.append("-- ============================================================")
sql.append("")
sql.append("-- Step 1: Add column")
sql.append("ALTER TABLE store_info ADD COLUMN qmai_store_id BIGINT DEFAULT NULL COMMENT 'Qmai store ID' AFTER cangkuid;")
sql.append("")

sql.append("-- Step 2: Update qmai_store_id")
for m in all_matched:
    note = m['local_name'] + " -> " + m['qmai_name']
    if m["match_type"].startswith("name"):
        note += " [" + m['match_type'] + "]"
    elif m["match_type"] == "manual":
        note += " [manual]"
    sql.append("UPDATE store_info SET qmai_store_id = " + str(m['qmai_id']) + " WHERE id = " + str(m['local_id']) + ";  -- " + note)

with open("tmp_qmai_store_sync.sql", "w", encoding="utf-8") as f:
    f.write("\n".join(sql) + "\n")

print("\nSQL regenerated: tmp_qmai_store_sync.sql")
