# -*- coding: utf-8 -*-
"""只读：查带 num/partNum 字段的那批已入库单 —— num 是不是实收、与 inboundNum 是否一致、什么类型"""
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

out = []
for page in range(1, 40):
    resp = call(BASE + "/v3/scm/order/inbound/order/list",
                {"createdStartAt": "2026-07-16 00:00:00", "createdEndAt": "2026-08-15 23:59:59",
                 "pageNo": page, "pageSize": 1000})
    data = resp.get("data") or {}
    recs = data.get("records") if isinstance(data, dict) else data
    if isinstance(data, dict) and recs is None:
        recs = data.get("data")
    if not recs:
        break
    out.extend(recs)
    if len(recs) < 1000:
        break

print("== 带 num / partNum 的 item 所在订单 ==")
seen = {}
for r in out:
    for it in (r.get("inboundProductList") or []):
        if it.get("num") is not None or it.get("partNum") is not None:
            key = r.get("inboundNo")
            if key not in seen:
                seen[key] = (r, [])
            seen[key][1].append(it)

for inboundNo, (r, items) in seen.items():
    for it in items[:3]:
        print("type=%s status=%s | %s | %s | bizNo=%s | 入库时间=%s 入库人=%s" % (
            r.get("inboundType"), r.get("status"), r.get("warehouseName"), inboundNo,
            r.get("bizNo"), r.get("inboundAt"), r.get("inboundPerson")))
        print("    %s: inboundNum=%s num=%s partNum=%s quantity=%s" % (
            it.get("productName"), it.get("inboundNum"), it.get("num"),
            it.get("partNum"), it.get("quantity")))
print("共 %d 张单" % len(seen))
