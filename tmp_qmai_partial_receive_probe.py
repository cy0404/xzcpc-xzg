# -*- coding: utf-8 -*-
"""只读探测：企迈 9.2.2 原始字段里是否有"部分收货"痕迹
1. 打印一张待入库(status=1)单的完整原始 JSON（看有哪些字段）
2. 统计：status=1 的单里有没有 item 已收>0；status=2 的单里有没有 item 已收<应收
"""
import hmac, hashlib, base64, time, random, json, sys, io
from urllib.parse import quote
import urllib.request

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

OPEN_ID = "71fcea7abc9709d653693116410b5385"
GRANT_CODE = "WaGI2rqy9b"
OPEN_KEY = "KYDwl5j39Wb71bff5fe6b0434ac6366a913c0dbc947q1SpQyZ"
BASE = "https://openapi.qmai.cn"

def call(url, params, retry=3):
    for i in range(retry):
        ts = int(time.time()); nonce = random.randint(10000, 99999)
        sorted_str = "grantCode=%s&nonce=%s&openId=%s&timestamp=%s" % (GRANT_CODE, nonce, OPEN_ID, ts)
        restored = quote(sorted_str, safe='').replace("%3D", "=").replace("%26", "&")
        tok = base64.b64encode(hmac.new(OPEN_KEY.encode(), restored.encode(), hashlib.sha1).digest()).decode()
        body = {"openId": OPEN_ID, "grantCode": GRANT_CODE, "nonce": nonce,
                "timestamp": ts, "token": quote(tok, safe=''), "params": params}
        req = urllib.request.Request(url, data=json.dumps(body).encode(),
                                     headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.loads(r.read().decode())
        except Exception as e:
            print("  retry %d: %s" % (i + 1, e)); time.sleep(2)
    raise RuntimeError("call failed: " + url)

def pull(start, end, max_page=40):
    out = []
    for page in range(1, max_page + 1):
        resp = call(BASE + "/v3/scm/order/inbound/order/list",
                    {"createdStartAt": start, "createdEndAt": end, "pageNo": page, "pageSize": 1000})
        data = resp.get("data") or {}
        recs = data.get("records") if isinstance(data, dict) else data
        if isinstance(data, dict) and recs is None:
            recs = data.get("data")
        if not recs:
            break
        out.extend(recs)
        if len(recs) < 1000:
            break
    return out

recs = pull("2026-07-16 00:00:00", "2026-08-15 23:59:59")
print("总拉取 %d 条" % len(recs))

# 1. 找一张待入库单，打印完整原始 JSON（截断）
pend = [r for r in recs if r.get("status") == 1]
print("\n== 待入库单样例（完整原始字段，首张） ==")
if pend:
    print(json.dumps(pend[0], ensure_ascii=False, indent=1)[:4000])
else:
    print("  近30天没有 status=1 的单")

# 2. 字段盘点：item 里和"数量/收货"相关的字段名
print("\n== item 层字段名盘点 ==")
item_keys = set()
for r in recs:
    for it in (r.get("inboundProductList") or []):
        item_keys.update(it.keys())
        break
    if len(item_keys) >= 40:
        break
print(sorted(item_keys))

# 3. 分析：item 里像"已收"的字段（含收/receive/num 的键）在 status=1 与 status=2 单上的分布
import collections
cand = [k for k in item_keys if ("收" in k or "eceive" in k or "num" in k.lower())]
print("\n== 候选数量字段分布（status=1 待入库 / status=2 已入库） ==")
for k in sorted(cand):
    vals1, vals2 = [], []
    n1 = n2 = 0
    for r in recs:
        st = r.get("status")
        for it in (r.get("inboundProductList") or [])[:3]:
            v = it.get(k)
            if v is None:
                continue
            if st == 1:
                vals1.append(v); n1 += 1
            elif st == 2:
                vals2.append(v); n2 += 1
            else:
                continue
    if not vals1 and not vals2:
        continue
    def s(vals):
        if not vals:
            return "无值"
        num = [v for v in vals if isinstance(v, (int, float))]
        if not num:
            return "非数值: " + str(vals[:3])
        return "min=%s max=%s 非零占比=%.0f%% (n=%d)" % (
            min(num), max(num), 100.0 * sum(1 for v in num if v > 0) / len(num), len(num))
    print("字段 %-24s | 待入库: %s | 已入库: %s" % (k, s(vals1), s(vals2)))

# 4. 已入库单里找"部分收货"例子：inboundNum 与其它数量字段不一致的
print("\n== 已入库单部分收货样例（inboundNum vs 其他 num 字段不一致） ==")
shown = 0
for r in recs:
    if r.get("status") != 2:
        continue
    for it in (r.get("inboundProductList") or []):
        ib = it.get("inboundNum")
        for k in cand:
            v = it.get(k)
            if k == "inboundNum" or v is None or not isinstance(v, (int, float)):
                continue
            if isinstance(ib, (int, float)) and abs(ib - v) > 0.001:
                print("  %s %s %s: inboundNum=%s %s=%s" % (
                    r.get("inboundNo"), r.get("bizNo"), it.get("productName"), ib, k, v))
                shown += 1
                break
        if shown >= 10:
            break
    if shown >= 10:
        break
if not shown:
    print("  未发现（已入库单各数量字段一致）")
