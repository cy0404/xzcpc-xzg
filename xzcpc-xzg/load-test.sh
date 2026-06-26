#!/bin/bash
# ============================================================
# 月末高峰压测 — 模拟 1000 人密集操作
# 用法: ./load-test.sh [并发数] [轮数]
#   ./load-test.sh 20 10   # 20并发 × 10轮 = 200次请求
#   ./load-test.sh 50 10   # 50并发 × 10轮 = 500次请求
# ============================================================

CONCURRENT=${1:-20}
ROUNDS=${2:-5}
BASE_URL="${BASE_URL:-https://www.xzcpc-9pd.top/storeInventory/api/mp}"
TOKEN="${TOKEN:-}"  # 从环境变量传入，如 TOKEN=xxx ./load-test.sh 10 5

TMP_DIR=$(mktemp -d)
trap "rm -rf $TMP_DIR" EXIT

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

# ============================================================
# 1. 先登录取 token（dev 模式用 mock openid）
# ============================================================
if [ -z "$TOKEN" ]; then
  echo -e "${YELLOW}[准备] 获取测试 token ...${NC}"
  LOGIN_RESP=$(curl -s -X POST "$BASE_URL/auth/wx/login" \
    -H "Content-Type: application/json" \
    -d '{"code":"dev_test","wxNickname":"压测用户"}')
  TOKEN=$(echo "$LOGIN_RESP" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
  if [ -z "$TOKEN" ]; then
    echo -e "${RED}[失败] 无法获取 token，请确认 mp-server 已启动（dev profile）${NC}"
    echo "登录响应: $LOGIN_RESP"
    exit 1
  fi
  echo -e "${GREEN}[准备] Token 就绪${NC}"
fi

AUTH="Authorization: Bearer $TOKEN"

# ============================================================
# 2. 通用请求函数
# ============================================================
do_request() {
  local label=$1 method=$2 url=$3 data=$4
  local start=$(date +%s%3N)
  local http_code
  if [ "$method" = "GET" ]; then
    http_code=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH" "$BASE_URL$url")
  else
    http_code=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" \
      -H "$AUTH" -H "Content-Type: application/json" \
      -d "$data" "$BASE_URL$url")
  fi
  local end=$(date +%s%3N)
  local elapsed=$((end - start))
  if [ "$http_code" = "200" ]; then
    echo "OK|$label|${elapsed}ms"
  else
    echo "FAIL|$label|HTTP$http_code|${elapsed}ms"
  fi
}

# ============================================================
# 3. 模拟真实场景: 任务列表 → 任务详情 → 分区物料
# ============================================================
simulate_user() {
  local uid=$1
  local log="$TMP_DIR/user_$uid.log"

  # 1. 任务列表（首页）
  do_request "task_list" "GET" "/tasks" >> "$log"

  # 2. 任务详情（假设 taskId=1，可改）
  do_request "task_detail" "GET" "/tasks/1" >> "$log"

  # 3. 分区物料列表（假设 taskId=1, zoneId=1）
  do_request "zone_materials" "GET" "/tasks/1/zones/1/materials" >> "$log"
}

# ============================================================
# 4. 跑压测
# ============================================================
echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}  月末高峰压测: $CONCURRENT 并发 × $ROUNDS 轮${NC}"
echo -e "${YELLOW}  模拟场景: 任务列表 → 详情 → 分区物料${NC}"
echo -e "${YELLOW}============================================${NC}"
echo ""

TOTAL_START=$(date +%s%3N)

for round in $(seq 1 $ROUNDS); do
  echo -e "${YELLOW}--- 第 $round/$ROUNDS 轮 ($CONCURRENT 并发) ---${NC}"

  for i in $(seq 1 $CONCURRENT); do
    simulate_user "${round}_${i}" &
  done
  wait

  # 统计本轮
  OK=$(cat "$TMP_DIR"/user_${round}_*.log 2>/dev/null | grep -c "^OK|")
  FAIL=$(cat "$TMP_DIR"/user_${round}_*.log 2>/dev/null | grep -c "^FAIL|")
  SLOW=$(cat "$TMP_DIR"/user_${round}_*.log 2>/dev/null | awk -F'|' '$3+0>500{print $0}' | wc -l)
  AVG=$(cat "$TMP_DIR"/user_${round}_*.log 2>/dev/null | awk -F'|' '{gsub(/ms/,"",$3); sum+=$3; count++} END{printf "%.0f", sum/count}')

  echo -e "  ${GREEN}OK: $OK${NC}  ${RED}FAIL: $FAIL${NC}  ${YELLOW}>500ms: $SLOW${NC}  平均: ${AVG}ms"
done

TOTAL_END=$(date +%s%3N)
TOTAL_TIME=$((TOTAL_END - TOTAL_START))
TOTAL_OK=$(cat "$TMP_DIR"/user_*.log 2>/dev/null | grep -c "^OK|")
TOTAL_FAIL=$(cat "$TMP_DIR"/user_*.log 2>/dev/null | grep -c "^FAIL|")

echo ""
echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}  总耗时: ${TOTAL_TIME}ms  成功: $TOTAL_OK  失败: $TOTAL_FAIL${NC}"
echo -e "${YELLOW}============================================${NC}"

# 有失败时显示详情
if [ "$TOTAL_FAIL" -gt 0 ]; then
  echo ""
  echo -e "${RED}失败详情:${NC}"
  cat "$TMP_DIR"/user_*.log 2>/dev/null | grep "^FAIL" | head -20
fi
