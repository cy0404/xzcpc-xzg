#!/bin/bash
# ============================================================
# 精确撤回：拉取指定群全部消息，只撤回「机器人(app)发的、含 issue-accept.html 链接的卡片」
# 其他消息（用户消息/系统消息/其他机器人消息）一律不碰
# 用法: APP_ID=xxx APP_SECRET=xxx CHAT_ID=oc_xxx bash recall-verify.sh
# ============================================================
set -u

APP_ID="${APP_ID:-}"
APP_SECRET="${APP_SECRET:-}"
CHAT_ID="${CHAT_ID:-}"
if [ -z "$APP_ID" ] || [ -z "$APP_SECRET" ] || [ -z "$CHAT_ID" ]; then
  echo "ERROR: 需要 APP_ID / APP_SECRET / CHAT_ID" >&2
  exit 1
fi
command -v python3 >/dev/null 2>&1 && PY=python3 || PY=python

# ---------- 1. tenant token ----------
TOKEN=$(curl -s -X POST "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal" \
  -H "Content-Type: application/json" \
  -d "{\"app_id\":\"$APP_ID\",\"app_secret\":\"$APP_SECRET\"}" \
  | grep -o '"tenant_access_token":"[^"]*"' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
  echo "ERROR: 获取 tenant token 失败" >&2
  exit 1
fi
echo "token OK"

# ---------- 2. 翻页拉取群全部消息 ----------
MSGS=/tmp/recall-msgs-$$.jsonl
PAGE_TOKEN=""
while :; do
  URL="https://open.feishu.cn/open-apis/im/v1/messages?container_id_type=chat&container_id=$CHAT_ID&page_size=100"
  [ -n "$PAGE_TOKEN" ] && URL="$URL&page_token=$PAGE_TOKEN"
  RESP=$(curl -s "$URL" -H "Authorization: Bearer $TOKEN")
  echo "$RESP" | $PY -c "
import sys, json
d = json.load(sys.stdin)
for it in ((d.get('data') or {}).get('items') or []):
    print(json.dumps(it, ensure_ascii=False))
" >> "$MSGS"
  HAS_MORE=$(echo "$RESP" | $PY -c "import sys, json; d=json.load(sys.stdin); print('true' if (d.get('data') or {}).get('has_more') else 'false')")
  [ "$HAS_MORE" = "true" ] || break
  PAGE_TOKEN=$(echo "$RESP" | $PY -c "import sys, json; d=json.load(sys.stdin); print((d.get('data') or {}).get('page_token') or '')")
  [ -n "$PAGE_TOKEN" ] || break
done

# ---------- 3. 筛选 + 只撤机器人卡片 ----------
TOKEN="$TOKEN" $PY <<EOF
import json, os, subprocess, time
token = os.environ['TOKEN']
hits = []
for line in open(r"$MSGS", encoding='utf-8'):
    line = line.strip()
    if not line:
        continue
    it = json.loads(line)
    body = (it.get('body') or {}).get('content') or ''
    if 'issue-accept.html' in body:
        hits.append(it)
print(f"群消息共 {sum(1 for _ in open(r'$MSGS', encoding='utf-8'))} 条（含分页），其中含 issue-accept.html 的 {len(hits)} 条：")
for it in hits:
    ts = int(it.get('create_time') or 0) / 1000
    t = time.strftime('%m-%d %H:%M:%S', time.localtime(ts))
    sender = it.get('sender') or {}
    print(f"  {it.get('message_id')}  {t}  sender_type={sender.get('sender_type')}  msg_type={it.get('msg_type')}")
app_hits = [it for it in hits if (it.get('sender') or {}).get('sender_type') == 'app']
print(f"其中机器人(app)发的 {len(app_hits)} 条，开始撤回（其余不碰）...")
for it in app_hits:
    mid = it.get('message_id')
    r = subprocess.run(['curl', '-s', '-X', 'DELETE',
                        f'https://open.feishu.cn/open-apis/im/v1/messages/{mid}',
                        '-H', f'Authorization: Bearer {token}'],
                       capture_output=True, text=True)
    print(f"  撤回 {mid}: {(r.stdout or '').strip()}")
EOF
rm -f "$MSGS"
