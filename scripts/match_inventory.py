#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
盘点数据补齐脚本
功能：将盘点数据汇总中的门店名称清洗，并补齐门店信息和物料信息

输入文件（3个）：
  1. 物料分类与规格清单.xlsx  — 物料主数据
  2. 象子茶铺门店信息表.xlsx  — 门店主数据
  3. 盘点数据汇总.xlsx        — 原始盘点数据（门店, 名称, 规格, 单价, 盘点数量）

输出：
  盘点数据汇总_已补齐.xlsx    — 补齐后的完整数据（新增8列：门店id, 小程序id, 门店名称,
                                物料ID, 企迈ID, 父级分类, 分类, 物料名, 物料规格）

用法：
  python match_inventory.py
  （运行后按提示输入3个文件的路径，或回车使用默认路径）
"""

import openpyxl
import re
import os
import sys
from collections import Counter

# ============================================================
# 配置：文件默认路径（修改为你的实际路径）
# ============================================================
DEFAULT_PATHS = {
    'material': r'C:\Users\xiemg\Documents\test\inventory-tool\scripts\物料分类与规格清单.xlsx',
    'store': r'C:\Users\xiemg\Downloads\象子茶铺门店信息表.xlsx',
    'inventory': r'C:\Users\xiemg\Downloads\5月盘点数据汇总 (1).xlsx',
}

# ============================================================
# 手动映射：门店名称（简称->全称）
# ============================================================
STORE_MANUAL_MAP = {
    '印象': '象子茶铺茶祥云印象花园店',
    '吾悦': '象子茶铺茶保山吾悦店',
    '大理古城': '象子茶铺茶大理古城店',
    '建水': '象子茶铺茶建水小桂湖店',
    '香格里拉': '象子茶铺茶香格里拉店',
    '芒市店': '象子茶铺茶芒市三棵树店',
    '弥勒-印象': '象子茶铺茶弥勒印象街店',
    '弥勒-金辰': '象子茶铺茶弥勒金辰店',
}

# ============================================================
# 手动映射：物料名称（简称->全称），名称不同但同一产品
# ============================================================
MATERIAL_MANUAL_MAP = {
    '500中杯': '注塑杯(500)',
    '500注塑杯': '注塑杯(500)',
    '玫瑰花瓣': '花瓣（原材料）',
    '纸巾（按件采购）': '定制纸巾',
    '纸巾（按份采购）': '定制纸巾',
    '双杯无纺布袋': '无纺布双杯袋',
    '速冻牛奶米布': '冷冻牛奶米布',
    '速冻白西柚粒': '速冻白西柚果粒',
    '速冻胭脂果浆': '冷冻胭脂果汁',
    '蓝色毛巾（器具）': '蓝色毛巾(蓝色)',
    '新单个打包袋-PLA': 'PLA单个打包袋',
    '小木勺（奶油专用）': '小木勺子(奶油专用)',
    '小木勺/木叉（奶油专用）': '小木勺子(奶油专用)',
    '普洱茶（新）': '普洱茶（新版）',
    '普洱茶(新)': '普洱茶（新版）',
    '速冻青芒汁': '速冻青芒果汁',
    'PP700细吸管-旧': 'PP700细吸管-（旧）',
    'PP700细吸管新': 'PP700细吸管（新）',
    'PP700细吸管-（新)': 'PP700细吸管（新）',
    'PP700细吸管-（新）': 'PP700细吸管（新）',
    '青芒果杯套': '青芒杯套',
    '青芒果香片': '青芒香片',
    '木薯粉': '木薯淀粉',
    '速冻芭乐浆': '速冻红芭乐果浆',
    '牛油果公仔': '牛油果限定公仔',
    '速冻西柚粒': '速冻白西柚果粒',
    '剥壳巴旦木1Kg': '剥壳巴旦木1公斤',
    '新三孔鲜奶吸管': '（新）PLA三孔鲜奶吸管',
    '普洱茶新': '普洱茶（新版）',
    '顾客喝水用纸杯': '纸杯（顾客喝水用）',
    'Pp700细新': 'PP700细吸管（新）',
    '清脆李': '青脆李',
    '双杯无纺布袋外卖': '无纺布双杯袋',
    '试饮纸杯.': '试饮杯',
    '玻璃水': '玻璃清洁剂',
    '去茶渍粉': '餐具浸泡去渍粉',
    '象子贴纸（周边）': '贴纸（新店用）',
    '小象贴纸': '贴纸（新店用）',
    '客人喝水纸杯': '纸杯（顾客喝水用）',
    '三孔管': '（新）PLA三孔鲜奶吸管',
    '新普洱茶': '普洱茶（新版）',
    '普洱新茶': '普洱茶（新版）',
    '胭脂果浆': '预制胭脂果葡萄浆',
    '单杯无纺布袋外卖': '无纺布单杯袋',
    '冷冻牛油果泥（250g）': '冷冻牛油果泥(24包/件)',
    'PP700细吸管-': 'PP700细吸管（新）',
    '冷冻胭脂果': '冷冻胭脂果汁',
    '夏黑葡萄': '葡萄',
    '香水柠檬': '柠檬',
    '纸杯（给顾客喝水用）': '纸杯（顾客喝水用）',
    '（新）三孔鲜奶吸管': '（新）PLA三孔鲜奶吸管',
    '冷冻青芒果汁': '速冻青芒果汁',
    '普洱茶（小包）': '普洱茶（新版）',
    '普洱茶·新': '普洱茶（旧版）',
    '三孔鲜奶吸管2.0': '（新）PLA三孔鲜奶吸管',
    '圆边防漏纸': '圆边防漏溢纸',
}

# ============================================================
# 多候选默认选择（一对多歧义时的默认匹配）
# ============================================================
MATERIAL_MULTI_DEFAULTS = {
    ('一次性胶手套', '50副/包'): '一次性胶手套(M码)',
    ('橙子', ''): '橙子',
    ('火龙果', ''): '火龙果',
    ('芒果', ''): '芒果',
    ('牛油果', ''): '牛油果鲜果',
    ('苦瓜', ''): '苦瓜（原材料）',
    ('释迦果', ''): '释迦果（原材料）',
    ('芭乐', ''): '红心芭乐',
    ('羽衣甘蓝', ''): '羽衣甘蓝（原料）',
    ('奇亚籽', '1kg/包'): '奇亚籽（原材料）',
    ('甜白酒', '750g/瓶'): '甜白酒（旧版）',
    ('甜白酒', '800g/瓶'): '甜白酒（旧版）',
    ('南姜', ''): '南姜',
    ('南姜', 'KG'): '南姜',
    ('珍珠', ''): '珍珠粉圆',
    ('葡萄', ''): '葡萄',
    ('滇橄榄', ''): '滇橄榄贴纸',
    ('橄榄', ''): '橄榄鲜果',
    ('700细吸管', ''): 'PP700细吸管（新）',
    ('青芒', ''): '速冻青芒果汁',
    # 直营店水果物料 (spec=kg)
    ('芒果', 'kg'): '芒果',
    ('羽衣甘蓝', 'kg'): '羽衣甘蓝（原料）',
    ('火龙果', 'kg'): '火龙果',
    ('牛油果', 'kg'): '牛油果鲜果',
    ('木瓜', 'kg'): '木瓜（原材料）',
    ('释迦果', 'kg'): '释迦果',
    ('芭乐', 'kg'): '红心芭乐',
    ('橙子', 'kg'): '橙子',
    ('南姜', 'kg'): '预制南姜汁',
    ('葡萄', 'kg'): '葡萄',
    ('滇橄榄', 'kg'): '橄榄鲜果',
    ('苦瓜', 'kg'): '苦瓜',
}

# ============================================================
# 工具函数
# ============================================================
def normalize_name(s):
    """标准化名称：统一中英文括号，去空格，去尾部点号"""
    if not s:
        return ''
    s = s.strip()
    s = s.replace('（', '(').replace('）', ')')
    s = s.replace('，', ',')
    s = re.sub(r'\s+', '', s)
    s = re.sub(r'\.$', '', s)
    return s


def extract_quantities(spec):
    """从规格字符串中提取所有数值"""
    if not spec or spec in ['/', '']:
        return set()
    s = spec.replace(' ', '')
    nums = re.findall(r'(\d+\.?\d*)', s)
    return set(float(n) for n in nums)


def clean_store_name(raw):
    """清洗门店名称：去除前缀、后缀、括号备注"""
    if not raw:
        return raw
    s = raw.strip()

    # 1. 去除括号备注（支持中英文混合括号）
    s = re.sub(r'[（(][^）)]*[）)]', '', s)

    # 2. 去除前缀
    s = re.sub(r'^\d+月财务\s+', '', s)
    s = re.sub(r'^\d+月(?=[^\s])', '', s)

    # 3. 去除后缀
    s = re.sub(r'\s+\d+月\s+ok\s*$', '', s)
    s = re.sub(r'\s*\d+月(?:份)?财务数据.*$', '', s)
    s = re.sub(r'\s+\d+\+?\d*月(?:份)?财务报表?\s*.*$', '', s)
    s = re.sub(r'\s+\d+月(?:份)?财务核算[.。]?\s*.*$', '', s)
    s = re.sub(r'\s+\d+月(?:份)?财务\s*.*$', '', s)
    s = re.sub(r'\s+\d+月\s+.*$', '', s)
    s = re.sub(r'\s+\d+月\s*$', '', s)
    s = re.sub(r'\s+\d+\+\d+月.*$', '', s)
    s = re.sub(r'\s+ok\s*$', '', s, flags=re.IGNORECASE)

    # 4. 合并空格
    s = re.sub(r'\s+', '', s)
    return s.strip()


def load_excel(path, sheet=None):
    """加载Excel，返回(workbook, worksheet, is_xls)"""
    try:
        wb = openpyxl.load_workbook(path)
        ws = wb[sheet] if sheet else wb.active
        return wb, ws, False
    except:
        import xlrd
        wb = xlrd.open_workbook(path)
        ws = wb.sheet_by_name(sheet) if sheet else wb.sheet_by_index(0)
        return wb, ws, True

def load_material_list(path):
    """加载物料信息表，返回物料列表和name->info映射"""
    mat_list = []
    mat_by_name = {}
    _, ws, is_xls = load_excel(path)

    if is_xls:
        # xlrd: 按位置读取。列序: 物料ID, 企迈ID, 父级分类, 分类, 物料名, 规格, 单位, 单价, 副盘点单位
        for r in range(1, ws.nrows):
            info = {
                '物料ID': str(ws.cell(r, 0).value or ''),
                '企迈ID': str(ws.cell(r, 3).value or ''),  # col 3 = 企迈ID
                '父级分类': str(ws.cell(r, 1).value or ''),  # col 1 = 父级分类
                '分类': str(ws.cell(r, 2).value or ''),       # col 2 = 分类
                '物料名': str(ws.cell(r, 4).value or ''),
                '规格': str(ws.cell(r, 5).value or ''),
            }
            if ws.ncols >= 9:
                info['单位'] = str(ws.cell(r, 6).value or '')
                info['单价'] = str(ws.cell(r, 7).value or '')
                info['副盘点单位'] = str(ws.cell(r, 8).value or '')
            mat_list.append(info)
            mat_by_name[info['物料名']] = info
    else:
        for r in range(2, ws.max_row + 1):
            info = {}
            for c in range(1, ws.max_column + 1):
                info[ws.cell(1, c).value] = str(ws.cell(r, c).value or '')
            mat_list.append(info)
            mat_by_name[info['物料名']] = info

    return mat_list, mat_by_name


def print_progress(current, total, label=''):
    """打印进度条"""
    pct = current / total * 100 if total else 0
    print(f'\r  {label} {pct:.1f}% ({current}/{total})', end='', flush=True)


# ============================================================
# Phase 1: 门店名称清洗
# ============================================================
def phase1_clean_stores(ws_inventory, store_col=1):
    """清洗门店列，返回{原始名称->清洗后名称}的映射"""
    store_map = {}
    for r in range(2, ws_inventory.max_row + 1):
        raw = str(ws_inventory.cell(r, store_col).value or '').strip()
        if raw and raw not in store_map:
            store_map[raw] = clean_store_name(raw)
    return store_map


# ============================================================
# Phase 2: 门店匹配
# ============================================================
def phase2_match_stores(store_map, ws_store_info):
    """匹配清洗后的门店名到门店信息表"""
    # 加载门店信息表
    store_info_list = []
    store_by_name = {}
    for r in range(2, ws_store_info.max_row + 1):
        sid = str(ws_store_info.cell(r, 1).value or '')
        mid = str(ws_store_info.cell(r, 2).value or '')
        name = str(ws_store_info.cell(r, 3).value or '').strip()
        if name:
            info = {'门店id': sid, '小程序id': mid, '门店名称': name}
            store_info_list.append(info)
            store_by_name[name] = info

    matched = {}    # 清洗名 -> store info
    unmatched = []  # 未匹配的清洗名
    multi = []      # 一对多

    for raw, cleaned in sorted(store_map.items()):
        # 先查手动映射
        if cleaned in STORE_MANUAL_MAP:
            full_name = STORE_MANUAL_MAP[cleaned]
            if full_name in store_by_name:
                matched[cleaned] = store_by_name[full_name]
                continue

        # 名称包含匹配（清洗名 包含于 全称）
        cleaned_norm = normalize_name(cleaned)
        candidates = []
        for info in store_info_list:
            full_norm = normalize_name(info['门店名称'])
            if cleaned_norm in full_norm:
                candidates.append(info)

        if len(candidates) == 1:
            matched[cleaned] = candidates[0]
        elif len(candidates) > 1:
            multi.append((cleaned, candidates))
        else:
            unmatched.append(cleaned)

    return matched, unmatched, multi


# ============================================================
# Phase 3: 物料匹配
# ============================================================
def find_col_index(ws, keyword):
    """根据表头关键词查找列号（1-based）"""
    for c in range(1, ws.max_column + 1):
        h = str(ws.cell(1, c).value or '')
        if keyword in h:
            return c
    return None

def phase3_match_materials(ws_inventory, name_col, spec_col, material_path):
    """匹配物料：先名+规，再纯名，再手动映射"""
    # 加载物料信息表
    mat_list, mat_by_name = load_material_list(material_path)

    # 收集唯一(物料名, 规格)对
    pairs = {}
    for r in range(2, ws_inventory.max_row + 1):
        name = str(ws_inventory.cell(r, name_col).value or '').strip()
        spec = str(ws_inventory.cell(r, spec_col).value or '').strip()
        if name:
            key = (name, spec)
            pairs[key] = pairs.get(key, 0) + 1

    # Step 1: 先检查手动映射
    manual_matched = {}
    for (name, spec), cnt in pairs.items():
        if name in MATERIAL_MANUAL_MAP:
            f2_name = MATERIAL_MANUAL_MAP[name]
            if f2_name in mat_by_name:
                manual_matched[(name, spec)] = mat_by_name[f2_name]

    # Step 2: 名称+规格匹配
    name_spec_matched = {}
    for (name, spec), cnt in pairs.items():
        if (name, spec) in manual_matched:
            continue
        info = _match_by_name_and_spec(name, spec, mat_list)
        if info:
            name_spec_matched[(name, spec)] = info

    # Step 3: 纯名称匹配（单候选）
    name_only_matched = {}
    name_only_multi = {}
    for (name, spec), cnt in pairs.items():
        if (name, spec) in manual_matched or (name, spec) in name_spec_matched:
            continue
        result = _match_by_name_only(name, mat_list)
        if result is None:
            continue
        if isinstance(result, list):
            name_only_multi[(name, spec)] = result
        else:
            name_only_matched[(name, spec)] = result

    return manual_matched, name_spec_matched, name_only_matched, name_only_multi, mat_by_name


def _match_by_name_and_spec(f1_name, f1_spec, mat_list):
    """名称包含 + 规格数量交集匹配"""
    f1_norm = normalize_name(f1_name)
    f1_qtys = extract_quantities(f1_spec)

    candidates = []
    for m in mat_list:
        m_norm = normalize_name(m['物料名'])
        name_match = (f1_norm == m_norm or f1_norm in m_norm)
        if not name_match and len(m_norm) >= 3 and m_norm in f1_norm:
            name_match = True
        if not name_match:
            continue

        m_qtys = extract_quantities(m['规格'])
        if f1_qtys and m_qtys:
            if f1_qtys & m_qtys:
                candidates.append(m)
        elif f1_qtys and not m_qtys:
            continue
        else:
            candidates.append(m)

    return candidates[0] if len(candidates) == 1 else None


def _match_by_name_only(f1_name, mat_list):
    """纯名称包含匹配，返回单个info或list(多个候选)"""
    f1_norm = normalize_name(f1_name)
    candidates = []
    for m in mat_list:
        m_norm = normalize_name(m['物料名'])
        if f1_norm == m_norm or f1_norm in m_norm:
            candidates.append(m)
        elif len(m_norm) >= 3 and m_norm in f1_norm:
            candidates.append(m)

    if len(candidates) == 1:
        return candidates[0]
    elif len(candidates) > 1:
        return candidates
    return None


# ============================================================
# Phase 3b: 普洱规则
# ============================================================
def match_puer_by_spec(name, spec, mat_by_name):
    """普洱系列按规格匹配"""
    if '贴纸' in name:
        return None
    if '普洱' not in name:
        return None
    if '500g/包' in spec:
        return mat_by_name.get('普洱茶（旧版）')
    elif '50g/包' in spec:
        return mat_by_name.get('普洱茶（新版）')
    elif not spec or spec.strip() == '':
        return mat_by_name.get('普洱茶（新版）')
    return None


# ============================================================
# Phase 3c: 玫瑰杯套规则
# ============================================================
def match_rose_cup_sleeve(name, mat_by_name):
    """玫瑰杯套各变体统一匹配"""
    if name == '玫瑰杯套':
        return mat_by_name.get('玫瑰杯套')
    return None


# ============================================================
# 主流程
# ============================================================
def main():
    print('=' * 60)
    print('  盘点数据补齐脚本')
    print('=' * 60)
    print()

    # --- 获取文件路径 ---
    paths = {}
    for key, label in [('material', '物料分类与规格清单.xlsx'),
                       ('store', '象子茶铺门店信息表.xlsx'),
                       ('inventory', '盘点数据汇总.xlsx')]:
        default = DEFAULT_PATHS.get(key, '')
        prompt = f'{label} 路径 [{default}]: '
        user_input = input(prompt).strip()
        paths[key] = user_input if user_input else default
        if not os.path.exists(paths[key]):
            print(f'  X 文件不存在: {paths[key]}')
            sys.exit(1)

    # --- 加载文件 ---
    print('\n[Phase 0] 加载文件...')
    wb_mat, ws_mat, _ = load_excel(paths['material'])
    wb_store, ws_store, _ = load_excel(paths['store'])
    wb_inv, ws_inv, _ = load_excel(paths['inventory'])
    total_rows = ws_inv.max_row - 1
    mat_rows = ws_mat.nrows - 1 if hasattr(ws_mat, 'nrows') else ws_mat.max_row - 1
    store_rows = ws_store.max_row - 1 if hasattr(ws_store, 'max_row') else ws_store.nrows - 1
    print(f'  物料清单: {mat_rows} 条')
    print(f'  门店信息: {store_rows} 条')
    print(f'  盘点数据: {total_rows} 行')

    # --- Phase 0: 检测列位置 ---
    # 根据表头自动定位
    store_col = find_col_index(ws_inv, '门店') or 1  # 门店列
    name_col = find_col_index(ws_inv, '名称') or 2    # 物料名称列
    spec_col = find_col_index(ws_inv, '规格') or 3    # 规格列
    print(f'  列检测: 门店={store_col}, 名称={name_col}, 规格={spec_col}')

    # 新增: 门店id, 小程序id, 门店名称, 物料ID, 企迈ID, 父级分类, 分类, 物料名, 物料规格
    store_headers = ['门店id', '小程序id', '门店名称']
    mat_headers = ['物料ID', '企迈ID', '父级分类', '分类', '物料名', '物料规格', '单位', '单价', '副盘点单位']

    base_cols = ws_inv.max_column
    for i, h in enumerate(store_headers + mat_headers):
        ws_inv.cell(1, base_cols + 1 + i).value = h

    store_id_col = base_cols + 1
    mp_id_col = base_cols + 2
    store_name_col = base_cols + 3
    mat_id_col = base_cols + 4
    qm_id_col = base_cols + 5
    parent_cat_col = base_cols + 6
    cat_col = base_cols + 7
    mat_name_col = base_cols + 8
    mat_spec_col = base_cols + 9
    unit_col = base_cols + 10
    price_col = base_cols + 11
    sub_unit_col = base_cols + 12

    # --- Phase 1: 清洗门店名称 ---
    print('\n[Phase 1] Phase 1: 清洗门店名称...')
    store_map = phase1_clean_stores(ws_inv, store_col)
    print(f'  共 {len(store_map)} 种门店名称')

    # --- Phase 2: 匹配门店 ---
    print('\n[Phase 2] Phase 2: 匹配门店信息...')
    store_matched, store_unmatched, store_multi = phase2_match_stores(store_map, ws_store)
    print(f'  匹配: {len(store_matched)} | 未匹配: {len(store_unmatched)} | 一对多: {len(store_multi)}')

    # 处理一对多和未匹配
    if store_multi:
        print('\n  !! 门店一对多歧义：')
        for i, (cleaned, candidates) in enumerate(store_multi):
            print(f'  {i+1}. "{cleaned}" 匹配到 {len(candidates)} 个门店:')
            for j, c in enumerate(candidates):
                print(f'     {j+1}. {c["门店名称"]}')
        print('  已跳过，请手动添加映射到 STORE_MANUAL_MAP 后重跑')

    if store_unmatched:
        print(f'\n  !! {len(store_unmatched)} 个门店未匹配: {store_unmatched[:10]}...')
        print('  已跳过，请手动添加映射到 STORE_MANUAL_MAP 后重跑')

    # --- Phase 3: 匹配物料 ---
    print('\n[Phase 3] Phase 3: 匹配物料信息...')
    manual_m, spec_m, name_m, multi_m, mat_by_name = phase3_match_materials(ws_inv, name_col, spec_col, paths['material'])

    print(f'  手动映射: {len(manual_m)} 种')
    print(f'  名+规匹配: {len(spec_m)} 种')
    print(f'  纯名匹配: {len(name_m)} 种')

    # 多候选默认选择
    extra_matched = {}
    still_multi = {}
    for (name, spec), candidates in multi_m.items():
        key = (name, spec)
        if key in MATERIAL_MULTI_DEFAULTS:
            f2_name = MATERIAL_MULTI_DEFAULTS[key]
            if f2_name in mat_by_name:
                extra_matched[(name, spec)] = mat_by_name[f2_name]
                continue
        # 普洱规则
        m = match_puer_by_spec(name, spec, mat_by_name)
        if m:
            extra_matched[(name, spec)] = m
            continue
        # 玫瑰杯套
        m = match_rose_cup_sleeve(name, mat_by_name)
        if m:
            extra_matched[(name, spec)] = m
            continue
        still_multi[(name, spec)] = candidates

    print(f'  默认选择+规则: {len(extra_matched)} 种')

    if still_multi:
        print(f'\n  !! {len(still_multi)} 种物料一对多歧义需手动处理：')
        for (name, spec), candidates in list(still_multi.items())[:10]:
            print(f'    "{name}" ({spec}): {[c["物料名"] for c in candidates[:5]]}')
        print('  已跳过，请手动添加映射到 MATERIAL_MULTI_DEFAULTS 后重跑')

    # --- Phase 4: 写回数据 ---
    print(f'\n[Phase 4]  Phase 4: 写回数据...')

    # 合并所有匹配结果
    all_material_matches = {}
    all_material_matches.update(manual_m)
    all_material_matches.update(spec_m)
    all_material_matches.update(name_m)
    all_material_matches.update(extra_matched)

    # 写入
    stats = Counter()
    for r in range(2, ws_inv.max_row + 1):
        # 门店信息
        raw_store = str(ws_inv.cell(r, store_col).value or '').strip()
        cleaned = store_map.get(raw_store, raw_store)
        store_info = store_matched.get(cleaned)
        if store_info:
            ws_inv.cell(r, store_id_col).value = store_info['门店id']
            ws_inv.cell(r, mp_id_col).value = store_info['小程序id']
            ws_inv.cell(r, store_name_col).value = store_info['门店名称']

        # 物料信息
        mat_name = str(ws_inv.cell(r, name_col).value or '').strip()
        mat_spec = str(ws_inv.cell(r, spec_col).value or '').strip()
        mat_info = all_material_matches.get((mat_name, mat_spec))
        if mat_info:
            ws_inv.cell(r, mat_id_col).value = mat_info['物料ID']
            ws_inv.cell(r, qm_id_col).value = mat_info['企迈ID']
            ws_inv.cell(r, parent_cat_col).value = mat_info['父级分类']
            ws_inv.cell(r, cat_col).value = mat_info['分类']
            ws_inv.cell(r, mat_name_col).value = mat_info['物料名']
            ws_inv.cell(r, mat_spec_col).value = mat_info['规格']
            ws_inv.cell(r, unit_col).value = mat_info.get('单位', '')
            ws_inv.cell(r, price_col).value = mat_info.get('单价', '')
            ws_inv.cell(r, sub_unit_col).value = mat_info.get('副盘点单位', '')
        stats['物料已匹配' if mat_info else '物料未匹配'] += 1
        stats['门店已匹配' if store_info else '门店未匹配'] += 1

        if r % 2000 == 0:
            print_progress(r - 1, total_rows, '写入中')

    # --- 保存 ---
    out_path = paths['inventory'].replace('.xlsx', '_已补齐.xlsx')
    wb_inv.save(out_path)

    # --- 报告 ---
    print(f'\n\n[Done] 完成！')
    print(f'  输出文件: {out_path}')
    print(f'  总行数: {total_rows}')
    print(f'  门店已匹配: {stats["门店已匹配"]} ({100*stats["门店已匹配"]/total_rows:.1f}%)')
    print(f'  物料已匹配: {stats["物料已匹配"]} ({100*stats["物料已匹配"]/total_rows:.1f}%)')
    print()


if __name__ == '__main__':
    main()
