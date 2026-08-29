#!/bin/bash
# ============================================================
# 撤回「未验收问题提醒」旧卡片（飞书机器人可撤回自己 24 小时内发送的消息）
# 适用：旧版 jar 发出的门店卡片（按钮链接不带 chatId，门店点开会看到全部门店问题）
#
# 用法一（直接按 message_id 撤回，推荐，send_log 有记录时用）：
#   APP_ID=cli_xxx APP_SECRET=xxx bash recall-issue-cards.sh <message_id> [<message_id> ...]
#
# 用法二（按群自动撤回：列出该群今天机器人发的提醒卡片并撤回，send_log 没记录时用）：
#   APP_ID=cli_xxx APP_SECRET=xxx CHAT_ID=oc_xxx bash recall-issue-cards.sh
#   说明：先从飞书 API 拉该群最近消息，筛出链接含 issue-accept.html 的卡片，逐条撤回
#   （注意：API 按时间倒序，start_time 过滤今天 00:00 之后）
# ============================================================
set -u

APP_ID="${APP_ID:-}"
APP_SECRET="${APP_SECRET:-}"
CHAT_ID="${CHAT_ID:-}"
MSG_IDS=("$@")

if [ -z "$APP_ID" ] || [ -z "$APP_SECRET" ]; then
  echo "ERROR: 需要设置 APP_ID / APP_SECRET（取自服务器启动环境 FEISHU_APP_SECRET 与 application-prod.yml 的 feishu.app-id）" >&2
  exit 1
fi
if [ ${#MSG_IDS[@]} -eq 0 ] && [ -z "$CHAT_ID" ]; then
  echo "ERROR: 需要传 message_id 参数，或设置 CHAT_ID 走群自动撤回" >&2
  exit 1
fi

# ---------- 1. 获取 tenant token ----------
echo "== 获取 tenant token =="
TOKEN=$(curl -s -X POST "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal" \
  -H "Content-Type: application/json" \
  -d "{\"app_id\":\"$APP_ID\",\"app_secret\":\"$APP_SECRET\"}" \
  | grep -o '"tenant_access_token":"[^"]*"' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
  echo "ERROR: 获取 tenant token 失败（检查 APP_ID / APP_SECRET）" >&2
  exit 1
fi
echo "token OK"

# ---------- 2. 收集要撤回的 message_id ----------
if [ ${#MSG_IDS[@]} -gt 0 ]; then
  TARGETS=("${MSG_IDS[@]}")
else
  echo "== 拉取群 $CHAT_ID 最近 3 天的消息（筛提醒卡片）=="
  # 撤回窗口只有 24h，拉 3 天足够覆盖；start_time 过滤太窄（如"今天"）会漏掉昨天发的卡片
  START_MS=$(( $(date -d "3 days ago" +%s) * 1000 ))
  RESP=$(curl -s -G "https://open.feishu.cn/open-apis/im/v1/messages" \
    -H "Authorization: Bearer $TOKEN" \
    --data-urlencode "container_id_type=chat" \
    --data-urlencode "container_id=$CHAT_ID" \
    --data-urlencode "start_time=$START_MS" \
    --data-urlencode "page_size=50")
  # 只筛含 issue-accept.html 链接的卡片消息（提醒卡片），避免误撤其他消息
  if command -v python3 >/dev/null 2>&1; then PY=python3; else PY=python; fi
  TARGETS=($(echo "$RESP" | $PY -c "
import sys, json
d = json.load(sys.stdin)
hits = []
for it in ((d.get('data') or {}).get('items') or []):
    body = (it.get('body') or {}).get('content') or ''
    if 'issue-accept.html' in body:
        hits.append(it.get('message_id'))
print('\n'.join(x for x in hits if x))
"))
  echo "群消息返回 $(echo "$RESP" | grep -o '"message_id":"[^"]*"' | wc -l) 条，其中提醒卡片 ${#TARGETS[@]} 条"
fi

if [ ${#TARGETS[@]} -eq 0 ]; then
  echo "没有找到可撤回的消息，退出"
  exit 0
fi

# ---------- 3. 逐条撤回 ----------
# 注意：飞书撤回消息 API 是 DELETE /open-apis/im/v1/messages/{message_id}（POST 会 404）
for MID in "${TARGETS[@]}"; do
  echo "== 撤回 $MID =="
  curl -s -X DELETE "https://open.feishu.cn/open-apis/im/v1/messages/$MID" \
    -H "Authorization: Bearer $TOKEN"
  echo
done
echo "完成"
