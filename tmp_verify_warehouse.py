# -*- coding: utf-8 -*-
"""验证补发出库单用的三个仓库编码是否在企迈存在（9.2.13 实时库存列表）"""
import hmac, hashlib, base64, time, random, json
from urllib.parse import quote
import urllib.request

OPEN_ID = "71fcea7abc9709d653693116410b5385"
GRANT_CODE = "WaGI2rqy9b"
OPEN_KEY = "KYDwl5j39Wb71bff5fe6b0434ac6366a913c0dbc947q1SpQyZ"
URL = "https://openapi.qmai.cn/v3/newPattern/scmApiserver/post/warehouse-product/list"

def make_auth():
    ts = int(time.time())
    nonce = random.randint(10000, 99999)
    sorted_str = "grantCode=%s&nonce=%s&openId=%s&timestamp=%s" % (GRANT_CODE, nonce, OPEN_ID, ts)
    restored = quote(sorted_str, safe='').replace("%3D", "=").replace("%26", "&")
    raw_token = base64.b64encode(
        hmac.new(OPEN_KEY.encode(), restored.encode(), hashlib.sha1).digest()).decode()
    url_token = quote(raw_token, safe='')
    return ts, nonce, url_token

def call(params):
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

# 不传 warehouseNoList 查全部仓库（返回所有仓库的行，含 warehouseNo/warehouseName）
all_rows = []
for page in range(1, 101):
    resp = call({"pageNo": page, "pageSize": 100, "isEmpty": 1})
    data = resp.get("data") or {}
    rows = data.get("list") or data.get("data") or data.get("records") or []
    if isinstance(data, dict) and not rows:
        break
    all_rows.extend(rows)
    if len(rows) < 100:
        break

print("接口 code:", resp.get("code"), "msg:", resp.get("msg"))
print("共拉取行数:", len(all_rows))

# 去重收集仓库编码+名称
wh = {}
for r in all_rows:
    no = r.get("warehouseNo")
    name = r.get("warehouseName")
    if no:
        wh[no] = name
print("\n== 企迈返回的全部仓库（%d 个）==" % len(wh))
for no, name in sorted(wh.items()):
    print("%s  %s" % (no, name))

# 验证三个目标编码
print("\n== 验证目标编码 ==")
for code in ["PSCK000149", "PSCK000070", "PSCK000023"]:
    if code in wh:
        print("%s ✓ 存在 → %s" % (code, wh[code]))
    else:
        print("%s ✗ 不存在" % code)
