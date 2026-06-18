import pandas as pd
import re

df = pd.read_excel(r'C:\Users\xiemg\Downloads\物料信息.xls')
cols = df.columns.tolist()
qm_col, name_col, spec_col, unit_col, price_col, convert_col = cols

def parse_conversions(text, spec=''):
    """解析换算规则"""
    if pd.isna(text) or not str(text).strip():
        return []
    s = str(text).strip()
    rules = []
    # Directly find all N unit = N unit patterns from the raw string
    # Unit: sequence of non-digit, non-=, non-comma, non-dot chars
    # Find all "N unit = Q unit" patterns — each rule anchored by =
    # Unit after left number: non-digit chars before =
    # Unit after right number: non-digit chars till next = or end
    for m in re.finditer(r'(\d+(?:\.\d+)?)\s*(\D+?)\s*=\s*(\d+(?:\.\d+)?)\s*(\D+?)(?=\s*\d+\D*=|$)', s):
        fu = re.sub(r'[^\w一-鿿]', '', m.group(2).strip())
        tu = re.sub(r'[^\w一-鿿]', '', m.group(4).strip())
        if fu and tu:
            rules.append({'fq': m.group(1), 'fu': fu, 'tq': m.group(3), 'tu': tu})
    # Remove duplicates preserving order
    seen = set()
    unique = []
    for r in rules:
        key = (r['fq'], r['fu'], r['tq'], r['tu'])
        if key not in seen:
            seen.add(key)
            unique.append(r)
    return unique

sql = []
sql.append("-- 物料规则同步 SQL — 来源: 物料信息.xls")
sql.append("-- 注意: 换算规则中的中文单位名可能因编码问题不准确，请审核后执行\n")
sql.append("START TRANSACTION;\n")

rule_count = 0
conv_count = 0
warnings = []

for idx, row in df.iterrows():
    qm = str(row[qm_col]).strip() if not pd.isna(row[qm_col]) else ''
    name = str(row[name_col]).strip() if not pd.isna(row[name_col]) else ''
    base_unit = str(row[unit_col]).strip() if not pd.isna(row[unit_col]) else ''
    price = row[price_col] if not pd.isna(row[price_col]) else None
    raw_conv = str(row[convert_col]) if not pd.isna(row[convert_col]) else ''
    conv = parse_conversions(raw_conv)

    if not qm or qm == 'nan': continue
    if not base_unit or base_unit == 'nan':
        warnings.append(f"SKIP {qm} {name}: no base_unit")
        continue

    # inventory_units
    units = [base_unit]
    for c in conv:
        if c['fu'] not in units: units.append(c['fu'])
        if c['tu'] not in units: units.append(c['tu'])
    inv_units = ','.join(units)
    price_str = f"{price:.6f}" if price is not None else 'NULL'

    mat_var = f"@mat_{qm.replace('-','_')}"

    sql.append(f"-- {name} (qm={qm})")
    sql.append(f"SET {mat_var} = (SELECT material_id FROM material WHERE qm_code = '{qm}');\n")

    sql.append(f"INSERT INTO material_inventory_rule (material_id, base_unit, inventory_units, unit_price)")
    sql.append(f"VALUES ({mat_var}, '{base_unit}', '{inv_units}', {price_str})")
    sql.append(f"ON DUPLICATE KEY UPDATE base_unit = '{base_unit}', inventory_units = '{inv_units}', unit_price = {price_str};\n")
    rule_count += 1

    if conv:
        sql.append(f"DELETE FROM material_conversion_rule WHERE rule_id = (SELECT rule_id FROM material_inventory_rule WHERE material_id = {mat_var});")
        for c in conv:
            sql.append(f"INSERT INTO material_conversion_rule (rule_id, from_quantity, from_unit, to_quantity, to_unit, conversion_type)")
            sql.append(f"SELECT rule_id, {c['fq']}, '{c['fu']}', {c['tq']}, '{c['tu']}', 'unit'")
            sql.append(f"FROM material_inventory_rule WHERE material_id = {mat_var};\n")
            conv_count += 1

# Task sync
sql.append("-- ========== 同步未提交任务快照 ==========")
sql.append("UPDATE task_zone_material tzm")
sql.append("JOIN task t ON t.id = tzm.task_id")
sql.append("JOIN material_inventory_rule r ON r.material_id = tzm.material_id")
sql.append("SET tzm.base_unit_snapshot = r.base_unit, tzm.unit_price_snapshot = r.unit_price")
sql.append("WHERE t.status IN ('not_started', 'in_progress');\n")

sql.append("UPDATE task_material_summary tms")
sql.append("JOIN task t ON t.id = tms.task_id")
sql.append("JOIN material_inventory_rule r ON r.material_id = tms.material_id")
sql.append("SET tms.unit_price_snapshot = r.unit_price")
sql.append("WHERE t.status IN ('not_started', 'in_progress');\n")

sql.append("-- ========== 同步模板和门店分区物料 ==========")
sql.append("UPDATE template_zone_material tzm2")
sql.append("JOIN material_inventory_rule r ON r.material_id = tzm2.material_id")
sql.append("SET tzm2.base_unit_snapshot = COALESCE(r.base_unit, tzm2.base_unit_snapshot),")
sql.append("    tzm2.inventory_units = COALESCE(r.inventory_units, tzm2.inventory_units);\n")

sql.append("UPDATE store_zone_material szm")
sql.append("JOIN material_inventory_rule r ON r.material_id = szm.material_id")
sql.append("SET szm.base_unit = COALESCE(r.base_unit, szm.base_unit),")
sql.append("    szm.inventory_units = COALESCE(r.inventory_units, szm.inventory_units);\n")

sql.append("COMMIT;")

out = r'C:\Users\xiemg\Documents\test\inventory-tool\sync_material_rules.sql'
with open(out, 'w', encoding='utf-8') as f:
    f.write('\n'.join(sql))

print(f"Generated: {out}")
print(f"Materials: {rule_count}, Conversions: {conv_count}")
if warnings:
    print(f"Warnings ({len(warnings)}):")
    for w in warnings[:10]:
        print(f"  {w}")
