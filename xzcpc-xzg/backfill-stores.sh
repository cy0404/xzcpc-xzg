#!/bin/bash
#===============================================================================
# 盘点工具 1.0 — 存量任务门店信息回填
# 从外部 API 获取所有门店信息，生成 SQL 更新 task 表的 store_name/store_code/xiaochengxuid
#
# 用法:
#   chmod +x backfill-stores.sh
#   ./backfill-stores.sh                    # 生成 SQL 文件
#   ./backfill-stores.sh | mysql -u...      # 直接执行
#===============================================================================

API_URL="http://162.14.122.80:5174/api/publish/entities/mendianxinxi/records"
API_KEY="aSwnTbaCRhKPARFtARvaBHtyZ6fUMZJM"

echo "-- ============================================================"
echo "-- 存量任务门店信息回填 SQL（自动生成）"
echo "-- 生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "-- ============================================================"
echo ""

page=1
pageSize=100
hasMore=true

while $hasMore; do
    resp=$(curl -s -H "X-Publish-Api-Key: $API_KEY" "${API_URL}?page=${page}&pageSize=${pageSize}")

    # 提取 records 数组和 total
    records=$(echo "$resp" | python3 -c "
import sys, json
data = json.load(sys.stdin)
records = data.get('records', [])
total = data.get('total', 0)
for r in records:
    sid = str(r.get('id', ''))
    name = str(r.get('mendianmingcheng', '')).replace(\"'\", \"''\")
    code = str(r.get('bianma', '')).replace(\"'\", \"''\")
    xid = str(r.get('xiaochengxuid', '')).replace(\"'\", \"''\")
    if sid:
        print(f\"{sid}|{name}|{code}|{xid}\")
print(f'TOTAL:{total}', file=sys.stderr)
" 2>&1)

    # 检查有没有数据
    line_count=$(echo "$records" | grep -v "^TOTAL:" | grep -c "|")
    total=$(echo "$records" | grep "^TOTAL:" | cut -d: -f2)

    if [ "$line_count" -eq 0 ]; then
        break
    fi

    echo "$records" | grep -v "^TOTAL:" | while IFS='|' read -r sid name code xid; do
        [ -z "$sid" ] && continue
        echo "UPDATE task SET store_name = '$name', store_code = '$code', xiaochengxuid = '$xid' WHERE store_id = '$sid' AND (store_name IS NULL OR store_code IS NULL OR xiaochengxuid IS NULL);"
    done

    if [ -n "$total" ] && [ "$((page * pageSize))" -ge "$total" ]; then
        hasMore=false
    else
        page=$((page + 1))
    fi
done

echo ""
echo "-- 回填完成，共处理 $total 条门店记录"
