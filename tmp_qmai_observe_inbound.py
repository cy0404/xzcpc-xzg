# -*- coding: utf-8 -*-
"""只读观察：查指定入库单的企迈侧实时状态（status / 每物料数量 / 单价）
用法：python tmp_qmai_observe_inbound.py <inboundNo> [--days 7]
小程序操作一步后跑一次，对照企迈侧实际变化。
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

def pull(start, end, max_page=12):
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

target = sys.argv[1]
days = 7
if "--days" in sys.argv:
    i = sys.argv.index("--days")
    if i + 1 < len(sys.argv):
        days = int(sys.argv[i + 1])

found = None
for d in range(0, days):
    start = time.strftime("%Y-%m-%d 00:00:00", time.localtime(time.time() - 86400 * (d + 1)))
    end = time.strftime("%Y-%m-%d 23:59:59", time.localtime(time.time() - 86400 * d))
    for r in pull(start, end):
        if r.get("inboundNo") == target:
            found = r
            break
    if found:
        break

if found is None:
    print("近 %d 天没找到该单号" % days)
    sys.exit(0)

st = found.get("status")
stmap = {1: "待入库", 2: "已入库", 3: "已作废"}
print("== %s | bizNo=%s | type=%s | 状态=%s(%s) | 创建=%s 入库=%s" % (
    target, found.get("bizNo"), found.get("inboundType"),
    st, stmap.get(st, "?"), found.get("createdAt"), found.get("inboundAt")))
for it in (found.get("inboundProductList") or []):
    print("   %s | code=%s | inboundNum=%s | 单价raw=%s | num=%s partNum=%s quantity=%s" % (
        it.get("productName"), it.get("productCode"), it.get("inboundNum"),
        it.get("inboundPrice"), it.get("num"), it.get("partNum"), it.get("quantity")))
