# -*- coding: utf-8 -*-
"""只读：列出测试门店(246964)所有待入库(status=1)的入库单，供下一步探测选靶"""
import hmac, hashlib, base64, time, random, json
from urllib.parse import quote
import urllib.request

OPEN_ID = "71fcea7abc9709d653693116410b5385"
GRANT_CODE = "WaGI2rqy9b"
OPEN_KEY = "KYDwl5j39Wb71bff5fe6b0434ac6366a913c0dbc947q1SpQyZ"
BASE = "https://openapi.qmai.cn"
TEST_QM_STORE_ID = 246964

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

# 测试门店报货单归属键（同今天探测脚本）
print("== 拉测试门店报货单归属键 ==")
keys = set()
resp = call(BASE + "/v3/newPattern/scmApiserver/post/declare/order/list",
            {"storeIdList": [TEST_QM_STORE_ID],
             "createdStartAt": "2026-07-16 00:00:00", "createdEndAt": "2026-08-15 23:59:59",
             "pageNo": 1, "pageSize": 50, "statusList": [3, 4], "payStatusList": [1, 2]})
data = resp.get("data") or {}
recs = data.get("data") if isinstance(data, dict) else None
if recs:
    for r in recs:
        for f in ("declareNo", "requireNo", "bizNo"):
            if r.get(f): keys.add(r[f])
        for f in ("requireNoList", "purchaseApplyNoList", "bizNoList"):
            for v in (r.get(f) or []):
                if v: keys.add(v)
print("  归属键 %d 个" % len(keys))

print("== 测试门店全部入库单（近30天，按创建倒序） ==")
out = []
for page in range(1, 12):
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

cnt = 0
for r in out:
    wn = str(r.get("warehouseName") or "")
    if not (r.get("bizNo") in keys or "测试门店" in wn):
        continue
    cnt += 1
    items = r.get("inboundProductList") or []
    names = ",".join((i.get("productName") or "")[:8] for i in items[:3])
    print("status=%s type=%s | %s | inboundNo=%s bizNo=%s | 创建=%s | %d品: %s" % (
        r.get("status"), r.get("inboundType"), wn, r.get("inboundNo"), r.get("bizNo"),
        r.get("createdAt"), len(items), names))
print("测试门店相关入库单共 %d 条（status: 1=待入库 2=已入库 3=已作废）" % cnt)
