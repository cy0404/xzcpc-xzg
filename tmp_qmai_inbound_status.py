# -*- coding: utf-8 -*-
"""查询企迈 OpenAPI 9.2.2 入库单，分析入库类型与状态分布（看是否自动入库）"""
import hmac, hashlib, base64, time, random, json
from urllib.parse import quote
import urllib.request

OPEN_ID = "71fcea7abc9709d653693116410b5385"
GRANT_CODE = "WaGI2rqy9b"
OPEN_KEY = "KYDwl5j39Wb71bff5fe6b0434ac6366a913c0dbc947q1SpQyZ"
URL = "https://openapi.qmai.cn/v3/scm/order/inbound/order/list"

def make_auth():
    ts = int(time.time())
    nonce = random.randint(10000, 99999)
    sorted_str = "grantCode=%s&nonce=%s&openId=%s&timestamp=%s" % (GRANT_CODE, nonce, OPEN_ID, ts)
    restored = quote(sorted_str, safe='').replace("%3D", "=").replace("%26", "&")
    raw_token = base64.b64encode(
        hmac.new(OPEN_KEY.encode(), restored.encode(), hashlib.sha1).digest()).decode()
    url_token = quote(raw_token, safe='')
    return ts, nonce, url_token

def call_922(params):
    ts, nonce, token = make_auth()
    body = {
        "openId": OPEN_ID, "grantCode": GRANT_CODE,
        "nonce": nonce, "timestamp": ts, "token": token,
        "params": params,
    }
    req = urllib.request.Request(URL, data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode())

# 近 7 天
all_records = []
for page in range(1, 6):
    resp = call_922({
        "createdStartAt": "2026-08-08 00:00:00",
        "createdEndAt": "2026-08-15 23:59:59",
        "pageNo": page, "pageSize": 1000,
    })
    data = resp.get("data") or {}
    recs = data.get("records") if isinstance(data, dict) else data
    if isinstance(data, dict) and recs is None:
        recs = data.get("data")
    if not recs:
        break
    all_records.extend(recs)
    print("page", page, "got", len(recs))
    if len(recs) < 1000:
        break

print("TOTAL =", len(all_records))

# 类型/状态分布
from collections import Counter
type_status = Counter((r.get("inboundType"), r.get("status")) for r in all_records)
print("\n== (inboundType, status) 分布 ==")
for k in sorted(type_status, key=lambda x: (x[0] is None, x[0])):
    print("type=%s status=%s : %d" % (k[0], k[1], type_status[k]))

# 只看 1仓配 / 3采购：状态与收货人
print("\n== 类型1/3 明细（按创建时间倒序）==")
sub = [r for r in all_records if r.get("inboundType") in (1, 3)]
sub.sort(key=lambda r: str(r.get("createdAt") or ""), reverse=True)
for r in sub[:25]:
    print("%s | type=%s status=%s | %s | 单号=%s bizNo=%s | 创建=%s 入库时间=%s 入库人=%s | 品数=%s" % (
        r.get("createdAt"), r.get("inboundType"), r.get("status"),
        r.get("warehouseName"), r.get("inboundNo"), r.get("bizNo"),
        r.get("createdAt"), r.get("inboundAt"), r.get("inboundPerson"),
        r.get("productAllNum")))

# status=1（待入库）里类型1/3 的比例
pend = [r for r in all_records if r.get("status") == 1]
print("\n待入库(status=1)总数 =", len(pend), "；其中类型1/3 =", len([r for r in pend if r.get("inboundType") in (1, 3)]))
