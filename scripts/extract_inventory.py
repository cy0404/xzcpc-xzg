#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
直营店财务数据提取脚本
功能：从多个门店的Excel文件中提取盘点数据，合并为统一格式

输入：
  文件夹路径（包含多个门店的xlsx文件，每个文件含"盘点表"sheet）

输出：
  盘点数据汇总.xlsx（6列：门店, 物料名称, 规格, 单价, 盘点数量, 盘点金额）

用法：
  python extract_inventory.py
  （运行后输入文件夹路径，或回车使用默认路径）
"""

import openpyxl
import re
import os
import sys

# ============================================================
# 配置
# ============================================================
DEFAULT_FOLDER = r'C:\Users\xiemg\Downloads\直营店5月财务数据'
OUTPUT_NAME = '盘点数据汇总.xlsx'

# ============================================================
# 工具函数
# ============================================================
def extract_store_name(filename):
    """从文件名提取店名：'1.新迎f.xlsx' -> '新迎'"""
    name = os.path.splitext(filename)[0]  # 去扩展名
    name = re.sub(r'^\d+\.', '', name)    # 去前缀 '1.', '10.' 等
    name = re.sub(r'f$', '', name)        # 去尾部 'f'
    return name.strip()


def is_valid_data_row(ws, row):
    """
    判断是否为有效数据行
    跳过：汇总行（小计/合计/总计）、表头行、空行
    """
    name = ws.cell(row, 3).value      # 名称
    cat2 = ws.cell(row, 2).value      # 分类
    cat1 = ws.cell(row, 1).value      # 总类

    # 名称为空 -> 跳过（表头/空行/分组标签）
    if not name or str(name).strip() == '':
        return False

    # 分类为"小计：" -> 跳过
    if cat2 and '小计' in str(cat2):
        return False

    # 总类含"合计"或"总计" -> 跳过
    if cat1 and ('合计' in str(cat1) or '总计' in str(cat1)):
        return False

    return True


def process_file(filepath):
    """处理单个文件，返回数据行列表"""
    store_name = extract_store_name(os.path.basename(filepath))

    wb = openpyxl.load_workbook(filepath, data_only=True)

    # 查找盘点表sheet
    sheet_name = None
    for sn in wb.sheetnames:
        if '盘点' in sn:
            sheet_name = sn
            break
    if not sheet_name:
        print(f'  !! {os.path.basename(filepath)}: 未找到盘点表sheet')
        return []

    ws = wb[sheet_name]
    rows = []

    # 从第5行开始扫描（前4行为标题/说明/表头）
    for r in range(5, ws.max_row + 1):
        if not is_valid_data_row(ws, r):
            continue

        name = str(ws.cell(r, 3).value or '').strip()
        spec = str(ws.cell(r, 4).value or '').strip()
        price = ws.cell(r, 6).value          # 单价
        remain_qty = ws.cell(r, 9).value     # 本月剩余数量 (col 9)
        remain_amt = ws.cell(r, 12).value    # 本月剩余金额 (col 12)

        # 数值处理：保留原始值，None->0或空
        if price is None:
            price = ''
        if remain_qty is None:
            remain_qty = ''
        if remain_amt is None:
            remain_amt = ''

        rows.append([store_name, name, spec, price, remain_qty, remain_amt])

    wb.close()
    return rows


# ============================================================
# 主流程
# ============================================================
def main():
    print('=' * 60)
    print('  直营店财务数据提取脚本')
    print('=' * 60)
    print()

    # 获取文件夹路径
    folder = input(f'文件夹路径 [{DEFAULT_FOLDER}]: ').strip()
    if not folder:
        folder = DEFAULT_FOLDER
    if not os.path.isdir(folder):
        print(f'  X 文件夹不存在: {folder}')
        sys.exit(1)

    # 找到所有xlsx文件
    files = sorted([
        f for f in os.listdir(folder)
        if f.endswith('.xlsx') and not f.startswith('~$')
    ])
    if not files:
        print(f'  X 文件夹内无xlsx文件')
        sys.exit(1)

    print(f'\n[Phase 1] 找到 {len(files)} 个文件')
    for f in files:
        print(f'    {f} -> 门店: {extract_store_name(f)}')

    # 处理每个文件
    print(f'\n[Phase 2] 处理中...')
    all_rows = []
    for i, f in enumerate(files):
        filepath = os.path.join(folder, f)
        rows = process_file(filepath)
        all_rows.extend(rows)
        store = extract_store_name(f)
        print(f'  [{i+1}/{len(files)}] {store}: {len(rows)} 条')

    # 写入输出文件
    out_path = os.path.join(folder, OUTPUT_NAME)
    wb_out = openpyxl.Workbook()
    ws_out = wb_out.active
    ws_out.title = '盘点明细'

    # 写表头
    headers = ['门店', '物料名称', '规格', '单价', '盘点数量', '盘点金额']
    for c, h in enumerate(headers, 1):
        ws_out.cell(1, c).value = h

    # 写数据
    for r, row in enumerate(all_rows, 2):
        for c, val in enumerate(row, 1):
            ws_out.cell(r, c).value = val

    wb_out.save(out_path)

    print(f'\n[Output] 输出: {out_path}')
    print(f'   总行数: {len(all_rows)}')
    print(f'   门店数: {len(files)}')
    print()


if __name__ == '__main__':
    main()
